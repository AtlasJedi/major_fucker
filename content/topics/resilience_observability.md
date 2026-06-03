# Resilience & Observability — question bank

> Resilience and Observability are the two pillars that separate systems that survive production from systems that collapse under it. Resilience covers the mechanisms that keep a distributed system functional (or gracefully degraded) when things go wrong: circuit breakers, bulkheads, retries, timeouts, rate limiters, and fallbacks — all wired together correctly. Observability is the ability to understand what your system is doing from the outside: metrics, structured logs, and distributed traces are the three pillars. Senior engineers are expected to know not just that these tools exist, but how they work internally, how to configure them for real workloads, where they fail, and how to connect the signals into actionable answers. This bank targets the Resilience4j ecosystem (standard for Spring Boot), Micrometer + Prometheus for metrics, OpenTelemetry for tracing, structured logging with ELK/Loki, and SLO/error-budget thinking.

## Scope

- Resilience4j: CircuitBreaker (count vs time sliding window, state machine, failure-rate and slow-call thresholds)
- Resilience4j: RateLimiter, Bulkhead (semaphore vs thread-pool), Retry (backoff + jitter), TimeLimiter
- Decorator composition order and why it matters
- Failing fast and graceful degradation / fallback strategies
- Timeouts at every hop — why defaults are infinite and why that kills you
- Retry storms, thundering herd, and jitter as the fix
- Idempotency keys enabling safe retries on mutations
- Observability three pillars: metrics, logging, tracing
- Micrometer as a metrics facade; Prometheus registry; /actuator/prometheus
- RED method (Rate, Errors, Duration) and USE method (Utilization, Saturation, Errors)
- Histograms vs summaries in Prometheus — trade-offs and cardinality risks
- Structured/JSON logging, MDC, correlation IDs, centralized ELK/Loki
- OpenTelemetry SDK, spans, W3C traceparent header format and propagation
- Sampling strategies (head-based, tail-based) and their trade-offs
- Health checks: liveness vs readiness in Kubernetes — what each checks and the restart-loop trap
- SLI, SLO, SLA definitions and error budgets
- Symptom-based vs cause-based alerting; alert fatigue
- Chaos engineering basics: fault injection, blast radius, steady-state hypothesis

---

## Q-RESIL-001 [bloom: recall] [level: junior]
**Question:** What is a circuit breaker pattern and what are its three states?
**Model answer:** A circuit breaker wraps a call to an unreliable downstream service. When the failure rate exceeds a threshold it "trips" — subsequent calls fail immediately (fast fail) without actually calling the downstream, returning a fallback. This prevents cascading failures where a slow or dead service holds threads and causes the caller to queue up and eventually die too.

Three states:
- **CLOSED** — normal operation. Calls pass through. Failures are counted in a sliding window. When failure rate >= threshold, transitions to OPEN.
- **OPEN** — calls fail immediately. No actual call is made to the downstream. A fallback is executed. After `waitDurationInOpenState` elapses, transitions to HALF_OPEN.
- **HALF_OPEN** — a limited number of probe calls (`permittedNumberOfCallsInHalfOpenState`) are allowed through. If they succeed, transitions back to CLOSED. If they fail, back to OPEN.

In Java: Resilience4j is the current standard (Hystrix is deprecated/unmaintained — do not name it as your current choice).
**Interview trap:** "What's the difference between circuit breaker and retry?" — Retry handles *transient* failures (network blip, brief 503 that clears quickly) — try again. Circuit breaker handles *sustained* failures — the downstream is down, stop hammering it. They are complementary: use retry for transient errors, circuit breaker to stop retrying when failure is persistent.
**Tags:** resilience, circuit-breaker, resilience4j, patterns

---

## Q-RESIL-002 [bloom: recall] [level: junior]
**Question:** What are the three pillars of observability?
**Model answer:** The three pillars are **metrics**, **logs**, and **traces**. Each answers a different question:

- **Metrics** — numeric aggregates over time (request rate, error rate, latency p99). Cheap to store (just numbers), great for alerting and dashboards. Tools: Micrometer -> Prometheus -> Grafana.
- **Logs** — discrete events with context. Structured (JSON) logs with fields like `traceId`, `userId`, `orderId` allow searching across services. Tools: Logback/Log4j2 -> ELK (Elasticsearch, Logstash, Kibana) or Loki + Grafana.
- **Traces** — the journey of a single request across multiple services. Each service records a span (operation + timing). Spans form a tree (a trace). Tools: OpenTelemetry SDK -> Jaeger / Zipkin / Datadog APM.

The pillars are most powerful when correlated: a Grafana alert fires on high error rate (metric), you click through to logs filtered by that time window, then jump to the trace of a failing request to find which downstream is broken.
**Interview trap:** "Can you replace tracing with just logging?" — Partially. If every log line includes a shared `traceId` that's propagated across service boundaries, you can reconstruct a trace manually by querying logs. But you lose the visual timeline, span hierarchy, and automatic timing — so in practice tracing backends give you much more with much less effort.
**Tags:** observability, metrics, logging, tracing

---

## Q-RESIL-003 [bloom: recall] [level: junior]
**Question:** What is the difference between liveness and readiness probes in Kubernetes, and what does Spring Boot Actuator expose for each?
**Model answer:** Both are HTTP endpoints Kubernetes polls to decide what to do with a pod.

- **Liveness probe** — "Is the process alive and not deadlocked?" If it fails, Kubernetes kills and restarts the pod. It should check only internal process health (no OOM, no deadlock, no infinite loop). **Critical rule:** never check external dependencies (DB, downstream services) in a liveness probe. If your DB is down, liveness fails → pod restarts → DB is still down → pod restarts again → restart loop, service unavailable. The bug is in the DB, not the pod.
- **Readiness probe** — "Is the pod ready to receive traffic?" If it fails, Kubernetes removes the pod from the load balancer endpoints (traffic stops flowing to it) but does NOT restart it. Use for: warmup phase after startup (caches loading), downstream dependency unavailable (temporarily route traffic elsewhere).

Spring Boot Actuator (2.3+) with `management.endpoint.health.probes.enabled=true` exposes:
- `/actuator/health/liveness` — checks `LivenessState` (internal health)
- `/actuator/health/readiness` — checks `ReadinessState` + all registered `HealthIndicator` beans (DB, Redis, etc.)
**Interview trap:** "What does a startup probe do?" — A third probe type: if the startup probe hasn't succeeded yet, Kubernetes won't run liveness/readiness checks. Useful for slow-starting apps (JVM warmup, large caches) — prevents liveness from killing the pod before it's had a chance to fully start.
**Tags:** health-checks, kubernetes, actuator, resilience

---

## Q-RESIL-004 [bloom: recall] [level: junior]
**Question:** What is SLI, SLO, and SLA? Give a concrete example for each.
**Model answer:**
- **SLI (Service Level Indicator)** — a measurable metric that reflects service behavior from the user's perspective. Examples: request success rate, p99 latency, throughput. SLI is the *measurement*.
- **SLO (Service Level Objective)** — an internal target for an SLI. Example: "99.9% of requests return 2xx within 500ms, measured over a rolling 28-day window." SLO is the *goal*. It's owned by the engineering team.
- **SLA (Service Level Agreement)** — a contractual commitment to a customer, usually less strict than the SLO (you set SLO tighter so you have buffer before breaching the SLA). Example: "We guarantee 99.5% availability per month; breach triggers billing credits." SLA is a *contract with financial consequences*.

**Error budget:** `100% - SLO`. If SLO is 99.9% availability, the error budget is 0.1% of time — roughly 43 minutes/month. The budget tracks how much failure you can afford while staying within SLO. When budget is exhausted, freeze new feature work and focus on reliability.
**Interview trap:** "Should your SLO equal your SLA?" — No. Set SLO tighter (e.g., SLO=99.9%, SLA=99.5%). The gap is your safety buffer for incidents, planned maintenance, and monitoring lag. If your SLO equals your SLA, any measurement error or incident immediately breaches the contract.
**Tags:** slo, sli, sla, error-budget, observability

---

## Q-RESIL-005 [bloom: recall] [level: junior]
**Question:** What is structured logging and why is it preferred over plain text logs in a microservices environment?
**Model answer:** Structured logging means emitting logs as machine-parseable key-value records (usually JSON) instead of free-form strings.

Plain text: `2026-06-03 INFO OrderService - Order 1234 placed by user 9876 total 99.00`
Structured (JSON): `{"ts":"2026-06-03T14:22:00Z","level":"INFO","service":"order-service","traceId":"abc123","userId":9876,"orderId":1234,"total":99.00,"msg":"Order placed"}`

Benefits in microservices:
1. **Queryable** — centralized log aggregators (Elasticsearch, Loki) can filter on any field: `userId=9876 AND level=ERROR` returns all errors for that user across all services.
2. **Correlation** — include `traceId` as a field in every line; you can pull all logs for a single request across 10 services by querying one field.
3. **Alerting** — alert on `error_count > 100/min` by parsing a structured field, not by regex-ing free text.
4. **No log parsing failures** — plain text requires fragile regex parsers in Logstash/Filebeat; JSON eliminates that.

In Java/Spring: use `logstash-logback-encoder` library which replaces Logback's default text appender with JSON output. MDC (Mapped Diagnostic Context) fields are included automatically.
**Interview trap:** "What is MDC?" — Mapped Diagnostic Context is a thread-local (or reactive-context) map of key-value pairs that Logback/Log4j2 automatically includes in every log statement on that thread. Use it to store `traceId`, `userId`, `requestId` at the start of a request and every log line in that request gets those fields without you passing them explicitly.
**Tags:** logging, structured-logging, mdc, observability

---

## Q-RESIL-006 [bloom: recall] [level: junior]
**Question:** What is exponential backoff with jitter and why does jitter matter?
**Model answer:** **Exponential backoff** — after each failed retry, wait longer before retrying: `wait = base * 2^attempt`. Example with base=100ms: attempt 1 waits 200ms, attempt 2 waits 400ms, attempt 3 waits 800ms. This gives the downstream time to recover instead of hammering it continuously.

**The problem without jitter:** if 1000 clients all hit the same downstream and it fails at the same moment (e.g., a deployment restart), they all retry at the same time after 200ms, then again at 400ms — synchronized waves of traffic that can prevent recovery. This is called a **thundering herd** or **retry storm**.

**Jitter** adds randomness: `wait = random(0, base * 2^attempt)` (full jitter) or `wait = base * 2^attempt + random(-delta, +delta)` (decorrelated jitter). The clients desynchronize — traffic spreads out over the window instead of spiking simultaneously.

AWS, Google, and Resilience4j all recommend full jitter for most cases. In Resilience4j Retry: configure `enableExponentialBackoff()` and `randomizedWaitFactor`.
**Interview trap:** "When should you NOT retry?" — Never retry non-idempotent operations (POST creating a resource, payment processing) unless the request carries an idempotency key. Never retry on 4xx client errors (400, 401, 403, 404) — those won't change with retry. Retry only on 5xx server errors and network-level failures.
**Tags:** retry, backoff, jitter, resilience, thundering-herd

---

## Q-RESIL-007 [bloom: understand] [level: regular]
**Question:** Explain the two sliding window types in Resilience4j CircuitBreaker and how the failure rate threshold works in each.
**Model answer:** Resilience4j CircuitBreaker tracks failures using a sliding window. There are two types:

**COUNT_BASED** — the window is the last N calls. `slidingWindowSize=10` means: look at the last 10 calls. If 6+ failed, failure rate = 60%. If `failureRateThreshold=50`, the circuit trips. Simple but doesn't account for time: if calls are infrequent, 10 calls might span 10 minutes and a threshold trip might be stale.

**TIME_BASED** — the window is the last N seconds. `slidingWindowSize=10` means: look at all calls in the last 10 seconds. Better for high-frequency services because it reflects recent reality. Internally implemented as a circular array of per-second buckets.

Both windows use the same thresholds:
- `failureRateThreshold` (default 50%) — percentage of calls that failed (exception thrown or non-2xx, depending on config)
- `slowCallRateThreshold` — percentage of calls that exceeded `slowCallDurationThreshold`. A slow call counts as a failure for the purposes of tripping the circuit. This is crucial: a downstream that responds in 30s (holding threads) is just as dangerous as one that throws exceptions.
- `minimumNumberOfCalls` — the circuit won't trip until at least this many calls have been recorded in the window. Prevents flapping on cold start with 1 call.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 2s
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 5
        minimumNumberOfCalls: 10
```
**Interview trap:** "What counts as a failure by default?" — By default, any exception thrown by the decorated function counts. You can configure `recordExceptions` and `ignoreExceptions` to fine-tune: e.g., ignore `BusinessException` (client error, not a downstream problem) but record `TimeoutException` and `ConnectException`.
**Tags:** circuit-breaker, resilience4j, sliding-window, configuration

---

## Q-RESIL-008 [bloom: understand] [level: regular]
**Question:** What is the difference between Resilience4j Bulkhead semaphore mode and thread-pool mode? When do you use each?
**Model answer:** Both Bulkhead types limit concurrent calls to a downstream to prevent resource exhaustion, but they work at different levels.

**Semaphore Bulkhead** (`BulkheadConfig`) — maintains a counter of concurrent calls. When `maxConcurrentCalls` is reached, new calls are rejected immediately (or after `maxWaitDuration`). Uses the **caller's thread** — no new threads are created. Zero thread overhead. Appropriate for reactive/non-blocking code (WebFlux, Kotlin coroutines) or synchronous code where thread pools are managed elsewhere. Reject is fast.

**ThreadPool Bulkhead** (`ThreadPoolBulkheadConfig`) — runs the decorated function in a **dedicated thread pool** with bounded queue and bounded pool size. The caller submits to the pool and gets back a `CompletableFuture`. When the pool + queue is full, new submissions are rejected. Use for blocking calls (RestTemplate, JDBC) — the caller's thread is freed immediately. The thread pool is the isolation boundary: a slow downstream exhausts its own pool, not the common thread pool serving other requests.

| | Semaphore | ThreadPool |
|---|---|---|
| Thread overhead | None | Dedicated pool per downstream |
| Caller thread freed? | No | Yes |
| Best for | Non-blocking / reactive | Blocking I/O |
| Reject mechanism | Counter check | Queue/pool full |

In Spring Boot (non-reactive, with WebClient / async calls): semaphore is usually sufficient. For legacy `RestTemplate` calls in a servlet stack: thread-pool provides better isolation.
**Interview trap:** "Can you use TimeLimiter with semaphore bulkhead?" — Yes. TimeLimiter wraps a `CompletableFuture` and cancels it if it exceeds `timeoutDuration`. But TimeLimiter requires that the decorated operation returns a `CompletableFuture` — it doesn't work with synchronous blocking calls directly. You'd use ThreadPool Bulkhead (which returns `CompletableFuture`) and then wrap with TimeLimiter.
**Tags:** bulkhead, resilience4j, thread-pool, semaphore, isolation

---

## Q-RESIL-009 [bloom: understand] [level: regular]
**Question:** Explain the RED method and the USE method for metrics. What kind of service is each best suited for?
**Model answer:** These are two mental frameworks for deciding which metrics to measure and alert on.

**RED method** (Tom Wilkie / Weaveworks) — for **request-driven services** (APIs, microservices serving synchronous requests):
- **R**ate — requests per second. Is traffic normal? Are we getting hit?
- **E**rrors — error rate (5xx, timeouts, failed business operations). What fraction of requests are failing?
- **D**uration — latency distribution (p50, p95, p99). How slow are we for users?

RED captures the user experience directly. A RED dashboard is the first thing you look at for a service regression.

**USE method** (Brendan Gregg) — for **resource/infrastructure components** (CPU, memory, disk, connection pools, message queues):
- **U**tilization — what % of the resource's capacity is being used? (CPU 85%, connection pool 90/100)
- **S**aturation — is anything queuing up waiting for the resource? (thread queue depth, GC pause time)
- **E**rrors — resource-level errors (disk write errors, NIC drops, connection refused)

USE is for finding resource bottlenecks, not for understanding user-facing behavior.

**In practice:** use RED on your service's HTTP layer, use USE on JVM metrics (heap utilization, GC saturation), DB connection pool, and Kafka consumer lag. Both exposed through Micrometer → Prometheus → Grafana.
**Interview trap:** "Where do SLIs fit relative to RED?" — SLIs are typically derived from RED metrics. Your SLI for availability might be `1 - (error_rate)`. Your SLI for latency might be `fraction of requests completing under 300ms`. RED gives you the raw signals; SLO is the threshold you hold them to.
**Tags:** metrics, red-method, use-method, prometheus, micrometer, observability

---

## Q-RESIL-010 [bloom: understand] [level: regular]
**Question:** What is the difference between a Prometheus histogram and a summary? What is cardinality and why is high cardinality dangerous?
**Model answer:** Both histogram and summary measure the distribution of a value (e.g., request latency), but they handle quantile calculation differently.

**Histogram** — the client records observations into pre-configured buckets (e.g., ≤0.1s, ≤0.5s, ≤1s, ≤5s, +Inf). Each bucket is a counter. Prometheus server calculates approximate quantiles at query time using `histogram_quantile()`. **Aggregatable across instances** — you can sum bucket counts from multiple pods and compute fleet-wide p99. The trade-off: quantile accuracy depends on bucket boundaries; choose buckets to match your expected latency range.

**Summary** — the client calculates exact quantiles (e.g., p50, p95, p99) locally using a streaming algorithm. More accurate but **NOT aggregatable** — you cannot meaningfully average p99 from 10 pods. Summary is useful for per-instance accuracy when aggregation isn't needed.

**Recommendation:** use histograms in microservices because you almost always want fleet-wide quantiles. Micrometer uses histograms by default with Prometheus backend.

**Cardinality** — the number of distinct time series in Prometheus, determined by the number of unique label value combinations. `http_requests_total{service="order", status="200", path="/orders"}` — each unique combination of label values creates a separate time series.

High cardinality danger: if you add a label like `userId` or `orderId` (unbounded, millions of values), Prometheus creates millions of time series. This exhausts memory, slows ingestion, and breaks queries. **Rule: never use high-cardinality values as Prometheus labels** — no user IDs, request IDs, IP addresses, or UUIDs.
**Interview trap:** "How would you track per-user latency without breaking Prometheus?" — Don't put userId in Prometheus labels. Instead: log per-request data to structured logs with userId, and query Elasticsearch/BigQuery for per-user analysis. Use Prometheus only for aggregate signals.
**Tags:** metrics, prometheus, histogram, summary, cardinality, micrometer

---

## Q-RESIL-011 [bloom: understand] [level: regular]
**Question:** How does W3C traceparent header propagation work in OpenTelemetry? Walk through what happens when a request enters Service A and calls Service B.
**Model answer:** W3C `traceparent` is the standard HTTP header for distributed trace context propagation, replacing Zipkin's B3 headers. Format: `00-{traceId}-{spanId}-{flags}` where `00` is the version, `traceId` is 16-byte hex (128-bit), `spanId` is 8-byte hex (64-bit), and `flags` is a byte for sampling (bit 0 = sampled).

Flow:
1. **Service A receives an HTTP request** (no `traceparent` header — it's the root). OpenTelemetry instrumentation creates a new trace: generates `traceId` (e.g., `4bf92f3577b34da6a3ce929d0e0e4736`) and a root `spanId`. Sets sampling decision. Adds `traceId` to MDC so logs are correlated.
2. **Service A's span starts** — records the timestamp. `span.setTag("http.method", "GET")` etc.
3. **Service A calls Service B via HTTP** — OpenTelemetry's `TextMapPropagator` injects context into outgoing headers: `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`. The `spanId` here is Service A's span ID — it becomes the **parent span ID** for Service B's span.
4. **Service B receives the call** — OpenTelemetry extracts `traceparent`, creates a **child span** with the same `traceId` and a new `spanId`, parent = Service A's span. Service B's work is recorded as a child of A's span.
5. **Both spans are reported** to the collector (OTLP to Jaeger/Zipkin/etc.) — the backend assembles them into a tree using `traceId` as the key.

In Spring Boot 3: add `io.micrometer:micrometer-tracing-bridge-otel` + an exporter dependency. Auto-configuration handles everything; MDC is populated automatically.
**Interview trap:** "What does the sampling flag mean? If Service A decides not to sample, what happens in Service B?" — The `flags` byte bit 0 indicates whether this trace is being sampled. OpenTelemetry propagates the sampling decision downstream — if Service A decided to not sample (bit 0 = 0), Service B should respect that and not export the span either. This is **head-based sampling** — the root makes the decision and all downstream services honor it.
**Tags:** opentelemetry, tracing, traceparent, distributed-tracing, context-propagation

---

## Q-RESIL-012 [bloom: understand] [level: regular]
**Question:** What is the difference between head-based sampling and tail-based sampling in distributed tracing? What are the trade-offs?
**Model answer:** Sampling decides which traces to record and export — recording 100% is too expensive at scale.

**Head-based sampling** — the sampling decision is made at the **root span** (the first service receiving the request) before the trace is complete. Decision is propagated to all downstream services via `traceparent` flags. Implementations: probabilistic (record N% of all traces), rate-limited (record N traces/second).

Pros: simple, low overhead, no buffering needed, consistent (all spans of a trace are either recorded or not).
Cons: you don't know at sampling time whether this will be an interesting (failed, slow) trace. You'll undersample rare errors at low percentages.

**Tail-based sampling** — the sampling decision is made **after the entire trace is complete**, at a collector that buffers the spans. Decision logic can look at the full trace: "keep all traces with errors, keep all traces >2s, keep 1% of everything else." OpenTelemetry Collector supports tail-based sampling via the `tail_sampling` processor.

Pros: intelligent — you can guarantee 100% capture of errors and slow traces.
Cons: the collector must buffer all spans until the trace is complete (memory pressure), complex to operate, requires all spans to route to the same collector instance (or sticky routing).

**In practice:** start with head-based probabilistic (1-10% depending on volume) for general traces, and add a separate path for errors (always sample on `HTTP status >= 500` or exception in span). Tail-based sampling is the right answer for mature observability platforms with high traffic.
**Interview trap:** "Your service does 50k req/sec. You're told to add tracing. What sampling rate would you suggest?" — Start with 1% (500 traces/sec is already substantial storage). Add 100% sampling for errors and for requests exceeding SLO thresholds. Use adaptive sampling if the collector supports it. Never commit to a fixed rate without understanding the storage and query cost.
**Tags:** tracing, sampling, opentelemetry, observability, head-based, tail-based

---

## Q-RESIL-013 [bloom: apply] [level: regular]
**Question:** You're building a Spring Boot service that calls a payment provider via HTTP. Configure Resilience4j to protect this call with a circuit breaker, retry with jitter, and a timeout. Show configuration and annotation usage.
**Model answer:**
```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 3s
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 5
  retry:
    instances:
      paymentService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        randomizedWaitFactor: 0.5      # jitter: actual wait = wait * [1-0.5, 1+0.5]
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.example.PaymentDeclinedException  # 4xx-like, don't retry
  timelimiter:
    instances:
      paymentService:
        timeoutDuration: 3s
        cancelRunningFuture: true
```

```java
@Service
public class PaymentClient {

    // Decorator order matters: TimeLimiter wraps CircuitBreaker wraps Retry
    // Outer annotation is evaluated first → TimeLimiter → CircuitBreaker → Retry
    @TimeLimiter(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    public CompletableFuture<PaymentResult> charge(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> paymentHttpClient.charge(request));
    }

    private CompletableFuture<PaymentResult> paymentFallback(
            PaymentRequest request, Exception ex) {
        log.warn("Payment service unavailable, returning fallback", ex);
        return CompletableFuture.completedFuture(PaymentResult.serviceUnavailable());
    }
}
```

Key points: the fallback method must have the **same return type** and the **same parameters + Exception**. `cancelRunningFuture: true` actually cancels the underlying future on timeout (important for cleanup). Retry fires first on failure, then if retries are exhausted the exception propagates to CircuitBreaker which records the failure.
**Interview trap:** "What happens to the retry count when the circuit is OPEN?" — When the circuit is OPEN, calls fail immediately with `CallNotPermittedException` before reaching the actual operation. The Retry decorator will catch this and retry — but each retry attempt hits the open circuit and fails again immediately, burning through all retry attempts in milliseconds. To avoid this, configure Retry to ignore `CallNotPermittedException`: add it to `ignoreExceptions`. The circuit being open is not a transient failure worth retrying.
**Tags:** resilience4j, circuit-breaker, retry, timelimiter, spring-boot, configuration

---

## Q-RESIL-014 [bloom: apply] [level: senior]
**Question:** Explain the correct decorator composition order in Resilience4j when combining Retry, CircuitBreaker, and TimeLimiter. What breaks if you get the order wrong?
**Model answer:** Resilience4j decorators are applied from **outermost to innermost** — the outermost decorator "sees" the result of everything inside it. When using `@annotations` on a method in Spring Boot, annotations are applied in this precedence order (outermost first):

**Correct order (outermost → innermost):**
```
TimeLimiter → CircuitBreaker → RateLimiter → Bulkhead → Retry → (actual call)
```

Reasoning:
- **TimeLimiter outermost** — the time budget applies to the entire operation including retries. You want the total time (retries included) bounded.
- **CircuitBreaker inside TimeLimiter** — if the circuit is open, fail fast before burning time budget. The circuit breaker records outcomes; it should see what actually happened after retries.
- **Retry innermost** (relative to CircuitBreaker) — retry individual calls; after retries exhausted, the final failure is reported to the circuit breaker.

**What breaks with wrong order:**

`Retry → CircuitBreaker` (retry outside CB): each retry attempt is a separate call into the circuit breaker. If the CB is CLOSED, retries proceed normally. But if the CB opens mid-retry-sequence, subsequent retries hit an open CB and throw `CallNotPermittedException`, which the retry then... retries again. Each `CallNotPermittedException` is another failure recorded by the CB. This can cause a feedback loop where retries accelerate the CB staying open.

`CircuitBreaker → TimeLimiter` (CB outside timeout): the circuit breaker records a failure only after the timeout fires. If your downstream hangs for 30s and TimeLimiter cancels after 3s, the CB records one slow-call failure at 3s. That's actually fine. But if it's the other way and CB is inside TimeLimiter, the CB might record the outcome differently from what the caller sees. Generally keep TimeLimiter outermost.

In programmatic API (when not using annotations), build the chain explicitly:
```java
Supplier<String> decorated = Decorators.ofSupplier(this::callDownstream)
    .withRetry(retry)              // innermost
    .withCircuitBreaker(cb)
    .withTimeLimiter(tl, scheduler) // outermost
    .decorate();
```
**Interview trap:** "Does the order matter for Bulkhead?" — Yes. Bulkhead should be outside Retry so that each retry attempt consumes a slot. If Bulkhead is inside Retry, the slot is released between retries, which may allow more concurrent retries than intended.
**Tags:** resilience4j, decorator-composition, circuit-breaker, retry, timelimiter, senior

---

## Q-RESIL-015 [bloom: apply] [level: senior]
**Question:** Design a graceful degradation strategy for a product recommendations service that is called on every page view of your e-commerce homepage. What happens when the recommendations service is down?
**Model answer:** Graceful degradation means the system continues to function at reduced quality rather than failing completely. For recommendations on a high-traffic homepage, the stakes are high: a 500ms timeout here affects every user.

**Degradation levels (most to least preferred):**

1. **Stale cache hit** — serve the last-known-good recommendations from a local in-process cache (Caffeine) or distributed cache (Redis). TTL: 10-15 minutes is fine for recommendations. Cache miss → try live service → fallback to next level. This is the preferred degradation.

2. **Personalized fallback from a cheaper source** — if the recommendations service is down but a simpler "top items" endpoint is available (different service or a DB query), serve that. Less personalized but still relevant.

3. **Static editorial / bestsellers** — return a pre-configured list of top 10 products hardcoded or stored in config. Non-personalized but valid content.

4. **Empty / hidden widget** — hide the recommendations widget entirely. Not ideal for revenue but better than an error or spinner that hangs indefinitely.

**Implementation:**
```java
@CircuitBreaker(name = "recommendations", fallbackMethod = "recommendationsFallback")
@TimeLimiter(name = "recommendations")
public CompletableFuture<List<Product>> getRecommendations(String userId) {
    return CompletableFuture.supplyAsync(() -> recommendationsClient.fetch(userId));
}

private CompletableFuture<List<Product>> recommendationsFallback(
        String userId, Exception ex) {
    // Try local cache first
    List<Product> cached = localCache.getIfPresent(userId);
    if (cached != null) return CompletableFuture.completedFuture(cached);
    // Fall back to editorial bestsellers
    return CompletableFuture.completedFuture(editorialService.getBestsellers());
}
```

**Operational considerations:**
- Set a tight timeout (200-500ms) — recommendations are nice-to-have, not blocking.
- Monitor cache hit rate on fallback — sustained high fallback rate = the real service has a problem that needs alerting.
- Never let the fallback itself be slow — if Redis is also slow, the cache fallback adds latency. Put a tight timeout on the cache read too.
**Interview trap:** "What if the fallback itself fails?" — Fallback should be designed to never throw (or wrap in try-catch and return empty). If the fallback can also fail, add a second level of defense: a `@CircuitBreaker` on the editorial service too, with a final fallback that returns `Collections.emptyList()`. At the outermost level, the recommendations widget simply doesn't render.
**Tags:** graceful-degradation, fallback, circuit-breaker, cache, senior, ecommerce

---

## Q-RESIL-016 [bloom: apply] [level: senior]
**Question:** Your service started experiencing retry storms after a brief database outage. Explain what caused them and what changes you make to prevent recurrence.
**Model answer:** **What happened:** when the DB came back up after a brief outage, all clients that had been retrying (with synchronized exponential backoff) fired simultaneously — a thundering herd. The sudden synchronized load spike can exceed the DB's capacity, causing it to fail again, causing more retries, creating a self-reinforcing loop that extends the outage far beyond the initial brief failure.

**Root causes (common combination):**
1. No jitter on retry wait times → all threads retry at exactly the same moment
2. No circuit breaker → retries continued indefinitely even while the DB was clearly down
3. No idempotency checks → duplicate writes on recovery made it worse
4. No connection pool saturation protection → all threads queued on DB connection acquisition

**Fixes:**

1. **Add jitter** to all retry backoff configurations:
```java
RetryConfig.custom()
    .waitDuration(Duration.ofMillis(500))
    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2.0, 0.5))
    .build();
```

2. **Add a circuit breaker** wrapping DB calls (not just HTTP calls). Use `@CircuitBreaker` on repository methods or configure it at the datasource level. This stops retries when the DB is clearly down.

3. **Idempotency**: ensure all mutations are idempotent (check-before-write or use a unique constraint + idempotency key). When recovery happens and duplicate requests arrive, they should be safe no-ops.

4. **Connection pool limits** (`HikariCP`): set `maximumPoolSize`, `connectionTimeout` (fail fast when pool exhausted), `keepaliveTime` (detect dead connections early).

5. **Slow ramp-up after outage**: after circuit goes HALF_OPEN, use `permittedNumberOfCallsInHalfOpenState=2` (not 20) to probe gently before fully reopening.

6. **Alert on circuit state changes** — circuit opening is a signal worth paging on.
**Interview trap:** "Would a rate limiter help here?" — Yes, as a complementary tool. A RateLimiter on outbound DB calls puts an upper bound on how many calls/second reach the DB during recovery, regardless of how many threads are trying to retry. Resilience4j `RateLimiter` can be composed in the decorator chain.
**Tags:** retry-storm, thundering-herd, jitter, circuit-breaker, resilience, senior, database

---

## Q-RESIL-017 [bloom: apply] [level: senior]
**Question:** How does idempotency enable safe retries? Describe how you would implement an idempotency key for a payment API endpoint.
**Model answer:** An operation is **idempotent** if applying it multiple times produces the same result as applying it once. Safe HTTP methods (GET, HEAD, PUT, DELETE) are idempotent by definition. POST is not — submitting the same POST twice can create two records.

Retries are only safe on idempotent operations. Without idempotency on a payment endpoint, a network timeout that causes the client to retry could result in the customer being charged twice even though the first charge succeeded (the response was lost in transit).

**Idempotency key pattern:**
1. Client generates a unique `Idempotency-Key` header (UUID) for each logical operation.
2. Server: before processing, check if this key already exists in an idempotency store (Redis, DB table).
3. If key exists: return the cached response from the previous successful execution. No re-processing.
4. If key is new: execute the operation, persist the response alongside the key (atomically or with a short lock), return response.

```java
@PostMapping("/payments")
public ResponseEntity<PaymentResult> charge(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PaymentRequest request) {
    
    // Check cache
    Optional<PaymentResult> cached = idempotencyStore.get(idempotencyKey);
    if (cached.isPresent()) {
        return ResponseEntity.ok(cached.get());
    }
    
    // Execute (use distributed lock to prevent concurrent duplicate execution)
    try (var lock = lockService.acquire(idempotencyKey, Duration.ofSeconds(30))) {
        // Double-check after acquiring lock
        cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent()) return ResponseEntity.ok(cached.get());
        
        PaymentResult result = paymentService.process(request);
        idempotencyStore.put(idempotencyKey, result, Duration.ofHours(24));
        return ResponseEntity.ok(result);
    }
}
```

Key operational details: TTL on the idempotency store (24h is common for payments). The distributed lock prevents two concurrent requests with the same key from both executing the payment before either has persisted the result.
**Interview trap:** "What if the server crashes after charging but before persisting the idempotency key?" — This is the fundamental distributed systems problem. Solutions: (a) use the payment provider's own idempotency key (Stripe, Adyen all support this — if they see the same key they return the same result), (b) use an outbox + two-phase process (first persist "charging intent" with key, then charge, then mark complete), (c) accept a very small risk of duplicates and detect them out-of-band via reconciliation.
**Tags:** idempotency, retry, payments, distributed-systems, senior

---

## Q-RESIL-018 [bloom: analyze] [level: senior]
**Question:** Walk through how you would implement structured logging with trace ID correlation in a Spring Boot 3 service using Micrometer Tracing and OpenTelemetry. What's in the MDC and how does it get there?
**Model answer:** In Spring Boot 3, the tracing stack is: **OpenTelemetry SDK** (instrumentation) → **Micrometer Tracing bridge** (facade between OTel and Spring) → **Logback** (logging) with `logstash-logback-encoder` (JSON output).

**Dependencies:**
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**How MDC gets populated:** Micrometer Tracing registers a `SpanHandler` (or `ObservationHandler`) that, when a span is started, calls `MDC.put("traceId", span.traceId())` and `MDC.put("spanId", span.spanId())`. Spring Boot's auto-configuration wires this handler automatically. Every subsequent `log.info(...)` call on the same thread includes these MDC keys in the log event.

For reactive code (WebFlux): MDC is thread-local and doesn't survive thread switches. Micrometer Tracing uses Reactor's `Context` to propagate trace data, and a Reactor `HookOnEachOperator` re-populates MDC on each operator that executes.

**Logback JSON config (`logback-spring.xml`):**
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyNames>traceId,spanId,userId,requestId</includeMdcKeyNames>
    </encoder>
</appender>
```

Output per log line:
```json
{
  "@timestamp": "2026-06-03T14:22:00.123Z",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "message": "Order placed",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "userId": "9876",
  "orderId": "1234"
}
```

**Adding custom MDC fields:**
```java
try (var ignored = MDC.putCloseable("userId", userId)) {
    orderService.placeOrder(request);
} // MDC cleared automatically
```

Or use Micrometer's `Observation.createNotStarted("order.place", registry).contextualName("...").observe(...)` to add custom tags that appear in both traces and metrics.
**Interview trap:** "If you have traceId in logs and in traces, how do you correlate them in Grafana?" — In Grafana, configure a "derived field" in the Loki/Elasticsearch datasource: when a log line contains `traceId`, make it a clickable link to Jaeger/Tempo using the trace ID value. This creates one-click navigation from a log line to the full distributed trace. Grafana Tempo supports this out of the box.
**Tags:** logging, tracing, mdc, micrometer, opentelemetry, spring-boot-3, structured-logging, senior

---

## Q-RESIL-019 [bloom: analyze] [level: senior]
**Question:** Describe symptom-based vs cause-based alerting. Why do most teams suffer from alert fatigue and how do you fix it?
**Model answer:** **Cause-based alerting** — alerting on the internal state of your infrastructure: "CPU > 85%", "disk > 90% full", "DB connection pool > 80% utilized". The problem: these causes don't necessarily indicate a user is experiencing an issue. CPU at 85% might be normal during a batch job. It also requires you to know all possible causes in advance — you'll miss novel failure modes.

**Symptom-based alerting** — alerting on what users actually experience: "error rate > 1%", "p99 latency > 2s", "availability < 99.9% in the last 5 minutes". These are derived from SLIs against SLOs. If users are impacted, you'll alert. If they're not, you won't.

**Alert fatigue** — when alerts fire too frequently or for things that don't require action, engineers start ignoring them. The pager becomes noise. The cycle: cause-based alert fires at 2am → engineer wakes up → CPU is 85% but service is healthy → engineer does nothing → next morning engineers start filtering alerts as noise → a real incident gets missed.

**Fixes:**

1. **Alert on SLO burn rate**, not causes. "Error budget burn rate > 14x in last 5 minutes" means you'll exhaust your monthly error budget in 30 minutes — page. "CPU > 85%" — log it, don't page.

2. **Multi-window burn rate** (Google SRE book): combine a fast burn alert (5min window) with a slow burn alert (1h window) to catch both sudden spikes and slow degradations without false positives.

3. **Tiered severity**: Page (immediate action required) vs ticket (fix before next business day) vs log (investigate if you're bored).

4. **Regular alert review**: if an alert fires and the response is always "it's fine, ignore it", delete or raise the threshold. Every live alert should have a runbook.

5. **Cause-based metrics as dashboards, not pages**: put CPU/memory/disk on dashboards for post-incident investigation, not as paging alerts.
**Interview trap:** "What's the difference between an alert that's too sensitive and one that's too slow?" — Too sensitive = high false positive rate = alert fatigue, engineers ignore pages. Too slow = by the time it fires, you've already breached SLO and users are suffering. The SLO burn rate approach solves both: a sustained burn fires early (before SLO breach) but doesn't fire on transient spikes that self-resolve.
**Tags:** alerting, slo, observability, alert-fatigue, senior, monitoring

---

## Q-RESIL-020 [bloom: apply] [level: senior]
**Question:** How does the Resilience4j RateLimiter work, and how is it different from a circuit breaker? Show a configuration example and explain the internal mechanism.
**Model answer:** A **RateLimiter** limits how many calls can be made in a time period, regardless of whether they succeed or fail. It protects against overloading a downstream service or enforcing a quota on outbound calls (e.g., a third-party API with 100 req/sec limit).

**Internal mechanism — token bucket / atomic permissions:** Resilience4j uses a refresh-based model. Every `limitRefreshPeriod` (e.g., 1 second), the number of available permissions is reset to `limitForPeriod` (e.g., 100). A call acquires one permission. If no permissions are available, the call waits up to `timeoutDuration`. If still no permission after the timeout, it throws `RequestNotPermitted`.

This is a fixed-window rate limiter, not a sliding window — there's a burst problem at window boundary (100 at the end of one second + 100 at the start of the next = 200 in a ~0ms window). For smoother rate control, use a token bucket implementation or put a RateLimiter in front of a Bulkhead.

```yaml
resilience4j:
  ratelimiter:
    instances:
      externalApi:
        limitForPeriod: 100          # permissions per refresh period
        limitRefreshPeriod: 1s       # reset every second
        timeoutDuration: 0ms         # don't wait — fail fast if limit hit
```

```java
@RateLimiter(name = "externalApi", fallbackMethod = "apiLimitFallback")
public ExternalResponse callApi(String param) {
    return externalClient.call(param);
}
```

**RateLimiter vs CircuitBreaker:**
| | RateLimiter | CircuitBreaker |
|---|---|---|
| Trigger | Quota/throughput limit | Failure rate / slow calls |
| Purpose | Control call rate (client-side quota) | Stop calling a failing service |
| State | Permissions counter | CLOSED/OPEN/HALF_OPEN state machine |
| Response | `RequestNotPermitted` when quota exceeded | `CallNotPermittedException` when OPEN |

Use both together: RateLimiter ensures you don't exceed a third-party quota; CircuitBreaker stops calling when the third-party is broken.
**Interview trap:** "How do you implement distributed rate limiting across multiple service instances?" — Resilience4j RateLimiter is in-process and per-instance. For distributed rate limiting (all instances share one quota), use Redis with a Lua script (atomic INCR + TTL) or the `spring-cloud-gateway` built-in Redis-backed RateLimiter. Each instance's request atomically decrements a shared counter in Redis.
**Tags:** rate-limiter, resilience4j, throttling, senior

---

## Q-RESIL-021 [bloom: analyze] [level: master]
**Question:** You have a chain of four microservices: API Gateway → Service A → Service B → Service C. Each hop has a circuit breaker and a retry configured. A developer adds retry(maxAttempts=3) to every hop. What could go wrong? Calculate the worst-case number of calls to Service C.
**Model answer:** This is the **retry amplification** (or multiplicative retry) problem. When retries are configured at every layer, failures multiply across hops.

**Worst case calculation:**
- API Gateway retries 3 attempts to Service A
- Service A retries 3 attempts to Service B per incoming call
- Service B retries 3 attempts to Service C per incoming call

One client request → API Gateway tries 3 times to Service A = **3 calls to A**
Each call to A tries 3 times to Service B = **3 × 3 = 9 calls to B**
Each call to B tries 3 times to Service C = **9 × 3 = 27 calls to C**

So 1 user request can generate **27 calls to Service C**. If the system is already under stress (Service C is slow), this amplification makes the problem 27x worse. The circuit breakers won't help if C is slow but not yet tripping thresholds.

**What breaks:**
- Service C gets stampeded by retry waves
- The slow calls from C hold threads in B, exhaust B's thread pool, B starts failing, A's retries all fail, gateway gives up → all 27 calls wasted
- If C is recovering from an incident, this can prevent recovery (the retry waves overwhelm it just as it comes back up)

**Fixes:**
1. **Retry at most one layer** — typically the outermost (API Gateway or the calling client). Interior services should fail fast to the caller, not retry internally.
2. If interior retries are needed, **keep maxAttempts low** (2, not 3) and **ensure idempotency at every layer**.
3. **Use circuit breakers properly** — the CB at Service A should trip after B consistently fails, stopping A from retrying toward B.
4. **Add jitter at every layer** to desynchronize the retry waves.
5. **Consider no interior retries** and rely solely on the circuit breaker to fast-fail, letting the caller (API Gateway or the client SDK) handle retry logic.
6. **Cap total outstanding requests** with Bulkhead at each service — even if retries multiply, Bulkhead ensures the multiplication can't exhaust all threads.
**Interview trap:** "What if the circuit breaker at Service A opens? Does that fix the problem?" — Partially. Once A's CB opens for the B circuit, calls to B stop — but only after the CB has seen enough failures to trip. During the trip-delay window you're still amplifying. And if the CB opens at A, A's fallback fires — the API Gateway might still retry A's fallback responses (which succeed) without knowing B is broken. Full protection requires: retry at one layer only, CBs everywhere, and careful fallback design.
**Tags:** retry-amplification, resilience, circuit-breaker, master, distributed-systems, microservices

---

## Q-RESIL-022 [bloom: analyze] [level: master]
**Question:** Design the observability strategy for a new microservice from day one. What do you instrument, what metrics do you define as SLIs, how do you structure logs, how do you configure tracing, and what are your initial alerts?
**Model answer:** **Philosophy:** build observability in from the start, not bolted on. Instrument at the framework level first (auto-instrumentation), then add business-level signals.

**Metrics (Micrometer → Prometheus):**

Auto-instrumented by Spring Boot Actuator:
- JVM: heap usage, GC pause duration/frequency, live threads, class loading
- HTTP server: `http_server_requests` (rate, errors, duration by endpoint + status code)
- Connection pool (HikariCP): active/idle/pending connections
- Resilience4j: CB state, call outcomes, retry count

Custom business metrics:
```java
// Order placed rate (business KPI)
Counter.builder("orders.placed")
    .tag("channel", channel)
    .register(meterRegistry);

// Order processing duration (business SLI)
Timer.builder("orders.processing.duration")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry);
```

SLIs: `http_server_requests` error rate and p99 latency per endpoint. These are the source for SLO dashboards.

**Logging:**
- JSON output via `logstash-logback-encoder`
- MDC: `traceId`, `spanId`, `userId`, `requestId`, `service` (set once via startup property)
- Log levels: ERROR for exceptions that reach the error handler; WARN for expected failure paths (CB fallback hit, retry exhausted); INFO for business events (order placed); DEBUG for detailed flow (disabled in production, enabled per-request via dynamic log level)
- Correlation: every ERROR log includes enough context to find the trace in Jaeger

**Tracing:**
- OpenTelemetry auto-instrumentation (Spring Boot starter) — instruments all HTTP incoming/outgoing, JDBC, Redis
- Exporter: OTLP to collector, then to Jaeger/Tempo
- Sampling: 10% head-based for normal traffic, 100% for errors (configure in OTel Collector `tail_sampling` processor)
- Custom spans for expensive business operations:
```java
Observation.createNotStarted("order.enrich", observationRegistry)
    .observe(() -> productEnrichmentService.enrich(order));
```

**Initial alerts (symptom-based):**
1. Error rate > 1% for 5 minutes → page (high severity)
2. p99 latency > SLO threshold for 10 minutes → page
3. Error budget burn rate > 10x in 1 hour → page
4. Circuit breaker OPEN state for > 2 minutes → page (service degraded)
5. HikariCP pending connections > 5 for 2 minutes → ticket (resource pressure)
6. Kafka consumer lag > 10k messages for 5 minutes → ticket (processing falling behind)

**Dashboards:**
- RED dashboard (rate, errors, duration per endpoint)
- Circuit breaker state panel
- JVM and connection pool USE dashboard
- Business KPIs (orders/min, conversion rate)
**Interview trap:** "Your p99 latency alert fires at 3am. Walk me through how you investigate." — (1) Check the trace link in the alert → find slow traces in Jaeger. (2) Identify which span is slow (DB? downstream service?). (3) Check metrics for that downstream (CB state, error rate). (4) Check structured logs for that traceId for context (exception messages, retry count). (5) Check USE metrics for resources (connection pool saturation, GC pause spike). The three pillars guide the diagnosis: metric fires the alert, trace locates the slow hop, logs explain what happened.
**Tags:** observability, slo, metrics, logging, tracing, master, design, spring-boot

---

## Q-RESIL-023 [bloom: analyze] [level: master]
**Question:** What is chaos engineering? Describe how you would run a chaos experiment to validate the circuit breaker protecting your payment service, following the steady-state hypothesis approach.
**Model answer:** **Chaos engineering** is the practice of intentionally injecting failures into a system in a controlled way to find weaknesses before real incidents do. It's not random destruction — it's scientific experimentation: form a hypothesis about how the system should behave, inject a fault, observe whether the hypothesis holds, fix gaps found.

**Steady-state hypothesis:** before injecting anything, define what "normal" looks like as measurable metrics. Example: "Homepage conversion rate stays above 3%, p99 latency is below 500ms, and no errors are visible to users." If the experiment doesn't disturb these, the system is resilient to that fault class.

**Experiment: circuit breaker validation for payment service**

1. **Steady state baseline**: confirm the system is healthy. Dashboard shows: payment success rate = 99.8%, homepage conversion = 3.2%, p99 = 320ms.

2. **Hypothesis**: "If the payment service becomes unresponsive, the circuit breaker opens within 10 seconds, the checkout page shows a 'try again' fallback, and conversion rate drops no more than 30% (not to zero)."

3. **Fault injection**: use a chaos tool (Chaos Monkey for Spring Boot, Chaos Mesh for Kubernetes, Toxiproxy for network-level faults) to make the payment service unreachable or extremely slow:
   - Network level: Toxiproxy injects 10s latency on the payment connection
   - Application level: `@LatencyAssault` with Spring Boot Chaos Monkey

4. **Observation**:
   - CB state metric: did it transition to OPEN within the expected window?
   - Is the fallback being served (metric: `fallback_invocations` counter)?
   - Are users seeing errors in the checkout page, or a graceful degraded message?
   - Is conversion rate dropping gracefully or to zero?
   - Are other services unaffected (DB, catalog, cart — bulkhead validation)?

5. **Restore fault**: remove the fault injection.

6. **Post-experiment**: did the CB recover (HALF_OPEN → CLOSED) when the payment service came back? Were there residual effects?

**Tools:** Chaos Monkey for Spring Boot (Chaos Monkey Assaults: latency, exception, kill-app), Chaos Mesh (Kubernetes-native), Toxiproxy (TCP proxy with latency/abort injection), Gremlin (commercial).

**Blast radius control**: run chaos experiments in a staging environment with production-like traffic, or in production on a small percentage of pods (canary). Never run on full production without a kill switch (feature flag to disable the chaos assault instantly).
**Interview trap:** "Isn't chaos engineering too risky for most teams?" — Starting small is fine: run chaos experiments in staging, on a feature branch, or on one pod in production with a kill switch. The risk of NOT doing it is that you discover your circuit breakers are misconfigured during a real incident at 3am. Even one annual chaos day reviewing your resilience configuration is better than nothing.
**Tags:** chaos-engineering, circuit-breaker, master, resilience, testing, observability

---

## Q-RESIL-024 [bloom: analyze] [level: master]
**Question:** Your Prometheus cardinality has exploded — the TSDB is using 200GB and ingestion is slowing down. You suspect a recently deployed service is the cause. How do you diagnose and fix the cardinality problem?
**Model answer:** **Diagnosis:**

1. **Prometheus TSDB status page**: `http://prometheus:9090/tsdb-status` shows top metrics by time series count and top label names by series count. This immediately identifies the offending metric and label.

2. **Query for series count per metric**:
```promql
# Top 20 metrics by series count
topk(20, count by (__name__) ({__name__!=""}))
```

3. **Find high-cardinality labels on the offending metric** (e.g., if `http_requests_total` has exploded):
```promql
count by (path) (http_requests_total)  -- if "path" has thousands of values
```

If you see paths like `/api/orders/12345/items`, `/api/orders/67890/items` — the path template wasn't normalized. Each unique order ID became a label value.

4. **Check the recently deployed service's metrics endpoint**: `curl http://service/actuator/prometheus | grep "high_cardinality_metric"`.

**Common causes:**
- URL path parameters not stripped: `/api/users/{userId}` logged as-is instead of `/api/users/{id}`
- Request IDs, trace IDs, session IDs added as Prometheus tags (never do this)
- Free-form error messages as label values

**Fix:**

1. **In Spring Boot / Micrometer**: customize `WebMvcTagsProvider` (Boot 2) or `DefaultServerRequestObservationConvention` (Boot 3) to normalize URI paths:
```java
@Bean
public DefaultServerRequestObservationConvention observationConvention() {
    return new DefaultServerRequestObservationConvention() {
        @Override
        protected String getOutcome(HttpServletResponse response) {
            // normalize outcome
        }
    };
}
// Or simpler: management.metrics.web.server.request.autotime.percentiles-histogram=true
// and ensure URI templating is used in @GetMapping("/api/users/{id}") — Micrometer uses the template
```

2. **In Prometheus**: add `metric_relabel_configs` to drop the high-cardinality label or replace its values:
```yaml
- source_labels: [__name__, path]
  regex: 'http_requests_total;/api/orders/\d+/.*'
  target_label: path
  replacement: '/api/orders/{id}/...'
```

3. **Federation / recording rules**: pre-aggregate high-cardinality metrics into low-cardinality summaries at ingest time.

4. **Audit all new services**: PR review should include a check that no Prometheus tags contain unbounded values.

**Recovery**: after fixing, wait for the TSDB compaction cycle (or force with `/-/reload`) — old high-cardinality series expire after their retention window. For immediate relief, use `DELETE /api/v1/series?match[]=offending_metric` (Prometheus admin API) to drop the bad series early.
**Interview trap:** "Would switching from histograms to summaries reduce cardinality?" — No. The cardinality explosion here is caused by label values, not the measurement type. A summary with the same high-cardinality labels would have the same problem. The fix is always to reduce label cardinality by normalizing or removing the offending label.
**Tags:** prometheus, cardinality, metrics, observability, master, micrometer, tsdb

---

## Q-RESIL-025 [bloom: understand] [level: regular]
**Question:** How do you implement a custom `HealthIndicator` in Spring Boot? Show the code and explain how health groups and `management.endpoint.health.*` properties control what `/actuator/health` exposes.
**Model answer:** Spring Boot's `/actuator/health` aggregates the health of all registered `HealthIndicator` beans. To add a custom one, implement the `HealthIndicator` interface:

```java
@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final PaymentGatewayClient client;

    public PaymentGatewayHealthIndicator(PaymentGatewayClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            boolean reachable = client.ping();
            if (reachable) {
                return Health.up()
                    .withDetail("url", client.getBaseUrl())
                    .withDetail("responseTimeMs", client.lastPingMs())
                    .build();
            }
            return Health.down()
                .withDetail("reason", "ping returned false")
                .build();
        } catch (Exception ex) {
            return Health.down(ex)
                .withDetail("url", client.getBaseUrl())
                .build();
        }
    }
}
```

Spring Boot names the indicator by stripping `HealthIndicator` from the class name → component appears as `paymentGateway` under `components` in the `/actuator/health` response.

**Key configuration properties:**

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized   # never | always | when-authorized
      show-components: always
      probes:
        enabled: true                 # enables /actuator/health/liveness and /readiness
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
```

**Health groups** — aggregate a subset of indicators into a named group with its own HTTP endpoint:

```yaml
management:
  endpoint:
    health:
      group:
        liveness:
          include: livenessState       # only internal process state
        readiness:
          include: readinessState, db, redis  # readiness + external deps
```

This exposes `/actuator/health/liveness` and `/actuator/health/readiness` independently, each returning HTTP 200 (UP) or HTTP 503 (DOWN). Kubernetes probes point at these group endpoints.

**What's exposed by default:** in Boot 3, only `/actuator/health` and `/actuator/info` are exposed over HTTP by default. All other actuator endpoints require explicit inclusion in `management.endpoints.web.exposure.include`.
**Interview trap:** "What HTTP status code does `/actuator/health` return when a component is DOWN?" — HTTP 503 Service Unavailable when any included component is DOWN. This means if a custom `HealthIndicator` throws or returns `Health.down()`, the entire health endpoint returns 503 — which can cause Kubernetes readiness probes to pull the pod from rotation. Make sure your custom indicator is reliable: wrap with try-catch and use appropriate timeouts so a slow external check doesn't itself become an outage.
**Tags:** actuator, health-indicator, spring-boot, health-groups, kubernetes, regular
