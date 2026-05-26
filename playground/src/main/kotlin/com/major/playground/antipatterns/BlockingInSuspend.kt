/**
 * REQUIREMENTS this file demonstrates:
 *   - The most common coroutine bug: calling Thread.sleep() inside a suspend function
 *   - Why it's a bug and what the correct alternative is
 *   - How to identify blocking calls on the reactor event loop thread
 *
 * LESSONS embedded:
 *   - Thread.sleep() blocks the OS thread; delay() suspends the coroutine (releases the thread)
 *   - WebFlux runs on a small Netty event loop (typically 2*CPU threads); blocking one blocks all requests on that thread
 *   - Reactor BlockHound can detect blocking calls at runtime — use it in tests
 *   - Dispatchers.IO is the escape hatch for unavoidably blocking code
 *
 * RELATED DRILL TOPICS: coroutines_webflux
 */
package com.major.playground.antipatterns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Demonstrates blocking vs non-blocking delays in a coroutine context.
 *
 * LESSON: This is the single most important WebFlux + Coroutines mistake.
 * It causes subtle production issues: the service appears healthy but
 * latency spikes under load because the event loop is blocked.
 */
@RestController
@RequestMapping("/antipatterns/blocking")
class BlockingInSuspend {

    private val log = LoggerFactory.getLogger(BlockingInSuspend::class.java)

    /**
     * BUG version — do NOT use this in production.
     * curl http://localhost:8080/antipatterns/blocking/wrong
     */
    @GetMapping("/wrong")
    suspend fun theBuggyWay(): Map<String, String> {
        log.info("BUG: about to block the event loop thread with Thread.sleep()")

        // BUG: Thread.sleep(1000) blocks the OS thread for 1 second.
        //
        // What actually happens in WebFlux:
        //   1. The Netty event loop has N threads (default: 2 * CPU cores, e.g. 16 on an 8-core)
        //   2. Each thread can handle MANY concurrent coroutines — as long as they SUSPEND
        //   3. Thread.sleep() does NOT suspend — it pins the thread for the full duration
        //   4. While this thread sleeps, it handles ZERO other requests
        //   5. Under load (many concurrent requests), all event loop threads sleep at once
        //   6. Result: throughput collapses, latency spikes, CPU idles at near-0% (all sleeping)
        //
        // FIX: See theCorrectWay() below — use delay() instead.
        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(1000) // BUG: blocks OS thread, wastes Netty event loop capacity

        return mapOf(
            "status" to "done",
            "warning" to "This endpoint blocks the Netty event loop — see source for explanation",
        )
    }

    /**
     * CORRECT version — use delay() for artificial waits in coroutines.
     * curl http://localhost:8080/antipatterns/blocking/correct
     */
    @GetMapping("/correct")
    suspend fun theCorrectWay(): Map<String, String> {
        log.info("CORRECT: suspending coroutine with delay()")

        // LESSON: delay(1000) suspends the current coroutine for 1 second.
        //   - The OS thread is RELEASED back to the event loop's thread pool
        //   - The event loop uses that thread to handle OTHER requests during the wait
        //   - After 1 second, the coroutine resumes (possibly on a different thread)
        //   - Result: the service handles thousands of concurrent "waiting" requests
        //     with the same small thread pool — this is the power of non-blocking I/O
        delay(1000)

        return mapOf(
            "status" to "done",
            "note" to "This endpoint releases the thread while waiting — correct pattern",
        )
    }

    /**
     * CORRECT way to call a GENUINELY blocking API (legacy JDBC, file I/O, etc.)
     * that you cannot replace with a non-blocking alternative.
     * curl http://localhost:8080/antipatterns/blocking/blocking-io
     *
     * LESSON: Sometimes you must call blocking code (old JDBC driver, legacy library).
     * The escape hatch is `withContext(Dispatchers.IO)`:
     *   - Switches the coroutine to the IO dispatcher (an unbounded thread pool)
     *   - The IO pool is DESIGNED for blocking operations (it grows as needed)
     *   - The event loop thread is freed while the IO pool thread blocks
     *   - After the blocking call returns, execution resumes on the original dispatcher
     */
    @GetMapping("/blocking-io")
    suspend fun blockingIoCorrectly(): Map<String, String> {
        log.info("CORRECT: wrapping blocking call in withContext(Dispatchers.IO)")

        val result = withContext(Dispatchers.IO) {
            // LESSON: Thread.sleep() is still wrong here ethically (use real blocking I/O),
            // but the pattern is correct: blocking code goes inside Dispatchers.IO.
            // In production this would be: legacyJdbcRepository.findById(id) or similar.
            Thread.sleep(100) // simulates legacy blocking DB call
            "legacy_data_retrieved"
        }
        // LESSON: After withContext returns, we're back on the event loop dispatcher.
        // `result` is available here — withContext suspends until the block completes.

        return mapOf(
            "status" to "done",
            "data" to result,
            "note" to "withContext(Dispatchers.IO) is the correct escape hatch for blocking calls",
        )
    }
}
