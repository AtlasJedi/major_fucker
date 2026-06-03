# Coroutines & WebFlux — question bank

> Kotlin coroutines and Spring WebFlux are the reactive backbone of high-RPS Java/Kotlin backend services. A senior candidate must understand both layers deeply: how the Kotlin compiler transforms `suspend` functions into continuation-passing-style state machines, how structured concurrency propagates cancellation and exceptions through a Job tree, how Project Reactor's Reactive Streams backpressure protocol works at the subscriber/subscription level, and where the two worlds meet via `awaitSingle`/`asFlow`/`mono {}`. Questions span from definition-level recall to operational analysis of real production trade-offs: event-loop vs thread-per-request at 20k+ RPS, GlobalScope leaks, blocking-on-event-loop disasters, and the Loom migration decision.

## Scope

- Coroutine fundamentals: suspend functions, continuation-passing-style, compiler-generated state machine
- CoroutineScope, Job, SupervisorJob, structured concurrency
- Dispatchers: Default, IO, Main, Unconfined — when to use each
- launch vs async/await; Deferred; fire-and-forget semantics
- coroutineScope vs supervisorScope — failure propagation rules
- Cancellation: cooperative cancellation, isActive, ensureActive, CancellationException, NonCancellable
- Exception handling: CoroutineExceptionHandler, supervision, async vs launch exception semantics
- Flow: cold streams, flowOn, buffer, conflate, backpressure by suspension
- StateFlow and SharedFlow as hot streams
- Why GlobalScope is discouraged
- Reactive Streams spec: Publisher/Subscriber/Subscription/backpressure protocol
- Project Reactor: Mono/Flux, operators (map/flatMap/zip/flatMapMany), schedulers
- WebFlux: Netty event loop, never-block rule, MVC thread-per-request vs event-loop
- Coroutines-Reactor interop: awaitSingle/awaitSingleOrNull, asFlow, mono {}, flux {}
- suspend functions in Spring controllers; Spring's automatic Mono wrapping
- Blocking inside a suspend function — detection (BlockHound) and fix
- WebClient configuration: connection pool, timeouts, retry with backpressure
- Testing suspend functions with virtual time (runTest, TestCoroutineScheduler)
- When reactive pays off: high-concurrency I/O at 20k+ RPS
- Loom vs WebFlux trade-off analysis

---

## Q-CORO-001 [bloom: recall] [level: junior]

**Question:** What is a coroutine? Define it in terms of a lightweight thread and explain what "suspension" means at the runtime level.

**Model answer:** A coroutine is a unit of concurrent work that can be suspended and resumed without blocking an OS thread. Unlike a thread (which occupies a native OS thread for its entire lifetime), a coroutine releases its thread when it suspends — the thread is free to execute other coroutines while this one waits.

"Suspension" means the coroutine saves its local variables and the current execution point (the continuation) onto the heap, returns the thread to the dispatcher, and later gets resumed — potentially on a different thread — when the awaited work completes.

Concrete example: two coroutines, one thread:
```
Thread-1: runs coroutine A → A calls delay(1000) → A suspends, saves state to heap
Thread-1: now runs coroutine B → B finishes → 1000ms elapsed
Thread-1: resumes coroutine A from where it left off
```
The OS thread was never blocked. From the developer's perspective the code reads sequentially (no callbacks), but at runtime it is fully non-blocking.

Key numbers: a coroutine takes ~a few hundred bytes on the heap vs ~1 MB stack per Java thread. You can launch millions of coroutines where launching a million threads would crash the JVM.

**Interview trap:** "Aren't coroutines just virtual threads?" — No. Virtual threads (Loom) still occupy a carrier thread during blocking I/O (they just park the carrier, not the OS thread). Coroutines cooperatively suspend and hand the underlying thread back entirely. Coroutines also compose with structured concurrency semantics that virtual threads do not provide out of the box.

**Tags:** coroutine-basics, suspension, threading

---

## Q-CORO-002 [bloom: recall] [level: junior]

**Question:** What does the `suspend` modifier on a function mean? What transformation does the Kotlin compiler perform on a `suspend fun`, and where can it be called from?

**Model answer:** `suspend` marks a function as suspendable: it may pause execution without blocking the thread and resume later. The Kotlin compiler transforms every `suspend fun` into a regular function that takes an implicit `Continuation<T>` parameter — this is continuation-passing style (CPS) transformation. The function becomes a state machine whose states correspond to each suspension point in the body.

What the compiler generates (conceptual sketch):
```kotlin
// Source:
suspend fun fetchUser(id: Long): User {
    val raw = httpClient.get(id)      // suspension point 1
    val parsed = parse(raw)           // suspension point 2
    return parsed
}

// Compiler output (pseudocode):
fun fetchUser(id: Long, continuation: Continuation<User>): Any {
    when (continuation.label) {
        0 -> { continuation.label = 1; return httpClient.get(id, continuation) }
        1 -> { val raw = continuation.result; continuation.label = 2; return parse(raw, continuation) }
        2 -> return continuation.result
    }
}
```

What a `suspend fun` CAN do that a regular function cannot:
- Call other `suspend` functions directly.
- Call `delay()`, `await()`, channel operations — anything that suspends the coroutine.

What it CANNOT do differently:
- It cannot be called from regular (non-suspend) code without a coroutine builder (`launch`, `async`, `runBlocking`).
- It does not automatically make blocking code non-blocking. A `suspend fun` that calls `Thread.sleep()` still blocks the thread.

**Interview trap:** "Does `suspend` make my function non-blocking automatically?" — No. `suspend` is a capability marker. If the body calls blocking JDBC, it is still blocking. `suspend` just enables the compiler to generate the state machine; what you put inside determines whether it actually suspends.

**Tags:** suspend, coroutine-basics, CPS, state-machine

---

## Q-CORO-003 [bloom: recall] [level: junior]

**Question:** What is the difference between `launch` and `async`? What do they return and when do you use each?

**Model answer:** Both are coroutine builders that start a new coroutine, but they differ in return type and intent:

| Builder | Returns | Use when |
|---------|---------|----------|
| `launch` | `Job` | fire-and-forget; you care about side effects, not a result |
| `async` | `Deferred<T>` | you need the result; call `.await()` to get it |

```kotlin
// launch: send a notification, don't wait for result
val job: Job = scope.launch {
    notificationService.send(userId)
}

// async: fetch two things in parallel, combine results
val deferredUser: Deferred<User> = async { userService.find(id) }
val deferredPrice: Deferred<Price> = async { pricingService.get(id) }

val user = deferredUser.await()    // suspends until result ready
val price = deferredPrice.await()  // likely already done
```

With `async`, exceptions are stored in the `Deferred` and rethrown on `.await()`. With `launch`, an unhandled exception propagates to the parent scope immediately (unless a `CoroutineExceptionHandler` is installed).

Important: calling `async { }.await()` immediately is equivalent to calling the suspend function directly — you gain no parallelism. The point of `async` is to start multiple coroutines before calling any `.await()`.

**Interview trap:** "Can I ignore the return value of `async`?" — You can, but you lose the exception. If the coroutine throws and nobody calls `.await()`, the exception is silently swallowed (in a `supervisorScope`) or crashes the parent (in a regular `coroutineScope`). Never fire `async` and discard the `Deferred` unless you have a deliberate reason.

**Tags:** launch, async, coroutine-builders, Deferred

---

## Q-CORO-004 [bloom: recall] [level: junior]

**Question:** What is structured concurrency? Define it and explain why it matters operationally compared to unstructured concurrency (e.g., `GlobalScope`).

**Model answer:** Structured concurrency is the principle that every coroutine has a parent, and a parent does not complete until all its children complete. Coroutines form a tree of scopes; cancellation and exceptions propagate through this tree in predictable ways. You cannot "lose" a coroutine — if you started it in a scope, the scope tracks it.

The tree means:
- Cancel the root scope → all children cancel automatically.
- Child throws → exception propagates to parent (or is isolated if using `supervisorScope`).
- Parent waits → it actually waits for ALL launched children, not just the last awaited one.

`GlobalScope` breaks structured concurrency:
```kotlin
// WRONG: GlobalScope ignores the request lifecycle
@GetMapping("/reco")
suspend fun getReco(@PathVariable userId: Long): Reco {
    GlobalScope.launch { analyticsService.track(userId) }  // leaks on request cancel
    return recsEngine.compute(userId)
}
```
- Coroutine launched in `GlobalScope` lives as long as the application, not the request.
- If the HTTP client disconnects, the request coroutine is cancelled — but the `GlobalScope` coroutine keeps running, burning resources on work nobody will consume.
- Leaks accumulate. Under load this becomes a memory and thread pool exhaustion bug.

Correct: launch inside the request's scope (provided by Spring WebFlux coroutine support) or a service-level `CoroutineScope` with a `SupervisorJob` that is cancelled on shutdown.

**Interview trap:** "When is `GlobalScope` acceptable?" — In narrow cases: top-level application lifecycle tasks (main function init, app-wide background workers explicitly tied to process exit). Never inside request handlers, service methods, or any scope shorter than the JVM process.

**Tags:** structured-concurrency, GlobalScope, scope-lifecycle

---

## Q-CORO-005 [bloom: recall] [level: junior]

**Question:** Describe the four standard Dispatchers: `Default`, `IO`, `Main`, `Unconfined`. When would you use each in a backend service?

**Model answer:** `Dispatchers.Default`:
- Backed by a thread pool sized to the number of CPU cores (minimum 2).
- Use for: CPU-intensive work — JSON parsing, ranking/scoring, sorting, compression.
- Wrong use: blocking I/O will exhaust this small pool fast.

`Dispatchers.IO`:
- Backed by a large elastic thread pool (default limit: 64 threads, configurable via `kotlinx.coroutines.io.parallelism` system property).
- Use for: blocking I/O — JDBC, blocking file access, legacy blocking libraries.
- Shares threads with `Dispatchers.Default` under the hood but enforces a separate limit.

`Dispatchers.Main`:
- Single-thread UI dispatcher. Only available on Android/JavaFX/Swing.
- Backend services: never use this. It throws if the main dispatcher is not installed.

`Dispatchers.Unconfined`:
- Starts the coroutine in the current thread; after the first suspension, resumes in whichever thread completed the suspension.
- Use for: testing or very specific low-overhead scenarios. Never in production handlers — thread of resumption is unpredictable and can be an event loop thread.

Backend rule of thumb:
```kotlin
// CPU work: reranking 200 candidates
withContext(Dispatchers.Default) { reranker.score(candidates) }

// Legacy blocking DB call
withContext(Dispatchers.IO) { jdbcRepo.find(userId) }

// Non-blocking WebClient: no withContext needed — Reactor uses its own scheduler
webClient.get(url).awaitBody<User>()
```

**Interview trap:** "Can I use `Dispatchers.Default` for everything to keep it simple?" — No. Blocking I/O on `Default` starves CPU-bound coroutines and causes latency spikes. `Default` has only ~8 threads on a modern machine; block even 2 of them with JDBC calls and throughput collapses.

**Tags:** dispatchers, threading, performance, IO, Default

---

## Q-CORO-006 [bloom: recall] [level: junior]

**Question:** What is `Flow<T>`? How does it differ from a cold stream vs a hot stream? What are `StateFlow` and `SharedFlow`?

**Model answer:** `Flow<T>` is Kotlin's cold asynchronous stream — a sequence of values emitted over time. "Cold" means the producer code does not run until a collector subscribes, and each collector gets its own independent execution of the producer.

```kotlin
fun numberFlow(): Flow<Int> = flow {
    println("producer started")  // runs per collector, not at declaration
    for (i in 1..5) {
        delay(100)
        emit(i)
    }
}
val f = numberFlow()   // nothing runs yet
f.collect { println(it) }  // now the producer starts
```

Hot streams (`SharedFlow`, `StateFlow`) emit regardless of collectors:

`SharedFlow`: a broadcast channel. Emissions happen whether or not there are subscribers. Subscribers miss emissions that occurred before they subscribed (configurable `replay` buffer). Use for: events, notifications, pub-sub within the same JVM.

`StateFlow`: a `SharedFlow` that always holds the latest value. New collectors immediately receive the current state. Replay = 1, always has a value, emissions deduplicated by equality. Use for: in-memory state that multiple parts of the system observe (e.g., a cache of current model version metadata).

```kotlin
val state = MutableStateFlow(ModelVersion.INITIAL)
state.value = ModelVersion.V2  // all active collectors receive this
```

In WebFlux/SSE: `Flow<T>` maps naturally to `Flux<T>` via `.asFlux()` — each HTTP request gets its own cold stream execution.

**Interview trap:** "Is `Flow` the same as `RxJava Observable`?" — Cold semantics are similar, but `Flow` is built on coroutines: collection is a `suspend` operation, operators are inline functions (no wrapper object per operator), cancellation integrates with structured concurrency. `RxJava Observable` requires manual `dispose()` management.

**Tags:** Flow, cold-stream, hot-stream, SharedFlow, StateFlow

---

## Q-CORO-007 [bloom: understand] [level: regular]

**Question:** Explain `coroutineScope { }` vs `supervisorScope { }`. Show the exact failure-propagation rules for each, with a code example illustrating a case where the choice matters.

**Model answer:** Both builders suspend the caller until all child coroutines finish. They differ only in how child failures propagate.

**`coroutineScope`** — fail-fast:
- Any child throws → scope cancels ALL other children → exception propagates to the caller.
- Use when: all children are required for the result (parallel feature fetching for a recommendation — if one fails, the result is unusable anyway).

```kotlin
suspend fun fetchRecommendation(userId: Long): Reco = coroutineScope {
    val features = async { featureStore.get(userId) }      // if this throws...
    val candidates = async { annIndex.query(userId) }      // ...this is cancelled
    buildReco(features.await(), candidates.await())
    // RuntimeException from featureStore propagates out of coroutineScope
}
```

**`supervisorScope`** — isolated failure:
- A child throwing does NOT cancel siblings. Each child's result must be handled individually.
- Use when: children are independent optional data sources (dashboard panels, optional enrichment).

```kotlin
suspend fun enrichOptional(base: BaseResp): EnrichedResp = supervisorScope {
    val badgesDeferred = async { badgeService.get(base.userId) }   // may fail
    val tagsDeferred = async { tagService.get(base.userId) }       // independent

    val badges = try { badgesDeferred.await() } catch (e: Exception) { emptyList() }
    val tags = try { tagsDeferred.await() } catch (e: Exception) { emptyList() }

    base.copy(badges = badges, tags = tags)  // degraded response is acceptable
}
```

`SupervisorJob` vs `supervisorScope`:
- `supervisorScope` is a builder (scope block, suspends inline).
- `SupervisorJob()` is a `Job` element for a long-lived `CoroutineScope` (service-level scope): `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.

**Interview trap:** "`CoroutineExceptionHandler` installed on a `coroutineScope` child catches the exception, right?" — No. `CoroutineExceptionHandler` only applies to root coroutines (those with no parent) and coroutines launched directly in a scope with a `SupervisorJob`. In a nested `coroutineScope`, the exception propagates to the scope itself regardless.

**Tags:** coroutineScope, supervisorScope, structured-concurrency, exception-propagation

---

## Q-CORO-008 [bloom: understand] [level: regular]

**Question:** How does cancellation work in Kotlin coroutines? Explain cooperative cancellation, what `isActive`, `ensureActive()`, and `NonCancellable` do, and what happens if you swallow `CancellationException`.

**Model answer:** Cancellation in coroutines is cooperative: calling `job.cancel()` sets a cancellation flag, but the coroutine itself must check for it. A coroutine in a tight CPU loop with no suspension points runs to completion even after `cancel()`.

All standard suspension points (`delay()`, `.await()`, `withContext()`, channel operations) check cancellation automatically and throw `CancellationException` if the coroutine has been cancelled.

For CPU-bound loops with no suspension points, check manually:
```kotlin
suspend fun processLargeList(items: List<Item>) = withContext(Dispatchers.Default) {
    for (item in items) {
        ensureActive()          // throws CancellationException if cancelled
        heavyCompute(item)
    }
}
```

`isActive`: a boolean property; `true` if not cancelled. Use when you want to handle cancellation gracefully (break loop, clean up) without throwing.

`ensureActive()`: shorthand for `if (!isActive) throw CancellationException()`. Preferred in tight loops — throws immediately on cancellation.

`CancellationException` is special: the coroutine framework uses it to signal cancellation. It is swallowed at scope boundaries and does NOT trigger error handlers or `CoroutineExceptionHandler`. This means you MUST re-throw it if you catch it:
```kotlin
// WRONG: swallows CancellationException, coroutine can no longer be cancelled
try {
    delay(1000)
} catch (e: Exception) {
    logger.info("interrupted")
    // CancellationException is eaten here — coroutine ignores future cancel() calls
}

// CORRECT:
try {
    delay(1000)
} catch (e: CancellationException) {
    throw e  // re-throw so the framework can propagate cancellation
} catch (e: Exception) {
    logger.error("real error", e)
}
```

`NonCancellable`: a special `Job` context element for cleanup code that must run even during cancellation:
```kotlin
// In a finally block during cancellation, delay() would throw again — use NonCancellable:
try {
    doWork()
} finally {
    withContext(NonCancellable) {
        delay(100)                   // safe — NonCancellable ignores cancellation
        cleanupService.flush()
    }
}
```

**Interview trap:** "What happens when a WebFlux request is cancelled (client disconnects)?" — The `ServerWebExchange` is cancelled, propagating down to the request's coroutine scope. All child coroutines (parallel WebClient calls, DB queries) are cancelled via structured concurrency. No dangling threads doing work nobody will consume — a major practical win over thread-per-request.

**Tags:** cancellation, CancellationException, isActive, ensureActive, NonCancellable

---

## Q-CORO-009 [bloom: understand] [level: regular]

**Question:** How do exceptions propagate in `coroutineScope` vs `supervisorScope`? What is `CoroutineExceptionHandler` for, and what is the critical difference between exception handling for `launch` vs `async`?

**Model answer:** Exception propagation rules:

**`coroutineScope`:** any child exception cancels siblings and re-throws from the scope. The caller of `coroutineScope` catches it with a normal `try/catch`.

**`supervisorScope`:** child exceptions do NOT cancel siblings. They are stored in the child's `Job` and rethrown only when the caller calls `.await()` on that child's `Deferred`.

**`launch` vs `async` — the critical difference:**
- `launch`: an unhandled exception propagates immediately to the parent scope (or to a `CoroutineExceptionHandler` if installed). You cannot catch it at the call site because `launch` returns `Job`, not a result.
- `async`: the exception is stored in the `Deferred`. It is NOT thrown until `.await()` is called. If you never call `.await()`, the exception is silently lost (in `supervisorScope`) or propagates when the `supervisorScope` sees the failed child.

```kotlin
supervisorScope {
    val job = launch {
        throw RuntimeException("launch failure")
        // propagates immediately to scope / CoroutineExceptionHandler
    }

    val deferred = async {
        throw RuntimeException("async failure")
        // stored in Deferred, NOT thrown yet
    }

    // deferred.await() would throw here — but if you skip it, exception is lost
}
```

**`CoroutineExceptionHandler`:** a `CoroutineContext` element that catches unhandled exceptions from `launch` (and `async` failures that bubble up through a `SupervisorJob`). It is a last-resort handler — like `Thread.UncaughtExceptionHandler`. It does NOT prevent propagation; it gets notified after. Useful for logging/alerting at the service scope.

```kotlin
val handler = CoroutineExceptionHandler { _, e ->
    logger.error("Unhandled coroutine exception", e)
    alerting.fire(e)
}
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
```

**Interview trap:** "`CoroutineExceptionHandler` catches exceptions from `async`, right?" — Wrong. It only catches exceptions from `launch`. For `async`, you must catch at the `.await()` call site. This is the single most-asked gotcha about coroutine exception handling in senior interviews.

**Tags:** exception-handling, coroutineScope, supervisorScope, CoroutineExceptionHandler, async, launch

---

## Q-CORO-010 [bloom: understand] [level: regular]

**Question:** What does `withContext` do? How does it differ from `launch`? When would you use it vs `async`?

**Model answer:** `withContext(context) { ... }` switches the coroutine's context for the duration of the block and returns the block's result. It suspends the current coroutine, moves work to the specified context (typically a different dispatcher), and resumes on the original context when done.

```kotlin
suspend fun computeScore(candidates: List<Candidate>): List<ScoredCandidate> =
    withContext(Dispatchers.Default) {         // switch to CPU pool
        candidates.map { scorer.score(it) }   // CPU-intensive work
    }
    // automatically returns to the original dispatcher after the block
```

**How `withContext` differs from `launch`:**
- `withContext` runs in the SAME coroutine, just on a different dispatcher. No new `Job` is created.
- `launch` creates a NEW coroutine (new `Job`) that runs concurrently.
- `withContext` is sequential — the caller suspends and waits for the block to finish.
- `launch` is fire-and-forget (or parallel if you don't await it).

**When to use `withContext` vs `async`:**
- `withContext`: switch dispatcher for a sequential block; you need the result inline.
- `async`: start concurrent work and collect the result later via `.await()`.

```kotlin
// withContext: sequential dispatcher switch
val score = withContext(Dispatchers.Default) { scorer.compute(input) }

// async: parallel work
val scoreDeferred = async(Dispatchers.Default) { scorer.compute(input) }
val priceDeferred = async { pricingService.get(id) }
val score = scoreDeferred.await()
val price = priceDeferred.await()
```

**Interview trap:** "If I wrap a blocking JDBC call in `withContext(Dispatchers.IO)`, is my controller fully non-blocking?" — The event loop thread is freed (the controller coroutine suspends), so yes from the event loop's perspective. But you are still occupying a thread from `Dispatchers.IO`'s pool for the duration. It's better than blocking the event loop, but not as scalable as truly non-blocking I/O (R2DBC, reactive WebClient).

**Tags:** withContext, dispatcher-switching, context, launch-vs-async

---

## Q-CORO-011 [bloom: understand] [level: regular]

**Question:** Explain `Flow` operators `flowOn`, `buffer`, and `conflate`. How does each affect backpressure and the threading model?

**Model answer:** In a `Flow`, backpressure is implicit: `emit()` suspends until the collector is ready. This means by default producer and consumer run sequentially on the same coroutine/thread. The operators below break or reshape that coupling.

**`flowOn(dispatcher)`**: changes the `CoroutineContext` (usually dispatcher) for the upstream part of the flow only. Everything above `flowOn` in the chain runs on the specified dispatcher; everything below runs on the original context. Creates an internal channel buffer between upstream and downstream.

```kotlin
flow { emit(heavyCpuWork()) }     // runs on Dispatchers.Default
    .flowOn(Dispatchers.Default)  // <-- upstream dispatcher
    .map { transform(it) }        // runs on the calling coroutine's dispatcher
    .collect { consume(it) }
```

**`buffer(capacity)`**: introduces an explicit channel between producer and collector. The producer can emit up to `capacity` items ahead of the collector without suspending. Decouples their speeds — improves throughput if producer and consumer have variable speed.

```kotlin
fetchPagesFlow()
    .buffer(50)       // producer can be 50 items ahead
    .collect { sendToDownstream(it) }
```
Risk: if producer is always faster than consumer, the buffer fills and producer blocks anyway, or OOM if unbounded.

**`conflate()`**: drops intermediate values if the collector is busy. The collector only sees the latest value (like a `StateFlow` emission). Use for: metrics, status updates, telemetry — where stale values are useless.

```kotlin
sensorReadingFlow()
    .conflate()       // skip readings while processing the previous one
    .collect { processReading(it) }
```

**Summary table:**

| Operator | Effect | Use case |
|----------|--------|----------|
| `flowOn` | Change upstream dispatcher | CPU work on producer side |
| `buffer(n)` | Decouple producer/consumer speed | Page fetching, I/O + CPU pipeline |
| `conflate()` | Drop intermediates, keep latest | Real-time status/metrics |

**Interview trap:** "Does `flowOn` affect the entire pipeline?" — Only the upstream (above the `flowOn` call). Multiple `flowOn` calls in a chain each affect their respective upstream segment. This lets you build pipelines where different stages run on different dispatchers.

**Tags:** Flow, flowOn, buffer, conflate, backpressure, threading

---

## Q-CORO-012 [bloom: understand] [level: regular]

**Question:** Explain the Reactive Streams specification. What are the four interfaces (Publisher, Subscriber, Subscription, Processor) and what is the backpressure protocol between them?

**Model answer:** Reactive Streams (RS) is a specification (also a Java 9 `java.util.concurrent.Flow` API) defining interoperable async stream processing with non-blocking backpressure. It has four interfaces:

**`Publisher<T>`**: produces a stream of T. Has one method: `subscribe(Subscriber<T>)`.

**`Subscriber<T>`**: consumes the stream. Four methods:
- `onSubscribe(Subscription)` — called once on subscription; the subscriber receives its handle to the stream.
- `onNext(T)` — called for each item.
- `onError(Throwable)` — called on terminal error.
- `onComplete()` — called on terminal success.

**`Subscription`**: the contract between Publisher and Subscriber. Two methods:
- `request(long n)` — Subscriber demands at most `n` more items. This is the backpressure signal.
- `cancel()` — Subscriber cancels the subscription.

**`Processor<T, R>`**: both a `Subscriber<T>` and a `Publisher<R>` — an intermediate transformation stage.

**Backpressure protocol:**
1. Subscriber receives `Subscription` in `onSubscribe`.
2. Subscriber calls `subscription.request(n)` to signal demand.
3. Publisher emits at most `n` items via `onNext`.
4. When more capacity is available, Subscriber calls `request(n)` again.
5. Publisher MUST NOT emit more than demanded. Emitting without demand = protocol violation.

```
Publisher                Subscriber
   |  subscribe()            |
   |<------------------------|
   |  onSubscribe(sub)        |
   |------------------------>|
   |                         |  sub.request(3)
   |<------------------------|
   |  onNext(item1)          |
   |------------------------>|
   |  onNext(item2)          |
   |------------------------>|
   |  onNext(item3)          |
   |------------------------>|
   |                         |  (processing...)
   |                         |  sub.request(5)
   |<------------------------|
```

Project Reactor's `Mono` and `Flux` implement this spec. Kotlin `Flow` achieves the same backpressure goal differently — via coroutine suspension instead of `request(n)`.

**Interview trap:** "What happens in Reactor if a Subscriber never calls `request(n)`?" — The Publisher never emits. The subscription is effectively stalled. This is the "cold" property of Reactor types — nothing happens until you subscribe AND request. Missing `request()` is a bug pattern that manifests as a hanging request with no response.

**Tags:** reactive-streams, Publisher, Subscriber, Subscription, backpressure, Reactor

---

## Q-CORO-013 [bloom: understand] [level: regular]

**Question:** What are `Mono<T>` and `Flux<T>` in Project Reactor? Describe the key operators `map`, `flatMap`, `flatMapMany`, and `zip`. When does each apply?

**Model answer:** `Mono<T>`: a reactive stream of 0 or 1 elements. Represents an async computation of a single optional value. Analogous to `CompletableFuture<Optional<T>>` but composable and lazy.

`Flux<T>`: a reactive stream of 0 to N elements. Represents a sequence of values over time. Analogous to `Stream<T>` but async and lazy.

Both are cold — nothing runs until you subscribe (or until a WebFlux framework subscribes on your behalf).

**Key operators:**

`map(fn)`: synchronous transformation of each element. No new async work; just transforms the value in the current thread context.
```java
userMono.map(user -> user.toDto())
```

`flatMap(fn)`: async transformation — `fn` returns a `Publisher` (Mono or Flux) per element. Inner publishers run concurrently (for `Flux`). This is the most powerful and most dangerous operator: concurrency is implicit and ordering is not preserved by default.
```java
// For each userId, make an async HTTP call — calls happen concurrently
userIdFlux.flatMap(id -> webClient.get().uri("/user/" + id).retrieve().bodyToMono(User.class))
```

`flatMapMany(fn)`: applies when a `Mono` needs to expand into a `Flux`. `fn` receives the single Mono value and returns a `Flux`.
```java
// One userId → stream of recommendations
userMono.flatMapMany(user -> recsService.streamRecs(user))
```

`zip(p1, p2, combiner)`: waits for multiple publishers to emit and combines their values. Like `coroutineScope { async {} async {} }` in coroutine-land.
```java
Mono.zip(userMono, priceMono, (user, price) -> new Response(user, price))
```

**Interview trap:** "`flatMap` vs `concatMap` — what's the difference?" — `flatMap` subscribes to all inner publishers immediately and merges results as they arrive (concurrent, unordered). `concatMap` subscribes to inner publishers one by one, preserving order. Use `concatMap` when order matters or you need to limit concurrency to 1.

**Tags:** Mono, Flux, map, flatMap, flatMapMany, zip, operators, Reactor

---

## Q-CORO-014 [bloom: understand] [level: regular]

**Question:** Explain the bridge between `Mono<T>`/`Flux<T>` and coroutines (`suspend fun` / `Flow<T>`). Which functions cross the bridge in each direction, and when do you need them explicitly?

**Model answer:** Kotlin coroutines and Project Reactor are separate worlds. The `kotlinx-coroutines-reactor` library (bundled with Spring Boot + Kotlin) provides the bridge.

**Reactor → Coroutines:**
```kotlin
// Mono → suspend (single value)
val user: User = userMono.awaitSingle()           // throws if 0 or >1 elements
val userOrNull: User? = userMono.awaitSingleOrNull() // null if empty Mono
val first: User = userMono.awaitFirst()            // first element or throw

// Flux → Flow
val flow: Flow<User> = userFlux.asFlow()
```

`awaitSingle()` suspends the coroutine until the `Mono` completes, then returns the value. Internally it subscribes to the `Mono` and wires the `Subscription` to the coroutine's `Continuation`.

**Coroutines → Reactor:**
```kotlin
// suspend lambda → Mono (cold)
val mono: Mono<User> = mono { userService.find(id) }   // suspend fun called inside

// Flow → Flux
val flux: Flux<User> = userFlow.asFlux()
```

`mono { }` and `flux { }` are coroutine builders returning Reactor types. They are cold — the coroutine only starts when subscribed.

**In a WebFlux controller with Spring's coroutine support**, you rarely need these bridges explicitly — Spring's `DispatcherHandler` recognises `suspend fun` on `@RestController` and wraps it in a `Mono` internally:
```kotlin
@GetMapping("/user/{id}")
suspend fun getUser(@PathVariable id: Long): User =
    userService.find(id)  // Spring generates the Mono subscription under the hood
```

You need the bridge explicitly when:
- Calling Reactor-based library APIs from a coroutine (R2DBC, reactive Redis) — use `.awaitSingle()`.
- Writing WebFlux filters/interceptors that must return `Mono<Void>` — use `mono { }`.
- Integrating existing `Mono`-returning repository interfaces.

**Interview trap:** "What does `Mono.block()` do inside a coroutine?" — It blocks the thread entirely, bypassing coroutine suspension. In a WebFlux service this blocks the event loop thread, which can deadlock the entire server if the thread pool is exhausted. Never call `.block()` in production WebFlux code.

**Tags:** Mono, Flux, awaitSingle, asFlow, mono-builder, reactor-bridge, interop

---

## Q-CORO-015 [bloom: apply] [level: regular]

**Question:** Write a Spring WebFlux Kotlin controller with a `suspend fun` handler that calls three downstream services in parallel via `WebClient` and combines results. Include per-call timeouts. Show idiomatic Kotlin.

**Model answer:**
```kotlin
@RestController
@RequestMapping("/api/recommendations")
class RecsController(
    private val userClient: WebClient,
    private val featureClient: WebClient,
    private val annClient: WebClient,
) {
    companion object {
        private val CALL_TIMEOUT = Duration.ofMillis(200)
    }

    @GetMapping("/{userId}")
    suspend fun getRecommendations(@PathVariable userId: Long): RecsResponse =
        coroutineScope {
            // All three start immediately in parallel
            val userDeferred = async {
                userClient.get()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .bodyToMono<UserProfile>()
                    .timeout(CALL_TIMEOUT)
                    .awaitSingle()
            }

            val featuresDeferred = async {
                featureClient.get()
                    .uri("/features/{id}", userId)
                    .retrieve()
                    .bodyToMono<FeatureVector>()
                    .timeout(CALL_TIMEOUT)
                    .awaitSingle()
            }

            val candidatesDeferred = async {
                annClient.get()
                    .uri("/ann/query/{id}", userId)
                    .retrieve()
                    .bodyToMono<List<Long>>()
                    .timeout(CALL_TIMEOUT)
                    .awaitSingle()
            }

            // Collect results — if any throws, coroutineScope cancels the others
            RecsResponse(
                userId = userId,
                user = userDeferred.await(),
                features = featuresDeferred.await(),
                candidates = candidatesDeferred.await(),
            )
        }
}
```

Key points:
- `coroutineScope { }` — if any call fails, the other two are cancelled automatically.
- Three `async { }` blocks start before any `.await()` is called — true parallelism.
- `.timeout()` on each `Mono` enforces per-call budget independently.
- `.awaitSingle()` bridges `Mono` → coroutine suspension.
- `spring-boot-starter-webflux` + `kotlinx-coroutines-reactor` on classpath — Spring handles wrapping the `suspend fun` in a `Mono`.

To add a global deadline across all three (e.g., 400ms total), wrap with `withTimeout(400)` outside `coroutineScope`.

**Interview trap:** "What if I call `.await()` immediately after each `async` instead of batching?" — Then the three calls execute sequentially, not in parallel. Total latency = T1 + T2 + T3 instead of max(T1, T2, T3). Always start ALL `async` blocks before calling ANY `.await()`.

**Tags:** WebClient, parallel, async, coroutineScope, timeout, suspend-controller

---

## Q-CORO-016 [bloom: apply] [level: regular]

**Question:** Write a `Flow<T>` that reads a paginated downstream endpoint via `WebClient`, filters results, and emits batched chunks of 10 items for an SSE endpoint. Show the idiomatic Spring WebFlux controller.

**Model answer:**
```kotlin
data class Item(val id: Long, val score: Double, val active: Boolean)
data class Batch(val items: List<Item>)

// Cold paginated producer
fun fetchItemsFlow(client: WebClient, userId: Long): Flow<Item> = flow {
    var page = 0
    while (true) {
        val results = client.get()
            .uri { it.path("/items").queryParam("userId", userId).queryParam("page", page).build() }
            .retrieve()
            .bodyToMono<List<Item>>()
            .awaitSingleOrNull() ?: break

        if (results.isEmpty()) break
        results.forEach { emit(it) }
        page++
    }
}

// Manual chunked operator (kotlinx.coroutines 1.6+ has chunked; implement for older):
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

// SSE controller
@RestController
class ItemStreamController(private val client: WebClient) {

    @GetMapping("/stream/items/{userId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamItems(@PathVariable userId: Long): Flow<Batch> =
        fetchItemsFlow(client, userId)
            .filter { it.active && it.score > 0.5 }
            .chunked(10)
            .map { Batch(it) }
            .flowOn(Dispatchers.Default)   // filter/map on Default dispatcher
}
```

Spring WebFlux natively supports `Flow<T>` return types for SSE — no manual `.asFlux()` conversion needed when `kotlinx-coroutines-reactor` is on the classpath.

**Interview trap:** "Do you need to call `.collect()` somewhere for this to work?" — No. Spring's `ResponseBodyResultHandler` subscribes to the `Flow` (via its Reactor bridge) when writing the HTTP response. Returning the `Flow` from the controller is sufficient; calling `.collect()` manually would be a bug (double subscription).

**Tags:** Flow, SSE, WebClient, pagination, chunked, flowOn

---

## Q-CORO-017 [bloom: apply] [level: senior]

**Question:** Why is calling a blocking function inside a `suspend` function a critical bug in a WebFlux service? Describe what happens at the runtime level, how to detect it, and two ways to fix it with different scalability trade-offs.

**Model answer:** WebFlux uses Netty with a small event loop thread pool (typically 2 × CPU cores — e.g., 16 threads on an 8-core machine). These threads process ALL I/O events for ALL concurrent connections.

When a `suspend` function calls a blocking operation (JDBC, `Thread.sleep()`, `Files.readAllBytes()`), the coroutine does NOT suspend — it literally blocks the event loop thread. While that thread is blocked, it cannot process I/O events for ANY other connection.

Failure mode: with 16 event loop threads and 16 concurrent blocking calls, the entire service freezes. Every new incoming request sits in the accept queue. Monitoring shows: event loop threads stuck in `WAITING`/`TIMED_WAITING`, request queue depth climbing, CPU near zero (threads are blocked, not working), p99 latency spiking to connection timeout.

```kotlin
// CRITICAL BUG in a WebFlux service:
@GetMapping("/reco/{userId}")
suspend fun getReco(@PathVariable userId: Long): Reco {
    val user = jdbcTemplate.queryForObject(...)  // blocks event loop thread!
    return recsEngine.compute(user)
}
```

**Detection — BlockHound:**
```kotlin
// In test configuration or dev mode:
BlockHound.install()
// Throws BlockingOperationError if any blocking call is detected on a non-blocking thread
// Works by instrumenting the JVM at agent level — catches blocking even inside libraries
```
Also: thread dump analysis (event loop threads in `BLOCKED` state in stack traces), Reactor's debug agent, custom Micrometer metrics on event loop thread utilization.

**Fix 1 — `withContext(Dispatchers.IO)`** (migration path, acceptable interim):
```kotlin
suspend fun getReco(@PathVariable userId: Long): Reco {
    val user = withContext(Dispatchers.IO) {
        jdbcTemplate.queryForObject(...)   // moves to IO thread pool, event loop is free
    }
    return recsEngine.compute(user)
}
```
Trade-off: event loop is free, but you are still occupying a thread from `Dispatchers.IO` (default 64-thread pool). Under 64+ concurrent blocked calls, you hit the IO pool limit. For brownfield migration, this is correct and safe.

**Fix 2 — replace with non-blocking driver** (preferred for new code):
```kotlin
// Replace JdbcTemplate with R2DBC reactive repository
suspend fun getReco(@PathVariable userId: Long): Reco {
    val user = r2dbcUserRepo.findById(userId).awaitSingle()  // truly non-blocking
    return recsEngine.compute(user)
}
```
Trade-off: requires switching the DB driver (R2DBC), reworking query abstractions (no JPA). Pays off at high concurrency: O(1) threads for waiting on DB, vs O(concurrent requests) threads with `Dispatchers.IO`.

**Interview trap:** "My `suspend` function just calls another `suspend` function that wraps the JDBC call — so it's fine, right?" — Only if that inner function uses `withContext(Dispatchers.IO)` itself. The `suspend` modifier does not make the call non-blocking; the actual blocking work must be dispatched.

**Tags:** blocking-in-coroutine, event-loop, BlockHound, Dispatchers.IO, R2DBC, WebFlux

---

## Q-CORO-018 [bloom: apply] [level: senior]

**Question:** Configure a `WebClient` bean in Kotlin with: connection pool (max 200, pending acquire 500), connect timeout 500ms, response timeout 2s, max in-memory buffer 2MB, default `Authorization` header, and retry on 5xx with exponential backoff (3 attempts, initial delay 100ms, factor 2, jitter 10%). Show idiomatic production-grade code.

**Model answer:**
```kotlin
@Configuration
class WebClientConfig {

    @Bean
    fun recsWebClient(): WebClient {
        val connectionProvider = ConnectionProvider.builder("recs-pool")
            .maxConnections(200)
            .pendingAcquireMaxCount(500)
            .pendingAcquireTimeout(Duration.ofMillis(1000))
            .evictInBackground(Duration.ofSeconds(120))
            .build()

        val httpClient = HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
            .responseTimeout(Duration.ofSeconds(2))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(2, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(2, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${resolveToken()}")
            .build()
    }
}

// Retry at call site — more composable than a global filter:
suspend fun fetchWithRetry(client: WebClient, uri: String): MyResponse =
    client.get()
        .uri(uri)
        .retrieve()
        .onStatus(HttpStatusCode::is5xxServerError) {
            Mono.error(RetryableServerException(it.statusCode()))
        }
        .bodyToMono<MyResponse>()
        .retryWhen(
            Retry.backoff(3, Duration.ofMillis(100))
                .multiplier(2.0)
                .jitter(0.1)
                .filter { it is RetryableServerException }
        )
        .awaitSingle()
```

Configuration notes:
- `CONNECT_TIMEOUT_MILLIS`: TCP connection establishment deadline.
- `responseTimeout`: time from sending the request to receiving the first response byte.
- `ReadTimeoutHandler`: guards against a slow server that stalls mid-response.
- `maxInMemorySize`: prevents OOM when deserializing large response bodies (default 256KB is often too small).
- `pendingAcquireTimeout`: prevents request threads from waiting forever for a connection from the pool when all 200 are in use.
- `evictInBackground`: evicts idle connections, preventing "connection reset" errors after server-side idle timeout.

**Interview trap:** "Will `retryWhen` retry on a connection timeout?" — Only if `ConnectTimeoutException` is included in the `.filter()`. It is not included by default. Be explicit about which exception types are retryable. Also: never retry non-idempotent operations (POST with side effects) without careful thought.

**Tags:** WebClient, configuration, connection-pool, timeout, retry, exponential-backoff

---

## Q-CORO-019 [bloom: apply] [level: senior]

**Question:** Write a test (JUnit 5 + `kotlinx-coroutines-test`) for a `suspend fun` that makes two parallel async calls with `delay`. The test must: use virtual time, verify calls ran concurrently (not sequentially), verify exception from one call cancels the other, and not use `runBlocking`.

**Model answer:**
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import kotlin.test.*

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

        val fetcher = FeatureFetcher(
            storeA = { id -> delay(100); callLog.add("A:$id"); "featureA" },
            storeB = { id -> delay(100); callLog.add("B:$id"); "featureB" },
        )

        val start = currentTime
        val result = fetcher.fetchBoth(42L)
        val elapsed = currentTime - start

        // Sequential would be 200ms; parallel should be ~100ms
        assertEquals(100L, elapsed, "Expected concurrent execution (~100ms), got ${elapsed}ms")
        assertEquals(Pair("featureA", "featureB"), result)
        assertEquals(setOf("A:42", "B:42"), callLog.toSet())
    }

    @Test
    fun `fetchBoth cancels storeB when storeA throws`() = runTest {
        var storeBCancelled = false

        val fetcher = FeatureFetcher(
            storeA = { throw RuntimeException("store A down") },
            storeB = { id ->
                try {
                    delay(500)
                    "featureB"
                } catch (e: CancellationException) {
                    storeBCancelled = true
                    throw e   // re-throw — mandatory
                }
            },
        )

        assertFailsWith<RuntimeException> { fetcher.fetchBoth(1L) }
        assertTrue(storeBCancelled, "storeB must be cancelled when storeA fails")
    }
}
```

Key API:
- `runTest { }`: test coroutine builder that uses `TestCoroutineScheduler` with virtual time. `delay()` inside does not actually wait.
- `currentTime`: virtual clock in milliseconds from `TestCoroutineScheduler`.
- `advanceTimeBy(n)` / `advanceUntilIdle()`: manually advance virtual time if needed (not needed here since `runTest` auto-advances).
- Never use `runBlocking` in coroutine unit tests — it uses real time, making tests slow and `delay()`-based concurrency assertions unreliable.

**Interview trap:** "Why not just measure wall-clock time with `System.currentTimeMillis()`?" — Non-deterministic: CI machines vary in speed, parallel test execution causes interference, and `delay()` in virtual time completes in 0 real milliseconds. Virtual time is the only reliable way to test time-dependent coroutine behavior.

**Tags:** testing, runTest, virtual-time, coroutines-test, concurrency-verification, TestCoroutineScheduler

---

## Q-CORO-020 [bloom: apply] [level: senior]

**Question:** Translate a blocking Spring MVC controller that calls a JDBC repository and a REST client sequentially into idiomatic non-blocking Kotlin coroutines + WebFlux. Show before/after, list the required dependency and driver changes, and explain the latency gain.

**Model answer:** **BEFORE (blocking Spring MVC):**
```kotlin
@RestController
class PriceController(
    private val userService: UserService,       // JdbcTemplate internally
    private val pricingService: PricingService, // RestTemplate internally
) {
    @GetMapping("/price/{userId}/{itemId}")
    fun getPrice(
        @PathVariable userId: Long,
        @PathVariable itemId: Long,
    ): PriceResponse {
        val user = userService.findById(userId)      // blocks thread (~10ms)
        val price = pricingService.getPrice(itemId)  // blocks thread (~30ms)
        return PriceResponse(user.segment, price.amount, price.currency)
    }
    // Total: ~40ms sequential, thread blocked entire time
}
```

**AFTER (coroutines + WebFlux):**
```kotlin
@RestController
class PriceController(
    private val userRepo: R2dbcUserRepository,    // R2DBC, non-blocking
    private val pricingClient: WebClient,          // reactive HTTP client
) {
    @GetMapping("/price/{userId}/{itemId}")
    suspend fun getPrice(
        @PathVariable userId: Long,
        @PathVariable itemId: Long,
    ): PriceResponse = coroutineScope {
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
    // Total: ~max(10ms, 30ms) = ~30ms parallel, event loop thread free during I/O
}
```

**Required changes:**
1. Dependency: `spring-boot-starter-webflux` replaces `spring-boot-starter-web`; add `kotlinx-coroutines-reactor`.
2. DB driver: replace JDBC driver + JPA/JdbcTemplate with `r2dbc-*` driver + Spring Data R2DBC reactive repository.
3. HTTP client: replace `RestTemplate`/Feign with `WebClient`.
4. Method signature: `fun` → `suspend fun` (Spring auto-wraps in `Mono`).
5. Parallelism: wrap independent calls in `coroutineScope { async {} async {} }`.

**Latency gain:** sequential → parallel for independent calls: T_total changes from T_db + T_http to max(T_db, T_http). For 3 parallel calls, this multiplier grows. At high concurrency (10k+ RPS), the main benefit is freeing event loop threads from blocking — far fewer threads required, lower memory footprint, better tail latency under load.

**Interview trap:** "Can I keep JDBC and just use `withContext(Dispatchers.IO)` as an interim?" — Yes, as a brownfield migration step. It correctly moves the block off the event loop. But under very high concurrency you hit the 64-thread `Dispatchers.IO` limit. R2DBC is the correct long-term solution.

**Tags:** migration, MVC-to-WebFlux, R2DBC, WebClient, parallel, coroutines

---

## Q-CORO-021 [bloom: analyze] [level: senior]

**Question:** Describe the complete threading model of a Spring WebFlux + Kotlin coroutines service end-to-end: which thread handles which phase of a request, and what are the danger zones?

**Model answer:** A request journey through a Kotlin + WebFlux service:

**Phase 1 — TCP accept + HTTP decode (Netty I/O thread):**
- Netty's event loop thread accepts the TCP connection, reads bytes from the socket, decodes HTTP headers and body.
- This thread is shared across potentially thousands of connections. Never block it.

**Phase 2 — Controller coroutine launch (Reactor Netty thread → coroutine dispatcher):**
- WebFlux's `DispatcherHandler` identifies the `suspend fun` controller method.
- Spring wraps it in a `Mono` using `mono { coroutine body }`.
- When the `Mono` is subscribed, the coroutine starts on the current Reactor Netty scheduler thread (typically a `parallel` scheduler thread, which IS an event loop thread).

**Phase 3 — Coroutine body running (varies by dispatcher):**
- If the controller calls `WebClient`, the coroutine suspends (`.awaitSingle()` suspends on the Reactor subscription) — event loop thread released.
- If the controller calls `withContext(Dispatchers.IO) { jdbcCall }`, the coroutine moves to an IO pool thread. Event loop thread is free. IO thread blocks for the duration.
- If there is no dispatcher switch and no real suspension point (CPU-only work), the coroutine runs on the Reactor parallel scheduler thread — effectively an event loop thread. Heavy CPU work here is a latency bug (starves other connections).

**Phase 4 — I/O callback + resume (Netty I/O thread → back to coroutine dispatcher):**
- WebClient response arrives on a Netty I/O thread.
- Reactor schedules the continuation on its `parallel` scheduler (or whatever `subscribeOn` specifies).
- The coroutine resumes on that scheduler thread.

**Phase 5 — Response serialization + write-back (Netty I/O thread):**
- The response body is serialized and written to the socket on a Netty I/O thread.

**Danger zones:**

| Zone | Risk | Detection |
|------|------|-----------|
| Phase 3: blocking on event loop thread | Freezes all connections sharing that thread | BlockHound, thread dump |
| Phase 3: CPU-bound work without `withContext(Dispatchers.Default)` | Starvation of other requests | CPU profiling, latency histogram |
| `Dispatchers.Unconfined` in WebFlux | May resume on an event loop thread after suspension | Code review; don't use Unconfined in production |
| `Mono.block()` inside a coroutine | Deadlock if called on event loop thread with small pool | BlockHound |

**Interview trap:** "Does `Dispatchers.Unconfined` run on the event loop thread?" — After the first suspension, it resumes on whichever thread completed the suspension — which COULD be a Netty event loop thread if the suspension was inside a WebClient call. This is why `Unconfined` is dangerous in WebFlux services.

**Tags:** threading-model, Netty, event-loop, Dispatchers, WebFlux, danger-zones

---

## Q-CORO-022 [bloom: analyze] [level: senior]

**Question:** A colleague proposes: "We should use `GlobalScope.launch` for all background tasks in our Spring service — it's simpler than managing custom scopes." Analyze this approach and explain what goes wrong at scale.

**Model answer:** This is a common trap for developers learning coroutines. The simplicity is superficial; the failure modes are severe.

**What `GlobalScope` is:** `GlobalScope` is a coroutine scope whose `Job` lives for the entire JVM process. Coroutines launched there are not children of any request or service scope — they exist outside the structured concurrency tree.

**Failure modes:**

**1. Coroutine leaks on request cancellation:**
```kotlin
// HTTP handler — client disconnects mid-request
@GetMapping("/reco/{userId}")
suspend fun getReco(@PathVariable userId: Long): Reco {
    GlobalScope.launch {
        analyticsService.track(userId)  // still runs after client disconnected
        expensiveEnrichment(userId)     // wastes CPU/network on abandoned work
    }
    return recsEngine.compute(userId)
}
```
Under load with frequent client timeouts (common at Allegro's scale), GlobalScope accumulates thousands of orphaned coroutines doing work for requests nobody is waiting for.

**2. No backpressure on background work:**
`GlobalScope.launch` has no admission control. 10,000 concurrent requests spawn 10,000 background coroutines. The IO thread pool saturates. The application thrashes.

**3. Uncancellable on graceful shutdown:**
At `SIGTERM`, Spring shuts down the web context. Custom scopes cancel cleanly. `GlobalScope` coroutines keep running — they hold DB connections, occupy IO threads, and prevent clean shutdown.

**4. Testing nightmare:**
`GlobalScope` coroutines outlive test scope. Tests that verify side effects via `GlobalScope` are flaky — the coroutine may not have finished when the assertion runs. `runTest` does not control `GlobalScope`.

**Correct alternatives:**

For request-scoped background work:
```kotlin
// Use the request's coroutine scope (provided by Spring WebFlux)
@GetMapping("/reco/{userId}")
suspend fun getReco(@PathVariable userId: Long): Reco = coroutineScope {
    launch { analyticsService.track(userId) }  // cancelled if request cancels
    recsEngine.compute(userId)
}
```

For long-lived service-level background workers:
```kotlin
@Service
class RecsService : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.Default

    @PreDestroy
    fun shutdown() { coroutineContext.cancel() }  // clean shutdown
}
```

**Interview trap:** "Are there ANY legitimate uses of `GlobalScope`?" — Narrowly: truly application-level tasks tied to process lifetime (main() initialization, app-wide health check loops). In a Spring application, a properly managed `CoroutineScope` bean is always preferable because it respects the Spring lifecycle.

**Tags:** GlobalScope, structured-concurrency, leaks, shutdown, scope-management

---

## Q-CORO-023 [bloom: analyze] [level: senior]

**Question:** Compare the full threading model of Spring MVC (thread-per-request) vs Spring WebFlux (event-loop + Reactor). At what request concurrency level does WebFlux start outperforming MVC, and why? What are the failure modes of each model?

**Model answer:** **Spring MVC — thread-per-request:**
- One OS thread per concurrent request.
- Thread is blocked for the full duration of I/O waits (DB query, HTTP call).
- Tomcat default: 200 threads. Kubernetes typical: 1-2 vCPU pods, 200 threads means 200 concurrent requests before queuing.
- Memory: ~1MB stack per thread → 200 threads = 200MB stack memory.
- Failure mode: thread pool exhaustion. At 200 concurrent slow requests, new connections queue. p99 latency spikes. JVM shows hundreds of `WAITING` threads in thread dump.

**Spring WebFlux (Netty event loop):**
- 2N event loop threads (N = CPU cores). These handle I/O state machine transitions, not blocking work.
- Coroutines / Reactor chains suspend on I/O and release the event loop thread.
- Can handle tens of thousands of concurrent connections on 8-16 threads.
- Memory: coroutines are heap-allocated (~hundreds of bytes each). 10k concurrent requests = 10k coroutines = a few MB vs 10 GB for thread-per-request.
- Failure mode: blocking on event loop thread. One blocked thread kills throughput for all connections sharing it.

**At what concurrency does WebFlux win:**
Rule of thumb: WebFlux outperforms MVC when:
- Concurrent connections > thread pool size (typically > 200-500 for a standard MVC deployment), AND
- Work is I/O bound (not CPU bound — WebFlux doesn't help with CPU work).
- For I/O-bound workloads at ~500-1000+ concurrent requests, WebFlux provides dramatically better throughput and tail latency.
- Below ~200 concurrent requests, Spring MVC is simpler and often has lower per-request latency (no reactive subscription overhead).

**Allegro recsys context:** serving endpoints at 20k+ RPS with sub-50ms p99 budgets, with each request making 3-5 parallel downstream calls. This is exactly the workload where WebFlux shines: high concurrency, I/O-bound, parallel composition.

**Practical benchmark numbers** (approximate, varies by workload):
| Metric | Spring MVC (200 threads) | WebFlux + Coroutines |
|--------|--------------------------|----------------------|
| Max concurrent I/O-bound requests | ~200 | ~50,000+ |
| Memory at 1000 concurrent requests | ~1GB stack | ~50MB heap |
| p99 at 2000 concurrent (I/O bound) | high (queuing) | stable |
| p99 at 100 concurrent | low | low |

**Interview trap:** "Does switching to WebFlux automatically give you 100x throughput?" — No. If your bottleneck is CPU (scoring, ranking) or DB (connection pool exhaustion), WebFlux doesn't help. The gain is specifically for I/O wait time at high concurrency. Also: a single blocking call on the event loop can undo all the gains.

**Tags:** threading-model, MVC-vs-WebFlux, concurrency, performance, event-loop, RPS

---

## Q-CORO-024 [bloom: analyze] [level: senior]

**Question:** A senior engineer reviews your code: "You're using `async` where `launch` would do — that allocates a `Deferred` unnecessarily, and you have to remember to `.await()`." Analyze when this critique is right vs when `async` is the correct tool.

**Model answer:** The critique is correct about the mechanics: `async` returns a `Deferred<T>` (a heap-allocated `Job` subtype), and every `.await()` call site is a place where you can forget to call it, accidentally swallow exceptions, or await after cancellation.

**When the critique is right — use `launch` instead:**

Fire-and-forget side effects:
```kotlin
// BAD: async with ignored Deferred, exception swallowed in supervisorScope
supervisorScope {
    val d1 = async { analyticsService.track(event) }  // nobody calls d1.await()
    val d2 = async { cacheService.invalidate(key) }   // same — exceptions lost
}

// GOOD: launch for side effects
supervisorScope {
    launch { analyticsService.track(event) }   // exception propagates to scope
    launch { cacheService.invalidate(key) }
}
```

Immediate `.await()` (pointless parallelism):
```kotlin
val result = async { compute() }.await()  // same as just: compute()
// pointless — no parallelism gained, extra Deferred allocated
```

**When `async` is genuinely correct:**

1. You need the return value AND want to parallelize collection before awaiting:
```kotlin
coroutineScope {
    val userD = async { fetchUser(id) }
    val priceD = async { fetchPrice(id) }
    // True parallelism — both start before either is awaited
    Response(userD.await(), priceD.await())
}
```

2. You want independent exception handling per child (in `supervisorScope`):
```kotlin
supervisorScope {
    val a = async { riskyServiceA() }
    val b = async { riskyServiceB() }
    val aResult = runCatching { a.await() }.getOrNull()
    val bResult = runCatching { b.await() }.getOrNull()
}
```

3. Deferred computation — start work early, collect result later when needed.

**Decision rule:**
- Need a return value + parallelism: `async`.
- Side effects only: `launch`.
- Single sequential call: just call the suspend function directly.

**Interview trap:** "`async` is strictly more powerful than `launch`, so why not always use `async`?" — Power comes with responsibility. A forgotten `.await()` swallows exceptions silently (in supervisorScope). `launch` fails loudly. In code review: if you see `async` without a corresponding `.await()`, it is almost always a bug.

**Tags:** async, launch, Deferred, code-review, exception-handling

---

## Q-CORO-025 [bloom: analyze] [level: master]

**Question:** "We should migrate our Kotlin + WebFlux service to virtual threads now that Spring Boot 3.2+ ships them." Analyze the full trade-off for a high-RPS service: what WebFlux + coroutines gives you that virtual threads alone cannot, and when virtual threads are genuinely better.

**Model answer:** This is a legitimate engineering question. The answer is nuanced and depends on specific service characteristics.

**What virtual threads (Loom) give you:**
- Write blocking code, get concurrency — no reactive plumbing, no `withContext(Dispatchers.IO)`.
- Eliminates the #1 WebFlux bug: accidental blocking on event loop thread. Virtual threads park safely under blocking I/O.
- Spring Boot 3.2+: `spring.threads.virtual.enabled=true` is a one-line migration.
- Kotlin coroutines run fine on virtual threads — you get structured concurrency AND cheap blocking.
- For legacy codebases with JDBC/blocking libraries, virtual threads are dramatically simpler.

**What WebFlux + Reactor gives that virtual threads alone do NOT:**

1. **Reactive Streams backpressure:** `Flux` with `limitRate`, `onBackpressureBuffer`, demand-driven `request(n)` protocol. Virtual threads have no equivalent — if your service streams ranked candidates downstream and the consumer is slow, you need Reactor's backpressure operators.

2. **Non-blocking I/O at wire level:** Netty's event loop processes I/O with O(1) threads regardless of connection count. Virtual threads under blocking I/O still consume O(active I/O operations) parked carrier threads. At 50k+ concurrent slow connections, this matters.

3. **Operator fusion and composition:** Reactor pipelines can fuse adjacent operators to eliminate intermediate allocations (macro-fusion). This is invisible to the developer but visible in GC pause profiles at Allegro's scale.

4. **Reactive library ecosystem:** R2DBC, reactive Redis, reactive Kafka — these are Reactor-native. Switching to virtual threads means switching back to blocking drivers, losing connection-pool optimizations tuned for reactive use.

**When virtual threads are genuinely better:**
- New greenfield services with no streaming/backpressure requirements.
- Team pain point is blocking-in-coroutine bugs (virtual threads eliminate the root cause).
- Moderate load (< ~20-50k RPS per instance) where event-loop vs virtual-thread difference is not measurable.
- Legacy blocking libraries that are hard to replace (JDBC, blocking Redis clients) — virtual threads are the pragmatic answer.

**Recommendation for a senior engineer:**
1. Do NOT migrate opportunistically. Run shadow load tests with `spring.threads.virtual.enabled=true` on a canopy instance at production traffic.
2. If the service uses `Flux` with backpressure operators or SSE with flow control → stay on WebFlux.
3. If the service has no streaming and the team's biggest pain is reactive complexity → virtual threads are worth piloting.
4. New services at 2025+: virtual threads are a reasonable default unless streaming/backpressure is required.
5. Coroutines remain valuable in either model — they provide structured concurrency that virtual threads do not.

**Interview trap:** "Virtual threads are strictly better than event loops, right?" — No. An event loop is O(1) threads for I/O waiting; virtual threads are O(active I/O) parked threads. Under extreme concurrent slow connection counts (e.g., 100k open SSE connections), virtual threads use more memory. Netty's event loop remains superior for that scenario.

**Tags:** loom, virtual-threads, migration, trade-offs, backpressure, WebFlux, analyze

---

## Q-CORO-026 [bloom: analyze] [level: master]

**Question:** Explain how the Kotlin compiler transforms a `suspend fun` with multiple suspension points into a concrete state machine. What does the generated bytecode structure look like, what is a `Continuation`, and what are the performance implications of CPS transformation?

**Model answer:** The Kotlin compiler transforms every `suspend fun` into a class implementing `Continuation<Unit>` (or the return type). The transformation is a desugaring of coroutine suspension into explicit state machine transitions.

**What the compiler generates (pseudocode):**
```kotlin
// Source:
suspend fun fetchAndProcess(id: Long): Result {
    val raw = fetch(id)       // suspension point 0 → 1
    val parsed = parse(raw)   // suspension point 1 → 2
    return Result(parsed)
}
```

Compiler output (conceptual Java-ish bytecode):
```java
// The function becomes a state machine class + a static method
class FetchAndProcessContinuation(
    val completion: Continuation<Result>  // parent continuation (caller)
) : ContinuationImpl(completion) {
    var label: Int = 0    // current state
    var raw: Any? = null  // stored intermediate results between suspensions
}

static Object fetchAndProcess(long id, Continuation<Result> $completion) {
    FetchAndProcessContinuation cont;
    if ($completion instanceof FetchAndProcessContinuation) {
        cont = (FetchAndProcessContinuation) $completion;
    } else {
        cont = new FetchAndProcessContinuation($completion);  // first call
    }

    switch (cont.label) {
        case 0:
            cont.label = 1;
            Object result = fetch(id, cont);  // pass continuation as callback
            if (result == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED;
            // fall through if fetch completed synchronously

        case 1:
            cont.raw = cont.result;  // retrieve result stored by fetch()
            cont.label = 2;
            Object result2 = parse(cont.raw, cont);
            if (result2 == COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED;

        case 2:
            return new Result(cont.result);
    }
}
```

**`Continuation<T>` interface:**
```kotlin
interface Continuation<in T> {
    val context: CoroutineContext
    fun resumeWith(result: Result<T>)  // called by the resuming thread
}
```
When an async operation completes, it calls `continuation.resumeWith(Result.success(value))`, which re-enters the state machine at the correct label.

**Performance implications:**

1. **Allocation on first call:** one `Continuation` object is allocated per `suspend fun` invocation. For hot paths this is measurable GC pressure.

2. **State stored on heap, not stack:** local variables between suspension points are fields of the `Continuation` object. This is why coroutines can be paused/resumed — but it means every suspension point copies live locals to heap.

3. **Synchronous fast-path:** if a suspend function never actually suspends (e.g., cache hit, data already available), the state machine returns immediately without heap allocation of a suspended state. Kotlin's runtime detects this via the `COROUTINE_SUSPENDED` sentinel.

4. **No boxing for primitive returns (mostly):** Kotlin's inline functions and `@InlineClass` reduce boxing, but `Continuation<T>` generics can force boxing for primitive types. For very hot paths, this matters.

5. **Deep call stacks:** each `suspend fun` call frame becomes a chained `Continuation`. Deep chains (like a pipeline of 10 operators on a `Flow`) create chains of `Continuation` objects. Stack overflow is impossible, but GC overhead grows.

**Practical implication for a senior engineer:** avoid unnecessary `suspend` markers on functions that never actually suspend — they add state machine overhead for nothing. Profile GC if coroutine allocation rates are high on critical hot paths.

**Interview trap:** "Since CPS transformation eliminates the call stack, does that mean coroutines never stack overflow?" — Correct that coroutines don't stack overflow during suspension (state is on heap). But synchronous portions between suspension points DO use the call stack. A `suspend fun` that calls 10,000 regular (non-suspend) functions recursively before its first suspension point WILL stack overflow.

**Tags:** CPS, state-machine, Continuation, compiler-transformation, performance, internals

---

## Q-CORO-027 [bloom: analyze] [level: master]

**Question:** Describe `CoroutineScope` and `Job` lifecycle in a Spring service bean. How should you wire a service-scoped `CoroutineScope`, handle graceful shutdown, and what happens if a child coroutine throws in a `SupervisorJob`-backed scope?

**Model answer:** A Spring service that launches background coroutines needs a managed `CoroutineScope` — one that starts when the bean initializes and cancels cleanly when the application shuts down.

**Correct pattern:**
```kotlin
@Service
class RecsBackgroundService(
    private val featureStore: FeatureStoreClient,
    private val cache: RecsCache,
) : CoroutineScope {

    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext =
        supervisorJob + Dispatchers.Default + CoroutineName("RecsBackground")

    @PostConstruct
    fun startBackgroundWorkers() {
        // Periodic cache warm-up — runs independently, failures don't kill each other
        launch {
            while (isActive) {
                try {
                    val snapshot = featureStore.fetchAll()
                    cache.update(snapshot)
                } catch (e: Exception) {
                    logger.warn("Cache warm-up failed, will retry", e)
                }
                delay(60_000)
            }
        }

        launch {
            while (isActive) {
                try { cache.evictStale() } catch (e: Exception) { /* log */ }
                delay(300_000)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Cancelling background coroutines")
        supervisorJob.cancel()   // cancels ALL children; they see CancellationException at next suspension
        // For graceful drain, optionally: runBlocking { supervisorJob.join() }
    }
}
```

**`Job` lifecycle states:**
`New → Active → Completing → Completed`  
`Active → Cancelling → Cancelled`

`SupervisorJob` changes child failure semantics: a child throwing does NOT cancel the `SupervisorJob` or its siblings. Each child's failure is isolated. The `CoroutineExceptionHandler` installed on the scope receives the exception.

**What happens when a child throws in a `SupervisorJob`-backed scope:**
1. The child `Job` transitions to `Cancelled(cause=exception)`.
2. The `SupervisorJob` is NOT affected — its state remains `Active`.
3. Sibling coroutines continue running.
4. The `CoroutineExceptionHandler` (if installed) is invoked with the exception.
5. If no `CoroutineExceptionHandler` is installed, the exception is passed to `Thread.UncaughtExceptionHandler` (JVM default — logged to stderr).

**Without `SupervisorJob` (regular `Job`):**
One child failure cancels the entire scope — all siblings receive `CancellationException`. For independent background workers this is almost never what you want.

**Interview trap:** "Does `supervisorJob.cancel()` in `@PreDestroy` wait for children to finish?" — No. `cancel()` sends cancellation signals but returns immediately. If you need to wait for graceful completion (e.g., flush in-flight writes to DB before shutdown), you must call `runBlocking { supervisorJob.join() }` after `cancel()`, or use a shutdown timeout.

**Tags:** CoroutineScope, Job, SupervisorJob, Spring-lifecycle, shutdown, PostConstruct, PreDestroy

---

## Q-CORO-028 [bloom: analyze] [level: master]

**Question:** Compare error handling strategies in a coroutine/reactive stack: `Mono.onErrorResume`, coroutine `try/catch`, `Result<T>`, and Arrow `Either<E, A>`. When does each model break down, and what is the recommended approach for a domain-rich senior backend?

**Model answer:** Four tools on a spectrum from implicit to explicit error modeling:

**`Mono.onErrorResume(fn)` — Reactor pipeline recovery:**
```kotlin
userMono.onErrorResume(NotFoundException::class.java) { Mono.just(User.ANONYMOUS) }
```
When to use: inline recovery within a Reactor chain, library interop code that must return a `Mono`.
Breaks down: mixes business logic with stream operators; error type erasure (must use class reference); forces you to stay in Reactor's API even when you have a coroutine context. Do not use when you have `awaitSingle()` available — `try/catch` is cleaner.

**Coroutine `try/catch` — standard exception handling:**
```kotlin
val user = try {
    userService.find(id)
} catch (e: UserNotFoundException) {
    User.ANONYMOUS
}
```
When to use: genuinely exceptional conditions (infrastructure failures, programming errors), local error recovery.
Breaks down: exceptions are non-local — they can silently cross many async call frames. For expected business outcomes (user not found is EXPECTED), using exceptions as control flow is an anti-pattern — it makes the signature lie (return type says `User`, actual behavior includes a failure path). Also: catching `Exception` too broadly swallows `CancellationException`.

**`Result<T>` — Kotlin stdlib:**
```kotlin
suspend fun findUser(id: Long): Result<User> = runCatching { repo.find(id) }

// Caller must handle:
findUser(id)
    .getOrElse { User.ANONYMOUS }
    .also { if (it is Failure) logger.warn("User not found", it.exceptionOrNull()) }
```
When to use: force callers to handle failures explicitly; errors cross async boundaries (coroutine → callback → continuation); no Arrow dependency available.
Breaks down: error type is always `Throwable` — no domain error modeling. Callers cannot distinguish `UserNotFoundException` from `DatabaseConnectionException` without `instanceof` checks. Composition via `.map {}` / `.flatMap {}` on `Result` is verbose.

**Arrow `Either<E, A>` — typed domain error:**
```kotlin
sealed interface UserError { data class NotFound(val id: Long) : UserError; object Unavailable : UserError }

suspend fun findUser(id: Long): Either<UserError, User> = either {
    repo.findById(id) ?: raise(UserError.NotFound(id))
}

// Caller — exhaustive handling enforced by the type system:
findUser(id).fold(
    ifLeft = { error -> when (error) {
        is UserError.NotFound -> User.ANONYMOUS
        UserError.Unavailable -> throw ServiceUnavailableException()
    }},
    ifRight = { user -> process(user) },
)
```
When to use: rich domain with multiple typed failure modes; you want the function signature to tell the full story; team is committed to functional style.
Breaks down: requires Arrow dependency; ALL collaborators in the call chain must speak `Either` or you need adapters at boundaries; learning curve is real; `either { }` DSL has a few gotchas (nested `bind()`, interaction with coroutine cancellation).

**Decision matrix for a senior service:**
| Scenario | Recommended |
|----------|-------------|
| Infrastructure failure (timeout, 5xx) | `try/catch` + log + degraded response |
| Expected business outcome (not found) | `Either` or `Result` — NOT exceptions |
| Reactor chain inline fallback | `onErrorResume` only at library interop boundaries |
| Domain-heavy service, multiple typed errors | Arrow `Either` if team buys in |
| Simple service, no Arrow | `Result<T>` |

**Interview trap:** "Should I never use exceptions in a coroutine service?" — Exceptions are correct for genuinely unexpected conditions (infrastructure failures, programming errors). The controversy is about expected business outcomes. Use typed returns for expected failures; exceptions for unexpected ones.

**Tags:** error-handling, Result, Either, Arrow, onErrorResume, exception-handling, domain-modeling
