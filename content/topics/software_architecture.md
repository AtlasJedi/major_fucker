# Software Architecture & DDD — question bank

> This bank covers architectural styles, system decomposition strategies, Domain-Driven Design (strategic and tactical), and cross-cutting design concerns. For a senior Java/Kotlin backend engineer this is the highest-leverage interview territory: questions here probe design judgment, not just syntax. Interviewers distinguish candidates who know pattern names from those who understand why patterns exist, what they cost, and when to skip them. Every question in this bank is one a real senior interviewer asks — or should ask.

## Scope

- Architectural styles: layered/n-tier, hexagonal (ports-and-adapters), onion, clean architecture and the dependency rule
- Event-driven architecture: event notification vs event-carried state transfer vs event sourcing
- Microkernel/plugin architecture and pipes-and-filters
- Monolith vs modular monolith vs microservices — decision criteria
- DDD strategic design: bounded context, ubiquitous language
- DDD context mapping: partnership, customer-supplier, conformist, anti-corruption layer, shared kernel, published language
- DDD tactical patterns: entity, value object, aggregate, aggregate root, invariants, transactional boundary
- DDD tactical patterns: repository, domain service, domain event, factory
- Anemic vs rich domain model
- CQRS as an architectural pattern
- 12-Factor App
- Architecture Decision Records (ADRs)
- Quality attributes / the -ilities and their trade-offs
- Conway's Law
- Coupling and cohesion at the service level
- The C4 model for architecture documentation

---

## Q-ARCH-001 [bloom: recall] [level: junior]
**Question:** Name the main layers in a traditional layered (n-tier) architecture and describe what each layer is responsible for.
**Model answer:** Classic three-tier / four-tier layered architecture top-to-bottom:

1. **Presentation layer** (UI, REST controllers, GraphQL resolvers) — receives external input, translates it to application calls, formats responses. No business logic.
2. **Application layer** (application services, use case orchestrators) — coordinates use cases. Loads domain objects, calls domain logic, persists results, publishes events. Thin — no business rules.
3. **Domain layer** (entities, value objects, aggregates, domain services) — the heart of the system. Business rules, invariants, domain events. No infrastructure dependencies.
4. **Infrastructure layer** (repositories, message brokers, HTTP clients, DB adapters) — implements interfaces defined by domain/application layer. Talks to the outside world.

**Dependency rule in classic layering:** each layer depends only on the layer directly below it. Presentation → Application → Domain → Infrastructure in terms of calls — but Domain should NOT depend on Infrastructure (that would be circular). In practice classic layering often violates this by letting the domain import JPA annotations or use infrastructure concerns directly. Hexagonal architecture was created specifically to fix this.

**Interview trap:** "What's wrong with classic layered architecture?" — The most common failure: developers put business logic in the Application layer (fat services, anemic domain model). Also: lower layers can be imported by anything above, so a controller can import a repository directly, bypassing the application layer entirely. No enforced boundary.
**Tags:** architecture, layered, basics

---

## Q-ARCH-002 [bloom: recall] [level: junior]
**Question:** What is hexagonal architecture (ports and adapters)? What problem does it solve compared to classic layering?
**Model answer:** Hexagonal architecture (Alistair Cockburn, 2005) places the application core (domain + application services) at the center. The core defines **ports** — interfaces for things it needs (e.g., `OrderRepository`, `PaymentGateway`, `EventPublisher`). **Adapters** are the implementations that plug into those ports (JPA adapter for `OrderRepository`, Stripe adapter for `PaymentGateway`, Kafka adapter for `EventPublisher`).

```
[HTTP REST Adapter] ──→ [Application Core] ←── [JPA Adapter]
[gRPC Adapter]     ──→ [    (Ports)       ] ←── [Kafka Adapter]
[CLI Adapter]      ──→ [  Domain Model    ] ←── [HTTP Client Adapter]
```

**Primary ports** (driving side): interfaces the outside world calls into (REST, gRPC, CLI). **Secondary ports** (driven side): interfaces the core calls out to (DB, broker, external APIs).

**Problem it solves:** In classic layering the domain often depends on infrastructure (JPA entities, JDBC, etc.). Hexagonal inverts this: the domain owns the port interfaces; infrastructure adapters implement them. The domain has zero infrastructure imports. This means: (1) domain can be tested without a database — just use in-memory fakes. (2) swap databases without touching domain code. (3) add a CLI or test harness without changing business logic.

**Interview trap:** "Is hexagonal architecture the same as clean architecture?" — Related but not identical. Clean architecture (Uncle Bob) is a refinement that adds the dependency rule explicitly, the Entities/Use Cases/Interface Adapters/Frameworks layers, and the stable-dependencies principle. Both share the core idea of inverting dependencies toward the domain.
**Tags:** architecture, hexagonal, ports-and-adapters

---

## Q-ARCH-003 [bloom: recall] [level: junior]
**Question:** What is the dependency rule in Clean Architecture? What is the Onion Architecture?
**Model answer:** **Clean Architecture (Robert C. Martin):** concentric circles — Entities (innermost), Use Cases, Interface Adapters, Frameworks & Drivers (outermost). The **dependency rule**: source code dependencies must point ONLY inward. An inner circle knows nothing about an outer circle. Entities don't import use cases. Use cases don't import controllers or databases. Outer layers depend on inner layers, never the reverse. Achieved via the Dependency Inversion Principle: inner circles define interfaces (abstractions), outer circles provide implementations.

**Onion Architecture (Jeffrey Palermo):** same idea, slightly different layer naming: Domain Model (core) → Domain Services → Application Services → Infrastructure (outermost). The onion metaphor: peel off infrastructure layers, the business logic survives intact. Dependency direction: always inward.

**Practical implication in Java/Kotlin:** `domain` module has zero Spring/JPA/Kafka imports. `application` module depends on `domain`, has Spring `@Service` annotations but no `@Repository` or JPA. `infrastructure` module implements the domain's repository interfaces with JPA. Main/bootstrap wires everything together.

**Interview trap:** "Can a Use Case call another Use Case?" — Generally discouraged in Clean Architecture — it creates coupling between use cases and can hide complexity. Prefer extracting shared logic into a Domain Service or restructuring.
**Tags:** architecture, clean-architecture, onion, dependency-rule

---

## Q-ARCH-004 [bloom: recall] [level: junior]
**Question:** What is the difference between a monolith, a modular monolith, and microservices? When would you choose each?
**Model answer:**

| | Monolith | Modular Monolith | Microservices |
|---|---|---|---|
| Deployment | Single artifact | Single artifact | Many artifacts |
| Modules | Entangled / none | Explicit, enforced | Physical services |
| DB | One shared DB | Shared DB, separate schemas | Separate DB per service |
| Latency | In-process | In-process | Network hops |
| Team | One | Multiple, coordinated | Independent teams |
| Complexity | Low | Medium | High |

**When to choose:**
- **Monolith:** early-stage product, team < 5 engineers, domain not yet understood. Fastest time to market. Don't distribute what you don't understand yet.
- **Modular monolith:** team 5–20, domain partially understood, clear module boundaries exist, but operational complexity of microservices isn't justified. Best of both worlds — modules enforce boundaries, single deployment keeps ops simple. Evans and Fowler both recommend this as the default starting point.
- **Microservices:** independent scaling requirements per component, teams large enough to own services end-to-end (2-pizza rule), distinct deployment cadences, different language/runtime needs per service, or regulatory isolation. Conway's Law: if you have independent teams, your architecture will reflect it anyway.

**The anti-pattern:** "distributed monolith" — services that share a database, have synchronous call chains, and can't be deployed independently. Worst of both worlds.

**Interview trap:** "Can you refactor a monolith to microservices later?" — Yes, and it's often the right approach. Start modular monolith → identify seams (bounded contexts) → extract services one at a time using the Strangler Fig pattern. Much safer than greenfield microservices when the domain is unclear.
**Tags:** architecture, monolith, microservices, modular-monolith

---

## Q-ARCH-005 [bloom: recall] [level: junior]
**Question:** What is the C4 model? Name its four levels and what each shows.
**Model answer:** C4 (Simon Brown) is a hierarchical approach to software architecture diagrams. Four levels of abstraction, each for a different audience:

1. **Context (Level 1):** The system in its environment. Shows your system as a black box, surrounded by users and external systems it interacts with. Audience: non-technical stakeholders. No internal detail.
2. **Container (Level 2):** Inside the system. Shows deployable units — applications, databases, message brokers, mobile apps. Shows how they communicate (REST, async, etc.). Audience: developers and architects.
3. **Component (Level 3):** Inside one container. Shows major components/modules and their responsibilities. Maps to code packages or major Spring beans/services. Audience: developers working on that container.
4. **Code (Level 4):** UML class diagram / code-level view. Usually auto-generated. Only warranted for complex or critical components.

**Why C4 matters in interviews:** it shows you can communicate architecture at the right altitude. A common failure is going straight to component diagrams when a context diagram is what the audience needs.

**Interview trap:** "What's the difference between a C4 Container and a Docker container?" — C4 Container is a deployable/runnable unit (web app, database, microservice, serverless function). It doesn't mean Docker specifically — it could be a WAR file, a Lambda function, or a native executable.
**Tags:** c4-model, documentation, architecture

---

## Q-ARCH-006 [bloom: recall] [level: junior]
**Question:** What is an Architecture Decision Record (ADR)? What does a minimal ADR contain?
**Model answer:** An ADR is a short document that captures an architectural decision, its context, and its consequences. It answers: what did we decide, why, what did we reject, and what are the trade-offs. ADRs live in the codebase (e.g., `docs/adr/`) alongside the code they describe.

**Minimal ADR structure (Nygard format):**
```
# ADR-0042: Use Kafka for inter-service events

## Status
Accepted (2026-01-15)

## Context
Services need to communicate asynchronously. We evaluated RabbitMQ, Kafka, and AWS SQS.

## Decision
Use Apache Kafka for all inter-service async communication.

## Consequences
+ Log retention enables event replay and audit trail
+ High throughput for flash sale event spikes
- Ops complexity: need Kafka cluster (or Confluent Cloud)
- Ordering guarantees only within a partition, not across topics
```

**Why ADRs matter:** architecture decisions get made in Slack, forgotten in 6 months, and re-litigated forever. ADRs create institutional memory. New engineers can understand why the system is the way it is without interrogating the founding team.

**Interview trap:** "What status values does an ADR have?" — Common statuses: Proposed, Accepted, Deprecated, Superseded (by ADR-NNNN). A Superseded ADR is kept — you need to know what was replaced and why.
**Tags:** adr, documentation, architecture

---

## Q-ARCH-007 [bloom: recall] [level: junior]
**Question:** What are the 12 factors of the 12-Factor App methodology? Name at least 8.
**Model answer:** The 12-Factor App (Heroku, 2011) — methodology for building portable, scalable, maintainable SaaS apps:

1. **Codebase** — one codebase tracked in version control; many deploys
2. **Dependencies** — explicitly declare and isolate dependencies (no implicit system-level packages)
3. **Config** — store config in the environment (not in code; `DATABASE_URL`, not hardcoded strings)
4. **Backing services** — treat databases, queues, mail as attached resources (swappable via config)
5. **Build, release, run** — strictly separate build stage / release stage / run stage
6. **Processes** — execute the app as one or more stateless processes (no sticky sessions)
7. **Port binding** — export services via port binding (the app is self-contained, not deployed into a container managed by app server)
8. **Concurrency** — scale out via the process model (horizontal, not vertical)
9. **Disposability** — fast startup, graceful shutdown; processes are disposable
10. **Dev/prod parity** — keep development, staging, and production as similar as possible
11. **Logs** — treat logs as event streams; write to stdout
12. **Admin processes** — run admin/management tasks as one-off processes (migrations, REPL)

**Most commonly violated in enterprise Java:** Factor 3 (config in code or in `application.properties` committed to repo), Factor 6 (stateful singletons), Factor 11 (writing to log files instead of stdout).

**Interview trap:** "Is 12-Factor still relevant with Kubernetes?" — Yes and more so. Kubernetes expects stateless pods (Factor 6), environment-based config via ConfigMaps/Secrets (Factor 3), fast startup for rolling deploys (Factor 9). 12-Factor is effectively a prerequisite for running well in Kubernetes.
**Tags:** twelve-factor, cloud-native, architecture

---

## Q-ARCH-008 [bloom: recall] [level: junior]
**Question:** What is Conway's Law? What is its practical implication for software architecture?
**Model answer:** Conway's Law (Mel Conway, 1968): "Any organization that designs a system will produce a design whose structure is a copy of the organization's communication structure."

In practice: if your organization has a frontend team, backend team, and DBA team, you will get a three-tier architecture regardless of what the architecture diagrams say. Team boundaries become service boundaries.

**Practical implications:**
1. **Inverse Conway Maneuver** — if you want a certain architecture, organize your teams to match it first. Want microservices organized by business capability? Create teams aligned to business capabilities, not technical layers.
2. **Independent team → independent service:** if a team cannot deploy independently (blocked by another team), they effectively share a service.
3. **Shared codebase antipattern:** if 3 teams own the same service, every release becomes a coordination nightmare. Split the code to match the team structure.
4. **Remote vs collocated:** remote teams will create more API boundaries than collocated ones. Both are valid if deliberate.

**Interview trap:** "Can you design an architecture that contradicts your org structure?" — Temporarily, maybe. But over time Conway's Law wins. Architecture without org alignment drifts back to mirror the org. The Inverse Conway Maneuver is the only durable fix.
**Tags:** conways-law, organization, architecture

---

## Q-ARCH-009 [bloom: understand] [level: regular]
**Question:** Explain the three event-driven styles: event notification, event-carried state transfer, and event sourcing. What are the trade-offs of each?
**Model answer:**

**1. Event Notification** — an event signals that something happened; it carries minimal data (just the ID or enough to know what changed). Consumers call back to the source for full state if needed.
- Example: `{ "type": "OrderPlaced", "orderId": "abc-123" }` — consumer fetches `GET /orders/abc-123`
- Trade-offs: `+` small payload, `+` source remains authoritative; `-` extra round-trip query, `-` coupling to source's API, `-` source must be available when consumer processes the event

**2. Event-Carried State Transfer** — event carries all the data the consumer needs (fat event). Consumer needs no callback.
- Example: `{ "type": "OrderPlaced", "orderId": "abc-123", "customerId": "...", "items": [...], "total": 99.99 }`
- Trade-offs: `+` consumer is decoupled from source's read API, `+` works even if source is down; `-` larger payloads, `-` schema evolution is harder (consumers depend on event structure), `-` risk of stale event data if re-processed later

**3. Event Sourcing** — the event log IS the source of truth. State is derived by replaying events. The database stores events, not current state snapshots.
- Example: `OrderCreated`, `ItemAdded`, `PaymentReceived`, `OrderShipped` — replay all to get current Order state
- Trade-offs: `+` complete audit trail, `+` temporal queries ("what did the order look like at T?"), `+` event replay for projections; `-` query complexity (need projections/read models), `-` schema evolution of old events is hard, `-` eventual consistency, `-` high operational complexity. Most systems don't need event sourcing — it solves specific problems and introduces significant complexity.

**Interview trap:** "Should you always use Event Sourcing with CQRS?" — No. CQRS (separate read/write models) is useful independently of event sourcing. Event sourcing is one way to implement the write side, but you can have CQRS with traditional persistence. The two are complementary, not synonymous.
**Tags:** event-driven, event-sourcing, cqrs, architecture

---

## Q-ARCH-010 [bloom: understand] [level: regular]
**Question:** What is CQRS? What problem does it solve, and what does it cost?
**Model answer:** CQRS (Command Query Responsibility Segregation, Greg Young) — separate the model used to handle write operations (Commands) from the model used to handle read operations (Queries).

**Write side (Command):** receives commands (`PlaceOrderCommand`), validates, runs business logic on the domain model, persists changes (events or state), emits domain events. Optimized for correctness and invariant enforcement.

**Read side (Query):** a separate read model (projection) optimized for the query shape needed by the UI. Often denormalized, sometimes pre-joined, possibly stored in a different datastore (Redis, Elasticsearch, read-replica).

```
Client → Command → Command Handler → Aggregate → Repository (write DB)
                                          ↓ DomainEvent
                                    Event Handler → Read Model (read DB)
Client → Query  → Query Handler → Read Model Repository → DTO
```

**Problems it solves:**
1. **Impedance mismatch:** domain model optimized for writes (aggregates enforce invariants) is often terrible for reads (complex joins, N+1). CQRS lets each side optimize independently.
2. **Scaling asymmetry:** reads vastly outnumber writes (100:1). CQRS allows scaling read replicas independently.
3. **Complex queries:** read model can be a pre-joined, denormalized projection — O(1) reads instead of expensive JOINs.

**What it costs:**
- Eventual consistency between write DB and read model (seconds to milliseconds lag)
- Two data models to maintain; synchronization complexity
- More code: command handlers + event handlers + projections
- Overkill for simple CRUD — adds ceremony with no gain

**Interview trap:** "When would you NOT use CQRS?" — When consistency requirements are strict (user edits and immediately sees the edit), when read and write complexity is similar (simple CRUD), when the team is small and the overhead isn't worth it.
**Tags:** cqrs, architecture, read-model, write-model

---

## Q-ARCH-011 [bloom: understand] [level: regular]
**Question:** What is DDD's strategic design? Explain Bounded Context and Ubiquitous Language.
**Model answer:** Strategic DDD is about how to carve a large, complex domain into manageable pieces and how those pieces relate.

**Bounded Context:** an explicit boundary within which a particular domain model applies. Within the boundary, terms have precise, unambiguous meanings. The same word can mean different things in different contexts — that is correct and expected. Example: `Product` in the `Catalog` context = rich description with images, attributes, SEO metadata. `Product` in the `Inventory` context = SKU, warehouse location, stock count. Same real-world thing, two different models optimized for their purpose. Forcing one `Product` class to serve both leads to an anemic, overcomplicated mess.

Physical boundary: typically a service (or module in a monolith) with its own database.

**Ubiquitous Language:** a shared vocabulary between developers and domain experts (product managers, business analysts), used consistently in code, tests, documentation, and conversation. If the business calls it an "offer promotion" the code has `OfferPromotion`, the DB table is `offer_promotion`, the API endpoint is `/offer-promotions`. No translation layer in your head. When developers catch themselves translating from code terms to business terms in meetings — the language is broken. Ubiquitous Language is discovered through collaboration (Event Storming, domain workshops), not decreed.

**Interview trap:** "Can two services share a Bounded Context?" — Technically yes, if they both implement the same model. But it's rare and usually a sign the service boundary is wrong. More commonly a single service owns its bounded context.
**Tags:** ddd, strategic-design, bounded-context, ubiquitous-language

---

## Q-ARCH-012 [bloom: understand] [level: regular]
**Question:** Name and describe the six main Context Map relationship patterns in DDD.
**Model answer:** Context Map patterns describe how two Bounded Contexts relate and who has the power in the relationship:

| Pattern | Description | Power |
|---|---|---|
| **Partnership** | Two teams collaborate closely, coordinate releases, evolve APIs together | Equal |
| **Shared Kernel** | Two contexts share a common subset of the model (shared library, shared DB schema). Changes require agreement | Equal but risky |
| **Customer-Supplier** | Upstream (Supplier) publishes API; downstream (Customer) adapts to it. Supplier prioritizes Customer's needs | Upstream power |
| **Conformist** | Downstream conforms to upstream's model without negotiation power. Upstream doesn't adapt for downstream | Upstream power, downstream accepts |
| **Anti-Corruption Layer (ACL)** | Downstream builds a translation layer to shield its own model from upstream's model. Protects domain purity | Downstream protects itself |
| **Published Language** | Upstream publishes a well-defined, versioned API/schema (OpenAPI, Avro, Protobuf) for all consumers. Consumers conform | Upstream defines the contract |
| **Open Host Service** | Upstream provides a protocol/API accessible to many consumers. Often combined with Published Language | — |
| **Separate Ways** | Teams decide not to integrate — solve the same problem independently | — |

**Most important for interviews:** ACL — it's the defensive pattern. If you integrate with a legacy system or an external API that has a terrible model, you don't let that model infect your domain. You build an ACL that translates their `UserDTO` into your `Customer` aggregate.

**Interview trap:** "When do you use Conformist vs ACL?" — Conformist when upstream's model is acceptable and the integration cost of ACL is too high. ACL when upstream's model would corrupt your domain with bad naming, missing invariants, or entangled concepts. ACL has a maintenance cost — it's a deliberate trade-off.
**Tags:** ddd, context-map, anti-corruption-layer, strategic-design

---

## Q-ARCH-013 [bloom: understand] [level: regular]
**Question:** Describe the core DDD tactical building blocks: Entity, Value Object, Aggregate, Aggregate Root, Repository, Domain Service, Domain Event, Factory. One sentence each.
**Model answer:**

- **Entity:** a domain object with a unique identity that persists through state changes; equality is by identity, not by data values (`Order` with id=42 is still that order even when its status changes).
- **Value Object:** an immutable domain object defined entirely by its attributes; no identity, equality by value (`Money(100, EUR)` == `Money(100, EUR)` always; implemented as Kotlin `data class` or Java record).
- **Aggregate:** a cluster of domain objects (entities + VOs) treated as a single consistency unit; all invariants within the boundary are enforced atomically; a single transaction never spans two aggregates.
- **Aggregate Root:** the sole entry point to an aggregate; external code holds only a reference to the root; all mutations go through root methods that enforce invariants.
- **Repository:** an abstraction that provides collection-like access to aggregates; one repository per aggregate root; the interface lives in the domain layer, implementation in infrastructure.
- **Domain Service:** stateless, behavior-only object containing business logic that doesn't naturally fit any single aggregate or entity (e.g., `PriceCalculationService` combining Product + Customer + Promotions).
- **Domain Event:** an immutable record of something meaningful that happened in the domain (past tense: `OrderPlaced`, `PaymentFailed`); carries the data true at the time it occurred; used to decouple bounded contexts.
- **Factory:** encapsulates complex creation logic for aggregates or domain objects; ensures the created object is always in a valid state; keeps the constructor clean (private constructor + factory method or static `create()`).

**Interview trap:** "Can an Aggregate Root be a Value Object?" — No. An Aggregate Root is by definition an Entity — it has identity (its ID is used to look it up via Repository). Value Objects don't have identity and are not persisted independently.
**Tags:** ddd, tactical-design, entity, value-object, aggregate, repository

---

## Q-ARCH-014 [bloom: understand] [level: regular]
**Question:** What are quality attributes (the "-ilities")? Name 8 and describe the most common trade-offs between them.
**Model answer:** Quality attributes (non-functional requirements) define HOW a system behaves, not WHAT it does. They drive architectural decisions.

Key quality attributes:
- **Availability** — system is operational and accessible (e.g., 99.99% uptime = 52 min downtime/year)
- **Reliability** — system produces correct results consistently
- **Scalability** — handles increased load (horizontal: add nodes; vertical: bigger node)
- **Performance** (latency + throughput)
- **Security** — confidentiality, integrity, authentication, authorization
- **Maintainability** — ease of changing, understanding, and extending the system
- **Testability** — ease of validating behavior through automated tests
- **Deployability** — ease and safety of releasing new versions
- **Observability** — ability to understand system state from its outputs (logs, metrics, traces)
- **Consistency** — data is correct across all nodes at all times
- **Portability** — runs on different environments

**Key trade-offs:**
- **Consistency vs Availability** (CAP theorem): in a network partition, choose one. Strong consistency (all reads see latest write) costs availability. High availability means accepting eventual consistency.
- **Performance vs Maintainability:** highly optimized code (bit manipulation, unsafe operations) is fast but unreadable and unmaintainable.
- **Security vs Deployability:** air-gapped environments, change approval boards, and HSMs slow down deployment.
- **Scalability vs Consistency:** distributed systems can scale but at the cost of consistency guarantees.
- **Availability vs Cost:** 99.999% uptime requires redundancy at every layer — expensive.

**Interview trap:** "Is there ever a wrong answer to which quality attribute matters most?" — Yes: ignoring the business context. An internal analytics tool has different availability requirements than a payment gateway. Always anchor quality attribute priorities to the business scenario.
**Tags:** quality-attributes, ilities, trade-offs, architecture

---

## Q-ARCH-015 [bloom: understand] [level: regular]
**Question:** What is coupling and cohesion at the service level? What does high coupling / low cohesion look like in a microservices system?
**Model answer:** **Cohesion:** how closely related the responsibilities within a service are. High cohesion = the service does one thing well, all its components work toward the same purpose. Low cohesion = the service is a dumping ground for unrelated functionality.

**Coupling:** how much one service depends on another. Low coupling = services can be changed, deployed, or restarted independently. High coupling = changes in Service A require changes in Service B.

**High coupling antipatterns:**
1. **Synchronous call chains:** Order → Inventory → Pricing → Promotion → Payment (a chain of 5 synchronous calls). A failure or slowdown at step 3 cascades to all upstream callers.
2. **Shared database:** two services read/write the same table. Schema change to that table requires coordinated deployment of both services.
3. **Chatty services:** Service A calls Service B 10 times per request. Network overhead, tight temporal coupling.
4. **Shared domain model library:** a fat shared library containing domain entities used by 5 services. Adding a field to `Order` requires releasing 5 services.

**Low cohesion antipatterns:**
1. **God service:** one service handles Orders, Recommendations, User Profiles, and Analytics.
2. **Feature envy:** a service that mostly manipulates data owned by another service.

**Desirable:** High cohesion + Low coupling. Measured by: can you change and deploy Service A without touching Service B? Can you understand Service A without reading Service B's code? If the answer is no — you have a coupling problem.

**Interview trap:** "What's the difference between temporal coupling and logical coupling?" — Temporal coupling: two services must be available simultaneously (synchronous calls). Logical coupling: a change to one service's logic requires changing another. Both are bad; temporal coupling is often overlooked.
**Tags:** coupling, cohesion, microservices, architecture

---

## Q-ARCH-016 [bloom: understand] [level: regular]
**Question:** What is microkernel (plugin) architecture? What is pipes-and-filters? Give a concrete example of each.
**Model answer:** **Microkernel (Plugin) Architecture:** a minimal core system (microkernel) that provides the base functionality, with additional features added as plugins. The core defines extension points; plugins implement them.

Examples:
- **IDE (IntelliJ IDEA):** core provides text editing, project model, UI framework. Every language, build tool, VCS integration is a plugin.
- **Spring Framework:** core IoC container is the microkernel. Spring MVC, Spring Data, Spring Security are all plugins.
- **Jenkins:** core handles CI orchestration; all build tools, SCMs, notifications are plugins.
- **E-commerce promotions engine:** core applies promotions in a defined order; each promotion type (BOGO, percentage, threshold) is a plugin implementing `PromotionStrategy`.

Trade-offs: `+` extensible without changing the core, `+` third-party can add plugins; `-` plugin API is a public contract — hard to evolve, `-` plugin isolation (a bad plugin can crash the core if not sandboxed).

**Pipes-and-Filters:** a processing pipeline where data flows through a sequence of independent processing stages (filters). Each filter does one transformation; pipes connect them. Output of one filter is input to the next.

Examples:
- **Unix command line:** `cat log.txt | grep ERROR | awk '{print $3}' | sort | uniq -c`
- **Image processing pipeline:** decode → resize → watermark → compress → store
- **ETL pipeline:** extract → validate → transform → load
- **HTTP request processing in Spring:** filter chain (`OncePerRequestFilter` sequence) — authentication → authorization → rate limiting → logging → business handler

Trade-offs: `+` filters are independently testable, `+` easy to reorder/add stages; `-` data between stages may need serialization overhead, `-` sequential processing — one slow filter blocks the pipeline.

**Interview trap:** "When would you choose microkernel over a simple strategy pattern?" — Scale of extensibility. Strategy pattern is fine when you control all implementations. Microkernel is for when external parties (or separate teams) will provide extensions, and you need a formal plugin lifecycle (load, validate, unload).
**Tags:** microkernel, plugin, pipes-and-filters, architecture

---

## Q-ARCH-017 [bloom: apply] [level: senior]
**Question:** Your team is designing a new e-commerce platform. How do you identify Bounded Context boundaries? Walk through the process.
**Model answer:** Bounded Context boundaries are discovered, not invented. The process:

**1. Event Storming:** run a workshop with domain experts (PM, operations, finance, customer support) and developers. Use sticky notes: orange = domain events (past tense), blue = commands, yellow = aggregates, red = hotspots/questions. Map the entire business flow end-to-end. Typical e-commerce flow: `ProductListed → OfferCreated → CustomerBrowsed → ItemAddedToCart → OrderPlaced → PaymentProcessed → OrderFulfilled → PackageShipped → OrderDelivered`.

**2. Identify natural pivots:** look for places where: (a) the vocabulary changes (same word means different things), (b) different teams own the process, (c) different data consistency requirements, (d) different change rates. These are boundary candidates.

**3. E-commerce example boundaries:**
```
Catalog      — product information, attributes, taxonomy, search indexing
Inventory    — stock levels, warehouse locations, reservations
Ordering     — order lifecycle: cart → checkout → placed → confirmed
Payment      — payment methods, authorization, capture, refunds
Fulfillment  — pick, pack, ship; carrier integration
Promotions   — discount rules, campaign lifecycle, coupon management
Recommendations — user behavior, scoring, personalization
Customer     — profile, address book, loyalty tier
Notifications — email, SMS, push; templates, delivery status
```

**4. Apply heuristics:**
- Single team owns the context (two-pizza rule)
- Can be deployed independently
- Has its own data store
- Domain events cross the boundary (not internal method calls)

**5. Validate with the "alien replacement" test:** could you replace the Catalog service with one from a third-party vendor? If yes, it's a well-bounded context.

**Interview trap:** "Should you always have one service per bounded context?" — In a modular monolith, one module per context. In microservices, usually one service per context. But a context can be implemented by multiple services if they share the same model — though this is a warning sign.
**Tags:** ddd, bounded-context, event-storming, architecture

---

## Q-ARCH-018 [bloom: apply] [level: senior]
**Question:** Design the aggregate boundary for an e-commerce `Order`. What is inside, what is outside, what invariants does the aggregate enforce, and how do you handle concurrent modifications?
**Model answer:**

**Inside the Order aggregate:**
- `Order` (aggregate root) — id, customerId (reference by ID, not object), status, placedAt
- `OrderLine` (entity) — lineId, productId, productName (snapshot), quantity, unitPrice, subtotal
- `DeliveryAddress` (value object) — snapshot of address at order time
- `Money` total (value object)

**Outside (referenced by ID only):**
- `Customer` — not inside Order; Order holds `customerId: UUID`
- `Product` — not inside Order; `OrderLine` holds a snapshot of name/price at order time (price can change; order records what was paid)
- `Promotion` — applied during `place()`, result stored as `discountAmount`, not a live reference

**Invariants enforced by the aggregate:**
```kotlin
class Order private constructor(val id: OrderId, val customerId: CustomerId, ...) {
    private val lines: MutableList<OrderLine> = mutableListOf()
    var status: OrderStatus = OrderStatus.DRAFT; private set

    fun addLine(productId: ProductId, name: String, qty: Int, unitPrice: Money) {
        check(status == OrderStatus.DRAFT) { "Cannot modify a placed order" }
        require(qty > 0) { "Quantity must be positive" }
        require(lines.size < 100) { "Order cannot exceed 100 lines" }
        lines.add(OrderLine(LineId.generate(), productId, name, qty, unitPrice))
    }

    fun place() {
        check(lines.isNotEmpty()) { "Cannot place an empty order" }
        check(status == OrderStatus.DRAFT) { "Order already placed" }
        status = OrderStatus.PLACED
        registerEvent(OrderPlaced(id, customerId, lines.toList(), placedAt = Instant.now()))
    }
}
```

**Concurrent modification — optimistic locking:**
```java
@Entity
@OptimisticLocking(type = OptimisticLockType.VERSION)
class OrderJpaEntity {
    @Version
    val version: Long = 0
}
```
If two requests simultaneously modify the same order, one will get `OptimisticLockException`. Retry is appropriate if the conflict is transient; surface the conflict to the user if business logic dictates (e.g., item was removed by another session).

**Keep aggregates small:** if you find aggregates with 20+ entities, they're usually trying to be two aggregates. Large aggregates = high contention = performance problems at scale.

**Interview trap:** "Should `OrderLine` have its own repository?" — No. Only aggregate roots have repositories. `OrderLine` is an internal entity; it's only accessible through `Order`. If you need to find lines by product ID, do it via the `Order` repository with a query.
**Tags:** ddd, aggregate, order-aggregate, concurrency, ecommerce

---

## Q-ARCH-019 [bloom: apply] [level: senior]
**Question:** Describe how you would implement CQRS for an Order service. What is on the write side, what is on the read side, and how are they synchronized?
**Model answer:**

**Write side (Command model):**
```kotlin
// Command
data class PlaceOrderCommand(val customerId: UUID, val items: List<OrderItemDto>)

// Command Handler (Application Service)
@Service
@Transactional
class PlaceOrderCommandHandler(
    private val orderRepository: OrderRepository,      // domain port
    private val eventPublisher: DomainEventPublisher   // domain port
) {
    fun handle(cmd: PlaceOrderCommand): OrderId {
        val order = Order.create(CustomerId(cmd.customerId), cmd.items.map { ... })
        orderRepository.save(order)
        order.domainEvents().forEach { eventPublisher.publish(it) }
        return order.id
    }
}
```

**Read side (Query model):**
```kotlin
// Flat, denormalized read model — no joins needed at query time
data class OrderSummaryView(
    val orderId: UUID,
    val customerName: String,  // denormalized from Customer context
    val totalAmount: BigDecimal,
    val status: String,
    val itemCount: Int,
    val placedAt: Instant
)

// Query Handler — thin, reads from optimized read store
@Service
class GetOrderSummaryHandler(private val readStore: OrderReadStore) {
    fun handle(query: GetOrderSummariesQuery): List<OrderSummaryView> =
        readStore.findByCustomerId(query.customerId, query.pageRequest)
}
```

**Synchronization via domain events:**
```
Order aggregate → emits OrderPlaced event
    ↓
Transactional Outbox (same DB transaction as the write)
    ↓
Message relay → Kafka topic `order.placed`
    ↓
OrderProjectionHandler (event listener) → updates OrderSummaryView in read store (PostgreSQL read replica / Redis / Elasticsearch)
```

**Eventual consistency window:** typically milliseconds to a few seconds. Callers must tolerate "order just placed, summary not yet visible." The UI can optimistically display the order immediately from the command response while the projection catches up.

**Read store choice:** SQL read replica for complex queries, Redis for hot summary data, Elasticsearch for full-text order search.

**Interview trap:** "What if the event handler fails — does the read model get permanently out of sync?" — With Transactional Outbox + at-least-once delivery, the event will be retried. Idempotent event handlers (check if projection already updated) prevent double-apply. Without Outbox, the write succeeds but the event is lost — you get inconsistency permanently.
**Tags:** cqrs, order-service, event-driven, read-model, transactional-outbox

---

## Q-ARCH-020 [bloom: apply] [level: senior]
**Question:** You are integrating your Order service with a legacy ERP system that has a terrible data model (customer IDs are 8-character strings, order statuses are numeric codes, and it uses its own concept of "transaction" that maps loosely to your Order). How do you protect your domain?
**Model answer:** This is exactly the Anti-Corruption Layer (ACL) pattern. The legacy ERP is an upstream, and its model must not infect your bounded context.

**ACL structure:**

```kotlin
// Your domain model — clean
data class OrderId(val value: UUID)
enum class OrderStatus { DRAFT, PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }
data class CustomerId(val value: UUID)

// ERP's model (external, ugly)
data class ErpTransaction(
    val txnId: String,         // "TXN00042"
    val custCode: String,      // "C0012345" — their customer ID
    val statusCode: Int,       // 10=open, 20=confirmed, 30=shipped, 99=closed
    val lineItems: List<ErpLineItem>
)

// Anti-Corruption Layer — lives in your infrastructure layer
class ErpOrderAdapter(private val erpClient: ErpHttpClient) {

    fun fetchOrder(orderId: OrderId): Order {
        val txn = erpClient.getTransaction(toErpId(orderId))
        return translateToOrder(txn)
    }

    private fun translateToOrder(txn: ErpTransaction): Order =
        Order(
            id = fromErpId(txn.txnId),
            customerId = resolveCustomerId(txn.custCode),
            status = translateStatus(txn.statusCode),
            lines = txn.lineItems.map { translateLine(it) }
        )

    private fun translateStatus(code: Int): OrderStatus = when (code) {
        10 -> OrderStatus.PLACED
        20 -> OrderStatus.PAID
        30 -> OrderStatus.SHIPPED
        99 -> OrderStatus.DELIVERED
        else -> throw IllegalArgumentException("Unknown ERP status code: $code")
    }

    private fun toErpId(orderId: OrderId): String = "TXN${orderId.value.toString().takeLast(5).uppercase()}"
    private fun fromErpId(erpId: String): OrderId = /* lookup or generate */ OrderId(UUID.randomUUID())
    private fun resolveCustomerId(custCode: String): CustomerId = /* lookup in customer mapping table */ ...
}
```

**Key principles:**
1. ERP types never appear in the domain layer — only in the ACL (infrastructure layer)
2. ACL is a translation boundary: ERP model → your domain model
3. The ACL can also buffer against ERP schema changes — only the ACL needs to update, not your domain
4. If ERP has a concept that has no equivalent in your model, map to the closest domain concept or ignore it deliberately

**Interview trap:** "Who owns the ACL?" — Your team. The upstream (ERP vendor) doesn't know or care about it. You build it to protect yourself. The ACL is entirely on the downstream side.
**Tags:** anti-corruption-layer, ddd, integration, legacy, ecommerce

---

## Q-ARCH-021 [bloom: apply] [level: senior]
**Question:** Walk through how Domain Events flow in a Spring Boot application — from an aggregate raising an event to a subscriber in another bounded context receiving it. What failure modes exist?
**Model answer:**

**Step 1: Aggregate raises event (in-memory)**
```kotlin
class Order(...) : AggregateRoot() {
    fun place() {
        check(status == DRAFT)
        status = PLACED
        registerDomainEvent(OrderPlaced(id, customerId, Instant.now()))
    }
}
```
`AggregateRoot` base class holds a list of pending events. No infrastructure dependency.

**Step 2: Application Service saves and publishes**
```kotlin
@Transactional
fun placeOrder(cmd: PlaceOrderCommand) {
    val order = orderRepository.findById(cmd.orderId) ?: error("not found")
    order.place()
    orderRepository.save(order)
    // WRONG: publishing here, after save, outside the transaction
    // eventPublisher.publish(order.popEvents()) — event lost if crash here
}
```

**Problem: publish-after-save race condition.** If the app crashes between `save` and `publish`, the DB has the new state but the event was never sent. Consumers never know the order was placed.

**Fix: Transactional Outbox Pattern**
```kotlin
@Transactional
fun placeOrder(cmd: PlaceOrderCommand) {
    val order = orderRepository.findById(cmd.orderId)!!
    order.place()
    orderRepository.save(order)
    // Write event to outbox in the SAME transaction
    outboxRepository.saveAll(order.popEvents().map { OutboxMessage(it) })
    // No external publish here — transaction commits both atomically
}
// Separate process: OutboxRelayJob polls outbox table, publishes to Kafka, deletes row
```

**Step 3: Event relay → Kafka → consumer**
```
OutboxRelayJob (or Debezium CDC) → publishes OrderPlaced to Kafka
Consumer (RecommendationService) → @KafkaListener → ACL translates OrderPlaced into PurchaseRecorded → updates recommendation model
```

**Failure modes:**
1. App crash between save and publish → fixed by Outbox (event stays in DB)
2. Kafka down → Outbox accumulates, replays when Kafka recovers
3. Consumer down → Kafka retains messages until consumer catches up
4. Consumer processes event twice (at-least-once delivery) → idempotent handler (check `event.id` already processed)
5. Schema mismatch between producer and consumer → Avro + Schema Registry, or explicit versioning strategy

**Interview trap:** "Can you use Spring's `ApplicationEventPublisher` for cross-service events?" — For in-process, same-transaction events yes (Spring publishes them within the transaction using `@TransactionalEventListener`). For cross-service (Kafka/async), you need the Outbox pattern — `ApplicationEventPublisher` publishes only within the same JVM.
**Tags:** domain-events, transactional-outbox, kafka, spring-boot, ecommerce

---

## Q-ARCH-022 [bloom: apply] [level: senior]
**Question:** What is the Strangler Fig pattern? How would you use it to extract a Promotions bounded context from a monolith into its own service?
**Model answer:** Strangler Fig (Martin Fowler, 2004) — incrementally migrate a legacy system by creating a new service alongside it. Route traffic to the new service for specific functionality. Once the new service handles the full feature set, the old code path is "strangled" (removed). Named after the fig tree that grows around a host tree.

**Applied to extracting Promotions from a monolith:**

**Phase 1: Identify seams.** Map all code paths that touch promotion logic. Who calls what? Typically: `OrderService.calculateDiscount()` → `PromotionRepository` → `promotions` table. Also: admin UI for managing promotions, batch job for expiring promotions.

**Phase 2: Build new Promotions service alongside the monolith.**
- New service owns `promotions` DB (separate schema initially, or separate DB)
- Implements all promotion CRUD and `POST /promotions/apply` endpoint
- No traffic yet — dark launch / shadow mode optional

**Phase 3: Install the facade (Strangler Fig proxy).**
```
Monolith code → PromotionFacade → HTTP call → new Promotions service
                (instead of → PromotionRepository)
```
The monolith still orchestrates; it now delegates to the new service via the facade. The monolith's `PromotionRepository` is replaced by `PromotionServiceClient` implementing the same interface.

**Phase 4: Migrate data.** Dual-write period: write to both old DB and new service. Verify data consistency. Cut over reads to new service. Stop writing to old DB. Backfill any gaps.

**Phase 5: Strangle the old code.** Remove the old `PromotionRepository`, `PromotionService`, `promotions` table, and admin routes from the monolith. Promote new service to standalone.

**Failure modes:** dual-write inconsistency, facade adding latency, monolith still owning some promotion logic (partial strangling). Each phase should be reversible — feature flags to route back to old path if new service has issues.

**Interview trap:** "What's the difference between Strangler Fig and Branch by Abstraction?" — Branch by Abstraction is the code-level technique (introduce an interface, swap implementations). Strangler Fig is the system-level migration strategy (route traffic progressively to new service). They're complementary — Branch by Abstraction is often how you implement the facade step in Strangler Fig.
**Tags:** strangler-fig, migration, microservices, architecture, ecommerce

---

## Q-ARCH-023 [bloom: apply] [level: senior]
**Question:** Design the architecture for an event sourcing implementation of an Order aggregate. What are the read model and projection concerns?
**Model answer:**

**Event Sourcing basics:** instead of storing the current state of an Order, store every event that ever happened to it. Current state is derived by replaying events.

**Event store schema:**
```sql
CREATE TABLE order_events (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB NOT NULL,
    occurred_on  TIMESTAMPTZ NOT NULL,
    sequence_num BIGINT NOT NULL,  -- per-aggregate sequence for ordering
    UNIQUE(order_id, sequence_num)
);
CREATE INDEX ON order_events (order_id, sequence_num);
```

**Reconstituting an aggregate:**
```kotlin
class OrderRepository(private val eventStore: EventStore) {
    fun findById(id: OrderId): Order {
        val events = eventStore.loadEvents(id)
        return Order.reconstitute(events)  // replay all events to derive state
    }
}

class Order {
    companion object {
        fun reconstitute(events: List<DomainEvent>): Order {
            val order = Order()
            events.forEach { order.apply(it) }
            return order
        }
    }
    private fun apply(event: DomainEvent) = when (event) {
        is OrderCreated -> { this.id = event.orderId; this.status = DRAFT }
        is OrderPlaced  -> { this.status = PLACED }
        is OrderShipped -> { this.status = SHIPPED; this.shippedAt = event.shippedAt }
        else -> {}
    }
}
```

**Snapshots:** loading 10,000 events per aggregate on every request is slow. Add snapshots: periodically serialize aggregate state; on load, find latest snapshot and replay only events after it.

**Projections (read models):**
```
OrderEvents stream → ProjectionHandler → OrderSummaryTable (RDBMS)
                                       → OrderSearchIndex (Elasticsearch)
                                       → CustomerOrderHistoryCache (Redis)
```
Each projection is an event consumer that builds its own optimized view. Multiple projections can be built from the same event stream. If a projection needs rebuilding (bug fix, new requirement) — replay the event log from the beginning.

**Critical gotchas:**
1. **Schema evolution:** `OrderPlaced` v1 has no `promotionCode`; v2 does. Old events in the store don't have this field. Handle via upcasters (transform old event format to current format during replay) or tolerant reader pattern.
2. **Event sourcing ≠ CQRS:** you need CQRS (separate read models) because querying event streams directly is impractical.
3. **Performance:** snapshot + projection caching. Don't replay full history on every request.
4. **Event log is immutable:** you can't fix a bug by updating old events. You compensate with correcting events.

**Interview trap:** "Can you delete data from an event sourced system (GDPR right to erasure)?" — Hard problem. Options: encrypt event payload with a customer-specific key; when erasure is requested, delete the key (crypto-shredding). Event structure remains, data becomes unreadable. Or: store PII in a separate, deletable store and reference it from events by ID.
**Tags:** event-sourcing, cqrs, ddd, architecture, projections

---

## Q-ARCH-024 [bloom: analyze] [level: master]
**Question:** Your team argues about whether to use a modular monolith or microservices for a new product. The team has 8 engineers, the domain is partially understood, and the CEO wants to scale to 10M users in 18 months. What is your recommendation and why?
**Model answer:** The answer is "modular monolith now, microservices extraction later — if needed." Here's the full reasoning:

**Why not microservices at day one:**
1. **Domain unknown:** 8 engineers, partially understood domain = wrong aggregate boundaries guaranteed. Incorrect service boundaries in microservices = distributed monolith (shared DB, synchronous call chains). Unmigrating is brutal.
2. **8 engineers can't staff microservices properly.** Each service needs: CI/CD pipeline, observability, service discovery, health checks, retry/circuit breaker. At 8 engineers you'd spend 40% on infrastructure, 60% on business logic. A modular monolith offloads most of this to the framework.
3. **10M users in 18 months is a scaling requirement, not an architecture requirement.** Monoliths can scale horizontally (stateless, multiple instances behind load balancer). Many 10M+ user systems run on well-engineered monoliths.
4. **Time to market matters.** Microservices slow down early development (every feature requires coordinating 3 services). Modular monolith delivers faster.

**How to set up the modular monolith for future extraction:**
- Each module has its own package, its own service interfaces, its own data access layer (separate DB schemas or at minimum separate table prefixes)
- No cross-module direct calls to repositories — only through published interfaces
- Domain events published in-process (Spring `ApplicationEventPublisher`) — same mechanism that will later be Kafka events when extracted
- No shared domain objects across module boundaries — each module has its own DTOs for inter-module communication

**When to extract a service:**
- A module's scaling requirements diverge sharply from the rest (Recommendation engine needs ML infrastructure; Order service doesn't)
- A team owns a module end-to-end and their deployment cycle is blocked by the monolith
- A module has different operational requirements (different runtime, different language)
- The module's bounded context is stable and well-understood

**The critical question:** "What would make you regret this decision?" If the domain turns out to be trivially simple (few modules, no contention), the modular monolith stays — no regrets. If the modules turn out to need vastly different scaling, extraction is straightforward because the boundaries are clean.

**Interview trap:** "Netflix/Amazon/Uber used microservices and they scaled." — Netflix started with a monolith and migrated over years with hundreds of engineers. Amazon had 100+ engineers when they started the SOA migration. Survivorship bias: the companies that tried microservices at day one and failed never gave a conference talk.
**Tags:** architecture, decision-making, modular-monolith, microservices, trade-offs

---

## Q-ARCH-025 [bloom: analyze] [level: master]
**Question:** A colleague proposes event sourcing for the entire platform "because it gives us a full audit trail." What are the specific failure modes, costs, and alternatives you would raise in the architecture review?
**Model answer:** Event sourcing is one of the most over-applied architectural patterns in modern backend. Here is the full cost/benefit analysis to surface in review:

**Legitimate use cases for event sourcing:**
- Financial ledgers (every debit/credit is inherently an event; double-entry bookkeeping IS event sourcing)
- Audit-critical domains where the history of how state was reached is a business requirement, not a nice-to-have
- Temporal queries ("what did the customer's cart look like at 14:23:07?")
- Complex undo/redo workflows
- High-volume write-intensive systems where CQRS read models need to be rebuilt from scratch

**Specific failure modes and costs:**

1. **Schema evolution hell.** Events are permanent records. `OrderPlaced` v1 didn't have `promotionCode`. In 2 years you have 50M events in v1 format and need to add `promotionCode` to the domain model. You need an upcaster that transforms v1 → v2 at replay time. For a 5-year-old system with 20 evolved events, the upcaster chain becomes a nightmare.

2. **GDPR / right to erasure is fundamentally at odds with immutable logs.** Crypto-shredding (encrypt PII, delete key) works but adds complexity. Regular deletion of personal data is incompatible with true event sourcing.

3. **Operational complexity.** Event store needs to be append-only, globally consistent within an aggregate, and performant at replay. EventStoreDB is specialized for this; using PostgreSQL as an event store works but requires discipline. Snapshots, projection rebuilds, and event versioning are operational burdens.

4. **Query complexity.** "Give me all orders above €100 placed in November" — impossible against the event store without a projection. Every query shape needs a pre-built read model. For a team used to SQL, this is a significant cognitive overhead.

5. **Debugging difficulty.** Bugs in aggregate logic can silently corrupt projections. Replay reveals the bug — but old events are immutable. Fix: add a compensating event, not update the old one.

**Alternatives for the stated goal (audit trail):**

1. **Append-only audit table:** separate `order_audit_log` table. Write a row for every significant state change with timestamp, actor, old value, new value. 90% of the audit trail benefit at 5% of the complexity cost.

2. **Database-level CDC (Change Data Capture):** Debezium on PostgreSQL streams every row change. Full audit trail without changing application code.

3. **Event notification (not sourcing):** publish domain events to Kafka for integration and analytics without making the event log the source of truth.

**Recommendation:** Apply event sourcing to the one or two aggregates where the event history IS the domain (ledger, order lifecycle in a financial context), not to the entire platform. The "full audit trail" use case is almost always solvable with a simpler pattern.

**Interview trap:** "Doesn't EventStoreDB solve all these problems?" — It solves the storage and ordering problems. It doesn't solve schema evolution, GDPR, projection complexity, or the cognitive overhead of the CQRS model you must build alongside it.
**Tags:** event-sourcing, architecture, trade-offs, audit, master

---

## Q-ARCH-026 [bloom: analyze] [level: master]
**Question:** Your platform has 15 microservices. Engineers report that making a cross-cutting change (e.g., adding a new field to the Customer concept shared across 8 services) takes 3 sprints and 4 coordinated deployments. Diagnose the architectural problem and propose a fix.
**Model answer:** This is a classic distributed monolith / shared kernel coupling problem. Three symptoms, each pointing to a different root cause:

**Diagnosis:**

**Cause 1: Shared domain model artifact.** A common `domain-commons` library contains the `Customer` class. 8 services depend on it. Adding a field = bump library version = update 8 services = 8 PRs + 8 deployments.

**Cause 2: No bounded context isolation.** Services are treating `Customer` as a global concept rather than their local view of a customer. The `Order` service doesn't need `Customer.loyaltyPoints` (that's a `Loyalty` context concern). The `Shipping` service doesn't need `Customer.taxExemptionCode`. Each service should have its own `Customer` representation containing only the fields it cares about.

**Cause 3: Synchronous coupling.** Services calling a central `CustomerService` on the hot path. Every change to `CustomerService`'s API requires callers to update.

**Fix 1: Eliminate the shared domain library.** Each service defines its own Customer representation. If Service A needs customer name and email, it has `CustomerSummary(id, name, email)`. If Service B needs customer tier, it has `CustomerProfile(id, tier)`. No shared library.

**Fix 2: Apply the "data sovereignty" principle.** The `Customer` service owns the canonical customer data. Other services subscribe to `CustomerProfileUpdated` events and maintain their own local copy of the fields they care about. This is Event-Carried State Transfer. 8 services no longer need to call Customer service at all — they query their own local projection.

**Fix 3: Versioned event schemas (Avro + Schema Registry).** When adding `loyaltyTier` to `CustomerProfileUpdated`, the new field is optional. Old consumers ignore it (tolerant reader). Only consumers that care subscribe to the new version. No coordinated deployment.

**Fix 4: Identify the actual change frequency.** If "customer" changes every sprint, the concept is under-modeled. Run an Event Storming session. Maybe `LoyaltyProfile`, `BillingProfile`, and `ShippingProfile` should be separate aggregates with separate change rates.

**Expected outcome:** a cross-cutting change to the Customer concept should require: 1 event schema change (with backward compatibility) + deployment of the Customer service. Downstream services update lazily as they consume events. Zero coordinated deployments.

**Interview trap:** "What if services need the customer data RIGHT NOW at request time (can't wait for events)?" — For synchronous lookups: the customer service is a dependency, but services should cache aggressively (Redis, local in-memory with TTL) and tolerate stale data by seconds/minutes. If a service truly needs consistent, real-time customer data on every request — it's too tightly coupled; revisit whether it's in the right bounded context.
**Tags:** coupling, bounded-context, distributed-monolith, event-driven, architecture, master

---

## Q-ARCH-027 [bloom: analyze] [level: master]
**Question:** You are reviewing the architecture of a system that uses CQRS with event sourcing, Kafka for event streaming, and microservices. The system has 3 correctness bugs: (1) events sometimes arrive out of order, (2) a projection sees the same event twice, (3) a command is executed twice for one user request (network retry). For each, diagnose the root cause and prescribe the fix.
**Model answer:**

**Bug 1: Events arrive out of order**

Root cause: Events for the same aggregate are landing in different Kafka partitions. Kafka only guarantees ordering within a partition.

Fix: **Partition by aggregate ID.** When publishing `OrderPlaced`, `OrderShipped`, etc., use `orderId` as the Kafka message key. Kafka hashes the key to a partition — all events for the same order land in the same partition, in sequence.

Caveat: if you need ordering across aggregates (e.g., all events for a customer across their orders), you need to partition by `customerId` — but then you lose per-order ordering unless each order is a separate topic. This is a fundamental Kafka limitation; design your event consumers to be order-tolerant where possible, or use sequence numbers to detect and buffer out-of-order events.

**Bug 2: Projection sees the same event twice**

Root cause: Kafka's at-least-once delivery semantics. If a consumer crashes after processing an event but before committing the offset, it replays the event on restart.

Fix: **Idempotent event handlers.** Each event has a unique ID (`event.id: UUID`). The projection handler tracks processed event IDs:

```kotlin
@KafkaListener
@Transactional
fun handle(event: OrderPlacedEvent) {
    if (processedEventsRepository.exists(event.id)) return  // deduplicate
    // apply projection update
    orderProjection.apply(event)
    processedEventsRepository.markProcessed(event.id)
}
```
Both the projection update and the `markProcessed` must be in the same transaction. Alternatively: use an idempotent operation where double-applying has no effect (upsert instead of insert).

Fix 2 (infrastructure): Kafka's exactly-once semantics (EOS) with transactions — `enable.idempotence=true` + `transactional.id` on the producer, and `isolation.level=read_committed` on the consumer. This is heavier and has throughput implications but provides exactly-once at the Kafka level.

**Bug 3: Command executed twice for one user request**

Root cause: Network retry (from client, load balancer, or API gateway) sends the same HTTP request twice. The second reaches a different instance or arrives after the first completed.

Fix: **Idempotent commands.** Client generates a unique `idempotencyKey` (UUID) per operation. Server stores `(idempotencyKey → result)` in a fast store (Redis with TTL or DB table):

```kotlin
@PostMapping("/orders/{orderId}/place")
fun placeOrder(@RequestHeader("Idempotency-Key") key: String, @PathVariable orderId: UUID): ResponseEntity<OrderDto> {
    val cached = idempotencyStore.get(key)
    if (cached != null) return ResponseEntity.ok(cached)  // return previous result

    val result = placeOrderHandler.handle(PlaceOrderCommand(orderId))
    idempotencyStore.store(key, result, ttl = 24.hours)
    return ResponseEntity.ok(result)
}
```

The first execution runs and stores the result. The retry finds the cached result and returns it without re-executing. This is safe because the same `idempotencyKey` from the same client represents the same intent.

**Interview trap:** "What is the difference between idempotency and exactly-once?" — Idempotency = the operation can be applied multiple times with the same result. Exactly-once = the operation is applied exactly once (infrastructure guarantee). Idempotency is achievable in application code; exactly-once requires infrastructure support (Kafka EOS, distributed transactions). In practice, idempotent design + at-least-once delivery achieves the same business outcome as exactly-once at lower complexity.
**Tags:** cqrs, event-sourcing, kafka, idempotency, distributed-systems, master

---

## Q-ARCH-028 [bloom: analyze] [level: master]
**Question:** Conway's Law, the Inverse Conway Maneuver, and Team Topologies — how do they connect, and how would you apply them when designing the team and architecture for a growing e-commerce platform going from 15 to 60 engineers?
**Model answer:** **The chain of reasoning:**

Conway's Law (1968): software architecture mirrors communication structure. If 3 teams share a service, the service has 3 de facto owners, releases require coordination, and the architecture erodes toward the team structure anyway.

Inverse Conway Maneuver (Thoughtworks, ~2015): deliberately design your team structure to produce the architecture you want. Want loosely coupled microservices by capability? Form teams aligned to capabilities (Order, Promotions, Catalog, Recommendations), not technical layers (Frontend team, Backend team, DBA team).

Team Topologies (Skelton & Pais, 2019): provides the vocabulary and patterns for team design:
- **Stream-aligned team:** owns a flow of work (a product capability, a customer journey). Responsible end-to-end: code, deploy, operate, on-call. E.g., Order & Checkout team.
- **Enabling team:** helps stream-aligned teams acquire missing capabilities (e.g., a platform observability team that teaches other teams how to instrument their services). Not permanent; dissolves when capability is transferred.
- **Complicated subsystem team:** owns a deep technical component requiring specialist knowledge (e.g., Search/Ranking engine with ML engineers, or a payments team dealing with PCI DSS). Minimized — most teams should be stream-aligned.
- **Platform team:** provides self-service infrastructure (developer portal, CI/CD, service mesh, observability stack) so stream-aligned teams don't manage their own Kubernetes. Reduces cognitive load.

**Applied to 60-engineer e-commerce platform:**

```
Stream-aligned teams (35 engineers, 5-7 per team):
  - Order & Checkout          (owns ordering, cart, payment integration)
  - Catalog & Search          (owns product data, search, SEO)
  - Promotions & Campaigns    (owns discounts, coupons, campaign lifecycle)
  - Recommendations           (owns personalization, ML pipeline)
  - Seller & Inventory        (owns seller tooling, stock management)

Complicated subsystem team (5 engineers):
  - ML & Ranking              (core ranking models, feature store)

Platform team (8 engineers):
  - Internal developer platform: Kubernetes, CI/CD, observability (Grafana/Tempo/Loki stack), service template generator

Enabling teams (4 engineers, rotating):
  - Security guild: helps teams implement auth, secrets management, SAST
  - Data engineering: helps teams design their event schemas, set up Kafka consumers
```

**Architecture consequence:** each stream-aligned team owns their bounded context — separate service, separate DB, separate Kafka topics. Interactions are APIs and events. The platform team ensures every team can deploy independently without needing the platform team.

**Cognitive load principle:** each team must fit the entire system they own in their heads. If a team's service is too large ("we can't understand our own codebase"), split the team and the service simultaneously — Conway's Law in reverse.

**Interview trap:** "What if the business org chart doesn't match this structure?" — Conway's Law wins long-term. If org and architecture are misaligned, budget for the cost of coordination (more meetings, slower deploys) or make the case to leadership for restructuring. Architectural diagrams alone won't fix it.
**Tags:** conways-law, team-topologies, organization, architecture, master

---

## Q-ARCH-029 [bloom: apply] [level: senior]
**Question:** Describe the Saga pattern for managing distributed transactions across microservices. Compare choreography-based and orchestration-based sagas, and explain when each fails.
**Model answer:** A Saga is a sequence of local transactions, each within a single service, linked together to implement a business transaction that spans multiple services. Because distributed transactions (2PC) are impractical in microservices (lock contention, availability impact), sagas use compensating transactions to roll back on failure.

**Choreography-based Saga:**
Each service listens for events and decides independently what to do next. No central coordinator.

```
OrderService       → emits OrderPlaced
PaymentService     → hears OrderPlaced → charges card → emits PaymentAuthorized
InventoryService   → hears PaymentAuthorized → reserves stock → emits StockReserved
FulfillmentService → hears StockReserved → creates shipment

On failure:
InventoryService   → stock unavailable → emits StockReservationFailed
PaymentService     → hears StockReservationFailed → issues refund → emits PaymentRefunded
OrderService       → hears PaymentRefunded → cancels order → emits OrderCancelled
```

`+` Simple, no central point of failure. `-` Hard to follow the flow — logic scattered across services. `-` Cyclic event dependencies are possible. `-` Difficult to audit: "what is the current state of this saga?" requires querying all participating services.

**Orchestration-based Saga:**
A central Saga Orchestrator (its own service or a state machine within the originating service) directs each step and handles compensation explicitly.

```kotlin
class OrderSagaOrchestrator {
    fun execute(orderId: OrderId) {
        try {
            paymentClient.charge(orderId)          // step 1
            inventoryClient.reserve(orderId)        // step 2
            fulfillmentClient.createShipment(orderId) // step 3
        } catch (e: InventoryUnavailableException) {
            paymentClient.refund(orderId)           // compensate step 1
            orderRepository.cancel(orderId)
        }
    }
}
```

`+` Centralized flow — easy to trace, audit, and reason about. `+` Compensating transactions are explicit and in one place. `-` Orchestrator becomes a point of coupling; must be highly available. `-` Risk of fat orchestrator containing business logic that should be in services.

**When choreography fails:**
- Complex flows where >4 services participate — event chains become impossible to follow
- When you need "what is the current state of this business process?" — no single place to answer

**When orchestration fails:**
- Orchestrator becomes a monolith-by-proxy — all business rules end up there
- Single point of failure if orchestrator crashes mid-saga (mitigate with durable state: Temporal, Conductor, or a saga-state table in the DB)

**Failure modes both share:**
- A compensating transaction can also fail — you need compensations for compensations, or alerting for human intervention (the "saga stuck" state)
- Sagas are eventually consistent — participants see partial state during execution; design read models and UIs to tolerate this

**Interview trap:** "Is a Saga a distributed transaction?" — No. A distributed transaction (2PC) provides ACID atomicity across services. A Saga provides eventual consistency — intermediate states are visible. If strict ACID is required across service boundaries, the service boundary is probably wrong; pull the data into one service.
**Tags:** saga, distributed-transactions, choreography, orchestration, microservices

---

## Q-ARCH-030 [bloom: apply] [level: senior]
**Question:** Explain the Bulkhead and Circuit Breaker resilience patterns. How do they differ, how do they complement each other, and what does backpressure mean in this context?
**Model answer:** Both patterns prevent cascading failures in distributed systems, but at different levels.

**Circuit Breaker (Michael Nygard / Hystrix / Resilience4j):**
Wraps a remote call. Monitors failure rate. When failures exceed a threshold, the circuit "opens" and subsequent calls fail fast (no network call) for a cooldown period. After cooldown, a probe request goes through ("half-open"); if it succeeds, the circuit closes again.

```kotlin
val circuitBreaker = CircuitBreaker.ofDefaults("paymentService")
// Configuration:
// failureRateThreshold = 50%   → open if >50% of calls fail in sliding window
// waitDurationInOpenState = 60s → wait before trying again
// slowCallRateThreshold = 100% at 2000ms → slow calls count as failures

val result = circuitBreaker.executeSupplier {
    paymentClient.charge(orderId)  // wrapped call
}
```

States: `CLOSED` (normal) → `OPEN` (failing fast) → `HALF_OPEN` (probing) → `CLOSED`.

**Purpose:** stop hammering a failing downstream service; give it time to recover. Fail fast instead of holding threads waiting for timeouts.

**Bulkhead:**
Isolates resources (thread pools, connection pools, semaphores) per downstream dependency. If Service A calls both Payment and Inventory, a separate thread pool is allocated to each. If Payment gets slow and exhausts its thread pool, Inventory calls are unaffected — the bulkhead contains the blast radius.

```kotlin
// Resilience4j ThreadPoolBulkhead
val bulkhead = ThreadPoolBulkhead.of("payment", ThreadPoolBulkheadConfig.custom()
    .maxThreadPoolSize(10)
    .coreThreadPoolSize(5)
    .queueCapacity(20)   // reject (fail fast) if queue full
    .build())

bulkhead.executeCallable { paymentClient.charge(orderId) }
```

**Purpose:** prevent resource exhaustion in one dependency from stealing resources meant for others. Named after the watertight compartments in a ship's hull — one flooded compartment doesn't sink the ship.

**How they complement each other:**
- Bulkhead limits CONCURRENT calls to a dependency (resource isolation).
- Circuit Breaker limits calls when a dependency is FAILING (state-based).
- Together: the bulkhead contains how much thread-pool damage a failing service can cause; the circuit breaker then short-circuits once the failure rate is high enough, recovering the thread pool.

**Backpressure:**
When a consumer processes events/requests slower than a producer sends them, the queue grows unboundedly — eventually causing OOM or cascading lag. Backpressure is a mechanism for the consumer to signal "slow down" to the producer.

In reactive systems (Project Reactor, RxJava): a subscriber requests `n` items; the publisher sends at most `n`. If the subscriber is slow, it requests fewer items.

```kotlin
// Reactor example
Flux.range(1, 1_000_000)
    .onBackpressureBuffer(100)    // buffer up to 100; drop or error beyond
    // or .onBackpressureDrop()   // silently drop
    // or .onBackpressureLatest() // keep only the latest
    .subscribe { processSlowly(it) }
```

In Kafka: backpressure is explicit — consumers pull at their own pace; `max.poll.records` and consumer group lag are the signals. If lag grows, the operator adds consumer instances or throttles producers.

**The Bulkhead + Circuit Breaker + Backpressure triad for resilience:**
1. Bulkhead: contain resource exhaustion per dependency.
2. Circuit Breaker: fail fast when a dependency is unhealthy.
3. Backpressure: prevent unbounded queue growth when processing is slower than intake.
4. Timeout: never wait indefinitely — always pair with a deadline.

**Interview trap:** "Can you have a Circuit Breaker per instance of a service, or is it shared?" — In a stateless app with 10 pods, each pod has its own Circuit Breaker state. A single pod can have its circuit open while others are closed. For cluster-wide circuit state, you need a shared store (Redis) — Resilience4j supports this via `CircuitBreakerRegistry` with external state. In practice, per-pod is often fine: if the downstream is failing, all pods will open their breakers quickly.
**Tags:** bulkhead, circuit-breaker, backpressure, resilience, microservices
