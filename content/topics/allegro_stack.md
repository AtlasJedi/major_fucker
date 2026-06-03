# Allegro Stack — question bank

> Allegro (allegro.pl, allegro.tech) — Poland's largest e-commerce platform. 1000+ microservices on Kubernetes, custom service mesh control plane (Envoy Control, written in Kotlin), custom messaging platform (Hermes on Kafka). Kotlin is the preferred backend language over Java internally. The recommendations-system team ships at ~20k RPS with p99 ~40ms using a two-tower neural retrieval model served in-process with Faiss.
>
> This bank targets the contextual knowledge a recruiter assumes you have: how Allegro is architecturally organized, their open-source projects, their practices, polyglot persistence choices, and what it means to ship code into this stack day one as a Regular Kotlin developer.

## Scope

- Allegro infrastructure: Mesos/Marathon history, Kubernetes migration
- Service mesh: Envoy data plane + Envoy Control (Kotlin xDS control plane, OSS)
- Hermes messaging: REST-over-Kafka pub/sub, push-over-HTTP delivery model
- Polyglot persistence: Cassandra, MongoDB, PostgreSQL, Elasticsearch, Druid, Redis — which fits what
- Java to Kotlin migration story (2016 experiment, 2018 "Back Again" post, current state)
- Allegro open source ecosystem: Hermes, BigCache, Envoy Control, Turnilo, Axion, Spunit
- Recommendations system architecture: two-tower model, Faiss ANN, 20k RPS, p99 40ms
- Engineering culture: 1000+ microservices, App Console dev portal, tech radar, publishing culture
- Recruitment pipeline: HR, DevSkiller, tech panel (code review + system design), leader/culture (STAR)
- Body-leasing context: The Codest as vendor, impact on role and career
- Spring Boot WebFlux + coroutines patterns for the recsys serving layer
- Hermes subscription contract and idempotency patterns
- Operational debugging at Allegro scale: Prometheus, Jaeger, App Console, kubectl, Envoy admin

---

## Q-ALG-001 [bloom: recall] [level: junior]
**Question:** What was the historical platform progression at Allegro before Kubernetes, and roughly when did the migration happen?
**Model answer:** Allegro ran on **Mesos + Marathon** as their PaaS for years (early 2014 through ~2020). Marathon was the orchestrator on top of Apache Mesos. They migrated to **Kubernetes** during the late 2010s / early 2020s. The migration was non-trivial because their entire service mesh (Envoy Control) had been built assuming Mesos service discovery — they had to adapt it to work in both worlds during transition. The "Ten Years of Microservices" post (2024-04, blog.allegro.tech) is the canonical retrospective on this journey. They now run 1000+ microservices on Kubernetes.
**Interview trap:** Do not say "they used Docker Swarm" — they did not. Mesos was the platform. The Mesos to Kubernetes move is a common talking point in Allegro interviews because it explains why their service mesh control plane (Envoy Control) is custom rather than Istio.
**Tags:** infrastructure, kubernetes, mesos, marathon, history

---

## Q-ALG-002 [bloom: recall] [level: junior]
**Question:** What is Envoy Control and why did Allegro build it instead of using Istio?
**Model answer:** **Envoy Control** is Allegro's open-source xDS control plane for the Envoy data plane, written in **Kotlin**. xDS stands for discovery services: Listener DS, Cluster DS, Route DS, Endpoint DS, Secret DS — these are the APIs Envoy uses to receive its dynamic config from a control plane. Envoy Control implements this protocol: it feeds each Envoy sidecar with service locations, routing rules, mTLS certs, retry/timeout policies, and permission rules.

Why they built it instead of using Istio:
1. **Istio assumed Kubernetes only** — Allegro needed mesh on Mesos when they started.
2. **Dual-platform transition** — had to support Mesos and Kubernetes simultaneously during migration.
3. **Scale** — managing 10k+ Envoy instances with Allegro-specific policies.
4. **Opinionated routing/permission model** — their internal access-control model doesn't map cleanly to Istio's AuthorizationPolicy CRDs.

Repo: github.com/allegro/envoy-control. Docs: envoy-control.readthedocs.io.
**Interview trap:** Do not claim Envoy Control replaces all of Istio's features. It focuses on traffic management and service discovery. Istio's CA, telemetry pipeline, and full authorization policy model are handled differently or separately at Allegro.
**Tags:** service-mesh, envoy, xds, control-plane, kotlin

---

## Q-ALG-003 [bloom: recall] [level: junior]
**Question:** Name Allegro's five most well-known open-source projects and what each one does.
**Model answer:**
1. **Hermes** (Java) — REST-over-Kafka pub/sub broker. Their flagship messaging product. Publishers POST JSON/Avro to Hermes Frontend; Hermes Consumers push messages to subscribers via HTTP. https://github.com/allegro/hermes
2. **BigCache** (Go, 8k+ stars) — concurrent in-memory cache in Go that avoids GC pressure by storing entries in pre-allocated byte slices instead of regular Go maps. https://github.com/allegro/bigcache
3. **Envoy Control** (Kotlin) — xDS control plane for Envoy mesh. See Q-ALG-002.
4. **Turnilo** (TypeScript) — web UI for exploring data in Apache Druid. https://github.com/allegro/turnilo
5. **Axion Release Plugin** (Groovy) — Gradle plugin for automatic semantic versioning based on git tags. https://github.com/allegro/axion-release-plugin

Bonus: **Spunit** (Kotlin) — Spock-style BDD assertions for Kotlin tests.
**Interview trap:** The recruiter may ask "have you used any of these?" — be honest. Even having read the README of Hermes and Envoy Control before the interview demonstrates preparation. They are proud of these projects and you will get credit for knowing them.
**Tags:** open-source, hermes, bigcache, envoy-control, turnilo, axion

---

## Q-ALG-004 [bloom: recall] [level: junior]
**Question:** Roughly how many microservices does Allegro run in production, and what is their team-to-service philosophy?
**Model answer:** **1000+ microservices** in production as of the "Ten Years of Microservices" post (2024). They follow a **team owns multiple services** model — a team typically owns 5–20 services covering one business capability (Search, Listings, Recommendations, Checkout). Services are deliberately small and single-purpose. They have a self-service developer portal called **App Console** (their internal equivalent of Backstage) where you can spin up a new service from a template, deploy it, monitor it, and manage Hermes subscriptions — blank to production in minutes. This low ceremony model enables service proliferation without chaos.
**Interview trap:** Do not say "1000 microservices is too many" in the interview. Engage with the trade-off: yes, observability and distributed tracing become first-class concerns; yes, you need standardized contracts (Hermes Avro schemas, REST conventions). Show you have thought about the operational cost.
**Tags:** microservices, scale, dev-portal, culture, app-console

---

## Q-ALG-005 [bloom: recall] [level: junior]
**Question:** Allegro transitioned from Java to Kotlin. When did this happen and what is the current state?
**Model answer:** Allegro started experimenting with **Kotlin around 2016–2017** as an early Polish adopter. The post "From Java to Kotlin and Back Again" (2018-05, blog.allegro.tech) documents that some teams initially adopted Kotlin, then rolled back to Java in specific cases due to compile-time overhead and tooling friction at that point in Kotlin's maturity. By 2024 the picture had inverted: **Kotlin is now more popular than Java internally** at Allegro. Most new services are written in Kotlin. Java is still present in legacy services and in some CPU-bound libraries. The recommendations-system serving layer is Kotlin + Spring Boot + WebFlux + coroutines.
**Interview trap:** The 2018 "Java to Kotlin and Back Again" post is a famous talking point. If you say "Allegro is all Kotlin and always was", you have shown you did not read. The story is more interesting: early adoption, partial rollback as tooling matured, eventual full adoption. Know the arc.
**Tags:** kotlin, java, migration, history

---

## Q-ALG-006 [bloom: recall] [level: junior]
**Question:** What databases does Allegro use and why do they practice polyglot persistence?
**Model answer:** Allegro practices **polyglot persistence** — different services choose the database that fits their workload.

| Database | Workload fit |
|---|---|
| **Cassandra** | High-write, high-scale event-style data; user activity logs, large catalogs partitioned by key; tunable consistency |
| **MongoDB** | Document workloads, flexible/evolving schema; product catalog variations |
| **PostgreSQL** | Transactional / relational data requiring strong consistency; order records, financial data |
| **Elasticsearch** | Full-text search, faceted product search, inverted index |
| **Apache Druid** | Time-series analytics; powers internal dashboards queried via Turnilo |
| **Redis** | Caching, rate limiting, session-like hot data |
| **Faiss** | Not a database — an ANN vector index used in the recsys serving layer |

**Why polyglot:** no single database fits all workloads. Marketplace catalog (search) vs. order ledger (transactions) vs. clickstream (analytics) have different consistency, latency, and throughput requirements. The operational cost is offset by App Console templates and SRE tooling that standardize provisioning.
**Interview trap:** Do not say "polyglot is overengineering" — at their scale it is not. But do not use "right tool for the job" as a slogan without pairing each database to a concrete workload. The interviewer wants the pairing, not the slogan.
**Tags:** databases, polyglot-persistence, cassandra, postgresql, elasticsearch, druid, redis

---

## Q-ALG-007 [bloom: recall] [level: junior]
**Question:** What is Hermes at a one-paragraph level? Why not "just use Kafka clients directly"?
**Model answer:** **Hermes** is Allegro's messaging platform built **on top of Kafka**. It exposes Kafka as a **REST API** for publishers and uses a **push-over-HTTP** delivery model to subscribers. Components: **Hermes Frontend** (publisher HTTP endpoint), **Hermes Consumers** (reads from Kafka, POSTs to subscriber endpoints), **Hermes Management** (admin API + UI for topics and subscriptions).

Why not raw Kafka clients:
1. **HTTP everywhere** — most services already speak HTTP; no per-language Kafka client library needed.
2. **Subscription model** — Hermes routes messages to subscribers based on declarative subscriptions, not consumer group coordination.
3. **Centralized delivery semantics** — retries with exponential backoff, dead-letter queues, batching, rate limits — done once in Hermes Consumers, not reimplemented in every consumer service.
4. **Avro support** with schema registry integration built in.
5. **Multi-DC replication** handled by Hermes at the platform level.

Trade-off: HTTP push adds latency and overhead compared to native pull. You trade some throughput for significant operational simplicity.
**Interview trap:** Hermes does NOT replace Kafka — it wraps it. The Kafka brokers, topics, and partitions still exist underneath. Hermes is the API surface and operational layer on top.
**Tags:** hermes, kafka, messaging, microservices, rest-over-kafka

---

## Q-ALG-008 [bloom: recall] [level: junior]
**Question:** What is the typical Allegro recruitment pipeline for a backend Kotlin role?
**Model answer:** Stages in order:
1. **HR / recruiter screen** — basic fit check, expectations, timeline, language competency.
2. **DevSkiller** — automated coding task, ~60 minutes online. A small realistic service or refactor; evaluated automatically and manually reviewed.
3. **Technical panel** (~1.5 hours) — two parts: **code review** (they show you real code, you spot bugs / performance issues / concurrency problems / design issues out loud) and **system design** (e.g., "design a notification service", "design recommendations serving"). They watch how you reason, ask follow-up questions, and name trade-offs.
4. **Leader / hiring manager + culture** — STAR-style behavioral questions: "Tell me about a time you disagreed with a tech lead." They probe for ownership, communication, willingness to publish/document, and culture fit.

**Body-leasing via The Codest:** The Codest handles the contractual and onboarding side. Allegro still owns the technical bar. You interview into the Allegro team for technical fit; The Codest is the legal employer.
**Interview trap:** Do not treat the body-leasing route as "easier" — Allegro's bar is the same for contractors. The Codest's reputation rides on every placement.
**Tags:** recruitment, interview-process, the-codest, body-leasing

---

## Q-ALG-009 [bloom: recall] [level: junior]
**Question:** What does "20k RPS at p99 40ms" mean for the recommendations endpoint, and what are the main components in the hot path?
**Model answer:** **20k requests per second** means the service handles 20,000 recommendation requests per second at peak. **p99 40ms** means that 99% of those requests return in under 40ms — only 1% take longer. This is the SLA described in Allegro's 2025 RecSys paper on their two-tower system.

The hot path (per request):
1. **Feature fetch** — user context from Redis / Cassandra (recent views, item metadata). Must be sub-5ms.
2. **ANN lookup** — query Faiss vector index with user embedding to get top-N candidates. 10–20ms budget.
3. **Re-rank** — apply business rules, diversity, recency, blacklists. Microseconds.
4. **Telemetry publish** — fire-and-forget impression event to Hermes/Kafka. Async, off the critical path.

The tight budget drives architectural choices: in-process Faiss (no network hop), parallel coroutine fetches, Redis for hot-path features, no synchronous calls to slow stores.
**Interview trap:** "p99 40ms" does not mean every request is 40ms. p50 might be 10ms. p99.9 might be 200ms. Know the difference and be able to explain what drives tail latency (GC pauses, cold caches, Faiss index load, lock contention).
**Tags:** recsys, latency, sla, performance, faiss

---

## Q-ALG-010 [bloom: understand] [level: regular]
**Question:** Explain the architecture of Allegro's recommendations serving path at a system-design level. Skip the ML internals, focus on the engineering.
**Model answer:**
```
Client (web/app)
   |  HTTPS request (userId + context)
   v
Edge / API Gateway  --- auth, rate limit, A/B bucket assignment
   |
   v
Recommendations service (Kotlin, Spring Boot, WebFlux + coroutines)
   |
   +- Feature fetch   --- Redis (recent activity, hot keys) + Cassandra (user embeddings)
   +- ANN lookup      --- Faiss index (in-memory, periodically refreshed from offline build)
   +- Re-rank         --- business rules, diversity, recency, blacklists
   +- Telemetry       --- fire-and-forget Hermes publish (impressions for offline training)
   |
   v
Response: ranked list of item_ids + metadata
```

**Latency budget at 20k RPS:** p99 target ~40ms. Feature fetch sub-5ms (Redis/Cassandra hot keys). ANN search 10–20ms. Re-rank microseconds. Serialization ~2ms.

**Offline side:** daily training pipeline on GCP (GPU, T4) builds a new Faiss index. Blue/green swap in serving — new pod set loads new index behind readiness probe, old pods drain, traffic shifts.

**Why coroutines + WebFlux:** high per-request I/O parallelism (feature fetch from N stores simultaneously, ANN call, async telemetry). Coroutines express this concurrently without callback nesting while staying on non-blocking event loops.
**Interview trap:** "What if Faiss is down?" — Faiss is in-process, so "down" means the pod crashed or the index failed to load. Fallback: popularity-based recommendations or cached recent recommendations. Never block the page on a recommendation failure.
**Tags:** recsys, system-design, architecture, latency, faiss, two-tower

---

## Q-ALG-011 [bloom: understand] [level: regular]
**Question:** Why does Allegro use an Envoy sidecar at the data plane instead of an in-process library like Resilience4j for retries, timeouts, and circuit breaking?
**Model answer:** Both approaches solve similar problems (resilience, observability, traffic shaping) but at different layers:

**In-process libraries** (Resilience4j, Hystrix legacy, Spring Retry):
- Tightly bound to the JVM. Full type safety, can reach into your domain ("retry only on PriceUnavailableException").
- Every language/stack needs its own implementation.
- Upgrading resilience policy means redeploying every service.
- Cannot enforce mesh-wide policies centrally.

**Sidecar Envoy + Envoy Control (control plane):**
- Separate L7 proxy intercepts all inbound and outbound traffic from your pod.
- Language-agnostic: same behavior for Kotlin, Go, Python services.
- Centrally managed: policies pushed via xDS without service redeployments.
- Uniform observability: Envoy emits standardized metrics and traces for every service.
- mTLS handled at the sidecar layer.
- Cons: ~1ms latency overhead per hop; operational complexity; no domain-level awareness.

**Allegro chose sidecar** for the scale and polyglot reasons. With 1000+ services in multiple languages, centralized policy and uniform observability outweigh per-service customization. They still use in-process libraries (Resilience4j-style) for **domain-aware** rules — which specific exceptions to retry, which dependencies to bulkhead.
**Interview trap:** "Envoy replaces Resilience4j" is wrong. They coexist. Sidecar handles transport-level (HTTP retries, mTLS, rate limiting); in-process handles domain-level (exception type routing, per-dependency bulkheading).
**Tags:** service-mesh, envoy, resilience4j, architecture, polyglot, sidecar

---

## Q-ALG-012 [bloom: understand] [level: regular]
**Question:** Explain push-over-HTTP delivery in Hermes versus native Kafka consumer pull. What does this mean operationally and what trade-offs does it impose?
**Model answer:** **Native Kafka consumer (pull):** your service runs a `KafkaConsumer`, calls `poll()` in a loop, decodes messages, processes them. You manage offset commits, consumer group membership, and rebalances. The service must be always running to consume.

**Hermes push-over-HTTP:** Hermes Consumers (Allegro-operated infrastructure) pull from Kafka and POST batches to your service's HTTP endpoint. Your service exposes a normal REST endpoint (`POST /hermes/impressions`). Returns 2xx = message acked; 5xx or timeout = Hermes retries with backoff. From your service's perspective you are a regular HTTP API.

**Trade-off table:**

| Dimension | Push-over-HTTP (Hermes) | Native pull (Kafka client) |
|---|---|---|
| Developer ergonomics | HTTP endpoint, no Kafka client | Must manage KafkaConsumer, group rebalance |
| Polyglot | Any HTTP stack | Need Kafka client per language |
| Retry/DLQ | Centralized in Hermes | Each consumer reimplements |
| Throughput | Lower (HTTP overhead) | Higher (native protocol) |
| Backpressure | Push pressure on slow endpoints | Consumer controls poll rate |
| Offset control | None (Hermes owns it) | Full control |
| Schema/registry | Built into Hermes | Manage yourself |

**Net:** Hermes fits when business throughput is below ~10k msg/sec per subscription and developer ergonomics matter. Ultra-high-throughput streaming (feature pipelines, Flink jobs) use native Kafka at Allegro.
**Interview trap:** Push vs pull is not just "HTTP vs Kafka protocol" — it is "who initiates contact". With native Kafka the consumer initiates (pulls). With Hermes the broker initiates (pushes). This affects backpressure direction, autoscaling model, and failure modes.
**Tags:** hermes, kafka, messaging, push-vs-pull, architecture, trade-offs

---

## Q-ALG-013 [bloom: understand] [level: regular]
**Question:** Allegro publishes papers and blog posts heavily. What specific posts should you read for a recsys/Kotlin role, and how should you reference them in the interview?
**Model answer:** **Treat it as required reading.** Allegro engineers reference their own posts in interviews — they expect candidates who have made the effort to know their public engineering identity.

**Canon for the recsys/Kotlin role:**
1. **"Suggest, Complement, Inspire: Story of Two Tower Recommendations at Allegro.com"** — 2025 RecSys paper (arxiv 2508.03702). The system you would work on. Non-negotiable.
2. **"Ten Years of Microservices"** (blog.allegro.tech, 2024-04) — platform evolution, Mesos to Kubernetes, service explosion retrospective.
3. **"Migrating to Service Mesh"** (2020-05) — why Envoy Control exists.
4. **"WebFlux and Coroutines"** (2020-02) — directly relevant to the codebase you would touch daily.
5. **"From Java to Kotlin and Back Again"** (2018-05) — culture and history. Be ready to discuss it.
6. **"Dynamic Workload Balancing in Hermes"** (2023-04) — shows how they evolved the broker under load.

**How to reference in interview:** do not quote verbatim. Use as context for a question: "When I read the two-tower paper, I wondered how you handle cold-start for newly-listed items — do you encode content features at listing time?" That is gold — shows you read, thought, and have specific curiosity.
**Interview trap:** Do not pretend to have read posts you have not. They will catch you if you misattribute or misquote. Honest admission beats fake fluency: "I read the microservices retrospective; I have not gotten to the service mesh post yet."
**Tags:** interview-prep, blog, papers, allegro-tech, recsys

---

## Q-ALG-014 [bloom: understand] [level: regular]
**Question:** How does Cassandra differ from PostgreSQL for write-heavy workloads, and which would you choose for storing user clickstream events at Allegro scale?
**Model answer:** **Cassandra** is a wide-column store built for **partition-key-based access**. Key properties:
- **Tunable consistency** (ONE, QUORUM, ALL) per read/write — you can sacrifice consistency for availability and partition tolerance.
- **Append-optimized** storage (LSM tree) — writes are fast because they go to a memtable + sequential SSTable flush, not random-access B-tree pages.
- **No joins, no transactions** (pre-2020 Cassandra). Data model is query-first: you design tables around access patterns.
- **Linear horizontal scaling** — add nodes, data distributes via consistent hashing (token ring).
- **No single point of failure** — masterless replication.

**PostgreSQL** is a relational ACID database:
- **B-tree indexes**, MVCC for isolation, full SQL with joins and transactions.
- Write-heavy workloads hit WAL + heap page writes; horizontal write scaling requires sharding (Citus) or external coordination.
- Best when you need complex queries, foreign key integrity, or transactional semantics.

**For user clickstream at Allegro scale:** **Cassandra**. Reasons:
- Write volume is enormous (every click/view = row). Cassandra's LSM tree handles this without write amplification spikes.
- Access pattern is predictable: `SELECT * FROM events WHERE user_id = ? ORDER BY ts DESC LIMIT N`. Perfect partition-key lookup.
- You don't need joins or transactions for raw event storage.
- No single point of failure is critical at their availability bar.
- Eventual consistency is acceptable for clickstream (a missed event is not a financial transaction).

**Interview trap:** "Cassandra is eventually consistent so you can't use it for anything important" — wrong. Tunable consistency lets you use QUORUM for writes that matter. The key is modeling your access patterns into the schema before you build.
**Tags:** cassandra, postgresql, polyglot-persistence, clickstream, data-modeling, trade-offs

---

## Q-ALG-015 [bloom: understand] [level: regular]
**Question:** What is the App Console developer portal and what day-one workflow does it enable for a new engineer at Allegro?
**Model answer:** **App Console** is Allegro's internal self-service developer portal — their equivalent of Spotify Backstage or Netflix's Paved Road tooling. It is the single pane of glass for service lifecycle at Allegro.

Day-one capabilities:
- **Service scaffold** — create a new Kotlin/Spring Boot service from a golden-path template in minutes. Template includes: build config (Gradle + Axion versioning), Dockerfile, Kubernetes manifests, Hermes subscription scaffolding, Prometheus metrics endpoint, structured logging config, distributed tracing wiring.
- **Deploy** — push button deploy to dev/staging/production. Integrated with their CI/CD (likely Jenkins or internal tooling on top of ArgoCD).
- **Monitor** — links to pre-built Grafana dashboards for your service. Metrics are auto-scraped via Prometheus.
- **Hermes subscription management** — declare or browse your topics and subscriptions through the UI.
- **App topology** — see which services call yours and which you call. Dependency graph.

**What this means on day 30 as a Regular dev:** you do not need to manually write Kubernetes YAMLs, wire up metrics, or configure log aggregation. The paved road handles it. Your job is business logic. The tradeoff: if the template makes a wrong assumption (e.g., default JVM heap too small for your Faiss-holding service), you need to know where to override it.
**Interview trap:** If asked "how would you set up a new service?", do not say "I'd write a Dockerfile and Kubernetes YAML from scratch." Say "I'd use App Console to scaffold from the golden path template, then customize the resource limits and any non-standard dependencies." Show you know the paved road.
**Tags:** app-console, developer-portal, devex, kubernetes, culture, day-one

---

## Q-ALG-016 [bloom: apply] [level: senior]
**Question:** You are brought in to investigate why your team's recommendations endpoint p99 jumped from 40ms to 200ms overnight. Walk through the diagnostic steps, naming the specific tools you would reach for.
**Model answer:** Start broad, narrow down:

**1. Confirm the symptom.**
Open Grafana dashboards. Look at p50/p95/p99/p99.9 latency over the last 24 hours. Confirm the jump is real, find the inflection timestamp.

**2. Was there a deploy?**
Check App Console deployment history. New version at the inflection? `kubectl rollout history deployment/recs-service`. If yes — leading suspect. Do not rollback before understanding (might be intentional; might not fix the real cause).

**3. Was traffic shape different?**
Check RPS, request size distribution, top callers in Grafana. New high-volume caller? Batch job hammering a specific path?

**4. Decompose the latency budget.**
Open distributed traces in Jaeger (or your tracing backend). Find a slow request from around the inflection. Look at the span tree: feature fetch took X, ANN took Y, re-rank took Z. The bloated stage is the suspect.

**5. For each candidate stage:**
- **Feature fetch slow?** Check Cassandra nodetool status (node health, pending compactions). Check Redis INFO (memory pressure, evictions). Check network latency between pods (Envoy admin stats at `localhost:9901/stats`).
- **ANN / Faiss slow?** Memory pressure on the pod (new index larger)? GC pauses on the JVM? Check `kubectl top pods`. Check JVM GC logs or Prometheus GC metrics.
- **Re-rank slow?** New business rules deployed? Check config service for changes.
- **Coroutine/event loop saturated?** Check thread metrics in Prometheus. A blocking call inside `withContext(Dispatchers.Default)` instead of `Dispatchers.IO` can saturate the compute pool.

**6. Sidecar / network?**
Check Envoy admin stats (`/clusters`, `/stats`). Is the upstream cluster showing high pending requests or connection resets? Is the mesh injecting retries that inflate p99?

**7. Logs.**
Tail structured logs filtered by trace ID of a slow request. Look for warnings, slow query logs, retry storms.

**8. Decide: mitigate vs. fix.**
If customers are hurting: rollback or scale out first, then root-cause. If inflection was a deploy and you cannot roll back safely, scale the deployment horizontally to reduce per-pod load while investigating.

**9. Postmortem.**
Write it down. Allegro culture — incidents become internal RFCs or eventually blog posts.
**Interview trap:** The interviewer is watching whether you (a) jump to conclusions ("must be GC, restart it"), (b) name real tools (Prometheus, Jaeger, App Console, kubectl, Envoy admin at :9901, nodetool), and (c) distinguish between "stop the bleeding" and "find root cause". Priority order: customer impact first, then understand, then fix, then document.
**Tags:** debugging, observability, sre, latency, jaeger, prometheus, envoy

---

## Q-ALG-017 [bloom: apply] [level: senior]
**Question:** Write a Spring Boot WebFlux + coroutines controller that calls three downstream services in parallel (user features, item metadata, model predictions) and returns a combined result. Use WebClient and coroutines correctly.
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

**What matters here:**
1. **`coroutineScope { }`** — creates a structured concurrency scope. If any child fails, all siblings are cancelled. The function only returns when all children complete. Never use `GlobalScope` here.
2. **`async { }`** — launches child coroutines returning `Deferred<T>`. Multiple `async` blocks run in parallel on the same event-loop thread pool.
3. **`.await()`** — suspends (not blocks) until that deferred completes. The underlying event loop is free to handle other requests while waiting.
4. **`suspend fun` controller** — Spring WebFlux understands `suspend` natively via `kotlinx-coroutines-reactor`. It bridges to the reactive `Mono` underneath without you writing reactive chains.
5. **`awaitBody()`** — coroutine-friendly terminator for `WebClient`, replaces `.bodyToMono<T>().awaitSingle()`.
6. **No `runBlocking`, no blocking I/O on the event loop.** Any blocking call (JDBC, legacy library) must be wrapped in `withContext(Dispatchers.IO)`.
7. **Timeouts** — each `WebClient` call must have a timeout configured. Missing timeouts means a slow downstream can hold your coroutine indefinitely, exhausting resources under load.

**Common bugs a code reviewer will catch:**
- `GlobalScope.async` — not structured, leaks on cancellation.
- `.block()` inside a `suspend` function — blocks the event-loop thread, defeats WebFlux.
- Missing per-call timeouts.
- Not handling optional downstream: if feature fetch is non-critical, wrap that `await` in `try/catch` and provide a fallback rather than letting the whole `coroutineScope` fail.
**Interview trap:** They will ask "what happens if `modelClient.predict` throws a timeout exception?" — the `coroutineScope` catches it and cancels `featuresDeferred` and `itemsDeferred`. The whole function throws. You need either a top-level try/catch or a per-call fallback inside the `async` block for graceful degradation.
**Tags:** kotlin, coroutines, webflux, webclient, structured-concurrency, apply

---

## Q-ALG-018 [bloom: apply] [level: senior]
**Question:** Write a Hermes subscription configuration and the receiving Kotlin/Spring endpoint for topic `recommendations.impressions`. Include the correct retry and error contract.
**Model answer:**

**Subscription (declared in Hermes Management UI or via API):**
```yaml
topic: recommendations.impressions
subscriptionName: recs-impressions-analytics-pipeline
endpoint: https://analytics.svc.allegro.internal/hermes/impressions
contentType: application/avro
subscriptionPolicy:
  rate: 5000                  # max msg/sec push rate
  inflightSize: 100           # max concurrent in-flight HTTP requests to endpoint
  messageTtl: 3600            # drop after 1 hour of retries (seconds)
  requestTimeout: 5000        # 5s per HTTP call to your endpoint
  retryClientErrors: false    # do NOT retry 4xx — they are permanent failures
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
    ): ResponseEntity<Unit> = try {
        impressionRepo.saveAll(messages)  // idempotent on (userId, itemId, ts)
        ResponseEntity.ok().build()       // 2xx => Hermes acks the batch
    } catch (e: TransientException) {
        ResponseEntity.status(503).build() // 5xx => Hermes retries with exponential backoff
    } catch (e: PermanentException) {
        log.error("Unprocessable batch {}: {}", batchId, e.message)
        ResponseEntity.ok().build()        // ack to avoid poison-pill infinite retry loop
    }
}
```

**Error contract with Hermes:**
- **2xx** — batch delivered, Hermes advances the Kafka offset.
- **5xx or timeout** — retry with exponential backoff up to `messageTtl`. After TTL expires, batch goes to the dead-letter queue (inspectable in Hermes Management UI).
- **4xx** — by default Hermes does retry 4xx. Setting `retryClientErrors: false` disables this. Unprocessable bad messages should be 2xx-acked and logged separately to avoid infinite retry loops.

**Idempotency:** Hermes delivery is **at-least-once**. Your receiver must be idempotent. Derive a dedup key from `Hermes-Message-Id` header or a business key like `(userId, itemId, ts)` and use `ON CONFLICT DO NOTHING` in PostgreSQL or a Cassandra lightweight transaction on insert.
**Interview trap:** "What if your endpoint takes 10s to respond?" — Hermes hits the 5s requestTimeout, treats it as a failure, and retries. You get the same batch again. Make the endpoint fast, or return 200 immediately and process asynchronously (with a local queue). "How do you handle a poison pill?" — one bad message in a batch should not block the whole subscription. Parse per-message, log and dead-letter the bad one, ack the rest.
**Tags:** hermes, kafka, subscription, idempotency, retry, dead-letter, avro

---

## Q-ALG-019 [bloom: apply] [level: senior]
**Question:** Design a system for A/B testing a new recommendation model version at Allegro, from traffic split through to measurement. Keep p99 < 100ms during the experiment.
**Model answer:**

**Phase 1: Shadow / offline evaluation (before live traffic)**
- Run new model offline on logged requests (replay). Compare ranking metrics (nDCG, MRR) vs. control. Only proceed if offline metrics are positive.

**Phase 2: Traffic split design**
- Assign users to experiment buckets via stable hash on `userId` modulo N (e.g., 10,000 buckets). Bucket assignment at the **Edge / API Gateway**, injected as `X-AB-Bucket` header.
- Map bucket ranges to variants: `0–499 = control`, `500–999 = treatment_a`. This is deterministic and sticky per user (same user always gets same variant).
- Bucket-to-variant mapping lives in a **feature flag service** or config store (Consul / internal config), hot-reloaded without deployments.

**Phase 3: Serving both variants**
- The recommendations service reads `X-AB-Bucket` from the incoming request.
- Loads the variant config at startup / hot-reload: `treatment_a` uses `ModelV2` scoring + new re-rank weights.
- Both model versions are loaded in memory simultaneously (for small model size difference). For large models — route to separate model server instances behind different upstream clusters in Envoy.
- Impression event includes `ab_variant` field published to Hermes.

**Phase 4: Measurement**
- Offline pipeline joins impressions (with `ab_variant`) against conversion/click events (from Kafka/Druid).
- Compute: CTR, conversion rate, revenue per session — segmented by `ab_variant`. Statistical significance via t-test or Bayesian posterior.
- **Guardrail metrics**: p99 latency per variant (alert if treatment_a > 100ms), error rate. If treatment_a degrades latency, rollback.

**Phase 5: Ramp + rollout**
- Expand treatment from 5% to 25% to 50% to 100%. Monitor guardrails at each step.
- Full rollout: delete control branch from code, archive experiment in config.

**Latency constraint during experiment:**
- If new model is heavier: warm the in-memory Faiss index before directing traffic (readiness probe gates pod until loaded).
- Use separate horizontal pod autoscaler target for treatment vs. control if resource profiles differ.
- Timeout per model call same for both variants; fallback to control-model result if treatment times out.
**Interview trap:** "How do you ensure users don't flip between variants?" — stable hash on userId. Same user same bucket every request. "What about new users with no history?" — they fall into a bucket too; the cold-start problem exists for both variants equally and is measured separately.
**Tags:** ab-testing, recsys, experimentation, feature-flags, latency, system-design

---

## Q-ALG-020 [bloom: apply] [level: senior]
**Question:** A new Kotlin service you are building needs to consume a high-cardinality Cassandra table (100M rows, partitioned by `userId`) and rebuild an in-memory Faiss vector index for the recsys serving layer. Describe the blue/green index refresh pattern you would implement.
**Model answer:**

**Problem:** the Faiss index is large (e.g., 10M 128-dim float32 vectors = ~5GB). It must be rebuilt daily from a freshly-trained model. Hot-swapping the index in a live pod causes a window where queries hit a partially loaded index or a JVM GC storm from allocating 5GB.

**Blue/green pattern:**
```
Offline pipeline (GCP, daily):
  Train model -> generate embeddings -> write to Cassandra / GCS blob
        |
        v
  Notify serving layer: "new index version v42 ready at gs://bucket/index-v42.faiss"

Serving pods (Kubernetes Deployment):
  Current pods (green): serving requests with index-v41
        |
  New pods (blue): start up, download index-v42 from GCS, load into Faiss
        |
  Readiness probe: returns 503 until Faiss.load() completes + warm-up queries pass
        |
  Once blue pods are ready: Kubernetes routes traffic to blue
        |
  Green pods drain existing connections, terminate
```

**Key implementation details:**
```kotlin
@Component
class FaissIndexManager(
    private val storageClient: StorageClient,
    private val healthIndicator: FaissReadinessIndicator,
) {
    private lateinit var index: Index

    @PostConstruct
    fun loadIndex() {
        val path = storageClient.download(indexVersion())
        index = FaissFactory.load(path)        // blocks during startup only
        warmUp()                               // run N dummy queries to warm JIT + caches
        healthIndicator.markReady()            // flip readiness
    }
}
```

**Readiness probe in Kubernetes:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 60   # time to download + load index
  periodSeconds: 10
  failureThreshold: 3
```

**GC consideration:** when loading 5GB into the JVM heap, a full GC can spike. Use off-heap Faiss (JNI) to keep the index outside GC's reach. Or use G1GC with region size tuned for large allocations, and pre-allocate with `-Xmx` large enough that GC does not trigger during load.

**Cassandra scan for embedding build:** scan full table with token-range parallelism — partition the `userId` token range across N Spark or Kotlin coroutine workers, each handling a range. Do not scan with a full table scan on a single coordinator — that will timeout at 100M rows.
**Interview trap:** "Why not hot-swap the index on a running pod?" — because you have no atomic pointer swap in Java that works at 5GB. During the load period you have no valid index. Blue/green sidesteps this entirely: zero downtime, zero query against partial data.
**Tags:** faiss, cassandra, blue-green, kubernetes, recsys, index-refresh, gc

---

## Q-ALG-021 [bloom: analyze] [level: master]
**Question:** Allegro built Envoy Control instead of adopting Istio. Five years later with Istio's maturity, would you rewrite it? Walk through the analysis.
**Model answer:**

**Reasons for the original choice (~2018–2020):**
- Istio assumed Kubernetes-only; Allegro was on Mesos.
- Istio's control plane was heavy and unstable at that scale (istiod 1.x had known stability issues at 1000+ services).
- Allegro wanted a custom routing/permission model that matched their internal security domain.
- They had a strong Kotlin engineering team capable of building and owning it.

**State today (Istio 1.20+):**
- Istio is Kubernetes-native and production-stable. Allegro is fully on Kubernetes. Constraint 1 is gone.
- Istio ambient mode (sidecar-less, ztunnel L4 + waypoint proxy L7) reduces per-pod overhead.
- Large ecosystem: Kiali UI, AuthorizationPolicy CRDs, well-understood operational model.
- BUT: Allegro now has years of Envoy Control investment — internal tooling, custom xDS extensions, on-call runbooks, App Console integration, permission model baked into their xDS resources.

**Migration cost analysis:**
- 1000+ services depend on current mesh behavior. Subtle semantic differences (timeout calculation, retry budget logic, header injection, mTLS bootstrap) will break services silently or loudly.
- Allegro's permission model is encoded in custom Envoy Control CRDs/configs; mapping to Istio AuthorizationPolicy is a non-trivial translation.
- Operational teams must relearn. On-call runbooks, dashboards, alert thresholds — all tied to current system.
- App Console wires directly into Envoy Control's management API. Replacing the control plane breaks the portal integration.
- Public OSS identity: abandoning the project has external reputation implications.

**Saved cost from migration:**
- Smaller long-term maintenance burden (community owns Istio).
- Easier hiring (more engineers know Istio than know Envoy Control).
- Ambient mode could reduce cluster resource consumption (no sidecar per pod).

**Net verdict:** probably not worth it unless there is a specific capability blocker Envoy Control cannot address (e.g., ambient mode resource savings become critical, or a security requirement forces L4 separation). Migration cost >> saved maintenance cost for at least 3–5 years. Better play: gradually narrow the gap — adopt Istio xDS resource types as the config language while keeping their control plane, contribute to upstream Envoy, benefit from community work without a full rewrite.
**Interview trap:** Do not be ideological ("Istio is the industry standard, use it"). Be operational. Weigh sunk cost vs. forward cost. Weigh migration risk vs. saved maintenance. The correct answer is "migration cost is enormous; only worth it if there is a specific blocker." Show you understand organizational reality.
**Tags:** service-mesh, architecture, trade-offs, build-vs-buy, migration, istio, envoy-control

---

## Q-ALG-022 [bloom: analyze] [level: master]
**Question:** "Replace Hermes with raw Kafka consumers for the recommendations team to reduce HTTP overhead." Analyze this proposal fully.
**Model answer:**

**Surface appeal:**
- Fewer HTTP round-trips → lower per-message latency.
- Higher throughput potential per consumer (native Kafka protocol).
- Direct control over offset commits and partition assignment.

**What you would lose:**

| Capability | Hermes (current) | Raw Kafka (proposed) |
|---|---|---|
| Retry / DLQ | Centralized, configurable per subscription | Each team reimplements (and gets it slightly wrong) |
| Schema registry | Wired in, Avro transparent | Each consumer manages deserialization + registry |
| Multi-DC replication | Hermes handles it | Team owns MirrorMaker or equivalent |
| Observability | Uniform dashboards across all subscriptions | Per-service bespoke metrics |
| Subscription management | Hermes Management UI, visible to product | Each team's internal topic management |
| On-call runbooks | Standardized across organization | Per-team |
| Consumer group rebalance handling | Hidden by Hermes | Team must handle |

**Where raw Kafka makes sense at Allegro:**
- Ultra-high-throughput streaming pipelines (100k+ events/sec for feature extraction).
- Stream processing with windowing or joins (Kafka Streams, Flink) — needs native pull and offset control.
- Exactly-once semantics with Kafka transactions (Hermes delivers at-least-once).

**For recommendations impressions specifically:**
- Volume: high but manageable through Hermes (50k–500k events/sec).
- Latency requirement: near-real-time is fine; feeds offline training, not hard real-time.
- Schema evolution: critical (model versions change the event shape frequently).
- Conclusion: **Hermes is correct here.** HTTP overhead is negligible relative to operational consistency gained. The team does not have the bandwidth to own all the concerns Hermes abstracts away.

**Where you might switch to raw Kafka:** the *training feature extraction pipeline* — a stream-processing job with windowing, grouping, and state. That is already Flink or Kafka Streams territory, not Hermes.

**Framing for the interview:** this is a "premature optimization" trap. The HTTP overhead of Hermes is measurable in microseconds per message at this volume. The operational cost of raw Kafka (retry logic, schema management, multi-DC, dashboards, runbooks) is measured in engineering weeks per team. The trade is clearly wrong for this use case.
**Interview trap:** They want to see you understand WHY Hermes exists (operational fleet consistency) not just WHAT it is (HTTP-over-Kafka). Do not fall for "let's optimize prematurely."
**Tags:** hermes, kafka, architecture, trade-offs, premature-optimization, operational-cost

---

## Q-ALG-023 [bloom: analyze] [level: master]
**Question:** Allegro runs 1000+ microservices with a polyglot persistence layer (Cassandra, Mongo, Postgres, Elasticsearch, Druid). What are the three hardest operational problems this creates at scale, and how would you address each?
**Model answer:**

**Problem 1: Cross-service data consistency (no global transaction)**
With polyglot persistence there is no distributed transaction across databases. An operation spanning Cassandra (user state) and PostgreSQL (order record) can fail halfway, leaving the system in an inconsistent state.

Solution: **Transactional Outbox Pattern** + **event-driven eventual consistency**.
- The service writes its local state change and a domain event to the same local database in a single transaction (Postgres: `events` table alongside `orders` table, or Cassandra `BATCH` for same partition).
- A separate relay process reads the outbox table and publishes to Hermes/Kafka.
- Downstream systems (other DBs, other services) react to the event.
- **Saga pattern** (choreography) for multi-step flows: each service listens for the previous step's event, applies its local change, publishes the next event. Compensating transactions handle rollback.

**Problem 2: Schema evolution and data contract fragility**
Different databases have different schema evolution models (Cassandra vs Postgres vs Elasticsearch). Changing a shared field causes silent data corruption if consumers are not updated atomically.

Solution: **Schema registry + Avro/Protobuf** for all inter-service data (via Hermes's schema registry integration). **Expand-contract migration**: add new fields as nullable/default, deploy readers, deploy writers, remove old fields only after all consumers migrated. Never break a field type atomically.

**Problem 3: Operational sprawl and observability gaps**
10 teams running 5 different databases each means wildly different expertise, runbooks, monitoring approaches, and backup strategies. A Cassandra outage on team A's partition is invisible to team B until their feature call fails silently.

Solution: **Centralized SRE with database-specific runbooks** + **standardized health metrics** per database type (promoted via App Console templates). Every service that uses Cassandra gets the same Prometheus scrape config and Grafana dashboard template. Cross-cluster alerting: if the Cassandra cluster serving `recommendations-features` becomes unhealthy, the alert fires in the platform team's channel, not just recommendations team's channel. Regular game-day chaos exercises per database type.
**Interview trap:** Do not give abstract answers ("use microservices properly"). Give the concrete pattern names and explain the mechanism. Transactional Outbox, Saga, Expand-Contract, schema registry — these are the vocabulary. If you say "eventual consistency" without explaining the outbox mechanism, you get partial credit at best.
**Tags:** polyglot-persistence, distributed-systems, saga, transactional-outbox, schema-evolution, operational-complexity

---

## Q-ALG-024 [bloom: analyze] [level: master]
**Question:** Compare body-leasing through The Codest with direct hire at Allegro from your perspective as the candidate. What is good, what is risky, and what should you ask in the interview to make an informed decision?
**Model answer:**

**Structure of body-leasing:**
- The Codest is your legal employer. You bill them or are on their payroll. They place you at Allegro full-time.
- You work in the Allegro team daily — Allegro email, access to repos, participates in standups, planning, and on-call.
- Contract has a defined duration (typically 6–24 months, renewable).
- Career growth and salary reviews go through The Codest, not Allegro.

**Advantages:**
- **Faster entry:** body-leasing tends to have a shorter hiring pipeline than direct Allegro.
- **Inside experience:** resume line "worked on Allegro recommendations system at 20k RPS" has real weight.
- **Conversion path:** strong contractors do sometimes receive direct offers. Not guaranteed but documented.
- **Rate:** often higher hourly than equivalent employed rate because benefits are excluded.

**Risks:**
- **Contract stability:** allocations can end, projects can be cancelled, budget cycles can cut contractors first.
- **Dual-manager problem:** Allegro tech lead drives your work; The Codest account manager drives your contract. Misalignment is possible and common.
- **Access gaps:** you may not receive the full internal employee package (all-hands, internal blog, training budget, equity).
- **Identity ambiguity:** "part of the team" vs "vendor with a desk" depends heavily on team culture. Ask explicitly.
- **Promotion ceiling:** cannot be internally promoted to Staff or Principal at Allegro without converting to direct hire. The Codest's promotion velocity may be slower.
- **Institutional knowledge loss:** when your contract ends, your context walks out. You may be less motivated to invest in long-term knowledge transfer.

**Critical questions to ask:**
1. What is the initial contract term and what triggers renewal vs. non-renewal?
2. What is The Codest's track record of conversion to direct Allegro hire?
3. Am I in the team's standups, planning, retrospectives, and on-call rotation, or am I treated as an external vendor?
4. Do I get the same internal tooling, training budget, and access levels as direct employees?
5. What does career growth look like at The Codest specifically — rate review cadence, senior promotion criteria?
6. What is the notice period on both sides?

**The "permanent contractor" trap:** staying in body-leasing beyond 24 months without conversion means you miss the promotion velocity of direct employment. Set a personal deadline: if not converted or clearly progressing by 24 months, actively explore other options.
**Interview trap:** Do not badmouth either party in the interview. Do not say "I prefer direct Allegro" in the Allegro interview — they are hiring body-leased for a reason. But asking the integration questions above is a positive signal: it shows you are thinking operationally about the working relationship, not just chasing the name on your resume.
**Tags:** career, body-leasing, the-codest, allegro, contract, career-growth

---

## Q-ALG-025 [bloom: analyze] [level: master]
**Question:** Allegro publishes papers and OSS heavily. Does this matter for a Regular Developer role day-to-day, or is it relevant only for senior engineers? Analyze the cultural signal and what it means for you on day 30.
**Model answer:**

**What heavy publishing signals about the organization:**
- Engineering brand-building is part of leadership's strategy — external visibility is treated as an asset, not a distraction.
- Engineers are encouraged (or expected) to write up non-trivial work. This is a proxy for "we value good documentation and clear thinking."
- OSS contributions signal: reusable components are valued, public code review bar is high (reputation is on the line), engineers default to "can this be extracted?" rather than "let's keep this internal."

**What it means for a Regular Dev on day 30:**
1. **You will not be expected to publish a RecSys paper.** Papers come from Staff/Principal and ML Research. Regular = mid-level implementation.
2. **You WILL be expected to write internal documentation** for non-trivial work. A technical writeup on a bug you fixed, a design decision you made, or a performance win you achieved is a normal artifact.
3. **Code review will be thorough and substantive.** Public-OSS review habits leak into internal code. Expect comments on naming, testability, observability wiring, edge case handling.
4. **You should be reading actively.** Internal tech radar, sibling team blog posts, RFCs in flight. Making a decision that contradicts 6-month-old team consensus because you did not read is a credibility hit.
5. **Demonstrate the publishing instinct in interview.** When discussing past work, mention internal docs, talks, or posts you have written. Even a Confluence ADR (Architecture Decision Record) counts. They want to see that your default is to write things down.

**What it does NOT mean:**
- You do not need a GitHub portfolio to get hired.
- You do not need to have invented algorithms or published externally.
- "Heavy publisher" culture does not mean every engineer spends half their time writing. Most publishing comes from a small proportion of senior staff.

**Risk to flag:** at companies with strong publishing culture, projects can be prioritized partly for their PR value. A cool ML paper is more publishable than a boring but critical data pipeline fix. Be aware of this distortion and push back when it affects priorities.
**Interview trap:** When asked "how do you stay current?", do not say "I read Medium." Say "I follow allegro.tech, the Stripe/Uber/Spotify engineering blogs, and I read the two-tower RecSys paper before this interview." Concrete and specific beats generic.
**Tags:** culture, publishing, oss, career, allegro-tech, documentation

---

## Q-ALG-026 [bloom: apply] [level: senior]
**Question:** How would you handle Cassandra hot partitions in the recommendations feature store, and what are the signs that you have one?
**Model answer:**

**What a hot partition is:** in Cassandra, a token range is assigned to a node (or vnode). If one partition key is responsible for a disproportionate fraction of reads or writes, the node owning that token range receives far more traffic than others — a "hot" node. Performance degrades for all partitions on that node.

**Signs you have a hot partition:**
- Cassandra node CPU/disk utilization is highly uneven across the ring (`nodetool status` shows one node at 90% CPU, others at 20%).
- `nodetool tpstats` shows high pending reads/writes on specific nodes.
- Prometheus metrics: `cassandra_table_read_latency_*` spikes on specific nodes.
- Application-side: p99 for feature fetch is high for a subset of users (the "famous user" problem — celebrity accounts with millions of followers, or top-selling items).

**Causes in recsys context:**
- Partition key is `userId`, but some users generate 1000x more feature lookups (bot traffic, high-traffic accounts).
- Partition key is `itemId`, and a viral item is queried millions of times per second.

**Fixes:**

1. **Salted partition keys:** append a random bucket suffix to the partition key: `userId + "#" + (random % N)`. Read by querying all N buckets and merging. Distributes load but complicates reads.

2. **Application-level caching:** put Redis in front of Cassandra for the hottest keys. Cache hit absorbs the traffic spike; Cassandra only sees cache misses (cold users, TTL expiry). This is the Allegro-scale answer: Redis for hot keys, Cassandra for the long tail.

3. **Read repair and speculative execution:** enable `speculative_retry` in Cassandra to hedge slow reads against a second replica in parallel. Reduces p99 from occasional node hiccups.

4. **Rate limiting at application level:** if a single client is hammering feature fetch (e.g., a broken batch job), rate-limit it at the service or mesh level (Envoy rate limiter) before it degrades the database.

**Allegro recsys context:** the hot key problem for user features is real at 20k RPS. Redis caching of the most-queried user contexts (TTL 60s) is the first line of defense. Cassandra handles cold reads and the long tail.
**Interview trap:** "Just increase the replication factor" does not fix hot partitions — it gives you more copies of the hot data but the same node load distribution problem. The fix is at the partition key design level or the caching level.
**Tags:** cassandra, hot-partition, performance, redis, caching, recsys, feature-store

---

## Q-ALG-027 [bloom: understand] [level: regular]
**Question:** What is Druid and when does Allegro use it instead of PostgreSQL or Elasticsearch for analytics?
**Model answer:** **Apache Druid** is a real-time OLAP (Online Analytical Processing) database optimized for time-series event data with fast aggregation queries. It ingests append-only, timestamped event streams (directly from Kafka) and answers aggregate queries like "how many impressions per seller per hour in the last 7 days" in sub-second time, even over billions of rows.

**Core properties:**
- **Column-oriented storage** with dictionary encoding and bitmap indexes per dimension — perfect for high-cardinality GROUP BY queries.
- **Pre-aggregated rollups** at ingest time: Druid can collapse raw events into minute/hour granularity segments during ingestion, reducing query-time computation.
- **Native Kafka integration**: streams events in near-real-time (seconds lag).
- **Immutable segments**: data is written once, indexed, and not updated (append-only). Not suitable for mutable records.

**When Druid instead of PostgreSQL:**
- Queries aggregate over hundreds of millions of rows with multiple dimensions and time filters.
- PostgreSQL would require full table scans and heavy index work for the same query; query time would be minutes instead of seconds.

**When Druid instead of Elasticsearch:**
- Elasticsearch is optimized for full-text search and document retrieval, not column aggregation.
- Druid's columnar layout makes `GROUP BY + SUM/COUNT/AVG` over time ranges ~10x faster than Elasticsearch for analytics workloads.
- Elasticsearch handles "find me documents matching this query"; Druid handles "give me aggregate stats over this time window."

**Allegro use case:** powers internal dashboards (queried via Turnilo) for business metrics — impression counts, click-through rates, revenue per category, A/B experiment metrics. Ingests from Kafka via the Hermes/Druid pipeline.
**Interview trap:** Druid is NOT a general-purpose database. You cannot update a row once written. You cannot do arbitrary joins across tables (limited join support). It is a specialized analytics engine — misusing it for transactional workloads is an anti-pattern.
**Tags:** druid, analytics, olap, elasticsearch, postgresql, turnilo, time-series

---

## Q-ALG-028 [bloom: understand] [level: regular]
**Question:** What is Elasticsearch's role in Allegro's architecture and what makes it the right choice for product search over PostgreSQL full-text search?
**Model answer:** **Elasticsearch** powers Allegro's product search — when a user types "Canon EOS" in the search bar, Elasticsearch returns ranked product listings in milliseconds.

**Why Elasticsearch over PostgreSQL full-text search (`tsvector` / `tsquery`):**

| Dimension | Elasticsearch | PostgreSQL FTS |
|---|---|---|
| Scale | Horizontally sharded, billion+ documents | Single node or Citus; FTS index size limits |
| Ranking | BM25 + custom scoring, learning-to-rank integration | Basic ranking only |
| Faceted search | Aggregations for filters (price ranges, brand, category) | Requires complex GROUP BY + HAVING |
| Near-real-time indexing | Document visible within ~1s of ingest | Immediate on commit |
| Schema flexibility | Dynamic mapping, nested objects | Fixed schema |
| Query DSL | Rich: fuzzy match, phrase match, synonyms, boosting | Limited |
| Operational model | Dedicated cluster, JVM-heavy | Same Postgres cluster |

**Operational notes at Allegro:**
- Product catalog changes (new listings, price updates, stock changes) are streamed to Elasticsearch via Hermes/Kafka events from the catalog service (not by Elasticsearch polling the database).
- Indexing pipeline: catalog event → Hermes → indexing service → Elasticsearch bulk API.
- Elasticsearch is not the system of record — the catalog database (likely MongoDB or PostgreSQL) is. Elasticsearch is a secondary read model.
- Queries go to Elasticsearch; writes go to the source-of-truth database; eventual consistency is acceptable for search (a new listing appearing in search 1–2 seconds after creation is fine).
**Interview trap:** Do not say "just use PostgreSQL full-text search at scale." At millions of products with millions of daily queries, Postgres FTS runs out of headroom quickly. The more interesting gotcha: Elasticsearch is not ACID and is eventually consistent. You can query and get stale data. For a marketplace where prices and stock change rapidly, your indexing pipeline must be low-latency.
**Tags:** elasticsearch, search, postgresql, polyglot-persistence, product-catalog, full-text-search

---

## Q-ALG-029 [bloom: recall] [level: junior]
**Question:** What is Axion Release Plugin and why do Allegro teams use it instead of manually managing version numbers?
**Model answer:** **Axion Release Plugin** is a Gradle plugin (github.com/allegro/axion-release-plugin) that generates semantic version numbers **automatically from git tags**. Instead of hardcoding `version = "1.2.3"` in `build.gradle.kts`, Axion reads the latest git tag (e.g., `v1.2.3`), calculates whether there are untagged commits since that tag, and derives the current version:
- If HEAD is exactly at `v1.2.3` → version is `1.2.3`.
- If HEAD is 3 commits past `v1.2.3` → version is `1.2.4-SNAPSHOT` (or configured suffix).

**Why use it:**
1. **Eliminates version bump commits** — no more "bump to 1.2.4" commits cluttering history. The version is always derivable from git state.
2. **Reproducible builds** — any checkout at a specific commit produces the same version string.
3. **CI-friendly** — CI builds get a version without manual intervention.
4. **Integrates with Gradle release task** — `./gradlew release` creates the git tag and pushes; the version is then "official."

**At Allegro:** with 1000+ services each with their own Gradle builds, consistent versioning tooling reduces cognitive overhead. App Console templates include Axion pre-configured.
**Interview trap:** Axion does not replace your artifact registry (Nexus, Artifactory) or Docker registry. It only handles version string derivation. You still publish to whatever registry your pipeline is wired to.
**Tags:** axion, versioning, gradle, build-tooling, open-source

---

## Q-ALG-030 [bloom: apply] [level: senior]
**Question:** You are shipping a new feature that writes impression events from the recommendations service to Hermes. Write a production-grade Kotlin helper that publishes to Hermes using WebClient, with proper timeout, retry-on-failure, and fire-and-forget semantics so it never delays the main request.
**Model answer:**
```kotlin
@Component
class HermesPublisher(
    private val webClient: WebClient,
    @Value("\${hermes.endpoint}") private val hermesEndpoint: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Fire-and-forget: launch in a non-cancellable scope so the parent coroutine
    // cancellation (request completing) does not kill the publish.
    fun publishAsync(topic: String, event: Any, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO + CoroutineName("hermes-publish-$topic")) {
            publishWithRetry(topic, event)
        }
    }

    private suspend fun publishWithRetry(topic: String, event: Any) {
        val body = jacksonObjectMapper().writeValueAsBytes(event)
        var attempt = 0
        while (attempt < 3) {
            try {
                webClient.post()
                    .uri("$hermesEndpoint/topics/$topic/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError) { resp ->
                        resp.createException().flatMap { Mono.error(it) }
                    }
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(2))
                    .awaitSingleOrNull()
                return  // success
            } catch (e: CancellationException) {
                throw e  // always rethrow cancellation
            } catch (e: Exception) {
                attempt++
                if (attempt >= 3) {
                    log.error("Hermes publish failed after 3 attempts for topic {}: {}", topic, e.message)
                    metricsCounter("hermes.publish.failure", "topic" to topic).increment()
                } else {
                    delay(100L * attempt)  // 100ms, 200ms backoff
                }
            }
        }
    }
}

// Usage in controller:
@GetMapping("/{userId}")
suspend fun recommend(@PathVariable userId: String): RecommendationResponse = coroutineScope {
    val result = computeRecommendations(userId)
    hermesPublisher.publishAsync("recommendations.impressions", result.toImpressionEvent(), this)
    result.toResponse()  // returns immediately; publish runs in background
}
```

**Key decisions:**
1. **`scope.launch` not `GlobalScope.launch`** — tied to a managed scope (application scope injected or coroutineScope of the request). If using application-scoped publisher, inject a `CoroutineScope` bean that survives the request lifecycle.
2. **`CancellationException` rethrown** — never swallow cancellation; structured concurrency depends on it.
3. **`delay` not `Thread.sleep`** — suspends the coroutine, does not block a thread.
4. **Timeout per call** — a 2s timeout prevents a slow Hermes from holding the retry loop indefinitely.
5. **Metrics on failure** — silent failures are invisible failures; always instrument.
6. **Fire-and-forget contract** — main request path returns before publish completes. If Hermes is down, the impression is lost (acceptable for a telemetry event; unacceptable for a financial transaction).
**Interview trap:** "What if the application shuts down before the publish completes?" — the background coroutine is cancelled during graceful shutdown. For critical events, use the Transactional Outbox pattern instead: write the event to your database in the same transaction as the main business operation, and have a separate relay process publish to Hermes. Fire-and-forget is only acceptable for non-critical telemetry.
**Tags:** hermes, kotlin, coroutines, fire-and-forget, webclient, retry, production-code

---

## Q-ALG-031 [bloom: understand] [level: regular]
**Question:** What is the Transactional Outbox pattern and when would you use it instead of direct Hermes publish in a Kotlin/Spring Boot service?
**Model answer:** The **Transactional Outbox** pattern guarantees that a domain event is published to the message broker **if and only if** the local database transaction commits. It eliminates the dual-write problem: "I saved to Postgres AND I published to Hermes — what if one succeeds and the other fails?"

**How it works:**
1. In the same database transaction that writes the business state change (e.g., `INSERT INTO orders`), also `INSERT INTO outbox_events (topic, payload, created_at)`.
2. Both writes succeed or both roll back — atomically.
3. A separate **relay process** (can be a background coroutine, a Debezium CDC connector, or a scheduled job) reads unprocessed rows from `outbox_events` and publishes each to Hermes/Kafka.
4. On successful publish, marks the row as delivered (or deletes it).

```kotlin
@Transactional
suspend fun placeOrder(order: Order): OrderId {
    val saved = orderRepo.save(order)  // INSERT INTO orders
    outboxRepo.save(                   // INSERT INTO outbox_events (same transaction)
        OutboxEvent(
            topic = "orders.placed",
            payload = jacksonObjectMapper().writeValueAsString(OrderPlacedEvent(saved.id)),
        )
    )
    return saved.id
    // Transaction commits: both rows written.
    // Relay process will pick up the outbox event and publish to Hermes.
}
```

**When to use Outbox instead of direct Hermes publish:**
- The domain event is **critical** (order placed, payment confirmed, inventory reserved). Loss is unacceptable.
- You need **exactly-once business-level semantics** (even though Hermes is at-least-once, the outbox deduplication makes the business effect idempotent).
- Your service is polyglot and needs a write-ordering guarantee: the event must not arrive at consumers before the database commit is visible.

**When direct Hermes publish (fire-and-forget) is acceptable:**
- The event is **best-effort telemetry** (impression logging, analytics, non-critical metrics). Loss of a small fraction of events is tolerable.
- No transactional coupling needed.

**Interview trap:** The relay process introduces its own latency (event visible to consumers seconds after the transaction, not milliseconds). For low-latency event-driven flows, this is a real cost. The mitigation is CDC (Change Data Capture) with Debezium reading the Postgres WAL — events flow in milliseconds after commit, with no polling overhead.
**Tags:** transactional-outbox, kafka, hermes, distributed-systems, at-least-once, dual-write

---

## Q-ALG-032 [bloom: analyze] [level: master]
**Question:** The recommendations team is considering moving the Faiss ANN index from in-process (in the serving pod JVM) to a dedicated model server (e.g., a separate Kotlin/gRPC microservice). Analyze the trade-offs.
**Model answer:**

**Current state: in-process Faiss (JVM + JNI)**
- Faiss index loaded into JVM heap (or off-heap via JNI) inside the recommendations service pod.
- ANN query = function call. No network hop. Latency: ~5–15ms for a 10M vector index with HNSW graph.
- Cold start: each pod downloads and loads the index at startup (slow — minutes for large index). Readiness probe gates traffic until loaded.
- Memory: each pod holds a full copy of the index. At 5GB index × 20 pods = 100GB RAM just for index copies.

**Proposed: dedicated model server microservice**
- Separate set of pods that load and serve the Faiss index, expose a gRPC/HTTP endpoint.
- Recommendations service sends an embedding vector, receives top-K candidate IDs. Network hop.

**Trade-off analysis:**

| Dimension | In-process Faiss | Dedicated model server |
|---|---|---|
| Latency | ~5–15ms, no network | +2–10ms network hop; total 8–25ms |
| Memory | Duplicated per pod | Shared; fewer copies needed |
| Scaling | Scales with the recommendation service | Scales independently |
| Cold start | Every pod loads index | Only model server pods load index |
| Operational complexity | Simple — one service | Two services, gRPC contract, separate deploy |
| Index update | Rolling blue/green deploy of entire service | Update model server independently |
| Failure mode | Pod crash = pod restarts with fresh index | Model server down = recs service needs fallback |

**When dedicated model server wins:**
- Index is large (>10GB) and memory duplication across 20+ pods is prohibitively expensive.
- Index update frequency is high (hourly vs. daily) — avoids full service redeploy for each index update.
- Multiple services share the same embedding space (recommendations + search + similar items) — centralizing avoids triple duplication.
- You need independent scaling: ANN queries are CPU/memory intensive, recommendations orchestration is I/O intensive.

**When in-process wins:**
- Index is small enough that per-pod memory cost is acceptable.
- Ultra-low latency requirement (p99 < 40ms is tight — adding 5–10ms network hop is meaningful).
- Team is small — operational overhead of a second service is not worth it yet.

**Allegro recsys context:** the system currently runs at p99 40ms. Adding a network hop risks breaching that SLA. In-process is likely correct at current scale unless the index size or update frequency forces the change.

**Recommendation:** start with in-process. Migrate to dedicated model server if (a) index exceeds ~10GB or (b) index updates need to be more frequent than once daily without a full service redeploy.
**Interview trap:** "Microservices are always better" is wrong here. The network hop cost is real at this latency budget. Premature decomposition of a compute-bound function into a microservice adds latency and operational overhead without proportional benefit.
**Tags:** faiss, architecture, microservices, latency, memory, recsys, model-serving, trade-offs
