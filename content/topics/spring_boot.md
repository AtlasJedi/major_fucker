# Spring Boot & Actuator — question bank

> Spring Boot is the standard production runtime for Java/Kotlin microservices. Senior interviews test whether you understand the auto-configuration machinery (not just "it works by magic"), can configure and secure Actuator endpoints safely, use externalized config correctly at scale, and know the operational gotchas — embedded server tuning, graceful shutdown, Kubernetes liveness/readiness probes, custom metrics via Micrometer — that separate someone who ships to production from someone who just writes features.

## Scope

- @SpringBootApplication breakdown: @Configuration + @EnableAutoConfiguration + @ComponentScan
- Auto-configuration internals: AutoConfiguration.imports, spring.factories, @Conditional* annotations, ordering, excludes
- Starters: what they are, key starters and what each pulls in, swapping embedded servers
- Externalized configuration: property-source precedence (18-level), profiles, @ConfigurationProperties vs @Value, relaxed binding
- Actuator: full endpoint catalogue (health, info, metrics, env, beans, mappings, loggers, threaddump, heapdump, httpexchanges, conditions, configprops, scheduledtasks), default HTTP exposure rules
- Actuator security: management.endpoints.web.exposure.include, management.server.port, show-details never/when-authorized/always
- Custom HealthIndicator: implementing Health health(), composite health, status ordering
- Health groups: management.endpoint.health.group.*, production/kubernetes grouping patterns
- Kubernetes probes: liveness vs readiness semantics, /actuator/health/liveness and /actuator/health/readiness, startup probe, danger of checking external deps in liveness
- Custom Micrometer metrics: Counter, Timer, Gauge, Tags, cardinality pitfalls
- Custom @Endpoint: @ReadOperation, @WriteOperation, @DeleteOperation, @Selector
- Embedded server: Tomcat defaults, thread pool tuning, swapping to Undertow/Jetty
- Layered JARs and fat JARs: structure, layer ordering for Docker cache efficiency
- Graceful shutdown: server.shutdown=graceful, timeout, in-flight request handling
- Logging: Logback defaults, logback-spring.xml, live level change via /loggers

---

## Q-SPRB-001 [bloom: recall] [level: junior]
**Question:** What three annotations does `@SpringBootApplication` combine, and what does each one do?

**Model answer:** `@SpringBootApplication` is a composed annotation equivalent to:

1. **`@SpringBootConfiguration`** (specialisation of `@Configuration`) — marks the class as a Spring configuration class and a source of bean definitions. It differs from plain `@Configuration` only in that Spring Boot's test infrastructure can locate it automatically.
2. **`@EnableAutoConfiguration`** — triggers the auto-configuration mechanism. Spring Boot scans `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3.x) or `META-INF/spring.factories` (Boot 2.x) for candidate configuration classes and conditionally registers beans based on what's on the classpath.
3. **`@ComponentScan`** — scans the package of the annotated class and all sub-packages for `@Component`-annotated classes (including `@Service`, `@Repository`, `@Controller`, etc.) and registers them as beans.

```java
// Equivalent to:
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.example")
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

The `exclude` attribute on `@SpringBootApplication` passes through to `@EnableAutoConfiguration`:
```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
```

**Interview trap:** "Where should you place the main class?" — In the root package of your application (e.g., `com.example`). If placed in the default (unnamed) package, `@ComponentScan` has no base package and scans the *entire classpath*, including framework internals. This causes enormous startup time and potential bean-definition conflicts. Spring Boot will actually warn you about this.

**Tags:** spring-boot, annotations, component-scan, auto-configuration, startup

---

## Q-SPRB-002 [bloom: recall] [level: junior]
**Question:** What is a Spring Boot starter, and why do starters exist instead of just listing individual dependencies?

**Model answer:** A starter is a dependency descriptor — a Maven/Gradle artifact that contains **no production code**, only a `pom.xml`/`build.gradle` that pulls in a curated, version-compatible set of transitive dependencies.

They exist to solve two problems:
1. **Version compatibility hell** — before starters, teams had to manually align versions of Spring MVC, Jackson, Hibernate Validator, Tomcat, etc. A single version mismatch causes subtle runtime failures. Starters guarantee a tested, compatible set.
2. **Convention over configuration** — adding one artifact signals intent and triggers auto-configuration.

Key starters and what they include:

| Starter | Key transitive dependencies |
|---|---|
| `spring-boot-starter-web` | Spring MVC, embedded Tomcat, Jackson, Hibernate Validator |
| `spring-boot-starter-data-jpa` | Hibernate ORM, Spring Data JPA, HikariCP, JDBC driver support |
| `spring-boot-starter-security` | Spring Security core + web filter chain, BCrypt |
| `spring-boot-starter-actuator` | Micrometer core, management endpoints |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Spring Test, Testcontainers (Boot 3.x) |
| `spring-boot-starter-webflux` | Project Reactor, Netty (replaces Tomcat) |

**Interview trap:** "Can you swap out Tomcat for Undertow with a starter?" — Yes. Exclude `spring-boot-starter-tomcat` from within `spring-boot-starter-web` and add `spring-boot-starter-undertow`. Spring Boot's `EmbeddedWebServerFactoryCustomizerAutoConfiguration` detects the server implementation on the classpath and configures it automatically. No `web.xml`, no deployment descriptor.

**Tags:** starters, dependencies, embedded-server, version-management

---

## Q-SPRB-003 [bloom: recall] [level: junior]
**Question:** Name at least eight Spring Actuator endpoints and describe what each one exposes.

**Model answer:** Actuator exposes operational endpoints under `/actuator` (configurable base path). The full catalogue includes:

| Endpoint | What it exposes |
|---|---|
| `/health` | Aggregate health status + per-indicator detail (DB, disk, cache, custom). Sub-paths `/health/liveness` and `/health/readiness` for Kubernetes probes (Boot 2.3+). |
| `/info` | App metadata: version, git commit hash, build time (populated via `spring-boot-maven-plugin` or `git-commit-id-plugin`). |
| `/metrics` | Micrometer metrics registry. Browse with `/metrics/{name}` (e.g., `/metrics/http.server.requests`). Prometheus scrape format via `/actuator/prometheus` with Micrometer Prometheus registry. |
| `/env` | All `PropertySource` values and their resolved values. **Sensitive — masks passwords by default, but structure leaks config key names.** |
| `/beans` | Full ApplicationContext bean graph: class, scope, dependencies. |
| `/mappings` | All `@RequestMapping`/`@GetMapping` etc. — shows URL → handler method mapping for every endpoint. |
| `/loggers` | Current log level per logger. Supports GET (read) and POST (live change without restart). |
| `/threaddump` | JVM thread snapshot with stack traces — invaluable for deadlock diagnosis. |
| `/heapdump` | Full HPROF heap dump download. Large file; tightly restricted in production. |
| `/httpexchanges` | Last N HTTP request/response exchanges (replaces `/httptrace` in Boot 3.x). Requires an `HttpExchangeRepository` bean. |
| `/conditions` | Auto-configuration report: which `@Conditional` matched (positive matches) and which didn't (negative matches) and why. |
| `/configprops` | All `@ConfigurationProperties` beans and their bound values. Masks sensitive fields. |
| `/scheduledtasks` | All `@Scheduled` tasks registered with the scheduler — method, cron expression, fixed-rate/delay. |
| `/caches` | Configured caches (if `spring-boot-starter-cache` present). |
| `/shutdown` | POST to gracefully shut down the application context. **Disabled by default.** |

**Interview trap:** "Which endpoints are exposed over HTTP by default?" — In Boot 2.x/3.x, only **`/health`** is exposed over HTTP by default (Boot 2.x also exposes `/info`; Boot 3.x changed the defaults so only `/health` is HTTP-exposed by default). All endpoints are enabled JMX-side. To expose more: `management.endpoints.web.exposure.include=health,info,metrics,prometheus`.

**Tags:** actuator, endpoints, health, metrics, operations, monitoring

---

## Q-SPRB-004 [bloom: recall] [level: junior]
**Question:** What is the difference between `@Value` and `@ConfigurationProperties`? When do you use each?

**Model answer:**

| | `@Value` | `@ConfigurationProperties` |
|---|---|---|
| Binding | Single property per field | Entire prefix-bound group of properties |
| Validation | No (manual) | Yes — annotate with `@Validated` + JSR-303 constraints |
| Relaxed binding | Limited | Full: `my-prop`, `MY_PROP`, `myProp`, `my_prop` all map to `myProp` |
| Refactoring | Fragile — string literals scattered | Centralized POJO, IDE-navigable |
| SpEL | Yes (`#{...}`) | No |
| Default value | Inline: `@Value("${prop:default}")` | Field initializer |
| IDE support | Partial | Full with `spring-boot-configuration-processor` |

`@Value` is acceptable for one-off single properties. `@ConfigurationProperties` is the right choice for anything with a prefix (database config, feature flags, integration params):

```java
@ConfigurationProperties(prefix = "app.datasource")
@Validated
public class DatasourceProperties {
    @NotBlank
    private String url;
    @Min(1) @Max(200)
    private int poolSize = 10;
    // getters/setters or use @ConstructorBinding (Boot 2.2+)
}
```

Registration: either `@EnableConfigurationProperties(DatasourceProperties.class)` on a `@Configuration` class, or annotate the class itself with `@Component` (simpler but mixes concerns).

**Interview trap:** "`@ConfigurationProperties` class defined, no bean found at startup — why?" — The class needs to be registered as a bean. Annotating it with just `@ConfigurationProperties` without `@Component` and without `@EnableConfigurationProperties` means Spring never creates the bean. `NoSuchBeanDefinitionException` or silent defaults result. Boot 3.x auto-scans `@ConfigurationProperties` classes if `@ConfigurationPropertiesScan` is present.

**Tags:** configuration, @Value, @ConfigurationProperties, relaxed-binding, validation, externalized-config

---

## Q-SPRB-005 [bloom: understand] [level: regular]
**Question:** Explain how Spring Boot's auto-configuration mechanism works internally. Walk through the chain from `@EnableAutoConfiguration` to a bean being conditionally registered.

**Model answer:** The chain has five stages:

**1. Trigger.** `@EnableAutoConfiguration` imports `AutoConfigurationImportSelector` via `@Import`. This class implements `ImportSelector` — Spring calls its `selectImports()` method during context refresh.

**2. Candidate discovery.** `AutoConfigurationImportSelector` reads the list of candidate configuration class names from:
- **Boot 2.x:** `META-INF/spring.factories` under key `org.springframework.boot.autoconfigure.EnableAutoConfiguration`
- **Boot 3.x:** `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (one class per line, more efficient — no scanning)

**3. Filtering.** Candidates are filtered by:
- `spring.autoconfigure.exclude` property / `exclude` attribute on `@SpringBootApplication`
- `AutoConfigurationImportFilter` implementations that short-circuit before the class is even loaded (e.g., `OnClassCondition`)

**4. Ordering.** Surviving candidates are ordered by `@AutoConfigureBefore`, `@AutoConfigureAfter`, `@AutoConfigureOrder`. This ensures, e.g., `DataSourceAutoConfiguration` runs before `HibernateJpaAutoConfiguration`.

**5. Conditional evaluation.** Each candidate class is loaded and its `@Conditional*` annotations evaluated:

| Annotation | Condition |
|---|---|
| `@ConditionalOnClass` | Named class present on classpath |
| `@ConditionalOnMissingClass` | Named class absent |
| `@ConditionalOnBean` | A bean of the type already registered |
| `@ConditionalOnMissingBean` | No bean of the type registered yet (most common — "back off if user provides their own") |
| `@ConditionalOnProperty` | Property key present / matches value |
| `@ConditionalOnWebApplication` | Running in a web context |
| `@ConditionalOnExpression` | SpEL expression evaluates to true |

Example: `DataSourceAutoConfiguration` is annotated with `@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })` — if neither is on the classpath, the entire config class is skipped without loading any of its `@Bean` methods.

**Debugging.** Start with `--debug` flag or enable `logging.level.org.springframework.boot.autoconfigure=DEBUG`. Alternatively, hit `/actuator/conditions` at runtime for the full positive/negative match report.

**Interview trap:** "What happens if you define your own `DataSource` bean?" — The auto-configuration for `DataSource` is annotated `@ConditionalOnMissingBean(DataSource.class)`. Once your bean is registered (component scan runs before auto-config import), the auto-config backs off entirely. This is the designed override mechanism — prefer it over `exclude`.

**Tags:** auto-configuration, conditional, AutoConfiguration.imports, spring.factories, @Conditional, internals

---

## Q-SPRB-006 [bloom: understand] [level: regular]
**Question:** Describe Spring Boot's property-source precedence order. Given a property defined in four places simultaneously — `application.yml`, an OS environment variable, a JVM system property, and a `@TestPropertySource` in a test — which value wins and why?

**Model answer:** Spring Boot evaluates `PropertySource`s in a strict order — **higher number overrides lower**. The full 18-level chain (abbreviated to the most interview-relevant levels):

| Priority (high→low) | Source |
|---|---|
| 1 (highest) | Test annotations: `@TestPropertySource`, `@SpringBootTest(properties=…)` |
| 2 | Command-line arguments (`--server.port=9090`) |
| 3 | `SPRING_APPLICATION_JSON` env var or system prop |
| 4 | Servlet init params (`ServletContext`/`ServletConfig`) |
| 5 | OS environment variables |
| 6 | JVM system properties (`-Dspring.datasource.url=…`) |
| 7 | Config data from `application.properties`/`application.yml` (inside JAR) |
| 8 | Default properties (`SpringApplication.setDefaultProperties`) |

In production, environment variables (level 5) beat `application.yml` (level 7), which is why `SPRING_DATASOURCE_URL=jdbc:postgresql://prod-host/db` in a Kubernetes env var overrides the localhost URL baked into the config file.

**Relaxed binding** in env vars: `SPRING_DATASOURCE_URL` → `spring.datasource.url` (uppercase + underscores → lowercase + dots). `SERVER_PORT` → `server.port`. This is done by `RelaxedPropertyResolver`.

In the scenario described: `@TestPropertySource` wins (priority 1) because test overrides sit at the top. JVM system property beats `application.yml` but loses to the environment variable in production (though in tests, `@TestPropertySource` beats everything else).

**Interview trap:** "What about profile-specific files?" — `application-prod.yml` has *higher* priority than `application.yml` when the `prod` profile is active. Profile-specific config overlays the default config rather than replacing it. Boot 2.4+ uses a single-document approach where `spring.config.activate.on-profile` replaces the older `spring.profiles` key.

**Tags:** externalized-config, property-source, precedence, profiles, environment-variables, relaxed-binding

---

## Q-SPRB-007 [bloom: understand] [level: regular]
**Question:** Only `/health` is exposed over HTTP by default in Spring Boot Actuator. How do you expose additional endpoints, and what are the security risks of exposing everything with `include: "*"` in production?

**Model answer:** Exposure is controlled by two orthogonal axes:
- **Enabled:** whether the endpoint bean exists at all. Default: all enabled except `/shutdown`.
- **Exposed:** whether it's accessible over HTTP (or JMX). Default HTTP: only `health`.

To expose more:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics,prometheus,loggers"
        # or include: "*" to expose all — dangerous in prod, see below
  endpoint:
    health:
      show-details: when-authorized  # never | when-authorized | always
```

**Risks of `include: "*"` in production:**

| Endpoint | Risk |
|---|---|
| `/env` | Exposes all property keys and (partially masked) values. An attacker learns your config structure, database hostnames, feature flag names. Masked passwords can sometimes be unmasked via `/configprops`. |
| `/heapdump` | Dumps the full JVM heap — contains secrets, session tokens, PII, everything in memory. Megabytes to gigabytes in size. |
| `/beans` | Reveals internal class names, package structure — aids targeted attacks. |
| `/shutdown` | POST shuts down the app. Not exposed by default, but `include: "*"` enables the endpoint (though it must also be enabled separately). |
| `/threaddump` | Reveals thread names, stack traces, potential locks and contention info. |

**Mitigation patterns:**
1. Whitelist explicitly: only expose what your monitoring stack needs.
2. Use a separate management port: `management.server.port=8081` and block it at the network layer (VPC firewall, k8s NetworkPolicy). Keeps operational endpoints completely off the public load balancer.
3. Secure with Spring Security: require `ACTUATOR` role for sensitive endpoints via `requestMatchers("/actuator/**").hasRole("ACTUATOR")`.

**Interview trap:** "Can you enable an endpoint but not expose it?" — Yes. A disabled endpoint has no bean. An enabled-but-not-exposed endpoint exists internally (e.g., accessible over JMX) but has no HTTP route. This distinction matters for JMX monitoring tools in legacy environments.

**Tags:** actuator, security, exposure, management-port, production, http-endpoints

---

## Q-SPRB-008 [bloom: apply] [level: regular]
**Question:** Write a custom `HealthIndicator` that checks whether a downstream REST dependency is reachable. Include the bean definition and explain how the aggregate `/actuator/health` status is computed from multiple indicators.

**Model answer:** Implement `HealthIndicator` (or `ReactiveHealthIndicator` for WebFlux):

```java
@Component("paymentService")  // bean name becomes the indicator key
public class PaymentServiceHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    public PaymentServiceHealthIndicator(RestClient.Builder builder) {
        this.restClient = builder
            .baseUrl("https://payment.internal/actuator/health")
            .build();
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<Map> response = restClient.get()
                .retrieve()
                .toEntity(Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                    .withDetail("url", "https://payment.internal")
                    .withDetail("status", response.getBody().get("status"))
                    .build();
            }
            return Health.down()
                .withDetail("httpStatus", response.getStatusCode().value())
                .build();

        } catch (Exception ex) {
            return Health.down(ex)
                .withDetail("error", ex.getMessage())
                .build();
        }
    }
}
```

**Aggregate status computation:** Spring Boot collects all `HealthIndicator` results and applies a `StatusAggregator`. The default ordering (worst wins):

`DOWN` > `OUT_OF_SERVICE` > `UP` > `UNKNOWN`

If any indicator returns `DOWN`, the aggregate `/health` is `DOWN` (HTTP 503). If all are `UP`, the aggregate is `UP` (HTTP 200). Custom statuses can be added and their severity order configured:

```yaml
management:
  endpoint:
    health:
      status:
        order: down,out-of-service,unknown,up
        http-mapping:
          down: 503
          out-of-service: 503
```

**`show-details` controls what the HTTP response body contains:**
- `never` (default): only `{"status":"UP"}` — safe for public endpoints
- `when-authorized`: full details only for authenticated users with `ACTUATOR` role
- `always`: full details for everyone — only for internal-only management ports

**Interview trap:** "Should a custom HealthIndicator call an external database or service?" — For **readiness** checks, yes — you want to know if the downstream dependency is available before routing traffic. For **liveness** checks, **no** — a downstream service being down is not a reason to restart *your* pod. The liveness indicator should only check your own process health (memory, thread pool, deadlock detection). Mixing these up causes k8s restart loops on dependency outages.

**Tags:** actuator, health-indicator, custom-health, status-aggregator, show-details, downstream-checks

---

## Q-SPRB-009 [bloom: apply] [level: regular]
**Question:** What are Spring Boot Actuator health groups, and how do you configure them for a Kubernetes deployment that needs separate liveness and readiness probes?

**Model answer:** Health groups let you partition health indicators into named subsets, each with its own HTTP path and independently configurable `show-details`. Boot 2.3+ ships with two built-in groups when Kubernetes support is detected:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true       # auto-enabled when running on k8s (KUBERNETES_SERVICE_HOST set)
      group:
        readiness:
          include: "db,redis,paymentService"   # custom indicators + built-in
          show-details: always
        liveness:
          include: "livenessState"             # built-in: checks ApplicationContext is alive
          show-details: never
```

This creates:
- `/actuator/health/readiness` — checks DB, Redis, payment service
- `/actuator/health/liveness` — checks only that the Spring context is alive

**Kubernetes Deployment spec:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3
```

**Semantics to be precise about:**
- **Liveness failure** → k8s kills and restarts the container. Use only for irrecoverable states: deadlock, OOM loop, corrupted in-memory state. *Never* include external dependency checks here.
- **Readiness failure** → k8s removes the pod from the Service's endpoint slice. Traffic stops routing to it, but the pod is not killed. Recovers automatically when the indicator flips back to UP. Use for: app still starting up, downstream service temporarily unavailable.
- **Startup probe** (Boot 2.3+ `startupState`): separate probe with higher `failureThreshold` to handle slow startup without triggering liveness. Maps to `/actuator/health/liveness` with longer tolerance.

**Interview trap:** "What's the blast radius if you put your database HealthIndicator in the liveness group?" — Every time the DB is down, k8s restarts all your pods. The restart doesn't fix the DB. You enter a CrashLoopBackOff restart loop and your entire service is unavailable even after the DB recovers. Liveness probe must only check the pod's own health, never external dependencies.

**Tags:** actuator, health-groups, kubernetes, liveness, readiness, probes, k8s

---

## Q-SPRB-010 [bloom: apply] [level: regular]
**Question:** How do you change a log level at runtime using Spring Actuator, without restarting the application?

**Model answer:** The `/actuator/loggers` endpoint exposes read and write access to the SLF4J logging system (Logback by default).

**Read current level:**
```bash
GET /actuator/loggers/com.example.service.PaymentService
# Response:
# {"configuredLevel": null, "effectiveLevel": "INFO"}
```
`null` configured level means it inherits from the root logger.

**Change level at runtime:**
```bash
POST /actuator/loggers/com.example.service.PaymentService
Content-Type: application/json

{"configuredLevel": "DEBUG"}
```

**Reset to inherited level:**
```bash
POST /actuator/loggers/com.example.service.PaymentService
Content-Type: application/json

{"configuredLevel": null}
```

**List all loggers:**
```bash
GET /actuator/loggers
# Returns all logger names + their levels
```

This is **in-memory only** — changes do not persist across restarts. To make permanent changes, update `application.yml` and redeploy. For immediate production debugging (e.g., enabling `DEBUG` for a specific service during an incident), this is invaluable without a restart.

**Security requirement:** the `/loggers` POST endpoint must be protected. Someone setting `ROOT` to `TRACE` on a production service will flood your log aggregation pipeline and potentially expose sensitive data.

**Interview trap:** "Does changing the log level via Actuator affect all instances in a cluster?" — No. The POST goes to a single instance. In a multi-pod deployment, you'd need to hit each pod individually (e.g., via `kubectl exec` or a small script iterating pod IPs). For cluster-wide log level management, use a dynamic config system like Spring Cloud Config Server with `@RefreshScope`.

**Tags:** actuator, loggers, runtime, log-level, debugging, operations

---

## Q-SPRB-011 [bloom: apply] [level: senior]
**Question:** Describe the full internals of how a Spring Boot fat JAR is structured. What is a layered JAR, how does it differ from a standard fat JAR, and why does layer ordering matter for Docker build cache efficiency?

**Model answer:** **Standard fat JAR (uber JAR):**
```
myapp.jar
├── META-INF/
│   └── MANIFEST.MF          # Main-Class: org.springframework.boot.loader.JarLauncher
├── BOOT-INF/
│   ├── classes/             # compiled application classes
│   └── lib/                 # all dependency JARs (not unpacked)
└── org/springframework/boot/loader/   # Boot's custom classloader
```

The `JarLauncher` (in `org.springframework.boot.loader`) creates a custom `LaunchedURLClassLoader` that can load classes from nested JARs — standard Java classloading cannot do this. This is how Boot avoids unpacking.

**Problem with fat JAR for Docker:** Every code change rebuilds a single layer containing all ~50MB of dependencies + your 200KB application code. Docker can't cache the dependencies separately because they're in the same layer.

**Layered JAR (Boot 2.3+):**
```
myapp.jar
└── BOOT-INF/
    └── layers.idx    # declares layer order
```

Layers (in order, least-frequently-changed first):
1. `dependencies` — stable third-party JARs
2. `spring-boot-loader` — Boot launcher classes
3. `snapshot-dependencies` — SNAPSHOT JARs (change more often)
4. `application` — your classes (change every commit)

**Dockerfile using layers:**
```dockerfile
FROM eclipse-temurin:21-jre AS builder
WORKDIR /app
COPY target/myapp.jar myapp.jar
RUN java -Djarmode=layertools -jar myapp.jar extract

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Docker caches each `COPY` instruction as a separate layer. When only application code changes (the common case), only the last ~200KB layer is rebuilt and pushed. The 50MB `dependencies` layer is served from cache. This can cut CI push time from 2 minutes to 5 seconds.

**Maven plugin activation:**
```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <layers><enabled>true</enabled></layers>
  </configuration>
</plugin>
```

**Interview trap:** "Can you run `java -jar` on a layered-extracted layout?" — No. After extraction the directory is not a self-contained JAR. You invoke the `JarLauncher` class directly: `java org.springframework.boot.loader.launch.JarLauncher`. The `ENTRYPOINT` in the Dockerfile must use the class name, not `java -jar`.

**Tags:** fat-jar, layered-jar, docker, build-cache, JarLauncher, spring-boot-maven-plugin, containers

---

## Q-SPRB-012 [bloom: apply] [level: senior]
**Question:** How does Spring Boot's graceful shutdown work? What configuration is needed, and what happens to in-flight requests when a SIGTERM is received?

**Model answer:** Graceful shutdown is enabled via:
```yaml
server:
  shutdown: graceful   # default is "immediate"

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # default 30s; max wait for in-flight requests
```

**Shutdown sequence on SIGTERM:**

1. JVM receives SIGTERM → Spring `ApplicationContext`'s shutdown hook fires.
2. `SmartLifecycle` beans with phase ordering: `WebServerGracefulShutdownLifecycle` runs first (phase `Integer.MIN_VALUE + 3`).
3. The embedded server (Tomcat/Undertow/Jetty/Netty) **stops accepting new connections** — the port closes, new TCP connections are refused.
4. In-flight requests are **allowed to complete** up to `timeout-per-shutdown-phase`.
5. After the timeout (or when all requests complete), the web server shuts down.
6. The Spring `ApplicationContext` closes — `@PreDestroy` methods, `DisposableBean.destroy()`, `SmartLifecycle.stop()` run in reverse order.
7. Non-daemon threads are given time to finish; then JVM exits.

**Kubernetes integration:**
```yaml
# Deployment spec — preStop hook buys time before SIGTERM
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 5"]
terminationGracePeriodSeconds: 60   # must be > timeout-per-shutdown-phase + preStop sleep
```

Why `preStop` sleep? When k8s sends SIGTERM it simultaneously removes the pod from the Endpoints list. But kube-proxy updates are eventually consistent — a few seconds of traffic may still arrive after SIGTERM. `preStop: sleep 5` keeps the pod alive for 5 seconds before Spring's shutdown begins, avoiding 502s on in-flight requests from slow kube-proxy nodes.

**What "graceful" does NOT cover:**
- Async work submitted to `@Async` thread pools — those complete only if the `TaskExecutor` is a `ThreadPoolTaskExecutor` with `setWaitForTasksToCompleteOnShutdown(true)`.
- Background scheduled tasks — `@Scheduled` methods in flight when context closes are interrupted unless `ThreadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(true)` is set.

**Interview trap:** "If `timeout-per-shutdown-phase` is 30s but a request takes 60s, what happens?" — After 30s Spring forcibly shuts down the server regardless. The in-flight request is terminated mid-response. The client typically receives a connection reset. For long-running operations you need to either reduce timeout requirements or handle partial completion gracefully on the client.

**Tags:** graceful-shutdown, sigterm, kubernetes, lifecycle, in-flight-requests, preStop, timeout

---

## Q-SPRB-013 [bloom: apply] [level: senior]
**Question:** Walk through implementing a custom Micrometer `Counter` and `Timer` in a Spring Boot service. What are the cardinality pitfalls, and how does Micrometer integrate with Prometheus?

**Model answer:** Micrometer is Spring Boot's metrics facade — analogous to SLF4J for logging. It abstracts over Prometheus, Datadog, CloudWatch, InfluxDB, etc. via registry implementations.

**Dependency:**
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```
This adds the Prometheus registry (auto-configured) and exposes `/actuator/prometheus` in Prometheus text format.

**Counter:**
```java
@Service
public class OrderService {
    private final Counter orderCreatedCounter;
    private final Counter orderFailedCounter;

    public OrderService(MeterRegistry registry) {
        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("Total orders successfully created")
            .tag("region", "eu-west")        // low-cardinality tag — safe
            .register(registry);
        this.orderFailedCounter = Counter.builder("orders.failed")
            .description("Total orders that failed")
            .register(registry);
    }

    public Order createOrder(CreateOrderCommand cmd) {
        try {
            Order order = repository.save(new Order(cmd));
            orderCreatedCounter.increment();
            return order;
        } catch (Exception e) {
            orderFailedCounter.increment();
            throw e;
        }
    }
}
```

**Timer (measures duration + throughput):**
```java
private final Timer orderTimer;

public OrderService(MeterRegistry registry) {
    this.orderTimer = Timer.builder("orders.processing.time")
        .description("Time to process an order")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()   // enables Prometheus histogram for P99 accuracy
        .register(registry);
}

public Order createOrder(CreateOrderCommand cmd) {
    return orderTimer.record(() -> doCreateOrder(cmd));
}
```

**Cardinality pitfalls:** Prometheus stores one time series per unique label combination. High-cardinality tags explode storage and query performance:

```java
// WRONG — userId has millions of values → millions of time series
Counter.builder("orders.created")
    .tag("userId", cmd.getUserId())   // cardinality bomb
    .register(registry);

// RIGHT — bucket by region, tier, or other low-cardinality dimension
Counter.builder("orders.created")
    .tag("tier", cmd.getCustomerTier())  // e.g., FREE, BASIC, PREMIUM — 3 values
    .register(registry);
```

Rule of thumb: any tag with more than ~100 distinct values is high-cardinality. Order IDs, user IDs, trace IDs, request URLs — never as tags. For trace correlation, use Micrometer's exemplar support (attaches one trace ID sample to a histogram bucket) rather than a tag.

**Gauge (point-in-time value):**
```java
// Track queue depth
Gauge.builder("queue.depth", orderQueue, Queue::size)
    .description("Current order queue depth")
    .register(registry);
```

**Interview trap:** "If Prometheus is down for 10 minutes, do you lose those metrics?" — With the Micrometer Prometheus registry (pull-based), no. Prometheus scrapes your app periodically. If Prometheus is down, your in-memory counters/timers keep accumulating. When Prometheus recovers and scrapes again, it gets the current values. What you lose is the *time-series history* for the scrape interval during the outage — you can't recover those data points retroactively. Push-based registries (InfluxDB, Statsd) would drop data for the outage period.

**Tags:** micrometer, prometheus, counter, timer, gauge, cardinality, metrics, observability

---

## Q-SPRB-014 [bloom: apply] [level: senior]
**Question:** Implement a custom Spring Boot Actuator `@Endpoint` that exposes a read operation returning the current feature flag state and a write operation to toggle a specific flag. Use the correct annotations and explain the HTTP method mapping.

**Model answer:** Custom endpoints use `@Endpoint` (exposed over both HTTP and JMX) or `@WebEndpoint` (HTTP only):

```java
@Component
@Endpoint(id = "featureflags")   // accessible at /actuator/featureflags
public class FeatureFlagsEndpoint {

    private final FeatureFlagService flagService;

    public FeatureFlagsEndpoint(FeatureFlagService flagService) {
        this.flagService = flagService;
    }

    // HTTP GET /actuator/featureflags
    @ReadOperation
    public Map<String, Object> getFlags() {
        return flagService.getAllFlags().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Map.of("enabled", e.getValue(), "description", flagService.describe(e.getKey()))
            ));
    }

    // HTTP GET /actuator/featureflags/{flagName}
    @ReadOperation
    public Map<String, Object> getFlag(@Selector String flagName) {
        return Map.of(
            "name", flagName,
            "enabled", flagService.isEnabled(flagName)
        );
    }

    // HTTP POST /actuator/featureflags/{flagName}
    // Body: {"enabled": true}
    @WriteOperation
    public void setFlag(@Selector String flagName, boolean enabled) {
        flagService.setFlag(flagName, enabled);
    }

    // HTTP DELETE /actuator/featureflags/{flagName}
    @DeleteOperation
    public void resetFlag(@Selector String flagName) {
        flagService.resetToDefault(flagName);
    }
}
```

**Annotation → HTTP method mapping:**

| Annotation | HTTP Method | Path |
|---|---|---|
| `@ReadOperation` | GET | `/actuator/{id}` |
| `@ReadOperation` with `@Selector` | GET | `/actuator/{id}/{selector}` |
| `@WriteOperation` | POST | `/actuator/{id}` or `/{id}/{selector}` |
| `@DeleteOperation` | DELETE | `/actuator/{id}/{selector}` |

**Expose the endpoint:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "health,featureflags"
```

**Response media type:** By default `application/vnd.spring-boot.actuator.v3+json` and `application/json`. Override with `produces` attribute on `@ReadOperation`.

**Caching:** `@ReadOperation` can declare `cache(time, unit)` to cache the response: `@ReadOperation(cache = @CacheSpec(time = 10, unit = TimeUnit.SECONDS))` — reduces repeated expensive calls.

**Interview trap:** "What's the difference between `@Endpoint` and `@WebEndpoint`?" — `@Endpoint` is technology-agnostic: registered for HTTP (via `WebMvcEndpointHandlerMapping`) and JMX simultaneously. `@WebEndpoint` is HTTP-only (not JMX). `@JmxEndpoint` is JMX-only. Use `@Endpoint` unless you have a specific reason to exclude one transport.

**Tags:** actuator, custom-endpoint, @Endpoint, @ReadOperation, @WriteOperation, @Selector

---

## Q-SPRB-015 [bloom: analyze] [level: senior]
**Question:** A Spring Boot service is deployed to Kubernetes and experiences frequent liveness probe failures during high-load periods, causing k8s to restart pods mid-request. Walk through the diagnosis and the correct architectural fix.

**Model answer:** **Symptom:** liveness probe fails under load → pod restarts → in-flight requests cancelled → users see 5xx → repeat.

**Step 1 — What is the liveness probe hitting?**
Check the deployment spec. If it's hitting `/actuator/health` (the aggregate endpoint), it's checking *all* health indicators including database connections, downstream services, disk space. Under load, the HikariCP pool may be fully saturated — a health check that tries to acquire a connection times out → `DOWN` → liveness fails.

**Step 2 — Root cause analysis:**

Scenario A: Liveness probe hits aggregate `/actuator/health` which includes DB check.
```
High load → all 10 HikariCP connections busy → health DB check can't acquire connection → 
health = DOWN → k8s kills pod → HikariCP connections released → other pods absorb load → 
new pod starts with cold HikariCP pool → probe fails again during warmup → CrashLoopBackOff
```

Scenario B: Liveness probe `periodSeconds` too short + application under CPU pressure. The health endpoint takes >1s to respond, liveness probe has `timeoutSeconds: 1`, so it times out and counts as failure.

**Fix A — Separate liveness from readiness:**
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: "livenessState"       # ONLY checks ApplicationContext is alive
        readiness:
          include: "readinessState,db"   # checks DB — if fails, removes from LB, doesn't restart
```

```yaml
# Deployment spec
livenessProbe:
  httpGet:
    path: /actuator/health/liveness   # NO external dependency checks
    port: 8080
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness  # external deps OK here
    port: 8080
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

**Fix B — Tune probe thresholds for startup:**
Add a startup probe with higher `failureThreshold` to handle slow JVM warmup:
```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30    # 30 * 10s = 5 minutes max startup time
  periodSeconds: 10
```
Once startup probe succeeds, liveness probe takes over.

**Fix C — HikariCP tuning:**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 3000      # fail fast if no connection available (default 30s)
      maximum-pool-size: 20         # match to DB connection limit / pod count
      minimum-idle: 5
      keepalive-time: 30000
```
Health indicator should use a dedicated `connectionInitSql` or validation query that is separate from the application pool, or accept that the health check can fail under saturation and that this is a readiness concern, not liveness.

**Interview trap:** "Would increasing `failureThreshold` on the liveness probe fix the problem?" — It delays the problem but doesn't fix it. Under sustained high load the pod will still eventually be killed. The correct fix is architectural: liveness must not check external dependencies. Higher thresholds just give you more time to observe the problem.

**Tags:** kubernetes, liveness, readiness, health-groups, hikaricp, diagnosis, probes, production

---

## Q-SPRB-016 [bloom: analyze] [level: senior]
**Question:** Explain how you would debug why a certain auto-configuration class did NOT fire in a Spring Boot application. What tools and techniques are available?

**Model answer:** The auto-configuration report is the first tool. Multiple access methods:

**Method 1 — Debug flag on startup:**
```bash
java -jar myapp.jar --debug
# or
SPRING_MAIN_LOG_STARTUP_INFO=true
logging.level.org.springframework.boot.autoconfigure=DEBUG
```
This prints the full conditions report to stdout at startup:
```
============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------
DataSourceAutoConfiguration matched:
   - @ConditionalOnClass found required class 'javax.sql.DataSource' (OnClassCondition)

Negative matches:
-----------------
MongoAutoConfiguration:
   Did not match:
      - @ConditionalOnClass did not find required class 'com.mongodb.MongoClient' (OnClassCondition)
```

**Method 2 — `/actuator/conditions` endpoint (runtime):**
```bash
GET /actuator/conditions
```
Returns JSON with `positiveMatches`, `negativeMatches`, and `unconditionalClasses`.

**Method 3 — Programmatic inspection:**
```java
@SpringBootTest
class AutoConfigTest {
    @Autowired
    private ApplicationContext ctx;

    @Test
    void checkSecurityAutoConfig() {
        // Check if a bean from the auto-config is present
        assertThat(ctx.containsBean("springSecurityFilterChain")).isTrue();
    }
}
```

**Diagnostic workflow:**

1. Find the auto-config class name (e.g., `DataSourceAutoConfiguration` in `spring-boot-autoconfigure.jar`).
2. Read its `@Conditional*` annotations — what conditions are required?
3. Check the negative matches report: which condition failed?
4. Common failures:
   - `@ConditionalOnClass` — the required class is not on the classpath (missing starter dep).
   - `@ConditionalOnMissingBean` fired in reverse — a bean of the type *already exists*, so auto-config backed off. Check if you accidentally have a conflicting bean.
   - `@ConditionalOnProperty` — the required property is missing or has the wrong value.
   - Ordering problem — auto-config ran before a dependency it expects; check `@AutoConfigureAfter`.

**Advanced: custom auto-config ordering:**
```java
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class MyCustomAutoConfiguration {
    // runs only if DataSource bean already exists
}
```

**Interview trap:** "Can you add your own auto-configuration to a library?" — Yes. Create the config class, annotate with `@AutoConfiguration` (Boot 3.x) or `@Configuration` (Boot 2.x), add it to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, and ship it as a JAR. When added to a Boot app's classpath, it participates in the auto-configuration mechanism exactly like Boot's built-in configs. This is how third-party starters (AWS, GCP, Micrometer registries) work.

**Tags:** auto-configuration, debugging, conditions-report, conditional, @AutoConfiguration, custom-starter

---

## Q-SPRB-017 [bloom: analyze] [level: senior]
**Question:** A Spring Boot service using `@ConfigurationProperties` binds a password from an environment variable but the value is `null` at runtime despite the env var being set. Walk through the five most likely causes and how to diagnose each.

**Model answer:** The binding chain: env var → `PropertySource` → relaxed binding → POJO field. Any break in the chain produces `null`.

**Cause 1 — Relaxed binding mismatch.**
Env var `APP_DB_PASSWORD` should bind to `app.db.password` via relaxed binding. But if the prefix is `app.datasource` (not `app.db`), there's no match. Fix: confirm the prefix in `@ConfigurationProperties(prefix = "app.datasource")` matches the env var structure. `APP_DATASOURCE_PASSWORD` → `app.datasource.password`.

**Diagnosis:** Add `SPRING_APPLICATION_JSON={"app.datasource.password":"test"}` and confirm binding works, then check what the env var should be named.

**Cause 2 — Bean not registered.**
The `@ConfigurationProperties` class is not a bean. It has neither `@Component` nor is registered via `@EnableConfigurationProperties`. The binding never runs.

**Diagnosis:** `ctx.getBean(DatasourceProperties.class)` throws `NoSuchBeanDefinitionException`.

**Cause 3 — Shadowed by `application.yml`.**
The `application.yml` has `app.datasource.password: ${APP_DB_PASSWORD:}` — note the empty default after `:`. If the env var is set but the YAML processes the property before the env var is loaded (startup ordering issue), it resolves to empty string, not `null`. But with `{}` empty default, the value is empty string — appears non-null but is blank.

**Diagnosis:** Hit `/actuator/env` and look at the `app.datasource.password` property source chain. Check which source "wins".

**Cause 4 — Container-level env var not injected.**
The env var exists in the shell where the developer runs the app but is NOT in the Dockerfile/Kubernetes deployment spec. The JVM process never sees it.

**Diagnosis:** `System.getenv("APP_DB_PASSWORD")` returns `null` inside the app. Check `kubectl describe pod` or `docker inspect` for the env var.

**Cause 5 — Validation failing silently.**
`@NotBlank` constraint on the field causes startup to fail with `BindException` if the property is truly missing — which means the app wouldn't even start. But if validation is not set up (`@Validated` missing on the class), a null field silently passes through. The null is not caught until first use.

**Diagnosis:** Add `@Validated` to the `@ConfigurationProperties` class and `@NotBlank` to the field. Now a missing property causes a clear startup failure with message: `Property 'app.datasource.password' must not be blank`.

**Fix pattern for production:**
```java
@ConfigurationProperties(prefix = "app.datasource")
@Validated
public class DatasourceProperties {
    @NotBlank(message = "app.datasource.password must be set (env: APP_DATASOURCE_PASSWORD)")
    private String password;
}
```

**Interview trap:** "Is `/actuator/env` safe to use for diagnosing this in production?" — `/actuator/env` masks values whose keys contain `password`, `secret`, `key`, `token` by default (replaces with `******`). This is correct security behavior. To diagnose, temporarily enable `management.endpoint.env.show-values: ALWAYS` in a non-production environment, or read the raw env var from the pod shell (`kubectl exec`).

**Tags:** @ConfigurationProperties, binding, null-value, relaxed-binding, diagnosis, @Validated, env-vars

---

## Q-SPRB-018 [bloom: analyze] [level: master]
**Question:** Spring Boot's `DataSourceAutoConfiguration` backs off when you define your own `DataSource` bean via `@ConditionalOnMissingBean`. But you need TWO `DataSource` beans — a primary and a read-replica. Describe the full strategy to achieve this without triggering auto-configuration conflicts, including transaction manager, JPA, and HikariCP pool configuration for each.

**Model answer:** When you define your own `DataSource`, all DataSource-dependent auto-configs back off: `HibernateJpaAutoConfiguration`, `TransactionAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`. You own the entire stack.

**Strategy:**

**1. Primary DataSource (write):**
```java
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.example.repository.write",
    entityManagerFactoryRef = "writeEntityManagerFactory",
    transactionManagerRef = "writeTransactionManager"
)
public class WriteDatabaseConfig {

    @Primary
    @Bean("writeDataSource")
    @ConfigurationProperties(prefix = "app.datasource.write")
    public DataSource writeDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean("writeEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean writeEntityManagerFactory(
            @Qualifier("writeDataSource") DataSource ds,
            JpaProperties jpaProperties) {
        return new EntityManagerFactoryBuilder(
                new HibernateJpaVendorAdapter(), jpaProperties.getProperties(), null)
            .dataSource(ds)
            .packages("com.example.domain")
            .persistenceUnit("write")
            .build();
    }

    @Primary
    @Bean("writeTransactionManager")
    public PlatformTransactionManager writeTransactionManager(
            @Qualifier("writeEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

**2. Read-replica DataSource (read-only):**
```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.repository.read",
    entityManagerFactoryRef = "readEntityManagerFactory",
    transactionManagerRef = "readTransactionManager"
)
public class ReadDatabaseConfig {

    @Bean("readDataSource")
    @ConfigurationProperties(prefix = "app.datasource.read")
    public DataSource readDataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
            .type(HikariDataSource.class).build();
        ds.setReadOnly(true);   // hint to JDBC driver
        return ds;
    }

    @Bean("readEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean readEntityManagerFactory(
            @Qualifier("readDataSource") DataSource ds,
            JpaProperties jpaProperties) {
        // same packages — read-only view of same entities
        return new EntityManagerFactoryBuilder(
                new HibernateJpaVendorAdapter(), jpaProperties.getProperties(), null)
            .dataSource(ds)
            .packages("com.example.domain")
            .persistenceUnit("read")
            .build();
    }

    @Bean("readTransactionManager")
    public PlatformTransactionManager readTransactionManager(
            @Qualifier("readEntityManagerFactory") EntityManagerFactory emf) {
        JpaTransactionManager tm = new JpaTransactionManager(emf);
        tm.setDefaultTimeout(5);   // read operations: short timeout
        return tm;
    }
}
```

**3. Configuration properties:**
```yaml
app:
  datasource:
    write:
      jdbc-url: jdbc:postgresql://write-host:5432/mydb
      username: write_user
      password: ${WRITE_DB_PASSWORD}
      maximum-pool-size: 20
      minimum-idle: 5
    read:
      jdbc-url: jdbc:postgresql://read-replica:5432/mydb
      username: read_user
      password: ${READ_DB_PASSWORD}
      maximum-pool-size: 40    # read traffic often higher
      minimum-idle: 10
      read-only: true
```

**4. Usage at service layer:**
```java
@Transactional("writeTransactionManager")
public Order createOrder(CreateOrderCommand cmd) { ... }

@Transactional(value = "readTransactionManager", readOnly = true)
public List<Order> listOrders(Pageable pageable) { ... }
```

**5. Health indicator for both:**
```java
@Component
public class ReadReplicaHealthIndicator implements HealthIndicator {
    @Qualifier("readDataSource")
    @Autowired private DataSource readDataSource;
    // ... standard JDBC ping
}
```

**Key gotchas:**
- `@Primary` on write beans ensures default `@Autowired DataSource` resolves to write — critical for Liquibase/Flyway which must run against the primary only.
- Flyway/Liquibase: exclude their auto-configuration and configure them explicitly against `writeDataSource` only.
- `@EnableTransactionManagement` only needed once; it's on the primary config.
- Schema migrations must NOT run against the read replica — it's typically read-only at the DB level.

**Interview trap:** "What if a service method is `@Transactional` with the write TM but calls a read repository method?" — The write transaction manager is active; the read repository, if it uses a different `DataSource`, will participate in a different transaction. This is not automatic — Spring does not bridge transaction managers. If you need cross-datasource atomicity, you need XA transactions (JTA) or the Saga pattern. For read-after-write consistency, use the write `EntityManagerFactory` for both operations or accept eventual consistency from the replica.

**Tags:** multi-datasource, @Primary, JPA, transaction-manager, HikariCP, read-replica, auto-configuration

---

## Q-SPRB-019 [bloom: analyze] [level: master]
**Question:** Describe the full auto-configuration ordering and conditional evaluation pipeline for a Spring Boot 3.x application — from class loading through `AutoConfiguration.imports` to final bean registration. Include how `@AutoConfigureBefore`/`@AutoConfigureAfter` work internally and when they can be violated.

**Model answer:** The pipeline has eight distinct phases:

**Phase 1 — `@EnableAutoConfiguration` triggers `AutoConfigurationImportSelector`.**
This is an `ImportSelector` registered via `@Import` inside `@EnableAutoConfiguration`. Spring's `ConfigurationClassParser` calls `selectImports()` during the parsing phase of context refresh (before beans are instantiated).

**Phase 2 — Candidate loading.**
`AutoConfigurationImportSelector.getCandidateConfigurations()` reads:
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
(one fully-qualified class name per line). All JARs on the classpath are scanned. This produces an unordered list of ~140 candidate class names in a stock Boot 3 app.

**Phase 3 — Exclusion filtering.**
`AutoConfigurationImportSelector.getExclusions()` collects:
1. `@SpringBootApplication(exclude = {...})` attribute
2. `spring.autoconfigure.exclude` property
These are removed from the candidate list by class name.

**Phase 4 — `AutoConfigurationImportFilter` filtering (before class loading).**
`AutoConfigurationImportFilter` implementations can reject candidates without loading the class. The built-in filters use a metadata cache (`META-INF/spring-autoconfigure-metadata.properties`) that stores pre-computed condition annotations as strings. `OnClassCondition`, `OnWebApplicationCondition`, `OnBeanCondition` run at this phase. This avoids loading hundreds of classes that would immediately fail their `@ConditionalOnClass` check.

**Phase 5 — Ordering.**
Surviving candidates are sorted by `AutoConfigurationSorter`:
1. `@AutoConfigureOrder` integer value (lower = earlier)
2. `@AutoConfigureBefore` / `@AutoConfigureAfter` — topological sort

`@AutoConfigureBefore(B.class)` means "A must be processed before B". The sorter builds a directed graph and runs a topological sort. **Cycle detection:** if A says before B and B says before A, `IllegalStateException` is thrown at startup.

**Violation case:** `@AutoConfigureBefore`/`@AutoConfigureAfter` only controls ordering within the auto-configuration import batch. If a user-defined `@Configuration` class creates a bean that an auto-config expected to create, the ordering annotation is irrelevant — the user bean is registered during component scan, which runs before auto-config import. This is why `@ConditionalOnMissingBean` is checked at instantiation time, not ordering time.

**Phase 6 — `@Conditional` evaluation (per class).**
Each sorted candidate class is now loaded and its class-level `@Conditional*` annotations evaluated by `ConditionEvaluator`:
- `@ConditionalOnClass` — checks `ClassLoader.loadClass()`, catches `ClassNotFoundException`
- `@ConditionalOnMissingBean` — queries `BeanDefinitionRegistry` for existing beans of the type; beans registered so far (user beans + earlier auto-configs) are visible
- `@ConditionalOnProperty` — reads from `Environment`

Classes that fail are recorded in the `ConditionEvaluationReport` (the `/actuator/conditions` data).

**Phase 7 — `@Bean` method conditional evaluation.**
For classes that pass phase 6, each `@Bean` method's own `@Conditional*` annotations are evaluated individually. `@ConditionalOnMissingBean` at the method level backs off a single bean while other beans in the same config class may still register.

**Phase 8 — Bean instantiation.**
Surviving `@Bean` methods are added to the `BeanDefinitionRegistry` and instantiated in dependency order. `@Lazy` beans are deferred until first use.

**Key invariant:** By the time a `@ConditionalOnMissingBean` is evaluated, all previously registered beans (component-scanned + earlier-sorted auto-configs) are visible. Later-sorted auto-configs' beans are NOT yet registered. This means `@ConditionalOnMissingBean` is order-sensitive — it sees only what has been registered so far.

**Interview trap:** "If you add `@AutoConfigureBefore(DataSourceAutoConfiguration.class)` to your own auto-config, will it fire before the DataSource bean exists?" — Yes, the class fires first. But `@ConditionalOnMissingBean(DataSource.class)` in `DataSourceAutoConfiguration` will still see your auto-config's `DataSource` bean (if you registered one) because beans registered by your auto-config in Phase 7/8 are in the registry. Ordering controls *processing order*, but `@ConditionalOnMissingBean` checks the registry at the time the condition is evaluated, which is during Phase 6/7 when all prior beans are already visible.

**Tags:** auto-configuration, internals, ordering, @AutoConfigureBefore, @AutoConfigureAfter, conditional, pipeline, Boot3

---

## Q-SPRB-020 [bloom: analyze] [level: master]
**Question:** Your production Spring Boot service is generating 50,000 unique Micrometer metric time series per minute, causing Prometheus OOM and Grafana query timeouts. Diagnose the root cause and describe the full remediation strategy including cardinality control at the Micrometer level.

**Model answer:** 50k unique time series per minute means high-cardinality tags are being used. This is the most common Micrometer production failure mode.

**Diagnosis:**

**Step 1 — Identify the cardinality bombs:**
```bash
# Prometheus metrics API — list metric names with highest cardinality
curl -s http://prometheus:9090/api/v1/label/__name__/values | jq -r '.data[]' | while read m; do
  count=$(curl -s "http://prometheus:9090/api/v1/series?match[]=${m}" | jq '.data | length')
  echo "$count $m"
done | sort -rn | head -20
```

Or via Spring Boot itself: `/actuator/metrics` lists all meter names. Cross-reference with Prometheus `/api/v1/label/__name__/values`.

**Step 2 — Find the tag source in code.**
Grep for `MeterRegistry`, `Counter.builder`, `Timer.builder` with dynamic tag values:
```java
// FOUND: this is the bomb
timer.record(duration, TimeUnit.MS,
    Tag.of("endpoint", request.getRequestURI()),   // /users/123, /users/456, /users/789...
    Tag.of("userId", userId));                      // millions of users
```

**Remediation:**

**Fix 1 — Tag normalization (URI templating):**
Spring Boot's `WebMvcMetricsFilter` auto-instruments HTTP requests. By default it uses the URI template (`/users/{id}`), not the actual URI (`/users/123`). Verify this is configured:
```yaml
management:
  metrics:
    web:
      server:
        request:
          autotime:
            enabled: true
```
Custom code must do the same — never use raw request URIs as tag values.

**Fix 2 — `MeterFilter` for cardinality capping:**
```java
@Bean
public MeterFilter cardinalityLimitingFilter() {
    return MeterFilter.maximumAllowableTags(
        "http.server.requests",   // meter name
        "uri",                    // tag name
        100,                      // max 100 unique values
        MeterFilter.deny()        // action when limit exceeded: deny new series
    );
}
```
New time series beyond the limit are silently dropped. Better than OOM-killing Prometheus.

**Fix 3 — Replace high-cardinality tags with low-cardinality bucketing:**
```java
// Before: Tag.of("userId", userId)   — millions of values
// After: Tag.of("userTier", getUserTier(userId))  — FREE, BASIC, PREMIUM

// Before: Tag.of("orderId", orderId)  — millions of values
// After: Tag.of("orderValue", bucketOrderValue(amount))  // "low", "medium", "high"
```

**Fix 4 — Use Micrometer exemplars for trace correlation instead of tags:**
```java
// Instead of Tag.of("traceId", traceId) — unbounded
// Let Micrometer attach an exemplar (one sample per histogram bucket):
Timer.builder("payment.duration")
    .publishPercentileHistogram()  // enables exemplars automatically with Micrometer 1.9+
    .register(registry);
```
Exemplars attach ONE trace ID to a histogram bucket — Prometheus stores this as a sparse annotation, not a new time series. Grafana can jump from a P99 spike to the exact trace.

**Fix 5 — Global deny filter for known high-cardinality meters:**
```java
@Bean
public MeterFilter denyHighCardinalityMeters() {
    return new MeterFilter() {
        @Override
        public MeterFilterReply accept(Meter.Id id) {
            if (id.getName().startsWith("jvm.") || id.getName().startsWith("process.")) {
                return MeterFilterReply.NEUTRAL;
            }
            // Deny any meter with a "userId" tag
            if (id.getTag("userId") != null) {
                return MeterFilterReply.DENY;
            }
            return MeterFilterReply.NEUTRAL;
        }
    };
}
```

**Prometheus tuning (parallel):** Set `storage.tsdb.retention.time=15d`, `--storage.tsdb.max-block-duration=2h` to limit storage growth while cardinality is reduced. Add recording rules to pre-aggregate high-frequency queries.

**Operational target:** Senior engineers know that Prometheus performance degrades non-linearly with cardinality. Under 100k active time series: fast. 100k–1M: manageable with SSD. Over 1M: consider Thanos, Cortex, or VictoriaMetrics for horizontal scaling.

**Interview trap:** "What if the high-cardinality tags are being added by auto-instrumented libraries you don't control?" — Use `MeterFilter.ignoreTags("userId")` to strip specific tags globally from all meters, or `MeterFilter.replaceTagValues("uri", actual -> normalize(actual), "others")` to collapse long-tail values. `MeterFilter` applies universally before any meter is registered.

**Tags:** micrometer, cardinality, prometheus, meter-filter, observability, production, performance, exemplars

---

## Q-SPRB-021 [bloom: analyze] [level: master]
**Question:** Describe how Spring Boot's embedded Tomcat thread pool works, what the key tuning parameters are, and how you would diagnose and remediate a "thread starvation" scenario under high concurrency.

**Model answer:** Spring Boot embeds Tomcat with a `ThreadPoolExecutor`-backed connector. The relevant configuration namespace is `server.tomcat.*`.

**Default thread pool parameters (Boot 3.x / Tomcat 10.x):**

| Property | Default | Meaning |
|---|---|---|
| `server.tomcat.threads.max` | 200 | Max worker threads |
| `server.tomcat.threads.min-spare` | 10 | Min idle threads kept alive |
| `server.tomcat.max-connections` | 8192 | Max simultaneous TCP connections |
| `server.tomcat.accept-count` | 100 | Queue depth for connections waiting when all threads busy |
| `server.tomcat.connection-timeout` | 20000ms | Time to wait for request line/headers |

**Anatomy of a request:**
1. New TCP connection arrives → accepted by Acceptor thread (separate from worker pool) up to `max-connections`.
2. Connection is polled by NIO Poller.
3. When data is available, a worker thread from the pool is assigned.
4. Worker thread runs the Servlet/request handler until response is sent.
5. Thread is returned to pool.

**Thread starvation scenario:**
```
max-connections=8192, threads.max=200
200 requests in flight, all blocked on slow downstream service (avg 5s response time)
→ All 200 threads occupied
→ New connections accepted (up to 8192) but no thread to process them
→ accept-count=100 queue fills up
→ 101st queued connection gets TCP RST → 503 to client
→ Thread dump shows 200 threads in WAITING on HTTP client socket read
```

**Diagnosis:**

```bash
# 1. Thread dump via actuator
GET /actuator/threaddump
# Look for: large numbers of threads named "http-nio-8080-exec-*" in WAITING or BLOCKED state
# Stack trace shows: SocketInputStream.read() or similar blocking I/O

# 2. Metrics
GET /actuator/metrics/tomcat.threads.busy
GET /actuator/metrics/tomcat.threads.current
GET /actuator/metrics/tomcat.connections.current

# 3. JVM via jstack
kubectl exec -it pod-name -- jstack 1 | grep -A 5 "http-nio"
```

**Remediation options:**

**Option A — Increase thread pool (short-term, not scalable):**
```yaml
server:
  tomcat:
    threads:
      max: 400
      min-spare: 20
```
Works until threads exhaust OS resources (~1k-2k is practical max before context switching overhead dominates).

**Option B — Reduce thread hold time (correct fix):**
Identify what's blocking: if it's downstream HTTP calls, switch from `RestTemplate`/synchronous `RestClient` to async calls or reactive (`WebClient`). If it's DB queries, ensure HikariCP pool size is calibrated and queries are fast.

**Option C — Timeout downstream calls:**
```java
RestClient restClient = RestClient.builder()
    .requestFactory(new HttpComponentsClientHttpRequestFactory(
        HttpClients.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(1000))
            .setResponseTimeout(Timeout.ofMilliseconds(3000))
            .build()))
    .build();
```
Threads block for max 3s instead of indefinitely, releasing back to pool sooner.

**Option D — Virtual threads (Boot 3.2+ / Java 21):**
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
Tomcat uses virtual threads (JDK 21 Project Loom) instead of OS threads. Virtual threads are cheap to park on blocking I/O — you can have thousands of them. Thread starvation is effectively eliminated for I/O-bound workloads. Each request gets its own virtual thread; parking on socket read costs ~1KB of memory vs ~1MB for platform threads.

**Caveat with virtual threads:** `synchronized` blocks pin a carrier thread. If your code (or a library like Hibernate) uses `synchronized` with I/O inside, you still get carrier thread exhaustion. Monitor with `-Djdk.tracePinnedThreads=full` flag. Spring Boot 3.2+ includes Hibernate 6.2 which is virtual-thread safe.

**Interview trap:** "If you're already on a reactive stack (WebFlux + Netty), does thread pool starvation apply?" — No. Netty uses an event loop with a small fixed number of threads (default: 2 × CPU cores). Requests are handled via non-blocking I/O callbacks. You don't hold a thread while waiting for I/O — the event loop thread moves to the next event immediately. Thread starvation is a blocking-I/O problem. The tradeoff: reactive code is harder to write, debug, and trace; virtual threads (Loom) offer the same scalability benefit with imperative code style.

**Tags:** tomcat, thread-pool, thread-starvation, virtual-threads, loom, performance, tuning, diagnosis, non-blocking

---

## Q-SPRB-022 [bloom: recall] [level: junior]
**Question:** What does `spring.profiles.active` do, and how do you activate a Spring profile when running a fat JAR in production?

**Model answer:** `spring.profiles.active` tells Spring Boot which profile(s) to activate at startup. An active profile causes profile-specific config files (`application-{profile}.yml`) to be loaded and overlay the base `application.yml`, and enables beans annotated with `@Profile("{profile}")`.

**Ways to activate a profile:**

1. **Environment variable (most common in containers):**
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   java -jar myapp.jar
   ```

2. **JVM system property:**
   ```bash
   java -Dspring.profiles.active=prod -jar myapp.jar
   ```

3. **Command-line argument:**
   ```bash
   java -jar myapp.jar --spring.profiles.active=prod
   ```

4. **Inside `application.yml` (for sub-profiles):**
   ```yaml
   spring:
     profiles:
       active: prod
   ```
   Note: setting this in `application.yml` is overridden by any of the above.

5. **Kubernetes Deployment env var:**
   ```yaml
   env:
     - name: SPRING_PROFILES_ACTIVE
       value: "prod"
   ```

**Multiple profiles** can be comma-separated: `SPRING_PROFILES_ACTIVE=prod,eu-west`. Profile-specific files for all listed profiles are loaded; later profiles in the list override earlier ones.

**Interview trap:** "What happens if `application-prod.yml` sets a property that `application.yml` also sets?" — The profile-specific file has **higher priority** than the base file. The value in `application-prod.yml` wins. Profile-specific files overlay, not replace, the base config — properties absent in the profile-specific file still come from `application.yml`.

**Tags:** profiles, spring.profiles.active, externalized-config, environment-variables, production

---

## Q-SPRB-023 [bloom: recall] [level: junior]
**Question:** What is the purpose of `spring-boot-devtools`, and what are its three key features? Should it be included in a production build?

**Model answer:** `spring-boot-devtools` is a development-time convenience dependency that improves the inner dev loop. It should **never** be active in production — Spring Boot automatically disables it when running from a fully packaged JAR (fat JAR) or when the classpath does not contain devtools in an exploded form.

**Three key features:**

1. **Automatic restart.** DevTools monitors the classpath. When a class file changes (e.g., after a `mvn compile` or IDE auto-compile), it restarts the `ApplicationContext` using a *restart classloader*. Restart is fast because only application classes are reloaded; Spring's framework classes stay in the base classloader. Restart time is typically 1–5 seconds vs a full JVM restart of 20–30 seconds.

2. **LiveReload.** Embeds a LiveReload server on port 35729. Browser extensions connect to it; when static resources (HTML, CSS, JS) change, the browser tab refreshes automatically without any manual reload.

3. **Property defaults.** DevTools sets several development-friendly defaults: disables Thymeleaf template caching (`spring.thymeleaf.cache=false`), enables `debug` logging for key Spring classes, disables HTTP response caching. These are overridden by explicit config in `application.yml`.

**Why not in production:**
- Monitoring classpath changes adds CPU overhead.
- Automatic restarts are dangerous — a deployment artifact change could cause mid-request restarts.
- LiveReload server exposes an unnecessary network port.

**How to exclude from production build (Maven):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>  <!-- not included in downstream JARs -->
</dependency>
```
`<optional>true</optional>` means the dependency is excluded when the JAR is used as a dependency by another project, and the Maven plugin's repackage goal excludes it from the fat JAR.

**Interview trap:** "Can DevTools restart survive database schema changes?" — No. A `DataSource` bean holds a connection pool configured at startup. Devtools restart reuses the base classloader, so the `DataSource` pool persists across restarts. If a schema migration is needed, a full JVM restart is required, or the dev uses an in-memory H2 DB that recreates on every restart.

**Tags:** devtools, developer-experience, hot-reload, livereload, restart-classloader, development

---

## Q-SPRB-024 [bloom: understand] [level: regular]
**Question:** Explain the difference between `@SpringBootTest` with no `webEnvironment` and with `webEnvironment = RANDOM_PORT`. What does each spin up, and when would you use each?

**Model answer:** `@SpringBootTest` loads a full `ApplicationContext`. The `webEnvironment` attribute controls whether and how the embedded web server is started.

| `webEnvironment` | What starts | Use case |
|---|---|---|
| `MOCK` (default) | `MockMvc`/`MockWebTestClient`-backed mock servlet environment; no real port | Integration tests that don't need real HTTP. Fastest. No network I/O. |
| `RANDOM_PORT` | Full embedded server on a random available port | End-to-end HTTP tests using `TestRestTemplate` or `WebTestClient`. Needed when testing filters, interceptors, or HTTP-level behavior. |
| `DEFINED_PORT` | Full server on `server.port` (default 8080) | Rarely used; risks port conflicts in CI. |
| `NONE` | No web environment at all | Testing service/repository layers that have no web dependency. Fastest context load. |

**`MOCK` example:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerMockTest {
    @Autowired MockMvc mockMvc;

    @Test
    void shouldReturnOrders() throws Exception {
        mockMvc.perform(get("/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
```

**`RANDOM_PORT` example:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldReturnOrders() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

**When `RANDOM_PORT` is required:**
- Testing Spring Security filter chain behavior (HTTP headers, redirect responses, 401/403).
- Testing filters or `OncePerRequestFilter` implementations.
- Testing gzip compression, CORS headers, HTTP/2.
- Testing WebSocket endpoints.
- Testing actual Servlet API behavior (request/response streaming).

**Performance note:** `RANDOM_PORT` starts the full Tomcat — significantly slower test startup than `MOCK`. Use `@DirtiesContext` sparingly; reuse the application context across test classes when possible.

**Interview trap:** "Can you test Spring Security with `MOCK` environment?" — Yes, using `@WithMockUser` and `MockMvc` with Spring Security's test support. `MockMvc` processes the full filter chain including `SecurityFilterChain`. You only need `RANDOM_PORT` when you must test HTTP-level redirects (OAuth2 redirects) or things that `MockMvc` abstracts away (actual SSL/TLS behavior).

**Tags:** testing, @SpringBootTest, webEnvironment, MockMvc, TestRestTemplate, integration-test, RANDOM_PORT

---

## Q-SPRB-025 [bloom: understand] [level: regular]
**Question:** What is Spring Boot's `@Conditional` mechanism, and how do you implement a custom condition? Give a concrete example of a custom condition that activates a bean only when running inside a Docker container.

**Model answer:** `@Conditional` is a meta-annotation mechanism that lets Spring defer bean registration decisions to a `Condition` implementation. At context startup, `ConditionEvaluator` calls `Condition.matches(ConditionContext, AnnotatedTypeMetadata)` for each conditional bean; if `matches()` returns false, the bean definition is not added to the registry.

**Built-in conditions** (`@ConditionalOn*`) all implement `Condition` internally (usually `SpringBootCondition`). You can write your own.

**Custom condition — active only inside Docker:**

Docker containers have `/.dockerenv` file present (or the cgroup identifies Docker). A simple file-check condition:

```java
public class DockerEnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Docker containers have /.dockerenv present
        return new java.io.File("/.dockerenv").exists();
    }
}
```

**Custom annotation wrapping the condition:**
```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(DockerEnvironmentCondition.class)
public @interface ConditionalOnDocker {}
```

**Usage:**
```java
@Configuration
@ConditionalOnDocker
public class DockerMetricsConfiguration {

    @Bean
    public ContainerMetricsCollector containerMetricsCollector() {
        // Only registered when running in Docker
        return new ContainerMetricsCollector("/sys/fs/cgroup");
    }
}
```

**`ConditionContext` provides access to:**
- `BeanDefinitionRegistry` — inspect already-registered beans
- `ConfigurableListableBeanFactory` — access bean factory
- `Environment` — read properties and profiles
- `ResourceLoader` — load resources (files, classpath entries)
- `ClassLoader` — check class availability

**More robust Docker detection using cgroup:**
```java
@Override
public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    // Check environment variable set by docker/k8s
    String containerEnv = System.getenv("CONTAINER");
    if ("true".equalsIgnoreCase(containerEnv)) return true;
    // Fall back to /.dockerenv file
    return context.getResourceLoader().getResource("file:/.dockerenv").exists();
}
```

**Interview trap:** "When is `Condition.matches()` called — at startup or lazily?" — At startup, during context refresh, in the `ConfigurationClassParser` phase. All conditions are evaluated eagerly before any beans are instantiated (except for `@Lazy` beans). This means `matches()` cannot rely on other beans being available — only on classpath, properties, and environment.

**Tags:** @Conditional, custom-condition, Condition, ConditionContext, auto-configuration, Docker, bean-registration
