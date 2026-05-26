/**
 * REQUIREMENTS this file demonstrates:
 *   - Idempotent in-memory store using a concurrent map
 *   - Idempotency key strategy: prefer a stable business key, fall back to messageId header
 *   - Thread safety in a coroutine context: ConcurrentHashMap + atomic putIfAbsent
 *
 * LESSONS embedded:
 *   - Idempotency: processing the same message twice must produce the same result
 *   - Why HTTP/Kafka consumers need idempotency: at-least-once delivery guarantees duplicates
 *   - ConcurrentHashMap.putIfAbsent() is atomic — safe for concurrent coroutine access
 *   - `data class` for immutable domain events
 *
 * RELATED DRILL TOPICS: kafka_hermes, distributed_systems
 */
package com.major.playground.hermes

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** A single impression event (user saw a recommended item). */
data class Impression(
    val messageId: String,   // idempotency key
    val userId: String,
    val itemId: String,
    val timestamp: Instant,
    val pageContext: String?,
    val position: Int,       // rank at which the item was shown
)

/** Possible outcomes of storing an impression. */
sealed class StoreResult {
    /** First time we see this messageId — impression stored. */
    object Stored : StoreResult()
    /** We already processed this messageId — safe to return 200 to the caller. */
    object Duplicate : StoreResult()
}

/**
 * Idempotent in-memory repository for impression events.
 *
 * LESSON: In production this would be backed by Redis (SETNX) or a database
 * (INSERT ... ON CONFLICT DO NOTHING). The contract is the same:
 *   "If I have seen this messageId before, do not process it again."
 *
 * Why is idempotency critical for Hermes/Kafka consumers?
 *   - Kafka provides at-least-once delivery by default.
 *   - Network failures can cause a message to be re-delivered even after processing.
 *   - Without idempotency, one click event → two impression records → skewed analytics.
 *
 * LESSON: This class is @Component, not @Service. Both are functionally identical;
 * @Component is a generic stereotype, @Service signals "business logic layer".
 * Repositories are typically annotated @Repository in Spring (enables exception translation),
 * but for this in-memory stub, @Component is fine.
 */
@Component
class ImpressionRepository {

    private val log = LoggerFactory.getLogger(ImpressionRepository::class.java)

    // LESSON: ConcurrentHashMap is the right choice here.
    //   - Coroutines on the same JVM thread pool may call store() concurrently.
    //   - A plain HashMap + synchronized{} would work but ConcurrentHashMap has better
    //     read concurrency (lock striping vs whole-map lock).
    //   - The value type is Boolean — we only care THAT we've seen the key, not what
    //     the original message contained. In production, store a TTL-bearing entry.
    private val seen = ConcurrentHashMap<String, Impression>()

    /**
     * Stores [impression] if its idempotency key has not been seen before.
     *
     * @return [StoreResult.Stored] on first insertion, [StoreResult.Duplicate] otherwise.
     *
     * LESSON: `putIfAbsent` is atomic under ConcurrentHashMap — the check-then-act
     * is a single operation, so two concurrent callers with the same key cannot both
     * see "not found" and both insert. One wins, the other gets the existing value.
     *
     * This is NOT a suspend function — it does no I/O, only in-memory map access.
     * Making it suspend for no reason adds unnecessary coroutine overhead.
     */
    fun store(impression: Impression): StoreResult {
        val existing = seen.putIfAbsent(impression.messageId, impression)
        return if (existing == null) {
            log.debug("Stored impression: messageId={}", impression.messageId)
            StoreResult.Stored
        } else {
            log.info(
                "Duplicate impression detected: messageId={}, first seen at={}",
                impression.messageId,
                existing.timestamp,
            )
            StoreResult.Duplicate
        }
    }

    /** Returns all stored impressions for testing/debugging. Not in production API. */
    fun all(): Collection<Impression> = seen.values

    /** Returns the count of unique impressions stored. */
    fun size(): Int = seen.size
}
