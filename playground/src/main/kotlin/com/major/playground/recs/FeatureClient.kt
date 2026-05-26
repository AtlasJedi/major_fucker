/**
 * REQUIREMENTS this file demonstrates:
 *   - WebClient with coroutines: awaitBody() to convert Mono → suspend result
 *   - Calling an external "feature store" HTTP service
 *   - Error handling: distinguish 404 (item has no features) from 5xx (transient error)
 *   - Retry logic and timeout wrapping at the call site
 *
 * LESSONS embedded:
 *   - awaitBody<T>() is the bridge: Mono<T>.awaitBody() suspends until the Mono completes
 *   - onStatus() maps HTTP error codes to exceptions before they reach the caller
 *   - WebClientResponseException carries the HTTP status and body for diagnosis
 *   - `awaitBodyOrNull()` for 404-tolerant lookups
 *
 * RELATED DRILL TOPICS: coroutines_webflux
 */
package com.major.playground.recs

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull

/** Feature vector returned by the feature store. Stub: only a subset of real features. */
data class ItemFeatures(
    @JsonProperty("item_id") val itemId: String,
    val category: String = "unknown",
    val price: Double = 0.0,
    val rating: Double = 0.0,
    @JsonProperty("in_stock") val inStock: Boolean = true,
)

/**
 * HTTP client wrapping the feature store service.
 *
 * In Allegro's architecture this would call an internal feature-store service
 * (similar to Feast or a custom Redis-backed feature server).
 *
 * LESSON: We inject the shared WebClient bean (configured with timeouts and
 * connection pool in WebClientConfig). We never create a new WebClient here.
 * Creating WebClients ad-hoc bypasses the shared connection pool → resource leak.
 */
@Component
class FeatureClient(
    // LESSON: Constructor injection is idiomatic in Kotlin + Spring.
    // No @Autowired needed — Spring auto-detects single-constructor classes.
    private val webClient: WebClient,
) {

    private val log = LoggerFactory.getLogger(FeatureClient::class.java)

    // Stub base URL — in production this would come from application.yml via @Value
    private val featureStoreBaseUrl = "http://feature-store"

    /**
     * Fetches features for a single item from the feature store.
     *
     * Returns null if the item has no features (404 from feature store).
     * Throws on 5xx (let the caller decide whether to retry or fallback).
     *
     * LESSON: The call chain:
     *   webClient.get() — start building a GET request
     *   .uri(...)        — set the URL
     *   .retrieve()      — execute and return a ResponseSpec (lazy — nothing happens yet)
     *   .onStatus(...)   — register error handler (maps status codes to exceptions)
     *   .awaitBodyOrNull<ItemFeatures>() — THIS is where the HTTP call actually fires.
     *                    It suspends the coroutine until the response arrives,
     *                    then deserialises the body using Jackson, or returns null on 404.
     *
     * LESSON: `awaitBodyOrNull()` vs `awaitBody()`:
     *   awaitBody()      — throws NoSuchElementException if the response body is empty
     *   awaitBodyOrNull()— returns null if body is empty or status is 404 (with handler below)
     */
    suspend fun getItemFeatures(itemId: String): ItemFeatures? {
        log.debug("Fetching features for item: {}", itemId)

        // LESSON: This is a STUB — the real feature store is not running.
        // We short-circuit and return synthetic features to allow the app to run.
        // In production, remove this block and let the WebClient call through.
        return syntheticFeatures(itemId)

        /*
        // Real implementation (uncomment when a real feature store is available):
        return try {
            webClient.get()
                .uri("$featureStoreBaseUrl/features/items/{itemId}", itemId)
                .retrieve()
                // LESSON: onStatus(predicate, handler) — map HTTP errors to exceptions.
                // 404 → we return null; the exception triggers the catch block below.
                .onStatus(HttpStatusCode::is4xxClientError) { response ->
                    response.createException().flatMap { Mono.error(it) }
                }
                .awaitBodyOrNull<ItemFeatures>()
        } catch (ex: WebClientResponseException.NotFound) {
            log.warn("No features found for item {}", itemId)
            null
        }
        */
    }

    /**
     * Fetches features for multiple items in parallel at the call site.
     *
     * LESSON: This method is intentionally simple — the caller is responsible for
     * parallelism (using coroutineScope { async { } } or similar).
     * Keeping parallelism at the controller/orchestration layer is easier to reason about
     * and test than hiding it inside a service method.
     */
    suspend fun getItemFeaturesBatch(itemIds: List<String>): Map<String, ItemFeatures> {
        // LESSON: `mapNotNull` — maps + filters out nulls in one pass.
        // Items without features are silently excluded from the result map.
        return itemIds
            .mapNotNull { id -> getItemFeatures(id)?.let { id to it } }
            .toMap()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stub helpers (not in production code)
    // ─────────────────────────────────────────────────────────────────────────

    private fun syntheticFeatures(itemId: String): ItemFeatures {
        val hash = itemId.hashCode()
        return ItemFeatures(
            itemId = itemId,
            category = listOf("electronics", "fashion", "home", "books")[(hash and 0x3)],
            price = (10 + (hash and 0xFF)).toDouble(),
            rating = 3.0 + (hash and 0x7) * 0.25,
            inStock = (hash and 0x1) == 0,
        )
    }
}
