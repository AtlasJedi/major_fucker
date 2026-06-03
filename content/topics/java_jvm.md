# JVM Internals — question bank

> The JVM is the engine every Java/Kotlin engineer relies on but few truly understand. Senior backend interviews test whether you know what actually runs your code: how memory is laid out, why GC pauses happen, how the JIT rewrites hot paths at runtime, and how to diagnose a production fire when the heap is full and the app is unresponsive. This bank covers the runtime data areas, garbage collection algorithms (generational, G1, ZGC/Shenandoah), reference types, class loading, JIT compilation, memory leak patterns, OOM types, diagnostic tooling, and key JVM flags. Depth expected: not just definitions — internals, operational trade-offs, and the exact failure modes a real senior encounters.

## Scope

- Runtime data areas: heap, JVM stack (frames, operand stack, local vars), metaspace, PC register, native method stack
- Object layout: mark word, class pointer, instance data, padding/alignment
- GC generational model: eden, survivor spaces (S0/S1), old gen, minor GC, major GC, full GC
- G1GC: regions, remembered sets, concurrent marking, pause-time target, humongous objects
- ZGC and Shenandoah: low-pause goals, colored pointers / load barriers, concurrent compaction
- GC roots and reachability analysis; reference types: strong, soft, weak, phantom + use cases
- Class loading: loading / linking (verify, prepare, resolve) / initialization; parent-delegation model; custom class loaders; classloader leaks
- JIT compilation: C1 (client) vs C2 (server), tiered compilation (levels 0-4), profiling, inlining, escape analysis, scalar replacement, stack allocation, deoptimization triggers
- Memory leaks: static collections, ThreadLocal leaks, classloader leaks, listener/observer leaks
- OOM kinds: Java heap space, Metaspace, GC overhead limit exceeded, Direct buffer memory, Compressed class space
- Key JVM flags: -Xmx/-Xms, -XX:+UseG1GC/-XX:+UseZGC, -XX:MaxGCPauseMillis, -XX:+HeapDumpOnOutOfMemoryError, -Xlog:gc*
- Diagnostics: jmap, jstack, jstat, jcmd, Java Flight Recorder, heap dump analysis, GC log interpretation
- Safe points and stop-the-world pauses

---

## Q-JVM-001 [bloom: recall] [level: junior]
**Question:** Name the main runtime data areas of the JVM. What lives in each one?
**Model answer:** The JVM spec defines five runtime data areas:

1. **Heap** — shared across all threads. All object instances and arrays are allocated here. Divided into generations (young gen: eden + two survivor spaces; old gen) in most collectors. Garbage-collected.
2. **JVM Stack** (per-thread) — holds stack frames. Each frame contains local variable array, operand stack, and reference to the runtime constant pool of the current method. Created on thread creation; removed on thread termination. `StackOverflowError` when depth exceeded.
3. **Metaspace** (Java 8+; replaced PermGen) — stores class metadata: class structures, method bytecode, constant pools, field/method descriptors. Off-heap (native memory), grows dynamically by default. `OutOfMemoryError: Metaspace` when native memory is exhausted.
4. **PC Register** (per-thread) — program counter; holds address of current JVM instruction being executed. For native methods, value is undefined.
5. **Native Method Stack** (per-thread) — stack for native (JNI) method calls. `StackOverflowError` or `OutOfMemoryError` can originate here.

Additionally: **Runtime Constant Pool** is per-class and lives inside Metaspace; the **Code Cache** (also off-heap) stores JIT-compiled native code.

**Interview trap:** "Is the heap shared across threads?" — yes, all object instances. Stack frames are per-thread private. The implication: heap objects need synchronization; stack-local variables (primitives or object references that don't escape) are inherently thread-safe. A follow-up: "Where do static fields live?" — in the Class object stored in Metaspace (the object itself, which holds the field values, is on the heap from Java 8+).
**Tags:** jvm, runtime-data-areas, heap, stack, metaspace

---

## Q-JVM-002 [bloom: recall] [level: junior]
**Question:** What is the difference between `-Xms` and `-Xmx`, and why would you set them to the same value?
**Model answer:** `-Xms` sets the **initial** (minimum) heap size. `-Xmx` sets the **maximum** heap size. On JVM startup, the heap starts at `-Xms` and can grow up to `-Xmx` as demand increases.

Setting them equal (`-Xms4g -Xmx4g`) avoids heap-resize operations at runtime. When the heap grows, the JVM must request more memory from the OS, which can cause a brief pause and may trigger a full GC. In production containerized services with predictable load, equal values are preferred because:
- Eliminates resize pauses.
- Makes memory usage predictable (container memory limits can be set accurately).
- Prevents the JVM from appearing to "leak" memory to ops monitoring (the heap grew because traffic grew, not because of a real leak).

Downside: reserves memory even at low load — wasteful if many small services share a node.

**Interview trap:** "What's the default heap size?" — typically 1/4 of available RAM up to a cap (exact formula varies by JVM version and GC). In containers without `-Xmx`, the JVM may read host RAM rather than container limit, causing OOM kills. Fix: use `-XX:MaxRAMPercentage=75.0` (Java 10+) or explicit `-Xmx`.
**Tags:** jvm-flags, heap, memory, operations

---

## Q-JVM-003 [bloom: recall] [level: junior]
**Question:** What are the four types of references in Java and when would you use each?
**Model answer:** Java has four reference strength levels, all in `java.lang.ref`:

| Type | GC behavior | Primary use case |
|------|-------------|-----------------|
| **Strong** | Never collected while reachable | Default; everything via `=` |
| **Soft** `SoftReference<T>` | Collected only when JVM needs memory (before OOM) | Memory-sensitive caches: image thumbnails, parsed configs |
| **Weak** `WeakReference<T>` | Collected at next GC if no strong refs exist | Canonicalizing mappings, listener registries (`WeakHashMap`) |
| **Phantom** `PhantomReference<T>` | Enqueued to `ReferenceQueue` AFTER object is finalized | Post-mortem cleanup, off-heap resource tracking (better than `finalize()`) |

Key operational details:
- `SoftReference`: JVM is allowed (not required) to clear them before OOM. Policy is implementation-specific. HotSpot: clears soft refs if the ref hasn't been accessed in at least `-XX:SoftRefLRUPolicyMSPerMB` ms per MB of free heap (default 1000ms/MB).
- `WeakHashMap`: keys are weak. When a key is GC'd, the entry is eventually removed. Watch out: values are strong — if a value holds a strong reference back to the key, the entry never gets collected.
- `PhantomReference.get()` always returns null (by design). You enqueue it to a `ReferenceQueue` to detect post-mortem cleanup opportunities without the pitfalls of `finalize()`.

**Interview trap:** "Can a SoftReference cause OOM?" — yes. If the JVM clears all soft refs and still can't allocate, it throws OOM. Soft refs don't prevent OOM; they merely delay it. Also: `finalize()` resurrects objects (strong ref assigned in `finalize()`) — a notorious source of memory leaks; phantom refs are the safe alternative.
**Tags:** reference-types, gc, soft-reference, weak-reference, phantom-reference

---

## Q-JVM-004 [bloom: recall] [level: junior]
**Question:** What is a GC root? Give concrete examples of what counts as a GC root in HotSpot.
**Model answer:** A **GC root** is an object that is reachable by definition — not through another heap object, but through an external anchor. The GC starts reachability analysis from all roots and marks everything transitively reachable. Anything not marked is garbage.

HotSpot GC roots include:
- **Local variables and method parameters** on JVM stack frames of all live threads.
- **Active thread objects** themselves (`java.lang.Thread` instances).
- **Static fields** of loaded classes (held via the Class object in Metaspace, the field value on heap).
- **JNI references** (global JNI refs held by native code; local JNI refs on the native stack).
- **Synchronization monitors** (objects used in `synchronized` blocks / intrinsic lock objects).
- **JVM internals**: interned strings (`String.intern()`), classes loaded by bootstrap classloader, references in JIT code (via OopMaps at safe points).

Implication: a static `Map<String, SomeObject>` that grows indefinitely is a classic leak — all values are strongly reachable via the static field GC root and will never be collected.

**Interview trap:** "Is a thread itself a GC root?" — yes, the Thread object is a GC root, and it holds its ThreadLocal map. If a ThreadLocal value contains a large object and the thread is pooled (as in a servlet container), that object leaks for the lifetime of the thread. This is the ThreadLocal leak pattern.
**Tags:** gc-roots, reachability, garbage-collection, memory-leak

---

## Q-JVM-005 [bloom: recall] [level: junior]
**Question:** What are the stages of class loading in the JVM, and what happens at each stage?
**Model answer:** Class loading has three phases, followed by use:

**1. Loading**
The ClassLoader reads the `.class` file (bytecode), creates a binary representation, and creates a `java.lang.Class` object in the Metaspace. The `.class` file can come from filesystem, JAR, network, or be generated at runtime (e.g., CGLIB proxy).

**2. Linking** (three sub-steps):
- **Verification** — bytecode verifier checks structural correctness: valid opcodes, stack consistency, type safety, no illegal memory access. Ensures the class can be executed safely. Can be disabled with `-Xverify:none` (dangerous).
- **Preparation** — allocates memory for static fields and sets them to default zero values (0, null, false). NOT your declared initial values yet.
- **Resolution** — resolves symbolic references in the constant pool to concrete runtime references (class references, method references, field references). May be lazy (happens on first use).

**3. Initialization**
The class initializer (`<clinit>`) is executed: static field assignments and static blocks run in textual order. This is where `private static final int MAX = 100;` actually gets the value 100. Guaranteed thread-safe by the JVM (class init lock). Runs at most once per ClassLoader.

**Interview trap:** "When does initialization happen?" — on first active use: `new MyClass()`, calling a static method, accessing/assigning a static field (not a compile-time constant), `Class.forName()`, or a subclass initialization. Accessing a `static final` compile-time constant does NOT trigger initialization (the value is inlined by the compiler). This is the basis for the Initialization-on-Demand Holder pattern.
**Tags:** class-loading, linking, initialization, classloader

---

## Q-JVM-006 [bloom: recall] [level: junior]
**Question:** What is the parent-delegation model for class loading?
**Model answer:** When a ClassLoader is asked to load a class, it **delegates to its parent first** before attempting to load it itself. The hierarchy:

```
Bootstrap ClassLoader  (loads rt.jar / JDK core classes, native C++)
        ↑ parent
Extension / Platform ClassLoader  (loads JDK extension modules)
        ↑ parent
Application ClassLoader  (loads classpath)
        ↑ parent
Custom ClassLoaders (e.g., OSGi, app server, Spring DevTools)
```

Algorithm:
1. Check if class already loaded (`findLoadedClass`).
2. Delegate to parent (`parent.loadClass`).
3. If parent throws `ClassNotFoundException`, try `findClass` in this loader.

**Why this matters:**
- **Security:** `java.lang.String` is always the one from Bootstrap — a malicious `String.class` on the classpath can't override it.
- **Consistency:** core JDK classes loaded once; no duplicates.
- **Isolation:** custom loaders can break delegation for isolation (OSGi, application servers) — this is a "child-first" or "inverted" delegation. Common in Tomcat: each webapp has its own ClassLoader, loads webapp classes first, falls back to shared.

**Interview trap:** "What is the consequence of two ClassLoaders loading the same class?" — they produce two distinct `Class` objects. Instances of Class A loaded by loader 1 are NOT `instanceof` Class A loaded by loader 2. This is the classloader hell problem in app servers and OSGi. Cast exception even though the class "looks the same."
**Tags:** classloader, parent-delegation, class-loading

---

## Q-JVM-007 [bloom: understand] [level: regular]
**Question:** Explain the generational hypothesis and how it shapes the JVM heap layout. Describe a minor GC cycle in detail.
**Model answer:** **The generational hypothesis:** most objects die young. Empirical data across many workloads shows that the vast majority of objects become unreachable within a few milliseconds of allocation (request-scoped objects, temporaries, etc.). A GC that exploits this only needs to scan the young generation most of the time.

**Heap layout (traditional, pre-G1):**

```
|<--- Young Gen (20-30% default) --->|<--- Old Gen (70-80%) --->|
|  Eden  |  Survivor 0  |  Survivor 1 |       Old / Tenured      |
```

- **Eden**: where all new objects are allocated (bump pointer allocator per thread — Thread-Local Allocation Buffers, TLABs — make allocation nearly free).
- **Survivor spaces (S0/S1)**: one is always empty. Objects surviving a GC bounce between them.
- **Old gen**: objects that survived enough minor GCs (`-XX:MaxTenuringThreshold`, default 15 for parallel GC) are promoted.

**Minor GC (young gen collection):**
1. Stop-the-world: all app threads reach a safe point and pause.
2. Mark from GC roots + remembered set (references from old gen into young gen). Only young gen scanned.
3. Live young-gen objects copied to the empty survivor space (copy collector — no fragmentation, cheap allocation reset for Eden).
4. Objects that exceed tenuring threshold or survivor space is full → promote to old gen.
5. Clear Eden and used survivor. Resume application threads.

Minor GC is fast (milliseconds) because it only scans a fraction of the heap and copying is cache-friendly.

**Major / Full GC:** collects old gen (or both). Far more expensive. Triggered when old gen fills up or promotion fails.

**Interview trap:** "What is a promotion failure?" — during minor GC, if old gen has insufficient contiguous space to hold promoted objects, the JVM falls back to a full GC. This is a worst-case pause. Common cause: fragmented old gen or undersized old gen. Symptom visible in GC logs as `(promotion failed)`.
**Tags:** generational-gc, minor-gc, eden, survivor, tenuring, tlab

---

## Q-JVM-008 [bloom: understand] [level: regular]
**Question:** How does G1GC work? What does "pause time target" mean and how does G1 honor (or fail to honor) it?
**Model answer:** G1 (Garbage First, default since Java 9) replaces the fixed young/old layout with a **region-based heap**: the heap is divided into equal-sized regions (1MB–32MB, power of 2, chosen based on heap size). Regions are dynamically assigned roles: Eden, Survivor, Old, or Humongous.

**Key mechanisms:**

1. **Concurrent marking** — G1 runs concurrent marking threads alongside the application to identify live objects in old-gen regions. Doesn't stop the world (mostly).

2. **Remembered Sets (RSets)** — each region maintains a card table of inter-region references pointing into it. During minor/mixed collection, G1 uses RSets to find all references into collected regions without scanning the whole heap.

3. **Pause-time target** (`-XX:MaxGCPauseMillis`, default 200ms) — G1 builds a predictive model (based on past GC data) of how many regions it can collect within the target pause. It prioritizes regions with the most garbage (hence "Garbage First"). It may not collect all garbage in one pause — that's intentional.

4. **Mixed GC** — after concurrent marking, G1 runs mixed collections: collect all young regions + a subset of old regions (those with most garbage). Gradually reclaims old gen without a full stop-the-world.

5. **Full GC (last resort)** — if G1 can't reclaim enough space concurrently (e.g., allocation rate exceeds reclamation rate), it falls back to a serial full GC. This is the "G1 full GC" log entry you never want to see in production.

**Humongous objects:** objects >= 50% of region size. Allocated directly in old gen (may span multiple contiguous regions). Not collected in minor GC. Can cause fragmentation and unexpected full GCs. Avoid frequent large allocations.

**Interview trap:** "Can G1 miss its pause target?" — yes, regularly. The 200ms is a soft goal, not a hard SLA. G1 may exceed it if: too many regions need collection, RSets are dirty (lots of cross-region references), or concurrent marking falls behind allocation rate (concurrent mode failure). Tune with `-XX:G1NewSizePercent`, `-XX:G1MaxNewSizePercent`, and by increasing region size for large heaps.
**Tags:** g1gc, regions, remembered-set, concurrent-marking, pause-target, humongous

---

## Q-JVM-009 [bloom: understand] [level: regular]
**Question:** What is tiered JIT compilation? Describe compilation levels 0–4 and when each applies.
**Model answer:** HotSpot uses a **tiered compilation** pipeline (default since Java 8) with two JIT compilers:

- **C1 (Client compiler)**: fast compilation, limited optimization. Produces decent-quality native code quickly.
- **C2 (Server compiler)**: slow, aggressive optimization (inlining, escape analysis, loop unrolling, etc.). Produces highly optimized native code.

**Compilation levels:**

| Level | Execution mode | Profiling | When used |
|-------|---------------|-----------|-----------|
| **0** | Interpreter | Full profiling (type, branch, call count) | Cold start; initial execution |
| **1** | C1-compiled | No profiling | Trivial methods (very short), no future optimization needed |
| **2** | C1-compiled | Limited profiling | Medium-frequency methods |
| **3** | C1-compiled | Full profiling | Hot methods waiting for C2 compilation |
| **4** | C2-compiled | No profiling (stable) | Hot methods, fully optimized |

**Typical lifecycle:** A method starts at level 0 (interpreter). As it heats up, C1 compiles it to level 3 (with profiling). Once enough profiling data is collected (invocation count + back-edge count exceed thresholds, roughly `-XX:CompileThreshold` = 10,000 for server mode), C2 compiles it to level 4. Level 4 replaces level 3.

**Why not go straight to C2?** C2 compilation is expensive (seconds for complex methods). C1 level 3 keeps the method fast while C2 works in the background.

**Interview trap:** "What happens when profiling data collected by C1 turns out to be wrong?" — C2 makes speculative optimizations (e.g., devirtualization assuming a call site is monomorphic). If a new class is loaded that violates the assumption, the optimized code is **deoptimized** — thrown away, execution falls back to the interpreter or C1. Deoptimization is a performance cliff and is visible via `-XX:+TraceDeoptimization` or JFR.
**Tags:** jit, tiered-compilation, c1, c2, profiling, deoptimization

---

## Q-JVM-010 [bloom: understand] [level: regular]
**Question:** What is escape analysis in the JVM JIT, and what optimizations does it enable?
**Model answer:** **Escape analysis** is a JIT (C2) optimization that determines whether an object's reference can "escape" the method or thread in which it was created.

**Escape categories:**
- **NoEscape**: object only used locally within the method, reference never stored anywhere observable outside.
- **ArgEscape**: object passed as an argument or returned, but doesn't escape to the heap in an uncontrolled way.
- **GlobalEscape**: object assigned to a static field, a heap field of another escaping object, or returned through an escaping reference. Truly escapes.

**Optimizations enabled for NoEscape/ArgEscape objects:**

1. **Stack allocation**: instead of allocating on the heap (triggering GC), allocate on the JVM stack frame. Object is automatically "freed" when method returns. Zero GC pressure. Example: a temporary `StringBuilder` used only inside a method.

2. **Scalar replacement**: the object is decomposed into its individual fields, which are stored in registers or local variables. No object header, no heap allocation at all. Example: a small `Point(x, y)` used in a loop may be replaced by two `int` registers.

3. **Lock elision**: if a `synchronized` block on an object that doesn't escape is detected, the lock acquisition/release is eliminated entirely (no contention possible since no other thread can see the object).

**Caveats:**
- Escape analysis is per-method after inlining. If the JIT doesn't inline a method, the callee's use of the object is opaque and assumed to escape.
- Large or complex objects may not be analyzed successfully.
- Visible via: `-XX:+PrintEscapeAnalysis` (diagnostic) or JFR allocation profiling.

**Interview trap:** "Does escape analysis mean you never need to worry about allocations in tight loops?" — no. EA is best-effort; the JIT may fail to apply it. For garbage-sensitive hot paths (HFT, real-time), explicit object pooling or value types (Project Valhalla) may still be necessary. Also: in GraalVM native-image, EA is applied at build time, not runtime — different trade-offs.
**Tags:** escape-analysis, stack-allocation, scalar-replacement, lock-elision, jit

---

## Q-JVM-011 [bloom: understand] [level: regular]
**Question:** What is a safe point in the JVM and why does it matter for GC pauses?
**Model answer:** A **safe point** is a point in a thread's execution where the JVM knows the exact state of all object references (via OopMaps) and can safely modify them. The GC (and several other JVM operations like biased lock revocation, class redefinition) requires all application threads to be stopped at a safe point before proceeding — this is a **stop-the-world** (STW) pause.

**How threads reach safe points:**
- The JVM sets a safe-point flag/poll location.
- Threads check the flag at compiled safe-point polls: method returns, loop back edges, certain bytecodes.
- Thread at a safe point: suspended and reports its register state.
- Thread NOT at a safe point (e.g., in native code): JVM waits until it returns from native. The thread is considered "at a safe point" while in native code (it can't modify the Java heap).

**Time to safe point (TTSP):** the wall-clock time from when the JVM requests STW to when the last thread arrives. TTSP adds to observed GC pause time. A thread stuck in a long interpreted loop (no back-edge safe-point polls) can delay STW for hundreds of ms. This is the "safe-point bias" problem — why a GC pause reported as 50ms might cause 200ms application latency.

**What requires safe points:**
- All STW GC phases (minor, major, full GC; G1's initial/remark phases).
- Deoptimization.
- Biased locking revocation.
- `Thread.stop()`, `Thread.getStackTrace()`.

**Interview trap:** "Does G1 eliminate stop-the-world pauses?" — no. G1 is concurrent (marking runs concurrently) but still has STW pauses for initial-mark and remark phases, and for the actual evacuation (young/mixed GC). ZGC and Shenandoah are designed to make STW pauses sub-millisecond regardless of heap size.
**Tags:** safe-point, stop-the-world, gc-pause, ttsp

---

## Q-JVM-012 [bloom: understand] [level: regular]
**Question:** Describe the most common JVM memory leak patterns. What causes each and how do you detect it?
**Model answer:** **1. Static collection growth**
```java
public class Registry {
    private static final Map<String, UserSession> SESSIONS = new HashMap<>();
    // sessions added on login, never removed
}
```
Root cause: static field is a GC root; values are strongly reachable forever. Detection: heap dump shows large `HashMap` retained by a static field; class histogram shows many instances of the value type.

**2. ThreadLocal leak in thread pools**
```java
private static ThreadLocal<HeavyContext> CTX = new ThreadLocal<>();
// set in HTTP request; not removed when request ends
```
In a thread pool (Tomcat, gRPC, etc.), threads are reused. `ThreadLocal` values survive between requests. Thread is GC root → ThreadLocalMap entry → value → large object retained indefinitely. Detection: jstack shows many threads with same threadlocal; heap dump shows `ThreadLocalMap` entries with large retained objects. Fix: always call `CTX.remove()` in a `finally` block.

**3. Classloader leak (most insidious)**
Occurs in apps that load classes dynamically (app servers, Groovy scripts, CGLIB proxies, Hibernate). If any live object holds a strong reference to a class instance (via its `Class` object), the entire classloader (and all classes it ever loaded) can't be GC'd from Metaspace.
Symptom: `OutOfMemoryError: Metaspace` after repeated redeployments. Detection: heap dump, look for multiple `ClassLoader` instances; check what holds strong references to them.

**4. Unregistered event listeners / observers**
```java
eventBus.register(myListener); // never eventBus.unregister(myListener)
```
The event bus holds a strong reference to the listener, which transitively holds all the listener's fields. Common in Android, Swing, and server-side pub/sub systems.

**5. Unbounded cache with strong references**
`@Cacheable` without eviction, or `new HashMap<>()` used as a naive cache. Detection: heap dump shows a single Map retaining gigabytes via dominator tree.

**6. Unclosed resources (indirect)**
Not directly a heap leak, but JDBC `ResultSet`, `InputStream` via finalizers may hold native handles and slow down GC.

**Interview trap:** "WeakHashMap prevents leaks?" — only for keys. Values in a `WeakHashMap` are still strongly referenced. If a value holds a strong reference back to its key, the entry never gets evicted. True weak-value caches require `WeakReference` values or Guava/Caffeine's `weakValues()`.
**Tags:** memory-leak, thread-local, classloader, static-collection, gc

---

## Q-JVM-013 [bloom: understand] [level: regular]
**Question:** What are the different kinds of `OutOfMemoryError` in the JVM? What causes each?
**Model answer:** `OutOfMemoryError` is not one thing — the message tells you where the JVM ran out:

| Message | Cause | Typical fix |
|---------|-------|-------------|
| `Java heap space` | Heap full, GC can't reclaim enough. Could be a real leak or undersized heap. | Heap dump analysis, increase `-Xmx`, fix leak |
| `GC overhead limit exceeded` | JVM spending >98% of time in GC, recovering <2% of heap per collection, for multiple consecutive GCs. Heap near full; GC is futile. | Same as above; this fires before heap is 100% full, giving you a window to diagnose |
| `Metaspace` | Class metadata space exhausted. Caused by: classloader leaks, excessive dynamic class generation (CGLIB, Groovy compilation, Javassist, Hibernate proxies). | Set `-XX:MaxMetaspaceSize` to prevent unconstrained growth; fix classloader leaks |
| `Compressed class space` | Separate 1GB (default) region for class pointers when compressed oops enabled. Exhausted by massive number of classes. | Increase with `-XX:CompressedClassSpaceSize` |
| `Direct buffer memory` | Off-heap NIO `ByteBuffer.allocateDirect()` exhausted. Governed by `-XX:MaxDirectMemorySize` (default = `-Xmx`). Common with Netty. | Find buffer leaks (buffers not returned to pool), increase limit, use `-XX:+DisableExplicitGC` carefully |
| `Unable to create new native thread` | OS-level limit on threads (ulimit, cgroup). Not a JVM heap issue. | Reduce thread count, increase OS limits, switch to virtual threads |
| `array size exceeds VM limit` | Allocating an array larger than ~2GB (Integer.MAX_VALUE elements). | Redesign data structure |

**Interview trap:** "What's the difference between GC overhead limit exceeded and Java heap space?" — GC overhead fires while there's still some heap left; it's an early warning that the app is about to run out. Java heap space fires when allocation literally can't be satisfied. Some apps disable the overhead limit check (`-XX:-UseGCOverheadLimit`) to get the actual OOM with a dump — but this trades early warning for late crash.
**Tags:** oom, outofmemoryerror, metaspace, direct-buffer, gc-overhead

---

## Q-JVM-014 [bloom: apply] [level: senior]
**Question:** Walk through how you would diagnose a production JVM process that is consuming 95%+ CPU and responding extremely slowly. Assume you have SSH access and standard JDK tools.
**Model answer:** **Step 1: Confirm GC is the culprit**
```bash
jstat -gcutil <pid> 1000 20   # GC stats every 1s for 20 samples
# Look for: YGC/FGC rates, FGCT (full GC time), %GC time
# If GC time > 80% of wall time → GC death spiral
```

Also check GC logs if enabled (`-Xlog:gc*:file=/path/gc.log:time,uptime`). Look for full GC entries every few seconds.

**Step 2: If not GC, identify hot threads**
```bash
# Get thread dump
jstack <pid> > /tmp/tdump.txt

# Get CPU per thread from OS
top -H -p <pid>    # Linux: shows per-thread CPU (TID = LWP)
ps -eLf | grep <pid>

# Map Linux TID to Java thread: convert TID to hex, match in jstack
printf "%x\n" <tid>    # e.g., 12345 → 0x3039
grep -A 20 "nid=0x3039" /tmp/tdump.txt
```

**Step 3: Capture heap state**
```bash
jmap -histo:live <pid>          # class histogram (forces GC, brief pause)
jmap -dump:format=b,file=/tmp/heap.hprof <pid>    # full heap dump
```
Analyze dump with Eclipse MAT: dominator tree, leak suspects, class histogram.

**Step 4: JFR continuous profiling (preferred in prod)**
```bash
jcmd <pid> JFR.start duration=60s filename=/tmp/rec.jfr settings=profile
# After 60s:
jcmd <pid> JFR.dump filename=/tmp/rec.jfr
```
Open in JDK Mission Control (JMC). Shows: CPU hot methods, allocation profiling, GC events, lock contention, file/network I/O.

**Step 5: Interpret findings**
- GC death spiral + high heap usage → find what's growing (leak or traffic spike).
- Hot thread in user code → CPU-bound computation; look for O(n²) algorithms, missing index, large data loaded into memory.
- Hot thread in `Object.wait` / `LockSupport.park` → lock contention; look for thread pool saturation, synchronized blocks.

**Interview trap:** "How do you get a heap dump without killing the process?" — `jmap -dump` or `jcmd <pid> GC.heap_dump` on a live JVM. Both cause a brief STW (to get a consistent snapshot). For minimal disruption, enable `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path` so the dump is automatic. Alternatively, use JFR heap sampling which is lower overhead but less complete.
**Tags:** diagnostics, jmap, jstack, jstat, jcmd, jfr, heap-dump, cpu-profiling

---

## Q-JVM-015 [bloom: apply] [level: senior]
**Question:** You're running a service with a 16GB heap on G1GC. Pause times are acceptable (p99 ~150ms) but every few hours you see a Full GC in the logs that causes a 10-second pause. How do you investigate and fix this?
**Model answer:** A G1 Full GC is the fallback when G1's concurrent mechanisms fail. Root causes:

**1. Humongous allocation exhausting regions**

G1 full GC log entry: `(Humongous before full collection)`. Objects >= 50% of region size are allocated in old gen directly and only collected at Full GC (unless G1 Humongous Reclaimation kicks in — available Java 12+). Fix:
```bash
# Check region size
-XX:G1HeapRegionSize=<N>m   # increase if large objects < 2x region size
```
Find the allocation source via JFR allocation profiling or async-profiler.

**2. Concurrent marking can't keep up (Concurrent Mode Failure)**

Symptom: `(concurrent humongous allocation)` or `(to-space exhausted)` in GC log. Allocation rate exceeds G1's ability to reclaim old gen concurrently. Fix:
```bash
-XX:InitiatingHeapOccupancyPercent=35   # start marking earlier (default 45)
-XX:G1ReservePercent=15                 # more headroom in old gen (default 10)
-XX:ConcGCThreads=4                     # more concurrent marking threads
```

**3. Metaspace growth**

Full GC triggered by Metaspace exhaustion. Check logs for `(Metadata GC Threshold)`. Fix:
```bash
-XX:MetaspaceSize=256m         # set initial (avoids resize overhead)
-XX:MaxMetaspaceSize=512m      # cap it; uncapped Metaspace can exhaust native memory
```

**4. Explicit `System.gc()` calls**

Some libraries call `System.gc()` (e.g., old RMI, some JDBC drivers). G1 responds with a full GC. Fix:
```bash
-XX:+DisableExplicitGC    # ignore System.gc() calls
# or
-XX:+ExplicitGCInvokesConcurrent   # make it concurrent instead
```

**Diagnostic workflow:**
```bash
# Enable detailed GC logging
-Xlog:gc*:file=/var/log/gc.log:time,uptime,pid:filecount=10,filesize=50m
# Look for cause in "cause:" field of Full GC entries
grep "Full GC" /var/log/gc.log | tail -20
```

**Interview trap:** "Should you switch to ZGC to avoid this?" — maybe, but first understand the root cause. If it's a humongous allocation you control, fix the allocation. If it's a genuine high-allocation-rate service needing sub-ms pauses at scale, ZGC is the right call. Jumping to a new GC without understanding the cause often just moves the problem.
**Tags:** g1gc, full-gc, humongous, concurrent-mode-failure, gc-tuning, senior

---

## Q-JVM-016 [bloom: apply] [level: senior]
**Question:** Explain how ZGC achieves sub-millisecond pause times regardless of heap size. What are colored pointers and load barriers?
**Model answer:** ZGC's key insight: move the expensive work (concurrent relocation) off the STW phase into concurrent threads running alongside the application. STW phases are only for root scanning and a short remark — O(number of GC roots), not O(heap size). Hence, pause time is ~1ms regardless of whether the heap is 4GB or 4TB.

**The fundamental challenge:** concurrent relocation (moving objects while app threads run) creates a problem — an app thread might read a stale pointer to an object's old location while the GC is moving it.

**Colored pointers (ZGC's solution):**
ZGC uses 64-bit pointers and steals metadata bits:
```
Bit layout (simplified):
[18 bits: reserved/OS] [1: finalizable] [1: remapped] [1: marked1] [1: marked0] [42 bits: address]
```
The "color" bits encode the GC state of the pointer:
- `marked`: object is live (in current marking cycle).
- `remapped`: pointer points to the object's current (post-relocation) address.

**Load barriers:**
Every time application code loads an object reference from the heap, ZGC inserts a small code fragment (load barrier):
```
// Pseudocode generated by JIT
T ref = *address;       // load
if (ref.color != expected_color) {
    ref = slow_path(ref);   // fix up stale pointer, update in place
}
use(ref);
```
The slow path: if the pointer is stale (object was moved), the barrier finds the new location (via forwarding table), heals the pointer in memory, and returns the correct reference. The app thread sees the new location transparently.

**Result:** relocation happens concurrently. The first access after relocation incurs one slow-path barrier (fixes the pointer); all subsequent accesses are fast. STW is only needed for root scanning (load/store roots in registers and stack) — typically under 1ms.

**Generational ZGC (Java 21):** adds young/old gen to ZGC, dramatically reducing load barrier overhead for most objects (most barriers only apply to young gen, which is smaller and collected more frequently). Significantly improves throughput vs non-generational ZGC.

**Shenandoah** takes a similar approach but uses a different mechanism (Brooks forwarding pointer — an extra word per object), available in OpenJDK since Java 12.

**Interview trap:** "ZGC has near-zero pauses — so throughput is free?" — no. Load barriers add overhead to every heap reference load. ZGC typically has 5–15% lower throughput than G1 on CPU-intensive workloads. Trade-off: latency vs throughput. For p99/p999 latency-sensitive services (trading, real-time, gaming), ZGC wins. For batch processing, G1 or Parallel GC may be better.
**Tags:** zgc, shenandoah, colored-pointers, load-barrier, low-pause, concurrent-relocation

---

## Q-JVM-017 [bloom: apply] [level: senior]
**Question:** A classloader leak is causing `OutOfMemoryError: Metaspace` after each application hot-redeploy. Walk through how you confirm, locate, and fix it.
**Model answer:** **Confirming the leak:**
```bash
# After each redeploy, Metaspace grows; trigger GC first
jcmd <pid> GC.run
jstat -gcmetacapacity <pid> 1000 5
# Or check via jmap
jmap -clstats <pid>    # per-classloader stats (Java 8+)
```
If classloader count grows with each redeploy and old loaders are never GC'd, confirmed.

**Heap dump approach:**
```bash
jmap -dump:format=b,file=/tmp/heap.hprof <pid>
```
In Eclipse MAT:
1. Run "Class Loader Explorer" or "OQL" query:
   ```
   SELECT cl FROM java.lang.ClassLoader cl
   ```
2. Look for multiple instances of your app's classloader (e.g., `WebappClassLoader` in Tomcat).
3. Use "Path to GC Roots" to find what strong reference is keeping the old classloader alive.

**Common culprits:**
- **Driver registration**: `java.sql.DriverManager` holds a static list of registered JDBC drivers. If your app registers a driver (often via `Class.forName` in an initializer), the driver is loaded by the webapp classloader and registered in the JVM-global DriverManager. After redeploy, the old webapp classloader can't be GC'd because DriverManager still holds a reference. Fix: deregister driver in `@PreDestroy` or `ServletContextListener.contextDestroyed()`.
- **Thread started by app, not stopped**: a background thread's classloader reference keeps the classloader alive. Fix: stop all threads in shutdown hook.
- **Static fields in JDK classes holding app objects**: `java.util.logging.Logger`, `ResourceBundle` cache, `Introspector` bean cache. Fix: call `Introspector.flushCaches()`, remove loggers.
- **CGLIB/javassist classes**: generated proxy classes loaded by webapp classloader. If a framework creates them and stores in a JVM-global cache, leak occurs. Fix: upgrade framework; some have fixes for this.

**Prevention:**
```bash
-XX:MaxMetaspaceSize=256m    # cap forces OOM instead of unbounded native memory growth
-Xlog:class+load=info        # log all class loads; useful for detecting leaks
```

**Interview trap:** "Can you use WeakReference to prevent classloader leaks?" — the reference to the classloader must be weak, not the classloader holding references to classes. Making the value of a cache a `WeakReference<ClassLoader>` would work if the cache is the only thing holding the loader. But if the loaded classes themselves hold strong references to objects that reference back to the classloader (common), weak-referencing the loader doesn't help.
**Tags:** classloader-leak, metaspace, oom, redeploy, heap-dump

---

## Q-JVM-018 [bloom: apply] [level: senior]
**Question:** What is JIT deoptimization? Give two concrete scenarios where the JVM deoptimizes previously compiled code and what the performance impact is.
**Model answer:** **Deoptimization** occurs when the JIT compiler's speculative assumptions are invalidated. C2-compiled code is thrown away; execution falls back to the interpreter or C1-compiled code. The JVM must reconstruct the interpreter frame from the optimized frame (frame state reconstruction), which is expensive.

**Scenario 1: Type profile invalidation (uncommon trap)**

C2 sees that a virtual call site has only ever been dispatched to one implementation (monomorphic). It devirtualizes the call (inlines it, eliminates the vtable dispatch). Later, a new implementation is loaded/used:

```java
interface Processor { void process(Item i); }
// C2 inlines FastProcessor.process() at this call site:
void handleAll(List<Processor> procs, Item item) {
    for (Processor p : procs) {
        p.process(item);   // only FastProcessor seen so far → inlined
    }
}
// Now SlowProcessor is instantiated and used → type assumption violated
// C2 code for handleAll is deoptimized and recompiled with a bimorphic/megamorphic dispatch
```

Performance impact: the deoptimized method runs in interpreter until it reheats through C1 back to C2. Visible as a latency spike when a new subclass first appears.

**Scenario 2: Escape analysis assumption broken by debugging / JVMTI**

When a debugger attaches (JVMTI), the JVM must ensure object identity is consistent. If C2 applied scalar replacement (eliminated heap allocation), a debugger can't inspect "the object" because it doesn't exist on the heap. The JVM deoptimizes all methods that had scalar-replaced objects. Re-attaching a profiler/debugger to a running prod JVM can cause a burst of deoptimizations.

**Other scenarios:**
- **Class redefinition** (`HotSwap`, Spring DevTools): deoptimizes everything using the redefined class.
- **Null check trap**: `NullPointerException` from a path C2 assumed was never taken.
- **Array bounds trap**: first out-of-bounds triggers deoptimization and recompilation with bounds checks.

**Detecting deoptimizations:**
```bash
-XX:+LogCompilation           # verbose compilation log (use JITWatch to analyze)
-XX:+TraceDeoptimization      # prints each deoptimization event
# Or via JFR: Deoptimization event
jcmd <pid> JFR.start duration=30s settings=profile filename=/tmp/r.jfr
```

**Interview trap:** "Should you avoid polymorphism to prevent deoptimization?" — no, that's premature pessimism. C2 handles bimorphic call sites (2 implementations) without full deoptimization (inline cache with type check). Megamorphic (3+ implementations) triggers vtable dispatch but not deoptimization. Deoptimization from type invalidation happens specifically when a previously monomorphic site becomes polymorphic for the first time.
**Tags:** deoptimization, jit, type-profile, uncommon-trap, speculative-optimization

---

## Q-JVM-019 [bloom: apply] [level: senior]
**Question:** What is a custom ClassLoader and when would you write one? What are the risks?
**Model answer:** A custom `ClassLoader` overrides `findClass(String name)` (preferred) or `loadClass(String name)` (breaks parent delegation if not careful) to control how bytecode is obtained and defined.

**Legitimate use cases:**
1. **Hot reload / dynamic deployment**: load a new version of a class at runtime (IDE HotSwap, Spring DevTools, OSGi). Each version needs its own classloader because a classloader can only load a class name once.
2. **Isolation**: application servers (Tomcat, Wildfly) give each webapp its own classloader so webapp A's `log4j-1.2.jar` doesn't conflict with webapp B's `log4j-2.x.jar`.
3. **Loading from non-standard sources**: encrypted JARs, network (old applets), database BLOBs.
4. **Bytecode transformation**: intercept class loading to apply instrumentation (AspectJ LTW, Java agents via `java.lang.instrument.ClassFileTransformer`).

**Minimal custom loader:**
```java
public class PluginClassLoader extends ClassLoader {
    private final Path pluginJar;
    public PluginClassLoader(Path jar, ClassLoader parent) {
        super(parent);
        this.pluginJar = jar;
    }
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = readBytesFromJar(pluginJar, name);
        if (bytes == null) throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length);
    }
}
```

**Risks:**
- **Classloader leak**: if any long-lived object holds a reference to a class loaded by this loader, the entire loader (and all its classes) stays in Metaspace forever. Common with `ThreadLocal`, static caches, JDBC driver registration.
- **ClassCastException at runtime**: a class loaded by two different loaders is two distinct types. Passing an object from loader A to code loaded by loader B causes `ClassCastException` even for the "same" class.
- **Class identity crisis**: `instanceof` returns false across loaders. Serialization can fail.
- **Broke parent delegation**: if `loadClass` is overridden without calling `super.loadClass` first, core JDK classes might be shadowed (security risk).

**Interview trap:** "Can two classes with the same name coexist in the JVM?" — yes, if loaded by different classloaders. This is how OSGi bundles work. But they're invisible to each other's code unless mediated through a shared interface loaded by a common parent loader.
**Tags:** classloader, custom-classloader, isolation, bytecode, hot-reload

---

## Q-JVM-020 [bloom: analyze] [level: senior]
**Question:** Your Java service has a p99 latency of 500ms under load but p50 is 5ms. Thread dumps show most threads are RUNNABLE. GC logs are clean (no Full GC, minor GC <20ms). What JVM-level investigation do you do next?
**Model answer:** The combination of p99 >> p50 with clean GC and RUNNABLE threads points toward:

**Hypothesis 1: Safe-point bias**
GC logs report GC pause, not total STW. Time-to-safe-point (TTSP) is invisible in standard GC logs. A thread stuck in a compiled loop (no safe-point poll) can delay all STW operations.

```bash
-Xlog:safepoint:file=/tmp/sp.log:time,uptime   # log safepoint operations and TTSP
# Look for: "Total time for which application threads were stopped: Xms, Stopping threads took: Yms"
# If Stopping threads took >> 10ms → safe-point bias
```

Mitigation: `-XX:+UseCountedLoopSafepoints` (Java 11+) inserts safe-point polls in counted loops.

**Hypothesis 2: JIT compilation pauses**
When a method is first compiled by C2, the compilation thread can briefly contend for JVM resources. Visible via:
```bash
-XX:+PrintCompilation   # or JFR: Compilation event
```
Typically not 500ms, but can contribute.

**Hypothesis 3: Lock contention not visible in thread dump**
Thread dumps are snapshots; threads might briefly block and be RUNNABLE by the time dump is taken. Use JFR:
```bash
jcmd <pid> JFR.start duration=60s settings=profile filename=/tmp/rec.jfr
```
In JMC: "Lock Instances" view shows lock contention histograms. "Method Profiling" shows CPU hot spots.

**Hypothesis 4: OS-level: CPU scheduling jitter, GC card table scanning**
On a shared host, OS scheduler can preempt threads unpredictably. `vmstat`, `perf stat`, `perf sched latency`.

**Hypothesis 5: JIT deoptimization bursts**
A new class being loaded triggers deoptimization of hot compiled methods; code falls to interpreter briefly. Check JFR Deoptimization events or `-XX:+TraceDeoptimization`.

**Hypothesis 6: Concurrent GC work stealing CPU**
G1 concurrent marking threads run alongside application. If concurrent threads are miscounted, they steal CPU from application threads. Check: `ps -eLf | grep <pid>` to count GC threads; `-XX:ConcGCThreads` if needed.

**Structured approach:** Use JFR + JMC first (lowest overhead, richest data). In JMC check: "Garbage Collection", "Lock Instances", "Method Profiling", "Exceptions" (thrown exceptions can be expensive), "File I/O" / "Socket I/O".

**Interview trap:** "Could it be GC log reporting that's wrong?" — yes. `-Xlog:gc*` with `:time` option shows wall-clock time; the GC pause shown is from start of STW to resume. But TTSP is separate. Enable `-Xlog:safepoint` to see the full picture. The 500ms spikes might correlate precisely with safepoint operations on the safepoint log.
**Tags:** latency, safe-point, jfr, deoptimization, gc-pause, lock-contention, p99

---

## Q-JVM-021 [bloom: analyze] [level: senior]
**Question:** What is object layout in HotSpot? How large is a "typical" Java object and what determines its size?
**Model answer:** Every Java object on the heap has a **header** followed by instance data. In HotSpot (64-bit JVM with compressed oops, the default up to 32GB heap):

```
Object layout (compressed oops enabled, -XX:+UseCompressedOops):
┌────────────────────────────────────────────┐
│  Mark Word (8 bytes)                       │  → GC age, lock state, identity hash
│  Class Pointer (4 bytes, compressed)       │  → pointer to class metadata
├────────────────────────────────────────────┤
│  Instance fields (aligned, ordered by JVM) │
│  [padding to 8-byte alignment]             │
└────────────────────────────────────────────┘
Minimum object size: 16 bytes (header 12 + padding 4)
```

**Mark Word (8 bytes)** encodes:
- Object's identity hash code (computed lazily on `System.identityHashCode()`).
- GC age (4 bits → max 15, hence `MaxTenuringThreshold` max 15).
- Locking state bits: unlocked, biased-locked, lightweight-locked, heavyweight-locked (inflated monitor pointer).
- Forwarding pointer during GC (object copied to new location).

**Field layout rules** (JVM reorders fields for alignment, does NOT follow declaration order):
1. `double`/`long` (8 bytes) — first, naturally aligned.
2. `int`/`float` (4 bytes).
3. `short`/`char` (2 bytes).
4. `byte`/`boolean` (1 byte).
5. Object references (4 bytes with compressed oops, 8 bytes without).

**Practical sizes (compressed oops):**
```java
new Object()         // 16 bytes
new Integer(42)      // 16 bytes (header 12 + int field 4)
new Long(42L)        // 24 bytes (header 12 + long 8 + padding 4)
new int[100]         // 16 (header+length) + 400 (data) = 416 bytes (rounded to 8)
```

**Arrays** have an extra 4-byte length field after the class pointer.

**Without compressed oops** (heap > ~32GB): class pointer is 8 bytes → header = 16 bytes. Every reference field is 8 bytes instead of 4. A `HashMap.Entry` grows significantly.

**Interview trap:** "What happens to object size when the heap exceeds 32GB?" — compressed oops are disabled (`-XX:-UseCompressedOops` or automatically disabled). Object references grow from 4 to 8 bytes. The memory usage of reference-heavy data structures (like collections) can increase 30–50%. This is why it's sometimes better to run two 28GB JVMs than one 56GB JVM — the two smaller ones keep compressed oops enabled.
**Tags:** object-layout, mark-word, compressed-oops, object-size, memory

---

## Q-JVM-022 [bloom: analyze] [level: master]
**Question:** Describe the complete lifecycle of a hot method from first invocation through full C2 optimization, including profiling feedback, inlining decisions, and how escape analysis interacts with the call graph.
**Model answer:** **Phase 1: Interpreter (level 0)**
Method runs in interpreter. HotSpot's template interpreter collects profiling data at every instrumented point:
- Invocation counter (incremented per call) and backedge counter (incremented per loop iteration).
- Type profile at virtual call sites: which concrete types were seen (mono/bi/megamorphic).
- Branch profile: which branches taken.
- Null check profile.

**Phase 2: C1 compilation (levels 1–3)**
When invocation + backedge count crosses threshold (~1500 for C1), C1 compiles the method:
- Level 1: fast compilation, minimal instrumentation.
- Level 2/3: C1 inserts profiling traps and counters (more overhead but feeds C2).

Level 3 is the typical path: C1-compiled with full profiling. C1 does simple optimizations (local value numbering, null-check elimination at known-non-null sites).

**Phase 3: C2 compilation (level 4)**
When C1 level 3 method accumulates enough profiling (~10,000 invocations total or after timeout), it's queued for C2 via the compilation queue. C2 runs in a background compiler thread.

C2's optimization pipeline (simplified):
1. **Parse bytecode → internal IR** (ideal graph / sea-of-nodes representation).
2. **Inlining**: C2 recursively inlines callees based on: call frequency (hot), small size (< `MaxInlineSize` = 35 bytes bytecode default), type profile (monomorphic virtual call → devirtualize → inline). Inline depth limited by `-XX:MaxInlineLevel` (default 9).
3. **Global value numbering, constant folding, dead code elimination.**
4. **Escape analysis on the inlined call graph**: now that callees are inlined, C2 can see the full object lifecycle across method boundaries. A `Point` created in method A, passed to inlined method B, used and discarded — EA can see it doesn't escape the combined inlined graph → scalar-replace it.
5. **Loop optimizations**: unrolling, vectorization (SIMD), range check elimination.
6. **Register allocation, instruction selection, code generation.**
7. **Install in code cache; replace level 3 entry.**

**Inlining and EA interaction:**
EA only works on the inlined subgraph. An object passed to a non-inlined callee is conservatively assumed to escape (can't see inside the callee). This is why failing to inline a method (due to size or call-site polymorphism) blocks EA for objects passing through that boundary.

```java
// C2 can scalar-replace 'p' if distance() is inlined:
double compute(int x1, int y1, int x2, int y2) {
    Point p = new Point(x2-x1, y2-y1);  // EA: NoEscape if distance() inlined
    return distance(p);
}
double distance(Point p) { return Math.sqrt(p.x*p.x + p.y*p.y); }
// With inlining: p → two int registers, zero heap allocation
```

**Deoptimization**:
C2 inserts `uncommon_trap` bytecodes at speculative paths (e.g., after devirtualized inline: "if the type assumption fails, deopt here"). The generated code jumps to the trap; the JVM reconstructs the interpreter frame and continues in the interpreter. The trap site is marked "too frequent" and the method is recompiled without that speculation.

**Interview trap:** "Does JIT compilation block the application thread?" — no, C2 compiles in background threads. The application continues running the C1-compiled version (or interpreter) while C2 compiles. Only the swap (installing the new compiled method in the code cache) requires a very brief synchronization. OSR (On-Stack Replacement) is the exception: for hot loops, C2 can replace an already-executing method mid-loop, which requires the running frame to be replaced.
**Tags:** jit, c2, inlining, escape-analysis, profiling, tiered-compilation, deoptimization, osr

---

## Q-JVM-023 [bloom: analyze] [level: master]
**Question:** A microservice processes millions of short-lived allocation-heavy requests per second. GC is 15% of CPU. Walk through a systematic strategy to reduce GC pressure without increasing heap size.
**Model answer:** **Step 1: Measure what you're actually allocating**

```bash
# async-profiler: allocation profiling (low overhead, samples alloc events)
./asprof -e alloc -d 30 -f /tmp/alloc.html <pid>
# Or JFR: Object Allocation in New TLAB + Object Allocation Outside TLAB events
jcmd <pid> JFR.start duration=30s settings=profile filename=/tmp/r.jfr
```

Find the top-N allocation sites by bytes. Don't guess — most engineers guess wrong.

**Step 2: Categorize allocations**

| Category | Strategy |
|----------|----------|
| Short-lived temporaries (request-scoped) | Let JIT escape-analysis handle (ensure callee is inlined) |
| Per-request small objects allocated in tight loops | Object pooling or value types (Valhalla preview) |
| Boxing of primitives (`Integer`, `Long` from generics) | Replace with primitive collections (Eclipse Collections, Koloboke, primitive streams) |
| Large char[]/byte[] for String operations | Reuse `StringBuilder`, `ByteBuffer` from pool |
| Intermediate collections in stream pipelines | Rewrite as loops; avoid `.collect()` when not needed |

**Step 3: Tune TLAB allocation**

TLABs (Thread-Local Allocation Buffers) make allocation near-free (bump pointer). When a TLAB fills, the thread must get a new one (slow path). If allocations are very large (outside TLAB), they go directly to Eden (even slower path, requires synchronization).

```bash
-XX:+PrintTLAB              # diagnostic: shows TLAB sizes and waste
-XX:TLABSize=<bytes>        # hint initial TLAB size
```

Objective: minimize TLAB refills. If objects are large, increase TLAB size or reduce allocation rate.

**Step 4: Reduce object graph depth for GC scanning**

Each live object the GC must scan costs time. Flatten data: prefer arrays of primitives over arrays of boxed values. Use `int[]` instead of `List<Integer>`.

**Step 5: Tune young gen sizing**

If objects are truly short-lived, they should all die in minor GC. If minor GC pause is high, young gen might be too small (frequent minor GCs) or too large (too many surviving objects to copy). Profile survival rates:
```bash
-Xlog:gc+age*=debug   # prints tenuring age distribution
```

If most objects die in S0→S1 copy, that's fine. If many survive to old gen, they either aren't short-lived or the tenuring threshold is too low.

**Step 6: Compiler-level: ensure inlining for EA**

Check whether hot methods exceed `MaxInlineSize` (35 bytes bytecode). Refactor or use `-XX:MaxInlineSize=<n>` carefully. Use JITWatch or `-XX:+PrintInlining` to see which callees aren't being inlined.

**Step 7: Consider Generational ZGC (Java 21)**

Generational ZGC has very efficient young-gen collection and much lower barrier overhead than non-generational ZGC. If allocation rate is the bottleneck, generational ZGC may reclaim young gen more efficiently than G1 while keeping pause targets.

**Interview trap:** "Should you use object pools aggressively?" — only for objects that are genuinely expensive to create (connection pool, large buffer pool) or have identity requirements. For small objects, pooling can make GC pressure worse (pooled objects survive minor GC → go to old gen → increase old gen pressure → trigger major/full GC more often). The JIT's escape analysis + stack allocation is a better "pool" for short-lived temporaries.
**Tags:** gc-pressure, allocation-profiling, tlab, object-pooling, escape-analysis, async-profiler, jfr

---

## Q-JVM-024 [bloom: analyze] [level: master]
**Question:** Explain what happens at the JVM level when you call `Thread.sleep(1)` inside a `synchronized` block, versus `Object.wait()`. How does this interact with monitor inflation, safe points, and GC?
**Model answer:** **`Thread.sleep(1)` inside `synchronized`:**

The thread holds the monitor (lock) and calls sleep. Sleep puts the thread in `TIMED_WAITING` state — it gives up CPU but retains the monitor. No other thread can enter the `synchronized` block while this thread sleeps. The OS parks the thread via `LockSupport.park` underneath.

JVM perspective: the monitor remains in whatever inflation state it was in. If no contention, the monitor may be a thin lock (lock bits in mark word) or biased lock. Sleep itself is a native call — the thread is "at a safe point" while in native code. GC can proceed.

**`Object.wait()` inside `synchronized`:**

`wait()` atomically: releases the monitor, records the thread on the object's wait set, and puts the thread in `WAITING` state.

Critically: the monitor must be **inflated** (become a heavyweight `ObjectMonitor`) before `wait()` can be called. `ObjectMonitor` has a proper wait set data structure. If the monitor was thin/biased, it's inflated at this point (mark word becomes pointer to `ObjectMonitor`). Inflation is a one-way transition — once inflated, never reverts to thin lock (in practice; biased lock can be revoked and re-biased).

The waiting thread releases CPU. When `notify()`/`notifyAll()` is called, the waiting thread moves from the wait set to the entry set (contending to reacquire the monitor). It's not immediately runnable — it must win the monitor contention race first.

**Safe points and GC:**
- A thread in `Object.wait()` / `Thread.sleep()` is blocked in native/OS code. The JVM considers it "at a safe point." GC can proceed without this thread reaching a poll explicitly.
- When GC occurs while a thread is in `wait()`, the GC may need to scan the thread's stack for live references (it's in `WAITING` but has a JVM stack). The stack is scanned using OopMaps recorded at the call site.

**Monitor inflation and GC:**
`ObjectMonitor` objects are themselves heap-allocated (or in a native monitor pool in newer JVMs). They hold references to the waiting thread objects. The GC must scan `ObjectMonitor` instances as part of root scanning.

**Biased locking (removed Java 21):** Prior to Java 21, if a monitor was biased to thread A and thread B wants to lock it, the JVM must revoke the bias. Revocation requires a safe point and pauses thread A. Revocations at scale (many threads competing for a biased lock) caused "biased lock revocation storms" that manifested as STW pauses in GC logs (even with no GC). Biased locking was deprecated Java 15, removed Java 21.

**Interview trap:** "Can GC run while a thread holds a monitor?" — yes. GC doesn't care about locks; it cares about reachability. The GC will stop the thread at a safe point (it parks in sleep/wait = safe point), scan its stack, and proceed. The monitor is held through the GC pause. Other threads waiting for the monitor continue to wait.
**Tags:** synchronized, monitor, object-wait, safe-point, biased-locking, monitor-inflation, gc

---

## Q-JVM-025 [bloom: analyze] [level: master]
**Question:** Design a strategy for running a latency-sensitive Java service (p999 < 2ms, 100k req/s) on the JVM. Address: GC choice, heap sizing, JIT warm-up, and operational readiness.
**Model answer:** This is an end-to-end JVM tuning design question. Requirements conflict: high throughput (100k req/s) + extreme low latency (p999 2ms) + JVM (which has GC pauses, JIT warm-up, etc.). Achievable with careful engineering.

**GC choice: Generational ZGC (Java 21)**
```bash
-XX:+UseZGC -XX:+ZGenerational
-XX:MaxGCPauseMillis=1    # soft target; ZGC typically achieves <1ms
-XX:GCPausIntervalMillis=50   # min interval between pauses
```
Why: ZGC's STW pauses are O(GC roots), typically 0.5–1ms regardless of heap size. G1 at this latency target is risky (p999 pauses can be 10–50ms). Shenandoah is an alternative.

**Heap sizing:**
```bash
-Xms8g -Xmx8g    # equal to prevent resize
```
Right-size heap for ~30–40% occupancy at peak (gives GC enough headroom to run concurrently without falling behind allocation rate). More headroom = fewer GC cycles = less CPU overhead. Generational ZGC benefits from a reasonably large young gen.

Monitor: `jstat -gcutil <pid> 1000` for live heap occupancy; trigger alert if old gen consistently > 60%.

**JIT warm-up:**
Cold JVM serves requests via interpreter + C1 — p99/p999 during warm-up will be poor. Strategy:
1. **Warm-up traffic**: before admitting production traffic, send a warm-up burst (replay production traffic pattern or synthetic) until JIT stabilizes. Monitor via JFR `Compilation` events; wait until compilation queue drains and method compilation counts plateau.
2. **AOT compilation** (Java 17+ CDS + AppCDS): class data sharing caches parsed class data; reduces startup and early GC pressure.
3. **JVM-level**: `-XX:+TieredCompilation` (default), `-XX:CompileThreshold=1000` (lower threshold to reach C2 faster at cost of more early compilation overhead).
4. **GraalVM native image** (alternative): compiles to native binary, eliminates JIT warm-up entirely. Trade-off: peak throughput slightly lower (AOT misses profile-guided optimizations), harder to profile, Class.forName / reflection needs config.

**Avoiding safe-point bias:**
```bash
-XX:+UseCountedLoopSafepoints    # safe-point polls in counted loops (Java 11+, small overhead)
-Xlog:safepoint:file=/var/log/sp.log:time,uptime   # monitor TTSP
```

**Allocation discipline:**
At 100k req/s the allocation rate matters. Profile with async-profiler regularly. Goals:
- Minimize boxed primitives (use primitive collections).
- Request-scoped objects that don't escape to thread pools → EA + stack allocation.
- Avoid creating large `byte[]` per request; use pooled `ByteBuf` (Netty).

**Operational readiness:**
```bash
# Always-on diagnostics (low overhead):
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps/
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=10,filesize=50m
-XX:+StartFlightRecording=disk=true,maxage=2h,maxsize=500m,settings=default,filename=/var/jfr/
```

**Pre-prod checklist:**
- Load test at 120% of expected peak for 30+ minutes; verify no GC degradation.
- Chaos: induce full GC (`jcmd <pid> GC.run`), verify p999 stays within SLA.
- Measure baseline allocation rate via JFR; set threshold alert.
- Deploy with `-XX:+PrintCommandLineFlags` logged to confirm JVM args as expected.

**Interview trap:** "Would you use real-time GC (Zing/Azul Falcon) for this?" — Azul Zing (C4 GC) is the gold standard for pauseless Java at this latency tier, used by HFT firms. It's expensive (commercial), but for truly hard SLA requirements it removes the GC variable entirely. For most companies, Generational ZGC on Java 21 is sufficient and free. The honest answer is: measure first; if ZGC meets the SLA, use it; if not, consider Zing or redesign around pre-allocation/native.
**Tags:** latency-slo, zgc, jit-warmup, gc-tuning, heap-sizing, production-readiness, master

---

## Q-JVM-026 [bloom: analyze] [level: master]
**Question:** How does the JVM handle class initialization in multithreaded scenarios? Can class initialization deadlock? Show a scenario.
**Model answer:** **JVM guarantee:** class initialization is thread-safe and runs at most once per ClassLoader. The JVM uses a per-class initialization lock (not a user-visible `synchronized` block, but a JVM-internal monitor). Only one thread runs `<clinit>`; other threads that trigger initialization of the same class block until it completes.

This is the basis for the **Initialization-on-Demand Holder (IODH) pattern**:
```java
public class Singleton {
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```
Thread-safe without `synchronized` because JVM guarantees `Holder.<clinit>` runs exactly once.

**Class initialization deadlock scenario:**

```java
class A {
    static final B b = new B();    // A.<clinit> creates B instance
    A() {}
}

class B {
    static final A a = new A();    // B.<clinit> creates A instance
    B() {}
}

// Thread 1: triggers A initialization (e.g., via Class.forName("A"))
//   → A's init lock acquired by Thread 1
//   → A.<clinit> tries to create new B()
//   → B not initialized → Thread 1 tries to acquire B's init lock
//   → Thread 1 blocks waiting for B's init lock

// Thread 2: triggers B initialization concurrently
//   → B's init lock acquired by Thread 2
//   → B.<clinit> tries to create new A()
//   → A not initialized → Thread 2 tries to acquire A's init lock
//   → Thread 2 blocks waiting for A's init lock

// DEADLOCK: Thread 1 holds A's lock waiting for B's; Thread 2 holds B's lock waiting for A's
```

The JVM detects class initialization cycles partially (if the same thread tries to re-initialize a class it's already initializing, it gets a partially-initialized class — fields may be at default zero values). But cross-thread cycles produce genuine deadlocks.

**JVM spec handling:** JVM 2.17.5 / JVMS 5.5: if class C's initialization is triggered by thread T, T acquires C's lock. If T already holds C's lock (recursive initialization by same thread), it proceeds with the partially-initialized class. Cross-thread cycle → deadlock (no resolution specified by the JVM spec).

**Detection and fix:**
- Stack traces show both threads in `Class.<clinit>` waiting.
- Fix: eliminate static field initialization cycles. Use lazy initialization (supplier, holder pattern) to break the cycle.

**Interview trap:** "Does Spring break class init cycles?" — Spring's `@Autowired` constructor injection doesn't involve class initialization locks. Spring bean cycles are detected at container startup. Class initialization cycles are JVM-level and happen before Spring is involved (during class loading). They're orthogonal problems.
**Tags:** class-initialization, clinit, deadlock, thread-safety, iodh, jvm-spec

---

