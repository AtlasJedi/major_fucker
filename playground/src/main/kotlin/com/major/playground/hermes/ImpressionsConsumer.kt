/**
 * REQUIREMENTS this file demonstrates:
 *   - Hermes-style HTTP consumer endpoint (Allegro's internal message bus uses HTTP callbacks)
 *   - Correct HTTP status code contract for message consumers:
 *       200 OK       — message processed (or permanent failure → dead-letter, don't retry)
 *       503 Service Unavailable — transient failure, retry after backoff
 *       4xx           — NEVER return for parse/validation errors (Hermes re-delivers 4xx)
 *   - Idempotency key strategy: messageId header OR business key fallback
 *   - Batch processing with partial failure handling
 *   - suspend controller method
 *
 * LESSONS embedded:
 *   - Hermes callback contract: return 200 OR 503. Never 4xx for payload issues.
 *   - Why: if you return 400, Hermes/Kafka retries forever. 200 = "I handled it (even if I
 *     dead-lettered it)". This is the at-least-once / dead-letter pattern.
 *   - @RequestHeader with defaultValue for optional headers
 *   - ResponseEntity<T> for fine-grained status code control
 *   - `partition` + `groupBy` for splitting valid/invalid items in a batch
 *
 * RELATED DRILL TOPICS: kafka_hermes, distributed_systems
 */
package com.major.playground.hermes

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** Payload shape expected from Hermes for each impression event in the batch. */
data class ImpressionEvent(
    val userId: String?,      // nullable — treat null as invalid
    val itemId: String?,      // nullable — treat null as invalid
    val timestamp: String?,   // ISO-8601 string, may be malformed
    val position: Int?,
    val pageContext: String?,
    // LESSON: This is the business-level idempotency key.
    // Prefer this over the transport-level messageId when available.
    val impressionId: String?,
)

/** Batch of impression events from Hermes. */
data class ImpressionBatch(
    val events: List<ImpressionEvent>,
)

/** Acknowledge payload returned to Hermes. */
data class ImpressionAck(
    val accepted: Int,
    val deadLettered: Int,
    val duplicates: Int,
    val errors: List<String>,
)

/**
 * Hermes impression consumer.
 *
 * LESSON: Allegro's Hermes is a publish-subscribe middleware built on Kafka.
 * Consumers register HTTP callbacks. When a message arrives on a topic,
 * Hermes calls the callback with a JSON body.
 *
 * The retry contract (critical for correctness):
 *   - Return 200:  Hermes marks the message as delivered. No retry.
 *   - Return 503:  Hermes retries with backoff (transient failure, e.g., database down).
 *   - Return 4xx:  Hermes retries forever. *** DO NOT RETURN 4xx FOR PAYLOAD ISSUES ***
 *                  A malformed payload will cause an infinite retry loop. Use 200 + dead-letter.
 *   - Return 5xx (except 503): Hermes behaviour varies — generally same as 503.
 */
@RestController
@RequestMapping("/hermes")
class ImpressionsConsumer(
    private val repository: ImpressionRepository,
) {

    private val log = LoggerFactory.getLogger(ImpressionsConsumer::class.java)

    // Simulated transient error state (toggled via /hermes/trigger-error for demo)
    @Volatile
    private var simulateTransientError = false

    /**
     * Processes a batch of impression events.
     *
     * LESSON: The messageId header is the transport-level idempotency key provided by Hermes.
     * We fall back to the business-level impressionId when available, because:
     *  - Transport messageId is unique per Hermes delivery attempt
     *  - A retried message may arrive with a NEW messageId but the same business event
     *  - We want to deduplicate on the BUSINESS event, not the transport envelope
     */
    @PostMapping("/impressions")
    suspend fun receive(
        @RequestBody batch: ImpressionBatch,
        // LESSON: @RequestHeader with defaultValue makes the header optional.
        // Hermes sets this header; direct callers (curl, tests) may not.
        @RequestHeader("Hermes-Message-Id", defaultValue = "") hermesMessageId: String,
        @RequestHeader("Hermes-Retry-Count", defaultValue = "0") retryCount: String,
    ): ResponseEntity<ImpressionAck> {

        log.info(
            "Received impression batch: events={}, messageId={}, retry={}",
            batch.events.size,
            hermesMessageId,
            retryCount,
        )

        // LESSON: 503 for TRANSIENT errors only.
        // Transient = "our database is down", "memory pressure", "circuit breaker open".
        // Hermes will retry with backoff — appropriate for infra-level failures.
        if (simulateTransientError) {
            log.warn("Simulating transient error — returning 503 for retry")
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5") // hint to Hermes: wait 5 seconds
                .build()
        }

        // Validate the batch structure itself (not payload content — that gets dead-lettered).
        if (batch.events.isEmpty()) {
            // LESSON: Empty batch is a permanent caller error. Return 200 + dead-letter summary.
            // Do NOT return 400 — Hermes would retry forever.
            log.warn("Empty impression batch received — possibly a publisher bug")
            return ResponseEntity.ok(
                ImpressionAck(
                    accepted = 0, deadLettered = 0, duplicates = 0,
                    errors = listOf("empty_batch: no events in payload — check publisher"),
                )
            )
        }

        var accepted = 0
        var deadLettered = 0
        var duplicates = 0
        val errors = mutableListOf<String>()

        for ((index, event) in batch.events.withIndex()) {
            // LESSON: `when` with smart cast — `when (val result = ...)` scopes the result
            // to the `when` block. No need for a separate variable declaration.
            when (val parseResult = parseEvent(event, hermesMessageId, index)) {
                is ParseResult.Valid -> {
                    when (repository.store(parseResult.impression)) {
                        is StoreResult.Stored -> accepted++
                        is StoreResult.Duplicate -> duplicates++
                    }
                }
                is ParseResult.Invalid -> {
                    // LESSON: Invalid payload → dead-letter it, return 200.
                    // This prevents infinite retry of a structurally broken message.
                    log.warn(
                        "Dead-lettering invalid event at index {}: {}",
                        index, parseResult.reason,
                    )
                    deadLettered++
                    errors.add("index[$index]: ${parseResult.reason}")
                }
            }
        }

        log.info(
            "Batch processed: accepted={}, deadLettered={}, duplicates={}",
            accepted, deadLettered, duplicates,
        )

        // LESSON: Always return 200 when the batch has been processed (even partially).
        // The dead-letter count tells the publisher which events were unprocessable.
        return ResponseEntity.ok(
            ImpressionAck(
                accepted = accepted,
                deadLettered = deadLettered,
                duplicates = duplicates,
                errors = errors,
            )
        )
    }

    /** Trigger endpoint for demo purposes: simulate a transient error on next call. */
    @PostMapping("/trigger-error")
    fun triggerError(): Map<String, Boolean> {
        simulateTransientError = !simulateTransientError
        return mapOf("simulateTransientError" to simulateTransientError)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing logic
    // ─────────────────────────────────────────────────────────────────────────

    private sealed class ParseResult {
        data class Valid(val impression: Impression) : ParseResult()
        data class Invalid(val reason: String) : ParseResult()
    }

    /**
     * Validates and converts an [ImpressionEvent] to a domain [Impression].
     *
     * LESSON: Parsing is separated from storage — the controller only coordinates,
     * the parsing logic is testable in isolation.
     *
     * Idempotency key resolution:
     *  1. Use impressionId (business key) if present — most stable
     *  2. Fall back to hermesMessageId + index — stable per Hermes delivery attempt
     *  3. Generate a synthetic key as last resort (not ideal but beats crashing)
     */
    private fun parseEvent(
        event: ImpressionEvent,
        hermesMessageId: String,
        index: Int,
    ): ParseResult {
        if (event.userId.isNullOrBlank()) return ParseResult.Invalid("missing userId")
        if (event.itemId.isNullOrBlank()) return ParseResult.Invalid("missing itemId")

        val timestamp = try {
            if (event.timestamp.isNullOrBlank()) Instant.now()
            else Instant.parse(event.timestamp)
        } catch (_: Exception) {
            return ParseResult.Invalid("invalid timestamp format: ${event.timestamp}")
        }

        // LESSON: Idempotency key preference order.
        val idempotencyKey = when {
            !event.impressionId.isNullOrBlank() -> event.impressionId
            hermesMessageId.isNotBlank() -> "$hermesMessageId-$index"
            else -> "${event.userId}:${event.itemId}:${event.timestamp}"
        }

        return ParseResult.Valid(
            Impression(
                messageId = idempotencyKey,
                userId = event.userId,
                itemId = event.itemId,
                timestamp = timestamp,
                pageContext = event.pageContext,
                position = event.position ?: 0,
            )
        )
    }
}
