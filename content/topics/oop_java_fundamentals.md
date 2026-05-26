# OOP / Java fundamentals — bank pytań

> Kontekst: Groovy interop z Javą (oba kompilują do JVM bytecode), pricing engine używa wielu konwencji Java (Spring, Hibernate, Bean Validation). Solidne fundamenty Java są wymagane od Software Engineera, nawet gdy główny język to Groovy.

## Zakres

- SOLID principles
- equals/hashCode contract, Comparable, Comparator
- generics: type erasure, PECS (Producer Extends, Consumer Super), bounded wildcards
- collections framework: List/Set/Map families, Concurrent variants
- immutability, defensive copying, final fields
- streams API, Optional, functional interfaces, Lambda
- concurrency basics: synchronized, volatile, Atomic*, ExecutorService, CompletableFuture, ConcurrentHashMap
- patterns: Singleton, Factory, Strategy, Observer, Builder, Decorator, Adapter
- exceptions: checked vs unchecked, custom exceptions, try-with-resources
- JVM basics: heap vs stack, GC, JIT, class loading
- Java 17/21 features: records, sealed classes, pattern matching, switch expressions, text blocks, virtual threads

---

## Q-OOP-001 [bloom: recall]
**Pytanie:** Wymień 5 zasad SOLID i krótko opisz każdą.
**Modelowa odpowiedź:** **S — Single Responsibility:** klasa ma mieć jeden powód do zmiany, jedną odpowiedzialność. Klasa `Order` zarządza zamówieniem, ale `OrderRepository` zapisuje, `OrderEmailService` wysyła emaile. **O — Open/Closed:** otwarta na rozszerzenie, zamknięta na modyfikację. Nowy typ płatności nie wymaga edycji `PaymentProcessor` — tworzy nową implementację interfejsu. **L — Liskov Substitution:** podtyp musi działać tam gdzie typ bazowy bez psucia kontraktu. Jeśli `Square extends Rectangle` ma `setWidth` która zmienia też height — to łamie LSP (Rectangle nie miał tej semantyki). **I — Interface Segregation:** wiele małych interfejsów lepsze niż jeden duży. Klient nie powinien być zmuszony implementować metod których nie używa. **D — Dependency Inversion:** zależ od abstrakcji, nie od implementacji. `OrderService` zależy od `PaymentGateway` (interface), nie od `StripePaymentGateway` (concrete).
**Pułapka rozmowna:** SOLID to guidelines, nie ścisłe prawa. Stosowanie SOLID dogmatycznie generuje over-engineering — abstrakcje za każdą rzecz, gdy konkretu by wystarczył. „Refactor when needed, not when clever".
**Tagi:** solid, design, oop

## Q-OOP-002 [bloom: recall]
**Pytanie:** Co to jest kontrakt equals/hashCode w Javie?
**Modelowa odpowiedź:** Kontrakt `Object.equals(Object)` i `Object.hashCode()`:
1. **Reflexive:** `x.equals(x)` zwraca `true`.
2. **Symmetric:** `x.equals(y)` ↔ `y.equals(x)`.
3. **Transitive:** `x.equals(y) && y.equals(z) → x.equals(z)`.
4. **Consistent:** wielokrotne wywołanie zwraca to samo (jeśli pola nie zmienione).
5. **Null:** `x.equals(null)` zwraca `false`.

**hashCode:**
- Jeśli `a.equals(b)` → MUSI `a.hashCode() == b.hashCode()`.
- Konwersja: `a.hashCode() == b.hashCode()` nie wymaga `a.equals(b)` (collision OK).
- Konsystentny: ten sam obiekt → ten sam hashCode (chyba że pola się zmieniły).

**Implementacja Java 17+:**
```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Product p = (Product) o;
    return Objects.equals(id, p.id) && Objects.equals(sku, p.sku);
}

@Override
public int hashCode() {
    return Objects.hash(id, sku);
}
```

Lub `record` (Java 14+) — auto-generated.

**Konsekwencje złamania:**
- Object w `HashMap` jako key, hashCode zmienia się po insert → unfindable. Jeden z najczęstszych bugów: mutable fields w hashCode.
- `equals` bez `hashCode` → HashMap/HashSet broken.
- `equals` używa pola które zmieniają się w czasie życia → corruption hash structures.

**Pułapka rozmowna:** „Mogę używać tylko equals" — false dla collections. HashMap, HashSet, ConcurrentHashMap wymagają hashCode. Druga: `instanceof` vs `getClass()` — `instanceof` pozwala subclasses być equal (czasem chcesz, czasem nie). Pattern Java 17+: `if (!(o instanceof Product p)) return false;` (pattern matching for instanceof).
**Tagi:** equals, hashcode, contract

## Q-OOP-003 [bloom: recall]
**Pytanie:** Co to jest type erasure w generykach Java?
**Modelowa odpowiedź:** Type erasure to JVM mechanism: w runtime informacja o type parameter `<T>` jest kasowana. Generic types istnieją tylko w compile time. Stąd:
- `List<String>` w runtime to po prostu `List`.
- `List<Integer>` w runtime to też `List`.
- `instanceof List<String>` nie jest legal — nie da się sprawdzić.
- Można `List<?>` (raw-ish) i `instanceof List`.

**Konsekwencje:**
1. **Brak distinction at runtime:** `void m(List<String> l) { }` i `void m(List<Integer> l) { }` to ta sama signature → compile error.
2. **No `new T()` ani `T.class`:** brak typu w runtime, nie da się instancjować ani uzyskać Class. Workaround: pass `Class<T>` jako argument.
3. **Cast warnings:** `List<String> l = (List<String>) raw;` — unchecked cast (nie da się zweryfikować w runtime).
4. **Bridge methods:** kompilator generuje synthetic methods dla compatibility z erased signatures.
5. **`Class<T>` workaround:** `T newInstance(Class<T> clazz) { return clazz.getDeclaredConstructor().newInstance(); }` — pass class reference żeby uzyskać runtime info.

**Korzyści (czemu erasure):**
- Backward compatibility z pre-generic Java (Java 1.4 nie miało generyków).
- Mniejszy footprint (nie generujemy osobnego bytecode per type).

**Trade-off:** Java vs C# — C# generics są reified (zachowane w runtime). C# pozwala `typeof(T)`, `new T()`. Java nie.

**Pułapka rozmowna:** „Generyki w Javie to syntax sugar" — częściowo. Erased w runtime, ale type checking compile-time jest realny. Druga: `List<Integer> + int` autoboxing — boxing daje koszt performance, nie zawsze widoczny w syntax.
**Tagi:** generics, type-erasure, jvm

## Q-OOP-004 [bloom: recall]
**Pytanie:** Co to jest PECS w generykach?
**Modelowa odpowiedź:** **PECS — Producer Extends, Consumer Super.** Reguła wyboru wildcards w generykach: 
- **`<? extends T>`** — bound wildcard, „producer". Czytasz z kolekcji T (lub subclass). Kolekcja "produkuje" T-ki dla ciebie. **Można tylko czytać** (read-only ze strony T-typed).
- **`<? super T>`** — bound wildcard, „consumer". Wstawiasz T (lub subclass) do kolekcji. Kolekcja "konsumuje" twoje T-ki. **Można tylko pisać** (write-only ze strony T).

**Przykład:**
```java
// Copy elements from src to dst
public static <T> void copy(List<? extends T> src, List<? super T> dst) {
    for (T t : src) {
        dst.add(t);
    }
}

// src jest producer (bierzemy z niego T), więc <? extends T>
// dst jest consumer (wstawiamy T), więc <? super T>

List<Number> nums = new ArrayList<>();
List<Integer> ints = List.of(1, 2, 3);
copy(ints, nums);  // OK: ints produkuje Integer (extends Number), nums konsumuje Number (super Integer)
```

**Czemu to tak:**
- `List<? extends Number>` = lista czegoś co jest Number lub subclass. Możesz wziąć Number out (`.get()` zwraca Number). Ale nie możesz wstawić — bo nie wiesz konkretnie co — Integer? Double? `add(new Integer(1))` może wybuchnąć jeśli faktycznie to `List<Double>`.
- `List<? super Number>` = lista czegoś co jest Number lub superclass. Możesz wstawić Number lub subclass (Integer extends Number, więc go nie psuje). Ale `.get()` zwraca `Object` (nie wiadomo czy Number, bo może być `List<Object>`).

**Standardowe API:**
- `Collections.copy(List<? super T>, List<? extends T>)`.
- `Function<? super T, ? extends R>` — przyjmuje T (consumer), zwraca R (producer).
- `Comparator<? super T>` — porównuje T-ki.

**Pułapka rozmowna:** Pomyłka extends/super — łatwo się pogubić. Reguła kciuka: jeśli czytasz z kolekcji → extends. Jeśli piszesz do kolekcji → super. Druga: nie mylić `T extends Number` (bound type parameter) z `<? extends Number>` (wildcard).
**Tagi:** generics, wildcards, pecs

## Q-OOP-005 [bloom: recall]
**Pytanie:** Wymień główne klasy w Java Collections Framework.
**Modelowa odpowiedź:** **List interface** (uporządkowane, duplikaty OK):
- `ArrayList` — backed by array. O(1) random access, O(n) insert middle. Default choice.
- `LinkedList` — doubly-linked list. O(1) insert anywhere (with reference), O(n) random access. Rzadko używane (w 99% ArrayList wygrywa cache locality).
- `Vector` — legacy synchronized ArrayList. Use `Collections.synchronizedList` lub `CopyOnWriteArrayList` zamiast.

**Set interface** (unique elements):
- `HashSet` — backed by HashMap. O(1) add/contains/remove. No order.
- `LinkedHashSet` — HashSet z insertion order.
- `TreeSet` — sorted, backed by red-black tree. O(log n).

**Map interface** (key-value):
- `HashMap` — O(1) typowo. No order. Allows null key/values.
- `LinkedHashMap` — insertion order (lub access order dla LRU).
- `TreeMap` — sorted by key. O(log n).
- `Hashtable` — legacy synchronized. Use `ConcurrentHashMap` zamiast.

**Queue / Deque:**
- `ArrayDeque` — array-backed, O(1) add/remove ends. Modern stack/queue replacement.
- `PriorityQueue` — heap, ordered by priority.
- `LinkedList` też implementuje Deque.

**Concurrent (java.util.concurrent):**
- `ConcurrentHashMap` — thread-safe HashMap, fine-grained locking.
- `CopyOnWriteArrayList` — write copies, read lock-free. Good for read-heavy.
- `BlockingQueue` (`LinkedBlockingQueue`, `ArrayBlockingQueue`) — producer-consumer patterns.
- `ConcurrentSkipListMap` — concurrent sorted map.

**Immutable (Java 9+):**
- `List.of(1,2,3)`, `Set.of()`, `Map.of()` — small immutable collections.
- `Collections.unmodifiableList(...)` — wraps mutable as immutable view.

**Pułapka rozmowna:** „LinkedList do random access" — antipattern, O(n). „Vector w nowym kodzie" — legacy. Druga: `null` w `ConcurrentHashMap` rzuca NPE (różnice z HashMap).
**Tagi:** collections, java

## Q-OOP-006 [bloom: recall]
**Pytanie:** Co to jest immutable obiekt i jakie ma zasady tworzenia?
**Modelowa odpowiedź:** Immutable obiekt to taki, którego stan po stworzeniu nie może się zmienić. **Reguły:**
1. **Wszystkie pola `final`** — żaden setter, żadne re-assignment.
2. **Wszystkie pola `private`** — brak direct access.
3. **Klasa `final`** — brak subklas które mogłyby ominąć immutability (lub konstruktory `private` z factory methods).
4. **No mutable fields** — jeśli pole to `Date`/`List`/`Map`, opakuj w defensive copy at construction i unmodifiable wrapper at access.
5. **No methods modifying state** — wszystkie operacje zwracają nowy obiekt, nie mutują.

**Przykład:**
```java
public final class Money {
    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
    }

    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }

    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException();
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

**Zalety immutability:**
- **Thread-safety free** — nie można corrupt state z wielu wątków.
- **Hash-safe** — hashCode niezmienne, OK jako klucz w HashMap.
- **Cache-friendly** — bezpiecznie share, nie trzeba defensive copy.
- **Łatwiejsze rozumowanie** — argument funkcji nie zmienia się.
- **Łatwiejsze testowanie** — fewer hidden states.

**Wady:**
- Każda zmiana = nowy obiekt → garbage. Dla high-frequency mutations problematic (mitigated by builders, persistent data structures).
- Setters API nie zadziała bezpośrednio (wymagana inna ergonomia).

**Java 14+ records:**
```java
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
    }
}
```
Auto-generated: konstruktor, accessors, equals, hashCode, toString. Immutable by design.

**Pułapka rozmowna:** „Final field daje immutability" — częściowo. Jeśli field to mutable type (np. `final List<String> tags`), to lista ZE ŚRODKA może być modyfikowana. Trzeba defensive copy + unmodifiable view. Druga: `Date` jest mutable historycznie — używaj `LocalDate`/`Instant` (immutable from Java 8+).
**Tagi:** immutability, records, design

## Q-OOP-007 [bloom: recall]
**Pytanie:** Co to jest `Optional<T>` i kiedy używać?
**Modelowa odpowiedź:** `Optional<T>` to wrapper który może zawierać wartość lub być empty. Wprowadzony Java 8. Zastępca `null`-returning methods. **API:**
- `Optional.of(value)` — wrapuje non-null.
- `Optional.ofNullable(value)` — wrapuje value (może być null).
- `Optional.empty()` — empty.
- `.isPresent()` / `.isEmpty()` (Java 11+).
- `.get()` — value, throws if empty (rzadko używać).
- `.orElse(default)` — value lub default.
- `.orElseGet(supplier)` — leniwy default.
- `.orElseThrow(...)`.
- `.map(fn)` — transformuje value (lub zostaje empty).
- `.flatMap(fn)` — chain Optionals.
- `.filter(predicate)`.
- `.ifPresent(consumer)`.

**Przykład:**
```java
public Optional<Product> findByid(Long id) {
    return repository.findById(id);
}

// Użycie:
findById(123)
    .map(Product::getPrice)
    .filter(p -> p.compareTo(BigDecimal.valueOf(100)) > 0)
    .orElseThrow(() -> new ProductNotFoundException(123));
```

**Kiedy używać:**
- ✓ **Return type** for methods that may not have a result (`findByEmail`, `getCustomerOrder`).
- ✓ **API design** — explicit signal że wartość może być nieobecna.

**Kiedy NIE:**
- ✗ **Field type** — `Optional<String> name` wewnątrz klasy. Antypattern. Pole = nullable, nie owrap.
- ✗ **Method parameter** — `void foo(Optional<String> bar)` zamiast `void foo(String bar)` z null check. Antypattern. Caller robi `Optional.ofNullable` jeśli potrzeba.
- ✗ **Collection** — `Optional<List<X>>` zamiast `List<X>` (pusta lista to też signal of „none").
- ✗ **Throwaway** — `if (Optional.ofNullable(x).isPresent())` zamiast `if (x != null)` — zbędna alokacja.

**Pułapka rozmowna:** Optional w polu serializowane przez Jackson — czasem działa dziwnie (default Optional jest serializowane jako `{empty: false}`, nie raw value). `jackson-datatype-jdk8` module rozwiązuje. Druga: `Optional` allocates (jest klasą) — w hot loop nie blast on every iteration.
**Tagi:** optional, null-safety, java8

## Q-OOP-008 [bloom: recall]
**Pytanie:** Co to jest stream w Javie?
**Modelowa odpowiedź:** Stream to abstrakcja sekwencji elementów wspierająca pipeline operacji (Java 8+). Style funkcyjny — operacje zwracają nowy stream, kompozycja przez chaining. **Operacje:**
- **Intermediate** (lazy, zwracają Stream): `filter`, `map`, `flatMap`, `distinct`, `sorted`, `peek`, `limit`, `skip`.
- **Terminal** (eager, materialize): `collect`, `forEach`, `reduce`, `count`, `findFirst`, `findAny`, `allMatch`, `anyMatch`, `noneMatch`, `min`, `max`, `toArray`.

**Przykład:**
```java
List<Product> products = ...;
Map<String, BigDecimal> totalByCountry = products.stream()
    .filter(p -> p.getPrice().compareTo(BigDecimal.ZERO) > 0)
    .collect(Collectors.groupingBy(
        Product::getCountry,
        Collectors.reducing(BigDecimal.ZERO, Product::getPrice, BigDecimal::add)
    ));
```

**Lazy evaluation:** intermediate ops nie execute się dopóki terminal op nie zostanie wywołane. Optimization: filter+findFirst zatrzymuje stream przy pierwszym match.

**Parallel streams:** `stream.parallel().filter(...)` — auto-parallelizuje na ForkJoinPool.commonPool(). Use carefully — overhead small datasets, race conditions w side-effecting code.

**Stream sources:**
- `collection.stream()` / `.parallelStream()`.
- `Stream.of(1, 2, 3)`.
- `Stream.iterate(0, i -> i+1)` — nieskończony.
- `Files.lines(Path)` — file lines as stream.
- `IntStream.range(0, 100)`.

**Collectors:**
- `Collectors.toList()`, `toSet()`, `toMap()`.
- `groupingBy`, `partitioningBy`.
- `counting`, `summingInt`, `averagingDouble`.
- `joining(delimiter)`.

**Plusy:**
- Czytelne functional pipelines.
- Easy parallelization.
- Lazy evaluation.

**Minusy:**
- Czasem trudniejsze do debugowania niż loop.
- Performance: stream overhead per call (lambda allocation, internal iteration). Dla micro-loops vanilla for jest szybszy.
- No early break (chyba że findFirst/anyMatch).

**Pułapka rozmowna:** „Parallel stream zawsze szybszy" — false. ForkJoin overhead, contention, ordering issues. Use parallel tylko gdy: data > ~10k elements, computation is heavy, no shared mutable state. Druga: `forEach` z stateful side effect — może łamać in parallel.
**Tagi:** streams, java8, fp

---

## Q-OOP-009 [bloom: understand]
**Pytanie:** Wytłumacz różnicę między `synchronized`, `volatile` i `Atomic*`.
**Modelowa odpowiedź:** **`synchronized`** — blok kodu wykonywany atomowo per monitor (object). Jeden wątek może być wewnątrz, inni czekają. Daje:
- **Mutual exclusion** (atomicity dla compound operations).
- **Happens-before** (visibility — zmiany przed `synchronized` block są widoczne dla następnego wątku wchodzącego w synchronized block na tym samym obiekcie).

**`volatile`** — modyfikator pola. Daje:
- **Visibility** — zapis do pola od razu widoczny dla innych wątków (no caching).
- **Happens-before** dla pojedynczego pola — zapis i odczyt.
- **NIE daje atomicity** dla compound operations: `volatile int counter; counter++` to read-modify-write, race condition possible.

**`Atomic*`** (`AtomicInteger`, `AtomicLong`, `AtomicReference`) — daje:
- Atomic compound operations: `incrementAndGet`, `compareAndSet`, `getAndAdd`.
- Implementacja przez CAS (Compare-And-Swap) — lock-free, faster niż synchronized.
- Visibility (jak volatile).

**Decision tree:**
- Single read/write atomic? Use `volatile`.
- Compound op (counter++, check-then-act)? Use `Atomic*` lub `synchronized`.
- Multiple fields update atomically? `synchronized` lub `Lock`.
- Complex state? Use higher-level abstractions: `ConcurrentHashMap`, `BlockingQueue`, `CompletableFuture`.

**Przykład pricing context:**
```java
// Counter cache hits
private final AtomicLong cacheHits = new AtomicLong();

void recordHit() { cacheHits.incrementAndGet(); }
long getHits() { return cacheHits.get(); }

// volatile dla flag
private volatile boolean shutdownRequested = false;

void shutdown() { shutdownRequested = true; }

// W loop worker:
while (!shutdownRequested) { processNext(); }
```

**Pułapka rozmowna:** `synchronized(this)` — zewnętrzny code może też synchronizować na tej samej instancji → unintended contention. Lepiej private lock object: `private final Object lock = new Object(); synchronized(lock) { }`. Druga: `volatile` na reference — zmiana referencji widoczna, ale modyfikacje OBIEKTU pod referencją wymagają osobnej synchronization.
**Tagi:** concurrency, synchronized, volatile, atomic

## Q-OOP-010 [bloom: understand]
**Pytanie:** Wytłumacz pattern Singleton i jego pułapki.
**Modelowa odpowiedź:** Singleton — pattern zapewniający że klasa ma tylko jedną instancję, dostępną globalnie. **Klasyczne implementacje w Javie:**

**1. Eager:**
```java
public class Cache {
    private static final Cache INSTANCE = new Cache();
    private Cache() {}
    public static Cache getInstance() { return INSTANCE; }
}
```
Plus: thread-safe, simple. Minus: instancja tworzona przy class load (nawet jeśli nie używana).

**2. Lazy with synchronization:**
```java
public class Cache {
    private static Cache instance;
    private Cache() {}
    public static synchronized Cache getInstance() {
        if (instance == null) instance = new Cache();
        return instance;
    }
}
```
Plus: lazy. Minus: synchronized na każde access — slow.

**3. Double-checked locking (Java 5+):**
```java
public class Cache {
    private static volatile Cache instance;
    private Cache() {}
    public static Cache getInstance() {
        if (instance == null) {
            synchronized (Cache.class) {
                if (instance == null) instance = new Cache();
            }
        }
        return instance;
    }
}
```
Volatile must — bez tego JVM może reorder operations, expose partially-constructed object.

**4. Holder idiom (preferred):**
```java
public class Cache {
    private Cache() {}
    private static class Holder {
        static final Cache INSTANCE = new Cache();
    }
    public static Cache getInstance() { return Holder.INSTANCE; }
}
```
Plus: lazy (Holder loads tylko przy first call), thread-safe (JVM gwarantuje class init), no synchronized.

**5. Enum:**
```java
public enum Cache {
    INSTANCE;
    public void doStuff() {}
}
```
Plus: thread-safe by JVM, serialization-safe, prevents reflection abuse. Minus: nie da się dziedziczyć.

**Pułapki Singletona:**
1. **Hidden dependencies** — `Cache.getInstance()` w środku metody. Test wymaga reset state. Antipattern w testowaniu.
2. **Global state** — modifikacja w jednym miejscu wpływa na cały system. Hard to reason.
3. **Thread-safety must be explicit** — chyba że sam state jest immutable lub używasz concurrent collections.
4. **Reflection abuse** — w 1-3 implementacji można wywołać prywatny konstruktor reflexją. Enum chroni.
5. **Serialization** — zwykły Singleton po deserialize tworzy nową instancję. Trzeba `readResolve()`. Enum chroni.

**Modern alternative — DI:** Spring `@Component` z scope singleton (default) daje single instance, ale managed by container, easily mockable in tests. **Preferred over manual Singleton w 99% przypadków.**

**Pułapka rozmowna:** „Singleton to OK pattern" — w Spring rzadziej, w plain Java często anty. Test pain, hidden coupling. Druga: „static fields = Singleton" — częściowo. Cała klasa z static jest podobnym antywzorcem (utility class — czasem OK, czasem ukrywa coupling).
**Tagi:** patterns, singleton, design

## Q-OOP-011 [bloom: understand]
**Pytanie:** Co to jest CompletableFuture i jak go używać?
**Modelowa odpowiedź:** `CompletableFuture<T>` (Java 8+) — async computation z chaining, error handling, composition. Następca `Future<T>` (który był prosty: get/cancel) z dużo bogatszym API.

**Tworzenie:**
```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // long computation
    return "result";
}); // używa ForkJoinPool.commonPool() default

CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> compute(), executor); // custom executor
```

**Chaining (transformations):**
```java
CompletableFuture<Integer> result = CompletableFuture.supplyAsync(() -> "hello")
    .thenApply(String::length)        // CF<Integer>
    .thenApply(len -> len * 2)
    .exceptionally(ex -> -1);          // fallback on error
```

**Combining:**
```java
CompletableFuture<String> nameF = fetchName();
CompletableFuture<Integer> ageF = fetchAge();

CompletableFuture<String> combined = nameF.thenCombine(ageF, 
    (name, age) -> name + " " + age);

// Or run multiple in parallel, await all:
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
all.join(); // wait for all, throws if any failed
```

**Error handling:**
- `exceptionally(fn)` — fallback value if exception.
- `handle((result, ex) -> ...)` — handles both success and failure.
- `whenComplete((result, ex) -> ...)` — side-effect, doesn't transform.

**Cancellation / completion:**
- `complete(value)` — manually complete future with value.
- `completeExceptionally(ex)` — complete with error.
- `cancel(true)` — cancel.

**Pricing example — fetch product + price + inventory in parallel:**
```java
CompletableFuture<Product> productF = CompletableFuture.supplyAsync(() -> productService.get(id));
CompletableFuture<Price> priceF = CompletableFuture.supplyAsync(() -> priceService.get(id, country));
CompletableFuture<Integer> stockF = CompletableFuture.supplyAsync(() -> stockService.get(id));

CompletableFuture<ProductView> viewF = CompletableFuture.allOf(productF, priceF, stockF)
    .thenApply(ignored -> new ProductView(
        productF.join(), priceF.join(), stockF.join()
    ));

ProductView view = viewF.get(5, TimeUnit.SECONDS); // timeout
```

3 calls in parallel zamiast sequential — speedup do 3x.

**Pitfalls:**
- **`get()` blocks** — w sync code OK, w reactive context (np. WebFlux) zepsuje thread.
- **Default ForkJoinPool** — common pool może być wyczerpany przez inne async tasks. Dedicated executor dla I/O.
- **`thenApply` runs on completing thread** — może być wątek z executor, może być calling thread (dla synchronously completed future). Use `thenApplyAsync(fn, executor)` dla determinism.
- **No automatic cancellation propagation** — anulowanie głównej future nie anuluje upstream tasks.

**Pułapka rozmowna:** „CompletableFuture jest reactive" — częściowo. Ma async chaining, ale nie jest reactive streams (no backpressure, no Flux/Flowable semantics). Druga: `.get()` checked exception (`InterruptedException`, `ExecutionException`) — zazwyczaj wrap w runtime.
**Tagi:** concurrency, completablefuture, async

## Q-OOP-012 [bloom: understand]
**Pytanie:** Wyjaśnij different design patterns: Factory vs Builder.
**Modelowa odpowiedź:** **Factory pattern** — encapsulate object creation. Caller dostaje obiekt nie wiedząc dokładnie jakim konstruktorem był stworzony, ani konkretną klasą. **Warianty:**

1. **Simple Factory (static method):**
```java
public class PaymentGatewayFactory {
    public static PaymentGateway create(String type) {
        return switch (type) {
            case "stripe" -> new StripeGateway();
            case "paypal" -> new PayPalGateway();
            default -> throw new IllegalArgumentException();
        };
    }
}
```

2. **Factory Method (subclass decides):**
```java
public abstract class DialogBuilder {
    public abstract Button createButton();
    public Dialog buildDialog() { 
        Dialog d = new Dialog();
        d.addButton(createButton()); // factory method call
        return d;
    }
}
class WindowsDialogBuilder extends DialogBuilder {
    public Button createButton() { return new WindowsButton(); }
}
```

3. **Abstract Factory** — factory of factories, family of related products.

**Builder pattern** — step-by-step construction of complex objects, often with many optional parameters. Daje fluent API.

```java
public class OrderRequest {
    private final Long customerId;
    private final List<Item> items;
    private final String currency;
    private final BigDecimal discount;
    private final String notes;
    
    private OrderRequest(Builder b) {
        this.customerId = b.customerId;
        this.items = b.items;
        // ...
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private Long customerId;
        private List<Item> items = new ArrayList<>();
        private String currency = "PLN"; // default
        private BigDecimal discount = BigDecimal.ZERO;
        private String notes;
        
        public Builder customerId(Long id) { this.customerId = id; return this; }
        public Builder addItem(Item item) { this.items.add(item); return this; }
        public Builder currency(String c) { this.currency = c; return this; }
        public Builder discount(BigDecimal d) { this.discount = d; return this; }
        public Builder notes(String n) { this.notes = n; return this; }
        
        public OrderRequest build() {
            // validation
            if (customerId == null) throw new IllegalStateException();
            if (items.isEmpty()) throw new IllegalStateException();
            return new OrderRequest(this);
        }
    }
}

// Usage:
OrderRequest req = OrderRequest.builder()
    .customerId(123L)
    .addItem(new Item(1, 2))
    .addItem(new Item(2, 1))
    .discount(BigDecimal.valueOf(0.1))
    .notes("Express delivery")
    .build();
```

**Plusy Builder:**
- Czytelny call site (named-param-like).
- Defaults handled w builderze.
- Validation in `build()` (fail-fast).
- Immutable result.

**Lombok shortcut:** `@Builder` annotation generuje builder.

**Decision:**
- Few mandatory params, no optional → constructor.
- Many optional params (telescoping constructor antipattern) → Builder.
- Conditional creation logic, hidden type → Factory.
- Both patterns can be combined.

**Pułapka rozmowna:** „Builder to over-engineering" — dla 2 pól tak. Dla 5+ z optional — natural fit. Druga: mutable builder po `build()` — najlepiej zostawić builder usable (no reset), ale obiekt niemodifiable.
**Tagi:** patterns, factory, builder, design

## Q-OOP-013 [bloom: understand]
**Pytanie:** Co to jest checked vs unchecked exception i kiedy które?
**Modelowa odpowiedź:** **Checked exceptions** (extend `Exception` ale nie `RuntimeException`):
- Compiler enforces handling: try/catch lub `throws` declaration.
- Klasyczne: `IOException`, `SQLException`, `InterruptedException`.
- Filozofia: caller MUSI być świadomy że ta operacja może failować.

**Unchecked exceptions** (extend `RuntimeException`):
- Compiler nie wymusza obsługi.
- Klasyczne: `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`, `ConcurrentModificationException`.
- Filozofia: programming errors lub unrecoverable state.

**Errors** (extend `Error`):
- `OutOfMemoryError`, `StackOverflowError`. Nie obsługujesz, są fatal.

**Kiedy checked (debata trwa):**
- Pierwotny design Java: użyteczne dla recoverable błędów (IO problem — można retry).
- Modern Java community często unika: zaśmieca code z try/catch wszędzie, lambda nie pozwala throw checked w functional interfaces.
- Spring i większość modern frameworks używa unchecked.

**Best practices (unpopular opinion z Spring world):**
- **Unchecked exceptions** dla większości business errors. `OrderNotFoundException extends RuntimeException`.
- **Checked exceptions tylko gdy** caller MUSI handle (rzadkie).
- **Custom exception hierarchy:**
  ```java
  public class PricingException extends RuntimeException { }
  public class ProductNotFoundException extends PricingException { }
  public class InvalidPriceException extends PricingException { }
  ```
- **Wrap checked exceptions:** `try { ... } catch (IOException e) { throw new MyAppException(e); }`.

**Try-with-resources (Java 7+)** — auto-close resources implementing AutoCloseable:
```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    // use
} // auto closed in reverse order, exceptions suppressed/chained
```
Replaces manual try/finally with close in finally — much cleaner.

**Exception chaining:**
```java
try {
    // ...
} catch (LowLevelException e) {
    throw new HighLevelException("Could not process", e); // 'cause'
}
```
Stack trace pokazuje cały łańcuch.

**Pułapka rozmowna:** **Catching `Exception` lub `Throwable` to antipattern** w produkcyjnym kodzie. Zawsze konkret — `catch (IOException e)`. Druga: **swallow exceptions (`catch { ... }` empty)** — bug factory. Co najmniej `log.warn("...", e);`. Trzecia: `throws Exception` w sygnaturze metody — tells nothing, bad API.
**Tagi:** exceptions, error-handling

## Q-OOP-014 [bloom: understand]
**Pytanie:** Co to są records i sealed classes (Java 14+ / 17+)?
**Modelowa odpowiedź:** **Records (Java 16+)** — concise syntax dla immutable data classes. Auto-generuje:
- Konstruktor canonical (z polami w kolejności deklaracji).
- Akcesory (`name()`, nie `getName()`).
- `equals()`, `hashCode()`, `toString()`.
- Immutable (pola final, brak setterów).

```java
public record Money(BigDecimal amount, String currency) { }

// Equivalent of ~50 lines of boilerplate
```

**Custom validation:**
```java
public record Money(BigDecimal amount, String currency) {
    public Money {  // compact constructor
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.signum() < 0) throw new IllegalArgumentException();
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException();
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

**Records can:**
- Have additional methods.
- Implement interfaces.
- Have static fields/methods.
- Have nested types.

**Records cannot:**
- Extend other classes (implicitly extend `Record`).
- Have additional instance fields beyond components.
- Be abstract.

**Sealed classes (Java 17)** — restrict który klasy mogą rozszerzać. Do hierarchii zamkniętej.

```java
public sealed interface PaymentMethod 
    permits CreditCard, BankTransfer, PayPal { }

public final class CreditCard implements PaymentMethod { }
public final class BankTransfer implements PaymentMethod { }
public final class PayPal implements PaymentMethod { }
```

`permits` clause określa który klasy mogą dziedziczyć. Subclasses muszą być `final`, `sealed` lub `non-sealed`.

**Plus:** exhaustive pattern matching możliwe (compiler wie że są tylko 3 types).

**Pattern matching for switch (Java 21):**
```java
double process(PaymentMethod pm) {
    return switch (pm) {
        case CreditCard c -> c.getFee();
        case BankTransfer b -> 0;
        case PayPal p -> p.getCommission();
        // No default needed — sealed type, compiler knows all cases
    };
}
```

**Records + sealed = algebraic data types** in Java. Enables ADTs / sum types like in Scala/Haskell.

**Pricing example:**
```java
public sealed interface PriceModifier 
    permits PercentDiscount, FixedDiscount, BogoOffer { }

public record PercentDiscount(BigDecimal pct) implements PriceModifier { }
public record FixedDiscount(BigDecimal amount) implements PriceModifier { }
public record BogoOffer(int buyQty, int getQty) implements PriceModifier { }

BigDecimal apply(BigDecimal price, PriceModifier mod) {
    return switch (mod) {
        case PercentDiscount p -> price.multiply(BigDecimal.ONE.subtract(p.pct()));
        case FixedDiscount f -> price.subtract(f.amount());
        case BogoOffer b -> // ...
    };
}
```

**Pułapka rozmowna:** „Record = klasa z lombok @Data" — częściowo. Record ma immutability built-in, lombok @Data generuje setters. Druga: rekord nie zastępuje builders gdy masz wiele optional params — record konstruktor jest fixed.
**Tagi:** records, sealed, java17, java21

## Q-OOP-015 [bloom: understand]
**Pytanie:** Co to są virtual threads (Java 21)?
**Modelowa odpowiedź:** Virtual threads (Project Loom, GA Java 21) — lightweight threads managed by JVM zamiast OS. Cel: tysiące thousands threads bez problemów scaling.

**Klasyczne (platform) threads:** mapowane 1:1 na OS threads. ~1 MB stack each. JVM może mieć tysiące, nie miliony.

**Virtual threads:** small (~few KB), JVM schedules na carrier (platform) threads. Możesz mieć 1M virtual threads na laptopie.

**Tworzenie:**
```java
// Old way:
Thread t = new Thread(() -> doWork());
t.start();

// Virtual thread:
Thread.startVirtualThread(() -> doWork());

// Executor:
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> doWork());
    }
} // close blocks until all complete
```

**Use case:** I/O-bound apps. Klasyczny serwer Java z thread-per-request: pool ~200 threads, beyond that thread starvation. Z virtual threads: thread-per-request scaling do hundreds of thousands concurrent requests, bo gdy thread czeka na DB call — virtual unmounts z carrier, carrier robi inną pracę. Massive concurrency.

**NOT for:** CPU-bound work. Virtual thread nie przyspieszy computation — wciąż jeden thread = one CPU. Dla compute use parallel streams / ForkJoinPool.

**Compatibility:** existing `Thread` API works. `BlockingQueue`, `synchronized`, `Lock` — działają. Caveat: `synchronized` block pinuje virtual thread do carrier (problematic). Workaround: use `ReentrantLock` zamiast `synchronized` w critical sections gdy używasz virtual threads.

**Spring Boot 3.2+ wspiera:** `spring.threads.virtual.enabled=true` — Tomcat/Jetty używa virtual thread per request.

**Pricing impact:** I/O-heavy services (call DB, call external APIs, wait for responses) — massive scaling improvement. Calculation-heavy — bez różnicy.

**Pułapka rozmowna:** „Virtual thread = goroutine" — koncepcyjnie podobne. „Virtual thread zastąpi reactive (WebFlux)?" — częściowo. Reactive ma backpressure i composability. Virtual threads ma simpler programming model (sync code, scaled). Wybór zależy od preference i ecosystem.
**Tagi:** virtual-threads, java21, concurrency

## Q-OOP-016 [bloom: understand]
**Pytanie:** Co to jest Strategy pattern i jak go zastosować w pricingu?
**Modelowa odpowiedź:** Strategy pattern — encapsulate algorithms, allow swapping at runtime. Klasyczna definicja: family of algorithms, each encapsulated in own class, klient wybiera/przekazuje który.

**Pricing example — różne strategie kalkulacji ceny per kraj:**
```java
public interface PricingStrategy {
    BigDecimal calculate(Product p, Customer c, Context ctx);
}

public class CostPlusPricing implements PricingStrategy {
    @Override
    public BigDecimal calculate(Product p, Customer c, Context ctx) {
        return p.getCost().multiply(BigDecimal.valueOf(1.3)); // 30% margin
    }
}

public class DynamicPricing implements PricingStrategy {
    @Override
    public BigDecimal calculate(Product p, Customer c, Context ctx) {
        BigDecimal base = p.getBasePrice();
        if (ctx.isHighDemand()) base = base.multiply(BigDecimal.valueOf(1.1));
        if (c.getTier() == Tier.GOLD) base = base.multiply(BigDecimal.valueOf(0.9));
        return base;
    }
}

public class PromotionalPricing implements PricingStrategy {
    @Override
    public BigDecimal calculate(Product p, Customer c, Context ctx) {
        return p.getBasePrice().multiply(BigDecimal.valueOf(0.7)); // 30% off
    }
}

// Service używa strategy
public class PriceService {
    private PricingStrategy strategy; // injected

    public void setStrategy(PricingStrategy s) { this.strategy = s; }
    public BigDecimal price(Product p, Customer c, Context ctx) { 
        return strategy.calculate(p, c, ctx); 
    }
}

// Lub strategy resolver (chooses based on context):
public class PricingStrategyResolver {
    private final Map<String, PricingStrategy> strategies;

    public PricingStrategy resolve(Context ctx) {
        if (ctx.isPromoActive()) return strategies.get("promotional");
        if (ctx.isHighDemand()) return strategies.get("dynamic");
        return strategies.get("cost-plus");
    }
}
```

**With Spring DI:**
```java
@Service
public class PricingStrategyResolver {
    private final Map<String, PricingStrategy> strategies;
    
    public PricingStrategyResolver(List<PricingStrategy> all) {
        this.strategies = all.stream().collect(toMap(s -> s.getClass().getSimpleName(), s -> s));
    }
}
```

Spring auto-wires wszystkie beans implementujące `PricingStrategy`. Resolver mapuje po nazwie / type.

**Plusy Strategy:**
- **Open/Closed** — nowy algorytm = nowa klasa, no modify existing.
- **Testable** — strategy unit-test independent.
- **Runtime flexibility** — switch behavior bez restart.
- **Single Responsibility** — każda strategia jedno robi.

**Minusy:**
- More classes / interfaces.
- Caller musi wiedzieć że są different strategies.
- Klient może źle wybrać.

**Lambdas as strategies (modern Java):**
```java
@FunctionalInterface
public interface PricingStrategy {
    BigDecimal calculate(Product p, Customer c, Context ctx);
}

PricingStrategy costPlus = (p, c, ctx) -> p.getCost().multiply(BigDecimal.valueOf(1.3));
PricingStrategy dynamic = (p, c, ctx) -> { /* ... */ };

priceService.setStrategy(costPlus);
```
Dla simple strategies — mniej boilerplate. Dla complex — full classes lepsze (state, dependencies).

**Pułapka rozmowna:** „Strategy ≠ Polymorphism" — Strategy USES polymorphism. Każda subklasa to konkretne polymorphic implementation. Drugi: nadmiar strategies dla prostych if-else — over-engineering. Dla 2 wariantów może być za dużo.
**Tagi:** strategy, patterns, pricing, oop

---

## Q-OOP-017 [bloom: apply]
**Pytanie:** Zaimplementuj klasę `OrderTotal` immutable z polami `subtotal: BigDecimal`, `tax: BigDecimal`, `discount: BigDecimal`. Method `total()` zwraca `subtotal + tax - discount`. Method `withDiscount(BigDecimal newDiscount)` zwraca nowy `OrderTotal`.
**Modelowa odpowiedź:**
```java
import java.math.BigDecimal;
import java.util.Objects;

public final class OrderTotal {
    private final BigDecimal subtotal;
    private final BigDecimal tax;
    private final BigDecimal discount;

    public OrderTotal(BigDecimal subtotal, BigDecimal tax, BigDecimal discount) {
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
        this.tax = Objects.requireNonNull(tax, "tax");
        this.discount = Objects.requireNonNull(discount, "discount");
        if (subtotal.signum() < 0) throw new IllegalArgumentException("subtotal must be >= 0");
        if (tax.signum() < 0) throw new IllegalArgumentException("tax must be >= 0");
        if (discount.signum() < 0) throw new IllegalArgumentException("discount must be >= 0");
    }

    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getDiscount() { return discount; }

    public BigDecimal total() {
        return subtotal.add(tax).subtract(discount);
    }

    public OrderTotal withDiscount(BigDecimal newDiscount) {
        return new OrderTotal(subtotal, tax, newDiscount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderTotal that)) return false;
        return subtotal.compareTo(that.subtotal) == 0 
            && tax.compareTo(that.tax) == 0 
            && discount.compareTo(that.discount) == 0;
    }

    @Override
    public int hashCode() {
        // BigDecimal.hashCode is sensitive to scale — strip
        return Objects.hash(subtotal.stripTrailingZeros(), tax.stripTrailingZeros(), discount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "OrderTotal{subtotal=" + subtotal + ", tax=" + tax + ", discount=" + discount + ", total=" + total() + "}";
    }
}
```

**Wersja record (Java 16+):**
```java
public record OrderTotal(BigDecimal subtotal, BigDecimal tax, BigDecimal discount) {
    public OrderTotal {
        Objects.requireNonNull(subtotal);
        Objects.requireNonNull(tax);
        Objects.requireNonNull(discount);
        if (subtotal.signum() < 0 || tax.signum() < 0 || discount.signum() < 0)
            throw new IllegalArgumentException("amounts must be non-negative");
    }
    
    public BigDecimal total() { return subtotal.add(tax).subtract(discount); }
    
    public OrderTotal withDiscount(BigDecimal newDiscount) { 
        return new OrderTotal(subtotal, tax, newDiscount); 
    }
}
```
Record auto-generuje equals/hashCode (BUT default uses BigDecimal.equals which is scale-sensitive — może być nieporządanymi behaviour, override jeśli ma znaczenie).

**Kluczowe punkty:**
- `final` klasa, `final` pola.
- Validation w konstruktorze.
- `withX` zamiast settera — tworzy nowy obiekt.
- `equals` używa `compareTo` żeby ignore scale (`new BigDecimal("1.0") != new BigDecimal("1.00")` w `equals`, ale `compareTo == 0`).
- `Objects.hash` z stripTrailingZeros żeby spójne z equals.
- `total()` calculated, nie cached (immutable, więc OK; cache miałby sens tylko jeśli compute jest expensive — tu nie).

**Pułapka rozmowna:** Record's auto equals/hashCode używają BigDecimal.equals — jeśli przekażesz `new BigDecimal("100.00")` vs `new BigDecimal("100")`, są nie-equal. W pricing zazwyczaj chcesz value equality — override or normalize w konstruktorze. Druga: `add`, `subtract` na BigDecimal — bez `MathContext` może produkować duże scale (np. `1.0/3 = 0.3333333...`); dla pricing `setScale(2, RoundingMode.HALF_UP)` na końcu.
**Tagi:** immutability, records, bigdecimal, pricing

## Q-OOP-018 [bloom: apply]
**Pytanie:** Napisz `Comparator<Product>` który sortuje produkty: najpierw aktywne potem nieaktywne, w obrębie każdej grupy malejąco po cenie.
**Modelowa odpowiedź:**
```java
import java.util.*;
import java.util.stream.*;

class Product {
    String name;
    BigDecimal price;
    boolean active;
    // ctor, getters
}

// Wariant 1 — chained Comparator (Java 8+ idiom)
Comparator<Product> comparator = Comparator
    .comparing((Product p) -> !p.isActive())  // false (active) before true (inactive)
    .thenComparing(Comparator.comparing(Product::getPrice).reversed());

products.sort(comparator);

// Wariant 2 — explicit comparison
Comparator<Product> comp2 = (a, b) -> {
    if (a.isActive() != b.isActive()) {
        return a.isActive() ? -1 : 1; // active first
    }
    return b.getPrice().compareTo(a.getPrice()); // descending
};

// Z Streams API:
List<Product> sorted = products.stream()
    .sorted(comparator)
    .collect(Collectors.toList());

// Lub Java 16+:
List<Product> sorted2 = products.stream().sorted(comparator).toList();
```

**Komponowanie comparators:**
- `Comparator.comparing(keyExtractor)` — sort by extracted key.
- `.reversed()` — odwraca kierunek.
- `.thenComparing(...)` — secondary sort gdy primary equal.
- `Comparator.naturalOrder()`, `reverseOrder()`.
- `Comparator.nullsFirst(comp)`, `nullsLast(comp)` — null handling.

**Bardziej rozbudowane:**
```java
Comparator<Product> complex = Comparator
    .comparing(Product::getCategory)              // primary: category alfabetycznie
    .thenComparing((Product p) -> !p.isActive()) // secondary: active first
    .thenComparing(Product::getPrice, Comparator.reverseOrder()) // tertiary: price desc
    .thenComparing(Product::getName, String.CASE_INSENSITIVE_ORDER); // tie-break: name
```

**Comparable vs Comparator:**
- `Comparable<T>` — natural order, `compareTo` na klasie. Single ordering, default.
- `Comparator<T>` — external ordering, multiple possible. Bardziej elastyczny.

**Pricing context:** Comparator dla różnych views — admin chce sort by status+price, customer chce sort by price asc, reporting chce sort by total revenue. Comparators są reusable, named.

**Pułapka rozmowna:** `Comparator.comparing(p -> !p.isActive())` — `boolean::!` daje `false < true`, więc `false` (active) idzie pierwszy. Subtelne, sprawdź. Druga: type inference w Java z chained comparator czasem wymaga explicit `(Product p)` cast w pierwszym `comparing` żeby kompilator wiedział.
**Tagi:** comparator, sorting, java8, fp

## Q-OOP-019 [bloom: apply]
**Pytanie:** Zaimplementuj custom thread-safe cache (klucz → wartość) z TTL 5 minut. Hot path: `get(key, supplier)` zwraca cached lub computes.
**Modelowa odpowiedź:**
```java
import java.util.concurrent.*;
import java.time.*;
import java.util.function.Supplier;

public class TtlCache<K, V> {
    private static class Entry<V> {
        final V value;
        final Instant expiresAt;
        Entry(V value, Instant expiresAt) { this.value = value; this.expiresAt = expiresAt; }
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    private final ConcurrentHashMap<K, Entry<V>> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TtlCache(Duration ttl) {
        this.ttl = ttl;
    }

    public V get(K key, Supplier<V> supplier) {
        Entry<V> entry = cache.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) return existing;
            V newValue = supplier.get();
            return new Entry<>(newValue, Instant.now().plus(ttl));
        });
        return entry.value;
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public void cleanupExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}

// Usage:
TtlCache<String, BigDecimal> priceCache = new TtlCache<>(Duration.ofMinutes(5));

BigDecimal price = priceCache.get("product-123-PL", () -> 
    pricingService.calculatePrice(123L, "PL"));
```

**Kluczowe decyzje:**
- `ConcurrentHashMap` — thread-safe, fine-grained locking.
- `compute` — atomic check-and-update. Bez tego: race condition (dwa wątki widzą expired, oba supplier.get()).
- `Entry` immutable — value + expiration.
- `Instant.now()` — UTC, monotonic-ish (nie idealny dla precise timing — `System.nanoTime` for that).

**Limitations:**
- **No size limit** — jeśli growing key space, pamięć rośnie. Dorzuć max-size + LRU eviction.
- **No async refresh** — gdy wygasł, supplier zawiesza wątek na recompute. Dla wolnego suppliers — refresh-ahead pattern.
- **No metrics** — hit rate, miss rate, eviction rate. Dorzuć counters.

**Production-grade alternatives:**
- **Caffeine** — wszystko z box: TTL, size limit, refresh-ahead, statistics, async. Java cache de facto.
  ```java
  Cache<String, BigDecimal> cache = Caffeine.newBuilder()
      .maximumSize(10_000)
      .expireAfterWrite(Duration.ofMinutes(5))
      .recordStats()
      .build();
  ```
- **Guava Cache** — older, similar API, mniej rozwijany niż Caffeine.
- **Redis** — distributed cache.

**Decyzja:** custom dla nauki / very specific needs. Caffeine dla 99% production. Caffeine ma tylko ~150 KB, mniej dependencies headache.

**Pułapka rozmowna:** `ConcurrentHashMap.compute` blokuje per-key. Jeśli supplier jest slow + wiele wątków pyta o ten sam key — pierwszy lock-uje, reszta czeka. To jest `loading cache pattern` — Caffeine ma `LoadingCache` z built-in deduplication concurrent loads.
**Tagi:** concurrency, cache, ttl, pricing

## Q-OOP-020 [bloom: apply]
**Pytanie:** Napisz Strategy pattern dla calc commission salesman: `Bronze` 5%, `Silver` 7% z bonusem 100zł jeśli sales > 10000, `Gold` 10% z mnożnikiem 1.2 jeśli sales > 50000.
**Modelowa odpowiedź:**
```java
import java.math.*;

public interface CommissionStrategy {
    BigDecimal calculate(BigDecimal sales);
}

public class BronzeCommission implements CommissionStrategy {
    private static final BigDecimal RATE = new BigDecimal("0.05");
    
    @Override
    public BigDecimal calculate(BigDecimal sales) {
        return sales.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
    }
}

public class SilverCommission implements CommissionStrategy {
    private static final BigDecimal RATE = new BigDecimal("0.07");
    private static final BigDecimal THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal BONUS = new BigDecimal("100");
    
    @Override
    public BigDecimal calculate(BigDecimal sales) {
        BigDecimal commission = sales.multiply(RATE);
        if (sales.compareTo(THRESHOLD) > 0) {
            commission = commission.add(BONUS);
        }
        return commission.setScale(2, RoundingMode.HALF_UP);
    }
}

public class GoldCommission implements CommissionStrategy {
    private static final BigDecimal RATE = new BigDecimal("0.10");
    private static final BigDecimal THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal MULTIPLIER = new BigDecimal("1.2");
    
    @Override
    public BigDecimal calculate(BigDecimal sales) {
        BigDecimal commission = sales.multiply(RATE);
        if (sales.compareTo(THRESHOLD) > 0) {
            commission = commission.multiply(MULTIPLIER);
        }
        return commission.setScale(2, RoundingMode.HALF_UP);
    }
}

// Resolver / Factory
public class CommissionStrategyResolver {
    private final Map<String, CommissionStrategy> strategies = Map.of(
        "BRONZE", new BronzeCommission(),
        "SILVER", new SilverCommission(),
        "GOLD", new GoldCommission()
    );
    
    public CommissionStrategy forTier(String tier) {
        CommissionStrategy s = strategies.get(tier.toUpperCase());
        if (s == null) throw new IllegalArgumentException("Unknown tier: " + tier);
        return s;
    }
}

// Usage:
CommissionStrategyResolver resolver = new CommissionStrategyResolver();
BigDecimal commission = resolver.forTier("GOLD").calculate(new BigDecimal("60000"));
// 60000 * 0.10 * 1.2 = 7200.00
```

**Test:**
```java
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CommissionTest {
    @Test
    void bronze_5_percent() {
        assertEquals(new BigDecimal("500.00"), 
            new BronzeCommission().calculate(new BigDecimal("10000")));
    }
    
    @Test
    void silver_with_bonus() {
        // 15000 * 0.07 + 100 = 1150
        assertEquals(new BigDecimal("1150.00"),
            new SilverCommission().calculate(new BigDecimal("15000")));
    }
    
    @Test
    void silver_no_bonus_at_threshold() {
        // 10000 (== threshold, NOT >) so no bonus: 10000 * 0.07 = 700
        assertEquals(new BigDecimal("700.00"),
            new SilverCommission().calculate(new BigDecimal("10000")));
    }
    
    @Test
    void gold_with_multiplier() {
        // 60000 * 0.10 * 1.2 = 7200
        assertEquals(new BigDecimal("7200.00"),
            new GoldCommission().calculate(new BigDecimal("60000")));
    }
}
```

**Z lambdas (functional approach):**
```java
@FunctionalInterface
public interface CommissionStrategy {
    BigDecimal calculate(BigDecimal sales);
}

CommissionStrategy bronze = sales -> 
    sales.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);

CommissionStrategy silver = sales -> {
    BigDecimal c = sales.multiply(new BigDecimal("0.07"));
    if (sales.compareTo(new BigDecimal("10000")) > 0) c = c.add(new BigDecimal("100"));
    return c.setScale(2, RoundingMode.HALF_UP);
};
```
Krócej, ale dla complex logic dedicated classes są czytelniejsze.

**Spring DI version:**
```java
@Component("BRONZE")
public class BronzeCommission implements CommissionStrategy { ... }

@Component("SILVER")
public class SilverCommission implements CommissionStrategy { ... }

@Service
public class CommissionStrategyResolver {
    @Autowired
    private Map<String, CommissionStrategy> strategies; // Spring auto-injects all by bean name
}
```

**Pułapka rozmowna:** `compareTo > 0` (strict) vs `>= 0` (inclusive) — sprawdź dokładnie wymaganie. „> 10000" znaczy 10001 i więcej. Druga: `BigDecimal.multiply` może produkować długie scale — zawsze `setScale` na końcu dla pricing.
**Tagi:** strategy, pricing, bigdecimal, design

## Q-OOP-021 [bloom: apply]
**Pytanie:** Pokaż użycie `CompletableFuture` żeby zrównoleglić: pobierz produkt z DB, w międzyczasie pobierz cenę z external API. Aggreguj do `ProductView`.
**Modelowa odpowiedź:**
```java
import java.util.concurrent.*;
import java.math.BigDecimal;

public class ProductService {
    private final Executor executor; // dedicated executor dla async work
    private final ProductRepository repo;
    private final ExternalPricingClient pricingClient;

    public ProductService(Executor executor, ProductRepository repo, ExternalPricingClient client) {
        this.executor = executor;
        this.repo = repo;
        this.pricingClient = client;
    }

    public CompletableFuture<ProductView> getProductView(Long productId, String country) {
        CompletableFuture<Product> productF = CompletableFuture.supplyAsync(
            () -> repo.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId)),
            executor
        );

        CompletableFuture<BigDecimal> priceF = CompletableFuture.supplyAsync(
            () -> pricingClient.getPrice(productId, country),
            executor
        ).orTimeout(2, TimeUnit.SECONDS)
         .exceptionally(ex -> {
            // Fallback to default price if pricing API down
            log.warn("Price fetch failed for {}, using default", productId, ex);
            return BigDecimal.ZERO;
         });

        return productF.thenCombine(priceF, (product, price) -> 
            new ProductView(product.getId(), product.getName(), price, country)
        );
    }

    public ProductView getProductViewSync(Long productId, String country) {
        try {
            return getProductView(productId, country).get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ServiceTimeoutException("Product fetch timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            // unwrap
            if (e.getCause() instanceof ProductNotFoundException) throw (ProductNotFoundException) e.getCause();
            throw new RuntimeException(e.getCause());
        }
    }
}
```

**Konfiguracja executor (Spring Boot):**
```java
@Configuration
public class AsyncConfig {
    @Bean
    public Executor productServiceExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(50);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("product-svc-");
        exec.initialize();
        return exec;
    }
}
```

**Bulk operation (parallel multiple products):**
```java
public CompletableFuture<List<ProductView>> getProductViews(List<Long> ids, String country) {
    List<CompletableFuture<ProductView>> futures = ids.stream()
        .map(id -> getProductView(id, country))
        .collect(Collectors.toList());

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(ignored -> futures.stream()
            .map(CompletableFuture::join) // safe — all complete
            .collect(Collectors.toList())
        );
}
```

**Plusy:**
- Parallel I/O: jeśli DB call 100ms i API call 200ms, total ~200ms (max), zamiast 300ms (sequential).
- Timeout per call (`.orTimeout`).
- Error fallback (`.exceptionally`).

**Java 21 alternative — virtual threads:**
```java
public ProductView getProductView_VT(Long productId, String country) {
    try (var scope = StructuredTaskScope.shutdownOnFailure()) {
        var productTask = scope.fork(() -> repo.findById(productId).orElseThrow());
        var priceTask = scope.fork(() -> pricingClient.getPrice(productId, country));
        scope.join().throwIfFailed();
        return new ProductView(productTask.get(), priceTask.get(), country);
    }
}
```
Structured concurrency (Java 21 preview) — czytelniejszy niż CompletableFuture, automatic cancellation, error propagation.

**Pułapka rozmowna:** `CompletableFuture.allOf` gdy jeden fail — `allOf` returns failed future. `.join` na każdej future-ce pojedynczo wyrzuci. Dla "all-or-nothing" OK, dla "best effort" trzeba per-future handling.
**Tagi:** completablefuture, concurrency, async, pricing

## Q-OOP-022 [bloom: apply]
**Pytanie:** Implementuj custom `RuntimeException` hierarchy dla pricing engine.
**Modelowa odpowiedź:**
```java
// Base exception — root hierarchii
public class PricingException extends RuntimeException {
    private final String errorCode;
    
    public PricingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public PricingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() { return errorCode; }
}

// Specific exceptions
public class ProductNotFoundException extends PricingException {
    public ProductNotFoundException(Long productId) {
        super("PRODUCT_NOT_FOUND", "Product not found: " + productId);
    }
}

public class InvalidPriceException extends PricingException {
    public InvalidPriceException(String reason) {
        super("INVALID_PRICE", reason);
    }
}

public class CurrencyMismatchException extends PricingException {
    public CurrencyMismatchException(String expected, String actual) {
        super("CURRENCY_MISMATCH", String.format("Expected currency %s, got %s", expected, actual));
    }
}

public class PricingRuleException extends PricingException {
    public PricingRuleException(String rule, String reason) {
        super("RULE_FAILURE", String.format("Rule '%s' failed: %s", rule, reason));
    }
}

public class ExternalPricingServiceException extends PricingException {
    public ExternalPricingServiceException(String message, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR", message, cause);
    }
}

// Spring REST exception handler
@RestControllerAdvice
public class PricingExceptionHandler {
    
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }
    
    @ExceptionHandler(InvalidPriceException.class)
    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(PricingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }
    
    @ExceptionHandler(ExternalPricingServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternal(ExternalPricingServiceException ex) {
        log.error("External service failure", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(ex.getErrorCode(), "Service temporarily unavailable"));
    }
    
    @ExceptionHandler(PricingException.class) // catch-all dla pozostałych pricing exceptions
    public ResponseEntity<ErrorResponse> handleGeneric(PricingException ex) {
        log.error("Pricing error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }
}

public record ErrorResponse(String code, String message) {}
```

**Plusy hierarchii:**
- **Catch-all** dla pricing logic: `catch (PricingException e)`.
- **Granularny handling**: per concrete subclass.
- **Error codes** for client-side handling — `errorCode` field stable contract, `message` może się zmieniać.
- **Spring integration**: `@RestControllerAdvice` mapuje na HTTP responses.

**Pułapka rozmowna:** „Wszystko jako runtime ex" — głównie OK. Rzadkie use case dla checked: gdy wymuszasz że caller MUSI handle (np. transaction commit failure with retry possible). Druga: catch-all `Exception` w handler — kasuje stack trace dla unrelated bugs. Always specific catches.
**Tagi:** exceptions, error-handling, spring, design

## Q-OOP-023 [bloom: apply]
**Pytanie:** Pokaż jak użyć Streams API do agregacji per kategorii: count, sum prices, avg price.
**Modelowa odpowiedź:**
```java
import java.math.*;
import java.util.*;
import java.util.stream.*;

class Product {
    String name; String category; BigDecimal price;
    // ctor, getters
}

List<Product> products = ...;

// Wariant 1 — multi-aggregate via collectingAndThen + helper class
class CategoryStats {
    long count; BigDecimal sum; BigDecimal avg;
    CategoryStats(long count, BigDecimal sum, BigDecimal avg) {
        this.count = count; this.sum = sum; this.avg = avg;
    }
}

Map<String, CategoryStats> stats = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.collectingAndThen(
            Collectors.toList(),
            list -> {
                long count = list.size();
                BigDecimal sum = list.stream()
                    .map(Product::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avg = count == 0 ? BigDecimal.ZERO 
                    : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                return new CategoryStats(count, sum, avg);
            }
        )
    ));

// Print
stats.forEach((cat, s) -> 
    System.out.printf("%s: count=%d, sum=%s, avg=%s%n", cat, s.count, s.sum, s.avg)
);

// Wariant 2 — pojedyncze aggregations
Map<String, Long> countByCategory = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

Map<String, BigDecimal> sumByCategory = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.reducing(BigDecimal.ZERO, Product::getPrice, BigDecimal::add)
    ));

// Wariant 3 — DoubleSummaryStatistics (jeśli double precision OK; w pricingu zazwyczaj NIE)
Map<String, DoubleSummaryStatistics> dblStats = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.summarizingDouble(p -> p.getPrice().doubleValue())
    ));
// dblStats.get("Electronics").getCount() / .getSum() / .getAverage()
```

**Wariant z record (Java 16+):**
```java
record CategoryStats(long count, BigDecimal sum, BigDecimal avg) {
    static CategoryStats from(List<Product> products) {
        long count = products.size();
        BigDecimal sum = products.stream()
            .map(Product::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = count == 0 ? BigDecimal.ZERO 
            : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new CategoryStats(count, sum, avg);
    }
}

Map<String, CategoryStats> stats = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory, 
        Collectors.collectingAndThen(Collectors.toList(), CategoryStats::from)));
```

**Z parallelStream:**
```java
products.parallelStream()
    .collect(Collectors.groupingByConcurrent(...));
```
Concurrent collectors dla parallel reduction. Ale: dla większości pricing data parallelStream overhead wins niż gain. Mierz przed parallelizacją.

**Pułapka rozmowna:** `BigDecimal.divide` bez explicit scale + rounding rzuca `ArithmeticException` jeśli wynik nieskończony decimal (np. 10/3). ZAWSZE `divide(divisor, scale, roundingMode)`. Druga: `summingDouble` w pricingu = precision loss. Stick with BigDecimal aggregation.
**Tagi:** streams, groupby, aggregation, bigdecimal

## Q-OOP-024 [bloom: apply]
**Pytanie:** Implement Java method `validatePromotion` że waliduje obiekt `Promotion` (start_date, end_date, discount_pct, customer_segments) i zwraca listę błędów.
**Modelowa odpowiedź:**
```java
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

class Promotion {
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal discountPct;
    List<String> customerSegments;
    // getters
}

public class PromotionValidator {
    private static final List<String> VALID_SEGMENTS = List.of("B2B", "B2C", "VIP", "STAFF");
    
    public static List<String> validate(Promotion promo) {
        List<String> errors = new ArrayList<>();
        
        // Required fields
        if (promo.getStartDate() == null) errors.add("startDate is required");
        if (promo.getEndDate() == null) errors.add("endDate is required");
        if (promo.getDiscountPct() == null) errors.add("discountPct is required");
        
        // If startDate and endDate present, check ordering
        if (promo.getStartDate() != null && promo.getEndDate() != null) {
            if (promo.getEndDate().isBefore(promo.getStartDate())) {
                errors.add("endDate must be after startDate");
            }
            if (promo.getStartDate().isBefore(LocalDate.now().minusDays(30))) {
                errors.add("startDate cannot be more than 30 days in the past");
            }
        }
        
        // Discount range
        if (promo.getDiscountPct() != null) {
            if (promo.getDiscountPct().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("discountPct must be greater than 0");
            }
            if (promo.getDiscountPct().compareTo(BigDecimal.ONE) > 0) { // > 1.0 i.e. > 100%
                errors.add("discountPct must not exceed 1.00 (100%)");
            }
        }
        
        // Segments
        if (promo.getCustomerSegments() == null || promo.getCustomerSegments().isEmpty()) {
            errors.add("at least one customer segment required");
        } else {
            for (String segment : promo.getCustomerSegments()) {
                if (!VALID_SEGMENTS.contains(segment)) {
                    errors.add("invalid segment: " + segment);
                }
            }
        }
        
        return errors;
    }
    
    public static void validateOrThrow(Promotion promo) {
        List<String> errors = validate(promo);
        if (!errors.isEmpty()) {
            throw new InvalidPromotionException(String.join(", ", errors));
        }
    }
}
```

**Bardziej Java-style — Bean Validation:**
```java
import jakarta.validation.constraints.*;

class Promotion {
    @NotNull
    @FutureOrPresent(message = "startDate cannot be in the past")
    LocalDate startDate;
    
    @NotNull
    LocalDate endDate;
    
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false, message = "discountPct must be > 0")
    @DecimalMax(value = "1.00", message = "discountPct must be <= 1")
    BigDecimal discountPct;
    
    @NotEmpty(message = "at least one segment required")
    List<@Pattern(regexp = "B2B|B2C|VIP|STAFF") String> customerSegments;
    
    @AssertTrue(message = "endDate must be after startDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}

// Spring usage:
@PostMapping("/promotions")
public Promotion create(@Valid @RequestBody Promotion promo) {
    // Auto-walidacja przed wejściem do metody
}
```

**Plusy custom validator (manual):**
- Pełna kontrola.
- Independent of frameworks.
- Łatwo unit-test.

**Plusy Bean Validation:**
- Deklaratywne (annotacje).
- Reusable across layers (DTO + entity).
- Spring integration out of box.
- Standard (JSR 380).

**Decision:** w Spring app preferuj Bean Validation. Custom validator dla complex rules nie wyrażalnych annotacjami (cross-field, conditional logic). Często mix: simple constraints przez annotations, complex via `@AssertTrue` method lub custom `ConstraintValidator`.

**Pułapka rozmowna:** Bean Validation jest fail-fast lub fail-all? Default fail-all (zbiera wszystkie błędy). `@GroupSequence` dla fail-fast w stages. Druga: `@FutureOrPresent` + `LocalDate.now()` — timezone-aware? `@FutureOrPresent` w Bean Validation używa systemowego clock, bezpiecznie.
**Tagi:** validation, bean-validation, pricing

---

## Q-OOP-025 [bloom: analyze]
**Pytanie:** Twój zespół rozważa przejście z Java 8 na Java 21. Argumenty?
**Modelowa odpowiedź:** **Plusy migracji:**

1. **Performance:**
   - JIT improvements (Java 11+ ZGC, Java 21 generational ZGC).
   - Better GC defaults.
   - Vector API (Java 21+) for SIMD operations.
   - Generally 10-30% throughput improvement out of box dla production apps.

2. **Language features:**
   - **Records** (Java 16) — boilerplate elimination.
   - **Sealed classes + pattern matching** (Java 17, 21) — exhaustive ADT-like switches.
   - **Switch expressions** (Java 14) — concise, exhaustive.
   - **Text blocks** (Java 15) — multi-line strings.
   - **`var`** local variable inference (Java 10) — less verbose.
   - **`instanceof` pattern matching** (Java 16).
   - **Virtual threads** (Java 21) — massive scaling for I/O-bound apps.
   - **Structured concurrency** (preview Java 21).

3. **API improvements:**
   - `Stream.toList()` (Java 16).
   - `Map.of`, `List.of`, `Set.of` (Java 9).
   - `String.repeat`, `strip`, `lines`, `formatted` (Java 11+).
   - `HttpClient` modern (Java 11).

4. **Security:**
   - Java 8 EOL premium support 2030 (free updates ended in 2019). Bezpieczeństwo, compliance.
   - Modern TLS 1.3 default.

5. **Library ecosystem:**
   - Spring Boot 3+ requires Java 17+.
   - Modern libraries deprecate Java 8.

**Minusy / Risks:**

1. **Migration effort:**
   - Module system (Java 9+) — może wymagać `--add-opens` flag dla reflective access.
   - Removed APIs (e.g., `JEP 411` deprecated SecurityManager).
   - Behavior changes in some APIs (subtle).
   - Build tools (Maven, Gradle) update.
   - CI/CD pipeline update.

2. **Library incompatibilities:**
   - Older libs (Java 8 only) can't be upgraded without patching.
   - JAXB removed from JDK (Java 11+) — explicit dependency.

3. **Container images:**
   - Update Dockerfiles, base images.

4. **Training:**
   - Team needs to learn new features.

5. **JVM args changes:**
   - GC config different (CMS removed, ZGC default for big heaps).

**Recommended approach:**
- Start z Java 17 (LTS, mature). Java 21 (newer LTS) jeśli teamem agile-friendly.
- Incremental: build na nowej Javie, run na starej. Then run na nowej.
- Update one service at a time (microservices style).
- Test thoroughly — edge cases w concurrency, regex, date/time.
- Document any --add-opens needed.

**Pricing-specific:**
- Records perfect dla DTOs (pricing entities).
- Pattern matching świetny dla Strategy / Visitor patterns.
- Virtual threads — scaling boost dla pricing service z dużo I/O.
- BigDecimal handling not changed — clean upgrade.

**Pułapka rozmowna:** „Java 8 still works" — yes, ale technical debt. Każdy rok = więcej za sobą. Odkładanie migracji = większy boom kiedyś. Druga: „Java 17 vs 21" — 21 jest current LTS (jesień 2023). Dla nowego projektu — 21. Dla migration z 8 — może 17 jako step.
**Tagi:** java-versions, migration, modernization, decision

## Q-OOP-026 [bloom: analyze]
**Pytanie:** Zespół debatuje: encje JPA z setters vs immutable z konstruktorem. Co wybierasz?
**Modelowa odpowiedź:** Klasyczna dyskusja. **Argumenty obu stron:**

**Mutable JPA encje (z setterami) — tradycyjny styl:**
- ✓ JPA workhorse — większość przykładów, tutoriali.
- ✓ Hibernate proxy generation działa naturally (lazy loading via setters/getters).
- ✓ Easy to manipulate w business logic — `entity.setStatus(NEW)`.
- ✗ Aliased state — można accidentally mutate w wielu miejscach.
- ✗ Encja jest jednocześnie domain object I database row — naruszenie SRP.
- ✗ Trudne do testowania logiki — każda metoda mutuje stan.

**Immutable encje:**
- ✓ Thread safety.
- ✓ Easy reasoning — value semantic.
- ✓ Better domain logic separation.
- ✗ JPA wymaga default constructor i setters dla load. Reflection magic. Można obejść z `@PersistenceConstructor` i private setters, ale walka z framework.
- ✗ Update przez `EntityManager.merge()` z full new object — wymaga sledzenia zmian.

**Realistyczne podejście — DDD + CQRS hybrid:**

**1. Domain model immutable** (record-like): `Money`, `Address`, `OrderStatus` — Value Objects (immutable).

**2. Aggregate roots mutable (controlled mutability):** `Order`, `Customer` — encje z metodami które enforce invariants:
```java
@Entity
public class Order {
    @Id Long id;
    OrderStatus status;
    BigDecimal total;
    
    // No public setters!
    // Mutations only through methods enforcing rules:
    public void addItem(Item i) {
        if (status != OrderStatus.DRAFT) throw new IllegalStateException();
        // recalc total
    }
    
    public void confirm() {
        if (items.isEmpty()) throw new IllegalStateException();
        this.status = OrderStatus.CONFIRMED;
    }
}
```

**3. Read DTOs immutable** (pricing read model):
```java
public record OrderView(Long id, OrderStatus status, BigDecimal total, List<ItemView> items) { }
```

**4. Write DTOs (commands):**
```java
public record CreateOrderCommand(Long customerId, List<ItemSpec> items) { }
```

**Best practices:**
- **NEVER** dump-style entities z public setters dla wszystkiego. Every mutation ma być invariant-checking method.
- Use `@Embeddable` z immutable Value Objects (`Money`, `Address`).
- Use records for read DTOs and commands.
- Hibernate envers / entity listeners dla audit.

**Pricing-specific:**
- `Price` jako Value Object (immutable record).
- `Product` aggregate ma `Price` field, ale modyfikacja przez `product.changePrice(newPrice, reason)` (audit, validation).
- `PriceCalculation` (immutable record) — output of calculation.
- `PriceCalculationCommand` (immutable record) — input.

**Pułapka rozmowna:** „Immutable entities = lepszy kod" — tak w abstrakcji, ale walka z JPA. Compromise: encapsulated mutability (no public setters) zamiast strict immutability. Druga: lombok `@Data` na encjach — generuje setters dla wszystkiego, robi domain anemic.
**Tagi:** jpa, immutability, ddd, design, decision

## Q-OOP-027 [bloom: analyze]
**Pytanie:** Code review: kolega napisał `synchronized` block na 200 liniach kodu. Reaguj.
**Modelowa odpowiedź:** **Czerwone flagi:**

1. **Long critical section** = poor concurrency. Wszystkie wątki czekają tak długo jak wykonuje się 200 lines. Throughput tyranniczne.

2. **What's actually being synchronized?** Często 200 lines obejmuje I/O (DB call, HTTP request) — nie powinno być w synchronized. Trzymanie locka podczas wait jest cardinal sin.

3. **Lock scope vs invariant:** czy faktycznie wszystkie 200 lines need locking, czy tylko mała część? Zazwyczaj niemal nigdy nie.

4. **Może być replaced wizualnie:**
   - **Concurrent collection** (`ConcurrentHashMap` zamiast manual sync na HashMap).
   - **Atomic operations** (`AtomicInteger` zamiast `synchronized` increment).
   - **Higher-level abstractions** (`BlockingQueue`, `Semaphore`).

5. **Test:** czy są testy concurrency? Single-thread test passes, multi-thread może deadlock / race.

**Praktyczna review:**
```
Q: Co tu chronimy?
Q: Jakie operacje są thread-safe by themselves (no need lock)?
Q: Które są I/O? (move out of sync)
Q: Czy lock object jest private / final?
Q: Jakie są inne wątki accessing this state?
```

**Refactor pattern:**
```java
// BEFORE
public synchronized void process(Order o) {
    log.info("Processing {}", o.id);   // I/O — out of sync
    Cache c = getCache();              // already thread-safe
    Customer cust = lookupCustomer(o); // DB call — out of sync!
    BigDecimal price = calculate(o, cust);  // pure compute — likely out of sync
    if (validatePrice(price)) {
        atomicallyUpdateBalance(o.customerId, price);   // ONLY this needs care
    }
    auditLog(o, price);                // I/O
}

// AFTER
public void process(Order o) {
    log.info("Processing {}", o.id);
    Cache c = getCache();
    Customer cust = lookupCustomer(o);
    BigDecimal price = calculate(o, cust);
    if (validatePrice(price)) {
        // Tylko ta operacja faktycznie wymaga thread-safety
        // Zrealizować przez Atomic lub ConcurrentHashMap.compute lub very small synchronized
        balanceService.deduct(o.customerId, price);  // moved to thread-safe service
    }
    auditLog(o, price);
}
```

**Decyzja:**
- Reject patch.
- Suggest:
  - Extract concurrent state into dedicated thread-safe class (`AtomicReference`, `ConcurrentHashMap`, `LongAdder`).
  - Move I/O outside locks.
  - Lock only what truly is shared mutable state.
  - Document why lock exists (comment).
- Ask: czy testy concurrency obejmują ten kod? Bez nich — risk.

**Patterns alternatywne:**
- **Lock-free** with `AtomicLong`, `AtomicReference`.
- **Read-Write locks** (`ReentrantReadWriteLock`) — reads parallel, writes exclusive.
- **STM (Software Transactional Memory)** — niche.
- **Actor model** (Akka / Vert.x) — message-passing, no shared state.
- **Database transactions** — przesunąć synchronization z kodu do DB (gdy to ma sens).

**Pułapka rozmowna:** „Synchronized to safe" — tak, ale slow + deadlock-prone. „Synchronized na wszystkim — better safe than sorry" — paralyzed concurrency. Real pros: minimum critical sections, lock-free where possible, profile with JMH dla validation.
**Tagi:** code-review, concurrency, synchronized, design

## Q-OOP-028 [bloom: analyze]
**Pytanie:** Spring DI vs manual constructor injection — co i kiedy?
**Modelowa odpowiedź:** Spring DI (Dependency Injection) container manages object creation, wiring, lifecycle. **Wariants:**

**1. Constructor injection (preferred, modern):**
```java
@Service
public class PricingService {
    private final ProductRepository repo;
    private final PromotionService promos;
    
    public PricingService(ProductRepository repo, PromotionService promos) {
        this.repo = repo;
        this.promos = promos;
    }
}
```
Plus: dependencies explicit, fields can be `final` (immutable), easier testing (no Spring needed for unit tests, just `new PricingService(mockRepo, mockPromos)`).

**2. Field injection (deprecated style):**
```java
@Service
public class PricingService {
    @Autowired private ProductRepository repo;
    @Autowired private PromotionService promos;
}
```
Plus: short. Minus: nie testowalne bez Spring/reflection, can't be `final`, hidden dependencies, harder to spot when class grows.

**3. Setter injection:**
```java
@Service
public class PricingService {
    private ProductRepository repo;
    
    @Autowired
    public void setRepo(ProductRepository repo) { this.repo = repo; }
}
```
Use case: optional dependencies. Otherwise constructor preferred.

**Manual injection (no DI framework):**
```java
public class Application {
    public static void main(String[] args) {
        DataSource ds = new HikariDataSource(/* config */);
        ProductRepository repo = new JpaProductRepository(ds);
        PromotionService promos = new PromotionService(repo);
        PricingService pricing = new PricingService(repo, promos);
        // ... wiring
    }
}
```
Plus: explicit, no magic, easy to follow. Minus: lots of boilerplate as app grows. Also: who manages singleton scope? Lifecycle hooks?

**When DI framework helps:**
- Many services with many dependencies (modern apps mają 100+ beans).
- Aspect-oriented features (`@Transactional`, `@Cacheable`, `@Async`).
- Lifecycle management (graceful shutdown, init hooks).
- Configuration management (`@Value`, `@ConfigurationProperties`).
- Testing utilities (`@MockBean`, `@WebMvcTest`).

**When manual is fine:**
- Small apps (CLI tool, simple utility).
- Library code (don't force DI on consumers).
- Performance-critical: DI startup cost (Spring ~3-10s startup overhead). For instant-start CLIs, AOT compilation (GraalVM Spring Native) helps.

**DDD + manual injection:** sometimes pure DDD prefers explicit wiring at composition root, with DI used only at boundaries. Allows clean unit testing of domain.

**Decision criteria:**
- Existing Spring app → continue Spring DI (consistency).
- New microservice → Spring Boot DI (matures, easy).
- Library / SDK → no DI, allow consumer to inject (constructor injection compatible with any DI).
- Lambda / cold-start sensitive → consider Micronaut/Quarkus z compile-time DI.

**Modern Spring features:**
- Constructor injection without `@Autowired` (one constructor — Spring auto-detects).
- `@RequiredArgsConstructor` (Lombok) — auto-generates constructor from `final` fields.

**Pricing-specific:**
- DI niemal zawsze used in modern Java backend.
- Pricing strategies registered as `@Component`, injected as `Map<String, PricingStrategy>` (Spring auto-collects).
- Configuration via `@ConfigurationProperties("pricing.tax")`.

**Pułapka rozmowna:** „Field injection convenient" — short term. Long term: hidden dependencies, hard tests, can't be `final`. Constructor injection becomes painful only when class has 7+ deps — that's a smell, refactor (split class).
**Tagi:** spring, di, design, testing

## Q-OOP-029 [bloom: analyze]
**Pytanie:** Twój system rzuca `OutOfMemoryError` w produkcji. Jak diagnozujesz?
**Modelowa odpowiedź:** **Step-by-step diagnostic:**

**1. Check basics:**
- Heap size: `-Xmx`. Czy zwiększony from default? Default 25% RAM, może być za mały.
- Memory dump: czy enabled `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dumps`?
- Logs: pełny stack trace, `OutOfMemoryError` ma cause: "Java heap space" / "Metaspace" / "Direct buffer memory" / "GC overhead limit exceeded".

**2. Identify type:**
- **"Java heap space"** — heap fully used, can't allocate more. Common.
- **"GC overhead limit exceeded"** — JVM spends >98% time in GC, recovers <2%. Symptom of memory pressure before OOM.
- **"Metaspace"** — class metadata space exhausted. Often caused by classloader leaks, dynamic class generation (CGLIB, Groovy, Hibernate proxy).
- **"Direct buffer memory"** — off-heap NIO buffers. NettyDirectBufLeak case.
- **"Compressed class space"** — class compressed pointers space.

**3. Heap dump analysis:**
- Tool: **Eclipse MAT (Memory Analyzer)**, VisualVM, JProfiler, YourKit.
- Open dump, look at:
  - **Dominator tree** — which objects retain most memory.
  - **Class histogram** — which classes have most instances + bytes.
  - **Leak suspects** — MAT's heuristic.
- Common findings:
  - Massive `byte[]` / `char[]` — likely caching, response buffers, or data being held.
  - `HashMap` with millions entries — unbounded cache.
  - `ThreadLocal` not cleaned — thread pool with stale state.
  - Listeners / observers not unregistered.
  - JDBC ResultSet not closed (rare but happens).

**4. Code analysis:**
- Search for `new HashMap()` / `new ArrayList()` as fields without size limits.
- Static fields holding collections (never GC'd until classloader unloaded).
- `@Cacheable` with no eviction policy (Spring Cache).
- `WeakHashMap` vs `HashMap` — weak refs allow GC; strong refs prevent.

**5. Profile in test environment:**
- Reproduce locally with smaller `-Xmx`.
- Use JFR (Java Flight Recorder): `-XX:StartFlightRecording=...`. Records allocations, GCs, locks.
- async-profiler for allocation profiling: `./profiler.sh -e alloc -d 60 <pid>`.

**6. Fix patterns:**
- **Bounded caches:** Caffeine z max size + TTL.
- **Pagination** zamiast load full dataset.
- **Streaming** (XML/JSON) zamiast DOM.
- **Pool resources** (DB connections, HTTP clients) — limited.
- **Cleanup hooks** — `@PreDestroy`, listeners removal.
- **Profile + iterate.**

**7. Mitigation while diagnosing:**
- **Restart pod** — quick fix, accepts data loss.
- **Increase heap** — `-Xmx` bumped, buys time. Not a fix.
- **Add canary monitoring** — alert before OOM (e.g., heap >90% for >5 min).

**Pricing-specific scenarios:**
- **Pricing cache unbounded** — load pricelist for every product/country/customer combination, never evict. Solution: bounded with eviction.
- **Big batch processing** — load 1M products into List<Product> for batch operation. Solution: stream/cursor-based.
- **Audit log buffered in memory** — flush to DB / queue async. Solution: bounded buffer + async flush.

**Pułapka rozmowna:** „Add more RAM" without diagnosis — hides leak. App hits OOM later or at higher scale. Druga: ignorowanie GC logs. `-Xlog:gc*` w prod (rotated). Diagnoza często widoczna w GC behavior przed OOM.
**Tagi:** memory, debugging, production, jvm

## Q-OOP-030 [bloom: analyze]
**Pytanie:** Builder pattern vs lombok `@Builder` vs records — kiedy które?
**Modelowa odpowiedź:** **Manual Builder:**
- Plus: full control, custom validation logic in `build()`, custom defaults logic, optional immutability.
- Minus: boilerplate (~50+ lines for class with 5+ fields).

**Lombok `@Builder`:**
```java
@Builder
public class Order {
    private Long customerId;
    @Builder.Default private String currency = "PLN";
    private List<Item> items;
    private BigDecimal discount;
}

Order o = Order.builder()
    .customerId(123L)
    .items(List.of(...))
    .discount(BigDecimal.valueOf(0.1))
    .build();
```
Plus: zero boilerplate. Minus: dependency on Lombok (build agent, IDE plugin), nie wszyscy lubią. Limited customization.

**Records (Java 16+):**
```java
public record Order(Long customerId, String currency, List<Item> items, BigDecimal discount) {
    public Order {
        Objects.requireNonNull(customerId);
        if (currency == null) currency = "PLN"; // default
        // ...
    }
}

Order o = new Order(123L, "PLN", List.of(...), BigDecimal.valueOf(0.1));
```
Plus: native Java, no library, immutable. Minus: positional args (po wielu polach unreadable: `new Order(123L, "PLN", null, null, null, null)` —what is what?). No defaults syntax (must do manually in compact constructor).

**Records with builder (combination):**
```java
public record Order(Long customerId, String currency, List<Item> items, BigDecimal discount) {
    // builder via static method or generated by Lombok:
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private Long customerId;
        private String currency = "PLN";
        // ...
        public Builder customerId(Long c) { this.customerId = c; return this; }
        public Order build() { return new Order(customerId, currency, items, discount); }
    }
}
```

**Decision matrix:**

| Use case | Recommendation |
|----------|---------------|
| Few fields (1-3), no defaults | Constructor / record |
| Many fields, named params helpful | Lombok @Builder lub manual builder |
| Domain Value Object (immutable) | Record |
| DTO with validation | Record + compact constructor |
| Very complex construction logic | Manual builder (full control) |
| Spring app with Lombok already | @Builder + existing classes |
| Library (no deps) | Manual builder |

**Pricing-specific:**
- `Money` Value Object → record.
- `OrderRequest` (write DTO with optional fields) → record + builder OR @Builder if Lombok.
- `PricingResult` (complex output with several optional fields) → record.
- `PricingRule` (loaded from DB, mutable for editing) → encja JPA or specific builder.

**Trends 2025:**
- Move toward **records** for new code (no Lombok).
- Lombok still common, but losing favor (records cover 80% use cases).
- Manual builder still relevant for high-customization needs.

**Pułapka rozmowna:** „Lombok wygodne więc lepsze" — dependency, magic w bytecode, czasem trudne do debug. Pure Java preferable when możliwe. Druga: Records bez named params — call site staje się `new Order(123L, "PLN", items, BigDecimal.ZERO, null, "promo123", true, false)` — nightmare. Add builder if 4+ fields with defaults/optional.
**Tagi:** builder, records, lombok, design, decision

## Q-OOP-031 [bloom: analyze]
**Pytanie:** Twój kolega kopiuje kod metody `calculatePrice` do 3 różnych miejsc. Nie chce wyciągnąć do shared, „bo każda wersja może się różnić". Reaguj.
**Modelowa odpowiedź:** Klasyczne napięcie: **DRY (Don't Repeat Yourself)** vs **WET (Write Everything Twice / Don't Generalize Prematurely)**. Both have merit.

**Argumenty za extraction (DRY):**
- Single source of truth → bug fix raz, działa wszędzie.
- Test coverage centralized.
- Documentation centralized.
- Future enhancements consistent.

**Argumenty kolegi (against premature generalization):**
- 3 use cases mogą wyglądać podobnie ale ewoluować różnie.
- Premature abstraction creates wrong abstraction — refactor more painful than copy.
- Sandi Metz: „Duplication is far cheaper than the wrong abstraction".

**Practical analysis:**

1. **Are they really duplicates?** Compare line-by-line. Often one is identical, others have variations:
   - Same logic, different inputs → parameter.
   - Same skeleton, different middle → Strategy pattern or method parameter.
   - Truly different code that just looks similar (fool's gold) → keep separate.

2. **What changes if we extract?**
   - Common parts → shared method.
   - Variable parts → parameters or strategies.
   - Future divergence: if one needs change, easy to inline + diverge.

3. **Test coverage:** czy każda kopia ma swoje testy? Bez testów — nie wiesz czy są semantycznie identyczne.

**Decision principles:**

- **Rule of three (Don Wells):** dwie kopie OK, trzy znaczy że jest pattern, ekstrahuj.
- **Wait for divergence to actually happen** before refactoring out of shared:
  ```
  3 identical methods. Future requirement: jedna z nich potrzebuje extra parameter.
  → Inline the shared method (revert). Add param to that one.
  ```
- **Be skeptical of "but they might diverge"** — speculation. Currently identical = current truth.

**Practical refactor for pricing example:**
```java
// Original 3 identical methods (różnie nazwane):
class CartService { BigDecimal computePrice(...) { /* 30 lines */ } }
class OrderService { BigDecimal calculateTotal(...) { /* same 30 lines */ } }
class QuoteService { BigDecimal estimatePrice(...) { /* same 30 lines */ } }

// Extracted:
class PricingCalculator {
    BigDecimal calculate(Product p, Customer c, Context ctx) { /* 30 lines */ }
}

// All three services depend on PricingCalculator:
class CartService { 
    private final PricingCalculator calc;
    BigDecimal computePrice(...) { return calc.calculate(...); }
}
```

**Decyzja w code review:**
- Reject patch jeśli to jest blatant copy-paste z 3 miejsc.
- Suggest: extract to shared, tests (which all 3 services use).
- If kolega obawiae się divergence, ustal kontrakt: „Jeśli pojawi się divergence, zrobimy refactor — split. Tymczasem jeden source of truth."

**Edge case where copy IS OK:**
- Module boundaries (microservices) — service A i service B mogą mieć ten sam algorytm, ale extracting na shared library tworzy coupling. Better: each service own copy + shared spec/test.
- Read-vs-write models in CQRS (intentionally different shapes).

**Pułapka rozmowna:** „DRY zawsze prawidłowe" — sometimes wrong abstraction tworzy more debt than duplikacja. Knuth: rozsądek ponad dogmaty. Druga: extracted method z 12 boolean flags żeby pokryć wszystkie use cases — to znaczy że abstrakcja jest źle wybrana, refactor.
**Tagi:** dry, refactoring, code-review, design

## Q-OOP-032 [bloom: analyze]
**Pytanie:** W pricing engine wybierasz: wykryć błędy walidacji input early (w controller) czy w service layer? Trade-offy.
**Modelowa odpowiedź:** **Defense in depth** — typowo **OBA**, ale z różnymi rolami.

**Controller layer validation:**
- **Format / structural:** JSON walidne, types correct, required fields present.
- Tools: `@Valid` Bean Validation, JSON Schema, OpenAPI.
- Goal: reject malformed requests EARLY — nie marnuj czasu na resource lookup, business logic.
- Returns: 400 Bad Request lub 422 Unprocessable Entity.

**Service layer validation:**
- **Business rules:** pricing constraints (price > MAP, customer eligible for tier, promotion still active).
- Knowledge of business state (DB lookups, related entities).
- May involve external calls (tax rate API, inventory check).
- Returns: domain exception (e.g., `InvalidPromotionException`) — controller maps to HTTP error.

**Why both:**
- **Controller alone** isn't sufficient: business rules need data not present in DTO (e.g., „discount must be ≤ 5% chyba że customer is VIP — VIP info from DB").
- **Service alone** wastes resources on bad inputs that controller could reject quickly. Plus: service alone doesn't help when service called from multiple sources (controller, scheduled job, message consumer).

**Pricing example:**
```java
// Controller — DTO walidacja
public record CreatePromotionRequest(
    @NotBlank String name,
    @NotNull @FutureOrPresent LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal discountPct,
    @NotEmpty List<String> segments
) {}

@PostMapping("/promotions")
public PromotionResponse create(@Valid @RequestBody CreatePromotionRequest req) {
    // @Valid catches: missing fields, wrong types, format violations
    // 400 Bad Request returned automatically by Spring
    Promotion promo = promotionService.create(req);
    return PromotionResponse.from(promo);
}

// Service — business validation
@Service
public class PromotionService {
    public Promotion create(CreatePromotionRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new InvalidPromotionException("endDate must be after startDate");
        }
        // Check overlapping promotions for same segments
        if (hasOverlapping(req.startDate(), req.endDate(), req.segments())) {
            throw new InvalidPromotionException("Overlaps with existing promotion");
        }
        // Check discount cap per segment from DB config
        BigDecimal maxDiscount = configService.getMaxDiscount(req.segments());
        if (req.discountPct().compareTo(maxDiscount) > 0) {
            throw new InvalidPromotionException("Exceeds segment max discount " + maxDiscount);
        }
        // ...
        return repository.save(new Promotion(...));
    }
}
```

**Layered validation philosophy:**
1. **Outer layer** (controller / API gateway) — input sanitization, format, basic constraints. Fast fail.
2. **Application layer** (service) — orchestration, can call multiple domains.
3. **Domain layer** (entity / aggregate methods) — invariants of the business object itself. „Promotion can't have negative discount" jest invariant of Promotion → enforce in constructor / setter.
4. **Database** — last line of defense (CHECK constraints, FK, unique). Catches what code missed.

**Anti-patterns:**
- **All validation in controller** — business logic w controller layer. Don't.
- **All validation in DB** — bad UX (reach DB only to find error message generic).
- **Validate same thing 3 times** — fix once, not in every layer.

**Reusing validation:**
- DTO walidacja przez annotation = reusable.
- Domain rule (e.g., „Promotion is valid") expressed in domain method `Promotion.validate()` — reused in create / update.
- Don't duplicate w controller AND service — extract to shared validator.

**Pricing-specific:**
- Validation jest CRITICAL — błąd cenny często = financial loss / customer trust issue.
- Multiple sources of price data (input, config, base price) — each potentially malformed.
- Audit invalid attempts — logs help find bad integrations / abuse.

**Pułapka rozmowna:** „Controller validates everything, service trusts" — naïve. Service called from queue consumer, scheduled job, internal API — service must not assume valid input. Druga: redundant validation everywhere → maintenance burden, drift between layers. Define ownership: controller = format, service = business rules, domain = invariants. No overlap, complete coverage.
**Tagi:** validation, architecture, layered, design
