> Pre-built topic bank. Your /onboard will generate personalized content for your target role.

# Recsys — bank pytań

> Covers recommendation system fundamentals with a focus on the **serving/engineering path** — not model training from scratch. Context: Allegro's production Two-Tower DLRM system as described in arxiv 2508.03702 ("Suggest, Complement, Inspire: Story of Two Tower Recommendations at Allegro.com"). You will be on the **recommendations system team as a Kotlin Regular Developer**, meaning your job is to build and operate the infrastructure that encodes requests, calls Faiss, ranks results, logs telemetry, and keeps p99 latency under 40ms at ~20k RPS. ML literacy here means speaking the language of data scientists and understanding the system's constraints — not training models.

## Zakres

- Two-stage recommendation architecture: retrieval (candidate generation) vs ranking (re-ranking)
- Two-Tower DLRM: query tower + item tower, dot product, weight tying, L2 normalization
- Allegro's three variants: Similarity-TT, Complementary-TT, Inspirational-TT
- Content-based encoding: features → embeddings → MLP → L2-normalize (cold-start solution)
- Approximate nearest neighbor (ANN): Faiss, IVF / HNSW / Flat index types
- Serving path: feature fetch → encode → ANN query → rerank → telemetry, Kotlin coroutines
- Negative sampling: in-batch negatives, mixed negative sampling, why it matters for training signal
- Offline eval metrics: Recall@K, Precision@K, NDCG
- Online eval: A/B testing strategy, business metrics (CTR, GMV/visit, CTA, CR, bounce, exit), SRM
- Fallback strategies: index unavailability, cold-start, degraded-mode serving
- Production MLOps: daily index refresh, blue/green swap, concept drift detection
- Cold-start problem and content-based mitigation

---

## Q-REC-001 [bloom: recall]
**Pytanie:** What is a recommendation system at a high level? Explain the two-stage architecture: retrieval vs ranking.
**Modelowa odpowiedź:** A recommendation system selects items from a large catalog that are most relevant to a user or context at a given moment. At scale, doing this in a single step over millions of items is too slow, so systems are split into two stages.

**Retrieval (candidate generation):** fast, coarse-grained. Narrows millions of items down to hundreds of candidates. Must be fast — ANN on dense embeddings, BM25 on text, or rules-based filters. Recall is the priority; it's OK to include some irrelevant items as long as relevant ones are not missed.

**Ranking (re-ranking):** slower, fine-grained. Takes the ~100-500 candidates from retrieval and scores each with a heavier model (cross-encoder, gradient boosted trees, feature-rich DNN) that can consider interaction features between query and item. Precision is the priority here.

At Allegro, the Two-Tower model (arxiv 2508.03702) serves the retrieval stage. It encodes query and item into dense vectors, then Faiss ANN finds nearest neighbors efficiently at ~20k RPS, p99 ~40ms. A downstream ranker then re-scores the returned candidates before final presentation.
**Pułapka rozmowna:** Recruiters often ask "why not just run the full ranker over all items?" The answer is computational: a cross-encoder that jointly encodes a (query, item) pair is O(items) and takes milliseconds per item — at 10M items, that's hours per request. Two-tower pre-computes item embeddings offline, reducing retrieval to a single vector lookup + ANN.
**Tagi:** architecture, retrieval, ranking, two-stage

---

## Q-REC-002 [bloom: recall]
**Pytanie:** What is candidate generation and what is re-ranking? What does each layer do and what does it optimize?
**Modelowa odpowiedź:** **Candidate generation** (retrieval layer): takes the incoming context (user query, viewed item, session) and retrieves a small set of plausible candidates from the full catalog. Optimizes for **recall** — do not miss relevant items. Techniques: ANN on embeddings, inverted indexes, collaborative filtering, popularity rules. The output is typically 100–1000 items.

**Re-ranking**: takes those candidates and applies a heavier scoring model that considers richer features — personalization signals, business rules (margin, stock, seller quality), diversity constraints, and contextual factors. Optimizes for **precision** and business KPIs (GMV, CTR, CR). This layer can also apply business logic: filter out-of-stock items, boost promoted listings, apply diversity rules.

The split exists because: (a) the retrieval model must be fast enough to query millions of items in milliseconds, so it uses a simplified scoring function (dot product of L2-normalized vectors); (b) the ranker can be expensive but only sees a small candidate set, so it can use more expressive feature crosses.

At Allegro, after Two-Tower retrieval via Faiss, a downstream ranking layer (not detailed in the public paper) applies before final delivery.
**Pułapka rozmowna:** "Can't you just use one model?" Yes, for small catalogs. At Allegro's catalog scale (millions of active listings with daily churn), the single-model approach breaks on latency and compute budget. The answer is always tied to scale.
**Tagi:** candidate-generation, reranking, architecture, scale

---

## Q-REC-003 [bloom: recall]
**Pytanie:** What is an embedding? Define it, describe its key properties, and explain why it's useful for recommendations.
**Modelowa odpowiedź:** An **embedding** is a dense, fixed-dimensional vector representation of a discrete object (item, user, query). Key properties:

1. **Fixed dimensionality** — regardless of input complexity, output is a vector of d floats (e.g., d=128 or d=256).
2. **Semantic proximity** — items with similar meaning or behavior are close in embedding space (typically measured by cosine similarity or dot product after L2 normalization).
3. **Learned from data** — the mapping is learned by a neural network trained to produce embeddings that are useful for a downstream task (e.g., "items co-viewed by the same users should be close").

In recommendations: instead of exact ID matching, you can retrieve items that are semantically similar to a query embedding, even if no exact match exists. This enables **generalization** — an embedding for a new product (cold start) can be computed from its features without needing historical interactions.

At Allegro (arxiv 2508.03702), product embeddings are computed from features (title, price, category, seller) → embedding tables → concat → MLP → L2-normalize → dense vector. All three Two-Tower variants (Similarity, Complementary, Inspirational) share this content-based encoding approach.
**Pułapka rozmowna:** "What's the difference between an embedding and a feature vector?" They're both vectors, but embeddings are *learned* dense representations optimized for a task. A hand-crafted feature vector (price, category_id, brand_id as raw numbers) is not an embedding. The learning is what produces the semantic proximity property.
**Tagi:** embedding, representation-learning, dense-vector

---

## Q-REC-004 [bloom: recall]
**Pytanie:** What is Approximate Nearest Neighbor (ANN) search and why use it instead of exact nearest neighbor search?
**Modelowa odpowiedź:** **Exact nearest neighbor (exact NN)** finds the mathematically closest vectors to a query by comparing against every vector in the index. For d=128 and N=10M items: 10M dot products per query. At 20k RPS that's 200 billion operations per second — infeasible on CPU.

**Approximate nearest neighbor (ANN)** trades a small, controlled loss in recall for orders-of-magnitude speedup. It uses data structures (graphs, inverted files, tree partitions) to prune the search space so only a fraction of vectors are compared. The result is "probably" the nearest neighbors — configurable precision/recall trade-off.

Why this matters for Allegro's serving path: they serve ~20k RPS at p99 ~40ms on CPU (NVIDIA T4 used only for training, not serving). Exact search over their full item catalog at that throughput would require GPU clusters just for dot products. ANN with Faiss on CPU achieves the target SLA.

The recall loss from ANN is typically small (1-5%) and is acceptable because: (a) downstream ranker corrects ordering of candidates, (b) users don't notice if #47 vs #52 is slightly off.
**Pułapka rozmowna:** "Why not just use exact search with enough CPUs?" You can, for small catalogs. But ANN is not just about speed — it's about serving latency *per request*, not throughput. You cannot parallelize a single request across 1000 CPUs to reduce p99. ANN reduces the work per single query.
**Tagi:** ann, faiss, nearest-neighbor, latency

---

## Q-REC-005 [bloom: recall]
**Pytanie:** What is Faiss? Compare IVF, HNSW, and Flat index types — when would you use each?
**Modelowa odpowiedź:** **Faiss** (Facebook AI Similarity Search) is an open-source library (C++/Python) for efficient similarity search over dense vectors. Supports billion-scale indexes, CPU and GPU, multiple index types.

**Flat (IndexFlatL2 / IndexFlatIP):** brute-force exact search. No approximation. Slowest at query time, fastest to build. Use when: catalog is small (<1M items), or you need ground truth for evaluation, or index rebuild is trivial. Faiss Flat is still faster than naive Python loops due to BLAS-optimized batched matrix multiply.

**IVF (Inverted File Index — IndexIVFFlat, IndexIVFPQ):** divides the embedding space into k Voronoi cells (via k-means). At query time, searches only the nprobe closest cells. Trade-off: nprobe controls recall vs speed. PQ variant compresses vectors to save RAM. Use when: medium-to-large catalogs, RAM is constrained, recall@k ~90%+ is acceptable with tuned nprobe. Requires training the k-means centroids.

**HNSW (Hierarchical Navigable Small World — IndexHNSW):** graph-based. Builds a multi-layer proximity graph; query traverses from top (coarse) to bottom (fine) layer. Very fast queries, excellent recall, but higher RAM usage and slower build. Use when: query latency is critical, RAM budget is available, online (real-time) insert is needed (HNSW supports add without full rebuild; IVF does not).

Allegro's paper mentions Faiss ANN with daily offline rebuild + blue/green swap — consistent with IVF or HNSW on a static index.
**Pułapka rozmowna:** "HNSW is always better." Wrong — HNSW uses ~4-6x more RAM than IVF+PQ for the same corpus. At 10M items × 128 dims × float32, Flat = 5GB, HNSW = 20-30GB. For Allegro's catalog scale, the RAM budget and rebuild cost matter as much as query latency.
**Tagi:** faiss, ivf, hnsw, ann, index

---

## Q-REC-006 [bloom: recall]
**Pytanie:** Define the Two-Tower architecture. What are the query tower and item tower? How is the final score computed?
**Modelowa odpowiedź:** A **Two-Tower model** has two independent neural networks (towers) that independently encode two objects into the same embedding space:

- **Query tower** (also: user tower): encodes the request context — the user, the viewed item, the search query, or any contextual signal.
- **Item tower** (also: candidate tower): encodes each candidate item from the catalog.

Both towers output a fixed-d vector, L2-normalized to the unit sphere. **Similarity score = dot product** of query embedding and item embedding (equivalent to cosine similarity after L2 normalization, range [-1, 1]).

**Why two towers?** Item embeddings can be pre-computed offline for the entire catalog and stored in the ANN index. At serving time, only the query tower runs live (single forward pass for one query). The expensive O(N × d) matrix multiply is replaced by one ANN lookup. This is the architectural choice that makes the system scalable.

At Allegro (arxiv 2508.03702), the towers share the same **Product Encoder** architecture (weight tying). For Similarity-TT: query tower = product encoder applied to the viewed item. For Complementary-TT: query tower has a modified head that maps the viewed item's category to a cross-category embedding space. For Inspirational-TT: a hierarchical ANN index is layered on top.
**Pułapka rozmowna:** "The user tower encodes the user profile, right?" Not necessarily. At Allegro, the query tower encodes a *product* (the item the user is currently viewing) — making this a item-to-item similarity model, not a user-to-item personalization model. The distinction matters for cold-start and for what features are available at serving time.
**Tagi:** two-tower, dlrm, architecture, dot-product

---

## Q-REC-007 [bloom: recall]
**Pytanie:** Define content-based filtering vs collaborative filtering. When would you use each?
**Modelowa odpowiedź:** **Collaborative filtering (CF):** recommendations based on behavioral patterns of many users — "users who interacted with item A also interacted with item B." Requires historical interaction data (clicks, purchases, views). Works well when: you have dense interaction data, you can tolerate cold-start issues, you want to capture latent preferences not visible in item features.

Problems: **cold-start** — new items with no interactions cannot be recommended; new users with no history get no personalized recs. Also vulnerable to popularity bias (popular items dominate).

**Content-based filtering:** recommendations based on item/user features — "this item is similar to items you've interacted with because it has similar features (title, category, price, seller)." Does not require interaction history for new items. Works well when: catalog changes rapidly (new products daily), items have rich features, cold-start is a hard constraint.

Problems: limited serendipity (filter bubble — you only see items similar to what you've already seen); feature engineering effort; less able to capture non-obvious associations.

**At Allegro (arxiv 2508.03702):** the Two-Tower model is **content-based**: product embeddings are computed from features (title, price, category, seller), not from ID-lookup tables. This is a deliberate choice to handle catalog churn (marketplace with millions of dynamic listings) and cold-start. Training signal uses co-view/co-purchase pairs (behavioral data), but serving inference depends only on product features — no interaction history needed per item.
**Pułapka rozmowna:** "Content-based is worse than CF because it misses behavioral signals." Allegro's paper shows the opposite trade-off is sometimes better: their content-based approach handles cold-start *and* achieves competitive business metrics. "Better" depends on catalog churn rate and cold-start severity.
**Tagi:** content-based, collaborative-filtering, cold-start, catalog

---

## Q-REC-008 [bloom: recall]
**Pytanie:** What is the cold-start problem in recommendations? What are the standard mitigations?
**Modelowa odpowiedź:** **Cold-start** refers to the inability to generate good recommendations for items or users with no (or very little) historical interaction data.

**Item cold-start:** a new product has zero views, zero purchases — no behavioral signal. CF systems simply cannot recommend it, or can only show it via popularity rules.

**User cold-start:** a new user has no interaction history. Personalization is impossible; only generic popular items can be shown.

Standard mitigations:

1. **Content-based encoding** (Allegro's approach): compute item embedding from features (title, price, category) at listing time. No interactions required. New items are immediately searchable in the ANN index after daily rebuild.

2. **Popularity fallback:** show globally or category-popular items when no signal is available. Simple and effective as a floor.

3. **Onboarding questionnaire:** ask new users for explicit preferences to bootstrap a user profile.

4. **Transfer learning / pretrained embeddings:** use text embeddings (e.g., from a product title encoder pretrained on the full catalog) to initialize item representations.

5. **Hybrid models:** combine CF and content signals so that new items get content-based scores and established items get CF boost.

At Allegro (arxiv 2508.03702): content-based encoding **fully solves** item cold-start. A new listing is encoded from its features; after the next daily index refresh, it's available for ANN retrieval. The trade-off is that the encoding quality depends on feature richness and the model's ability to generalize from features.
**Pułapka rozmowna:** "With daily index refresh, there's still a 24-hour cold-start window even with content-based encoding." True — and important. An item listed at 9am won't appear in recommendations until the next midnight refresh. For flash sales or new listings that need immediate visibility, a real-time fallback (text search, explicit boosting) is needed alongside the ANN index.
**Tagi:** cold-start, content-based, fallback, catalog

---

## Q-REC-009 [bloom: understand]
**Pytanie:** Why would you tie weights between the query tower and item tower (shared encoder)? When does it make sense — and when doesn't it?
**Modelowa odpowiedź:** **Weight tying** means the query tower and item tower share the same neural network parameters — a single "Product Encoder" that maps any product's features to an embedding. The query embedding and item embedding are both produced by the same function.

**When it makes sense:**
- The query and item are **the same type of object** — at Allegro (Similarity-TT, Complementary-TT), both query and candidate are products. Encoding a "query product" and a "candidate product" should use the same transformation since they live in the same semantic space.
- It **reduces parameter count** (half the parameters vs independent towers), reducing overfitting risk when training data is limited.
- It **guarantees symmetric embedding space** — the dot product is symmetric, so similarity(A, B) = similarity(B, A), which is desirable for item-to-item similarity.
- It simplifies **serving infrastructure** — one model to deploy, version, and monitor.

**When it doesn't make sense:**
- Query and item are **heterogeneous objects** — e.g., a user profile (demographics, history) vs a product (title, price, image). These have different feature schemas; a shared encoder makes no structural sense.
- The query requires **asymmetric transformation** — Allegro's Complementary-TT modifies the query tower head with a category mapping to shift the embedding from "similarity" space to "complementary" space. This breaks full weight tying (shared body, different head).
- When interaction features (query × item crosses) are needed — which requires a cross-encoder, not two towers.

The key insight: weight tying is elegant when query and item are the same entity type. It breaks down when the encoding context is fundamentally different.
**Pułapka rozmowna:** "Weight tying means the query and item towers are identical, so why bother with two towers at all?" Because they still receive different *inputs* at different *times* — item embeddings are precomputed offline; query embeddings are computed live at serving time. The "two towers" distinction is about the serving-time computation graph, not necessarily about having different weights.
**Tagi:** weight-tying, two-tower, architecture, shared-encoder

---

## Q-REC-010 [bloom: understand]
**Pytanie:** What is negative sampling in Two-Tower training? What are "in-batch negatives" and "mixed negative sampling"? Why are they needed?
**Modelowa odpowiedź:** Two-Tower models are trained to push the dot product of (query, positive item) higher than (query, negative item). **Negative examples** — items that are irrelevant to the query — are necessary to prevent the model from collapsing (mapping everything to the same embedding).

**Why not random negatives?** If negatives are random items from the catalog, the model learns to distinguish the positive from random noise, which is too easy. It doesn't learn to distinguish between the positive and hard-to-distinguish items (items that are similar but not the right one).

**In-batch negatives:** during training, for each (query, positive_item) pair in a batch, every *other* item in the batch serves as a negative. Efficient: no extra computation; negatives come "for free" from the batch. Drawback: if the batch contains other items that are *also* positives for this query (they happened to co-occur), treating them as negatives injects false negatives into training — a known issue called **false negative contamination**.

**Mixed negative sampling (Allegro's approach, arxiv 2508.03702):** combines in-batch negatives (cheap, somewhat hard) with **randomly sampled negatives** from the full catalog (to provide diverse global negatives and counter popularity bias). Mixing the two sources produces a better-calibrated training signal.

**Sampled softmax:** the training objective. Instead of computing a softmax over all N items (expensive), compute it over the positive + a sampled subset of negatives. Equivalent to noise-contrastive estimation (NCE). Allegro uses this with NVIDIA T4 GPU during training.

You as a serving engineer will not implement this — but you need to know it exists because: (a) it affects what the embeddings optimize for, and (b) when debugging quality issues, a data scientist will talk about "hard negatives" and "false negatives."
**Pułapka rozmowna:** "Can't we use all catalog items as negatives?" Technically yes (full softmax), but at Allegro's catalog scale (millions of items), the full softmax is computationally prohibitive per training step. Sampled softmax achieves nearly the same result with a small sample.
**Tagi:** negative-sampling, in-batch-negatives, training, softmax

---

## Q-REC-011 [bloom: understand]
**Pytanie:** Define Recall@K, Precision@K, and NDCG. When would you use each as an offline eval metric for a retrieval model?
**Modelowa odpowiedź:** These are ranking metrics computed on a held-out test set where you know the "ground truth" relevant items (e.g., items the user actually purchased).

**Recall@K:** of all relevant items for a query, what fraction did the model retrieve in its top-K? `Recall@K = |relevant ∩ top_K| / |relevant|`. Ranges [0, 1]. Use when: the retrieval stage must not miss relevant items. For Allegro's Two-Tower retrieval, Recall@K is the primary offline metric — if the relevant item isn't in the top-500 candidates, the downstream ranker has no chance to surface it.

**Precision@K:** of the top-K items returned, what fraction are relevant? `Precision@K = |relevant ∩ top_K| / K`. Use when: you care about the density of relevant items in the candidate set — relevant for a re-ranker that has limited capacity to process K items.

**NDCG (Normalized Discounted Cumulative Gain):** measures ranking quality, accounting for position. Items ranked higher contribute more to the score (log discount). Handles graded relevance (not just binary). Use when: you have graded relevance (purchased > added to cart > clicked), and position of the relevant item within top-K matters. More appropriate for the full ranking stage (retrieval + ranker combined) than for raw retrieval.

**Rule of thumb:**
- Retrieval / candidate generation → Recall@K (did we find it at all?)
- Re-ranker quality → NDCG (did we rank it in the right position?)
- Business presentations → A/B on CTR/GMV (offline metrics are proxies; always validate online)
**Pułapka rozmowna:** "NDCG is always better because it captures ordering." For the retrieval stage, ordering within top-500 candidates doesn't matter — the downstream ranker will reorder them. Optimizing NDCG for retrieval is over-engineering. Recall@K is the right metric because retrieval is a binary gate: item is in or out of the candidate set.
**Tagi:** recall-at-k, precision-at-k, ndcg, offline-eval, metrics

---

## Q-REC-012 [bloom: understand]
**Pytanie:** Allegro runs A/B tests measuring CTR, GMV/visit, CTA, CR, bounce rate, and exit rate — not just CTR. Why? What's the risk of optimizing only for CTR?
**Modelowa odpowiedź:** **CTR (Click-Through Rate)** measures how often users click a recommendation. It's easy to inflate: surface clickbait items, irrelevant-but-intriguing items, low-priced items with misleading titles. A model that maximizes CTR alone will recommend items users click but don't buy.

**Business metrics that matter beyond CTR:**

- **GMV/visit (Gross Merchandise Value per visit):** does the recommendation lead to revenue? An item clicked but not purchased is wasted attention. This is the primary business KPI at an e-commerce company.
- **CR (Conversion Rate):** clicks that result in purchase. Complementary to GMV; identifies whether recommendations are purchase-intent-aligned.
- **CTA (Call to Action rate):** intermediate step — add to cart, save for later. Leading indicator of purchase intent.
- **Bounce rate / Exit rate:** does the recommendation cause users to leave the site? A recommendation that sends users to a dead end (item out of stock, irrelevant) increases bounce. Recommendations should increase session depth.

**Why measure all of them?** Metrics are often in tension. A re-ranker that boosts CTR by surfacing cheap clickbait may decrease CR and GMV. A recommendation that increases "add to cart" but not purchase may inflate CTA without GMV lift. Measuring all signals allows detecting **metric gaming** and understanding the full effect.

Allegro (arxiv 2508.03702): 2-year continuous A/B at α=0.01, split by desktop vs app, measuring all these metrics. The high bar (α=0.01, not 0.05) reflects the cost of false positives on a system at this scale.
**Pułapka rozmowna:** "CTR is the most important metric." At an e-commerce company, GMV is the ground truth. CTR is a leading indicator, not the goal. If a recruiter at Allegro hears you say "we optimized for CTR," the follow-up will be "and what happened to GMV?" — be ready.
**Tagi:** a-b-testing, metrics, ctر, gmv, business-metrics, online-eval

---

## Q-REC-013 [bloom: apply]
**Pytanie:** Design the full request path for serving recommendations at ~20k RPS with p99 ~40ms. Be concrete: feature fetch, encode, ANN query, rerank, telemetry. Use Kotlin coroutines + WebClient.
**Modelowa odpowiedź:** At 20k RPS, p99 40ms budget is tight. Every step must be parallelized where possible and bounded by timeouts.

**Budget allocation (approximate):**
- Feature fetch: 5ms (Redis/in-process cache)
- Encode (query tower inference): 3ms (local JVM model via ONNX Runtime or TorchServe call)
- Faiss ANN query: 8ms (in-process or Faiss-as-a-service with 15ms timeout)
- Rerank (scoring, filtering, business rules): 3ms (in-process, CPU)
- Telemetry (async, non-blocking): 0ms (fire-and-forget Kafka publish)
- Overhead/serialization: ~5ms
- Total: ~24ms expected, 40ms p99 budget

```kotlin
// WebFlux handler, Reactor/Coroutines bridge
suspend fun getRecommendations(request: RecsRequest): RecsResponse {
    // Step 1: Fetch features for the query product
    // Use structured coroutine scope — NOT GlobalScope
    val features = withTimeout(10.millis) {
        featureStore.getProductFeatures(request.productId)  // Redis or local cache
    }

    // Step 2: Encode query via Product Encoder (ONNX Runtime — in-process, no network)
    val queryEmbedding: FloatArray = withTimeout(5.millis) {
        productEncoder.encode(features)  // ONNX inference, CPU, non-blocking
    }

    // Step 3: ANN retrieval — parallel with any auxiliary signals if needed
    val candidates = withTimeout(15.millis) {
        faissClient.search(queryEmbedding, topK = 500)
    }

    // Step 4: Fetch candidate features for reranking (batch)
    val candidateFeatures = withTimeout(10.millis) {
        featureStore.batchGetProductFeatures(candidates.ids)
    }

    // Step 5: Rerank (in-process scoring + business rules)
    val ranked = reranker.score(request, queryEmbedding, candidates, candidateFeatures)
        .filter { it.isInStock && it.id != request.productId }
        .take(request.limit)

    // Step 6: Telemetry — fire and forget, never on the critical path
    scope.launch(Dispatchers.IO) {  // structured scope, not GlobalScope
        telemetryPublisher.publishImpression(
            requestId = request.requestId,
            variantId = abTestContext.variantId,
            shownItemIds = ranked.map { it.id },
            queryProductId = request.productId,
            ts = Instant.now()
        )
    }

    return RecsResponse(ranked)
}
```

**Critical design choices:**
- `withTimeout` on every external call — if Faiss returns in 50ms, the whole p99 blows.
- `productEncoder.encode()` must be non-blocking (ONNX Runtime has a sync call but runs on CPU thread pool, wrap with `withContext(Dispatchers.Default)`).
- Telemetry is launched in a structured child coroutine (not `GlobalScope`) so it survives request completion but doesn't block it.
- Fallback logic (next question) is a separate concern — see Q-REC-014.
**Pułapka rozmowna:** "Just use `GlobalScope.async` for parallel steps." GlobalScope coroutines are not cancelled if the parent coroutine fails or the request times out. They leak. Always use a structured scope (`coroutineScope { }`, `CoroutineScope(job + dispatcher)`) or a lifecycle-bound scope.
**Tagi:** serving, coroutines, faiss, latency, kotlin, webflux

---

## Q-REC-014 [bloom: apply]
**Pytanie:** Implement a fallback strategy for when the Faiss ANN index is unavailable. Cover: popular items fallback, cached fallback, cohort default. Write in Kotlin.
**Modelowa odpowiedź:** A serving system must degrade gracefully. Three-tier fallback, each tier activating when the tier above fails:

```kotlin
suspend fun getRecommendationsWithFallback(request: RecsRequest): RecsResponse {
    return try {
        // Tier 1: Full path — Faiss ANN
        getRecommendationsFromANN(request)
    } catch (e: TimeoutCancellationException) {
        log.warn("Faiss timeout for productId=${request.productId}, falling back to cache")
        getFallback(request)
    } catch (e: FaissUnavailableException) {
        log.error("Faiss index unavailable, falling back", e)
        getFallback(request)
    }
}

private suspend fun getFallback(request: RecsRequest): RecsResponse {
    // Tier 2: Per-product cached recommendations (last successful ANN result, TTL=1h)
    val cached = recommendationCache.get(request.productId)
    if (cached != null && !cached.isExpired()) {
        metrics.increment("recs.fallback.cache_hit")
        return RecsResponse(cached.items)
    }

    // Tier 3: Category-level popular items (precomputed hourly, always available)
    val categoryId = productMetadataService.getCategoryId(request.productId)
    val popular = popularItemsStore.getByCategory(categoryId, limit = request.limit)
    if (popular.isNotEmpty()) {
        metrics.increment("recs.fallback.popular")
        return RecsResponse(popular)
    }

    // Tier 4: Global popular items (absolute last resort, always available)
    metrics.increment("recs.fallback.global_popular")
    return RecsResponse(popularItemsStore.getGlobal(limit = request.limit))
}
```

**Key engineering decisions:**
- Cache TTL must be tuned: too short → fallback is useless (cache expired); too long → stale during product catalog changes.
- Popular items are precomputed offline (never computed on the critical path) and stored in Redis or local in-memory with periodic refresh.
- Each fallback tier emits a metric tag — crucial for on-call visibility. If `recs.fallback.popular` spikes, the Faiss index is down.
- The fallback must **not** block indefinitely. `recommendationCache.get()` and `popularItemsStore.get()` must also have timeouts.
- Telemetry must record which fallback tier was used — A/B analysis that naively attributes conversions will be contaminated if some users received fallback recommendations.
**Pułapka rozmowna:** "Return an empty list if Faiss is down — it's safer than showing wrong items." An empty response breaks the UI and gives users zero value. A popular-items fallback is always better than an empty list. "Safer" in serving means "still useful," not "return nothing."
**Tagi:** fallback, resilience, faiss, kotlin, coroutines, cache

---

## Q-REC-015 [bloom: apply]
**Pytanie:** Design A/B test exposure logging for the recommendations system. How do you log impressions to Kafka so offline analysis can correctly attribute variant outcomes? What pitfalls must you avoid?
**Modelowa odpowiedź:** Correct A/B logging is the foundation of trustworthy experiments. A wrong impression log corrupts every metric computation.

**Impression event schema (Avro/JSON, published to Kafka topic `recs.impressions`):**

```kotlin
data class RecsImpressionEvent(
    val eventId: String,           // UUID, dedup key
    val ts: Instant,               // server-side timestamp
    val requestId: String,         // ties to the original request
    val userId: String,            // stable user identifier (hashed)
    val sessionId: String,         // for bounce/exit attribution
    val surface: String,           // "pdp_similar", "pdp_complementary", "homepage_inspirational"
    val variantId: String,         // A/B variant: "control" | "treatment_v2"
    val experimentId: String,      // e.g., "recs-two-tower-v2-2025-05"
    val queryProductId: String,
    val shownItemIds: List<String>, // ordered list — position matters for NDCG
    val fallbackTier: String?,      // null = full ANN; "cache" | "popular" | "global" if fallback
    val modelVersion: String,       // embedding model version — critical for debugging
    val indexTimestamp: Instant,    // which index build was used
    val deviceType: String          // "desktop" | "app" — Allegro splits A/B by device
)
```

**Critical pitfalls:**

1. **Sample Ratio Mismatch (SRM):** if the fraction of users in treatment != intended split (e.g., 50/50 becomes 48/52), the experiment is invalid. Cause: filtering impressions by downstream events (e.g., only log if the user clicked). Fix: log the exposure at the *moment of serving*, unconditionally, before the user acts.

2. **Logging fallback impressions as treatment impressions:** if Faiss is down and the user sees popular items, but the impression is logged as treatment variant, the treatment's CTR will be diluted by random popular items. Fix: include `fallbackTier` field; analysis filters or segments by this field.

3. **Clock skew between services:** if the serving service and the downstream click event use different clocks, session stitching fails. Fix: propagate `requestId` through the click event; join on `requestId`, not on timestamp.

4. **User re-assignment between sessions:** if variant assignment is session-based instead of user-based, the same user may see both variants — contaminating the control group. Fix: persist variant assignment to user profile or use a deterministic hash(userId, experimentId) → variant.

5. **Missing `modelVersion` / `indexTimestamp`:** when a model is updated mid-experiment, you cannot tell whether a CTR change is due to the model update or the A/B variant. Fix: always log what produced the result.
**Pułapka rozmowna:** "We can reconstruct the exposure from click logs." You cannot. If a user was shown recommendations and didn't click anything, there is no click log entry. You lose the denominator for CTR. Impression logging must be unconditional, at serving time.
**Tagi:** a-b-testing, kafka, impression-logging, srm, telemetry, experiment

---

## Q-REC-016 [bloom: apply]
**Pytanie:** Review this Kotlin code for serving recommendations. Find and fix all bugs.

```kotlin
suspend fun fetchAndRank(productId: String): List<Item> {
    val embedding = GlobalScope.async {
        encoder.encode(featureStore.getFeatures(productId))
    }
    val candidates = GlobalScope.async {
        faissClient.search(embedding.await(), topK = 200)
    }
    return reranker.rank(candidates.await())
}
```
**Modelowa odpowiedź:** This code has four distinct bugs:

**Bug 1: `GlobalScope.async` — unstructured concurrency.**
`GlobalScope` creates coroutines that are not children of the calling coroutine. If the parent coroutine is cancelled (e.g., request timeout), these coroutines keep running, leaking resources (CPU, threads, connections).
```kotlin
// Fix: use coroutineScope { } or a lifecycle-managed CoroutineScope
suspend fun fetchAndRank(productId: String): List<Item> = coroutineScope {
    ...
}
```

**Bug 2: Sequential execution disguised as parallel.**
`candidates` launches only *after* `embedding` is declared, but `candidates.await()` inside the `GlobalScope.async` block means `candidates` won't even start until `embedding` is awaited. The two async blocks are sequentially dependent — this is not parallelism.
Fix: `embedding` must complete before Faiss can run (correct dependency), but the structure should be explicit, not accidental:
```kotlin
val queryEmbedding = encoder.encode(featureStore.getFeatures(productId))
val candidates = faissClient.search(queryEmbedding, topK = 200)
```
(These are genuinely sequential — no parallelism bug here, but the GlobalScope wrapping adds overhead for no benefit.)

**Bug 3: No timeout on external calls.**
`encoder.encode` and `faissClient.search` have no timeouts. A slow Faiss response will block the coroutine indefinitely, blowing the p99 SLA and potentially exhausting the thread pool.
```kotlin
val queryEmbedding = withTimeout(5.millis) { encoder.encode(...) }
val candidates = withTimeout(15.millis) { faissClient.search(queryEmbedding, topK = 200) }
```

**Bug 4: No fallback.**
If `encoder.encode` or `faissClient.search` throws (timeout, unavailability), the exception propagates to the caller with no degradation. In production, this returns a 500 error to the user.
```kotlin
// Fix: wrap with try/catch and delegate to fallback (see Q-REC-014)
return try {
    val candidates = withTimeout(15.millis) { faissClient.search(queryEmbedding, topK = 200) }
    reranker.rank(candidates)
} catch (e: Exception) {
    getFallback(productId)
}
```

**Fixed version:**
```kotlin
suspend fun fetchAndRank(productId: String): List<Item> {
    val features = withTimeout(5.millis) { featureStore.getFeatures(productId) }
    val queryEmbedding = withTimeout(5.millis) { encoder.encode(features) }
    return try {
        val candidates = withTimeout(15.millis) { faissClient.search(queryEmbedding, topK = 200) }
        reranker.rank(candidates)
    } catch (e: Exception) {
        log.warn("Faiss unavailable for $productId", e)
        getFallback(productId)
    }
}
```
**Pułapka rozmowna:** "GlobalScope is fine for fire-and-forget." Only for genuinely fire-and-forget work (telemetry) where you explicitly accept the leak risk. For load-bearing retrieval paths, it's a reliability bug. The correct fire-and-forget pattern is `scope.launch` on a lifecycle-managed scope, not `GlobalScope`.
**Tagi:** code-review, coroutines, globalscope, timeout, fallback, kotlin

---

## Q-REC-017 [bloom: analyze]
**Pytanie:** Analyze the trade-offs between Two-Tower retrieval and a cross-encoder ranker. Why use Two-Tower for retrieval and a heavier ranker on top?
**Modelowa odpowiedź:** The core constraint is compute vs quality.

**Two-Tower retrieval:**
- Query and item encode **independently** — item embeddings pre-computed offline.
- Serving cost: one query tower forward pass + one ANN lookup. O(1) relative to catalog size.
- Cannot model **feature interactions** between query and item — the only interaction is the final dot product. Misses cross-features like "user viewed luxury shoes → show luxury shoes from same brand."
- Approximate: ANN misses some truly relevant items. The retrieval stage is a "coarse filter."
- Latency budget: 5-15ms for encode + ANN. Fits inside 40ms total.

**Cross-encoder ranker:**
- Jointly encodes (query, item) — full attention over both. Can model all pairwise feature crosses.
- **Cannot be precomputed** — must run live for every (query, candidate) pair.
- Cost: O(candidates) forward passes. At 500 candidates × 10ms each = 5 seconds. Feasible only over a small candidate set.
- Much higher precision than Two-Tower for re-ordering candidates.
- Common implementations: BERT-based cross-encoder, LambdaMART, gradient boosted trees on feature crosses.

**Why the two-stage pipeline:**
| | Two-Tower | Cross-encoder |
|---|---|---|
| Catalog coverage | All N items | Top-K candidates only |
| Latency | O(1) + ANN | O(K) × model_latency |
| Quality | Moderate (dot product only) | High (full feature interaction) |
| Precomputable | Item tower: yes | No |

For Allegro at ~20k RPS, p99 40ms: running a cross-encoder over 10M items is physically impossible. Two-Tower narrows to 500, then a ranker can afford to be expensive.

**When to question this design:** if catalog size is <100k items, exact search over all items with a lightweight ranker may be simpler and better. Two-Tower complexity pays off only at scale.
**Pułapka rozmowna:** "We'll just make the Two-Tower model bigger to get ranker-quality results." Bigger Two-Tower still only computes a dot product as the final interaction. No amount of MLP depth in the towers compensates for the absence of cross-encoder attention over (query, item) pairs. The architectural constraint is the dot product itself, not the tower capacity.
**Tagi:** two-tower, cross-encoder, retrieval, ranking, tradeoffs, architecture

---

## Q-REC-018 [bloom: analyze]
**Pytanie:** When does Faiss in-process (embedded in the JVM serving process) beat Faiss-as-a-service (separate model server with RPC)? Analyze trade-offs.
**Modelowa odpowiedź:** This is an infrastructure architecture decision with real latency, operational, and reliability consequences.

**Faiss in-process (embedded, JNI):**
- Zero network RTT for the ANN call — eliminates 5-20ms of network latency.
- No serialization overhead — float array is passed directly to C++ via JNI.
- Index lives in the same process memory — at 10M items × d=128 × float32 ≈ 5GB. The JVM process must have this RAM headroom (Xmx + off-heap).
- Index load at startup: loading a 5GB Faiss index takes 30-60 seconds. Pod startup time increases substantially — affects Kubernetes rolling deployments.
- **Blue/green index swap:** must coordinate within the process. Typical pattern: load new index to a second in-memory slot, atomic pointer swap, release old. Complex but doable.
- All replicas of the serving pod carry the full index — RAM cost multiplies by replica count (10 replicas × 5GB = 50GB aggregate RAM).
- **Best for:** latency-critical paths, small-to-medium indexes that fit in RAM, homogeneous serving fleet.

**Faiss-as-a-service (dedicated server with gRPC/HTTP):**
- Adds 5-20ms network RTT per query (plus serialization). At 40ms p99 budget, this is significant.
- Index is centralized — only one copy in RAM regardless of serving replica count. Saves RAM at scale.
- Index refresh is independent — you can hot-swap the index on the Faiss server without restarting serving pods. Cleaner blue/green.
- Separate scaling: Faiss server can be scaled independently (GPU instances for high throughput, CPU for cost).
- Single point of failure risk — serving pods are all dependent on the Faiss service. Must have HA (multiple replicas, circuit breaker).
- **Best for:** large indexes that exceed per-pod RAM, heterogeneous fleets, teams that want independent model/serving deployments.

**Allegro's approach (arxiv 2508.03702):** daily offline index rebuild + blue/green swap at ~20k RPS, CPU serving at p99 40ms. The paper does not specify in-process vs service, but the p99 target suggests in-process is likely — a remote call would consume 25-50% of the p99 budget on network alone.

**Decision matrix:**
| Factor | In-process | As-a-service |
|---|---|---|
| Query latency | Lower by 10-20ms | Higher |
| RAM per replica | Full index per replica | Centralized |
| Index hot-swap | Complex (in-process coordination) | Simple (independent deploy) |
| Operational isolation | Coupled to serving | Decoupled |
| SPOF | No (each pod self-contained) | Yes (requires HA) |
**Pułapka rozmowna:** "Just use Faiss-as-a-service — microservices are best practice." Microservices add latency. When your SLA is 40ms p99 and each network hop costs 10-20ms, "best practice" architectural patterns become constraints you need to explicitly justify or violate.
**Tagi:** faiss, in-process, microservices, latency, infrastructure, tradeoffs

---

## Q-REC-019 [bloom: analyze]
**Pytanie:** A team member proposes switching from daily index refresh to real-time embedding index updates (stream every new/updated product into the Faiss index within 60 seconds). Critique this proposal — analyze cost and benefit.
**Modelowa odpowiedź:** This is a classic "real-time everything" proposal that sounds better than it is. Let's break it down.

**Claimed benefit:** new listings appear in recommendations within 60 seconds instead of up to 24 hours. Critical for flash deals, limited-edition drops, daily new listings.

**Real benefits (genuine):**
- Reduces cold-start window from 24h to 60s for new items — material for high-velocity sellers and flash sales.
- Keeps the index more accurate during the day as items go out of stock or change price.

**Costs and challenges:**

1. **Faiss does not support concurrent read + write efficiently.** Standard Faiss indexes (IVF, HNSW) are not thread-safe for concurrent read + add. HNSW supports add without full rebuild, but IVF does not. Real-time updates require either: (a) HNSW with careful locking, (b) a streaming-capable vector DB (Milvus, Qdrant, Weaviate) — replacing Faiss entirely, (c) sharding the index with a "delta" index that is periodically merged.

2. **Encoding pipeline must be always-on.** You need a streaming pipeline (Kafka → feature fetch → encoder inference → index write) running 24/7. This is a new operational surface: encoder latency, Kafka consumer lag, encoder version drift.

3. **Index consistency across replicas.** If Faiss runs in-process across 10 serving pods, you must push updates to all 10 pods in real time. Coordination complexity grows. Any replica out of sync serves stale embeddings.

4. **Embedding model version mismatch.** If the Product Encoder model is retrained (daily, weekly), items encoded with the old model and items encoded with the new model coexist in the index. Dot products between embeddings from different model versions are meaningless — the embedding spaces don't align. Real-time updates require either: (a) never retrain the model (defeats the purpose), or (b) a full re-encode of the entire catalog on every retrain.

5. **Operational burden and cost:** daily batch rebuild is simple, auditable, and restartable. A streaming pipeline requires Kafka, a streaming consumer service, Flink or similar, and on-call coverage. Cost multiplies.

**Verdict:** the proposal makes sense **only if** the business has a demonstrated need (e.g., flash sales are a primary driver of GMV and 24h cold-start is measurably hurting them) AND the team is willing to migrate to a streaming-capable vector DB. For typical catalog updates (regular product listings), daily refresh is a better trade-off. The 24h latency is a known, acceptable constraint given the simplicity it buys.

Counter-proposal: hybrid — keep daily batch rebuild for the main index, add a small "hot" index (BM25 text search or simple popularity index) for items listed in the last 24 hours. Much simpler, handles the flash-sale use case.
**Pułapka rozmowna:** "Real-time is always better than batch." In ML systems, "real-time" means a whole new class of operational problems (consistency, version drift, streaming infrastructure). The right answer is "it depends on the business need and operational cost." Batch is not legacy; it's simple and correct for many use cases.
**Tagi:** real-time, index-refresh, streaming, faiss, mlops, tradeoffs

---

## Q-REC-020 [bloom: analyze]
**Pytanie:** How would you measure if the Two-Tower recommendation model is degrading in production without retraining? Cover: concept drift, embedding drift, popularity bias. What signals would you monitor?
**Modelowa odpowiedź:** Model degradation in production is insidious — the model doesn't throw errors, it silently becomes less relevant. You need a multi-layer monitoring strategy.

**Layer 1: Online business metrics (primary signal)**
The real canary: if CTR, CR, or GMV/visit trend down over weeks despite stable traffic mix, the model is degrading. Set up automated alerts on 7-day rolling averages with seasonal baselines (compare week-over-week, not absolute). Allegro's 2-year A/B (arxiv 2508.03702) is an example of long-horizon tracking.

Limitation: online metrics have high variance and lag. You need earlier signals.

**Layer 2: Embedding drift monitoring**
The model produces embeddings from product features. As the catalog composition changes (new categories, new sellers, new pricing patterns), the *distribution* of incoming feature vectors shifts — but the model hasn't retrained, so its weights were optimized for the old distribution. Measure:
- **Cosine similarity between today's average embedding centroid and the centroid from 30/60/90 days ago.** If centroid drift > threshold, the feature distribution has shifted.
- **Embedding norm distribution:** if L2 norms of query embeddings shift significantly, the model is extrapolating into regions it wasn't trained on.
- Track per-category: if "electronics" embedding cluster starts overlapping with "fashion," something is wrong.

**Layer 3: Recall@K on a held-out test set (offline, weekly)**
Re-run the offline eval on a freshly constructed held-out set (last 7 days of co-purchase pairs not seen during training). If Recall@K drops week-over-week, the model is drifting relative to current user behavior. This is the gold standard early warning signal.

**Layer 4: Popularity bias monitoring**
A degrading model often degrades to recommending popular items regardless of context (model collapses to a popularity prior). Measure:
- **Diversity@K:** average pairwise distance between recommended items. If diversity drops, the model is converging to a narrow popular set.
- **Long-tail coverage:** what fraction of recommendations come from the top-1% most popular items? If this fraction rises over time without a business reason, popularity bias is increasing.

**Layer 5: Prediction request monitoring**
- Distribution of ANN distances (score distributions): if the average dot product between query and top-1 item is decreasing, the model is less confident.
- Fraction of requests falling back to fallback tier (popularity/cache) — a rising fallback rate may indicate the model is returning poor-quality results that the reranker is rejecting.

**Operationalization (Kotlin service perspective):**
```kotlin
// Log per-request signals to a monitoring stream
data class RecsMetricsEvent(
    val requestId: String,
    val ts: Instant,
    val queryProductId: String,
    val topCandidateScore: Float,     // dot product of top-1 result
    val avgCandidateScore: Float,     // average of top-K scores
    val scoreStdDev: Float,           // low std dev → model collapsing
    val fallbackTier: String?,
    val candidateCount: Int,
    val diversityScore: Float         // avg pairwise cosine distance in results
)
```
Aggregate these in Grafana/BigQuery with weekly baselines. Alert on: top candidate score trending down >10% from 30-day baseline; diversity score dropping >15%; fallback rate rising.
**Pułapka rozmowna:** "We don't need drift monitoring — we'll retrain daily." Daily retraining doesn't eliminate drift; it reduces it. If the training pipeline has a bug (silent feature schema change, data pipeline outage causing stale training data), you can retrain daily and still degrade. Monitoring must be independent of the training pipeline.
**Tagi:** model-monitoring, drift, popularity-bias, embedding-drift, recall-at-k, mlops, observability
