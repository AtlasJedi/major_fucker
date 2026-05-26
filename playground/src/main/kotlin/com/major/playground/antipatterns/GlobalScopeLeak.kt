/**
 * REQUIREMENTS this file demonstrates:
 *   - GlobalScope.async/launch: why it leaks and when (if ever) it's acceptable
 *   - The correct alternative: structured concurrency with coroutineScope or a managed scope
 *   - CoroutineScope tied to a Spring @Service lifecycle as the production pattern
 *
 * LESSONS embedded:
 *   - GlobalScope is NOT a parent scope — it outlives any request, any service restart
 *   - Memory leak: GlobalScope jobs hold references until completion, even after request is done
 *   - Cancellation leak: GlobalScope.async is not cancelled when the request is cancelled
 *   - The fix: coroutineScope { } for per-request scopes, or a managed CoroutineScope for background work
 *
 * RELATED DRILL TOPICS: coroutines_webflux
 */
package com.major.playground.antipatterns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import jakarta.annotation.PreDestroy

/**
 * GlobalScope leak demonstration and the correct alternatives.
 *
 * LESSON: GlobalScope is a top-level CoroutineScope that is NOT tied to any lifecycle.
 * A coroutine launched in GlobalScope:
 *   1. Lives until it completes (or the JVM exits)
 *   2. Is NOT cancelled when the request context ends
 *   3. Is NOT cancelled when the service shuts down gracefully
 *   4. Holds memory references to any captured variables
 *
 * This makes it a resource leak in any server application.
 *
 * Analogy: GlobalScope is like spawning a daemon thread — you lose all control over it.
 */
@Service
class GlobalScopeLeak {

    private val log = LoggerFactory.getLogger(GlobalScopeLeak::class.java)

    // ─────────────────────────────────────────────────────────────────────────
    // WRONG WAY
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun theBuggyWay(requestId: String): String {
        // BUG: GlobalScope.async is launched outside any structured scope.
        //
        // Problems:
        //   1. If the HTTP client disconnects, the coroutineScope on the controller
        //      IS cancelled — but this GlobalScope.async keeps running. It will
        //      consume CPU/memory for its full duration even though no one needs the result.
        //
        //   2. If this service crashes/restarts, in-flight GlobalScope jobs are abandoned.
        //      Any state they were writing may be left partially written.
        //
        //   3. Under load, GlobalScope jobs accumulate. GC sees them as live references
        //      (the GlobalScope holds them) → memory grows unboundedly.
        //
        // FIX: Use coroutineScope { async { } } — see theCorrectWay() below.
        @Suppress("GlobalCoroutineUsage")
        val leakyJob = GlobalScope.async {
            delay(5000) // BUG: this runs even after the caller has given up
            log.warn("GlobalScope job completed for request {} — but caller may be gone!", requestId)
            "result_that_nobody_reads"
        }

        // We await immediately, so in THIS demo the leak isn't obvious.
        // The real danger is GlobalScope.launch { } (fire-and-forget) — the job
        // runs with zero lifecycle management.
        return leakyJob.await()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORRECT WAY #1: coroutineScope { } for per-request parallel work
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun theCorrectWay(requestId: String): String {
        // LESSON: coroutineScope { } creates a child scope of the current coroutine.
        // If the parent coroutine is cancelled (request done, timeout hit, client disconnect),
        // ALL children of coroutineScope are cancelled automatically.
        //
        // This is structured concurrency: the lifecycle of child work is bounded
        // by the lifecycle of the parent that started it.
        return coroutineScope {
            val job = async {
                delay(5000) // If parent is cancelled, delay() checks and throws CancellationException
                "result_$requestId"
            }
            job.await()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORRECT WAY #2: managed CoroutineScope for background service work
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Application-scoped CoroutineScope tied to this service's lifecycle.
     *
     * LESSON: When you genuinely need background work that outlives a single request
     * (e.g., a background cache refresher, a periodic metrics collector),
     * you create a CoroutineScope with an explicit lifecycle:
     *   - SupervisorJob() — child failures don't cancel the scope
     *   - Dispatchers.Default — CPU-bound work
     *   - The scope is cancelled in @PreDestroy — Spring calls this on shutdown
     *
     * This is the ONLY acceptable substitute for GlobalScope in production.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startBackgroundWork(workId: String): Job {
        // LESSON: launch (not async) for fire-and-forget background work.
        // The Job is returned so callers can cancel or join if needed.
        return serviceScope.launch {
            log.info("Background work started: {}", workId)
            repeat(10) { i ->
                delay(1000)
                log.debug("Background work tick {}/10 for: {}", i + 1, workId)
            }
            log.info("Background work completed: {}", workId)
        }
    }

    @PreDestroy
    fun onDestroy() {
        // LESSON: @PreDestroy is called by Spring before the bean is removed from context.
        // Cancelling the scope here ensures all in-flight background coroutines receive
        // CancellationException and clean up gracefully.
        // Without this, background work outlives the application context during shutdown.
        log.info("Cancelling service scope on shutdown")
        serviceScope.cancel("Service shutting down")
    }
}
