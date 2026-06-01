> Pre-built topic bank. Your /onboard will generate personalized content for your target role.

# Coroutines + WebFlux — bank pytań

> Kotlin coroutines and Spring WebFlux are the two pillars of Allegro's high-RPS backend stack. This bank assumes you know blocking Spring MVC well and are learning the reactive + coroutine model from scratch. Every question targets the daily codebase of a recommendations-system developer: suspend functions at the HTTP edge, parallel feature fetching with async/await, streaming with Flow, WebClient configuration, and the threading model that makes 40ms p99 budgets achievable. Coverage spans definition (recall) through trade-off reasoning at scale (analyze).

## Zakres

- Coroutine fundamentals: definition, suspend, launch vs async, structured concurrency
- CoroutineScope, Job, SupervisorJob, Dispatchers
- Flow: cold vs hot streams, backpressure, SharedFlow/StateFlow
- withContext, cancellation, CancellationException
- Spring WebFlux: Reactor model, Mono/Flux, event loop
- Coroutines + WebFlux integration: awaitSingle, asFlow, mono {}, flux {}
- Why blocking inside a suspend function is a major bug and how to fix it
- Threading model under WebFlux + coroutines
- WebClient: timeouts, retry, idiomatic Kotlin configuration
- Testing suspend functions with virtual time
- Trade-off analysis: Loom vs WebFlux, error semantics, async vs launch

---

## Q-CW-001 [bloom: recall]
**Pytanie:** What is a coroutine? Define it in terms of a lightweight thread and explain what "suspension" means at the runtime level.

**Modelowa odpowiedź:**
A coroutine is a unit of concurrent work that can be suspended and resumed without blocking an OS thread. Unlike a thread (which occupies a native OS thread for its entire lifetime), a coroutine releases its thread when it suspends — the thread is free to execute other coroutines while this one waits.

"Suspension" means the coroutine saves its local variables and the current execution point (the continuation) onto the heap, hands the thread back to the dispatcher, and later gets resumed — potentially on a different thread — by the dispatcher when the awaited work is done.

Concrete example: two coroutines, one thread:
```
Thread-1: runs coroutine A → A calls delay(1000) → A suspends, saves state to heap
Thread-1: now runs coroutine B → B finishes → 1000ms elapsed
Thread-1: resumes coroutine A from where it left off
```
The OS thread was never blocked. From a developer's perspective the code reads sequentially (no callbacks), but at runtime it is fully non-blocking.

Key numbers: a coroutine takes ~a few hundred bytes on the heap vs ~1 MB stack per Java thread. You can launch millions of coroutines where launching a million threads would crash the JVM.

**Pułapka rozmowna:** "Aren't coroutines just virtual threads?" — No. Virtual threads (Loom) still block a carrier thread during blocking I/O (they just park the carrier, not the OS thread). Coroutines cooperatively suspend and hand the underlying thread back entirely. Coroutines also compose with structured concurrency semantics that virtual threads do not provide out of the box.

**Tagi:** coroutine-basics, suspension, threading

---

## Q-CW-002 [bloom: recall]
**Pytanie:** What does the `suspend` modifier on a function mean? What can a `suspend` function do that a regular function cannot, and where can it be called from?

**Modelowa odpowiedź:**
`suspend` marks a function as suspendable: it may pause execution without blocking the thread and resume later. The compiler transforms a `suspend fun` into a state machine that takes an implicit `Continuation<T>` parameter (the "what to do when I resume" callback). This is CPS (continuation-passing style) transformation — you don't write callbacks, the compiler generates them.

What it CAN do that a regular function cannot:
- Call other `suspend` functions directly.
- Call `delay()`, `await()`, channel operations — anything that suspends the coroutine.

What it CANNOT do differently from a regular function:
- It cannot be called from regular (non-suspend) code without a coroutine builder (`launch`, `async`, `runBlocking`).
- It does not automatically make blocking code non-blocking. A `suspend fun` that calls `Thread.sleep()` inside still blocks the thread.

```kotlin
// OK: called from coroutine
suspend fun fetchUser(id: Long): User = webClient.get("...").awaitBody()

// WRONG: calling from regular code
fun main() {
    fetchUser(1L) // compile error: suspend function called from non-suspend context
}

// CORRECT: use a builder
fun main() = runBlocking {
    fetchUser(1L)
}
```

**Pułapka rozmowna:** "Does `suspend` make my function non-blocking automatically?" — No. `suspend` is a capability marker. If the body calls blocking JDBC inside, it is still blocking. `suspend` just enables the compiler to transform the function into a state machine; what you put inside determines whether it actually suspends.

**Tagi:** suspend, coroutine-basics, CPS

---

## Q-CW-003 [bloom: recall]
**Pytanie:** What is the difference between `launch` and `async`? What do they return and when do you use each?

**Modelowa odpowiedź:**
Both are coroutine builders that start a new coroutine, but they differ in their return type and purpose:

| Builder | Returns | Use when |
|---------|---------|----------|
| `launch` | `Job` | fire-and-forget; you care about side effects, not a result |
| `async` | `Deferred<T>` | you need the result; call `.await()` to get it |

```kotlin
// launch: send a notification, don't wait
val job: Job = scope.launch {
    notificationService.send(userId)
}

// async: fetch two things in parallel, combine
val deferredUser: Deferred<User> = async { userService.find(id) }
val deferredPrice: Deferred<Price> = async { pricingService.get(id) }

val user = deferredUser.await()    // suspends until result ready
val price = deferredPrice.await()  // likely already done
```

With `async`, exceptions are stored in the `Deferred` and rethrown on `.await()`. With `launch`, an unhandled exception propagates to the parent scope immediately (unless a `CoroutineExceptionHandler` is installed).

Important: calling `async { }.await()` immediately is equivalent to just calling the suspend function directly — you gain nothing. The point of `async` is to start multiple coroutines before calling `await()`.

**Pułapka rozmowna:** "Can I ignore the return value of `async`?" — You can, but then you lose the exception. If the coroutine throws and nobody calls `.await()`, the exception is silently swallowed (in a `supervisorScope`) or crashes the parent (in a regular `coroutineScope`). Never fire `async` and discard the `Deferred` unless you have a specific reason.

**Tagi:** launch, async, coroutine-builders

---

## Q-CW-004 [bloom: recall]
**Pytanie:** What is structured concurrency? Define `coroutineScope { }` vs `supervisorScope { }` and explain why structured concurrency matters.

**Modelowa odpowiedź:**
Structured concurrency is the principle that every coroutine has a parent, and a parent does not complete until all its children complete. Coroutines form a tree of scopes; cancellation and exceptions propagate through this tree in predictable ways. You cannot "lose" a coroutine — if you started it in a scope, the scope tracks it.

`coroutineScope { }`:
- Suspends the caller until all child coroutines finish.
- If **any** child throws, the scope cancels all other children and rethrows.
- Use for: parallel decomposition where any failure means the whole operation should fail (e.g., parallel feature fetching for a recommendation — if one fails, you can't build the result).

```kotlin
suspend fun fetchRecommendation(userId: Long): Reco = coroutineScope {
    val features = async { featureStore.get(userId) }
    val candidates = async { annIndex.query(userId) }
    buildReco(features.await(), candidates.await()) // either fails → both cancel
}
```

`supervisorScope { }`:
- Children are independent; one child failing does NOT cancel siblings.
- Exceptions must be caught per-child (via `.await()` inside try/catch or a `CoroutineExceptionHandler`).
- Use for: independent background tasks, dashboards aggregating multiple optional data sources.

Why it matters: without structured concurrency you'd need to manually track every background job and cancel them on cleanup. With it, cancelling the outer scope automatically cancels everything nested inside — zero leaks.

**Pułapka rozmowna:** "Can I just use `GlobalScope.launch` everywhere to avoid scope boilerplate?" — This is the classic mistake. `GlobalScope` lives for the entire app lifetime, ignores structured concurrency, leaks coroutines on request cancellation, and makes testing hard. Never use it in production request handlers.

**Tagi:** structured-concurrency, coroutineScope, supervisorScope

---

## Q-CW-005 [bloom: recall]
**Pytanie:** Explain the relationship between `CoroutineScope`, `Job`, and `SupervisorJob`. How do they fit together?

**Modelowa odpowiedź:**
`CoroutineScope` is a context holder — it wraps a `CoroutineContext` and provides a namespace for launching coroutines. Every coroutine builder (`launch`, `async`) requires a scope and inherits the scope's context.

`Job` is the handle to a coroutine's lifecycle. It has states: New → Active → Completing → Completed (or Cancelling → Cancelled). Jobs form a tree: a child job's parent is the `Job` in the scope from which it was launched. Cancelling a parent `Job` cancels all children.

`SupervisorJob` changes the failure semantics: a child failure does NOT cancel the `SupervisorJob` or its siblings. Regular `Job` cancels the whole tree on child failure.

```kotlin
// Bean-scoped coroutine scope in a Spring service
@Service
class RecsService(private val client: WebClient) : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default

    fun shutdown() {
        coroutineContext.cancel() // cancels all child coroutines cleanly
    }
}
```

Spring's `@Async` and coroutine integration in WebFlux controllers handle scope lifecycle automatically — you rarely create `CoroutineScope` manually in controller code, but you need to understand it for service-layer background work and testing.

**Pułapka rozmowna:** "Does cancelling a `Job` kill the coroutine immediately?" — No. Cancellation is cooperative. The coroutine must reach a suspension point or check `isActive`. A tight CPU loop with no suspension points will not stop until it finishes or checks cancellation explicitly.

**Tagi:** CoroutineScope, Job, SupervisorJob, lifecycle

---

## Q-CW-006 [bloom: recall]
**Pytanie:** Describe the four standard Dispatchers: `Default`, `IO`, `Main`, `Unconfined`. When would you use each in a backend service?

**Modelowa odpowiedź:**
`Dispatchers.Default`:
- Backed by a thread pool sized to the number of CPU cores.
- Use for: CPU-intensive work — JSON parsing, ranking/scoring, sorting candidates, compression.
- Wrong use: blocking I/O will exhaust this small pool.

`Dispatchers.IO`:
- Backed by a large, elastic thread pool (default limit: 64 threads, configurable via `kotlinx.coroutines.io.parallelism` system property).
- Use for: blocking I/O — JDBC, blocking file access, legacy blocking libraries.
- Under the hood it shares threads with `Dispatchers.Default` but uses a different limit.

`Dispatchers.Main`:
- Single-thread UI dispatcher. Only available on Android/JavaFX/Swing.
- Backend services: never use this. It will throw if the main dispatcher isn't installed.

`Dispatchers.Unconfined`:
- Starts the coroutine in the current thread; after the first suspension, resumes in the thread that completed the suspension.
- Use for: testing or very specific low-overhead scenarios. Never in production handlers — thread of resumption is unpredictable.

Backend rule of thumb:
```kotlin
// CPU work: reranking 200 candidates
withContext(Dispatchers.Default) { reranker.score(candidates) }

// Legacy blocking DB call
withContext(Dispatchers.IO) { jdbcRepo.find(userId) }

// Non-blocking WebClient: no withContext needed — Reactor uses its own scheduler
webClient.get(url).awaitBody<User>()
```

**Pułapka rozmowna:** "Can I use `Dispatchers.Default` for everything to keep it simple?" — No. Blocking I/O on `Default` starves CPU-bound coroutines and causes latency spikes. `Default` has only ~8 threads on a modern machine; block even 2 of them with JDBC calls and throughput collapses.

**Tagi:** dispatchers, threading, performance

---

## Q-CW-007 [bloom: recall]
**Pytanie:** What is `Flow<T>`? How does it differ from a cold stream vs a hot stream? What are `SharedFlow` and `StateFlow`?

**Modelowa odpowiedź:**
`Flow<T>` is Kotlin's cold reactive stream — an asynchronous sequence of values. "Cold" means the producer code does not run until a collector subscribes, and each collector gets its own independent execution of the producer.

```kotlin
fun numberFlow(): Flow<Int> = flow {
    println("producer started") // runs per collector
    for (i in 1..5) {
        delay(100)
        emit(i)
    }
}
// No output yet — nothing collecting
val f = numberFlow()
// Now it runs:
f.collect { println(it) }
```

Hot streams (`SharedFlow`, `StateFlow`) emit regardless of collectors:

`SharedFlow`: a broadcast channel. Emissions happen whether or not there are subscribers. Subscribers miss emissions that happened before they subscribed (configurable replay buffer). Use for: events, notifications.

`StateFlow`: a `SharedFlow` that always holds the latest value. New collectors immediately receive the current state. Replays exactly 1 value. Use for: in-memory state that multiple parts of the system observe (like a cache of model version metadata).

```kotlin
val state = MutableStateFlow(ModelVersion.INITIAL)
// elsewhere:
state.value = ModelVersion.V2  // triggers all collectors
```

In WebFlux/SSE context: `Flow<T>` maps naturally to `Flux<T>` via `.asFlux()` — each HTTP request gets its own cold stream execution.

**Pułapka rozmowna:** "Is `Flow` the same as `RxJava Observable`?" — Cold semantics are similar, but `Flow` is built on coroutines: collection is a `suspend` operation, operators are inline functions (no wrapper objects per operator), and it integrates natively with structured cancellation. `RxJava` requires manual `dispose()` management.

**Tagi:** Flow, cold-stream, hot-stream, SharedFlow, StateFlow

---

## Q-CW-008 [bloom: recall]
**Pytanie:** What does `withContext` do? When would you use it, and what does it NOT do?

**Modelowa odpowiedź:**
`withContext(context) { ... }` switches the coroutine's context for the duration of the block and then switches back. It suspends the coroutine, moves work to the new context (e.g., different dispatcher), and resumes on the original context when done.

```kotlin
suspend fun computeScore(candidates: List<Candidate>): List<ScoredCandidate> =
    withContext(Dispatchers.Default) {        // switch to CPU pool
        candidates.map { scorer.score(it) }  // CPU-intensive
    }
    // resumes on the original dispatcher after the block
```

When to use:
- Switching to `Dispatchers.IO` for a blocking call inside an otherwise non-blocking suspend function.
- Switching to `Dispatchers.Default` for CPU-heavy computation inside an IO-dispatched coroutine.
- Adding a `CoroutineName` for debugging without changing dispatcher.

What `withContext` does NOT do:
- It does not start a new coroutine — it runs in the same coroutine, just on a different dispatcher.
- It does not parallelize — the block runs sequentially. For parallelism use `async { }` inside `coroutineScope`.
- It is not the same as `launch` — there is no new `Job` in the parent scope.

**Pułapka rozmowna:** "If I wrap a blocking call in `withContext(Dispatchers.IO)`, is my controller now fully non-blocking?" — The controller coroutine suspends and does not occupy an event loop thread, so yes, the event loop is free. But you are still burning a thread from `Dispatchers.IO`'s pool for the duration of the blocking call. It's better than blocking the event loop, but not as scalable as a truly non-blocking solution (R2DBC, reactive WebClient).

**Tagi:** withContext, dispatcher-switching, context

---

## Q-CW-009 [bloom: recall]
**Pytanie:** How does cancellation work in Kotlin coroutines? What is cooperative cancellation, and what role do `isActive`, `ensureActive()`, and `CancellationException` play?

**Modelowa odpowiedź:**
Cancellation in coroutines is cooperative: calling `job.cancel()` sets a cancellation flag, but the coroutine itself must check for it. A coroutine that never yields will run to completion even after cancellation.

Suspension points (any `suspend` call from `kotlinx.coroutines`) check cancellation automatically — `delay()`, `yield()`, `.await()`, `withContext()`, channel operations all throw `CancellationException` if the coroutine has been cancelled.

For CPU-bound loops with no suspension points, you must check manually:
```kotlin
suspend fun processLargeList(items: List<Item>) = withContext(Dispatchers.Default) {
    for (item in items) {
        ensureActive()          // throws CancellationException if cancelled
        heavyCompute(item)
    }
}
```

`isActive`: a boolean property on `CoroutineScope` / `CoroutineContext`; `true` if the coroutine is not cancelled. Use when you want to handle cancellation gracefully (e.g., break loop, clean up) instead of throwing.

`ensureActive()`: shorthand for `if (!isActive) throw CancellationException()`. Preferred in tight loops.

`CancellationException`: a special exception that does NOT propagate up as an error. The coroutine framework uses it to signal cancellation; it is swallowed at scope boundaries and does not trigger error handlers. Do NOT catch and swallow it — you will prevent the coroutine from being cancelled.

```kotlin
// WRONG: swallowing CancellationException
try {
    delay(1000)
} catch (e: Exception) {  // catches CancellationException too!
    // coroutine can no longer be cancelled
}

// CORRECT:
try {
    delay(1000)
} catch (e: CancellationException) {
    throw e  // re-throw
} catch (e: Exception) {
    // handle real errors
}
```

**Pułapka rozmowna:** "What happens when a WebFlux request is cancelled (client disconnects)?" — The `ServerWebExchange` is cancelled, which propagates down to the coroutine scope handling that request. All child coroutines (parallel WebClient calls, DB queries) are cancelled via structured concurrency. This is one of the biggest practical wins of coroutines + WebFlux over thread-per-request: no dangling threads doing work nobody will consume.

**Tagi:** cancellation, CancellationException, isActive, ensureActive

---

## Q-CW-010 [bloom: recall]
**Pytanie:** Describe Spring WebFlux at a high level. What is the Reactor model, what are `Mono<T>` and `Flux<T>`, and how does it differ from Spring MVC?

**Modelowa odpowiedź:**
Spring WebFlux is a reactive web framework built on Project Reactor and Netty. Instead of blocking one thread per request (Spring MVC + servlet model), it uses a small fixed pool of event loop threads that handle I/O events for thousands of concurrent requests.

Core model:
- Netty runs N event loop threads (N = CPU cores by default).
- Each thread processes I/O events from many connections: request arrives → event fires → handler runs → if I/O needed, subscribe to a reactive stream → release thread → I/O completes → event fires again → resume handler.
- No thread is ever blocked. Throughput scales with CPU, not connection count.

`Mono<T>`: a reactive stream of 0 or 1 elements. Represents an asynchronous computation of a single value — analogous to `CompletableFuture<T>` but composable. Used for: single HTTP responses, single DB reads.

`Flux<T>`: a reactive stream of 0 to N elements. Represents a sequence of values over time. Used for: SSE endpoints, streaming results, batch DB reads.

Both are lazy (cold) by default — nothing runs until you subscribe.

```java
// Spring MVC (blocking)
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) {
    return userRepo.findById(id); // blocks thread
}

// WebFlux (reactive)
@GetMapping("/user/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return userRepo.findById(id); // returns immediately, Reactor subscribes later
}

// WebFlux with Kotlin coroutines (same semantics, readable)
@GetMapping("/user/{id}")
suspend fun getUser(@PathVariable id: Long): User =
    userRepo.findById(id) // looks blocking, isn't
```

**Pułapka rozmowna:** "Is WebFlux always faster than Spring MVC?" — No. For low concurrency, Spring MVC is simpler and often has lower latency per request (no subscription overhead). WebFlux wins under high concurrency (thousands of simultaneous connections), I/O-bound workloads, and streaming scenarios. Allegro's recsys serving qualifies: very high RPS, parallel outbound calls, tight latency budgets.

**Tagi:** WebFlux, Reactor, Mono, Flux, event-loop

---

## Q-CW-011 [bloom: understand]
**Pytanie:** Why use Kotlin coroutines + WebFlux together instead of (a) coroutines + virtual threads (Loom), or (b) just blocking Spring MVC + virtual threads?

**Modelowa odpowiedź:**
Three viable options, different trade-offs:

**Option A: WebFlux + Coroutines (Allegro's current approach)**
- WebFlux provides the non-blocking I/O engine (Netty event loop).
- Coroutines provide readable, sequential-looking code on top of reactive streams.
- Result: event loop threads are never blocked; coroutines suspend on I/O and resume efficiently.
- Trade-off: must understand two abstraction layers; library ecosystem must be reactive (R2DBC, reactive WebClient, reactive Redis).

**Option B: Coroutines + Virtual Threads (Loom)**
- Virtual threads park their carrier thread on blocking I/O, allowing the OS thread to do other work.
- Coroutines on top of virtual threads: you get readable code, but you're still paying the cost of blocking I/O at the OS-thread level (just cheaper than platform threads).
- Problem: a virtual thread that blocks still blocks a carrier thread. Under extreme throughput (Allegro scale: millions of requests/day), this is less efficient than a true event loop. Also, reactive backpressure is lost.

**Option C: Blocking Spring MVC + Virtual Threads**
- Simplest mental model: sequential code, thread-per-request, no reactive plumbing.
- Virtual threads are cheap (~KB of memory), so you can handle more concurrent requests.
- Problem: no backpressure, no streaming, blocking DB drivers still needed, harder to enforce timeouts across parallel calls. Works well for medium-load services; may struggle at Allegro's recsys RPS.

Bottom line for a senior interview: WebFlux + coroutines is the best combination when you have (1) extremely high concurrency, (2) parallel outbound I/O (parallel feature fetching), (3) streaming requirements (SSE), and (4) an existing WebFlux ecosystem. Loom is a valid alternative for new greenfield services at moderate load — and Spring Boot 3.2+ makes it trivial to enable. For existing Kotlin + WebFlux code, migration to Loom is non-trivial.

**Pułapka rozmowna:** "Spring Boot 3.2 supports virtual threads — should Allegro switch now?" — This is the Q-CW-022 analysis question. Short answer here: not without benchmarking. Reactive backpressure and the Netty event loop provide properties Loom alone cannot replicate.

**Tagi:** loom, virtual-threads, WebFlux, architecture, trade-offs

---

## Q-CW-012 [bloom: understand]
**Pytanie:** What is backpressure? How does Reactor handle it, and how does `Flow` handle it differently?

**Modelowa odpowiedź:**
Backpressure is the mechanism by which a slow consumer signals to a fast producer to slow down, preventing unbounded buffering and memory exhaustion.

In Reactor (Flux/Mono), backpressure is built into the Reactive Streams spec. A subscriber calls `request(n)` to ask for `n` elements; the publisher emits at most `n`. This demand signaling propagates up the operator chain. If you lose it (e.g., `subscribeOn` without proper operators), you can get `MissingBackpressureException`.

Strategies in Reactor:
- `onBackpressureBuffer()`: buffer overflow elements (risk: OOM).
- `onBackpressureLatest()`: keep only the latest.
- `onBackpressureDrop()`: drop if consumer is busy.
- `onBackpressureError()`: throw `MissingBackpressureException`.

In Kotlin `Flow`, backpressure is implicit and natural: `Flow` is sequential by default — the next `emit()` suspends until the collector has processed the previous value. The collector drives the pace. There is no separate `request(n)` protocol; coroutine suspension IS the backpressure mechanism.

```kotlin
flow {
    for (item in heavyDataSource()) {
        emit(item)  // suspends here if collector is slow
    }
}.collect { processSlowly(it) }  // no OOM risk
```

When you convert a `Flow` to a `Flux` (via `.asFlux()`), the bridge handles the translation between coroutine suspension and Reactive Streams `request()` protocol.

**Pułapka rozmowna:** "What happens to backpressure when you call `flow.buffer(100)`?" — The buffer decouples producer and consumer: the producer can emit up to 100 items ahead of the collector. This can improve throughput but risks OOM if the producer is much faster than the consumer long-term. Choose buffer size based on measured producer/consumer speed ratio.

**Tagi:** backpressure, Flow, Reactor, reactive-streams

---

## Q-CW-013 [bloom: understand]
**Pytanie:** Explain the bridge between `Mono<T>`/`Flux<T>` and coroutines (`suspend fun` / `Flow<T>`). Which functions cross the bridge in each direction?

**Modelowa odpowiedź:**
Kotlin coroutines and Project Reactor are separate worlds. The `kotlinx-coroutines-reactor` library (bundled with Spring Boot + Kotlin) provides the bridge.

**Reactor → Coroutines:**
```kotlin
// Mono → suspend
val user: User = userMono.awaitSingle()         // throws if 0 elements
val userOrNull: User? = userMono.awaitSingleOrNull() // null if empty Mono

// Flux → Flow
val flow: Flow<User> = userFlux.asFlow()
```

`awaitSingle()` suspends the coroutine until the `Mono` completes, then returns the value (or throws if empty or error). Internally it subscribes to the `Mono` and wires the subscription to the coroutine continuation.

**Coroutines → Reactor:**
```kotlin
// suspend lambda → Mono
val mono: Mono<User> = mono { userService.find(id) }   // calls suspend fun inside

// Flow → Flux
val flux: Flux<User> = userFlow.asFlux()
```

`mono { }` and `flux { }` are coroutine builders that return Reactor types. They are cold: the coroutine only starts when the `Mono`/`Flux` is subscribed to.

**In a WebFlux controller with Spring's coroutine support**, you do not need these bridges directly — Spring's `DispatcherHandler` recognises `suspend fun` on `@RestController` and handles the wiring automatically:
```kotlin
@GetMapping("/user/{id}")
suspend fun getUser(@PathVariable id: Long): User = userService.find(id)
// Spring wraps this in a Mono internally; you never see it
```

You need the bridge explicitly when:
- Calling Reactor-based library code from a coroutine (`.awaitSingle()`).
- Integrating with existing `Mono`-returning repository interfaces.
- Writing interceptors, filters, or custom middleware that must return `Mono<Void>`.

**Pułapka rozmowna:** "What does `Mono.block()` do inside a coroutine?" — It blocks the thread entirely, bypassing the coroutine suspension mechanism. This is the worst of both worlds: you're in a reactive pipeline, you block the event loop thread, and Reactor may deadlock if the thread pool is small. Never call `.block()` in production WebFlux code.

**Tagi:** Mono, Flux, awaitSingle, asFlow, mono-builder, reactor-bridge

---

## Q-CW-014 [bloom: understand]
**Pytanie:** How do exceptions propagate in `coroutineScope` vs `supervisorScope`? What is `CoroutineExceptionHandler` for, and how do you handle exceptions from `async`?

**Modelowa odpowiedź:**
**`coroutineScope` — fail-fast propagation:**
If any child coroutine throws an unhandled exception, the scope cancels all sibling coroutines and the exception propagates to the caller of `coroutineScope`:
```kotlin
suspend fun fetchBoth(): Pair<A, B> = coroutineScope {
    val a = async { serviceA.get() }   // throws RuntimeException
    val b = async { serviceB.get() }   // will be cancelled
    Pair(a.await(), b.await())
    // RuntimeException propagates out of coroutineScope
}
```

**`supervisorScope` — isolated failure:**
Child failures do not cancel siblings. Each child must handle its own exceptions:
```kotlin
suspend fun fetchBothOptional() = supervisorScope {
    val a = async { serviceA.get() }
    val b = async { serviceB.get() }
    val aResult = try { a.await() } catch (e: Exception) { null }
    val bResult = try { b.await() } catch (e: Exception) { null }
    Pair(aResult, bResult)
}
```

**`CoroutineExceptionHandler`:** an element in `CoroutineContext` that catches unhandled exceptions from `launch` (not `async`). It is a last-resort handler — think of it like `Thread.UncaughtExceptionHandler`. It does NOT prevent exception propagation; it just gets notified after. Useful for logging/alerting at service scope level.

```kotlin
val handler = CoroutineExceptionHandler { _, e ->
    logger.error("Unhandled coroutine exception", e)
}
val scope = CoroutineScope(SupervisorJob() + handler)
```

**Exceptions from `async`:** stored in the `Deferred`. They are NOT thrown until `.await()` is called. If you never call `.await()`, the exception is lost (in `supervisorScope`) or propagates when the `supervisorScope` sees the failed child.

**Pułapka rozmowna:** "`CoroutineExceptionHandler` catches exceptions from `async`, right?" — Wrong. It only catches exceptions from `launch`. For `async`, you must catch exceptions at the `.await()` call site. This is a common interview gotcha.

**Tagi:** exception-handling, coroutineScope, supervisorScope, CoroutineExceptionHandler, async

---

## Q-CW-015 [bloom: understand]
**Pytanie:** Why is calling a blocking function inside a `suspend` function a major bug in a WebFlux service? What actually happens at the runtime level, how do you detect it, and how do you fix it?

**Modelowa odpowiedź:**
WebFlux uses Netty with a small event loop thread pool (typically 2 × CPU cores, e.g., 16 threads on a 8-core machine). These threads process ALL I/O events for ALL concurrent connections.

When a `suspend` function calls a blocking operation (e.g., JDBC, `Thread.sleep()`, `Files.readAllBytes()`), the coroutine does not suspend — it literally blocks the event loop thread. While that thread is blocked, it cannot process I/O events for ANY other connection. With 16 event loop threads and 16 concurrent blocking calls, the entire service is frozen. Every new incoming request sits in the accept queue going nowhere.

```kotlin
// THIS IS A CRITICAL BUG in a WebFlux service:
@GetMapping("/reco")
suspend fun getReco(@PathVariable userId: Long): Reco {
    val user = jdbcTemplate.queryForObject(...)  // blocks event loop thread!
    return recsEngine.compute(user)
}
```

**Detection — BlockHound:**
```kotlin
// In tests or dev mode:
BlockHound.install()
// Throws BlockingOperationError if any blocking call is detected on a non-blocking thread
```
Also: Reactor's own debug agent, thread dump analysis (look for event loop threads stuck in JDBC/IO calls).

**Fix:** wrap blocking calls in `withContext(Dispatchers.IO)`:
```kotlin
suspend fun getReco(@PathVariable userId: Long): Reco {
    val user = withContext(Dispatchers.IO) {
        jdbcTemplate.queryForObject(...)  // now runs on IO thread pool
    }
    return recsEngine.compute(user)
}
```
Better fix for a recsys service: replace JDBC with R2DBC (non-blocking reactive DB driver) — then `withContext` is not needed at all.

**Pułapka rozmowna:** "My `suspend` function just calls another `suspend` function that wraps the JDBC call — so it's fine, right?" — Only if that inner function uses `withContext(Dispatchers.IO)` itself. The `suspend` modifier does not magically make the call non-blocking; the actual blocking work must be dispatched to `Dispatchers.IO`.

**Tagi:** blocking-in-coroutine, event-loop, BlockHound, Dispatchers.IO, WebFlux

---

## Q-CW-016 [bloom: understand]
**Pytanie:** Describe the full threading model of a Spring WebFlux + coroutines service: which thread handles what, and at what point does work move between threads?

**Modelowa odpowiedź:**
A request journey through a Kotlin + WebFlux service:

1. **Netty I/O thread** (event loop): accepts the TCP connection, reads bytes, decodes the HTTP request. This thread is precious — never block it.

2. **Netty I/O thread → coroutine dispatcher**: WebFlux's `DispatcherHandler` creates a coroutine for the `suspend fun` controller method. By default, Spring's coroutine support runs it in the reactor-netty scheduler context. Depending on your configuration, this may already be a different thread.

3. **Coroutine resumes on scheduler thread**: the body of the `suspend fun` runs. If it calls non-blocking `WebClient`, the WebClient registers a callback with Netty and the coroutine suspends — thread released.

4. **WebClient response arrives on a Netty I/O thread**: the Reactor Netty scheduler picks it up, the coroutine is resumed. Which thread resumes it? Whichever is available in the scheduler (often a Reactor `boundedElastic` or `parallel` scheduler thread).

5. **`withContext(Dispatchers.IO)` blocks**: the coroutine is moved to a thread from `Dispatchers.IO`'s thread pool. This thread is allowed to block. When the IO finishes, the coroutine moves back to its original dispatcher.

6. **Response serialization and write-back**: happens on a Netty I/O thread.

Key insight: the event loop threads only touch the coroutine at the edges (receive request, send response). The actual business logic runs on coroutine dispatcher threads or IO threads. Blocking on event loop threads is the only truly dangerous mistake.

**Pułapka rozmowna:** "Does `Dispatchers.Unconfined` run on the event loop thread?" — After the first suspension, it resumes on whatever thread completed the suspension — which COULD be a Netty event loop thread if the suspension was inside a WebClient call. This is why `Unconfined` is dangerous in WebFlux services.

**Tagi:** threading-model, Netty, event-loop, Dispatchers, WebFlux

---

## Q-CW-017 [bloom: apply]
**Pytanie:** Write a Spring WebFlux Kotlin controller with a `suspend fun` handler that calls three `WebClient`s in parallel and combines results. Include per-call timeouts. Show idiomatic Kotlin.

**Modelowa odpowiedź:**
```kotlin
@RestController
@RequestMapping("/api/recommendations")
class RecsController(
    private val userClient: WebClient,
    private val featureClient: WebClient,
    private val annClient: WebClient,
) {
    companion object {
        private val REQUEST_TIMEOUT = Duration.ofMillis(200)
    }

    @GetMapping("/{userId}")
    suspend fun getRecommendations(@PathVariable userId: Long): RecsResponse =
        coroutineScope {
            // All three start in parallel
            val userDeferred = async {
                userClient.get()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .bodyToMono<UserProfile>()
                    .timeout(REQUEST_TIMEOUT)
                    .awaitSingle()
            }

            val featuresDeferred = async {
                featureClient.get()
                    .uri("/features/{id}", userId)
                    .retrieve()
                    .bodyToMono<FeatureVector>()
                    .timeout(REQUEST_TIMEOUT)
                    .awaitSingle()
            }

            val candidatesDeferred = async {
                annClient.get()
                    .uri("/ann/query/{id}", userId)
                    .retrieve()
                    .bodyToMono<List<Long>>()
                    .timeout(REQUEST_TIMEOUT)
                    .awaitSingle()
            }

            // Suspends until all three complete; if any fails, others are cancelled
            val user = userDeferred.await()
            val features = featuresDeferred.await()
            val candidates = candidatesDeferred.await()

            RecsResponse(userId, user, features, candidates)
        }
}
```

Key points:
- `coroutineScope { }` ensures all three are cancelled if any throws.
- Three `async` blocks start immediately in parallel before any `await()` is called.
- `.timeout()` on each `Mono` independently enforces the per-call budget (not a global timeout).
- `.awaitSingle()` bridges `Mono` → coroutine.
- Spring's `@RestController` + `suspend fun` requires `spring-boot-starter-webflux` + `kotlinx-coroutines-reactor` on the classpath — Spring handles the `Mono` wrapping automatically.

To add a global 400ms deadline across all three calls, wrap with `withTimeout(400)` outside `coroutineScope`.

**Pułapka rozmowna:** "What if I call `await()` immediately after each `async` instead of at the end?" Then the three calls execute sequentially, not in parallel. Always start ALL `async` blocks before calling ANY `.await()`.

**Tagi:** WebClient, parallel, async, coroutineScope, timeout, apply

---

## Q-CW-018 [bloom: apply]
**Pytanie:** Write a `Flow<T>` that reads a paginated downstream endpoint via `WebClient`, filters results, and emits batched chunks of 10 items for an SSE endpoint.

**Modelowa odpowiedź:**
```kotlin
data class Item(val id: Long, val score: Double, val active: Boolean)
data class Batch(val items: List<Item>)

// Paginated Flow producer
fun fetchItemsFlow(client: WebClient, userId: Long): Flow<Item> = flow {
    var page = 0
    while (true) {
        val page_results = client.get()
            .uri { it.path("/items").queryParam("userId", userId).queryParam("page", page).build() }
            .retrieve()
            .bodyToMono<List<Item>>()
            .awaitSingleOrNull() ?: break

        if (page_results.isEmpty()) break
        page_results.forEach { emit(it) }
        page++
    }
}

// SSE controller using Flow
@RestController
class ItemStreamController(private val client: WebClient) {

    @GetMapping("/stream/items/{userId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamItems(@PathVariable userId: Long): Flow<Batch> =
        fetchItemsFlow(client, userId)
            .filter { it.active && it.score > 0.5 }
            .chunked(10)                          // batch into lists of 10
            .map { Batch(it) }
            .flowOn(Dispatchers.Default)          // filtering/mapping on Default
}
```

`chunked(n)` is available in `kotlinx.coroutines` 1.6+. For older versions, implement manually:
```kotlin
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    val buffer = mutableListOf<T>()
    collect { item ->
        buffer.add(item)
        if (buffer.size == size) {
            emit(buffer.toList())
            buffer.clear()
        }
    }
    if (buffer.isNotEmpty()) emit(buffer.toList())
}
```

Spring WebFlux natively supports `Flow<T>` as a return type for SSE endpoints (no conversion needed when `kotlinx-coroutines-reactor` is on classpath).

**Pułapka rozmowna:** "Do you need to call `.collect()` somewhere for this to work?" — Spring's `ResponseBodyResultHandler` subscribes to the `Flow` (via its Reactor bridge) when the HTTP response is being written. You never call `.collect()` manually in a controller — returning the `Flow` is enough.

**Tagi:** Flow, SSE, WebClient, pagination, batching, apply

---

## Q-CW-019 [bloom: apply]
**Pytanie:** Configure a `WebClient` bean with: connection timeout 500ms, response timeout 2s, max in-memory buffer size 2MB, default `Authorization` header, and retry on 5xx with exponential backoff (3 attempts, initial 100ms, factor 2). Show idiomatic Kotlin.

**Modelowa odpowiedź:**
```kotlin
@Configuration
class WebClientConfig {

    @Bean
    fun recommendationWebClient(): WebClient {
        val connectionProvider = ConnectionProvider.builder("recs-pool")
            .maxConnections(200)
            .pendingAcquireMaxCount(500)
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
            .responseTimeout(Duration.ofSeconds(2))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(2, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${resolveToken()}")
            .filter(retryFilter())
            .build()
    }

    private fun retryFilter(): ExchangeFilterFunction =
        ExchangeFilterFunction.ofResponseProcessor { response ->
            if (response.statusCode().is5xxServerError) {
                Mono.error(RetryableException("5xx: ${response.statusCode()}"))
            } else {
                Mono.just(response)
            }
        }
}

// Usage with retry at call site (preferred — more composable):
suspend fun fetchWithRetry(client: WebClient, uri: String): MyResponse =
    client.get()
        .uri(uri)
        .retrieve()
        .bodyToMono<MyResponse>()
        .retryWhen(
            Retry.backoff(3, Duration.ofMillis(100))
                .factor(2.0)
                .filter { it is RetryableException || it is WebClientResponseException.ServiceUnavailable }
                .jitter(0.1)
        )
        .awaitSingle()
```

Notes:
- `CONNECT_TIMEOUT_MILLIS` controls TCP connection establishment.
- `responseTimeout` controls time to first byte after the request is sent.
- `maxInMemorySize` prevents OOM when reading large response bodies into memory.
- Retry logic belongs at the call site or via a filter — not at the bean level — so you can tune per-endpoint.

**Pułapka rozmowna:** "Will `retryWhen` retry on timeout?" — Only if the timeout throws a retriable exception type you've included in the `filter`. `ReadTimeoutException` is not automatically included. Be explicit.

**Tagi:** WebClient, configuration, timeout, retry, exponential-backoff, apply

---

## Q-CW-020 [bloom: apply]
**Pytanie:** Translate this blocking Spring MVC controller to coroutines + non-blocking: the endpoint calls `userService.findById()` (JDBC), then `pricingService.getPrice()` (HTTP), then assembles a response. Show the before and after.

**Modelowa odpowiedź:**
**BEFORE (blocking Spring MVC):**
```kotlin
@RestController
class PriceController(
    private val userService: UserService,       // uses JdbcTemplate
    private val pricingService: PricingService, // uses RestTemplate
) {
    @GetMapping("/price/{userId}/{itemId}")
    fun getPrice(
        @PathVariable userId: Long,
        @PathVariable itemId: Long,
    ): PriceResponse {
        val user = userService.findById(userId)      // blocks thread (JDBC)
        val price = pricingService.getPrice(itemId)  // blocks thread (HTTP)
        return PriceResponse(user.segment, price.amount, price.currency)
    }
}
```

**AFTER (coroutines + non-blocking):**
```kotlin
@RestController
class PriceController(
    private val userRepo: R2dbcUserRepository,       // reactive, R2DBC
    private val pricingClient: WebClient,
) {
    @GetMapping("/price/{userId}/{itemId}")
    suspend fun getPrice(
        @PathVariable userId: Long,
        @PathVariable itemId: Long,
    ): PriceResponse = coroutineScope {
        // Run in parallel: DB and HTTP happen simultaneously
        val userDeferred = async { userRepo.findById(userId).awaitSingle() }
        val priceDeferred = async {
            pricingClient.get()
                .uri("/prices/{itemId}", itemId)
                .retrieve()
                .bodyToMono<Price>()
                .awaitSingle()
        }
        val user = userDeferred.await()
        val price = priceDeferred.await()
        PriceResponse(user.segment, price.amount, price.currency)
    }
}
```

Migration steps:
1. Replace `JdbcTemplate` / JPA with R2DBC reactive repository.
2. Replace `RestTemplate` / Feign with `WebClient`.
3. Change `@GetMapping` method to `suspend fun`.
4. Bridge `Mono` returns with `.awaitSingle()`.
5. Use `coroutineScope { async { } }` to parallelize independent calls.
6. Remove `@EnableAsync`, thread pool config — no longer needed for this path.

Bonus: the original code called user then price sequentially (total time = T_user + T_price). The coroutine version runs them in parallel (total time ≈ max(T_user, T_price)). For recsys feature fetching with 3+ parallel calls, this multiplier matters.

**Pułapka rozmowna:** "Can I keep JDBC and just wrap it in `withContext(Dispatchers.IO)`?" — Yes, as a migration step. It's not as scalable as R2DBC (you're still burning IO threads), but it's safe and avoids blocking the event loop. For a brownfield migration, `withContext(Dispatchers.IO)` around JDBC is a valid intermediate state.

**Tagi:** migration, MVC-to-WebFlux, R2DBC, WebClient, parallel, apply

---

## Q-CW-021 [bloom: apply]
**Pytanie:** Write a test (JUnit 5 + `kotlinx-coroutines-test`) for a `suspend fun` that makes two parallel calls. The test should: use virtual time for `delay()`, verify both calls happened concurrently (not sequentially), and check the combined result.

**Modelowa odpowiedź:**
```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

// System under test
class FeatureFetcher(
    private val storeA: suspend (Long) -> String,
    private val storeB: suspend (Long) -> String,
) {
    suspend fun fetchBoth(userId: Long): Pair<String, String> = coroutineScope {
        val a = async { storeA(userId) }
        val b = async { storeB(userId) }
        Pair(a.await(), b.await())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFetcherTest {

    @Test
    fun `fetchBoth runs both calls concurrently`() = runTest {
        val callLog = mutableListOf<String>()

        // Both stubs take 100ms each
        val fetcher = FeatureFetcher(
            storeA = { id ->
                delay(100)         // virtual time — no real waiting
                callLog.add("A:$id")
                "featureA"
            },
            storeB = { id ->
                delay(100)
                callLog.add("B:$id")
                "featureB"
            },
        )

        val startVirtualTime = currentTime  // coroutines-test TestCoroutineScheduler
        val result = fetcher.fetchBoth(42L)
        val elapsed = currentTime - startVirtualTime

        // If run sequentially: elapsed would be 200ms
        // If run in parallel: elapsed should be ~100ms
        assertEquals(100, elapsed, "Calls should run concurrently, not sequentially")

        assertEquals(Pair("featureA", "featureB"), result)
        assertEquals(setOf("A:42", "B:42"), callLog.toSet())  // both were called
    }

    @Test
    fun `fetchBoth propagates exception from storeA and cancels storeB`() = runTest {
        var storeBCancelled = false

        val fetcher = FeatureFetcher(
            storeA = { throw RuntimeException("store A down") },
            storeB = { id ->
                try {
                    delay(500)
                    "featureB"
                } catch (e: CancellationException) {
                    storeBCancelled = true
                    throw e
                }
            },
        )

        assertThrows<RuntimeException> { fetcher.fetchBoth(1L) }
        assertTrue(storeBCancelled, "storeB should have been cancelled when storeA failed")
    }
}
```

Key API:
- `runTest { }`: test coroutine builder that uses virtual time. `delay()` inside does not actually wait.
- `currentTime`: the virtual clock in milliseconds from the `TestCoroutineScheduler`.
- `advanceTimeBy(n)` / `advanceUntilIdle()`: manually advance virtual time if needed.
- No `runBlocking` in tests — it uses real time and can make tests slow or flaky.

**Pułapka rozmowna:** "Why not just use `runBlocking` for coroutine tests?" — `runBlocking` runs with real time, so `delay(5000)` makes your test 5 seconds slow. It also doesn't give you `currentTime` to verify concurrency. `runTest` is the right tool for coroutine unit tests.

**Tagi:** testing, runTest, virtual-time, coroutines-test, concurrency-verification, apply

---

## Q-CW-022 [bloom: analyze]
**Pytanie:** "We should migrate our Kotlin + WebFlux recsys service to virtual threads now that Spring Boot 3.2 ships them." Analyze the trade-offs for an existing high-RPS Allegro service.

**Modelowa odpowiedź:**
This is a legitimate engineering question, not a trick — the answer is nuanced.

**Arguments FOR migrating to virtual threads:**
- Virtual threads dramatically simplify the mental model: write blocking code, get concurrency "for free."
- No need for `withContext(Dispatchers.IO)` around legacy blocking libraries — virtual threads park safely.
- Eliminates the risk of accidentally blocking event loop threads (the #1 WebFlux bug).
- Spring Boot 3.2+ enables virtual threads via a single property: `spring.threads.virtual.enabled=true`.
- For new greenfield services with moderate load (< ~50k RPS), virtual threads are a compelling default.
- Kotlin coroutines on top of virtual threads still work — you get structured concurrency AND cheap blocking.

**Arguments AGAINST migrating this specific service:**
1. **Backpressure**: Reactor's `Flux` with backpressure operators (`onBackpressureBuffer`, `limitRate`) has no direct equivalent in virtual threads. If the recsys serving path streams ranked candidates to a downstream consumer, backpressure semantics matter.
2. **Existing reactive library ecosystem**: R2DBC, reactive Redis, reactive Kafka — these are built around Reactor. Switching to blocking drivers means losing connection pooling tuned for non-blocking use.
3. **Benchmarks first**: virtual threads under very high RPS (Allegro's recsys processes hundreds of requests/second per instance) may create carrier thread contention in ways that are hard to predict without load testing. Netty's event loop is proven at this scale.
4. **Migration risk**: the existing service is tested and tuned. Changing the threading model risks subtle bugs (thread-local state, context propagation) that only appear under production load.
5. **Coroutines already solve the main problem**: with `withContext(Dispatchers.IO)` and proper dispatcher discipline, blocking is already handled. The ergonomic win of virtual threads is smaller when you already have coroutines.

**Recommendation for a senior engineer to give:**
- Do NOT migrate opportunistically. Run a shadow load test with virtual threads enabled on a canopy instance.
- If the service has no streaming/backpressure requirements and the team's biggest pain is blocking-in-coroutine bugs → virtual threads are worth piloting.
- If the service uses reactive DB drivers and backpressure operators → stay on WebFlux + coroutines; the migration cost exceeds the benefit.
- New services at Allegro in 2024+: virtual threads are a reasonable default unless streaming is required.

**Pułapka rozmowna:** "Virtual threads are strictly better than event loops, right?" — No. An event loop is O(1) threads for I/O waiting; virtual threads are O(active requests) parked threads. Under extreme connection counts (10k+ concurrent slow connections), virtual threads use more memory. Netty's event loop remains superior for those scenarios.

**Tagi:** loom, virtual-threads, migration, trade-offs, analyze

---

## Q-CW-023 [bloom: analyze]
**Pytanie:** Compare error handling approaches in the reactive/coroutine stack: `Mono.onErrorResume`, coroutine `try/catch`, `Result<T>`, and Arrow's `Either<E, A>`. When would you reach for each?

**Modelowa odpowiedź:**
Four tools, four different points on the explicitness/composability spectrum:

**`Mono.onErrorResume { ... }` (Reactor operator chain)**
- Inline error recovery within a Reactor pipeline. The error is handled at the point of emission.
- Composable with other operators but forces you to stay in Reactor's API.
- Use when: you're working in a Reactor chain (not coroutine code) and want to provide a fallback `Mono`.
```kotlin
userMono.onErrorResume(NotFoundException::class.java) { Mono.just(User.ANONYMOUS) }
```
- Drawback: error type erasure (must cast), mixing business logic with stream operations is messy.

**Coroutine `try/catch`**
- Standard Kotlin exception handling; reads naturally, works with `suspend` functions.
- Use when: the error handling is local and the exception type is known.
```kotlin
val user = try {
    userService.find(id)
} catch (e: UserNotFoundException) {
    User.ANONYMOUS
}
```
- Drawback: exceptions are non-local by nature (can cross many call frames); for expected failures (user not found), using exceptions as control flow is controversial.

**`Result<T>` (Kotlin stdlib)**
- A sealed type wrapping either a success value or a `Throwable`. Function returns `Result<User>` instead of throwing.
- Explicit at the call site: caller must handle both branches.
```kotlin
suspend fun findUser(id: Long): Result<User> = runCatching { repo.find(id) }

// Caller:
findUser(id).getOrElse { User.ANONYMOUS }
findUser(id).onFailure { logger.warn("User not found", it) }
```
- Use when: you want to force callers to handle failures without exceptions crossing async boundaries.
- Drawback: `Throwable` as the error type — no domain error modeling.

**Arrow `Either<E, A>`**
- Left is a typed domain error (`UserError`, `PricingError`), Right is the success value.
- Forces exhaustive handling; error types are part of the function signature.
```kotlin
suspend fun findUser(id: Long): Either<UserError, User> =
    either { repo.find(id).rightIfNotNull { UserError.NotFound(id) } }

// Caller:
findUser(id).fold(
    ifLeft = { error -> handleError(error) },
    ifRight = { user -> process(user) },
)
```
- Use when: you have a rich domain error model (multiple typed failure modes that are expected), and you want type-safe, exhaustive error handling without exceptions.
- Drawback: requires Arrow dependency, steeper learning curve, all collaborators must use `Either`.

**Decision matrix for Allegro recsys:**
- Unexpected infrastructure failure (timeout, 5xx): `try/catch` + log + return degraded response.
- Expected business failures (user not found, feature unavailable): `Result<T>` or `Either` depending on team convention.
- Reactor pipeline fallback: `onErrorResume` only when already in a Reactor chain (e.g., library interop).
- Domain-heavy services with many typed errors: Arrow `Either` is worth the investment.

**Pułapka rozmowna:** "Should I never use exceptions in a coroutine service?" — Exceptions are fine for truly exceptional conditions (infrastructure failures, programming errors). The controversy is about using exceptions for expected business outcomes (user not found). Use typed returns (`Either`, `Result`) for expected failures; exceptions for unexpected ones.

**Tagi:** error-handling, Result, Either, Arrow, onErrorResume, analyze

---

## Q-CW-024 [bloom: analyze]
**Pytanie:** A senior engineer reviews your code: "You're using `async` where `launch` would do — that allocates a `Deferred` unnecessarily, and you have to remember to `.await()`." Analyze when this critique is right vs when `async` is genuinely better.

**Modelowa odpowiedź:**
The critique is correct about the mechanics: `async` returns a `Deferred<T>` (a heap-allocated object wrapping a `Job`), and every `.await()` call site is a place where you can forget to call it, accidentally swallow exceptions, or call it after cancellation.

**When the critique is right:**
You have a fire-and-forget side effect, and you're using `async` out of habit:
```kotlin
// BAD: async with unused Deferred, swallowed exception risk
coroutineScope {
    val d1 = async { analyticsService.track(event) } // nobody calls d1.await()
    val d2 = async { cacheService.invalidate(key) }   // same
    // if either throws, exception is lost
}

// GOOD: launch for fire-and-forget
coroutineScope {
    launch { analyticsService.track(event) }
    launch { cacheService.invalidate(key) }
    // exceptions propagate to scope immediately; no dangling Deferreds
}
```

Also right when you call `.await()` immediately:
```kotlin
val result = async { compute() }.await()  // equivalent to just: compute()
// pointless async; no parallelism gained
```

**When `async` is genuinely better:**
1. You need the return value and want to parallelize collection before awaiting:
```kotlin
coroutineScope {
    val userD = async { fetchUser(id) }
    val priceD = async { fetchPrice(id) }
    // true parallelism; impossible with launch
    Response(userD.await(), priceD.await())
}
```
2. You want to handle exceptions independently (with `supervisorScope`):
```kotlin
supervisorScope {
    val a = async { riskyServiceA() }
    val b = async { riskyServiceB() }
    val aResult = runCatching { a.await() }.getOrNull()
    val bResult = runCatching { b.await() }.getOrNull()
}
```
3. You need to start work early and possibly not collect results until later (deferred computation).

**Summary for the interview:**
The senior engineer's critique applies to `async` used for side effects or without parallelism. `async` is the RIGHT tool when you need a return value AND you're starting multiple coroutines before awaiting any of them. The smell to avoid: `async { }.await()` in sequence (just call the function) or fire-and-forget `async` (use `launch`).

**Pułapka rozmowna:** "`async` is strictly more powerful than `launch`, so why not always use `async`?" — Power comes with responsibility. A forgotten `.await()` swallows exceptions. In `supervisorScope`, a failed `async` with no `.await()` call drops the exception silently. `launch` fails loudly. Use the weakest tool that fits the job.

**Tagi:** async, launch, Deferred, code-review, analyze
