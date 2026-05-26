/**
 * REQUIREMENTS this file demonstrates:
 *   - suspend fun controller endpoint in Spring WebFlux
 *   - coroutineScope { } for structured concurrency with parallel async { } calls
 *   - withTimeout { } for deadline enforcement on the full recommendation pipeline
 *   - Graceful fallback when one parallel branch fails
 *   - A/B experiment variant propagation through the stack
 *   - Returning typed DTO with metadata (model version, latency, status)
 *
 * LESSONS embedded:
 *   - coroutineScope vs GlobalScope: structured vs unstructured concurrency
 *   - async { } is lazy-ish: it starts immediately within coroutineScope
 *   - If one async { } throws and you don't catch it, the whole coroutineScope cancels
 *   - withTimeout throws CancellationException — Spring converts it to 503
 *   - Fallback strategy: return empty/degraded rather than propagating 5xx
 *   - System.currentTimeMillis() for latency measurement (nanoTime() is more precise)
 *
 * RELATED DRILL TOPICS: coroutines_webflux, recsys
 */
package com.major.playground.recs

import com.major.playground.recs.dto.RecommendationRequest
import com.major.playground.recs.dto.RecommendationResponse
import com.major.playground.recs.dto.RecommendedItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Recommendations serving controller.
 *
 * LESSON: Spring WebFlux supports `suspend fun` in @RestController methods natively.
 * Spring wraps the suspend function in a coroutine scope under the hood.
 * The coroutine runs on the Reactor Netty event loop — DO NOT block here.
 */
@RestController
@RequestMapping("/recs")
class RecommendationsController(
    private val annService: AnnService,
    private val productEncoder: ProductEncoder,
    private val featureClient: FeatureClient,
    private val reranker: Reranker,
) {

    private val log = LoggerFactory.getLogger(RecommendationsController::class.java)

    companion object {
        // Total budget for the entire recommendation pipeline (P99 SLA target)
        private const val PIPELINE_TIMEOUT_MS = 3000L
        private const val MODEL_VERSION = "two-tower-v3.1-stub"
    }

    /**
     * Main recommendation endpoint.
     *
     * Pipeline:
     *   1. Encode query              (CPU-bound, Dispatchers.Default)
     *   2. In parallel:
     *      a. ANN candidate retrieval (CPU-bound)
     *      b. Metadata enrichment     (simulated I/O)
     *   3. Rerank candidates          (CPU-bound, business rules + MMR diversity)
     *   4. Return response with latency + model version + status
     *
     * LESSON: `coroutineScope { }` is the key to structured concurrency:
     *   - Creates a new scope that is a child of the current coroutine's scope.
     *   - All `async { }` blocks launched inside become children.
     *   - If the parent is cancelled (e.g., client disconnects), all children cancel too.
     *   - If a child throws an uncaught exception, the scope cancels all other children
     *     and re-throws the exception to the parent.
     *   - The scope suspends until ALL children complete (or the first failure).
     *
     * LESSON: `withTimeout` creates a deadline for the whole pipeline.
     *   If any step (or the combined time) exceeds PIPELINE_TIMEOUT_MS,
     *   a TimeoutCancellationException is thrown. Spring maps this to 503 via the
     *   exception handler below. This prevents unbounded latency tail.
     */
    @PostMapping("/recommend")
    suspend fun recommend(@RequestBody request: RecommendationRequest): RecommendationResponse {
        val startMs = System.currentTimeMillis()
        log.info(
            "Recommendation request: userId={}, contextItemId={}, limit={}, abVariant={}",
            request.userId, request.contextItemId, request.limit, request.abVariant,
        )

        return try {
            withTimeout(PIPELINE_TIMEOUT_MS.milliseconds) {
                recommendInternal(request, startMs)
            }
        } catch (ex: Exception) {
            // LESSON: Fallback strategy — instead of returning 5xx, serve a degraded response.
            // In production this would serve pre-computed "cold start" items or trending items.
            // Failing loudly is only appropriate if the system has no useful fallback.
            log.warn("Recommendation pipeline failed, returning fallback: {}", ex.message)
            buildFallbackResponse(request, startMs)
        }
    }

    /**
     * Internal pipeline. Called within the timeout.
     *
     * LESSON: coroutineScope { async { } + async { } + await() }
     * This is the canonical pattern for parallel service calls in Kotlin coroutines.
     *
     *  val featsDeferred = async { featureClient.getItemFeaturesBatch(...) }
     *  val metaDeferred  = async { someOtherService.getMetadata(...) }
     *  val feats = featsDeferred.await()   // suspend until features arrive
     *  val meta  = metaDeferred.await()    // likely already done — no extra wait
     *
     * Both calls run concurrently. Total wall time ≈ max(feats_latency, meta_latency),
     * NOT feats_latency + meta_latency. This is the primary latency optimisation.
     */
    private suspend fun recommendInternal(
        request: RecommendationRequest,
        startMs: Long,
    ): RecommendationResponse = coroutineScope {

        // Step 1: Encode the query (user context → query vector).
        // This runs in the current coroutine (not async) — it's fast and must complete
        // before we can start ANN search.
        val queryVector = productEncoder.encodeQuery(request.userId, request.contextItemId)

        // LESSON: These two async blocks start IMMEDIATELY and run concurrently.
        // We declare both before awaiting either — this is critical for parallelism.
        // If you awaited featsDeferred before declaring metaDeferred, they'd run sequentially.

        // Step 2a: ANN candidate retrieval
        val candidatesDeferred = async {
            val candidates = annService.search(
                queryVector = queryVector,
                topK = request.limit * 3, // over-fetch for reranking
                excludeIds = setOf(request.contextItemId), // don't recommend the context item
            )
            log.debug("ANN search returned {} candidates", candidates.size)
            candidates
        }

        // Step 2b: Metadata enrichment (simulated feature store call)
        // LESSON: Launched concurrently with ANN search. In production these hit
        // different backend services — true I/O parallelism.
        val metadataDeferred = async {
            // We fetch metadata for the context item to inform reranking.
            val meta = featureClient.getItemFeatures(request.contextItemId)
            log.debug("Context item metadata: {}", meta)
            meta
        }

        // Step 2c: A/B variant metadata (third parallel call)
        val abMetaDeferred = async {
            // Simulate fetching experiment configuration for this variant.
            // In production: call an experiment service or read from a cached config.
            request.abVariant?.let { variant ->
                log.debug("A/B variant active: {}", variant)
                mapOf("ab_variant" to variant, "experiment_id" to "rec-exp-2024")
            } ?: emptyMap()
        }

        // Step 3: Await all parallel calls.
        // LESSON: .await() suspends until the Deferred has a result (or throws).
        // The total wall time is max(ANN_time, metadata_time, abMeta_time) — not the sum.
        val candidates = candidatesDeferred.await()
        val contextFeatures = metadataDeferred.await()
        val abMeta = abMetaDeferred.await()

        // Step 4: Fetch candidate features for reranking (sequential — depends on candidates)
        val candidateIds = candidates.map { it.itemId }
        val features = featureClient.getItemFeaturesBatch(candidateIds)

        // Step 5: Build reranker inputs
        val rankerInputs = candidates.map { item ->
            val itemFeatures = features[item.itemId]
            RankerInput(
                item = item,
                relevanceScore = calculateRelevance(queryVector, item.vector, itemFeatures),
                metadata = buildItemMetadata(itemFeatures),
            )
        }

        // Step 6: Rerank (CPU-bound, business rules + MMR)
        val reranked = reranker.rerank(rankerInputs, request.limit)

        // Step 7: Assemble response
        val latencyMs = System.currentTimeMillis() - startMs
        RecommendationResponse(
            items = reranked.map { output ->
                RecommendedItem(
                    itemId = output.item.itemId,
                    score = output.finalScore,
                    title = "Item ${output.item.itemId}", // stub title
                    metadata = output.metadata + abMeta,
                )
            },
            modelVersion = MODEL_VERSION,
            abVariant = request.abVariant,
            latencyMs = latencyMs,
            status = com.major.playground.recs.dto.RecsStatus.Ok,
        )
    }

    /** Builds a degraded fallback response from pre-computed trending items. */
    private fun buildFallbackResponse(request: RecommendationRequest, startMs: Long): RecommendationResponse {
        // LESSON: Fallback should still return SOMETHING useful.
        // Here we return a static list of "trending" items.
        // In production: serve from a pre-warmed cache (Redis / memcached).
        val trending = (1..request.limit).map { i ->
            RecommendedItem(
                itemId = "trending-${i.toString().padStart(3, '0')}",
                score = 1.0 - i * 0.05,
                title = "Trending item $i",
                metadata = mapOf("source" to "fallback_trending"),
            )
        }
        return RecommendationResponse(
            items = trending,
            modelVersion = "fallback-trending-v1",
            abVariant = request.abVariant,
            latencyMs = System.currentTimeMillis() - startMs,
            status = com.major.playground.recs.dto.RecsStatus.Fallback(
                reason = "primary model timeout",
                fallbackModel = "trending-v1",
            ),
        )
    }

    /** Simple relevance scoring: cosine similarity + feature-based boost. */
    private fun calculateRelevance(
        queryVector: FloatArray,
        itemVector: FloatArray,
        features: ItemFeatures?,
    ): Double {
        // Base: dot product (approximation of cosine similarity)
        var score = queryVector.zip(itemVector.toList()).sumOf { (q, i) -> (q * i).toDouble() }
        // Boost highly-rated in-stock items
        if (features != null) {
            if (features.inStock) score += 0.05
            score += features.rating * 0.01
        }
        return score.coerceIn(-1.0, 1.0)
    }

    private fun buildItemMetadata(features: ItemFeatures?): Map<String, String> =
        if (features == null) emptyMap()
        else mapOf(
            "category" to features.category,
            "in_stock" to features.inStock.toString(),
            "price" to features.price.toString(),
        )

    /**
     * Health check endpoint for the recommendations service.
     *
     * LESSON: Every service should expose a health check. Spring Actuator provides
     * /actuator/health automatically, but a custom domain check is useful for
     * smoke testing (is the model loaded? is the ANN index warm?).
     */
    @GetMapping("/health")
    suspend fun health(): Map<String, String> =
        mapOf(
            "status" to "ok",
            "model_version" to MODEL_VERSION,
            "index_size" to AnnService.INDEX_SIZE.toString(),
        )
}
