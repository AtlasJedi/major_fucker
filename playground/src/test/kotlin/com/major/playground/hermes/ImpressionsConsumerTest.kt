/**
 * REQUIREMENTS this file demonstrates:
 *   - Idempotency test: sending the same batch twice results in one accepted + one duplicate
 *   - Status code contract: 200 on success AND on permanent failure (dead-letter), 503 on transient
 *   - Validation test: missing required fields → 200 with dead_lettered=1 (not 4xx)
 *   - Hermes header handling: messageId header and idempotency key precedence
 *
 * LESSONS embedded:
 *   - Testing idempotency: call the same endpoint twice, assert second is a duplicate
 *   - Why 200 for bad payloads: Hermes retries 4xx forever; 200+dead-letter is the correct contract
 *   - WebTestClient expectBody<T>() for typed response deserialization
 *   - @SpringBootTest vs @WebFluxTest trade-offs
 *
 * RELATED DRILL TOPICS: kafka_hermes, distributed_systems, testing
 */
package com.major.playground.hermes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImpressionsConsumerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var repository: ImpressionRepository

    @BeforeEach
    fun setUp() {
        // LESSON: We can't easily clear the ConcurrentHashMap between tests without
        // exposing a clear() method. In production, use a test-specific bean or
        // TestcontainersRedis with TTL-based expiry.
        // For this demo, we use unique IDs per test to avoid cross-test contamination.
    }

    @Test
    fun `valid batch is accepted with 200`() {
        val batch = ImpressionBatch(
            events = listOf(
                ImpressionEvent(
                    userId = "user-1",
                    itemId = "item-0001",
                    timestamp = "2024-01-01T12:00:00Z",
                    position = 1,
                    pageContext = "home",
                    impressionId = "imp-test-valid-001",
                ),
            ),
        )

        webTestClient
            .post()
            .uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                assertThat(ack.accepted).isEqualTo(1)
                assertThat(ack.deadLettered).isEqualTo(0)
                assertThat(ack.duplicates).isEqualTo(0)
                assertThat(ack.errors).isEmpty()
            }
    }

    @Test
    fun `duplicate event with same impressionId is deduplicated`() {
        val impressionId = "imp-test-dedup-${System.nanoTime()}"
        val event = ImpressionEvent(
            userId = "user-dedup",
            itemId = "item-0002",
            timestamp = "2024-01-01T12:00:00Z",
            position = 2,
            pageContext = null,
            impressionId = impressionId,
        )
        val batch = ImpressionBatch(events = listOf(event))

        // First call — should be accepted
        webTestClient.post().uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                // LESSON: First delivery → accepted=1, duplicates=0
                assertThat(ack.accepted).isEqualTo(1)
                assertThat(ack.duplicates).isEqualTo(0)
            }

        // Second call with the SAME impressionId — should be detected as duplicate
        webTestClient.post().uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            // LESSON: We return 200 on duplicate — NOT 4xx.
            // The message was processed (idempotently). Hermes should not retry.
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                // LESSON: Second delivery → accepted=0, duplicates=1
                assertThat(ack.accepted).isEqualTo(0)
                assertThat(ack.duplicates).isEqualTo(1)
            }
    }

    @Test
    fun `missing userId results in dead-letter with 200 response`() {
        // LESSON: Malformed payloads must return 200 + dead-letter the event.
        // Returning 4xx would cause Hermes to retry indefinitely — a broken message
        // would hammer the service forever and block the topic's consumer group.
        val batch = ImpressionBatch(
            events = listOf(
                ImpressionEvent(
                    userId = null, // MISSING — should be dead-lettered
                    itemId = "item-0003",
                    timestamp = "2024-01-01T12:00:00Z",
                    position = 1,
                    pageContext = null,
                    impressionId = "imp-test-invalid-001",
                ),
            ),
        )

        webTestClient
            .post()
            .uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            // LESSON: 200 even for invalid payload — dead-letter pattern
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                assertThat(ack.accepted).isEqualTo(0)
                assertThat(ack.deadLettered).isEqualTo(1)
                assertThat(ack.errors).isNotEmpty
                assertThat(ack.errors.first()).contains("userId")
            }
    }

    @Test
    fun `mixed batch handles valid and invalid events independently`() {
        val batch = ImpressionBatch(
            events = listOf(
                // Valid event
                ImpressionEvent(
                    userId = "user-mix-1",
                    itemId = "item-0010",
                    timestamp = "2024-01-01T12:00:00Z",
                    position = 1,
                    pageContext = null,
                    impressionId = "imp-test-mix-valid-${System.nanoTime()}",
                ),
                // Invalid event (missing itemId)
                ImpressionEvent(
                    userId = "user-mix-2",
                    itemId = null,
                    timestamp = "2024-01-01T12:00:00Z",
                    position = 2,
                    pageContext = null,
                    impressionId = "imp-test-mix-invalid-${System.nanoTime()}",
                ),
            ),
        )

        webTestClient
            .post()
            .uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                // LESSON: Partial success — valid event accepted, invalid dead-lettered
                assertThat(ack.accepted).isEqualTo(1)
                assertThat(ack.deadLettered).isEqualTo(1)
            }
    }

    @Test
    fun `transient error returns 503 with Retry-After header`() {
        // Trigger the simulated transient error
        webTestClient.post().uri("/hermes/trigger-error").exchange()

        try {
            webTestClient
                .post()
                .uri("/hermes/impressions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    ImpressionBatch(
                        events = listOf(
                            ImpressionEvent(
                                userId = "user-transient",
                                itemId = "item-0020",
                                timestamp = "2024-01-01T12:00:00Z",
                                position = 1,
                                pageContext = null,
                                impressionId = "imp-test-transient-001",
                            ),
                        ),
                    ),
                )
                .exchange()
                // LESSON: 503 = transient failure — Hermes will retry with backoff.
                // The Retry-After header hints how long Hermes should wait.
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().exists("Retry-After")
        } finally {
            // Always reset the error state so other tests are not affected
            webTestClient.post().uri("/hermes/trigger-error").exchange()
        }
    }

    @Test
    fun `hermes message id header is used as fallback idempotency key`() {
        val hermesMessageId = "hermes-msg-${System.nanoTime()}"
        val batch = ImpressionBatch(
            events = listOf(
                // No impressionId — falls back to hermesMessageId + index
                ImpressionEvent(
                    userId = "user-hermes",
                    itemId = "item-0030",
                    timestamp = "2024-01-01T12:00:00Z",
                    position = 1,
                    pageContext = null,
                    impressionId = null, // intentionally missing
                ),
            ),
        )

        // First delivery with a specific Hermes-Message-Id
        webTestClient.post().uri("/hermes/impressions")
            .header("Hermes-Message-Id", hermesMessageId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { assertThat(it.responseBody!!.accepted).isEqualTo(1) }

        // Second delivery with the SAME Hermes-Message-Id → duplicate
        webTestClient.post().uri("/hermes/impressions")
            .header("Hermes-Message-Id", hermesMessageId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { assertThat(it.responseBody!!.duplicates).isEqualTo(1) }
    }

    @Test
    fun `empty batch returns 200 with zero accepted`() {
        val batch = ImpressionBatch(events = emptyList())

        webTestClient
            .post()
            .uri("/hermes/impressions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(batch)
            .exchange()
            // LESSON: Empty batch is not a server error — return 200 with explanation.
            // The publisher has a bug (sent empty batch), but our consumer should not crash.
            .expectStatus().isOk()
            .expectBody<ImpressionAck>()
            .consumeWith { result ->
                val ack = result.responseBody!!
                assertThat(ack.accepted).isEqualTo(0)
            }
    }
}
