> Pre-built topic bank. Your /onboard will generate personalized content for your target role.

# E-commerce Domain — question bank

> Context: Backend Java/Kotlin developer on a major European e-commerce platform. Focus areas from the job: recommendation mechanisms, promotional campaign reporting, promoted offers across international markets. Millions of daily users.

## Scope

- Recommendation systems: collaborative filtering, content-based, hybrid
- Promotions: types, lifecycle, campaign attribution
- Offer ranking and promoted offers
- A/B testing fundamentals
- Reporting for campaign effectiveness (CTR, conversion, ROI)
- Catalog and inventory basics
- Pricing and discounting
- International / multi-market considerations
- Scalability patterns for high traffic
- Data consistency vs availability trade-offs in e-commerce

---

## Q-EC-001 [bloom: recall]
**Question:** What are the 3 main approaches to product recommendation, and what data does each require?
**Model answer:** 1) **Collaborative Filtering (CF):** "Users similar to you also bought X." Uses user behavior data (purchases, views, ratings). Two variants: user-based (find similar users) and item-based (find similar items by co-purchase patterns). Requires a matrix of users × items interaction. Cold start problem: new users with no history get no recommendations. 2) **Content-Based Filtering:** "You liked product X, here are similar products." Uses product attributes (category, brand, description). Doesn't require other users' data. Good for new users if you know their preferences. Cold start problem for new items with sparse attributes. 3) **Hybrid:** combines both. Typically weights CF and content-based by confidence (CF when enough history, content-based for cold start). Most production systems use hybrid. **Additional:** Knowledge-based (explicit rules), Context-aware (device, time of day, location). In e-commerce at scale: usually matrix factorization (ALS, SVD) for CF + content features + contextual signals + business rules (margin, stock, promotions).
**Interview trap:** "What is the cold start problem and how do you solve it?" — New user: use popular/trending items, onboarding preference capture, demographic-based defaults. New item: use content attributes, push during initial promotion phase.
**Tags:** recommendations, basics

## Q-EC-002 [bloom: recall]
**Question:** What types of promotions exist in e-commerce? Name at least 5 with a one-line description.
**Model answer:** 1) **Percentage discount** — 20% off a product or category. 2) **Fixed discount** — €10 off your order. 3) **Buy X Get Y (BOGO)** — buy 2, get 1 free; buy one, get second 50% off. 4) **Threshold discount** — spend €100, get free shipping / €15 off. 5) **Bundle discount** — buy product A + B together for a reduced combined price. 6) **Flash sale** — deep discount for a limited time window (creates urgency). 7) **Coupon code** — code entered at checkout activates a discount. 8) **Loyalty points** — earn/spend points instead of direct discount. 9) **Free shipping** — conditional on basket value or product type. 10) **Cashback** — refund after purchase (delayed). **In reporting context:** each promotion type has different attribution complexity — bundle discounts split across items, threshold discounts require basket-level calculation, BOGO requires identifying which item is "free."
**Interview trap:** "Which promotion type is hardest to implement correctly?" — BOGO and bundle discounts — they require understanding the full basket, item interactions, and deciding how to split discounts for tax/reporting purposes.
**Tags:** promotions, domain-knowledge

## Q-EC-003 [bloom: recall]
**Question:** What is CTR and conversion rate? How do they relate to measuring promotion effectiveness?
**Model answer:** **CTR (Click-Through Rate):** percentage of users who saw a promotion/offer and clicked it. `CTR = clicks / impressions × 100%`. Measures attention-grabbing. High CTR with low conversion = offer is attractive but product/landing page disappoints. **Conversion Rate:** percentage of users who completed the desired action (purchase) after seeing/clicking an offer. `CVR = purchases / clicks × 100%` or `purchases / impressions × 100%` depending on funnel stage. **For promotion effectiveness reporting:** CTR alone tells you if the promotion is visible/compelling. CVR tells you if it actually drives sales. You also need: **Revenue per click / ROAS (Return on Ad Spend):** `revenue generated / promotion cost`. **Incremental lift:** compare conversion rate of users who saw the promotion vs control group who didn't — this isolates the promotion's actual impact from baseline intent. **Attribution:** which touchpoint (email, banner, push) gets credit for the conversion? Last-click, first-click, linear, time-decay models.
**Interview trap:** "A promotion has 10% CTR but 0.5% conversion. Good or bad?" — Can't tell without context. Compare to baseline CTR/CVR for that product, that promotion type, that user segment. Absolute numbers are meaningless without a benchmark.
**Tags:** analytics, promotions, kpis

## Q-EC-004 [bloom: recall]
**Question:** What is A/B testing? What are the minimum requirements for a valid A/B test?
**Model answer:** A/B testing (split testing) randomly splits users into groups: control (A) gets baseline experience, treatment (B) gets the change. Compare metrics (CTR, CVR, revenue) to determine if the change has statistically significant impact. **Minimum requirements for a valid test:** 1) **Random assignment** — users must be randomly assigned, not by convenience (no "all mobile users see B"). Consistent per user (same user always in same bucket). 2) **Single change** — only change one variable per test. Multiple changes can't attribute the effect. 3) **Sufficient sample size** — calculated before the test using power analysis: desired effect size, significance level (usually p < 0.05), statistical power (usually 80%). Under-powered tests produce false negatives. 4) **Sufficient duration** — run for at least 1-2 full business cycles (avoid weekday/weekend bias). 5) **No peeking** — don't stop the test early when results look good (leads to false positives). 6) **One metric as primary** — define success metric before starting, not after.
**Interview trap:** "If the test shows p=0.04, is the result significant?" — Statistically yes (below 0.05 threshold), but "statistical significance ≠ practical significance." A 0.1% conversion lift might be significant with large N but not worth the engineering cost. Always consider effect size.
**Tags:** ab-testing, analytics

## Q-EC-005 [bloom: understand]
**Question:** Design the data model for a promoted offers system — where sellers pay to have their products ranked higher in search results. What tables/entities would you need?
**Model answer:** Core entities:
```
Offer (id, sellerId, productId, title, price, status)
PromotedOffer (id, offerId, campaignId, bidAmount, startDate, endDate, status)
Campaign (id, sellerId, budgetTotal, budgetDaily, startDate, endDate, status, spentToDate)
Impression (id, promotedOfferId, userId, sessionId, searchQuery, rank, timestamp)
Click (id, impressionId, userId, timestamp)
Conversion (id, clickId, orderId, revenue, timestamp)
```
**Key design decisions:** 1) `PromotedOffer` links to `Campaign` for budget tracking. 2) Impressions table tracks when promoted offer was shown — critical for CTR/CVR metrics and billing. 3) Click links to Impression (not directly to offer) — preserves the context (which query, which rank position). 4) Conversion links to Click — attribution chain. 5) `bidAmount` per offer + campaign budget cap — bidding determines ranking within promoted slot. **Reporting queries:** campaign effectiveness = `SELECT c.id, COUNT(i), COUNT(cl), COUNT(cv), SUM(cv.revenue) FROM campaign c JOIN promoted_offer po ... GROUP BY c.id`. **Index strategy:** `(promotedOfferId, timestamp)` on Impression for range queries. Partition by date for time-range reports.
**Interview trap:** "How do you handle budget cap enforcement at high request rates?" — Not in the DB with a SELECT + UPDATE (race condition). Use Redis atomic decrement or a token bucket per campaign. The DB is for settlement, not real-time enforcement.
**Tags:** design, promotions, ecommerce

## Q-EC-006 [bloom: understand]
**Question:** Explain how you'd implement an endpoint `GET /recommendations?userId=X&context=homepage&limit=20`. What happens between the request and the response?
**Model answer:**
```
1. Auth/rate limit at API Gateway
2. RecommendationService.getRecommendations(userId, context, limit)
   a. Fetch user history (recent views, purchases) from UserHistoryStore (Redis cache or Cassandra)
   b. Fetch user segment/tier from UserProfileService (cached)
   c. Call RecommendationEngine:
      - Collaborative filtering scores from pre-computed matrix (Redis or Elasticsearch)
      - Content-based scores based on recently viewed categories
      - Merge & rank with weighted combination
   d. Apply business rules layer:
      - Filter out-of-stock items
      - Boost items with active promotions (promoted offer slots)
      - Filter items the user just purchased
      - Respect country/market availability
   e. Fetch final product metadata (price, image, title) from CatalogService (cached)
3. Return ranked list of 20 items
```
**Latency target:** homepage recommendation must respond in <100ms. Key optimizations: pre-compute recommendation candidates (batch job, nightly or near-real-time). Serve from cache (Redis) rather than computing on the fly. Real-time component: just re-rank pre-computed candidates using fresh context.
**Interview trap:** "What if the cache is cold (user's first visit)?" — Fallback: return popular/trending items in the user's browsed categories (from session), or editorial picks. Track this as a cold-start event for model improvement.
**Tags:** recommendations, api-design, performance

## Q-EC-007 [bloom: apply]
**Question:** Write a Java/Kotlin service method signature + basic implementation for a reporting endpoint: given a campaign ID and a date range, return `CampaignReport` with total impressions, clicks, conversions, total revenue, CTR, and CVR.
**Model answer:**
```kotlin
data class CampaignReport(
    val campaignId: UUID,
    val from: LocalDate,
    val to: LocalDate,
    val impressions: Long,
    val clicks: Long,
    val conversions: Long,
    val totalRevenue: BigDecimal,
    val ctr: BigDecimal,    // clicks / impressions
    val cvr: BigDecimal     // conversions / clicks
)

@Service
class CampaignReportingService(
    private val impressionRepository: ImpressionRepository,
    private val clickRepository: ClickRepository,
    private val conversionRepository: ConversionRepository
) {
    fun getCampaignReport(campaignId: UUID, from: LocalDate, to: LocalDate): CampaignReport {
        val impressions = impressionRepository.countByCampaignAndDateRange(campaignId, from, to)
        val clicks = clickRepository.countByCampaignAndDateRange(campaignId, from, to)
        val (conversions, revenue) = conversionRepository.sumByCampaignAndDateRange(campaignId, from, to)

        return CampaignReport(
            campaignId = campaignId,
            from = from, to = to,
            impressions = impressions,
            clicks = clicks,
            conversions = conversions,
            totalRevenue = revenue,
            ctr = if (impressions > 0) clicks.toBigDecimal().divide(impressions.toBigDecimal(), 4, RoundingMode.HALF_UP) else BigDecimal.ZERO,
            cvr = if (clicks > 0) conversions.toBigDecimal().divide(clicks.toBigDecimal(), 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
        )
    }
}
```
**Key points:** 1) Divide-by-zero guard on CTR/CVR. 2) `BigDecimal` with explicit `RoundingMode`. 3) Revenue as `BigDecimal`, never Double. 4) Separate repositories to keep queries simple — can optimize later with a single aggregation query. 5) For large date ranges, this should be backed by a pre-aggregated reporting table (not real-time counts on raw events).
**Interview trap:** "This queries raw tables — what happens with 6 months of data?" — Slow. Reporting systems need pre-aggregated tables (materialized views, daily rollups) or a separate analytical store (ClickHouse, Redshift, BigQuery). OLTP DB is not the right tool for analytics queries.
**Tags:** reporting, promotions, kotlin

## Q-EC-008 [bloom: apply]
**Question:** You need to expose promoted offers in search results for international markets (PL, DE, FR, UK). Each market has different currencies, VAT rates, and legal requirements for promotional labeling. How do you design the service to handle this?
**Model answer:** Key principle: localization is a concern of the presentation/API layer, not the domain core.
```
Domain: PromotedOffer stores basePrice in source currency, vatRate by market, promotionType
Service layer: PricingLocalizationService.localizeOffer(offer, market)
  - Convert currency (live rate or daily snapshot from ExchangeRateService)
  - Apply market-specific VAT: priceIncVat = basePrice * (1 + vatRate)
  - Apply rounding rules per market (CHF rounds to 0.05)
  - Resolve promotional label text from i18n bundle (market + promotion type)
API response: always returns localizedPrice, currency, vatIncluded: true/false, promotionLabel per market's locale
```
**Legal requirements:** DE/AT require VAT to always be shown inclusive. FR requires "Prix TTC" label. UK post-Brexit: GBP, UK-specific promotions may differ from EU. **Implementation:** `Market` enum or entity with `vatRate`, `currency`, `vatDisplayRule`, `locale`. `PromotedOfferDto.forMarket(offer, market)` factory method or separate DTO mapper. Never hardcode market-specific logic — keep in configuration (DB or config file) so adding a new market doesn't require code changes.
**Interview trap:** "Should VAT be computed in the frontend or backend?" — Backend. Frontend can be compromised or cached; prices shown to users must be accurate and consistent. Backend computes, frontend displays.
**Tags:** international, ecommerce, design

## Q-EC-009 [bloom: analyze]
**Question:** The recommendation service is slow (avg 250ms) during flash sales because of spikes in real-time behavior data. How do you diagnose and fix this?
**Model answer:** **Diagnose first:** 1) Distributed traces to find which span is slow (user history fetch? recommendation scoring? product catalog lookup?). 2) Check Redis/cache hit rates — likely dropping during spikes (cache stampede). 3) Check DB query times — are they executing full table scans on the hot path? 4) Thread pool saturation? Too many concurrent requests queuing. **Likely root causes in order:** 1) **Cache stampede** — flash sale causes many users to query simultaneously, cache expires for popular items, everyone misses and hits DB. Fix: cache lock/probabilistic refresh, pre-warm cache before flash sale starts, extend TTL during event. 2) **Pre-computation lag** — real-time recommendation matrix not updated fast enough. Fix: serve pre-computed candidates (batch), only apply real-time re-ranking on smaller candidate set. 3) **Product catalog enrichment** — fetching metadata for 20 recommended items = 20 serial calls. Fix: batch call, cache product metadata aggressively (it rarely changes during a sale). 4) **Recommendation engine doing heavy computation on request** — should be offline. Move matrix factorization to batch job, serve results from precomputed store. **Architectural fix:** recommendations = precomputed candidates (batch) + lightweight re-ranking (real-time). Hot path should be: Redis read + business rules = <50ms.
**Interview trap:** "Would horizontal scaling solve it?" — Partially. If it's stateless computation, yes. If it's shared state (Redis bottleneck, DB contention), adding instances can make it worse. Identify the actual bottleneck first.
**Tags:** performance, recommendations, ecommerce

## Q-EC-010 [bloom: analyze]
**Question:** The business wants real-time campaign performance dashboards (update every 30 seconds). Your current setup is: raw impression/click/conversion events → PostgreSQL. This won't scale. What architecture would you propose?
**Model answer:** The fundamental problem: PostgreSQL is OLTP — optimized for transactional reads/writes, not analytical aggregations over billions of rows. **Proposed architecture:**
```
Events → Kafka → 
  ├─ Kafka Streams or Flink (real-time aggregation, 30s windows) → Redis (live counters)
  └─ Kafka Connect → S3/data lake → ClickHouse/BigQuery (historical analytics)

Dashboard API → reads from Redis for live (last hour)
             → reads from ClickHouse for historical (days/months)
```
**Details:** Real-time layer: Kafka Streams with tumbling 30-second windows computing impression/click/conversion counts per campaign. Results written to Redis as `campaign:{id}:metrics:{window}`. Dashboard polls Redis at 30s. Historical layer: raw events land in ClickHouse (columnar, optimized for aggregation). Pre-aggregated daily/hourly rollup tables. ClickHouse handles 10B row scans in seconds. **Why not just aggregate in Kafka Streams only?** Redis is ephemeral — not suitable for 6-month historical queries. ClickHouse handles both. **Why not Spark?** Latency — Spark micro-batch has minutes of lag. Kafka Streams or Flink gives sub-minute. **PostgreSQL:** keep for campaign/offer management (config data), not for event analytics.
**Interview trap:** "Is this over-engineered for a startup?" — Maybe. For <10M events/day, a PostgreSQL with proper indexes + materialized views might work fine. The proposed architecture is for millions of daily users (as stated in the job description).
**Tags:** analytics, architecture, kafka, reporting
