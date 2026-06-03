# Domain-Driven Design (DDD) — question bank

> Context: Java/Kotlin developer on an e-commerce platform actively migrating toward DDD. Focus on tactical patterns (entities, aggregates, value objects, domain events, repositories) and strategic patterns (bounded contexts, ubiquitous language). Practical, not theoretical.

## Scope

- Ubiquitous language
- Entity vs Value Object
- Aggregate and Aggregate Root
- Domain Events
- Repository pattern
- Application Service vs Domain Service
- Bounded Context
- Context Map (anti-corruption layer, shared kernel)
- Anemic vs Rich domain model
- Practical DDD in Java/Kotlin with Spring Boot

---

## Q-DDD-001 [bloom: recall]
**Question:** What is the difference between an Entity and a Value Object in DDD?
**Model answer:** **Entity:** has identity that persists through state changes. Two entities with same data but different IDs are different things. `Order(id=42, status=PENDING)` is still the same order as `Order(id=42, status=SHIPPED)` — identity survives mutation. Modeled with mutable state, equality by ID. **Value Object:** defined entirely by its value — no identity, no lifecycle. Two VOs with same data are the same thing. `Money(100, PLN)` == `Money(100, PLN)` always. VOs should be immutable (no setters). Examples: `Money`, `Address`, `EmailAddress`, `DateRange`, `ProductCode`. **Key rule:** if you need to track something over time — Entity. If you only care about what it is, not which one it is — Value Object. **In Java/Kotlin:** VOs implemented as immutable classes with proper `equals/hashCode` (Kotlin `data class` is perfect). Entities have a stable `id` field, `equals` by id only.
**Interview trap:** "Is `Address` always a Value Object?" — Usually yes, but if you're tracking address change history (address has its own lifecycle), it could be an Entity. Context matters.
**Tags:** basics, entity, value-object

## Q-DDD-002 [bloom: recall]
**Question:** What is an Aggregate and an Aggregate Root?
**Model answer:** An **Aggregate** is a cluster of domain objects (entities and value objects) treated as a single unit for data changes. It defines a consistency boundary — all invariants within the aggregate are enforced together, atomically. The **Aggregate Root** is the single entry point to the aggregate — the only object external code holds a reference to. All modifications go through the root; no direct access to internal entities. Example: `Order` aggregate — root is `Order`, internal are `OrderLine` entities and `Money` VOs. You can't add an `OrderLine` directly — you call `order.addItem(product, qty)`, the root enforces the invariant ("order can't have more than 50 items"). **Persistence:** store and load the entire aggregate as a unit. One repository per aggregate root. **Size rule:** keep aggregates small. Big aggregates cause contention (everyone locking the same root). If two entities rarely change together, they're probably separate aggregates.
**Interview trap:** "Can you hold a reference to an internal entity from outside the aggregate?" — No (by DDD rule). Only reference the root. If you need to find an internal entity, go through the root or use its ID as a value object reference.
**Tags:** aggregate, basics

## Q-DDD-003 [bloom: recall]
**Question:** What is Ubiquitous Language and why does it matter?
**Model answer:** Ubiquitous Language is a shared vocabulary between developers and domain experts (product managers, business analysts), used consistently in code, documentation, conversations, and tests. Not "user" and "account" in code but the business says "customer" and "subscription" — the language leaks. Ubiquitous Language means: if the business calls it an "offer promotion" — the code has `OfferPromotion`, the DB table is `offer_promotion`, the API endpoint is `/offer-promotions`. No translation layer in your head. **Why it matters:** 1) Reduces miscommunication — developers and PMs discuss the same `Order` with the same rules. 2) Domain model stays aligned with business reality. 3) Code becomes self-documenting. 4) Makes refactoring explicit — when the business renames a concept, it propagates consistently. **In practice:** hold Event Storming or domain workshops. If developers catch themselves translating from code terms to business terms in meetings — the language is broken.
**Interview trap:** "Who defines the Ubiquitous Language?" — Both developers and domain experts together. It's not the business telling devs what to call things, nor devs inventing names. It emerges from collaboration.
**Tags:** strategic, ubiquitous-language

## Q-DDD-004 [bloom: recall]
**Question:** What is a Domain Event? How is it different from a system event or a log entry?
**Model answer:** A **Domain Event** represents something that happened in the domain that domain experts care about. It's immutable (it happened, you can't unhappen it), named in past tense: `OrderPlaced`, `PaymentFailed`, `PromotionActivated`. It carries the data that was true at the time it occurred. **Differences:** System event (e.g., `DatabaseRowUpdated`) — technical, no business meaning. Log entry — observability artifact, not a business event. Domain event — business-meaningful, can trigger reactions in the same or other bounded contexts. **Uses:** 1) Decoupling — when `Order` is placed, `RecommendationService` listens to `OrderPlaced` to update user history, `NotificationService` sends email. `Order` aggregate knows nothing about these. 2) Audit log — domain events are a natural audit trail. 3) Event sourcing — store events as the source of truth, derive state. **In code:** usually a simple immutable data class: `data class OrderPlaced(val orderId: UUID, val customerId: UUID, val occurredOn: Instant)`.
**Interview trap:** "Should domain events include the full aggregate state?" — Generally no — include only what's necessary for consumers. Fat events (full state) couple consumers to your internal model. Consumers can query for more if needed.
**Tags:** domain-events, basics

## Q-DDD-005 [bloom: recall]
**Question:** What is a Bounded Context?
**Model answer:** A Bounded Context is an explicit boundary within which a domain model applies. The same word can mean different things in different contexts, and that's OK — each context has its own model. Classic example: "Customer" in `Sales` context = potential buyer with contact info and contract history. "Customer" in `Shipping` context = delivery address and package recipient. They're different models even though they refer to the same real-world person. Within a bounded context: one team, one codebase, one database, one Ubiquitous Language. Between bounded contexts: explicit translation via Anti-Corruption Layer or shared contracts. In microservices: bounded contexts often map 1-to-1 with services. Don't force one model to serve all contexts — it becomes an anemic, over-generalized mess.
**Interview trap:** "Can two services share a bounded context?" — Yes, if they're part of the same domain with the same model. But usually each service is its own context. The boundary is conceptual first, then physical.
**Tags:** strategic, bounded-context

## Q-DDD-006 [bloom: understand]
**Question:** What is the difference between an Application Service and a Domain Service?
**Model answer:** **Application Service:** orchestrates use case execution. It sits between the infrastructure and the domain. Responsibilities: load aggregates from repositories, call domain methods, persist results, publish events, handle transactions. It's thin — no business logic inside. Example: `PlaceOrderApplicationService.placeOrder(customerId, items)` — loads `Customer`, loads `Inventory`, calls `Order.place(...)`, saves to `OrderRepository`. **Domain Service:** contains business logic that doesn't naturally belong to any single entity or value object. Named after a domain concept. Stateless. Example: `PriceCalculationService.calculateFinalPrice(product, customer, promotions)` — the logic involves multiple aggregates, so it's not on any one of them. **Rule of thumb:** if the logic involves only one aggregate — it belongs in the aggregate. If it involves infrastructure (DB, HTTP) — Application Service. If it's pure domain logic involving multiple aggregates — Domain Service. **What NOT to do:** fat Application Services with domain logic (anemic domain model). Business rules belong in the domain layer, not in service classes.
**Interview trap:** "Can Application Services call other Application Services?" — Generally no. That's a sign of missing a domain service or an orchestration problem. In microservices it leads to distributed transaction nightmares.
**Tags:** architecture, services, ddd

## Q-DDD-007 [bloom: understand]
**Question:** What is an anemic domain model and why is it considered an anti-pattern?
**Model answer:** An anemic domain model is where domain objects (entities) are just data containers — public getters and setters, no behavior. All the business logic lives in service classes that operate on these passive objects. Example anti-pattern:
```java
// Anemic
class Order { 
    private String status;
    public String getStatus() { ... }
    public void setStatus(String s) { status = s; }
}
// Logic leaks into service
class OrderService {
    void ship(Order o) {
        if (o.getStatus().equals("PAID")) o.setStatus("SHIPPED");
    }
}
```
**Why anti-pattern:** 1) Business logic is scattered across service classes, not encapsulated. 2) Invariants are impossible to enforce — anyone can call `setStatus("SHIPPED")` skipping validation. 3) No Ubiquitous Language in the model. 4) Tests must test services, not the model. **Rich model alternative:** `order.ship()` — the entity enforces the rule, throws `IllegalStateException` if not in valid state. Logic lives where the data lives. **Reality check:** anemic models are extremely common in Java enterprise — it's what happens when you copy Java Bean conventions into domain objects. DDD requires a deliberate shift.
**Interview trap:** "Is an anemic model always wrong?" — Not always. For simple CRUD apps with no complex invariants, rich models add ceremony for no gain. DDD tactical patterns pay off in complex domains with non-trivial business rules.
**Tags:** anti-pattern, anemic-model, ddd

## Q-DDD-008 [bloom: apply]
**Question:** Model a simplified `Promotion` aggregate for an e-commerce platform. A promotion has a code, a discount (percentage or fixed), a validity period, a maximum usage count, and a current usage count. What are the invariants and how would you enforce them?
**Model answer:**
```kotlin
data class DiscountAmount(val type: DiscountType, val value: BigDecimal) // Value Object
enum class DiscountType { PERCENTAGE, FIXED }

class Promotion private constructor(
    val id: UUID,
    val code: String,
    val discount: DiscountAmount,
    val validFrom: Instant,
    val validUntil: Instant,
    val maxUsages: Int,
    private var usageCount: Int = 0
) {
    companion object {
        fun create(code: String, discount: DiscountAmount, from: Instant, until: Instant, maxUsages: Int): Promotion {
            require(code.isNotBlank()) { "Promotion code must not be blank" }
            require(until.isAfter(from)) { "validUntil must be after validFrom" }
            require(maxUsages > 0) { "maxUsages must be positive" }
            require(discount.value > BigDecimal.ZERO) { "Discount value must be positive" }
            if (discount.type == DiscountType.PERCENTAGE) {
                require(discount.value <= BigDecimal(100)) { "Percentage discount cannot exceed 100%" }
            }
            return Promotion(UUID.randomUUID(), code, discount, from, until, maxUsages)
        }
    }

    fun apply(at: Instant): BigDecimal {
        check(at in validFrom..validUntil) { "Promotion is not valid at $at" }
        check(usageCount < maxUsages) { "Promotion has reached max usages" }
        usageCount++
        return discount.value
    }

    fun isActive(at: Instant) = at in validFrom..validUntil && usageCount < maxUsages
}
```
**Invariants enforced:** 1) `validUntil > validFrom` — validated at creation, not mutable. 2) `usageCount < maxUsages` before applying — enforced in `apply()`. 3) Time validity — checked in `apply()`. 4) Discount value positive, percentage ≤ 100. Private constructor + factory method pattern prevents creating invalid aggregates. No setters — state changes only through business methods.
**Interview trap:** "What's the problem with `usageCount` in a distributed system?" — Concurrent requests can both pass the check before either increments. Fix: optimistic locking (DB version column) or a dedicated usage tracking service with atomic increments.
**Tags:** aggregate, ecommerce, design

## Q-DDD-009 [bloom: apply]
**Question:** Your team is debating where to put the logic for "calculate the effective price of a product given the customer tier, active promotions, and bulk discount rules." Where does it go in DDD? Write the signature and justify.
**Model answer:** This logic involves multiple aggregates (`Product`, `Customer`, potentially `Promotion`) and no single aggregate owns all of it. It's pure domain logic — no infrastructure calls. → **Domain Service.**
```kotlin
interface PriceCalculationService {
    fun calculateEffectivePrice(
        product: Product,
        customer: Customer,
        activePromotions: List<Promotion>,
        quantity: Int
    ): Money
}

class DefaultPriceCalculationService : PriceCalculationService {
    override fun calculateEffectivePrice(
        product: Product, customer: Customer,
        activePromotions: List<Promotion>, quantity: Int
    ): Money {
        var price = product.basePrice
        // Tier discount
        price = customer.tier.applyDiscount(price)
        // Bulk discount
        if (quantity >= 10) price = price.multiply(BigDecimal("0.95"))
        // Promotions (best applicable)
        val bestPromotion = activePromotions
            .filter { it.isActive(Instant.now()) }
            .maxByOrNull { it.discount.value }
        bestPromotion?.let { price = price.subtract(it.discount.value) }
        return Money(price.max(BigDecimal.ZERO), product.currency)
    }
}
```
**Justification:** Not on `Product` (doesn't know about customer tiers or promotions). Not on `Customer` (doesn't know about product pricing). Not in Application Service (pure domain logic). Interface in domain layer, implementation in domain or infrastructure — no DB calls here. Application Service loads the aggregates, calls this service, then persists any state changes.
**Interview trap:** "Could you put this on the `Product` aggregate?" — You could add `product.calculatePrice(customer, promotions, qty)` but then `Product` gets dependencies on `Customer` and `Promotion` — coupling that makes the aggregate hard to test and reason about.
**Tags:** domain-service, ecommerce, design

## Q-DDD-010 [bloom: analyze]
**Question:** Your e-commerce platform has an Order service and a Recommendation service. When an order is placed, the recommendation engine needs to update its model. How do you handle this with DDD and bounded contexts, and what are the trade-offs?
**Model answer:** These are two separate bounded contexts. The `Order` aggregate should not directly call `RecommendationService` — that would create tight coupling between contexts. **Approach: Domain Events + Anti-Corruption Layer.** 1) `Order` aggregate raises `OrderPlaced` domain event (includes `customerId`, `productIds`, `orderedAt`). 2) Application Service (in Order context) publishes this event to a message broker (Kafka topic `order-events`). 3) `RecommendationService` subscribes to the topic. It has its own internal model — translates `OrderPlaced` into its own `PurchaseHistory` concept via Anti-Corruption Layer (ACL). The ACL prevents the Recommendation model from being polluted by Order model concepts. **Trade-offs:** `+` Loose coupling — Order knows nothing about Recommendation. `+` Recommendation can be down without affecting Order placement. `+` Easy to add more consumers (analytics, email). `-` Eventual consistency — recommendation model lags behind by seconds/milliseconds. `-` Message ordering and deduplication must be handled. `-` Schema evolution of events needs versioning strategy. **Alternative (wrong):** Order service calling `RecommendationService.updateHistory()` synchronously — creates coupling, recommendation failure can cascade to Order.
**Interview trap:** "What if the Recommendation service needs data that's not in the OrderPlaced event?" — It can query the Order service read model for enrichment, or the event can carry more data. Fat vs thin events trade-off. Prefer events with just enough data; consumers pull more if needed.
**Tags:** bounded-context, events, ecommerce, architecture
