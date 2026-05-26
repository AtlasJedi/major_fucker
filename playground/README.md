# Kotlin Spring Boot Playground

A learning playground for a Java/Spring developer preparing for a Kotlin RegDev role on Allegro's
recommendations team. Pre-built demo implementations of the patterns you will touch daily,
with dense inline lesson comments so you can study the code as a drill target.

This is NOT production code. Some things are deliberately simplified or stubbed so the focus
stays on the learning target, not on infrastructure plumbing.

---

## How to Run

Requires Java 21 or later (the Gradle wrapper downloads its own JDK toolchain if needed).

```bash
cd playground
./gradlew bootRun
```

The server starts on port **8080**. You should see:

```
Started PlaygroundApplication in X.XXX seconds (JVM running for X.XXX)
```

Run tests:

```bash
./gradlew test
```

Compile check only (no tests):

```bash
./gradlew compileKotlin compileTestKotlin
```

---

## curl Examples

### Recommendations (recs/)

```bash
# Get recommendations for a user + context item
curl -s -X POST http://localhost:8080/recs/recommend \
  -H "Content-Type: application/json" \
  -d '{"user_id":"user-42","context_item_id":"item-0001","limit":5}' | jq .

# With A/B variant
curl -s -X POST http://localhost:8080/recs/recommend \
  -H "Content-Type: application/json" \
  -d '{"user_id":"user-42","context_item_id":"item-0001","limit":5,"ab_variant":"variant-B"}' | jq .

# Health check
curl -s http://localhost:8080/recs/health | jq .
```

### Hermes Impressions Consumer (hermes/)

```bash
# Send a valid batch
curl -s -X POST http://localhost:8080/hermes/impressions \
  -H "Content-Type: application/json" \
  -H "Hermes-Message-Id: msg-001" \
  -d '{
    "events": [
      {"userId":"user-1","itemId":"item-0001","timestamp":"2024-01-01T12:00:00Z","position":1,"impressionId":"imp-001"}
    ]
  }' | jq .

# Send the same batch again — observe duplicates=1
curl -s -X POST http://localhost:8080/hermes/impressions \
  -H "Content-Type: application/json" \
  -H "Hermes-Message-Id: msg-001" \
  -d '{"events":[{"userId":"user-1","itemId":"item-0001","timestamp":"2024-01-01T12:00:00Z","position":1,"impressionId":"imp-001"}]}' | jq .

# Send batch with missing userId — observe deadLettered=1 and 200 response
curl -s -X POST http://localhost:8080/hermes/impressions \
  -H "Content-Type: application/json" \
  -d '{"events":[{"itemId":"item-0001","timestamp":"2024-01-01T12:00:00Z","position":1}]}' | jq .

# Toggle transient error (returns 503 on next impressions call)
curl -s -X POST http://localhost:8080/hermes/trigger-error | jq .
```

### Flow / SSE Streams (events/)

```bash
# Stream 10 events, 200ms apart (press Ctrl+C to stop)
curl -N http://localhost:8080/events/stream?count=10&intervalMs=200

# Batched stream: events grouped into batches of 5
curl -N "http://localhost:8080/events/stream/batched?count=20&batchSize=5&intervalMs=100"

# Error-handling stream: 3 good events, then an error sentinel, then graceful completion
curl -N http://localhost:8080/events/stream/with-error
```

### Anti-patterns (antipatterns/)

```bash
# WRONG: blocks the event loop thread (Thread.sleep inside suspend)
curl -s http://localhost:8080/antipatterns/blocking/wrong | jq .
# CORRECT: uses delay() — suspends coroutine, releases thread
curl -s http://localhost:8080/antipatterns/blocking/correct | jq .
# CORRECT: blocking I/O wrapped in withContext(Dispatchers.IO)
curl -s http://localhost:8080/antipatterns/blocking/blocking-io | jq .

# WRONG: JDBC on the event loop thread
curl -s http://localhost:8080/antipatterns/jdbc/wrong/item-001 | jq .
# CORRECT: JDBC on Dispatchers.IO
curl -s http://localhost:8080/antipatterns/jdbc/correct/item-001 | jq .
# CORRECT (stub): R2DBC fully non-blocking
curl -s http://localhost:8080/antipatterns/jdbc/r2dbc/item-001 | jq .

# Flow backpressure demos
curl -s http://localhost:8080/antipatterns/flow/correct/take | jq .
curl -s "http://localhost:8080/antipatterns/flow/correct/timeout" | jq .
curl -s "http://localhost:8080/antipatterns/flow/correct/buffer" | jq .
```

---

## What Each Package Demonstrates

### `recs/` — Recommendations Serving Pipeline

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `RecommendationsController.kt` | coroutines_webflux, recsys | `coroutineScope { async {} + async {} }` for parallel calls; `withTimeout` for SLA; fallback on failure |
| `FeatureClient.kt` | coroutines_webflux | `WebClient` + `awaitBodyOrNull()` to bridge Mono and suspend; error handling per status code |
| `AnnService.kt` | recsys | Brute-force cosine similarity; `withContext(Dispatchers.Default)` for CPU work; contrast with real HNSW/IVF |
| `ProductEncoder.kt` | recsys, kotlin_basics | Two-tower model architecture; deterministic hash stub; extension functions |
| `Reranker.kt` | recsys, kotlin_basics | MMR diversity algorithm; idiomatic Kotlin sorting with `maxByOrNull`; sealed classes |
| `dto/` | kotlin_basics | `data class` DTOs; `sealed class` for typed status; `@JsonProperty`; nullable vs non-nullable |

### `hermes/` — Hermes-Style Message Consumer

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `ImpressionsConsumer.kt` | kafka_hermes, distributed_systems | Hermes status code contract (200/503/never-4xx); dead-letter pattern; idempotency key priority |
| `ImpressionRepository.kt` | distributed_systems, kotlin_basics | `ConcurrentHashMap.putIfAbsent()` for atomic idempotency; sealed class `StoreResult`; no unnecessary suspend |

### `flow/` — Kotlin Flow Patterns

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `EventStreamService.kt` | coroutines_webflux, kotlin_flows | Cold Flow; `flow { }` builder; `transform`, `onEach`, `catch`, `onCompletion`; SSE endpoint; custom `chunked()` operator |

### `antipatterns/` — What NOT to Do

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `BlockingInSuspend.kt` | coroutines_webflux | `Thread.sleep()` inside suspend = event loop starvation; `delay()` is the fix |
| `GlobalScopeLeak.kt` | coroutines_webflux | `GlobalScope.async` leaks; `coroutineScope` for per-request; managed `CoroutineScope` + `@PreDestroy` for background work |
| `BlockingJdbcInWebFlux.kt` | coroutines_webflux | JDBC on event loop = throughput collapse; `withContext(Dispatchers.IO)` escape hatch; R2DBC alternative |
| `UnboundedFlowCollect.kt` | kotlin_flows | Infinite source + unbounded collect = OOM; `take(n)` / `withTimeout` / `buffer(DROP_OLDEST)` as fixes |

### `util/` — Utilities

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `Coroutines.kt` | coroutines_webflux, resilience_patterns | `withTimeoutOrNull`; `retryWithBackoff` via `Flow.retryWhen`; `runAllSettled` via `supervisorScope` |

### `config/` — Spring Configuration

| File | Drill Topics | Key Lesson |
|------|-------------|------------|
| `WebClientConfig.kt` | coroutines_webflux | Connection pool; connect timeout vs response timeout; retry with backoff; wrong way documented inline |

---

## Drill Topic Index

Use this when Major assigns a topic and you want to find the relevant code:

- **coroutines_webflux**: `RecommendationsController`, `FeatureClient`, `BlockingInSuspend`, `GlobalScopeLeak`, `BlockingJdbcInWebFlux`, `WebClientConfig`, `Coroutines.kt`
- **recsys**: `RecommendationsController`, `AnnService`, `ProductEncoder`, `Reranker`
- **kafka_hermes**: `ImpressionsConsumer`, `ImpressionRepository`
- **kotlin_flows**: `EventStreamService`, `UnboundedFlowCollect`
- **kotlin_basics**: `dto/`, `ProductEncoder`, `Reranker`, `ImpressionRepository`
- **distributed_systems**: `ImpressionsConsumer`, `ImpressionRepository`
- **resilience_patterns**: `WebClientConfig`, `Coroutines.kt`
- **testing**: `RecommendationsControllerTest`, `ImpressionsConsumerTest`, `EventStreamServiceTest`, `CoroutinesTest`

---

## Key Concepts Quick Reference

### Structured Concurrency

```kotlin
// Parallel calls — total time = max(a, b, c), not a + b + c
coroutineScope {
    val a = async { serviceA.call() }  // starts immediately
    val b = async { serviceB.call() }  // starts immediately
    val c = async { serviceC.call() }  // starts immediately
    Triple(a.await(), b.await(), c.await())
}
```

### Dispatcher Choice

| Work Type | Dispatcher |
|-----------|-----------|
| CPU computation (encoding, ranking) | `Dispatchers.Default` |
| Non-blocking I/O (WebClient, R2DBC) | coroutine default (event loop) |
| Blocking I/O (JDBC, legacy libs) | `Dispatchers.IO` |
| UI updates (Android only) | `Dispatchers.Main` |

### Hermes Status Code Contract

| Situation | HTTP Status | Why |
|-----------|-------------|-----|
| Message processed | 200 | Hermes marks delivered, no retry |
| Message permanently invalid | 200 + dead-letter in body | Hermes must NOT retry malformed messages |
| Transient failure (DB down) | 503 | Hermes retries with backoff |
| Never return | 4xx | Hermes retries 4xx indefinitely — infinite retry loop |

### Flow Backpressure Strategies

| Need | Solution |
|------|----------|
| Limit items from infinite source | `take(n)` |
| Time-box collection | `withTimeout { flow.collect {} }` |
| Decouple fast producer / slow consumer | `buffer(capacity, DROP_OLDEST)` |
| Group items into batches | `chunked(n)` (custom) or `kotlinx.coroutines 1.8+ built-in` |
