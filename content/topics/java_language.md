# Java Language (8–21) — question bank

> This bank covers Java language features from Java 8 through Java 21, with emphasis on the evolution of the language and the depth a senior backend engineer is expected to command. Interview panels at senior level routinely probe how well you understand *why* features exist, what the JVM actually does at runtime, and where the sharp edges are in production. Definitions alone fail the bar here — you need internals, tradeoffs, and war stories.

## Scope

- Java 8→21 feature timeline: what landed when and why
- var: local variable type inference, scope rules, what it is and is not
- Text blocks: multiline strings, incidental vs significant whitespace
- Switch expressions: arrow syntax, yield, exhaustiveness
- Pattern matching for instanceof: binding variable, scope rules
- Pattern matching for switch: guards, type patterns, null handling (Java 21)
- Records: components, compact constructor, restrictions, when NOT to use
- Sealed classes and interfaces: permits, subtype constraints, exhaustive switch
- Optional: creation, chaining API, correct vs incorrect usage patterns
- Stream API: intermediate vs terminal ops, laziness, stateful ops, collectors, parallel pitfalls
- Lambdas and functional interfaces: effectively final, closure semantics, SAM types
- Method references: four kinds, when they differ from equivalent lambdas
- Generics: type erasure, wildcards, PECS, bounded type parameters
- Autoboxing: performance pitfalls, identity equality of wrappers
- String: immutability, pool, interning, `+` in loops, `String.format` vs `formatted()`
- Checked vs unchecked exceptions: design philosophy, lambda incompatibility
- try-with-resources: AutoCloseable, suppressed exceptions, multi-resource ordering

---

## Q-JLNG-001 [bloom: recall] [level: junior]

**Question:** What is `var` in Java (introduced in Java 10) and what are the rules for where it can be used?

**Model answer:** `var` is local variable type inference. The compiler infers the static type from the initializer expression — it is NOT a dynamic type. `var` is syntactic sugar; the bytecode is identical to an explicit type declaration.

**Where `var` is allowed:**
- Local variables in method/block bodies — must have an initializer: `var list = new ArrayList<String>();`
- for-each loop variables: `for (var item : collection)`
- Traditional for-loop init: `for (var i = 0; i < n; i++)`
- try-with-resources: `try (var in = Files.newInputStream(path))`

**Where `var` is NOT allowed:**
- Method parameters
- Constructor parameters
- Method return types
- Fields (instance or static)
- Catch parameters (before Java 10; still not allowed)
- Lambda parameters (except Java 11 allows `var` in lambda parameter list for annotation use: `(var x, var y) -> x + y`)

**Key gotcha:** `var x = null;` does not compile — the compiler cannot infer a type from `null`.

**Interview trap:** "Does `var` make Java dynamically typed?" — No. The type is inferred at compile time and is fixed. `var list = new ArrayList<String>(); list = new LinkedList<>();` compiles fine because the inferred type is `ArrayList<String>` and a `LinkedList` is assignable — but `var x = 1; x = "hello";` fails to compile. Completely static.

**Tags:** var, java10, type-inference, local-variables

---

## Q-JLNG-002 [bloom: recall] [level: junior]

**Question:** What are text blocks in Java (Java 15, GA) and how does the compiler handle incidental whitespace?

**Model answer:** Text blocks are multiline string literals delimited by `"""`. They make JSON, HTML, SQL, and similar payloads readable without escape sequences.

```java
String json = """
        {
            "name": "Alice",
            "age": 30
        }
        """;
```

**Incidental vs significant whitespace:**
- The position of the closing `"""` on its own line sets the *re-indentation baseline* — the leftmost column among the content lines and the closing delimiter.
- The compiler strips that many spaces from the left of every line (incidental whitespace).
- Trailing whitespace on each content line is stripped unless explicitly escaped with `\s` (trailing space escape, Java 15+).
- `\` at end of a content line is a line continuation — suppresses the newline.

```java
// closing """ at col 8 → 8 spaces stripped from left
String sql = """
        SELECT *
        FROM orders
        WHERE id = ?
        """;
// result: "SELECT *\nFROM orders\nWHERE id = ?\n"
```

If the closing `"""` is on the same line as content, no trailing newline is added.

**Interview trap:** "Does a text block compile differently from a regular String?" — No, at runtime it is just a `String`. The reindentation is a compile-time transformation. Second trap: forgetting that the string *always ends with `\n`* when the closing `"""` is on its own line — can break assertions comparing against a string without trailing newline.

**Tags:** text-blocks, java15, strings, whitespace

---

## Q-JLNG-003 [bloom: recall] [level: junior]

**Question:** What is the difference between a switch expression (Java 14, GA) and a traditional switch statement? What is `yield`?

**Model answer:** Switch expressions (stable Java 14) are an expression form of switch — they produce a value. Two syntactic forms:

**Arrow form (preferred):**
```java
int numLetters = switch (day) {
    case MONDAY, FRIDAY, SUNDAY -> 6;
    case TUESDAY                -> 7;
    case THURSDAY, SATURDAY     -> 8;
    case WEDNESDAY              -> 9;
};
```
No fall-through. Each arm is a single expression, block, or `throw`.

**Traditional colon form (less common):**
```java
int numLetters = switch (day) {
    case MONDAY, FRIDAY, SUNDAY: yield 6;
    case TUESDAY: yield 7;
    default: yield 9;
};
```
`yield` exits the switch expression and provides its value. It is only valid inside a switch expression, not a switch statement.

**Key differences from statement:**
- Produces a value, so usable in assignments, return statements, arguments.
- Compiler enforces *exhaustiveness* — every possible value must be covered (or a `default` must be present). Missing cases = compile error, not silent fall-through.
- Arrow form eliminates fall-through entirely.

**Interview trap:** "`yield` vs `return`" — `yield` is only for switch expressions and provides the expression value; `return` exits the enclosing method. You cannot `return` from inside a switch expression to provide the expression value.

**Tags:** switch-expression, yield, java14, exhaustiveness

---

## Q-JLNG-004 [bloom: recall] [level: junior]

**Question:** What is pattern matching for `instanceof` (Java 16, GA) and how does the binding variable scope work?

**Model answer:** Pattern matching for `instanceof` removes the redundant cast after a type check:

```java
// Before Java 16
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}

// Java 16+
if (obj instanceof String s) {
    System.out.println(s.length());
}
```

The binding variable `s` is in scope where the pattern is definitely matched — the compiler performs *flow analysis*:

```java
// s is in scope in the if-true branch
if (obj instanceof String s) {
    System.out.println(s.toUpperCase());
}

// s is in scope in the rest of the block when the condition is negated with !
if (!(obj instanceof String s)) {
    return;
}
System.out.println(s.length()); // valid

// s is in scope for the && remainder (short-circuit means s is bound)
if (obj instanceof String s && s.length() > 5) { ... }

// s is NOT in scope for || (if first operand is false, s may not be bound)
if (obj instanceof String s || s.length() > 5) { } // compile error
```

**Interview trap:** Can you use a pattern binding in a ternary? Yes: `int len = (obj instanceof String s) ? s.length() : 0;`. Second trap: the binding variable shadows any outer variable with the same name — be careful in loops.

**Tags:** pattern-matching, instanceof, java16, flow-analysis

---

## Q-JLNG-005 [bloom: recall] [level: junior]

**Question:** What is a record in Java (GA Java 16)? What does the compiler auto-generate, and what are records' restrictions?

**Model answer:** A record is a concise, immutable data carrier. The declaration:
```java
public record Point(int x, int y) { }
```
Auto-generates:
- A *canonical constructor* matching the component list.
- Private final fields `x` and `y`.
- Public accessor methods `x()` and `y()` (not `getX()`).
- `equals()`, `hashCode()`, `toString()` based on all components.

**Customization — compact constructor:**
```java
public record Range(int lo, int hi) {
    public Range {  // no parameter list — they are implicit
        if (lo > hi) throw new IllegalArgumentException("lo > hi");
        // assignments to this.lo/this.hi happen automatically after this block
    }
}
```

**What records CAN do:**
- Implement interfaces.
- Add static fields/methods.
- Add additional instance methods.
- Define additional constructors (must delegate to canonical with `this(...)`).

**What records CANNOT do:**
- Extend any class (implicitly extend `java.lang.Record`).
- Declare additional instance fields (only components).
- Be abstract.
- Have non-final component fields.

**Interview trap:** "Record vs Lombok `@Value`?" — Both generate immutable classes. Record is a JDK primitive; `@Value` is annotation processing. Record accessor naming convention differs (`x()` vs `getX()`). Second trap: a record component that is a mutable type (e.g., `List<String>`) does NOT give deep immutability — the list can be mutated by external callers. Use `List.copyOf()` in the compact constructor.

**Tags:** records, java16, immutability, compact-constructor

---

## Q-JLNG-006 [bloom: recall] [level: junior]

**Question:** What is autoboxing in Java and what are the equality/performance pitfalls?

**Model answer:** Autoboxing is the compiler's automatic conversion between primitives and their wrapper types (`int` ↔ `Integer`, `long` ↔ `Long`, etc.), introduced in Java 5.

```java
List<Integer> list = new ArrayList<>();
list.add(42);           // autoboxing: int -> Integer
int n = list.get(0);   // unboxing: Integer -> int
```

**Equality pitfall — Integer cache:** The JVM caches `Integer` instances for values -128 to 127 (spec-mandated; upper bound can be tuned with `-XX:AutoBoxCacheMax`). Comparing outside this range with `==` compares references:
```java
Integer a = 127, b = 127;
System.out.println(a == b); // true  (cached)

Integer c = 128, d = 128;
System.out.println(c == d); // false (different heap objects)
System.out.println(c.equals(d)); // true — always use .equals()
```
Same for `Long` (-128 to 127), `Short`, `Byte`, `Character` (0–127).

**Performance pitfall:** Every autobox/unbox allocates (outside the cache). In tight loops accumulating into `Integer` instead of `int` can create millions of objects:
```java
Long sum = 0L;
for (long i = 0; i < 1_000_000; i++) {
    sum += i; // unboxes sum, adds, re-boxes result each iteration — 1M allocations
}
// Fix: use primitive long sum = 0L;
```

**NullPointerException from unboxing:**
```java
Integer val = null;
int x = val; // NPE at runtime — unboxing null
```

**Interview trap:** "What does `new Integer(42) == new Integer(42)` return?" — `false`, because `new` bypasses the cache and creates distinct heap objects. `new Integer(int)` is deprecated since Java 9; use `Integer.valueOf(42)`.

**Tags:** autoboxing, wrappers, integer-cache, equality, performance

---

## Q-JLNG-007 [bloom: understand] [level: regular]

**Question:** Explain how the Stream API's laziness works. What is the difference between intermediate and terminal operations, and what is a stateful intermediate operation?

**Model answer:** A `Stream` pipeline is a *description of computation*, not a computation itself. Intermediate operations return a new `Stream` and are lazy — nothing runs until a terminal operation is invoked. This allows the JVM to fuse operations into a single pass.

**Intermediate operations (lazy, return Stream):**
- Stateless: `filter`, `map`, `flatMap`, `peek`, `mapToInt/Long/Double`
- Stateful: `distinct`, `sorted`, `limit`, `skip` — these may need to see many/all elements before producing output

**Terminal operations (eager, trigger execution):**
`forEach`, `collect`, `count`, `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `reduce`, `toArray`, `min`, `max`

**Laziness in practice:**
```java
List.of(1, 2, 3, 4, 5).stream()
    .filter(n -> { System.out.println("filter " + n); return n > 2; })
    .map(n -> { System.out.println("map " + n); return n * 10; })
    .findFirst();  // terminal
// Output: filter 1, filter 2, filter 3, map 3 — stops at first match
```
Without laziness all 5 elements would be filtered and mapped before `findFirst` could short-circuit.

**Stateful ops break short-circuiting:**
```java
stream.sorted().findFirst()
```
`sorted` must consume the entire stream to determine the first element. Adding `sorted()` before `findFirst()` forces full materialisation — no lazy short-circuit.

**Stream is single-use:** once a terminal operation runs, the stream is "consumed". Reusing it throws `IllegalStateException`.

**Interview trap:** "Does `peek` trigger evaluation?" — No, `peek` is an intermediate op; it's lazy. It's often misused as a debugging hook with the assumption it runs eagerly. Second trap: `Stream.generate(() -> expensiveOp())` creates an *infinite* stream; without `limit()` or a short-circuiting terminal it never terminates.

**Tags:** streams, laziness, intermediate-ops, terminal-ops, stateful, java8

---

## Q-JLNG-008 [bloom: understand] [level: regular]

**Question:** How do collectors work in the Stream API? Explain `Collectors.groupingBy`, `partitioningBy`, and `toUnmodifiableMap` — and what are common collector mistakes?

**Model answer:** `Collector<T, A, R>` is a fold operation with three type parameters: element type T, mutable accumulator A, and result type R. `Stream.collect(collector)` wires up supplier/accumulator/combiner/finisher.

**Common collectors:**

```java
// groupingBy — Map<K, List<V>>
Map<String, List<Order>> byStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::getStatus));

// groupingBy with downstream collector
Map<String, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

// groupingBy with mapping downstream
Map<String, List<String>> idsByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.mapping(Order::getId, Collectors.toList())));

// partitioningBy — Map<Boolean, List<T>>
Map<Boolean, List<Order>> paid = orders.stream()
    .collect(Collectors.partitioningBy(Order::isPaid));

// toMap — duplicate key throws by default
Map<Long, Order> byId = orders.stream()
    .collect(Collectors.toMap(Order::getId, o -> o)); // throws on duplicate key!

// Safe: provide merge function
Map<Long, Order> byIdSafe = orders.stream()
    .collect(Collectors.toMap(
        Order::getId, o -> o,
        (existing, replacement) -> existing));

// Java 10+: unmodifiable map
Map<Long, Order> immutable = orders.stream()
    .collect(Collectors.toUnmodifiableMap(Order::getId, o -> o));

// joining
String csv = items.stream().collect(Collectors.joining(", ", "[", "]"));
```

**Common mistakes:**
1. `toMap` without a merge function — throws `IllegalStateException: Duplicate key` the moment two elements map to the same key.
2. `groupingBy` with a null key — throws `NullPointerException` (unlike `HashMap` which allows null keys, `groupingBy` does not).
3. Forgetting `Collectors.counting()` returns `Long` not `long` — autoboxing in merge ops.
4. Using `toList()` (Java 16+, returns unmodifiable list) vs `Collectors.toList()` (returns mutable `ArrayList`). They are not interchangeable.

**Interview trap:** "What if you need a `TreeMap` from `groupingBy`?" — `Collectors.groupingBy(keyFn, TreeMap::new, Collectors.toList())` — the three-arg overload takes a map factory.

**Tags:** streams, collectors, groupingby, tomap, java8, java16

---

## Q-JLNG-009 [bloom: understand] [level: regular]

**Question:** What are the pitfalls of parallel streams? When should you use them and when should you avoid them?

**Model answer:** `stream.parallel()` or `collection.parallelStream()` splits the work across `ForkJoinPool.commonPool()` (default parallelism = number of CPUs - 1). For the right workloads this is free lunch; for the wrong ones it is a production incident.

**When parallel streams help:**
- Large data sets (roughly > 10k elements — benchmark for your actual case).
- Computationally expensive per-element operations (CPU-bound).
- No shared mutable state.
- Operation is associative and the order of results is irrelevant or handled.

**When parallel streams hurt or fail:**

1. **Small collections** — `ForkJoin` split/merge overhead exceeds the computation.
2. **IO-bound operations** — threads block on IO; you saturate `commonPool` and starve other parallel streams in the JVM (including Spring Batch, Spring MVC if it uses parallel streams internally).
3. **Stateful lambdas / shared mutable state** — data races:
   ```java
   List<Integer> results = new ArrayList<>(); // not thread-safe
   list.parallelStream().map(this::process).forEach(results::add); // WRONG
   // Fix: .collect(Collectors.toList())
   ```
4. **Ordered operations on unordered data** — e.g., `forEachOrdered` on a parallel stream serialises output and kills parallelism benefit.
5. **`sorted()` on parallel stream** — forces a merge sort across threads; often slower than sequential.
6. **Custom `ForkJoinPool`** — if you need isolation from `commonPool` (e.g., in a web app), you must wrap in a custom pool:
   ```java
   ForkJoinPool pool = new ForkJoinPool(4);
   pool.submit(() -> list.parallelStream().map(fn).collect(toList())).get();
   ```
   Without this, parallel streams in a web application compete with every other parallel stream for `commonPool` threads.

**Interview trap:** "Parallel stream always returns results in the same order as input?" — No, unless the source has a defined encounter order AND you use an order-preserving terminal like `collect(toList())`. `forEach` in parallel is unordered. `forEachOrdered` preserves order but kills parallelism.

**Tags:** streams, parallel-streams, forkjoinpool, thread-safety, performance

---

## Q-JLNG-010 [bloom: understand] [level: regular]

**Question:** Explain lambdas in Java: what is an effectively final variable, what are the closure semantics, and what is a functional interface?

**Model answer:** A lambda expression is a concise way to express an instance of a *functional interface* — any interface with exactly one abstract method (SAM type).

```java
Comparator<String> comp = (a, b) -> a.compareTo(b);
Runnable r = () -> System.out.println("hello");
Function<Integer, Integer> square = x -> x * x;
```

**Functional interface:** interface with exactly one abstract method. `@FunctionalInterface` annotation is optional but enforces this at compile time. Key built-in functional interfaces (java.util.function):

| Interface | Signature | Use |
|---|---|---|
| `Predicate<T>` | `T → boolean` | filter |
| `Function<T,R>` | `T → R` | map, transform |
| `Consumer<T>` | `T → void` | forEach |
| `Supplier<T>` | `() → T` | lazy values |
| `BiFunction<T,U,R>` | `T,U → R` | merge, combine |
| `UnaryOperator<T>` | `T → T` | transform in place |

**Closure and effectively final:** lambdas can capture local variables from the enclosing scope, but those variables must be *effectively final* — assigned exactly once (no need for the `final` keyword, but must behave as if final).

```java
int base = 10; // effectively final — never reassigned
Function<Integer, Integer> adder = x -> x + base; // OK

int counter = 0;
Runnable r = () -> counter++; // COMPILE ERROR — counter is not effectively final
```

**Why effectively final?** Lambdas may run in a different thread or later than the enclosing scope exits. The compiler captures the value, not a reference to the variable slot. Allowing mutation would mean the lambda sees a stale value.

**Interview trap:** "Can a lambda mutate a field (not local variable)?" — Yes. The restriction is only on *local variables*. `this.counter++` inside a lambda is fine (though not thread-safe). Second trap: `@FunctionalInterface` on an interface with two abstract methods causes a compile error, not a runtime failure.

**Tags:** lambdas, functional-interface, effectively-final, closure, java8

---

## Q-JLNG-011 [bloom: understand] [level: regular]

**Question:** What are the four kinds of method references in Java and when do they differ semantically from the equivalent lambda?

**Model answer:** Method references are shorthand for lambdas that do nothing but call a single method. Four kinds:

| Kind | Syntax | Equivalent lambda |
|---|---|---|
| Static method | `ClassName::staticMethod` | `(args) -> ClassName.staticMethod(args)` |
| Instance method of particular object | `instance::instanceMethod` | `(args) -> instance.instanceMethod(args)` |
| Instance method of arbitrary object of type | `ClassName::instanceMethod` | `(obj, args) -> obj.instanceMethod(args)` |
| Constructor | `ClassName::new` | `(args) -> new ClassName(args)` |

```java
// Static
Function<String, Integer> parse = Integer::parseInt;
// Particular instance
String prefix = "Mr. ";
Function<String, String> greet = prefix::concat;
// Arbitrary instance
Function<String, String> upper = String::toUpperCase; // (s) -> s.toUpperCase()
// Constructor
Supplier<ArrayList<String>> factory = ArrayList::new;
```

**Semantic difference:** The "particular instance" form *captures* the reference at the point the method reference is created. If the captured object is mutable, the method reference behaviour depends on the object's state at invocation time, not capture time. A lambda makes this explicit:

```java
Supplier<String> ref = someBean::getName;  // captures someBean NOW
// vs
Supplier<String> lam = () -> someBean.getName(); // resolves someBean on each call (same reference, but intent is clearer)
```

**Overloaded methods:** if the target method is overloaded, the compiler resolves the overload based on the functional interface's signature. If ambiguous, use a lambda with an explicit cast instead.

**Interview trap:** "Is `String::toUpperCase` a static or instance method reference?" — Instance method of arbitrary object of type (third kind). The functional interface must accept a `String` as its first argument. Second trap: `ClassName::new` with multiple constructors — the compiler picks the constructor matching the functional interface's parameter types.

**Tags:** method-references, lambdas, java8, functional-interface

---

## Q-JLNG-012 [bloom: understand] [level: regular]

**Question:** Explain Optional's API and describe at least three common misuse patterns that make code worse, not better.

**Model answer:** `Optional<T>` (Java 8) is a container that may or may not hold a non-null value. It is designed to be the *return type* of methods that might have no result, making the "no value" case explicit in the API.

**Core API:**
```java
Optional<Order> opt = orderRepo.findById(id);

// Bad — replicates null-check idiom, defeats the purpose
if (opt.isPresent()) { process(opt.get()); }

// Good — chaining
opt.ifPresent(this::process);
opt.map(Order::getTotal).orElse(BigDecimal.ZERO);
opt.flatMap(o -> o.getAddress()).map(Address::getCity).orElse("unknown");
opt.orElseThrow(() -> new OrderNotFoundException(id)); // Java 10+: orElseThrow() (no arg)
opt.filter(Order::isPaid).ifPresent(this::archiveOrder);
```

**Three common misuse patterns:**

**1. Optional as a field type:**
```java
public class Customer {
    private Optional<String> middleName; // ANTIPATTERN
}
```
`Optional` is not `Serializable`. It was never intended for fields; nullable fields are the correct idiom. Jackson serialisation goes wrong. Use `@Nullable` annotation for documentation.

**2. Optional as a method parameter:**
```java
void send(String to, Optional<String> cc) // ANTIPATTERN
```
Forces callers to wrap: `send("alice", Optional.of("bob"))`. Method overloading is cleaner: `send(String to)` and `send(String to, String cc)`.

**3. Using `isPresent()` + `get()` instead of functional chaining:**
```java
Optional<String> name = findName();
if (name.isPresent()) {
    return name.get().toUpperCase();
} else {
    return "UNKNOWN";
}
// Should be:
return findName().map(String::toUpperCase).orElse("UNKNOWN");
```

**4. Wrapping collections:**
```java
Optional<List<Order>> orders = ... // ANTIPATTERN
// An empty list already represents "nothing". Return List.of() not Optional.empty()
```

**Performance note:** `Optional` is a heap-allocated object. In hot paths creating thousands of `Optional.empty()` instances adds GC pressure. Java 9 added `Optional.stream()` — converts to a `Stream` of 0 or 1 elements for use in `flatMap` chains.

**Interview trap:** "Does `Optional.get()` on an empty Optional throw NPE?" — No, it throws `NoSuchElementException`. Use `orElseThrow()` if you want a meaningful exception. Second trap: `Optional.of(null)` throws `NullPointerException` immediately; use `Optional.ofNullable(null)` for the empty optional.

**Tags:** optional, null-safety, java8, api-design

---

## Q-JLNG-013 [bloom: understand] [level: regular]

**Question:** What is type erasure in Java generics, and what can you NOT do at runtime because of it?

**Model answer:** Type erasure is the JVM mechanism by which generic type parameters are removed at compile time. The compiler uses type information for type checking and inserts casts into the bytecode, then discards the generic type information. At runtime, `List<String>` and `List<Integer>` are both just `List` (their *raw type*).

**What the compiler does:**
- Unbounded `T` → `Object`
- Bounded `T extends Comparable<T>` → `Comparable`
- Wildcard `?` → appropriate bound or `Object`
- Inserts checked casts at use sites.

**What you CANNOT do because of erasure:**

```java
// 1. Cannot create generic array
T[] arr = new T[10]; // compile error

// 2. Cannot use instanceof with parameterised type
if (list instanceof List<String>) { } // compile error (Java <16); warning/error

// 3. Cannot instantiate T
public T create() { return new T(); } // compile error

// 4. Cannot catch or throw parameterised types
catch (SomeException<String> e) { } // compile error

// 5. Cannot overload methods that differ only in type parameter
void process(List<String> list) { }
void process(List<Integer> list) { } // compile error — same erasure
```

**Heap pollution and unchecked warnings:**
```java
@SuppressWarnings("unchecked")
List<String> strings = (List<String>) someRawList; // "unchecked cast"
strings.get(0); // ClassCastException at this line if list actually had Integer
```

**Reified generics comparison:** C# generics are reified — `typeof(List<string>)` works at runtime, and `new T()` is possible with `where T : new()`. Java lacks this for backward compatibility with pre-5 bytecode.

**Interview trap:** "Can you get generic type information at runtime?" — Sometimes. If a class *extends* a parameterised type, the supertype's type argument is preserved in the class file as a `ParameterizedType` and accessible via reflection (`getGenericSuperclass()`). This is how Jackson, Spring, and Guava's `TypeToken` work around erasure. But a plain method-local `List<String>` is fully erased.

**Tags:** generics, type-erasure, java5, reflection, heap-pollution

---

## Q-JLNG-014 [bloom: understand] [level: regular]

**Question:** Explain the PECS rule for generic wildcards. When do you use `<? extends T>` vs `<? super T>`?

**Model answer:** PECS — **Producer Extends, Consumer Super** — is a mnemonic for choosing wildcard bounds in generic APIs.

**`<? extends T>` — the producer bound (upper-bounded wildcard):**
You can read elements of type T from the collection. The collection "produces" T-typed objects for you. You cannot write to it (except `null`) because the compiler doesn't know the exact subtype.

```java
// Copy FROM source (producer) — can be List<Integer>, List<Double>, etc.
public static <T extends Number> double sum(List<? extends Number> source) {
    return source.stream().mapToDouble(Number::doubleValue).sum();
}
// You can: Number n = source.get(0);  OK
// You cannot: source.add(1.0);        compile error
```

**`<? super T>` — the consumer bound (lower-bounded wildcard):**
You can write T objects into the collection. The collection "consumes" what you put in. Reading gives you only `Object` because the exact supertype is unknown.

```java
// Write INTO dest (consumer) — can be List<Integer>, List<Number>, List<Object>
public static <T> void copy(List<? extends T> src, List<? super T> dest) {
    for (T t : src) dest.add(t);
}
// You can:  dest.add(someInteger);   OK
// Reading: Object o = dest.get(0);  only Object
```

**Unbounded wildcard `<?>`:** when neither reading typed elements nor writing matters — only the structure. Used in reflective / utility code.

```java
public void printAll(List<?> list) {
    list.forEach(System.out::println); // treating as Object, fine
}
```

**Classic Collections.copy signature:**
```java
public static <T> void copy(List<? super T> dest, List<? extends T> src)
```
Exactly PECS: `src` is a producer (extends), `dest` is a consumer (super).

**Interview trap:** "What is the difference between `List<?>` and `List<Object>`?" — `List<Object>` can only hold an actual `List<Object>` — you cannot pass a `List<String>` to a method accepting `List<Object>` (generics are invariant). `List<?>` accepts any parameterised list: `List<String>`, `List<Integer>`, etc., but you can only read `Object` from it.

**Tags:** generics, wildcards, pecs, upper-bounded, lower-bounded, java5

---

## Q-JLNG-015 [bloom: understand] [level: regular]

**Question:** Explain Java String immutability, the String pool, and when `String.intern()` is relevant. What is the performance consequence of String concatenation in a loop?

**Model answer:** `String` in Java is immutable — once created, its character array cannot change. Every "modification" (concatenation, substring, replace) produces a new `String` object.

**String pool (string interning):**
The JVM maintains a pool of string literals in the heap (since Java 7; previously in PermGen). String literals in source code are automatically interned — two identical literals resolve to the same object.

```java
String a = "hello";
String b = "hello";
System.out.println(a == b); // true — same pool reference

String c = new String("hello"); // bypasses pool, new heap object
System.out.println(a == c); // false
System.out.println(a.equals(c)); // true — always use equals for content
System.out.println(a == c.intern()); // true — intern() returns pool reference
```

**`String.intern()`:** returns the pool canonical copy. Calling `intern()` on a computed string (e.g., from DB) adds it to the pool and returns the pooled reference, enabling `==` comparison and reducing memory for repeated strings. Risk: filling the pool causes GC pressure in the string pool space (though since Java 7 it's regular heap and GC can reclaim unreferenced pool entries).

**Concatenation in a loop:**
```java
String result = "";
for (String s : list) {
    result += s; // creates new String object on every iteration — O(n²) time and n allocations
}
// Fix: use StringBuilder
StringBuilder sb = new StringBuilder();
for (String s : list) sb.append(s);
String result = sb.toString();
```

The `+` operator on `String` is compiled to `StringBuilder` by the compiler for *simple concatenations within a single statement*, but in a loop the `StringBuilder` is re-created each iteration until Java 9's `invokedynamic` StringConcatFactory which can optimise some patterns but not the assignment-in-loop antipattern.

**Java 11+:** `String` is backed by a byte array (`LATIN1` or `UTF16`) rather than a `char[]` — halves memory for ASCII-only strings (Compact Strings, JEP 254, Java 9).

**Interview trap:** "`String` is thread-safe because it's immutable?" — Yes for the String content, but a `String` reference can still be subject to visibility issues if not properly published across threads. Immutability guarantees the *state* is safe, not the reference variable itself.

**Tags:** string, immutability, string-pool, interning, stringbuilder, java9

---

## Q-JLNG-016 [bloom: apply] [level: senior]

**Question:** Walk through sealed classes (Java 17, GA): what constraints do `permits` impose, what must permitted subclasses declare, and how do sealed types enable exhaustive pattern matching?

**Model answer:** A sealed class or interface restricts which types may extend/implement it. The `permits` clause lists the allowed subtypes explicitly.

```java
public sealed interface Shape permits Circle, Rectangle, Triangle { }

public record Circle(double radius) implements Shape { }
public record Rectangle(double w, double h) implements Shape { }
public final class Triangle implements Shape {
    private final double base, height;
    // ...
}
```

**Permitted subclass constraints:**
- Must be in the same package (or same module) as the sealed type.
- Must directly extend/implement the sealed type.
- Must explicitly declare one of three modifiers:
  - `final` — no further extension allowed.
  - `sealed` — continues the sealed hierarchy (must have its own `permits`).
  - `non-sealed` — reopens the hierarchy; anyone can extend this subtype (escape hatch).

**Exhaustive pattern matching for switch (Java 21, stable):**
When the switch target is a sealed type, the compiler knows the complete set of subtypes and can enforce exhaustiveness — no `default` required:

```java
double area(Shape s) {
    return switch (s) {
        case Circle c    -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.w() * r.h();
        case Triangle t  -> 0.5 * t.base() * t.height();
        // compiler error if you omit any branch — sealed guarantees closure
    };
}
```

**Guards (Java 21):**
```java
String describe(Shape s) {
    return switch (s) {
        case Circle c when c.radius() > 100 -> "huge circle";
        case Circle c                        -> "circle";
        case Rectangle r when r.w() == r.h() -> "square";
        case Rectangle r                     -> "rectangle";
        case Triangle t                      -> "triangle";
    };
}
```

**Null handling in switch:** Java 21 patterns can handle `null` explicitly:
```java
switch (s) {
    case null -> throw new NullPointerException("shape is null");
    case Circle c -> ...
}
```
Without a `null` case, passing `null` to a pattern switch throws `NullPointerException` (unlike traditional switch on String/int which also throws NPE).

**Algebraic Data Types analogy:** Sealed + records give Java a sum type: the `Shape` domain is *exactly* `Circle | Rectangle | Triangle`. This is the foundation of visitor-pattern-free exhaustive dispatch and is the Java equivalent of Kotlin `sealed class` / Haskell `data`.

**Interview trap:** "If a permitted subtype is `non-sealed`, does the switch still compile without `default`?" — No. The compiler loses exhaustiveness guarantees because `non-sealed` allows unknown subtypes. You must add a `default` (or wildcard `case _`) arm. Second trap: sealed classes introduced in Java 15 as preview, Java 16 as second preview, GA in Java 17 — be ready to state the timeline.

**Tags:** sealed-classes, java17, pattern-matching, exhaustiveness, records, adt

---

## Q-JLNG-017 [bloom: apply] [level: senior]

**Question:** Describe pattern matching for switch in Java 21 (stable). Cover type patterns, guarded patterns, dominance rules, and how it interacts with null.

**Model answer:** Pattern matching for switch is stable in Java 21 (JEP 441, after previews in 17, 18, 19, 20). It unifies type-testing, binding, and dispatch in a single construct.

**Type patterns:**
```java
static String format(Object obj) {
    return switch (obj) {
        case Integer i   -> "int " + i;
        case Long l      -> "long " + l;
        case Double d    -> "double " + d;
        case String s    -> "string \"" + s + "\"";
        case int[] arr   -> "int[] of length " + arr.length;
        case null        -> "null";
        default          -> "other: " + obj.getClass().getName();
    };
}
```

**Guarded patterns (`when` clause):**
```java
switch (shape) {
    case Circle c when c.radius() <= 0 -> throw new IllegalArgumentException("zero radius");
    case Circle c                      -> processCircle(c);
    case Rectangle r when r.w() == r.h() -> processSquare(r);
    case Rectangle r                   -> processRectangle(r);
}
```

**Dominance rules:** More specific patterns must come before more general ones. A compile error is issued if a less specific case dominates (shadows) a more specific one:
```java
case Object o -> ...; // ERROR if placed before case String s
case String s -> ...; // unreachable, compile error
```

**Completeness:** If the selector type is sealed, the compiler requires all subtypes covered (no `default` needed). For `Object` (or `interface` with open hierarchy), a `default` or `case Object o` is required.

**Null semantics:** Traditional switch throws NPE on null for all selector types. Pattern switch:
- Without `case null`: still throws NPE (backward-compatible).
- With `case null`: handled explicitly.
- `case null, default`: single arm for null + fallback.

**Primitive patterns (preview Java 23+):** Upcoming preview allows patterns like `case int i` on a boxed `Integer` target, combining unboxing and binding.

**Interview trap:** "What Java version made pattern matching for switch stable?" — Java 21 (JEP 441). Commonly confused with Java 17 (sealed classes stable) or Java 16 (instanceof pattern matching stable). Know the timeline. Second trap: `case String s when s.isEmpty()` — the guard runs only if the type pattern matches; if `obj` is not a `String` at all, the `when` does not run.

**Tags:** pattern-matching, switch-expression, java21, java17, guarded-patterns, null-handling

---

## Q-JLNG-018 [bloom: apply] [level: senior]

**Question:** Explain checked vs unchecked exceptions in depth: the design rationale, their incompatibility with functional interfaces and streams, and how modern Java codebases handle the tension.

**Model answer:** **Checked exceptions** (extend `Exception` but not `RuntimeException`) are part of the method contract — callers must either catch them or declare `throws`. The original Java design philosophy was: if a failure is *recoverable*, force the caller to think about it at compile time.

**Unchecked exceptions** (extend `RuntimeException` or `Error`) are not part of the signature. Used for programming errors (NPE, AIOOBE), unrecoverable states (OOM), or any domain exception in modern frameworks.

**The functional interface incompatibility:**
`java.util.function.*` interfaces do not declare `throws`. A lambda used as a `Function<T, R>` cannot throw a checked exception:

```java
// Compile error — Files.readString throws IOException (checked)
Function<Path, String> reader = path -> Files.readString(path); // FAILS

// Options:

// 1. Wrap in unchecked
Function<Path, String> reader = path -> {
    try { return Files.readString(path); }
    catch (IOException e) { throw new UncheckedIOException(e); }
};

// 2. Define a throwing functional interface
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}
// Then write a wrapper that converts it to a regular Function
static <T, R> Function<T, R> wrap(ThrowingFunction<T, R> fn) {
    return t -> {
        try { return fn.apply(t); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}
Function<Path, String> reader = wrap(Files::readString);

// 3. Libraries like Vavr provide Try<T> monad
```

**try-with-resources and suppressed exceptions:**
```java
try (InputStream in = open(); OutputStream out = create()) {
    copy(in, out);
}
// Resources closed in reverse declaration order: out closed first, then in
// If copy throws AND close throws, the close exception is SUPPRESSED
// Retrieve with: e.getSuppressed()
```

**Modern conventions:**
- Spring, Hibernate, Jakarta EE all use unchecked exceptions universally.
- Checked exceptions cause "exception tunnelling" — layers wrap and re-throw repeatedly.
- Rule of thumb: use checked only when the caller has a realistic recovery path (e.g., file not found — maybe try alternate path); for everything else, unchecked.
- `throws Exception` in a method signature is an anti-pattern — it leaks implementation detail and forces callers to catch `Exception`.

**Interview trap:** "Can you use `InterruptedException` (checked) in a lambda?" — No, not directly. Common pattern: catch it, restore the interrupt flag, throw an unchecked: `Thread.currentThread().interrupt(); throw new RuntimeException(e);`. Swallowing `InterruptedException` without restoring the flag is a subtle concurrency bug that silently breaks thread pools.

**Tags:** exceptions, checked-exceptions, unchecked, try-with-resources, lambda, suppressed-exceptions

---

## Q-JLNG-019 [bloom: apply] [level: senior]

**Question:** Describe the Java 8→21 feature timeline. For a senior candidate, which releases are most important to know in depth and why?

**Model answer:** The key releases and their landmark features:

| Release | Year | Key language/JVM features |
|---|---|---|
| **Java 8** | 2014 | Lambdas, Streams, Optional, default/static interface methods, `java.time`, `CompletableFuture`, Nashorn, `PermGen` → Metaspace |
| **Java 9** | 2017 | Module System (Jigsaw), `jshell`, `List.of()`/`Map.of()` factory methods, `Stream.takeWhile/dropWhile/iterate`, Compact Strings, HTTP/2 client (incubator) |
| **Java 10** | 2018 | `var` local type inference, Application CDS |
| **Java 11** (LTS) | 2018 | `String` new methods (`isBlank`, `strip`, `lines`, `repeat`), `Optional.isEmpty()`, HTTP Client (standard), `Files.readString/writeString`, `var` in lambda params |
| **Java 14** | 2020 | Switch expressions (stable, JEP 361), `instanceof` pattern matching (preview), Records (preview), NullPointerException helpful messages |
| **Java 15** | 2020 | Text blocks (stable, JEP 378), Sealed classes (preview) |
| **Java 16** | 2021 | Records (stable, JEP 395), `instanceof` pattern matching (stable, JEP 394), `Stream.toList()` |
| **Java 17** (LTS) | 2021 | Sealed classes (stable, JEP 409), Pattern matching for switch (preview), Strong encapsulation of JDK internals |
| **Java 19–20** | 2022–23 | Virtual threads (preview), Record patterns (preview), Structured concurrency (incubator) |
| **Java 21** (LTS) | 2023 | Virtual threads (stable, JEP 444), Pattern matching for switch (stable, JEP 441), Record patterns (stable, JEP 440), Sequenced collections (JEP 431), Structured concurrency (preview) |

**Most important for senior interviews:**
- **Java 8** — still the foundation of all production code; every Stream/lambda question lives here.
- **Java 11 (LTS)** — most teams' minimum supported version; String API, `var` in lambdas.
- **Java 17 (LTS)** — sealed classes + switch expression — ADT pattern.
- **Java 21 (LTS)** — virtual threads (paradigm shift for IO-bound services), full pattern matching.

**Interview trap:** "When did records become stable?" — Java 16 (not 14, where they were preview). Same for instanceof pattern matching. Sealed classes stable in Java 17. A common interview error is to say "Java 17 introduced records."

**Tags:** java-timeline, java8, java11, java17, java21, lts

---

## Q-JLNG-020 [bloom: apply] [level: senior]

**Question:** You have a large computation pipeline: filter a list of 1M orders, group by customer, compute per-customer total, and sort the groups by total descending. Write this using the Stream API and explain every design decision.

**Model answer:**
```java
Map<Long, BigDecimal> totalByCustomer = orders.stream()
    .filter(o -> o.getStatus() == Status.COMPLETED)
    .collect(Collectors.groupingBy(
        Order::getCustomerId,
        Collectors.mapping(
            Order::getTotal,
            Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

List<Map.Entry<Long, BigDecimal>> ranked = totalByCustomer.entrySet().stream()
    .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
    .collect(Collectors.toList());
```

**Decision walkthrough:**

1. **`filter` first** — reduce the working set before `groupingBy` materialises the map. Stateless ops (`filter`, `map`) come before stateful ops (`sorted`).

2. **`groupingBy` with downstream `reducing`** — avoids intermediate `List<BigDecimal>` per customer. `Collectors.summingDouble` would lose precision on monetary values; `reducing` with `BigDecimal::add` is correct for money.

3. **Two-pass pipeline** — `groupingBy` is a terminal op (forces full stream evaluation); sorting must be a second stream over the entries. Trying to sort inside `groupingBy` is awkward; cleaner to separate.

4. **`Map.Entry.comparingByValue().reversed()`** — avoids a custom `Comparator`. `reversed()` gives descending order.

5. **Parallel consideration:** With 1M orders and expensive `BigDecimal::add`, `parallel()` on the first stream could help — but only if `groupingBy` in parallel mode is safe. It is: `Collectors.groupingBy` is a concurrent-safe collector when used with `groupingByConcurrent` (returns `ConcurrentHashMap`). For plain `groupingBy`, parallel produces correct results via the `combiner` function.

```java
// Parallel version — use groupingByConcurrent for less overhead
Map<Long, BigDecimal> totalByCustomer = orders.parallelStream()
    .filter(o -> o.getStatus() == Status.COMPLETED)
    .collect(Collectors.groupingByConcurrent(
        Order::getCustomerId,
        Collectors.reducing(BigDecimal.ZERO, Order::getTotal, BigDecimal::add)));
```

**Interview trap:** "Why not `summingDouble`?" — `double` arithmetic is approximate. For monetary totals, `BigDecimal` with `reducing` is required. A candidate who uses `summingDouble` for money has a serious production bug waiting to happen.

**Tags:** streams, collectors, groupingby, bigdecimal, parallel-streams, apply

---

## Q-JLNG-021 [bloom: apply] [level: senior]

**Question:** What are the rules for `var` in lambda parameters (Java 11) and why would you use `var` in a lambda at all when you could omit the type entirely?

**Model answer:** Java 11 (JEP 323) allows `var` in lambda parameter lists. You could already omit types entirely in lambdas:

```java
// All three are equivalent for a BiFunction<String, Integer, String>:
(String s, Integer n) -> s.repeat(n)   // explicit types
(s, n) -> s.repeat(n)                  // inferred, no var
(var s, var n) -> s.repeat(n)          // var (Java 11+)
```

**Constraint:** if you use `var` for one parameter, you must use it for all parameters (same rule as mixing explicit types — you cannot mix `var` and explicit, or `var` and omitted):
```java
(var s, n) -> s.repeat(n)             // COMPILE ERROR — mixing var and inferred
(String s, var n) -> s.repeat(n)      // COMPILE ERROR — mixing explicit and var
```

**Why use `var` in a lambda at all?** The only practical reason is to attach annotations to lambda parameters — you cannot annotate a parameter without declaring its type somehow:

```java
(@NotNull var s, @NotNull var n) -> s.repeat(n)
// Equivalent annotation with explicit type:
(@NotNull String s, @NotNull Integer n) -> s.repeat(n)
```
With `var`, you get annotation support without hardcoding the type, which matters when the lambda type is complex or inferred from a long generic chain.

**Interview trap:** "What does `var` do inside a lambda body?" — Nothing special; `var` in the *body* of a lambda is standard local variable type inference (Java 10), same as in any method body. Only the *parameter list* usage is the Java 11 addition.

**Tags:** var, java11, lambda, annotations

---

## Q-JLNG-022 [bloom: apply] [level: senior]

**Question:** Explain the `try-with-resources` mechanics in depth: what happens when both the body and the `close()` call throw exceptions, and what ordering applies when multiple resources are declared?

**Model answer:** `try-with-resources` (Java 7, JEP via JSR 334) auto-closes any resource that implements `AutoCloseable`. The resource variable is effectively final within the try block.

**Multi-resource declaration — reverse-order closing:**
```java
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    // use rs
}
// Close order: rs.close(), then ps.close(), then conn.close()
// (reverse of declaration — stack-like)
```

**Exception suppression — what actually happens when both body and close throw:**
```java
try (SomeResource r = new SomeResource()) {
    throw new RuntimeException("body exception");
    // r.close() is called; if it also throws:
    // the body exception is the PRIMARY exception
    // the close exception is SUPPRESSED and attached to primary
}
// catch sees: RuntimeException("body exception")
// with: e.getSuppressed() == [CloseException]
```

The body exception "wins." The close exception is not lost — it's accessible via `Throwable.getSuppressed()`. This is the correct behaviour (pre-TWR `finally` blocks did the opposite — the finally exception *replaced* the try exception, losing the original cause).

**What if only `close()` throws (no body exception):** the close exception propagates normally as the primary exception.

**`AutoCloseable` vs `Closeable`:**
- `Closeable` extends `AutoCloseable` but narrows `throws` to `IOException` (idempotent close recommended).
- `AutoCloseable.close()` can throw any `Exception`.

**Initialisation failure:** if the second resource declaration throws during initialisation, the first resource (already opened) is closed before the exception propagates. If the third resource declaration fails, the first two are closed.

```java
try (A a = openA();       // opens OK
     B b = openB(a);      // throws!
     C c = openC(b)) {    // never reached
}
// a.close() is called automatically; b.close() not called (never opened)
```

**Interview trap:** "Is it safe to return from inside a try-with-resources block?" — Yes. The resources are still closed before the method returns. TWR uses a `finally`-equivalent mechanism in the bytecode. Second trap: a resource declared as `null` — `close()` is NOT called on null resources (the JVM skips the close call if the reference is null). Useful: `try (Connection c = maybeNull ? null : ds.getConnection())` — safe.

**Tags:** try-with-resources, autocloseable, suppressed-exceptions, java7, resource-management

---

## Q-JLNG-023 [bloom: analyze] [level: master]

**Question:** A senior engineer says: "I added `.parallel()` to our nightly batch Stream pipeline and it's actually slower than the sequential version — sometimes by 2x. The data set is 50k records and each element requires a DB lookup." Diagnose the problem and explain the fix.

**Model answer:** This is a textbook case of parallel stream misuse. The culprit is IO-bound work on `ForkJoinPool.commonPool`.

**Diagnosis:**

1. **IO-bound operations block carrier threads.** Each DB lookup blocks the thread for tens of milliseconds. `ForkJoinPool` default parallelism is `Runtime.getRuntime().availableProcessors() - 1` (e.g., 7 on an 8-core box). With blocking IO, all 7 threads are waiting on DB; no parallelism benefit is achieved. In fact, thread management overhead makes it slower.

2. **`commonPool` contention.** The JVM has one `commonPool` shared by all parallel streams, `CompletableFuture.supplyAsync()` (when no executor is specified), and any library that uses ForkJoin. A batch job saturating `commonPool` with blocking tasks starves other parallel work in the same JVM (e.g., Spring's async tasks, other scheduled jobs).

3. **50k records is not large enough to amortize overhead.** ForkJoin splits the task recursively. For fast in-memory ops, the threshold is typically 10k+ elements before parallelism wins. With DB round-trips, you want a *thread pool* sized to match DB connection pool concurrency, not CPU count.

**Fix options:**

**Option 1: Sequential stream + async DB calls (virtual threads, Java 21):**
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<CompletableFuture<Result>> futures = records.stream()
        .map(r -> CompletableFuture.supplyAsync(() -> dbLookup(r), executor))
        .toList();
    List<Result> results = futures.stream()
        .map(CompletableFuture::join)
        .toList();
}
// Virtual threads: one per task, block without pinning OS threads
```

**Option 2: Custom bounded thread pool sized to DB pool:**
```java
ExecutorService pool = Executors.newFixedThreadPool(dbPoolSize);
List<Future<Result>> futures = records.stream()
    .map(r -> pool.submit(() -> dbLookup(r)))
    .toList();
// collect futures
```

**Option 3 (pre-Java 21, parallel stream with isolation):**
```java
ForkJoinPool pool = new ForkJoinPool(dbPoolSize);
List<Result> results = pool.submit(() ->
    records.parallelStream().map(r -> dbLookup(r)).toList()
).get();
pool.shutdown();
```
This isolates the batch from `commonPool` and sizes parallelism to the DB pool, but it's less clean than option 1.

**Root cause summary:** Parallel stream is designed for CPU-bound work. IO-bound work needs a thread pool sized to IO concurrency, not CPU count. Virtual threads (Java 21) make the cleanest solution: write blocking code, get IO concurrency for free.

**Interview trap:** "How would you measure this?" — JMH microbenchmark or at minimum: add logging to record elapsed time, active thread count, and DB pool wait time. Profiler with thread state view (BLOCKED vs RUNNABLE). Then compare sequential, parallel (commonPool), and virtual-thread approaches.

**Tags:** parallel-streams, forkjoinpool, virtual-threads, io-bound, performance, java21, diagnosis

---

## Q-JLNG-024 [bloom: analyze] [level: master]

**Question:** Explain the edge cases in Java generics that trip up experienced developers: raw types, heap pollution, unchecked casts, and the `@SafeVarargs` annotation. When is `@SafeVarargs` actually safe to apply?

**Model answer:** **Raw types** are generic types used without type parameters:
```java
List raw = new ArrayList(); // raw type — type checking bypassed
raw.add("hello");
raw.add(42);                // compiles! — no type parameter to enforce
String s = (String) raw.get(1); // ClassCastException at runtime
```
Raw types exist for backward compatibility with pre-Java 5 code. Every modern codebase should treat raw type compiler warnings as errors.

**Heap pollution** occurs when a variable of parameterised type refers to an object that is not of that parameterised type:
```java
List<String> strings = new ArrayList<>();
List raw = strings; // raw alias
raw.add(42);        // heap is now polluted
String s = strings.get(0); // ClassCastException — the cast the compiler inserted
```
Heap pollution is usually caused by raw type assignments or unchecked casts.

**Unchecked casts and the `@SuppressWarnings("unchecked")` contract:** The `@SuppressWarnings` annotation tells the compiler "I've verified this is safe." If you suppress without actually verifying, you're deferring the ClassCastException to an unrelated line where the compiler-inserted cast fails — making debugging very painful.

**Varargs and heap pollution — the `@SafeVarargs` problem:**
```java
@SafeVarargs
public static <T> List<T> listOf(T... elements) {
    return Arrays.asList(elements); // safe — reads the array but doesn't write back
}

// UNSAFE — varargs array is written to:
@SafeVarargs // WRONG — this lie causes heap pollution
public static <T> T[] dangerous(T... elements) {
    return elements; // leaks the parameterised array — heap pollution at caller
}
```

**When `@SafeVarargs` is legitimate:**
1. The method does NOT write to the varargs array (no `elements[i] = ...`).
2. The method does NOT pass the array reference to code that may write to it or leak it.
3. The method only reads from the array (iterates, passes elements by value).

`@SafeVarargs` can only be applied to `static`, `final`, or `private` methods (non-overridable) because a subclass override could violate the safety contract.

**The bridge method and generics:** When a class extends a generic class and overrides a method, the compiler generates a synthetic *bridge method* with the erased signature that delegates to the actual override. This enables polymorphism to work correctly after erasure and can surface in stack traces.

**Interview trap:** "Can you create a `List<String>[]`?" — Not directly: `new List<String>[10]` is a compile error (generic array creation). You can `@SuppressWarnings("unchecked") List<String>[] arr = new List[10]` with the unchecked cast, but you own the heap pollution risk. Second trap: `Class<T>` as a reification workaround — passing `Class<T> clazz` lets you use `clazz.cast(obj)` for runtime type checking without a heap pollution risk, which is why Jackson's `TypeReference` exists.

**Tags:** generics, raw-types, heap-pollution, unchecked-cast, safevarargs, type-erasure

---

## Q-JLNG-025 [bloom: analyze] [level: master]

**Question:** Deep-dive on Stream's internal mechanics: how does the spliterator/sink pipeline actually execute, what is a characteristic, and how does the JVM optimize a pipeline with known characteristics?

**Model answer:** Under the hood, `stream()` creates a pipeline of `Stage` objects. Calling a terminal operation triggers a *traversal protocol* using `Spliterator` (the source) and `Sink` (the per-stage consumer).

**Spliterator:** analogous to `Iterator` but designed for parallel splitting. Key methods:
- `tryAdvance(Consumer)` — process one element.
- `forEachRemaining(Consumer)` — process remaining elements.
- `trySplit()` — split into two spliterators for parallel processing.
- `estimateSize()` — hint for partitioning.
- `characteristics()` — bitmask of stream properties.

**Characteristics bitmask (from `Spliterator`):**

| Characteristic | Meaning | Optimisations it enables |
|---|---|---|
| `SIZED` | exact size known | avoids size re-check, better parallel split |
| `ORDERED` | encounter order is defined | enables ordered collectors |
| `DISTINCT` | all elements unique | `distinct()` can be a no-op |
| `SORTED` | elements in natural order | `sorted()` can be skipped |
| `IMMUTABLE` | source won't change | no `ConcurrentModificationException` guard |
| `CONCURRENT` | safe concurrent modification | used by concurrent collections |
| `NONNULL` | no null elements | null checks skipped internally |
| `SUBSIZED` | splits are also SIZED | better parallel split estimation |

**How the JVM optimises a pipeline:**
1. `ArrayList.stream()` returns a `SIZED | ORDERED | IMMUTABLE` spliterator.
2. `filter()` strips `SIZED` (can't predict how many pass).
3. `sorted()` sets `SORTED | ORDERED`.
4. If a `DISTINCT` source is detected, adding `.distinct()` is a no-op (the stream implementation can see the characteristic and skip the work).

**Loop fusion:** the terminal operation requests elements from the pipeline head. Each element flows through all intermediate stages one at a time ("vertical" traversal, not "horizontal"). This means memory is O(1) per element, not O(n) per stage.

```
Source → filter → map → findFirst
  1      ✓ pass  →10   → return 10   (element 1 traverses all stages, terminal fires)
```

**Parallel execution:** `trySplit()` is called recursively until sub-tasks are small enough (threshold based on size/parallelism). Each sub-task runs a sequential sub-pipeline. Ordered merge (`forEachOrdered`, ordered `collect`) requires synchronisation after all sub-tasks finish.

**Interview trap:** "Why does adding `distinct()` to a `HashSet.stream()` not allocate a HashSet internally?" — `HashSet` spliterator has `DISTINCT` characteristic. The `distinct()` operation checks for this characteristic and returns the same stream unmodified — no extra set allocated. This is one of the few characteristic-based optimisations visible in the JDK source.

**Tags:** streams, spliterator, sink, characteristics, internals, parallel, master

---

## Q-JLNG-026 [bloom: analyze] [level: master]

**Question:** A codebase uses `Optional` extensively as field types, method parameters, and inside collections. Beyond style concerns, what are the concrete runtime and serialisation problems this causes, and what is the correct migration path?

**Model answer:** `Optional` as field type, method parameter, and inside collections are three distinct antipatterns with concrete consequences:

**1. `Optional` as a field type — serialisation breakage:**
```java
public class UserDto {
    private Optional<String> nickname; // ANTIPATTERN
}
```
- `Optional` does not implement `Serializable` — Java object serialisation throws `NotSerializableException`.
- Jackson (without `jackson-datatype-jdk8` module) serialises `Optional<String>` as `{"empty": false, "present": true}` instead of the contained value. Even with the module, the JSON shape is surprising: `Optional.empty()` → `null` in JSON but `null` → no field. This makes the serialisation schema ambiguous.
- Hibernate/JPA mapping fails — `Optional` is not a supported field type; the JPA provider doesn't know how to persist it.

**2. `Optional` as method parameter — caller burden:**
```java
void send(String recipient, Optional<String> cc) { }
// Callers must write:
send("alice", Optional.of("bob"));
send("alice", Optional.empty());
// Rather than clean overloads:
void send(String recipient) { }
void send(String recipient, String cc) { }
```
Additionally, a caller can pass `null` as the `Optional` argument, causing NPE inside the method — worse than the problem it was supposed to solve.

**3. `Optional` inside collections — nested optionality:**
```java
Map<String, Optional<BigDecimal>> prices = ...
// prices.get("ITEM") returns Optional<Optional<BigDecimal>> (NOT really but confusing)
// Worse: List<Optional<Order>> — the empty Optional has no business being in the list
```
An absent value in a map should be a missing key, not a present key with `Optional.empty()`. An absent list element shouldn't be in the list at all.

**Migration path:**
1. Fields: replace `Optional<T> field` with `@Nullable T field` (Jetbrains or Jakarta). Use null checks or `Objects.requireNonNullElse`.
2. Method parameters: introduce overloads or use a builder/parameter-object pattern.
3. Return types: this is the legitimate use — keep `Optional<T>` as method return types.
4. Collections: remove absent entries from the map/list instead of storing `Optional.empty()`.

**Performance footnote:** `Optional` is a heap object — every method call that creates one in a hot path contributes to GC. In Java 9+ value types are on the roadmap (Project Valhalla), which would make `Optional<int>` a stack-allocated value object — but until then, optional-heavy hot paths should be profiled.

**Interview trap:** "Is there ever a legitimate use of Optional as a field?" — Arguably yes: an `Optional` field in a `Builder` pattern (before the object is built) can represent "not yet set." Some teams find this cleaner than a nullable field plus a sentinel. But it should never appear in the final built object's serialised form.

**Tags:** optional, serialization, antipatterns, jackson, nullable, master
