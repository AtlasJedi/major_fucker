/**
 * REQUIREMENTS this file demonstrates:
 *   - Nested data classes for structured responses
 *   - Sealed class for typed status variants
 *   - Idiomatic use of `when` expression over if-else chains
 *
 * LESSONS embedded:
 *   - Sealed classes = exhaustive discriminated unions (sum types)
 *   - `when` as an expression (returns a value, not just a statement)
 *   - `@JsonInclude` to suppress null fields in JSON output
 *
 * RELATED DRILL TOPICS: kotlin_basics, recsys
 */
package com.major.playground.recs.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Recommendation item returned to the caller.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendedItem(
    @JsonProperty("item_id") val itemId: String,
    val score: Double,
    val title: String,
    // LESSON: Non-null with default — always present in the response, defaults to empty map.
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Full response envelope.
 *
 * LESSON: Enveloping responses with metadata (modelVersion, abVariant, latencyMs)
 * is a best practice at Allegro-scale:
 *   - Debugging which model served the request in production
 *   - A/B experiment tracking (which variant returned what results)
 *   - SLA monitoring (was this response generated within budget?)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendationResponse(
    val items: List<RecommendedItem>,

    @JsonProperty("model_version")
    val modelVersion: String,

    @JsonProperty("ab_variant")
    val abVariant: String?,

    @JsonProperty("latency_ms")
    val latencyMs: Long,

    // LESSON: Sealed class member as a response field.
    // `status` is always one of the defined subclasses — exhaustive, compile-checked.
    val status: RecsStatus,
)

/**
 * Recommendation serving status.
 *
 * LESSON: `sealed class` restricts the class hierarchy to the same file/package.
 * The compiler knows ALL possible subclasses, which enables exhaustive `when` expressions
 * (no `else` branch needed — the compiler will warn if you miss a case).
 *
 * Compare to Java enums: sealed classes can carry different data per variant.
 *  - Ok carries nothing extra
 *  - Degraded carries a reason string
 *  - Fallback carries reason + fallback model name
 */
sealed class RecsStatus {
    /** Full recommendations from the primary model. */
    object Ok : RecsStatus()

    /** Some signals were missing; quality may be reduced. */
    data class Degraded(val reason: String) : RecsStatus()

    /** Primary model timed out; fallback model served the response. */
    data class Fallback(val reason: String, val fallbackModel: String) : RecsStatus()

    // LESSON: This is a method on the sealed class — all subclasses inherit it,
    // but the implementation uses `when` to dispatch on the actual type.
    fun description(): String =
        // LESSON: `when` as an expression — returns a String, not just a statement.
        // The compiler enforces exhaustiveness: add a new subclass → this won't compile
        // until you add a branch. This is MUCH safer than if-else chains.
        when (this) {
            is Ok -> "ok"
            is Degraded -> "degraded: $reason"
            is Fallback -> "fallback($fallbackModel): $reason"
        }
}
