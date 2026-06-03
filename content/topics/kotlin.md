# Kotlin — question bank

> Kotlin is the primary JVM language for modern backend development at companies running Spring Boot microservices. For a senior backend engineer, this means: deep knowledge of the type system (nullability, variance, sealed hierarchies), idiomatic use of the standard library (scope functions, collections, sequences), understanding what the compiler generates (data classes, inline, delegation), and clean Java interop without friction. Coroutine fundamentals belong here; deep coroutine/reactive architecture is in `coroutines_webflux`.

## Scope

- `val` vs `var` — immutable reference vs mutable, backing fields, compiler output
- Null safety — `?`, `!!`, `?.`, `?:`, `let`, `lateinit`, platform types from Java
- Smart casts — compiler flow analysis, contracts
- Data classes — generated methods (`equals`, `hashCode`, `toString`, `copy`, `componentN`), body properties exclusion
- Sealed classes and sealed interfaces — exhaustive `when`, hierarchy rules, vs enum
- `object` keyword — declaration (singleton), companion object, anonymous object expression
- Extension functions and properties — static dispatch, priority vs members, access to private members
- Higher-order functions and lambdas — function types, `it`, trailing-lambda convention, SAM conversions
- Scope functions — `let`, `run`, `with`, `apply`, `also` — receiver vs argument, return value, when to use each
- Delegation — `by` keyword, `lazy`, `observable`, interface delegation
- `inline` functions and `reified` type parameters — why inlining is needed, how reified works
- Collections — immutable vs mutable interfaces, `List`/`MutableList` distinction, `Sequence` lazy vs eager evaluation
- Default and named arguments — Java interop with `@JvmOverloads`
- Destructuring — `componentN`, destructuring in lambdas and `for` loops
- Type system — `Any`, `Unit`, `Nothing`, bottom type, covariance/contravariance
- Declaration-site variance — `out` (covariance), `in` (contravariance), `*` projection
- Java interop — `@JvmStatic`, `@JvmOverloads`, `@JvmField`, `@Throws`, nullability annotations
- Properties vs fields — backing field, custom getters/setters, computed properties
- Coroutines intro — `suspend`, `launch` vs `async`, structured concurrency (depth in `coroutines_webflux`)
- Spring Boot integration pitfalls — `final` by default, `kotlin-spring` plugin, JPA, Jackson

---

## Q-KT-001 [bloom: recall] [level: junior]

**Question:** What is the difference between `val` and `var` in Kotlin? Is `val` the same as immutable?

**Model answer:** `val` declares a read-only reference — once assigned it cannot be reassigned (analogous to Java `final`). `var` is a mutable variable that can be reassigned. However, `val` does NOT mean the object itself is immutable. A `val list: MutableList<Int>` can still have elements added or removed; you just cannot point `list` at a different object.

Under the hood: `val` compiles to a `final` field (for properties) with only a getter generated; `var` compiles to a non-final field with both getter and setter. For local variables `val` simply prevents reassignment without a field at all.

Best practice: prefer `val` by default. Reach for `var` only when reassignment is genuinely required. In Spring beans, all injected dependencies should be `val`.

```kotlin
val list = mutableListOf(1, 2, 3)
list.add(4)       // fine — object mutated
// list = mutableListOf() // compile error — reference reassignment blocked
```

**Interview trap:** "Can a `val` property have a custom getter that returns a different value each call?" — Yes. `val time get() = System.currentTimeMillis()` is legal. The `val` contract is about reassignment, not about returning the same value.

**Tags:** val, var, basics, syntax

---

## Q-KT-002 [bloom: recall] [level: junior]

**Question:** Name and explain the four null-safety operators in Kotlin: `?.`, `?:`, `!!`, and smart cast after null check.

**Model answer:** Kotlin's type system separates nullable types (`String?`) from non-nullable types (`String`) at compile time. You cannot assign `null` to a non-nullable type.

The four key mechanisms:

1. **`?.` safe-call operator** — evaluates to `null` instead of throwing NPE if the receiver is null. Chains cleanly: `user?.address?.city`.
2. **`?:` Elvis operator** — provides a default value (or throws) when the left side is null: `val name = user?.name ?: "anonymous"`. Can throw: `val name = user?.name ?: throw IllegalStateException("no name")`.
3. **`!!` not-null assertion** — forces a nullable type to non-null, throwing `KotlinNullPointerException` if the value is actually null. Use sparingly, only at system boundaries where external validation guarantees non-null.
4. **Smart cast** — after an `if (x != null)` check (or `is` check), the compiler automatically treats `x` as non-null (or the checked type) inside that branch without an explicit cast.

```kotlin
val city: String? = user?.address?.city
val display = city ?: "unknown"         // Elvis
val forced: String = city!!             // throws if null
if (city != null) println(city.length)  // smart cast: city is String here
```

**Interview trap:** "When does smart cast NOT work?" — When the variable is a `var` (compiler cannot guarantee another thread didn't change it between the check and the use), when it's a `var` property, or when it's a delegated/open property. In those cases use `val city = this.city` to capture locally.

**Tags:** null-safety, smart-cast, basics

---

## Q-KT-003 [bloom: recall] [level: junior]

**Question:** What is `lateinit var` and when should you use it? What are its restrictions?

**Model answer:** `lateinit var` tells the compiler "I promise to initialize this before first use; don't require an initial value or nullable type." It is designed for dependency injection and test setup where initialization happens after construction.

```kotlin
class UserService {
    @Autowired
    lateinit var userRepository: UserRepository

    fun find(id: Long) = userRepository.findById(id)
}
```

If accessed before initialization, Kotlin throws `UninitializedPropertyAccessException` with a clear message identifying the property — better than a generic NPE.

Restrictions:
- Only `var`, not `val`
- Only non-nullable types (nullable types already have `null` as initial value)
- Only reference types — not primitive types (`Int`, `Boolean`, etc.)
- Can check: `if (::userRepository.isInitialized)` before access

Best practice: in Spring, prefer constructor injection with `val` over `lateinit var` for field injection. `lateinit var` shines in test classes (`@BeforeEach` setup).

**Interview trap:** "Why can't `lateinit` be used with primitive types like `Int`?" — Primitives are stored as JVM primitives (`int`, `long`), which cannot be `null`. `lateinit` internally marks the field as `null` to signal "not yet initialized", which requires an object reference. For primitives, use a nullable wrapper (`Int?`) or a different initialization strategy.

**Tags:** lateinit, null-safety, spring, basics

---

## Q-KT-004 [bloom: recall] [level: junior]

**Question:** What does the compiler auto-generate for a `data class`?

**Model answer:** A `data class` is a class whose primary purpose is holding data. The compiler generates five things based on the **primary constructor properties**:

1. `equals()` — structural equality comparing all primary constructor properties
2. `hashCode()` — consistent with `equals()`, derived from primary constructor properties
3. `toString()` — human-readable format: `User(id=1, name=Jane)`
4. `copy()` — creates a new instance with optional overrides: `user.copy(name = "Bob")`
5. `componentN()` functions — `component1()`, `component2()`, etc., enabling destructuring: `val (id, name) = user`

Critical exclusion: properties declared in the class body (not the primary constructor) are excluded from all generated methods.

```kotlin
data class User(val id: Long, val name: String) {
    val displayName: String = name.uppercase() // excluded from equals/hashCode/toString
}
```

Constraints: at least one primary constructor parameter; cannot be `abstract`, `open`, `sealed`, or `inner`.

**Interview trap:** "Two `User` objects with the same `id` and `name` but different `displayName` — are they equal?" — Yes, because `displayName` is a body property, excluded from `equals`. This can cause subtle bugs when body properties carry meaningful state.

**Tags:** data-class, equals, hashCode, destructuring, basics

---

## Q-KT-005 [bloom: recall] [level: junior]

**Question:** What is an extension function? How does it compile, and what are its limitations?

**Model answer:** An extension function lets you add methods to an existing class without modifying its source or using inheritance.

```kotlin
fun String.isPalindrome(): Boolean = this == this.reversed()
"racecar".isPalindrome() // true
```

**Compilation:** The compiler transforms this to a static JVM method in the file's class, with the receiver as the first parameter:
```java
// Java equivalent
public static boolean isPalindrome(String $this) {
    return $this.equals(StringsKt.reversed($this));
}
```
No virtual dispatch. No runtime overhead. Called via static dispatch resolved at compile time.

**Limitations:**
- No access to private or protected members of the class
- Member functions always win over extension functions with the same signature — extensions cannot override members
- Resolved statically: calling an extension on a variable typed as `Animal` calls `Animal.ext()` even if the runtime type is `Dog`
- Not part of the class's actual API — they don't appear in reflective inspection of the class

**Interview trap:** "Can you polymorphically override an extension function?" — No. Extensions are statically dispatched. If `Animal` and `Dog` both have `fun Animal.sound()` and `fun Dog.sound()`, calling `sound()` on a `Dog` reference typed as `Animal` calls `Animal.sound()`.

**Tags:** extension-functions, static-dispatch, basics

---

## Q-KT-006 [bloom: recall] [level: junior]

**Question:** What is a sealed class and how does it differ from an enum?

**Model answer:** A sealed class restricts subclassing: all direct subclasses must be defined in the same file (Kotlin 1.4 and earlier) or the same package/module (Kotlin 1.5+ sealed interfaces). The compiler knows the complete set of subclasses, which makes `when` expressions exhaustive.

```kotlin
sealed class Result<out T>
data class Success<T>(val data: T) : Result<T>()
data class Failure(val error: Throwable) : Result<Nothing>()
object Loading : Result<Nothing>()

fun <T> handle(r: Result<T>): String = when (r) {
    is Success -> "data: ${r.data}"
    is Failure -> "error: ${r.error.message}"
    is Loading -> "loading..."
    // no else needed — compiler knows all subtypes
}
```

**vs enum:**
| | Sealed class | Enum |
|---|---|---|
| Instances | Multiple per subclass | One per constant |
| Data per variant | Different fields per subclass | Same fields for all |
| Inheritance | Can subclass further | Cannot subclass |
| Type params | Supported | Not supported |

Use sealed classes when variants need different shapes (different fields). Use enums when constants all have the same shape.

**Interview trap:** "Can a sealed class subclass have its own subclasses?" — Yes. A sealed class subclass can itself be open or abstract, allowing further inheritance. The seal only applies to the direct subclasses of the sealed root.

**Tags:** sealed-class, when, type-system, basics

---

## Q-KT-007 [bloom: understand] [level: regular]

**Question:** Explain the three uses of the `object` keyword in Kotlin: object declaration, companion object, and object expression. When would you use each?

**Model answer:** 

**1. Object declaration — singleton:**
```kotlin
object AppConfig {
    val timeout = 30_000L
    fun baseUrl() = System.getenv("BASE_URL") ?: "http://localhost:8080"
}
AppConfig.timeout // accessed like a static field
```
Thread-safe initialization via class loader (equivalent to Java's class-holder idiom). Use for stateless utilities, registries, or app-wide constants.

**2. Companion object — class-level members:**
```kotlin
class UserService(private val repo: UserRepository) {
    companion object {
        private const val MAX_PAGE = 100
        fun create(repo: UserRepository) = UserService(repo) // factory
    }
}
UserService.MAX_PAGE
UserService.create(repo)
```
Can implement interfaces. Can be named: `companion object Factory`. From Java, accessed as `UserService.Companion.create(repo)` unless annotated with `@JvmStatic`, which generates a real Java static method.

**3. Object expression — anonymous object:**
```kotlin
val comparator = object : Comparator<String> {
    override fun compare(a: String, b: String) = a.length - b.length
}
```
Replaces Java anonymous inner classes. Can capture enclosing scope. Note: each call to the object expression creates a new instance (unlike object declarations which are singletons).

**Interview trap:** "Companion object vs top-level function — which to prefer?" — Top-level functions for general utilities. Companion object for factory methods or constants tightly coupled to the class (e.g., `User.fromJson()`). A top-level function has no implicit association with a class, which is often cleaner.

**Tags:** object, companion, singleton, anonymous-object, regular

---

## Q-KT-008 [bloom: understand] [level: regular]

**Question:** Explain the five scope functions: `let`, `run`, `with`, `apply`, `also`. How do they differ and when do you use each?

**Model answer:** All five execute a lambda in the context of an object. They differ on two axes: **how the context object is referenced** (as `this` — receiver — or as `it` — argument) and **what they return** (the lambda result or the context object itself).

| Function | Context as | Returns | Use case |
|---|---|---|---|
| `let` | `it` | lambda result | null-safe block, transform + return result |
| `run` | `this` | lambda result | configure + compute result |
| `with` | `this` | lambda result | grouping calls, no chaining |
| `apply` | `this` | context object | builder-style initialization |
| `also` | `it` | context object | side effects (logging, validation) without interrupting chain |

```kotlin
// let — null-safe + transform
val len = str?.let { it.trim().length }

// apply — builder-style init
val request = HttpRequest().apply {
    method = "POST"
    url = "/api/orders"
    addHeader("Content-Type", "application/json")
}

// also — side effect in chain
val user = createUser()
    .also { log.info("Created user ${it.id}") }
    .also { auditService.record(it) }

// run — compute result using receiver
val result = dbConnection.run {
    val rows = query("SELECT ...")
    rows.map { transform(it) }
}

// with — grouping without chaining
with(userDto) {
    validate()
    transform()
    save()
}
```

Memory rule: `apply`/`also` return the object (for chaining builders and side effects). `let`/`run`/`with` return the lambda result (for transformations).

**Interview trap:** "When would `also` cause a bug?" — If you mutate the context object in `also` thinking you're in `apply`. `also` gives `it`, `apply` gives `this`. Using `also` and writing `name = "foo"` won't compile because `it.name = "foo"` is what you need. Easy to confuse under pressure.

**Tags:** scope-functions, let, run, with, apply, also, regular

---

## Q-KT-009 [bloom: understand] [level: regular]

**Question:** What is the `by` delegation keyword in Kotlin? Explain `lazy`, `observable`, and interface delegation.

**Model answer:** The `by` keyword delegates property access (or interface implementation) to another object. The compiler generates the required accessor or method calls.

**`lazy` — computed once on first access:**
```kotlin
val heavyService: HeavyService by lazy {
    HeavyService(config)
}
```
Thread-safe by default (`LazyThreadSafetyMode.SYNCHRONIZED`). The initializer runs once; subsequent reads return the cached value. For single-threaded contexts use `lazy(LazyThreadSafetyMode.NONE)` to skip locking overhead.

**`observable` — callback on change:**
```kotlin
var status: String by Delegates.observable("PENDING") { prop, old, new ->
    log.info("${prop.name} changed: $old -> $new")
}
```
Fires the lambda after every assignment. `vetoable` is similar but the lambda returns a boolean to accept or reject the change.

**Interface delegation — composition over inheritance:**
```kotlin
interface Printer { fun print(msg: String) }

class ConsolePrinter : Printer {
    override fun print(msg: String) = println(msg)
}

class LoggingPrinter(private val delegate: Printer) : Printer by delegate {
    // all Printer methods forwarded to delegate automatically
    // override specific methods to add behavior
}
```
The compiler generates boilerplate that forwards every interface method to the delegate. Eliminates manual delegation code.

**Custom delegate:** any object with `getValue`/`setValue` operator functions can be a delegate.

**Interview trap:** "Is `lazy` always thread-safe?" — Only with the default mode (`SYNCHRONIZED`). `PUBLICATION` mode allows multiple initializations but only one result survives. `NONE` is not thread-safe. In a multithreaded Spring service context, the default is correct.

**Tags:** delegation, lazy, observable, by, regular

---

## Q-KT-010 [bloom: understand] [level: regular]

**Question:** What is declaration-site variance in Kotlin? Explain `out` (covariance) and `in` (contravariance) with examples.

**Model answer:** Variance describes how a generic type with a subtype relationship behaves: if `Dog` extends `Animal`, is `Box<Dog>` a subtype of `Box<Animal>`?

Java uses use-site wildcards (`? extends T`, `? super T`) — awkward and verbose. Kotlin uses **declaration-site variance** — you declare variance once at the class definition.

**`out T` — covariant (producer):** the class only produces `T` values (returns them), never consumes (never takes them as input). `Box<Dog>` IS-A `Box<Animal>`.

```kotlin
interface Source<out T> {
    fun next(): T  // only produces T
}
val dogs: Source<Dog> = DogSource()
val animals: Source<Animal> = dogs  // legal because out
```

**`in T` — contravariant (consumer):** the class only consumes `T` values (takes them as parameters), never produces. `Sink<Animal>` IS-A `Sink<Dog>`.

```kotlin
interface Sink<in T> {
    fun accept(value: T)  // only consumes T
}
val animalSink: Sink<Animal> = AnimalSink()
val dogSink: Sink<Dog> = animalSink  // legal because in
```

**Invariant (no annotation):** `MutableList<Dog>` is NOT a `MutableList<Animal>` because mutation breaks type safety.

**Star projection `*`:** unknown type — `List<*>` is `List<out Any?>`. Safe to read (get `Any?`), unsafe to write.

Real-world example: Kotlin's `List<out E>` is covariant — `List<String>` is a `List<Any>`. `MutableList<E>` is invariant.

**Interview trap:** "Why can't `MutableList` be covariant?" — Because it has `add(element: E)` — it consumes `E`. If `MutableList<Dog>` were a `MutableList<Animal>`, you could add a `Cat` to it, corrupting the list. The type system correctly rejects this.

**Tags:** generics, variance, covariance, contravariance, type-system, regular

---

## Q-KT-011 [bloom: understand] [level: regular]

**Question:** Explain Kotlin's `Any`, `Unit`, and `Nothing` types. Where does each fit in the type hierarchy?

**Model answer:** 

**`Any`** — the root of the Kotlin type hierarchy (equivalent to Java's `Object` but cleaner). Every non-nullable Kotlin class is a subtype of `Any`. It provides `equals()`, `hashCode()`, `toString()`. `Any?` is the supertype of every Kotlin type including nullable types.

**`Unit`** — the return type of functions that don't return a meaningful value (equivalent to Java `void`). Unlike Java `void`, `Unit` is an actual type with a single value (`Unit`). This makes it usable in generic code: `fun process(): Unit` is valid and `Callable<Unit>` works.

```kotlin
val result: Unit = println("hello")  // println returns Unit
```

**`Nothing`** — the bottom type: a subtype of every type. A function returning `Nothing` never returns normally (throws or loops forever). Used for:
- `throw` expressions (which have type `Nothing`)
- Functions that always throw: `fun fail(msg: String): Nothing = throw IllegalStateException(msg)`
- Sealed class subtypes that carry no `T`: `data class Error(val e: Throwable) : Result<Nothing>()`

```kotlin
fun assertNotNull(value: String?): String =
    value ?: throw IllegalArgumentException("null") // ?: Nothing is fine here
```

Because `Nothing` is a subtype of everything, the Elvis expression `value ?: throw ...` type-checks: the right side is `Nothing`, which is also a `String`, so the expression is `String`.

**Interview trap:** "What's the difference between `Nothing` and `Unit`?" — `Unit` returns normally with a unit value. `Nothing` never returns — execution ends via exception or infinite loop. `Nothing` is a subtype of every type; `Unit` is just the top-level ordinary type for void-like functions.

**Tags:** type-system, any, unit, nothing, regular

---

## Q-KT-012 [bloom: understand] [level: regular]

**Question:** What are higher-order functions and lambdas in Kotlin? Explain function types, the `it` shorthand, and the trailing-lambda convention.

**Model answer:** A higher-order function takes a function as a parameter or returns a function. Kotlin represents functions as first-class values with explicit function types.

**Function types:**
```kotlin
val transform: (String) -> Int = { s -> s.length }
val biFunction: (Int, Int) -> Boolean = { a, b -> a > b }
val noArg: () -> String = { "hello" }
```

**`it` shorthand:** when a lambda has exactly one parameter, you can omit the parameter declaration and use `it` implicitly.
```kotlin
listOf("a", "bb", "ccc").map { it.length }  // it = String
```

**Trailing-lambda convention:** if the last parameter of a function is a function type, the lambda can be placed outside the parentheses. If the lambda is the only argument, parentheses can be omitted entirely.
```kotlin
fun withRetry(times: Int, block: () -> Unit) { ... }

withRetry(3) { doWork() }       // trailing lambda
listOf(1, 2, 3).filter { it > 1 }  // parens omitted entirely
```

**SAM conversions:** Kotlin automatically converts a lambda to a Java functional interface (single abstract method). `button.setOnClickListener { handleClick(it) }` — no need for anonymous class.

**Return from lambda:** `return` inside a lambda returns from the enclosing function (local return). Use `return@label` for non-local returns in inline functions.

**Interview trap:** "What's the difference between `return` and `return@forEach` inside `forEach`?" — Bare `return` inside an inline lambda returns from the enclosing function (non-local return — only works in inline functions). `return@forEach` returns from the current lambda iteration only, like `continue`.

**Tags:** higher-order-functions, lambdas, function-types, trailing-lambda, regular

---

## Q-KT-013 [bloom: apply] [level: regular]

**Question:** Write a Kotlin function that takes a list of `Order` objects (`customerId: Long`, `total: BigDecimal`, `status: String`) and returns a `Map<Long, BigDecimal>` of total spend per customer for COMPLETED orders only.

**Model answer:**
```kotlin
data class Order(val customerId: Long, val total: BigDecimal, val status: String)

fun totalSpendByCustomer(orders: List<Order>): Map<Long, BigDecimal> =
    orders
        .filter { it.status == "COMPLETED" }
        .groupBy { it.customerId }
        .mapValues { (_, customerOrders) ->
            customerOrders.sumOf { it.total }
        }
```

`filter` removes non-completed orders. `groupBy` produces `Map<Long, List<Order>>`. `mapValues` transforms each list to a sum. `sumOf` (Kotlin 1.4+) accepts a `BigDecimal`-returning selector.

Alternative with `fold` for older Kotlin:
```kotlin
.mapValues { (_, orders) -> orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.total } }
```

Never use `Double` or `Float` for monetary values — use `BigDecimal` throughout.

**Interview trap:** "Why not `sum()`?" — `sum()` only works for `Int`, `Long`, `Double`, `Float`. For `BigDecimal` (or any custom numeric type) you need `sumOf { it.total }` or `fold`. Using `Double` for currency introduces floating-point rounding errors.

**Tags:** collections, higher-order-functions, bigdecimal, apply, regular

---

## Q-KT-014 [bloom: apply] [level: regular]

**Question:** Explain how `Sequence` differs from `List` in collection operations. When should you prefer a `Sequence`?

**Model answer:** Kotlin's standard collection operations (`map`, `filter`, `flatMap`) on `List` are **eager** — each operation creates an intermediate list. On `Sequence` operations are **lazy** — elements are processed one at a time through the entire pipeline before moving to the next.

```kotlin
// Eager — creates 3 intermediate lists
val result = (1..1_000_000)
    .filter { it % 2 == 0 }      // intermediate List<Int>
    .map { it * it }              // intermediate List<Int>
    .take(5)                      // final List<Int>

// Lazy — no intermediate lists, stops after 5 elements
val result = (1..1_000_000)
    .asSequence()
    .filter { it % 2 == 0 }
    .map { it * it }
    .take(5)
    .toList()
```

With the sequence, `.take(5)` causes the pipeline to stop after 5 elements pass through — it doesn't process all 1M elements.

**When to prefer Sequence:**
- Large or infinite collections where only a portion is needed
- Multi-step pipelines with `filter` + `map` + `take` where intermediate lists waste memory
- Reading from a database cursor or file line-by-line

**When NOT to use Sequence:**
- Small collections — lazy overhead (object allocation per step) costs more than the gain
- Operations that need full traversal anyway (e.g., `sorted()`, `groupBy()`)
- When you need a list result immediately (must call `toList()` at the end)

**Interview trap:** "Is a Sequence always faster than a List?" — No. For small collections, the per-element overhead of lazy evaluation is worse. Benchmark before assuming. Sequences shine on large datasets with early termination.

**Tags:** collections, sequence, lazy-evaluation, performance, regular

---

## Q-KT-015 [bloom: apply] [level: senior]

**Question:** What are `inline` functions and `reified` type parameters? Why does Kotlin need them and what are the constraints?

**Model answer:** 

**`inline` functions:** The compiler copies the function body (and any lambda arguments) to the call site instead of calling through a function object. Primary motivation: avoid the overhead of lambda object allocation and virtual dispatch for higher-order functions in hot paths.

```kotlin
inline fun <T> measure(block: () -> T): T {
    val start = System.nanoTime()
    val result = block()
    println("${System.nanoTime() - start}ns")
    return result
}
```

Without `inline`, each call to `measure { ... }` allocates a lambda object. With `inline`, the lambda body is inlined — zero allocation.

**`reified` type parameters:** Normally, generic type parameters are erased at runtime (JVM type erasure). You can't write `if (x is T)` in a normal generic function. But inside an `inline` function, the compiler knows `T` at each call site and can inline the actual type. `reified` makes `T` available at runtime:

```kotlin
inline fun <reified T> Any.isType(): Boolean = this is T
inline fun <reified T> parseJson(json: String): T = objectMapper.readValue(json, T::class.java)

"hello".isType<String>()  // true — compiles to: "hello" is String
parseJson<User>(json)     // T::class.java is User::class.java at this call site
```

**Constraints:**
- `reified` only works in `inline` functions — the type must be known at compile time for inlining
- `inline` functions cannot be called from Java (no lambda inlining across JVM boundaries)
- `noinline` marks a lambda parameter that should NOT be inlined (e.g., when you store it in a variable)
- `crossinline` disallows non-local returns from the lambda (when the lambda is called in a different execution context)
- Inlining large functions bloats bytecode — keep inline functions small

**Interview trap:** "Why can't a regular generic function use `reified`?" — Because at runtime the JVM erases `T` to its upper bound (`Any?`). `reified` requires the compiler to substitute the actual type at each call site, which is only possible when the function is inlined. Without inlining, there's no call site to substitute into.

**Tags:** inline, reified, generics, type-erasure, senior

---

## Q-KT-016 [bloom: apply] [level: senior]

**Question:** Explain platform types in Kotlin. How does Java interop introduce them, what are the risks, and how do you mitigate those risks?

**Model answer:** When Kotlin calls a Java method that returns a reference type, Kotlin cannot know from the bytecode alone whether the return value can be `null`. The type is represented as a **platform type**, denoted `String!` in IDE error messages (the `!` is not valid Kotlin syntax — it's a notation only). A platform type is neither nullable nor non-null — it bypasses the null safety system.

```java
// Java
public class UserRepository {
    public User findById(Long id) { ... } // might return null
}
```

```kotlin
// Kotlin calling it
val user = repo.findById(1L)  // type is User! (platform type)
user.name  // compiles fine — but NPE at runtime if user is null!
```

**Risks:** Platform types silently opt out of null safety. Any dereference can throw `NPE` at runtime without a compiler warning.

**Mitigations:**
1. **Annotate Java code** with `@Nullable` / `@NotNull` (from `javax.annotation`, `org.jetbrains.annotations`, or `jakarta.validation`). Kotlin reads these and infers the correct nullable or non-nullable type.
2. **Immediately assign to a typed variable:** `val user: User? = repo.findById(1L)` — forces explicit acknowledgment of nullability.
3. **Write Kotlin wrappers** around Java APIs you control, handling null at the boundary.
4. **Lombok + annotations plugin:** if using Lombok, configure the Kotlin compiler plugin to respect Lombok's nullability annotations.

**Interview trap:** "If you assign a platform type to a `val user: User` (non-nullable), are you safe?" — No. Kotlin trusts the non-nullable declaration and won't insert a null check. If the Java method returns null, you get an NPE when you first try to use `user`, not at the assignment. Always use `User?` when in doubt.

**Tags:** java-interop, platform-types, null-safety, senior

---

## Q-KT-017 [bloom: apply] [level: senior]

**Question:** Describe the Java interop annotations: `@JvmStatic`, `@JvmOverloads`, `@JvmField`. What Java code does each generate and when is each needed?

**Model answer:** 

**`@JvmStatic`** — applied to companion object or object methods. Generates a real Java static method in addition to the instance method on the `Companion` object. Without it, Java callers must write `UserService.Companion.create()`.

```kotlin
class UserService {
    companion object {
        @JvmStatic
        fun create(repo: UserRepository): UserService = UserService(repo)
    }
}
// Java: UserService.create(repo) ← works cleanly
```

**`@JvmOverloads`** — applied to functions with default parameters. Generates a Java overloaded method for each default parameter combination (from left to right). Without it, Java callers must supply all arguments explicitly.

```kotlin
@JvmOverloads
fun connect(host: String, port: Int = 8080, ssl: Boolean = false) { ... }
// Java sees: connect(host), connect(host, port), connect(host, port, ssl)
```

Note: `@JvmOverloads` on constructors generates overloaded constructors — useful for Spring's no-arg constructor requirement.

**`@JvmField`** — exposes a Kotlin property as a Java field directly (no getter/setter). The property must not have a custom getter or setter. Used when Java frameworks expect direct field access (e.g., constants in `companion object`).

```kotlin
companion object {
    @JvmField
    val DEFAULT_TIMEOUT = 30_000L
}
// Java: UserService.DEFAULT_TIMEOUT (field access, not method call)
```

Without `@JvmField`, a `const val` is already inlined at compile time. For non-const, `@JvmField` is needed.

`@Throws(IOException::class)` — declares checked exceptions for Java callers (Kotlin has no checked exceptions, so Java doesn't know to catch them).

**Interview trap:** "`const val` vs `@JvmField val` in companion object — what's the difference?" — `const val` works only for compile-time constants (primitives and `String`); it's inlined by the compiler. `@JvmField val` works for any property but is a runtime field, not inlined. For public API consumed from Java, `const val` is preferred for constants; `@JvmField` for object references.

**Tags:** java-interop, jvmstatic, jvmoverloads, jvmfield, annotations, senior

---

## Q-KT-018 [bloom: apply] [level: senior]

**Question:** Explain Kotlin properties vs Java fields. What is a backing field and how do custom getters/setters work? When does a property NOT have a backing field?

**Model answer:** In Kotlin, a **property** is a language concept that bundles a value (optionally stored) with accessors (getter and optionally setter). It's not the same as a field — a property may or may not have a backing field.

**Default property:** `val name: String = "Alice"` — the compiler generates a `private final String name` field, a `getName()` getter. For `var`, also a `setName()` setter.

**Backing field (`field` keyword):** inside a custom accessor, `field` refers to the underlying storage. Without `field`, the accessor is computed-only.

```kotlin
var name: String = ""
    get() = field.trim()
    set(value) {
        field = value.trim()  // field = backing storage
    }
```

**Computed property — no backing field:**
```kotlin
val fullName: String
    get() = "$firstName $lastName"
```
No `field` reference → compiler generates no backing storage. Calling `fullName` always executes the getter.

```kotlin
val isActive: Boolean get() = status == "ACTIVE"  // computed, no storage
```

**When no backing field is generated:**
- Property with only a custom getter that doesn't reference `field`
- Interface properties (always abstract)
- Delegated properties (storage is in the delegate object)

**Visibility:** you can narrow the setter's visibility while keeping getter public: `var count: Int = 0  private set` — useful for encapsulating mutation.

**Interview trap:** "If a `val` property has a custom getter, can it return different values on different calls?" — Yes. `val time: Long get() = System.currentTimeMillis()` computes a new value every time. `val` only prevents reassignment of the property; it says nothing about the getter's purity.

**Tags:** properties, backing-field, getters-setters, senior

---

## Q-KT-019 [bloom: apply] [level: senior]

**Question:** Walk through how destructuring works in Kotlin. Cover data classes, `componentN`, destructuring in lambdas, and `for` loops.

**Model answer:** Destructuring extracts multiple values from an object in one declaration. The compiler calls `componentN()` functions (where N is 1-based position) to extract values.

**Data classes** auto-generate `componentN()` for each primary constructor property in order:
```kotlin
data class Point(val x: Int, val y: Int)
val (x, y) = Point(3, 4)  // x=3, y=4
// compiles to: val x = point.component1(); val y = point.component2()
```

**Skip components with `_`:**
```kotlin
val (_, y) = Point(3, 4)  // skip x, only need y
```

**`for` loops over `Map.Entry`:**
```kotlin
for ((key, value) in map) {
    println("$key -> $value")
}
```
`Map.Entry` provides `component1()` (key) and `component2()` (value).

**Destructuring in lambdas:**
```kotlin
map.forEach { (key, value) -> println("$key: $value") }

// vs without destructuring:
map.forEach { entry -> println("${entry.key}: ${entry.value}") }
```
The parentheses in the lambda parameter list signal destructuring.

**Custom `componentN()`:** any class can support destructuring by declaring `operator fun component1()`, `operator fun component2()`, etc.:
```kotlin
class Range(val start: Int, val end: Int) {
    operator fun component1() = start
    operator fun component2() = end
}
```

**Pitfall:** destructuring is positional, not named. If a data class reorders its constructor parameters, destructuring silently changes meaning without a compile error. Name matters for readability; position matters for destructuring.

**Interview trap:** "If a data class adds a new property as the second constructor parameter, what happens to existing destructuring code?" — It silently breaks. `val (id, name)` now gets `id=id, name=newField` — the old `name` binding now receives `newField`. No compile error. This is why destructuring should be used carefully with data classes that may evolve.

**Tags:** destructuring, componentN, data-class, senior

---

## Q-KT-020 [bloom: apply] [level: senior]

**Question:** How does Spring Boot work with Kotlin, and what are the specific pitfalls you must address? Cover: `final` by default, JPA entities, Jackson, constructor injection.

**Model answer:** Kotlin classes are `final` by default — a fundamental conflict with Spring's CGLIB proxy mechanism, which subclasses beans to add AOP, transactions, and `@Configuration` processing.

**Problem 1 — CGLIB proxying fails on final classes:**
`@Service`, `@Component`, `@Configuration`, `@Repository` beans need to be subclassable by Spring. Solution: `kotlin-spring` Gradle plugin (part of `kotlin-allopen`) automatically applies `open` to classes annotated with `@Component`, `@Service`, `@Repository`, `@Controller`, `@Configuration`, `@Transactional`, and others.

```kotlin
// build.gradle.kts
plugins {
    kotlin("plugin.spring") version "..."
}
```

**Problem 2 — JPA entities need `open` and no-arg constructor:**
JPA proxies for lazy-loading require `open` on entity classes and their properties, plus a no-arg constructor (Hibernate requirement). Solution: `kotlin-jpa` plugin adds `@Entity`, `@Embeddable`, `@MappedSuperclass` to `allopen` and generates synthetic no-arg constructors.

**Problem 3 — Jackson deserialization fails on data classes:**
By default, Jackson cannot deserialize Kotlin data classes because it doesn't know about the primary constructor pattern. Solution: add `jackson-module-kotlin`:
```kotlin
val mapper = ObjectMapper().registerKotlinModule()
// or via Spring Boot auto-configuration (already included in spring-boot-starter-web)
```

**Problem 4 — Constructor injection (prefer over `lateinit var`):**
```kotlin
@Service
class OrderService(
    private val orderRepo: OrderRepository,   // val, injected via constructor
    private val paymentClient: PaymentClient
)
```
Constructor injection with `val` is idiomatic. Avoid `@Autowired lateinit var` — it breaks encapsulation and makes testing harder.

**Problem 5 — `@Transactional` on `final` methods:**
Even with `allopen` on the class, if a method inside is called from within the same class (self-invocation), the proxy is bypassed. This is a Spring AOP limitation, not Kotlin-specific, but easier to hit in Kotlin.

**Interview trap:** "You added `@Transactional` to a Kotlin service but transactions aren't rolling back. What do you check?" — (1) Is the class `open` (or `kotlin-spring` plugin active)? (2) Is the method being called from outside the proxy (not self-invocation)? (3) Is the exception unchecked (Kotlin has no checked exceptions, but Spring's default rollback is for `RuntimeException`)?

**Tags:** spring-boot, kotlin, jpa, jackson, configuration, senior

---

## Q-KT-021 [bloom: analyze] [level: senior]

**Question:** A colleague proposes using `!!` (not-null assertion) throughout a Kotlin codebase to "deal with nullability faster." What are the real-world consequences and what patterns should replace it?

**Model answer:** `!!` is the ejector seat: it compiles but throws `KotlinNullPointerException` at runtime if the value is null. Using it liberally defeats the entire purpose of Kotlin's null safety — you get Java-style NPEs but with Kotlin syntax.

**Consequences:**
- Stack traces point to the `!!` line, not the source of null — debugging becomes harder
- The codebase develops a false sense of safety (it compiles!) while hiding bugs
- In production, `!!` crashes on data the developer assumed was always non-null
- Code reviews have no way to distinguish "this is provably non-null" from "I was lazy"

**Legitimate uses for `!!`** (rare, documented):
1. At truly validated system boundaries where null is a bug and you want immediate loud failure: `val env = System.getenv("DATABASE_URL")!!` — if missing, fail fast at startup, not silently later.
2. After external validation not visible to the compiler (e.g., after a framework method that guarantees non-null but isn't annotated).
Always add a comment explaining why `!!` is safe here.

**Replacement patterns:**

| Scenario | Instead of `!!` | Use |
|---|---|---|
| Provide default | `name!!` | `name ?: "default"` |
| Skip if null | `process(user!!)` | `user?.let { process(it) }` |
| Throw with context | `user!!.name` | `user ?: error("User not found for id=$id")` |
| lateinit for DI | `lateinit var` + `!!` | constructor injection with `val` |
| Smart cast | `if (x != null) x!!.foo` | `if (x != null) x.foo` |

`error(message)` is idiomatic Kotlin — it throws `IllegalStateException` with a descriptive message.

**Interview trap:** "Is there a case where `!!` is preferable to smart cast?" — Yes: when the variable is a `var` or an open property and the compiler can't smart cast (another thread could change it). In that case, assign to a local `val` first (`val local = this.varProp`) and use the local, rather than using `!!` on the original.

**Tags:** null-safety, code-quality, best-practices, senior

---

## Q-KT-022 [bloom: analyze] [level: senior]

**Question:** Explain how Kotlin's type system handles `Nothing` in practice. Give examples where `Nothing` is load-bearing in sealed class hierarchies and in expression typing.

**Model answer:** `Nothing` is the **bottom type** of Kotlin's type hierarchy — it is a subtype of every other type. A function returning `Nothing` never completes normally.

**In sealed class hierarchies:** error subtypes in a `Result<out T>` sealed class carry no `T` value. Using `Nothing` for `T` means they can be used anywhere a `Result<T>` is expected for any `T`.

```kotlin
sealed class Result<out T>
data class Success<T>(val data: T)   : Result<T>()
data class Failure(val error: Throwable) : Result<Nothing>()  // Nothing here
object Loading : Result<Nothing>()

val r: Result<User> = Failure(RuntimeException("oops"))
// legal because Result<Nothing> <: Result<User> (covariant + Nothing <: User)
```

**In expression typing:** `throw` is an expression of type `Nothing`. This makes it usable in `when` branches, Elvis, and ternary-style expressions without breaking type inference.

```kotlin
val port = config["port"]?.toInt() ?: throw IllegalStateException("port required")
// ?: requires both sides to have compatible types
// right side is Nothing, which is a subtype of Int — so the whole expression is Int
```

```kotlin
fun requireUser(id: Long): User =
    userRepo.findById(id) ?: error("No user for id=$id")
// error() returns Nothing — compatible with User return type
```

**`TODO()`:** returns `Nothing` — lets code compile as a stub while explicitly marking incompleteness.

```kotlin
fun computeRisk(order: Order): RiskScore = TODO("implement after design review")
```

**Interview trap:** "Can you instantiate `Nothing`?" — No. `Nothing` has no instances — it's impossible to construct a value of type `Nothing`. Its only role is in the type system for subtyping and expression typing. A function returning `Nothing` must diverge (throw or loop infinitely).

**Tags:** nothing, type-system, sealed-class, variance, analyze, senior

---

## Q-KT-023 [bloom: analyze] [level: master]

**Question:** Describe exactly what bytecode a Kotlin `inline` function with a lambda parameter produces, and explain the implications for `crossinline`, `noinline`, and non-local returns.

**Model answer:** 

**Without `inline`:** each lambda is compiled to an anonymous class implementing `Function0`/`Function1`/etc. Each call allocates a lambda object and invokes a virtual method. For hot paths (e.g., `forEach` over a large list), this generates GC pressure.

**With `inline`:** the compiler copies the body of the inline function AND the body of any lambda arguments into every call site. No lambda object is created. No virtual dispatch.

```kotlin
inline fun withLock(lock: Lock, block: () -> Unit) {
    lock.lock()
    try { block() } finally { lock.unlock() }
}
// Call site expands to:
// lock.lock(); try { /* block body */ } finally { lock.unlock() }
```

**Non-local returns:** because the lambda is inlined into the calling function's body, a bare `return` inside the lambda returns from the calling function (not just the lambda). This is only legal in inline functions — the compiler can verify the lambda is inlined.

```kotlin
inline fun forEach(list: List<Int>, action: (Int) -> Unit) { ... }
fun find(list: List<Int>, target: Int): Boolean {
    forEach(list) {
        if (it == target) return true  // returns from find(), not just lambda
    }
    return false
}
```

**`crossinline`:** marks a lambda parameter that is inlined but called in a different execution context (e.g., passed to another thread or coroutine). Disables non-local returns from that lambda because the calling function may have already returned by then.

```kotlin
inline fun async(crossinline block: () -> Unit) {
    Thread { block() }.start()
    // block runs after async() returns — non-local return would be meaningless
}
```

**`noinline`:** opt a specific lambda out of inlining. Used when you need to store the lambda in a variable, pass it to another function that expects `Function<T>`, or when the lambda is too large to inline efficiently.

```kotlin
inline fun compose(a: () -> Unit, noinline b: () -> Unit): () -> Unit {
    a()
    return b  // can't return an inlined lambda — must be noinline
}
```

**Bytecode implication:** excessive inlining of large functions bloats class files. JVM JIT has its own inlining budget; aggressive Kotlin inlining can interfere with JIT optimization. Keep inline function bodies small.

**Interview trap:** "If `inline` eliminates lambda allocation, why not inline every function?" — (1) Code bloat: each call site gets a copy. (2) The function can't be called from Java (no lambda inlining). (3) Internal implementation details become part of the public ABI (callers are compiled against the body). (4) Large inline functions may degrade JIT optimization.

**Tags:** inline, reified, bytecode, crossinline, noinline, non-local-return, master

---

## Q-KT-024 [bloom: analyze] [level: master]

**Question:** You're designing a library API in Kotlin that will be consumed by both Kotlin and Java clients. Walk through every design decision you need to make to ensure the API is idiomatic from both languages.

**Model answer:** This is a classic Kotlin-as-public-API problem. Key considerations:

**1. Nullability at boundaries:**
- Annotate every parameter and return type with `@Nullable`/`@NotNull` (or JSR-305 / JetBrains annotations). Without them, Kotlin clients get platform types; Java clients get no documentation.
- Prefer non-nullable parameters + documented preconditions over nullable parameters in public API.

**2. Default parameters → `@JvmOverloads`:**
```kotlin
@JvmOverloads
fun connect(host: String, port: Int = 8080, ssl: Boolean = false): Connection
```
Java clients can't use default values — they see only one overload. `@JvmOverloads` generates overloads for each combination.

**3. Companion object / top-level → `@JvmStatic` / organize in classes:**
```kotlin
class ConnectionFactory {
    companion object {
        @JvmStatic fun create(config: Config): ConnectionFactory = ...
    }
}
```
Without `@JvmStatic`, Java must call `ConnectionFactory.Companion.create(config)`.

**4. Properties → Java getter/setter naming:**
Kotlin `var name: String` generates `getName()`/`setName()`. Boolean properties: `val isActive: Boolean` generates `isActive()` — matches Java convention. `val active: Boolean` generates `getActive()` — Java clients expect `isActive()`. Prefer `isX` naming for booleans.

**5. Extension functions — invisible from Java:**
Extensions compile to static methods in `<Filename>Kt` class. Java calls `StringExtensionsKt.isPalindrome(str)` — ugly. For Java-facing APIs, put methods in the class instead. Use extensions only for internal Kotlin code or clearly documented Kotlin-only API.

**6. Sealed classes — Java `when` equivalent:**
Java has no exhaustive pattern matching on sealed classes (until Java 21). Document that Java clients must handle `else` cases. Consider providing a `fold`/`match` method on the sealed class:
```kotlin
fun <R> fold(onSuccess: (T) -> R, onFailure: (Throwable) -> R): R = when (this) {
    is Success -> onSuccess(data)
    is Failure -> onFailure(error)
}
```

**7. Coroutines — Java callers can't call `suspend` functions directly:**
Wrap coroutine APIs in `CompletableFuture` for Java clients using `future { ... }` from kotlinx-coroutines-jdk8.

**8. Collections — use Kotlin stdlib but return `List` not `MutableList`:**
Return immutable interfaces (`List`, `Map`, `Set`) unless mutation is intended. Java clients handle these fine.

**9. `@JvmField` for constants:**
```kotlin
companion object {
    @JvmField val DEFAULT = Config()
    const val VERSION = "1.0"  // const val is always accessible as field from Java
}
```

**Interview trap:** "What happens when a Java client calls a Kotlin function that has a `Unit` return type?" — It compiles but returns `void` from Java's perspective. `Unit` is represented as `void` in the JVM type descriptor when used as a return type. Java clients don't need to handle the `Unit` value.

**Tags:** java-interop, api-design, kotlin, master

---

## Q-KT-025 [bloom: analyze] [level: master]

**Question:** Explain structural equality vs referential equality in Kotlin. How do `==`, `===`, `equals()`, `hashCode()`, and `copy()` interact, and what are the edge cases with data classes, inheritance, and `null`?

**Model answer:** 

**`==` (structural equality):** compiles to `a?.equals(b) ?: (b === null)`. Calls `equals()` — for data classes, this compares all primary constructor properties. Null-safe by design: `null == null` is `true`, `"foo" == null` is `false`.

**`===` (referential equality):** compares object identity — same memory address. Equivalent to Java `==`.

```kotlin
val a = User(1, "Alice")
val b = User(1, "Alice")
val c = a
println(a == b)   // true  — structural equality (data class equals)
println(a === b)  // false — different objects
println(a === c)  // true  — same reference
```

**`copy()` and identity:**
```kotlin
val copy = a.copy()
println(a == copy)   // true  — same field values
println(a === copy)  // false — new object
```

**Inheritance and `equals` contract:**
Data class `equals()` checks: (1) same runtime type (using `javaClass`), (2) all primary constructor properties equal. If `class VipUser(id, name, tier) : User(id, name)` — this breaks the `equals` contract (symmetric + transitive axioms fail when mixing `User` and `VipUser` comparisons). The Kotlin compiler warns: data classes should not be extended. Prefer sealed classes or composition.

**`hashCode` contract:** if `a == b` then `a.hashCode() == b.hashCode()`. Data classes satisfy this because `hashCode` is generated from the same properties as `equals`. Breaking this (e.g., overriding one but not the other) causes silent bugs in `HashMap`/`HashSet`: equal objects map to different buckets and aren't found.

**Mutable data class in a Set:**
```kotlin
data class Tag(var name: String)  // mutable property — danger!
val set = mutableSetOf(Tag("kotlin"))
val tag = set.first()
tag.name = "java"  // mutates hash — tag is now "lost" in the set
println(set.contains(tag))  // false! hashCode changed, set can't find it
```

**`null` behavior:**
- `null == null` → `true`
- `null == "foo"` → `false`
- `"foo" == null` → `false`
- `"foo"?.equals(null)` → `false`

**Interview trap:** "Two data class objects with the same values but one has `var` properties and one has `val` properties — are they equal?" — Yes, `equals()` checks the values at comparison time, not mutability. But the `var` version is dangerous in collections as shown above.

**Tags:** equals, hashCode, structural-equality, referential-equality, data-class, master

---

## Q-KT-026 [bloom: analyze] [level: master]

**Question:** Compare Kotlin's `object` singleton initialization with Java's class-holder singleton. What are the thread-safety guarantees, and when could an `object` initialization fail silently?

**Model answer:** 

**Kotlin `object` initialization:**
```kotlin
object DatabasePool {
    val pool = createPool()  // runs once
}
```
Compiles to a Java class with a `INSTANCE` field and a static initializer block:
```java
public final class DatabasePool {
    public static final DatabasePool INSTANCE;
    static {
        INSTANCE = new DatabasePool();
        INSTANCE.pool = DatabasePool.createPool();
    }
}
```
Thread safety is guaranteed by the JVM class loader: static initializers run once and are synchronized by the class loading mechanism. This is the equivalent of Java's **initialization-on-demand holder** pattern.

**Failure modes:**

1. **Exception during initialization:** if `createPool()` throws, the JVM catches the exception, the class is marked as failed, and subsequent accesses throw `NoClassDefFoundError` (not the original exception). This is a catastrophic silent failure — the original cause is lost after the first attempt.

```kotlin
object Config {
    val value: Int = System.getenv("PORT")!!.toInt()  // throws if PORT is missing
}
// First access: ExceptionInInitializerError (wrapping NPE/NumberFormatException)
// Subsequent accesses: NoClassDefFoundError — original cause gone
```

2. **Circular initialization:** if two `object` declarations initialize each other, the JVM delivers a partially-initialized state. Very hard to debug.

3. **`lazy` inside `object`:** `val heavyResource by lazy { ... }` defers initialization but `lazy` uses `SYNCHRONIZED` mode by default — safe. Using `LazyThreadSafetyMode.NONE` inside a singleton accessed from multiple threads is a bug.

**Best practice for Spring beans:** don't use `object` for stateful Spring beans. Let Spring manage lifecycle (`@Bean`, `@Component`). Use `object` only for stateless utilities or compile-time constants.

**Interview trap:** "After `object` initialization throws, does retrying access reinitialize it?" — No. The JVM marks the class as permanently failed after a failed static initializer. All subsequent access throws `NoClassDefFoundError`. The only way to recover is to restart the JVM.

**Tags:** object, singleton, thread-safety, jvm, initialization, master

---

## Q-KT-027 [bloom: apply] [level: senior]

**Question:** Model a domain event hierarchy for an order processing system using sealed interfaces (Kotlin 1.5+). Include at least 4 event types with different payloads. Demonstrate exhaustive `when` and covariance.

**Model answer:**
```kotlin
import java.math.BigDecimal
import java.time.Instant

sealed interface OrderEvent {
    val orderId: Long
    val occurredAt: Instant
}

data class OrderPlaced(
    override val orderId: Long,
    override val occurredAt: Instant,
    val customerId: Long,
    val totalAmount: BigDecimal
) : OrderEvent

data class OrderPaid(
    override val orderId: Long,
    override val occurredAt: Instant,
    val paymentId: String,
    val amount: BigDecimal
) : OrderEvent

data class OrderShipped(
    override val orderId: Long,
    override val occurredAt: Instant,
    val trackingNumber: String,
    val carrier: String
) : OrderEvent

data class OrderCancelled(
    override val orderId: Long,
    override val occurredAt: Instant,
    val reason: String,
    val cancelledBy: String
) : OrderEvent

// Exhaustive when — no else needed, compiler checks all cases
fun toAuditMessage(event: OrderEvent): String = when (event) {
    is OrderPlaced    -> "Order ${event.orderId} placed by customer ${event.customerId} for ${event.totalAmount}"
    is OrderPaid      -> "Order ${event.orderId} paid via ${event.paymentId}"
    is OrderShipped   -> "Order ${event.orderId} shipped via ${event.carrier} (${event.trackingNumber})"
    is OrderCancelled -> "Order ${event.orderId} cancelled by ${event.cancelledBy}: ${event.reason}"
}

// Covariant container — works because sealed interface allows out variance
fun latestEvent(events: List<OrderEvent>): OrderEvent? =
    events.maxByOrNull { it.occurredAt }
```

**Sealed interface advantages over sealed class (Kotlin 1.5+):**
- A class can implement multiple sealed interfaces (vs single sealed class inheritance)
- Interfaces can't hold state — cleaner for event/command types
- Works well with `data class` (which already extends one class implicitly)

**Interview trap:** "What breaks if you add a new event type `OrderReturned` without updating the `when` expression?" — With a sealed interface, the `when` used as an expression (assigning its result) becomes a **compile error** — non-exhaustive. `when` used as a statement (no assignment) only warns. Always use `when` as an expression for exhaustiveness checking.

**Tags:** sealed-interface, when, domain-modeling, events, senior

---

## Q-KT-028 [bloom: apply] [level: regular]

**Question:** What is the difference between Kotlin's `List` and `MutableList`? How does this relate to Kotlin's collection mutability model, and what's the gotcha when passing Kotlin collections to Java?

**Model answer:** Kotlin's collection interfaces have a strict split:

- **`List<T>`** — read-only view: `size`, `get()`, `contains()`, `iterator()`. No add/remove.
- **`MutableList<T>`** — extends `List<T>` with mutation: `add()`, `remove()`, `set()`, `clear()`.
- **`ArrayList<T>`** — the standard implementation implementing `MutableList<T>`.

At runtime, `listOf(1,2,3)` returns an `ArrayList` wrapped in a read-only `List` interface. There is no separate "truly immutable" class — it's an interface contract, not a deep copy or immutable wrapper.

```kotlin
val readOnly: List<Int> = mutableListOf(1, 2, 3)
val mutable = readOnly as MutableList<Int>  // compiles and works at runtime!
mutable.add(4)  // readOnly now has 4 elements — no error
```

**The Java gotcha:** Java has no concept of Kotlin's read-only interfaces. When you pass a `List<Int>` to Java, Java sees `java.util.List` — which has `add()`, `remove()`, etc. Java can mutate it. The Kotlin read-only contract is enforced only by the Kotlin compiler at compile time.

```kotlin
// Kotlin
fun processInJava(list: List<String>) = JavaService.process(list)
// Java
public static void process(List<String> list) { list.add("oops"); }  // works!
```

To pass truly immutable collections to Java: use `Collections.unmodifiableList()` or Guava's `ImmutableList`.

**Interview trap:** "Is `listOf()` return value truly immutable?" — No. It's a read-only view of a (usually) mutable backing structure. You can cast it to `MutableList` and mutate it. True immutability requires `Collections.unmodifiableList()` or a library like Guava/kotlinx-collections-immutable.

**Tags:** collections, immutable, mutable-list, java-interop, regular

---

## Q-KT-029 [bloom: recall] [level: junior]

**Question:** What are default and named arguments in Kotlin? Give an example and explain why they matter for Java interop.

**Model answer:** Kotlin functions can declare default values for parameters, eliminating the need for multiple overloads. Callers may omit any parameter that has a default.

```kotlin
fun connect(host: String, port: Int = 8080, ssl: Boolean = false): Connection {
    // ...
}

connect("localhost")                  // uses defaults: port=8080, ssl=false
connect("localhost", port = 443, ssl = true)  // named args — order doesn't matter
connect("localhost", ssl = true)     // skip port, use its default
```

**Named arguments** let callers specify which parameter they are setting by name, improving readability when a function has several parameters of the same type or many boolean flags.

**Java interop:** Java does not support default arguments or named calls. A Java caller must supply all arguments. To expose a convenient API to Java, annotate the Kotlin function with `@JvmOverloads`, which generates a Java-visible overloaded method for each default parameter combination.

```kotlin
@JvmOverloads
fun connect(host: String, port: Int = 8080, ssl: Boolean = false): Connection { ... }
// Java sees: connect(host), connect(host, port), connect(host, port, ssl)
```

**Interview trap:** "Does `@JvmOverloads` generate every possible combination of defaults?" — No. It generates overloads from left to right, dropping trailing defaulted parameters one at a time. It generates N overloads for N default parameters, always keeping the prefix of non-defaulted arguments.

**Tags:** default-arguments, named-arguments, java-interop, basics

---

## Q-KT-030 [bloom: recall] [level: junior]

**Question:** What is a `suspend` function in Kotlin? What makes it different from a regular function and where can you call it?

**Model answer:** A `suspend` function is a function that can be paused and resumed without blocking a thread. The `suspend` modifier is a compiler instruction — it transforms the function's bytecode using continuation-passing style (CPS), inserting a hidden `Continuation` parameter that carries the resumed state.

```kotlin
suspend fun fetchUser(id: Long): User {
    delay(100)            // suspends — releases the thread without blocking
    return userRepo.findById(id)
}
```

**Key difference from a regular function:**
- A regular function, when it calls `Thread.sleep()` or blocks on I/O, holds the OS thread for the entire wait.
- A `suspend` function can yield the thread back to the thread pool while waiting, then resume on the same or a different thread when the work is ready.

**Where you can call it:**
- Inside another `suspend` function
- Inside a coroutine builder: `launch { ... }`, `async { ... }`, `runBlocking { ... }`
- You CANNOT call a `suspend` function from regular (non-suspend) code directly — the compiler will reject it.

```kotlin
// This compiles only inside a suspend or coroutine:
val user = fetchUser(1L)

// From regular code, you need a builder:
runBlocking { val user = fetchUser(1L) }  // for tests/main only
```

**Interview trap:** "Does `suspend` make a function run on a background thread automatically?" — No. `suspend` only marks that the function may pause. The thread it runs on is determined by the `CoroutineDispatcher` of the coroutine scope (e.g., `Dispatchers.IO` for I/O, `Dispatchers.Default` for CPU). A `suspend` function called in a scope using `Dispatchers.Main` still runs on the main thread between suspension points.

**Tags:** coroutines, suspend, basics

---
