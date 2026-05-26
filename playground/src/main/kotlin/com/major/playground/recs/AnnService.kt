/**
 * REQUIREMENTS this file demonstrates:
 *   - In-memory ANN (Approximate Nearest Neighbour) stub for the recommendations pipeline
 *   - Deterministic brute-force top-K cosine similarity search
 *   - Coroutine-friendly service (suspend function, Dispatchers.Default for CPU work)
 *
 * LESSONS embedded:
 *   - Real Faiss uses IVF (Inverted File Index) or HNSW for sub-linear search; this is O(n*d)
 *   - Cosine similarity = dot product of unit vectors — numerically stable when normalised
 *   - CPU-bound work belongs on Dispatchers.Default, not Dispatchers.IO (which is for blocking I/O)
 *   - `withContext(Dispatchers.Default)` switches the coroutine to the CPU-optimised pool
 *
 * RELATED DRILL TOPICS: recsys, coroutines_webflux
 */
package com.major.playground.recs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.sqrt

/** A single item in the index: its ID and its embedding vector. */
data class IndexedItem(val itemId: String, val vector: FloatArray)

/**
 * ANN service stub.
 *
 * In production this would be a gRPC client calling a Faiss server (or a Milvus cluster).
 * Here we maintain a small in-memory index with 16-dimensional float vectors and search
 * it with brute-force cosine similarity. The dataset is deterministic so tests don't flake.
 *
 * LESSON: Real Faiss HNSW works like this conceptually — but navigates a graph of
 * "entry points" rather than comparing all vectors. Complexity: O(log n) per query vs O(n*d).
 * The accuracy/speed trade-off is controlled by the `ef` (exploration factor) parameter.
 */
@Service
class AnnService {

    private val log = LoggerFactory.getLogger(AnnService::class.java)

    companion object {
        const val VECTOR_DIM = 16
        const val INDEX_SIZE = 200
    }

    // LESSON: `lazy` initialises the value on first access (thread-safe by default).
    // The index is heavy to build; we don't want to do it at construction time
    // (it would slow Spring context startup and complicate tests).
    private val index: List<IndexedItem> by lazy { buildDeterministicIndex() }

    /**
     * Returns the top-K items most similar to [queryVector].
     *
     * @param queryVector  the encoded query item's embedding
     * @param topK         how many neighbours to return
     * @param excludeIds   items to skip (e.g., the query item itself)
     *
     * LESSON: `suspend` + `withContext(Dispatchers.Default)`:
     *   - The function is called from a coroutine on the IO/WebFlux dispatcher
     *   - CPU-intensive work (the dot product loop) should NOT run on the IO pool
     *     because that pool is sized for blocking I/O, not compute
     *   - withContext switches execution to Default (sized = number of CPU cores)
     *   - After withContext, execution returns to the original dispatcher automatically
     */
    suspend fun search(queryVector: FloatArray, topK: Int, excludeIds: Set<String> = emptySet()): List<IndexedItem> =
        withContext(Dispatchers.Default) {
            log.debug("ANN search: topK={}, excludeIds={}", topK, excludeIds.size)

            index
                .filter { it.itemId !in excludeIds }
                .map { item -> item to cosineSimilarity(queryVector, item.vector) }
                // LESSON: `sortedByDescending` is stable — equal scores preserve original order.
                .sortedByDescending { (_, score) -> score }
                .take(topK)
                .map { (item, _) -> item }
        }

    /**
     * Cosine similarity between two vectors.
     *
     * LESSON: cos(θ) = (A · B) / (|A| * |B|)
     * When vectors are already unit-normalised (||v|| = 1), this reduces to the dot product.
     * We normalise here for correctness regardless of input magnitude.
     *
     * Returns values in [-1.0, 1.0]. For recommendation scoring, all values should be > 0
     * if embeddings were trained with a proper similarity loss (contrastive, triplet, etc.).
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} != ${b.size}" }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        // LESSON: Guard against zero-vector division — would give NaN without this check.
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }

    /**
     * Builds a deterministic in-memory index.
     * Seed = itemIndex, so item-0's vector is always the same across JVM restarts.
     * This makes tests stable — no need to seed a Random explicitly in tests.
     *
     * LESSON: In production you'd load pre-computed embeddings from a feature store
     * (e.g., Redis, a parquet file on S3, or a Milvus collection).
     */
    private fun buildDeterministicIndex(): List<IndexedItem> {
        return (0 until INDEX_SIZE).map { i ->
            val vector = FloatArray(VECTOR_DIM) { j ->
                // Deterministic: value is a function of item index and dimension index only.
                // sin/cos ensures values are bounded in [-1, 1] and varied across dimensions.
                kotlin.math.sin((i * VECTOR_DIM + j).toDouble()).toFloat()
            }
            IndexedItem(itemId = "item-${i.toString().padStart(4, '0')}", vector = vector)
        }.also {
            log.info("Built ANN index: {} items, {} dimensions each", it.size, VECTOR_DIM)
        }
    }
}
