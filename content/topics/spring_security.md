# Spring Security — question bank

> Spring Security is the de-facto security framework for Java/Kotlin backend applications. It handles authentication (who are you?), authorization (what can you do?), CSRF protection, session management, password hashing, OAuth2/OIDC integration, and JWT validation through a servlet filter chain that intercepts every HTTP request before it reaches application code. For a senior backend interview, you must understand not just the API surface but the internals: how the filter chain is ordered, how the SecurityContext propagates through threads, why stateless REST APIs disable CSRF, how JWTs enable horizontal scaling at the cost of revocability, and when OAuth2 authorization-code+PKCE vs client-credentials is the right choice. This topic is consistently probed in senior interviews because auth mistakes have production consequences — getting it wrong is not an academic failure.

## Scope

- SecurityFilterChain: what it is, how it replaces WebSecurityConfigurerAdapter, lambda DSL (Spring Security 6)
- Filter ordering: built-in filter sequence, how to inject a custom filter at the right position
- Authentication vs authorization: conceptual distinction and where each is enforced in the framework
- AuthenticationManager / AuthenticationProvider / DaoAuthenticationProvider: delegation chain
- UserDetailsService: contract, loadUserByUsername, UserDetails fields
- SecurityContextHolder: ThreadLocal storage, MODE_INHERITABLETHREADLOCAL, clearing in async code
- Password encoding: BCryptPasswordEncoder, Argon2, DelegatingPasswordEncoder, timing-safe comparison
- Roles vs authorities: ROLE_ prefix convention, hasRole vs hasAuthority
- CSRF: why it exists, how the synchronizer token works, when to disable on stateless REST
- CORS interplay with Spring Security: why MVC-only CORS config breaks with Security on the chain
- Session management: stateful vs stateless (STATELESS policy), session fixation, concurrent sessions
- Method security: @PreAuthorize / @PostAuthorize with SpEL, @Secured, @RolesAllowed, @EnableMethodSecurity
- OAuth2 overview: four roles (Resource Owner, Client, Authorization Server, Resource Server)
- Authorization Code + PKCE flow: step-by-step, why PKCE, public vs confidential clients
- Client Credentials flow: machine-to-machine, when to use
- Resource server vs OAuth2 client in Spring Security
- JWT structure: header.payload.signature, claims, HS256 vs RS256/RS384
- JWT stateless validation: JWKS, issuer check, expiry, audience
- JWT revocation problem: why you cannot revoke, short expiry + refresh token strategy, jti deny-list
- Refresh token rotation: why, storage (HttpOnly cookie vs memory)
- OIDC: how it extends OAuth2, id_token, UserInfo endpoint
- Custom JwtAuthenticationConverter: extracting scopes and custom claims as GrantedAuthority
- Testing Spring Security: @WithMockUser, @WithMockJwt, @WebMvcTest slice, Testcontainers + Keycloak
- Security headers: HSTS, X-Frame-Options, CSP — Spring Security defaults
- OWASP Top 10 mitigations: broken access control, injection, auth failures

---

## Q-SPRS-001 [bloom: recall] [level: junior]
**Question:** What is the difference between authentication and authorization in Spring Security? Where does each concern live in the framework?

**Model answer:** Authentication answers "who are you?" — it verifies identity by validating credentials (username/password, token, certificate). Authorization answers "what are you allowed to do?" — it checks whether the authenticated identity has permission to perform a requested action.

In Spring Security: authentication is handled by the filter chain (e.g. `UsernamePasswordAuthenticationFilter`, `BearerTokenAuthenticationFilter`) and the `AuthenticationManager` subsystem. Once authentication succeeds, a populated `Authentication` object is stored in `SecurityContextHolder`. Authorization runs after authentication: URL-level authorization via `http.authorizeHttpRequests()` is enforced by `AuthorizationFilter` (the last major filter), and method-level authorization via `@PreAuthorize`/`@PostAuthorize` is enforced by AOP proxies around service beans.

Separation of concerns is intentional — you can swap auth mechanisms (form login → JWT) without touching your authorization rules.

**Interview trap:** "So if authentication fails, the app returns 401, and if authorization fails it returns 403 — always?" Answer: correct by default, but only if the `AuthenticationEntryPoint` and `AccessDeniedHandler` are wired correctly. A common mistake is a misconfigured `AuthenticationEntryPoint` that returns 403 on missing credentials instead of 401, confusing clients. Always test both paths explicitly.

**Tags:** authentication, authorization, filter-chain, security-context, spring-security-6

---

## Q-SPRS-002 [bloom: recall] [level: junior]
**Question:** What is `SecurityContextHolder` and how does it store the currently authenticated principal?

**Model answer:** `SecurityContextHolder` is a thread-bound store (by default backed by a `ThreadLocal<SecurityContext>`) that holds the `SecurityContext` for the current request thread. The `SecurityContext` contains the `Authentication` object — the principal, credentials, and `GrantedAuthority` list.

Default strategy: `MODE_THREADLOCAL` — each thread has its own context. The filter chain populates it at the start of the request and clears it (important!) at the end in `SecurityContextPersistenceFilter` / `SecurityContextHolderFilter` (Spring Security 6).

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();
Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
```

Strategies:
- `MODE_THREADLOCAL` (default): safe for servlet containers with one-thread-per-request.
- `MODE_INHERITABLETHREADLOCAL`: child threads (e.g. `@Async`) inherit the parent's context.
- `MODE_GLOBAL`: single context shared by all threads — only for standalone apps, never in a web server.

**Interview trap:** "You use `@Async` and suddenly `SecurityContextHolder.getContext()` returns null — why?" Because `@Async` spawns a new thread that does not inherit the parent's `ThreadLocal`. Fix: switch to `MODE_INHERITABLETHREADLOCAL` or use `DelegatingSecurityContextExecutor` to propagate the context explicitly. Do not just set mode globally if your async tasks should NOT share the parent's security context.

**Tags:** security-context-holder, thread-local, async, authentication, principal

---

## Q-SPRS-003 [bloom: recall] [level: junior]
**Question:** What is `UserDetailsService` and what contract must it fulfill?

**Model answer:** `UserDetailsService` is a single-method interface that Spring Security calls to load user information from a data source during form-login or basic-auth authentication:

```java
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
```

It returns a `UserDetails` object containing: `getUsername()`, `getPassword()` (encoded hash, not plaintext), `getAuthorities()` (roles/permissions), and boolean flags: `isEnabled()`, `isAccountNonLocked()`, `isAccountNonExpired()`, `isCredentialsNonExpired()`.

The returned `UserDetails` is used by `DaoAuthenticationProvider`: it calls `loadUserByUsername()` to get the stored hash, then calls `passwordEncoder.matches(rawPassword, storedHash)` to verify. It never compares passwords with `equals()`.

Spring Security's `User` class is a ready-made `UserDetails` implementation. For custom user entities, implement the interface directly or wrap with `User.withUsername(...).build()`.

**Interview trap:** "What happens if `loadUserByUsername` returns a user with `isEnabled() = false`?" `DaoAuthenticationProvider` checks all flags after password verification and throws `DisabledException`, `LockedException`, or `CredentialsExpiredException` as appropriate. These map to 401 responses with different `WWW-Authenticate` error codes. Many devs implement the loading but forget to set and check these flags — especially `isAccountNonLocked()` for brute-force lockout.

**Tags:** user-details-service, dao-authentication-provider, user-details, password-encoder

---

## Q-SPRS-004 [bloom: recall] [level: junior]
**Question:** What is BCrypt and why does Spring Security use it as the default password encoder?

**Model answer:** BCrypt is an adaptive password hashing algorithm designed specifically for password storage. Key properties:

1. **Automatic per-password salt** — BCrypt generates and embeds a random 16-byte salt in the output hash. No separate salt column needed in the database. The stored hash (60 chars) encodes: version (`$2a$`), cost factor, salt, and hash.
2. **Adaptive cost factor** — the work factor (default 10 in Spring's `BCryptPasswordEncoder`) is a base-2 exponent for hashing rounds. Cost 10 = 1024 rounds. You can raise it as hardware gets faster without breaking stored hashes.
3. **Intentionally slow** — makes brute-force and rainbow-table attacks expensive. At cost 10, hashing takes ~100ms on modern hardware, acceptable for login but prohibitive for bulk cracking.

```java
PasswordEncoder encoder = new BCryptPasswordEncoder(12); // cost 12 for higher security
String hash = encoder.encode("rawPassword");
boolean matches = encoder.matches("rawPassword", hash); // timing-safe
```

The `matches()` method uses constant-time comparison to prevent timing attacks.

Spring Security also ships `Argon2PasswordEncoder` (OWASP preferred — memory-hard), `SCryptPasswordEncoder`, and `Pbkdf2PasswordEncoder`. For new projects, Argon2 is the modern recommendation. For migration across algorithms, use `DelegatingPasswordEncoder`.

**Interview trap:** "Can you use BCrypt in a high-throughput login endpoint that gets 10k req/s?" No — BCrypt at cost 10 takes ~100ms per hash, so a single thread can do ~10 logins/second. At 10k req/s you need 1000 threads just for hashing — unworkable. Solutions: lower cost (increases cracking risk), rate-limit the endpoint, or offload to an async worker pool. This is a real production constraint that most devs haven't thought through.

**Tags:** bcrypt, password-encoding, argon2, delegating-password-encoder, timing-attack

---

## Q-SPRS-005 [bloom: recall] [level: junior]
**Question:** What is CSRF and why does Spring Security enable CSRF protection by default for stateful web apps but recommend disabling it for stateless REST APIs?

**Model answer:** CSRF (Cross-Site Request Forgery) is an attack where a malicious website tricks a browser into making an authenticated request to your API using the victim's session cookie. Browsers automatically attach cookies for a domain regardless of the page origin making the request.

**How CSRF protection works (synchronizer token pattern):**
1. Server generates a random CSRF token, stores it in the session.
2. Server includes the token in every HTML form as a hidden field (or sends it in a response header/cookie for SPAs).
3. On state-changing requests (POST/PUT/DELETE), the server checks that the submitted CSRF token matches the session token.
4. An attacker's page cannot read the token (same-origin policy), so it cannot forge a valid request.

**Why stateless REST APIs can disable it:**
- CSRF exploits the browser's automatic cookie sending.
- If your API uses `Authorization: Bearer <token>` headers instead of session cookies, the attacker's page cannot set that header — only JS from the same origin can. JWTs in headers are not sent automatically by browsers.
- Disabling: `http.csrf(AbstractHttpConfigurer::disable)`.

**Warning:** if your REST API uses cookie-based auth (even `HttpOnly` cookies carrying a JWT), you must keep CSRF protection. The transport mechanism (cookie), not the token type (JWT), is what matters.

**Interview trap:** "Is disabling CSRF on a REST endpoint safe if you use `SameSite=Strict` cookies?" `SameSite=Strict` does largely prevent CSRF by blocking cross-site cookie sending, but it is a browser-side protection and older browsers don't support it. Defense-in-depth says: use both CSRF tokens AND `SameSite` cookies; disable CSRF only when using `Authorization` header bearer tokens.

**Tags:** csrf, synchronizer-token, stateless, rest-api, session-cookie, same-site

---

## Q-SPRS-006 [bloom: recall] [level: junior]
**Question:** What is the difference between a role and an authority in Spring Security? Why does Spring's `hasRole()` add a `ROLE_` prefix automatically?

**Model answer:** In Spring Security, both roles and authorities are represented as `GrantedAuthority` objects — they are the same underlying type. The distinction is conceptual and by convention:

- **Authority** (fine-grained permission): a specific action or resource access, e.g. `READ_USERS`, `WRITE_ORDERS`, `scope:write:profiles`. Used with `hasAuthority("READ_USERS")`.
- **Role** (coarse group of permissions): a named identity category, e.g. `ADMIN`, `PARTNER`, `VIEWER`. By convention, role names are stored with the `ROLE_` prefix: `ROLE_ADMIN`.

`hasRole('ADMIN')` in SpEL automatically prepends `ROLE_`, so it checks for the authority `ROLE_ADMIN`.
`hasAuthority('ROLE_ADMIN')` is equivalent but explicit.
`hasAuthority('READ_USERS')` checks for that exact string — no prefix added.

```java
// Granting in UserDetails
List.of(
    new SimpleGrantedAuthority("ROLE_ADMIN"),
    new SimpleGrantedAuthority("READ_USERS")
)

// Checking in security config
.requestMatchers("/admin/**").hasRole("ADMIN")  // checks ROLE_ADMIN
.requestMatchers("/api/users").hasAuthority("READ_USERS")
```

OAuth2 scopes do not follow the `ROLE_` convention and are typically mapped as plain authorities by `JwtGrantedAuthoritiesConverter`.

**Interview trap:** "You store role `ADMIN` (no prefix) in the DB and use `hasRole('ADMIN')` — will it work?" No. `hasRole` adds the prefix, so it checks `ROLE_ADMIN`. Your stored value `ADMIN` won't match. Either store `ROLE_ADMIN` in the DB, or use `hasAuthority('ADMIN')`. This prefix mismatch is a very common auth bug that produces silent 403 responses.

**Tags:** roles, authorities, granted-authority, has-role, has-authority, role-prefix

---

## Q-SPRS-007 [bloom: understand] [level: regular]
**Question:** Describe the Spring Security filter chain architecture. What is `SecurityFilterChain`, what built-in filters does it contain (and in what order), and how does Spring Boot wire it automatically?

**Model answer:** Spring Security integrates into the servlet container as a single `DelegatingFilterProxy` (registered as filter `springSecurityFilterChain` in the servlet filter chain). This proxy delegates to a `FilterChainProxy` which holds one or more `SecurityFilterChain` beans. Each `SecurityFilterChain` has a request matcher and an ordered list of `SecurityFilter` instances.

**Key built-in filters in order (abbreviated):**

| Order | Filter | Purpose |
|-------|--------|---------|
| 100 | `DisableEncodeUrlFilter` | Prevents session ID in URL |
| 200 | `SecurityContextHolderFilter` | Loads/saves SecurityContext |
| 300 | `HeaderWriterFilter` | Adds security headers (HSTS, X-Frame-Options) |
| 400 | `CsrfFilter` | CSRF token validation |
| 500 | `LogoutFilter` | Handles logout URL |
| 600 | `UsernamePasswordAuthenticationFilter` | Form login |
| 700 | `BasicAuthenticationFilter` | HTTP Basic |
| 800 | `BearerTokenAuthenticationFilter` | OAuth2 JWT bearer |
| 900 | `SecurityContextHolderAwareRequestWrapper` | Wraps request |
| 1000 | `AnonymousAuthenticationFilter` | Adds anonymous auth if none set |
| 1100 | `ExceptionTranslationFilter` | Translates auth/access exceptions to 401/403 |
| 1200 | `AuthorizationFilter` | URL-based authorization (replaces `FilterSecurityInterceptor` in SS6) |

**Spring Boot auto-configuration:** `SpringBootWebSecurityConfiguration` creates a default `SecurityFilterChain` if no custom one is defined. When you define a `@Bean SecurityFilterChain`, it replaces (or adds alongside) the default. `WebSecurityConfigurerAdapter` is removed in Spring Security 6 — never extend it.

**Lambda DSL (Spring Security 6):**
```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/api/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
}
```

Multiple `SecurityFilterChain` beans are matched by their `securityMatcher` — the first match wins. Useful for having different auth mechanisms for `/api/**` (JWT) and `/admin/**` (form login).

**Interview trap:** "You have two `SecurityFilterChain` beans — one for `/api/**` and one catch-all. Requests to `/api/login` hit both chains, right?" No. `FilterChainProxy` stops at the FIRST matching chain. If the `/api/**` chain matches first, the catch-all never runs for those paths. This is the multiplexing feature — and the footgun if you misconfigure matchers.

**Tags:** filter-chain, security-filter-chain, filter-ordering, delegating-filter-proxy, filter-chain-proxy, spring-security-6

---

## Q-SPRS-008 [bloom: understand] [level: regular]
**Question:** Walk through the `AuthenticationManager` → `AuthenticationProvider` → `UserDetailsService` delegation chain. What is each component's responsibility?

**Model answer:** Spring Security separates authentication delegation into three layers:

**`AuthenticationManager`** — top-level interface with one method: `authenticate(Authentication)`. Returns a fully populated `Authentication` on success, throws `AuthenticationException` on failure. The standard implementation is `ProviderManager`.

**`ProviderManager`** — iterates a list of `AuthenticationProvider` beans. For each provider, checks `supports(authClass)` — if yes, delegates authentication to it. If all providers reject, throws `ProviderNotFoundException`. `ProviderManager` can have a parent `AuthenticationManager` (e.g. for fallback to a global provider).

**`AuthenticationProvider`** — handles a specific `Authentication` type. The default for username/password is `DaoAuthenticationProvider`:
1. Calls `userDetailsService.loadUserByUsername(username)` to get `UserDetails`.
2. Calls `passwordEncoder.matches(rawPassword, storedHash)` to verify credentials.
3. Checks account flags (enabled, non-locked, non-expired).
4. Returns `UsernamePasswordAuthenticationToken` with `UserDetails` as principal and populated authorities.

For OAuth2 JWT, `JwtAuthenticationProvider` validates the JWT signature + claims and uses a `JwtAuthenticationConverter` to build the `Authentication`.

**Diagram:**
```
Filter →  ProviderManager → DaoAuthenticationProvider → UserDetailsService
                         → JwtAuthenticationProvider  → JwtDecoder
                         → (custom providers...)
```

You can add custom providers (LDAP, OTP, API-key) by implementing `AuthenticationProvider` and registering the bean.

**Interview trap:** "Where exactly is the password compared — in `UserDetailsService` or in `AuthenticationProvider`?" In the `AuthenticationProvider` (`DaoAuthenticationProvider`). `UserDetailsService` only loads the user record; it never sees the raw password. The provider gets both the raw password (from the `Authentication` token) and the encoded hash (from `UserDetails`) and does the comparison. This is a common confusion point.

**Tags:** authentication-manager, provider-manager, authentication-provider, dao-authentication-provider, user-details-service

---

## Q-SPRS-009 [bloom: understand] [level: regular]
**Question:** What is `DelegatingPasswordEncoder` and why should you use it instead of hardcoding `new BCryptPasswordEncoder()`?

**Model answer:** `DelegatingPasswordEncoder` is a `PasswordEncoder` that prefixes stored hashes with an algorithm identifier and delegates encode/matches to the appropriate encoder. The stored format is: `{id}encodedHash`.

Examples:
- `{bcrypt}$2a$10$...` → delegates to `BCryptPasswordEncoder`
- `{argon2}$argon2id$...` → delegates to `Argon2PasswordEncoder`
- `{noop}plaintext` → no-op (dev only, never production)

**Why use it:**
1. **Algorithm migration**: you can upgrade from BCrypt to Argon2 without invalidating existing hashes. Users on old algorithm still authenticate (matched by their prefix). After login, you can re-encode with the new algorithm and update the stored hash.
2. **Avoid ID mismatch errors**: if you store hashes without a prefix and hardcode `BCryptPasswordEncoder`, a hash generated with a different encoder will silently fail `matches()`.
3. **Future-proofing**: Spring can add new encoders; your app picks them up without changing stored data.

```java
// Recommended factory
PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
// Default encoding uses bcrypt; can verify noop/scrypt/argon2 for migration

// Custom (if you want Argon2 as new default)
Map<String, PasswordEncoder> encoders = new HashMap<>();
encoders.put("argon2", new Argon2PasswordEncoder(...));
encoders.put("bcrypt", new BCryptPasswordEncoder());
PasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);
```

**Interview trap:** "You have a legacy app where passwords are stored as plain MD5 hashes. Can `DelegatingPasswordEncoder` help?" Yes, but you must wrap `MessageDigestPasswordEncoder("MD5")` under the appropriate id. However, MD5 is cryptographically broken for passwords — you should use the migration path (on next login, verify MD5, then re-encode with BCrypt/Argon2 and update the DB). `DelegatingPasswordEncoder` facilitates this migration, but MD5 must be treated as temporary.

**Tags:** delegating-password-encoder, algorithm-migration, bcrypt, argon2, password-encoder

---

## Q-SPRS-010 [bloom: understand] [level: regular]
**Question:** How does Spring Security handle CORS, and why is configuring CORS only in Spring MVC (via `WebMvcConfigurer`) not enough when Spring Security is on the classpath?

**Model answer:** The root cause: **Spring Security's filter chain runs before `DispatcherServlet`**. When a browser sends a preflight `OPTIONS` request for a cross-origin call, the `CsrfFilter` or `AuthorizationFilter` may reject it with 401/403 before the request ever reaches MVC's CORS handling — because at the filter level, the request has no CSRF token and no auth credentials (preflight requests don't carry auth).

**Correct approach — register CORS at the Security filter level:**

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // ... rest of config
        .build();
}
```

`http.cors()` inserts `CorsFilter` before `CsrfFilter`, so preflight OPTIONS requests are handled and allowed before any auth check runs.

**Three options, ranked:**
1. `CorsConfigurationSource` bean + `http.cors()` — correct for Security + MVC.
2. `@CrossOrigin` on controllers — MVC-only, works for simple requests but not if Security blocks preflights.
3. `WebMvcConfigurer.addCorsMappings()` — same problem as option 2.

**Interview trap:** "You add `@CrossOrigin("*")` to all controllers. CORS works in dev without Security but breaks in prod with Security. Why?" Security is on the classpath in prod. Preflight OPTIONS requests hit the Security filter chain first and get 401 (no credentials in preflight). `@CrossOrigin` never sees the request. Fix: use `CorsConfigurationSource` wired into `http.cors()`.

**Tags:** cors, preflight, spring-security-filter, cross-origin, web-mvc-configurer, cors-configuration-source

---

## Q-SPRS-011 [bloom: understand] [level: regular]
**Question:** What is stateless session management in Spring Security and what does setting `SessionCreationPolicy.STATELESS` actually do?

**Model answer:** `SessionCreationPolicy.STATELESS` instructs Spring Security to never create or use an `HttpSession` for storing `SecurityContext`. Each request must carry all authentication context (e.g., a JWT `Authorization` header). This is the correct policy for stateless REST APIs.

**What it does internally:**
- `SecurityContextHolderFilter` does not attempt to load `SecurityContext` from the session before processing the request.
- `SecurityContextHolderFilter` does not save `SecurityContext` to the session after processing.
- `SessionManagementFilter` is effectively a no-op (no session fixation protection needed — no sessions).
- The `HttpSession` is never created by Spring Security. (Application code can still create one, but that's a bug in a stateless setup.)

```java
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

**Contrast with stateful (ALWAYS/IF_REQUIRED):** Spring Security stores the `Authentication` in the session after login. Subsequent requests load it from there. Session fixation protection (`changeSessionId` strategy) regenerates the session ID after login to prevent fixation attacks.

**Operational implications of STATELESS:**
- No sticky sessions needed — any server can handle any request.
- No session replication / distributed session store needed.
- Scale horizontally freely.
- Authentication info must be re-validated on every request (JWT signature + expiry check).
- No logout invalidation on the server side — you "log out" by discarding the token client-side (or rely on expiry).

**Interview trap:** "You set STATELESS but your users complain they get logged out after 15 minutes. Is that the session timeout?" No — there is no session. They are seeing JWT expiry. The fix is a proper refresh token flow, not tweaking session configuration.

**Tags:** stateless, session-creation-policy, security-context-holder-filter, session-fixation, horizontal-scaling

---

## Q-SPRS-012 [bloom: understand] [level: regular]
**Question:** What is `@PreAuthorize` and how does SpEL enable fine-grained authorization beyond simple role checks?

**Model answer:** `@PreAuthorize` is a method-level security annotation that evaluates a SpEL expression before the annotated method executes. If the expression evaluates to `false`, Spring Security throws `AccessDeniedException` (→ 403). Requires `@EnableMethodSecurity` on a `@Configuration` class (replaces the older `@EnableGlobalMethodSecurity(prePostEnabled=true)`).

**SpEL context for security expressions includes:**
- `authentication` — the current `Authentication` object
- `principal` — `authentication.principal`
- `#paramName` — access to method parameter by name (requires `-parameters` compiler flag or `@P`)
- `returnObject` — in `@PostAuthorize`, the returned value

**Examples:**

```java
// Simple role check
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long id) { ... }

// Authority check (no ROLE_ prefix)
@PreAuthorize("hasAuthority('scope:write:profiles')")
public void updateProfile(String profileId) { ... }

// Ownership check — user can only access their own resource
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
public UserDto getUser(Long userId) { ... }

// Post-authorize — check the returned object
@PostAuthorize("returnObject.owner == authentication.name")
public Document getDocument(Long docId) { ... }
```

**`@PostAuthorize` use case:** when you need to verify that the data returned from DB belongs to the requesting user. The method runs first (DB hit), then the authorization check. If it fails, a 403 is thrown and the response is discarded. Use sparingly — you've already done the DB work.

**`@Secured` and `@RolesAllowed`** are older alternatives: no SpEL, role-only checks. Use `@PreAuthorize` for anything new.

**Interview trap:** "Does `@PreAuthorize` work on private methods?" No. Spring Security method security is implemented via AOP proxies (Spring AOP, not AspectJ by default). Private methods are not proxied. Also, self-invocation (calling an annotated method from within the same bean) bypasses the proxy. Common gotcha: `userService.adminMethod()` — if called internally, the security annotation is ignored.

**Tags:** pre-authorize, post-authorize, spel, method-security, enable-method-security, aop-proxy

---

## Q-SPRS-013 [bloom: understand] [level: regular]
**Question:** Explain the structure of a JWT. What are the three parts and what does each contain? What is the difference between HS256 and RS256 signing?

**Model answer:** A JWT (JSON Web Token) is a base64url-encoded, period-delimited string: `header.payload.signature`.

**Header** — JSON object, base64url encoded:
```json
{ "alg": "RS256", "typ": "JWT" }
```
Declares the signing algorithm and token type.

**Payload (claims)** — JSON object, base64url encoded. Contains:
- Standard claims: `sub` (subject/user id), `iss` (issuer), `exp` (expiry Unix timestamp), `iat` (issued at), `jti` (JWT id — unique, used for deny-lists), `aud` (audience)
- Custom claims: roles, scopes, tenant id, carrier code, etc.

The payload is **not encrypted** — it is base64url encoded, meaning anyone can decode it. Never put sensitive data (passwords, PII) in a JWT payload unless the JWT is also encrypted (JWE).

**Signature:**
- For HS256: `HMAC-SHA256(base64url(header) + "." + base64url(payload), sharedSecret)` — symmetric, same key signs and verifies. All services that validate the JWT need the shared secret, which becomes a distribution problem.
- For RS256: `RSA-SHA256(data, privateKey)` — asymmetric. The authorization server signs with a private key; resource servers verify using the public key (fetched from JWKS endpoint). The private key never leaves the auth server. Preferred for distributed architectures.

**Validation steps for a resource server:**
1. Decode and verify signature (via JWKS public key for RS256).
2. Verify `iss` claim matches the expected issuer URI.
3. Verify `exp` has not passed (with configurable clock skew tolerance, e.g. 30s).
4. Verify `aud` claim includes the expected audience identifier.
5. Extract scopes/roles from custom claims and build `GrantedAuthority` list.

**Interview trap:** "You change your RS256 key pair (key rotation). What happens to existing JWTs?" They become invalid immediately — signature verification will fail because the stored `kid` (key ID) won't match any current key in the JWKS. The JWKS endpoint should serve both the old and new key temporarily (overlap period) to allow graceful rotation without mass token invalidation.

**Tags:** jwt, header, payload, claims, hs256, rs256, jwks, signature, base64url

---

## Q-SPRS-014 [bloom: apply] [level: senior]
**Question:** Why can't you revoke a JWT before it expires? What are the practical mitigations and their trade-offs?

**Model answer:** A JWT is self-contained and stateless. The resource server validates it locally using the issuer's public key (RS256) or shared secret (HS256) — there is no call back to the authorization server per request. This is the statelessness win. But it means there is no server-side record to delete. When a user logs out, changes their password, or has their account compromised, the token remains technically valid until `exp`.

**Mitigations and trade-offs:**

| Mitigation | How | Trade-off |
|-----------|-----|-----------|
| **Short expiry** (15-30 min) | Set `exp` TTL short, use refresh tokens for new access tokens | Token must be refreshed frequently; requires refresh token infra |
| **Refresh token rotation** | Issue new access + refresh token on each refresh; old refresh token is one-time use and invalidated | Server-side refresh token state (DB/Redis); solves logout, but not mid-session revocation |
| **jti deny-list** | On logout/revocation, add `jti` to a Redis set. On each request, check if `jti` is in set (only if not expired) | Adds a Redis lookup on every request — partially defeats statelessness; set entries auto-expire at JWT `exp` time |
| **Short-circuit via auth server introspection** | Opaque tokens; resource server calls auth server on every request to check validity | Auth server becomes latency bottleneck and SPOF; kills the scaling benefit |
| **Key rotation** | Rotate RS256 keys immediately; all existing tokens fail signature check | Nuclear option — invalidates ALL active sessions; only for emergencies |

**Recommended production pattern:** short-lived access tokens (15 min) + longer-lived refresh tokens (7-30 days) stored in HttpOnly cookies. Refresh token is stored hashed in DB; on logout, delete the DB record. On refresh, issue new pair (rotation). The `jti` deny-list is an emergency brake for high-value accounts — check Redis only when `jti` appears in a suspect-set pre-filter to keep the happy-path latency impact near zero.

**Interview trap:** "Your CISO says 'we need instant revocation of all tokens.' You're using stateless JWTs. What do you do?" Honest answer: stateless JWTs and instant global revocation are architecturally incompatible without a centralized check. You either: (a) keep JWTs but add a mandatory Redis deny-list check (trades away statelessness for revocability), (b) switch to opaque tokens with introspection (trades away scalability for revocability), or (c) accept that the blast radius is bounded by TTL and invest in making TTL short. If the CISO needs instant revocation, opaque tokens or a hybrid is the honest answer.

**Tags:** jwt, revocation, refresh-token, jti, deny-list, short-expiry, token-rotation, stateless

---

## Q-SPRS-015 [bloom: apply] [level: senior]
**Question:** Walk through the OAuth2 Authorization Code + PKCE flow step by step. Why is PKCE needed and what attack does it prevent?

**Model answer:** **Authorization Code flow with PKCE (RFC 7636)** — the correct flow for user-facing apps (SPAs, mobile apps, web apps):

**Step-by-step:**

1. **Client generates PKCE pair:**
   - `code_verifier`: cryptographically random string (43-128 chars)
   - `code_challenge`: `BASE64URL(SHA256(code_verifier))`

2. **Client redirects user to Authorization Server:**
   ```
   GET /authorize?
     response_type=code
     &client_id=myapp
     &redirect_uri=https://app.example.com/callback
     &scope=openid profile email
     &state=random_csrf_value
     &code_challenge=BASE64URL_SHA256_of_verifier
     &code_challenge_method=S256
   ```

3. **User authenticates and consents** at the Authorization Server (AS).

4. **AS redirects to client with authorization code:**
   ```
   GET /callback?code=AUTH_CODE&state=random_csrf_value
   ```

5. **Client exchanges code for tokens (back-channel, server-to-server):**
   ```
   POST /token
     grant_type=authorization_code
     code=AUTH_CODE
     redirect_uri=https://app.example.com/callback
     client_id=myapp
     code_verifier=ORIGINAL_VERIFIER
   ```
   AS hashes `code_verifier`, compares to stored `code_challenge`. Match = proof that the party exchanging the code is the same one that initiated the flow.

6. **AS returns** `access_token`, `id_token` (OIDC), `refresh_token`.

**Why PKCE:**
Without PKCE, a malicious app on the same device (or a man-in-the-middle on a custom URL scheme redirect) could steal the authorization code. The code alone was enough to get tokens. PKCE adds proof-of-possession: only the initiator has the `code_verifier`. The stolen code is useless without it.

**Public vs confidential clients:**
- **Confidential client** (server-side web app): can store a `client_secret`; adds it to the token exchange. PKCE is still recommended (defense-in-depth).
- **Public client** (SPA, mobile): cannot safely store a `client_secret`. PKCE is mandatory.

**Interview trap:** "You're building a server-to-server API integration (no user). Should you use Authorization Code + PKCE?" No. Use **Client Credentials** grant — no user in the loop, the client authenticates directly with `client_id` + `client_secret` and gets an access token scoped to what that client is allowed. Authorization Code is for delegated user authorization.

**Tags:** oauth2, authorization-code, pkce, code-challenge, code-verifier, public-client, confidential-client, oidc

---

## Q-SPRS-016 [bloom: apply] [level: senior]
**Question:** What is the difference between an OAuth2 Resource Server and an OAuth2 Client in Spring Security? How do you configure a Resource Server for JWT validation in Spring Boot?

**Model answer:** **OAuth2 Resource Server:** your API that holds the protected resources. It does NOT redirect users or issue tokens. It validates the incoming `Authorization: Bearer <JWT>` token on each request and grants/denies access based on the token's scopes/claims.

**OAuth2 Client:** your app that wants to access a protected resource on behalf of a user. It handles redirecting to the Authorization Server, exchanging codes for tokens, storing/refreshing tokens, and attaching them to outbound requests.

**Resource server configuration (Spring Boot 3 / Spring Security 6):**

Minimal `application.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/myrealm
```
Spring Security auto-discovers the JWKS URI from the OIDC discovery endpoint (`issuer-uri/.well-known/openid-configuration`). It fetches the public keys and caches them, refreshing on encountering an unknown `kid`.

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/public/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtAuthenticationConverter(customConverter())
            )
        )
        .build();
}
```

**JWT validation performed automatically:**
- Signature verification via JWKS public key (matches `kid` in token header)
- `iss` claim vs configured issuer
- `exp` claim (token not expired, with 30s clock skew tolerance by default)

**Audience validation (not automatic — must be added):**
```yaml
spring.security.oauth2.resourceserver.jwt.audiences: my-api
```
Or programmatically via `JwtDecoder` customization. Without audience validation, a token issued for a different client in the same realm is accepted — a real security hole in multi-client Keycloak setups.

**Interview trap:** "You configure `issuer-uri` and everything works in dev. In prod the Authorization Server is at an internal hostname your service can't reach. What happens?" The app fails to start — Spring Security eagerly fetches the OIDC discovery document at startup to build the `JwtDecoder`. Fix: use `jwk-set-uri` directly (skips the discovery call, but you lose auto-rotation) or ensure the internal hostname is resolvable from the service, or use lazy initialization.

**Tags:** resource-server, oauth2-client, jwt-decoder, jwks, issuer-uri, audience-validation, spring-boot-3

---

## Q-SPRS-017 [bloom: apply] [level: senior]
**Question:** How does OIDC extend OAuth2? What is the `id_token` and what guarantees does it provide that an access token does not?

**Model answer:** OAuth2 is an **authorization** framework — it answers "can this client access this resource?" It says nothing about who the user is. OpenID Connect (OIDC) is an identity layer on top of OAuth2 that adds **authentication** — it answers "who is this user?"

**What OIDC adds:**
1. **`id_token`**: a JWT returned alongside the `access_token` in the token response. Contains standard identity claims: `sub` (user ID), `name`, `email`, `email_verified`, `iat`, `exp`, `iss`, `aud`, `nonce`.
2. **`openid` scope**: requesting `scope=openid` is what triggers OIDC; without it you get OAuth2 but not an `id_token`.
3. **`nonce`**: a client-generated random value included in the auth request and mirrored in the `id_token`. Prevents replay attacks and ensures the token was issued in response to this specific login request.
4. **UserInfo endpoint**: `GET /userinfo` with the access token returns additional profile claims if the `profile`, `email` scopes were requested.

**id_token vs access_token:**
| | id_token | access_token |
|-|----------|-------------|
| Purpose | Prove user authenticated | Authorize API calls |
| Audience | The client app | The resource server(s) |
| Must validate `aud` | Against your `client_id` | Against your API's audience |
| Should you send to API? | No — it's for the client | Yes — `Authorization: Bearer` |
| Standard claims | Mandated by OIDC spec | Implementation-specific |

A critical mistake: forwarding the `id_token` to a resource server instead of the `access_token`. The resource server would accept it (it's a valid JWT) but the `aud` claim is wrong (your client ID, not the API audience) — or it would fail audience validation and return 403.

**Interview trap:** "You use the `id_token` to identify the user in your session. Is that safe?" Only if you validate the `nonce`, `iss`, `aud` (must be your `client_id`), and `exp`. The `sub` claim is the stable, opaque, unique user identifier — not `email` (which can change) and not `name`. Always use `sub` as the application-internal user reference.

**Tags:** oidc, id-token, access-token, openid-scope, nonce, userinfo, oauth2-extension

---

## Q-SPRS-018 [bloom: apply] [level: senior]
**Question:** How do you inject a custom JWT filter for API-key or custom-token authentication into the Spring Security filter chain at the correct position?

**Model answer:** You extend `OncePerRequestFilter` (which guarantees single execution per request, even with forward/include) and add it to the chain using `addFilterBefore()` or `addFilterAfter()` relative to a built-in filter.

**Custom filter:**
```java
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenValidator validator;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res); // no token — let the chain continue
            return;
        }
        try {
            Authentication auth = validator.validate(header.substring(7));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (InvalidTokenException e) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return; // stop chain
        }
        chain.doFilter(req, res);
    }
}
```

**Wiring into the chain:**
```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

Position reasoning: `UsernamePasswordAuthenticationFilter` (form login) sits at ~order 600. Adding a JWT filter before it means JWT auth runs first; if it succeeds, form-login filter sees an already-authenticated context and skips its logic.

**Key correctness rules:**
1. Always call `chain.doFilter()` unless you are terminating the request (error response). Forgetting this hangs the request.
2. On success, set the `Authentication` in `SecurityContextHolder` BEFORE calling `chain.doFilter()`.
3. Catch token exceptions and send the error response; do NOT rethrow as unchecked (the container error page would give a confusing 500).
4. Clear `SecurityContextHolder` if you set it and then encounter an error — or rely on `SecurityContextHolderFilter` to clear it at end of request.

**Interview trap:** "You implement a `GenericFilterBean` instead of `OncePerRequestFilter`. Your filter runs twice on some requests — why?" `GenericFilterBean` has no protection against being called multiple times when the servlet container forwards the request internally (e.g., error dispatch). `OncePerRequestFilter` uses a request attribute flag to ensure single execution. Always prefer `OncePerRequestFilter`.

**Tags:** once-per-request-filter, custom-filter, add-filter-before, jwt-filter, filter-chain, filter-ordering

---

## Q-SPRS-019 [bloom: apply] [level: senior]
**Question:** You have a Spring Boot REST API deployed horizontally behind a load balancer. Authentication is JWT-based. Describe the complete security configuration: session management, CSRF, CORS, JWT validation, and method-level authorization. Show the key code.

**Model answer:** Complete stateless REST API security configuration:

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)              // stateless JWT, no cookies
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter()))
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(
                    (req, res, e) -> res.sendError(401, "Unauthorized"))
                .accessDeniedHandler(
                    (req, res, e) -> res.sendError(403, "Forbidden"))
            )
            .build();
    }

    @Bean
    CorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of("https://app.example.com"));
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }

    @Bean
    JwtAuthenticationConverter converter() {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("scope");
        gac.setAuthorityPrefix("SCOPE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(gac);
        return conv;
    }
}
```

**Configuration rationale:**
- `csrf disabled`: safe because auth uses `Authorization: Bearer`, not cookies. Attacker cannot forge the header.
- `STATELESS`: no server-side session state; any pod handles any request.
- `OPTIONS permitAll`: allows CORS preflight through before the JWT check.
- `authenticationEntryPoint`: returns clean 401 JSON-friendly error instead of Spring's HTML 401 redirect.
- `@EnableMethodSecurity` + `@PreAuthorize` on service layer for fine-grained authorization.

**Service layer:**
```java
@PreAuthorize("hasAuthority('SCOPE_write:profiles') and #userId == authentication.name")
public void updateProfile(String userId, ProfileDto dto) { ... }
```

**Interview trap:** "Why `permitAll()` on OPTIONS rather than configuring CSRF exemption for OPTIONS?" Because CSRF is already disabled for the entire chain. The OPTIONS `permitAll()` is about authorization, not CSRF — without it, an unauthenticated preflight request would be rejected by the `AuthorizationFilter` with 403 before returning the CORS headers.

**Tags:** stateless, csrf, cors, session-management, oauth2-resource-server, enable-method-security, production-config

---

## Q-SPRS-020 [bloom: apply] [level: senior]
**Question:** How do you write meaningful tests for Spring Security configuration? What layers do you test and what tools does Spring provide?

**Model answer:** Three testing layers, each catching different bugs:

**1. Unit / slice tests with `@WebMvcTest` + security mocking:**
Tests URL-level authorization rules without a full context. `@WebMvcTest` loads only the web layer + Security config.

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired MockMvc mvc;

    @Test
    void missingToken_returns401() throws Exception {
        mvc.perform(get("/api/users/1"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUser_cannotDeleteUser_returns403() throws Exception {
        mvc.perform(delete("/api/users/1"))
           .andExpect(status().isForbidden());
    }

    @Test
    void validJwt_withAdminScope_returns200() throws Exception {
        mvc.perform(get("/api/users/1")
               .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
           .andExpect(status().isOk());
    }
}
```

`SecurityMockMvcRequestPostProcessors.jwt()` (from `spring-security-test`) injects a mock JWT `Authentication` directly, bypassing actual JWT decoding — fast, no network.

**2. Method security unit tests:**
Test `@PreAuthorize` on service beans in isolation with `@SpringBootTest(classes = ...)` or `@ExtendWith(SpringExtension.class)`:

```java
@SpringBootTest
class ProfileServiceSecurityTest {

    @Autowired ProfileService profileService;

    @Test
    @WithMockUser(authorities = "SCOPE_write:profiles")
    void ownerCanUpdate() {
        assertDoesNotThrow(() -> profileService.updateProfile("user1", dto));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_write:profiles")
    void nonOwner_isRejected() {
        assertThrows(AccessDeniedException.class,
            () -> profileService.updateProfile("user2", dto)); // authenticated as user1
    }
}
```

**3. Integration tests with real JWT issuance (Testcontainers + Keycloak / Spring Authorization Server):**
Actually runs the full OAuth2 flow: client credentials → real JWT → call API → verify 200/401/403.

```java
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OAuth2IntegrationTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer();

    @Test
    void validClientCredentials_accessProtectedEndpoint() {
        String token = obtainTokenFromKeycloak(keycloak.getAuthServerUrl());
        webTestClient.get().uri("/api/protected")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk();
    }
}
```
These tests caught real bugs (audience validation misconfiguration, clock skew, converter errors) that mocked tests missed.

**Interview trap:** "`@WithMockUser` sets up the authentication but my `@PreAuthorize` that checks `authentication.principal.id` fails — why?" `@WithMockUser` creates a `UsernamePasswordAuthenticationToken` with a `User` principal. If your `@PreAuthorize` accesses `.id` on the principal, and `User` has no `id` field, the SpEL expression throws. For JWT-based auth, use `@WithMockJwt` or the `jwt()` request post-processor which creates a `JwtAuthenticationToken` matching your production principal type.

**Tags:** testing, web-mvc-test, with-mock-user, jwt-post-processor, testcontainers, keycloak, method-security-test

---

## Q-SPRS-021 [bloom: analyze] [level: senior]
**Question:** A user reports they still have access to admin functionality 30 minutes after their account was downgraded from ADMIN to USER role in the database. Your app uses JWTs with a 60-minute expiry. Diagnose the root cause and propose a fix without switching to opaque tokens.

**Model answer:** **Root cause: stale JWT claims.** When the user logged in, the JWT was issued with `roles: ["ADMIN"]` baked into the payload. Spring Security validates the signature and expiry on every request, but it does NOT re-read the database on each request — it trusts the JWT claims until expiry. The role change in the DB has no effect on the active JWT.

**Diagnosis chain:**
1. JWT expiry is 60 min → user logged in at T=0, role changed at T=30, but JWT with `ADMIN` role is valid until T=60.
2. No server-side session state → no session invalidation possible.
3. DB role is correct, but the resource server only sees the JWT payload.

**Fixes (without opaque tokens), ranked by complexity:**

| Fix | How | Trade-off |
|----|-----|-----------|
| **Shorten JWT TTL** | Reduce access token expiry to 5-15 min | Role change takes effect within 15 min max; requires frequent refresh |
| **jti deny-list + role-change event** | On role change, add current `jti` to Redis deny-list; resource server checks deny-list per request | Adds Redis lookup; need deny-list TTL = JWT expiry |
| **Claim refresh via refresh token** | On refresh (every 15 min), re-issue access token with fresh claims from DB | Roles lag by at most 1 refresh cycle; clean, widely used |
| **Database-backed token version** | Add `token_version` to User entity; embed in JWT; on each request, check `token_version` in DB | Adds DB call per request — trades away statelessness; effective but costly |

**Recommended:**
Short TTL (15 min) + refresh token rotation. On each refresh, load fresh user claims from DB and embed in new access token. Role downgrade takes effect within at most 15 minutes — acceptable for most apps. For security-critical cases (instant revocation required), the `jti` deny-list populated by a role-change event is the minimum overhead solution.

**Architectural note:** this is the fundamental tension in stateless JWT design. "Stateless" means "trust the token" — but the token's claims become stale. Every team that doesn't plan for this hits it in production. The right answer is a deliberate TTL policy, not just "use JWT."

**Interview trap:** "The team suggests adding a DB call to validate the role on every request — isn't that the right fix?" It works but defeats statelessness. If you're hitting the DB on every request anyway, you might as well use opaque tokens with introspection, which is a cleaner architecture for that use case. The JWT statefulness tradeoff must be a conscious design decision.

**Tags:** jwt-staleness, role-change, short-expiry, deny-list, refresh-token, stateless-tradeoff, incident-analysis

---

## Q-SPRS-022 [bloom: analyze] [level: senior]
**Question:** You need to enforce tenant isolation in a multi-tenant SaaS API: users from tenant A must never access tenant B's data, regardless of their roles. How do you implement this in Spring Security + Spring Data JPA?

**Model answer:** Tenant isolation is a cross-cutting concern that must be enforced at multiple layers — defense in depth.

**Layer 1: JWT claim — tenant ID in token**
The Authorization Server embeds `tenant_id` in the JWT (custom claim). The `JwtAuthenticationConverter` extracts it and makes it available on the principal:

```java
JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
conv.setJwtGrantedAuthoritiesConverter(jwt -> {
    String tenantId = jwt.getClaimAsString("tenant_id");
    // Store in a custom Authentication or ThreadLocal accessible to the service layer
    TenantContext.set(tenantId);
    return scopeConverter.convert(jwt);
});
```

**Layer 2: Method security — @PreAuthorize with tenant check**
```java
@PreAuthorize("@tenantGuard.canAccess(authentication, #resourceId)")
public Resource getResource(String resourceId) { ... }
```

```java
@Component("tenantGuard")
public class TenantGuard {
    public boolean canAccess(Authentication auth, String resourceId) {
        String tokenTenant = extractTenantId(auth); // from JWT claims
        Resource r = resourceRepo.findById(resourceId).orElseThrow();
        return tokenTenant.equals(r.getTenantId());
    }
}
```

**Layer 3: Data layer — Hibernate Filters or Spring Data @Query**
Use Hibernate multi-tenancy or a `@Filter` on all entity classes:

```java
@Entity
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Order { ... }
```

Enable on each session via an interceptor that reads `TenantContext`. This prevents accidental data leaks even if method-level guards are misconfigured.

**Layer 4: URL matcher (coarse)**
If tenant ID is path-based (`/tenants/{tenantId}/...`), validate it at the filter level before authorization runs.

**Key principle:** never rely on a single enforcement point. Method security can be bypassed (self-invocation, internal calls). DB-layer filtering is the safety net that catches what slips through.

**Interview trap:** "Isn't it enough to just filter all queries by `tenantId` in the service layer?" No — manual service-layer filtering is brittle and must be remembered for every new query. DB-level Hibernate filters are declarative and enforce by default; you opt out explicitly rather than opting in. The declarative approach has fewer failure modes.

**Tags:** multi-tenancy, tenant-isolation, jwt-claims, pre-authorize, hibernate-filter, defense-in-depth, data-isolation

---

## Q-SPRS-023 [bloom: analyze] [level: master]
**Question:** Spring Security's `SecurityContextHolder` uses `ThreadLocal` by default. Explain what happens in a reactive (WebFlux) application, why `ThreadLocal` breaks, and how Spring Security solves it.

**Model answer:** `ThreadLocal` fundamentally breaks in reactive programming because a reactive pipeline can execute on multiple threads (different event-loop threads per operator). A single HTTP request may have its `SecurityContext` written on thread A and read on thread B — and `ThreadLocal` is thread-scoped, so thread B sees null.

**Spring Security's solution for WebFlux: Reactor Context**

Spring Security WebFlux uses `ReactorContextWebFilter` (instead of servlet `SecurityContextHolderFilter`). It:
1. Loads `SecurityContext` from the session (stateful) or from the request (e.g., JWT bearer filter in `ReactiveSecurityContextHolder`).
2. Stores it in **Reactor's `Context`** (an immutable, request-scoped key-value map that propagates through the reactive pipeline automatically, regardless of thread switches).

```java
// Reading security context in WebFlux
Mono<Authentication> auth = ReactiveSecurityContextHolder
    .getContext()
    .map(SecurityContext::getAuthentication);
```

`ReactiveSecurityContextHolder` reads from Reactor `Context` — NOT from `ThreadLocal`. Thread-safe across all reactive operator switches.

**If you mix servlet + reactive incorrectly:**
Calling `SecurityContextHolder.getContext()` (ThreadLocal) from a reactive chain produces null. This is the top WebFlux security bug — developers familiar with servlet Spring assume `SecurityContextHolder` works everywhere.

**Custom reactive filter:**
```java
public class JwtReactiveFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("Authorization"))
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .flatMap(this::validateToken)
            .flatMap(auth ->
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            )
            .switchIfEmpty(chain.filter(exchange));
    }
}
```

Note `contextWrite` — this injects the `SecurityContext` into the downstream Reactor `Context`.

**Interview trap:** "Can you call `SecurityContextHolder.getContext()` inside a `@Service` method when using WebFlux?" No, unless the service method was called from a servlet thread (mixed servlet/reactive). In a fully reactive app, always use `ReactiveSecurityContextHolder.getContext()` and propagate through the reactive chain. The compile-time API looks the same; the runtime behavior is completely different.

**Tags:** webflux, reactive, reactor-context, thread-local, reactive-security-context-holder, web-filter, context-write

---

## Q-SPRS-024 [bloom: analyze] [level: master]
**Question:** You are designing a microservices system. Describe how to propagate authentication context from an API gateway to downstream services, covering both JWT forwarding and service-to-service (machine) auth. What are the security risks of each approach?

**Model answer:** Two distinct auth problems: user identity propagation (who is the end user?) and service identity (which service is calling?).

**Pattern 1: JWT forwarding (pass-through)**
API gateway validates the JWT (`Authorization: Bearer`). Downstream services also validate the same JWT independently against the JWKS endpoint.

```
Client → [API GW: validate JWT] → Service A → Service B
         forwards Authorization header unchanged
```

Pros: each service independently verifies identity; no single point of trust after the gateway.
Cons: JWT must be valid for all downstream services' audiences; large JWTs increase per-request overhead; if any service skips validation and trusts forwarded headers, it becomes a security hole.

**Pattern 2: Token exchange (RFC 8693)**
Gateway exchanges the user JWT for a service-specific token narrowed to the needed scopes. Each downstream service gets a token scoped only to what it needs.

**Pattern 3: Service identity (mTLS + service JWT)**
For service-to-service calls inside the mesh: each service has its own identity. Options:
- **mTLS**: each service presents a TLS client certificate. The receiving service validates the certificate chain. Identity is at the transport layer. Used in service meshes (Istio, Linkerd).
- **Service JWT (client credentials)**: each service obtains a JWT from the Authorization Server using its `client_id` + `client_secret`. Passed as `Authorization: Bearer` on outbound calls. Spring Security's `OAuth2AuthorizedClientManager` handles token acquisition and refresh.

**Security risks:**

| Risk | Mitigation |
|------|-----------|
| Gateway-bypass: attacker calls Service A directly | Network policy / mTLS; services reject requests without valid service identity |
| Forwarding user JWT to untrusted downstream | Validate audience in downstream; use token exchange to narrow scope |
| Trusting internal headers blindly (`X-User-Id: admin`) | Never trust unvalidated headers from outside the network boundary; validate JWT signature always |
| Service credential leakage | Rotate client secrets; prefer mTLS which eliminates static secrets |
| Confused deputy | Service acting on behalf of user without re-checking user permissions in service B |

**Interview trap:** "Your API gateway strips the `Authorization` header and adds `X-User-Id: <userId>` and `X-User-Roles: ADMIN`. Service B trusts these headers. What's the risk?" Any caller who can reach Service B directly (or spoof the gateway) can set arbitrary headers and impersonate any user. This "internal header trust" is a classic confused deputy / header injection vulnerability. Fix: Service B must validate a cryptographically signed token (JWT), not trust plain headers.

**Tags:** microservices, jwt-forwarding, service-to-service, mtls, client-credentials, token-exchange, gateway-security, header-injection

---

## Q-SPRS-025 [bloom: analyze] [level: master]
**Question:** Explain Spring Security's `ExceptionTranslationFilter`. What is the difference between `AuthenticationEntryPoint` and `AccessDeniedHandler`, and how do you customize them for a JSON REST API that must not return HTML error pages?

**Model answer:** `ExceptionTranslationFilter` sits between the security filters and `AuthorizationFilter`. It catches two types of exceptions thrown during the filter chain or authorization decision:

1. **`AuthenticationException`** — thrown when there is no authentication or credentials are invalid (401 territory). Handled by `AuthenticationEntryPoint`.
2. **`AccessDeniedException`** — thrown when authentication exists but the user lacks permission (403 territory). Handled by `AccessDeniedHandler`.

Special case: if the current user is anonymous (no credentials presented) and `AccessDeniedException` is thrown, `ExceptionTranslationFilter` treats it as an authentication failure and delegates to `AuthenticationEntryPoint` instead. This is why a missing token on a secured endpoint returns 401, not 403.

**Default behavior (wrong for REST APIs):**
- `LoginUrlAuthenticationEntryPoint`: redirects to `/login` (HTML form). Returns 302 redirect — catastrophic for an API client expecting JSON.
- `AccessDeniedHandlerImpl`: redirects to a 403 error page or returns Spring's default HTML error response.

**Custom JSON handlers:**
```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        // ... other config
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("""
                    {"error":"unauthorized","message":"%s"}
                    """.formatted(authException.getMessage()));
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("""
                    {"error":"forbidden","message":"%s"}
                    """.formatted(accessDeniedException.getMessage()));
            })
        )
        .build();
}
```

Better practice: inject an `ObjectMapper` and serialize a proper error DTO rather than raw string interpolation (avoids JSON injection if the message contains special chars).

**Error attribution subtlety:**
- Token present but expired → `AuthenticationException` → 401 (EntryPoint)
- Token valid but wrong scope → `AccessDeniedException` → 403 (DeniedHandler)
- No token at all → anonymous → `AccessDeniedException` → treated as AuthenticationException → 401 (EntryPoint)

**Interview trap:** "A user with valid credentials calls an endpoint they're not allowed to access. They get 302 to `/login` instead of 403. Your API clients are breaking. Why and how do you fix it?" The default `AccessDeniedHandler` (or `AuthenticationEntryPoint`) is configured to redirect. The fix is wiring a custom handler that writes a JSON 403/401 directly to the response without a redirect, as shown above. This is a very common production misconfiguration when developers add Spring Security to an existing app and forget that the default handlers are HTML/form-oriented.

**Tags:** exception-translation-filter, authentication-entry-point, access-denied-handler, json-error, rest-api, 401, 403

---

## Q-SPRS-026 [bloom: analyze] [level: master]
**Question:** Spring Security 6 removed `WebSecurityConfigurerAdapter`. What was the old pattern, what replaced it, and what are the deeper architectural improvements in Spring Security 5.7–6?

**Model answer:** **Old pattern (pre-5.7, now removed):**
```java
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/public/**").permitAll()
            .anyRequest().authenticated();
    }
}
```
Problems: inheritance-based configuration, method chaining with `.and()` making it easy to lose context, single `configure()` method that grows without bound, no clean way to have multiple independent `SecurityFilterChain` beans.

**New pattern (Spring Security 5.7+ / 6, Spring Boot 3):**
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain admin(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(auth -> auth
                .anyRequest().hasRole("ADMIN")
            )
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

**Architectural improvements in Spring Security 5.7–6:**

1. **Component-based, not inheritance-based**: each `@Bean SecurityFilterChain` is independent and testable in isolation.
2. **Lambda DSL**: nested lambdas replace `.and()` chains. Each subsystem (csrf, sessionManagement, oauth2ResourceServer) is configured in its own closure — more readable, compiler-checked.
3. **Multiple filter chains with matchers**: clean separation of concerns per URL namespace.
4. **`AuthorizationFilter` replaces `FilterSecurityInterceptor`**: unified URL authorization (supports `@Order`, programmatic `AuthorizationManager`).
5. **`@EnableMethodSecurity` replaces `@EnableGlobalMethodSecurity`**: enables `@PreAuthorize`/`@PostAuthorize` with correct AOP ordering and `@AuthorizationEventPublisher` support.
6. **`SecurityContextHolderFilter` replaces `SecurityContextPersistenceFilter`**: explicit opt-in to session-backed context persistence; default no-op for stateless setups.
7. **`authorizeHttpRequests` with `requestMatchers`**: replaces deprecated `authorizeRequests` + `antMatchers` (which used `AntPathRequestMatcher` in all cases; new API uses `PathPatternRequestMatcher` by default, consistent with MVC routing).

**Interview trap:** "Can I still extend `WebSecurityConfigurerAdapter` if I add it to the classpath?" In Spring Security 6 / Spring Boot 3, the class is removed from the `spring-security-config` jar entirely. It does not exist. The migration is mandatory, not optional. Any answer that says "you can still use it if you add the old jar" is wrong in a Spring Boot 3 project.

**Tags:** web-security-configurer-adapter, lambda-dsl, spring-security-6, multiple-filter-chains, authorization-filter, enable-method-security, spring-boot-3-migration

---

## Q-SPRS-027 [bloom: analyze] [level: master]
**Question:** What security headers does Spring Security add by default, and when would you customize or disable them? Include HSTS, X-Frame-Options, CSP, and X-Content-Type-Options.

**Model answer:** Spring Security's `HeaderWriterFilter` writes security headers by default when `http.headers()` is not disabled. Understanding what's added and why matters for production deployment — especially behind reverse proxies.

**Default headers added:**

| Header | Default Value | Purpose |
|--------|--------------|---------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | HSTS: forces HTTPS for 1 year. Browser will not make HTTP requests to this domain. |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME-type sniffing. Browser uses declared `Content-Type`, not guessing. |
| `X-Frame-Options` | `DENY` | Prevents the page from being loaded in an iframe (clickjacking protection). |
| `X-XSS-Protection` | `0` (SS 6) | Modern browsers have built-in XSS filters; `0` disables the old IE XSS filter (which had bypass vulnerabilities). |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` | Prevents caching of authenticated responses. |
| `Content-Security-Policy` | NOT set by default | Must be configured manually; controls which scripts/styles/sources are allowed. |

**HSTS caveat:** If your app is behind a TLS-terminating load balancer that already adds HSTS, having the app add it too is harmless but redundant. However: if the app is on HTTP internally (LB offloads TLS) and you expose the app directly in dev on HTTP, HSTS in the response will cause the browser to refuse to talk to the app on HTTP for a year — HSTS is "sticky." In a LB-behind scenario, consider disabling app-level HSTS and letting the LB/CDN add it.

**X-Frame-Options — common legitimate need to change:** embedding your own app in an iframe (e.g., dashboard embedded in a portal). Change to `SAMEORIGIN`:
```java
.headers(h -> h.frameOptions(f -> f.sameOrigin()))
```

**Content-Security-Policy — must configure manually:**
```java
.headers(h -> h
    .contentSecurityPolicy(csp ->
        csp.policyDirectives("default-src 'self'; script-src 'self'; img-src 'self' data:"))
)
```
CSP is the strongest XSS mitigation. It tells the browser which sources are trusted for scripts, styles, images, etc. Misconfigured CSP (e.g., `unsafe-inline`) is as bad as no CSP. Testing CSP in report-only mode before enforcing is standard practice.

**Disabling all headers (dangerous, only for embedded non-browser contexts):**
```java
.headers(HeadersConfigurer::disable)
```
Only acceptable for non-browser APIs (machine-to-machine) where headers are overhead with no browser to honor them.

**Interview trap:** "You deploy behind a CDN that adds its own `Strict-Transport-Security` header. Spring Security also adds it. Is there a problem?" Two `Strict-Transport-Security` headers may cause unpredictable browser behavior (specs say take the longest `max-age`, but behavior varies). Best practice: disable HSTS at the app level when the CDN/LB handles it, to avoid duplicate headers. Also, if your app is on HTTP internally and the CDN adds HTTPS, HSTS from the app on the internal HTTP connection is semantically wrong.

**Tags:** security-headers, hsts, x-frame-options, csp, content-security-policy, x-content-type-options, header-writer-filter, clickjacking

