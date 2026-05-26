/**
 * REQUIREMENTS this file demonstrates:
 *   - runTest { } with virtual time for testing delay-based Flows
 *   - Turbine-style Flow collection with toList() after take(n)
 *   - Testing Flow operators: chunked, catch, onCompletion
 *   - Asserting correct SSE event types and structure
 *
 * LESSONS embedded:
 *   - runTest uses a TestCoroutineScheduler that controls virtual time
 *   - delay() inside Flow is advanced by the scheduler, not real wall clock
 *   - Flow.toList() collects all items synchronously (within runTest)
 *   - Testing cold vs hot flow behaviour
 *
 * RELATED DRILL TOPICS: coroutines_webflux, kotlin_flows, testing
 */
package com.major.playground.flow

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStreamServiceTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var eventStreamService: EventStreamService

    // ─────────────────────────────────────────────────────────────────────────
    // Unit tests — test Flow directly without HTTP layer
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `chunked extension function groups items correctly`() = runTest {
        // LESSON: runTest is the coroutines-test entry point.
        // It creates a TestScope with a virtual clock and runs the block as a coroutine.
        // delay() calls inside the block advance virtual time, not real time.
        // This makes delay-heavy tests run in milliseconds.

        val source = kotlinx.coroutines.flow.flow {
            repeat(10) { emit(it) }
        }

        val batches = source.chunked(3).toList()

        // LESSON: toList() collects ALL items from the Flow into a List.
        // Safe here because the source is finite (10 items).
        assertThat(batches).hasSize(4) // [0,1,2], [3,4,5], [6,7,8], [9]
        assertThat(batches[0]).containsExactly(0, 1, 2)
        assertThat(batches[1]).containsExactly(3, 4, 5)
        assertThat(batches[2]).containsExactly(6, 7, 8)
        assertThat(batches[3]).containsExactly(9) // partial last chunk
    }

    @Test
    fun `chunked with exact multiple emits complete chunks only`() = runTest {
        val source = kotlinx.coroutines.flow.flow {
            repeat(6) { emit(it) }
        }

        val batches = source.chunked(3).toList()

        assertThat(batches).hasSize(2)
        assertThat(batches[0]).containsExactly(0, 1, 2)
        assertThat(batches[1]).containsExactly(3, 4, 5)
    }

    @Test
    fun `chunked with size larger than source emits single chunk`() = runTest {
        val source = kotlinx.coroutines.flow.flow {
            repeat(3) { emit(it) }
        }

        val batches = source.chunked(10).toList()

        assertThat(batches).hasSize(1)
        assertThat(batches[0]).containsExactly(0, 1, 2)
    }

    @Test
    fun `event stream emits correct event types in round-robin order`() = runTest {
        // LESSON: We access the service bean directly (not via HTTP) to test Flow logic
        // in isolation. This avoids SSE serialisation complexity.
        // We use take(4) to collect only the first 4 events (avoids waiting for all 50).

        // LESSON: take(n) cancels the upstream flow after n items.
        // The generateEventFlow() infinite-ish loop is cancelled at the next delay() point.
        val events = eventStreamService
            .streamEvents(count = 4, intervalMs = 1) // intervalMs=1 for fast tests
            .take(4)
            .toList()

        assertThat(events).hasSize(4)

        // Verify event types follow the round-robin pattern: IMPRESSION, CLICK, CONVERSION, QUALITY_SIGNAL
        val eventTypes = events.map { sse -> sse.event() }
        assertThat(eventTypes).containsExactly(
            EventType.IMPRESSION.name.lowercase(),
            EventType.CLICK.name.lowercase(),
            EventType.CONVERSION.name.lowercase(),
            EventType.QUALITY_SIGNAL.name.lowercase(),
        )
    }

    @Test
    fun `each SSE event has a non-blank id`() = runTest {
        val events = eventStreamService
            .streamEvents(count = 5, intervalMs = 1)
            .take(5)
            .toList()

        for (event in events) {
            assertThat(event.id()).withFailMessage("SSE event id should not be blank").isNotBlank()
        }
    }

    @Test
    fun `batched stream groups events into correct batch sizes`() = runTest {
        val batches = eventStreamService
            .streamBatchedEvents(count = 10, intervalMs = 1, batchSize = 3)
            .toList()

        // 10 events chunked by 3: [3, 3, 3, 1]
        assertThat(batches).hasSize(4)
        val batchSizes = batches.map { sse -> sse.data()!!.batchSize }
        assertThat(batchSizes).containsExactly(3, 3, 3, 1)
    }

    @Test
    fun `error stream emits error sentinel and completes gracefully`() = runTest {
        // LESSON: The stream emits events 0, 1, 2 (skips 3 due to throw), then the error sentinel.
        // After the catch block, the flow completes normally.
        val events = eventStreamService.streamWithErrorHandling().toList()

        // 3 normal events (0, 1, 2) + 1 error sentinel = 4 total
        assertThat(events).hasSize(4)

        val errorEvent = events.last()
        assertThat(errorEvent.event()).isEqualTo("error")
        assertThat(errorEvent.data()).contains("ERROR")
        assertThat(errorEvent.data()).contains("Simulated upstream error")
    }

    @Test
    fun `each collector gets its own independent flow execution (cold stream)`() = runTest {
        // LESSON: Cold stream property — each collect() starts from the beginning.
        // Two collectors on the same Flow get independent event sequences.
        // A HOT stream (SharedFlow/StateFlow) would share a single running sequence.

        val firstCollection = eventStreamService
            .streamEvents(count = 3, intervalMs = 1)
            .take(3)
            .toList()
            .map { it.id() }

        val secondCollection = eventStreamService
            .streamEvents(count = 3, intervalMs = 1)
            .take(3)
            .toList()
            .map { it.id() }

        // LESSON: Event IDs use an AtomicLong counter so they're globally unique.
        // Cold stream means two separate executions → different IDs.
        // If this were a hot stream (SharedFlow), both collections might share IDs.
        assertThat(firstCollection).doesNotContainAnyElementsOf(secondCollection)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration test via HTTP SSE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SSE HTTP endpoint returns text event stream content type`() {
        // LESSON: We don't collect the full stream — just verify the content type header.
        // Testing SSE via WebTestClient is tricky because the connection stays open.
        // For full SSE integration tests, use returnResult() to get a Flux and take(n).
        webTestClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/events/stream")
                    .queryParam("count", 3)
                    .queryParam("intervalMs", 10)
                    .build()
            }
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
    }
}
