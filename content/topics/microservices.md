# Microservices — question bank

> Context: Backend Java/Kotlin developer working on a major e-commerce platform with microservices architecture. Focus on patterns, communication, resilience, service decomposition, and DDD alignment.

## Scope

- What is a microservice (single responsibility, independent deployment, own data)
- Synchronous communication: REST, gRPC
- Asynchronous communication: message queues, event-driven (Kafka, RabbitMQ)
- Service discovery (Eureka, Kubernetes DNS)
- API Gateway pattern
- Circuit breaker (Resilience4j)
- Distributed tracing, correlation IDs
- Saga pattern for distributed transactions
- CQRS and event sourcing basics
- Decomposition strategies: by business capability, by subdomain (DDD)
- Monolith vs microservices trade-offs

---

## Q-MS-001 [bloom: recall]
**Question:** What defines a microservice? Name 3 key characteristics.
**Model answer:** 1) **Single responsibility** — a microservice does one thing, aligned with a business capability (not a technical tier). Examples: `OrderService`, `CatalogService`, `PromotionService`. 2) **Independent deployment** — can be deployed without coordinating with other services. Own build pipeline, own release cycle. 3) **Own data store** — each service owns its data, no direct DB sharing with other services. This enforces loose coupling; other services must call the API to access data. Additional: small enough to be rewritten in 2 weeks; independently scalable; technology-agnostic (polyglot). The "micro" is about scope of responsibility, not lines of code.
**Interview trap:** "Is sharing a database between services OK?" — No. It creates tight coupling — schema changes in one service break others. The "shared database" anti-pattern is one of the most common microservices mistakes.
**Tags:** basics, architecture

## Q-MS-002 [bloom: recall]
**Question:** What is an API Gateway and what problems does it solve?
**Model answer:** An API Gateway is a single entry point for all client requests to a microservices system. It handles: 1) **Routing** — forwards requests to the appropriate downstream service. 2) **Authentication/Authorization** — validates JWT/OAuth tokens once, before services see the request. 3) **Rate limiting** — protects services from abuse. 4) **Request aggregation** — can combine multiple service calls into one client response (BFF pattern). 5) **SSL termination** — handles HTTPS at the edge, internal traffic can be plain HTTP. 6) **Load balancing** — distributes traffic across instances. Examples: AWS API Gateway, Kong, Netflix Zuul, Spring Cloud Gateway. Trade-off: single point of failure (mitigate with replicas), can become a bottleneck if too much logic is pushed into it.
**Interview trap:** "What is the BFF (Backend for Frontend) pattern?" — A variant where each frontend (mobile, web, partner API) gets its own gateway tailored to its needs, instead of one generic gateway.
**Tags:** patterns, api-gateway

## Q-MS-003 [bloom: recall]
**Question:** What is a circuit breaker pattern? What are its 3 states?
**Model answer:** Circuit breaker prevents cascading failures — when a downstream service is failing, the circuit breaker stops sending requests to it and returns a fallback immediately. Named after an electrical circuit breaker. **3 states:** 1) **Closed** (normal) — requests pass through, failures are counted. If failure rate exceeds threshold, trips to Open. 2) **Open** — requests fail fast (no actual call made), fallback returned. After a timeout, transitions to Half-Open. 3) **Half-Open** — a few probe requests are sent. If they succeed, circuit Closes again; if they fail, goes back to Open. **Java implementation:** Resilience4j (recommended for Spring Boot) or Hystrix (Netflix, now in maintenance). Config: `failureRateThreshold`, `waitDurationInOpenState`, `permittedNumberOfCallsInHalfOpenState`.
**Interview trap:** "What's the difference between circuit breaker and retry?" — Retry assumes transient failure — try again. Circuit breaker assumes the service is down — stop hammering it. Use both: retry with backoff for transient errors, circuit breaker to stop if the service is consistently failing.
**Tags:** resilience, patterns, resilience4j

## Q-MS-004 [bloom: recall]
**Question:** What is the difference between synchronous and asynchronous communication in microservices? Give an example use case for each.
**Model answer:** **Synchronous (request/response):** caller waits for the response. REST (HTTP), gRPC. Use when: you need the result immediately to proceed — checking stock before placing an order, fetching product details for a page render. Drawback: temporal coupling — caller and callee must both be available. **Asynchronous (message-based):** caller publishes a message to a broker (Kafka, RabbitMQ) and continues. Subscriber picks it up when ready. Use when: the outcome doesn't need to be immediate — sending order confirmation email, updating recommendation engine after a purchase, syncing to analytics. Benefit: decoupling, resilience (messages persist if consumer is down), natural backpressure. Drawback: eventual consistency, harder to debug, need to handle idempotency and message ordering.
**Interview trap:** "For an e-commerce checkout, which would you use for payment processing?" — Depends. Sync for the immediate payment authorization (user waits to confirm payment accepted). Async for post-payment: fulfillment, email, analytics events.
**Tags:** communication, patterns, ecommerce

## Q-MS-005 [bloom: recall]
**Question:** What is a Saga pattern and why is it needed in microservices?
**Model answer:** In a monolith, a business transaction spanning multiple operations uses a single database ACID transaction. In microservices, each service has its own DB — you can't span a transaction across services. Saga solves this. A Saga is a sequence of local transactions, each publishing an event or message to trigger the next. If a step fails, compensating transactions are executed in reverse to undo previous steps. **Two types:** 1) **Choreography** — each service reacts to events from other services. No central coordinator. Simple but harder to track the overall flow. 2) **Orchestration** — a central orchestrator (Saga orchestrator/process manager) tells each service what to do. Easier to track and debug. Example: place order saga — reserve inventory → charge payment → ship. If payment fails → release inventory (compensating transaction).
**Interview trap:** "Is a Saga an ACID transaction?" — No. It provides eventual consistency (BASE), not ACID. Isolation is not guaranteed — another transaction can see intermediate state.
**Tags:** distributed-transactions, saga, patterns

## Q-MS-006 [bloom: understand]
**Question:** Explain CQRS. Why is it used in high-traffic e-commerce systems?
**Model answer:** CQRS (Command Query Responsibility Segregation) separates read and write models. **Write side (Command):** accepts commands that mutate state (`PlaceOrderCommand`), applies business logic, persists to write store. **Read side (Query):** optimized read models (denormalized, pre-aggregated) for queries. Often stored in a separate DB (Elasticsearch, Redis, read replicas) tuned for query patterns. **Why in e-commerce:** catalog browsing is read-heavy (millions of reads/second) while order placement is write-heavy (thousands/second). CQRS lets you scale reads and writes independently. Read model can be a denormalized flat document — no joins at query time. Write model can enforce business invariants on a normalized structure. **The catch:** eventual consistency between write and read model (propagated via events). Users might briefly see stale data. **Often paired with Event Sourcing:** write side stores events (not current state), read side projects events into query-friendly views.
**Interview trap:** "Must CQRS always use different databases?" — No. You can apply CQRS within a single database (separate read/write objects in code), but the full benefit comes from physically separate stores.
**Tags:** cqrs, architecture, ecommerce

## Q-MS-007 [bloom: understand]
**Question:** How does distributed tracing work and why is it essential in microservices debugging?
**Model answer:** In a monolith, a stack trace tells you everything. In microservices, a user request hops through 5-10 services — a failure anywhere is opaque. Distributed tracing solves this. **Mechanism:** 1) Each incoming request gets a unique `traceId`. Each hop (service-to-service call) gets a `spanId`. The `traceId` is propagated in HTTP headers (`X-B3-TraceId` in Zipkin, `traceparent` in W3C standard). 2) Each service reports timing and metadata for its span to a collector. 3) The collector assembles the full trace as a tree of spans. **Tools:** Jaeger, Zipkin, AWS X-Ray, Datadog APM. **In Spring Boot:** Spring Cloud Sleuth (now Micrometer Tracing) auto-instruments with `@Bean` registration, adds trace/span IDs to MDC (so they appear in log lines). **Essential because:** without it, a 200ms latency regression could be in any of 8 services — tracing pinpoints which span is slow.
**Interview trap:** "What's the difference between a trace and a span?" — Trace = the full journey of a request (tree). Span = a single operation within that trace (one service's work, one DB call, etc.).
**Tags:** observability, tracing, debugging

## Q-MS-008 [bloom: apply]
**Question:** An e-commerce order service needs to: 1) deduct inventory, 2) charge the customer, 3) send a confirmation email — all as part of placing an order. The payment service is synchronous, inventory is synchronous, email is fire-and-forget. Design the communication flow.
**Model answer:**
```
Client → POST /orders → OrderService
  │
  ├─ sync → InventoryService.reserve(items)   [if fails: 409 Conflict, stop]
  │
  ├─ sync → PaymentService.charge(amount)     [if fails: release inventory, 400, stop]
  │
  ├─ persist Order (status=CONFIRMED) to OrderDB
  │
  └─ async → publish OrderPlaced event → Kafka
               └─ NotificationService consumes → sends email
```
Key decisions: 1) Inventory and payment are sync — order flow depends on their outcome, user must know immediately. 2) Email is async — failure shouldn't block the order; user will get the email eventually. 3) Compensating transaction on payment failure: release the inventory reservation (or use a Saga). 4) The `OrderPlaced` event is published after persisting — never before (avoid event without matching state). **Idempotency:** inventory deduction and payment must be idempotent — if OrderService retries after a timeout, the same request shouldn't double-charge.
**Interview trap:** "What if the Kafka publish fails after the order is saved?" — Transactional outbox pattern: save the event to an `outbox` table in the same DB transaction as the order. A separate process polls and publishes. Guarantees at-least-once delivery.
**Tags:** design, ecommerce, saga, async

## Q-MS-009 [bloom: apply]
**Question:** You need to add rate limiting to your API Gateway so that any single customer can make at most 100 product search requests per minute. Describe the implementation approach.
**Model answer:** **Algorithm choice:** Token Bucket or Sliding Window Log. Token Bucket: each customer gets a bucket of 100 tokens/minute, each request consumes one token, bucket refills at 100/min rate. Sliding Window: count requests in a rolling 60-second window per customer. **Storage:** must be distributed (Redis) — multiple gateway instances share the same counters. Key: `rate_limit:{customerId}:search`. Use Redis `INCR` + `EXPIRE` for simplicity, or `lua script` for atomic check-and-increment. **Implementation in Spring Cloud Gateway:** `RequestRateLimiterGatewayFilterFactory` with `RedisRateLimiter` built-in. Configure via YAML: `redis-rate-limiter.replenishRate=100`, `redis-rate-limiter.burstCapacity=100`. Resolve by customer ID from JWT claim. **Response:** return `429 Too Many Requests` with `Retry-After` header. **Edge cases:** unauthenticated users rate limited by IP. Don't rate-limit health check endpoints.
**Interview trap:** "Why Redis and not in-memory?" — Multiple gateway instances → in-memory counters would be per-instance (multiply the limit by instance count). Redis is shared state.
**Tags:** rate-limiting, api-gateway, redis

## Q-MS-010 [bloom: analyze]
**Question:** Your team is considering breaking a monolith into microservices for the e-commerce platform. What questions would you ask before committing, and what signals tell you the monolith is ready to be split?
**Model answer:** **Questions first:** 1) Do teams deploy together? If one team's change blocks others from deploying, that's coupling worth breaking. 2) Are there performance isolation needs? Does the catalog service spike during sale events while checkout is stable? 3) Is there a clear bounded context boundary? 4) Can you afford the operational overhead? Microservices = N×(logging, monitoring, deployment, on-call rotation). 5) Do you have CI/CD maturity? Without fast automated deployments, microservices slow you down. **Signals the monolith is ready to split:** scaling bottleneck in one area, different teams owning different domains, one change breaks unrelated features (high coupling), deployment takes hours due to coordinating teams, need different tech stacks for different parts. **Signals to stay monolithic:** small team (<10 engineers), domain is poorly understood (premature decomposition = wrong cuts), no DevOps maturity, performance is fine. **The Strangler Fig pattern:** don't big-bang rewrite. Identify a bounded context (e.g., `PromotionService`), extract it incrementally. Route its traffic through a facade first, then migrate the implementation behind the facade.
**Interview trap:** "Should every new project start as microservices?" — No. Start as a modular monolith, extract services when you have clear boundaries and a proven need. "Microservices-first" for small teams is premature complexity.
**Tags:** architecture, decision, monolith-vs-microservices
