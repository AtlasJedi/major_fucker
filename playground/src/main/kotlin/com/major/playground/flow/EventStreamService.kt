/**
 * REQUIREMENTS this file demonstrates:
 *   - Kotlin Flow: cold stream, operators, SSE endpoint
 *   - Flow operators: map, filter, buffer, chunked, onEach, catch, transform
 *   - Exposing a Flow<T> as a Server-Sent Events (SSE) stream in Spring WebFlux
 *   - Flow cancellation and upstream lifecycle
 *   - Batching with chunked() and time-windowed accumulation
 *
 * LESSONS embedded:
 *   - Cold vs Hot streams: Flow is cold (starts on collect), SharedFlow is hot
 *   - Flow.asFlux() bridges Kotlin Flow to Project Reactor Flux (needed for SSE in WebFlux)
 *   - buffer() operator: decouples producer from consumer, prevents backpressure blocking
 *   - chunked(n) accumulates n items into a List<T> — batch processing pattern
 *   - transform { } is the escape hatch: emit 0, 1, or many items per input
 *   - catch { } handles upstream errors without terminating the stream
 *
 * RELATED DRILL TOPICS: coroutines_webflux, kotlin_flows
 */
package com.major.playground.flow

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** A single recommendation system event (impression, click, conversion, etc.). */
data class RecEvent(
    val eventId: String,
    val type: EventType,
    val userId: String,
    val itemId: String,
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
)

enum class EventType { IMPRESSION, CLICK, CONVERSION, QUALITY_SIGNAL }

/** A batch of events, used when downstream prefers batch processing. */
data class EventBatch(
    val events: List<RecEvent>,
    val batchSize: Int = events.size,
)

/**
 * Event stream service and controller.
 *
 * Demonstrates two Flow patterns:
 *  1. Single events as SSE (Server-Sent Events) — useful for real-time dashboards
 *  2. Batched events — useful for downstream consumers that prefer bulk processing
 *
 * LESSON: Server-Sent Events (SSE) is a one-way persistent HTTP stream from server to client.
 * WebFlux exposes SSE by returning a Flux<ServerSentEvent<T>> or a Flow<T> with the
 * correct MediaType (text/event-stream).
 *
 * LESSON: Flow vs Flux in Spring WebFlux:
 *   - WebFlux natively supports Flow<T> in controller return types (no conversion needed)
 *     when the suspend bridge (kotlinx-coroutines-reactor) is on the classpath.
 *   - For SSE you need MediaType.TEXT_EVENT_STREAM_VALUE.
 *   - You CAN also explicitly call Flow.asFlux() to get a Reactor Flux if needed.
 */
@RestController
@RequestMapping("/events")
class EventStreamService {

    private val log = LoggerFactory.getLogger(EventStreamService::class.java)
    private val eventCounter = AtomicLong(0)

    /**
     * SSE endpoint streaming individual events.
     * curl http://localhost:8080/events/stream
     *
     * LESSON: Returning `Flow<ServerSentEvent<RecEvent>>` with
     * MediaType.TEXT_EVENT_STREAM_VALUE tells Spring to:
     *   1. Keep the HTTP connection open
     *   2. Serialise each Flow item as an SSE frame: "data: {...}\n\n"
     *   3. Close the stream when the Flow completes or the client disconnects
     *
     * LESSON: When the HTTP client disconnects, Spring cancels the downstream Flux,
     * which propagates cancellation up to the Flow. The `isActive` check in the
     * generator catches this and stops emitting. This is structured cancellation.
     */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(
        @RequestParam(defaultValue = "50") count: Int,
        @RequestParam(defaultValue = "100") intervalMs: Long,
    ): Flow<ServerSentEvent<RecEvent>> {
        log.info("SSE stream started: count={}, intervalMs={}", count, intervalMs)

        // LESSON: `flow { }` builder creates a COLD stream.
        // Nothing runs until someone calls collect() on this Flow.
        // Each new collector gets its own independent execution.
        return generateEventFlow(count, intervalMs)
            // LESSON: transform { emit(ServerSentEvent.builder(...).build()) }
            // transforms each RecEvent into an SSE frame with metadata.
            .transform { event ->
                emit(
                    ServerSentEvent.builder(event)
                        .id(event.eventId)
                        .event(event.type.name.lowercase())
                        // LESSON: comment field in SSE — useful for debugging in browser DevTools
                        .comment("ts=${event.timestamp}")
                        .build()
                )
            }
            .onStart { log.debug("SSE flow collector connected") }
            .onCompletion { cause ->
                if (cause == null) log.info("SSE stream completed normally")
                else log.info("SSE stream cancelled: {}", cause.message)
            }
    }

    /**
     * Batched event stream — emits batches of up to [batchSize] events.
     * curl http://localhost:8080/events/stream/batched?batchSize=5
     *
     * LESSON: `chunked(n)` accumulates n items and emits a List<T>.
     * This is the Kotlin Flow equivalent of Reactor's `bufferCount(n)`.
     * Use when your downstream consumer is more efficient in bulk (e.g., a DB batch insert).
     */
    @GetMapping("/stream/batched", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamBatchedEvents(
        @RequestParam(defaultValue = "30") count: Int,
        @RequestParam(defaultValue = "50") intervalMs: Long,
        @RequestParam(defaultValue = "5") batchSize: Int,
    ): Flow<ServerSentEvent<EventBatch>> {
        log.info("Batched SSE stream: count={}, batchSize={}", count, batchSize)

        return generateEventFlow(count, intervalMs)
            // LESSON: chunked(n) collects n items then emits the List.
            // The last chunk may be smaller than n (partial batch at end of stream).
            .chunked(batchSize)
            .map { batch ->
                val eb = EventBatch(events = batch)
                ServerSentEvent.builder(eb)
                    .id("batch-${System.currentTimeMillis()}")
                    .event("batch")
                    .build()
            }
    }

    /**
     * Demonstrates Flow error handling.
     * curl http://localhost:8080/events/stream/with-error
     *
     * LESSON: `catch { }` intercepts upstream exceptions.
     * It can: re-throw, emit a recovery value, or complete the stream.
     * Without catch, any upstream exception terminates the Flow immediately.
     */
    @GetMapping("/stream/with-error", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamWithErrorHandling(): Flow<ServerSentEvent<String>> {
        return flow {
            repeat(5) { i ->
                delay(200)
                if (i == 3) throw RuntimeException("Simulated upstream error at item $i")
                emit("event-$i")
            }
        }
            // LESSON: catch { cause -> emit(...) } — recover by emitting an error sentinel.
            // After this, the flow completes normally. The client sees the error as a
            // regular SSE event, not a broken connection.
            .catch { cause ->
                log.warn("Flow error recovered: {}", cause.message)
                emit("ERROR: ${cause.message} — stream ended gracefully")
            }
            .map { msg ->
                ServerSentEvent.builder(msg)
                    .event(if (msg.startsWith("ERROR")) "error" else "data")
                    .build()
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Flow generator (shared by all endpoints above)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a cold Flow of [count] events with [intervalMs] delay between them.
     *
     * LESSON: `flow { }` is the fundamental Flow builder.
     *   - Everything inside is "producer" code.
     *   - `emit(value)` is a suspending call — it suspends if the downstream is slow.
     *   - `currentCoroutineContext().isActive` checks for cancellation from the downstream.
     *     If the HTTP client disconnects, the coroutine context is cancelled and this
     *     check prevents wasted work on events no one will receive.
     *
     * LESSON: `delay()` inside flow { } suspends the COROUTINE (not a thread).
     *   This is the critical difference from Thread.sleep() — see BlockingInSuspend.kt.
     */
    private fun generateEventFlow(count: Int, intervalMs: Long): Flow<RecEvent> = flow {
        val eventTypes = EventType.values()
        val userIds = (1..10).map { "user-$it" }
        val itemIds = (1..50).map { "item-${it.toString().padStart(3, '0')}" }

        repeat(count) { i ->
            // LESSON: Check cancellation before doing work — cooperative cancellation.
            // Structured concurrency requires coroutines to check for cancellation
            // at suspension points (delay does this automatically) OR at explicit checks.
            if (!currentCoroutineContext().isActive) return@flow

            delay(intervalMs)

            val event = RecEvent(
                eventId = "evt-${eventCounter.incrementAndGet().toString().padStart(8, '0')}",
                type = eventTypes[i % eventTypes.size],
                userId = userIds[i % userIds.size],
                itemId = itemIds[i % itemIds.size],
                payload = mapOf("sequence" to i.toString()),
            )
            emit(event)
        }
    }.onEach { event ->
        // LESSON: onEach is a side-effect operator — runs for every element without
        // transforming it. Use for logging, metrics, etc.
        log.debug("Emitting event: id={}, type={}", event.eventId, event.type)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension function: Flow<T>.chunked(size: Int): Flow<List<T>>
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Accumulates [size] items from the upstream Flow into a List<T>, then emits the list.
 * The last chunk may contain fewer than [size] items.
 *
 * LESSON: This is a custom operator implemented with `flow { collect { } }`.
 * We wrap the upstream in a new flow builder and collect it manually. Inside the
 * collector we buffer items and emit batches. After upstream completes, we flush
 * any remaining items as a partial batch.
 *
 * LESSON: `flow { upstream.collect { ... } }` is the idiomatic way to write
 * stateful Flow operators that need to track inter-item state (here: the buffer).
 * `transform { }` is better for stateless 1-to-many mappings.
 *
 * Note: kotlinx.coroutines 1.8+ has Flow.chunked(n) built-in. This impl shows the pattern.
 */
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    val buffer = mutableListOf<T>()
    this@chunked.collect { item ->
        buffer.add(item)
        if (buffer.size >= size) {
            emit(buffer.toList())
            buffer.clear()
        }
    }
    // Flush the last partial batch if any items remain after the upstream completes.
    if (buffer.isNotEmpty()) {
        emit(buffer.toList())
    }
}
