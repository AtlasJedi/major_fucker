# Spring Web / REST — question bank

> Spring Web MVC is the primary HTTP layer in every Spring Boot backend. For a senior Java engineer, this means understanding more than annotations — you must know the DispatcherServlet lifecycle, the HttpMessageConverter pipeline, how exception handling propagates through three distinct layers, and how the framework's abstractions (filters, interceptors, AOP) differ in what they can and cannot see. This bank drills from basic annotation usage (junior) through content negotiation and validation groups (regular) to filter ordering gotchas and async MVC (senior) and into WebMvc vs WebFlux architectural tradeoffs (master). The Coforge role is a Java 17 / Spring Boot 3.x microservices backend — questions are calibrated to that stack.

## Scope

- DispatcherServlet request lifecycle: handler mapping, handler adapter, argument resolvers, message converters, view resolver
- @RestController vs @Controller — annotation composition, @ResponseBody effect
- Mapping annotations: @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @PatchMapping, @DeleteMapping
- @PathVariable, @RequestParam, @RequestBody — binding rules, optional/default values
- HttpMessageConverter pipeline: content negotiation, Jackson integration, produces/consumes
- ResponseEntity — when to use vs bare return, building Location headers, 201/204/304
- Exception handling: @ExceptionHandler, @ControllerAdvice, ResponseStatusException, ProblemDetail (RFC 7807), Spring Boot 3 built-in support
- Bean Validation (JSR-380 / Jakarta): @Valid, @Validated, constraint annotations, groups, sequences, custom validators, BindingResult
- Servlet Filters vs HandlerInterceptor vs AOP — layer position, what each sees, ordering, OncePerRequestFilter
- CORS: preflight mechanics, @CrossOrigin, WebMvcConfigurer, Spring Security integration
- Async MVC: Callable, DeferredResult, WebAsyncTask — thread model, timeout handling
- HTTP caching: ETag, Last-Modified, If-None-Match, Cache-Control, ResponseEntity helpers
- Multipart file upload: MultipartFile, streaming to object storage, size limits
- Spring MVC vs Spring WebFlux — threading model, backpressure, migration triggers

---

## Q-SPRW-001 [bloom: recall] [level: junior]
**Question:** What does `@RestController` actually do, and how does it differ from `@Controller`?
**Model answer:** `@RestController` is a composed annotation: `@Controller` + `@ResponseBody` applied to every handler method in the class. The effect of `@ResponseBody` is to tell Spring MVC to skip the `ViewResolver` chain and instead serialize the method's return value directly to the HTTP response body through an `HttpMessageConverter` (Jackson by default for JSON). With plain `@Controller`, Spring assumes the return value is a view name (a String to be resolved by Thymeleaf, FreeMarker, etc.) unless individual methods are annotated with `@ResponseBody`.

```java
@RestController                      // = @Controller + @ResponseBody on every method
@RequestMapping("/api/orders")
public class OrderController {
    @GetMapping("/{id}")
    public OrderDto get(@PathVariable Long id) { ... }  // return value → JSON, not view name
}
```

Use `@Controller` only when a single class must serve both MVC views and REST endpoints (rare in microservices). For any pure JSON API, `@RestController` is the right choice.

**Interview trap:** "What converts the return value to JSON?" — the annotation itself does not. It merely signals that no view resolution is needed. The `MappingJackson2HttpMessageConverter` (registered automatically by `spring-boot-starter-web`) does the actual serialization. The annotation bypasses the view resolver; Jackson does the work.
**Tags:** spring-mvc, annotations, restcontroller, httpmessageconverter

---

## Q-SPRW-002 [bloom: recall] [level: junior]
**Question:** What are the Spring MVC mapping annotations and what HTTP methods do they map to?
**Model answer:** All mapping annotations are composed shortcuts for `@RequestMapping(method = RequestMethod.X)`:

| Annotation | HTTP method | Idempotent | Safe | Typical use |
|---|---|---|---|---|
| `@GetMapping` | GET | Yes | Yes | Read resource |
| `@PostMapping` | POST | No | No | Create resource / trigger action |
| `@PutMapping` | PUT | Yes | No | Full replace of resource |
| `@PatchMapping` | PATCH | No* | No | Partial update |
| `@DeleteMapping` | DELETE | Yes | No | Delete resource |

*PATCH is idempotent only if designed that way (set-semantics, not increment-semantics).

`@RequestMapping` at class level sets the base path; method-level annotations add to it:

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    @GetMapping("/{id}")             // → GET /api/v1/products/{id}
    public ProductDto get(...) { ... }

    @PostMapping                     // → POST /api/v1/products
    public ResponseEntity<ProductDto> create(...) { ... }
}
```

**Interview trap:** PUT replaces the entire resource. If the client omits a field in a PUT body, it should be nulled/defaulted on the server — not preserved. PATCH updates only the sent fields. This distinction is probed frequently. If your PUT endpoint merges (preserves unsent fields), it technically behaves like a PATCH.
**Tags:** spring-mvc, annotations, mapping, http-methods

---

## Q-SPRW-003 [bloom: recall] [level: junior]
**Question:** Explain `@PathVariable`, `@RequestParam`, and `@RequestBody` — when do you use each?
**Model answer:**
- **`@PathVariable`** — binds a URI template variable. Used for resource identity: `GET /orders/{id}` → `@PathVariable Long id`. Value is extracted from the path segment. Required by default; can be optional with `required=false`.
- **`@RequestParam`** — binds a query string parameter. Used for filtering, sorting, pagination: `GET /orders?status=OPEN&page=0`. Can be optional with `required=false, defaultValue="..."`. Spring auto-converts types (String → int, enum, etc.).
- **`@RequestBody`** — deserializes the entire HTTP request body via `HttpMessageConverter`. Used for POST/PUT/PATCH payloads. Requires `Content-Type` to match a registered converter (e.g. `application/json` → Jackson). Pair with `@Valid` to trigger Bean Validation.

```java
@GetMapping("/{id}")
public OrderDto get(@PathVariable Long id) { ... }

@GetMapping
public Page<OrderDto> list(
    @RequestParam(defaultValue = "OPEN") String status,
    Pageable pageable) { ... }

@PostMapping
public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest req) { ... }
```

**Interview trap:** For complex filter objects with many optional query params, use a dedicated filter DTO bound via `@ModelAttribute` rather than 10 individual `@RequestParam`s — cleaner and easier to extend. Also: `@RequestBody` reads the stream once; if a filter already consumed it (e.g. for logging), the body will be empty. Wrap with `ContentCachingRequestWrapper` in that case.
**Tags:** spring-mvc, binding, pathvariable, requestparam, requestbody

---

## Q-SPRW-004 [bloom: recall] [level: junior]
**Question:** What is `ResponseEntity<T>` and when should you use it instead of returning a bare DTO?
**Model answer:** `ResponseEntity<T>` is Spring MVC's representation of a complete HTTP response — status code, headers, and body in one object. It gives the handler method full control over all three.

Use `ResponseEntity` when:
1. Creating a resource: return `201 Created` + `Location` header pointing to the new resource URL.
2. Empty response: return `204 No Content` (DELETE, PUT-with-no-body).
3. Conditional response: return `304 Not Modified` with ETag logic.
4. Setting custom response headers (e.g. `X-Correlation-Id`, pagination `Link`).

```java
@PostMapping
public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest req) {
    OrderDto dto = orderService.create(req);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}").buildAndExpand(dto.id()).toUri();
    return ResponseEntity.created(location).body(dto);
}
```

For simple `200 OK` GETs, returning the DTO directly is cleaner and perfectly valid — Spring defaults to 200.

**Interview trap:** `ResponseEntity.ok(dto)` sets status 200. `ResponseEntity.created(uri).body(dto)` sets 201 and the Location header. Forgetting the Location header on a 201 response violates the HTTP spec and is a common mistake interviewers probe.
**Tags:** spring-mvc, responseentity, http-status, location-header

---

## Q-SPRW-005 [bloom: recall] [level: junior]
**Question:** What is `@ControllerAdvice` and what is it used for?
**Model answer:** `@ControllerAdvice` is a specialization of `@Component` that acts as a cross-cutting interceptor for Spring MVC controllers. It applies globally to all controllers in the application context (or to a subset via `basePackages`, `assignableTypes`, or annotation filters). The three things you put inside it:

1. **`@ExceptionHandler`** — maps exception types to handler methods that return custom responses.
2. **`@ModelAttribute`** — pre-populates model data for every request (rare in REST APIs).
3. **`@InitBinder`** — customizes data binding/conversion globally.

For REST APIs, `@ControllerAdvice` is almost exclusively used for centralized exception handling:

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(OrderNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order Not Found");
        return ResponseEntity.status(404).body(pd);
    }
}
```

**Interview trap:** `@ControllerAdvice` only handles exceptions that reach the `DispatcherServlet`. Exceptions thrown in a Servlet Filter (which runs before the DispatcherServlet) are **not** caught by `@ControllerAdvice`. You must handle them in the filter itself or in a dedicated error filter.
**Tags:** spring-mvc, controlleradvice, exception-handling, global

---

## Q-SPRW-006 [bloom: recall] [level: junior]
**Question:** What annotations does Spring/Jakarta Bean Validation provide for constraining request fields? How do you trigger validation on a `@RequestBody`?
**Model answer:** The JSR-380 (Jakarta Bean Validation 3.x) specification — implemented by Hibernate Validator — provides a rich set of constraint annotations:

| Annotation | Purpose |
|---|---|
| `@NotNull` | Field must not be null |
| `@NotBlank` | String must not be null or whitespace |
| `@Size(min, max)` | String/collection length bounds |
| `@Min(value)` / `@Max(value)` | Numeric bounds |
| `@Positive` / `@PositiveOrZero` | Numeric positivity |
| `@Pattern(regexp)` | Regex match |
| `@Email` | Valid email format |
| `@Future` / `@Past` | Date constraints |

Trigger validation by adding `@Valid` on the `@RequestBody` parameter:

```java
public record CreateOrderRequest(
    @NotBlank String productCode,
    @Positive @NotNull BigDecimal amount,
    @Valid @NotNull AddressDto shippingAddress   // @Valid cascades to nested object
) {}

@PostMapping
public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest req) { ... }
```

Constraint violations throw `MethodArgumentNotValidException`, which Spring maps to `400 Bad Request` by default.

**Interview trap:** `@Valid` (from `jakarta.validation`) does NOT support validation groups. `@Validated` (Spring-specific, applied to the class or method) does. If the interviewer asks "how do you apply different rules for create vs update?" — that's groups, and you need `@Validated` with group interfaces.
**Tags:** validation, bean-validation, jsr380, valid, validated

---

## Q-SPRW-007 [bloom: recall] [level: junior]
**Question:** What does a Spring MVC Servlet Filter do, and how does it differ from a `HandlerInterceptor`?
**Model answer:** Both are extension points in the request pipeline, but they operate at different layers:

| Mechanism | Layer | Has access to | Use for |
|---|---|---|---|
| **Servlet Filter** | Servlet container (before DispatcherServlet) | Raw `HttpServletRequest` / `HttpServletResponse` | Auth, logging, rate limiting, CORS, compression |
| **HandlerInterceptor** | Spring DispatcherServlet (after mapping, before controller) | Handler method object, `ModelAndView` | Pre/post business logic, locale, audit |

Full ordering: `Filter → DispatcherServlet → HandlerInterceptor.preHandle → Controller → HandlerInterceptor.postHandle → View → HandlerInterceptor.afterCompletion`

`OncePerRequestFilter` is the standard base class for filters — guarantees the filter executes exactly once per request even in forward/include dispatches.

**Interview trap:** `HandlerInterceptor.postHandle` is called after the controller returns but before the response is written. `afterCompletion` is called after the response is written — even if an exception was thrown. Exception thrown in a filter bypasses `@ControllerAdvice` entirely.
**Tags:** spring-mvc, filter, interceptor, request-pipeline, onceperequest

---

## Q-SPRW-008 [bloom: understand] [level: regular]
**Question:** Walk through the DispatcherServlet request lifecycle — what happens between the HTTP request arriving at the servlet container and the JSON response leaving?
**Model answer:** The DispatcherServlet is the Front Controller for Spring MVC. Full lifecycle:

1. **Servlet container** receives the HTTP request. Servlet Filters run in chain order (authentication, logging, etc.).
2. **DispatcherServlet.doDispatch()** is called.
3. **Handler mapping** (`RequestMappingHandlerMapping` for annotation-based controllers): iterates registered mappings to find a `HandlerExecutionChain` — the matching controller method plus any interceptors. Returns 404 if nothing matches.
4. **Handler adapter** lookup (`RequestMappingHandlerAdapter`): adapts the generic `Handler` interface to the specific invocation strategy.
5. **Interceptor preHandle()** chain. If any returns `false`, processing stops.
6. **Argument resolvers** populate method parameters: `@PathVariable` from URI, `@RequestParam` from query string, `@RequestBody` from stream via `HttpMessageConverter`, `@Valid` triggers Bean Validation, etc.
7. **Controller method** executes, returns a value (DTO, `ResponseEntity`, `Callable`, `DeferredResult`, etc.).
8. **Return value handler**: if `@ResponseBody` (or `@RestController`), invokes `HttpMessageConverter.write()` to serialize the return value. If returning a view name, passes to `ViewResolver`.
9. **Interceptor postHandle()** (after controller, before response write).
10. **Response committed** — Jackson writes JSON bytes.
11. **Interceptor afterCompletion()** — always called, even on exception.
12. **Exception handling**: if any exception propagated, `HandlerExceptionResolver` chain is consulted — `ExceptionHandlerExceptionResolver` handles `@ExceptionHandler` methods in `@ControllerAdvice`.

**Interview trap:** "Where does content negotiation happen?" — in step 8, when `RequestMappingHandlerAdapter` iterates `HttpMessageConverter` list and picks the first that can write the return type AND whose `mediaType` matches the request's `Accept` header. If nothing matches, it returns `406 Not Acceptable`.
**Tags:** spring-mvc, dispatcher-servlet, lifecycle, handler-mapping, handler-adapter, message-converter

---

## Q-SPRW-009 [bloom: understand] [level: regular]
**Question:** How does content negotiation work in Spring MVC? What happens when a client sends `Accept: application/xml` to an endpoint that only has Jackson on the classpath?
**Model answer:** Content negotiation is the process by which Spring MVC selects which `HttpMessageConverter` to use for the response, based on the request's `Accept` header and the return type of the handler method.

The `RequestMappingHandlerAdapter` iterates the registered converters in order and picks the first that:
1. Can write the return type (e.g. `OrderDto`)
2. Supports a media type compatible with the client's `Accept` header

Default converters registered by `spring-boot-starter-web`:
- `ByteArrayHttpMessageConverter` — `application/octet-stream`
- `StringHttpMessageConverter` — `text/plain`, `text/*`
- `MappingJackson2HttpMessageConverter` — `application/json`, `application/*+json`
- `FormHttpMessageConverter` — `application/x-www-form-urlencoded`
- (others for primitives, resources, etc.)

**If the client sends `Accept: application/xml`** and only Jackson (no `jackson-dataformat-xml`) is present: no converter can handle XML. Spring returns `406 Not Acceptable`.

**Adding XML support:** add `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` to the classpath — Spring Boot auto-configures `MappingJackson2XmlHttpMessageConverter` and the same endpoint now serves both JSON and XML based on `Accept`.

You can restrict what an endpoint produces:
```java
@GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
```
This returns `406` if the client asks for XML even if a converter exists, making the contract explicit.

**Interview trap:** `Content-Type` (request header) governs which converter is used to READ the request body (`@RequestBody`). `Accept` governs which converter WRITES the response. Swapping these up is a common confusion. A `415 Unsupported Media Type` means the request's `Content-Type` doesn't match any registered reader.
**Tags:** content-negotiation, httpmessageconverter, jackson, accept-header, 406, 415

---

## Q-SPRW-010 [bloom: understand] [level: regular]
**Question:** Explain RFC 7807 `ProblemDetail` and how Spring Boot 3 supports it out of the box.
**Model answer:** RFC 7807 "Problem Details for HTTP APIs" is a standard for machine-readable error responses. A `ProblemDetail` has five standardized fields:

| Field | Type | Meaning |
|---|---|---|
| `type` | URI | Identifies the error class (link to docs). Defaults to `about:blank`. |
| `title` | String | Short summary of the problem type |
| `status` | int | HTTP status code |
| `detail` | String | Human-readable explanation for this specific occurrence |
| `instance` | URI | URI of the specific request that triggered the error (e.g. request path) |

Additional properties can be added via `setProperty(key, value)`.

**Spring Boot 3 support:** Set `spring.mvc.problemdetails.enabled=true` — Spring's `DefaultHandlerExceptionResolver` and `ResponseEntityExceptionHandler` automatically produce `ProblemDetail` bodies for built-in exceptions (validation failures, 405, 415, etc.).

Custom handler:
```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Order Not Found");
        pd.setType(URI.create("https://api.example.com/errors/not-found"));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("orderId", ex.getOrderId());
        return ResponseEntity.status(404).body(pd);
    }
}
```

Wire `Content-Type: application/problem+json` in the response for strict RFC compliance.

**Interview trap:** `ResponseStatusException` is a simpler alternative for ad-hoc status + message without a custom exception class: `throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + id + " not found")`. It bubbles to Spring's built-in handler. But it leaks internal message to the client directly, making it less suitable for production error contracts — `ProblemDetail` with controlled fields is cleaner.
**Tags:** exception-handling, problem-detail, rfc-7807, controlleradvice, spring-boot-3

---

## Q-SPRW-011 [bloom: understand] [level: regular]
**Question:** How does `@Valid` differ from `@Validated` in Spring? When do you need validation groups?
**Model answer:** Both trigger Bean Validation, but they have different capabilities:

| Feature | `@Valid` (Jakarta) | `@Validated` (Spring) |
|---|---|---|
| Origin | `jakarta.validation.Valid` | `org.springframework.validation.annotation.Validated` |
| Validation groups | **No** | **Yes** |
| Cascading (nested objects) | Yes | Yes (same behavior) |
| Method-level validation on beans | Only via Spring wrapping | Native support |

**Validation groups** let you apply different constraint rules for different operations. Typical use case: `productCode` is required on create but optional on update.

```java
public interface OnCreate {}
public interface OnUpdate {}

public record ProductRequest(
    @NotBlank(groups = OnCreate.class)  String productCode,
    @NotNull  BigDecimal price
) {}

@RestController
@Validated                              // enables group-aware validation at class level
public class ProductController {

    @PostMapping
    public ResponseEntity<ProductDto> create(
        @Validated(OnCreate.class) @RequestBody ProductRequest req) { ... }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> update(@PathVariable Long id,
        @Validated(OnUpdate.class) @RequestBody ProductRequest req) { ... }
}
```

Without `groups`, use `@Valid` — it's simpler. Add `@Validated` with groups only when create/update constraints genuinely differ.

**BindingResult:** If you add `BindingResult` as the parameter immediately after `@Valid @RequestBody`, Spring will NOT throw `MethodArgumentNotValidException` automatically — it places errors into `BindingResult` for manual handling. Useful when you want to collect all errors and return a custom response in the same method rather than delegating to `@ControllerAdvice`.

**Interview trap:** Forgetting `@Valid` on a nested field means the nested object's constraints are never evaluated (no cascading). Forgetting `@Validated` at the class level when using groups means groups are silently ignored.
**Tags:** validation, valid, validated, groups, bindingresult, jsr380

---

## Q-SPRW-012 [bloom: understand] [level: regular]
**Question:** How does CORS work in Spring MVC? What is the difference between `@CrossOrigin`, `WebMvcConfigurer`, and Spring Security CORS configuration?
**Model answer:** CORS is a browser security policy — by default, JavaScript from `app.example.com` cannot fetch `api.example.com` (different origin). The server must opt-in by returning `Access-Control-Allow-*` headers.

**Preflight flow:** For non-simple requests (PUT, DELETE, custom headers, `Content-Type: application/json`), the browser sends an `OPTIONS` request first with `Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers`. The server must respond with the allowed methods/headers; only then does the browser send the real request.

**Spring options:**

1. **`@CrossOrigin` on controller/method** — per-endpoint, fine for prototype work:
   ```java
   @CrossOrigin(origins = "https://frontend.example.com", maxAge = 3600)
   @RestController
   public class OrderController { ... }
   ```

2. **`WebMvcConfigurer.addCorsMappings`** — global, preferred for production:
   ```java
   @Override
   public void addCorsMappings(CorsRegistry registry) {
       registry.addMapping("/api/**")
           .allowedOrigins("https://frontend.example.com")
           .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
           .allowCredentials(true)
           .maxAge(3600);
   }
   ```

3. **Spring Security CORS config** — if Spring Security is on the classpath, its filter chain runs BEFORE the DispatcherServlet. MVC CORS configuration is therefore invisible to Security. You must configure CORS through Security:
   ```java
   http.cors(cors -> cors.configurationSource(corsConfigurationSource));
   ```
   Failing to do this means preflight OPTIONS requests are rejected with 403 before MVC ever sees them.

**Interview trap:** CORS is browser enforcement only. `curl`, Postman, and server-to-server calls are not subject to CORS. Never treat CORS as a security mechanism — it cannot replace authentication.
**Tags:** cors, crossorigin, webmvcconfigurer, spring-security, preflight

---

## Q-SPRW-013 [bloom: understand] [level: regular]
**Question:** How does Spring MVC handle multipart file uploads? What are the key configuration knobs and how should large files (50 MB+) be handled?
**Model answer:** Spring MVC resolves `multipart/form-data` requests via a `MultipartResolver` (configured automatically by Spring Boot). Handler methods bind the file with `@RequestParam("file") MultipartFile file` or `@RequestPart`.

```java
@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<JobStatus> importPricelist(
    @RequestParam("file") MultipartFile file) {
    ...
}
```

**Configuration knobs** (`spring.servlet.multipart.*`):
- `max-file-size` (default `1MB`) — max single file
- `max-request-size` (default `10MB`) — max total request
- `file-size-threshold` — above this, Spring writes to a temp file instead of memory
- `location` — temp directory for buffered files

**Large files (50 MB+) — don't buffer in application memory:**
1. **Stream directly to object storage** (S3/GCS): pipe `MultipartFile.getInputStream()` into a multipart upload SDK call.
2. **Better: presigned URL pattern** — client calls `POST /import/upload-url`, server returns a presigned S3 URL. Client uploads directly to S3, server receives an S3 event notification (via SNS/SQS) and triggers processing. Server never sees the bytes.
3. **Always return 202 Accepted + job ID** for large file processing — parsing 50 MB CSV can take minutes. A synchronous approach will hit HTTP timeouts and OOM.

```
POST /api/v1/pricelist/import
→ 202 Accepted
  Location: /api/v1/jobs/abc123
  { "job_id": "abc123", "status": "processing" }

GET /api/v1/jobs/abc123
→ { "status": "completed", "total": 10000, "errors": 2 }
```

**Interview trap:** Using `MultipartFile.getBytes()` on a 50 MB file loads the entire file into the JVM heap — instant OOM on high concurrency. Always stream. Also: default `max-file-size=1MB` will silently reject the upload with `413 Payload Too Large` — operators forget to tune it in production.
**Tags:** multipart, file-upload, streaming, s3, async, job-pattern

---

## Q-SPRW-014 [bloom: understand] [level: regular]
**Question:** How do Servlet Filters, `HandlerInterceptor`, and AOP advice differ in what they can and cannot do? Give a concrete use case for each.
**Model answer:** Three extension mechanisms at different layers:

```
HTTP Request
    ↓
[Servlet Container]
    → Filter chain (auth, CORS, compression, rate limiting)
        ↓
[DispatcherServlet]
    → HandlerMapping
    → HandlerInterceptor.preHandle()
        ↓
    [AOP proxy around the @Service bean]
        → @Before advice (transaction, security check, audit)
    [Controller method]
    [Service method + AOP @After / @AfterReturning]
        ↓
    → HandlerInterceptor.postHandle()
    → View / MessageConverter
    → HandlerInterceptor.afterCompletion()
```

| Mechanism | Knows about Spring handler? | Can abort request? | Sees response body? | Use case |
|---|---|---|---|---|
| **Servlet Filter** | No — raw bytes | Yes (`chain.doFilter` not called) | Yes (via wrapper) | JWT auth, rate limiting, request/response logging |
| **HandlerInterceptor** | Yes — handler method, ModelAndView | Yes (`preHandle` returns false) | No (body not written yet in postHandle) | Locale resolution, per-handler audit, timing |
| **AOP @Around** | No — targets beans, not HTTP | Yes (throw exception) | No — sees method args/return | `@Transactional`, `@Cacheable`, method-level security |

**Concrete examples:**
- Filter: `OncePerRequestFilter` that parses JWT from `Authorization` header and populates `SecurityContextHolder`.
- HandlerInterceptor: `preHandle` checks `X-Correlation-Id` header and puts it in MDC for structured logging; `afterCompletion` clears MDC.
- AOP: `@Around` advice that logs execution time for all `@Service` methods using `@annotation` pointcut.

**Interview trap:** `HandlerInterceptor.postHandle()` is NOT called when the controller throws an exception — the exception exits the flow before postHandle. `afterCompletion` IS called regardless. If you need cleanup that must always run (MDC clear, resource release), use `afterCompletion` or a Filter's `finally` block.
**Tags:** filter, interceptor, aop, request-pipeline, spring-mvc

---

## Q-SPRW-015 [bloom: apply] [level: senior]
**Question:** Describe how the `HttpMessageConverter` pipeline selects a converter for reading a `@RequestBody`. What happens under the hood when Jackson deserializes the body, and what can go wrong?
**Model answer:** **Reading pipeline (request → Java object):**

1. `RequestMappingHandlerAdapter` delegates to an `HttpEntityMethodProcessor` (or `RequestResponseBodyMethodProcessor`).
2. It iterates the registered `HttpMessageConverter` list in registration order.
3. For each converter, checks: `canRead(Class<?> clazz, MediaType contentType)` — does this converter support both the target Java type AND the request's `Content-Type`?
4. First match wins: `converter.read(clazz, request)` is called, consuming the request `InputStream`.
5. If no converter matches: `HttpMediaTypeNotSupportedException` → `415 Unsupported Media Type`.

**Jackson (`MappingJackson2HttpMessageConverter`) internals:**
- Calls `ObjectMapper.readValue(InputStream, Class<T>)`.
- Tokens are streamed via `JsonParser` — Jackson never loads the full string; it streams.
- Property binding via `BeanDeserializer`: by default looks for matching field names, respects `@JsonProperty`, `@JsonAlias`.
- Unknown properties: by default Jackson IGNORES them (`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` in Spring Boot auto-config). You can re-enable strictness via `spring.jackson.deserialization.fail-on-unknown-properties=true`.
- Null handling: missing JSON field → Java field keeps its default value. `null` JSON value → Java `null`.

**What can go wrong:**
- **Stream consumed twice** — a filter reads the body for logging without caching. Jackson reads an empty stream. Fix: wrap with `ContentCachingRequestWrapper`.
- **Type mismatch** — client sends string `"abc"` for a numeric field: `InvalidFormatException` → 400. But only if Spring Boot's default exception handler maps it. Otherwise, a 500 if you have no `@ControllerAdvice` for `HttpMessageNotReadableException`.
- **ObjectMapper shared state** — Spring Boot creates a single shared `ObjectMapper` bean. Reconfiguring it mid-request in a filter (don't do this) causes race conditions.
- **Large bodies** — Jackson streams, so a 100 MB JSON blob will be fully deserialized into a heap object. Set `spring.servlet.multipart.max-request-size` and apply body size limits in the filter or API gateway.

**Interview trap:** "Does `@RequestBody` fail if a `@NotNull` field is missing from the JSON?" — depends. If the field is not in the JSON, Jackson leaves it `null`. `@NotNull` violation is caught by Bean Validation (`@Valid`) _after_ deserialization. If `@Valid` is not on the parameter, the `null` silently passes through to the service. Two separate steps: deserialization then validation.
**Tags:** httpmessageconverter, jackson, requestbody, objectmapper, deserialization

---

## Q-SPRW-016 [bloom: apply] [level: senior]
**Question:** How do you implement HTTP caching (ETag + `Cache-Control`) for a GET endpoint in Spring MVC? Show the full lifecycle including conditional requests.
**Model answer:** Spring MVC supports HTTP conditional requests via `ShallowEtagHeaderFilter` (simple, automatic) or manual `ResponseEntity` with `ETag` (full control).

**Manual approach (full control):**

```java
@GetMapping("/{id}")
public ResponseEntity<ProductDto> get(
        @PathVariable Long id,
        WebRequest webRequest) {

    Product product = productService.findById(id);
    String etag = "\"" + product.getVersion() + "\"";  // strong ETag, must be quoted

    // Conditional GET — returns 304 if ETag matches
    if (webRequest.checkNotModified(etag)) {
        return null;  // Spring writes 304 with no body
    }

    return ResponseEntity.ok()
        .eTag(etag)
        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES)
            .cachePublic()
            .staleWhileRevalidate(60, TimeUnit.SECONDS))
        .lastModified(product.getUpdatedAt())
        .body(productMapper.toDto(product));
}
```

**Lifecycle:**
1. Client has no cache: `GET /products/123` → server returns `200 OK` with `ETag: "v42"` and `Cache-Control: public, max-age=300, stale-while-revalidate=60`. Client stores response + ETag.
2. Client re-requests within 5 min: browser serves from local cache, no network call.
3. After 5 min (stale): client sends `GET /products/123` with `If-None-Match: "v42"`.
4. Server fetches product, computes ETag. If ETag matches: `304 Not Modified` (empty body, fast). If different: `200 OK` with new body and new ETag.
5. CDN: `Cache-Control: public, s-maxage=600` makes a CDN cache the response for 10 min, serving many clients with zero backend hits.

**Optimistic concurrency (second use of ETag):**
```
PUT /products/123
If-Match: "v42"          ← client's last-known version
```
If the server's current ETag differs (someone else modified it): `412 Precondition Failed`.

**`ShallowEtagHeaderFilter`** (simpler): adds a servlet filter that MD5-hashes the response body and sends `304` automatically when `If-None-Match` matches. Drawback: the controller still executes and the body is still computed — the filter just avoids transmitting it. No benefit for DB-heavy endpoints. Manual ETag is better when you can check the version without computing the full body.

**Interview trap:** ETag values must be enclosed in double quotes in HTTP headers: `"v42"` not `v42`. Forgetting the quotes means clients and CDNs won't match them correctly. Also: `Cache-Control: private` with credentials is essential for user-specific data — missing `private` can cause a CDN to cache one user's data and serve it to another.
**Tags:** http-caching, etag, cache-control, conditional-get, 304, optimistic-concurrency

---

## Q-SPRW-017 [bloom: apply] [level: senior]
**Question:** How does Spring MVC async processing work? Explain `Callable`, `DeferredResult`, and `WebAsyncTask` — what problem each solves, and the thread model.
**Model answer:** Spring MVC runs on a Servlet container (Tomcat/Undertow) with a thread pool. By default, each request occupies a thread for its entire duration. If the handler blocks (waiting for a downstream service, slow DB query), that thread is idle but unavailable for other requests. Async MVC releases the container thread while work continues elsewhere.

**`Callable<T>`** — simplest. Handler returns a `Callable`. Spring MVC calls it on a task executor thread, releasing the container thread immediately. When the Callable completes, Spring re-acquires a container thread to write the response.

```java
@GetMapping("/{id}/price")
public Callable<PriceDto> getPrice(@PathVariable Long id) {
    return () -> priceService.calculatePrice(id);  // runs on async executor
}
```

**`DeferredResult<T>`** — decoupled. Handler returns a `DeferredResult` immediately. The actual result is set from any thread (message listener, scheduled task, WebSocket handler) at any future time. Container thread released immediately.

```java
@GetMapping("/live-price/{id}")
public DeferredResult<PriceDto> getLivePrice(@PathVariable Long id) {
    DeferredResult<PriceDto> result = new DeferredResult<>(5000L);  // 5s timeout
    priceEventBus.subscribe(id, price -> result.setResult(price));
    result.onTimeout(() -> result.setErrorResult(
        ResponseEntity.status(503).body("Price feed timeout")));
    return result;
}
```

**`WebAsyncTask<T>`** — `Callable` with timeout and callback wiring:

```java
@GetMapping("/{id}")
public WebAsyncTask<PriceDto> getWithTimeout(@PathVariable Long id) {
    Callable<PriceDto> callable = () -> priceService.calculatePrice(id);
    return new WebAsyncTask<>(3000L, callable);  // 3s timeout, then 503
}
```

**Thread model (Callable):**
1. Container thread T1 handles the request.
2. Spring stores async context (request, response, timeout config).
3. T1 is returned to the pool.
4. Task executor thread T2 runs the Callable.
5. T2 completes; Spring re-dispatches to the container.
6. Container thread T3 writes the response.

**When to use which:**
- `Callable` — blocking work you want off the container thread but you initiate it yourself.
- `DeferredResult` — result set externally (event-driven, long-polling).
- `WebAsyncTask` — Callable with explicit timeout handling.

**Interview trap:** Async MVC is NOT the same as reactive (WebFlux). It still uses blocking Servlet infrastructure — just offloads work to a different thread pool. For true non-blocking I/O with backpressure, you need Spring WebFlux + Project Reactor. Async MVC helps under high concurrency with slow I/O but doesn't eliminate thread usage — the task executor still needs threads.
**Tags:** async-mvc, callable, deferredresult, webasynctask, thread-model

---

## Q-SPRW-018 [bloom: apply] [level: senior]
**Question:** You need request/response body logging in a production Spring Boot app without breaking the `@RequestBody` stream. How do you implement it?
**Model answer:** The `HttpServletRequest` input stream can only be read once. If a filter reads it for logging, the controller receives an empty stream. Fix: cache the body in a wrapper.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest =
            new ContentCachingRequestWrapper(request, 8192);   // max 8KB buffer
        ContentCachingResponseWrapper wrappedResponse =
            new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - start;

            // Body is available AFTER chain.doFilter (controller has read it by now)
            byte[] requestBody = wrappedRequest.getContentAsByteArray();
            byte[] responseBody = wrappedResponse.getContentAsByteArray();

            log.info("method={} uri={} status={} duration={}ms reqBody={} resBody={}",
                request.getMethod(), request.getRequestURI(),
                response.getStatus(), duration,
                new String(requestBody, StandardCharsets.UTF_8),
                new String(responseBody, StandardCharsets.UTF_8));

            wrappedResponse.copyBodyToResponse();  // ← CRITICAL: must relay body to client
        }
    }
}
```

**Key points:**
- `ContentCachingRequestWrapper` buffers up to the specified limit; larger bodies are truncated (not read twice — the limit prevents OOM).
- `ContentCachingResponseWrapper` captures the response body written by the controller. Without `copyBodyToResponse()` at the end, the client receives an empty body.
- Register as `OncePerRequestFilter` to prevent double-logging on error forwards.
- **Do not log secrets**: redact `Authorization`, `Cookie`, `X-API-Key` headers. Truncate large bodies in production — logging 50 MB responses destroys log infrastructure.
- `@Order` controls filter position relative to security filters — logging should run after auth so you can include the authenticated user in the log line.

**Interview trap:** Forgetting `wrappedResponse.copyBodyToResponse()` is the most common implementation mistake — everything looks logged but the client gets empty responses. Also: `ContentCachingRequestWrapper` only caches the body after it has been read downstream (by Jackson, for example). If you call `getContentAsByteArray()` before `chain.doFilter()`, you get an empty array.
**Tags:** filter, request-logging, onceperequest, contentcachingrequestwrapper, production

---

## Q-SPRW-019 [bloom: apply] [level: senior]
**Question:** How do you configure global exception handling with `@ControllerAdvice` so it covers validation errors, custom business exceptions, and unexpected runtime exceptions with different response shapes?
**Model answer:**

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // --- Business exceptions → specific status + ProblemDetail ---

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ProductNotFoundException ex, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://api.example.com/problems/not-found"));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("productId", ex.getProductId());
        return ResponseEntity.status(404).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    @ExceptionHandler(PriceConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(PriceConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return ResponseEntity.status(409).body(pd);
    }

    // --- Bean Validation (overrides ResponseEntityExceptionHandler) ---

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
            .toList());
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    // --- Catch-all: hide internals from client ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        String correlationId = MDC.get("correlationId");
        log.error("Unhandled exception correlationId={}", correlationId, ex);  // log with stack
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setDetail("An unexpected error occurred. Reference: " + correlationId);
        // Do NOT expose ex.getMessage() — may leak internals
        return ResponseEntity.status(500).body(pd);
    }
}
```

**Design principles:**
- Extend `ResponseEntityExceptionHandler` to override Spring's built-in handlers (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, etc.) in one place.
- `@ExceptionHandler` resolution is most-specific-type-first — `ProductNotFoundException` matches before `Exception`.
- Never expose stack traces or internal exception messages in 500 responses — use a `correlationId` (set in MDC by a filter) so support can trace logs.
- Use `application/problem+json` content type for RFC 7807 compliance.

**Interview trap:** What happens if the exception is thrown inside a Servlet Filter? The `@ControllerAdvice` never sees it. The request never reaches the DispatcherServlet. You must handle it in the filter itself, or add an error-handling filter higher in the chain that catches exceptions from downstream filters.
**Tags:** exception-handling, controlleradvice, problem-detail, validation, global-error-handler

---

## Q-SPRW-020 [bloom: apply] [level: senior]
**Question:** A junior dev has written a `HandlerInterceptor` that adds a custom header to every response. It works on normal endpoints but the header is missing when the endpoint throws an exception. Explain why and fix it.
**Model answer:** **Root cause:** `HandlerInterceptor.postHandle()` is only called when the controller method returns normally. When an exception propagates from the controller, Spring skips `postHandle()` and jumps directly to `afterCompletion()`. The header addition code in `postHandle` never runs.

```java
// Broken implementation
public class CorrelationHeaderInterceptor implements HandlerInterceptor {
    @Override
    public void postHandle(HttpServletRequest req, HttpServletResponse res,
                           Object handler, ModelAndView mav) {
        res.setHeader("X-Correlation-Id", MDC.get("correlationId"));  // skipped on exception
    }
}
```

**Fix: move to `afterCompletion`:**

```java
public class CorrelationHeaderInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String correlationId = Optional.ofNullable(req.getHeader("X-Correlation-Id"))
            .orElse(UUID.randomUUID().toString());
        MDC.put("correlationId", correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        // afterCompletion is ALWAYS called — even on exception
        String id = MDC.get("correlationId");
        if (id != null) {
            res.setHeader("X-Correlation-Id", id);
        }
        MDC.remove("correlationId");  // always clean up MDC
    }
}
```

**Caveat:** `afterCompletion` runs after the response has been committed on some paths — setting a header on an already-committed response is silently ignored. For guaranteed header presence, move to a Servlet Filter (which wraps the entire DispatcherServlet dispatch and always runs around it):

```java
@Override
protected void doFilterInternal(...) {
    String id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    response.setHeader("X-Correlation-Id", id);  // set BEFORE chain.doFilter
    try {
        chain.doFilter(request, response);
    } finally {
        MDC.remove("correlationId");
    }
}
```

Setting the header before `chain.doFilter()` in a filter guarantees it is present in both success and error paths.

**Interview trap:** `afterCompletion` receives the exception object as a parameter (`Exception ex`) — but only if the exception was not already handled by a `HandlerExceptionResolver`. If `@ControllerAdvice` handled it, `ex` will be `null` in `afterCompletion`.
**Tags:** interceptor, posthandle, aftercompletion, filter, correlation-header, exception-handling

---

## Q-SPRW-021 [bloom: apply] [level: senior]
**Question:** How does Spring MVC filter ordering work? You have a JWT auth filter, a CORS filter, and a request-logging filter. In what order should they run and how do you control that order?
**Model answer:** Servlet filter execution order is determined by the filter's registration order in the `FilterChain`. In Spring Boot, `FilterRegistrationBean` controls this via `setOrder(int)`. Lower numbers run first. Spring Security registers its filter chain at `OrderedFilter.REQUEST_WRAPPER_FILTER_MAX_ORDER - 100` (approximately -100).

**Correct order for these three filters:**

```
1. CORS filter        (highest priority — must respond to OPTIONS preflight before auth runs)
2. Request logging    (before auth so we can log the raw request including anonymous attempts)
3. Spring Security    (JWT extraction, authentication, SecurityContext population)
4. (DispatcherServlet)
```

Actually, Spring Security's `CorsFilter` integration makes this more nuanced: CORS is configured inside Security so it runs within the Security filter chain before `AuthorizationFilter`.

**Manual registration:**

```java
@Bean
public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
    FilterRegistrationBean<RequestLoggingFilter> reg = new FilterRegistrationBean<>();
    reg.setFilter(new RequestLoggingFilter());
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);  // before security
    reg.addUrlPatterns("/api/*");
    return reg;
}
```

**Order constants (Spring Boot):**
- `Ordered.HIGHEST_PRECEDENCE` = `Integer.MIN_VALUE`
- `Ordered.LOWEST_PRECEDENCE` = `Integer.MAX_VALUE`
- Spring Security default: `SecurityProperties.DEFAULT_FILTER_ORDER` = `-100`

**Key constraints:**
- CORS must respond to `OPTIONS` before auth rejects unauthenticated OPTIONS requests. Putting CORS inside Spring Security (`http.cors()`) is the standard fix — Security's CORS handling runs before its auth filters.
- A logging filter must run before security so it can log rejected/unauthenticated requests too.
- Don't use `@Component` on filters you want ordered — Spring Boot auto-detects `@Component` filters and assigns them default order (`Integer.MAX_VALUE`). Use `FilterRegistrationBean` for precise control.

**Interview trap:** `OncePerRequestFilter` extends `GenericFilterBean`, which implements `Filter`. If you annotate a custom `OncePerRequestFilter` with `@Component`, Spring Boot registers it with default (lowest) order AND also processes it via `FilterRegistrationBean` if you define one — resulting in double registration. Disable auto-registration: `registration.setEnabled(false)`.
**Tags:** filter, ordering, filtrationregistrationbean, spring-security, cors, jwt

---

## Q-SPRW-022 [bloom: analyze] [level: master]
**Question:** Compare Spring MVC (WebMvc) and Spring WebFlux architecturally. What is the threading model difference, when does the choice matter operationally, and what are the migration blockers?
**Model answer:** **Spring MVC (Servlet stack):**
- Thread-per-request model. One thread from the container pool (Tomcat, Jetty, Undertow) handles a request from arrival to response.
- Blocking I/O is fine — the thread just waits. `JdbcTemplate`, JPA, blocking REST clients all work naturally.
- Tomcat default: 200 worker threads. Under 200 concurrent slow requests, all threads exhaust → new requests queue → latency spike.
- Async MVC (`DeferredResult`, `Callable`) partially mitigates by offloading to task executor threads, but you still need threads.

**Spring WebFlux (Reactive stack):**
- Event-loop model (Netty by default, or Undertow in reactive mode). A small number of event-loop threads (typically `2 × CPU cores`) handle I/O events non-blocking.
- **Nothing can block the event-loop thread** — blocking a Netty I/O thread stalls thousands of concurrent requests. All I/O must use reactive drivers: R2DBC (not JDBC), reactive MongoDB, WebClient (not RestTemplate).
- Backpressure: `Flux<T>` / `Mono<T>` propagate demand signals upstream — a slow subscriber tells the publisher to slow down. Prevents unbounded memory growth under load.

**When the choice matters:**
- **High concurrency with slow I/O (calls to many services, SSE/WebSocket, long-polling):** WebFlux wins — event loop handles 10k+ concurrent connections on 8 threads.
- **CPU-bound or DB-heavy with JDBC:** WebFlux offers no benefit (still needs threads for JDBC). MVC is simpler.
- **Team and ecosystem:** if you use JPA, Feign, Spring Security (blocking portions), Spring Batch — all require wrapping or avoiding blocking in WebFlux. Steep learning curve.

**Migration blockers (MVC → WebFlux):**
1. **JDBC / JPA** — no reactive driver. Must switch to R2DBC or accept `publishOn(Schedulers.boundedElastic())` to run blocking JDBC on a bounded thread pool (defeats the purpose).
2. **`ThreadLocal`-based patterns** — Spring Security's `SecurityContextHolder`, MDC (logging), transaction management — all rely on `ThreadLocal`. Reactive uses `Context` propagation instead; Security supports it but requires explicit config.
3. **Blocking third-party libraries** — anything that blocks a thread (legacy HTTP clients, synchronous SDK calls).
4. **`@Transactional`** — JPA transactions don't compose with reactive. R2DBC has its own `@Transactional`-like mechanism.

**Practical recommendation for Spring Boot 3.x microservices:**
Start with MVC. If you hit real C10K problems (sustained 10k+ concurrent connections, long-lived connections), consider WebFlux selectively for gateway/streaming services. Don't rewrite a CRUD service to WebFlux because it's "faster" — the JPA migration cost alone is significant.

**Interview trap:** "WebFlux is always faster" — false. Under low concurrency with fast I/O, MVC and WebFlux perform similarly. WebFlux's advantage appears at high concurrency with high latency I/O. A blocking call on a WebFlux event-loop thread (accidentally calling a blocking JDBC driver) is worse than MVC — it stalls all requests on that thread.
**Tags:** webflux, webmvc, reactive, threading, r2dbc, event-loop, backpressure, migration

---

## Q-SPRW-023 [bloom: analyze] [level: master]
**Question:** A `@ControllerAdvice` exception handler is not catching exceptions from certain endpoints. Describe all the reasons this can happen and how to diagnose each.
**Model answer:** `@ControllerAdvice` is handled by `ExceptionHandlerExceptionResolver` — it only intercepts exceptions that: (a) reach the `DispatcherServlet` and (b) are not caught by a higher-priority resolver first. Here are all the ways it silently fails:

**1. Exception thrown in a Servlet Filter**
Filters run before `DispatcherServlet`. Exceptions escape entirely. Diagnosis: check if the stack trace shows the exception inside a filter class. Fix: catch in the filter, or add an error-catching filter at the top.

**2. Exception thrown in a `@Async` method**
`@Async` methods run in a separate thread. Exceptions are swallowed into the `CompletableFuture`/`Future` return value (or trigger `AsyncUncaughtExceptionHandler` for `void` methods). They never touch the HTTP thread. Fix: configure `AsyncUncaughtExceptionHandler` or handle in the caller via `CompletableFuture.exceptionally()`.

**3. A `HandlerExceptionResolver` with higher priority handles it first**
`DefaultHandlerExceptionResolver` (handles Spring MVC exceptions like `MethodNotAllowedException`) and `ResponseStatusExceptionResolver` (handles `@ResponseStatus` annotated exceptions and `ResponseStatusException`) both run before `ExceptionHandlerExceptionResolver`. If they handle the exception, `@ControllerAdvice` never sees it. Diagnosis: check if exception class has `@ResponseStatus` or extends `ResponseStatusException`.

**4. Multiple `@ControllerAdvice` beans with overlapping `@ExceptionHandler`**
Spring uses the first `@ControllerAdvice` bean that matches. If two beans both handle `Exception.class`, only one wins. Diagnosis: use `--debug` logging to see which advice is registered. Fix: use a single catch-all advice and be explicit.

**5. `@ExceptionHandler` method signature mismatch**
If the handler method declares a parameter type that Spring cannot inject (wrong type), Spring treats it as non-matching and skips it. Diagnosis: enable `DEBUG` logging for `org.springframework.web.servlet`.

**6. Exception wrapped by AOP advice**
An `@Around` advice on the controller catches the original exception and re-throws a different type. The `@ControllerAdvice` only sees the wrapped type, which may not match any handler. Diagnosis: look at the actual exception type in the 500 response, not the expected one.

**7. Application context split (parent/child contexts)**
In some legacy configurations (Spring MVC with ContextLoaderListener), controllers live in the child context but `@ControllerAdvice` is registered in the parent context. Child context scans don't see parent beans for exception handling. Diagnosis: check if using separate root/servlet contexts. Fix: define all MVC infrastructure in the same context.

**Diagnostic steps:**
```
1. Enable DEBUG for org.springframework.web.servlet.DispatcherServlet
2. Check the exact exception class in the log
3. Verify @ControllerAdvice is in the correct application context
4. Confirm no higher-priority ExceptionHandlerResolver handles it first
5. Check for filters in the call stack above the exception
```

**Interview trap:** In Spring Boot, there is typically only one `ApplicationContext` (no parent/child split). But in monolith apps with `@ContextHierarchy` or `SpringMVC` XML config alongside Boot, the split context problem surfaces. Also: `@ExceptionHandler` on a `@ControllerAdvice` class is matched using the most specific applicable type — adding a handler for `RuntimeException` won't catch `IOException` (not a subtype).
**Tags:** controlleradvice, exception-handling, debugging, filter, async, resolvers

---

## Q-SPRW-024 [bloom: analyze] [level: master]
**Question:** Describe how you would design a high-throughput Spring MVC endpoint serving 50k req/sec for `GET /price?product_id=X&country=Y`. What changes at the Spring layer and infrastructure layer?
**Model answer:** At 50k req/sec, the constraint is rarely Spring itself — it's I/O and shared resources. Here's a layered strategy:

**Spring MVC layer optimizations:**

```java
@GetMapping
@ResponseBody
public PriceDto getPrice(
    @RequestParam Long productId,
    @RequestParam @NotBlank String country) {

    // 1. Fast path: in-process L1 cache (Caffeine, bounded, TTL 60s)
    return priceCache.get(PriceCacheKey.of(productId, country),
        key -> priceService.load(key));  // L1 miss → L2/DB
}
```

1. **In-process L1 cache (Caffeine):** sub-millisecond, no network. TTL 60s. Handles 95%+ of reads on hot products. Per-instance — no coherence guarantee, acceptable for pricing with short TTL.
2. **`@ResponseBody` serialization cost:** reuse a single `ObjectMapper` (Spring Boot ensures this). Avoid reflection-heavy serialization on the hot path — consider Jackson's `StreamingJsonGenerator` for large responses, or use `@JsonView` to serialize a minimal projection.
3. **Argument resolution overhead:** `@RequestParam` binding is cheap. Don't add `@Valid` on the hot path if the validation is trivial — validate in application code instead.
4. **Thread pool tuning (Tomcat):** `server.tomcat.threads.max` default 200. At 50k RPS × 1ms avg latency = 50 concurrent threads needed theoretically (Little's Law). At 10ms avg, 500 threads needed. Tune to the actual latency profile.
5. **`@Async` for non-critical side effects** (audit logging, analytics events): don't do them synchronously on the request thread.

**Infrastructure layer:**

| Layer | What it does | Impact |
|---|---|---|
| CDN (Cloudflare, CloudFront) | Cache `public` responses at edge | Eliminates backend for cached hits |
| Redis L2 cache | Shared cache across instances | DB protection, cross-node consistency |
| Read replicas | Distribute DB reads | Scales read throughput linearly |
| Horizontal scaling | K8s HPA on CPU/RPS | Multiple app instances behind LB |
| Connection pool sizing (HikariCP) | `max-pool-size = (db_max_connections / app_instances)` | Prevent DB connection exhaustion |

**Cache invalidation strategy at 50k RPS:**
- Write event published to Kafka on price change.
- Each app instance subscribes → evicts L1 entry.
- Redis TTL provides upper bound on staleness even without explicit invalidation.
- Accept eventual consistency window (60s max stale) — appropriate for pricing; not for real-time stocks.

**Batch endpoint to reduce QPS:**
```
GET /prices?product_ids=1,2,3&country=PL   // 1 request instead of N
```
A single batch call can replace 100 individual requests from a frontend rendering a product grid.

**Interview trap:** "Just add more instances" without addressing the DB layer means all instances hammer the same DB — connection exhaustion. Cache is the primary lever. Also: at 50k RPS with no cache, even a 1ms DB query requires 50k concurrent DB operations/sec — well beyond a typical Postgres instance's capability without read replicas and aggressive caching.
**Tags:** performance, caching, scaling, spring-mvc, caffeine, redis, cdn, architecture

---

## Q-SPRW-025 [bloom: analyze] [level: master]
**Question:** Your team is evaluating whether to replace Spring MVC's `@ExceptionHandler`-based error handling with `ResponseStatusException` everywhere. What are the tradeoffs and when is each appropriate?
**Model answer:** Both mechanisms produce HTTP error responses, but they serve different purposes and have different characteristics:

**`ResponseStatusException` (inline, ad-hoc):**
```java
throw new ResponseStatusException(HttpStatus.NOT_FOUND,
    "Product " + id + " not found");
```
- Thrown from any service/controller method.
- Handled by `ResponseStatusExceptionResolver` (higher priority than `ExceptionHandlerExceptionResolver`).
- In Spring Boot 3+, with `spring.mvc.problemdetails.enabled=true`, produces a `ProblemDetail` body automatically.
- No boilerplate — no custom exception class needed.
- **Downsides:**
  - The `reason` string (human message) is written directly to the response — leaks internal details to clients in production if not careful.
  - Hard to unit test: asserting on specific HTTP status requires catching the exception or testing via `MockMvc`.
  - No place to attach structured error context (field names, error codes) — you can only set a string message.
  - Tightly couples service layer to HTTP semantics — services should throw domain exceptions, not HTTP exceptions.

**`@ControllerAdvice` + `@ExceptionHandler` (centralized, structured):**
```java
// Domain exception — no HTTP dependency
public class ProductNotFoundException extends RuntimeException {
    private final Long productId;
    // constructor
}

// HTTP mapping — one place
@ExceptionHandler(ProductNotFoundException.class)
public ResponseEntity<ProblemDetail> handle(ProductNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product not found");
    pd.setProperty("productId", ex.getProductId());
    return ResponseEntity.status(404).body(pd);
}
```
- Clean domain/HTTP separation.
- Structured error body with typed fields (error codes, field pointers, request IDs).
- One place to change the error contract for a class of errors.
- Easier to test: assert on the exception class in unit tests; assert on HTTP mapping in integration tests.
- Boilerplate cost: one custom exception class per business error type.

**Decision matrix:**

| Use case | `ResponseStatusException` | `@ControllerAdvice` |
|---|---|---|
| Quick prototype / internal tool | OK | Overkill |
| Public API with typed error schema | Not sufficient | Required |
| Service layer throwing HTTP-agnostic errors | Wrong layer | Right layer |
| Uniform error format across all endpoints | Hard to enforce | Natural |
| Existing exception hierarchy | Disrupts it | Wraps it |

**Hybrid approach (pragmatic):** Use `ResponseStatusException` only in controller layer for truly trivial cases (path-variable parsing failure, missing required parameter). Use `@ControllerAdvice` for all business logic exceptions and for unifying the error schema across the API.

**Interview trap:** "Just use `ResponseStatusException` everywhere" — this was common advice in early Spring Boot but it leads to service classes importing `org.springframework.web` packages, which violates hexagonal architecture. If your service throws `ResponseStatusException`, you can't reuse it in a batch job, message listener, or CLI tool without it throwing HTTP-flavored exceptions in non-HTTP contexts.
**Tags:** exception-handling, responsestatusexception, controlleradvice, api-design, architecture, error-contract

---

## Q-SPRW-026 [bloom: analyze] [level: master]
**Question:** A Spring MVC endpoint returns paginated results using Spring Data's `Page<T>`. At page 5000 with 20 items per page, queries take 8 seconds. Explain the root cause and redesign the pagination for production.
**Model answer:** **Root cause: OFFSET-based pagination performance degradation.**

Spring Data's `Pageable` + `Page<T>` translates to:
```sql
SELECT * FROM products ORDER BY created_at DESC OFFSET 100000 LIMIT 20;
```

Even with an index on `created_at`, the database must scan and skip 100,000 rows before returning 20. At page 5000, that's `5000 × 20 = 100,000` rows scanned and discarded. This is O(offset) — performance degrades linearly with depth.

**Fix 1: Keyset (cursor-based) pagination**

Replace `OFFSET` with a `WHERE` predicate on the last-seen value:
```sql
-- First page
SELECT * FROM products ORDER BY created_at DESC, id DESC LIMIT 21;

-- Page N+1 (cursor from last row: created_at='2024-01-15T10:00:00', id=12345)
SELECT * FROM products
WHERE (created_at, id) < ('2024-01-15T10:00:00', 12345)
ORDER BY created_at DESC, id DESC LIMIT 21;
```
Fetch 21 rows, return 20, use the 21st's existence to set `hasMore=true`. The cursor is the encoded last-seen `(created_at, id)` tuple.

Requires a composite index `(created_at DESC, id DESC)` — the query becomes an index range scan: O(log n + page_size).

**Spring MVC layer:**
```java
@GetMapping
public PagedResponse<ProductDto> list(
    @RequestParam(required = false) String cursor,   // base64-encoded last position
    @RequestParam(defaultValue = "20") @Max(100) int limit) {

    CursorPage<Product> page = productRepository.findAfterCursor(
        CursorDecoder.decode(cursor), limit + 1);

    List<ProductDto> items = page.items().stream()
        .limit(limit).map(productMapper::toDto).toList();

    String nextCursor = page.items().size() > limit
        ? CursorEncoder.encode(page.items().get(limit - 1)) : null;

    return new PagedResponse<>(items, nextCursor, nextCursor != null);
}
```

**Fix 2: Limit maximum page depth for offset pagination**

If keyset is not feasible (UI requires page numbers), cap the allowable offset:
```java
if (pageable.getOffset() > 10_000) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum page offset exceeded");
}
```
Pair with a count query shortcut: use `COUNT(*)` with an index-only scan on a small indexed column.

**Fix 3: `Slice<T>` instead of `Page<T>`**

`Page<T>` executes an extra `COUNT(*)` query for total element count. `Slice<T>` omits the count — halves DB load for "infinite scroll" use cases that don't need total count.

**Fix 4: Covering index**

If the query filters on common params, a covering index (includes all SELECTed columns) allows an index-only scan, bypassing heap fetches entirely.

**Interview trap:** "Just use `Slice<T>` instead of `Page<T>`" — it removes the COUNT query but the OFFSET scan problem remains. `Slice` is faster than `Page` by one query, but at page 5000 you still scan 100k rows. The real fix is keyset pagination. Also: keyset requires a stable, unique sort column — sorting by `name` alone without a tie-breaking `id` can produce inconsistent cursors when names are duplicated.
**Tags:** pagination, keyset, offset, page, slice, spring-data, performance, index

---
