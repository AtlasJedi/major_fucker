/**
 * REQUIREMENTS this file demonstrates:
 *   - Post-ANN reranking with diversity and business rule constraints
 *   - Maximal Marginal Relevance (MMR) diversity algorithm stub
 *   - Idiomatic Kotlin: data classes, extension functions, `sortedWith`, `compareBy`
 *
 * LESSONS embedded:
 *   - Why pure ANN score ranking is not enough: diversity, freshness, business rules
 *   - MMR: balances relevance (ANN score) with diversity (penalises similar-to-already-selected)
 *   - `sortedWith(compareBy { ... })` vs `sortedBy { ... }` — multi-key sorting
 *   - `fold` accumulator pattern for building a result list
 *
 * RELATED DRILL TOPICS: recsys, kotlin_basics
 */
package com.major.playground.recs

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Input to the reranker: an ANN candidate with its raw relevance score and metadata. */
data class RankerInput(
    val item: IndexedItem,
    val relevanceScore: Double,
    val metadata: Map<String, String> = emptyMap(),
)

/** Output of reranking: the final ordered list with a composite score. */
data class RankerOutput(
    val item: IndexedItem,
    val finalScore: Double,
    val metadata: Map<String, String>,
)

/**
 * Reranker service.
 *
 * Sits between ANN retrieval and the final response.
 *
 * Production reranking pipeline at recommendation systems typically:
 *   1. Applies a lightweight "L2" ranking model (gradient boosted trees or a small NN)
 *   2. Enforces diversity (avoid 10 identical items)
 *   3. Applies business rules (boost sponsored, suppress out-of-stock, enforce category caps)
 *   4. Applies personalisation signals (user's past behaviour, location, device)
 *
 * This stub implements a simplified MMR + business rule demo.
 */
@Service
class Reranker {

    private val log = LoggerFactory.getLogger(Reranker::class.java)

    companion object {
        // LESSON: Lambda parameter = diversity weight in MMR.
        // lambda=1.0 → pure relevance ranking (same as ANN order).
        // lambda=0.0 → pure diversity (maximise spread, ignore relevance).
        // Typical production value: 0.6–0.8 (lean toward relevance but diversify).
        private const val MMR_LAMBDA = 0.7
    }

    /**
     * Reranks [candidates] using Maximal Marginal Relevance.
     *
     * LESSON: MMR algorithm (Carbonell & Goldstein, 1998):
     *   Selected = []
     *   While |Selected| < k:
     *     next = argmax_i [lambda * relevance(i) - (1-lambda) * max_j_in_selected sim(i, j)]
     *     Selected.append(next)
     *
     * Intuition: at each step, pick the item that is BOTH relevant AND dissimilar
     * to already-selected items.
     *
     * Here "similarity" = dot product of embedding vectors (cheap approximation).
     */
    fun rerank(candidates: List<RankerInput>, topK: Int): List<RankerOutput> {
        if (candidates.isEmpty()) return emptyList()

        log.debug("Reranking {} candidates, topK={}", candidates.size, topK)

        val selected = mutableListOf<RankerInput>()
        val remaining = candidates.toMutableList()

        repeat(minOf(topK, candidates.size)) {
            val next = remaining.maxByOrNull { candidate ->
                val relevance = candidate.relevanceScore
                // LESSON: Max similarity to any already-selected item.
                // If nothing is selected yet, diversity penalty is 0.
                val maxSimilarityToSelected = if (selected.isEmpty()) {
                    0.0
                } else {
                    selected.maxOf { s ->
                        dotProduct(candidate.item.vector, s.item.vector)
                    }
                }
                // MMR score: balance relevance and diversity
                MMR_LAMBDA * relevance - (1.0 - MMR_LAMBDA) * maxSimilarityToSelected
            } ?: return@repeat

            selected.add(next)
            remaining.remove(next)
        }

        // LESSON: `mapIndexed` gives us both the index and the element — useful for
        // computing position-based scores or rank-based features.
        return selected.mapIndexed { rank, input ->
            // Attach rank as metadata so the consumer can log/audit it.
            val enrichedMeta = input.metadata + mapOf("rerank_position" to rank.toString())
            RankerOutput(
                item = input.item,
                // LESSON: Final score blends ANN relevance with a small position-decay bonus.
                // In production this would be the output of a proper L2 model.
                finalScore = input.relevanceScore * (1.0 - rank * 0.02),
                metadata = enrichedMeta,
            )
        }
    }

    /** Dot product of two equal-length float arrays. */
    private fun dotProduct(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
