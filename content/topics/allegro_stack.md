# Allegro Stack — question bank

> Allegro (allegro.pl, allegro.tech) — Poland's largest e-commerce platform. 1000+ microservices on Kubernetes, custom service mesh control plane (Envoy Control), custom messaging platform (Hermes on Kafka). Kotlin > Java as the primary backend. Heavy publishing on blog.allegro.tech.
>
> This bank targets the contextual knowledge a recruiter assumes you have: how Allegro is architecturally organized, their open-source, their practices, their tech radar.

## Scope

- Allegro infrastructure: Mesos/Marathon → Kubernetes migration history
- Service mesh: Envoy data plane + Envoy Control (Kotlin control plane, OSS)
- Hermes messaging (przegląd; deep dive w osobnym banku `kafka_hermes`)
- Polyglot persistence: Cassandra, MongoDB, PostgreSQL, Elasticsearch, Druid
- Java → Kotlin migration story (post 2018 "Java to Kotlin and Back Again")
- Allegro open source ecosystem (github.com/allegro) — Hermes, BigCache, Envoy Control, Turnilo, Axion, Spunit
- Engineering culture: 1000+ microservices, App Console (internal dev portal), tech radar
- Recruitment pipeline: HR → DevSkiller → tech panel (code review + system design) → leader + culture (STAR)
- Allegro ML org (ml.allegro.tech) — GCP + Kubernetes, training pipeline
- Body-leasing context: The Codest jako vendor, jak to wpływa na rolę

---

## Q-AL-001 [bloom: recall]
**Question:** What was the historical platform progression at Allegro before Kubernetes, and roughly when did the migration happen?
**Model answer:** Allegro ran on **Mesos + Marathon** as their PaaS for years (early 2014–~2020). Marathon was the orchestrator on top of Apache Mesos. They migrated to **Kubernetes** during the late 2010s / early 2020s. The migration was non-trivial because their entire service mesh (Envoy Control) had been built assuming Mesos service discovery — they had to adapt it to work in both worlds during transition. The "Ten Years of Microservices" post (2024-04, blog.allegro.tech) is the canonical retrospective on this journey. They now run 1000+ microservices on Kubernetes.
**Interview trap:** Don't say "they used Docker Swarm" — they did not. Mesos was the platform. The Mesos→K8s move is a common talking point in Allegro interviews because it explains why their service mesh control plane (Envoy Control) is custom rather than Istio.
**Tags:** infrastructure, kubernetes, mesos, marathon, history

## Q-AL-002 [bloom: recall]
**Question:** What is Envoy Control and why did Allegro build it instead of using Istio?
**Model answer:** **Envoy Control** is Allegro's open-source xDS control plane for Envoy data plane, written in **Kotlin**. xDS = discovery services (Listener DS, Cluster DS, Route DS, Endpoint DS, Secret DS) — the protocol Envoy uses to receive its dynamic config. Envoy Control implements this control plane: feeds Envoy with where services live, routing rules, mTLS certs, retry/timeout policies. They built it (instead of using Istio) because: (1) **Istio assumed Kubernetes only** — Allegro was on Mesos when they needed a mesh; (2) need to support **both Mesos and K8s** during migration; (3) **scale** — managing 10k+ Envoys with custom policies; (4) own opinionated routing / permission model. Repo: github.com/allegro/envoy-control. Docs: envoy-control.readthedocs.io.
**Interview trap:** Don't claim Envoy Control replaces all of Istio's features — it focuses on traffic management + service discovery. Things like Istio's CA, telemetry collection, and authorization policies they implement separately or differently.
**Tags:** service-mesh, envoy, xds, control-plane, kotlin

## Q-AL-003 [bloom: recall]
**Question:** Name Allegro's five most well-known open-source projects and what each one does.
**Model answer:**
1. **Hermes** (Java) — REST-over-Kafka pub/sub broker. Their flagship messaging product. Publishers POST JSON/Avro to Hermes Frontend; Hermes Consumers push messages to subscribers via HTTP. https://github.com/allegro/hermes
2. **BigCache** (Go, 8k+ stars) — concurrent in-memory cache in Go that doesn't pressure GC by storing entries in pre-allocated byte slices. https://github.com/allegro/bigcache
3. **Envoy Control** (Kotlin) — xDS control plane for Envoy mesh. See Q-AL-002.
4. **Turnilo** (TypeScript) — web UI for exploring data in Apache Druid. https://github.com/allegro/turnilo
5. **Axion Release Plugin** (Groovy) — Gradle plugin for automatic semantic versioning based on git tags. https://github.com/allegro/axion-release-plugin
Bonus: **Spunit** (Kotlin) — Spock-style assertions for Kotlin tests.
**Interview trap:** The recruiter might ask "have you used any of these?" — be honest. Even just having read the README of Hermes and Envoy Control before the interview shows you did your homework. They're proud of these and you'll get warmth points.
**Tags:** open-source, hermes, bigcache, envoy-control, turnilo

## Q-AL-004 [bloom: recall]
**Question:** Roughly how many microservices does Allegro run in production, and what's their team-to-service ratio philosophy?
**Model answer:** **1000+ microservices** in production as of the "Ten Years of Microservices" post (2024). They follow a **team owns multiple services** model — a team typically owns 5–20 services covering one business capability (e.g., "Search", "Listings", "Recommendations", "Checkout"). Services are deliberately small and single-purpose. They have a self-service developer portal called **App Console** (their internal equivalent of Backstage) where you can spin up a new service from template, deploy, monitor, manage subscriptions in Hermes, etc. — blank → production in minutes. This unblocks "service explosion" tendencies — you don't add ceremony when creating a new service, you just create one.
**Interview trap:** Don't say "1000 microservices is too many" in the interview. It is what it is. Engage with the trade-off: yes, observability and tracing become first-class concerns; yes, you need standardized contracts (Hermes Avro schemas, REST conventions). Show you've thought about the operational cost.
**Tags:** microservices, scale, dev-portal, culture

## Q-AL-005 [bloom: recall]
**Question:** Allegro went from Java to Kotlin. When did this happen and what's the current state?
**Model answer:** Allegro started experimenting with **Kotlin around 2016–2017**, when they were a major early Polish adopter. The famous post "From Java to Kotlin and Back Again" (2018-05) documents that some teams initially tried Kotlin, then rolled back to Java in specific cases due to compile time + tooling friction at that point. By 2024, the situation had inverted: **Kotlin is now more popular than Java internally** at Allegro. Most new services are written in Kotlin. Java is still in use for legacy services and where Kotlin doesn't add value (some CPU-bound libs). The recsys serving layer is Kotlin + Spring Boot.
**Interview trap:** Read the 2018 "Java to Kotlin and Back" post BEFORE the interview — it's a famous talking point. If you say "Allegro is all Kotlin now and always was", you've shown you didn't read. The story is more interesting: early adoption, partial rollback, eventual full adoption as tooling matured.
**Tags:** kotlin, java, migration, history

## Q-AL-006 [bloom: recall]
**Question:** What databases does Allegro use? Why polyglot persistence?
**Model answer:** Allegro practices **polyglot persistence** — different services pick the database that fits their workload. Common choices:
- **Cassandra** — high-write, high-scale event-style data (e.g., user activity, large catalogs partitioned by key).
- **MongoDB** — document workloads, flexible schema (e.g., product catalog variations).
- **PostgreSQL** — transactional / relational data with strong consistency needs.
- **Elasticsearch** — full-text search, faceted product search.
- **Apache Druid** — time-series analytics; powers their internal dashboards (queried via Turnilo).
- **Redis** — caching, rate limiting, session-like data.
- **Faiss** (not a database but a vector index) — recsys ANN serving.
**Why polyglot:** no single DB fits all workloads. Marketplace catalog (search) vs. order ledger (transactions) vs. clickstream (analytics) — each has different consistency, latency, and throughput needs. The cost is operational complexity, which they offset with App Console templates and SRE tooling.
**Interview trap:** Don't say "polyglot is overengineering" — at their scale it's not. But also don't claim "use the right DB for the job" as a slogan without examples. Pair each DB with a concrete workload.
**Tags:** databases, polyglot-persistence, cassandra, postgresql, elasticsearch, druid

## Q-AL-007 [bloom: recall]
**Question:** What is Hermes (Allegro's project) at a one-paragraph level? Why not "just use Kafka clients"?
**Model answer:** **Hermes** is Allegro's messaging platform built **on top of Kafka**. It exposes Kafka as a **REST API** for publishers and uses a **push-over-HTTP** delivery model to subscribers. Components: **Hermes Frontend** (publisher HTTP endpoint), **Hermes Consumers** (read from Kafka, POST to subscribers), **Hermes Management** (admin API + UI for topics/subscriptions). Why not raw Kafka clients: (1) **HTTP everywhere** — most services already speak HTTP; no need for Kafka client libraries per language; (2) **subscription model** — Hermes routes messages to consumers based on declarative subscriptions; (3) **delivery semantics handled centrally** — retries with exponential backoff, dead-letter, batching, rate limits — done in Hermes Consumers, not in every consumer service; (4) **Avro support** + schema registry integration; (5) **multi-DC** replication built in. Trade-off: HTTP push has overhead vs native pull; you give up some throughput for operational simplicity at the consumer.
**Interview trap:** Hermes does NOT replace Kafka — it wraps it. The Kafka brokers, topics, and partitions are still there underneath. Hermes is the API surface and operational layer on top.
**Tags:** hermes, kafka, messaging, microservices

## Q-AL-008 [bloom: recall]
**Question:** What is the typical Allegro recruitment pipeline for a backend Kotlin role?
**Model answer:** Stages, in order:
1. **HR / recruiter screen** — basic fit, expectations, when can you start, language check.
2. **DevSkiller** — automated coding task, typically ~60 minutes, online. Real-ish code (a small service or refactor), evaluated automatically + manually.
3. **Technical panel** (~1.5 h) — usually two parts: **code review** (they show you a piece of code, you spot bugs / perf / concurrency / design issues out loud) + **system design** (e.g., "design a notification service", "design recommendations serving"). They watch how you reason, ask questions, name trade-offs.
4. **Leader / hiring manager + culture** — STAR-style behavioral ("Tell me about a time you disagreed with a tech lead..."). They probe for ownership, communication, willingness to publish/document, fit with team.
**Body-leasing twist (you via The Codest):** The Codest does the contractual / onboarding side. Allegro still owns the technical bar. You're interviewing into the Allegro team for technical fit; The Codest is the legal employer.
**Interview trap:** Don't treat the body-leasing route as "easier" — Allegro's bar is the same for contractors. Show The Codest you can hold up because their reputation rides on each placement.
**Tags:** recruitment, interview-process, the-codest, body-leasing

---

## Q-AL-009 [bloom: understand]
**Question:** Explain the architecture of Allegro's recommendations serving path at a system-design level. Hand-wave the ML, focus on the engineering.
**Model answer:** Roughly:
```
Client (web/app)
   │  HTTPS request (item_id or context)
   ▼
Edge / API Gateway   ─── auth, rate limit, A/B exposure header
   │
   ▼
Recommendations service (Kotlin, Spring Boot, WebFlux + coroutines)
   │
   ├─ Feature fetch  ─── Cassandra / Redis (recent user activity, item metadata)
   ├─ Encode request via Product Encoder (model server: TF Serving / TorchServe / in-proc)
   ├─ ANN lookup     ─── Faiss index (in-memory, periodically refreshed from offline build)
   ├─ Re-rank        ─── business rules, diversity, recency, blacklists
   └─ Telemetry      ─── Kafka via Hermes (impressions, exposures for offline training)
   │
   ▼
Response: ranked list of item_ids + metadata
```
**Latency budget:** total p99 ~40 ms at 20k RPS (per the 2025 RecSys paper). Most of that goes to ANN search + serialization. Feature fetch must be sub-5ms (Redis / Cassandra with hot keys). Re-rank is microseconds.

**Offline side:** daily training pipeline on GCP (GPU box, T4) → builds new Faiss index → blue/green swap in serving.

**Why coroutines/WebFlux:** lots of parallel I/O per request (feature fetch from N stores, ANN call, telemetry publish). Coroutines let you express this concurrently without callback hell.
**Interview trap:** The recruiter may probe "what if Faiss is down" — fallback path: return popularity-based recommendations or cached recent recs; never block the page. Also: "how do you A/B test?" — exposure key in request, log impressions to Kafka, offline analysis vs control group.
**Tags:** recsys, system-design, architecture, latency, faiss

## Q-AL-010 [bloom: understand]
**Question:** Why does Allegro use Envoy (sidecar) at the data plane instead of an in-process library like Resilience4j for retries/timeouts/circuit breaking?
**Model answer:** Both approaches solve similar problems (resilience, observability, traffic shaping) but at different layers:
- **In-process libs** (Resilience4j, Hystrix legacy, Spring Retry): tightly bound to your JVM. Pros: full type safety, can reach into your domain (e.g., "retry only on `PriceUnavailableException`"). Cons: each language/stack needs its own implementation; upgrading the resilience policy means redeploying every service; can't enforce mesh-wide policies centrally.
- **Sidecar Envoy** (data plane) + Envoy Control (control plane): a separate L7 proxy intercepts all in/out traffic from your pod. Pros: **language-agnostic** (works for Java, Kotlin, Go, Python services equally); **centrally managed** (policies pushed via xDS without redeploys); **uniform observability** (Envoy emits standardized metrics, traces); **mTLS** done at the sidecar. Cons: latency overhead per hop (~1ms); operational complexity; less domain-aware (it's pure L7/HTTP).
- **Allegro chose sidecar** for the scale + polyglot reasons. With 1000+ services in multiple languages, centralized policy + uniform observability outweighs per-service customization. They still use Resilience4j-style in-process libs for **domain-aware** rules (e.g., "retry only on these specific exceptions").
**Interview trap:** Don't say "Envoy replaces Resilience4j" — they coexist. Sidecar handles transport-level (HTTP retries, mTLS, rate limiting). In-process handles domain-level (which exception to retry, bulkheading specific dependencies).
**Tags:** service-mesh, envoy, resilience, architecture, polyglot

## Q-AL-011 [bloom: understand]
**Question:** "Push-over-HTTP" delivery in Hermes vs. native Kafka consumer (pull). What does this actually mean operationally, and what trade-offs does it impose?
**Model answer:** **Native Kafka consumer (pull):** your service runs a `KafkaConsumer`, calls `poll()` in a loop, decodes messages, processes them. You manage offset commits, consumer group membership, rebalances. The service is "long-lived" — must be always running to consume.

**Hermes push-over-HTTP:** Hermes Consumers (Allegro-operated) pull from Kafka and POST batches of messages to your service's HTTP endpoint. Your service exposes a regular REST endpoint that handles `POST /subscriptions/my-thing`. It returns 200 → message acked; 5xx or timeout → Hermes retries with backoff. From your service's perspective, you're "just" a REST API.

**Trade-offs:**
- **Pros of push-over-HTTP:** (1) Your service is a regular HTTP service — no Kafka client lib, no consumer group tuning. (2) Autoscales like any HTTP service. (3) Polyglot — works the same in Kotlin/Go/Python. (4) Hermes centralizes retry/DLQ/rate limit logic. (5) Easier to plug into existing HTTP-based service mesh, observability.
- **Cons:** (1) Extra HTTP hop adds latency. (2) Less throughput per consumer (HTTP overhead vs raw Kafka). (3) Push pressure — if your endpoint is slow, Hermes Consumers back up. (4) You give up direct control over offset commits / partition assignment.
- **Net:** good fit when business throughput < ~10k msg/sec per subscription and developer ergonomics matter; less ideal for ultra-high-throughput streaming pipelines (those still go raw Kafka at Allegro).
**Interview trap:** Push vs pull is NOT just "HTTP vs Kafka protocol" — it's "who initiates contact". With native Kafka the consumer initiates (pulls). With Hermes the broker initiates (pushes to subscriber). This affects: backpressure direction, autoscaling model, failure modes.
**Tags:** hermes, kafka, messaging, push-vs-pull, architecture

## Q-AL-012 [bloom: understand]
**Question:** Allegro publishes papers and blog posts heavily. How should you treat this in interview prep, and what specific posts should you read for a recsys/Kotlin role?
**Model answer:** **Treat it as required reading.** Allegro engineers reference their own posts in interviews — they expect candidates who've made the effort to know their public-facing engineering identity.

**Canon for your role:**
1. **"Suggest, Complement, Inspire: Story of Two Tower Recommendations at Allegro.com"** — the 2025 RecSys paper (arxiv 2508.03702). This describes the system you'd be working on. Non-negotiable.
2. **"Ten Years of Microservices"** (blog.allegro.tech, 2024-04) — the retrospective on platform evolution, Mesos→K8s, service explosion.
3. **"Migrating to Service Mesh"** (2020-05) — why Envoy Control exists.
4. **"WebFlux and Coroutines"** (2020-02) — directly relevant to the daily codebase you'd touch.
5. **"From Java to Kotlin and Back Again"** (2018-05) — culture / history. Be ready to discuss it.
6. **"Dynamic Workload Balancing in Hermes"** (2023-04) — they're proud of this; shows how they evolved the broker.

**How to use them in interview:** Don't quote them verbatim. Reference them as context: "When I read about your two-tower setup in the paper, I wondered how you handle cold-start for newly-listed items — do you encode content features at listing time?" That's gold — shows you read, thought, and have specific curiosity.
**Interview trap:** Don't pretend to have read posts you haven't. They'll catch you if you misattribute or misquote. Be honest: "I read the microservices retrospective; haven't gotten to the service mesh post yet." Honesty beats fake fluency.
**Tags:** interview-prep, blog, papers, allegro-tech

---

## Q-AL-013 [bloom: apply]
**Question:** You're brought in to investigate why your team's recommendations endpoint p99 jumped from 40ms to 200ms last night. Walk through the diagnostic steps you'd take, naming the specific tools you'd reach for.
**Model answer:** Start broad, narrow down:

1. **Confirm symptom.** Open the service dashboard (Grafana / Prometheus). Look at p50/p95/p99/p99.9 latency over the last 24h. Confirm 40ms→200ms jump is real and find the inflection point.

2. **Was there a deploy?** Check App Console / deployment history. New version at the inflection? If yes — leading suspect. `kubectl rollout history deployment/recs`. Consider rollback button (deferred — don't rollback before understanding).

3. **Was traffic shape different?** Check RPS, request size distribution, top callers. A new caller hammering a path? A heavy batch job?

4. **Decompose the latency budget.** Look at per-stage timings via distributed tracing (OpenTelemetry / Jaeger). For one slow request: feature fetch took X, ANN took Y, rerank took Z. The stage that bloated is the suspect.

5. **For each candidate stage:**
   - **Feature fetch slow?** Check Cassandra / Redis metrics. Hot key? GC pause on Cassandra node? Network issue? Run `nodetool status` / Redis `INFO`.
   - **ANN slow?** Faiss index loaded? Memory pressure (Faiss is heap/off-heap heavy)? Recent index swap with a larger index? Check GC pauses on the recs service JVM.
   - **Re-rank slow?** New rules added? Check business config for changes.
   - **Coroutine pool / WebFlux event loop saturated?** Check thread metrics. Long blocking call inside a `suspend` function would saturate the Reactor scheduler.

6. **Sidecar / network?** Check Envoy metrics (request duration breakdown: upstream vs downstream). Mesh issue or app issue?

7. **Logs.** Tail structured logs filtered by trace ID of a slow request. Look for warnings, slow queries, retries.

8. **Decide:** mitigate (scale up, traffic shift, rollback) vs. fix (push patched version). Mitigate first if customers hurt; root-cause after.

9. **Postmortem.** Write it down. This is Allegro culture — incidents become blog posts.

**Interview trap:** The interviewer is watching whether you (a) jump to conclusions ("must be GC, restart it"), (b) name real tools (Prometheus, Jaeger, App Console, `kubectl`, Envoy admin), and (c) think about customer impact vs root cause. Hierarchy: stop the bleeding → understand → fix → document.
**Tags:** debugging, observability, sre, latency, system-design

## Q-AL-014 [bloom: apply]
**Question:** Write a one-page system design for: "a new endpoint that, given a user, returns the top 10 recommended products in <100ms p99, served from your team's recommendations system, with A/B testing baked in."
**Model answer:** Outline (you'd elaborate live):

**API contract:**
```
GET /v1/users/{userId}/recommendations?limit=10&context=homepage
Headers: X-AB-Bucket: <auto-assigned bucket id>
Response 200:
{
  "recommendations": [
    {"itemId": "abc", "score": 0.87, "reason": "similar_to_recent_view"},
    ...
  ],
  "modelVersion": "two-tower-v42",
  "abVariant": "treatment_a"
}
```

**Path (per request):**
1. **Edge** — rate limit, auth, A/B bucket assignment (hashed userId → bucket).
2. **Recs service** (Kotlin, Spring Boot WebFlux + coroutines).
3. **Parallel coroutines:**
   - Fetch user context features (recent views, cart, segment) from Redis. Timeout 10ms.
   - Fetch precomputed user embedding from offline-built store (Cassandra). Timeout 10ms.
4. **ANN query** — Faiss in-process or Faiss model server. Use user embedding to query. Return top 100 candidates. Timeout 15ms.
5. **Re-rank** — apply diversity (no two items from same seller in top 3), recency boost, business blacklist. Return top 10. Microseconds.
6. **Telemetry** — fire-and-forget: publish impression event to Hermes (Kafka). Do NOT await.
7. **Response** — JSON serialization, return.

**A/B:**
- Bucket assigned at edge via stable hash. Service receives bucket ID in header.
- Service maps bucket → variant config (e.g., "treatment_a" uses re-rank-v2). Variant config from a feature-flag service or central config (Consul / Vault / internal).
- Impression event carries `abVariant`. Offline analysis: variant-segmented metrics.

**Resilience:**
- Each parallel fetch has timeout + fallback. Missing user embedding → fall back to popularity-based or cohort-default.
- Faiss timeout → return cached previous response for this user (Redis TTL 5 min), else popularity.
- Hermes publish failure → log, don't fail request.

**Caching:**
- Hot users: cache final 10 for 60s in Redis. Cache key: `userId + context + abVariant + modelVersion`.
- Cache miss falls through to full pipeline. Cache hit short-circuits (response time 5ms).

**Scaling:**
- Service stateless → HPA on CPU + RPS.
- Faiss index in-process: each pod holds the index (~few GB). Cold start slow → readiness probe gates traffic until index loaded.
- Index refresh: blue/green deploy; never hot-swap in place.

**Observability:**
- Metrics: p50/p95/p99 per stage, cache hit rate, fallback rate, model version distribution, A/B variant counts.
- Traces: full request span tree via OpenTelemetry. Sample 1%.
- Logs: structured, correlation ID, no PII.

**Interview trap:** They'll dig in on edge cases. "What if a user has zero history?" — cohort default, popular items, or onboarding-style recs. "What if model server returns stale embeddings?" — version every embedding; reject if too old; fall back. "How do you roll out a new model?" — shadow traffic first (compute new model in parallel, log, don't serve), then small A/B, then ramp.
**Tags:** system-design, recsys, latency, ab-testing, resilience

## Q-AL-015 [bloom: apply]
**Question:** Show how you'd wire up a Spring Boot WebFlux + coroutines controller that calls three downstream services in parallel (user features, item metadata, model predictions) and returns a combined result. Use `WebClient` and coroutines properly.
**Model answer:**
```kotlin
@RestController
@RequestMapping("/v1/recommendations")
class RecommendationsController(
    private val featureClient: FeatureClient,
    private val itemClient: ItemClient,
    private val modelClient: ModelClient,
) {

    @GetMapping("/{userId}")
    suspend fun recommend(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): RecommendationResponse = coroutineScope {
        val featuresDeferred = async { featureClient.fetchUserFeatures(userId) }
        val itemsDeferred    = async { itemClient.fetchRecentItems(userId) }
        val modelDeferred    = async { modelClient.predict(userId) }

        val features = featuresDeferred.await()
        val items    = itemsDeferred.await()
        val scores   = modelDeferred.await()

        rerank(scores, features, items).take(limit).toResponse()
    }
}

class FeatureClient(private val webClient: WebClient) {
    suspend fun fetchUserFeatures(userId: String): UserFeatures =
        webClient.get()
            .uri("/features/{id}", userId)
            .retrieve()
            .awaitBody()
}
```

**What's important here:**
1. **`coroutineScope { ... }`** — creates a structured concurrency scope. If any child fails, all siblings are cancelled. The function returns only when all children complete.
2. **`async { ... }`** — launches a child coroutine that returns a `Deferred<T>`. Multiple `async` running in parallel.
3. **`.await()`** — suspends until that specific deferred completes. Suspending, not blocking — the underlying event loop is free.
4. **`suspend fun` controller method** — Spring WebFlux understands `suspend` natively (via `kotlinx-coroutines-reactor`). It bridges to the reactive `Mono` underneath.
5. **`webClient.retrieve().awaitBody()`** — `awaitBody` is the coroutine-friendly terminator that replaces `.bodyToMono(...).awaitSingle()`.
6. **No `runBlocking`, no blocking I/O.** If you need to call a blocking JDBC, wrap it in `withContext(Dispatchers.IO)`.
7. **Error handling** — if `featuresDeferred.await()` throws (e.g., timeout), the whole `coroutineScope` cancels and bubbles up. Catch upstream or apply per-call recovery via `try/catch` or `.catch { }`.

**Interview trap:** Common bugs the reviewer will hunt:
- Using `GlobalScope.async` (not structured — leaks). Always tie to `coroutineScope` or the controller's scope.
- Calling `.block()` or `runBlocking` inside `suspend` — defeats the entire purpose.
- Not setting timeouts on `WebClient`. Each downstream call MUST have a timeout (`webClient.mutate().responseTimeout(...)` or per-call `.timeout(...)`).
- Forgetting that exceptions cancel the whole scope. If one downstream is optional, wrap its `await` in try/catch to recover.
**Tags:** kotlin, coroutines, webflux, webclient, structured-concurrency

## Q-AL-016 [bloom: apply]
**Question:** Write a Hermes subscription definition (conceptually) and the receiving Kotlin/Spring endpoint for a topic `recommendations.impressions`. Include retry/error contract.
**Model answer:**

**Subscription (declared in Hermes Management UI or via API):**
```yaml
topic: recommendations.impressions
subscriptionName: recs-impressions-analytics-pipeline
endpoint: https://analytics.svc.allegro.internal/hermes/impressions
contentType: application/avro    # schema registry handles binary
subscriptionPolicy:
  rate: 5000                     # max msg/sec push
  inflightSize: 100              # max concurrent in-flight HTTP requests
  messageTtl: 3600               # drop after 1 hour of retries
  requestTimeout: 5000           # 5s per HTTP call
  retryClientErrors: false       # don't retry 4xx
  backoff: exponential
```

**Receiving endpoint:**
```kotlin
@RestController
@RequestMapping("/hermes")
class ImpressionsConsumer(
    private val impressionRepo: ImpressionRepository,
) {

    @PostMapping("/impressions", consumes = ["application/avro"])
    suspend fun handleImpressions(
        @RequestBody messages: List<ImpressionEvent>,
        @RequestHeader("Hermes-Message-Id") batchId: String,
    ): ResponseEntity<Unit> {
        return try {
            impressionRepo.saveAll(messages)  // idempotent on (userId, itemId, ts)
            ResponseEntity.ok().build()       // 2xx → ack
        } catch (e: TransientException) {
            ResponseEntity.status(503).build() // 5xx → Hermes retries with backoff
        } catch (e: PermanentException) {
            log.error("Bad message batch {}: {}", batchId, e.message)
            ResponseEntity.ok().build()        // ack to avoid poison-pill loop
        }
    }
}
```

**Error contract with Hermes:**
- **2xx response** → Hermes treats batch as delivered, advances offset.
- **5xx / timeout** → retry with exponential backoff up to `messageTtl`. After TTL → message goes to dead-letter (Hermes management has DLQ inspection).
- **4xx response** → by default Hermes DOES retry, unless `retryClientErrors: false` in subscription policy. Bad messages should be 2xx-acked and logged separately to avoid infinite retry.

**Idempotency:** Hermes delivery is **at-least-once**. Your receiver MUST be idempotent. Pattern: derive a unique key per message (e.g., `messageId` from Hermes header or business key like `(userId, itemId, ts)`) and dedup in the repo on insert (`ON CONFLICT DO NOTHING` in Postgres, or `LWT` in Cassandra).

**Interview trap:** Two things they'll probe:
1. "What if your endpoint takes 10s to respond?" — Hermes will hit its request timeout (5s here), treat as failure, retry. You'll get the same batch again. So make the work fast or async (return 200 immediately, queue internally).
2. "How do you handle a poison pill?" — a single bad message in the batch shouldn't block the whole subscription. Catch parse errors per-message, log + dead-letter individually, ack the rest.
**Tags:** hermes, kafka, subscription, idempotency, retry

---

## Q-AL-017 [bloom: analyze]
**Question:** Allegro built Envoy Control instead of adopting Istio. Five years later, with Istio's maturity, would you rewrite? Walk through the analysis.
**Model answer:**

**Reasons for original choice (~2018–2020):**
- Istio assumed K8s-only; Allegro on Mesos.
- Istio control plane was heavy / unstable at that scale.
- Wanted custom permission/routing model fitting Allegro's mesh policies.
- Strong in-house team capable of building it (Kotlin people, JVM expertise).

**State today (Istio maturity):**
- Istio is K8s-native and stable. Allegro is fully on K8s. Constraint (1) gone.
- Istio supports ambient mode (no sidecar overhead per pod).
- Big ecosystem: telemetry, Kiali UI, AuthorizationPolicy CRDs.
- BUT: Allegro now has years of Envoy Control investment — internal tooling, dashboards, on-call runbooks, custom xDS extensions for their permission model.

**Analysis:**
- **Pros of rewriting to Istio:** smaller maintenance footprint long-term (community owns it), can hire engineers familiar with Istio off the shelf, less "another internal special snowflake" to learn for new joiners, ambient mode could reduce resource cost.
- **Cons:** migration risk is huge — 1000+ services depending on current mesh behavior. Subtle behavior differences (timeout semantics, retry budgets, header propagation) will break things. Operational team has to relearn. Years of integrations to redo (App Console wires into Envoy Control specifically). Open-source identity (their Envoy Control project) — abandoning it has external optics.
- **Net:** probably NOT worth it unless they have a specific pain point Envoy Control can't solve. Migration cost > saved cost. Better play: contribute back to upstream Envoy, narrow the gap with Istio at the data plane, and gradually adopt Istio CRDs as their config language while keeping their control plane.

**This is a system-design taste question.** They want to see: do you understand sunk-cost vs forward-cost; do you weigh organizational reality vs theoretical clean rebuild; do you know what would actually break.

**Interview trap:** Don't be ideological ("Istio is the standard, use it"). Be operational. The right answer depends on what the migration costs and what continued maintenance of Envoy Control costs. Default to "the migration cost is huge, only worth it if there's a specific blocker."
**Tags:** service-mesh, architecture, trade-offs, build-vs-buy, migration

## Q-AL-018 [bloom: analyze]
**Question:** You're proposed: "Replace Hermes with raw Kafka consumers for the recommendations team to reduce HTTP overhead." Analyze.
**Model answer:**

**Surface appeal:**
- Less HTTP round-trips → lower latency per message.
- Higher throughput per consumer.
- Direct control over offset commits, partition assignment.

**What you'd lose:**
1. **Uniform delivery semantics** — Hermes centralizes retry / DLQ / rate limit policy. With raw Kafka, every consumer reimplements (and gets it slightly wrong).
2. **Schema registry integration** — Hermes wires Avro + schema registry transparently. Raw Kafka means each consumer manages it.
3. **Multi-DC replication** — Hermes handles cross-DC. Raw Kafka would mean your team owning MirrorMaker or equivalent.
4. **Observability uniformity** — Hermes metrics dashboards work the same across all subscriptions. Raw Kafka, you build per-service.
5. **Subscription model** — Hermes Management UI lets product folks view/inspect subscriptions. Lost.
6. **Operational standardization** — on-call runbooks assume Hermes patterns.

**Where raw Kafka makes sense:**
- Ultra-high-throughput streaming jobs (e.g., feature pipeline computing 100k events/sec) — Hermes HTTP overhead becomes a real cost.
- Stream processing with windowing/joins (Kafka Streams, Flink) — needs native pull and offset control.
- Cases where you need exactly-once semantics with Kafka transactions.

**For recsys impressions ingestion specifically:**
- Volume: high (every recommendation view = impression event) but not crushing. Maybe 50k–500k events/sec depending on traffic.
- Latency: near-real-time is fine; not strict ms requirements (it feeds offline training).
- Schema evolution: matters (model versions change).
- Conclusion: **Hermes is the right tool here.** The HTTP overhead is negligible at this scale; the operational consistency is worth more than the saved ms.

**Where you might switch:** the *training feature extraction pipeline* — that's a stream-processing job (groupings, windowing) and benefits from Kafka-native. That's likely already on Flink or Kafka Streams, not Hermes.

**Interview trap:** They want to see that you understand WHY Hermes exists, not just what it is. The HTTP overhead is the surface trade-off; the real value is operational consistency at fleet scale. Don't fall for "let's optimize prematurely".
**Tags:** hermes, kafka, architecture, trade-offs, premature-optimization

## Q-AL-019 [bloom: analyze]
**Question:** Compare body-leasing through The Codest with direct hire at Allegro from your perspective as the candidate. What's good, what's risky, what to ask in the interview?
**Model answer:**

**Body-leasing (your situation):**
- The Codest is your legal employer. You bill them or are on their payroll. They place you at Allegro full-time.
- You work in the Allegro team day-to-day; have an Allegro email/access; ship code into Allegro repos.
- Your contract has a defined duration (often 6–24 months, renewable).
- Career growth, salary reviews go through The Codest, not Allegro.

**Good:**
- **Lower entry bar** — body-leasing tends to be faster to get hired into than direct Allegro.
- **Inside experience** — you're in a great team, get the resume line "worked on Allegro recommendations system", build network.
- **Conversion path** — strong contractors sometimes get direct offers from the client. Not guaranteed but possible.
- **Rate** — often higher hourly than employed equivalent (since you don't get full benefits package).

**Risky:**
- **Less stable** — contracts end; project may end; allocation may shift.
- **Two managers** — Allegro tech lead drives your work; The Codest account manager drives your contract/career. Misalignment possible.
- **Tooling/access limits** — you may not get the same access (internal blogs, tech radar, all-hands) as a direct employee.
- **Identity ambiguity** — when something breaks, are you "on Allegro's team" or "vendor with a desk"? Depends on culture.
- **No equity / direct benefits** — Allegro's benefits package isn't yours.
- **Promotion ceiling** — typically can't get internally promoted to lead role at the client without converting to direct hire.

**Questions to ask in the Allegro / The Codest interviews:**
1. **Contract duration & renewal** — initial term, what determines renewal.
2. **Conversion policy** — does Allegro convert contractors? Track record?
3. **Team integration** — am I treated as part of the team in standups, planning, on-call, social? Or as "external"?
4. **Access** — do I get the same internal tools, blog access, training budget?
5. **What does career growth look like at The Codest if I deliver well at Allegro?** Senior promotion, rate review cadence?
6. **Notice period both sides** — what's the cancellation clause if the project ends.

**Interview trap:** Don't badmouth either side. Don't say "I'd prefer direct Allegro" in the Allegro interview — they're hiring body-leased for a reason. But DO ask the integration questions — it shows you've thought operationally about how this works, which is a green flag.

**Pułapka osobna:** be aware of "permanent contractor" trap — staying in body-leasing too long means you may not get the promotion velocity of a direct hire. Set a personal timer (e.g., "if I'm not converted or promoted by 24 months, look elsewhere").
**Tags:** career, body-leasing, the-codest, allegro, contract

## Q-AL-020 [bloom: analyze]
**Question:** "Allegro publishes lots of papers and OSS. Does this matter for a Regular Dev role, or is it just for senior engineers?" Analyze the cultural signal and what it means for you on day 30.
**Model answer:**

**Cultural signal:**
- Heavy publishing means: (a) engineering brand-building is part of leadership's strategy; (b) engineers are encouraged (or expected) to write up their work; (c) external visibility is considered a positive — they hire for talkers + writers, not just heads-down implementers.
- OSS contributions mean: (a) reusable components are valued — they invest in turning internal libs into public projects; (b) code review bar is high (public code = reputation); (c) engineers think about "is this generic enough to extract?".

**What this means for a Regular Dev on day 30:**
1. **You're not expected to publish a paper.** Reg = mid-level. Publishing is mostly Seniors/Staff.
2. **But you ARE expected to write internal docs / blog posts** for non-trivial work. A solid technical blog post on a problem you solved is a normal contribution.
3. **Code review will be thorough.** Public-OSS code review habits leak into internal code. Expect comments on naming, testability, observability, edge cases.
4. **You should be reading.** Internal tech radar, blog posts from sibling teams, RFCs in flight. Otherwise you'll be making decisions that contradict 6-month-old consensus.
5. **Show "publishing instinct" in interview.** When discussing past work, mention any docs/talks/posts you've written. Even internal Confluence pages count. They want to see your default is to write things down.

**What it doesn't mean:**
- You don't need an arxiv paper to get hired.
- You don't need a GitHub portfolio (though it helps).
- You're not expected to invent new algorithms day one.

**Risk to flag:** at companies with strong publishing culture, there's a risk of "shipping = publishing" — i.e., projects get prioritized partly for their PR value. Be aware. Not necessarily a bad thing, but a force.

**Interview trap:** In the interview, when asked "how do you stay current?", don't say "I read Medium". Say "I follow allegro.tech blog, the engineering blogs of [Stripe / Uber / Spotify / ...], and read [specific paper / book]". Concrete > generic.
**Tags:** culture, publishing, oss, career, allegro
