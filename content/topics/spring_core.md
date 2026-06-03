# Spring Core (IoC/DI/AOP) — question bank

> Spring Core is the foundation every Java backend interview probes, because it exposes whether a candidate actually understands the framework or just uses it as a black box. At senior level the questions shift from "what is X" to "why does X break in production", "what does the proxy actually do", and "how do you design around the container's constraints". This bank covers IoC/DI mechanics, the full bean lifecycle, scopes and their pitfalls, CGLIB vs JDK proxy internals, AOP vocabulary and the self-invocation trap, @Transactional internals including propagation and rollback rules, circular dependency resolution via the 3-level cache, conditional configuration, profiles, and application events.

## Scope

- IoC concept and the inversion of control principle
- DI types: constructor vs setter vs field injection — why constructor wins
- ApplicationContext vs BeanFactory — what each adds
- Full bean lifecycle: instantiation, population, Aware callbacks, BeanPostProcessor, @PostConstruct, InitializingBean, @PreDestroy
- Bean scopes: singleton, prototype, request, session — and the prototype-in-singleton trap
- Scoped proxies (ScopedProxyMode) and how they solve scope mismatch
- @Configuration full mode vs @Component lite mode and CGLIB proxying of @Bean methods
- Stereotype annotations: @Component, @Service, @Repository, @Controller and what each adds
- @Autowired disambiguation: @Primary, @Qualifier, parameter name matching
- JDK dynamic proxy vs CGLIB: when each is used, limitations (final class/method)
- AOP vocabulary: aspect, advice, pointcut, join point, weaving
- AOP self-invocation problem and fixes
- @Transactional internals: proxy-based, propagation (REQUIRED/REQUIRES_NEW/NESTED/MANDATORY/NEVER/NOT_SUPPORTED), isolation, rollback rules, readOnly, private-method gotcha
- Circular dependencies: why constructor injection fails, how the 3-level cache resolves field/setter cycles
- @Profile: activation, negation, profile-specific property files
- @Conditional and @ConditionalOn* family
- ApplicationEvents: publish/listen, @TransactionalEventListener
- @Lazy: per-bean and global, production trade-offs

---

## Q-SPRC-001 [bloom: recall] [level: junior]
**Question:** What is Inversion of Control (IoC) and how does Spring implement it?

**Model answer:** Inversion of Control means the framework, not your code, is responsible for creating and wiring objects. Instead of writing `new OrderService(new PaymentService())` inside your business logic, you declare dependencies and the IoC container builds the object graph for you. Spring implements IoC via its DI container: you annotate classes with `@Component` (or stereotypes), declare dependencies via constructor/field/setter, and Spring's `ApplicationContext` instantiates everything, resolves references, and manages lifecycles. The key benefits are testability (swap real deps for mocks without changing production code), lifecycle management (init/destroy hooks), and transparent cross-cutting concerns via proxies.

**Interview trap:** "Is IoC the same as DI?" — No. IoC is the broader principle: control over object creation is inverted from caller to container. DI is the specific mechanism Spring uses to implement IoC — it injects dependencies rather than having the object construct them. DI is one strategy for achieving IoC.

**Tags:** ioc, di, container, spring-core, junior

---

## Q-SPRC-002 [bloom: recall] [level: junior]
**Question:** What are the three types of dependency injection Spring supports, and which one is preferred?

**Model answer:** Spring supports constructor injection, setter injection, and field injection.

| Type | Mechanism | Verdict |
|------|-----------|---------|
| Constructor | Dependencies passed as constructor args | Preferred — immutable fields (`final`), all required deps explicit in signature, fails fast if dep missing, testable without Spring (just call `new`) |
| Setter | `@Autowired` on a setter method | OK for optional dependencies; allows reconfiguration after construction |
| Field | `@Autowired` on a private field | Discouraged — hides the dependency graph, requires reflection to test, silently allows circular deps |

Constructor injection is the Spring team's recommendation since Spring 4.x. Since Spring 4.3, if a class has exactly one constructor, `@Autowired` is implicit — you don't even need the annotation.

```java
// Preferred
@Service
public class OrderService {
    private final PaymentService payment;
    private final InventoryService inventory;

    public OrderService(PaymentService payment, InventoryService inventory) {
        this.payment = payment;
        this.inventory = inventory;
    }
}
```

**Interview trap:** "Why is field injection bad for testing specifically?" — With field injection, unit tests cannot construct the class with `new OrderService()` passing mock deps — all fields are private and there's no constructor to use. You either depend on Spring's test context (slow) or use `ReflectionTestUtils.setField()` (fragile). Constructor injection makes test instantiation trivial.

**Tags:** di, constructor-injection, field-injection, setter-injection, testing, spring-core

---

## Q-SPRC-003 [bloom: recall] [level: junior]
**Question:** What is the difference between BeanFactory and ApplicationContext? Which one do you use in practice?

**Model answer:** `BeanFactory` is the basic IoC container interface — it provides bean creation, dependency injection, and getBean(). It's lazy: singleton beans are created on first access, not at startup.

`ApplicationContext` extends `BeanFactory` and adds:
- **Eager singleton initialization** at startup — all singletons created on context load, startup failures surface immediately
- **AOP integration** — required for proxies (`@Transactional`, `@Cacheable`, etc.)
- **Event publishing** — `ApplicationEventPublisher`
- **Message source / i18n** — `MessageSource`
- **ResourceLoader** — unified resource access
- **Multiple context hierarchy** — parent/child context support

In practice, always use `ApplicationContext`. Common implementations: `AnnotationConfigApplicationContext` for standalone apps, `GenericWebApplicationContext` / `WebApplicationContext` in servlet containers, `SpringApplication` (Spring Boot) wraps all of this.

`BeanFactory` directly is only relevant if you're writing infrastructure code (custom framework layer) or in memory-constrained environments where lazy init matters.

**Interview trap:** "When would eager init be a problem?" — In tests with large context hierarchies or in lambda / serverless deployments where cold start time is critical. That's why `spring.main.lazy-initialization=true` exists, but it trades startup correctness for speed — missing bean errors surface at runtime instead of startup.

**Tags:** beanfactory, applicationcontext, ioc-container, spring-core, junior

---

## Q-SPRC-004 [bloom: recall] [level: junior]
**Question:** Name the stereotype annotations Spring provides and explain what each one adds beyond `@Component`.

**Model answer:** Spring has four stereotype annotations, all of which are composed on `@Component` and treated identically by the component scanner. The distinction is semantic and functional:

| Annotation | Layer | Extra behavior |
|------------|-------|----------------|
| `@Component` | Generic | None — base stereotype |
| `@Service` | Business logic | None — naming convention only |
| `@Repository` | Data access | Enables **PersistenceExceptionTranslation** — wraps native `SQLException` / JPA exceptions into Spring's `DataAccessException` hierarchy |
| `@Controller` | MVC presentation | Marks for handler mapping detection; used by `DispatcherServlet` |
| `@RestController` | REST API | `@Controller` + `@ResponseBody` — response body auto-serialized |

The only one with real behavioral difference is `@Repository`: if you put JDBC code in a class annotated with `@Service`, SQL exceptions won't be translated into Spring's hierarchy — you lose the abstraction. Always use `@Repository` on DAO classes.

**Interview trap:** "Can you put @Transactional on a @Repository?" — Yes, nothing prevents it, but conventionally transactions live on the service layer so you can coordinate multiple repository calls in one unit of work. Putting `@Transactional` on repositories gives you per-call transactions, which is often too fine-grained.

**Tags:** stereotype, component, service, repository, controller, exception-translation, spring-core

---

## Q-SPRC-005 [bloom: understand] [level: junior]
**Question:** What are the default bean scopes in Spring, and what is the prototype-in-singleton trap?

**Model answer:** Spring's built-in scopes:

| Scope | Lifecycle | Typical use |
|-------|-----------|-------------|
| `singleton` (default) | One instance per `ApplicationContext` | Stateless services |
| `prototype` | New instance per `getBean()` / injection point | Stateful or heavyweight setup objects |
| `request` | Per HTTP request | Web: per-request context holder |
| `session` | Per HTTP session | User session state |
| `application` | Per `ServletContext` | App-wide shared state in web |

**Prototype-in-singleton trap:** If a singleton bean has a prototype-scoped dependency injected once at construction time, that prototype instance is captured for the singleton's lifetime — you always get the same "prototype" instance, defeating its purpose entirely.

```java
@Service  // singleton
public class ReportService {
    @Autowired
    private ReportContext ctx;  // prototype — but only ONE instance ever injected!
}
```

**Fixes:**
1. `@Scope(value="prototype", proxyMode=ScopedProxyMode.TARGET_CLASS)` — Spring injects a scoped proxy; each call goes through the proxy to a fresh prototype instance.
2. Inject `ApplicationContext` and call `ctx.getBean(ReportContext.class)` each time.
3. `@Lookup` annotation on an abstract method — Spring overrides the method to call `getBean()` at runtime.

**Interview trap:** "Does the prototype bean get destroyed with @PreDestroy?" — No. Spring does not manage the lifecycle of prototype beans after handing them out. The container creates them, but the caller is responsible for cleanup. `@PreDestroy` is never called on prototypes.

**Tags:** bean-scopes, singleton, prototype, scoped-proxy, spring-core, lifecycle

---

## Q-SPRC-006 [bloom: recall] [level: junior]
**Question:** What does `@PostConstruct` do, and where does it sit in the Spring bean lifecycle?

**Model answer:** `@PostConstruct` (JSR-250 / Jakarta annotation) marks a method to be called by the container after the bean has been fully initialized — all dependencies injected, all `*Aware` callbacks fired, and `BeanPostProcessor.postProcessBeforeInitialization()` complete.

Full singleton lifecycle order:
1. Instantiate (call constructor)
2. Populate properties (inject dependencies)
3. `*Aware` callbacks (`BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`)
4. `BeanPostProcessor.postProcessBeforeInitialization()` — all BPPs run (e.g. `@Autowired` processing, `@Value` injection)
5. **`@PostConstruct`** method
6. `InitializingBean.afterPropertiesSet()`
7. `init-method` from `@Bean(initMethod=...)`
8. `BeanPostProcessor.postProcessAfterInitialization()` — **AOP proxies created here**
9. Bean in use
10. `@PreDestroy` → `DisposableBean.destroy()` → `destroyMethod`

Use `@PostConstruct` for: cache warming, validation after injection, starting background threads. Prefer it over `InitializingBean` because it's a standard Java annotation — not Spring-specific.

**Interview trap:** "Why is step 8 (postProcessAfterInitialization) important?" — That's where `AbstractAutoProxyCreator` creates AOP proxies. The beans that other beans get injected with are the proxies, not the raw target. But `this` inside the bean always refers to the raw object — which is the root of the self-invocation problem.

**Tags:** postconstruct, predestroy, lifecycle, beanpostprocessor, spring-core, initialization

---

## Q-SPRC-007 [bloom: understand] [level: regular]
**Question:** Explain the difference between `@Configuration` full mode and `@Component` lite mode when using `@Bean` methods. What does CGLIB proxying of `@Configuration` classes do?

**Model answer:** When you annotate a class with `@Configuration`, Spring subclasses it with CGLIB at startup. The CGLIB subclass intercepts calls to `@Bean` methods: if a `@Bean` method is called from within the same `@Configuration` class, the CGLIB proxy intercepts and returns the existing singleton bean from the context instead of executing the method body again.

```java
@Configuration
public class AppConfig {
    @Bean
    public HikariConfig hikariConfig() {
        return new HikariConfig();  // executes once
    }

    @Bean
    public DataSource dataSource() {
        // CGLIB intercepts this call — returns existing HikariConfig bean
        // Does NOT call the method body a second time
        return new HikariDataSource(hikariConfig());
    }
}
```

**Lite mode** happens when `@Bean` methods appear on a `@Component`-annotated class (or `@Configuration(proxyBeanMethods=false)`). No CGLIB subclass is created. The methods are just regular Java methods — calling `hikariConfig()` from `dataSource()` would call the method body again, creating a second `HikariConfig` instance that is NOT the Spring-managed bean.

| Mode | How | Inter-@Bean calls | Startup cost |
|------|-----|-------------------|--------------|
| Full (`@Configuration`) | CGLIB subclass | Returns existing bean from context | Slightly higher (CGLIB generation) |
| Lite (`@Component` + `@Bean`) | Plain class | Creates new instance each call | Lower |

Use lite mode only when `@Bean` methods are independent of each other. If bean A calls bean B's factory method, you must use full mode or inject B as a parameter.

**Interview trap:** "`@Configuration(proxyBeanMethods=false)` — when is it legitimate?" — When all `@Bean` methods are independent and performance matters (many configs, e.g. in Spring Boot's own auto-configurations). Spring Boot's internal auto-configs use `proxyBeanMethods=false` extensively. If you ever copy-paste a Spring Boot auto-config and remove `@Configuration`, check the cross-method dependencies first.

**Tags:** configuration, component, cglib, proxyBeanMethods, full-mode, lite-mode, spring-core

---

## Q-SPRC-008 [bloom: understand] [level: regular]
**Question:** How does Spring disambiguate when multiple beans of the same type are in the context? Explain `@Primary`, `@Qualifier`, and parameter name matching.

**Model answer:** Spring's injection resolution order when multiple candidates of the same type exist:

1. **Type match** — find all beans assignable to the required type.
2. **`@Primary`** — if one candidate is annotated `@Primary`, pick it as the default.
3. **`@Qualifier("name")`** at the injection point — explicit name match; overrides `@Primary`.
4. **Parameter/field name matching** — if no qualifier, Spring tries to match the parameter or field name against bean names (case-insensitive). This is a fallback and fragile under refactoring.

```java
@Bean @Primary
public MailSender smtpMailSender() { ... }

@Bean("asyncMailSender")
public MailSender asyncMailSender() { ... }

// Uses @Primary — gets smtpMailSender
@Autowired private MailSender sender;

// Explicit qualifier — gets asyncMailSender regardless of @Primary
@Autowired @Qualifier("asyncMailSender")
private MailSender notificationSender;
```

Use `@Primary` when you want a sensible default (e.g., real impl vs stub in tests). Use `@Qualifier` when you need two different implementations in two different places simultaneously.

**Interview trap:** "`@Autowired(required=false)` — what is the danger?" — If no bean of the required type exists, injection is silently skipped and the field stays `null`. This creates NPEs at runtime far from the actual misconfiguration. Prefer `Optional<MailSender>` injection — it makes optionality explicit in the type system and forces you to handle the absent case.

**Tags:** primary, qualifier, autowired, disambiguation, injection, spring-core

---

## Q-SPRC-009 [bloom: understand] [level: regular]
**Question:** Describe the Spring bean lifecycle in full — from the moment the container creates a bean to the moment it destroys it. Include all the extension hooks.

**Model answer:** For a singleton bean (prototype skips shutdown callbacks entirely):

**Startup phase:**
1. **Instantiate** — container calls the constructor (or factory method)
2. **Property population** — `@Autowired`, `@Value`, XML properties injected
3. **`*Aware` callbacks** (in order): `BeanNameAware.setBeanName()`, `BeanClassLoaderAware`, `BeanFactoryAware.setBeanFactory()`, `EnvironmentAware`, `ApplicationContextAware.setApplicationContext()`
4. **`BeanPostProcessor.postProcessBeforeInitialization()`** — all registered BPPs called for this bean (e.g. `CommonAnnotationBeanPostProcessor` processes `@PostConstruct` here)
5. **`@PostConstruct`** method (processed by CommonAnnotationBPP above)
6. **`InitializingBean.afterPropertiesSet()`** — if implemented
7. **`init-method`** from `@Bean(initMethod=...)` or XML
8. **`BeanPostProcessor.postProcessAfterInitialization()`** — `AbstractAutoProxyCreator` wraps bean in AOP proxy here; other beans receive the proxy, not the raw object

**In use:** Bean is available from the context

**Shutdown phase** (`context.close()` or JVM shutdown hook):
9. **`@PreDestroy`** method
10. **`DisposableBean.destroy()`**
11. **`destroyMethod`** from `@Bean(destroyMethod=...)`

Note: prototype beans only go through steps 1–8 (never 9–11 — Spring doesn't track them after creation).

**Interview trap:** "Where exactly are AOP proxies created?" — Step 8: `postProcessAfterInitialization`. The consequence: every other bean that depends on this one receives the proxy. But `this` inside the target class always refers to the raw, unwrapped object — not the proxy. This is the fundamental reason self-invocation bypasses `@Transactional`.

**Tags:** bean-lifecycle, beanpostprocessor, postconstruct, predestroy, aware, spring-core

---

## Q-SPRC-010 [bloom: understand] [level: regular]
**Question:** What is the difference between JDK dynamic proxy and CGLIB proxy? When does Spring use each?

**Model answer:** Spring AOP needs to wrap beans in proxies to intercept method calls. Two mechanisms:

| | JDK Dynamic Proxy | CGLIB |
|---|---|---|
| **Requires** | Target class implements at least one interface | No interface required |
| **How** | Creates a new class at runtime that implements the same interface(s) and delegates to the target | Creates a runtime subclass of the target class |
| **Intercepts** | Only methods declared on the interface | All non-final public methods |
| **Limitation** | Non-interface methods not intercepted | Cannot proxy `final` classes or `final` methods |
| **Default** | Original Spring AOP when interface present | Spring Boot default (`spring.aop.proxy-target-class=true`) |

Spring Boot sets `proxyTargetClass=true` by default — CGLIB is used even when the bean implements an interface. This ensures that if you inject a concrete type instead of the interface, it still works.

```java
// With CGLIB default, both of these work:
@Autowired OrderService orderService;        // concrete class — works
@Autowired OrderServiceIF orderServiceIF;    // interface — also works
```

**CGLIB limitation in practice:** If your `@Service` class is `final`, or if an `@Transactional` method is `final`, the CGLIB proxy cannot subclass/override it. The annotation silently does nothing — no exception thrown.

**Interview trap:** "Can you use both mechanisms on the same class?" — No. Spring picks one. A common confusion is thinking the proxy is JDK-based because the bean implements an interface. In Spring Boot with default config, CGLIB is used regardless.

**Tags:** jdk-proxy, cglib, aop, proxy, final, spring-core, proxy-mechanism

---

## Q-SPRC-011 [bloom: understand] [level: regular]
**Question:** Explain the AOP vocabulary: aspect, advice, pointcut, and join point. What does weaving mean?

**Model answer:** Spring AOP terminology maps directly to the AspectJ model:

| Term | Definition | Spring example |
|------|-----------|----------------|
| **Join point** | A point in program execution where advice can be applied. In Spring AOP, this is always a **method execution** (Spring only supports method-level interception, unlike AspectJ which also supports field access/constructors). | Calling `orderService.placeOrder()` |
| **Pointcut** | An expression that selects a set of join points | `@Pointcut("execution(* com.example.service.*.*(..)))")` |
| **Advice** | Code that runs at a matched join point. Types: `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`, `@Around` | Logging before method; timing around method |
| **Aspect** | A class that contains pointcuts and advice — the modularization of a cross-cutting concern | `@Aspect @Component class AuditAspect` |
| **Weaving** | The process of applying aspects to target objects to create advised objects | Happens at runtime in Spring (proxy creation), at compile time in AspectJ CTW, or at load time with LTW |

Spring AOP is proxy-based and handles only method execution join points. AspectJ supports full instrumentation (field get/set, constructor, static methods) but requires bytecode weaving.

**Interview trap:** "Does Spring AOP support field-level interception?" — No. Spring AOP is purely proxy-based and only intercepts method calls on Spring-managed beans via the proxy boundary. For field access interception, you need AspectJ compile-time or load-time weaving.

**Tags:** aop, aspect, advice, pointcut, join-point, weaving, spring-aop

---

## Q-SPRC-012 [bloom: apply] [level: regular]
**Question:** Explain the AOP self-invocation problem. Why does it happen, and what are the three ways to fix it?

**Model answer:** Spring AOP is proxy-based. When Spring wraps a bean in a proxy (`@Transactional`, `@Cacheable`, `@Async`, etc.), the proxy intercepts calls that come **from outside the bean** through the proxy reference. When the bean calls its own method via `this`, it bypasses the proxy entirely — the advice never runs.

```java
@Service
public class InvoiceService {
    public void processAll(List<Invoice> invoices) {
        for (Invoice inv : invoices) {
            this.save(inv);  // BYPASSES PROXY — @Transactional on save() does NOTHING
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Invoice inv) { ... }
}
```

`processAll` calls `save` via `this` — the raw target object, not the proxy. The `@Transactional` on `save` is silently ignored.

**Three fixes:**

1. **Extract to a separate bean** (best — also fixes SRP violations):
   ```java
   @Service class InvoicePersister {
       @Transactional(propagation = REQUIRES_NEW)
       public void save(Invoice inv) { ... }
   }
   @Service class InvoiceService {
       @Autowired InvoicePersister persister;
       public void processAll(...) { persister.save(inv); }
   }
   ```

2. **Inject self** (quick workaround — signals a design smell):
   ```java
   @Autowired private InvoiceService self;
   public void processAll(...) { self.save(inv); }  // goes through the proxy
   ```

3. **AspectJ weaving instead of proxy-based AOP** — AspectJ instruments the bytecode directly, so `this.save()` is also intercepted. Load-time weaving or compile-time weaving required. High setup cost, rarely worth it unless self-invocation is pervasive.

**Interview trap:** "Does `@Transactional` work on private methods?" — No. CGLIB creates a subclass and can only override non-private methods. A `@Transactional` on a private method is silently ignored — no exception, no transaction. This is one of the most common silent bugs in Spring apps.

**Tags:** self-invocation, aop, transactional, proxy, cglib, spring-core, senior-trap

---

## Q-SPRC-013 [bloom: apply] [level: regular]
**Question:** How do circular dependencies work in Spring? Why does constructor injection fail while field/setter injection can resolve them?

**Model answer:** A circular dependency: `A` depends on `B`, `B` depends on `A`.

**Constructor injection:** fails fast at startup with `BeanCurrentlyInCreationException`. When Spring tries to create `A`, it needs `B` first. To create `B`, it needs `A` — but `A` isn't finished yet. With constructor injection, there is no way to provide `A` to `B`'s constructor without a fully constructed `A`. Spring detects this and throws. This is the correct behavior — it surfaces design flaws immediately.

**Field/setter injection:** Spring resolves it using a **three-level cache** (inside `DefaultSingletonBeanRegistry`):

| Cache | Contents |
|-------|----------|
| `singletonObjects` (L1) | Fully initialized, ready beans |
| `earlySingletonObjects` (L2) | Early references — exposed before `postProcessAfterInitialization`; used when AOP proxy needed for circular ref |
| `singletonFactories` (L3) | `ObjectFactory` lambdas that produce an early reference on demand |

Workflow for A→B→A:
1. Start creating A → put `ObjectFactory<A>` in L3 (singletonFactories)
2. A needs B → start creating B
3. B needs A → ask L3 for early reference to A → L3 produces it, moves to L2
4. B gets injected with early A → B finishes → moves to L1
5. A finishes property population → tries to move to L1 — proxy check: if A needed a proxy (e.g. for AOP), use the early reference from L2 (which may already be the proxy)

**Spring Boot 2.6+ change:** `spring.main.allow-circular-references=false` by default. Circular deps throw even for field/setter injection. The right response is to fix the design, not re-enable the flag.

**Preferred fix:** Extract a third bean `C` that both `A` and `B` depend on. Circular deps almost always signal an SRP violation.

**Interview trap:** "Can you have a circular dependency with @Transactional?" — Yes, and it's subtler. If A is `@Transactional` and has a circular dep with B, the early reference exposed through L3 might be the raw object (before AOP wrapping in step 8 of the lifecycle). Spring handles this with the `earlySingletonObjects` (L2) cache — if an early reference is requested and the bean needs a proxy, Spring generates the proxy early and stores it in L2. But this only works if the proxy creation doesn't itself require the finished bean. This is why AOP + circular deps can still fail in edge cases.

**Tags:** circular-dependency, 3-level-cache, constructor-injection, singletonObjects, spring-core, senior

---

## Q-SPRC-014 [bloom: apply] [level: regular]
**Question:** What does `@Transactional` actually do at runtime? Walk through the internals from annotation to commit.

**Model answer:** `@Transactional` is implemented via Spring AOP — a proxy is created for the annotated bean during `BeanPostProcessor.postProcessAfterInitialization()`. The proxy wraps method calls; when a `@Transactional` method is invoked on the proxy:

1. **`TransactionInterceptor`** (a `MethodInterceptor`) is invoked
2. It calls **`PlatformTransactionManager.getTransaction()`** with the method's `TransactionDefinition` (propagation, isolation, timeout, readOnly)
3. The transaction manager checks for an existing transaction bound to the current thread via **`TransactionSynchronizationManager`** (a ThreadLocal map)
4. Based on propagation rules, it joins, suspends, or creates a transaction
5. The actual method runs inside the transactional context
6. On normal return: **`commit()`**
7. On exception: checks rollback rules → **`rollback()`** if the exception qualifies

**Key internals:**
- Transactions are bound to threads via `TransactionSynchronizationManager` (ThreadLocal). Never spawn a new thread inside a `@Transactional` method and expect the transaction to follow — it won't.
- `readOnly=true` is a hint to the transaction manager and underlying driver. Hibernate can skip dirty checking, some databases optimize read paths. It does NOT prevent writes at the SQL level (no `READ ONLY` statement issued by default in most setups).

**Rollback rules:** Only `RuntimeException` and `Error` trigger rollback by default. Checked exceptions do NOT roll back. Override with `rollbackFor = Exception.class` or `noRollbackFor = SpecificRuntimeException.class`.

```java
@Transactional(rollbackFor = IOException.class, readOnly = false, timeout = 30)
public void processOrder(Order order) throws IOException { ... }
```

**Interview trap:** "What happens if you mark a `@Transactional` method as `private`?" — Nothing — it is silently ignored. CGLIB can only override public and protected methods. No transaction is started, no exception is thrown. This is one of the most insidious Spring bugs.

**Tags:** transactional, platformtransactionmanager, proxy, rollback, readonly, threadlocal, spring-core

---

## Q-SPRC-015 [bloom: apply] [level: senior]
**Question:** Describe all six `@Transactional` propagation modes. When would you use `REQUIRES_NEW` vs `NESTED`, and what are the operational trade-offs?

**Model answer:** Propagation controls what happens to the transaction when a `@Transactional` method is called within an existing transactional context:

| Propagation | Behavior | Use case |
|-------------|----------|----------|
| `REQUIRED` (default) | Join existing tx; create new if none | Standard — one logical unit of work |
| `REQUIRES_NEW` | Suspend current tx, open a new independent tx | Audit log that must commit even if outer tx rolls back |
| `NESTED` | Create a savepoint inside current tx; rollback to savepoint on failure (outer tx unaffected) | Partial rollback of a sub-operation without rolling back everything |
| `SUPPORTS` | Join if exists, run non-transactional if none | Read-only helper methods that tolerate both contexts |
| `NOT_SUPPORTED` | Suspend current tx; run without any tx | Legacy code that breaks inside a transaction |
| `MANDATORY` | Throw `IllegalTransactionStateException` if no active tx | Enforce that callers always provide a transaction |
| `NEVER` | Throw if an active transaction exists | Assert "this must never run inside a tx" |

**`REQUIRES_NEW` vs `NESTED`:**

`REQUIRES_NEW` opens a completely independent transaction with its own connection (the connection pool is involved — you need at least 2 connections). If the outer tx rolls back, the inner tx has already committed independently. If the inner tx rolls back, the outer tx is unaffected but must continue with whatever the inner tx partially did (or didn't do).

`NESTED` uses a **savepoint** within the same connection and transaction. If the inner work fails, you roll back to the savepoint but the outer transaction can continue. If the outer rolls back, everything rolls back including the nested portion. `NESTED` is NOT the same as an independent transaction — it's a sub-unit of the same transaction.

**Operational gotcha with `REQUIRES_NEW`:** because it suspends the current transaction and acquires a new connection, calling `REQUIRES_NEW` methods in a loop (e.g., processing 1000 items) means 1000 connection checkouts from the pool. Under load this can exhaust the pool and deadlock.

**Interview trap:** "Does NESTED work with all databases and transaction managers?" — No. `NESTED` requires the JDBC driver to support savepoints (`Savepoint` interface). PostgreSQL, MySQL, and Oracle support this. Some JDBC wrappers or distributed transaction managers do not. `JpaTransactionManager` supports `NESTED` via JPA savepoints if the underlying provider supports it — Hibernate does, but only when using `JpaTransactionManager`, not `JtaTransactionManager`.

**Tags:** transactional, propagation, required, requires-new, nested, savepoint, spring-core, senior

---

## Q-SPRC-016 [bloom: apply] [level: senior]
**Question:** What is the scoped proxy pattern in Spring? Walk through a concrete problem it solves and explain how it works internally.

**Model answer:** **The problem:** A singleton bean (e.g., a service) needs to use a shorter-scoped bean (e.g., a request-scoped HTTP context). If you inject the request-scoped bean directly into the singleton at startup, you'd get a single instance captured forever — the singleton outlives every individual request.

**Solution — scoped proxy:**
```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String traceId = UUID.randomUUID().toString();
    // ...
}

@Service  // singleton
public class OrderService {
    @Autowired
    private RequestContext ctx;  // actually a CGLIB proxy is injected here
}
```

Spring injects a **CGLIB proxy** (or JDK dynamic proxy if an interface) into the singleton, not the actual `RequestContext` instance. The proxy implements the same type as the target. Each time `orderService.doWork()` accesses `ctx.getTraceId()`, the proxy delegates to the **current** request-scoped instance from the thread's active scope (looked up via `RequestContextHolder` / `ScopeHolder`).

**Internal mechanics:**
1. `ScopedProxyFactoryBean` creates the proxy backed by a `ScopedObject` wrapper
2. On every method call, the proxy calls `scope.get(beanName, objectFactory)` where `scope` is the registered scope (e.g. `RequestScope` backed by `RequestContextHolder`)
3. The request scope stores beans in the `HttpServletRequest` attribute map — the same request gets the same instance; a new request gets a new instance

**`ScopedProxyMode` choices:**
- `TARGET_CLASS` — CGLIB subclass (no interface needed)
- `INTERFACES` — JDK dynamic proxy (target must implement interface)
- `NO` — no proxy; direct injection (only works if lifecycles match)

**Interview trap:** "Does the scoped proxy work for prototype scope?" — Yes. `@Scope(value="prototype", proxyMode=ScopedProxyMode.TARGET_CLASS)` causes the proxy to call `getBean()` on every method call, returning a new prototype instance each time. This is the standard fix for the prototype-in-singleton trap.

**Tags:** scoped-proxy, request-scope, session-scope, prototype, cglib, spring-core, senior

---

## Q-SPRC-017 [bloom: apply] [level: senior]
**Question:** Explain how `@Profile` works internally and how you would use profile-specific configuration in a production Spring Boot application. Include the `!` negation form and its gotchas.

**Model answer:** `@Profile` is implemented as a `@Conditional` — specifically it uses `ProfileCondition` which calls `Environment.acceptsProfiles(Profiles.of(...))`. A bean or configuration class annotated with `@Profile("prod")` is only registered when the `prod` profile is active.

**Activation:**
```bash
# JVM argument
-Dspring.profiles.active=prod,cloud

# Environment variable (overrides all others in Boot)
SPRING_PROFILES_ACTIVE=prod

# Programmatic
SpringApplication app = new SpringApplication(App.class);
app.setAdditionalProfiles("prod");
```

**Profile-specific property files (Spring Boot):**
- `application-prod.properties` / `application-prod.yml` is automatically loaded when `prod` profile is active
- Properties in profile-specific files override `application.properties`
- Multiple profiles active: all their files are loaded; last profile's value wins for conflicts

**`@Profile` on `@Bean` and `@Configuration`:**
```java
@Configuration
@Profile("!prod")  // active on ALL profiles EXCEPT prod
public class DevDataSeedConfig {
    @Bean
    public DataSeeder dataSeeder() { ... }
}
```

**Negation gotcha:** `@Profile("!prod")` means "not prod" — it activates on `dev`, `staging`, `test`, and on no-profile-set. This is what you want for dev-only beans like test data seeders — but don't accidentally exclude `staging` if staging needs that bean too.

**Compound expressions (Spring 5.1+):**
```java
@Profile("(dev | test) & !legacy")  // dev or test, but not legacy
```

**Interview trap:** "What if no profile is active?" — `@Profile("dev")` beans are NOT registered. `@Profile("!prod")` beans ARE registered (negation of inactive profile is active). The default profile is literally named `default` — only beans explicitly annotated `@Profile("default")` or beans with no `@Profile` at all are registered when no profile is set.

**Tags:** profile, conditional, environment, properties, spring-core, senior

---

## Q-SPRC-018 [bloom: apply] [level: senior]
**Question:** How does `@Conditional` work? What is the difference between `@Conditional` and the `@ConditionalOn*` family in Spring Boot?

**Model answer:** `@Conditional` is Spring Framework's low-level mechanism for conditional bean registration. You provide a `Condition` implementation:

```java
public class OnLinuxCondition implements Condition {
    @Override
    public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata metadata) {
        return ctx.getEnvironment().getProperty("os.name", "").toLowerCase().contains("linux");
    }
}

@Bean
@Conditional(OnLinuxCondition.class)
public LinuxMonitor linuxMonitor() { ... }
```

Spring Boot's `@ConditionalOn*` annotations are opinionated, pre-built `Condition` implementations:

| Annotation | Condition |
|------------|-----------|
| `@ConditionalOnClass` | Class present on classpath |
| `@ConditionalOnMissingClass` | Class NOT on classpath |
| `@ConditionalOnBean` | Bean of given type/name exists |
| `@ConditionalOnMissingBean` | Bean NOT yet registered (lets user override) |
| `@ConditionalOnProperty` | Property present with specific value |
| `@ConditionalOnExpression` | SpEL expression evaluates to true |
| `@ConditionalOnWebApplication` | Running as a web application |
| `@ConditionalOnNotWebApplication` | Not a web application |
| `@ConditionalOnResource` | Resource exists at given path |

**Auto-configuration pattern:** Spring Boot's auto-configs use `@ConditionalOnMissingBean` so that your own `@Bean` definitions always win — Boot backs off. This is the open/closed principle applied to configuration: Boot is open for extension (define your bean), closed for modification (you never edit Boot's code).

**Evaluation order gotcha:** `@ConditionalOnBean` is evaluated during the config processing phase. If your `@Configuration` class is processed before the bean it checks for is registered, the condition evaluates to false even though the bean will exist later. Fix: use `@AutoConfigureAfter` or order configs explicitly. This is a common bug in custom auto-configuration libraries.

**Interview trap:** "What's the difference between `@ConditionalOnClass` and `@ConditionalOnBean`?" — `@ConditionalOnClass` checks the classpath at startup (cheap — just classloader lookup). `@ConditionalOnBean` checks the ApplicationContext's bean registry (more expensive, ordering-sensitive). Use `@ConditionalOnClass` for "is the library even present" guards; use `@ConditionalOnMissingBean` for "don't override user beans" guards.

**Tags:** conditional, conditionalon, auto-configuration, spring-boot, condition, spring-core, senior

---

## Q-SPRC-019 [bloom: apply] [level: senior]
**Question:** Describe Spring's `ApplicationEvent` mechanism. When would you use `@TransactionalEventListener` over `@EventListener`, and why?

**Model answer:** Spring's built-in event bus allows beans to communicate in a decoupled, within-process way without explicit dependencies.

**Publishing:**
```java
// Event class
public class OrderCreatedEvent extends ApplicationEvent {
    private final Order order;
    public OrderCreatedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
}

// Publisher
@Service
public class OrderService {
    @Autowired private ApplicationEventPublisher publisher;

    @Transactional
    public void createOrder(Order order) {
        orderRepo.save(order);
        publisher.publishEvent(new OrderCreatedEvent(this, order));
    }
}
```

**Listening — `@EventListener`:**
```java
@Component
public class NotificationHandler {
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        emailService.sendConfirmation(event.getOrder());  // PROBLEM: may run before commit
    }
}
```

`@EventListener` is **synchronous by default** — the listener runs in the same thread and the same transaction as the publisher. The event fires immediately when `publishEvent()` is called, meaning the email is sent *before the transaction commits*. If the transaction rolls back after the email is sent, you've notified a customer about an order that doesn't exist.

**`@TransactionalEventListener`:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) {
    emailService.sendConfirmation(event.getOrder());  // fires only after DB commit
}
```

`TransactionPhase` options: `AFTER_COMMIT` (default), `AFTER_ROLLBACK`, `AFTER_COMPLETION` (both), `BEFORE_COMMIT`.

**Important caveat:** `@TransactionalEventListener` only fires if there is an active transaction when the event is published. If the publisher runs without `@Transactional`, the listener is never called. Use `fallbackExecution = true` if you need the listener to fire even outside a transaction.

**Async events:** add `@Async` alongside `@EventListener` to run the listener in a different thread pool. Loses transactional binding.

**Interview trap:** "Are ApplicationEvents a replacement for a message broker?" — No. Events are in-process, synchronous (by default), and lost on process restart or crash. They're for intra-service decoupling. For cross-service communication or durability, use Kafka/RabbitMQ. For durable within-process-after-crash semantics, use the Transactional Outbox pattern.

**Tags:** application-events, eventlistener, transactional-event-listener, after-commit, spring-core, senior

---

## Q-SPRC-020 [bloom: analyze] [level: senior]
**Question:** Walk through exactly what happens when Spring resolves the `@Transactional` isolation level `READ_COMMITTED` for a JPA-backed service. Why is `readOnly=true` not a true read-only guarantee?

**Model answer:** When `@Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)` is applied:

**Isolation level resolution:**
1. `TransactionInterceptor` calls `PlatformTransactionManager.getTransaction()` passing a `DefaultTransactionDefinition` with `isolationLevel = Connection.TRANSACTION_READ_COMMITTED`
2. `JpaTransactionManager` (or `DataSourceTransactionManager`) obtains a connection from the pool and calls `connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)`
3. The connection starts a transaction at that level

Isolation levels map to JDBC constants; Spring just passes them to the driver. The actual MVCC / locking behavior is entirely database-specific. `READ_COMMITTED` in PostgreSQL means every read sees the latest committed data at the time of the individual statement — no phantom reads between statements within the same transaction. In MySQL (InnoDB) with `REPEATABLE_READ` (the MySQL default), reads within the transaction are consistent with the first read. These are different behaviors at the same isolation label.

**Why `readOnly=true` is not a hard read-only guarantee:**
- `readOnly=true` is a **hint**, not an enforcement. Spring sets `connection.setReadOnly(true)` — the JDBC driver may or may not honor it.
- Hibernate, when it detects `readOnly=true`, skips **dirty checking** and entity snapshot creation — this is a real performance win for large result sets.
- PostgreSQL's JDBC driver sends `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY` when `readOnly=true` — which does actually prevent writes. But this is driver-specific.
- Spring Data JPA's `@Query` methods on `@Transactional(readOnly=true)` repositories benefit from Hibernate's optimization, not necessarily database enforcement.
- Most importantly: you CAN issue a `save()` or `delete()` in a `readOnly=true` transaction and Spring will not throw — Hibernate may flush it at commit depending on the flush mode.

**Practical rule:** Use `readOnly=true` on all read-only service methods — it gives you Hibernate performance benefits and signals intent to readers. Don't rely on it as a security control.

**Interview trap:** "What happens to the `readOnly` hint in a `REQUIRES_NEW` inner transaction that is read-write, called from a `readOnly` outer transaction?" — Each transaction has its own `TransactionDefinition`. The inner `REQUIRES_NEW` transaction gets its own connection with its own `readOnly=false` setting — completely independent. The outer transaction's `readOnly=true` only applies to the outer connection/transaction.

**Tags:** transactional, isolation, readonly, jpa, hibernate, dirty-checking, spring-core, senior

---

## Q-SPRC-021 [bloom: analyze] [level: master]
**Question:** You have a Spring Boot 3 service where `@Transactional` on a public method is silently doing nothing. Walk through your debugging checklist systematically. List every root cause and how you confirm each one.

**Model answer:** When `@Transactional` silently does nothing (no transaction opened, no rollback on exception), the causes fall into these categories:

**1. Self-invocation (most common)**
- Symptom: method called from within the same bean via `this`
- Confirm: add a log in the method; check if `TransactionSynchronizationManager.isActualTransactionActive()` returns `false`
- Fix: extract to separate bean, inject self, or use AspectJ weaving

**2. Method is private or package-private**
- CGLIB cannot override non-public methods; annotation silently ignored
- Confirm: check method visibility
- Fix: make method `public`

**3. Method is `final`**
- CGLIB cannot override `final` methods
- Confirm: check method signature
- Fix: remove `final`

**4. Class is `final`**
- CGLIB cannot subclass a `final` class (Kotlin data classes and `data class` in general are `final` by default)
- Confirm: check class declaration
- Fix: in Kotlin, use `open class` or the `kotlin-spring` compiler plugin which automatically makes Spring beans `open`

**5. Bean not managed by Spring**
- Created with `new` rather than via container; no proxy ever created
- Confirm: check if the class is in a scanned package, annotated with a stereotype, and the context is actually created
- Fix: use proper Spring bean injection

**6. Exception type doesn't trigger rollback**
- Checked exception thrown but `rollbackFor` not set
- Confirm: check exception hierarchy; check if it's a checked exception
- Fix: `@Transactional(rollbackFor = Exception.class)`

**7. Exception caught inside the method**
- `@Transactional` only sees exceptions that escape the method boundary; if you swallow the exception internally, the tx commits
- Confirm: read the method body for try/catch blocks
- Fix: rethrow or mark for rollback manually with `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`

**8. Wrong transaction manager**
- Multiple `PlatformTransactionManager` beans; `@Transactional` uses the default one but the data source used inside uses a different one
- Confirm: check `@Transactional(transactionManager="specificTxManager")` or whether there's a `@Primary` on the right manager
- Fix: explicit `transactionManager` reference

**9. Spring Boot 2.6+ circular dependency + `@Lazy` proxy**
- A proxy bean is proxied again via `@Lazy`; the inner method dispatch can skip the `TransactionInterceptor`
- Rare; confirm with transaction debug logging: `logging.level.org.springframework.transaction=TRACE`

**Tooling:** Enable transaction trace logging:
```properties
logging.level.org.springframework.transaction.interceptor=TRACE
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```
This logs every transaction begin/commit/rollback/skip with the reason.

**Interview trap:** "How do you confirm the proxy was actually created for the bean?" — `applicationContext.getBean(OrderService.class).getClass().getName()` — if it returns `OrderService$$SpringCGLIB$$0` the proxy exists. If it returns `com.example.OrderService`, no proxy was created (or you got the raw target, which shouldn't happen unless you called `getBean` in a tricky way).

**Tags:** transactional, debugging, cglib, proxy, kotlin, self-invocation, checklist, master

---

## Q-SPRC-022 [bloom: analyze] [level: master]
**Question:** Explain the full three-level singleton cache in `DefaultSingletonBeanRegistry`. When does Spring put a bean in each level, and why does this mechanism sometimes still fail with `@Transactional` beans in circular dependency scenarios?

**Model answer:** `DefaultSingletonBeanRegistry` maintains three maps:

```
singletonObjects:        Map<String, Object>   — fully initialized singletons (L1)
earlySingletonObjects:   Map<String, Object>   — early-exposed references (L2)
singletonFactories:      Map<String, ObjectFactory<?>>  — factories for early refs (L3)
```

**Lifecycle of bean A with a circular dep on bean B (B also depends on A), both needing AOP proxies:**

1. `getBean("A")` — A not in L1, start creation
2. A added to `singletonsCurrentlyInCreation` set
3. `ObjectFactory<A>` registered in L3 (singletonFactories): `() -> getEarlyBeanReference("A", mbd, bean)` — this factory can produce an early proxy if needed
4. A needs B → `getBean("B")` — B not in L1, start creation
5. B needs A → `getBean("A")` — A in `singletonsCurrentlyInCreation`, so check caches
6. L1 miss, L2 miss, **L3 hit** — call the `ObjectFactory`, which calls `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference()` — if A has an AOP proxy scheduled, this creates the proxy **early** and moves it to L2
7. B gets the early proxy reference of A injected
8. B finishes initialization, moves to L1
9. Back in A's initialization: A's properties are set (B injected), A goes through `postProcessAfterInitialization`
10. `AbstractAutoProxyCreator` checks: "did I already expose an early proxy for A?" — yes, found in `earlyProxyReferences`. It returns the **same proxy object** (not a new one) and puts it in L1
11. A removed from `singletonsCurrentlyInCreation`

**Why it can still fail:**

The mechanism assumes `getEarlyBeanReference()` produces the final proxy. But some `BeanPostProcessor` implementations cannot create their proxy in `getEarlyBeanReference()` — they require the fully initialized bean. If such a BPP is present and A is involved in a circular dep, Spring detects that the early reference and the final `postProcessAfterInitialization` result would be different objects. Spring then throws `BeanCurrentlyInCreationException` with a message like: "Raw injected bean ... differs from final proxy".

**Common trigger in practice:** Custom `BeanPostProcessor` that wraps the raw bean (not proxy-aware) combined with `@Transactional` on a circularly-dependent bean. Or using `@Async` (which uses `AsyncAnnotationBeanPostProcessor`) combined with circular deps.

**Spring Boot 2.6+ default:** `spring.main.allow-circular-references=false` — throws immediately for any circular dep. Forces proper design. Re-enabling it is a code smell; address the circular dep instead.

**Interview trap:** "Why does the singletonFactories store a `ObjectFactory` rather than the raw bean?" — Because at the point of L3 registration, the bean exists (constructed) but hasn't had BPPs run. The factory defers the "should I wrap this in an early proxy?" decision until the early reference is actually requested. If nobody requests it (no circular dep), the factory is never called and no early proxy is created — the normal path runs instead.

**Tags:** 3-level-cache, circular-dependency, singletonObjects, earlySingletonObjects, proxy, aop, master

---

## Q-SPRC-023 [bloom: analyze] [level: master]
**Question:** Design a production-ready custom `BeanPostProcessor` that measures initialization time for every Spring bean and logs warnings for beans that take longer than a configurable threshold. What lifecycle concerns must you address?

**Model answer:** Custom `BeanPostProcessor` implementation:

```java
@Component
public class SlowBeanInitWarner implements BeanPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(SlowBeanInitWarner.class);
    private final ConcurrentHashMap<String, long[]> startTimes = new ConcurrentHashMap<>();
    private long thresholdMs = 500L;

    @Override
    public void setEnvironment(Environment env) {
        // Read threshold from config — can't use @Value here (BPP created before value injection)
        String val = env.getProperty("monitoring.bean-init.threshold-ms", "500");
        this.thresholdMs = Long.parseLong(val);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        startTimes.put(beanName, new long[]{System.currentTimeMillis()});
        return bean;  // MUST return the bean (or the replacement)
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        long[] start = startTimes.remove(beanName);
        if (start != null) {
            long elapsed = System.currentTimeMillis() - start[0];
            if (elapsed > thresholdMs) {
                log.warn("Slow bean init: {} took {}ms (threshold {}ms)",
                    beanName, elapsed, thresholdMs);
            }
        }
        return bean;  // MUST return the bean — returning null would break the context
    }
}
```

**Critical lifecycle concerns:**

1. **Always return the bean from both methods** (or a replacement). Returning `null` causes a null bean definition — context will fail. If you wrap the bean (e.g. create a proxy), return the wrapper.

2. **BPPs cannot use `@Value` or `@Autowired`-injected fields** for their own configuration. BPPs are instantiated very early — before `AutowiredAnnotationBeanPostProcessor` runs. Use `EnvironmentAware`, constructor injection with `@Value` resolved via `BeanDefinitionRegistryPostProcessor`, or read from `Environment` directly as shown above.

3. **BPPs themselves are NOT post-processed by other BPPs** in the normal flow. A BPP bean doesn't go through `@Autowired` processing the same way normal beans do.

4. **BPPs run for EVERY bean including infrastructure beans** — including other BPPs. Guard against processing BPPs themselves if needed: `if (bean instanceof BeanPostProcessor) return bean;`

5. **Thread safety:** Multiple beans can be initialized in parallel during Spring Boot with parallel init. Use `ConcurrentHashMap` or other thread-safe structures.

6. **`postProcessAfterInitialization` returns the proxy** in `AbstractAutoProxyCreator`. If your BPP needs to see the raw bean, it must run in `postProcessBeforeInitialization` or before AOP proxies are created. If your BPP creates its own proxy AND AOP also creates a proxy, you can end up with double-wrapped beans — use `SmartInstantiationAwareBeanPostProcessor` to coordinate.

**Interview trap:** "What if a BPP throws an exception?" — The exception propagates up through `ApplicationContext.refresh()`, which calls `destroyBeans()` and rethrows. The context fails to start. If you want a BPP that doesn't fail startup, catch exceptions internally and log them. But be careful — a BPP that silently eats errors creates invisible production bugs.

**Tags:** beanpostprocessor, lifecycle, spring-core, custom-infrastructure, master, design

---

## Q-SPRC-024 [bloom: analyze] [level: master]
**Question:** You are running a high-throughput Spring Boot service. You notice that `@Transactional(readOnly=true)` repository calls are still acquiring exclusive connections from HikariCP under load. What are the possible causes and what architectures can you apply to route reads to a read replica?

**Model answer:** **Why readOnly=true still acquires connections:**

`readOnly=true` is a hint — HikariCP itself has no concept of routing. Every `DataSource.getConnection()` call returns a connection from the same pool, regardless of `readOnly`. The hint only affects what the driver/database does with the connection once acquired.

**Root causes for high connection pressure on reads:**

1. **Single DataSource for all operations** — reads and writes compete for the same HikariCP pool. Solution: separate pools for primary and replica.

2. **Implicit transaction escalation** — a method annotated `@Transactional(readOnly=true)` that calls a non-readOnly method will escalate (REQUIRED propagation joins the outer tx) — the inner write creates the same connection.

3. **N+1 query pattern** — not a connection issue per se, but inflates transaction duration, holding connections longer. Diagnose with `spring.jpa.show-sql=true` or Hibernate's statistics.

4. **Long-running transactions** — `readOnly=true` with pagination queries that don't close the transaction promptly.

**Architecture for read replica routing:**

**Option 1: Spring's `AbstractRoutingDataSource`**
```java
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? "replica"
            : "primary";
    }
}

@Bean
public DataSource dataSource(DataSource primary, DataSource replica) {
    ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
    routing.setTargetDataSources(Map.of("primary", primary, "replica", replica));
    routing.setDefaultTargetDataSource(primary);
    return routing;
}
```
This inspects `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` and routes to replica for `readOnly=true` transactions. The transaction must already be started before routing occurs — ensure `@Transactional` is processed before the DataSource routing decision. This means the `@Transactional` service layer must open the tx, not the repository.

**Option 2: Dedicated `@Repository` beans with separate `@Qualifier`-d `DataSource`/`EntityManagerFactory`** — explicit, no magic, most controllable. More boilerplate.

**Option 3: Datasource proxy pattern (p6spy / datasource-proxy)** — intercept at the driver level, route based on statement type (SELECT vs INSERT/UPDATE/DELETE). Fully transparent to application code.

**Interview trap:** "What if the replica lags behind?" — This is the fundamental trade-off of read replicas: eventual consistency. For a `readOnly=true` query reading data that was just written (immediately after a write in a workflow), you might read stale data. Solution: route reads-after-writes to primary. Implement via a thread-local flag set in the service layer after a write, checked in the routing DataSource: if the current request context had a write, stick to primary for the remainder of the request.

**Tags:** transactional, readonly, hikaricp, read-replica, abstract-routing-datasource, datasource, master, performance
