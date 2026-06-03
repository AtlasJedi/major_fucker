# Microservices — question bank

> Microservices architecture is a core senior backend interview domain. For a 5-year Java/Kotlin engineer, interviewers don't just check if you know the pattern names — they probe whether you've felt the operational pain: distributed transactions, consistency trade-offs, resilience under failure, schema evolution. This bank covers service design and decomposition, sync/async communication, the full Resilience4j resilience toolkit (Circuit Breaker, Bulkhead, Retry, Timeout, Rate Limiter), data consistency patterns (CQRS, Event Sourcing, Saga, Transactional Outbox, idempotency), observability, contract testing, and migration strategies. The gotchas are embedded in every model answer because that's where senior interviews live.

## Scope

- Microservice definition: bounded context, data-per-service, independent deployability, no shared DB
- Decomposition strategies: by business capability, by subdomain (DDD), bounded contexts
- Synchronous communication: REST vs gRPC trade-offs, temporal coupling
- Asynchronous communication: event-driven, Kafka, RabbitMQ, backpressure
- API Gateway pattern, BFF (Backend for Frontend)
- Service discovery: client-side (Eureka) vs server-side (k8s DNS)
- Circuit Breaker: 3 states (Closed/Open/Half-Open), Resilience4j configuration
- Bulkhead: thread-pool isolation vs semaphore isolation — the difference and when to use each
- Retry with exponential backoff and jitter (thundering herd problem)
- Timeout — explicit timeouts on every outbound call
- Rate Limiter pattern
- Fallback strategies
- CQRS: command/query split, separate read model, eventual consistency, when to use
- Event Sourcing: event log as source of truth, replay, snapshots, projections
- Saga pattern: choreography vs orchestration, compensating transactions, vs 2PC
- Transactional Outbox: dual-write problem, outbox table + CDC/Debezium relay
- Idempotency: idempotency keys, at-least-once delivery, consumer deduplication
- Distributed tracing: traceId/spanId, W3C traceparent, OpenTelemetry, Micrometer Tracing
- Eventual consistency and BASE vs ACID
- Strangler Fig migration pattern
- Distributed monolith anti-pattern
- Contract testing (Pact/consumer-driven)
- Service mesh: Envoy sidecar, Istio, library approach vs sidecar trade-off
- Backpressure in async systems
- Health checks: liveness vs readiness (k8s probes)
- Schema evolution and shared-library pitfalls

---

## Q-MS-001 [bloom: recall] [level: junior]
**Question:** What defines a microservice? Name at least 3 key characteristics.
**Model answer:** A microservice is a small, independently deployable service aligned with a single business capability. Key characteristics:

1. **Single responsibility / bounded context** — owns one business domain (e.g., `OrderService`, `CatalogService`). The boundary follows business function, not technical tier.
2. **Independent deployment** — can be built, tested, deployed, and scaled without coordinating with other services. Own CI/CD pipeline, own release cycle.
3. **Database per service** — each service exclusively owns its data store. No other service queries it directly; access is only through the service's API. This is what enforces loose coupling.
4. **Independently scalable** — can be scaled horizontally without scaling the whole system.
5. **Technology-agnostic (polyglot)** — teams can choose different languages, frameworks, or databases per service based on fitness.

The "micro" refers to scope of responsibility, not lines of code. A service that does one complex thing well is still a microservice.

**Interview trap:** "Is it OK to share a database between two microservices if it's just read-only?" — No. Even read-only sharing creates a coupling at the schema level: one team's migration or index change can silently break another service's queries. The rule is absolute: own your schema, expose APIs. If you need to share data, copy it (event-driven denormalization) or query via API.
**Tags:** basics, architecture, definition

---

## Q-MS-002 [bloom: recall] [level: junior]
**Question:** What is an API Gateway and what problems does it solve in a microservices system?
**Model answer:** An API Gateway is the single entry point for all external client traffic into a microservices system. It handles cross-cutting edge concerns so individual services don't have to each implement them.

**Responsibilities:**
- **Routing** — forwards requests to the correct downstream service based on path/header predicates
- **Authentication/Authorization** — validates JWT/OAuth2 tokens once at the edge; services receive a trusted identity header
- **Rate limiting** — protects services from abuse and overload (Token Bucket or Sliding Window, Redis-backed for distributed state)
- **SSL termination** — handles HTTPS at the edge; internal traffic can be plain HTTP or mTLS via service mesh
- **Request aggregation (BFF)** — one gateway call fans out to N services and merges the response
- **Circuit breaking** — can wrap downstream calls via Resilience4j filter

**Examples:** Spring Cloud Gateway, Kong, AWS API Gateway, Netflix Zuul (legacy).

**Trade-offs:** Introduces a single point of failure (mitigate with replicas + load balancer). Becomes a bottleneck and hidden monolith if business logic leaks into it — gateway is infrastructure only.

**Interview trap:** "What is the BFF (Backend for Frontend) pattern?" — A variant where each client type (mobile app, web SPA, third-party partners) gets its own dedicated gateway, shaped to its exact data needs. Avoids over-fetching on mobile, under-fetching on web. Each BFF is owned by the team that owns that client surface.
**Tags:** patterns, api-gateway, bff

---

## Q-MS-003 [bloom: recall] [level: junior]
**Question:** What is a Circuit Breaker? Describe its 3 states and what triggers transitions between them.
**Model answer:** A Circuit Breaker wraps a call to a remote service and prevents cascading failures when that service is down or degraded. Named after an electrical circuit breaker — when a fault is detected, it "opens" to stop current flowing.

**3 states:**

| State | Behavior | Transition |
|-------|----------|------------|
| **CLOSED** (normal) | Requests pass through; failures are counted in a sliding window | → OPEN when `failureRateThreshold` exceeded (e.g., 50% in last 100 calls) |
| **OPEN** | Requests fail fast, no actual call made, fallback executed immediately | → HALF_OPEN after `waitDurationInOpenState` (e.g., 60s) |
| **HALF_OPEN** | Limited probe calls (`permittedNumberOfCallsInHalfOpenState`) are sent | → CLOSED if probes succeed; → OPEN if they fail |

**Resilience4j implementation:**
```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
public InventoryResponse checkStock(String productId) {
    return inventoryClient.check(productId);
}

private InventoryResponse inventoryFallback(String productId, Exception ex) {
    return InventoryResponse.unavailable(); // degrade gracefully
}
```

Config: `slidingWindowSize`, `failureRateThreshold`, `slowCallRateThreshold`, `waitDurationInOpenState`, `permittedNumberOfCallsInHalfOpenState`.

Resilience4j also tracks slow calls (not just failures) — a service that responds in 10s is as damaging as one that errors.

**Interview trap:** "What's the difference between a circuit breaker and a retry?" — Retry is for transient, short-lived failures — try again immediately or with backoff. Circuit Breaker is for a service that is consistently degraded — stop hammering it entirely. In production you use both: Retry wraps individual calls; Circuit Breaker sits above Retry and opens if Retry keeps failing. Hystrix is deprecated — Resilience4j is the current answer.
**Tags:** resilience, circuit-breaker, resilience4j

---

## Q-MS-004 [bloom: recall] [level: junior]
**Question:** What is the difference between synchronous and asynchronous communication in microservices? Give a use case for each.
**Model answer:** **Synchronous (request/response):** the caller sends a request and waits for the response before continuing. Protocols: REST (HTTP/1.1, HTTP/2), gRPC.

**Use when:** the caller needs the result to proceed — checking inventory before confirming a reservation, authenticating a user before returning data.

**Drawback:** temporal coupling — both caller and callee must be available at the same time. A slow downstream service stalls the caller thread. Long chains (A→B→C→D) amplify this: one slow hop stalls the whole chain.

**Asynchronous (event/message-based):** the caller publishes a message to a broker (Kafka, RabbitMQ) and continues immediately. The subscriber processes it when ready.

**Use when:** the outcome doesn't need to be immediate — sending order confirmation email, updating recommendation model after a purchase, notifying analytics of a price change, fan-out to multiple consumers.

**Benefits:** temporal decoupling (consumer can be down, messages persist), natural backpressure (broker absorbs bursts), independently scalable consumers.

**Drawbacks:** eventual consistency, harder to trace failures, requires idempotency and at-least-once delivery handling.

**REST vs gRPC comparison:**

| | REST (HTTP) | gRPC |
|---|---|---|
| Protocol | HTTP/1.1 or HTTP/2 | HTTP/2 always |
| Payload | JSON (text) | Protobuf (binary) |
| Schema | OpenAPI (loose) | Protobuf (strict) |
| Latency | Medium | Low |
| Best for | Public APIs, CRUD | Internal high-throughput |

**Interview trap:** "For an e-commerce checkout, which do you use for payment?" — Sync for immediate payment authorization (user must know if payment succeeded). Async for post-payment events: fulfillment, email confirmation, analytics. The boundary is: does the user's current flow depend on the result?
**Tags:** communication, rest, grpc, async, kafka

---

## Q-MS-005 [bloom: recall] [level: junior]
**Question:** What is the Saga pattern and why is it needed in microservices?
**Model answer:** In a monolith, a business transaction spanning multiple operations uses a single ACID database transaction with rollback. In microservices, each service has its own database — you cannot span a single transaction across services. Saga is the solution.

A Saga is a sequence of local transactions, each publishing an event or message to trigger the next step. If any step fails, compensating transactions execute in reverse to undo the preceding steps.

**Two coordination styles:**

| | Choreography | Orchestration |
|---|---|---|
| Coordinator | None — each service reacts to events | Central orchestrator service |
| Coupling | Loose (event-based) | Orchestrator knows all steps |
| Debugging | Hard — flow is implicit, scattered | Easier — flow is explicit in one place |
| Best for | Simple 2-3 step flows | Complex, long-running sagas |

**Example (order placement):**
1. `OrderService` creates order → publishes `OrderCreated`
2. `InventoryService` listens → reserves stock → publishes `InventoryReserved`
3. `PaymentService` listens → charges card → publishes `PaymentCharged`
4. If payment fails: `PaymentService` publishes `PaymentFailed` → `InventoryService` hears it → releases reservation (compensating transaction)

**vs 2PC (Two-Phase Commit):** 2PC requires a distributed coordinator, holds locks across all participants during the protocol, and a coordinator crash leaves participants in uncertainty — it kills availability. Saga trades strict isolation for availability and independence. Sagas provide eventual consistency (BASE), not ACID isolation.

**Interview trap:** "Is a Saga ACID?" — No. Sagas have no isolation: other transactions can observe intermediate state mid-saga. This is a known trade-off. Design for it: idempotent compensations, reconciliation jobs to detect and fix stuck sagas.
**Tags:** distributed-transactions, saga, patterns, consistency

---

## Q-MS-006 [bloom: recall] [level: junior]
**Question:** What is service discovery? Explain client-side vs server-side service discovery.
**Model answer:** In a microservices system, service instances have dynamic IPs (containers, k8s pods restart with new IPs). Service discovery is the mechanism by which a caller finds a healthy instance of a target service without hardcoded addresses.

**Client-side discovery:**
- Client queries a service registry (e.g., Netflix Eureka) directly.
- Registry returns a list of healthy instances.
- Client picks one (load balances itself, e.g., round-robin).
- Example: Netflix Eureka + Spring Cloud LoadBalancer.
- Drawback: client must implement registry client logic; language-specific.

**Server-side discovery:**
- Client calls a fixed, stable endpoint (a DNS name or load balancer address).
- The router/LB resolves to a healthy instance internally.
- Client knows nothing about the registry.
- Example: Kubernetes Service (`ClusterIP`) — DNS resolves `my-service.namespace.svc.cluster.local` via kube-dns. AWS ALB, GCP Cloud Load Balancing.

**On Kubernetes (the modern answer):** server-side via k8s Services. Eureka is a Spring-Cloud-on-VMs pattern — know it for legacy interview context. In greenfield k8s you lean on k8s DNS + Ingress/Gateway, not Eureka.

**Interview trap:** "What happens if the service registry goes down in client-side discovery?" — All service lookups fail — it becomes a single point of failure. Mitigation: replicated Eureka cluster, or switch to k8s server-side where kube-dns is managed by the platform and highly available.
**Tags:** service-discovery, eureka, kubernetes

---

## Q-MS-007 [bloom: understand] [level: regular]
**Question:** Explain CQRS. What problem does it solve and what are its costs?
**Model answer:** CQRS (Command Query Responsibility Segregation) separates the **write model** (commands that mutate state) from the **read model** (queries that return data). They are different code paths and often different data stores.

**Write side (Command):**
- Accepts commands (`PlaceOrderCommand`, `UpdatePriceCommand`)
- Applies business logic and invariants
- Persists to a write store (normalized, optimized for consistency)
- Publishes domain events

**Read side (Query):**
- Optimized read models — denormalized, pre-aggregated, specific to query patterns
- Separate store (Elasticsearch for full-text, Redis for hot data, read replicas, event-sourced projections)
- No business logic — pure data retrieval, no joins at query time

**Why use it:**
- In e-commerce: catalog browsing is read-heavy (millions reads/sec); order placement is write-heavy (thousands/sec). Separate scaling.
- Read model is a denormalized flat document — no N+1, no joins.
- Write model enforces invariants on normalized structure.
- Enables using best-fit store for each side (Postgres for writes, Elasticsearch for search).

**Costs:**
- **Eventual consistency** between write and read model (events propagate asynchronously). Users may briefly see stale data — must be acceptable.
- Increased complexity: two models to maintain, event pipeline between them.
- Not worth it for simple CRUD with low read/write ratio.

**CQRS does NOT require Event Sourcing** — they're complementary but independent. You can CQRS with a regular SQL write store and a Redis read cache.

**Interview trap:** "Must CQRS use different databases?" — No. You can apply it within one database (separate read/write paths in code). Full benefit requires physically separate stores, but that's the scaled-up version.
**Tags:** cqrs, architecture, read-model, scalability

---

## Q-MS-008 [bloom: understand] [level: regular]
**Question:** Explain Event Sourcing. How does it differ from traditional state storage? What are snapshots and projections?
**Model answer:** In traditional storage, you persist the **current state** of an entity: `UPDATE orders SET status='SHIPPED' WHERE id=123`. The history is lost.

In **Event Sourcing**, you persist the **sequence of events** that led to the current state. The event log is the source of truth; current state is derived by replaying events.

```
events table:
| aggregate_id | sequence | event_type      | payload                        | ts                  |
|-------------|----------|-----------------|-------------------------------|---------------------|
| order-123   | 1        | OrderPlaced     | {items: [...], total: 99.99}  | 2026-01-10T10:00:00 |
| order-123   | 2        | PaymentCharged  | {amount: 99.99, method: CARD} | 2026-01-10T10:00:05 |
| order-123   | 3        | OrderShipped    | {trackingId: "TRK-789"}       | 2026-01-10T10:01:00 |
```

**To get current state:** replay all events for the aggregate in order.

**Benefits:**
- Complete audit trail — exactly what happened and when, never lost
- Temporal queries — "what was the state of this order at noon yesterday?"
- Event-driven naturally — events are already there, publish to Kafka
- Debugging: replay events to reproduce any past state

**Snapshots:** replaying 10,000 events for every read is expensive. A snapshot captures the aggregate state at a point in sequence. On load: find the latest snapshot, replay only events after it.

```
snapshots table:
| aggregate_id | sequence | state_json           |
|-------------|----------|----------------------|
| order-123   | 2        | {status: 'PAID', ...}|
```

**Projections:** read-side views built by consuming the event stream. A projection processes events and writes to a query-optimized store (SQL table, Elasticsearch index, Redis). If corrupted, drop and rebuild from event log.

**Costs:** eventual consistency in projections, complex event schema evolution (you can never change past events), steep learning curve, overkill for simple CRUD.

**Interview trap:** "How do you handle changing an event's schema?" — Events are immutable (they already happened). Solutions: upcasting (transform old events on read to new format), new event versions (`OrderPlacedV2`), or event migration scripts. Never mutate past events in the log.
**Tags:** event-sourcing, cqrs, audit-trail, projections

---

## Q-MS-009 [bloom: understand] [level: regular]
**Question:** What is the Transactional Outbox pattern? What problem does it solve?
**Model answer:** **The dual-write problem:** after a service persists a domain entity change, it must also publish an event to a message broker (Kafka). These are two separate systems — no distributed transaction spans them. If the service saves the order then crashes before publishing `OrderPlaced`, the event is lost. If it publishes first then crashes before saving, consumers act on a non-existent order. There's no safe ordering.

**Transactional Outbox solves this:**
1. In the same database transaction as the domain change, also insert a row into an `outbox` table:
```java
// Both in one @Transactional:
orderRepository.save(order);
outboxRepository.save(new OutboxEvent("OrderPlaced", order.getId(), payload));
// Transaction commits atomically — either both succeed or neither does
```
2. A separate process reads the `outbox` table and publishes events to Kafka/RabbitMQ.
3. After successful publish, mark the row as processed (or delete it).

**Two relay approaches:**
- **Polling publisher:** scheduled job queries `outbox WHERE processed = false`, publishes, marks done. Simple but adds DB load and has latency proportional to polling interval.
- **CDC (Change Data Capture) with Debezium:** Debezium tails the PostgreSQL Write-Ahead Log (WAL). When an outbox row is inserted, Debezium picks it up in near-real-time and publishes to Kafka. Zero polling overhead. The WAL event is the trigger.

```
outbox table:
| id   | aggregate_type | aggregate_id | event_type  | payload    | created_at | processed |
|------|---------------|--------------|-------------|------------|------------|-----------|
| uuid | Order         | order-123    | OrderPlaced | {...}      | 2026-01-10 | false     |
```

**Guarantees at-least-once delivery** (Debezium or polling may re-deliver if it crashes after publish but before marking processed). Consumers must be idempotent.

**Interview trap:** "Why not just use a JTA/XA distributed transaction to span the DB and Kafka?" — XA is supported by very few brokers, adds significant latency (2PC), and databases like PostgreSQL and Kafka have limited/unreliable XA support in practice. Outbox is simpler, more performant, and works with any database and broker.
**Tags:** outbox, cdc, debezium, dual-write, reliability, kafka

---

## Q-MS-010 [bloom: understand] [level: regular]
**Question:** What is idempotency in microservices? How do you implement idempotent consumers and operations?
**Model answer:** An operation is **idempotent** if executing it multiple times produces the same result as executing it once. This is critical in distributed systems because:
- Message brokers guarantee **at-least-once delivery** — duplicates are possible
- Network failures cause retries — the same request may reach the server multiple times
- Saga compensations may re-trigger steps

**Idempotent HTTP methods:** GET, PUT, DELETE are idempotent by definition. POST is not — calling `POST /orders` twice creates two orders.

**Idempotency key pattern for non-idempotent operations:**
- Client generates a unique key (UUID) and sends it in a header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`
- Server checks if it has already processed this key:
  - If yes: return the stored result without re-executing
  - If no: execute, store the result keyed by the idempotency key, return result
- Storage: Redis with TTL (e.g., `SET idempotency:{key} {result} EX 86400`)

**Idempotent Kafka consumer:**
```java
@KafkaListener(topics = "orders")
public void handleOrderPlaced(OrderPlacedEvent event) {
    if (processedEventRepository.exists(event.getEventId())) {
        return; // already processed, skip
    }
    // process...
    processedEventRepository.save(event.getEventId());
}
```

**Database-level idempotency:** `INSERT ... ON CONFLICT DO NOTHING` or `UPSERT` semantics. Use the event ID or idempotency key as the unique constraint.

**Retry safety rules:**
- Always attach an idempotency key when retrying a mutation (POST, non-idempotent PATCH)
- Never retry on 4xx responses (client error — retrying won't fix it, except 429 with backoff)
- Retry on 5xx, 503, timeout — but only with an idempotency key

**Interview trap:** "Can PUT be non-idempotent?" — In theory no, but a poorly designed PUT that uses relative updates (`quantity += 1`) is not idempotent in practice. True idempotent PUT sets absolute state (`quantity = 5`). Design matters, not just the HTTP verb.
**Tags:** idempotency, reliability, kafka, retry, at-least-once

---

## Q-MS-011 [bloom: understand] [level: regular]
**Question:** Explain the Bulkhead pattern. What are the two implementation variants and when would you choose each?
**Model answer:** The Bulkhead pattern isolates resource pools so that failure in one downstream dependency cannot exhaust shared resources and bring down the entire service. Named after ship bulkheads that partition the hull — a flood in one compartment doesn't sink the ship.

**The problem:** Service A calls Service B and Service C. B is degraded and threads start queueing. Without isolation, B's thread pool exhaustion consumes all of A's threads — A can no longer serve requests for C, or anything else. One bad downstream takes down the whole service.

**Two implementation variants in Resilience4j:**

**1. Thread-pool bulkhead (semaphore isolation in older Hystrix terminology, but Resilience4j uses actual thread pools):**
- Each downstream gets a dedicated thread pool
- Caller thread is released immediately; work executes in the pool thread
- If pool is full, request is rejected (fast fail) rather than queueing indefinitely
- Overhead: thread context switching, requires thread-per-call capacity

```java
ThreadPoolBulkhead bulkhead = ThreadPoolBulkhead.of("inventoryService",
    ThreadPoolBulkheadConfig.custom()
        .maxThreadPoolSize(10)
        .coreThreadPoolSize(5)
        .queueCapacity(20)
        .build());
```

**2. Semaphore bulkhead:**
- Limits the number of concurrent calls using a counting semaphore
- Caller thread executes the call directly (no separate pool)
- Lower overhead — no thread switching
- Suitable for reactive/non-blocking code (WebFlux), or when you want to limit concurrency without thread isolation

```java
Bulkhead bulkhead = Bulkhead.of("inventoryService",
    BulkheadConfig.custom()
        .maxConcurrentCalls(10)
        .maxWaitDuration(Duration.ofMillis(0)) // fail fast, no queue
        .build());
```

**When to choose:**
- **Thread-pool bulkhead:** blocking I/O, traditional servlet stack, when you want true thread isolation and can afford the overhead. Downstream outage is fully contained — dedicated threads are the only ones affected.
- **Semaphore bulkhead:** reactive/non-blocking stack (WebFlux), low overhead environments, when you just want concurrency limiting without thread isolation.

**Interview trap:** "Does a bulkhead replace a circuit breaker?" — No, they're complementary. Bulkhead limits concurrent load on a dependency (prevents resource starvation). Circuit Breaker detects sustained failure and stops sending requests entirely (prevents cascading failure). Use both: Bulkhead first (capacity limit), Circuit Breaker above it (failure detection).
**Tags:** bulkhead, resilience, resilience4j, thread-pool, semaphore

---

## Q-MS-012 [bloom: understand] [level: regular]
**Question:** Explain Retry with exponential backoff and jitter. What is the thundering herd problem?
**Model answer:** **Retry** is appropriate for transient failures: a brief network hiccup, a momentary 503, a service briefly unavailable. The assumption is that retrying shortly after will succeed.

**Simple retry is dangerous:** if 1,000 clients all fail at the same moment and all retry at the same 1-second interval, they hammer the recovering service with 1,000 simultaneous requests — which may cause it to fail again. This is the **thundering herd problem**.

**Exponential backoff:** increase wait time exponentially with each attempt:
```
wait = base * 2^attempt
attempt 0: 1s
attempt 1: 2s
attempt 2: 4s
attempt 3: 8s
```
Caps at a maximum (e.g., 60s). This spreads load over time but still has a problem: all 1,000 clients compute the same wait times.

**Jitter:** add randomness to the wait:
```
wait = random(0, base * 2^attempt)    // full jitter
wait = base * 2^attempt + random(0, base)  // decorrelated jitter
```
With jitter, clients spread their retries across the window — the service recovers without a second thunderstorm.

**Resilience4j Retry:**
```java
Retry retry = Retry.of("inventoryService",
    RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(500))
        .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2.0, 0.5))
        .retryOnResult(response -> response.getStatus() == 503)
        .retryExceptions(IOException.class, TimeoutException.class)
        .ignoreExceptions(IllegalArgumentException.class) // don't retry client errors
        .build());
```

**Critical rules:**
- **Never retry 4xx** (client error — retrying is pointless and adds load)
- **Never retry non-idempotent mutations without an idempotency key** — duplicate order creation, double charge
- **Always set a max retry count** — without a cap, a stuck service creates infinite retry loops

**Interview trap:** "Retry and Circuit Breaker — which executes first in the chain?" — Retry wraps the individual call attempt. Circuit Breaker sits outside Retry — it observes the aggregate behavior. If Retry exhausts its attempts and all fail, Circuit Breaker counts one failure. If enough aggregated failures accumulate, CB opens and future calls don't even reach Retry.
**Tags:** retry, backoff, jitter, resilience, thundering-herd

---

## Q-MS-013 [bloom: understand] [level: regular]
**Question:** What is distributed tracing? Explain the relationship between a trace, a span, and a traceId/spanId.
**Model answer:** In a monolith, a stack trace gives you the full picture of a request's execution path. In microservices, a user request hops through 5-10 services — a failure or latency spike anywhere is opaque without a mechanism to correlate logs and timings across those services.

**Distributed tracing** creates a connected timeline of a request as it travels through services.

**Core concepts:**

- **TraceId:** a globally unique ID generated when the first service receives the request. Propagated in HTTP headers to every downstream service. Identifies the entire end-to-end request journey.
- **SpanId:** a unique ID for a single unit of work within the trace — one service processing a request, one DB call, one Kafka produce. A trace is a tree of spans.
- **Parent SpanId:** links a child span to its parent, forming the tree structure.

**W3C traceparent header (modern standard):**
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
              version-traceId(128-bit)-spanId(64-bit)-flags
```

**Spring Boot 3 + Micrometer Tracing / OpenTelemetry:**
- Auto-instruments incoming HTTP requests, outgoing WebClient/RestClient calls, Kafka producers/consumers
- Populates MDC with `traceId` and `spanId` — automatically included in every log line
- Exports spans to Jaeger, Zipkin, or GCP Cloud Trace

```java
// No code change needed — Micrometer Tracing auto-instruments
// Log output automatically includes:
// 2026-01-10 10:00:05 INFO [order-service,4bf92f3577b34da6,a3ce929d0e0e4736] OrderService - Processing order
```

**Observability stack:**
- OpenTelemetry SDK (traces) + Micrometer (metrics) + SLF4J structured JSON logs
- Backend: Jaeger/Zipkin for traces, Prometheus/Grafana for metrics, ELK/Loki for logs
- GCP equivalent: Cloud Trace, Cloud Monitoring, Cloud Logging

**Interview trap:** "What's the difference between a trace and a span?" — Trace = the full journey (the tree). Span = one node in that tree (one service's work for the request). A trace with 5 services = at least 5 spans, often more if you instrument DB calls and Kafka sends separately.
**Tags:** tracing, observability, opentelemetry, micrometer, jaeger

---

## Q-MS-014 [bloom: apply] [level: senior]
**Question:** Design the communication flow for an e-commerce order placement: deduct inventory, charge payment, send confirmation email. Both inventory and payment are synchronous; email is fire-and-forget. What are the failure modes and how do you handle them?
**Model answer:**
```
Client → POST /orders → OrderService
  │
  ├─ sync → InventoryService.reserve(orderId, items)
  │           ↳ if fails: 409 Conflict (out of stock) → stop, return error to client
  │
  ├─ sync → PaymentService.charge(orderId, amount, idempotencyKey)
  │           ↳ if fails: 402 Payment Required → release inventory (compensating tx) → stop
  │
  ├─ @Transactional: persist Order(status=CONFIRMED) + OutboxEvent("OrderPlaced")
  │           ↳ outbox written in same DB transaction as order
  │
  └─ Debezium CDC → Kafka topic "orders" → OrderPlaced event
                      └─ NotificationService consumes → sends confirmation email
```

**Key design decisions:**

1. **Inventory first, payment second:** inventory reservation is cheaper to reverse than a payment refund. Fail fast on stock issues before touching payment.

2. **Email is async:** email failure must not roll back a confirmed order. Fire-and-forget via Kafka. NotificationService is decoupled — can retry, can fail, won't affect the order flow.

3. **Idempotency keys on payment:** `PaymentService.charge()` receives an `idempotencyKey` (orderID or a UUID generated before the call). If OrderService times out and retries, the same key prevents double-charge.

4. **Transactional Outbox:** the `OrderPlaced` event is written to the outbox table inside the same database transaction as the order row. If the transaction commits, the event exists and Debezium will relay it. If it rolls back, neither persists. No event without a committed order, no order without an event being relayed.

5. **Compensating transaction on payment failure:** `InventoryService.release(orderId)` — must be idempotent (release an already-released reservation is a no-op).

**Failure modes:**

| Scenario | Handling |
|----------|----------|
| InventoryService down | Circuit Breaker opens → return 503 with Retry-After |
| PaymentService timeout | Retry with idempotency key → if still fails, release inventory, return 402 |
| Order DB commit fails | Nothing published (outbox not written) → client gets 500, no partial state |
| Debezium lag | Email delayed, not lost — outbox row persists until relayed |
| NotificationService down | Kafka retains message → email sent when service recovers |

**Interview trap:** "What if Kafka is down when Debezium tries to publish?" — Debezium has built-in retry and can buffer. The outbox row remains in the DB. Eventually Kafka recovers and Debezium catches up from WAL. The outbox row is the safety net — nothing is lost.
**Tags:** design, saga, outbox, idempotency, resilience, ecommerce

---

## Q-MS-015 [bloom: apply] [level: senior]
**Question:** Explain how you would implement a Saga for a multi-service checkout flow. Compare choreography and orchestration approaches for this use case. When would you choose each?
**Model answer:** **Checkout saga steps:** reserve inventory → charge payment → create shipment → send notification. Any step failure must compensate all preceding steps.

**Choreography approach:**
```
OrderService:  publishes OrderCreated
InventoryService: listens OrderCreated → reserves → publishes InventoryReserved
                                           fails → publishes InventoryReservationFailed
PaymentService: listens InventoryReserved → charges → publishes PaymentCharged
                                             fails → publishes PaymentFailed
InventoryService: listens PaymentFailed → releases reservation (compensating tx)
ShipmentService: listens PaymentCharged → creates shipment
```

**Problems with choreography at scale:**
- Flow is implicit — no single place shows the full saga state
- Adding a step (e.g., fraud check between payment and shipment) requires changing multiple services
- Hard to detect stuck sagas — what if InventoryService never publishes its event?
- Circular event dependencies can emerge

**Orchestration approach:**
```java
@Component
class OrderSagaOrchestrator {
    public void startOrderSaga(OrderId orderId) {
        sagaState = SagaState.RESERVING_INVENTORY;
        inventoryClient.reserve(orderId, items); // sync or command message
    }
    
    @EventListener(InventoryReservedEvent.class)
    public void onInventoryReserved(InventoryReservedEvent e) {
        sagaState = SagaState.CHARGING_PAYMENT;
        paymentClient.charge(e.getOrderId(), amount, idempotencyKey);
    }
    
    @EventListener(PaymentFailedEvent.class)
    public void onPaymentFailed(PaymentFailedEvent e) {
        sagaState = SagaState.COMPENSATING;
        inventoryClient.release(e.getOrderId()); // compensating tx
        orderService.markFailed(e.getOrderId());
    }
    // ...
}
```

The orchestrator persists saga state (in DB or a saga framework like Eventuate Tram). This enables:
- Monitoring: query saga state table to find stuck/failed sagas
- Timeout detection: scheduled job finds sagas stuck in `CHARGING_PAYMENT` > 5 minutes
- Clear flow in one place

**When to choose:**

| | Choreography | Orchestration |
|---|---|---|
| Use when | Simple 2-3 step flows with stable steps | Complex sagas, long-running, many services |
| Avoid when | Flow changes frequently, hard to debug | Simple flows (overkill) |
| Operational visibility | Low | High |

**Interview trap:** "What guarantees that compensating transactions succeed?" — None. A compensating transaction can also fail. Production sagas need: (1) idempotent compensations so retries are safe, (2) a saga monitoring/cleanup job to detect stuck sagas and retry or alert, (3) alerting and manual intervention path for truly stuck sagas. "Eventual consistency with manual backstop" is the honest answer.
**Tags:** saga, choreography, orchestration, distributed-transactions, compensating-transactions

---

## Q-MS-016 [bloom: apply] [level: senior]
**Question:** Your service publishes events to Kafka after persisting domain state. You're seeing occasional missing events in the consumer — some orders in the DB have no corresponding OrderPlaced event in Kafka. What is the root cause and how do you fix it?
**Model answer:** **Root cause: the dual-write problem.** The service performs two separate writes — one to the database and one to Kafka — without atomicity. Any failure between them causes inconsistency:

```java
// BROKEN: two separate writes
orderRepository.save(order);      // write 1 — succeeds
kafkaTemplate.send("orders", event); // write 2 — may fail: timeout, broker down, OOM
```

If the process crashes, network fails, or Kafka is temporarily unavailable after the DB commit, the event is lost. The DB has the order; Kafka has no event.

**Fix: Transactional Outbox pattern:**

```java
@Transactional
public Order placeOrder(PlaceOrderCommand cmd) {
    Order order = Order.create(cmd);
    orderRepository.save(order);
    // Same transaction: write to outbox
    outboxRepository.save(OutboxEvent.builder()
        .id(UUID.randomUUID())
        .aggregateType("Order")
        .aggregateId(order.getId().toString())
        .eventType("OrderPlaced")
        .payload(serialize(new OrderPlacedEvent(order)))
        .createdAt(Instant.now())
        .build());
    // transaction commits — both rows atomically
    // kafkaTemplate NOT called here
}
```

Separate relay process (Debezium CDC or polling publisher) reads `outbox WHERE published = false`, publishes to Kafka, marks as published.

**Outbox table:**
```sql
CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255),
    aggregate_id VARCHAR(255),
    event_type VARCHAR(255),
    payload JSONB,
    created_at TIMESTAMP,
    published BOOLEAN DEFAULT FALSE
);
```

**CDC with Debezium:** tails PostgreSQL WAL. An insert to `outbox` triggers a WAL event → Debezium reads it → publishes to Kafka → marks published. Near-real-time, zero polling.

**Residual risk:** Debezium may publish and crash before marking published → at-least-once delivery → consumers must be idempotent (deduplicate on `outbox.id`).

**Interview trap:** "Can you use Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` instead?" — It solves the "don't publish before commit" problem but not the "publish after commit" problem. If the Kafka send fails after `AFTER_COMMIT` fires, the event is still lost. Outbox is the correct solution for guaranteed delivery.
**Tags:** outbox, dual-write, debezium, kafka, reliability, cdc

---

## Q-MS-017 [bloom: apply] [level: senior]
**Question:** How would you migrate a monolith to microservices? Describe the Strangler Fig pattern and the risks of the distributed monolith anti-pattern.
**Model answer:** **Strangler Fig pattern** (named after the fig tree that grows around a host tree and eventually replaces it): incrementally extract functionality from the monolith behind a facade, without a big-bang rewrite.

**Migration steps:**
1. **Identify a bounded context** to extract first — choose low-coupling, high-change-frequency, or scaling bottleneck (e.g., `PromotionService` — heavily modified, different team, seasonal load spikes).
2. **Create a facade (strangler facade):** introduce a routing layer (API gateway or proxy) in front of the monolith. All traffic still hits the monolith. No behavior changed yet.
3. **Build the new service in parallel:** implement `PromotionService` as an independent microservice with its own DB.
4. **Dark launch / shadow mode:** route a copy of production traffic to the new service (don't use its response yet). Monitor for discrepancies.
5. **Cut over traffic gradually:** route 1%, 10%, 50%, 100% of promotion traffic to the new service. Monolith's promotion code is still there as fallback.
6. **Remove monolith code:** once traffic is fully migrated and stable, delete the promotion code from the monolith. The service is extracted.

**Distributed Monolith anti-pattern** — the worst of both worlds:
- Services are split at the API/deployment level but remain tightly coupled in practice:
  - **Shared database:** multiple services read/write the same tables. Schema change in one service breaks others. No independent deployability.
  - **Synchronous chain coupling:** Service A calls B which calls C which calls D. All must be deployed together for compatibility. Failure in D cascades to A.
  - **Shared domain model jar:** a `commons-dto` library couples all services. Every release requires coordinated deploys of all services.
- You pay all the operational costs of microservices (distributed debugging, network latency, infrastructure overhead) while getting none of the benefits (independent deploy, independent scale, autonomy).

**How to detect it:** if two services must be deployed together when one changes, or if Service A queries Service B's database directly, you have a distributed monolith.

**Interview trap:** "Should every new project start as microservices?" — No. Start as a **modular monolith** with clean internal module boundaries. Extract services when you have: clear domain boundaries, proven scaling needs, team autonomy requirements, and CI/CD maturity. Extracting early with undefined boundaries creates chatty services and structural debt.
**Tags:** strangler-fig, migration, distributed-monolith, anti-pattern, architecture

---

## Q-MS-018 [bloom: apply] [level: senior]
**Question:** What is backpressure in async microservices and how do you implement it?
**Model answer:** **Backpressure** is the mechanism by which a downstream consumer signals to upstream producers that it cannot process messages fast enough, causing the producer to slow down or buffer instead of overwhelming the consumer.

**The problem without backpressure:**
- Producer writes 10,000 events/second to Kafka
- Consumer can only process 1,000 events/second
- Consumer queue depth grows without bound → eventually OOM or extreme latency
- Or the consumer falls so far behind it processes stale data that's no longer relevant

**Backpressure mechanisms:**

**1. Kafka consumer-side (pull-based = inherent backpressure):**
Kafka is pull-based: consumers poll at their own pace. They never receive more messages than they request. Configure:
```yaml
spring.kafka.consumer:
  max-poll-records: 500  # max per poll call
  fetch-max-wait: 500ms  # how long to wait for data
```
If processing is slow, the consumer simply polls less frequently. Kafka retains messages — no data loss, just lag. Monitor `consumer_lag` metric in Kafka to detect backpressure buildup.

**2. Reactive streams (Project Reactor / WebFlux):**
Reactor implements reactive streams specification which has backpressure built in: a subscriber signals how many items it can accept (`request(n)`). The publisher only emits that many.
```java
Flux.fromIterable(items)
    .limitRate(100)  // request 100 at a time from upstream
    .flatMap(item -> processItem(item), 10)  // max 10 concurrent
    .subscribe();
```

**3. Thread pool as backpressure signal:**
When all Bulkhead threads are busy, new requests are rejected (fast fail). This is implicit backpressure — the caller knows the downstream is saturated.

**4. Queue capacity limits:**
```java
ThreadPoolBulkheadConfig.custom()
    .maxThreadPoolSize(10)
    .queueCapacity(50)  // bounded queue — beyond 50, reject
    .build();
```

**Operational handling of sustained backpressure:**
- Alert on consumer lag (Kafka) exceeding a threshold
- Scale out consumers (k8s HPA on consumer lag metric via KEDA)
- Shed non-critical load with a Rate Limiter or priority queue
- Circuit break producers upstream if consumers are critically overwhelmed

**Interview trap:** "What's the difference between backpressure and rate limiting?" — Rate limiting restricts producers based on an external policy (100 req/min per user). Backpressure is a signal from the consumer to the producer based on actual processing capacity — it's reactive, not policy-based. Both are tools; they address different problems.
**Tags:** backpressure, kafka, reactive, resilience, flow-control

---

## Q-MS-019 [bloom: apply] [level: senior]
**Question:** What is contract testing? How does consumer-driven contract testing (Pact) work and why is it better than integration tests for microservices interfaces?
**Model answer:** In microservices, services evolve independently. A common failure mode: the producer team changes a response field name or removes a field, and the consumer breaks silently — nobody noticed because they only tested their service in isolation.

**Contract testing** verifies that a producer and consumer agree on the interface (contract) — without deploying both at the same time.

**Consumer-driven contract testing (Pact):**

1. **Consumer writes tests that define their expectations:**
```java
// Consumer side (e.g., OrderService expects this from InventoryService)
PactDslWithProvider builder = ...;
RequestResponsePact pact = builder
    .given("product-123 is in stock")
    .uponReceiving("check inventory for product-123")
        .path("/inventory/product-123")
        .method("GET")
    .willRespondWith()
        .status(200)
        .body(LambdaDsl.newJsonBody(body -> body
            .integerType("quantity", 5)
            .booleanType("available", true)
        ).build())
    .toPact();
```
2. Consumer tests run against a Pact mock server — no real producer needed.
3. **Pact file (contract) is published** to a Pact Broker (hosted or pactflow.io).
4. **Producer verifies the contract:** pulls the consumer's pact file, runs the interactions against its real implementation.
```java
@Provider("inventory-service")
@PactBroker
class InventoryServicePactTest {
    @State("product-123 is in stock")
    void setupProductInStock() { /* insert test data */ }
}
```
5. If the producer's implementation doesn't satisfy the consumer's contract, the verification fails — **before deployment**.

**Why better than integration tests for interface contracts:**
- **No shared environment needed:** consumer and producer test independently
- **No deployment coupling:** can verify contract before producer deploys
- **Consumer drives the contract:** producers know exactly which fields consumers actually use — can safely remove unused fields
- **Fast feedback:** contract test runs in CI in seconds vs integration tests that need full environments

**Schema registry + Avro/Protobuf for async (Kafka):** the event schema equivalent of contract testing. A schema registry (Confluent Schema Registry) enforces backward/forward compatibility on schema changes at publish time. Producers can't break consumers' event schemas silently.

**Interview trap:** "What's the difference between contract testing and end-to-end testing?" — E2E tests deploy the full system and test real workflows. They're slow, flaky, and expensive. Contract tests verify API compatibility in isolation, fast. They don't test business workflows — they test interface compatibility. Both have a place; contract tests catch interface breakage earlier and cheaper.
**Tags:** contract-testing, pact, consumer-driven, api-compatibility

---

## Q-MS-020 [bloom: apply] [level: senior]
**Question:** Explain service mesh (Istio/Envoy). What does the sidecar proxy pattern give you, and when is it worth the operational overhead versus using Resilience4j in the application?
**Model answer:** A **service mesh** moves cross-cutting network concerns — retries, timeouts, circuit breaking, mTLS, distributed tracing, traffic splitting — out of application code and into a network proxy that runs alongside each service pod.

**Sidecar proxy pattern (Envoy + Istio):**
- Istio injects an Envoy proxy container into every pod automatically (no code change)
- All inbound and outbound traffic for the pod flows through the Envoy sidecar
- Envoy handles: mTLS (mutual TLS between all services, zero-trust), retries, timeouts, circuit breaking, rate limiting, traffic splitting (canary/A/B), distributed trace injection
- Configuration via Istio custom resources (`VirtualService`, `DestinationRule`) — not in application code

```yaml
# Istio VirtualService — retry policy, no app code change needed
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
spec:
  http:
  - route:
    - destination:
        host: inventory-service
    retries:
      attempts: 3
      perTryTimeout: 2s
    timeout: 6s
```

**What the mesh gives you:**
- **mTLS everywhere** — service-to-service encryption + identity verification, zero application code
- **Polyglot resilience** — retry/CB config applies to Go, Python, Java services uniformly
- **Traffic control** — canary at 5% traffic, blue/green, fault injection for chaos testing
- **Observability** — Envoy emits metrics and injects trace headers automatically (no SDK needed)

**Library approach (Resilience4j + Spring Cloud):**
- Resilience logic in Java code — fine-grained control, readable, debuggable in IDE
- Per-service configuration and deployment
- Works without platform buy-in
- No sidecar overhead (Envoy adds ~5-10ms latency, ~150MB RAM per pod)

**When to use mesh vs library:**

| | Service Mesh | Library (Resilience4j) |
|---|---|---|
| Language diversity | Required (polyglot) | Single language |
| Platform maturity | k8s with Istio already | Simple k8s or VMs |
| Ops overhead | High (Istio is complex) | Low |
| Fine-grained Java control | Harder | Easy |
| Zero-trust network | Yes (mTLS) | Requires separate solution |

**GCP context:** Traffic Director / Anthos Service Mesh are managed equivalents. Reduces operational overhead vs self-managed Istio.

**Interview trap:** "Can you use both Istio and Resilience4j together?" — Yes, and it's common. Resilience4j for application-level circuit breaking with custom fallback logic; Istio for network-level retries, mTLS, traffic splitting. They operate at different layers. Risk: double retry — both Istio and Resilience4j retry independently, multiplying requests. Configure one of them to not retry, or carefully coordinate retry counts.
**Tags:** service-mesh, istio, envoy, sidecar, resilience4j, mtls

---

## Q-MS-021 [bloom: analyze] [level: master]
**Question:** You are designing an Event Sourcing system for an order aggregate in a high-traffic e-commerce platform. Walk through the complete design: aggregate reconstitution, snapshotting strategy, projection rebuilds, event schema evolution, and how you handle a requirement to replay 3 years of events for a new projection.
**Model answer:** **Aggregate reconstitution:**
```java
public class Order {
    private OrderId id;
    private OrderStatus status;
    private List<OrderItem> items;
    private Money total;
    
    // Reconstitute from event stream
    public static Order reconstitute(List<DomainEvent> events) {
        Order order = new Order();
        events.forEach(order::apply);
        return order;
    }
    
    private void apply(DomainEvent event) {
        switch (event) {
            case OrderPlaced e -> { this.id = e.orderId(); this.items = e.items(); this.status = PLACED; }
            case PaymentCharged e -> { this.status = PAID; this.total = e.amount(); }
            case OrderShipped e -> { this.status = SHIPPED; }
            // ...
        }
    }
}
```

**Event store structure:**
```sql
CREATE TABLE events (
    aggregate_id UUID NOT NULL,
    sequence     BIGINT NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      JSONB NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (aggregate_id, sequence)
);
-- Optimistic concurrency: INSERT fails if (aggregate_id, sequence) already exists
```

**Snapshotting strategy:**
- Take a snapshot every N events (e.g., every 50). Store in `snapshots` table: `(aggregate_id, sequence, state_json)`.
- On load: `SELECT * FROM snapshots WHERE aggregate_id = ? ORDER BY sequence DESC LIMIT 1` then `SELECT * FROM events WHERE aggregate_id = ? AND sequence > ?`.
- N=50 means max 50 events to replay after latest snapshot — negligible cost.
- Snapshot is an optimization, never the source of truth — can always rebuild from events if snapshot is corrupted.

**Replaying 3 years of events for a new projection:**
This is the core operational challenge.

1. **Parallel replay infrastructure:** spin up a dedicated consumer reading the event store from `sequence = 0`. Don't replay on the live event store cluster — run against a read replica or an event store export.
2. **Batch reading with pagination:** `SELECT * FROM events WHERE event_type IN (...) ORDER BY occurred_at LIMIT 10000 OFFSET ?` — or use a CDC tool to stream from the event log.
3. **Projection state machine:** build projection state in a shadow table/index. Don't affect the live projection until rebuild is complete.
4. **Gap handling:** during rebuild, live events keep arriving. Use a "catch-up" phase: after bulk rebuild reaches near-current time, switch to event stream tail to close the gap, then atomic swap live → shadow.
5. **Time estimate:** 3 years, assume 100M events at 10K events/sec processing = ~3 hours. Plan for multi-hour rebuilds.
6. **Idempotent projection handlers:** if rebuild fails halfway, restart from last checkpoint without duplicate side effects.

**Event schema evolution (immutable events problem):**
Events already written to the store cannot be changed. Three strategies:
- **Upcasting:** an upcaster transforms old event format to new format on read. The event store remains unchanged; the application never sees the old format.
  ```java
  // V1 had "customerName", V2 has separate "firstName"/"lastName"
  if (event instanceof OrderPlacedV1 e) return upcaster.toV2(e);
  ```
- **New event version:** publish `OrderPlacedV2` going forward; old `OrderPlacedV1` events are handled by the legacy apply() case.
- **Schema registry with Avro:** enforce backward compatibility at produce time — new schema must be able to read old records. Avro's reader/writer schema resolution handles field additions with defaults.

**Operational scaling:**
- Partition event store by `aggregate_id` (consistent hash) for horizontal scaling
- Snapshots reduce read latency for high-event aggregates
- Projections run as Kafka consumer groups — scale consumers independently

**Interview trap:** "How do you handle a bug in an event handler that already corrupted a projection?" — Drop the projection, fix the handler, replay from the event log. This is the Event Sourcing superpower: the audit log is the truth. The projection is always reconstructible. This is why events are immutable and the log is append-only.
**Tags:** event-sourcing, projections, snapshots, schema-evolution, replay

---

## Q-MS-022 [bloom: analyze] [level: master]
**Question:** Compare CQRS + Event Sourcing against a traditional CRUD + read replica approach for a high-scale e-commerce catalog (100M product listings, billions of reads/day, frequent price/stock updates). When does ES+CQRS win and when is it the wrong choice?
**Model answer:** **Traditional CRUD + read replica:**
```
Write: POST /products → PostgreSQL primary (normalized schema)
Read: GET /products/search → read replica (same schema, but indexed for search)
     or → Elasticsearch (synced via CDC or polling)
```
- Operational complexity: low to medium. Read replica is mature, well-understood.
- Query flexibility: limited by whatever indexes you create on the read replica.
- Latency for complex reads: still requires joins if data is normalized.
- Audit trail: none — you know current state, not how it got there.

**CQRS + Event Sourcing:**
```
Write: POST /products → EventStore (ProductCreated, PriceUpdated, StockChanged events)
Read projections (built from event stream):
  - Elasticsearch: full-text search optimized view
  - Redis: hot product cache (top 1M products)
  - PostgreSQL: category/pricing aggregation view
  - Recommendation engine: gets the raw event stream via Kafka
```

**When ES+CQRS wins:**
- **Multiple specialized read models needed:** search, pricing, recommendations, analytics all need different shapes. ES lets each projection be optimized for its query pattern.
- **Audit/compliance requirement:** "Who changed the price of product X and when, and what was the price at every point?" — trivial with ES (query event log), impossible with CRUD (history is overwritten).
- **Event-driven downstream consumers:** inventory systems, recommendation engines, analytics all need to react to catalog changes. Events are the natural contract.
- **Temporal queries:** "What was the catalog state at 2pm during the Black Friday sale?" — ES supports this; CRUD does not.
- **Complex business logic with rollback:** reversible operations, undo history, audit.

**When CRUD + replica is better:**
- **Simple catalog with standard queries:** if you need search + basic filtering, Postgres with Elasticsearch sync via CDC is 80% of the benefit at 20% of the complexity.
- **Small team:** ES+CQRS requires discipline. Event schema evolution, upcasters, projection rebuilds — each is a source of bugs and operational burden. A 3-person team will be slower, not faster.
- **Read model stability:** if one read model serves all needs, the dual-write overhead of ES projections isn't justified.
- **Regulatory simplicity:** some compliance frameworks actually want a simple mutable state (GDPR right-to-erasure: deleting a user's data is trivial in CRUD, deeply complex in ES where you can't delete events).

**GDPR + Event Sourcing tension:** events are immutable. If a customer requests data deletion, you can't delete their events. Solutions: encrypt personal data in events with a per-customer key, then delete the key (crypto-shredding). Or: store personal data references, not values, in events.

**Honest answer:** for the stated use case (100M products, billions of reads, frequent price/stock updates), the read side almost certainly needs a specialized read model regardless (Elasticsearch for search, Redis for hot data). CQRS for the read side is justified. Event Sourcing on the write side is justified only if audit trail and multiple event consumers are required — otherwise a regular write store with CDC for projections is simpler and adequate.

**Interview trap:** "Can you do CQRS without Event Sourcing?" — Yes, absolutely. CQRS just means separate read/write paths. You can have a PostgreSQL write store and an Elasticsearch read model synced via CDC, with no Event Sourcing at all. ES+CQRS is the combined pattern — CQRS alone is much more accessible.
**Tags:** cqrs, event-sourcing, architecture-decision, scale, gdpr

---

## Q-MS-023 [bloom: analyze] [level: master]
**Question:** Walk through a complete resilience strategy for a microservice that calls 3 downstream services: an inventory service (critical, must respond), a recommendation service (non-critical, can degrade gracefully), and a fraud detection service (critical but can timeout at 500ms). How do you configure Resilience4j for each, and what are the operational failure modes?
**Model answer:** **Architecture:**
```
OrderService
  ├─ InventoryService (critical — must succeed)
  ├─ RecommendationService (non-critical — graceful degradation)
  └─ FraudDetectionService (critical but time-bounded — hard timeout 500ms)
```

**Resilience4j component stack per dependency (applied in order, outside-in):**
`TimeLimiter → CircuitBreaker → Bulkhead → Retry`

Applied from outermost (client perspective) to innermost (call execution):
1. **Bulkhead** isolates thread capacity per downstream
2. **Retry** wraps the individual call
3. **CircuitBreaker** aggregates retry outcomes to decide open/close
4. **TimeLimiter** enforces hard timeout per attempt

**InventoryService (critical):**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        slidingWindowSize: 20
        failureRateThreshold: 50      # open after 50% failures
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 200ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
  timelimiter:
    instances:
      inventoryService:
        timeoutDuration: 2s
  bulkhead:
    instances:
      inventoryService:
        maxConcurrentCalls: 20
```
Fallback: return 503 to client — do not degrade, inventory is required. Log and alert.

**RecommendationService (non-critical):**
```yaml
  circuitbreaker:
    instances:
      recommendationService:
        failureRateThreshold: 70      # more tolerant — non-critical
        waitDurationInOpenState: 60s
  timelimiter:
    instances:
      recommendationService:
        timeoutDuration: 500ms         # fast timeout — not worth waiting
  bulkhead:
    instances:
      recommendationService:
        maxConcurrentCalls: 5          # small pool — non-critical
```
Fallback: return `RecommendationResponse.empty()` or a cached popular items list. **Never fail the main flow** for a recommendation failure.

**FraudDetectionService (critical, 500ms SLA):**
```yaml
  timelimiter:
    instances:
      fraudService:
        timeoutDuration: 500ms          # hard — business SLA
  circuitbreaker:
    instances:
      fraudService:
        slowCallDurationThreshold: 400ms  # track slow calls too
        slowCallRateThreshold: 30         # open if 30% calls > 400ms
        failureRateThreshold: 30          # lower threshold — fraud failures are serious
  retry:
    instances:
      fraudService:
        maxAttempts: 1                    # no retry — 500ms total budget, no time
```
Fallback: **risk-based decision**, not a simple default. E.g.: if fraud service is down, route order to manual review queue (async), proceed with order placement, flag for human review. Do not silently approve. Log and alert immediately — fraud service outage is an incident.

**Operational failure modes to account for:**

| Scenario | What happens | Detection |
|----------|-------------|-----------|
| Inventory CB open | Orders return 503; client retries with backoff | CB state metric, 503 rate alert |
| Recommendation CB open | Fallback response; no impact to orders | Low priority alert |
| Fraud service degraded (slow calls) | slowCallRate triggers CB; orders go to manual review queue | Alert ops immediately |
| Bulkhead exhausted (inventory) | New calls rejected while existing calls process | Bulkhead rejection rate metric |
| Retry storm on inventory | Retry + jitter prevents thundering herd | Monitor inventory service RPS |

**Key insight:** the most dangerous configuration error is having the same thread pool for all three dependencies. A slow Recommendation service (low priority) would steal threads from Inventory (critical). Separate bulkheads are non-negotiable.

**Interview trap:** "What happens if you stack Retry inside CircuitBreaker and the CB opens halfway through a retry sequence?" — The CB opens after counting enough failures from completed Retry attempts. Once open, the CB short-circuits immediately — Retry never executes (there's no call to retry). The fail-fast path returns the fallback without touching Retry. This is correct behavior.
**Tags:** resilience4j, circuit-breaker, bulkhead, retry, timeout, fallback, production-design

---

## Q-MS-024 [bloom: analyze] [level: master]
**Question:** Explain eventual consistency in microservices. How do you reason about and bound the inconsistency window? Give a concrete example of a business decision where eventual consistency is acceptable and one where it is not.
**Model answer:** **Eventual consistency** means that if no new updates are made to a data item, all replicas or derived views will eventually converge to the same value. The system is not immediately consistent after a write — there is a window during which different parts of the system see different versions.

**Sources of eventual consistency in microservices:**
1. **Event propagation delay:** Order is placed, `OrderPlaced` event is published to Kafka, consumed by `ShippingService` to create a shipment. Between publish and consume, `ShippingService` doesn't know about the order. Delay: milliseconds to seconds depending on Kafka consumer lag.
2. **CQRS read model sync:** write model commits, event is published, projection consumer processes it, read model updated. Window: typically < 1 second if healthy, minutes if consumer is lagging.
3. **Database replication lag:** read replicas in PostgreSQL typically lag 10-100ms behind primary.

**Bounding the inconsistency window:**
- Measure and monitor consumer lag in Kafka (metric: `records-lag-max`). Alert if > N seconds.
- Read-after-write consistency trick: after writing, route the immediate read for that specific entity to the write model / primary (bypass cache). Subsequent reads can hit the eventually-consistent read model.
- Design UI for eventual consistency: show the action optimistically ("Your order has been placed — confirmation email coming shortly") rather than waiting for full propagation.

**Where eventual consistency IS acceptable:**

*Product review counts / ratings:* if a product shows 4.7 stars with 1,243 reviews vs 4.7 stars with 1,244 reviews for 2 seconds, no business consequence. The user doesn't care. Eventual consistency is fine.

*Recommendation engine updates:* showing recommendations based on a purchase event that arrives with 5-second latency is fine. Personalization is probabilistic anyway.

*Analytics dashboards:* if the "orders last hour" counter in an ops dashboard lags by 30 seconds, nobody is harmed.

**Where eventual consistency is NOT acceptable:**

*Inventory check at checkout:* if two users attempt to buy the last unit simultaneously, both read "1 in stock," both proceed, both have their payment charged — only one can actually ship. Overselling. This requires **synchronous strong consistency** at the moment of checkout. Solution: a distributed lock or optimistic lock on the inventory record, checked and decremented in a single atomic operation at purchase time.

*Financial double-spend prevention:* if a customer has £100 balance and two concurrent withdrawals of £80 each both read the balance as £100 before either commits, both succeed — overdraft. Requires strong consistency (serializable transaction or pessimistic lock).

*Payment idempotency:* the fraud check and payment authorization must have strong consistency guarantees — eventual consistency here means potentially authorizing the same payment twice.

**The practical rule:** eventual consistency is acceptable when the business impact of a stale read is low and the inconsistency is time-bounded. Strong consistency is required when a stale read causes a financial, legal, or critical business integrity violation.

**Interview trap:** "Can you get strong consistency in microservices?" — Yes, for reads within a single service (use the write path or a lock). Across services, you can approximate it with synchronous calls + distributed locks, but this reintroduces coupling and reduces availability. The honest answer: microservices architectures are designed for eventual consistency. If you need strong consistency across multiple services regularly, your service boundaries may be wrong.
**Tags:** eventual-consistency, consistency, distributed-systems, inventory, design-decision
