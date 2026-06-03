# The Codest Stack — question bank

> The Codest (Warsaw software house, ~30 people) recruits for Java/Kotlin. Their public code reveals a specific stack: Java 17, Google Guice DI (not Spring in newer projects), Vavr, Lombok, JUnit 5 + Mockito, AWS Lambda/S3, Slack SDK. This bank targets what you'll see in the interview.

## Scope

- Google Guice DI — AbstractModule, @Named, @Inject, provider binding
- Vavr — Try, Tuple, Tuple2, Option, immutable collections
- Java 17 features — records, var, sealed classes, text blocks, Stream.toList()
- Interface-first design — SlackClient / SimpleSlackClient pattern
- Lombok — @RequiredArgsConstructor, @Slf4j, @Log, @VisibleForTesting
- Assembler pattern (pellse/assembler) — join collections without N+1
- JUnit 5 + Mockito — @ExtendWith, @Mock, @Spy, @InjectMocks, @Captor
- Code quality pipeline — Checkstyle, PMD, Jacoco, Eclipse formatter at validate phase
- AWS Lambda + S3 — serverless.yml, Spring Cloud Function adapter, Jib
- Kotlin basics — since job req mentions Kotlin alongside Java

---

## Q-THECODEST-001 [bloom: recall]
**Question:** Co to jest Google Guice? Jakie trzy główne komponenty go tworzą?
**Model answer:** Guice to lekki framework DI od Google — alternatywa dla Spring IoC, działająca bez XML i bez skanowania classpath. Trzy główne komponenty: (1) **Injector** — kontener, który tworzy i łączy obiekty; (2) **Module** — klasa konfiguracyjna (`AbstractModule.configure()`) gdzie deklarujesz bindingi; (3) **Binding** — mapowanie interfejsu lub klasy na konkretną implementację. W odróżnieniu od Spring, Guice jest strictly type-safe — błędy DI wychodzą na etapie tworzenia Injectora, a nie w runtime przy pierwszym request. Używasz `Guice.createInjector(new MyModule())` w main() jako composition root.
**Interview trap:** Rekruter może spytać „po co Guice skoro macie Spring?". Odpowiedź: Guice jest lżejszy (brak auto-configuration, brak component scan), deterministyczny (explicit bindings), świetny do AWS Lambda gdzie czas cold-start ma znaczenie. The Codest wyraźnie przeszedł z Spring Boot (Java 11) na Guice (Java 17) w nowszych projektach.
**Tags:** guice, di, dependency-injection

## Q-THECODEST-002 [bloom: recall]
**Question:** Co tworzy keyword `record` w Java? Jak wygląda minimalna deklaracja?
**Model answer:** `record` to specjalna klasa w Java 16+ (preview od 14). Minimalna deklaracja: `record Point(int x, int y) {}`. Kompilator automatycznie generuje: konstruktor kanoniczny, `final` pola prywatne, gettery (bez `get` prefix — po prostu `x()`, `y()`), `equals()`, `hashCode()`, `toString()`. Recordy są shallowly immutable — pola są `final`, ale jeśli pole jest mutable obiektem (np. List), jego zawartość można zmieniać. Nie można dziedziczyć po recordzie ani rozszerzać go klasą. The Codest używa recordów do value objects: `record Configuration(@Named("slack-channel") String slackChannel) {}` z `@Inject` na konstruktorze.
**Interview trap:** „Record jest w pełni immutable" — FAŁSZ dla mutable reference types. Rekruter to przetestuje: `record Wrapper(List<String> items) {}` — items można modyfikować przez `wrapper.items().add(...)`.
**Tags:** java17, records, immutability

## Q-THECODEST-003 [bloom: recall]
**Question:** Co to jest Vavr? Wymień 3 typy które dostarcza i po co je używać.
**Model answer:** Vavr (dawniej Javaslang) to biblioteka functional programming dla Java — wypełnia luki które Java ma vs Scala/Haskell. Trzy kluczowe typy: (1) **Try<T>** — enkapsuluje operację która może wyrzucić wyjątek; zamiast try-catch masz `Try.of(() -> risky()).getOrElse(default)`; (2) **Option<T>** — jak Optional ale z bogatszym API (flatMap, fold, toStream); (3) **Tuple2<A,B>** — para wartości z dostępem przez `._1` i `._2`, immutable. Vavr dostarcza też immutable kolekcje (List, Map, Set) i Either. The Codest używa Try do Slack SDK calls (łapią błędy bez checked exceptions) i Tuple2 do groupBy wyników.
**Interview trap:** „Po co Vavr skoro Java ma Optional?" — Optional to tylko null-safe wrapper, nie obsługuje wyjątków. Try to coś innego: reprezentuje sukces lub porażkę obliczenia które może rzucić. Nie myl tych dwóch.
**Tags:** vavr, functional-java, try-monad

## Q-THECODEST-004 [bloom: recall]
**Question:** Co generuje `@RequiredArgsConstructor` z Lomboka? Kiedy nie zadziała?
**Model answer:** `@RequiredArgsConstructor` generuje konstruktor ze wszystkimi polami `final` i polami oznaczonymi `@NonNull`. Pola inicjalizowane inline lub mutable bez `@NonNull` są pomijane. Używany w Guice: `@RequiredArgsConstructor(onConstructor_ = {@Inject})` dodaje `@Inject` na wygenerowanym konstruktorze — elegancki sposób na constructor injection bez boilerplate. Nie zadziała jeśli klasa ma pola `final` z wartościami inicjalizowanymi bezpośrednio w deklaracji (`final int x = 5`) — te pola nie trafiają do konstruktora. Nie zadziała też przy cyklicznych zależnościach.
**Interview trap:** Rekruter może spytać o `@AllArgsConstructor` vs `@RequiredArgsConstructor`. Różnica: `@All` bierze WSZYSTKIE pola, `@Required` tylko `final` i `@NonNull`. W DI chcesz `@Required`.
**Tags:** lombok, constructor-injection, di

## Q-THECODEST-005 [bloom: recall]
**Question:** Czym jest adnotacja `@Named` w Guice? Do czego służy?
**Model answer:** `@Named` to kwalifikator z `javax.inject` — pozwala rozróżnić wiele bindingów tego samego typu. Przykład: masz dwa `String` bindingi — token Slacka i kanał. Bez `@Named` Guice nie wie który wstrzyknąć. Rozwiązanie: `bind(String.class).annotatedWith(Names.named("slack-token")).toInstance(token)` w module, i `@Named("slack-token") String token` na konstruktorze. The Codest używa dokładnie tego wzorca w `AuthViaEnvModule` i `SlackModule`. Alternatywa to własna adnotacja `@Qualifier`.
**Interview trap:** W Spring `@Qualifier` działa podobnie, ale jest Spring-specific. `@Named` z `javax.inject` to standard JSR-330 — działa i w Guice i w Spring. Jeśli używasz Guice, trzymaj się `@Named` z `javax.inject`, nie z Guice własnego pakietu.
**Tags:** guice, named, qualifier, jsr330

## Q-THECODEST-006 [bloom: recall]
**Question:** Co to jest Checkstyle i co dodaje PMD ponad to co Checkstyle daje?
**Model answer:** Checkstyle weryfikuje **styl kodu** — formatowanie, nazewnictwo, długości linii, whitespace, import order, Javadoc conventions. Zwykle integrowany z Eclipse/IntelliJ formatter rules. PMD analizuje **wzorce kodu** — wykrywa potencjalne bugi, martwy kod, duplikaty (CPD — Copy-Paste Detector), nadmierną złożoność cyklomatyczną, puste bloki catch, unnecessary object creation. Krótko: Checkstyle = jak kod wygląda; PMD = jak kod działa (potencjalne problemy). The Codest ma oba uruchomione na fazie Maven `validate` — build się nie skompiluje jeśli któryś reguł jest złamana.
**Interview trap:** Rekruter może spytać o SpotBugs (dawniej FindBugs). SpotBugs analizuje bytecode (nie source), wykrywa null dereference, resource leaks, synchronization issues. Trzy narzędzia mają różne zakresy i się uzupełniają.
**Tags:** checkstyle, pmd, code-quality, static-analysis

## Q-THECODEST-007 [bloom: recall]
**Question:** Co mierzy Jacoco? Co generuje jako output?
**Model answer:** Jacoco (Java Code Coverage) mierzy pokrycie kodu przez testy — line coverage, branch coverage, instruction coverage, method coverage, class coverage. Instrumentuje bytecode w runtime i zbiera dane kiedy testy są uruchamiane. Generuje raporty w HTML, XML i CSV. The Codest integruje Jacoco przez Maven plugin z celem `prepare-agent` (przed testami) i `report` (w fazie `test`). W projekcie activity-rewards mają też `org.jacoco.agent` jako runtime dependency w scope test. Raport ląduje w `target/site/jacoco/`. Branch coverage jest ważniejsza niż line coverage — linia może być „pokryta" ale nie wszystkie gałęzie warunkowe przetestowane.
**Interview trap:** 100% line coverage ≠ testy są dobre. Możesz mieć 100% coverage pisząc testy które nie assertują niczego. Coverage to konieczny ale niewystarczający wskaźnik jakości testów.
**Tags:** jacoco, test-coverage, maven

## Q-THECODEST-008 [bloom: recall]
**Question:** Czym jest `@VisibleForTesting` i kiedy go używasz?
**Model answer:** `@VisibleForTesting` to adnotacja z biblioteki Guava (Google) — czysto dokumentacyjna, kompilator jej nie wymusza. Oznacza że metoda lub pole ma słabszy access modifier niż normalnie by miało, TYLKO dlatego żeby ją przetestować. The Codest używa jej na `sendMessage()` i `calculateScore()` w `ActivityRewards` — są package-private zamiast private, żeby test klasy (w tym samym pakiecie) mógł je wywołać bezpośrednio. Alternatywa: Mockito `@InjectMocks` + testowanie przez publiczne metody, lub PowerMock (do prywatnych — ale to code smell). `@VisibleForTesting` to uczciwe przyznanie że naruszamy enkapsulację na potrzeby testowalności.
**Interview trap:** Nie mylić z `@TestOnly` z IntelliJ — to IDE hint (podświetla użycie w produkcyjnym kodzie). `@VisibleForTesting` jest z Guavy i jest bardziej powszechne w ekosystemie Google/Guice.
**Tags:** testing, encapsulation, visibility, guava

---

## Q-THECODEST-009 [bloom: understand]
**Question:** Wyjaśnij mechanizm bindingów Guice. Czym `AbstractModule.configure()` różni się od Spring `@Configuration` z `@Bean`?
**Model answer:** W Guice `AbstractModule.configure()` deklaruje bindingi w DSL-u: `bind(SlackClient.class).to(SimpleSlackClient.class)`, `bind(String.class).annotatedWith(Names.named("token")).toInstance(System.getenv("TOKEN"))`. Guice czyta te bindingi i buduje Injector — graf zależności jest znany w czasie inicjalizacji. Błąd (brakujący binding) → wyjątek przy `createInjector()`. W Spring `@Bean` to metody które produkują beany — Spring wywołuje je przez reflection, skanuje classpath (`@ComponentScan`), obsługuje AOP proxy, scope (singleton/prototype/request). Spring jest bogatszy ale mniej deterministyczny — missing bean wykrywa się przy starcie kontenera ale może być delayed (lazy beans). Guice: explicit, lightweight, szybki cold-start (ważne dla Lambda). Spring: auto-wiring, ekosystem starters, ale overhead.
**Interview trap:** Rekruter może spytać „co to jest composition root?" — to jeden punkt w aplikacji gdzie budujesz cały graf DI. W Guice to `main()`. W Spring to startup. Guice wymusza explicit composition root, Spring pozwala na rozproszony (`@Autowired` wszędzie).
**Tags:** guice, spring, di, composition-root

## Q-THECODEST-010 [bloom: understand]
**Question:** Jak działa `Try` z Vavr? Dlaczego jest lepszy niż `try-catch` w pipeline'ach?
**Model answer:** `Try<T>` to sealed type z dwoma implementacjami: `Success<T>` (zawiera wartość) i `Failure<T>` (zawiera Throwable). `Try.of(() -> riskyCall())` przechwytuje każdy wyjątek z lambdy i zwraca `Failure`. Możesz chain-ować: `.map()`, `.flatMap()`, `.recover()`, `.getOrElse()`. Zalety vs try-catch: (1) composable — możesz zwrócić `Try<T>` z metody i caller decyduje jak obsłużyć; (2) w pipeline'ach streamowych `try-catch` inside lambda wymaga checked exception obsługi — Try to enkapsuluje; (3) lepsza czytelność — zamiast imperatywnego try/catch/finally masz deklaratywny łańcuch. The Codest używa `Try.of(() -> methods.chatPostMessage(...)).onFailure(Throwable::printStackTrace).get()`. Uwaga: `.get()` na Failure rzuca WrappedException — nie jest bezpieczne jeśli failure jest możliwa.
**Interview trap:** `Try.get()` na `Failure` nie zwraca `null` — rzuca `NonFatalException` (Vavr). Jeśli chcesz bezpiecznie wyciągnąć wartość: `.getOrElse(defaultValue)` lub `.getOrElseThrow(e -> new MyException(e))`. The Codest ma w kodzie miejsca gdzie używają `.get()` bez recovery — to potencjalny NPE/exception w runtime.
**Tags:** vavr, try-monad, error-handling, functional-java

## Q-THECODEST-011 [bloom: understand]
**Question:** Dlaczego The Codest używa interfejsu `SlackClient` z osobną implementacją `SimpleSlackClient` zamiast jednej klasy? Jakie konkretne korzyści to daje?
**Model answer:** Interface-first design daje trzy konkretne korzyści: (1) **Testowalność** — w testach możesz mockować `SlackClient` przez `@Mock SlackClient slackClient` bez żadnych zewnętrznych połączeń; `ActivityRewards` zależy od interfejsu, nie od konkretnej klasy; (2) **Wymienialność** — możesz dorzucić `FakeSlackClient` (stub zwracający stałe dane), `LoggingSlackClient` (dekorator logujący wszystkie wywołania), `CachingSlackClient` bez zmiany `ActivityRewards`; (3) **Czytelna umowa** — interfejs `SlackClient` z Javadoc na każdej metodzie komunikuje co system robi, bez implementacyjnych detali (`toTs()`, paginacja, filter logika są schowane w `SimpleSlackClient`). To zasada Dependency Inversion (DIP) z SOLID: wysokopoziomowy moduł (`ActivityRewards`) zależy od abstrakcji, nie od detalu.
**Interview trap:** Rekruter może spytać „czy zawsze tak robić?" — odpowiedź: nie zawsze. Jeśli masz jeden konkretny use case i testujesz przez public API, interface może być over-engineering. Tu ma sens bo Slack SDK call jest external side effect — idealny kandydat na interface + mock w testach.
**Tags:** solid, dip, interface-design, testability

## Q-THECODEST-012 [bloom: understand]
**Question:** Java records są "shallowly immutable". Co to oznacza i jakie są implikacje dla kolekcji wewnątrz recordu?
**Model answer:** Shallow immutability oznacza że pola recordu są `final` — nie możesz przypisać nowego obiektu do pola po konstrukcji. Ale jeśli pole wskazuje na mutable obiekt (np. `ArrayList`), możesz modyfikować jego zawartość. Przykład: `record Users(List<String> names) {}` — `users.names()` zwraca tę samą listę (nie kopię), ktoś może wywołać `users.names().add("hacker")` i zmutować stan. Implikacja: jeśli chcesz prawdziwą immutability, w compact constructorze skopiuj: `this.names = List.copyOf(names)` (Java 10+, immutable copy). Alternatywnie użyj Vavr `io.vavr.collection.List` jako typ pola — jest immutable by design. The Codest używa `List.of()` i `Collectors.toUnmodifiableList()` co jest dobrą praktyką.
**Interview trap:** `Collections.unmodifiableList()` wraps mutable list — jeśli ktoś ma referencję do oryginalnej listy, może ją zmutować a ty przez wrapper zobaczysz te zmiany. `List.copyOf()` tworzy prawdziwą kopię.
**Tags:** java17, records, immutability, collections

## Q-THECODEST-013 [bloom: understand]
**Question:** Czym jest Assembler pattern z biblioteki pellse/assembler? Jaki problem z N+1 rozwiązuje?
**Model answer:** Problem N+1: masz listę N obiektów i dla każdego chcesz dołączyć dane z innego źródła — wywołujesz to źródło N razy. W `ActivityRewards.calculateScore()` mają listę `(userId, count)` i chcą dołączyć `SlackUser` przez `getUsersByIds()`. Naiwne podejście: `resultsRaw.stream().map(t -> new Result(slackClient.getUserById(t._1), t._2))` — N wywołań do Slack API. Assembler pattern: zbiera wszystkie IDs, wywołuje `getUsersByIds(allIds)` raz (1 request), mapuje wyniki na ID, i łączy. `AssemblerBuilder.assemblerOf(Result.class).withIdExtractor(t -> t._1).withAssemblerRules(MapperUtils.oneToOne(slackClient::getUsersByIds, SlackUser::slackId), (t, su) -> new Result(su, t._2)).using(streamAdapter()).assemble(resultsRaw)` — jedno wywołanie `getUsersByIds` dla całej listy.
**Interview trap:** Rekruter może spytać jak to porównać do JPA `@EntityGraph` lub `@BatchSize` w Hibernate — to rozwiązuje ten sam N+1 problem na poziomie ORM. Assembler działa dla dowolnych źródeł danych (API, cache, DB).
**Tags:** assembler-pattern, n-plus-one, performance, functional-java

## Q-THECODEST-014 [bloom: understand]
**Question:** Wyjaśnij różnicę między `@Mock` a `@Spy` w Mockito. Kiedy używasz `@Spy`?
**Model answer:** `@Mock` tworzy pełny mock — wszystkie metody zwracają domyślne wartości (`null`, `0`, `false`, empty collections) chyba że je stubbujesz. Nie ma żadnej prawdziwej logiki. `@Spy` wraps prawdziwy obiekt — wywołuje prawdziwe metody chyba że konkretnie je stubbujemy. Używasz `@Spy` gdy: (1) chcesz przetestować część klasy korzystając z prawdziwej logiki reszty; (2) klasa ma logikę którą chcesz zachować ale chcesz stub tylko jednej metody. The Codest używa `@Spy private LocalRunningCalculator localRunningCalculator` — bo chcą przetestować prawdziwą logikę kalkulatora, nie mockować go w całości. Pułapka: `@Spy` na klasie bez no-arg konstruktora wymaga `@Spy MyClass spy = new MyClass(args)`.
**Interview trap:** Stubbowanie Spy jest odwrotne niż Mock. Mock: `when(mock.method()).thenReturn(x)`. Spy z prawdziwą metodą: `doReturn(x).when(spy).method()` — jeśli użyjesz `when(spy.method())` wywołasz prawdziwą metodę przed stubowaniem.
**Tags:** mockito, spy, mock, testing, junit5

## Q-THECODEST-015 [bloom: understand]
**Question:** Jak działa Jib Maven Plugin? Czym różni się od tradycyjnego Dockerfile?
**Model answer:** Jib buduje obrazy Docker bez Docker daemon i bez Dockerfile. Czyta projekt Maven/Gradle, buduje layered image (dependencies layer, resources layer, classes layer — oddzielne), i pushuje bezpośrednio do registry lub do lokalnego daemon (`jib:dockerBuild`). Zalety: (1) no Docker daemon needed w CI/CD; (2) layer caching jest inteligentny — jeśli dependencies się nie zmieniły, ta warstwa jest cache'owana; (3) reprodukowalne buildy — brak zależności od lokalnego Docker kontekstu; (4) szybszy rebuild bo tylko classes layer się zmienia. Wada: mniej kontroli niż Dockerfile (nie możesz łatwo dodać custom instrukcji). The Codest używa Jib w profilu `docker` z goal `dockerBuild` i taguje obrazem `activity-rewards:${project.version}`.
**Interview trap:** Jib używa distroless images domyślnie — bez shell, bez package manager. Debugowanie w prod jest trudniejsze. Dla dev możesz ustawić `<from><image>eclipse-temurin:17</image></from>`.
**Tags:** jib, docker, ci-cd, maven

## Q-THECODEST-016 [bloom: understand]
**Question:** The Codest ma Checkstyle i Eclipse formatter uruchomione na fazie `validate`. Co to znaczy dla dewelopera który pushuje kod?
**Model answer:** Maven `validate` to pierwsza faza cyklu — uruchamia się przed `compile`. Jeśli Checkstyle lub formatter validation failuje, `mvn package` (i każda następna faza) nie przejdzie. Deweloper nie może zbuildować projektu bez poprawnego formatowania. Implikacja: (1) musisz skonfigurować Eclipse formatter w IDE (IntelliJ ma plugin do importu eclipse-formatter.xml) i formatować przed każdym commitem; (2) w CI/CD pipeline build failing na validate jest szybki fail (zanim compile, zanim testy); (3) code review nie musi się zajmować stylem — narzędzie to wymusza. Checkstyle jest w fazie `validate` via `maven-checkstyle-plugin`, formatter via `formatter-maven-plugin` z goal `validate`. PMD też na validate — mają strict quality gate.
**Interview trap:** Rekruter może spytać jak to integrować z IntelliJ. Odpowiedź: plugin Eclipse Code Formatter + import `eclipse-formatter.xml` + Checkstyle-IDEA plugin z podpiętym `checkstyle.xml`. Wtedy IDE i Maven są zgodne.
**Tags:** checkstyle, maven-lifecycle, code-quality, ci-cd

---

## Q-THECODEST-017 [bloom: apply]
**Question:** Napisz Guice `AbstractModule` który binduje `SlackClient` do `SimpleSlackClient` oraz binduje `@Named("slack-token")` String z zmiennej środowiskowej `SLACK_TOKEN`.
**Model answer:**
```java
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class SlackModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SlackClient.class).to(SimpleSlackClient.class);
        bind(String.class)
            .annotatedWith(Names.named("slack-token"))
            .toInstance(System.getenv("SLACK_TOKEN"));
    }
}
```
Jeśli `SLACK_TOKEN` może być null (env nie ustawiony), lepiej dodać null-check i rzucić wyjątek przy starcie niż failować przy pierwszym Slack call. `SimpleSlackClient` musi mieć `@Inject` na konstruktorze który przyjmuje `@Named("slack-token") String token`. Guice wstrzyknie ten String automatycznie gdy będzie tworzył `SimpleSlackClient`.
**Interview trap:** Jeśli zapomnisz `.annotatedWith(Names.named("slack-token"))` to binding będzie na `String.class` bez kwalifikatora — i Guice nie będzie wiedział który String wstrzyknąć gdy masz więcej niż jeden String binding.
**Tags:** guice, module, named, binding

## Q-THECODEST-018 [bloom: apply]
**Question:** Przepisz ten kod na Vavr `Try`: `try { return methods.usersList(req).getMembers(); } catch (Exception e) { e.printStackTrace(); return Collections.emptyList(); }`
**Model answer:**
```java
return Try.of(() -> methods.usersList(req))
    .onFailure(Throwable::printStackTrace)
    .map(UsersListResponse::getMembers)
    .getOrElse(Collections.emptyList());
```
Różnica od oryginalnego: `map()` jest wywoływane tylko na `Success` — jeśli `usersList()` rzuci, `map` jest skipped i `getOrElse` zwraca pustą listę. Możesz też użyć `.recover(e -> emptyList())` zamiast `getOrElse` jeśli chcesz chain-ować dalej. Bardziej verbose ale explicit: `.mapFailure(API_ERROR, e -> new SlackApiException(e))` pozwala zmienić typ wyjątku.
**Interview trap:** NIE pisz `.get()` na końcu — jeśli Try jest Failure, `.get()` rzuca NonFatalException. Zawsze kończ `.getOrElse()`, `.getOrElseThrow()`, lub `.fold()`.
**Tags:** vavr, try-monad, refactoring, functional-java

## Q-THECODEST-019 [bloom: apply]
**Question:** Napisz Java 17 record `SlackUser` z polami `slackId`, `name`, `email`. Dodaj compact constructor który normalizuje email do lowercase i waliduje że slackId nie jest null.
**Model answer:**
```java
public record SlackUser(String slackId, String name, String email) {
    public SlackUser {
        if (slackId == null || slackId.isBlank()) {
            throw new IllegalArgumentException("slackId cannot be null or blank");
        }
        email = email != null ? email.toLowerCase().trim() : null;
    }

    public String normalizedEmail() {
        return email != null ? email.toLowerCase().trim() : "";
    }
}
```
Compact constructor (bez listy parametrów w nawiasach) przypisuje `this.field = field` automatycznie CHYBA że nadpiszesz — jak w `email = email.toLowerCase()`. Compact constructor jest idealny do walidacji i normalizacji. Metody pomocnicze jak `normalizedEmail()` mogą być dodawane jak w zwykłej klasie.
**Interview trap:** W compact constructorze piszesz `email = ...` NIE `this.email = ...` — to jest specjalna składnia compact constructorów. `this.field` jest automatycznie przypisywane na końcu na podstawie tego co nadpiszesz.
**Tags:** java17, records, compact-constructor, validation

## Q-THECODEST-020 [bloom: apply]
**Question:** Napisz test JUnit 5 dla metody `sendMessage(MonthlyResults)` z klasy `ActivityRewards`, używając `@ExtendWith(MockitoExtension.class)`, `@Mock SlackClient`, `@Mock Configuration`. Sprawdź że `slackClient.sendMessageToChannel()` jest wywołany z właściwymi argumentami.
**Model answer:**
```java
@ExtendWith(MockitoExtension.class)
class ActivityRewardsTest {

    @Mock private SlackClient slackClient;
    @Mock private Configuration configuration;
    @Spy private LocalRunningCalculator localRunningCalculator;
    @InjectMocks private ActivityRewards activityRewards;

    @Test
    void sendMessage_callsSlackWithWinnerMessage() {
        // given
        when(configuration.slackChannel()).thenReturn("general");
        SlackUser winner = new SlackUser("U123", "jan", "jan@test.com");
        Result winnerResult = new Result(winner, 42L);
        MonthlyResults results = new MonthlyResults(Month.APRIL, 2026,
            List.of(winnerResult));

        // when
        activityRewards.sendMessage(results);

        // then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).sendMessageToChannel(eq("general"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("jan");
    }
}
```
`@InjectMocks` tworzy `ActivityRewards` i wstrzykuje wszystkie `@Mock` / `@Spy` przez konstruktor lub pola. `ArgumentCaptor` pozwala przechwycić argument i assertować jego zawartość.
**Interview trap:** `@InjectMocks` wstrzykuje przez konstruktor jeśli jest `@RequiredArgsConstructor` / `@AllArgsConstructor`, lub przez pola (field injection) jeśli nie ma konstruktora z argumentami. Może być unpredictable — upewnij się że klasa ma jasny konstruktor.
**Tags:** junit5, mockito, argumentcaptor, testing

## Q-THECODEST-021 [bloom: apply]
**Question:** Korzystając z Java Streams i Vavr Tuple, zgrupuj listę `MessageBeing` (ma pole `userId()`) według `userId` i zwróć `List<Tuple2<String, Long>>` (userId, liczba wiadomości), posortowaną malejąco po liczbie.
**Model answer:**
```java
List<Tuple2<String, Long>> results = messageBeings.stream()
    .collect(Collectors.groupingBy(MessageBeing::userId, Collectors.counting()))
    .entrySet()
    .stream()
    .map(e -> Tuple.of(e.getKey(), e.getValue()))
    .sorted(Comparator.comparing(Tuple2::_2, Comparator.reverseOrder()))
    .toList(); // Java 16+ (unmodifiable)
```
Albo bardziej functional Vavr styl:
```java
io.vavr.collection.List.ofAll(messageBeings)
    .groupBy(MessageBeing::userId)
    .mapValues(io.vavr.collection.List::length)
    .toList()
    .map(t -> Tuple.of(t._1, (long) t._2))
    .sortBy(Comparator.comparing((Tuple2<String, Long> t) -> t._2).reversed());
```
The Codest używa pierwszego podejścia z Java Streams w `calculateScore()`.
**Interview trap:** `Collectors.counting()` zwraca `Long`. `Tuple.of(key, value)` — wartości są autoboxed. `Comparator.reverseOrder()` na Long zadziała bo Long implementuje Comparable.
**Tags:** streams, vavr, tuple, groupby, collectors

## Q-THECODEST-022 [bloom: apply]
**Question:** Masz klasę z field injection (`@Autowired`). Refaktoruj ją na constructor injection z `@RequiredArgsConstructor` i przygotuj ją pod Guice `@Inject`.
**Model answer:**
```java
// PRZED (Spring field injection — złe)
@Component
public class ActivityService {
    @Autowired private SlackClient slackClient;
    @Autowired private DateCalculator dateCalc;
    @Autowired private Configuration config;

    public void run() { ... }
}

// PO (Constructor injection, Guice-ready)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class ActivityService {
    private final SlackClient slackClient;
    private final DateCalculator dateCalc;
    private final Configuration config;

    public void run() { ... }
}
```
Lombok generuje: `@Inject public ActivityService(SlackClient slackClient, DateCalculator dateCalc, Configuration config) { this.slackClient = slackClient; ... }`. Pola muszą być `final`. Nie potrzebujesz `@Component` — Guice zarządza tworzeniem przez Injector.
**Interview trap:** `onConstructor_ = {@Inject}` (z podkreślnikiem) to specjalna składnia Lomboka dla dodawania adnotacji na wygenerowanym konstruktorze. Bez podkreślnika (`onConstructor`) jest deprecated. W nowszych Lombok `onConstructor_ = {@Inject}` to standard.
**Tags:** lombok, guice, constructor-injection, refactoring

## Q-THECODEST-023 [bloom: apply]
**Question:** Napisz Guice `Provider<RunningCalculator>` który zwraca `FakeRunningCalculator` jeśli env `TEST_RUN=true`, a `LocalRunningCalculator` w przeciwnym razie.
**Model answer:**
```java
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class RunningCalculatorProvider implements Provider<RunningCalculator> {

    private final boolean testRun;

    @Inject
    public RunningCalculatorProvider(@Named("test-run") Boolean testRun) {
        this.testRun = testRun;
    }

    @Override
    public RunningCalculator get() {
        return testRun ? new FakeRunningCalculator() : new LocalRunningCalculator();
    }
}
```
W module: `bind(RunningCalculator.class).toProvider(RunningCalculatorProvider.class)`. I: `bind(Boolean.class).annotatedWith(Names.named("test-run")).toInstance(Boolean.valueOf(System.getenv("TEST_RUN")))`. To dokładnie wzorzec z The Codest `MasterModule`.
**Interview trap:** `Boolean.valueOf(null)` zwraca `false` — więc jeśli `TEST_RUN` nie jest ustawiony w env, dostaniesz `false` (production path). To bezpieczne zachowanie. `Boolean.parseBoolean(null)` też zwraca false.
**Tags:** guice, provider, factory, conditional-binding

## Q-THECODEST-024 [bloom: apply]
**Question:** Skonfiguruj Maven Checkstyle plugin żeby failował build przy naruszeniach, używał pliku `checkstyle.xml` z katalogu projektu, i działał na fazie `validate`.
**Model answer:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.2.0</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <inputEncoding>UTF-8</inputEncoding>
        <consoleOutput>true</consoleOutput>
        <failsOnError>true</failsOnError>
        <includeTestSourceDirectory>true</includeTestSourceDirectory>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>10.3.3</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>validate</id>
            <phase>validate</phase>
            <goals>
                <goal>checkstyle</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
`failsOnError: true` — build failuje przy naruszeniu. `includeTestSourceDirectory: true` — Checkstyle też na testach. `<goal>checkstyle</goal>` — nie `check` — generuje raport, ale z `failsOnError` blokuje. Explicit `<dependency>` na `com.puppycrawl.tools:checkstyle` pinuje wersję niezależnie od wersji pluginu.
**Interview trap:** Goal `check` vs `checkstyle` — `check` failuje build automatycznie, `checkstyle` generuje raport i failuje tylko jeśli `failsOnError=true`. The Codest używa `checkstyle` z `failsOnError` — to ten sam efekt ale z jawnym ustawieniem.
**Tags:** maven, checkstyle, build-lifecycle, code-quality

---

## Q-THECODEST-025 [bloom: analyze]
**Question:** The Codest przeszedł z Spring Boot (Java 11, conversation-builder) na Guice (Java 17, activity-rewards). Przeanalizuj trade-offy tej decyzji — co zyskali, co stracili?
**Model answer:** **Zyski:** (1) Cold-start na AWS Lambda — Spring Boot startuje ~2-5s, Guice ~100-300ms; krytyczne dla serverless; (2) Deterministyczne bindingi — żadnego magic component scan, widzisz cały DI graf w Module klasach; (3) Mniejszy JAR — Spring Boot z embedded Tomcat to ~50MB+, Guice JAR to ~0.8MB; (4) Java 17 features — records, sealed classes, pattern matching; (5) Explicit architecture — wszystkie zależności są widoczne w Module.configure(). **Straty:** (1) Brak Spring ekosystemu — żadnego Spring Data, Spring Security, Spring Web MVC; musisz implementować ręcznie lub dobierać biblioteki; (2) Mniejsza społeczność — mniej tutoriali, Stack Overflow, mniej junior-ów znających Guice; (3) Brak auto-configuration i starters — każdy binding ręcznie; (4) Integracja z Kotlin — Spring ma pierwsze wsparcie Kotlin (coroutines, Spring Data extensions), Guice jest Java-centric. **Wniosek:** Guice to dobry wybór dla Lambda/microservices gdzie cold-start i explicit design są ważne. Spring Boot lepszy dla pełnych aplikacji webowych gdzie potrzebujesz bogatego ekosystemu.
**Interview trap:** Nie mów „Guice jest przestarzały" — Google używa go wewnętrznie w ogromu projektów (w tym Android). Jest aktywnie rozwijany. Różni się filozofią od Spring, ale to choice, nie deprecation.
**Tags:** guice, spring-boot, architecture, trade-offs, aws-lambda

## Q-THECODEST-026 [bloom: analyze]
**Question:** W `SimpleSlackClient.getUsersByFilter()` kod pobiera WSZYSTKICH użytkowników z Slack API i filtruje lokalnie. Przeanalizuj performance implikacje i zaproponuj lepsze podejście dla workspace z 10,000 użytkowników.
**Model answer:** Obecny kod: `methods.usersList(UsersListRequest.builder().build())` — pobiera wszystkich użytkowników, jeden lub wiele page requests (Slack paginuje). Dla 10k użytkowników: (1) Slack API rate limit — users.list ma limit; przy 200 users/page to 50 requestów; (2) Pamięć — 10k obiektów `Member` w heap; (3) Latency — każdy page request to HTTP round-trip. **Lepsze podejście:** (1) Pobierz IDs bezpośrednio przez `users.lookupByEmail()` jeśli znasz emaile — 1 request per user ale targeted; (2) Cache users list lokalnie (Redis, S3, w pamięci z TTL) — użytkownicy rzadko się zmieniają; (3) Paginacja z early exit — przerwij gdy znajdziesz wszystkich szukanych; (4) `conversations.members()` jeśli chcesz tylko użytkowników z konkretnego kanału — zwraca tylko member IDs tego kanału; (5) Batch lookup — `users.info()` per ID jest bardziej targeted ale ma swój rate limit.
**Interview trap:** Rekruter może spytać o Slack API rate limits — Tier 2 (users.list) to 20+ requests/minute. Przy 50 requestach dla 10k users i rate limicie możesz zająć minutę tylko na pobranie listy. Cache to kluczowe rozwiązanie.
**Tags:** performance, slack-api, caching, pagination, scalability

## Q-THECODEST-027 [bloom: analyze]
**Question:** `ActivityRewards.calculateScore()` używa `AssemblerBuilder` zamiast wywołania `getUsersByIds()` wewnątrz stream loop. Przeanalizuj dlaczego to jest lepsze i jakie są granice tej optymalizacji.
**Model answer:** **Dlaczego lepsze:** Bez Assemblera naiwna implementacja wywołuje `getUsersByIds(List.of(singleId))` N razy — N round-tripów do Slack API. Z Assemblerem: zbiera wszystkie IDs, wywołuje `getUsersByIds(allIds)` RAZ, buduje mapę `id → SlackUser`, łączy z wynikami. Z N=100 użytkowników: 1 vs 100 Slack API calls. **Granice optymalizacji:** (1) Jeśli lista wyników jest bardzo duża (np. tysiące), jednorazowe `getUsersByIds(allIds)` może przekroczyć limit Slack API dla batch lookups; (2) Assembler zakłada że wszystkie dane są dostępne jednocześnie — nie działa w streaming/lazy pipeline gdzie dane napływają stopniowo; (3) Memory: wszystkie wyniki muszą być w pamięci jednocześnie dla joinowania; (4) Jeśli paginacja jest potrzebna (wyniki w batches), trzeba zastosować Assembler per batch. **Ogólna zasada:** Assembler pattern eliminuje N+1 ale nie zastępuje prawdziwego cachingu. Jeśli `getUsersByIds` jest wywoływane często, cache przed nim jest ważniejszy.
**Interview trap:** Rekruter może spytać czy to nie jest przedwczesna optymalizacja. Odpowiedź: nie — N+1 to klasyczny problem, Assembler to udokumentowany pattern, a różnica między 1 a 100 API calls jest znacząca dla latency i rate limitów.
**Tags:** assembler-pattern, n-plus-one, performance, trade-offs

## Q-THECODEST-028 [bloom: analyze]
**Question:** The Codest używa `@VisibleForTesting` na metodach package-private zamiast prywatnych. Co to mówi o ich filozofii testowania? Jakie są ryzyka?
**Model answer:** **Filozofia:** Chcą testować konkretne metody biznesowe (`calculateScore`, `sendMessage`) izolowane — nie tylko przez publiczne API. To białoskrzynkowe testowanie (white-box). Zakładają że tester zna implementację i celuje w konkretne zachowanie. Jest to uzasadnione kiedy metoda ma złożoną logikę biznesową i testowanie przez publiczne API wymagałoby skomplikowanego setup. **Ryzyka:** (1) Coupling testów do implementacji — jeśli refaktorujesz metodę (rename, split, merge), testy się psują nawet jeśli zachowanie się nie zmienia; (2) `@VisibleForTesting` nie jest enforced przez kompilator — ktoś w innym pakiecie nie może wywołać (package-private), ale w tym samym pakiecie może; (3) Zachęca do omijania encapsulation — łatwy fix gdy test nie może dosięgnąć metody to „zrobię ją package-private"; (4) Testowanie implementation details zamiast behavior — testy stają się brittler. **Lepsza alternatywa:** Testuj przez publiczne API + użyj AssertJ's field assertions dla state verification. Lub zastosuj ports & adapters — logika w małych, testowalnych klasach z publicznym API.
**Interview trap:** Rekruter może spytać o alternative: Reflection vs @VisibleForTesting. Reflection (force-access private fields) jest gorsze — kompilator nie pomaga, IDE nie pomoże, JDK 17+ blokuje reflective access do private members modułów.
**Tags:** testing-philosophy, encapsulation, white-box-testing, design

## Q-THECODEST-029 [bloom: analyze]
**Question:** `App.main()` składa wszystkie Guice modules: `MasterModule`, `AuthViaEnvModule`, `SlackModule`, `AwsModule`. Porównaj to z Spring auto-configuration. Jakie trade-offy ma explicit composition root?
**Model answer:** **Explicit Guice composition root (main()):** (1) Widać dokładnie co jest wstrzykiwane i skąd — cały DI graf jest w jednym miejscu; (2) Łatwe debugowanie — sprawdzasz `MasterModule.configure()` i widzisz bindingi; (3) Brak magic — żadnego component scan, żadnych warunkowych beans (`@ConditionalOnProperty`); (4) Testowanie — możesz zbudować Injector z innym Module do testów (`TestSlackModule` zamiast `SlackModule`); (5) Fail-fast — błędy bindingów wychodzą przy `createInjector()`, nie przy pierwszym użyciu. **Spring auto-configuration:** (1) Zero-config dla standardowych przypadków — `spring-boot-starter-web` daje gotowy WebMVC; (2) `@ConditionalOnProperty` dla feature flags; (3) Ale: magic może być frustrujące — „skąd ten bean pochodzi?" wymaga debugowania; (4) `@EnableAutoConfiguration` skanuje classpath i aktywuje konfiguracje — trudniej śledzić co jest aktywne. **Trade-off:** Guice explicit = czytelniejszy ale verbose. Spring auto = szybszy start ale mniej przezroczysty. Dla małych focused applications (Lambda) Guice wygrywa. Dla dużych aplikacji webowych Spring ekosystem wygrywa.
**Interview trap:** Rekruter może spytać „jak testujesz z Guice?" — zamiast `@MockBean` (Spring Test) tworzysz Injector z mock modules: `Guice.createInjector(new TestSlackModule())` gdzie `TestSlackModule` binduje `SlackClient` do mock obiektu.
**Tags:** guice, spring, composition-root, architecture, testability

## Q-THECODEST-030 [bloom: analyze]
**Question:** Kod używa `Try.of(...).onFailure(Throwable::printStackTrace).get()`. Jaki jest problem z tą linią? Co się stanie gdy Try jest Failure? Zaproponuj poprawkę.
**Model answer:** **Problem:** `.get()` na `Try.Failure` nie zwraca wartości — rzuca `NonFatalException` (Vavr wraps oryginalny Throwable). `onFailure(Throwable::printStackTrace)` loguje błąd, ale nie recovery — Try nadal jest Failure. Więc `.get()` rzuci wyjątek. Jeśli to jest w kodzie produkcyjnym który ma nie crashować, to bug. **Analiza kodu The Codest:** `sendMessageToUser()` i `sendMessageToChannel()` kończą `.get()` — jeśli Slack API zwróci błąd, cała aplikacja crashuje. `getUsersByIds()` używa `.getOrElse(Collections.emptyList())` — bezpieczne. Niespójna strategia obsługi błędów. **Poprawki:**
```java
// Opcja 1: zwróć Optional
return Try.of(() -> methods.chatPostMessage(req))
    .onFailure(e -> log.warning("Slack API error: " + e.getMessage()))
    .toOption()
    .getOrNull();

// Opcja 2: typed exception
return Try.of(() -> methods.chatPostMessage(req))
    .getOrElseThrow(e -> new SlackCommunicationException("Failed to send", e));

// Opcja 3: Either dla caller decyduje
return Try.of(() -> methods.chatPostMessage(req)).toEither();
```
**Interview trap:** Rekruter może spytać „czy to nie jest exception handling przez pominięcie?" — tak, `getOrElse(emptyList)` w getUsersByIds tworzy silent failure. Dla Slack bot który nie może wysłać wiadomości, to może być akceptowalne (log + continue) albo nie (zależy od SLA).
**Tags:** vavr, error-handling, try-monad, code-review, analyze

## Q-THECODEST-031 [bloom: analyze]
**Question:** Porównaj podejście The Codest do testowania (JUnit 5 + Mockito, @VisibleForTesting, brak integration testów w publicznym kodzie) do filozofii Atipery (~100% unit test coverage na domain layer, TestContainers). Jakie wnioski dla pracy w każdej z firm?
**Model answer:** **The Codest (z kodu publicznego):** unit testy z Mockito, package-private methods dla testowalności, brak widocznych integration testów i TestContainers. Sugeruje pragmatyczne podejście — testuj co ważne, nie ścigaj się za coverage. `ActivityRewardsTest` ma tylko boilerplate setup (@Mock, @Spy, @InjectMocks) bez żadnych `@Test` — to może być WIP lub celowo puste (tylko kompilacja). **Atipera (z ich job req):** ~100% unit coverage na domain layer, TestContainers (real DB w testach), strict quality gate. Sugeruje silną kulturę TDD, domain-driven design gdzie domain jest czyste i przetestowane izolowane. **Implikacje dla rozmowy w The Codest:** Pokaż że umiesz pisać testy ale też że nie jesteś dogmatyczny. Skup się na strategii testowania (what to test, not how much). **Implikacje dla Atipery:** Musisz znać TDD, TestContainers, architekturę hex gdzie domain nie zależy od infrastruktury. **Wniosek:** Różne firmy, różne kultury. W The Codest zadaj pytanie „jaki macie coverage target?" i „czy robicie integration tests?". To pokaże zainteresowanie qualityą.
**Interview trap:** Nie mów że The Codest ma słabe testy — nie wiesz co jest w prywatnych repo. Publiczne repo to tylko próbka. Zakładaj że mają wewnętrzne standardy których nie widzisz.
**Tags:** testing-philosophy, tdd, mockito, testcontainers, culture

## Q-THECODEST-032 [bloom: analyze]
**Question:** The Codest zatrudnia na Java/Kotlin, ale ich publiczny kod jest tylko Java. Jakie pytania o Kotlin możesz się spodziewać na rozmowie? Jak przygotować odpowiedzi znając ich Java styl?
**Model answer:** **Spodziewane pytania Kotlin:** (1) „Czym data class różni się od Java record?" — data class generuje copy(), componentN() dla destructuring, equals/hashCode/toString. Record nie ma copy(). Data class może dziedziczyć i być dziedziczona (ograniczone). (2) „Co to są coroutines w Kotlin? Kiedy używać vs virtual threads Java 21?" — coroutines to structured concurrency, suspend functions, lightweight. Virtual threads (Loom) to Java-native alternative. (3) „Co to sealed class w Kotlin vs Java?" — Kotlin sealed class działa na poziomie pakietu, wszystkie subclasy muszą być w tym samym pliku (lub package w nowszych Kotlin). (4) „Extension functions — kiedy używasz?" — dodawanie metod do istniejących klas bez dziedziczenia. (5) „Null safety — jak Kotlin rozwiązuje NPE?" — typy nullable (`String?`) vs non-null (`String`), operator `?.`, `!!`, `?:` (Elvis). **Twoja pozycja:** Znasz ich Java styl (Guice, records, functional). Kotlin data class = records + więcej. Kotlin extension functions = bardziej eleganckie static utility methods. Kotlin sealed = lepsze Java sealed classes. Pokaż że Java wzorce które znają mają bezpośrednie Kotlin equivalenty.
**Interview trap:** Nie myl Kotlin coroutines z Java CompletableFuture — coroutines są lightweight (wiele tysięcy na heap) i mają structured cancellation. CompletableFuture to promise/future model bez cancellation support.
**Tags:** kotlin, data-class, sealed-class, coroutines, null-safety, java-kotlin-comparison
