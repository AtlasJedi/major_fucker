# Java Concurrency — question bank

> Java concurrency is a mandatory senior topic. Interviewers test it relentlessly because bugs are subtle, non-deterministic, and catastrophic in production. This bank covers the full stack: JMM foundations, the classic primitives (synchronized/volatile/atomics), the java.util.concurrent toolkit (locks, executors, futures, collections, synchronizers), and modern features (virtual threads, structured concurrency). Every question is calibrated for a 5-year engineer who needs to demonstrate operational understanding, not just API recollection.

## Scope

- Thread lifecycle (NEW/RUNNABLE/BLOCKED/WAITING/TIMED_WAITING/TERMINATED)
- Runnable vs Callable vs Future — differences and trade-offs
- synchronized keyword: intrinsic monitor lock, reentrancy, object vs class lock
- volatile: visibility guarantee, happens-before, what it does NOT give you
- Java Memory Model (JMM): happens-before, reordering, visibility failures
- Atomics: AtomicInteger/Long/Reference, CAS internals, ABA problem, LongAdder vs AtomicLong
- Lock/ReentrantLock/ReadWriteLock/StampedLock + Condition — when to prefer over synchronized
- ExecutorService and thread pools: core/max/queue/keepAlive, rejection policies
- ThreadPoolExecutor internals: work queue, worker lifecycle, sizing heuristics (CPU vs IO)
- CompletableFuture: thenApply vs thenCompose, exceptionally/handle, allOf/anyOf, supplyAsync executor choice
- ConcurrentHashMap: segment/striped locking, compute/merge, null prohibition, size() semantics
- BlockingQueue: ArrayBlockingQueue vs LinkedBlockingQueue, producer-consumer pattern
- Synchronizers: CountDownLatch, CyclicBarrier, Semaphore, Phaser
- ThreadLocal: usage, inheritance, memory leaks in thread pools
- Deadlock, livelock, starvation: detection and prevention
- False sharing: CPU cache lines, @Contended
- Virtual threads (Java 21): carrier threads, pinning, when to use vs platform threads
- Structured concurrency: StructuredTaskScope, shutdown-on-failure/success

---

## Q-JCON-001 [bloom: recall] [level: junior]
**Question:** What are the six states in the Java thread lifecycle, and what triggers transitions between them?

**Model answer:** Java threads cycle through six states defined in `Thread.State`:

| State | Meaning | How you get here |
|---|---|---|
| `NEW` | Thread object created, not yet started | `new Thread(...)` |
| `RUNNABLE` | Running or ready to run on CPU | `thread.start()`, or waking from blocked/waiting |
| `BLOCKED` | Waiting to acquire a monitor lock | Trying to enter a `synchronized` block held by another thread |
| `WAITING` | Indefinitely waiting for another thread's action | `Object.wait()`, `Thread.join()`, `LockSupport.park()` |
| `TIMED_WAITING` | Waiting with a timeout | `Thread.sleep(ms)`, `Object.wait(ms)`, `Thread.join(ms)`, `LockSupport.parkNanos()` |
| `TERMINATED` | Run method finished (normally or via exception) | `run()` returns or throws |

Key point: `RUNNABLE` includes both "actually running on CPU" and "in the OS run queue waiting for a CPU slice". The JVM does not distinguish these. `BLOCKED` is specific to monitor lock acquisition — park-based waiting (e.g., `ReentrantLock`) shows up as `WAITING`, not `BLOCKED`.

**Interview trap:** "Can you restart a terminated thread?" — No. Once `TERMINATED`, a thread is dead. You must create a new `Thread` instance. Calling `start()` on a terminated thread throws `IllegalThreadStateException`.

**Tags:** thread, lifecycle, state

---

## Q-JCON-002 [bloom: recall] [level: junior]
**Question:** What is the difference between `Runnable` and `Callable`?

**Model answer:** Both represent a unit of work to execute on a thread, but they differ in return type and exception handling:

| | `Runnable` | `Callable<V>` |
|---|---|---|
| Method | `void run()` | `V call() throws Exception` |
| Return value | None | Returns `V` |
| Checked exceptions | Cannot declare any | Can throw any checked exception |
| Submit to `ExecutorService` | `execute()` or `submit()` | `submit()` only |
| Result type | `Future<?>` (result is null) | `Future<V>` |

`Runnable` predates Java 5 (it is in `java.lang`). `Callable` was introduced with the Executor framework. When you need the result of an async computation or need to propagate checked exceptions, use `Callable`. Fire-and-forget work fits `Runnable`.

```java
// Runnable — no return
executor.execute(() -> sendEmail(msg));

// Callable — returns a value, can throw checked exception
Future<Order> future = executor.submit(() -> fetchOrder(id));
Order order = future.get(); // blocks; throws ExecutionException wrapping the Callable's exception
```

**Interview trap:** "What does `Future.get()` throw?" — `InterruptedException` (if the waiting thread is interrupted) and `ExecutionException` (wrapping the exception thrown by the task). Many devs forget to unwrap `ExecutionException.getCause()` when logging or handling the real failure.

**Tags:** runnable, callable, future, executor

---

## Q-JCON-003 [bloom: recall] [level: junior]
**Question:** What does the `volatile` keyword guarantee, and what does it NOT guarantee?

**Model answer:** `volatile` on a field gives exactly two guarantees:

1. **Visibility:** every write to a volatile field is immediately flushed to main memory; every read re-fetches from main memory. No CPU-level register or store-buffer caching of that field.
2. **Happens-before:** a write to a volatile field happens-before every subsequent read of that same field by any thread. This prevents reordering of instructions around volatile accesses.

What `volatile` does NOT give you:
- **Atomicity for compound operations.** `volatile long counter; counter++;` is a read-modify-write — three separate operations. Two threads can interleave and lose an increment. Use `AtomicLong` or `synchronized` instead.
- **Mutual exclusion.** Multiple threads can read and write concurrently; only individual reads/writes are atomic (for 32-bit types and references, always; for `long`/`double` without volatile, not guaranteed by JLS).

Classic valid use: a shutdown flag:
```java
private volatile boolean stopRequested = false;

// Writer thread:
void stop() { stopRequested = true; }

// Worker thread loop:
while (!stopRequested) { doWork(); }
```
Without `volatile`, the JVM is allowed to hoist the read of `stopRequested` out of the loop (it appears loop-invariant), and the worker thread never sees the update.

**Interview trap:** "Is `volatile long` atomic?" — Yes, for individual reads and writes. Without `volatile`, JLS allows 64-bit `long`/`double` reads/writes to be non-atomic (word tearing). With `volatile`, they are guaranteed atomic. But `volatile long counter; counter++` is still NOT atomic.

**Tags:** volatile, visibility, jmm, happens-before

---

## Q-JCON-004 [bloom: recall] [level: junior]
**Question:** What is an intrinsic lock (monitor lock) in Java, and what does `synchronized` protect?

**Model answer:** Every Java object has an intrinsic lock (monitor). `synchronized` uses it to enforce mutual exclusion.

Three forms:
```java
// 1. Instance method — lock is `this`
public synchronized void increment() { count++; }

// 2. Static method — lock is the Class object (MyClass.class)
public static synchronized void register() { ... }

// 3. Block — lock is an explicit object reference
private final Object lock = new Object();
public void update() {
    synchronized (lock) { ... }
}
```

Properties:
- **Mutual exclusion:** at most one thread holds the monitor at a time. Others block.
- **Reentrancy:** a thread that already holds a lock can re-enter `synchronized` blocks that require the same lock without deadlocking. This allows recursive calls and calling `synchronized` super methods.
- **Visibility (happens-before):** on unlock, all writes are flushed; on lock, all previously flushed writes become visible. So `synchronized` subsumes `volatile`'s visibility guarantee.

Prefer `private final` lock objects over `synchronized(this)` — external code can synchronize on `this` and create unintended contention or deadlock.

**Interview trap:** "Does `synchronized` prevent all visibility issues?" — Yes, for code inside the synchronized block, but only between threads that synchronize on the SAME object. If thread A locks on `objA` and writes a field, and thread B only locks on `objB`, B may not see the write.

**Tags:** synchronized, monitor, intrinsic-lock, reentrancy

---

## Q-JCON-005 [bloom: recall] [level: junior]
**Question:** What is `ExecutorService` and why use it instead of creating raw `Thread` objects?

**Model answer:** `ExecutorService` decouples task submission from thread management. Instead of `new Thread(task).start()` you submit tasks to a pool that reuses threads.

```java
ExecutorService pool = Executors.newFixedThreadPool(8);
Future<String> future = pool.submit(() -> fetchData());
pool.shutdown();         // stop accepting new tasks
pool.awaitTermination(10, TimeUnit.SECONDS);
```

Advantages over raw threads:
1. **Thread reuse** — creating threads is expensive (~1 MB stack, OS overhead). A pool amortizes this cost.
2. **Bounded concurrency** — a fixed pool prevents thread explosion under load.
3. **Lifecycle management** — `shutdown()`, `shutdownNow()`, `awaitTermination()`.
4. **Task result handling** — `submit()` returns `Future<T>`.
5. **Exception handling** — uncaught task exceptions are wrapped in `ExecutionException`, retrievable from `Future.get()`.

Factory methods in `Executors`:
- `newFixedThreadPool(n)` — fixed n threads, unbounded `LinkedBlockingQueue`.
- `newCachedThreadPool()` — grows/shrinks as needed; dangerous under sustained load.
- `newSingleThreadExecutor()` — serial execution, guaranteed ordering.
- `newScheduledThreadPool(n)` — for delayed/periodic tasks.

**Production note:** Never use `newFixedThreadPool` or `newCachedThreadPool` from `Executors` in production without understanding their queue strategy. Prefer constructing `ThreadPoolExecutor` directly so you control queue capacity, rejection policy, and thread naming.

**Interview trap:** "What happens if you forget to call `shutdown()`?" — The JVM will not exit normally as long as non-daemon threads in the pool are alive. The application appears to hang after `main()` returns.

**Tags:** executorservice, thread-pool, executor

---

## Q-JCON-006 [bloom: recall] [level: junior]
**Question:** What are `CountDownLatch` and `CyclicBarrier`, and how do they differ?

**Model answer:** Both are coordination synchronizers but with different semantics:

**CountDownLatch:** a one-shot counter. One or more threads wait at `await()` until other threads call `countDown()` enough times to reach zero. Cannot be reset.
```java
CountDownLatch ready = new CountDownLatch(3); // wait for 3 workers
// Worker threads:
doWork(); ready.countDown();
// Coordinator:
ready.await(); // blocks until count == 0
```
Use: waiting for N services to start, waiting for N tasks to finish (but prefer `CompletableFuture.allOf` for this today).

**CyclicBarrier:** a reusable rendezvous point. A fixed number of threads all call `await()`, and they are all released together once the last one arrives. Can be reset and reused.
```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("phase done"));
// Each of 3 threads:
doPhaseWork();
barrier.await(); // all 3 block until all arrive, then optional action runs, all continue
```
Use: iterative parallel algorithms where all threads must synchronize between phases.

Key difference: `CountDownLatch` is one-way (threads count down and wait); `CyclicBarrier` is symmetric (all parties wait for each other). `CountDownLatch` cannot be reused; `CyclicBarrier` can.

**Interview trap:** "What happens if a thread waiting at `CyclicBarrier.await()` is interrupted?" — `BrokenBarrierException` is thrown to all waiting threads, marking the barrier as broken. Subsequent calls throw `BrokenBarrierException` too. Must create a new barrier after a break.

**Tags:** countdownlatch, cyclicbarrier, synchronizer

---

## Q-JCON-007 [bloom: understand] [level: regular]
**Question:** Explain the Java Memory Model (JMM) and the happens-before relationship. Why is it needed?

**Model answer:** Modern CPUs and JIT compilers reorder instructions for performance. Without a memory model, a write on thread A may never be visible to thread B, or visible in an unexpected order, even without a data race. The JMM defines when writes by one thread are guaranteed to be seen by another.

**Happens-before (HB):** a formal ordering. If action X happens-before action Y, then all side effects of X (writes to variables) are visible to Y. Key rules:

1. **Program order:** within one thread, each action HB the next.
2. **Monitor lock:** unlock of a monitor HB every subsequent lock of that monitor.
3. **Volatile:** write to a volatile field HB every subsequent read of that field.
4. **Thread start:** `Thread.start()` HB any action in the new thread.
5. **Thread join:** all actions in thread T HB the return of `T.join()`.
6. **Transitivity:** if X HB Y and Y HB Z, then X HB Z.

Without establishing HB, the JMM permits compilers and CPUs to cache writes, reorder stores, and expose partially-initialized objects. Classic failure:
```java
// Thread A:
result = compute();   // (1)
ready = true;         // (2) — if ready is NOT volatile, (1) and (2) can be reordered

// Thread B:
while (!ready) {}     // may spin forever, or see ready=true but stale result
use(result);
```
If `ready` is `volatile`, write (2) HB read of `ready` by B, and because program order is also HB, (1) HB (2) HB B's read of `ready` HB B's use of `result`. Transitivity gives B a correct view.

**Interview trap:** "Does `synchronized` give happens-before?" — Yes. Unlock HB lock of the same monitor. But it only connects threads that synchronize on the SAME object. Using different locks leaves no HB relationship between the writes.

**Tags:** jmm, happens-before, visibility, memory-model, volatile

---

## Q-JCON-008 [bloom: understand] [level: regular]
**Question:** How does `ConcurrentHashMap` achieve thread safety, and how does it differ from `Hashtable` and `Collections.synchronizedMap`?

**Model answer:** `ConcurrentHashMap` (CHM) uses a fundamentally different strategy than the legacy alternatives:

**Java 7:** segment-based locking (16 segments by default). Each segment is an independent `ReentrantLock`-protected sub-map. Reads are mostly lock-free (volatile reads); writes lock only the owning segment.

**Java 8+:** per-bucket synchronization using `synchronized` on the first node of each bucket, plus CAS for empty-bucket inserts. No segments. Concurrency level effectively equals the number of buckets. Reads are nearly always lock-free.

**Comparison:**

| | `Hashtable` | `synchronizedMap` | `ConcurrentHashMap` |
|---|---|---|---|
| Lock granularity | Whole map | Whole map | Per-bucket |
| Read concurrency | Blocked by writes | Blocked by writes | Mostly lock-free |
| `null` keys/values | Not allowed | Allowed (wraps HashMap) | Not allowed |
| Iteration | Fail-fast (throws CME) | Fail-fast | Weakly consistent (no CME) |
| `size()` | Exact | Exact | Approximate (no global lock) |

**`null` prohibition:** unlike `HashMap`, CHM throws `NullPointerException` for null keys or values. Reason: in a concurrent context you cannot distinguish "key not present" from "key maps to null" without additional synchronization. The design eliminates the ambiguity.

**Atomic bulk operations (Java 8+):**
```java
map.compute(key, (k, v) -> v == null ? 1 : v + 1);  // atomic read-modify-write
map.merge(key, 1, Integer::sum);                      // simpler counter
map.computeIfAbsent(key, k -> new ArrayList<>());     // init once
```
These are atomic at the bucket level — safe for counters and initialization without external locking.

**Interview trap:** "Is `size()` reliable in CHM?" — No. `size()` (and `isEmpty()`) is approximate under concurrent modification. Use `mappingCount()` (returns `long`) for better accuracy, but even that is a snapshot. If you need exact count, synchronize externally or use `LongAdder` separately.

**Tags:** concurrenthashmap, thread-safety, lock-granularity, collections

---

## Q-JCON-009 [bloom: understand] [level: regular]
**Question:** Explain `ThreadLocal`: what it does, a legitimate use case, and how it causes memory leaks in thread pools.

**Model answer:** `ThreadLocal<T>` provides each thread with its own independent copy of a value. Reads and writes are scoped to the calling thread — no synchronization needed.

```java
private static final ThreadLocal<SimpleDateFormat> SDF =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// Each thread gets its own SimpleDateFormat instance — no shared state
String formatted = SDF.get().format(date);
```

Legitimate uses:
- Per-thread formatters (as above — `SimpleDateFormat` is not thread-safe)
- Request-scoped context in servlet containers (MDC in Logback, Spring `RequestContextHolder`)
- Database connection per thread (older patterns)

**Memory leak in thread pools:**
Thread pools reuse threads. `ThreadLocal` values are stored in `Thread.threadLocals` — a map keyed by the `ThreadLocal` instance. When a task completes and the thread returns to the pool, the `ThreadLocal` entry remains. If the `ThreadLocal` instance itself is garbage-collected (e.g., its class is unloaded), the key becomes a `WeakReference` pointing to null, but the value is still strongly reachable from the thread. The value cannot be GC'd.

In practice this causes:
1. Stale context bleeding between requests (security issue if context holds user identity)
2. Memory accumulation if values hold large objects

**Fix:** always call `ThreadLocal.remove()` in a `finally` block after the task:
```java
try {
    SDF.set(new SimpleDateFormat("dd-MM-yyyy")); // or set per-request
    doWork();
} finally {
    SDF.remove(); // mandatory in thread-pool context
}
```

**Interview trap:** "Is `InheritableThreadLocal` safe in pools?" — No. It copies parent-thread values into child threads at creation time, but thread pools reuse threads and don't re-create them per task. The inheritance happens once at pool initialization, not per task submission. Use explicit context propagation instead (e.g., pass context as a parameter or use `MDC.putCloseable`).

**Tags:** threadlocal, memory-leak, thread-pool, context

---

## Q-JCON-010 [bloom: understand] [level: regular]
**Question:** What is a `BlockingQueue`, and how is it used in a producer-consumer pattern?

**Model answer:** `BlockingQueue<E>` (in `java.util.concurrent`) is a thread-safe queue with blocking operations:

| Method | Behavior when empty/full |
|---|---|
| `put(e)` | Blocks if full |
| `take()` | Blocks if empty |
| `offer(e, timeout, unit)` | Waits up to timeout if full |
| `poll(timeout, unit)` | Waits up to timeout if empty |
| `add(e)` / `remove()` | Throws exception immediately |

Main implementations:
- `ArrayBlockingQueue(capacity)` — bounded, array-backed, single lock (producers and consumers contend)
- `LinkedBlockingQueue([capacity])` — optionally bounded, two separate locks (head lock for take, tail lock for put) — higher throughput under contention
- `PriorityBlockingQueue` — unbounded, ordered by priority
- `SynchronousQueue` — zero capacity; each put blocks until a take matches (handoff)

**Producer-consumer pattern:**
```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(1000);

// Producer threads:
executorService.execute(() -> {
    while (running) {
        Task task = generateTask();
        queue.put(task);  // blocks if queue full — natural backpressure
    }
});

// Consumer threads:
executorService.execute(() -> {
    while (running || !queue.isEmpty()) {
        Task task = queue.poll(100, TimeUnit.MILLISECONDS);
        if (task != null) process(task);
    }
});
```

`ThreadPoolExecutor` internally uses a `BlockingQueue` as its work queue — understanding `BlockingQueue` explains how the executor's queuing and rejection behavior works.

**Interview trap:** "`LinkedBlockingQueue` default capacity?" — `Integer.MAX_VALUE`. This is a production footgun: if producers outpace consumers, the queue grows until OOM. Always specify a capacity in production.

**Tags:** blockingqueue, producer-consumer, backpressure, executor

---

## Q-JCON-011 [bloom: understand] [level: regular]
**Question:** Explain `Semaphore` and `Phaser` — what they do and when to use each.

**Model answer:** **`Semaphore`** controls access to a finite number of permits. `acquire()` decrements (blocks when zero); `release()` increments.

```java
Semaphore sem = new Semaphore(5); // max 5 concurrent
try {
    sem.acquire();
    callExternalApi(); // at most 5 concurrent calls
} finally {
    sem.release();
}
```
Use cases: rate-limiting, connection pool throttling, limiting concurrent access to a resource.

`Semaphore(1)` is a binary semaphore (like a mutex), but unlike `synchronized`/`ReentrantLock` it is NOT reentrant and the release can come from a different thread (useful for cross-thread signaling).

**`Phaser`** is a reusable, flexible multi-phase barrier that combines features of `CountDownLatch` and `CyclicBarrier`:
- Dynamic party registration: `register()` / `arriveAndDeregister()`
- Phase progression: `arriveAndAwaitAdvance()` advances the phase when all registered parties arrive
- Tree structure: phasers can be chained to reduce contention

```java
Phaser phaser = new Phaser(3); // 3 parties

// Each worker thread:
for (int phase = 0; phase < 3; phase++) {
    doPhaseWork(phase);
    phaser.arriveAndAwaitAdvance(); // wait for all
}
```

`Phaser` vs `CyclicBarrier`: Phaser allows dynamic party count (register/deregister at runtime), supports tiered phases, and can be terminated. Preferred for complex multi-phase algorithms where thread count changes.

**Interview trap:** "Can `Semaphore` replace `ReentrantLock`?" — Technically a binary `Semaphore` enforces mutual exclusion, but lacks reentrancy. If the same thread calls `acquire()` twice it deadlocks. `ReentrantLock` is reentrant by design. Use `Semaphore` for resource pool throttling, not as a general mutex.

**Tags:** semaphore, phaser, synchronizer, rate-limiting

---

## Q-JCON-012 [bloom: apply] [level: regular]
**Question:** Describe the key parameters of `ThreadPoolExecutor` (corePoolSize, maximumPoolSize, keepAliveTime, workQueue, rejectionHandler) and how they interact. How do you size a pool for CPU-bound vs IO-bound work?

**Model answer:** `ThreadPoolExecutor` is the engine behind all `Executors` factories. Understanding its parameters is essential for production configuration:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,         // threads always kept alive
    maximumPoolSize,      // max threads when queue is full
    keepAliveTime,        // idle non-core thread survival time
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(queueCapacity),
    new ThreadFactory() { ... },
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

**Lifecycle logic:**
1. If running threads < `corePoolSize` → spawn new thread.
2. If running threads >= `corePoolSize` → put task in queue.
3. If queue is full AND running threads < `maximumPoolSize` → spawn non-core thread.
4. If queue is full AND running threads == `maximumPoolSize` → invoke rejection handler.

Non-core (> core) threads exit after `keepAliveTime` idle. Core threads survive indefinitely unless `allowCoreThreadTimeOut(true)`.

**Rejection policies:**
- `AbortPolicy` (default): throws `RejectedExecutionException`
- `CallerRunsPolicy`: caller thread runs the task — built-in backpressure
- `DiscardPolicy`: silently drops the task
- `DiscardOldestPolicy`: drops the oldest queued task, submits new one

**Sizing:**
- **CPU-bound:** N threads ≈ `Runtime.getRuntime().availableProcessors()` (or N+1). Adding more threads causes context switching overhead with no throughput gain.
- **IO-bound:** threads can far exceed CPU count. Rule of thumb: `N_cpu * (1 + wait_time / compute_time)`. With DB calls averaging 10ms and CPU work 1ms, target ~10x CPU count. In practice: measure with a load test; JVM 21 virtual threads make this mostly moot for IO.

**Common mistake — `Executors.newFixedThreadPool`:** uses unbounded `LinkedBlockingQueue` (capacity = `Integer.MAX_VALUE`). Under sustained overload, the queue absorbs all tasks without rejection — OOM before any rejection. Always size the queue explicitly in production.

**Interview trap:** "What is `maximumPoolSize` useful for if you have a large queue?" — Only kicks in when the queue is *full*. If using `LinkedBlockingQueue` with default capacity, `maximumPoolSize` is effectively irrelevant because the queue fills to 2 billion before extra threads are spawned.

**Tags:** threadpoolexecutor, executor, sizing, rejection-policy, cpu-bound, io-bound

---

## Q-JCON-013 [bloom: apply] [level: regular]
**Question:** What is the difference between `thenApply` and `thenCompose` in `CompletableFuture`?

**Model answer:** The distinction is whether the next stage returns a plain value or another `CompletableFuture`.

**`thenApply(Function<T, R>)`:** maps a value T to R synchronously. The function runs on the completing thread and wraps the result in a new `CompletableFuture<R>`.
```java
CompletableFuture<String> upper = 
    CompletableFuture.supplyAsync(() -> "hello")
                     .thenApply(String::toUpperCase); // CF<String>
```

**`thenCompose(Function<T, CompletableFuture<R>>)`:** the function itself returns a `CompletableFuture<R>`. `thenCompose` flattens it — equivalent to `flatMap` in streams. Without `thenCompose` you'd get `CF<CF<R>>` (nested futures).
```java
CompletableFuture<Order> order =
    CompletableFuture.supplyAsync(() -> userId)
                     .thenCompose(id -> fetchOrderAsync(id)); // avoids CF<CF<Order>>
```

**Other key combinators:**
- `thenCombine(other, BiFunction)` — wait for both, combine results
- `allOf(cf1, cf2, ...)` — `CF<Void>` that completes when all complete; call `.join()` on each inside `.thenApply` to extract values
- `anyOf(cf1, cf2, ...)` — completes with the first finished; result is `Object`, requires casting
- `exceptionally(Function<Throwable, T>)` — recover from exception with fallback
- `handle(BiFunction<T, Throwable, R>)` — handles both success and failure in one stage

**Executor choice for `thenApply` vs `thenApplyAsync`:**
- `thenApply(fn)` — fn runs on the thread that completed the prior stage (or the caller, if already done). Non-deterministic thread.
- `thenApplyAsync(fn, executor)` — fn always dispatched to `executor`. Predictable, preferred for non-trivial work.

**Interview trap:** "What does `allOf` return when one future fails?" — `allOf` itself completes exceptionally as soon as any constituent future fails (short-circuits the wait). The other futures continue running — there is no cancellation propagation. You must handle exceptions on each individual future or check them in the `.thenApply` callback.

**Tags:** completablefuture, thenapply, thencompose, async, compose

---

## Q-JCON-014 [bloom: apply] [level: senior]
**Question:** What is the Java `Lock` interface and `ReentrantLock`? When would you choose `ReentrantLock` over `synchronized`?

**Model answer:** `ReentrantLock` implements `Lock` and provides explicit, programmable locking with capabilities `synchronized` cannot offer:

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();

public void produce(Item item) throws InterruptedException {
    lock.lock();
    try {
        queue.add(item);
        notEmpty.signal();
    } finally {
        lock.unlock(); // MUST be in finally
    }
}

public Item consume() throws InterruptedException {
    lock.lock();
    try {
        while (queue.isEmpty()) notEmpty.await();
        return queue.poll();
    } finally {
        lock.unlock();
    }
}
```

**Advantages over `synchronized`:**
| Feature | `synchronized` | `ReentrantLock` |
|---|---|---|
| Interruptible lock wait | No | `lockInterruptibly()` |
| Try-lock with timeout | No | `tryLock(time, unit)` |
| Fairness policy | No (unfair) | `new ReentrantLock(true)` |
| Multiple conditions | One wait-set per object | Multiple `Condition` objects |
| Lock polling | No | `tryLock()` |
| Diagnostics | Limited | `getQueuedThreads()`, `isLocked()` |

**When to prefer `ReentrantLock`:**
- Need `tryLock()` to avoid deadlock or implement timeout-based retry
- Need `lockInterruptibly()` for cancellable operations
- Need multiple `Condition` variables (e.g., `notEmpty` + `notFull` in a bounded buffer)
- Need fair ordering (rare — fairness has performance cost)
- Using virtual threads: `synchronized` can pin a virtual thread to its carrier; `ReentrantLock` does not

**When `synchronized` is fine:**
- Simple critical sections, no need for advanced features
- Code clarity is paramount — `synchronized` is harder to misuse (no forgotten `unlock()`)

**Interview trap:** "Is `ReentrantLock` faster than `synchronized`?" — In Java 6+, `synchronized` uses adaptive spinning and biased/thin locks — performance is roughly equivalent in low-contention scenarios. `ReentrantLock` may win in high contention with fairness off, but benchmark first. Never choose it purely for perceived performance.

**Tags:** reentrantlock, lock, condition, synchronized, virtual-threads

---

## Q-JCON-015 [bloom: apply] [level: senior]
**Question:** Explain `ReadWriteLock` and `StampedLock`. When does each outperform a plain `ReentrantLock`?

**Model answer:** Both address the read-heavy access pattern: many readers are safe to run concurrently, but a writer needs exclusive access.

**`ReentrantReadWriteLock`:**
```java
private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
private final ReadWriteLock.ReadLock  readLock  = rwLock.readLock();
private final ReadWriteLock.WriteLock writeLock = rwLock.writeLock();

public V get(K key) {
    readLock.lock();
    try { return map.get(key); }
    finally { readLock.unlock(); }
}
public void put(K key, V value) {
    writeLock.lock();
    try { map.put(key, value); }
    finally { writeLock.unlock(); }
}
```
Multiple threads hold the read lock simultaneously; the write lock is exclusive. Wins over `ReentrantLock` when reads >> writes and read operations take non-trivial time. Under heavy write contention, write starvation can occur unless fair mode is enabled (which reduces throughput).

**`StampedLock` (Java 8+):**
Goes further: adds an **optimistic read** mode that avoids acquiring any lock:
```java
private final StampedLock sl = new StampedLock();
private double x, y;

public double distanceFromOrigin() {
    long stamp = sl.tryOptimisticRead(); // no lock acquired
    double cx = x, cy = y;
    if (!sl.validate(stamp)) {          // check if a write happened
        stamp = sl.readLock();          // fall back to read lock
        try { cx = x; cy = y; }
        finally { sl.unlockRead(stamp); }
    }
    return Math.hypot(cx, cy);
}
```
Optimistic read succeeds without any lock, only validating that no write happened between the snapshot reads. If a write occurred, retry with a real read lock. Under mostly-read workloads this gives near-zero contention.

**Trade-offs of `StampedLock`:**
- NOT reentrant — holding a read lock and trying to upgrade to write will deadlock
- NOT interruptible — `readLock()` ignores interruption
- More complex API, easier to misuse
- Lock downgrade is supported; upgrade (read → write) requires explicit unlock and re-lock

**Interview trap:** "Can you downgrade from a write lock to a read lock in `StampedLock`?" — Yes, using `tryConvertToReadLock(stamp)`. For `ReentrantReadWriteLock`, downgrade is supported (acquire read lock, release write lock while still holding read). Upgrade (read → write) is NOT supported in RRWL — causes deadlock.

**Tags:** readwritelock, stampedlock, lock, concurrency, optimistic-read

---

## Q-JCON-016 [bloom: apply] [level: senior]
**Question:** What are CAS and the ABA problem? How do `AtomicReference.compareAndSet` and `AtomicStampedReference` address it?

**Model answer:** **CAS (Compare-And-Swap):** a single atomic CPU instruction. Semantics: "if the value at this memory location equals expected, replace it with update; return whether the swap succeeded." All `Atomic*` classes build on this.

```java
AtomicInteger counter = new AtomicInteger(0);
// increment without synchronized:
int current, next;
do {
    current = counter.get();
    next = current + 1;
} while (!counter.compareAndSet(current, next));
// or simply: counter.incrementAndGet()
```

CAS is lock-free: if the swap fails (another thread changed the value), retry. Under low contention this is faster than locking; under very high contention, retries can thrash (use `LongAdder` instead).

**ABA problem:** Thread A reads value A. Thread B changes A → B → A. Thread A's CAS sees A, succeeds — but the value has changed and changed back. For simple integers this is usually harmless (increment is idempotent). For pointer/reference-based structures (e.g., lock-free stacks), it causes corruption:
- Thread A reads head = Node1
- Thread B pops Node1 (A→B), pushes Node3 (B→A) — head is Node1 again but internal state differs
- Thread A's CAS succeeds, but the stack is now corrupt

**Solution: `AtomicStampedReference<V>`** — wraps value + integer stamp (version counter). CAS checks both:
```java
AtomicStampedReference<Node> head = new AtomicStampedReference<>(node, 0);
int[] stampHolder = new int[1];
Node current = head.get(stampHolder);
// Only succeeds if BOTH value AND stamp match:
head.compareAndSet(current, newHead, stampHolder[0], stampHolder[0] + 1);
```
Now the A→B→A sequence produces stamps 0→1→2, so the CAS fails when the stamp doesn't match.

**`LongAdder` vs `AtomicLong`:** `LongAdder` uses a cell array — each thread increments its own cell, sum is computed on `sum()`. Dramatically lower contention than `AtomicLong` for counter-only use cases. Use `AtomicLong` when you need CAS (check-then-act) semantics; `LongAdder` for pure high-throughput counting.

**Interview trap:** "Is CAS always better than synchronized?" — No. Under high contention, CAS spin loops burn CPU. `synchronized` blocks the thread (no CPU waste while waiting). Profile before choosing.

**Tags:** cas, aba, atomic, atomicstampedreference, longadder, lock-free

---

## Q-JCON-017 [bloom: apply] [level: senior]
**Question:** Describe the four necessary conditions for deadlock. Show a code example and explain how to prevent it.

**Model answer:** Deadlock requires all four conditions (Coffman conditions) simultaneously:
1. **Mutual exclusion:** resources are non-shareable (locks).
2. **Hold and wait:** a thread holds one resource while requesting another.
3. **No preemption:** resources cannot be forcibly taken from a thread.
4. **Circular wait:** thread A waits for a resource held by B; B waits for a resource held by A.

**Code example:**
```java
Object lockA = new Object();
Object lockB = new Object();

// Thread 1:
synchronized (lockA) {
    Thread.sleep(10); // allows Thread 2 to acquire lockB
    synchronized (lockB) { use(lockA, lockB); }
}

// Thread 2:
synchronized (lockB) {
    Thread.sleep(10);
    synchronized (lockA) { use(lockA, lockB); } // deadlock
}
```

**Prevention strategies (break any one condition):**

1. **Lock ordering (break circular wait):** always acquire locks in a consistent global order. Both threads lock A then B:
```java
synchronized (lockA) { synchronized (lockB) { ... } }
```

2. **Lock timeout with `tryLock` (break hold-and-wait):**
```java
if (lockA.tryLock(1, SECONDS)) {
    try {
        if (lockB.tryLock(1, SECONDS)) {
            try { useResources(); }
            finally { lockB.unlock(); }
        }
    } finally { lockA.unlock(); }
}
```

3. **Coarse-grained locking:** use a single lock for operations that always need both resources (reduces concurrency but eliminates deadlock).

4. **Lock-free structures:** eliminate locks entirely using `ConcurrentHashMap`, `AtomicReference`, etc.

**Detection:** `jstack <pid>` or `ThreadMXBean.findDeadlockedThreads()` — reports deadlocked thread chains with lock ownership. Set up a scheduled check in production.

**Livelock vs Starvation:**
- **Livelock:** threads are active but perpetually yielding to each other, making no progress (e.g., two `tryLock` callers always back off at the same time).
- **Starvation:** one thread can never acquire a lock because high-priority or frequent threads always win. Fix: fair locks (`new ReentrantLock(true)`).

**Interview trap:** "How would you detect a deadlock in production?" — `jstack` dumps thread state with lock owners. For JMX: `ManagementFactory.getThreadMXBean().findDeadlockedThreads()` returns thread IDs. Add a scheduled monitor that calls this and alerts.

**Tags:** deadlock, livelock, starvation, lock-ordering, trylock

---

## Q-JCON-018 [bloom: apply] [level: senior]
**Question:** What is false sharing in the context of CPU caches, and how does `@Contended` (or manual padding) fix it?

**Model answer:** Modern CPUs cache memory in cache lines — typically 64 bytes. If two independent variables (e.g., two `long` fields) happen to share a cache line, and two separate CPU cores each write to one of those variables, the cores' caches constantly invalidate each other — even though they are writing different locations. This is **false sharing**: the fields are logically independent, but physically share a cache line.

**Scenario:**
```java
class Counters {
    long a; // 8 bytes
    long b; // 8 bytes — same 64-byte cache line as a
}
// Thread 1 increments a; Thread 2 increments b
// Cache ping-pong between cores, even though no logical sharing
```

**Fix 1 — Manual padding:**
```java
class PaddedCounter {
    long a;
    long p1, p2, p3, p4, p5, p6, p7; // 56 bytes padding — push b to next cache line
    long b;
}
```

**Fix 2 — `@jdk.internal.vm.annotation.Contended` (or `sun.misc.Contended` in older JDKs):**
```java
@jdk.internal.vm.annotation.Contended
class Counters {
    long a;
    long b;
}
// JVM adds padding around annotated fields/classes, isolating each to its own cache line
```
Requires `-XX:-RestrictContended` (or unrestricted in newer JDKs).

**`LongAdder` uses this:** internally it has a `Cell` array, each `Cell` annotated with `@Contended`, so concurrent updates to different cells don't cause cache invalidation. This is why `LongAdder` dramatically outperforms `AtomicLong` under high contention.

**When it matters:** high-frequency concurrent writes to adjacent fields on different threads (counters, flags, accumulators in hot loops). Measurable with JMH by looking at memory bandwidth and throughput. Don't optimize prematurely.

**Interview trap:** "Does immutability prevent false sharing?" — No. False sharing is about writes. Immutable (read-only) fields on the same cache line don't cause false sharing because no invalidation occurs. False sharing requires concurrent *writes* from different cores.

**Tags:** false-sharing, cache-line, contended, performance, longadder

---

## Q-JCON-019 [bloom: apply] [level: senior]
**Question:** Explain virtual threads in Java 21: what problem they solve, how carrier threads work, what "pinning" means, and when NOT to use them.

**Model answer:** **Problem:** Classic (platform) threads map 1:1 to OS threads. OS threads cost ~1 MB stack + kernel overhead. A standard JVM can sustain a few thousand threads. I/O-heavy workloads (REST APIs, DB calls) leave most threads blocked on I/O — CPU is idle while OS threads are exhausted. Thread pools with 200 threads become the bottleneck.

**Virtual threads (Project Loom, GA Java 21):** JVM-managed lightweight threads. They are scheduled onto a small pool of platform threads called **carrier threads** (default: one per CPU core). When a virtual thread blocks on I/O (or `Object.wait()`, `Thread.sleep()`, etc.), the JVM unmounts it from its carrier thread. The carrier is immediately free to run another virtual thread. The OS sees only the small set of carrier threads.

```java
// Create a million virtual threads:
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> { Thread.sleep(1000); return null; });
    }
} // blocks until all complete
```

**Pinning:** a virtual thread is pinned when it cannot be unmounted from its carrier, removing the concurrency benefit. Causes:
1. **`synchronized` block or method** that executes a blocking operation inside it (carrier cannot switch away). JDK 24 removes most pinning from `synchronized`.
2. **`JNI` calls** — native frames prevent unmounting.

Diagnosis: `-Djdk.tracePinnedThreads=full` or JFR event `jdk.VirtualThreadPinned`.

Fix: replace `synchronized` with `ReentrantLock` in sections that block on I/O.

**When NOT to use virtual threads:**
- **CPU-bound work:** virtual threads don't add parallelism. If all work is CPU computation, you still need only N platform threads (N = CPU count). Virtual threads increase context switching overhead for no gain.
- **When you need thread identity stability:** virtual threads are ephemeral; carrier thread can change after each unmount. Code that relies on `Thread.currentThread()` identity across blocking calls will break.
- **`ThreadLocal` abuse:** each virtual thread has its own `ThreadLocal` map; if you create millions of virtual threads, millions of `ThreadLocal` maps can pile up. Use `ScopedValue` (Java 21 preview → GA 23) instead.

**Interview trap:** "Do virtual threads replace reactive programming?" — They simplify I/O-bound concurrency to the point where reactive frameworks are often unnecessary for throughput reasons. But reactive (Reactor, RxJava) provides backpressure, composable pipelines, and memory efficiency for streaming. Virtual threads do not provide backpressure. Choose based on whether you need the programming model.

**Tags:** virtual-threads, loom, carrier-thread, pinning, java21

---

## Q-JCON-020 [bloom: apply] [level: senior]
**Question:** What is structured concurrency (Java 21 preview / Java 23 GA)? How does `StructuredTaskScope` differ from `CompletableFuture.allOf`?

**Model answer:** **Structured concurrency** applies the principle of structured programming to threads: a thread's subtasks must complete before the thread that spawned them can proceed. This ensures that subtask lifetimes are scoped to a lexical block — no orphaned threads escaping beyond the code that created them.

**`StructuredTaskScope`:**
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<Product>  product = scope.fork(() -> fetchProduct(id));
    Subtask<Price>    price   = scope.fork(() -> fetchPrice(id));
    Subtask<Integer>  stock   = scope.fork(() -> fetchStock(id));

    scope.join();           // blocks until all subtasks complete or any fails
    scope.throwIfFailed();  // propagates first exception if any subtask failed

    return new ProductView(product.get(), price.get(), stock.get());
} // scope.close() cancels any still-running subtasks automatically
```

`ShutdownOnFailure`: when any subtask fails, the scope shuts down (signals other subtasks to stop), and the error propagates. `ShutdownOnSuccess`: returns the first successful result, cancels others (race pattern).

**vs `CompletableFuture.allOf`:**

| | `CompletableFuture.allOf` | `StructuredTaskScope` |
|---|---|---|
| Cancellation on failure | No — other CFs continue | Yes — scope shuts down |
| Error propagation | Must manually inspect each CF | `throwIfFailed()` re-throws |
| Thread lifetime | Threads can outlive caller | Subtasks bounded by `try-with-resources` |
| Observability | Thread names generic | Subtasks visible in thread dump as children |
| API style | Functional/chain | Imperative/sequential reads naturally |

**Structured concurrency solves three classic problems:**
1. **Orphaned threads:** CF tasks run on executor threads that outlive the calling scope; if the caller is cancelled, subtasks keep running. Scope guarantees cleanup.
2. **Error masking:** in `allOf`, if subtask B fails after A succeeds, the `allOf` CF fails, but A's result is silently discarded. `StructuredTaskScope` surfaces all failures.
3. **Observability:** JVM thread dumps now show parent-child relationships, making diagnosis of hanging requests trivial.

**Interview trap:** "Is `StructuredTaskScope` production-ready in Java 21?" — It was a preview API in Java 21, re-previewed in 22, and finalized in Java 23 (`java.util.concurrent.StructuredTaskScope`). In Java 21, requires `--enable-preview` and `--source 21`.

**Tags:** structured-concurrency, structuredtaskscope, java21, completablefuture

---

## Q-JCON-021 [bloom: analyze] [level: senior]
**Question:** A service that used a fixed thread pool of 100 threads and `LinkedBlockingQueue` sees random `OutOfMemoryError: unable to create new native thread` in production. Walk through your diagnosis and fix.

**Model answer:** **Diagnosis path:**

1. **Check thread count:** `jstack <pid> | grep "java.lang.Thread" | wc -l` or JMX `ThreadMXBean.getThreadCount()`. If it's near the OS limit (`ulimit -u`), something is creating unbounded threads.

2. **Identify the source:** `jstack` will show thread names. If many are worker threads from other pools, or if thread names look like `Thread-<N>` (raw thread creation), trace the callsite.

3. **Check `LinkedBlockingQueue` size:** the default capacity is `Integer.MAX_VALUE`. If producer rate > consumer rate, the queue grows without bound. Tasks backlog → heap fills → OOM. This is a different OOM from thread starvation but equally lethal.

4. **Look for thread leaks:** are `ExecutorService` instances being created per-request and never shut down? Each abandoned pool holds live threads. Common in code like `Executors.newFixedThreadPool(n)` inside a factory method.

**Root causes and fixes:**

| Root cause | Fix |
|---|---|
| New pool created per request | Create pool once as a singleton (Spring bean) |
| `LinkedBlockingQueue` unbounded backlog | Use `new ArrayBlockingQueue<>(10000)` with rejection policy |
| `CompletableFuture.supplyAsync` using FJP | Pass a dedicated bounded executor |
| ThreadLocal not removed | Threads hold references, GC can't release, OOM on heap |
| OS thread limit | `ulimit -u 65536` (or systemd `TasksMax`), but fix root cause first |

**Permanent fix pattern:**
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    20, 20,
    60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(5000),
    new CustomThreadFactory("pricing-pool"),
    new ThreadPoolExecutor.CallerRunsPolicy() // backpressure to caller
);
executor.allowCoreThreadTimeOut(true);
```

**Interview trap:** "Why `CallerRunsPolicy` instead of `AbortPolicy`?" — `AbortPolicy` throws `RejectedExecutionException` — caller must handle it. `CallerRunsPolicy` makes the submitting thread execute the task itself, automatically slowing the submission rate. It is a natural backpressure mechanism in synchronous paths. For async paths (reactive, event loop) `CallerRunsPolicy` can block the event loop — use `AbortPolicy` + circuit breaker there.

**Tags:** thread-pool, oom, diagnosis, rejection-policy, production

---

## Q-JCON-022 [bloom: analyze] [level: senior]
**Question:** How does `ConcurrentHashMap.compute` differ from a read-then-CAS loop, and what guarantees does it provide?

**Model answer:** `ConcurrentHashMap.compute(key, remappingFunction)` atomically applies a function to the current value (or null if absent) and stores the result, all within a single synchronized section on the bucket:

```java
// Atomic increment — no external synchronization needed:
map.compute("requests", (k, v) -> v == null ? 1 : v + 1);

// vs naive (broken) approach:
Integer val = map.get("requests");
map.put("requests", val == null ? 1 : val + 1); // RACE: lost update possible
```

**What `compute` guarantees:**
1. The read-modify-write on the bucket is atomic — no other thread can see an intermediate state for that key during the function's execution.
2. The remapping function is called exactly once per invocation (the lock is held during the call).
3. If the function returns null, the entry is removed atomically.

**Important caveat:** the remapping function must NOT modify the same map (it would deadlock on the bucket lock or cause `ConcurrentModificationException`). It also should be fast — the lock is held for its duration.

**`merge` for simpler accumulation:**
```java
map.merge("errors", 1, Integer::sum); // cleaner than compute for counting
```

**`computeIfAbsent` for cache initialization:**
```java
// Safe double-checked initialization:
List<Event> events = map.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
```
Only calls the function if the key is absent; ensures only one instance is created per key under concurrency.

**`size()` after bulk operations:** CHM's `size()` is approximate. If you need an accurate count after `compute`, use `mappingCount()` or maintain a separate `LongAdder`.

**Interview trap:** "Is the remapping function in `compute` allowed to throw a checked exception?" — No. `BiFunction` is unchecked. Wrap checked exceptions in a `RuntimeException` inside the lambda, then unwrap in the caller.

**Tags:** concurrenthashmap, compute, merge, atomic, lock-striping

---

## Q-JCON-023 [bloom: analyze] [level: master]
**Question:** Walk through the internals of `ThreadPoolExecutor`: how does it track the combined running/count state in a single `AtomicInteger`, and what happens during thread pool shutdown?

**Model answer:** `ThreadPoolExecutor` stores both the **run state** and the **worker count** in a single `AtomicInteger ctl` using bit packing — a classic space/CAS-atomicity trade-off:

```
ctl = (runState << COUNT_BITS) | workerCount
```
- High 3 bits: run state (RUNNING, SHUTDOWN, STOP, TIDYING, TERMINATED)
- Low 29 bits: active worker count (up to ~500 million workers)

```java
private static final int COUNT_BITS = Integer.SIZE - 3; // 29
private static final int RUNNING    = -1 << COUNT_BITS; // 111...
private static final int SHUTDOWN   =  0 << COUNT_BITS; // 000...
private static final int STOP       =  1 << COUNT_BITS;
private static final int TIDYING    =  2 << COUNT_BITS;
private static final int TERMINATED =  3 << COUNT_BITS;
```

A CAS on `ctl` atomically transitions state or increments/decrements worker count — without a separate lock.

**Worker lifecycle (`Worker` inner class extends `AbstractQueuedSynchronizer`):**
1. `Worker` is created, added to `workers` (a `HashSet` protected by `mainLock`).
2. `Worker.run()` calls `runWorker()`, which loops: fetch task from queue via `getTask()`.
3. `getTask()` blocks on `workQueue.poll(keepAlive, ...)` for non-core threads (exits if timeout and can trim).
4. On idle timeout, `getTask()` returns null → `runWorker` loop exits → `processWorkerExit()` removes from `workers`, decrements count.

**Shutdown sequence:**

`shutdown()`:
1. CAS `ctl` state from RUNNING → SHUTDOWN.
2. Interrupts idle workers (those blocked in `getTask()`).
3. Queued tasks continue to execute — no new tasks accepted.

`shutdownNow()`:
1. CAS state → STOP.
2. Interrupts ALL workers (including those executing tasks).
3. Drains the work queue and returns un-executed tasks.

State progression: SHUTDOWN/STOP → TIDYING (when workers HashSet is empty + queue drained) → hook `terminated()` called → TERMINATED. `awaitTermination()` waits on `termination` Condition.

**Why `AQS` for `Worker`?** Each Worker IS a lock (`tryAcquire` / `tryRelease` are implemented). A non-reentrant lock (lock state 0=idle, 1=running). This lets `interruptIdleWorkers()` safely interrupt only threads that are NOT currently executing a task (only idle threads have lock state 0 — `tryLock()` succeeds).

**Interview trap:** "What is `mainLock` used for in `ThreadPoolExecutor`?" — `mainLock` (a `ReentrantLock`) protects the `workers` `HashSet` and the `largestPoolSize` and `completedTaskCount` statistics. It is NOT held during task execution — only during pool bookkeeping (adding/removing workers, iterating workers for shutdown).

**Tags:** threadpoolexecutor, internals, ctl, worker, aqs, shutdown

---

## Q-JCON-024 [bloom: analyze] [level: master]
**Question:** Explain how `AbstractQueuedSynchronizer` (AQS) works internally, and how `ReentrantLock` and `CountDownLatch` are built on top of it.

**Model answer:** `AQS` is the backbone of most `java.util.concurrent` synchronizers. It provides:
1. An `int state` field (CAS-managed, semantics defined by subclass).
2. A CLH (Craig, Landin, Hagersten) variant queue of waiting threads — a doubly-linked list of `Node` objects.
3. Template methods: `tryAcquire` / `tryRelease` (exclusive), `tryAcquireShared` / `tryReleaseShared` (shared).

**How it works:**
- `acquire(1)`: calls `tryAcquire(1)` (subclass). If it fails (lock held), current thread is enqueued as a `Node`, then parked via `LockSupport.park()`.
- `release(1)`: calls `tryRelease(1)`. If it returns true, unparks the head's successor node — which then re-tries `tryAcquire`.
- The CLH queue ensures FIFO ordering (for fair mode). In non-fair mode, barging is allowed: a new thread tries `tryAcquire` before enqueuing — often succeeds if the lock just became free (avoids park/unpark overhead).

**`ReentrantLock` on top of AQS:**
- `state = 0`: unlocked; `state > 0`: locked, count = hold count (reentrancy).
- `tryAcquire(1)`: CAS state from 0 to 1 (non-fair: barge first); if current owner == this thread, increment state (reentrant).
- `tryRelease(1)`: decrement state; if state reaches 0, clear owner.
- Fair mode: `tryAcquire` checks if queue is non-empty before barging (`hasQueuedPredecessors()`).

**`CountDownLatch` on top of AQS:**
- `state = N` (latch count).
- `await()` → `acquireShared(-1)` → `tryAcquireShared`: returns 0 if state == 0 (succeed, don't block), else return -1 (block, enqueue).
- `countDown()` → `releaseShared(1)` → `tryReleaseShared`: CAS state from N to N-1; if reaches 0, returns true → AQS unparks all waiting nodes (shared release unparks the entire wait queue, unlike exclusive which unparks only head).

**`Semaphore`:** `state = permits`. `acquire` CAS-decrements; if negative, enqueue. `release` CAS-increments; if waiters, unpark.

**Why this matters at senior/master level:** understanding AQS means you understand why `synchronized` uses BLOCKED state but `ReentrantLock` uses WAITING — because `ReentrantLock` uses `LockSupport.park()` (→ WAITING) rather than the JVM monitor mechanism (→ BLOCKED). This is observable in `jstack` output and affects tooling/debugging.

**Interview trap:** "Why does `jstack` show `ReentrantLock`-blocked threads as WAITING rather than BLOCKED?" — Because `ReentrantLock` internally calls `LockSupport.park()` to suspend the thread, which puts it in state WAITING (or TIMED_WAITING if `parkNanos`). The BLOCKED state is exclusively for threads waiting to acquire a JVM monitor (`synchronized`).

**Tags:** aqs, abstractqueuedsynchronizer, reentrantlock, countdownlatch, internals, lockSupport

---

## Q-JCON-025 [bloom: analyze] [level: master]
**Question:** Design a bounded, thread-safe cache with expiry. What synchronization primitives would you use, and what pitfalls must you avoid?

**Model answer:** A production-grade bounded expiry cache requires:
1. Thread-safe reads and writes
2. Bounded size (eviction on overflow)
3. Time-based expiry
4. Minimal lock contention

**Design using `ConcurrentHashMap` + `ScheduledExecutorService`:**

```java
public class BoundedExpiryCache<K, V> {
    private record Entry<V>(V value, long expiresAt) {}

    private final ConcurrentHashMap<K, Entry<V>> map;
    private final int maxSize;
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleaner");
            t.setDaemon(true);
            return t;
        });

    public BoundedExpiryCache(int maxSize, long ttlMillis) {
        this.map = new ConcurrentHashMap<>(maxSize * 2);
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        cleaner.scheduleAtFixedRate(this::evictExpired, ttlMillis, ttlMillis, TimeUnit.MILLISECONDS);
    }

    public V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null || System.currentTimeMillis() > e.expiresAt()) {
            map.remove(key, e); // atomic: only removes if still the same entry
            return null;
        }
        return e.value();
    }

    public void put(K key, V value) {
        if (map.size() >= maxSize) evictOne(); // size bounding — approximate
        map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    private void evictOne() {
        // Simple: remove first entry found (LRU would require a LinkedHashMap + external lock)
        map.keySet().stream().findFirst().ifPresent(map::remove);
    }
}
```

**Pitfalls:**
1. **`map.size() >= maxSize` is racy** — `size()` is approximate in CHM; two threads can both see `size < maxSize` and both insert, overshooting by a small amount. Acceptable if the bound is soft. Hard bound requires a `Semaphore(maxSize)` on `put`.
2. **`remove(key, e)` in `get`** — use the two-arg form to avoid removing a freshly inserted replacement entry.
3. **Cleaner thread is a daemon** — must be daemon or JVM won't exit.
4. **`evictExpired` under load** — scanning the whole map locks individual buckets serially; keep TTL not too short for very large maps.
5. **No `computeIfAbsent` + put race** — if you need get-or-load semantics, use `computeIfAbsent` which is atomic per key.

**Production alternative:** use Caffeine (`com.github.ben-manes.caffeine`) — supports `expireAfterWrite`, `maximumSize`, async loading, and statistics. Its `W-TinyLFU` eviction policy outperforms simple LRU. Don't hand-roll a cache in production if Caffeine covers your needs.

**Interview trap:** "Why not use `Collections.synchronizedMap(new LinkedHashMap(...))`?" — A synchronized wrapper makes individual operations thread-safe but compound operations (check-then-insert, iterate-and-remove) still need external synchronization. Under concurrent access, iterator still throws `ConcurrentModificationException`. `ConcurrentHashMap` handles iteration without CME via weakly-consistent iterators.

**Tags:** cache, concurrency, concurrenthashmap, expiry, design

---

## Q-JCON-026 [bloom: analyze] [level: master]
**Question:** You have a high-throughput service where multiple threads write metrics to a shared stats object. Profiling shows 40% of CPU time in `synchronized` contention. Walk through a sequence of optimizations, explaining the trade-offs at each step.

**Model answer:** This is a layered optimization problem. Work from least-intrusive to most:

**Baseline (bad):**
```java
class Stats {
    private long requests, errors, totalLatencyMs;
    public synchronized void record(boolean error, long latencyMs) {
        requests++; if (error) errors++; totalLatencyMs += latencyMs;
    }
    public synchronized long avgLatency() { return totalLatencyMs / requests; }
}
```
Single coarse lock. Every record call serializes all threads.

**Step 1 — `AtomicLong` (CAS, lock-free):**
```java
class Stats {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong errors   = new AtomicLong();
    private final AtomicLong latency  = new AtomicLong();
    public void record(boolean error, long latencyMs) {
        requests.incrementAndGet();
        if (error) errors.incrementAndGet();
        latency.addAndGet(latencyMs);
    }
}
```
Better. CAS contention still exists under very high concurrency; all writers CAS on the same memory location.

**Step 2 — `LongAdder` (per-thread cells, minimal false sharing):**
```java
class Stats {
    private final LongAdder requests = new LongAdder();
    private final LongAdder errors   = new LongAdder();
    private final LongAdder latency  = new LongAdder();
    public void record(boolean error, long latencyMs) {
        requests.increment();
        if (error) errors.increment();
        latency.add(latencyMs);
    }
    public long getAvgLatency() {
        long r = requests.sum(); // weakly consistent — sum at this instant
        return r == 0 ? 0 : latency.sum() / r;
    }
}
```
`LongAdder` uses `@Contended` cells — each thread increments its own cell, avoiding cache-line ping-pong. `sum()` is slightly expensive (adds all cells) but reads are rare. Big win under high write concurrency.

**Step 3 — Thread-local accumulation with periodic flush (zero contention on hot path):**
```java
class Stats {
    private final LongAdder globalRequests = new LongAdder();
    private final ThreadLocal<long[]> local = ThreadLocal.withInitial(() -> new long[3]);
    private static final int FLUSH_INTERVAL = 1000;

    public void record(boolean error, long latencyMs) {
        long[] buf = local.get();
        buf[0]++; if (error) buf[1]++; buf[2] += latencyMs;
        if (buf[0] % FLUSH_INTERVAL == 0) flush(buf);
    }
    private void flush(long[] buf) {
        globalRequests.add(buf[0]); ... buf[0] = buf[1] = buf[2] = 0;
    }
}
```
Zero shared-memory writes on 999/1000 records. Trade-off: up to `FLUSH_INTERVAL` records lost on thread death (no `remove()` + final flush).

**Step 4 — Striped counter (Guava `Striped<Lock>` pattern or custom):** partition the stats object into N shards. Each thread hashes to a shard. Reads scan all shards. Reduces contention by N without `ThreadLocal` lifecycle complexity.

**Step 5 — Off-critical-path aggregation:** if latency measurement itself is the bottleneck, sample (record every Nth call with `LongAdder` modulo check) or use HDR histogram (`HdrHistogram` library) which uses lock-free CAS internally and gives percentiles.

**Key principle:** every step trades some read accuracy or complexity for write throughput. Profile with JMH after each step — don't over-engineer. `LongAdder` usually solves 90% of counter contention issues cleanly.

**Interview trap:** "Is `LongAdder.sum()` exact?" — No. It is a snapshot that may miss concurrent increments still in cells. For reporting/dashboards this is fine. For correctness-critical counters (billing), you need synchronized totals or events sourced to a reliable log.

**Tags:** performance, contention, longadder, threadlocal, profiling, optimization

---

## Q-JCON-027 [bloom: analyze] [level: master]
**Question:** Compare `CompletableFuture` with structured concurrency for orchestrating parallel service calls. When does each break down?

**Model answer:** Both orchestrate concurrent subtasks, but with fundamentally different philosophies:

**`CompletableFuture.allOf` — push-based, functional:**
```java
CompletableFuture<Product> pF  = fetchProductAsync(id);
CompletableFuture<Price>   prF = fetchPriceAsync(id);
CompletableFuture<Integer> sF  = fetchStockAsync(id);

return CompletableFuture.allOf(pF, prF, sF)
    .thenApply(v -> new View(pF.join(), prF.join(), sF.join()))
    .orTimeout(5, SECONDS)
    .exceptionally(ex -> fallbackView(ex));
```

**Where CF breaks down:**
1. **Cancellation does not propagate.** `allOf(...).cancel()` cancels the `allOf` future but not `pF`, `prF`, `sF`. The subtasks keep running (and consuming threads) even after the caller timed out.
2. **Error association is manual.** If `prF` fails, `allOf` completes exceptionally, but `pF` result is silently discarded. To know which subtask failed requires inspecting individual futures.
3. **Observability:** thread dumps show anonymous pool threads with no parent-child relationship. Hard to trace which request spawned which tasks.
4. **Scope leaks:** if `supplyAsync` runs on the common ForkJoinPool, the tasks escape the logical request scope.

**Structured concurrency — lexical scope, imperative:**
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var pT  = scope.fork(() -> fetchProduct(id));
    var prT = scope.fork(() -> fetchPrice(id));
    var sT  = scope.fork(() -> fetchStock(id));

    scope.join().throwIfFailed(); // one line: wait + propagate
    return new View(pT.get(), prT.get(), sT.get());
}
```

**Where SC breaks down:**
1. **Non-nestable fan-out:** SC works best for tree-shaped task graphs. Dynamic parallelism (spawning subtasks from within subtasks with independent lifetimes) requires manual `StructuredTaskScope` nesting and becomes complex.
2. **Streaming/reactive scenarios:** SC does not model push-based event streams or backpressure. A reactive pipeline (Reactor Flux) cannot be expressed as a structured scope.
3. **Asynchronous HTTP frameworks:** if the controller returns a `CompletableFuture` (Spring WebFlux), SC's blocking `scope.join()` blocks the virtual thread — fine for virtual threads, fatal for reactor event loop threads.
4. **Java 21 availability:** preview in 21, GA in 23. Teams on Java 17/21 without preview flags can't use it.

**Summary table:**

| Concern | `CompletableFuture` | `StructuredTaskScope` |
|---|---|---|
| Cancellation propagation | Manual | Automatic |
| Error surfacing | Manual join+check | `throwIfFailed()` |
| Thread observability | Poor | Parent-child visible |
| Dynamic parallelism | Natural | Requires nesting |
| Java version | 8+ | 21 preview / 23 GA |
| Backpressure | None | None |

**Interview trap:** "Can you mix them?" — Yes. `StructuredTaskScope` tasks can return `CompletableFuture`s; the scope merely bounds the subtask lifetimes. But the common case is to use SC for the orchestration layer and avoid CF inside scopes to keep the model clean.

**Tags:** structured-concurrency, completablefuture, java21, orchestration, cancellation

---

## Q-JCON-028 [bloom: analyze] [level: master]
**Question:** Explain the double-checked locking (DCL) pattern: what was wrong with it before Java 5, why `volatile` fixes it, and what the modern alternative is.

**Model answer:** DCL is a lazy initialization pattern that attempts to avoid synchronization on every read:

```java
// Broken pre-Java-5:
class Cache {
    private static Cache instance;
    public static Cache getInstance() {
        if (instance == null) {           // (1) first check — unsynchronized
            synchronized (Cache.class) {
                if (instance == null) {   // (2) second check — inside lock
                    instance = new Cache(); // (3) construction
                }
            }
        }
        return instance;                  // (4) return
    }
}
```

**Why it was broken (pre-Java 5 / pre-JMM fix):** object construction is not a single atomic operation. The JVM can perform it as:
1. Allocate memory for `Cache` object.
2. Write reference to `instance` (memory is allocated, but object not yet initialized).
3. Execute `Cache` constructor body (initialize fields).

Steps 2 and 3 can be reordered by the compiler/CPU. A thread that reads `instance` at (1) after step 2 but before step 3 sees a non-null reference to a partially-constructed object. Using that object is undefined behavior.

**`volatile` fix (Java 5+):** volatile writes have a happens-before relationship with subsequent volatile reads. Writing to `volatile instance` at step (3) cannot be reordered with the constructor (program order + HB). Any thread that reads `instance != null` at (1) is guaranteed to see the fully-constructed object.

```java
private static volatile Cache instance; // volatile is the fix
```

**Modern alternative — Initialization-on-demand holder (preferred):**
```java
class Cache {
    private Cache() {}
    private static class Holder {
        static final Cache INSTANCE = new Cache(); // loaded lazily on first reference
    }
    public static Cache getInstance() { return Holder.INSTANCE; }
}
```
The JVM guarantees class initialization is atomic and visible to all threads. No `volatile`, no `synchronized` in the hot path — just the class loader's initialization lock, held once. This is both simpler and free of subtle memory model concerns.

**Or just use enum (for true singletons):**
```java
public enum Cache {
    INSTANCE;
    // JVM guarantees: thread-safe, lazy (loaded on first use), serialization-safe
}
```

**Interview trap:** "Is `volatile` on a field sufficient without `synchronized` in DCL?" — Yes, in Java 5+ with the updated JMM. The `volatile` write at construction prevents reordering, and the `volatile` read at the first null check establishes the happens-before. The `synchronized` block is still needed to prevent two threads from both passing the first null check and both running construction — but the `volatile` prevents the partial-construction visibility issue.

**Tags:** double-checked-locking, volatile, jmm, singleton, initialization

---
