/**
 * REQUIREMENTS this file demonstrates:
 *   - Two-tower encoder stub: maps item ID → embedding vector deterministically
 *   - Demonstrates where in the recs pipeline encoding fits
 *   - Shows how to make a stub "real enough" to be testable without ML inference
 *
 * LESSONS embedded:
 *   - Two-tower model: user tower + item tower trained jointly; dot-product = relevance score
 *   - In production the encoder call is a gRPC/REST call to a TF Serving or TorchServe replica
 *   - Deterministic hash-based stub: no randomness, tests never flake
 *   - Extension function on String for cleaner API
 *
 * RELATED DRILL TOPICS: recsys, kotlin_basics
 */
package com.major.playground.recs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Stub two-tower product encoder.
 *
 * LESSON: A two-tower model has two sub-networks:
 *   User tower:    userId + context features  → user embedding  (e.g. 128-dim)
 *   Item tower:    itemId + item features      → item embedding  (e.g. 128-dim)
 *
 * At serving time, item embeddings are pre-computed and stored in the ANN index.
 * Only the user/query tower runs at request time.
 *
 * Here we skip the actual neural network and use a deterministic hash function to
 * produce pseudo-embeddings. The shape of the pipeline is identical to production.
 */
@Service
class ProductEncoder {

    private val log = LoggerFactory.getLogger(ProductEncoder::class.java)

    /**
     * Encodes a product (item) ID into a vector in the embedding space.
     *
     * LESSON: `suspend` + `withContext(Dispatchers.Default)`:
     * In real code this would be an async gRPC call (Dispatchers.IO).
     * Here we do CPU hashing — still correct to keep off the reactor event loop.
     */
    suspend fun encodeItem(itemId: String): FloatArray =
        withContext(Dispatchers.Default) {
            log.debug("Encoding item: {}", itemId)
            itemId.deterministicVector(AnnService.VECTOR_DIM)
        }

    /**
     * Encodes a user context into a query vector.
     *
     * LESSON: The query vector is produced from the USER tower at request time.
     * It incorporates: user ID, session features, page context, recent interactions.
     * For this stub we just hash userId + contextItemId together.
     */
    suspend fun encodeQuery(userId: String, contextItemId: String): FloatArray =
        withContext(Dispatchers.Default) {
            log.debug("Encoding query: userId={}, contextItemId={}", userId, contextItemId)
            // LESSON: Concatenating the two inputs before hashing creates a combined signal.
            // In a real two-tower model the user tower takes many more features as input.
            "$userId:$contextItemId".deterministicVector(AnnService.VECTOR_DIM)
        }
}

// LESSON: Extension function — adds `deterministicVector` to String without subclassing it.
// This is a pure utility that belongs conceptually to the encoding logic.
// Kotlin extension functions are compiled to static methods; no virtual dispatch overhead.
private fun String.deterministicVector(dim: Int): FloatArray {
    // Use hashCode as a seed — deterministic per input, fast, no external dependency.
    val seed = this.hashCode().toLong()
    return FloatArray(dim) { i ->
        // Mix seed with dimension index to vary values across dimensions.
        val mixed = (seed * 6364136223846793005L + i * 1442695040888963407L) ushr 1
        // Normalise to [-1.0, 1.0]
        (mixed.toFloat() / Long.MAX_VALUE.toFloat()).coerceIn(-1.0f, 1.0f)
    }
}
