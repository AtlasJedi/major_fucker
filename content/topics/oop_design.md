# OOP & Design — question bank

> Covers the object-oriented fundamentals and software-design principles that every senior Java/Kotlin backend interview tests. Topics range from definitional recall (four pillars, SOLID) through design-pattern internals (GoF patterns with thread-safety and serialization traps) to architectural judgment calls (anemic vs rich domain, composition vs inheritance, DI vs DIP). The bar is: not just naming the pattern, but knowing when it breaks, what it costs, and what the interview will try to catch you on.

## Scope

- Four pillars of OOP: encapsulation, abstraction, inheritance, polymorphism
- SOLID principles — each with a concrete example and a canonical violation
- equals/hashCode contract: all five properties, hashCode consistency rule, what breaks in HashSet/HashMap
- Immutability: rules for truly immutable class, defensive copies, final caveats, records
- Composition vs inheritance: favor composition, LSP, fragile base class problem
- Interface vs abstract class: default methods (Java 8+), when to choose each
- GoF patterns: Singleton (thread-safe variants + enum), Factory/Abstract Factory, Builder, Strategy, Observer, Decorator, Adapter, Template Method, Proxy
- Dependency Inversion Principle vs Dependency Injection
- Law of Demeter
- Cohesion and coupling
- DRY / KISS / YAGNI
- Anemic domain model vs rich domain model
- Value object vs entity

---

## Q-OOP-001 [bloom: recall] [level: junior]
**Question:** Name the four pillars of OOP and give a one-sentence description of each.
**Model answer:** **Encapsulation** — bundling data and behavior together and hiding internal state; callers interact only through a defined interface (getters/setters, methods). **Abstraction** — exposing only what is necessary; the caller knows *what* an object does, not *how*. **Inheritance** — a subclass inherits state and behavior from its parent, enabling code reuse and establishing an is-a relationship. **Polymorphism** — a single interface (method call, reference type) can refer to many concrete implementations; the correct implementation is resolved at runtime (dynamic dispatch) or compile time (overloading).

Key distinction: **inheritance** is a reuse mechanism at the type level; **polymorphism** is about interchangeable behavior through a common interface. They are related but distinct. You can have polymorphism without class inheritance (interfaces, duck typing).
**Interview trap:** "What is the difference between compile-time and runtime polymorphism?" Compile-time (static dispatch) = method overloading — resolved by the compiler based on argument types. Runtime (dynamic dispatch) = method overriding — resolved by the JVM at runtime based on the actual object type stored in a reference. `((Animal) dog).speak()` calls `Dog.speak()`, not `Animal.speak()`.
**Tags:** oop, pillars, polymorphism, encapsulation, abstraction, inheritance

---

## Q-OOP-002 [bloom: recall] [level: junior]
**Question:** Name the five SOLID principles. For each, give its full name and a one-sentence definition.
**Model answer:**
- **S — Single Responsibility Principle:** a class should have exactly one reason to change — one cohesive responsibility.
- **O — Open/Closed Principle:** software entities should be open for extension but closed for modification.
- **L — Liskov Substitution Principle:** any subtype must be substitutable for its supertype without breaking the program's correctness.
- **I — Interface Segregation Principle:** clients should not be forced to depend on methods they do not use; prefer many small, focused interfaces over one fat interface.
- **D — Dependency Inversion Principle:** high-level modules should not depend on low-level modules; both should depend on abstractions.
**Interview trap:** SOLID are guidelines, not commandments. Applying them dogmatically produces over-engineered abstractions where a simple concrete class would do. The tell is: if you're creating an interface with only one possible implementation and no foreseeable extension, that's SOLID theater. "Refactor when needed, not when clever."
**Tags:** solid, design, oop

---

## Q-OOP-003 [bloom: understand] [level: junior]
**Question:** For each SOLID principle, give a concrete example of a violation and how you would fix it.
**Model answer:**
**S violation:** `OrderService` that validates the order, persists it to a DB, sends a confirmation email, and generates a PDF invoice — four reasons to change. Fix: split into `OrderValidator`, `OrderRepository`, `OrderEmailService`, `InvoiceGenerator`.

**O violation:** A `PaymentProcessor` with a switch statement that checks `payment.getType()` and branches per type — every new payment method requires editing the class. Fix: `PaymentProcessor` depends on `PaymentStrategy` interface; each payment type is its own class. Adding Stripe = new class, no edits to `PaymentProcessor`.

**L violation:** `Square extends Rectangle`. `Rectangle` has independent `setWidth`/`setHeight`. `Square` overrides them both to keep width == height. Code that does `rect.setWidth(5); rect.setHeight(10); assert rect.area() == 50;` passes for `Rectangle` but fails for `Square`. Fix: `Square` and `Rectangle` should not share an inheritance hierarchy if they break each other's behavioral contract. Use a common `Shape` interface instead.

**I violation:** A `Worker` interface with `work()`, `eat()`, and `sleep()`. A `Robot` class is forced to implement `eat()` and `sleep()` as no-ops. Fix: split into `Workable`, `Eatable`, `Sleepable`.

**D violation:** `OrderService` instantiates `new MySQLOrderRepository()` internally — hard-coded concrete dependency. Fix: `OrderService` depends on `OrderRepository` (interface), injected via constructor. The concrete `MySQLOrderRepository` is wired externally (Spring, manual construction).
**Interview trap:** LSP is the most subtle. It is not about compile-time type compatibility — it is about behavioral substitutability. A method can override and still compile while completely breaking the contract. Ask yourself: "can I swap this subclass everywhere the parent is used and have all tests still pass?"
**Tags:** solid, lsp, ocp, srp, dip, isp, design

---

## Q-OOP-004 [bloom: recall] [level: junior]
**Question:** State the full equals/hashCode contract in Java. What are the five properties of equals and what rule must hashCode satisfy?
**Model answer:** The `Object.equals` contract requires:
1. **Reflexive:** `x.equals(x)` must return `true` for any non-null `x`.
2. **Symmetric:** `x.equals(y)` must return the same as `y.equals(x)`.
3. **Transitive:** if `x.equals(y)` and `y.equals(z)`, then `x.equals(z)`.
4. **Consistent:** repeated calls return the same value as long as no fields used in comparison have changed.
5. **Null:** `x.equals(null)` must return `false` (never throw NullPointerException).

**hashCode rule:** if `a.equals(b)` is `true`, then `a.hashCode()` **must** equal `b.hashCode()`. The converse is not required — equal hash codes do not imply equal objects (collisions are allowed). hashCode must also be consistent: the same object returns the same value across calls unless fields used in the hash change.

**What breaks in practice:**
- Implementing `equals` but not `hashCode` — objects that are logically equal land in different buckets in `HashMap`/`HashSet`, so `contains` returns `false` even after `add`.
- Using mutable fields in `hashCode` — after inserting into a `HashSet` and then mutating the key field, the object is "lost" in the set: it was stored under the old hash bucket, but looked up in the new one.
- Breaking symmetry with inheritance: if `Employee.equals(Person)` returns true but `Person.equals(Employee)` returns false, `Set.contains` returns different results depending on which reference you call it on.
**Interview trap:** "Should you use `instanceof` or `getClass()` in equals?" `getClass()` ensures that only objects of the exact same class are equal — subclasses cannot be equal to their parent. `instanceof` allows subclass instances to be equal to the parent (used in value-type patterns). `getClass()` is safer for general purpose; `instanceof` is fine for `final` classes or sealed hierarchies. Java 17+ pattern matching: `if (!(o instanceof Product p)) return false;` replaces the cast safely.
**Tags:** equals, hashcode, contract, collections

---

## Q-OOP-005 [bloom: understand] [level: junior]
**Question:** What is an immutable class in Java? List the rules for making a class truly immutable and explain why `final` fields alone are not sufficient.
**Model answer:** An immutable class is one whose instances cannot be modified after construction. Rules:

1. **Declare the class `final`** — prevents subclasses from overriding methods and adding mutable state. Alternative: private/package constructor + static factory methods.
2. **All fields `private final`** — no re-assignment, no direct access.
3. **No setter methods** — state-changing operations return new instances.
4. **Defensive copies of mutable fields at construction** — if a field is a `List`, `Date`, array, or any mutable type, copy it in the constructor so the caller cannot hold a reference to modify it later.
5. **Return defensive copies or unmodifiable views** from accessors — never return a direct reference to a mutable internal field.

```java
public final class Money {
    private final BigDecimal amount;
    private final Currency currency;
    // BigDecimal and Currency are both immutable — no defensive copy needed here

    public Money(BigDecimal amount, Currency currency) {
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
    }
    public BigDecimal getAmount() { return amount; }
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

**Why `final` fields are not sufficient:**
```java
public final class Tags {
    private final List<String> tags; // field is final — reference cannot change
    public Tags(List<String> tags) { this.tags = tags; } // BUG: no defensive copy
    public List<String> getTags() { return tags; }       // BUG: returns mutable reference
}
// Caller can: list.add("injected"); and the Tags object is now mutated.
```
Fix: `this.tags = List.copyOf(tags);` in constructor and return `Collections.unmodifiableList(tags)` (or just use `List.copyOf` which already gives an unmodifiable copy).

**Java 16+ records** are immutable by design for primitive/immutable fields, but records with mutable field types (`List`, `Date`, arrays) have the same defensive-copy requirement.
**Interview trap:** "`Date` is common in legacy code — is it immutable?" No. `java.util.Date` is mutable. Always replace with `java.time.Instant` / `LocalDate` / `ZonedDateTime` (immutable since Java 8). If you have a `Date` field in an "immutable" class without defensive copies, it is not actually immutable.
**Tags:** immutability, defensive-copy, final, records, design

---

## Q-OOP-006 [bloom: understand] [level: junior]
**Question:** What is the difference between an interface and an abstract class in Java? When would you choose one over the other?
**Model answer:**

| | Interface | Abstract class |
|---|---|---|
| Instantiation | No | No |
| Multiple inheritance | Yes (a class can implement many) | No (single extends) |
| State (fields) | Only `public static final` constants | Any fields, any access modifier |
| Constructor | No | Yes (called by subclass via `super()`) |
| Method implementations | `default` and `static` since Java 8; `private` since Java 9 | Any mix of abstract and concrete |
| Intended relationship | *can-do* / *behaves-as* | *is-a* (shared implementation base) |

**Java 8+ `default` methods:** interfaces can carry implementation, closing the gap with abstract classes. This is how `Collection.stream()`, `Iterable.forEach()`, etc. were added retroactively without breaking every existing implementation.

**Choose interface when:**
- You are defining a contract / capability (`Comparable`, `Serializable`, `PaymentGateway`).
- Multiple unrelated classes need to share the contract.
- You want to allow multiple inheritance of type.

**Choose abstract class when:**
- Subclasses share significant state or concrete behavior that would be duplicated without a base class.
- You need constructors with parameters to enforce initialization (interfaces cannot do this).
- You are building a Template Method pattern where the skeleton is in the base class.
**Interview trap:** "Since Java 8 added default methods, are abstract classes obsolete?" No. Abstract classes can hold state (fields), have constructors, and use any access modifier. If you need to enforce that subclasses initialize required fields through a constructor, only an abstract class can do that. Interfaces cannot force constructor parameters.
**Tags:** interface, abstract-class, java8, default-methods, design

---

## Q-OOP-007 [bloom: understand] [level: regular]
**Question:** Explain the difference between composition and inheritance. What is the "fragile base class" problem and why does it push toward favoring composition?
**Model answer:** **Inheritance** (is-a): subclass extends parent, reuses and potentially overrides behavior. Tight coupling — every change to the parent potentially affects every subclass. **Composition** (has-a): a class holds a reference to another object and delegates behavior to it. Looser coupling — the composited object is replaceable, its internals are hidden.

**Fragile base class problem:** when a parent class changes its internal implementation (even if the public API stays the same), subclasses that depend on the implementation details can silently break. Classic example: `InstrumentedHashSet extends HashSet` that counts `add` calls:
```java
public class InstrumentedHashSet<E> extends HashSet<E> {
    private int addCount = 0;
    @Override public boolean add(E e) { addCount++; return super.add(e); }
    @Override public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c); // BUG: HashSet.addAll calls this.add() internally
    }                           // so addCount gets incremented twice per element
}
```
`HashSet.addAll` delegates to `add` internally — an implementation detail not part of the contract. The subclass counted on it not doing that, and it silently double-counts. This is the fragile base class: the parent's private implementation decision broke the subclass.

**Favor composition fix:**
```java
public class InstrumentedSet<E> implements Set<E> {
    private final Set<E> delegate;
    private int addCount = 0;
    public InstrumentedSet(Set<E> delegate) { this.delegate = delegate; }
    @Override public boolean add(E e) { addCount++; return delegate.add(e); }
    @Override public boolean addAll(Collection<? extends E> c) { addCount += c.size(); return delegate.addAll(c); }
    // delegate all other Set methods
}
```
Now `HashSet`'s internal implementation is irrelevant — we never call `super`. The count logic is correct regardless of how `HashSet` implements `addAll`.

**Rule of thumb (Effective Java Item 18):** use inheritance only when a true is-a relationship exists AND the subclass will not need to override behavior that calls other overridable methods (i.e., there is no self-use that can break you).
**Interview trap:** "Isn't composition just more boilerplate?" Yes, it's more code. But it is the right trade-off for maintainability. Lombok `@Delegate` or Kotlin `by` delegation syntax reduce the boilerplate significantly. In Kotlin: `class InstrumentedSet<E>(private val delegate: Set<E>) : Set<E> by delegate { ... }` — the compiler generates all delegation methods automatically.
**Tags:** composition, inheritance, fragile-base-class, lsp, effective-java, design

---

## Q-OOP-008 [bloom: understand] [level: regular]
**Question:** Describe the Singleton pattern. Show at least three Java implementations with different thread-safety and laziness properties, and explain which one to prefer and why.
**Model answer:** Singleton ensures a class has exactly one instance, accessible globally. Four classical implementations:

**1. Eager initialization:**
```java
public final class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig(); // created at class load
    private AppConfig() {}
    public static AppConfig getInstance() { return INSTANCE; }
}
```
Thread-safe (class initialization is done by the JVM under lock). Not lazy — created even if never used.

**2. Synchronized lazy:**
```java
public final class AppConfig {
    private static AppConfig instance;
    private AppConfig() {}
    public static synchronized AppConfig getInstance() {
        if (instance == null) instance = new AppConfig();
        return instance;
    }
}
```
Lazy but acquires the monitor on every call — unnecessary contention after first initialization.

**3. Double-checked locking (DCL):**
```java
public final class AppConfig {
    private static volatile AppConfig instance; // volatile is MANDATORY
    private AppConfig() {}
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) instance = new AppConfig();
            }
        }
        return instance;
    }
}
```
Lazy + fast (only synchronizes on first creation). `volatile` is mandatory — without it, the JVM can reorder the write to `instance` before the constructor finishes, exposing a partially-constructed object to another thread's first null-check.

**4. Initialization-on-demand holder (preferred for plain Java):**
```java
public final class AppConfig {
    private AppConfig() {}
    private static final class Holder {
        static final AppConfig INSTANCE = new AppConfig(); // loaded lazily on first access
    }
    public static AppConfig getInstance() { return Holder.INSTANCE; }
}
```
Lazy (the `Holder` class is loaded only when `getInstance()` is first called), thread-safe (JVM guarantees class initialization under a lock), no `synchronized` overhead on every call. No `volatile` needed.

**5. Enum singleton (best for serialization safety):**
```java
public enum AppConfig {
    INSTANCE;
    public void doSomething() { ... }
}
```
Thread-safe by JVM guarantees. Immune to reflection attacks (cannot call private constructor via reflection on an enum). Serialization-safe — JVM special-cases enum serialization so the same instance is always returned. Downside: cannot extend a class (enums implicitly extend `Enum`), cannot be lazy.

**Modern practice:** prefer Spring `@Component` (default scope = singleton per container). The container manages lifecycle, it is mockable in tests, and there is no hidden global state. Manual singletons are justified only outside of a DI container.
**Interview trap:** "Is double-checked locking safe without volatile?" No — it was broken before Java 5 because the old memory model did not guarantee visibility of writes across threads without a memory barrier. With Java 5+ memory model and `volatile`, DCL is correct. Without `volatile`, another thread's first null-check may see a non-null but partially-initialized object. The holder idiom is cleaner — no volatile, no double-check, just rely on class initialization guarantees.
**Tags:** singleton, patterns, thread-safety, dcl, volatile, serialization

---

## Q-OOP-009 [bloom: understand] [level: regular]
**Question:** Explain the Factory Method and Abstract Factory patterns. How do they differ, and when would you use each?
**Model answer:** Both patterns abstract object creation, but at different scales.

**Factory Method** — defines an interface for creating an object, but lets subclasses decide which class to instantiate. The "factory method" is a method in an abstract class that subclasses override:
```java
public abstract class NotificationSender {
    public void notify(String message) {
        Notification n = createNotification(message); // factory method
        n.send();
    }
    protected abstract Notification createNotification(String message); // override this
}

public class EmailSender extends NotificationSender {
    @Override
    protected Notification createNotification(String message) {
        return new EmailNotification(message);
    }
}
public class SmsSender extends NotificationSender {
    @Override
    protected Notification createNotification(String message) {
        return new SmsNotification(message);
    }
}
```
The parent class controls the *algorithm* (notify), the subclass controls *which object* is created.

**Abstract Factory** — provides an interface for creating *families* of related objects without specifying their concrete classes. Useful when objects must be used together:
```java
public interface UIFactory {
    Button createButton();
    TextField createTextField();
    Dialog createDialog();
}
public class WindowsUIFactory implements UIFactory { ... }
public class MacUIFactory implements UIFactory { ... }
// Client uses UIFactory — never references WindowsButton or MacButton directly.
// Switching OS theme = swap the factory.
```

**Static factory method** (not a GoF pattern but commonly discussed in the same breath): a static method that returns an object, optionally cached, possibly a subtype. `Optional.of(...)`, `List.of(...)`, `LocalDate.of(...)`. Advantages: descriptive naming, can return subtypes, can cache instances. Disadvantage: classes with only static factories cannot be subclassed.

**When to use:**
- Factory Method: you have a base algorithm and want subclasses to plug in the object creation step.
- Abstract Factory: you have product families that must be consistent (UI widgets, cloud provider services).
- Static factory: simple creation with naming clarity, caching, or subtype return.
**Interview trap:** "Isn't Factory Method just a virtual constructor?" Conceptually yes. The key insight is that the *calling code* (in the abstract class or client) does not know the concrete type — only that it satisfies the product interface. This is what enables the Open/Closed principle: adding a new product family = new concrete factory, no changes to existing code.
**Tags:** factory-method, abstract-factory, patterns, design, ocp

---

## Q-OOP-010 [bloom: apply] [level: regular]
**Question:** Describe the Builder pattern. When is it justified over a plain constructor or static factory? Show the structure of a Java Builder for a complex object.
**Model answer:** Builder separates the construction of a complex object from its representation, enabling step-by-step assembly with a fluent API.

**When justified:** when a class has many parameters, several of which are optional, and telescoping constructors become unreadable or error-prone. A constructor with 8 positional parameters is an invitation for argument transposition bugs.

```java
public final class HttpRequest {
    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Duration timeout;
    private final boolean followRedirects;

    private HttpRequest(Builder b) {
        this.method = Objects.requireNonNull(b.method, "method");
        this.uri = Objects.requireNonNull(b.uri, "uri");
        this.headers = Map.copyOf(b.headers);        // defensive copy
        this.body = b.body != null ? b.body.clone() : new byte[0];
        this.timeout = b.timeout != null ? b.timeout : Duration.ofSeconds(30);
        this.followRedirects = b.followRedirects;
    }

    public static Builder builder(String method, URI uri) { return new Builder(method, uri); }

    public static final class Builder {
        private final String method;
        private final URI uri;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body;
        private Duration timeout;
        private boolean followRedirects = true;

        private Builder(String method, URI uri) { this.method = method; this.uri = uri; }

        public Builder header(String name, String value) { headers.put(name, value); return this; }
        public Builder body(byte[] body) { this.body = body.clone(); return this; }
        public Builder timeout(Duration t) { this.timeout = t; return this; }
        public Builder followRedirects(boolean f) { this.followRedirects = f; return this; }

        public HttpRequest build() { return new HttpRequest(this); }
    }
}
// Usage:
HttpRequest req = HttpRequest.builder("POST", uri)
    .header("Content-Type", "application/json")
    .body(payload)
    .timeout(Duration.ofSeconds(10))
    .build();
```

**Key properties:**
- Required parameters enforced in the Builder constructor (not optional setters).
- Defensive copies in both Builder methods and the final constructor.
- Built object is immutable — the Builder is discarded after `build()`.
- Validation can live in `build()` before creating the final object.

**Lombok shortcut:** `@Builder` on the class generates this automatically. Add `@Builder.Default` for default field values. Combine with `@Value` for immutable class.
**Interview trap:** "What if I need to create 1000 objects rapidly — is Builder too slow?" The Builder object itself is cheap (one extra allocation). For truly hot-path construction (tight loops, millions/sec) you can reuse/reset Builders or use static factory methods. But in typical service code, the readability and safety benefits of Builder far outweigh the marginal allocation cost.
**Tags:** builder, patterns, immutability, design, effective-java

---

## Q-OOP-011 [bloom: apply] [level: regular]
**Question:** Describe the Strategy pattern. Show a Java example and explain how Spring DI makes strategy selection elegant.
**Model answer:** Strategy encapsulates interchangeable algorithms behind a common interface, letting the behavior vary at runtime without changing the client.

```java
@FunctionalInterface
public interface DiscountStrategy {
    BigDecimal apply(BigDecimal price, Customer customer);
}

@Component("noDiscount")
public class NoDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal price, Customer c) { return price; }
}
@Component("loyaltyDiscount")
public class LoyaltyDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal price, Customer c) {
        if (c.getLoyaltyYears() >= 3) return price.multiply(BigDecimal.valueOf(0.90));
        return price;
    }
}
@Component("vipDiscount")
public class VipDiscount implements DiscountStrategy {
    public BigDecimal apply(BigDecimal price, Customer c) {
        return price.multiply(BigDecimal.valueOf(0.80));
    }
}
```

**Spring-idiomatic resolver:**
```java
@Service
public class DiscountStrategyResolver {
    private final Map<String, DiscountStrategy> strategies;

    // Spring injects ALL beans implementing DiscountStrategy as a Map<beanName, bean>
    public DiscountStrategyResolver(Map<String, DiscountStrategy> strategies) {
        this.strategies = strategies;
    }

    public DiscountStrategy resolve(Customer customer) {
        if (customer.isVip()) return strategies.get("vipDiscount");
        if (customer.getLoyaltyYears() >= 3) return strategies.get("loyaltyDiscount");
        return strategies.get("noDiscount");
    }
}
```
Adding a new discount type = add a new `@Component`. The resolver and client are unchanged — OCP in action.

**Lambda strategy (for simple stateless cases):**
```java
DiscountStrategy seasonal = (price, c) -> price.multiply(BigDecimal.valueOf(0.85));
```
Use full classes when the strategy needs injected dependencies or has meaningful state; use lambdas for pure, stateless computation.
**Interview trap:** "Strategy and polymorphism sound the same — what's the difference?" Strategy *uses* polymorphism, but the distinction is intent and flexibility. Polymorphism is resolved at compile time (by reference type) or via class hierarchy. Strategy makes the algorithm selection explicit and runtime-configurable — you can swap strategies on the same object, or select one from a registry. It also enforces that all variants share a single-method interface, which maps cleanly to a lambda.
**Tags:** strategy, patterns, spring, di, ocp, design

---

## Q-OOP-012 [bloom: understand] [level: regular]
**Question:** Explain the Observer pattern. What is its intent, what problem does it solve, and what are its production pitfalls?
**Model answer:** Observer defines a one-to-many dependency: when one object (the *subject*/*publisher*) changes state, all its *observers*/*subscribers* are notified automatically. Decouples the publisher from the subscribers — the publisher does not need to know who is listening.

**Classic Java structure:**
```java
public interface OrderEventListener {
    void onOrderCreated(Order order);
}

public class OrderService {
    private final List<OrderEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(OrderEventListener l) { listeners.add(l); }
    public void unsubscribe(OrderEventListener l) { listeners.remove(l); }

    public Order createOrder(OrderRequest req) {
        Order order = buildOrder(req);
        save(order);
        listeners.forEach(l -> l.onOrderCreated(order)); // notify all
        return order;
    }
}
```

**Modern Java equivalents:**
- Spring `ApplicationEventPublisher` / `@EventListener` — Spring-managed observer wired through the DI container, supports async (`@Async`).
- `java.util.concurrent.Flow` / `SubmissionPublisher` — reactive streams (Java 9+), with backpressure.
- Message brokers (Kafka, RabbitMQ) — distributed Observer; decoupled across services, persistent, replayable.

**Production pitfalls:**
1. **Synchronous notification in transaction** — if the publisher fires observers inside a DB transaction, a slow or failing observer rolls back the transaction. Use Spring's `@TransactionalEventListener` with `phase = AFTER_COMMIT` to fire only on successful commit.
2. **Exception in one observer stops all** — iterate defensively: wrap each call in try-catch, log, continue.
3. **Memory leak** — observers registered but never unsubscribed hold the publisher alive. Use `WeakReference` or explicit lifecycle management.
4. **Ordering** — observer call order is often undefined. If observers have implicit ordering dependencies, you have a hidden coupling problem.
5. **Circular updates** — observer modifies the subject, triggering another event, infinite loop.
**Interview trap:** "Is Observer the same as Pub/Sub?" Similar intent, different mechanics. Observer is typically synchronous, in-process, and the subject knows its observers. Pub/Sub introduces a *message broker* (bus, topic) — publisher and subscriber are fully decoupled, do not know each other, and communication can be async and distributed. Spring's event system sits in between: it's in-process but goes through an event bus (ApplicationEventPublisher), which allows async dispatch.
**Tags:** observer, patterns, events, spring, transactional-event, design

---

## Q-OOP-013 [bloom: understand] [level: regular]
**Question:** Explain the Decorator pattern. How does it differ from inheritance for adding behavior, and give a concrete Java example.
**Model answer:** Decorator attaches new responsibilities to an object dynamically by wrapping it — both the wrapper and the wrapped object implement the same interface, so they are interchangeable from the caller's perspective. Behavior is composed at runtime, not baked in at compile time through a class hierarchy.

```java
public interface TextProcessor {
    String process(String text);
}
public class PlainProcessor implements TextProcessor {
    public String process(String text) { return text; }
}
public class TrimDecorator implements TextProcessor {
    private final TextProcessor delegate;
    public TrimDecorator(TextProcessor d) { this.delegate = d; }
    public String process(String text) { return delegate.process(text.trim()); }
}
public class UpperCaseDecorator implements TextProcessor {
    private final TextProcessor delegate;
    public UpperCaseDecorator(TextProcessor d) { this.delegate = d; }
    public String process(String text) { return delegate.process(text).toUpperCase(); }
}
// Compose at runtime:
TextProcessor p = new UpperCaseDecorator(new TrimDecorator(new PlainProcessor()));
p.process("  hello world  "); // → "HELLO WORLD"
```

**Java standard library examples:** `BufferedInputStream(new FileInputStream(path))`, `GZIPOutputStream(new FileOutputStream(path))` — classic Decorator chains.

**Decorator vs inheritance for adding behavior:**
- Inheritance adds behavior at compile time, locked into the class hierarchy. N features → 2^N subclasses.
- Decorator adds behavior at runtime, composable — any combination without class explosion.
- Decorator favors OCP and SRP: each decorator does one thing.

**Decorator vs Proxy:** structurally identical (both wrap the same interface). Intent differs: Decorator *adds* behavior; Proxy *controls access* (lazy init, authorization, logging, remote call). In practice, Spring AOP `@Transactional` and `@Cacheable` are Proxy-based.
**Interview trap:** "Are Java I/O streams an example of Decorator?" Yes — they are the canonical Java example. But the traditional Java I/O API predates generics and has usability issues (verbose nesting, confusing which wrapper to use). Java NIO and modern libraries use cleaner APIs, but the Decorator structure is still present.
**Tags:** decorator, patterns, composition, design, oop

---

## Q-OOP-014 [bloom: understand] [level: regular]
**Question:** Describe the Adapter pattern. When would you use it in a backend service, and how does it differ from a Facade?
**Model answer:** Adapter converts the interface of a class into another interface that the client expects — it makes incompatible interfaces work together without changing either side.

```java
// Target interface — what our system expects
public interface PaymentGateway {
    PaymentResult charge(String customerId, Money amount);
}

// Adaptee — an external SDK with a different interface
public class StripeClient {
    public StripeCharge createCharge(String stripeCustomerId, long amountCents, String currency) { ... }
}

// Adapter — wraps StripeClient, exposes PaymentGateway
@Component
public class StripePaymentAdapter implements PaymentGateway {
    private final StripeClient stripe;
    public StripePaymentAdapter(StripeClient stripe) { this.stripe = stripe; }

    @Override
    public PaymentResult charge(String customerId, Money amount) {
        long cents = amount.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        StripeCharge charge = stripe.createCharge(customerId, cents, amount.getCurrency().getCode());
        return new PaymentResult(charge.getId(), charge.getStatus());
    }
}
```

**Use cases:** integrating third-party libraries/SDKs into your domain model; wrapping legacy code behind a modern interface; switching between providers without changing the rest of the codebase.

**Adapter vs Facade:**
- **Adapter** makes an *existing* interface compatible with another expected interface — one-to-one wrapping, translation.
- **Facade** provides a *simplified* interface over a *complex subsystem* — it may wrap many classes behind one simple entry point and may not map to any specific existing interface. Facade reduces complexity; Adapter resolves incompatibility.
**Interview trap:** "How is Adapter different from Proxy?" Both wrap another object. Proxy controls or augments access to the *same interface* the real object implements (the client doesn't know it's talking to a proxy). Adapter presents a *different interface* — it bridges two incompatible APIs. Client code knows (by type) it's using `PaymentGateway`, not `StripeClient`.
**Tags:** adapter, facade, patterns, design, integration

---

## Q-OOP-015 [bloom: apply] [level: senior]
**Question:** Explain the Proxy pattern and its three main use cases in Java backend development. How does Spring implement it, and what are the gotchas?
**Model answer:** Proxy provides a surrogate for another object, controlling access to it. The proxy and the real object implement the same interface; clients cannot tell the difference.

**Three main use cases:**

1. **Virtual (lazy) proxy:** defers expensive initialization until first use. Hibernate lazy-loaded collections (`@OneToMany(fetch = LAZY)`) return a proxy object; the SQL fires only when you access `.getItems()`.

2. **Protection proxy:** controls access based on permissions — checks authorization before delegating to the real object.

3. **Remote proxy:** represents an object in a different address space. gRPC stub, RMI stub.

**Spring AOP Proxy (the one that matters in interviews):** Spring's `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize` all work through dynamic proxies. Spring wraps the bean with either:
- **JDK dynamic proxy** (default when the bean implements an interface) — uses `java.lang.reflect.Proxy`, works only for interface method calls.
- **CGLIB proxy** (when the bean does not implement an interface, or `proxyTargetClass = true`) — generates a subclass at runtime, overrides methods to add the cross-cutting behavior.

**The self-invocation gotcha:**
```java
@Service
public class OrderService {
    @Transactional
    public void createOrder(Order o) { save(o); }

    public void createBatch(List<Order> orders) {
        orders.forEach(this::createOrder); // DOES NOT START A TRANSACTION per order
        // "this" refers to the real object, not the proxy — @Transactional is bypassed
    }
}
```
`createBatch` calls `createOrder` on `this` (the raw object), not through the proxy. The advice never executes. Fix: inject self (`@Autowired OrderService self`) or restructure to keep transactional methods called from outside the class.

**CGLIB and `final` methods:** CGLIB proxies work by subclassing. A `final` method or a `final` class cannot be proxied by CGLIB — Spring will either fall back to JDK proxy or throw a startup error. Never mark Spring beans' advised methods `final`.
**Interview trap:** "Does `@Transactional` on a private method work?" No. JDK proxy can only intercept calls to public interface methods. CGLIB proxy can only override non-final, non-private methods. Private methods are never overridden by a subclass — the annotation is silently ignored. Spring 6 / AspectJ compile-time weaving can advise private methods, but that requires a different setup.
**Tags:** proxy, spring, aop, transactional, cglib, jdk-proxy, patterns

---

## Q-OOP-016 [bloom: apply] [level: senior]
**Question:** What is the Template Method pattern? How is it different from the Strategy pattern, and when does each fit better?
**Model answer:** Template Method defines the *skeleton* of an algorithm in a base class and delegates specific steps to subclasses via abstract (or hook) methods. The structure is fixed; only the varying parts are overridden.

```java
public abstract class ReportGenerator {
    // Template method — final to prevent overriding the skeleton
    public final Report generate(ReportRequest req) {
        List<Row> data = fetchData(req);          // abstract step
        List<Row> filtered = filter(data, req);   // hook with default
        String content = format(filtered);        // abstract step
        return new Report(req.getTitle(), content);
    }

    protected abstract List<Row> fetchData(ReportRequest req);
    protected abstract String format(List<Row> rows);

    // Hook — subclasses may override, base has a sensible default
    protected List<Row> filter(List<Row> rows, ReportRequest req) {
        return rows; // default: no filtering
    }
}

public class CsvReportGenerator extends ReportGenerator {
    protected List<Row> fetchData(ReportRequest req) { return db.query(req.getSql()); }
    protected String format(List<Row> rows) { return CsvFormatter.format(rows); }
}
```

**Template Method vs Strategy:**
| | Template Method | Strategy |
|---|---|---|
| Mechanism | Inheritance — subclass fills in steps | Composition — algorithm injected as object |
| Algorithm control | Base class owns the skeleton | Client (or factory) selects the algorithm |
| Runtime flexibility | Fixed at instantiation (by class) | Swappable at any time |
| Coupling | Tight (subclass to parent) | Loose (through interface) |
| Java style | Classic OOP, less favored in modern code | Preferred — testable, composable |

**Modern preference:** Strategy via composition is preferred over Template Method via inheritance for new code — it avoids the fragile base class problem and is easier to unit-test (inject mock strategies). Template Method still appears in frameworks designed before Java 8 (e.g., Spring's `JdbcTemplate`, `RestTemplate`, `AbstractMessageConverterMethodArgumentResolver`) where the "template" structure was already established.
**Interview trap:** "`JdbcTemplate` is called a Template — is it the Template Method pattern?" Partially. The *name* "template" refers to the template-method style of `execute(ConnectionCallback)`, but the modern approach uses `RowMapper` (a Strategy) injected into the template. It is actually a hybrid: the query skeleton is in `JdbcTemplate` (Template Method), and the result extraction is plugged in via `RowMapper` (Strategy).
**Tags:** template-method, strategy, patterns, inheritance, design, spring

---

## Q-OOP-017 [bloom: apply] [level: senior]
**Question:** Explain the Dependency Inversion Principle. How does it differ from Dependency Injection, and why does the distinction matter?
**Model answer:** **Dependency Inversion Principle (DIP)** — one of the SOLID principles. States:
1. High-level modules must not depend on low-level modules. Both should depend on abstractions.
2. Abstractions should not depend on details. Details should depend on abstractions.

**Example of violation:**
```java
public class OrderService {           // high-level
    private MySQLOrderRepository repo; // depends on concrete low-level class
    public OrderService() {
        this.repo = new MySQLOrderRepository(); // hardcoded
    }
}
```
`OrderService` is coupled to MySQL. Switching to PostgreSQL or mocking in tests requires changing `OrderService`.

**DIP-compliant design:**
```java
public interface OrderRepository { Order findById(long id); }     // abstraction
public class OrderService {                                         // high-level
    private final OrderRepository repo;                            // depends on abstraction
    public OrderService(OrderRepository repo) { this.repo = repo; }
}
public class MySQLOrderRepository implements OrderRepository { ... } // detail depends on abstraction
```

**Dependency Injection (DI)** is a *mechanism* — a way to provide (inject) an object's dependencies from outside, rather than having the object construct them itself. DI is a technique for *implementing* DIP, but they are not the same:
- DIP is a *design principle* — tells you to depend on abstractions.
- DI is a *wiring mechanism* — tells you how to get the concrete implementation into the object at runtime.

You can satisfy DIP without DI (e.g., using a service locator or factory). You can use DI without satisfying DIP (injecting concrete classes, not interfaces). For DIP to be achieved, the injected dependency should be an interface or abstract type.

**Why it matters:** DIP enables testability (swap in mock), replaceability (swap in a different implementation), and architectural layering (domain layer does not know about infrastructure). DI containers (Spring) are the standard Java mechanism, but the *principle* is the reason the container is useful.
**Interview trap:** "Is `@Autowired private OrderRepository repo;` field injection or constructor injection, and which is preferred?" Field injection is convenient but hides dependencies, makes the class harder to instantiate outside the container, and cannot enforce immutability (`final` fields). Constructor injection is explicit, works without a container, supports `final` fields, and makes missing dependencies fail at startup. Modern Spring guidance: prefer constructor injection. Lombok `@RequiredArgsConstructor` generates it with zero boilerplate.
**Tags:** dip, di, solid, spring, design, testability

---

## Q-OOP-018 [bloom: analyze] [level: senior]
**Question:** What is the Law of Demeter? Give a concrete example of a violation in a Java service method, explain why it is harmful, and show the refactored version.
**Model answer:** The Law of Demeter (LoD), also called the "principle of least knowledge": a method should only call methods on:
1. Its own class (`this`).
2. Objects passed as method parameters.
3. Objects it creates.
4. Direct fields of its own class.

It should NOT call methods on objects returned by another method — "only talk to your immediate friends, not strangers."

**Violation:**
```java
// OrderService reaching through layers it should not know about
public BigDecimal calculateShipping(Order order) {
    String countryCode = order.getCustomer()    // OK — direct field
                              .getAddress()     // call on a "stranger"
                              .getCountry()     // call on a "stranger's stranger"
                              .getCode();       // three levels deep
    return shippingRateTable.get(countryCode);
}
```
This method knows about `Customer`, `Address`, and `Country` — it is coupled to the entire object graph. If `Address` refactors `getCountry()` to `getRegion()`, this breaks. If Customer moves to a different address model, this breaks.

**Refactored:**
```java
// Add a convenience method to Order that encapsulates the traversal
public class Order {
    public String getCustomerCountryCode() {
        return customer.getAddress().getCountry().getCode(); // encapsulated in Order
    }
}
// Service now only calls its immediate friend
public BigDecimal calculateShipping(Order order) {
    return shippingRateTable.get(order.getCustomerCountryCode());
}
```

**Why harmful:** "train wreck" chains (`a.getB().getC().getD()`) create hidden coupling to the entire call chain. Any structural change anywhere in that chain breaks the caller. It also reveals that the caller knows too much about the internal structure of objects it should treat as black boxes.

**Practical nuance:** LoD is a guideline, not an absolute rule. Builder and fluent APIs intentionally chain calls on the same object (`return this`), which is fine — every call in the chain returns `this`, so you're always talking to the same friend. The violation is chaining across *different* object types.
**Interview trap:** "Does LoD conflict with Stream pipelines like `list.stream().filter(...).map(...).collect(...)`?" No. Stream chaining returns a `Stream` — you are always calling methods on the same type. LoD is about traversing object graphs (reaching into foreign objects), not about fluent APIs where each call returns the same abstraction.
**Tags:** law-of-demeter, coupling, design, oop, encapsulation

---

## Q-OOP-019 [bloom: understand] [level: senior]
**Question:** Define cohesion and coupling. What do "high cohesion" and "low coupling" mean in practice, and how do violations manifest in a backend codebase?
**Model answer:** **Cohesion** — the degree to which the elements within a module belong together. A class or package has *high cohesion* when everything in it serves a single, well-defined purpose.

**Coupling** — the degree to which one module depends on another. *Low coupling* means a module can be changed, tested, or deployed with minimal effect on others.

**High cohesion violation (low cohesion = "god class"):**
```java
public class OrderUtils {  // does everything
    public Order parse(String json) { ... }
    public void saveToDb(Order o) { ... }
    public void sendEmail(Order o) { ... }
    public BigDecimal calculateTax(Order o) { ... }
    public byte[] generatePdf(Order o) { ... }
}
```
Five unrelated responsibilities. Every change to the email template requires touching the class that also knows about DB persistence. Hard to test any one thing in isolation.

**High coupling violation:**
```java
@Service
public class OrderService {
    @Autowired private CustomerService customerService;
    @Autowired private InventoryService inventoryService;
    @Autowired private PricingService pricingService;
    @Autowired private ShippingService shippingService;
    @Autowired private NotificationService notificationService;
    @Autowired private AuditService auditService;
    @Autowired private FraudDetectionService fraudService;
    // 7 dependencies — changes in any of these can break OrderService
}
```
Any change in any dependency potentially requires changing, re-testing, re-deploying `OrderService`. High coupling also makes the class impossible to unit-test without mocking all seven dependencies.

**How to improve:**
- Extract single-purpose classes (SRP).
- Introduce intermediate domain events to break direct service-to-service calls (Observer/event bus).
- Aggregate roots in DDD absorb internal cohesion — one service operates on one aggregate.
- Package by feature (all order-related code in `order/`) not by layer (`service/`, `repository/`) — improves package-level cohesion.

**Metric:** if you can delete a class and touch only one or two other places, coupling is low. If you delete a class and fifty places break, coupling is high.
**Interview trap:** "Is low coupling always better?" More abstraction to reduce coupling has its own cost — more indirection, harder to trace code paths, extra interfaces with one implementation. The goal is *appropriate* coupling. Within a bounded context, direct coupling to the context's own repository/service is fine. Coupling across bounded contexts should go through interfaces, events, or ACLs.
**Tags:** cohesion, coupling, design, srp, architecture

---

## Q-OOP-020 [bloom: recall] [level: regular]
**Question:** What do DRY, KISS, and YAGNI stand for? Give a one-sentence definition and one example of violating each.
**Model answer:**

**DRY — Don't Repeat Yourself:** every piece of knowledge should have a single, authoritative representation in the system. Violation: the same discount-calculation logic copy-pasted into three different service methods — when the business rule changes, all three must be updated, and one is inevitably missed.

**KISS — Keep It Simple, Stupid:** prefer the simplest solution that works. Violation: building a generic, pluggable, factory-driven, annotation-configurable persistence framework in-house when Spring Data JPA would have done the job with three lines of code.

**YAGNI — You Aren't Gonna Need It:** do not add functionality until it is necessary. Violation: designing a plugin architecture with hot-reloading, versioning, and sandboxing on day one for a feature that "might someday need to be extensible" — complexity that delivers no current value and must now be maintained.

**Interplay:** DRY fights duplication; KISS fights accidental complexity; YAGNI fights premature extensibility. They are complementary but can conflict. Extracting a shared abstraction to satisfy DRY can violate KISS if the abstraction is more complex than the duplication it removes. The judgment call is: is the duplication likely to diverge (DRY is worth it) or stay identical (duplication might be fine)?
**Interview trap:** "Is copy-paste always a DRY violation?" Not always. The "Rule of Three": the first time you write something, just write it. The second time, note the duplication. The third time, refactor. Also: two pieces of code that look identical but represent *different concepts* (coincidental similarity) should NOT be merged — merging them couples unrelated things. DRY is about knowledge and intent, not textual similarity.
**Tags:** dry, kiss, yagni, design-principles, design

---

## Q-OOP-021 [bloom: analyze] [level: senior]
**Question:** What is an anemic domain model? What is a rich domain model? Which does Spring typically produce and why, and when is each appropriate?
**Model answer:** **Anemic domain model** (Martin Fowler, 2003): domain objects are plain data bags — fields, getters, setters, no business logic. All logic lives in service classes (`OrderService`, `InventoryService`).

```java
// Anemic — pure data, no behavior
public class Order {
    private Long id;
    private OrderStatus status;
    private List<OrderItem> items;
    private BigDecimal total;
    // getters/setters only
}

// All logic externalized to a service
public class OrderService {
    public void confirm(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) throw ...;
        order.setStatus(OrderStatus.CONFIRMED);
        // also recalculate total, validate stock, ...
    }
}
```

**Rich domain model:** domain objects contain both data *and* the business behavior that operates on it. The object is responsible for enforcing its own invariants.

```java
public class Order {
    private OrderStatus status;
    private final List<OrderItem> items = new ArrayList<>();

    public void confirm() {
        if (status != OrderStatus.PENDING)
            throw new IllegalStateException("Can only confirm a PENDING order, current: " + status);
        this.status = OrderStatus.CONFIRMED;
    }

    public void addItem(Product product, int quantity) {
        if (status != OrderStatus.PENDING) throw new IllegalStateException("Order already confirmed");
        items.add(new OrderItem(product, quantity));
    }

    public Money total() {
        return items.stream().map(OrderItem::subtotal).reduce(Money.ZERO, Money::add);
    }
}
```
The invariant "only PENDING orders can be confirmed" is enforced by the object itself — no service can bypass it.

**Which does Spring typically produce?** Anemic. Spring + JPA + `@Service` + `@Repository` naturally pull logic into services — it's easier to inject repositories and other services into service classes than into domain objects. JPA also requires a no-arg constructor and mutable fields, which conflicts with the rich domain style.

**When each is appropriate:**
- **Anemic** is fine for CRUD-heavy, transaction-script style applications where business logic is minimal. It's simpler, easier to debug, and fits the "fat controller / service" Spring idiom.
- **Rich domain model** pays off in complex domains with many invariants, where logic scattered across dozens of services is hard to reason about and test. Pairs with DDD (Domain-Driven Design) aggregate roots. Requires more discipline and often Hexagonal Architecture to keep infrastructure out of the domain.
**Interview trap:** "Is anemic domain model always bad?" No — it's Martin Fowler's anti-pattern label, but Fowler himself says it's a trade-off. For simple CRUD with little behavior, a rich domain would be over-engineering. The anti-pattern is applying anemic style to genuinely complex domains where invariants get lost in scattered services.
**Tags:** anemic-domain, rich-domain, ddd, design, spring, architecture

---

## Q-OOP-022 [bloom: understand] [level: senior]
**Question:** What is the difference between a Value Object and an Entity in domain-driven design? How do you implement a Value Object correctly in Java?
**Model answer:** **Entity** — an object with a continuous *identity* that persists through changes in its attributes. Two entities are the same if they have the same identity, even if all their attributes differ. Example: `Customer` — the same customer remains the same entity whether they change their name, address, or email. Identity is typically a surrogate key (database ID, UUID).

**Value Object (VO)** — an object with no identity; it is defined entirely by its *attributes*. Two VOs with the same attributes are interchangeable. Example: `Money(100, USD)` — any two instances representing $100 USD are equivalent. VOs are immutable; "changing" a VO means replacing it with a new one.

**Value Object in Java:**
```java
public final class Money {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        this.amount = Objects.requireNonNull(amount).setScale(2, RoundingMode.HALF_UP);
        this.currency = Objects.requireNonNull(currency);
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("amount must be non-negative");
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException("currency mismatch");
        return new Money(this.amount.add(other.amount), this.currency);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }
    @Override
    public int hashCode() { return Objects.hash(amount.stripTrailingZeros(), currency); }
    @Override
    public String toString() { return amount + " " + currency; }
}
```

**Key rules for a correct Value Object:**
1. **Immutable** — `final` class, `final` fields, no setters, operations return new instances.
2. **Equality by value** — `equals`/`hashCode` based on all significant fields (not an ID).
3. **Validation in constructor** — a VO must always be in a valid state; enforce constraints at creation.
4. **Self-contained behavior** — relevant operations live on the VO (`add`, `subtract`, `convertTo`).
5. **No identity** — no `id` field.

**Java 16+ record as Value Object:**
```java
public record EmailAddress(String value) {
    public EmailAddress {
        Objects.requireNonNull(value);
        if (!value.matches("^[^@]+@[^@]+\\.[^@]+$"))
            throw new IllegalArgumentException("invalid email: " + value);
    }
}
```
Records automatically generate `equals`, `hashCode`, `toString` based on components, making them a natural fit for Value Objects — provided the component types are themselves immutable.
**Interview trap:** "Can a Value Object be a JPA `@Entity`?" No — they are conceptually opposite. JPA entities have identity (primary key). Value Objects should be mapped as `@Embeddable` (embedded in the entity table) or stored as a separate table with a foreign key but without their own identity in the domain model. Hibernate supports `@Embeddable`/`@Embedded` for this.
**Tags:** value-object, entity, ddd, immutability, equals, hashcode

---

## Q-OOP-023 [bloom: analyze] [level: master]
**Question:** Walk through every way the equals/hashCode contract can be broken in a production Java application and the exact failure mode each causes in HashMap, HashSet, and ConcurrentHashMap.
**Model answer:** The contract has five properties for `equals` and one for `hashCode`. Here are all the ways it breaks and what happens:

**1. Missing `hashCode` (implements `equals` but inherits `Object.hashCode`):**
`Object.hashCode` returns the identity hash (derived from memory address). Two logically equal objects get different hash codes.
- `HashSet.add(a); set.contains(b)` → `false` even if `a.equals(b)` — they land in different buckets, `contains` never finds `b`.
- `HashMap.put(a, v); map.get(b)` → `null` — same root cause.

**2. Mutable fields used in `hashCode`:**
```java
@Override public int hashCode() { return Objects.hash(name, email); } // email can change
```
After insertion, mutating `email` changes the hash code. The object is now stored in the wrong bucket.
- `set.add(user); user.setEmail("new@x.com"); set.contains(user)` → `false` — object is in the old bucket, `contains` looks in the new one.
- `set.remove(user)` → `false` for the same reason — the entry is never found.
- `set.size()` counts the ghost object — phantom entry you can never remove.

**3. Breaking symmetry (`x.equals(y)` ≠ `y.equals(x)`):**
```java
// Subclass with "enhanced" equals
public class ExtendedProduct extends Product {
    @Override public boolean equals(Object o) {
        if (o instanceof ExtendedProduct ep) return super.equals(ep) && ep.extraField.equals(this.extraField);
        if (o instanceof Product p) return super.equals(p); // asymmetric
        return false;
    }
}
```
`product.equals(extendedProduct)` may be `true`, but `extendedProduct.equals(product)` is `false`. `list.contains(extendedProduct)` → result depends on which `equals` is called first during the scan — non-deterministic behavior.

**4. Breaking transitivity (inheritance + value equality):**
`a.equals(b)` and `b.equals(c)` but not `a.equals(c)`. Happens when mixing `instanceof`-based equals across an inheritance chain. The Set-theoretic invariant breaks: `set.add(a); set.add(c)` should keep both, but `a.equals(c) == false` may still hold while `a.equals(b)` and `b.equals(c)` are true — breaks sorted structures (`TreeSet` relies on transitive comparisons).

**5. Inconsistent `hashCode` (different values for same object between calls):**
If `hashCode` reads from a field that uses `Object.hashCode()` internally (e.g., a mutable nested object), two calls to `hashCode()` on the same object may return different values after the nested object is mutated. `ConcurrentHashMap` is especially sensitive because segment selection and read locking use `hashCode()` — inconsistent hashes can cause reads to check the wrong segment.

**6. `hashCode` returns a constant (e.g., `return 42`):**
Technically contract-compliant (equal objects have equal hash codes) but catastrophic for performance. All objects land in one bucket — `HashMap` degrades from O(1) to O(n) (or O(log n) with Java 8 treeification, which kicks in at 8 entries per bin when table capacity ≥ 64). A `HashMap` of 10,000 entries becomes effectively a linked list (or balanced tree), and lookup time becomes O(log n) instead of O(1).

**7. ConcurrentHashMap under mutation:**
Even with correct equals/hashCode, `ConcurrentHashMap` may return stale values if a mutable key is modified after insertion. The lookup uses `hashCode` to find the segment and `equals` to find the entry; a mutated key that returns a new hash will never find its old slot — same phantom-entry problem but now potentially across threads with visibility implications.
**Interview trap:** "Java 8 treeified buckets — does that fix the O(n) problem of hash collisions?" It reduces worst-case lookups to O(log n) instead of O(n) *within a bucket*. Treeification only occurs when a single bucket has ≥8 entries AND the table capacity is ≥64 (otherwise the table resizes first). The tree uses `Comparable` if available, or identity hash as a tiebreaker. It does NOT fix the root problem — it's a mitigation. A proper hash function distributes evenly and avoids collision entirely.
**Tags:** equals, hashcode, hashmap, hashset, concurrenthashmap, contract, internals, performance

---

## Q-OOP-024 [bloom: analyze] [level: master]
**Question:** Design a thread-safe, truly immutable class in Java for a `PriceSnapshot` (product ID, price amount, currency, timestamp). Enumerate every decision point and explain the consequence of getting each one wrong.
**Model answer:**
```java
public final class PriceSnapshot {                       // (1) final class
    private final long productId;                        // (2) final field, primitive — trivially immutable
    private final BigDecimal amount;                     // (3) BigDecimal is immutable — no defensive copy needed
    private final Currency currency;                     // (4) java.util.Currency is effectively immutable singleton
    private final Instant capturedAt;                    // (5) Instant is immutable — no defensive copy needed

    public PriceSnapshot(long productId, BigDecimal amount, Currency currency, Instant capturedAt) {
        if (productId <= 0) throw new IllegalArgumentException("productId must be positive");
        this.productId = productId;
        this.amount = Objects.requireNonNull(amount, "amount");        // (6) null guard
        this.currency = Objects.requireNonNull(currency, "currency");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
    }

    // No setters. (7)
    public long getProductId() { return productId; }
    public BigDecimal getAmount() { return amount; }   // BigDecimal is immutable — safe to return directly
    public Currency getCurrency() { return currency; }
    public Instant getCapturedAt() { return capturedAt; }

    public PriceSnapshot withAmount(BigDecimal newAmount) {    // (8) wither, not setter
        return new PriceSnapshot(productId, newAmount, currency, capturedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PriceSnapshot ps)) return false;
        return productId == ps.productId
            && amount.compareTo(ps.amount) == 0           // (9) compareTo, not equals — scale-independent
            && currency.equals(ps.currency)
            && capturedAt.equals(ps.capturedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, amount.stripTrailingZeros(), currency, capturedAt); // (9) consistent with equals
    }

    @Override
    public String toString() {
        return "PriceSnapshot{productId=" + productId + ", amount=" + amount
            + " " + currency + ", at=" + capturedAt + "}";
    }
}
```

**Decision points and failure consequences:**

(1) **`final` class:** without it, a subclass can add mutable fields and override getters. Callers holding a `PriceSnapshot` reference could receive a mutable subclass.

(2) **`final` fields:** prevents field reassignment after construction. Without `final`, a malicious or careless method could re-assign a field post-construction. Also: `final` fields have a Java Memory Model guarantee — their value is visible to all threads that obtain a reference to the object *after* the constructor completes, without additional synchronization. This is the formal basis for immutable objects being "free" thread-safety.

(3,4,5) **Choosing immutable field types (`BigDecimal`, `Instant`, `Currency`):** this is why avoiding `java.util.Date` matters — `Date` is mutable. If you stored a `Date` and returned it directly, the caller could call `date.setTime(...)` and retroactively mutate your "immutable" snapshot. The fix for mutable field types is: defensive copy in constructor + return defensive copy (or unmodifiable view) in getter. Rule of thumb: `BigDecimal`, `String`, `Integer`, `Instant`, `LocalDate`, `ZonedDateTime` — immutable, safe. `Date`, `byte[]`, `List`, `Map`, arrays — mutable, require defensive copies.

(6) **Null guards:** a null field can cause NPE at unexpected times (not at construction). Fail fast at construction with explicit message.

(7) **No setters:** omitting setters is necessary but not sufficient (see (3)).

(8) **Wither methods (copy-on-change):** return a new instance with one field modified. Without this pattern, callers who need a slightly different value must build from scratch or use the constructor with all arguments — error-prone. `withAmount` is safe because it calls the constructor which enforces all validation again.

(9) **`compareTo` in `equals` for `BigDecimal`:** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is `false` — `equals` is scale-sensitive. `compareTo` returns 0 for mathematically equal values regardless of scale. Using `equals` in your `equals` method means `PriceSnapshot(1, "10.0", USD, now)` and `PriceSnapshot(1, "10.00", USD, now)` are not equal — a subtle, hard-to-debug inconsistency in a Set or as a Map key.
**Interview trap:** "Is a `PriceSnapshot` safe to publish to another thread without synchronization?" Yes, if and only if it is truly immutable (all fields are final and all field types are immutable or defensively copied). The JMM guarantees that a safely constructed immutable object (constructor did not allow `this` to escape) is visible to any thread that reads the reference after the constructor returns, without additional synchronization. This is Java Language Specification §17.5.
**Tags:** immutability, thread-safety, value-object, bigdecimal, jmm, design, senior

---

## Q-OOP-025 [bloom: analyze] [level: master]
**Question:** Compare and contrast cohesion/coupling, Law of Demeter, DRY, SRP, and the anemic domain model — when do these principles point in different directions and force a genuine trade-off?
**Model answer:** These principles are complementary but have real tensions at their edges.

**DRY vs SRP:** extracting shared logic to satisfy DRY can create a class that serves multiple consumers — violating SRP if those consumers represent different reasons to change. Example: `PricingUtils` with shared tax-calculation logic used by both the checkout flow and the invoicing flow. DRY says: one place. SRP says: if checkout and invoicing have different change cadences or business owners, that shared class now changes for two reasons. Resolution: if the calculation is a domain concept (*how tax is calculated*) it belongs in a `TaxCalculator` service with a single, well-named responsibility. If it is coincidental similarity, let them diverge.

**Law of Demeter vs Rich Domain Model:** LoD says a method should not traverse deep object graphs. Rich Domain says the object itself should expose behavior. But a rich domain `Order` that knows about `Customer.Address.Country.TaxRules` to calculate tax violates LoD and creates tight coupling across the aggregate boundary. Resolution: the aggregate root (`Order`) exposes `calculateTax()` but delegates to a domain service that is passed in, rather than reaching through associations.

**High cohesion vs avoiding anemic model:** forcing all behavior onto domain objects to avoid the anemic anti-pattern can produce a god-class `Order` that knows about shipping, tax, invoicing, fraud, and inventory — low cohesion. Resolution (DDD approach): distribute behavior across multiple cohesive aggregates (`Order`, `Invoice`, `Shipment`) that communicate via domain events. Each aggregate is responsible for its own invariants only.

**DRY vs coupling:** eliminating all duplication often means centralizing logic, which increases coupling to the central module. If the central module is part of a different team's bounded context, you have now introduced a cross-context dependency to avoid copy-paste. Resolution: sometimes duplication across bounded contexts is correct — each context evolves independently, and sharing would couple their change cycles.

**The meta-principle (KISS + YAGNI as arbiters):** when two principles conflict, the simplest solution that handles the current requirements is usually correct. YAGNI says: don't create the abstraction until you have at least two concrete cases that genuinely benefit from it. KISS says: the cost of complexity is often higher than the cost of the duplication or coupling it was meant to fix.
**Interview trap:** "How do you decide when to extract an abstraction?" The Rule of Three: tolerate duplication twice, extract on the third occurrence. And only extract when the duplicated pieces truly represent the same *concept* — two pieces of code that are textually identical but will diverge as requirements evolve should not be merged. The question is not "do these look the same?" but "will they always mean the same thing?"
**Tags:** design-principles, trade-offs, dry, srp, law-of-demeter, cohesion, coupling, ddd, architecture

---

## Q-OOP-026 [bloom: analyze] [level: master]
**Question:** You are reviewing a pull request that introduces a new `@Component` with 11 injected dependencies. Walk through your analysis: what signals does this give you, what questions do you ask, and what refactoring strategies are available?
**Model answer:** Eleven dependencies is a strong smell — the class is almost certainly violating SRP and has high coupling. It is not automatically wrong (a true orchestration/facade class may legitimately coordinate many collaborators) but it demands scrutiny.

**Analysis questions:**
1. What does this class *do*? Can its behavior be described in one sentence without "and"? If not, it has multiple responsibilities.
2. Are all 11 dependencies used in every method, or do subsets cluster around specific operations? Clustering suggests the class should be split.
3. Are any of the dependencies infrastructure concerns mixed with domain logic? (e.g., `EmailSender`, `MetricsRegistry` sitting alongside `OrderValidator`, `PricingService`) — infrastructure concerns should be in a different layer or handled via events.
4. What is the test for this class? If the test setup requires 11 mocks, every test is fragile — any dependency change requires test changes.

**Refactoring strategies:**

**A. Extract method objects / decompose by operation:**
```java
// Instead of one service with 11 deps, split by distinct workflow step
class OrderCreationWorkflow { // 3-4 deps: validator, inventory, pricing }
class OrderFulfillmentWorkflow { // 3-4 deps: shipping, payment, notification }
class OrderAuditWorkflow { // 2 deps: auditLog, eventPublisher }
```

**B. Replace direct calls with domain events:**
Many dependencies are often notification/side-effect services (`EmailSender`, `AuditService`, `MetricsService`). Replace direct calls with publishing a domain event; each side-effect service is an independent event listener. The orchestrating class now has one dependency (`EventPublisher`) instead of four.

**C. Introduce a Facade or composite service:**
If some dependencies always travel together, wrap them in a cohesive abstraction:
```java
// Instead of injecting UserService, AddressService, PreferencesService separately:
class CustomerContext {
    UserService user; AddressService address; PreferencesService prefs;
    // customer-related operations — cohesive
}
// Now inject one CustomerContext
```

**D. Aggregate root pattern (DDD):**
If the class is a service that operates on multiple entities, the entities themselves may be poorly modeled. A well-defined aggregate root with rich domain behavior reduces the number of service-level dependencies.

**E. Accept it (orchestrator pattern):**
A top-level application service (use case handler, saga orchestrator) may legitimately have 8-10 dependencies if it is the one true place that coordinates a complex workflow. The key is: it should *coordinate*, not *compute* — all actual logic lives in the dependencies, and the orchestrator only calls them in sequence. If you can describe it as "call A, then B, check result, call C or D" it is a legitimate orchestrator.
**Interview trap:** "Would you reject this PR?" Not necessarily on dependency count alone. "I would ask for the rationale and look at whether the class is an orchestrator or a god object. If it is an orchestrator, 11 might be fine. If it is doing logic itself across all 11 dependencies, I would split it. The test complexity would tell me immediately — if the unit test requires 11 mocks and 400 lines of setup, the class is doing too much."
**Tags:** design, code-review, srp, coupling, refactoring, ddd, events, architecture, master
