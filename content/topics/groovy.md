> Pre-built topic bank. Your /onboard will generate personalized content for your target role.

# Groovy — bank pytań

> Stack referencyjny: Groovy 4.x (LTS) i Groovy 5.0 (sierpień 2025). Kontekst zastosowań: pricing engine, scripting w platformie e-commerce, integracje (XML/JSON), testowanie (Spock). Pytania ułożone od recall przez understand i apply do analyze.

## Zakres

- składnia: `def`, `var`, `final`, GString, listy, mapy, range
- closures (`{ }`), `it`, currying, `delegate`/`owner`/`this`
- `@CompileStatic`, `@TypeChecked`, dynamic vs static
- MOP (`methodMissing`, `propertyMissing`, `invokeMethod`)
- AST transformations: `@ToString`, `@EqualsAndHashCode`, `@TupleConstructor`, `@Canonical`, `@Immutable`, `@Builder`, `@Memoized`, `@Delegate`
- traits vs abstract class
- collections: `each`, `collect`, `findAll`, `inject`, `groupBy`, `sort`, `unique`, `withIndex`
- builders: `MarkupBuilder`, `JsonBuilder`, `JsonSlurper`, `XmlSlurper`, `XmlParser`
- testowanie: Spock (Specification, given/when/then, where, mocks)
- Groovy 5.0 nowości: operator implikacji `==>`, `partitionPoint`, `subList(Range)`, `@OperatorRename`, JEP-512 compact source files, JLine 3 REPL, JDK 25 support, lazy iterators, ~350 nowych extension methods
- pułapki: equals vs `==`, NPE z safe navigation `?.`, dynamic dispatch przy `def`

---

## Q-GRV-001 [bloom: recall]
**Pytanie:** Czym różni się `def` od `var` w Groovym?
**Modelowa odpowiedź:** `def` to dynamiczny placeholder — kompiluje się do `Object`, dispatch metod jest dynamiczny w runtime przez MOP. `var` (od Groovy 3.0) to inferowany typ statyczny, kompilator wnioskuje konkretny typ z RHS i wymusza go (`var x = 5` daje `int x`). Pod `@CompileStatic` różnica robi się jeszcze ostrzejsza: `def` traci dynamic dispatch i staje się sztywny `Object`, a `var` zachowuje konkretny typ. Praktycznie: `def` jest „groovy-way" i pozwala na meta-programowanie; `var` to ukłon w stronę Javy 10+ i dyscypliny statycznej.
**Pułapka rozmowna:** Wielu mówi „to to samo, alias". To pułapka. Performance, type checking i meta-programming różnią się realnie. Zwłaszcza pod statyczną kompilacją.
**Tagi:** typing, syntax, groovy3+

## Q-GRV-002 [bloom: recall]
**Pytanie:** Czym jest GString i kiedy NIE jest tym samym co `String`?
**Modelowa odpowiedź:** GString to interpolowany string z `"…${expr}…"`. Backed by `org.codehaus.groovy.runtime.GStringImpl` — przechowuje listę stałych fragmentów i listę wartości do wstawienia, materializuje się leniwie przez `toString()`. Kiedy nie jest `String`: 1) jako klucz mapy — `gstring as key` zachowuje się inaczej niż jego materialized String (różny `hashCode`), 2) JDBC z GString — Groovy ma osobną integrację, GString daje SQL z parametrami zamiast string concat, 3) jak interpolowane wyrażenie się zmieni między momentem stworzenia a `toString()`, GString to widzi (lazy). Konwersja: `"…${x}…".toString()` lub `"$x" as String`.
**Pułapka rozmowna:** SQL injection — `sql.execute("select * from t where id = ${userInput}")` z `groovy.sql.Sql` jest BEZPIECZNE (parametryzowane), ale `sql.execute("select * from t where id = ${userInput}".toString())` JEST DZIURĄ. Demonstruje, że GString ≠ String matters.
**Tagi:** strings, gstring, sql-injection

## Q-GRV-003 [bloom: recall]
**Pytanie:** Jaka jest różnica między `==` a `equals()` w Groovym?
**Modelowa odpowiedź:** W Groovym `==` to NIE jest reference equality jak w Javie. Groovy `==` wywołuje `equals()` (z dodatkowym null-safety: `a == b` zwraca `true` jeśli oba są null, `false` jeśli jeden jest null). `is()` to to czego oczekujesz po Java `==` — reference equality. Czyli: Groovy `a == b` ≈ `Objects.equals(a, b)`, Groovy `a.is(b)` ≈ Java `a == b`. Praktycznie nigdy nie używasz `is()`, chyba że celowo sprawdzasz tożsamość referencji.
**Pułapka rozmowna:** Programiści po Javie odruchowo piszą `if (foo == null)` myśląc o reference. Działa, bo Groovy `==` jest null-safe i `null.equals(x)` nie wybucha. Ale dla `if (a == b)` gdzie oba mogą być null — Groovy daje true, Java NPE.
**Tagi:** equality, semantics, java-interop

## Q-GRV-004 [bloom: recall]
**Pytanie:** Wymień 4 najczęściej używane metody na kolekcjach w Groovym i krótko opisz.
**Modelowa odpowiedź:** 1) `each { }` — iteracja, zwraca tę samą kolekcję, mutuje przez closure (side effects). 2) `collect { }` — map: zwraca nową listę z wynikami closure'a. 3) `findAll { }` — filter: zwraca nową listę elementów spełniających predykat. 4) `inject(initial) { acc, el -> }` — reduce: agregacja akumulatorem (np. suma, max, build map). Bonus: `groupBy { }` (Map klucz→lista), `find { }` (pierwszy match), `any { }` / `every { }`, `sort { }`, `unique`. To są extension methods z DefaultGroovyMethods, dostępne na każdej `Collection`.
**Pułapka rozmowna:** Często mylone: `findAll` (filter) vs `find` (pierwszy hit). `collect` ma return value, `each` zazwyczaj nie używasz dla return.
**Tagi:** collections, fp, dgm

## Q-GRV-005 [bloom: recall]
**Pytanie:** Co to jest closure w Groovym i czym różni się od metody?
**Modelowa odpowiedź:** Closure to obiekt typu `groovy.lang.Closure` — first-class function. Może przechwytywać zmienne z otoczenia (lexical scope), być przekazany jako argument, zwrócony z funkcji, kompozycja (`>>`), curry. Różni się od metody: metoda jest częścią klasy i wymaga obiektu (lub `static`); closure to pole/zmienna, można ją przypisać do `def x = { … }`. Closure ma 4 referencje: `this` (klasa, w której closure powstała), `owner` (zewnętrzna closure lub klasa), `delegate` (target dla method calls — zmienialny, używany w DSL builderach). `@DelegatesTo` to annotation typowania delegate'a dla static checking.
**Pułapka rozmowna:** Lambda Javy ≠ closure Groovy. Lambda Javy to `Function<T,R>` interfejs i nie ma `delegate`. Próba użycia `@CompileStatic` z DSL builderami (Markup, Json) wymaga `@DelegatesTo` żeby kompilator wiedział co jest delegatem.
**Tagi:** closures, fp, dsl

## Q-GRV-006 [bloom: recall]
**Pytanie:** Co robi `@Immutable` w Groovym?
**Modelowa odpowiedź:** AST transformation. Generuje: 1) wszystkie pola `private final`, 2) `@TupleConstructor` (konstruktor pozycyjny) i `@MapConstructor`, 3) `equals` i `hashCode` po polach, 4) `toString`, 5) blokadę setterów (klasa staje się effectively immutable), 6) defensywne kopie kolekcji (zawiera w niemodyfikowalne wrappers). To jest meta-annotation łącząca kilka transformów. Od Groovy 4 nazywa się `@groovy.transform.Immutable` (stary `@Immutable` z `groovy.transform` jest deprecated/usunięty zależnie od wersji). Przykład: `@Immutable class Money { BigDecimal amount; String currency }`.
**Pułapka rozmowna:** `@Immutable` nie obejmuje rekursywnie — pole typu `List` jest opakowane w unmodifiable, ale jeśli to `List<MutableThing>`, sam thing wciąż mutowalny. Trzeba `MutableThing` też uczynić immutable.
**Tagi:** ast-transform, immutability, dataclass

## Q-GRV-007 [bloom: recall]
**Pytanie:** Czym jest Spock i jak wygląda jego najprostszy test?
**Modelowa odpowiedź:** Spock to framework testowy dla Groovy/Javy z DSL opartym na blokach. Test (a właściwie `Specification`) ma struktury `given:` (setup), `when:` (akcja), `then:` (assercje), opcjonalnie `expect:` (skróty), `where:` (data driven). Przykład: `def "suma 2 i 2 daje 4"() { expect: 1 + 1 == 2 }`. Albo: `def "kalkulator"() { given: def calc = new Calc(); when: def r = calc.add(2, 3); then: r == 5 }`. Spock ma wbudowane mockowanie (`Mock(Foo)`), data tables w `where:` z `|`, i `@Unroll` żeby generować osobny raport dla każdego wiersza. Asercje są implicit — w `then:` każda linia to asercja.
**Pułapka rozmowna:** „W jaki sposób Spock waliduje exception?" → `then: thrown(IllegalArgumentException)` (a nie try-catch). I `where:` blok generuje osobne testy, więc nazwa metody nie musi być unique.
**Tagi:** testing, spock

## Q-GRV-008 [bloom: recall]
**Pytanie:** Co dał Groovy 5.0 i kiedy się ukazał?
**Modelowa odpowiedź:** Groovy 5.0 wyszedł w sierpniu 2025. Najważniejsze: 1) **Operator implikacji `==>`** — DSL-friendly, np. `(x > 0) ==> println('positive')`, semantyka jak `if`. 2) **`partitionPoint`** na listach — binary search find first index where predicate true. 3) **`subList(Range)`** — `list.subList(2..5)` zamiast Java-style `list.subList(2,6)`. 4) **`@OperatorRename`** — annotation pozwalająca nazwać operatory (np. własna semantyka dla `+`/`-`). 5) **JEP-512 compact source files** — wsparcie dla compact main bez explicit class. 6) **JLine 3 REPL** — nowy `groovysh`. 7) **JDK 25 support** (LTS). 8) **~350 nowych extension methods** w DGM — np. `lazy()` na iteratorach, więcej operacji na strumieniach. 9) **Lazy iterators** — niektóre operacje teraz lazy by default (memory wins).
**Pułapka rozmowna:** Łatwo pomylić daty — Groovy 4.0 to styczeń 2022. Groovy 5.0 to sierpień 2025. Wymaga JDK 11+ (preferowany 17+). „Czy 5.0 łamie kompatybilność?" — tak, kilka deprecation removed, ale w większości drop-in dla 4.x.
**Tagi:** groovy5, release

---

## Q-GRV-009 [bloom: understand]
**Pytanie:** Wytłumacz, jak działa `delegate` w closure i po co jest w builderach typu MarkupBuilder.
**Modelowa odpowiedź:** Closure ma trzy obiekty rozdzielające scope: `this` (klasa lexical), `owner` (najbliższa zewnętrzna closure lub `this`), `delegate` (target dla nieopisanych method calls — domyślnie `owner`). Strategia rozwiązywania: `OWNER_FIRST`, `DELEGATE_FIRST`, `OWNER_ONLY`, `DELEGATE_ONLY`. Buildery (MarkupBuilder, JsonBuilder, Gradle DSL) ustawiają `delegate` na siebie i strategię `DELEGATE_FIRST`. Wtedy w `xml.book { title 'Foo' }` wywołanie `title 'Foo'` najpierw próbuje znaleźć metodę `title` w MarkupBuilder (delegacie), znajduje `methodMissing` lub specjalny handler, generuje `<title>Foo</title>`. Bez delegate-a closure musiałaby explicite mówić `builder.title 'Foo'`. To fundament Groovy DSL.
**Pułapka rozmowna:** Pod `@CompileStatic` delegate magic przestaje działać bez `@DelegatesTo` — kompilator nie wie co jest delegatem i odrzuca call. To jest popularne źródło bólu przy migracji ze stuff dynamic na static.
**Tagi:** closures, dsl, delegates, advanced

## Q-GRV-010 [bloom: understand]
**Pytanie:** Co dokładnie robi `@CompileStatic` i czego się traci?
**Modelowa odpowiedź:** `@CompileStatic` wymusza statyczną kompilację — Groovy generuje bytecode taki jak Java (bez przechodzenia przez `MetaClass` i ScriptBytecodeAdapter). Zyskujesz: 1) ~10x szybsze metody, 2) compile-time type checking jak w Javie, 3) brak dynamic dispatch, 4) lepsze IDE support. Tracisz: 1) `methodMissing`, `propertyMissing` — nie zadziałają (target wymaga known type), 2) `ExpandoMetaClass` mods — ignorowane, 3) DSL-e bez `@DelegatesTo` — kompilator odrzuci, 4) `def` zachowuje się jak `Object`, więc miss-on-the-spot zwraca błąd zamiast late binding. Można aplikować na klasę (cała) lub metodę (selectively). Jest też `@TypeChecked` — sprawdza typy ale wciąż używa dynamic dispatch (mniej restrykcyjny).
**Pułapka rozmowna:** „Kiedy używać?" — produkcyjny kod, hot paths, integracje z Javą. „Kiedy nie?" — DSL-e, scripting, meta-programming. W praktyce: `@CompileStatic` jako default w prod kodzie, lokalnie `@CompileDynamic` na metodach co rzeczywiście potrzebują dynamiki.
**Tagi:** static-compilation, performance, compile-time

## Q-GRV-011 [bloom: understand]
**Pytanie:** Jak działa MOP w Groovym? Daj przykład `methodMissing`.
**Modelowa odpowiedź:** MOP (Meta-Object Protocol) — każdy obiekt Groovy ma `MetaClass` (instance of `MetaClass`), pośredniczący między call-site a faktyczną metodą. Wywołanie `obj.foo()` przechodzi: 1) `MetaClass.invokeMethod(obj, "foo", args)`, 2) jeśli nie znajdzie — `obj.methodMissing("foo", args)`. Możesz nadpisać `methodMissing` żeby reagować na nieznane metody. Przykład: dynamiczne pricingi
```groovy
class PriceList {
  def methodMissing(String name, args) {
    if (name.startsWith('priceFor')) {
      def country = name - 'priceFor'
      return calculatePrice(country, args)
    }
    throw new MissingMethodException(name, getClass(), args)
  }
}
def p = new PriceList()
p.priceForPL(100)  // wywołuje calculatePrice('PL', [100])
```
Analogicznie: `propertyMissing(name)` dla `obj.foo`. Można też podmienić cały MetaClass instancji albo klasy globalnie przez `ExpandoMetaClass`.
**Pułapka rozmowna:** MOP nie działa pod `@CompileStatic`. Dodatkowo: nadpisany `methodMissing` musi rzucać `MissingMethodException` dla nieobsłużonych przypadków, inaczej bardzo trudne błędy.
**Tagi:** mop, meta-programming, advanced

## Q-GRV-012 [bloom: understand]
**Pytanie:** Czym różni się `XmlSlurper` od `XmlParser`? Kiedy którego użyć?
**Modelowa odpowiedź:** Oba parsują XML i dają nawigowalną strukturę. **`XmlParser`** zwraca `groovy.util.Node` — pełna mutable tree, modyfikacja działa, koszt pamięciowy większy (cały DOM in-memory). **`XmlSlurper`** zwraca `GPathResult` — leniwy, deferred evaluation; nawigacja po ścieżkach (`xml.book.title`) zwraca proxy, który dopiero przy `.text()` materializuje wartość. Czytanie i query — szybsze i lżejsze pamięciowo. Modyfikacja XML jest trudniejsza (immutable view), trzeba `bind` lub StreamingMarkupBuilder. **Reguła kciuka:** czytasz dużo XML i tylko query — `XmlSlurper`. Czytasz i modyfikujesz — `XmlParser`. Pricing engine consuming external feed (np. ERP) — Slurper. Generation z mutacją — Parser albo Builder.
**Pułapka rozmowna:** Namespace handling — oba wymagają `declareNamespace` lub `*:tag` do query'owania nodów z namespace. Ignorowanie tego daje cichą lukę: pusty result, nie błąd.
**Tagi:** xml, parsing, gpath

## Q-GRV-013 [bloom: understand]
**Pytanie:** Co to jest `@Memoized` i jakie ma ograniczenia?
**Modelowa odpowiedź:** `@Memoized` to AST transformation cachująca return value metody (lub closure'a) per zestaw argumentów. Generowana przez transform: hidden `Map` (lub `LRUCache` z parametrem) jako pole, na początku metody check w cache, jeśli hit — return; jeśli miss — wykonaj, zapisz, return. Ograniczenia: 1) **argumenty muszą być hash-able i stable** — własne klasy bez sensownego `equals/hashCode` zepsują cache. 2) **side effects** — metoda z side effects nie powinna być memoized; cache zwraca z pierwszego wywołania, side effect się nie powtórzy. 3) **mutable args** — modyfikacja argumentu po wywołaniu da dziwne hits. 4) **memory leak** — cache rośnie, jeśli nie używasz `@Memoized(maxCacheSize=N)` lub `protectedCacheSize`. 5) **nie thread-safe** by default — przy concurrent access trzeba external sync albo `@Memoized(method=...)` z odpowiednim backendem.
**Pułapka rozmowna:** Aplikacja na metody z `Date.now()` lub innymi dynamicznymi inputami — nieintuicyjne, bo argumenty się różnią. Memoization na losowych funkcjach — daje to samo zawsze (ten kto pyta sprawdza zrozumienie tego, że memoization to determinism).
**Tagi:** caching, ast-transform, performance

## Q-GRV-014 [bloom: understand]
**Pytanie:** Wytłumacz różnicę między trait a abstract class w Groovym.
**Modelowa odpowiedź:** Trait (Groovy 2.3+) to `interface z implementacją + state`. Klasa może implementować wiele traits (multiple inheritance dla zachowania). Abstract class — pojedyncze dziedziczenie, ale full lifecycle (konstruktor, init blocks, super-chain). Trait jest stateful: może mieć pola, ale są stored per implementing class (mangled name). Trait methods są domyślnie `public`. Linearisation kolejności — Groovy stosuje C3-like linearization gdy traits się nakładają (deterministic order). Java interop: trait kompiluje się do interface + helper class. **Kiedy trait:** mixiny zachowania (logging, audit, validation), wieloosiowa kompozycja. **Kiedy abstract class:** template method pattern z wymuszonym konstruktorem, hierarchia z konkretnym shared state. **Kiedy interface:** kontrakt bez stanu, Java interop.
**Pułapka rozmowna:** Trait nie obsługuje konstruktorów — jeśli potrzebujesz `super(args)`, traitów nie złożysz tak jak abstract class. I trait z polem ma stan per-instance, ale storage jest mangled — debugowanie field `myField` może pokazywać jako `MyTrait__myField`.
**Tagi:** traits, oop, multiple-inheritance

## Q-GRV-015 [bloom: understand]
**Pytanie:** Operator Elvis (`?:`) i safe navigation (`?.`) — co robią i czym się różnią?
**Modelowa odpowiedź:** **Safe navigation `?.`**: `a?.b` zwraca `a.b` jeśli `a != null`, w przeciwnym razie zwraca `null` (bez NPE). Łańcuch: `a?.b?.c?.d` propaguje `null` przez całość. **Elvis `?:`**: `a ?: b` zwraca `a` jeśli truthy (non-null, non-zero, non-empty for collections — Groovy truth), w przeciwnym razie `b`. Razem: `a?.b ?: defaultValue` to popularny idiom — „jeśli a jest null lub a.b jest falsy, daj default". Elvis-assignment (`?=`, Groovy 3+): `x ?= 'default'` przypisuje tylko jeśli `x` falsy. **Różnica:** `?.` operuje na property/method access (NPE prevention), `?:` to pełen value selector (default fallback).
**Pułapka rozmowna:** Groovy truth — `0`, `""`, pusta lista, `null`, `false` są wszystkie falsy. Czyli `count ?: 10` da 10 też dla `count == 0`. Czasem chcesz `count != null ? count : 10` żeby zero przepuścić.
**Tagi:** operators, null-safety, syntax

## Q-GRV-016 [bloom: understand]
**Pytanie:** Co to jest `groupBy` i `inject` na kolekcjach? Kiedy które?
**Modelowa odpowiedź:** **`groupBy { }`**: zwraca `Map` gdzie klucze to wynik closure'a, a wartości — listy elementów dla danego klucza. `[1,2,3,4].groupBy { it % 2 }` = `[1:[1,3], 0:[2,4]]`. Idealne do partitioning po kategoriach. Można użyć `groupBy(closure1, closure2)` dla nested grouping. **`inject(initial) { acc, el -> nowy_acc }`**: redux/fold. Pierwszy argument to initial value (lub bez — pierwszy element), closure dostaje akumulator i bieżący element, zwraca nowy akumulator. `[1,2,3].inject(0) { acc, x -> acc + x }` = 6. Bardziej generyczny niż `groupBy` — można nim zbudować praktycznie wszystko inne. **Reguła:** `groupBy` jeśli chcesz Map<K, List<V>> z partycjonowaniem. `inject` jeśli chcesz pojedynczy wynik (suma, max, accumulator string, custom map). Często `groupBy` + `collectEntries` daje to co chcesz wygodniej niż `inject`.
**Pułapka rozmowna:** `inject` w Groovym 5.0 ma alias `fold` (z extension methods). `groupBy` zwraca `LinkedHashMap` (zachowuje kolejność wstawień), więc deterministic ordering bez sortowania. `inject` startuje od initial — bez initial bierze pierwszy element jako start, więc dla pustej listy bez initial leci NPE.
**Tagi:** collections, fp, dgm

---

## Q-GRV-017 [bloom: apply]
**Pytanie:** Mając listę pozycji faktury `invoiceLines = [[id:1, net:100, vat:23, country:'PL'], [id:2, net:50, vat:8, country:'PL'], [id:3, net:200, vat:23, country:'DE']]`, napisz w Groovym wyrażenie zwracające sumę netto pogrupowaną po stawce VAT, posortowaną malejąco po sumie.
**Modelowa odpowiedź:**
```groovy
invoiceLines
  .groupBy { it.vat }
  .collectEntries { vat, lines -> [vat, lines.sum { it.net }] }
  .sort { -it.value }
// wynik: [23: 300, 8: 50]
```
Krok po kroku: `groupBy { it.vat }` → mapa `{23: [line1, line3], 8: [line2]}`. `collectEntries` mapuje pary klucz-wartość na nową mapę, gdzie wartość to suma netto z `sum { it.net }`. `sort { -it.value }` sortuje po wartości malejąco (negacja → reverse natural order). Można też `sort(false) { it.value }` lub `toSorted` żeby nie mutować. Alternatywa z `inject`:
```groovy
def totals = [:]
invoiceLines.each { totals[it.vat] = (totals[it.vat] ?: 0) + it.net }
totals.sort { -it.value }
```
Pierwsza wersja bardziej idiomatyczna, druga mutująca i wymaga init zera.
**Pułapka rozmowna:** Wielu kandydatów pisze pętlę `for` jak w Javie — działa, ale w Groovym to anti-pattern. „Sort malejąco" — łatwo pomylić: `sort { -it.value }` vs `sort { a, b -> b.value <=> a.value }`. Oba poprawne. `sort` mutuje listę in-place; `toSorted` zwraca nową.
**Tagi:** collections, fp, pricing, idiom

## Q-GRV-018 [bloom: apply]
**Pytanie:** Napisz Spock test dla klasy `PriceCalculator` z metodą `applyMargin(BigDecimal cost, BigDecimal marginPct)`. Pokryj 3 przypadki: normalny, zerowy margin, ujemny margin (powinien rzucić `IllegalArgumentException`).
**Modelowa odpowiedź:**
```groovy
import spock.lang.Specification
import spock.lang.Unroll

class PriceCalculatorSpec extends Specification {

  PriceCalculator calc = new PriceCalculator()

  @Unroll
  def "applyMargin: cost=#cost, margin=#margin -> #expected"() {
    expect:
    calc.applyMargin(cost, margin) == expected

    where:
    cost   | margin || expected
    100.00 | 20.00  || 120.00
    100.00 | 0.00   || 100.00
    50.00  | 50.00  || 75.00
  }

  def "applyMargin rzuca dla ujemnego marginu"() {
    when:
    calc.applyMargin(100.00, -5.00)

    then:
    def ex = thrown(IllegalArgumentException)
    ex.message.contains('margin')
  }
}
```
Kluczowe elementy: `Specification` jako baza, `expect:` blok dla data-driven, `where:` z tabelą, `@Unroll` rozbija na osobne testy w raporcie, `thrown(...)` w `then:` waliduje exception. Można też dorzucić `BigDecimal` precision asserts (`expected.scale() == 2`).
**Pułapka rozmowna:** Często padają testy gdzie ktoś używa `Double` zamiast `BigDecimal` — w pricingu to zbrodnia (precision losses). Drugi błąd: `assert` w `expect:` jest redundantny — Spock robi assert implicit w blokach `expect:`/`then:`.
**Tagi:** spock, testing, pricing

## Q-GRV-019 [bloom: apply]
**Pytanie:** Sparsuj poniższy XML w Groovym i wyciągnij listę produktów z ceną netto > 100. XML: `<catalog><product id="1"><name>Foo</name><price net="50"/></product><product id="2"><name>Bar</name><price net="150"/></product></catalog>`. Pokaż dwie wersje: XmlSlurper i XmlParser, krótko porównaj.
**Modelowa odpowiedź:**
```groovy
// XmlSlurper — lazy, idiomatyczny dla query
def xml = new XmlSlurper().parseText(xmlString)
def expensive = xml.product
  .findAll { it.price.@net.toBigDecimal() > 100 }
  .collect { [id: it.@id.toString(), name: it.name.text(), net: it.price.@net.toBigDecimal()] }
// [[id:'2', name:'Bar', net:150]]

// XmlParser — DOM, mutowalne
def doc = new XmlParser().parseText(xmlString)
def expensive2 = doc.product.findAll { p -> 
    new BigDecimal(p.price[0].'@net') > 100 
  }.collect { p -> 
    [id: p.'@id', name: p.name.text(), net: new BigDecimal(p.price[0].'@net')] 
  }
```
Różnice: w Slurperze `it.@net` daje `GPathResult`, na którym wołasz `.toBigDecimal()` lub `.text()`. W Parserze `p.'@net'` daje stringa od razu. Slurper jest leniwy — query nie materializuje całego drzewa; Parser już ma drzewo. Slurper lepszy do read-only feed processing (np. parsing pricing list z ERP). Parser jeśli musisz coś modyfikować i serializować z powrotem.
**Pułapka rozmowna:** `xml.product[0]` w Slurperze daje `GPathResult` po pierwszym elemencie. `xml.product[0].text()` materializuje. Próba `as String` na `GPathResult` może dać niespodziewane wyniki — używaj `.text()` lub `.toString()`. Atrybuty: `@net` (Slurper) vs `'@net'` (Parser).
**Tagi:** xml, parsing, pricing, gpath

## Q-GRV-020 [bloom: apply]
**Pytanie:** Mając mapę cenników `{'PL': 100, 'DE': 120, 'UK': 130}`, napisz closure która dla danego kraju zwraca cenę z 10% rabatem dla 'PL', a dla pozostałych pełną cenę. Użyj curryingu.
**Modelowa odpowiedź:**
```groovy
def priceFor = { Map prices, String country ->
  def base = prices[country] ?: 0
  country == 'PL' ? base * 0.9 : base
}

def myPrices = priceFor.curry(['PL': 100, 'DE': 120, 'UK': 130])
myPrices('PL')  // 90.0
myPrices('DE')  // 120
myPrices('FR')  // 0 (nieznany kraj)
```
Currying: `closure.curry(arg)` zwraca nową closure z fixed pierwszym argumentem. Można `rcurry` (right-side) i `ncurry(idx, arg)` (n-th). To pattern do partial application — przydatny np. w pricingu gdzie cennik fixujesz raz, a kraje przychodzą wiele razy. Można też `@Memoized` na to: jak ten sam kraj pyta wiele razy — wynik cached.
**Pułapka rozmowna:** `priceFor.curry(map)('PL')` ≠ `priceFor(map, 'PL')` w Groovym — pierwsze tworzy closure, potem ją woła, drugie wywołuje od razu. Currying daje ci możliwość zwrócenia closure'a do innej części kodu, gdzie cennik nie jest dostępny w scope.
**Tagi:** closures, currying, pricing, fp

## Q-GRV-021 [bloom: apply]
**Pytanie:** Napisz `@Immutable` klasę `Money` z polami `amount: BigDecimal` i `currency: String`. Dodaj operator `+` żeby `Money(100, 'PLN') + Money(50, 'PLN')` dawał `Money(150, 'PLN')`, a próba dodania różnych walut rzucała wyjątek.
**Modelowa odpowiedź:**
```groovy
import groovy.transform.Immutable

@Immutable
class Money {
  BigDecimal amount
  String currency

  Money plus(Money other) {
    if (this.currency != other.currency) {
      throw new IllegalArgumentException("Cannot add ${this.currency} and ${other.currency}")
    }
    new Money(amount: this.amount + other.amount, currency: this.currency)
  }
}

def a = new Money(100, 'PLN')
def b = new Money(50, 'PLN')
def c = a + b   // Money(150, 'PLN')
def d = new Money(20, 'EUR')
a + d   // throws IllegalArgumentException
```
Operator overloading w Groovym: `+` mapuje na metodę `plus(other)`. Inne: `-` (`minus`), `*` (`multiply`), `/` (`div`), `==` (`equals`), `<=>` (`compareTo`), `[]` (`getAt`/`putAt`). `@Immutable` daje konstruktor named-args (`new Money(amount: 100, currency: 'PLN')`), `equals/hashCode` po polach, immutable. Bonus: dla full pricingu dorzuciłbyś `@CompileStatic` żeby `+` było type-checked.
**Pułapka rozmowna:** Część kandydatów próbuje overridować literal `+` — to nie działa. Trzeba metoda `plus(...)`. Druga pułapka: w `@Immutable` setterów nie ma, więc `m.amount = 999` rzuci `ReadOnlyPropertyException`.
**Tagi:** operator-overload, immutable, pricing

## Q-GRV-022 [bloom: apply]
**Pytanie:** Wygeneruj XML cennika z mapy `[[country:'PL', price:100], [country:'DE', price:120]]` używając MarkupBuilder, tak żeby wynik był `<priceList><item country="PL"><price>100</price></item>...</priceList>`.
**Modelowa odpowiedź:**
```groovy
import groovy.xml.MarkupBuilder

def data = [[country:'PL', price:100], [country:'DE', price:120]]
def writer = new StringWriter()
def xml = new MarkupBuilder(writer)
xml.priceList {
  data.each { entry ->
    item(country: entry.country) {
      price entry.price
    }
  }
}
println writer.toString()
```
Wynik:
```xml
<priceList>
  <item country='PL'>
    <price>100</price>
  </item>
  <item country='DE'>
    <price>120</price>
  </item>
</priceList>
```
Mechanika: `MarkupBuilder` używa MOP — `priceList { ... }` to call do `methodMissing`, generuje element. Atrybut: pierwsza Map w argumentach → atrybuty XML. Tekst: jak metoda dostaje single value (`price entry.price`) → text content. `MarkupBuilder` można skonfigurować (`escapeAttributes`, `useDoubleQuotes`).
**Pułapka rozmowna:** Pod `@CompileStatic` MarkupBuilder przestanie działać bez `@DelegatesTo` — bo cały DSL opiera się na MOP. Druga pułapka: `xml.priceList { ... }` — jeśli zapomnisz `priceList` na froncie, generujesz elementy bez korzenia, co dla XML jest invalid.
**Tagi:** xml, builder, dsl, pricing

## Q-GRV-023 [bloom: apply]
**Pytanie:** Użyj nowego `partitionPoint` z Groovy 5.0 do znalezienia indeksu pierwszego elementu listy posortowanej po cenie, którego cena przekracza 100.
**Modelowa odpowiedź:**
```groovy
def items = [[id:1, price:50], [id:2, price:80], [id:3, price:120], [id:4, price:200]]
def idx = items.partitionPoint { it.price <= 100 }
// idx == 2 (pierwszy element z price > 100 to items[2] = {id:3, price:120})
```
`partitionPoint` zakłada że lista jest posortowana w taki sposób, że predicate najpierw true, potem false (czyli predicate jest „decreasing"). Robi binary search → O(log n). To jest `lower_bound` z C++ STL semantically. Bez tego musielibyśmy `findIndexOf`, który jest liniowy. Praktyczne zastosowanie w pricingu: znaleźć tier cenowy w cenniku tier-based, znaleźć pricing rule w cenniku posortowanym po dacie.
**Pułapka rozmowna:** `partitionPoint` nie sprawdza czy lista jest faktycznie posortowana — daje undefined result jeśli nie jest. Wymaga monotonicznego predicate. Działa na `List`, nie na `Iterable` (potrzebuje random access dla binary search).
**Tagi:** groovy5, collections, performance, pricing

## Q-GRV-024 [bloom: apply]
**Pytanie:** Zamień JSON `{"customers":[{"id":1,"orders":[{"total":100},{"total":200}]},{"id":2,"orders":[{"total":50}]}]}` na mapę `customerId → suma orderów`. Użyj JsonSlurper.
**Modelowa odpowiedź:**
```groovy
import groovy.json.JsonSlurper

def json = '{"customers":[{"id":1,"orders":[{"total":100},{"total":200}]},{"id":2,"orders":[{"total":50}]}]}'
def parsed = new JsonSlurper().parseText(json)

def totals = parsed.customers.collectEntries { c ->
  [c.id, c.orders.sum { it.total } ?: 0]
}
// totals = [1: 300, 2: 50]
```
`JsonSlurper` parsuje JSON do `Map`/`List`/value. `collectEntries` mapuje listę na mapę dwustronnie. `c.orders.sum { it.total }` agreguje listę. `?: 0` na wypadek braku zamówień. Alternatywnie `inject`: `c.orders.inject(0) { acc, o -> acc + o.total }`.
**Pułapka rozmowna:** `sum()` na pustej liście zwraca `null` — stąd `?: 0`. W produkcji: nigdy nie ufaj że JSON będzie miał oczekiwaną strukturę — `?.` na `customers?.collectEntries` żeby chronić przed pustym/null root. Pricing data feeds bywają niespójne.
**Tagi:** json, parsing, pricing, fp

---

## Q-GRV-025 [bloom: analyze]
**Pytanie:** Twój zespół debatuje czy całą codebase pricingu oznaczyć `@CompileStatic`. Argumenty „za" i „przeciw"? Jaką decyzję byś podjął i dlaczego?
**Modelowa odpowiedź:** **Za:** 1) ~10x speedup na hot paths (kalkulacja cen w pętli batch), 2) compile-time type checking — błędy wcześniej niż w runtime, 3) lepszy IDE auto-complete i refactoring, 4) kod łatwiejszy do migracji do Java/Kotlin, 5) brak surprise'ów z metaprogramming wykonującego się w runtime. **Przeciw:** 1) DSL-e (MarkupBuilder, JsonBuilder, Spock) wymagają `@DelegatesTo` lub przejdą przez `@CompileDynamic`, 2) traci się elastyczność `methodMissing`, 3) `def` zachowuje się jak `Object` — kod z `def` bez explicit types daje compile errors, 4) migracja może wymagać przepisania kawałków, 5) testy Spock — niektóre patterns (mocks z dynamic methods) wymagają adjustment. **Decyzja:** TAK, `@CompileStatic` na cały production code z wyjątkami: a) DSL-e (Markup, Json builder code) — `@CompileDynamic` lokalnie, b) Spock specs — Spock ma własny compile mode, zostaw w spokoju, c) skrypty / one-shot scripts — niech zostają dynamic. Dodatkowo: ustaw `@CompileStatic` na poziomie packagu przez `package-info.groovy` z `@PackageScope` lub Gradle config, żeby nowy kod automatycznie miał static. Mierzony case: w pricingu kalkulacje są hot path, brak dynamic dispatch realnie ratuje CPU.
**Pułapka rozmowna:** Argument „ale Groovy jest dynamic" — to było prawdziwe w 2007. Dziś Groovy z `@CompileStatic` to świadomy wybór statycznej kompilacji z lepszym DX niż czysta Java (collections, closures, named args). Dynamic dla pricingu to ryzyko: przepisz `customer.discount.rate` na coś nieistniejącego — w runtime NPE w środku batch kalkulacji.
**Tagi:** architecture, performance, trade-offs, pricing

## Q-GRV-026 [bloom: analyze]
**Pytanie:** Porównaj 3 sposoby na cache w Groovym: ręczna `Map`, `@Memoized`, biblioteka (Caffeine/Guava). Kiedy który?
**Modelowa odpowiedź:** **Ręczna `Map`:** najprostsza, full control, ale brak limitów (memory leak), brak TTL, brak eviction policy. Dobre dla: małe, predictable input space, krótko żyjący scope (np. w trakcie jednej kalkulacji batch). **`@Memoized`:** AST-generated cache. Trywialne w użyciu (`@Memoized(maxCacheSize=1000)`). Wbudowane LRU jeśli z size. Brak TTL. Brak refresh-on-write. Dobre dla: pure functions z bounded input space (np. lookup country → currency). **Caffeine/Guava:** profesjonalne. TTL (`expireAfterWrite`/`expireAfterAccess`), max size, weak refs, async loading, refresh, statystyki (hit rate, miss rate), thread-safe. Koszt: dependencja, więcej kodu setup. Dobre dla: produkcyjne pricing caches (cennik z bazy z TTL 5 min), distributed loading, observability. **Decyzja w pricingu:** Caffeine dla cenników (TTL + size + statistics), `@Memoized` dla konwersji jednostek czy lookup tablic statycznych, ręczna mapa dla scratch w jednym requeście.
**Pułapka rozmowna:** „Czy `@Memoized` jest thread-safe?" — domyślnie używa `Collections.synchronizedMap` ale to nie jest najlepsze pod high concurrency. Caffeine ma optymalizowany BoundedLocalCache. „Czy `Map` w polu klasy?" — jeśli singleton — leak forever. Stąd preferencja Caffeine.
**Tagi:** caching, performance, architecture, trade-offs

## Q-GRV-027 [bloom: analyze]
**Pytanie:** Twoja aplikacja parsuje 10 GB XML feed dziennie z systemu ERP. Wybierz strategię: XmlParser, XmlSlurper, StAX (`XMLStreamReader`) lub coś innego. Uzasadnij.
**Modelowa odpowiedź:** **XmlParser** odpada — załaduje całość do pamięci, OOM. **XmlSlurper** jest leniwy w nawigacji, ale wciąż buduje parsed tree wewnętrznie — dla 10 GB to też za dużo. **StAX (`XMLStreamReader`)** to streaming, pull-parser z O(constant) memory — tu wygrywa. Strategia: czytasz event po evencie, na `START_ELEMENT` zaczynasz akumulować dane do struktury, na `END_ELEMENT` kompletny rekord przekazujesz dalej (do bazy / kolejki / batch processora) i czyścisz akumulator. **Realna implementacja w Groovym:** `XMLInputFactory.newInstance().createXMLStreamReader(inputStream)`, pętla `while reader.hasNext()`. Można też **Jackson Streaming** dla JSON. **Alternatywa:** podzielić feed na chunks po stronie ERP jeśli to możliwe (lepszy fix niż obchodzenie). **Bonus z Groovy 5.0:** lazy iterators na collections — pomaga w transformacji ale nie przy initial parsing. Dodatkowo: jeśli feed ma dobrze zdefiniowany namespace i schema, JAXB unmarshall per chunk + StAX driver dla początkowego splitu.
**Pułapka rozmowna:** „Skąd 10 GB?" — typowy ERP nightly export. „Czy nie da się .gz?" — można, ale parsing wciąż musi być stream. „Czemu nie split na pliki?" — można, ale wymaga ingerencji po stronie ERP, niezawsze możliwe. „Czy GZIPInputStream nad XMLStreamReader działa?" — tak, normalnie chainujesz strumieni.
**Tagi:** xml, performance, scaling, pricing, architecture

## Q-GRV-028 [bloom: analyze]
**Pytanie:** Programista X napisał `def calculatePrice = { product, customer -> ... }`. Programista Y napisał `BigDecimal calculatePrice(Product product, Customer customer) { ... }`. Klient narzeka na performance. Co byś zrobił first?
**Modelowa odpowiedź:** Zacznę od pytania: **jak często to wołane?** Closure (X) ma overhead per-call: `Closure.call()` przechodzi przez `MetaClass`, dispatch dynamic. Method (Y) z `@CompileStatic` ma JVM-direct dispatch. **Drugi krok — measurement:** profiluj (JFR, async-profiler), nie zgaduj. Jeśli `calculatePrice` jest w hot path (np. wewnątrz pętli na 100k pozycji) — closure traci wolumen na MetaClass overhead, nawet jeśli pojedyncze wywołanie jest tanie. **Praktyczna decyzja:** zostaw closure jeśli to jest wywołane okazjonalnie i daje czytelniejszy DSL/test setup; przejdź na metodę + `@CompileStatic` jeśli to jest produkcyjne core w pętli. **Trzecia rzecz:** dziedzina pricingu — czy nie cachujesz tego co już policzyłeś (`@Memoized` lub Caffeine)? Często „performance problem" w pricingu to brak cache, nie brak `@CompileStatic`. **Czwarta — JVM warmup:** upewnij się że benchmark mierzy po JIT, nie cold path. **Piąta — BigDecimal:** w pricingu BigDecimal jest droższy niż double, ale double jest niedopuszczalny (precision). Optymalizacja przez sensowne `MathContext` (precision, rounding) zamiast pisania w double.
**Pułapka rozmowna:** Naïwna reakcja: „closure zawsze wolniej niż method, podmień". Bez profilowania — zgadywanka. Real bottleneck może być w bazie (n+1 query do customer.discounts), nie w samej kalkulacji. Pozdro Knuthowi.
**Tagi:** performance, profiling, closures, pricing, decision

## Q-GRV-029 [bloom: analyze]
**Pytanie:** W pair-programming review widzisz: `@CompileStatic class PriceService { def discount }`. Co tu nie gra i jak by to poprawić?
**Modelowa odpowiedź:** Kilka rzeczy. **Pierwsza:** `def discount` pod `@CompileStatic` to `Object discount` — traci typing, IDE nie wie co to jest, kompilator nie sprawdzi prawidłowości użycia. **Druga:** `discount` jako instance field bez specyfikacji — może być wstrzyknięte (Spring `@Autowired`?), może być setter-injected, może być default null. **Trzecia:** brak konwencji — w pricing service to powinno być coś typu `DiscountStrategy strategy` albo `BigDecimal defaultDiscount`. **Poprawka 1 — explicite typu:** `BigDecimal discount` lub `DiscountStrategy discountStrategy`. **Poprawka 2 — final/initialization:** jeśli to dependency, użyj konstruktora i `final`: `final DiscountStrategy strategy; PriceService(DiscountStrategy s) { this.strategy = s }`. **Poprawka 3 — visibility:** `private` field, public getter (lub `@Lazy` jeśli computation). **Poprawka 4 — default:** jeśli to wartość konfigurowana, dorzuć domyślną wartość albo wymóg w konstruktorze. **Idealny kod:**
```groovy
@CompileStatic
class PriceService {
  private final DiscountStrategy discountStrategy
  
  PriceService(DiscountStrategy discountStrategy) {
    this.discountStrategy = discountStrategy
  }
  
  BigDecimal calculate(Product p, Customer c) { ... }
}
```
**Pułapka rozmowna:** Ktoś może powiedzieć „w Groovym `def` na polach jest OK". Tak, w dynamicznym Groovym jest. Pod `@CompileStatic` to anty-wzorzec — trzeba albo właściwy typ, albo `@CompileDynamic` lokalnie z uzasadnieniem.
**Tagi:** code-review, static-compilation, design, pricing

## Q-GRV-030 [bloom: analyze]
**Pytanie:** Dzień przed rozmową rekrutacyjną twój kolega mówi: „Groovy to umierający język, nie warto go uczyć". Co odpowiadasz, w sposób który by się sprawdził też na rozmowie?
**Modelowa odpowiedź:** Groovy nie jest growing market share-wise jak Kotlin czy TypeScript, ale jest **żywy w bardzo specyficznych nichach**: 1) **Gradle** — DSL gradle.build to Groovy (i Kotlin DSL alternatywa, ale Groovy nadal dominuje w Java codebase'ach). 2) **Spock** — najlepszy testowy framework dla Java/Groovy projektów. 3) **Jenkins pipelines** — Jenkinsfile to Groovy DSL. 4) **Pricing/integration platforms** — wiele systemów enterprise (zwłaszcza handlowych, ERP, financial) używa Groovy do scriptingu reguł biznesowych — bo dynamic dispatch i DSL możliwości > niż Java. Reżyseria pricingu w czasie rzeczywistym, gdzie reguły zmieniają się tygodniowo, idealnie się tu nadaje. 5) **Apache projects** — Groovy żyje pod Apache Software Foundation, zespół aktywny, Groovy 5.0 (sierpień 2025) to nie pożegnalny release. **Rekruterowi powiem:** „Groovy nie jest językiem do startupu w 2025, ale jest językiem produkcyjnego pricingu, integracji enterprise i build tooling. Jeśli rola jest na pricing engine — Groovy to dokładnie ten język, w którym ta domena żyje od lat. Plus: Java interop trywialny, więc kodu Java koło Groovy nie boli." **Honesty check:** Kotlin server-side rośnie, w wielu rolach dziś dominuje. Ale w specific niches (Gradle, Jenkins, Spock, pricing legacy) Groovy nie tylko żyje, ale dostarcza wartość trudną do zastąpienia.
**Pułapka rozmowna:** Mówić „Groovy to przyszłość JVM" — bzdura, rekruter wyłapie. Mówić „Groovy umiera" — sabotaż własnej rozmowy. Realistyczna pozycja: niche-active, dobrze sprawdza się w roli, na którą aplikujesz.
**Tagi:** career, market, soft-skills

## Q-GRV-031 [bloom: analyze]
**Pytanie:** Na rozmowie rekruter pyta: „Pokaż mi kod produkcyjny, z którego jesteś dumny". Co byś pokazał z Groovy i dlaczego?
**Modelowa odpowiedź:** Wybierz coś co demonstruje **decyzje architektoniczne**, nie składnię. Przykłady: 1) **DSL do reguł pricingowych** — pokazujesz `priceRule { when { customer.tier == 'GOLD' } then { applyDiscount(0.15) } }`. Tu uczysz: builder pattern, `@DelegatesTo` dla static checking, MOP. 2) **Service z `@CompileStatic` + Spock test suite** — pokazujesz że umiesz pisać performant kod i go testujesz. 3) **Migration script Groovy z XML/JSON ERP feed do bazy** — XmlSlurper + groovy.sql.Sql, transactional, idempotent. 4) **AST transformation własna** — jeśli faktycznie napisałeś (rzadko, ale impressive). **Co WAŻNE w tym co opowiadasz:** dlaczego ten design (trade-offs), co byś zrobił inaczej dziś, jak testowałeś. Rekruter nie patrzy na elegancję — patrzy na umiejętność rozumowania o swoim kodzie.
**Pułapka rozmowna:** Pokazać hello-world. Pokazać kod skopiowany z tutoriala bez zrozumienia. Pokazać kod tak skomplikowany że nie umiesz go wytłumaczyć — pull request review-style, rekruter pyta „czemu tu Closure a nie metoda" i topisz się.
**Tagi:** career, soft-skills, architecture

## Q-GRV-032 [bloom: analyze]
**Pytanie:** W pricing engine planujesz dodać nową regułę: „dla klientów z B2B rebatem > 5% nie naliczaj promocji". Omów: gdzie to wstawić (Groovy DSL? Java service? bazy?). Jakie trade-offy?
**Modelowa odpowiedź:** **Wariant A — Java service**: hardcoded w `PromotionService.shouldApply(customer, promotion)`. Plus: typowane, kompilowalne, refactor-friendly, łatwo przetestować. Minus: zmiana reguły = redeploy. Słabe dla biznesu chcącego A/B testować reguły. **Wariant B — Groovy DSL z reguł w pliku/bazie**: reguły jak `rule { when { customer.b2bRebate > 0.05 } then { skipPromotion() } }`, parsowane przez engine (np. własny lub Drools-like). Plus: zmiana bez redeploy, business team może edytować, audytowalne (versioned in git lub DB). Minus: wymaga sandbox (kompilacja źle napisanej reguły może zawieszać prod), wymaga CI/CD wokół reguł (testowanie!), wymaga dyscypliny. **Wariant C — w bazie jako data**: `promotion_rule` table z polami `condition_jsonpath`, `action`. Plus: nikt nie pisze kodu, czystym SQL/UI można edytować. Minus: ekspresywność ograniczona, complex rules robią się nieczytelne. **Decyzja:** zależy od zespołu i tempa zmian. Jeśli reguły zmieniają się rzadko (raz na kwartał) — A. Jeśli często i są complex — B z porządnym audytem. Jeśli proste i edytowane przez non-tech — C. **W pricing platformach klasy enterprise dominuje wariant B z Groovy** (DSL), bo daje balans między mocą a bezpieczeństwem, plus audyt reguł.
**Pułapka rozmowna:** Mówienie „zawsze B, bo elastyczność" — premature flexibility. Real answer ma uwzględniać team capacity, audit requirements, częstotliwość zmian.
**Tagi:** architecture, dsl, pricing, decision, trade-offs
