/**
 * REQUIREMENTS this file demonstrates:
 *   - Unbounded Flow.collect() without timeout or size limit — backpressure and memory issues
 *   - Correct patterns: take(n), timeout, buffer with overflow strategy
 *   - Cancellation of infinite flows
 *
 * LESSONS embedded:
 *   - Flow is lazy but collect() is greedy — it will process every item the upstream emits
 *   - An infinite upstream + unbounded collect = memory exhaustion or infinite execution
 *   - take(n) limits items; withTimeout limits time; buffer(onBufferOverflow=DROP_OLDEST) limits memory
 *   - Flow.launchIn(scope) as fire-and-forget collection (scope controls lifecycle)
 *
 * RELATED DRILL TOPICS: coroutines_webflux, kotlin_flows
 */
package com.major.playground.antipatterns

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Duration.Companion.milliseconds

/**
 * Backpressure and unbounded collection anti-patterns.
 *
 * LESSON: Backpressure is the mechanism by which a downstream consumer tells the upstream
 * producer to slow down or stop. In Kotlin Flow, backpressure is handled by suspension:
 *   - If the collector suspends (slow processing), the upstream suspends too (by default)
 *   - This is "sequential" backpressure — safe but can slow the producer
 *
 * Problem: if the upstream is a hot source (real-time events) that can't be slowed,
 * you need an explicit overflow strategy.
 */
@RestController
@RequestMapping("/antipatterns/flow")
class UnboundedFlowCollect {

    private val log = LoggerFactory.getLogger(UnboundedFlowCollect::class.java)

    /** Simulates an infinite or very large upstream event source. */
    private fun infiniteEventFlow(): Flow<String> = flow {
        var i = 0
        while (true) { // infinite — emits forever if not cancelled
            delay(10)
            emit("event-${i++}")
        }
    }

    /**
     * BUG: Collecting an infinite flow without any limit.
     * This endpoint would run forever (until the client disconnects or the server is killed).
     * curl http://localhost:8080/antipatterns/flow/wrong
     *
     * BUG: infiniteEventFlow() produces events forever.
     *      collect { allEvents.add(it) } adds them to a list forever.
     *      The list grows without bound → OutOfMemoryError.
     *      Even without OOM: the coroutine never completes → the HTTP response never sends.
     *
     * FIX: Use take(n), withTimeout, or return the flow as SSE — see below.
     */
    @GetMapping("/wrong")
    suspend fun theBuggyWay(): Map<String, Any> {
        val allEvents = mutableListOf<String>()

        // BUG: This will run until OOM or timeout (Spring's own request timeout, if configured).
        // The client gets no response. The coroutine is "stuck" collecting.
        // In production: JVM heap exhausted, GC pressure, service restarts.
        //
        // NOTE: We limit to 10_000 here so the demo server doesn't actually crash.
        //       In real buggy code there is NO limit.
        infiniteEventFlow()
            .take(10_000) // added ONLY to make this demo runnable — real bug has no take()
            .collect { event ->
                allEvents.add(event)
                // BUG: slow consumer (100ms per event) + fast producer (10ms per event)
                // = 10x producer speed > consumer speed
                // Without a buffer strategy, the upstream is slowed to match the consumer.
                // With a buffer of limited size and overflow=DROP_OLDEST, the slow consumer
                // drops items instead of slowing the producer.
                // (Slowdown is intentional here to simulate the mismatch.)
            }

        return mapOf(
            "collected" to allEvents.size,
            "warning" to "This collected ${allEvents.size} items into memory — BUG if source is truly infinite",
        )
    }

    /**
     * CORRECT: Take only the first N items from an infinite stream.
     * curl http://localhost:8080/antipatterns/flow/correct/take
     *
     * LESSON: take(n) installs a downstream cancellation signal:
     *   - After n items are collected, take() cancels the upstream Flow
     *   - The infinite generator's `while(true)` loop is interrupted at the next suspension point
     *   - This is structured cancellation — clean, no leaked resources
     */
    @GetMapping("/correct/take")
    suspend fun correctWithTake(): Map<String, Any> {
        val events = mutableListOf<String>()

        infiniteEventFlow()
            .take(20) // LESSON: take(n) = "I only want 20 items, stop after that"
            .collect { events.add(it) }

        return mapOf("collected" to events.size, "events" to events)
    }

    /**
     * CORRECT: Collect with a timeout budget.
     * curl http://localhost:8080/antipatterns/flow/correct/timeout
     *
     * LESSON: withTimeout cancels the collection after the deadline.
     * The catch{} block recovers from TimeoutCancellationException so we can
     * return whatever we collected so far.
     *
     * Use case: "collect as many events as arrive in the next 500ms, then respond".
     * This is a real pattern for batching/aggregation with latency budgets.
     */
    @GetMapping("/correct/timeout")
    suspend fun correctWithTimeout(): Map<String, Any> {
        val events = mutableListOf<String>()

        try {
            withTimeout(500.milliseconds) {
                infiniteEventFlow().collect { events.add(it) }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // LESSON: TimeoutCancellationException is expected here — it's how withTimeout signals expiry.
            // We catch it to return a normal response with however many events we collected.
            log.debug("Timeout reached — returning {} events collected so far", events.size)
        }

        return mapOf(
            "collected" to events.size,
            "note" to "Collected for 500ms then stopped — time-bounded collection pattern",
        )
    }

    /**
     * CORRECT: Buffer with overflow drop strategy for slow consumers.
     * curl http://localhost:8080/antipatterns/flow/correct/buffer
     *
     * LESSON: buffer(capacity, onBufferOverflow = DROP_OLDEST):
     *   - Creates a bounded queue between producer and consumer
     *   - If the queue is full (consumer is slow), DROP_OLDEST removes the oldest item
     *   - The producer is NOT slowed down — it can emit at its natural rate
     *   - Some events are lost (acceptable for metrics/telemetry, not for financial transactions)
     *
     * LESSON: Overflow strategies:
     *   SUSPEND      — backpressure to producer (default) — safe, no loss, may slow producer
     *   DROP_OLDEST  — drop the oldest buffered item — fast producer, some loss acceptable
     *   DROP_LATEST  — drop the newest item (current emission) — rare use case
     */
    @GetMapping("/correct/buffer")
    suspend fun correctWithBuffer(): Map<String, Any> {
        val events = mutableListOf<String>()

        infiniteEventFlow()
            .take(50)
            .buffer(
                capacity = 10,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
            )
            .collect { event ->
                delay(20) // LESSON: simulate slow consumer (takes 20ms; producer emits every 10ms)
                events.add(event)
            }

        return mapOf(
            "collected" to events.size,
            "note" to "Buffer drop strategy — fast producer, slow consumer, bounded memory",
        )
    }
}

