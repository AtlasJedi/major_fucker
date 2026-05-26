/**
 * REQUIREMENTS this file demonstrates:
 *   - Kotlin data classes as DTOs (no boilerplate equals/hashCode/toString/copy)
 *   - Jackson deserialization with @JsonProperty for camelCase ↔ snake_case mapping
 *   - Nullable vs non-nullable fields and their semantics
 *
 * LESSONS embedded:
 *   - data class provides equals/hashCode/toString/copy for free
 *   - Default parameter values allow partial JSON (optional fields)
 *   - @field:JsonProperty needed in data class constructors (annotation target disambiguation)
 *
 * RELATED DRILL TOPICS: kotlin_basics, recsys
 */
package com.major.playground.recs.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Incoming recommendation request from a client (e.g., Allegro web frontend).
 *
 * LESSON: `data class` in Kotlin is shorthand for a class whose primary purpose
 * is to hold data. The compiler auto-generates:
 *   - equals() / hashCode()  — structural equality
 *   - toString()             — human-readable representation
 *   - copy(...)              — shallow copy with selective overrides
 *   - componentN() functions — for destructuring: val (a, b) = myData
 *
 * In Java you'd need Lombok @Data or 50+ lines of boilerplate.
 */
data class RecommendationRequest(
    // LESSON: Non-nullable String in Kotlin. If the JSON field is absent,
    // Jackson will throw a MismatchedInputException. Use String? and a default
    // value if the field is optional.
    @field:JsonProperty("user_id")
    val userId: String,

    @field:JsonProperty("context_item_id")
    val contextItemId: String,

    // LESSON: Default parameter values = optional JSON fields.
    // If the caller doesn't send `limit`, this defaults to 20.
    val limit: Int = 20,

    // LESSON: Nullable type String? — can be null. The caller may omit `page_context`.
    // Kotlin forces you to handle the null case explicitly (no NullPointerException surprises).
    @field:JsonProperty("page_context")
    val pageContext: String? = null,

    // A/B experiment variant the caller is enrolled in (sent by the API gateway)
    @field:JsonProperty("ab_variant")
    val abVariant: String? = null,
)
