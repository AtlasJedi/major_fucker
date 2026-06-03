# Testing — question bank

> Testing is the discipline that separates engineers who ship working software from engineers who ship bugs with good intentions. For a senior Java/Spring backend interview, examiners expect fluency across the full test pyramid, JUnit 5 mechanics, Mockito internals, Spring test slices, Testcontainers, and higher-order concerns: coverage as a proxy for quality (and why it fails), mutation testing, consumer-driven contracts, and flaky tests. This bank covers all of it at the depth expected for a 5-year engineer aiming for a senior role.

## Scope

- Test pyramid: unit / integration / E2E, ratios, inverted-pyramid anti-pattern
- Test-double taxonomy: dummy, stub, fake, spy, mock — differences and when to use each
- AAA (Arrange-Act-Assert) and Given-When-Then patterns
- TDD: red-green-refactor cycle, when to use it, limitations
- JUnit 5 Jupiter: lifecycle annotations, @Nested, @ParameterizedTest, @DisplayName, @Tag, @TestInstance, assertThrows, assertAll
- JUnit 5 extensions vs JUnit 4 runners and rules (migration path)
- Mockito: mock vs spy vs stub, when().thenReturn(), thenThrow(), verify(), ArgumentCaptor, @Mock/@InjectMocks/@ExtendWith, BDDMockito, mocking statics and final classes, why not to mock value objects
- Spring test slices: @SpringBootTest vs @WebMvcTest vs @DataJpaTest, @MockBean, MockMvc, WebTestClient, context caching, @Transactional rollback
- Testcontainers: real Postgres/Kafka vs H2, @DynamicPropertySource, container reuse
- Code coverage: line vs branch, JaCoCo configuration, why coverage is not quality
- Mutation testing with PIT: what it measures, how it differs from coverage
- Consumer-driven contract testing: Pact workflow, Pact Broker, Spring Cloud Contract comparison
- Flaky tests: root causes, detection, mitigation
- What to test vs what not to test

---

## Q-TEST-001 [bloom: recall] [level: junior]
**Question:** Describe the test pyramid. Name the three layers, their typical tools in a Java/Spring project, and the approximate ratio between them.

**Model answer:** The test pyramid has three layers (bottom to top):

| Layer | Speed | Scope | Tools |
|---|---|---|---|
| Unit | milliseconds | single class/method in isolation | JUnit 5 + Mockito |
| Integration | seconds | multiple components wired together, or slices | @SpringBootTest, @DataJpaTest, Testcontainers |
| E2E | minutes | full stack against a running environment | RestAssured, Selenium, Playwright |

Target ratio: roughly **70% unit, 20% integration, 10% E2E**. The pyramid shape reflects cost: unit tests are cheap to write, fast to run, and pinpoint failures precisely. E2E tests are slow, brittle, expensive to maintain, and give no diagnostic precision.

**Interview trap:** What is the "inverted pyramid" (ice-cream cone) anti-pattern? — When most tests are E2E or full-context @SpringBootTest and very few are unit tests. Symptoms: 20-minute CI runs, tests that break for irrelevant reasons, no fast feedback loop. Fix: push logic down into service/domain classes, test those with Mockito, use slices for integration concerns only.

**Tags:** test-pyramid, unit-test, integration-test, e2e, testing-strategy

---

## Q-TEST-002 [bloom: recall] [level: junior]
**Question:** Name the five types of test doubles and explain each in one sentence.

**Model answer:** Test doubles are objects that stand in for real collaborators in a test. The five types (Gerard Meszaros taxonomy):

| Double | Definition |
|---|---|
| **Dummy** | Passed around but never used; fills a parameter slot (e.g., `null` or `new FakeLogger()`). |
| **Stub** | Returns canned answers to calls made during the test; no verification of how it was called. |
| **Fake** | Has working business logic, but simplified (e.g., an in-memory repository instead of a real DB). |
| **Spy** | A real object with some calls recorded or overridden; partial mock. |
| **Mock** | Pre-programmed with expectations about which calls it will receive; verified at the end of the test. |

In Mockito, `@Mock` creates a mock (default stubs, verifiable), `Mockito.spy()` creates a spy (real calls unless stubbed).

**Interview trap:** What's the difference between a stub and a mock? — A stub returns data; it doesn't verify behavior. A mock verifies that specific interactions happened. Using `when().thenReturn()` alone is stubbing; adding `verify()` makes it a mock. Over-relying on `verify()` leads to brittle tests that break on implementation change without behavioral change.

**Tags:** test-doubles, stub, mock, spy, fake, dummy, mockito

---

## Q-TEST-003 [bloom: recall] [level: junior]
**Question:** What is the AAA pattern in unit tests? Describe each phase.

**Model answer:** AAA stands for **Arrange – Act – Assert**. It structures a test into three clearly separated phases:

- **Arrange**: set up the system under test, create test data, configure mocks/stubs.
- **Act**: invoke the single behavior being tested (usually one method call).
- **Assert**: verify the outcome — return value, state change, or interaction with a collaborator.

```java
@Test
void findUser_whenExists_returnsDto() {
    // Arrange
    when(repo.findById(1L)).thenReturn(Optional.of(new User(1L, "Alice")));

    // Act
    UserDto result = service.findUser(1L);

    // Assert
    assertThat(result.getName()).isEqualTo("Alice");
}
```

Blank lines between phases make the structure immediately visible. In BDD (Behaviour-Driven Development), AAA maps directly to **Given – When – Then**.

**Interview trap:** If you have more than one Act phase in a test, what does that tell you? — The test is probably testing too many behaviors at once. Each test should verify exactly one behavior. Multiple Acts usually mean the test should be split.

**Tags:** aaa, arrange-act-assert, given-when-then, bdd, test-structure

---

## Q-TEST-004 [bloom: recall] [level: junior]
**Question:** What are the core lifecycle annotations in JUnit 5, and how do @BeforeAll / @AfterAll differ from @BeforeEach / @AfterEach?

**Model answer:** JUnit 5 (Jupiter) lifecycle annotations live in `org.junit.jupiter.api`:

| Annotation | When it runs | Frequency |
|---|---|---|
| `@BeforeAll` | Once before all tests in the class | Once per class |
| `@AfterAll` | Once after all tests in the class | Once per class |
| `@BeforeEach` | Before each test method | Per test method |
| `@AfterEach` | After each test method | Per test method |

By default, JUnit 5 creates a **new instance of the test class per test method** (PER_METHOD lifecycle). This means `@BeforeAll` methods must be `static` — they run before any instance is created. If you annotate the class with `@TestInstance(Lifecycle.PER_CLASS)`, the same instance is reused across all tests and `@BeforeAll` can be non-static.

Other key annotations: `@Test`, `@Disabled("reason")`, `@Tag("slow")`, `@DisplayName("human-readable name")`, `@Nested` (inner class grouping), `@Timeout`.

**Interview trap:** You write a `@BeforeAll` method without making it `static` in a standard JUnit 5 class. What happens? — It won't compile cleanly (or JUnit throws `JUnitException` at runtime) because the method must be `static` when using the default PER_METHOD lifecycle. Fix: either make it `static` or add `@TestInstance(Lifecycle.PER_CLASS)`.

**Tags:** junit5, lifecycle, beforeall, beforeeach, testinstance, per-class

---

## Q-TEST-005 [bloom: recall] [level: junior]
**Question:** What is @ParameterizedTest in JUnit 5 and why would you use it?

**Model answer:** `@ParameterizedTest` runs the same test method multiple times with different arguments, avoiding copy-paste test duplication. Arguments are provided by a source annotation:

| Source annotation | Provides |
|---|---|
| `@ValueSource` | Single-value array of primitives/Strings |
| `@CsvSource` | Multiple comma-separated value rows inline |
| `@CsvFileSource` | Rows from a CSV file on the classpath |
| `@MethodSource` | A static method returning a `Stream<Arguments>` |
| `@EnumSource` | All or a subset of enum values |

```java
@ParameterizedTest
@CsvSource({"1, true", "0, false", "-1, false"})
void isPositive(int input, boolean expected) {
    assertEquals(expected, NumberUtils.isPositive(input));
}
```

Use it when: the same logic must hold for many input/output pairs (boundary conditions, equivalence classes), or to test multiple error messages, HTTP status codes, or validation rules.

**Interview trap:** What's wrong with testing only the happy path in a parameterized test? — It misses boundary conditions (null, empty, min/max values, empty list). Good parameterized tests always include at least one happy-path case, one edge case, and one invalid input.

**Tags:** junit5, parameterized-test, csvSource, methodSource, data-driven

---

## Q-TEST-006 [bloom: understand] [level: junior]
**Question:** What is the difference between @Mock and @InjectMocks in Mockito, and what annotation do you need on the test class to activate them?

**Model answer:** `@Mock` creates a Mockito mock — an object where all methods return default values (`null`, `0`, `false`, empty collections) unless stubbed with `when().thenReturn()`. Nothing is wired automatically; it's just a mock object.

`@InjectMocks` creates a real instance of the annotated class and **injects all @Mock (and @Spy) fields into it**. Injection strategy (tried in order): constructor injection → setter injection → field injection (via reflection). If constructor injection succeeds, that's what's used.

To activate these annotations without calling `MockitoAnnotations.openMocks(this)` manually, annotate the test class with:

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repo;
    @InjectMocks UserService service; // gets repo injected
}
```

`@ExtendWith(MockitoExtension.class)` is the JUnit 5 way; the JUnit 4 equivalent was `@RunWith(MockitoJUnitRunner.class)`.

**Interview trap:** What happens if @InjectMocks can't find a matching constructor parameter? — It falls back to setter injection. If setters are absent, it uses field injection via reflection. If nothing matches, the field is left `null` and your test will NPE. This is why constructor injection is preferred — @InjectMocks works reliably, and you can also test without Mockito by calling `new UserService(mockRepo)` directly.

**Tags:** mockito, @mock, @injectmocks, @extendwith, mockitoextension, junit5

---

## Q-TEST-007 [bloom: understand] [level: junior]
**Question:** What does assertThrows do in JUnit 5, and how does it differ from try/catch in tests?

**Model answer:** `assertThrows` verifies that a code block throws a specific exception type. It returns the thrown exception so you can make further assertions on it:

```java
IllegalArgumentException ex = assertThrows(
    IllegalArgumentException.class,
    () -> service.process(null)
);
assertThat(ex.getMessage()).contains("null input");
```

Compared to try/catch:
- **Cleaner** — no boilerplate, no `fail("should have thrown")` sentinel.
- **Returns the exception** — you can assert on message, cause, etc.
- **Fails immediately** — if no exception is thrown, the test fails with a clear message.
- **Exact type check** — only passes if the exception is exactly the specified type (or a subtype).

AssertJ equivalent: `assertThatThrownBy(() -> service.process(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("null input")` — often preferred because of fluent chaining.

**Interview trap:** assertThrows passes even if a subclass of the expected exception is thrown. Is this desirable? — Usually yes (Liskov substitution: a subclass IS-A superclass). But if you need to verify the exact type, use `assertThat(ex.getClass()).isEqualTo(IllegalArgumentException.class)` separately.

**Tags:** junit5, assertThrows, assertThatThrownBy, exception-testing, assertj

---

## Q-TEST-008 [bloom: understand] [level: regular]
**Question:** Explain the difference between JUnit 5 extensions and JUnit 4 runners / rules. Why did JUnit 5 replace both with a single extension model?

**Model answer:** **JUnit 4** had two separate extension hooks:
- `@RunWith(SomeRunner.class)` — replaced the entire test runner (e.g., `SpringJUnit4ClassRunner`, `MockitoJUnitRunner`). Only one runner per class.
- `@Rule` / `@ClassRule` — lighter-weight per-test/per-class lifecycle hooks (e.g., `TemporaryFolder`, `ExpectedException`). Could have multiple rules, but the API was verbose.

**JUnit 5** replaced both with a single unified `Extension` API via `@ExtendWith`. An extension can implement any combination of callback interfaces: `BeforeEachCallback`, `AfterEachCallback`, `ParameterResolver`, `TestExecutionExceptionHandler`, `TestInstancePostProcessor`, etc. Multiple extensions can be stacked:

```java
@ExtendWith({MockitoExtension.class, SpringExtension.class})
```

Advantages of the extension model:
1. **Composable** — multiple extensions on one class, no conflict.
2. **Declarative** — annotation-driven, no inheritance needed.
3. **Granular** — extensions implement only the callbacks they need.
4. **Programmatic** — `@RegisterExtension` for instance-level or dynamic registration.

Spring Boot ships `SpringExtension` (also wired automatically by `@SpringBootTest`, `@WebMvcTest`, etc.).

**Interview trap:** What's the JUnit 5 equivalent of JUnit 4's @Rule TemporaryFolder? — `@TempDir` annotation (built into JUnit 5). Inject it as a `Path` or `File` field/parameter; JUnit creates and deletes the directory automatically.

**Tags:** junit5, junit4, extension, runner, rule, springextension, migration

---

## Q-TEST-009 [bloom: understand] [level: regular]
**Question:** What is the difference between a Mockito mock and a Mockito spy? When would you use a spy?

**Model answer:**

| | Mock | Spy |
|---|---|---|
| Created from | bytecode proxy of the class/interface | wraps a **real object instance** |
| Default behavior | all methods return defaults (null, 0, empty) | delegates to the **real method** |
| Stubbing syntax | `when(mock.method()).thenReturn(x)` | `doReturn(x).when(spy).method()` |
| Use case | full isolation — replace all behavior | partial mock — override some methods, keep others real |

```java
List<String> real = new ArrayList<>();
List<String> spy = Mockito.spy(real);

doReturn(100).when(spy).size(); // override size()
spy.add("hello");               // real method: list now has one element

assertThat(spy.get(0)).isEqualTo("hello"); // real
assertThat(spy.size()).isEqualTo(100);     // stubbed
```

Use a spy when: the class has significant logic you want to keep (e.g., a legacy class you can't refactor), and you only need to override one or two side-effect methods (like a file write or external call).

**Interview trap:** Why should you use `doReturn(x).when(spy).method()` instead of `when(spy.method()).thenReturn(x)` on a spy? — Because `when(spy.method())` actually **invokes the real method first** during setup, which may have side effects or throw an exception. `doReturn(x).when(spy).method()` bypasses the real method entirely during stubbing.

**Tags:** mockito, mock, spy, doReturn, partial-mock, stubbing

---

## Q-TEST-010 [bloom: apply] [level: regular]
**Question:** How does ArgumentCaptor work in Mockito, and when would you use it instead of a direct argument matcher?

**Model answer:** `ArgumentCaptor` captures the actual argument passed to a mocked method so you can make detailed assertions on it after the fact:

```java
@Captor ArgumentCaptor<EmailRequest> captor;

@Test
void sendWelcomeEmail_capturesCorrectRecipient() {
    service.registerUser("alice@example.com");

    verify(emailService).send(captor.capture());
    EmailRequest req = captor.getValue();
    assertThat(req.getTo()).isEqualTo("alice@example.com");
    assertThat(req.getSubject()).startsWith("Welcome");
}
```

Use ArgumentCaptor when:
- The argument is built internally (you can't pass it in directly).
- You need to assert multiple fields on the captured object.
- `argThat(predicate)` would work but you want readable failure messages.

Do **not** use ArgumentCaptor when:
- A simple `eq(expectedValue)` or `any()` matcher is enough — captors add noise.
- You find yourself capturing and then only asserting one simple equality — that's what `verify(mock).method(eq(x))` is for.

For multiple invocations, `captor.getAllValues()` returns a `List` of all captured arguments.

**Interview trap:** Can you use ArgumentCaptor in a stubbing (`when()`) call? — Technically yes but it's an anti-pattern. Captors are for verification, not stubbing. For stubbing with complex matching, use `argThat()` or `any()`.

**Tags:** mockito, argumentcaptor, verify, argument-matching, argThat

---

## Q-TEST-011 [bloom: apply] [level: regular]
**Question:** What is BDDMockito and how does its API differ from standard Mockito?

**Model answer:** `BDDMockito` is a wrapper around Mockito that renames the stubbing API to match BDD (Given-When-Then) vocabulary. It's in `org.mockito.BDDMockito`:

| Standard Mockito | BDDMockito |
|---|---|
| `when(mock.method()).thenReturn(x)` | `given(mock.method()).willReturn(x)` |
| `when(mock.method()).thenThrow(ex)` | `given(mock.method()).willThrow(ex)` |
| `verify(mock).method()` | `then(mock).should().method()` |

```java
// BDD style
given(repo.findById(1L)).willReturn(Optional.of(new User(1L, "Alice")));

// Act
UserDto result = service.findUser(1L);

// Assert + verify
assertThat(result.getName()).isEqualTo("Alice");
then(repo).should().findById(1L);
then(repo).shouldHaveNoMoreInteractions();
```

The behavior is identical to standard Mockito — it's purely a style choice. Advantages: the test reads as a sentence, vocabulary aligns with @Nested class names and @DisplayName annotations, and it avoids the `when()` clash when using standard BDD test structure.

**Interview trap:** Does BDDMockito change Mockito's internal behavior or just rename methods? — Just renames. It delegates to the same underlying Mockito infrastructure. There is no behavioral difference.

**Tags:** mockito, bddmockito, given-willReturn, bdd, testing-style

---

## Q-TEST-012 [bloom: apply] [level: regular]
**Question:** What is the difference between @SpringBootTest and @WebMvcTest? When would you choose each?

**Model answer:**

| | @SpringBootTest | @WebMvcTest |
|---|---|---|
| Context loaded | Full application context (all beans) | Web layer only (controllers, filters, HandlerMapping, security config) |
| Database | Real (or Testcontainers/H2) unless mocked | Not loaded |
| Services, repos | Real beans (or @MockBean them) | Not loaded — must @MockBean everything the controller uses |
| Speed | Slow (seconds to minutes) | Fast (sub-second) |
| Use case | Integration tests that need the full stack | Controller unit tests: JSON serialization, validation, error mapping, security |

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean  OrderService service; // must mock everything the controller needs

    @Test
    void getOrder_returns200() throws Exception {
        when(service.findById(1L)).thenReturn(new OrderDto(1L, "OPEN"));
        mockMvc.perform(get("/orders/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
```

Choose **@WebMvcTest** for: HTTP status codes, request validation, JSON serialization, error handling, security constraints.
Choose **@SpringBootTest** for: full integration flows that span multiple layers, startup verification, smoke tests.

**Interview trap:** Does @WebMvcTest load Spring Security? — Yes, if `spring-security` is on the classpath. Security is part of the web layer (filter chain). You can use `@WithMockUser` or `@WithAnonymousUser` to control auth in @WebMvcTest tests. If you forget this, your tests will return 401/403 instead of 200.

**Tags:** spring-test, springboottest, webmvctest, mockmvc, slices, context

---

## Q-TEST-013 [bloom: apply] [level: regular]
**Question:** What is @DataJpaTest and how does it handle transactions? What is the H2 problem and how do you fix it?

**Model answer:** `@DataJpaTest` loads only the JPA layer: `EntityManager`, `DataSource`, Spring Data repositories, Hibernate. It **does not** load controllers, services, or other beans.

**Transaction behavior**: each test is wrapped in a transaction that is **rolled back after the test** by default. This means tests are isolated — no database state leaks between tests. If you need to verify persistence (e.g., check that a flush actually wrote to the DB), use `@Commit` or `@Rollback(false)` on the test method, or flush explicitly.

**The H2 problem**: @DataJpaTest defaults to an in-memory H2 database (`@AutoConfigureTestDatabase(replace = ANY)`). H2 does not support:
- `JSONB` columns
- `ON CONFLICT DO UPDATE` (upsert)
- `GENERATED ALWAYS AS IDENTITY`
- Postgres-specific functions (`array_agg`, `to_tsvector`, etc.)
- Postgres DDL extensions

A green H2 test can fail on real Postgres. This creates a false sense of safety.

**Fix — Testcontainers**:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE) // don't replace datasource with H2
@Testcontainers
class OrderRepositoryTest {
    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }
}
```

**Interview trap:** Why should you use `replace = NONE` when using Testcontainers with @DataJpaTest? — Without it, Spring Boot replaces your configured datasource with an auto-configured H2. `replace = NONE` tells Boot to leave the datasource alone and use the one you configured via @DynamicPropertySource.

**Tags:** datajpatest, h2, testcontainers, transactional, rollback, postgres, spring-test

---

## Q-TEST-014 [bloom: apply] [level: regular]
**Question:** What is @MockBean and how does it differ from @Mock? What is the context-caching problem it can cause?

**Model answer:** `@MockBean` (Spring Test) creates a Mockito mock **and registers it as a Spring bean in the application context**, replacing the real bean. `@Mock` (pure Mockito) creates a mock with no interaction with the Spring context — it's never registered as a bean.

```java
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean  UserService userService; // replaces real UserService in context
}
```

**Context caching problem**: Spring caches application contexts by their configuration key (annotations, properties, loaded classes). When you add `@MockBean`, the context key includes the mock type. If two test classes use `@MockBean` for different beans (or the same bean with different stubs), Spring creates **separate contexts** — it can't reuse one. This multiplies startup costs.

Mitigation strategies:
- Share a base test class or `@TestConfiguration` that defines all `@MockBean`s in one place — same config key, same cached context.
- Use `@MockBean` sparingly. For @WebMvcTest, mock only what the specific controller needs.
- Avoid `@DirtiesContext` — it forces a context rebuild.

**Interview trap:** You have 50 @WebMvcTest classes, each with @MockBean for the same service. How many Spring contexts will be started? — Potentially one per test class if each has a different configuration key. They should all share one context if the set of @MockBean declarations is identical. The key insight: context caching is all-or-nothing per configuration.

**Tags:** mockbean, mock, context-caching, spring-test, webmvctest, performance

---

## Q-TEST-015 [bloom: apply] [level: regular]
**Question:** How does MockMvc work internally, and how does WebTestClient differ from it?

**Model answer:** `MockMvc` drives the **Spring MVC DispatcherServlet directly**, without starting a real HTTP server. Requests are handled in-process: `MockMvc.perform()` constructs a `MockHttpServletRequest`, dispatches through the full servlet filter chain and `DispatcherServlet`, and returns a `MockHttpServletResponse`. This means:
- All MVC logic executes: handler mapping, argument resolution, interceptors, exception handlers, message converters.
- No network overhead.
- Available via `@Autowired` in `@WebMvcTest` tests.

```java
mockMvc.perform(get("/orders/1")
    .header("Authorization", "Bearer token"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.id").value(1))
    .andDo(MockMvcResultHandlers.print()); // dumps request/response for debugging
```

**WebTestClient** is the reactive alternative. Originally for WebFlux (Netty-based), but also works against a running server (with `@SpringBootTest(webEnvironment = RANDOM_PORT)`) or against Spring MVC via `MockMvcWebTestClient.bindTo(mockMvc)`. Key difference: WebTestClient has a fluent reactive-style API and supports streaming/SSE; MockMvc is synchronous only.

```java
webTestClient.get().uri("/orders/1")
    .exchange()
    .expectStatus().isOk()
    .expectBody(OrderDto.class)
    .value(order -> assertThat(order.getId()).isEqualTo(1L));
```

**Interview trap:** Does MockMvc use a real HTTP port? — No. It bypasses the network entirely and dispatches directly to DispatcherServlet. If you need to test against a real port (e.g., to verify filter configuration that only applies to real requests, or to test Servlet containers), use `@SpringBootTest(webEnvironment = RANDOM_PORT)`.

**Tags:** mockmvc, webtestclient, webflux, spring-test, dispatcherservlet, random-port

---

## Q-TEST-016 [bloom: apply] [level: regular]
**Question:** What are line coverage and branch coverage? What does JaCoCo measure, and why is 80% coverage not a guarantee of quality?

**Model answer:** **Line coverage** (statement coverage): percentage of source lines executed by at least one test. Easy to satisfy — a single test that hits a method but never checks the output can achieve 100% line coverage.

**Branch coverage**: percentage of branches (both sides of each `if`/`else`, `ternary`, `switch` arm, `&&`/`||` operand) exercised. Harder to game — you must have tests for both the `true` and `false` path of every condition.

JaCoCo measures: line coverage, branch coverage, method coverage, class coverage, instruction coverage (bytecode), cyclomatic complexity.

**Why coverage is not quality**:
1. **Assertions can be absent** — a test that calls a method without asserting anything counts as coverage.
2. **Happy-path bias** — tests cover the normal flow; 100% line coverage is reachable while never testing error paths.
3. **Wrong metric** — 80% coverage on trivial getters with no business logic means very little; 70% coverage on complex discount calculation code is a serious gap.
4. **Mutation tests reveal the truth** — coverage tells you code was *executed*, not that the *behavior* is correct.

Best practices:
- Set a minimum threshold in JaCoCo (e.g., 80% branch) to catch regressions, not as a quality target.
- Exclude generated code (Lombok, MapStruct, DTOs) from reports via `<excludes>`.
- Pair JaCoCo with PIT (mutation testing) for a real quality signal.

**Interview trap:** You have 100% line coverage but the business logic has a bug. How is that possible? — A test executed every line but never asserted the return value or side effect. Coverage measures execution, not correctness.

**Tags:** coverage, jacoco, line-coverage, branch-coverage, mutation-testing, quality

---

## Q-TEST-017 [bloom: understand] [level: regular]
**Question:** What is TDD (Test-Driven Development)? Describe the red-green-refactor cycle and name situations where TDD is especially useful or especially painful.

**Model answer:** TDD is a development practice where you write a failing test **before** writing the production code to make it pass, then refactor.

**Red-Green-Refactor cycle**:
1. **Red** — Write a test for the next small increment of behavior. Run it: it fails (no implementation exists).
2. **Green** — Write the minimum code to make the test pass. Don't over-engineer.
3. **Refactor** — Clean up: remove duplication, improve names, extract abstractions. Tests must still pass.

Repeat for the next increment.

**Where TDD excels**:
- Pure business logic (calculations, validation rules, state machines).
- Algorithm development where the spec is clear.
- Fixing bugs — write a test that reproduces the bug first.
- Designing APIs — TDD forces you to think about the interface before the implementation.

**Where TDD is painful**:
- UI/controller layer — tests require complex setup; behavior changes often.
- Exploratory work — you don't know the design yet; tests slow down discovery.
- Infrastructure code (Kafka consumer, DB schema) — heavily coupled to environment.
- Third-party integration — the API is not yours to design.

**Interview trap:** What is the difference between TDD and writing tests after the code? — TDD shapes the design: test-first forces you to think about API ergonomics, dependencies, and testability before you write a line of code. Retrofitting tests after the fact often reveals untestable designs (tight coupling, no injection points), leading to either hard-to-write tests or production code restructured to accommodate testing as an afterthought.

**Tags:** tdd, red-green-refactor, test-first, design, unit-test

---

## Q-TEST-018 [bloom: apply] [level: senior]
**Question:** Explain how Testcontainers works under the hood. What are the advantages of real containers over H2 in repository tests, and what are the operational costs? How would you make containers fast in a large test suite?

**Model answer:** **How it works**: Testcontainers uses the Docker daemon (via `docker-java` library) to pull and start container images. It manages the full lifecycle: `@Container` starts the container before tests, stops it after. The container runs inside Docker; your test JVM connects to it via exposed ports.

`@DynamicPropertySource` (Spring Boot 2.2.6+) lets you inject the dynamic connection URL into the Spring context after the container is started:

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
}
```

**Advantages over H2**:
- Same DB engine, same SQL dialect, same functions — no divergence.
- Tests catch Postgres-specific failures: constraint names, `ON CONFLICT`, `GENERATED ALWAYS AS`, `JSONB` operators, custom types.
- Schema migrations (Flyway/Liquibase) run against the real target DB engine.
- Kafka, Redis, Elasticsearch — no in-memory fake; real behavior.

**Operational costs**:
- Container startup: ~2-5 seconds per container per test class.
- Docker daemon required — CI must have Docker-in-Docker or a Docker socket.
- Image pull on first run (mitigated by CI image caching).

**Making containers fast**:
1. **`static` container field + `@Container`** — shared across all test methods in one class; one startup cost.
2. **Singleton container pattern** — use a static base class that starts one container for the entire JVM run (all test classes share one container).
3. **Reuse mode** — `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`; keeps containers alive between local runs. (Not for CI — can cause state leakage.)
4. **`@SpringBootTest` context caching** — minimize `@MockBean` variation so the same Spring context (with its single container) is reused.

**Interview trap:** Can Testcontainers run without Docker? — Yes, via Testcontainers Cloud (remote container execution) or via the new Podman/Rancher Desktop backends. But the canonical setup is a local Docker daemon.

**Tags:** testcontainers, postgres, docker, h2, dynamicpropertysource, container-reuse, spring-test

---

## Q-TEST-019 [bloom: analyze] [level: senior]
**Question:** What is mutation testing? How does PIT work, and why does it reveal gaps that JaCoCo misses?

**Model answer:** **Mutation testing** measures the quality of tests by asking: "If I introduce a deliberate bug, do my tests catch it?"

**PIT (Pitest)** works:
1. Compiles your code normally.
2. Creates many **mutants** — modified copies of the bytecode with small changes ("mutations"):
   - Change `>` to `>=` in a conditional.
   - Replace `return x` with `return 0`.
   - Negate a boolean (`if (flag)` → `if (!flag)`).
   - Remove a void method call.
   - Change `+` to `-` in arithmetic.
3. Runs your test suite against each mutant.
4. A mutant is **killed** if at least one test fails. A mutant **survives** if all tests pass.
5. Reports **mutation score** = killed / total mutants.

**Why PIT reveals gaps that JaCoCo misses**:

JaCoCo says: "this line was executed."
PIT says: "this line was executed AND if I corrupt the logic, your tests notice."

Example: 100% branch coverage but no assertion on the return value → PIT kills 0% of mutants on that branch. The tests are hollow.

Common surviving mutants and what they reveal:
- Surviving negation mutation: test doesn't assert the conditional outcome.
- Surviving `> to >=` mutation: missing boundary-condition test.
- Surviving void-method removal: test doesn't verify the side effect.

**Operational reality**: PIT is slow (it runs your test suite N times for N mutants, with some optimization). Run it in CI on nightly or merge builds, not on every commit. Configure it to target only business logic packages; exclude DTOs, generated code, configuration.

**Interview trap:** Your mutation score is 90%. Does that mean your code is fully tested? — No. Mutation testing also has blind spots: equivalent mutants (a mutation that doesn't change observable behavior — PIT can't kill them; they're not bugs). A 90% score is a strong signal but not a guarantee.

**Tags:** mutation-testing, pit, pitest, jacoco, coverage-quality, mutant, killed, survived

---

## Q-TEST-020 [bloom: apply] [level: senior]
**Question:** What is consumer-driven contract testing with Pact? Describe the full workflow from consumer to producer verification, and compare it to Spring Cloud Contract.

**Model answer:** **Consumer-driven contract testing** inverts the traditional approach: the **consumer** defines what it expects from the provider, and the **provider** verifies that it meets those expectations. Neither side needs to be running for the tests to work.

**Pact workflow**:

1. **Consumer side** — write a Pact test that defines the interaction: given some provider state, when I send request X, I expect response Y.
   ```java
   @PactTestFor(providerName = "OrderService")
   @Pact(consumer = "PaymentService")
   RequestResponsePact createOrder(PactDslWithProvider builder) {
       return builder
           .given("order 1 exists")
           .uponReceiving("GET /orders/1")
           .method("GET").path("/orders/1")
           .willRespondWith()
           .status(200)
           .body(newJsonBody(o -> o.numberValue("id", 1).stringValue("status", "OPEN")).build())
           .toPact();
   }
   ```
   Running this test: (a) generates a **pact file** (JSON contract), (b) runs the consumer against a mock provider that returns the defined response, verifying the consumer can parse it.

2. **Pact Broker** — consumer publishes the pact file to a central broker (self-hosted or PactFlow SaaS).

3. **Provider side** — in CI, the provider pulls pact files from the broker and verifies its real implementation satisfies them:
   ```java
   @Provider("OrderService")
   @PactBroker
   class OrderProviderTest {
       @State("order 1 exists")
       void orderExists() { /* seed data */ }
   }
   ```

**Benefits**: Producers can't silently break consumers. The provider can verify against all consumer versions simultaneously. Works across polyglot systems (Pact is language-agnostic: Java, Node, Go, Python all speak pact files).

**Spring Cloud Contract comparison**:

| | Pact | Spring Cloud Contract |
|---|---|---|
| Who writes contracts | Consumer | Producer |
| Format | JSON pact files | Groovy/YAML DSL |
| Stub generation | Consumer-side stubs from pact | Producer generates WireMock stubs for consumer |
| Polyglot | Yes | Primarily Spring ecosystem |
| CI integration | Pact Broker / PactFlow | Git + CI pipeline |

Choose Pact for polyglot microservices. Choose Spring Cloud Contract for Spring-only ecosystems where the producer team controls the contracts.

**Interview trap:** What is a "provider state" in Pact and who is responsible for setting it up? — A provider state is a named prerequisite ("order 1 exists", "user is authenticated"). The **provider** must implement `@State` methods that seed the necessary data before Pact runs the verification request. Without it, the test would fail because the real provider has no data to respond with.

**Tags:** pact, contract-testing, consumer-driven, spring-cloud-contract, pact-broker, microservices

---

## Q-TEST-021 [bloom: analyze] [level: senior]
**Question:** What are flaky tests? Describe their common root causes in a Spring/Java backend project and how you would systematically detect and mitigate them.

**Model answer:** A **flaky test** passes and fails non-deterministically without any code change. It's one of the most destructive forces in a CI pipeline: it erodes trust in the test suite (people start ignoring red builds), hides real failures, and wastes engineer time.

**Common root causes**:

| Root cause | Example |
|---|---|
| Shared mutable state | Static fields not reset between tests; leaked H2 data when @Transactional rollback is missing |
| Time dependency | `LocalDate.now()` in production code; test passes in morning, fails at midnight |
| Thread ordering / race conditions | `CompletableFuture` not awaited; async listener not triggered before assertion |
| External dependency | Test calls real HTTP endpoint; that endpoint is slow or down |
| Port conflicts | `@SpringBootTest(webEnvironment = RANDOM_PORT)` but hardcoded port in config |
| Order dependency | Test B depends on data inserted by Test A; run in isolation, Test B fails |
| Container startup race | Testcontainers container not yet healthy before test runs |
| Floating-point / timestamp comparison | `assertEquals(0.1 + 0.2, 0.3)` |

**Detection**:
1. **Quarantine tagging** — `@Tag("flaky")`; run the suite many times in CI and track failure rates.
2. **Test retry plugins** — `junit-platform-launcher` or Surefire `rerunFailingTestsCount` to identify (not fix) flakes.
3. **Test history in CI** — track per-test pass rate over time.

**Mitigation**:
- Replace `Thread.sleep()` with `Awaitility.await().until(condition)` for async assertions.
- Use `@BeforeEach` to reset shared state.
- Use Testcontainers wait strategies (`.waitingFor(Wait.forHealthcheck())`).
- Fix time dependencies: inject a `Clock` and override it in tests.
- Use `@TestMethodOrder(MethodOrderer.Random.class)` to expose order dependencies.

**Interview trap:** Your manager says "just add a retry on flaky tests in CI and move on." What's wrong with that approach? — Retries mask the symptoms, they don't fix the cause. A flaky test that requires 3 retries to pass is a test that is either (1) not testing what it claims to, (2) revealing a real concurrency bug that shows up occasionally, or (3) hiding a timing issue in production code. You must investigate and fix the root cause. Retries are acceptable as a **temporary** measure while fixing, never as a permanent policy.

**Tags:** flaky-tests, test-reliability, shared-state, awaitility, determinism, ci

---

## Q-TEST-022 [bloom: apply] [level: senior]
**Question:** How do you test code that calls static methods (e.g., LocalDateTime.now(), UUID.randomUUID())? Describe two approaches and their trade-offs.

**Model answer:** Static method calls are the primary reason tests depend on time or external randomness — they make production code non-deterministic and untestable by default.

**Approach 1 — Inject a Clock (preferred)**

Replace `LocalDateTime.now()` with a `Clock` dependency:
```java
@Service
public class OrderService {
    private final Clock clock;
    // constructor injection

    public Order create(String item) {
        return new Order(item, LocalDateTime.now(clock));
    }
}
```
In production, inject `Clock.systemUTC()`. In tests:
```java
Clock fixed = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);
OrderService svc = new OrderService(fixed);
Order o = svc.create("widget");
assertThat(o.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 0));
```
Same pattern for `UUID.randomUUID()`: inject a `Supplier<UUID>` or a dedicated `IdGenerator` interface.

**Approach 2 — Mockito static mocking (last resort)**

Mockito 3.4+/5 supports mocking static methods via `MockedStatic`:
```java
try (MockedStatic<UUID> uuidMock = mockStatic(UUID.class)) {
    uuidMock.when(UUID::randomUUID).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    // test code
}
```
Scoped to the try-with-resources block to prevent leakage.

**Trade-offs**:

| | Clock injection | MockedStatic |
|---|---|---|
| Design impact | Improves design (explicit dependency) | No design change |
| Test speed | Fast | Slightly slower (bytecode instrumentation) |
| Thread safety | Thread-safe | Thread-unsafe — don't parallelize tests that use MockedStatic |
| Maintenance | Clear, explicit | Brittle — breaks if static method is refactored |

Always prefer injection. Use MockedStatic for legacy code you can't refactor.

**Interview trap:** Why is mocking value objects (like `Money`, `LocalDate`) an anti-pattern? — Value objects have no behavior worth mocking; they carry data and equality semantics. Mocking them breaks equality (a mocked `LocalDate` is not `equal` to another `LocalDate`), leads to over-specification, and signals the design is wrong — the SUT should accept values, not construct them internally from statics.

**Tags:** static-mocking, clock, mockito, mockedstatic, time-testing, value-objects

---

## Q-TEST-023 [bloom: analyze] [level: senior]
**Question:** Describe how Spring caches application contexts across test classes. What are the things that create a new context (cache miss), and how do you minimize startup time in a large test suite?

**Model answer:** Spring's `TestContext` framework caches `ApplicationContext` instances keyed by their **context configuration** — the combination of: `@ContextConfiguration` classes/locations, `@ActiveProfiles`, `@TestPropertySource`, `@SpringBootTest.properties`, the set of `@MockBean` / `@SpyBean` declarations, and custom `ContextCustomizer`s.

If two test classes share the **exact same configuration key**, the second class reuses the same context. Otherwise, a new context is created.

**Cache misses triggered by**:
- Different `@MockBean` sets (most common): adding `@MockBean UserService` in one class and `@MockBean OrderService` in another creates two contexts even if everything else is identical.
- `@DirtiesContext` — explicitly invalidates the cached context after the test class.
- `@TestPropertySource` or `properties = "..."` overrides that differ between classes.
- `@SpringBootTest(classes = ...)` pointing to different configs.
- Different `@ActiveProfiles`.

**Minimizing startup time**:
1. **Base test class with all @MockBeans**: define a common base class that declares all @MockBeans used across the test suite. All subclasses share one context.
   ```java
   @SpringBootTest
   abstract class IntegrationTestBase {
       @MockBean ExternalPaymentGateway paymentGateway;
       @MockBean EmailService emailService;
   }
   ```
2. **Avoid @DirtiesContext** — only use when the test genuinely corrupts the context (port binding, static state).
3. **Use slices instead of @SpringBootTest** — @WebMvcTest and @DataJpaTest have smaller contexts.
4. **Testcontainers singleton pattern** — one DB container for the entire JVM, shared via a static field in the base class.
5. **Parallel test execution** — JUnit 5 supports `junit.jupiter.execution.parallel.enabled=true`, but requires tests to be independent.

**Interview trap:** @DirtiesContext is annotated on a test class that only reads data. Is that appropriate? — Almost certainly not. @DirtiesContext should only be used when a test has actually modified the context (changed bean state, registered new beans, modified static state). Read-only tests never need it.

**Tags:** context-caching, springboottest, dirtiescontext, mockbean, test-performance, integration-test

---

## Q-TEST-024 [bloom: analyze] [level: senior]
**Question:** What is the "don't mock what you don't own" principle in Mockito? Give concrete examples.

**Model answer:** The principle: **mock interfaces and types you define, not third-party classes**. Mocking what you don't own creates tests that are tightly coupled to third-party implementation details and give false confidence.

**Why it's problematic to mock third-party classes**:
1. Third-party APIs have complex contracts (e.g., `HttpClient`, `EntityManager`). Mocking them requires you to replicate that contract correctly — if you get it wrong, your tests pass but production fails.
2. When the third-party library upgrades and changes behavior, your mocks don't update — tests stay green while production breaks.
3. Mocking concrete classes requires Mockito's inline mock-maker (opens modules, has limitations).

**What to do instead**:

| Scenario | Don't mock | Do this |
|---|---|---|
| `EntityManager` / JPA | Mock EntityManager | Use @DataJpaTest with real DB or Testcontainers |
| `HttpClient` / RestTemplate | Mock HttpClient | Use WireMock / MockServer to stub the HTTP endpoint |
| Jackson `ObjectMapper` | Mock ObjectMapper | Use a real ObjectMapper; use @JsonTest slice |
| `Clock` | Mock Clock | Inject `Clock.fixed(...)` directly — it's a JDK value |
| Your own `UserRepository` | Mock it — you own this interface | — |

Value objects (JDK types, domain values): never mock. Use real instances. A mocked `Optional<User>` is not equal to `Optional.of(user)` in any useful way.

**Interview trap:** You need to test a service that calls a Feign client interface. Should you mock the Feign client? — The Feign interface is yours (you declared it). Mocking it is acceptable for unit tests of the service layer. For testing the Feign client itself (serialization, URL mapping, error decoding), use WireMock.

**Tags:** mockito, mock-what-you-own, third-party, value-objects, wiremock, testing-philosophy

---

## Q-TEST-025 [bloom: analyze] [level: senior]
**Question:** What is the @Nested annotation in JUnit 5? Describe how you would use it to structure a complex test class and what lifecycle implications it has.

**Model answer:** `@Nested` creates an inner (non-static) test class inside a parent test class. It groups related test scenarios, improving readability and organization. Each `@Nested` class can have its own `@BeforeEach` / `@AfterEach` hooks that run **in addition to** the outer class's hooks.

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository repo;
    @InjectMocks UserService service;

    @Nested
    @DisplayName("findById")
    class FindById {
        @BeforeEach
        void setup() { /* specific setup for findById scenarios */ }

        @Test
        @DisplayName("returns DTO when user exists")
        void returnsDto() { ... }

        @Test
        @DisplayName("throws NotFoundException when missing")
        void throwsWhenMissing() { ... }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {
        @Test
        @DisplayName("persists and returns created user")
        void persists() { ... }

        @Test
        @DisplayName("throws DuplicateEmailException when email taken")
        void duplicateEmail() { ... }
    }
}
```

**Lifecycle order**: outer `@BeforeEach` → inner `@BeforeEach` → test → inner `@AfterEach` → outer `@AfterEach`.

**Benefits**:
- Tests read like a specification: "UserService > findById > returns DTO when user exists".
- Related edge cases are co-located.
- Shared setup within a nested class doesn't pollute other nested classes.
- `@DisplayName` at the nested level composes into readable test names in IDEs and CI reports.

**Limitation**: `@Nested` classes cannot be `static`. They have access to the outer class's fields (including `@Mock` / `@InjectMocks`).

**Interview trap:** Can a @Nested class have its own @Mock fields separate from the outer class? — No. A `@Nested` class is a non-static inner class; it shares the outer instance. You can declare additional @Mock fields in the nested class, but Mockito will inject those into the outer class's `@InjectMocks` too, which may cause unexpected injections.

**Tags:** junit5, nested, displayname, lifecycle, test-organization, structure

---

## Q-TEST-026 [bloom: analyze] [level: master]
**Question:** You're designing the test strategy for a new Spring Boot microservice that will own an order lifecycle (create, pay, ship, cancel). Describe your full testing architecture: what tests you'd write at each pyramid layer, what tools at each layer, and how you'd ensure contract safety with downstream consumers.

**Model answer:** **Unit layer** (~70% of tests, milliseconds):

- `OrderDomainService` logic: state machine transitions (valid/invalid state changes), business rules (can't cancel shipped order), discount calculation.
- `OrderMapper` (MapStruct): DTO → entity, entity → DTO — use `@JsonTest` slice or plain JUnit.
- Validators: custom `@OrderAmount` validator, each valid/invalid input.
- Tools: JUnit 5 + Mockito + AssertJ. No Spring context. Constructor-injected services, all deps as `@Mock`.

**Integration layer** (~20%, seconds):

- `OrderRepository` tests: save, findByStatus, custom queries with `JSONB`, pagination.
  - Tool: `@DataJpaTest` + Testcontainers (real Postgres 16) + `@AutoConfigureTestDatabase(replace = NONE)`.
  - Flyway migrations run against the container — verifies schema.
- `OrderController` tests: HTTP routing, validation, error mapping, security.
  - Tool: `@WebMvcTest` + `@MockBean OrderService` + MockMvc.
  - Verify: 201 on create, 422 on validation fail, 404 on not found, 401 without auth.
- Kafka producer tests: verify message published with correct structure.
  - Tool: `@SpringBootTest` + Testcontainers `KafkaContainer` + `KafkaTestConsumer`.

**E2E layer** (~10%, minutes):

- Full stack smoke tests: create order → pay → verify state transitions.
- Tool: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers (Postgres + Kafka) + RestAssured.
- Run in CI on merge to main, not on every PR.

**Contract safety with consumers**:

- Publish Pact contracts for each consumer of this service.
- In consumer repos (e.g., Fulfillment, Notification): `@PactTestFor` generates pact files; consumer CI publishes to Pact Broker.
- In this service's CI: `@Provider` verification step runs before deploy; it fails if any consumer's contract is broken.
- Use `can-i-deploy` CLI to gate production deployments.

**Build organization**:
- Maven profiles: `unit` (default, fast), `integration` (requires Docker), `e2e` (gated).
- `@Tag("integration")` on Testcontainers tests; filter in CI with `-Dgroups=integration`.

**Interview trap:** How do you handle the case where the Kafka consumer is a different team's service? — Pact consumer-driven contracts: their team writes the consumer pact, publishes it to the broker, and your service's CI must pass verification before any change can deploy. This creates an automated safety net without requiring coordination for every change.

**Tags:** test-strategy, test-pyramid, testcontainers, pact, contract-testing, kafka, integration-test, e2e, architecture

---

## Q-TEST-027 [bloom: analyze] [level: master]
**Question:** Explain how you would systematically track down and eliminate a flaky test in a Spring integration test suite. Walk through the diagnostic process step by step.

**Model answer:** **Step 1 — Reproduce reliably**

- Run the suspected test in isolation vs. in the full suite. If it only fails in the full suite → order or state dependency.
- Run it in a loop: `mvn test -pl . -Dtest=OrderServiceIT -Dsurefire.rerunFailingTestsCount=10`. Track failure rate.
- Run with `@TestMethodOrder(MethodOrderer.Random.class)` to expose ordering issues.

**Step 2 — Identify the class of flakiness**

Ask:
- Does it fail consistently on low-resource environments (e.g., CI with 2 cores)? → Thread/timing issue.
- Does it fail only after specific other tests? → Shared mutable state or missing rollback.
- Does it involve a `Testcontainers` container? → Healthcheck race.
- Does it involve time (`LocalDate.now()`, `System.currentTimeMillis()`)? → Non-deterministic time.
- Does it involve async processing (Kafka consumer, `@Async` methods)? → Missing synchronization.

**Step 3 — Common fixes**

| Symptom | Fix |
|---|---|
| Test B fails when run after Test A | Use `@Transactional` + `@DirtiesContext` (last resort) or fix shared static/bean state in `@BeforeEach` |
| Async assertion fails intermittently | Replace `Thread.sleep()` with `Awaitility.await().atMost(5, SECONDS).until(...)` |
| Testcontainers container not ready | Add `.waitingFor(Wait.forListeningPort())` or health-specific wait strategy |
| `LocalDateTime.now()` test fails at midnight | Inject `Clock`, override in test with `Clock.fixed(...)` |
| Port conflict | Ensure `webEnvironment = RANDOM_PORT`, don't hardcode ports |
| Entity state leaks between tests | Ensure `@DataJpaTest` has `@Transactional` (it does by default); if not — explicit rollback |

**Step 4 — Quarantine while fixing**

Add `@Tag("flaky")` and `@Disabled("JIRA-1234: investigating shared state")`. Configure CI to run flaky-tagged tests separately with retries, tracked in a dashboard. Never silently retry without tracking.

**Step 5 — Verify fix**

Run the test 50 times in CI. If pass rate is 100%, remove `@Disabled` and `@Tag("flaky")`.

**Interview trap:** Should you use `@DirtiesContext` as the fix for a flaky test caused by shared Spring context state? — Only as a last resort, because it destroys the cached context and forces a full context restart for every subsequent test class. First, try: resetting the shared state in `@BeforeEach` or `@AfterEach`, using a `@TestConfiguration` that does not mutate shared beans, or restructuring the test to not depend on global state.

**Tags:** flaky-tests, debugging, awaitility, testcontainers, shared-state, test-reliability, dirtiescontext

---

## Q-TEST-028 [bloom: analyze] [level: master]
**Question:** What are the dangers of testing implementation details vs. testing behavior? Give a concrete example of a test that tests implementation, rewrite it to test behavior, and explain why the behavioral version is better.

**Model answer:** **The principle**: tests should verify what a component **does** (observable behavior), not **how** it does it (internal mechanics). Tests that assert implementation details are fragile — any internal refactoring breaks them even if behavior is unchanged.

**Implementation-testing example** (bad):
```java
@Test
void findUser_callsRepoFindById_thenCallsMapper() {
    when(repo.findById(1L)).thenReturn(Optional.of(user));
    service.findUser(1L);
    verify(repo).findById(1L);          // ok — this is behavior
    verify(repo, never()).findAll();     // internal implementation detail
    verify(mapper).toDto(user);         // internal implementation detail
}
```
This test breaks if you inline the mapper, use a record constructor instead, or cache the result in a Map — all without changing observable behavior.

**Behavioral version** (good):
```java
@Test
void findUser_whenUserExists_returnsCorrectDto() {
    when(repo.findById(1L)).thenReturn(Optional.of(new User(1L, "Alice", "alice@x.com")));

    UserDto result = service.findUser(1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getName()).isEqualTo("Alice");
    assertThat(result.getEmail()).isEqualTo("alice@x.com");
    // NOT: verify(mapper.toDto(...)) — that's internal
}
```

**Why behavioral tests are better**:
1. **Refactor freely** — change internal structure, tests stay green.
2. **Document intent** — readable as a specification.
3. **Catch real regressions** — if behavior changes, test fails.
4. **Less over-specification** — don't assert `never()` on methods that might legitimately be called in a different implementation.

**Legitimate use of verify()**: when the side effect IS the behavior (e.g., `verify(emailService).send(...)` — the test is checking that an email was sent, which is the observable outcome of a registration flow).

**Interview trap:** When is `verify()` appropriate and when is it an anti-pattern? — Appropriate: verifying side effects that are observable outcomes (emails sent, audit log written, cache invalidated). Anti-pattern: verifying every internal call the SUT makes as a way to assert that the implementation hasn't changed. The litmus test: "if I refactor this without changing behavior, should this `verify()` break?" If no — remove it.

**Tags:** testing-philosophy, behavior-vs-implementation, verify, over-specification, test-design, refactoring

---

## Q-TEST-029 [bloom: analyze] [level: master]
**Question:** Describe in detail what mocking statics and final classes requires in Mockito 5, and explain the architectural signal that needing to mock statics sends.

**Model answer:** **Mockito 4/5 inline mock-maker** enables mocking of:
- Final classes (previously impossible with the default proxy-based approach).
- Static methods via `MockedStatic<T>`.
- Object constructors via `MockedConstruction<T>`.

In Mockito 5, the inline mock-maker is the **default**; no extra dependency needed. In Mockito 4, add `mockito-inline` artifact (or set `mockito-extensions/org.mockito.plugins.MockMaker` to `mock-maker-inline`).

**Mocking statics**:
```java
try (MockedStatic<Files> fileMock = Mockito.mockStatic(Files.class)) {
    fileMock.when(() -> Files.exists(Path.of("/data"))).thenReturn(true);
    // test code
    fileMock.verify(() -> Files.exists(Path.of("/data")));
} // automatically closed — static mock removed
```

Critical: `MockedStatic` is **thread-local and not thread-safe**. Tests using it must not run in parallel. The try-with-resources scope ensures cleanup; failing to close it leaks the mock into subsequent tests (a common source of flakiness).

**Architectural signal**: needing to mock a static method is a design smell. Statics are global state — they create hidden dependencies, make code hard to test in isolation, and couple code to a specific implementation. The proper fix:

| Static call | Refactoring |
|---|---|
| `LocalDateTime.now()` | Inject `Clock` |
| `UUID.randomUUID()` | Inject `Supplier<UUID>` or `IdGenerator` |
| `HttpClient.newBuilder()` | Inject the `HttpClient` |
| `MyLegacyUtil.compute(x)` | Extract interface `Computable`, inject it; legacy class becomes the default impl |

Use `mockStatic` for **legacy code** you cannot refactor. In greenfield code, if you reach for `mockStatic`, treat it as a red flag and reconsider the design.

**Interview trap:** What happens if you forget to close a MockedStatic? — The static mock persists on the thread for subsequent tests in the same thread pool. Other tests that call the static method unexpectedly get the stubbed return value, causing mysterious failures. Always use try-with-resources.

**Tags:** mockito, mockstatic, mockedstatic, final-classes, inline-mock-maker, architecture, design-smell

---

## Q-TEST-030 [bloom: analyze] [level: master]
**Question:** Describe the Object Mother and Test Data Builder patterns. When does each shine, how do they compose, and what goes wrong at scale when you use neither?

**Model answer:** Both patterns solve the same root problem: test data setup is verbose, duplicated, and brittle when constructors or setters change.

**Object Mother** — a factory class with static methods that produce canonical, named test instances:
```java
public class OrderMother {
    public static Order aNewOrder()       { return new Order(1L, "PENDING", BigDecimal.TEN, "alice@x.com"); }
    public static Order aPaidOrder()      { return new Order(2L, "PAID",    BigDecimal.TEN, "alice@x.com"); }
    public static Order aCancelledOrder() { return new Order(3L, "CANCELLED", BigDecimal.ZERO, "alice@x.com"); }
}
```
Strength: readable, zero boilerplate per test. Weakness: named fixtures explode as edge cases accumulate — you end up with `aNewOrderWithNullEmail`, `aPaidOrderWithAmountZero`, etc.

**Test Data Builder** — a fluent builder that starts with safe defaults and overrides only the relevant fields:
```java
public class OrderBuilder {
    private Long id = 1L;
    private String status = "PENDING";
    private BigDecimal amount = BigDecimal.TEN;
    private String email = "alice@x.com";

    public static OrderBuilder anOrder() { return new OrderBuilder(); }

    public OrderBuilder withStatus(String s) { this.status = s; return this; }
    public OrderBuilder withAmount(BigDecimal a) { this.amount = a; return this; }
    public OrderBuilder withNullEmail() { this.email = null; return this; }

    public Order build() { return new Order(id, status, amount, email); }
}

// in test:
Order order = anOrder().withStatus("PAID").withNullEmail().build();
```
Strength: handles every edge case without new factory methods — just chain overrides. Weakness: requires maintaining a builder class.

**Composition**: combine both — Object Mother uses builders internally:
```java
public class OrderMother {
    public static Order aPaidOrder() { return anOrder().withStatus("PAID").build(); }
}
```

**What goes wrong without either**: every test constructs objects inline with `new Order(...)`. When a constructor argument is added, every test breaks. Tests become documentation-free — it's unclear which field is relevant to the test. Shared objects mutated across tests become a source of flakiness.

**At scale — additional patterns**:
- **Faker libraries** (`java-faker`, `datafaker`) for realistic random data in bulk tests.
- **DbSetup** or **Testcontainers + Flyway fixtures** for DB-layer test data.
- **`@Sql`** (Spring) for injecting SQL scripts into integration tests.

**Interview trap:** When would you use `@Sql` instead of a Test Data Builder for a repository test? — When the data is complex enough (many rows, FK chains, realistic schema) that building it in Java becomes noise, or when you want to test the exact SQL your migration produces. `@Sql` scripts are also version-controlled and can be shared between tests that need the same baseline state. The downside: they're harder to parameterize and can become stale if schema changes.

**Tags:** test-data, object-mother, test-builder, fixtures, faker, sql-annotation, design-patterns

---

## Q-TEST-031 [bloom: analyze] [level: master]
**Question:** How does WireMock work as an HTTP stub server? Describe how you would use it to test a service that calls an external REST API, including fault injection, request verification, and how it differs from Mockito mocking of the HTTP client.

**Model answer:** **WireMock** starts a real embedded HTTP server (Jetty under the hood) and listens on a real port. Your SUT makes genuine HTTP calls against it — the network stack, serialization, and HTTP client are all exercised. This is fundamentally different from mocking the HTTP client: with WireMock you test the full call chain.

**Setup with JUnit 5**:
```java
@WireMockTest(httpPort = 8089)
class PaymentGatewayClientTest {

    @Autowired PaymentGatewayClient client; // configured with baseUrl = http://localhost:8089

    @Test
    void charge_whenGatewayReturns200_returnsSuccess() {
        stubFor(post(urlEqualTo("/v1/charge"))
            .withRequestBody(matchingJsonPath("$.amount", equalTo("100")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"ch_001\",\"status\":\"succeeded\"}")));

        ChargeResult result = client.charge(new ChargeRequest(100, "USD", "tok_visa"));

        assertThat(result.getStatus()).isEqualTo("succeeded");
        // Verify the exact request was sent:
        verify(postRequestedFor(urlEqualTo("/v1/charge"))
            .withRequestBody(matchingJsonPath("$.currency", equalTo("USD"))));
    }
}
```

**Fault injection** — critical for testing resilience:
```java
// Simulate network timeout
stubFor(get(anyUrl()).willReturn(aResponse().withFixedDelay(5000))); // 5s delay

// Simulate connection fault
stubFor(get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

// Simulate 500 error
stubFor(get(anyUrl()).willReturn(serverError().withBody("Internal Server Error")));
```

This lets you verify that your `@Retryable`, circuit breaker, or timeout configuration actually works.

**Stateful scenarios** — test multi-step flows:
```java
stubFor(get("/order/1").inScenario("order-lifecycle")
    .whenScenarioStateIs(STARTED)
    .willReturn(okJson("{\"status\":\"PENDING\"}"))
    .willSetStateTo("paid"));
stubFor(get("/order/1").inScenario("order-lifecycle")
    .whenScenarioStateIs("paid")
    .willReturn(okJson("{\"status\":\"PAID\"}")));
```

**WireMock vs Mockito HTTP client mocking**:

| | WireMock | Mockito mock of RestTemplate/Feign |
|---|---|---|
| What's tested | Full HTTP stack: serialization, headers, error mapping | Only your code's response to a stubbed interface |
| Network involved | Yes (loopback) | No |
| Fault injection | Yes (timeouts, resets, delays) | No |
| Contract fidelity | High — real HTTP | Low — only what you stub |
| Setup cost | Moderate | Low |
| Use case | Integration test of HTTP client config | Unit test of service logic only |

Never mock `RestTemplate` or `WebClient` directly in integration tests — you're testing the wrong thing. Use WireMock.

**Interview trap:** Your circuit breaker (Resilience4j) is configured to open after 3 failures. How would you test that with WireMock? — Stub the endpoint to return 500 three times, then verify your circuit opens (state transitions to OPEN, subsequent calls fail fast without hitting WireMock). Use `stubFor(...).inScenario(...)` to sequence the responses, then assert the circuit breaker state via the Resilience4j `CircuitBreaker` registry or via the actuator health endpoint.

**Tags:** wiremock, http-stubbing, fault-injection, circuit-breaker, resilience4j, integration-test, rest-client
