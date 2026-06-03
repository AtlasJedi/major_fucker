# Java Collections — question bank

> Java Collections Framework is a mandatory topic at every senior backend interview. Interviewers use it as a proxy for understanding of data structures, algorithmic complexity, memory model, concurrency, and API design. A 5-year engineer is expected to know not just what each class does but *why* the internals are designed that way, what breaks under load, and how to pick the right collection for a given production scenario. HashMap internals alone account for ~30% of collections questions at senior level.

## Scope

- equals/hashCode contract and its impact on hashed collections
- HashMap internal structure: Java 7 separate chaining vs Java 8 treeification
- HashMap: load factor, default capacity, resize/rehash, hash spread `h ^ (h>>>16)`, index formula
- HashMap: TREEIFY_THRESHOLD, MIN_TREEIFY_CAPACITY, UNTREEIFY_THRESHOLD constants and semantics
- HashMap: time complexity (average vs worst case), the mutable-key bug
- ArrayList vs LinkedList: array backing, 1.5x growth, cache locality, when LinkedList actually wins
- LinkedHashMap: insertion-order vs access-order mode, LRU cache pattern
- TreeMap: red-black tree, O(log n) navigation operations, use cases
- HashSet and TreeSet internals (backed by HashMap/TreeMap)
- ConcurrentHashMap: Java 7 segment locks vs Java 8 CAS + synchronized-bin approach, no nulls
- CopyOnWriteArrayList: copy-on-write semantics, read vs write cost
- Immutable collections: List.of / Set.of / Map.of (Java 9+) vs Collections.unmodifiable*
- Fail-fast vs fail-safe iterators: modCount, ConcurrentModificationException
- Comparable vs Comparator: natural order, external order, composition with Java 8 methods
- Queue/Deque family: ArrayDeque, PriorityQueue, LinkedList as Deque
- WeakHashMap and reference types in collections

---

## Q-JCOL-001 [bloom: recall] [level: junior]
**Question:** What are the core interfaces in the Java Collections Framework and what hierarchy do they form?

**Model answer:** The root interfaces split into two trees:

**Collection** branch:
- `Iterable` → `Collection` → `List` (ordered, duplicates allowed)
- `Collection` → `Set` (no duplicates)
- `Set` → `SortedSet` → `NavigableSet`
- `Collection` → `Queue` → `Deque`

**Map** branch (separate — Map does not extend Collection):
- `Map` → `SortedMap` → `NavigableMap`

Key implementations to know:

| Interface | Implementation | Notes |
|-----------|----------------|-------|
| List | ArrayList, LinkedList, Vector (legacy) | ArrayList is default |
| Set | HashSet, LinkedHashSet, TreeSet | TreeSet is NavigableSet |
| Map | HashMap, LinkedHashMap, TreeMap, Hashtable (legacy) | |
| Queue/Deque | ArrayDeque, PriorityQueue, LinkedList | ArrayDeque preferred over Stack |
| Concurrent | ConcurrentHashMap, CopyOnWriteArrayList, ArrayBlockingQueue | |

`Map` is not a `Collection` — a common gotcha. `Map.entrySet()` / `keySet()` return a `Set`, which is a Collection.

**Interview trap:** "Can you iterate a Map directly?" — No, Map itself is not Iterable. You iterate via `entrySet()`, `keySet()`, or `values()`. Since Java 8 you can also call `map.forEach((k, v) -> ...)`.

**Tags:** collections, interfaces, hierarchy, java

---

## Q-JCOL-002 [bloom: recall] [level: junior]
**Question:** What is the equals/hashCode contract in Java, and what breaks if you violate it?

**Model answer:** Five rules for `equals` (from the Javadoc):
1. **Reflexive:** `x.equals(x)` is `true`.
2. **Symmetric:** `x.equals(y)` ↔ `y.equals(x)`.
3. **Transitive:** `x.equals(y) && y.equals(z)` → `x.equals(z)`.
4. **Consistent:** repeated calls return the same result (no side-effects from comparison).
5. **Null-safe:** `x.equals(null)` returns `false`, never throws.

The **hashCode contract** adds:
- If `a.equals(b)` → then `a.hashCode() == b.hashCode()` (mandatory).
- If `a.hashCode() == b.hashCode()` → `a.equals(b)` may or may not be true (collisions are OK).

What breaks in practice:
- Override `equals` without `hashCode` → objects that are logically equal land in different buckets → `HashMap.get()` / `HashSet.contains()` return wrong results even though `equals` says true.
- Override `hashCode` without `equals` → identity equality used; objects collide in buckets, linked-list scan uses `equals` (identity) → logical duplicates allowed in `HashSet`.
- Violate symmetry → `TreeSet`/`TreeMap` can produce infinite loops or incorrect ordering.

Minimal correct pattern (Java 17+):
```java
@Override
public boolean equals(Object o) {
    if (!(o instanceof Product p)) return false;
    return Objects.equals(id, p.id);
}
@Override
public int hashCode() {
    return Objects.hash(id);
}
```

**Interview trap:** "What if I use `instanceof` instead of `getClass()` in equals?" — `instanceof` allows subclasses to be equal to the parent, which can violate symmetry if the subclass also overrides `equals`. `getClass()` enforces strict type equality. Neither is universally right; it depends on the class hierarchy design (value type vs entity type).

**Tags:** equals, hashcode, contract, collections

---

## Q-JCOL-003 [bloom: recall] [level: junior]
**Question:** What is the difference between ArrayList and LinkedList? When would you actually reach for LinkedList?

**Model answer:** Both implement `List`. Fundamental difference is the backing structure:

| | ArrayList | LinkedList |
|---|---|---|
| Backing | Object[] array | Doubly-linked nodes |
| Random access `get(i)` | O(1) | O(n) — traversal |
| Append at tail `add(e)` | O(1) amortized | O(1) |
| Insert/remove at index | O(n) — shift | O(n) to find + O(1) to splice |
| Insert/remove at head | O(n) | O(1) |
| Memory per element | ~4 bytes (reference) | ~48 bytes (node: prev, next, item + object header) |
| Cache locality | Excellent — contiguous array | Poor — nodes scattered in heap |

**Growth policy:** ArrayList grows by 50% (`newCapacity = oldCapacity + (oldCapacity >> 1)`). Initial capacity 10 if default constructor used. You can pre-size with `new ArrayList<>(expectedSize)` to avoid rehash.

**When LinkedList wins (rare):**
- Frequent insert/remove at both ends with a reference in hand (Deque usage via `addFirst`/`removeFirst`).
- Implementing a work-stealing deque where you hold an iterator/reference to a node.

**In practice:** `ArrayList` wins in 99% of cases due to cache locality. Modern CPUs prefetch contiguous memory; a scan of 10k-element ArrayList is typically 5–10x faster than the equivalent LinkedList even though both are O(n), because LinkedList pointer-chases across the heap.

**Interview trap:** "LinkedList insert in the middle is O(1)." — Technically true *once you have the node reference*. But finding position i is still O(n). Interviewers sometimes probe this distinction. Also note: `LinkedList` implements `Deque`, so it's the implementation behind `ArrayDeque` comparisons; for pure Queue/Deque usage, `ArrayDeque` outperforms `LinkedList` for the same cache-locality reason.

**Tags:** arraylist, linkedlist, performance, cache-locality, complexity

---

## Q-JCOL-004 [bloom: recall] [level: junior]
**Question:** What is the difference between HashSet, LinkedHashSet, and TreeSet?

**Model answer:** All three implement `Set` (no duplicates). They differ in ordering, performance, and backing structure:

| | HashSet | LinkedHashSet | TreeSet |
|---|---|---|---|
| Backed by | HashMap | LinkedHashMap | TreeMap (red-black tree) |
| Iteration order | Undefined | Insertion order | Sorted (natural or Comparator) |
| add/contains/remove | O(1) avg | O(1) avg | O(log n) |
| Null element | Allowed (1x) | Allowed (1x) | Allowed only if Comparator supports it |
| Memory overhead | Lowest | Medium (prev/next links) | Higher (tree nodes) |
| Implements NavigableSet | No | No | Yes |

`TreeSet` extras via `NavigableSet`: `floor(e)`, `ceiling(e)`, `headSet(e)`, `tailSet(e)`, `subSet(from, to)` — useful for range queries.

`HashSet` is just a `HashMap` where every value is the same sentinel `PRESENT` object. `add(e)` calls `map.put(e, PRESENT)`.

**Interview trap:** "TreeSet requires Comparable." — Not exactly. It requires either elements that implement `Comparable` *or* a `Comparator` passed to the constructor. If neither is provided and you insert a non-Comparable element, you get a `ClassCastException` at runtime (not compile time). This bites people with custom domain objects.

**Tags:** hashset, linkedhashset, treeset, ordering, complexity

---

## Q-JCOL-005 [bloom: recall] [level: junior]
**Question:** What is the difference between Comparable and Comparator?

**Model answer:** Both define ordering, but they live in different places:

**Comparable<T>** — *natural ordering*, implemented by the class itself:
```java
public class Product implements Comparable<Product> {
    @Override
    public int compareTo(Product other) {
        return this.price.compareTo(other.price);
    }
}
```
- Defines one canonical order for the class.
- Used by `Collections.sort(list)`, `Arrays.sort(arr)`, `TreeSet` / `TreeMap` without explicit comparator.
- Modifying the class is required.

**Comparator<T>** — *external/ad-hoc ordering*, separate object:
```java
Comparator<Product> byNameThenPrice = Comparator
    .comparing(Product::getName)
    .thenComparing(Product::getPrice);
```
- Multiple orderings possible, no modification to the class needed.
- Java 8 static factory methods: `Comparator.comparing()`, `.reversed()`, `.thenComparing()`, `.nullsFirst()`, `.nullsLast()`.

**Contract for both:** return negative if this < other, 0 if equal, positive if this > other. For `TreeMap`/`TreeSet`: the comparator must be consistent with `equals` — i.e., `compareTo(o) == 0` iff `equals(o)` is true — otherwise the map will behave oddly (it uses the comparator, not `equals`, for key equality).

**Interview trap:** "`Comparator.comparing(Product::getPrice).reversed()` — what's the return type?" — It's `Comparator<Product>`. But the type inference can fail with chained calls. If you write `Comparator.comparing(p -> p.getPrice()).reversed()`, the lambda's type is inferred as `Comparator<Object>` and `.reversed()` returns `Comparator<Object>`. Fix: explicit cast `(Product p)` on the lambda or use method reference.

**Tags:** comparable, comparator, sorting, java8

---

## Q-JCOL-006 [bloom: recall] [level: junior]
**Question:** What does List.of() return, and how is it different from Collections.unmodifiableList()?

**Model answer:** Both produce lists you cannot mutate, but they differ in important ways:

**`List.of(a, b, c)` (Java 9+):**
- Returns a truly immutable, structurally fixed list. Any structural mutation (`add`, `remove`, `set`) throws `UnsupportedOperationException`.
- Null elements are **not allowed** — throws `NullPointerException` at creation time.
- Backed by a compact, fixed-size internal array (not `ArrayList`).
- The implementation is optimized by element count: `List.of()` → empty singleton; `List.of(e)` → single-element; etc.
- Serialization-safe.

**`Collections.unmodifiableList(list)`:**
- Returns a **view** over the original mutable list. The view throws on mutations, but the underlying list can still be mutated by whoever holds a reference to it. This is a common bug — the caller thinks they have an immutable list, but the producer mutates the backing list.
- Nulls are allowed (delegates to the backing list).
- Iteration reflects any changes to the underlying list.

```java
List<String> mutable = new ArrayList<>(List.of("a", "b"));
List<String> view = Collections.unmodifiableList(mutable);
mutable.add("c");
System.out.println(view.size()); // 3 — surprise!

List<String> truly = List.of("a", "b");
// truly is truly immutable — no reference to mutate it through
```

Also available: `Collections.emptyList()` (since Java 2), `List.copyOf(collection)` (Java 10, makes a truly immutable deep copy of the collection).

**Interview trap:** "Is List.of thread-safe?" — Yes for reads, because it's immutable and the state never changes after publication. But you still need safe publication (e.g., via `final` field or `volatile`) to ensure the reference itself is visible to other threads before they read it.

**Tags:** list-of, unmodifiable, immutable, java9, collections

---

## Q-JCOL-007 [bloom: understand] [level: regular]
**Question:** Explain fail-fast vs fail-safe iterators. What is `modCount` and when does it throw `ConcurrentModificationException`?

**Model answer:** **Fail-fast iterators** (ArrayList, HashMap, HashSet, etc.) detect structural modification during iteration and throw `ConcurrentModificationException` immediately rather than silently producing wrong results.

**Mechanism — `modCount`:**
Every structural modification to an ArrayList or HashMap increments an internal `int modCount` field. When you call `list.iterator()`, the iterator records `expectedModCount = list.modCount`. On every `next()` or `remove()` call, it checks `modCount == expectedModCount`. If not, it throws.

```java
List<String> list = new ArrayList<>(List.of("a", "b", "c"));
for (String s : list) {           // uses iterator internally
    if (s.equals("b")) list.remove(s); // modCount++ → iterator sees mismatch
}
// throws ConcurrentModificationException on next iteration
```

Correct removal patterns:
```java
// 1. Iterator.remove() — updates expectedModCount
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("b")) it.remove();
}

// 2. removeIf (Java 8)
list.removeIf(s -> s.equals("b"));

// 3. Stream + collect (creates new list)
list = list.stream().filter(s -> !s.equals("b")).toList();
```

**Fail-safe iterators** (from `java.util.concurrent`):
- `ConcurrentHashMap`, `CopyOnWriteArrayList` — iterators operate on a snapshot or use a different mechanism. They do **not** throw `ConcurrentModificationException`.
- `CopyOnWriteArrayList`: iterator holds a reference to the array snapshot at creation time. Structural modifications create a new array. Iterator never sees new elements added after it was created.
- `ConcurrentHashMap`: weakly consistent — may or may not reflect modifications made after iterator creation.

**Important nuance:** `ConcurrentModificationException` is a **best-effort** detection, not a guarantee. The Javadoc explicitly says it should not be relied upon for program correctness — it's for catching bugs, not for synchronization. In a multithreaded context, the check is not atomic, so you can miss it.

**Interview trap:** "Can ConcurrentModificationException occur in a single-threaded program?" — Yes, absolutely. It's not about threads; it's about modifying the collection while iterating it. Single-thread code that adds to a list in a for-each loop demonstrates this every day.

**Tags:** fail-fast, fail-safe, modcount, concurrentmodificationexception, iterator

---

## Q-JCOL-008 [bloom: understand] [level: regular]
**Question:** How does HashMap store entries? Describe the bucket array, the linked list, and how `get()` finds a value.

**Model answer:** HashMap is backed by an array of buckets (`Node<K,V>[] table`). The default initial capacity is **16**; the default load factor is **0.75**.

**Storing an entry — `put(key, value)`:**
1. Compute `key.hashCode()`.
2. Apply hash spread: `hash = h ^ (h >>> 16)` — XOR the high 16 bits into the low 16 bits to reduce collisions when the table is small.
3. Compute bucket index: `index = (n - 1) & hash`, where `n` is current table length. This works correctly only when `n` is a power of two (and HashMap always keeps n as a power of two).
4. If the bucket is empty, insert a new `Node` there.
5. If the bucket is non-empty (collision), walk the chain comparing hash and key via `equals`. If an existing node's key matches, update the value. Otherwise append a new node.

**`get(key)`:**
1. Same hash computation → same bucket index.
2. Walk the chain; compare `hash` first (cheap int comparison), then `key.equals(node.key)` (potentially expensive).
3. Return the value if found, null otherwise.

**Complexity:**
- Average case: O(1) — hash distributes uniformly, chains are short.
- Worst case (Java 7): O(n) — all keys hash to the same bucket, single long linked list.
- Worst case (Java 8+): O(log n) — treeified bucket (see next question).

**Interview trap:** "Does HashMap allow null keys?" — Yes, exactly one null key is allowed. `null` key always maps to bucket 0 (special-cased in the code). null values are also allowed, any number of them. ConcurrentHashMap does NOT allow null keys or null values — it throws NPE. This difference exists because in a concurrent context you can't distinguish "key not present" from "key maps to null" without locking.

**Tags:** hashmap, buckets, hashing, put, get, collision

---

## Q-JCOL-009 [bloom: understand] [level: regular]
**Question:** When does HashMap resize, and what happens during a resize?

**Model answer:** **Resize trigger:** HashMap resizes when `size > capacity * loadFactor`. With default values (capacity=16, loadFactor=0.75), resize fires when the 13th entry is inserted (16 × 0.75 = 12 → on 13th entry, `size` would exceed threshold).

**What happens:**
1. New table is allocated with double the capacity (`newCapacity = oldCapacity << 1`).
2. All existing entries are rehashed: for each entry, re-apply `(newCapacity - 1) & hash` to find the new bucket index. Because capacity is always a power of two, this is efficient — the new bit in `(newCapacity - 1)` determines whether the entry stays in the same bucket or moves to `oldIndex + oldCapacity`. So Java 8 avoids re-computing the hash; it just tests one bit.
3. All chains are re-distributed into the new table.

**Cost:** O(n) for a single resize. Amortized across all inserts, the cost per insert is O(1).

**Why load factor 0.75?** It's a deliberate trade-off between time and space. Lower factor (e.g., 0.5) means fewer collisions but more memory waste and more frequent resizes. Higher factor (e.g., 0.9) means more collisions per bucket. 0.75 was chosen empirically as a good balance (Poisson distribution of bin lengths at 0.75 gives expected bin length ~0.5).

**Pre-sizing tip:** If you know the expected size, construct with `new HashMap<>(expectedSize / 0.75 + 1)` (or use Guava's `Maps.newHashMapWithExpectedSize(n)`) to avoid resizes entirely.

**Interview trap:** "Does resize happen when a bucket gets too long?" — No, that triggers *treeification* (Java 8+), not a resize. Resize is purely size-vs-capacity. However: if the table is small (capacity < MIN_TREEIFY_CAPACITY = 64), then even if a bucket hits the treeify threshold, the map resizes instead of treeifying. This is a subtle distinction.

**Tags:** hashmap, resize, rehash, load-factor, capacity

---

## Q-JCOL-010 [bloom: understand] [level: regular]
**Question:** What is LinkedHashMap and how would you use it to implement a simple LRU cache?

**Model answer:** `LinkedHashMap` extends `HashMap` and adds a doubly-linked list that runs through all entries in insertion order (default) or access order (if constructed with `accessOrder = true`).

- **Insertion order (default):** entries iterated in the order they were first put. Useful when you need a Map that also remembers insertion sequence (config parsing, ordered JSON output).
- **Access order (`new LinkedHashMap<>(16, 0.75f, true)`):** entries are reordered to the tail on every `get()` or `put()`. Head = least recently accessed.

**LRU cache using `removeEldestEntry`:**
```java
public class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public LruCache(int maxSize) {
        super(maxSize, 0.75f, true);   // accessOrder = true
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
```
When `size() > maxSize`, the eldest entry (head of the linked list — least recently accessed) is automatically removed after each `put`. This gives a correct LRU cache in ~10 lines.

**Complexity:** Same O(1) average for get/put as HashMap (backed by the same hash table). The linked-list maintenance adds constant overhead per operation.

**Production note:** `LinkedHashMap` is not thread-safe. For a concurrent LRU cache, use Caffeine's `LoadingCache` or wrap with `Collections.synchronizedMap()` (but this coarsens locking and kills concurrent reads).

**Interview trap:** "Is the access-order mode maintained during iteration?" — Iterating a LinkedHashMap in access-order mode does NOT reorder entries (that would corrupt the iterator). `get()` and `put()` reorder; iteration is read-only and reflects the current order at iterator creation. Also: do not call `get()` inside a `forEach` on the same access-order LinkedHashMap — that's a structural modification and will throw `ConcurrentModificationException`.

**Tags:** linkedhashmap, lru, access-order, insertion-order, cache

---

## Q-JCOL-011 [bloom: understand] [level: regular]
**Question:** How does TreeMap work internally, and when should you prefer it over HashMap?

**Model answer:** `TreeMap` is backed by a **red-black tree** — a self-balancing binary search tree. The tree invariant guarantees that the height is at most 2*log(n), so all core operations are O(log n):

| Operation | Complexity |
|-----------|------------|
| get / put / remove | O(log n) |
| containsKey | O(log n) |
| firstKey / lastKey | O(log n) |
| headMap / tailMap / subMap | O(log n) + O(k) iteration |
| size | O(1) |

**Internal mechanics:** Red-black tree guarantees ≤ 2 rotations per insert/delete and no more than 3 for deletion. The Java implementation uses `RED/BLACK` node color flags and performs left/right rotations after insert/delete to restore balance.

**When to prefer TreeMap:**
1. You need entries in **sorted key order** (reporting, pagination, prefix queries).
2. You need **range operations**: `headMap(toKey)`, `tailMap(fromKey)`, `subMap(from, to)`, `floorKey(k)`, `ceilingKey(k)`, `higherKey(k)`.
3. You need `firstKey()` / `lastKey()` efficiently.
4. You use keys that are `Comparable` (or have a `Comparator`) and ordering is meaningful.

**When NOT to use TreeMap:**
- Pure lookup-by-exact-key with no ordering requirement → HashMap is O(1) vs O(log n).
- High-throughput concurrent writes → `ConcurrentSkipListMap` is the concurrent sorted map (TreeMap is not thread-safe).

**Interview trap:** "TreeMap allows null keys?" — Only if a null-tolerant `Comparator` is provided. The natural ordering (Comparable) will throw NPE on `null.compareTo(other)`. HashMap allows null keys (special-cased to bucket 0); TreeMap generally does not.

**Tags:** treemap, red-black-tree, sorted-map, navigablemap, complexity

---

## Q-JCOL-012 [bloom: understand] [level: regular]
**Question:** What is the difference between Queue and Deque? When would you use ArrayDeque over LinkedList?

**Model answer:** **Queue** (interface): FIFO ordered collection. Key methods: `offer(e)` / `poll()` / `peek()`. `add`/`remove`/`element` are the throwing variants.

**Deque** (interface, extends Queue): double-ended queue. Operations at both head and tail: `offerFirst`/`offerLast`, `pollFirst`/`pollLast`, `peekFirst`/`peekLast`. Can be used as FIFO queue *or* LIFO stack.

**ArrayDeque vs LinkedList as Deque:**

| | ArrayDeque | LinkedList |
|---|---|---|
| Backing | Circular resizable array | Doubly-linked nodes |
| Head/tail ops | O(1) amortized | O(1) |
| Random access | Not supported | O(n) |
| Memory per element | ~8 bytes (ref + array overhead) | ~48 bytes (node object) |
| Cache locality | Excellent | Poor |
| Null elements | Not allowed | Allowed |
| Implements List | No | Yes |

`ArrayDeque` should be the **default choice** for both stacks and queues. It outperforms `LinkedList` for queue/stack operations in practice due to cache locality, and it outperforms `Stack` (legacy, synchronized) and `LinkedList`. The only reason to choose `LinkedList` as a Deque is if you also need List access by index.

**PriorityQueue:** min-heap backed by array. `poll()` returns the minimum (natural or Comparator order). O(log n) insert/remove, O(1) peek. Not a sorted list — iterating does not give sorted order.

**Interview trap:** "Stack class vs ArrayDeque for stack?" — Use `ArrayDeque`. `Stack` extends `Vector` which is synchronized (legacy). The Javadoc for `Stack` itself says to prefer `Deque` implementations. `ArrayDeque.push(e)` / `.pop()` gives you LIFO semantics without unnecessary locking.

**Tags:** queue, deque, arraydeque, linkedlist, priorityqueue, stack

---

## Q-JCOL-013 [bloom: apply] [level: senior]
**Question:** Walk me through the full internal lifecycle of a `HashMap.put(key, value)` call in Java 8+, including the hash spread formula, index calculation, and what happens when a bucket already has entries.

**Model answer:** Full call trace for `map.put(key, value)`:

**Step 1 — Table initialization (lazy):**
The table array is null until first `put`. `putVal` calls `resize()` which allocates the initial array of capacity 16.

**Step 2 — Hash computation:**
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
The `^ (h >>> 16)` spreads higher bits into lower bits. Why? The bucket index is `(n-1) & hash`. For a small table (n=16), only the bottom 4 bits matter. Without spreading, two keys with the same low bits but different high bits collide, even if their full hash is different. Spreading halves the collision probability for typical hash distributions.

**Step 3 — Index calculation:**
```java
int index = (n - 1) & hash;
```
`n` is always a power of two. `(n-1)` is all 1s in binary (e.g., 15 = `0b00001111` for n=16). ANDing with hash is a fast modulo operation: equivalent to `hash % n` but branchless.

**Step 4 — Bucket state decision:**
```java
Node<K,V> p = tab[index];
if (p == null) {
    tab[index] = newNode(hash, key, value, null); // empty bucket
} else {
    // collision path
}
```

**Step 5 — Collision handling:**
```java
if (p.hash == hash && (p.key == key || key.equals(p.key))) {
    // exact match on first node — update value
} else if (p instanceof TreeNode) {
    // bucket is a red-black tree — O(log n) insert
    e = ((TreeNode)p).putTreeVal(this, tab, hash, key, value);
} else {
    // bucket is a linked list — walk and append
    for (int binCount = 0; ; ++binCount) {
        if ((e = p.next) == null) {
            p.next = newNode(hash, key, value, null);
            if (binCount >= TREEIFY_THRESHOLD - 1) // 7 → 8th node
                treeifyBin(tab, hash);
            break;
        }
        if (e.hash == hash && (e.key == key || key.equals(e.key))) break;
        p = e;
    }
}
```

**Step 6 — Treeification decision:**
`treeifyBin` checks: if `tab.length < MIN_TREEIFY_CAPACITY (64)`, resize instead of treeify. This avoids creating tree nodes in a tiny table where resize would spread the entries anyway.

If `tab.length >= 64`, convert the linked-list bucket into a red-black tree. The tree uses the same `TreeNode` objects (which are larger — they carry left, right, parent, red/black flag).

**Step 7 — Post-put:**
Increment `modCount` (for fail-fast iterators), increment `size`. If `size > threshold` (capacity × loadFactor), call `resize()`.

**Interview trap:** "Why does Java use `TREEIFY_THRESHOLD = 8` specifically?" — Statistically, for a uniformly distributed hash function, the probability of a bucket having ≥ 8 entries with a load factor of 0.75 is about 0.00006 (6 in 100,000). So in practice, treeification should almost never occur unless `hashCode()` is bad or adversarial. The threshold balances the cost of tree node overhead vs. the performance gain of O(log n) traversal.

**Tags:** hashmap, put, hash-spread, treeify, treeify-threshold, min-treeify-capacity, internals

---

## Q-JCOL-014 [bloom: apply] [level: senior]
**Question:** What are `TREEIFY_THRESHOLD`, `MIN_TREEIFY_CAPACITY`, and `UNTREEIFY_THRESHOLD` in HashMap? Explain each constant's role and why all three are needed.

**Model answer:** Three constants govern the transition between linked-list and tree-based bucket storage:

**`TREEIFY_THRESHOLD = 8`:**
When adding to a bucket that already has 8 entries, `treeifyBin()` is called. This is the number of entries *after* which the bucket becomes a candidate for conversion to a red-black tree. The value 8 is chosen because Poisson distribution at load factor 0.75 predicts the probability of a bin having 8 entries is ~0.00000006 — so this should virtually never happen with a good hash function.

**`MIN_TREEIFY_CAPACITY = 64`:**
Treeification only happens if the table's capacity is at least 64. If `TREEIFY_THRESHOLD` is hit but `table.length < 64`, `treeifyBin` resizes the table instead of converting to a tree. Rationale: small tables have few buckets, and a long chain is probably caused by too many entries for the table size rather than by a bad hash function. Doubling the table (resize) will redistribute the chain elements better. Treeifying in a tiny table would waste memory on tree overhead when a resize would fix the problem.

**`UNTREEIFY_THRESHOLD = 6`:**
When entries are removed from a tree-bucket and it shrinks to 6 entries, it's converted back to a linked list. This hysteresis gap (8 to treeify, 6 to untreeify) prevents oscillation: without the gap, a map hovering at exactly 7 entries with alternating insert/delete would constantly convert back and forth.

**Summary table:**
| Constant | Value | Direction | Action |
|----------|-------|-----------|--------|
| TREEIFY_THRESHOLD | 8 | On add | Convert linked list → red-black tree (if table ≥ 64) |
| MIN_TREEIFY_CAPACITY | 64 | On add | Resize instead of treeify if table too small |
| UNTREEIFY_THRESHOLD | 6 | On remove | Convert red-black tree → linked list |

**Interview trap:** "What's the worst-case complexity of HashMap.get() in Java 8+" — O(log n) per bucket after treeification, but the per-bucket traversal is O(log k) where k is the number of entries in that specific bucket, and k is bounded by TREEIFY_THRESHOLD + growth. Overall worst case across the whole map is O(log n) where n is total entries if all collide into one bucket.

**Tags:** hashmap, treeify-threshold, min-treeify-capacity, untreeify-threshold, red-black-tree, internals

---

## Q-JCOL-015 [bloom: apply] [level: senior]
**Question:** Explain the mutable-key bug in HashMap. What causes it, how does it manifest, and how do you prevent it?

**Model answer:** **The bug:** If you use a mutable object as a HashMap key and then mutate a field that participates in `hashCode()` after inserting it into the map, the key becomes permanently unfindable.

**Why it happens:**
1. On `put(key, value)`: `hash = key.hashCode()` is computed → entry goes into bucket `(n-1) & hash`.
2. You mutate the key object → `key.hashCode()` now returns a different value.
3. On `get(key)`: the new hash maps to a different bucket. The entry is not there → returns `null`.
4. The entry still exists in the map at the old bucket — it's orphaned. It leaks memory, cannot be removed or found. Even `containsKey(key)` returns `false`.

**Demonstration:**
```java
class MutableKey {
    String value;
    MutableKey(String v) { this.value = v; }
    public int hashCode() { return value.hashCode(); }
    public boolean equals(Object o) {
        return o instanceof MutableKey mk && value.equals(mk.value);
    }
}

Map<MutableKey, String> map = new HashMap<>();
MutableKey k = new MutableKey("alpha");
map.put(k, "payload");
System.out.println(map.get(k)); // "payload"

k.value = "beta";                // MUTATION after insert
System.out.println(map.get(k)); // null — bucket mismatch
System.out.println(map.size()); // 1 — entry is orphaned, leaks
```

**Prevention:**
1. **Use immutable keys.** `String`, `Integer`, `UUID`, `LocalDate`, records with only immutable fields — all safe.
2. **Only include immutable fields in hashCode/equals.** If you must use a mutable class, only use fields that never change after construction (e.g., a database `id` field).
3. **Defensive design:** if the class is mutable, document "do not use as Map/Set key" or make `hashCode` based on object identity (`System.identityHashCode`) and use identity equality.

**Interview trap:** "Does this affect TreeMap too?" — Yes. TreeMap uses `compareTo` (or Comparator) for ordering, not `hashCode`, but if you mutate a key field that affects comparison, the tree's invariants are violated. The entry may end up in the wrong subtree, and `get`/`remove` will use comparison to navigate and miss it entirely.

**Tags:** hashmap, mutable-key, hashcode, immutability, bug

---

## Q-JCOL-016 [bloom: apply] [level: senior]
**Question:** How does ConcurrentHashMap differ from HashMap and Hashtable in terms of internal structure and concurrency strategy? Compare Java 7 vs Java 8 implementations.

**Model answer:** **Hashtable (legacy):** All methods are `synchronized` on the entire object. Only one thread can read or write at a time. Effectively useless for concurrent applications — massive contention.

**`Collections.synchronizedMap(new HashMap<>())`:** Same problem as Hashtable — single mutex over the entire map. Compound operations (check-then-act) still require external synchronization.

**ConcurrentHashMap Java 7 — Segment-based locking:**
- Backed by an array of `Segment` objects (default 16 segments).
- Each Segment is a `ReentrantLock` guarding its own small hash table.
- Put operations lock only the relevant segment → up to 16 concurrent writers.
- Get operations are lock-free (volatile reads of table and node values).
- Concurrency level limited to number of segments (16 by default).

**ConcurrentHashMap Java 8 — CAS + synchronized bins:**
- Segments removed. A single flat `Node<K,V>[] table` (same as HashMap).
- **No lock for empty buckets:** `CAS (compare-and-swap)` to atomically set `table[index]` from null to the new node. Lock-free for non-colliding inserts.
- **Synchronized on bin head for collision handling:** `synchronized (firstNode)` — locks only the specific bucket's first node, not the whole map.
- Red-black tree bins (same TREEIFY_THRESHOLD=8 / MIN_TREEIFY_CAPACITY=64 logic as HashMap).
- Size maintained via `LongAdder`-style mechanism (`CounterCell[]` array) to reduce contention on `size()`.
- Full concurrency level = number of bins (up to n threads write concurrently to n different bins).

**No null keys or null values** in ConcurrentHashMap:
- HashMap: null key allowed (bucket 0), null values allowed.
- ConcurrentHashMap: both throw NPE.
- Reason: in a concurrent context, `map.get(key) == null` is ambiguous — was the key absent, or did it map to null? In a single-threaded context, you can call `containsKey` to disambiguate, but in a concurrent context that creates a TOCTOU race. Disallowing null eliminates the ambiguity.

**Interview trap:** "Is `ConcurrentHashMap.get()` guaranteed to see the latest `put()`?" — Weakly consistent. `get()` uses volatile reads, so it sees writes that happened-before it in Java Memory Model terms. But there's no locking across get/put, so a `get()` can observe a partially updated state. For atomic compound operations (getOrInsert, computeIfAbsent), use the provided atomic methods: `putIfAbsent`, `compute`, `computeIfAbsent`, `merge`.

**Tags:** concurrenthashmap, thread-safety, cas, segments, java7-vs-java8, concurrency

---

## Q-JCOL-017 [bloom: apply] [level: senior]
**Question:** Describe `CopyOnWriteArrayList`. When is it appropriate and when is it a performance disaster?

**Model answer:** `CopyOnWriteArrayList` is a thread-safe `List` implementation where every **structural modification** (add, set, remove) creates a brand-new copy of the underlying array, applies the change, and then atomically swaps the reference with `volatile`.

**Read path:** Readers hold a reference to the current array snapshot. Reads are **lock-free and very fast** — just array access with a volatile read.

**Write path:** Each write:
1. Acquires a `ReentrantLock`.
2. Copies the entire backing array.
3. Applies the modification to the copy.
4. Sets `array = newArray` (volatile write → visible to all readers immediately).
5. Releases the lock.

**Iterator semantics:** The iterator captures a reference to the array at creation time. It never throws `ConcurrentModificationException`. It also never reflects modifications made after the iterator was created — the iterator is a snapshot. `iterator.remove()` is not supported.

**When it's appropriate:**
- Read-heavy, write-rare scenarios: event listener lists, static configuration that changes rarely, observer patterns where many threads read the list but writes are infrequent (e.g., once per deploy).
- When you need snapshot semantics for iteration without external locking.

**When it's a disaster:**
- Write-heavy workloads: every write copies the entire array → O(n) per write, massive GC pressure.
- Large lists: copying a 100k-element array on every add is catastrophic.
- High-frequency small writes: use `ConcurrentLinkedQueue`, `LinkedBlockingQueue`, or `ConcurrentHashMap` depending on the use case.

**Interview trap:** "Is CopyOnWriteArrayList consistent?" — From a reader's perspective, no — readers see a snapshot that may be stale. Two reads on the same thread may see different snapshots if a write happened between them. This is "eventual consistency" within the JVM. For use cases requiring strong consistency across reads, use a `ReadWriteLock` with a plain `ArrayList` instead.

**Tags:** copyonwritearraylist, thread-safety, snapshot, read-heavy, write-cost

---

## Q-JCOL-018 [bloom: apply] [level: senior]
**Question:** You have a HashMap that's causing performance problems in production under high read/write concurrency. Walk through your diagnosis and migration options.

**Model answer:** **Diagnosis first:**

1. **Confirm the symptom:** is it lock contention, CPU spike, or memory pressure?
2. **HashMap is not thread-safe.** Under concurrent access without external synchronization: data corruption, lost updates, infinite loops (Java 6 and earlier — the hash table resize can create a cycle in the linked list; Java 8+ is safer but still no guarantee of visibility or atomicity).
3. **Thread dump:** if you see threads spinning in `HashMap.get()` or `put()`, it's concurrent access without sync. In Java 6/7, two threads resizing simultaneously can create a cycle, causing `get()` to loop forever (CPU 100%).

**Migration options — in order of preference:**

**Option 1: ConcurrentHashMap (most cases)**
- Drop-in replacement for read/write concurrency.
- O(1) avg lock-free reads; per-bin locking for writes.
- Use atomic methods for compound ops: `computeIfAbsent`, `merge`, `compute`.
- Caveat: no nulls, weakly consistent iteration.

**Option 2: ReadWriteLock + HashMap (many reads, few writes)**
```java
ReadWriteLock rwl = new ReentrantReadWriteLock();
// read: rwl.readLock().lock() / unlock()
// write: rwl.writeLock().lock() / unlock()
```
- Multiple concurrent readers; exclusive writer.
- More control than ConcurrentHashMap but more boilerplate.

**Option 3: Collections.synchronizedMap(map) (avoid)**
- Single mutex. Defeats concurrency for high-throughput use cases. Compound ops still need external sync.

**Option 4: Caffeine / Guava Cache (if it's a cache)**
- If the map is used as a cache, replace with a proper caching library. Size-bounded, eviction-aware, concurrent.

**Option 5: Immutable snapshot pattern**
- If writes are rare and can be batched (config reload, A/B test overrides), compute a new immutable HashMap and swap the reference atomically via a `volatile` field or `AtomicReference`. Zero-overhead reads.

**Interview trap:** "Isn't `Collections.synchronizedMap` enough?" — For simple single-operation thread safety, yes. But `map.containsKey(k)` followed by `map.get(k)` is not atomic even with a synchronized map. Between the two calls, another thread can remove the key. `ConcurrentHashMap.computeIfAbsent` solves this atomically.

**Tags:** hashmap, concurrency, migration, concurrenthashmap, readwritelock, diagnosis

---

## Q-JCOL-019 [bloom: analyze] [level: master]
**Question:** A colleague reports that after switching from Java 7 to Java 11, their HashMap-heavy service's memory usage jumped 20%. They suspect treeification. How do you investigate and potentially address this?

**Model answer:** **Is treeification actually the cause?** Let's reason through the mechanics.

Tree nodes (`TreeNode`) inherit from `Node` but add four extra references: `parent`, `left`, `right`, `prev`. On a 64-bit JVM with compressed OOPs, a `Node` is ~32 bytes; a `TreeNode` is ~56 bytes. If a large fraction of buckets get treeified, you could see a measurable memory increase.

**When treeification happens at scale:**
- Poor `hashCode()` implementations (constant, partial fields, XOR of two equal fields).
- Adversarial hash inputs (e.g., some String hash attacks).
- Very high load: if you create a `new HashMap()` and insert 100M entries, you'll have many collisions in later buckets due to hash distribution imperfection.

**Investigation steps:**
1. **Heap dump + histogram:** compare `java.util.HashMap$TreeNode` count vs `java.util.HashMap$Node` count. If TreeNode count is significant, confirm the hypothesis.
2. **JFR/async-profiler:** check allocation profile during map put-heavy phases.
3. **Check hashCode quality:** `IntStream.range(0,n).mapToObj(i -> key(i)).collect(groupingBy(k -> k.hashCode() & 15, counting()))` — if any bucket class has dramatically more than n/16 entries, the hash function is bad.
4. **Review custom hashCode implementations:** are they using all relevant fields? Are they mixing bits properly?

**Remediation options:**
1. **Fix hashCode:** use `Objects.hash(field1, field2, ...)` or `31*h + field` idiom; avoid obvious clustering.
2. **Pre-size the map:** `new HashMap<>(size / 0.75 + 1)`. With a properly sized map, the load never gets high enough to generate long chains.
3. **Use a different map:** for known-bad hash distributions, consider `IdentityHashMap` (reference equality, no treeify), or a custom hash table implementation with open addressing (rare in Java, but Agrona's `Object2ObjectHashMap` avoids GC pressure).
4. **Reduce key diversity:** if many logically equivalent keys are being used as map keys (e.g., String paths with common prefixes), consider normalizing or interning.
5. **Evaluate alternative structures:** if the 20% memory increase is acceptable and the alternative is O(n) lookup, treeification is actually correct behavior — it's trading memory for O(log n) worst case.

**Interview trap:** "Does enabling `+XX:+UseCompressedOops` affect TreeNode size?" — Yes. With compressed OOPs (default on heaps < 32GB), references are 4 bytes. Without compression (large heap), references are 8 bytes. A TreeNode with 4 extra references goes from ~56 bytes to ~88 bytes without compressed OOPs. If the migration also involved moving to a > 32GB heap, this could independently explain the memory jump.

**Tags:** hashmap, treenode, memory, performance, hashcode-quality, heap-analysis, java11

---

## Q-JCOL-020 [bloom: analyze] [level: master]
**Question:** Describe all the ways that HashMap can exhibit non-deterministic or incorrect behavior in a multithreaded environment, including the Java 6/7 resize infinite loop. What does the Java Memory Model guarantee (or not) for HashMap?

**Model answer:** HashMap provides **no thread-safety guarantees**. The Java Memory Model does not establish a happens-before relationship between operations on a non-synchronized HashMap from different threads. Concrete failure modes:

**1. Stale reads (all Java versions):**
Without volatile/synchronized, writes on one thread may not be visible to another. A thread can see an old version of the table reference, old entry values, or null for keys that were inserted.

**2. Lost updates:**
Two threads insert different keys that hash to the same bucket. Thread A reads the bucket, Thread B reads the bucket, both compute the new chain, both write back. One write is lost.

**3. Inconsistent state during iteration:**
Reads of the table array are non-atomic at the memory level. A thread can observe a partially resized map — part of the new table, part of the old — and read corrupt data.

**4. Infinite loop during resize (Java 6/7 — the legendary bug):**
- `resize()` calls `transfer()`, which moves entries from old table to new table.
- `transfer()` reverses the linked list order in each bucket (classic bug — it re-inserts at the head).
- If two threads call `resize()` concurrently: Thread A starts, Thread B starts. Thread A completes and reverses the chain. Thread B resumes with `next` pointer pointing to a node that now points back — a cycle.
- `get()` on the affected bucket loops forever: `e = e.next` cycles through the same two nodes indefinitely. CPU hits 100%.
- **Java 8 fix:** `resize()` preserves list order (splits into lo/hi chains). This eliminates the cycle. Java 8 still has data races and wrong results, but not infinite loops.

**5. Concurrent treeification (Java 8+):**
If two threads both trigger `treeifyBin()` on the same bucket simultaneously (both observe `binCount >= 7`), both try to restructure the bucket. Result: corrupt tree state, possible NPE or ClassCastException.

**JMM summary for HashMap:**
- No guarantees whatsoever for cross-thread access.
- Even a single `put` followed by a `get` from a different thread is not guaranteed to observe the put, unless there's a happens-before edge (volatile, lock, thread start/join, etc.).

**Interview trap:** "Is HashMap safe if one thread writes and one thread reads, as long as they don't write at the same time?" — No. Without a happens-before edge, the reader may see stale table reference, stale entries, or partially initialized Node objects (hashCode/key/value written in arbitrary order from the reader's perspective). Use ConcurrentHashMap or establish a happens-before via volatile/lock.

**Tags:** hashmap, concurrency, infinite-loop, resize, jmm, memory-model, thread-safety

---

## Q-JCOL-021 [bloom: analyze] [level: master]
**Question:** Design a thread-safe, bounded LRU cache that handles concurrent reads and writes efficiently. What are the trade-offs between different implementation strategies?

**Model answer:** **Requirements:** O(1) get, O(1) put, bounded size (evict LRU on overflow), thread-safe, high read concurrency.

**Approach 1: Synchronized LinkedHashMap (naive)**
```java
Map<K,V> cache = Collections.synchronizedMap(
    new LinkedHashMap<>(capacity, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry e) {
            return size() > capacity;
        }
    }
);
```
- Single mutex on every operation. Get/put are serialized.
- Fine for low-throughput use cases (< a few thousand ops/sec).
- `containsKey` + `get` is not atomic (two synchronized calls, race between them).

**Approach 2: ReadWriteLock + LinkedHashMap**
```java
ReadWriteLock rwl = new ReentrantReadWriteLock();
LinkedHashMap<K,V> map = new LinkedHashMap<>(capacity, 0.75f, true);

V get(K key) {
    rwl.writeLock().lock(); // must write-lock because access-order reorders
    try { return map.get(key); }
    finally { rwl.writeLock().unlock(); }
}
```
Note: access-order mode reorders on `get()` — that's a structural modification — so you cannot use `readLock()` for get. Effectively still serializes all operations. Marginally better than synchronized for multiple concurrent readers *without* access-order.

**Approach 3: ConcurrentHashMap + ConcurrentLinkedDeque (approximate LRU)**
```java
ConcurrentHashMap<K,V> map = new ConcurrentHashMap<>(capacity);
ConcurrentLinkedDeque<K> order = new ConcurrentLinkedDeque<>();
```
- `get` is lock-free on the map; `offer(key)` to deque records access.
- `put` checks size, polls from deque to evict.
- Problem: deque is not a doubly-linked map — removing from middle is O(n). Eviction is approximate.
- Suitable for "mostly LRU" semantics where occasional non-LRU eviction is acceptable.

**Approach 4: Caffeine (production-grade)**
```java
Cache<K,V> cache = Caffeine.newBuilder()
    .maximumSize(capacity)
    .build();
```
Caffeine uses **Window TinyLFU** algorithm with a striped write buffer (`StripedBuffer<K>`) and a dedicated drain thread. Reads are recorded in a lockless ring buffer per CPU stripe; the drain thread periodically flushes to the frequency sketch. Eviction is done asynchronously. Measured throughput: ~10x over `ConcurrentHashMap` wrapped in sync LRU, with better hit ratio than pure LRU.

**Trade-off matrix:**

| Strategy | Throughput | Consistency | Complexity | Recommendation |
|----------|-----------|-------------|-----------|----------------|
| Sync LinkedHashMap | Low | Exact LRU | Low | Prototyping |
| ReadWriteLock + LHM | Low | Exact LRU | Medium | Rarely worth it |
| CHM + approximate | High | Approximate | Medium | Acceptable for caches |
| Caffeine | Very high | Near-optimal | Low (library) | Production |

**Interview trap:** "Does Caffeine guarantee LRU eviction?" — No, it uses Window TinyLFU, which is better than LRU for realistic workloads (LRU is famously bad for scan-heavy access patterns — a single full scan evicts all hot entries). TinyLFU maintains a frequency sketch and admits new entries only if they are more popular than what they'd evict.

**Tags:** lru-cache, concurrenthashmap, linkedhashmap, caffeine, thread-safety, design

---

## Q-JCOL-022 [bloom: analyze] [level: master]
**Question:** You're reviewing a codebase and find `new HashMap<>()` used as a field in a singleton Spring bean, populated at startup and read by many threads. No synchronization. Is this safe? What are all the conditions under which it could be, and what would you change?

**Model answer:** **The short answer:** It depends on publication and post-construction mutability. The answer has three cases.

**Case 1 — Populated in constructor/`@PostConstruct`, never modified after:**
```java
@Component
class Config {
    private final Map<String, String> props;
    public Config(@Value("...") ...) {
        this.props = new HashMap<>();
        props.put("k", "v");
        // no more writes after this
    }
}
```
If the `HashMap` reference is declared `final`, the JMM **final field guarantee** ensures all writes performed before the constructor completes are visible to any thread that obtains the object reference. This is safe — reads after publication are guaranteed to see all entries inserted during construction.

If the reference is not `final` but is published via Spring's `ApplicationContext` (which uses synchronization internally to make beans available to other threads), the happens-before chain through Spring's startup synchronization is sufficient. Still safe in practice, but relying on container internals is fragile.

**Case 2 — Populated in `@PostConstruct` (after constructor):**
`@PostConstruct` is called after the constructor. Spring calls it while holding no particular lock visible to application code. The bean is then published to the context. Technically the JMM guarantee requires a happens-before between the writes and any read. Spring's `AnnotationConfigApplicationContext.refresh()` uses internal synchronization that establishes this, but it's fragile to rely on — better to make the field `volatile` or switch to an immutable map.

**Case 3 — Modified after publication (some thread adds entries later):**
Completely unsafe. Concurrent reads and writes to HashMap cause data races, potentially: stale reads, lost updates, corrupt bucket chains, exceptions.

**What to do:**
```java
// Best option: make it truly immutable
private final Map<String, String> props = Map.copyOf(buildMap());

// If immutable is not possible: ConcurrentHashMap
private final ConcurrentHashMap<String, String> props = new ConcurrentHashMap<>();

// If reads dominate writes: volatile reference swap
private volatile Map<String, String> props;
// writer: props = Map.copyOf(newMap); // atomic reference swap
```

**Interview trap:** "Is a `Collections.unmodifiableMap(new HashMap<>(...))` safe for concurrent reads?" — The unmodifiable wrapper itself only prevents structural modifications through the wrapper. If the backing HashMap is not modified after the wrapper is created, and the wrapper is properly published (via final field or synchronization), then concurrent reads are safe — HashMap reads from multiple threads are safe when no writes occur concurrently. The issue is publication and post-publication mutation.

**Tags:** hashmap, spring, thread-safety, jmm, final-field, publication, singleton

---

## Q-JCOL-023 [bloom: analyze] [level: master]
**Question:** Walk through the performance characteristics of `ConcurrentHashMap.computeIfAbsent()` under high concurrency. What can go wrong and how does Caffeine's approach differ?

**Model answer:** **What `computeIfAbsent(key, mappingFunction)` does:**
```java
map.computeIfAbsent(key, k -> expensiveCompute(k));
```
1. Look up `key` in the map. If present, return the value.
2. If absent: for Java 8 CHM, lock the specific bin (`synchronized(firstNode)` or CAS if empty bucket).
3. While holding the bin lock, check again (double-checked locking pattern).
4. If still absent, call `mappingFunction.apply(key)`.
5. Insert result, release lock.

**Problem — contended key:**
If multiple threads call `computeIfAbsent(sameKey, ...)` concurrently:
- Thread A acquires the bin lock, calls `mappingFunction.apply(key)` (possibly slow — DB call, HTTP request, heavy computation).
- Threads B, C, D... all want the same key. They queue on the same bin's synchronized monitor.
- The bin lock is per-key cluster (per-bucket), so all threads waiting for the same key are blocked.
- **Thread A's work is duplicated if the function is slow** — wait, no: the second-check inside the lock prevents duplicate insertion. But all waiting threads queue up and are released only after Thread A finishes. If `mappingFunction` takes 500ms, all threads on that key wait 500ms.
- If `mappingFunction` calls `computeIfAbsent` on the same map (recursive or nested), Java 8 CHM can **deadlock** — same thread re-entrant on the same bin's synchronized block. Java 9+ added a check that throws `IllegalStateException` instead of deadlocking.

**Caffeine's approach — LoadingCache:**
```java
LoadingCache<K,V> cache = Caffeine.newBuilder()
    .build(key -> expensiveCompute(key));
V val = cache.get(key);
```
Caffeine de-duplicates concurrent loads for the same key:
- First thread starts loading → result stored as a `CompletableFuture` in the cache.
- Subsequent threads asking for the same key get the *same* `CompletableFuture` → they all block on it together.
- Only **one** computation happens; all waiters receive the same result.
- This is the "request coalescing" or "thundering herd prevention" pattern.

**Comparison:**

| | ConcurrentHashMap.computeIfAbsent | Caffeine LoadingCache |
|---|---|---|
| Concurrent load of same key | Serialized on bin lock; one thread loads | One thread loads, others await same future |
| Duplicate computation | Prevented by double-check | Prevented by future coalescing |
| Deadlock risk | Yes (Java 8), IllegalStateException (Java 9+) | No |
| Async loading | Manual (return a Future) | Built-in (`buildAsync`) |
| Bounded size | No (unbounded) | Yes |

**Interview trap:** "Is `computeIfAbsent` with a fast function safe?" — Yes, fast non-recursive functions are fine. The performance concern only arises with slow mappingFunctions and high key-contention. For cache warm-up with cheap computations (e.g., constructing a `List.of()`), CHM is perfectly appropriate.

**Tags:** concurrenthashmap, computeifabsent, thundering-herd, caffeine, loadingcache, concurrency

---

## Q-JCOL-024 [bloom: apply] [level: senior]
**Question:** What is WeakHashMap and in what scenario is it a better fit than a regular HashMap?

**Model answer:** `WeakHashMap` is a `Map` implementation where keys are held via **weak references** (`WeakReference<K>`). When the key object has no strong references elsewhere in the application and the GC runs, the key is eligible for collection. After GC, the corresponding entry is removed from the map (via a `ReferenceQueue` mechanism polled by the map's internal operations).

**Use cases where WeakHashMap fits:**
1. **Metadata/attachment maps:** storing auxiliary data associated with objects you don't own, without preventing those objects from being garbage collected. Example: attaching debugging metadata to `Class` objects, or tracking which instances of a type have been created.
2. **Canonicalization tables:** a `WeakHashMap<SomeKey, SomeKey>` as an intern pool — if no external reference to the canonical instance exists, it can be collected and regenerated later.
3. **Cache where key lifecycle drives entry lifecycle:** not a general-purpose cache (use Caffeine for that), but specifically when "if the key is gone, the value is irrelevant."

**What WeakHashMap is NOT:**
- A general-purpose thread-safe cache (it's not thread-safe — use `ConcurrentHashMap` with `WeakReference` values explicitly, or `Caffeine.weakKeys()`).
- A reliable cache (entries disappear unpredictably at GC time, even with sufficient memory).
- A map with predictable iteration size (size can decrease between calls).

**Gotcha:** The key is weakly referenced, but the **value is strongly referenced** (through the Entry). If the value holds a strong reference back to the key, the key will never be collected. Classic mistake:
```java
WeakHashMap<Widget, List<Widget>> map = new WeakHashMap<>();
List<Widget> listeners = new ArrayList<>();
listeners.add(widget); // value strongly references key
map.put(widget, listeners); // widget never collected!
```

**Interview trap:** "Is WeakHashMap the same as using `WeakReference` values in a regular HashMap?" — No. Weak keys vs weak values are completely different semantics. `WeakHashMap` uses weak keys — the entry is removed when the key is collected. Weak values (which you'd build manually with `HashMap<K, WeakReference<V>>`) means the entry stays in the map (key is strongly held) but the value may be collected; you'd get `null` from `ref.get()` when you dereference the value.

**Tags:** weakhashmap, weak-reference, gc, memory, cache

---

## Q-JCOL-025 [bloom: apply] [level: senior]
**Question:** You need to track word frequencies across a large document set (multi-threaded pipeline). Which collection(s) do you use, and what are the atomic primitives that make it correct?

**Model answer:** **Goal:** increment a counter per word across many threads, then query frequencies.

**Naive broken approach:**
```java
Map<String, Integer> freq = new HashMap<>();
freq.put(word, freq.getOrDefault(word, 0) + 1); // NOT atomic — read-modify-write race
```

**Correct approaches in order of preference:**

**Option 1: `ConcurrentHashMap.merge()`**
```java
ConcurrentHashMap<String, Long> freq = new ConcurrentHashMap<>();
freq.merge(word, 1L, Long::sum); // atomic: get-or-default + update in one CAS
```
`merge(key, value, remappingFunction)`: if key absent, puts `value`; if present, atomically replaces with `remappingFunction.apply(existing, value)`. One atomic bin-level operation.

**Option 2: `ConcurrentHashMap.compute()`**
```java
freq.compute(word, (k, v) -> v == null ? 1L : v + 1L);
```
`compute()` holds the bin lock for the duration of the function — ensures atomicity. Same effective behavior as `merge` here.

**Option 3: `LongAdder` values (high contention)**
```java
ConcurrentHashMap<String, LongAdder> freq = new ConcurrentHashMap<>();
freq.computeIfAbsent(word, k -> new LongAdder()).increment();
```
`LongAdder` maintains a cell per CPU stripe under contention, drastically reducing CAS contention. Better throughput when thousands of threads increment the same word concurrently. Slightly more complex (read with `longValue()`), but significantly faster for hot keys.

**Option 4: Parallel stream + `groupingByConcurrent`**
```java
Map<String, Long> freq = words.parallelStream()
    .collect(Collectors.groupingByConcurrent(Function.identity(), Collectors.counting()));
```
Uses `ConcurrentHashMap` internally, merges partial counts from each thread's segment. Good for batch processing of an in-memory collection.

**Which to use when:**
- Few keys, moderate concurrency → `merge()` or `compute()`.
- Many threads hammering the same hot keys → `LongAdder` values.
- Single-pass batch computation → `groupingByConcurrent`.
- Low concurrency, high simplicity requirement → simple `HashMap` with external locking or sequential stream.

**Interview trap:** "`putIfAbsent` + `get` is atomic, right? So I can do freq.putIfAbsent(word, new AtomicLong(0)); freq.get(word).incrementAndGet()?" — The sequence is **not** atomic as a whole. Between `putIfAbsent` and `get`, another thread could do the same and create a different `AtomicLong`. But `get` returns the value that won the `putIfAbsent` race, so both threads get the same `AtomicLong` and `incrementAndGet()` is safe. The pattern works correctly here — but it creates a new `AtomicLong` on every call even when the key exists. Use `computeIfAbsent` to avoid that.

**Tags:** concurrenthashmap, merge, compute, longadder, word-frequency, concurrency, atomic

---

## Q-JCOL-026 [bloom: understand] [level: regular]
**Question:** What guarantees does `Collections.unmodifiableMap()` give, and what can still go wrong with it?

**Model answer:** `Collections.unmodifiableMap(map)` returns a `Map` view that delegates all read operations to the backing map and throws `UnsupportedOperationException` for all mutation operations (`put`, `remove`, `clear`, `putAll`).

**What it does NOT guarantee:**

1. **The backing map is still mutable.** Anyone holding a reference to the original map can still modify it. The "unmodifiable" wrapper is only a barrier on the wrapper reference, not on the data.
   ```java
   Map<String, List<String>> original = new HashMap<>();
   Map<String, List<String>> view = Collections.unmodifiableMap(original);
   original.put("surprise", List.of("gotcha")); // view now contains this key
   ```

2. **Values are NOT deep-copied or protected.** If a value is a mutable object (like a `List<String>`), you can mutate that value object through the wrapper.
   ```java
   Map<String, List<String>> view = Collections.unmodifiableMap(original);
   view.get("key").add("mutated!"); // works! UnsupportedOperationException only on map-structural ops
   ```

3. **Not thread-safe.** The unmodifiable wrapper adds no synchronization. Concurrent reads are safe only if the backing map is not mutated concurrently.

**When to use it:**
- API boundary: you want to return a Map from a method and prevent callers from modifying your internal state — but you still hold the original reference and may update it yourself.
- Defensive API design when the caller is trusted not to store the backing reference.

**Better alternatives for true immutability:**
- `Map.copyOf(map)` — creates a truly immutable deep copy (values still mutable if they're mutable types, but map structure is frozen and no backing reference is retained).
- `ImmutableMap` from Guava — similar semantics, null-disallowing.

**Interview trap:** "Does `Map.copyOf` protect against mutable values?" — No. `Map.copyOf` makes the map structure immutable (no key addition/removal), but the value objects are the same references. If a value is a `List`, that List can still be mutated. For truly deep immutability you'd need immutable value types throughout.

**Tags:** unmodifiable-map, immutability, defensive-copy, map-copyof, thread-safety

---

## Q-JCOL-027 [bloom: understand] [level: regular]
**Question:** What is `ArrayDeque` and why does the Java documentation recommend it over `Stack` and `LinkedList` for stack/queue use cases?

**Model answer:** `ArrayDeque` is a resizable-array implementation of `Deque` (double-ended queue). It maintains a circular buffer with `head` and `tail` indices.

**Why it beats `Stack`:**
- `Stack` extends `Vector`, which synchronizes every method. Acquiring a mutex on every `push`/`pop` in single-threaded code is pure overhead.
- `ArrayDeque` has zero synchronization overhead.
- `Stack` is a legacy class; the Javadoc since Java 1.6 says "A more complete and consistent set of LIFO operations is provided by the `Deque` interface and its implementations, which should be used in preference to this class."

**Why it beats `LinkedList` for Queue/Deque use:**
- `LinkedList` allocates a `Node` object per element (heap allocation + GC pressure).
- `ArrayDeque` stores elements in a contiguous array — cache-friendly traversal, no per-element allocation after initial capacity.
- `ArrayDeque.addFirst`/`addLast`/`pollFirst`/`pollLast` are all O(1) amortized (doubles capacity when full).
- In microbenchmarks, `ArrayDeque` is consistently 2–4x faster than `LinkedList` for queue operations.

**Growth:** When the buffer is full, `ArrayDeque` doubles its capacity and copies elements to a new array, preserving circular ordering. Starting capacity is 16 by default.

**What `ArrayDeque` cannot do that `LinkedList` can:**
- `ArrayDeque` does not implement `List` — no index-based access.
- `ArrayDeque` does not allow `null` elements (throws NPE on `push(null)`).

```java
Deque<String> stack = new ArrayDeque<>();
stack.push("a"); stack.push("b");
System.out.println(stack.pop()); // "b" — LIFO

Queue<String> queue = new ArrayDeque<>();
queue.offer("a"); queue.offer("b");
System.out.println(queue.poll()); // "a" — FIFO
```

**Interview trap:** "Is ArrayDeque thread-safe?" — No. Use `LinkedBlockingDeque` or `ConcurrentLinkedDeque` for concurrent deque usage. `LinkedBlockingDeque` is bounded-optional and supports blocking `take`/`put` operations — useful for work queues.

**Tags:** arraydeque, deque, queue, stack, linkedlist, performance

---

## Q-JCOL-028 [bloom: recall] [level: junior]
**Question:** What is the difference between `HashMap`, `Hashtable`, and `ConcurrentHashMap`?

**Model answer:** Three Map implementations with very different concurrency semantics and nullability:

| | HashMap | Hashtable | ConcurrentHashMap |
|---|---|---|---|
| Thread-safe | No | Yes (coarse lock) | Yes (fine-grained) |
| Null key | 1 allowed | No | No |
| Null values | Allowed | No | No |
| Synchronization | None | `synchronized` every method | CAS + per-bin lock |
| Performance (concurrent) | Unsafe | Poor (single lock) | High |
| Iterator | Fail-fast | Fail-fast | Weakly consistent |
| Legacy | No (Java 2+) | Yes (Java 1) | No (Java 5+) |

**When to use each:**
- `HashMap`: single-threaded code, or explicitly externally synchronized code. Default choice.
- `Hashtable`: never in new code. It's an evolutionary dead end.
- `ConcurrentHashMap`: any concurrent access. Drop-in for HashMap in multi-threaded contexts (with the null caveat).

The null restriction on ConcurrentHashMap is intentional: in a concurrent map, `map.get(key) == null` is ambiguous — you cannot distinguish "key absent" from "key maps to null" without additional locking. ConcurrentHashMap eliminates the ambiguity by forbidding null.

**Interview trap:** "Can I use `Collections.synchronizedMap(new HashMap<>())` instead of ConcurrentHashMap?" — For single-operation thread safety, technically yes. But `synchronizedMap` uses a single coarse lock, meaning all reads and all writes are serialized. ConcurrentHashMap allows concurrent reads (lock-free) and concurrent writes to different bins. For any significant concurrency, `synchronizedMap` is a performance bottleneck. Also, compound operations (`containsKey` + `get`) are not atomic even with `synchronizedMap`.

**Tags:** hashmap, hashtable, concurrenthashmap, thread-safety, null-keys

---

## Q-JCOL-029 [bloom: recall] [level: junior]
**Question:** What is the difference between `poll()` and `remove()` on a Queue?

**Model answer:** Both retrieve and remove the head element of a Queue. The difference is in behavior when the queue is empty:

| Method | Empty queue behavior | Return type |
|--------|---------------------|-------------|
| `poll()` | Returns `null` | `E` (nullable) |
| `remove()` | Throws `NoSuchElementException` | `E` |
| `peek()` | Returns `null` | `E` (nullable) |
| `element()` | Throws `NoSuchElementException` | `E` |
| `offer(e)` | Returns `false` (capacity-bounded) | `boolean` |
| `add(e)` | Throws `IllegalStateException` (capacity-bounded) | `boolean` |

The `Queue` interface defines two method families for each operation:
- **Throws exception:** `add`, `remove`, `element` — fail loudly when capacity is full or queue is empty.
- **Returns special value:** `offer`, `poll`, `peek` — return false/null instead.

**Prefer the null-returning variants** (`offer`/`poll`/`peek`) in production code. Exceptions are expensive and catching `NoSuchElementException` as control flow is an anti-pattern.

**Interview trap:** "What does `LinkedList.poll()` return on an empty list?" — `null`. But `LinkedList.remove()` on an empty list throws `NoSuchElementException` (because `remove()` calls `removeFirst()` which checks and throws). This is a polymorphism gotcha — same class, different behavior depending on which interface method you call.

**Tags:** queue, poll, remove, peek, element, offer, add

---

## Q-JCOL-030 [bloom: recall] [level: junior]
**Question:** What is `PriorityQueue` and how does it maintain order?

**Model answer:** `PriorityQueue<E>` is a **min-heap** backed by an array. The element with the smallest ordering value (natural or Comparator) is always at the head.

**Internal structure:** A binary heap stored in an array where for any element at index `i`:
- Left child is at `2*i + 1`
- Right child is at `2*i + 2`
- Parent is at `(i - 1) / 2`
- Heap invariant: `parent <= children`

**Operations:**
- `offer(e)` / `add(e)`: insert at end of array, then sift-up. O(log n).
- `poll()`: removes head (index 0), moves last element to index 0, sifts down. O(log n).
- `peek()`: returns element at index 0. O(1).
- `remove(o)`: linear scan to find element, then sift-up or sift-down. O(n).
- `contains(o)`: linear scan. O(n).

**Key gotcha — iteration does NOT produce sorted order:**
```java
PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 3, 1, 4, 2));
for (int x : pq) System.out.print(x + " "); // NOT 1 2 3 4 5 — heap order, not sorted
// Only poll() produces sorted order: while(!pq.isEmpty()) System.out.print(pq.poll() + " ");
```

**Not thread-safe.** Use `PriorityBlockingQueue` for concurrent scenarios.

**Interview trap:** "PriorityQueue is a sorted collection." — Wrong framing. It's a heap — it only guarantees the minimum is always accessible in O(1). It does not maintain full sorted order. Extracting all elements in sorted order is O(n log n) via repeated `poll()`.

**Tags:** priorityqueue, heap, min-heap, complexity, sorting

---

## Q-JCOL-031 [bloom: understand] [level: regular]
**Question:** How does `HashSet` work internally? What is the relationship between `HashSet.add(e)` and `HashMap.put(k, v)`?

**Model answer:** `HashSet<E>` is implemented as a `HashMap<E, Object>` where the value is always the same sentinel object:

```java
private transient HashMap<E,Object> map;
private static final Object PRESENT = new Object();

public boolean add(E e) {
    return map.put(e, PRESENT) == null;
}

public boolean contains(Object o) {
    return map.containsKey(o);
}

public boolean remove(Object o) {
    return map.remove(o) == PRESENT;
}
```

**All of HashMap's internals apply directly to HashSet:**
- Hash spread: `h ^ (h >>> 16)`.
- Bucket index: `(n-1) & hash`.
- Collision handling: linked list → treeify at 8 (if table ≥ 64).
- Load factor 0.75, default capacity 16.
- Resize doubles capacity.
- `add(e)` returns `true` if the element was not already present (`put` returns null for new keys), `false` if it was (put returns the old value, which is `PRESENT`).

**Why a separate HashSet class?** Purely for API clarity — Set semantics (no duplicates, no values, contains/add/remove centered on elements) vs Map semantics.

**Interview trap:** "What's the memory cost of storing 1M elements in a HashSet vs a HashMap where all values are the same constant?" — Identical. HashSet *is* a HashMap with a shared PRESENT sentinel. There's no extra memory vs using `map.put(e, PRESENT)` yourself.

**Tags:** hashset, hashmap, internals, set, backed-by

---

## Q-JCOL-032 [bloom: understand] [level: regular]
**Question:** Explain the difference between `Collections.sort()`, `List.sort()`, and `Arrays.sort()`. Which sorting algorithm does each use and why?

**Model answer:** All three ultimately sort, but they differ in what they sort, their algorithms, and their history.

**`Arrays.sort(int[])` (primitive arrays):**
- Uses **dual-pivot quicksort** (Yaroslavskiy-Bentley-Bloch, Java 7+).
- O(n log n) average, O(n²) worst case (but engineered to avoid degenerate cases).
- In-place, not stable.
- For primitive arrays, quicksort beats mergesort because no object allocation, excellent cache behavior.

**`Arrays.sort(Object[])` (object arrays):**
- Uses **TimSort** (hybrid merge sort + insertion sort).
- O(n log n) worst case, O(n) best case (already sorted).
- Stable — equal elements maintain their relative order.
- Object arrays require stable sort because comparisons may be arbitrary (user-defined `Comparator`).

**`Collections.sort(List<T>)` (Java 7 and earlier):**
- Dumped list to array, called `Arrays.sort(Object[])` (TimSort), copied back.
- O(n log n), stable.
- Java 8: delegated to `list.sort(null)`.

**`List.sort(Comparator)` (Java 8+):**
- Default method on `List`, calls `Arrays.sort` on the list's array (for ArrayList: direct access to the backing array).
- TimSort, stable, O(n log n).
- Better than `Collections.sort` for ArrayList because no defensive copy — sorts the backing array directly.

**TimSort rationale:**
- Real-world data is often partially sorted. TimSort detects "runs" (already sorted subsequences) and merges them. On fully sorted data, O(n). On random data, O(n log n).
- Stable sort is necessary for correct behavior with multiple comparators (sort by name, then by age — stability preserves the name order within same-age groups).

**Interview trap:** "Is `Collections.sort` stable?" — Yes. Both `Collections.sort` and `List.sort` use TimSort which is stable. "Is `Arrays.sort(int[])` stable?" — For primitives, dual-pivot quicksort is used which is NOT stable (but stability is meaningless for primitives since there's no identity to preserve).

**Tags:** sorting, timsort, quicksort, collections-sort, arrays-sort, stable-sort