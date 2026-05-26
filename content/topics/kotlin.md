# Kotlin — question bank

> Context: Java developer transitioning to Kotlin for backend microservices. Focus on idiomatic Kotlin, Java interop, null safety, coroutines, and patterns used in Spring Boot + Kotlin projects.

## Scope

- `val` vs `var`, type inference
- Null safety: `?`, `!!`, `?.`, `?:`, `let`/`also`/`run`/`apply`/`with`
- Data classes, sealed classes, enum classes
- Extension functions and properties
- `when` expression
- Higher-order functions, lambdas, `it`
- Companion objects, `object` declarations
- Coroutines: `suspend`, `launch`, `async`, `Dispatchers`, `Flow`
- Java interop: `@JvmStatic`, `@JvmOverloads`, `@JvmField`, platform types
- Kotlin + Spring Boot patterns

---

## Q-KT-001 [bloom: recall]
**Question:** What is the difference between `val` and `var` in Kotlin?
**Model answer:** `val` declares a read-only reference — once assigned, cannot be reassigned (like Java `final`). `var` is a mutable variable. Note: `val` does NOT mean the object is immutable — a `val list: MutableList<Int>` can still be mutated, you just can't point `list` at a different object. Under the hood `val` compiles to a `final` field (or local without setter). Best practice: prefer `val` by default, use `var` only when mutation is necessary. In Spring beans, prefer `val` for injected dependencies.
**Interview trap:** "Is `val` the same as immutable?" — No. `val` = immutable reference, not immutable object.
**Tags:** syntax, basics

## Q-KT-002 [bloom: recall]
**Question:** What is Kotlin's null safety system? Name the 4 key operators.
**Model answer:** Kotlin's type system distinguishes nullable (`String?`) from non-nullable (`String`) types at compile time — a `String` variable can never hold `null`, the compiler enforces this. The 4 key operators: 1) `?.` safe call — returns null instead of NPE if receiver is null (`user?.address?.city`). 2) `?:` Elvis operator — provides default if expression is null (`name ?: "unknown"`). 3) `!!` not-null assertion — throws `NullPointerException` if null (use sparingly, only when you're certain). 4) `let` — executes a block only if non-null: `user?.let { sendEmail(it) }`. Smart cast: after `if (x != null)` the compiler treats `x` as non-null inside the block.
**Interview trap:** "When to use `!!`?" — Only at system boundaries where you've already validated externally and the type system can't know. Never use it to "shut up the compiler."
**Tags:** null-safety, basics

## Q-KT-003 [bloom: recall]
**Question:** What is a data class in Kotlin? What does the compiler auto-generate?
**Model answer:** A data class (`data class User(val id: Long, val name: String)`) is a class whose primary purpose is holding data. The compiler auto-generates: 1) `equals()` and `hashCode()` based on all primary constructor properties. 2) `toString()` in format `User(id=1, name=John)`. 3) `copy()` — creates a copy with optional field overrides: `user.copy(name = "Jane")`. 4) `componentN()` functions enabling destructuring: `val (id, name) = user`. Does NOT generate: mutable setters (unless `var`), `clone()`, or thread safety. Restriction: must have at least one primary constructor parameter; body properties are excluded from generated methods.
**Interview trap:** "What's excluded from `equals/hashCode`?" — Properties declared in the class body (not the primary constructor). `data class Foo(val x: Int) { val y: Int = 0 }` — `y` is ignored in equals.
**Tags:** data-class, basics

## Q-KT-004 [bloom: recall]
**Question:** What is an extension function? Give an example.
**Model answer:** An extension function lets you add methods to an existing class without inheriting from it or modifying its source. Syntax: `fun ReceiverType.methodName(params): ReturnType { ... }`. Example:
```kotlin
fun String.isPalindrome(): Boolean = this == this.reversed()
"racecar".isPalindrome() // true
```
Under the hood it compiles to a static method with the receiver as the first parameter — no runtime overhead, no dynamic dispatch. Extension functions don't have access to private members of the class. Priority: member functions always win over extension functions with same signature.
**Interview trap:** "Can you override an extension function polymorphically?" — No. Extensions are resolved statically (at compile time), not virtually. `fun Animal.speak()` called on a `Dog` reference typed as `Animal` calls `Animal.speak()`, not `Dog.speak()`.
**Tags:** extension-functions, basics

## Q-KT-005 [bloom: recall]
**Question:** What is a sealed class and when do you use it?
**Model answer:** A sealed class restricts the class hierarchy — all subclasses must be defined in the same file (or same package in Kotlin 1.5+). This gives `when` expressions exhaustiveness: the compiler knows all possible subtypes. Example:
```kotlin
sealed class Result<out T>
data class Success<T>(val data: T) : Result<T>()
data class Error(val message: String) : Result<Nothing>()
object Loading : Result<Nothing>()
```
Then `when (result) { is Success -> ... is Error -> ... is Loading -> ... }` needs no `else` clause — compiler guarantees coverage. Use when: modeling state machines, API responses, error types, command/event hierarchies. Stronger than `enum` because subclasses can carry different data.
**Interview trap:** "Difference from enum?" — Enum instances are singletons with same shape; sealed class subclasses can be full classes with different fields and multiple instances.
**Tags:** sealed-class, type-system

## Q-KT-006 [bloom: understand]
**Question:** Explain the `object` keyword in Kotlin — what are its 3 uses?
**Model answer:** 1) **Object declaration** — singleton. `object Config { val timeout = 30 }` creates a thread-safe lazy-initialized singleton. Accessed as `Config.timeout`. 2) **Companion object** — static-like members inside a class. `companion object { fun create(): MyClass = MyClass() }` — called as `MyClass.create()`. Can implement interfaces, can be named. `@JvmStatic` makes it appear as a true Java static method. 3) **Object expression** (anonymous object) — anonymous class instantiation, replacing Java's anonymous inner classes: `val listener = object : ClickListener { override fun onClick() { ... } }`. Thread safety: object declarations use double-checked locking under the hood, so the singleton is safe.
**Interview trap:** "`companion object` vs top-level functions?" — Top-level functions (defined outside any class) are preferred for utility functions; companion object is for factory methods or constants tightly coupled to the class.
**Tags:** object, companion, singleton

## Q-KT-007 [bloom: understand]
**Question:** What are Kotlin coroutines? What is the difference between `launch` and `async`?
**Model answer:** Coroutines are Kotlin's answer to concurrency — lightweight threads managed by the runtime, not the OS. They're suspended (not blocked) when waiting, releasing the thread for other work. Core: `suspend` functions can be paused and resumed. `CoroutineScope` defines the lifecycle. `Dispatchers` determine which thread pool runs the coroutine (`IO`, `Default`, `Main`). `launch` vs `async`: `launch` — fire-and-forget, returns a `Job`, result is unit. Use for side effects. `async` — returns a `Deferred<T>`, you call `.await()` to get the result. Use when you need the return value or want to run parallel computations and collect results. Structured concurrency: coroutines launched inside a scope are cancelled when the scope is cancelled — no orphan coroutines.
**Interview trap:** "What's the difference from RxJava/threads?" — Coroutines are sequential by default (readable top-to-bottom), don't block threads, lighter weight (thousands vs hundreds of threads), and structured concurrency prevents leaks.
**Tags:** coroutines, async, concurrency

## Q-KT-008 [bloom: understand]
**Question:** How does Kotlin interoperate with Java? What annotations help bridge the two?
**Model answer:** Kotlin compiles to JVM bytecode — any Kotlin class is usable from Java and vice versa. Common friction points and solutions: 1) `@JvmStatic` on companion object methods — makes them a true Java static method, callable as `MyClass.method()` from Java (without `Companion`). 2) `@JvmOverloads` — generates Java overloaded methods for functions with default parameters, so Java callers don't have to pass all args. 3) `@JvmField` — exposes a Kotlin property as a public field instead of getter/setter. 4) `@Throws(IOException::class)` — declares checked exceptions for Java callers (Kotlin has no checked exceptions). 5) Platform types: when calling Java code that returns a potentially-null value, Kotlin represents it as `String!` (platform type) — neither nullable nor non-null, you take responsibility. Best practice: annotate Java code with `@NonNull`/`@Nullable` so Kotlin can infer correctly.
**Interview trap:** "What is a platform type and why is it dangerous?" — It's a type the compiler can't determine nullability for. Accessing it without null check can produce NPE at runtime, bypassing Kotlin's null safety.
**Tags:** java-interop, annotations

## Q-KT-009 [bloom: apply]
**Question:** Write a Kotlin function that takes a list of `Order` objects (each with `customerId: Long`, `total: BigDecimal`, `status: String`) and returns a `Map<Long, BigDecimal>` of total spend per customer, for orders with status `"COMPLETED"` only.
**Model answer:**
```kotlin
data class Order(val customerId: Long, val total: BigDecimal, val status: String)

fun totalSpendByCustomer(orders: List<Order>): Map<Long, BigDecimal> =
    orders
        .filter { it.status == "COMPLETED" }
        .groupBy { it.customerId }
        .mapValues { (_, orders) -> orders.fold(BigDecimal.ZERO) { acc, o -> acc + o.total } }
```
Step by step: `filter` removes non-completed. `groupBy` creates `Map<Long, List<Order>>`. `mapValues` transforms each list to a sum using `fold` with `BigDecimal.ZERO` as initial accumulator. Alternative using `sumOf`: `.mapValues { (_, o) -> o.sumOf { it.total } }` (available in Kotlin stdlib). Always use `BigDecimal` for monetary values, never `Double`.
**Interview trap:** "Why not `sum()`?" — `sum()` works for `Int`/`Long`/`Double` but not `BigDecimal`. Use `sumOf { it.total }` (Kotlin 1.4+) or `fold`.
**Tags:** collections, fp, ecommerce

## Q-KT-010 [bloom: apply]
**Question:** Model an API response using a sealed class hierarchy: Success with data, ApiError with HTTP status code and message, NetworkError with the underlying exception. Write a function that processes the result and returns a String summary.
**Model answer:**
```kotlin
sealed class ApiResult<out T>
data class Success<T>(val data: T) : ApiResult<T>()
data class ApiError(val statusCode: Int, val message: String) : ApiResult<Nothing>()
data class NetworkError(val cause: Throwable) : ApiResult<Nothing>()

fun <T> summarize(result: ApiResult<T>): String = when (result) {
    is Success -> "OK: ${result.data}"
    is ApiError -> "HTTP ${result.statusCode}: ${result.message}"
    is NetworkError -> "Network failure: ${result.cause.message}"
}
```
No `else` needed — sealed class exhaustiveness. `out T` makes `ApiResult` covariant, so `ApiResult<String>` is a subtype of `ApiResult<Any>`. `Nothing` for error cases signals they can never produce a value of T.
**Interview trap:** "Why `Nothing` in error subtypes?" — Because they carry no `T` data. `Nothing` is the bottom type — a subtype of everything — so `ApiError` is assignable to `ApiResult<String>`, `ApiResult<Int>`, etc.
**Tags:** sealed-class, generics, ecommerce

## Q-KT-011 [bloom: analyze]
**Question:** Your team is migrating a Java Spring Boot service to Kotlin. A colleague proposes rewriting everything at once. What's your approach, and what Kotlin-specific pitfalls in Spring Boot should you warn about?
**Model answer:** **Incremental migration:** Kotlin files compile alongside Java in the same project — no "big bang" required. Add Kotlin plugin to Gradle, start writing new classes in Kotlin, keep existing Java untouched. Migrate class by class when touching them. **Spring Boot pitfalls:** 1) `@Configuration` classes — Kotlin's `class` is `final` by default, Spring needs to proxy (subclass) `@Configuration` beans. Fix: `open class` or use `kotlin-spring` Gradle plugin which auto-opens annotated classes. 2) `@Autowired` constructor injection: in Kotlin prefer constructor injection with `val` — no `lateinit var`. 3) JPA entities require `open` (for lazy loading proxies) and no-arg constructor — use `kotlin-jpa` plugin. 4) Jackson + data classes: needs `jackson-module-kotlin` registered, otherwise deserialization fails. 5) `lateinit var` for field injection — avoid, use constructor injection. 6) Default method parameters don't survive Java interop without `@JvmOverloads`. **When to prefer Kotlin:** new services, greenfield code, existing code touched for bug fixes. Don't rewrite stable, tested Java just to have Kotlin.
**Interview trap:** "Why does Spring fail to proxy a Kotlin class?" — All Kotlin classes are `final` by default (unlike Java). CGLIB proxies require subclassing, which `final` blocks. The `kotlin-spring` plugin (`allopen`) fixes this automatically for `@Component`, `@Service`, etc.
**Tags:** spring-boot, migration, kotlin, architecture

## Q-KT-012 [bloom: analyze]
**Question:** When would you use Kotlin `Flow` vs `suspend fun` returning a single value for a backend service endpoint?
**Model answer:** **`suspend fun` returning T:** use for single-value async operations — database lookup, HTTP call, computation that returns one result. It's the Kotlin equivalent of `CompletableFuture<T>` but without the callback hell. `suspend fun getUser(id: Long): User`. **`Flow<T>`:** use for streams of values emitted over time — paginated results, SSE (Server-Sent Events), database change streams, processing large datasets in chunks without loading all into memory. `Flow` is cold (only runs when collected), supports backpressure, and is cancellable. **Backend examples:** `suspend fun` → single order lookup, single price calculation. `Flow` → streaming a batch report row-by-row, real-time order status updates via SSE, consuming a Kafka topic. **Spring WebFlux + Kotlin:** `Flow<T>` maps to Reactor `Flux<T>`; `suspend fun` maps to `Mono<T>` automatically via Spring's Kotlin coroutine integration. **Trade-off:** `Flow` adds complexity (operator chains, cold/hot semantics, backpressure); don't use it if you just need one value.
**Interview trap:** "Is Flow hot or cold?" — Cold by default (like Sequence, nothing runs until collected). `SharedFlow`/`StateFlow` are hot.
**Tags:** coroutines, flow, reactive, architecture
