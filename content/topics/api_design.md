# API Design — question bank

> This topic covers the principles, patterns, and operational realities of designing HTTP APIs. For a senior backend engineer (Java/Kotlin), this means more than "which verb for which action" — it means knowing the full safe/idempotent matrix, when to pick REST vs gRPC vs GraphQL, how versioning strategies age in production, how to make POST safe to retry, how to paginate at scale, and how to make errors machine-readable. Interviewers probe whether you understand the *why* behind each convention and what breaks when you cut corners.

## Scope

- HTTP method semantics: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
- Safe vs idempotent matrix (which methods are which, and why it matters for retries)
- Status codes: 201, 202, 204, 400, 401, 403, 404, 409, 422, 429 and the tricky distinctions
- Richardson Maturity Model: Level 0 through Level 3
- HATEOAS: concept, practical adoption, trade-offs
- API versioning: URI path, Accept header, media-type; deprecation policy
- Idempotency keys for POST (Stripe-style pattern)
- Pagination: offset/limit vs cursor/keyset — performance and consistency implications
- Filtering, sorting, sparse fieldsets
- Error format: RFC 7807 ProblemDetail
- HTTP caching: ETag, Cache-Control directives, conditional requests (If-None-Match, If-Modified-Since)
- Content negotiation: Accept, Accept-Language, Vary header
- REST vs gRPC: protobuf, HTTP/2, streaming use cases
- REST vs GraphQL: over/under-fetching, N+1 resolver problem, schema, caching challenge
- OpenAPI / contract-first design
- Rate limiting: algorithms, 429 + Retry-After, distributed implementation
- Webhooks: delivery guarantees, security, retry
- DTO vs entity: why the separation exists and what goes wrong without it
- Request validation: Bean Validation, RFC 7807 error responses

---

## Q-API-001 [bloom: recall] [level: junior]
**Question:** List the main HTTP methods and describe what each one does.
**Model answer:** **GET** — retrieve a resource. Safe and idempotent. Body in GET is non-standard; many proxies and clients discard it. **POST** — create a resource or execute an action. Neither safe nor idempotent (two identical POSTs typically create two resources). **PUT** — full replacement of a resource. Idempotent (PUT twice = same final state). **PATCH** — partial modification. Not necessarily idempotent (depends on the patch format — JSON Merge Patch is idempotent, JSON Patch with `op:add` to an array may not be). **DELETE** — remove a resource. Idempotent (a second DELETE returns 404 or 204, but the end state is the same). **HEAD** — identical to GET but no response body; used for metadata checks and cache validation. **OPTIONS** — asks what methods are supported; CORS preflight is the main real-world use case.

**Safe/idempotent matrix:**
| Method  | Safe | Idempotent |
|---------|------|------------|
| GET     | yes  | yes        |
| HEAD    | yes  | yes        |
| OPTIONS | yes  | yes        |
| PUT     | no   | yes        |
| DELETE  | no   | yes        |
| POST    | no   | no         |
| PATCH   | no   | no*        |

*PATCH idempotency depends on payload format.

**Interview trap:** The classic "PUT vs POST" question. Rule of thumb: use PUT when the client controls the resource URL (`PUT /products/123`), use POST when the server assigns it (`POST /products` returns 201 with Location header). PUT is idempotent; POST is not.
**Tags:** http-methods, basics, idempotency

---

## Q-API-002 [bloom: recall] [level: junior]
**Question:** What is idempotency, and why does it matter in REST API design?
**Model answer:** An idempotent operation is one where making the same call multiple times produces the same system state as making it once. The response may differ (e.g., first DELETE returns 204, second returns 404) but the resource state is identical.

**Why it matters:** networks are unreliable. When a client sends PUT and gets a timeout, it has no way to know whether the server processed the request. With an idempotent method, it can safely retry — the worst case is a redundant operation, not a duplicate. With a non-idempotent method like POST, a naive retry creates a duplicate record.

**Safe vs idempotent distinction:** "safe" means no observable side effects (read-only). All safe methods are idempotent, but not all idempotent methods are safe (PUT and DELETE modify state but can be retried safely).

**Interview trap:** "GET is always idempotent" — at the HTTP level yes, but if GET has side effects (analytics tracking, download-once tokens), the state-change definition breaks down. "Idempotent = same response" — false. A second PUT on an unchanged resource returns 200 again; that is idempotent even if it returns a different ETag than a hypothetical non-matching request.
**Tags:** idempotency, semantics, http-methods

---

## Q-API-003 [bloom: recall] [level: junior]
**Question:** Name and explain the most important HTTP status codes across the 2xx, 4xx, and 5xx ranges.
**Model answer:** **2xx Success:** 200 OK (generic success), 201 Created (new resource created — typically for POST/PUT, should include a `Location` header with the URL of the new resource), 202 Accepted (accepted for async processing; response will come later), 204 No Content (success, no response body — common for DELETE and PUT without a return payload).

**4xx Client Error:** 400 Bad Request (malformed request — unparseable JSON, missing required field at syntax level), 401 Unauthorized (misleadingly named — means *unauthenticated*: no credentials or invalid credentials), 403 Forbidden (authenticated but not permitted), 404 Not Found, 405 Method Not Allowed, 409 Conflict (concurrent modification conflict, duplicate key, version conflict), 422 Unprocessable Entity (request is well-formed but fails business validation — the right code when `POST /orders` gets `quantity: -5`), 429 Too Many Requests (rate limit hit; must include `Retry-After`).

**5xx Server Error:** 500 Internal Server Error (generic crash), 502 Bad Gateway (upstream failure), 503 Service Unavailable (include `Retry-After`), 504 Gateway Timeout.

**Other notable:** 304 Not Modified (conditional GET, cache valid), 307 Temporary Redirect (preserves method — unlike 302), 410 Gone (permanently deleted, harder than 404).

**Interview trap:** 401 vs 403 — 401 means "I don't know who you are", 403 means "I know who you are but you can't do this". 422 vs 400 — 400 is for structurally invalid requests (can't parse it at all); 422 is for requests that parsed fine but violated business rules.
**Tags:** http-codes, basics

---

## Q-API-004 [bloom: recall] [level: junior]
**Question:** What is the Richardson Maturity Model?
**Model answer:** A model that grades how "RESTful" an HTTP API is, in four levels:

- **Level 0 — The Swamp of POX:** a single endpoint accepting everything, HTTP used as a tunnel (e.g., SOAP, XML-RPC). The verb and URL carry no meaning.
- **Level 1 — Resources:** multiple URIs, one per resource/concept (`/products`, `/orders`). But still ignores HTTP verbs — everything goes over POST or GET.
- **Level 2 — HTTP Verbs:** uses GET/POST/PUT/PATCH/DELETE with their standard semantics, and returns appropriate status codes. This is where most "REST" APIs live in practice.
- **Level 3 — HATEOAS:** responses include hyperlinks that describe available actions. Clients navigate the API by following links rather than constructing URLs.

Roy Fielding (the author of the REST dissertation) has stated that Level 2 without HATEOAS is not actually REST — it is "HTTP API". In daily industry usage "REST" typically means Level 2.

**Interview trap:** "Is Level 3 HATEOAS realistic?" — Mostly no. HATEOAS increases payload size, and clients generally hardcode flows anyway. The practical value is looser coupling when URLs change. Most production systems are Level 2 with OpenAPI for contract discovery.
**Tags:** richardson-maturity-model, hateoas, rest-levels

---

## Q-API-005 [bloom: recall] [level: junior]
**Question:** What is HATEOAS and what problem does it solve?
**Model answer:** HATEOAS stands for "Hypermedia As The Engine Of Application State." In a Level 3 REST API, each response includes links that tell the client what actions are available next. For example:

```json
{
  "id": 123,
  "name": "Widget",
  "price": 99.99,
  "_links": {
    "self":    { "href": "/products/123" },
    "prices":  { "href": "/products/123/prices" },
    "edit":    { "href": "/products/123", "method": "PUT" },
    "delete":  { "href": "/products/123", "method": "DELETE" }
  }
}
```

The client does not hardcode `/products/123/prices` — it discovers it from the `_links.prices` field. **Problem solved:** when the server changes a URL, the client still works as long as it follows the link. The API becomes self-describing.

**Common formats:** HAL (Hypertext Application Language) uses `_links`; JSON:API uses `links`; Siren adds `actions`.

**Why it is rarely used in practice:** clients hardcode flows for performance and simplicity; HATEOAS increases payload size; and caching becomes harder when link URLs change. Most teams stop at Level 2 + OpenAPI.

**Interview trap:** "Is an API without HATEOAS RESTful?" — according to Fielding's original thesis, no. In practice the industry calls Level 2 "REST." Know the academic definition and the pragmatic reality.
**Tags:** hateoas, rest, hypermedia

---

## Q-API-006 [bloom: recall] [level: junior]
**Question:** What does HTTP `ETag` do and how does conditional request validation work?
**Model answer:** An ETag (Entity Tag) is a version identifier for a resource — typically a hash of the content or a version counter. The server returns `ETag: "abc123"` in the response. The client caches this value and uses it in two ways:

**1. Cache validation (conditional GET):**
```
GET /products/123
If-None-Match: "abc123"
```
If unchanged, server returns `304 Not Modified` with an empty body (client uses cached copy). If changed, server returns `200 OK` with new body and new ETag.

**2. Optimistic locking (conditional PUT/PATCH/DELETE):**
```
PUT /products/123
If-Match: "abc123"
```
If the ETag still matches, the update proceeds. If another client modified the resource first (ETag differs), the server returns `412 Precondition Failed`, and the client knows there was a concurrent modification.

**Strong vs weak ETag:** strong `"abc"` — byte-identical guarantee; weak `W/"abc"` — semantically equivalent (e.g., same content served with different compression).

**Interview trap:** ETag is opaque to the client — don't parse it. The server can use any strategy (MD5, version counter, timestamp) and clients must treat it as a black box. Also: ETags and `Last-Modified` / `If-Modified-Since` are the two conditional request mechanisms; ETags are more precise (1-second resolution limitation on `Last-Modified`).
**Tags:** caching, etag, conditional-requests, http

---

## Q-API-007 [bloom: recall] [level: junior]
**Question:** What is OpenAPI and what is the difference between spec-first and code-first approaches?
**Model answer:** OpenAPI (formerly Swagger) is a standard for describing REST APIs — a YAML or JSON document that specifies all endpoints, parameters, request/response schemas, error codes, and security requirements. Current version is 3.1 (2021), compatible with JSON Schema.

**What it enables:** (1) interactive documentation (Swagger UI, Redoc); (2) client SDK generation (`openapi-generator` in TypeScript, Java, Python, etc.); (3) server stub generation; (4) runtime request/response validation; (5) mock server generation for frontend teams before backend exists.

**Spec-first (contract-first):** write the OpenAPI spec first, generate server stubs and client SDKs from it. Forces upfront API design thinking. Preferred in teams with multiple consumer teams.

**Code-first:** annotate code with OpenAPI annotations (`@Operation`, `@ApiResponse` in Spring via `springdoc-openapi`), and the spec is generated from the code. More common in practice — developers stay in code. Risk: spec becomes outdated if annotations fall behind.

**Interview trap:** OpenAPI describes *any* HTTP API, not just REST. Spec drift is the main operational risk with code-first — the generated spec says one thing, the actual behavior another. Mitigation: CI pipeline that generates the spec and validates it against a snapshot (contract testing).
**Tags:** openapi, swagger, contract-first, documentation

---

## Q-API-008 [bloom: recall] [level: junior]
**Question:** What is RFC 7807 ProblemDetail and what goes in the error response body?
**Model answer:** RFC 7807 defines a standard error response format for HTTP APIs — `application/problem+json` (or `application/problem+xml`). The goal: machine-readable errors that clients can handle programmatically without screen-scraping messages.

**Standard fields:**
```json
{
  "type":     "https://api.example.com/errors/insufficient-stock",
  "title":    "Insufficient stock",
  "status":   422,
  "detail":   "Product 123 only has 3 units in stock; requested 10.",
  "instance": "/orders/456"
}
```
- `type` — a URI identifying the problem type (may be a documentation URL)
- `title` — short human-readable summary (should not change between occurrences of the same type)
- `status` — the HTTP status code (redundant but useful for clients reading the body without the status line)
- `detail` — specific explanation of this occurrence
- `instance` — URI of the specific request or resource that triggered the problem

**Extensions:** add custom fields freely (e.g., `"errors": [{"field":"quantity","message":"must be > 0"}]`).

**Spring support:** `org.springframework.http.ProblemDetail` is built into Spring Framework 6 / Spring Boot 3. Returns the RFC 7807 format when thrown as a `ResponseEntity<ProblemDetail>` or mapped via `@ControllerAdvice`.

**Interview trap:** The `type` field should be a dereferenceable URL pointing to documentation when possible, not just an arbitrary string. Teams that return `"type": "VALIDATION_ERROR"` are technically compliant but miss the spirit. Also: returning 500 with stack trace in the body is a security leak — ProblemDetail is the standard way to return structured errors without revealing internals.
**Tags:** error-format, rfc7807, problem-detail, spring

---

## Q-API-009 [bloom: understand] [level: regular]
**Question:** Explain the `Cache-Control` header and its most important directives.
**Model answer:** `Cache-Control` is the primary HTTP mechanism for controlling caching behavior at both client and shared (CDN/proxy) layers.

**Key directives:**
| Directive | Effect |
|-----------|--------|
| `max-age=N` | Cache for N seconds from response time |
| `s-maxage=N` | Like `max-age` but only for shared caches (CDN); overrides `max-age` for CDN |
| `no-cache` | Cache locally but *always* revalidate with the server before serving (uses ETag/If-None-Match). Misleading name — it does *not* mean "don't cache" |
| `no-store` | Do not cache at all — not in browser, not in CDN. For sensitive data. |
| `private` | Client cache only — CDN must not cache. Required for user-specific data |
| `public` | Any cache (including CDN) may store the response |
| `must-revalidate` | Once stale, *must* check with server — do not serve stale under any circumstances |
| `stale-while-revalidate=N` | Serve stale for up to N seconds while a background refresh happens |
| `immutable` | Content will never change (e.g., content-hashed assets) — client never revalidates |

**Practical examples:**
```
# Hashed static asset (webpack output)
Cache-Control: public, max-age=31536000, immutable

# HTML index — always check but cache locally
Cache-Control: no-cache, must-revalidate

# Per-user API response, 1 minute
Cache-Control: private, max-age=60

# Sensitive endpoint (passwords, tokens)
Cache-Control: no-store
```

**Interview trap:** Forgetting `private` on a user-specific response: CDN caches user A's data and serves it to user B — a data leak. The `no-cache` vs `no-store` confusion is a classic interview test — `no-cache` still caches, `no-store` does not cache at all.
**Tags:** caching, cache-control, http-headers, cdn

---

## Q-API-010 [bloom: understand] [level: regular]
**Question:** Explain API versioning strategies. What are the trade-offs and what should a deprecation policy look like?
**Model answer:** **Why version:** breaking changes in an API break existing clients. Versioning gives clients time to migrate.

**Strategies:**

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| URI path versioning | `/v1/products` | Explicit, cache-friendly, easy to test/debug | URL changes = technically a different resource; must maintain parallel routes |
| Accept header versioning | `Accept: application/vnd.api.v2+json` | URL is stable; REST-orthodox | Hard to test in browser; CDN must vary cache on header |
| Custom header | `X-API-Version: 2` | Simple | Non-standard; easy to overlook; same caching problem as Accept |
| Query parameter | `/products?version=2` | Simple | Easily forgotten; pollutes query string |

URI path versioning is the dominant choice in production (it is explicit and cache-friendly). Accept header is more RESTfully correct but less common.

**Deprecation policy best practices:**
1. Announce with `Deprecation: <date>` and `Sunset: <date>` headers (RFC 8594) in every response from the deprecated version.
2. Give clients 3–12 months depending on audience (external consumers need longer).
3. Track usage telemetry per version — never sunset when traffic is non-zero without explicit client agreement.
4. Redirect after sunset with 301/308 or return 410 Gone with a helpful message.

**Anti-pattern:** versioning individual endpoints (`/v1/products` but `/v2/orders`) — leads to a chaos matrix of what version each endpoint is on. Version the entire API as a coherent unit.

**Interview trap:** "Just make changes backward-compatible." Often true — adding optional fields or endpoints is non-breaking. But changing field semantics (e.g., `price` was an integer in cents, now it is a decimal string), renaming fields, or removing endpoints require a version bump.
**Tags:** versioning, api-design, deprecation, evolution

---

## Q-API-011 [bloom: understand] [level: regular]
**Question:** Compare offset-based and cursor-based pagination. What are the performance and consistency implications of each?
**Model answer:** **Offset-based:** `GET /products?offset=100&limit=20` — skip 100 rows, return the next 20.

*Pros:* simple; client can jump to any page; natural for numbered page UIs.

*Cons:*
1. **Performance:** `OFFSET 100000 LIMIT 20` in SQL still scans 100,020 rows regardless — the offset is a row skip, not an index seek. Performance degrades linearly with page depth.
2. **Consistency:** if a row is inserted between fetching page 1 and page 2, all subsequent rows shift — the client either misses a record or sees a duplicate across pages.

**Cursor-based (keyset pagination):** `GET /products?after=eyJpZCI6MTIzfQ&limit=20` — the cursor encodes the last-seen sort key (usually base64-encoded JSON like `{"id":123}`). SQL: `WHERE id > 123 ORDER BY id LIMIT 20`.

*Pros:*
1. **Performance:** index seek on `WHERE id > X` is O(log n) regardless of depth.
2. **Consistency:** insertions or deletions before the cursor position do not shift the window.

*Cons:* no random page access; "Load more" or infinite scroll UX only; harder to implement with multi-column sort.

**Multi-column cursor:** for sorting by `price` + `id` (tie-breaker), cursor is `{"price":99.99,"id":123}`, SQL uses row constructor: `WHERE (price, id) > (99.99, 123)` — requires composite index on `(price, id)`.

**Link header (RFC 5988):** an alternative to JSON pagination metadata; the `Link` header contains the next URL.

**Interview trap:** Cursor requires a stable sort column. Sorting by a non-unique column (e.g., `name`) without a tie-breaker like `id` causes duplicate/missing records at cursor boundaries. Always include `id` as a tie-breaker.
**Tags:** pagination, cursor, keyset, offset, performance

---

## Q-API-012 [bloom: understand] [level: regular]
**Question:** What is an idempotency key for POST, and how do you implement it server-side?
**Model answer:** An idempotency key is a client-generated UUID sent with a non-idempotent request (usually POST) so that the server can safely deduplicate retries. Made popular by Stripe's API.

**Client usage:**
```
POST /orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{"customer_id": 123, "items": [...]}
```
The client generates one UUID per *attempt* (same UUID on every retry of the same operation).

**Server-side implementation:**
1. On receiving a request, check a fast lookup store (Redis) for the key: `GET idempotency:{key}`.
2. If found: return the cached response (same status + body as original).
3. If not found: execute the operation. Atomically store the response under the key before returning: `SET idempotency:{key} {response_json} NX EX 86400` (NX = only if not exists; 24-hour TTL).
4. The `NX` flag handles concurrent retries — only one wins the write, the other gets the cached result.

**Response consistency:** the idempotent response must be byte-for-byte identical. This means storing the full response body and status, not just the resource ID.

**Key scoping:** keys should be scoped to the client (API key + idempotency key), not global.

**If the client sends the same key with a different body:** return `422 Unprocessable Entity` — the key was already consumed for a different request.

**Interview trap:** Idempotency keys do not help if the server processed the request and *then* crashed before writing to the store. This window is very small with NX + EX, but not zero. For true exactly-once semantics at the database level, add a unique constraint on `(customer_id, idempotency_key)` in the database as a secondary guard.
**Tags:** idempotency, post, retry, distributed-systems, stripe-pattern

---

## Q-API-013 [bloom: understand] [level: regular]
**Question:** Explain REST vs gRPC — when would you choose gRPC over REST?
**Model answer:** **REST over HTTP/1.1 + JSON** — human-readable, wide tooling support, works with browsers and curl natively, CDN-cacheable GET responses. The dominant choice for public-facing or browser-facing APIs.

**gRPC** — a framework from Google built on Protocol Buffers (protobuf) and HTTP/2.

**Key differences:**
| Dimension | REST/JSON | gRPC/Protobuf |
|-----------|-----------|---------------|
| Wire format | JSON (text, ~10x overhead) | Protobuf binary (compact, fast) |
| Transport | HTTP/1.1 or HTTP/2 | HTTP/2 always |
| Schema | Optional (OpenAPI) | Required (`.proto` files, strongly typed) |
| Code generation | openapi-generator | `protoc` generates stubs in 20+ languages |
| Streaming | No native bidirectional | Server, client, and bidirectional streaming |
| Browser support | Native | Requires gRPC-Web proxy |
| Human-readable | Yes | No (need `grpcurl` or Postman gRPC) |

**When to choose gRPC:**
- **High-throughput internal service-to-service communication** where JSON overhead is meaningful (serialization CPU, payload size).
- **Bidirectional streaming** (e.g., real-time telemetry, chat, live data push).
- **Polyglot microservices** where contract enforcement across languages is critical — `.proto` files are the single source of truth.
- **Strong typing at the transport layer** without relying on OpenAPI spec discipline.

**When to stick with REST:**
- Public APIs consumed by browsers or third-party developers.
- When you need CDN caching (HTTP GET).
- When the operations team is not familiar with gRPC tooling.
- When simplicity matters more than throughput.

**Interview trap:** gRPC is not a replacement for REST — it is a complement. Many architectures expose REST at the edge (browser, mobile, external partners) and use gRPC internally between microservices. Also: gRPC's HTTP/2 multiplexing eliminates head-of-line blocking for concurrent calls on a single connection — meaningful for high fan-out services.
**Tags:** grpc, rest, protobuf, http2, architecture

---

## Q-API-014 [bloom: understand] [level: regular]
**Question:** Explain REST vs GraphQL — what problem does GraphQL solve, and what are the operational trade-offs?
**Model answer:** **The problem GraphQL solves:**
- **Over-fetching:** a REST endpoint for a product list returns 30 fields per product; the mobile client only needs 4. GraphQL lets the client declare exactly which fields it wants.
- **Under-fetching / N+1 calls:** displaying a list of 20 orders, each with customer name, requires 1 call for orders + 20 calls for customers in REST. GraphQL resolves all in one query.

**GraphQL basics:** single endpoint (`/graphql`); client sends a query in GraphQL SDL; server returns exactly the requested shape. Schema defines types, queries, mutations, and subscriptions.

**Trade-offs:**

| Dimension | REST | GraphQL |
|-----------|------|---------|
| HTTP caching | Trivial (GET is cacheable) | Hard (queries sent as POST; need persisted queries for CDN) |
| Rate limiting | Per endpoint (natural) | Per query complexity (must implement depth/complexity limits) |
| N+1 problem | Server controls, visible | Moves to resolvers — must use DataLoader to batch |
| Security | Endpoint-level auth, easy | Query depth attacks (`friends{friends{friends{...}}}`) need query depth/complexity limits |
| Tooling | Ubiquitous | Requires Apollo/graphql-java, more setup |
| Contract | OpenAPI | Schema introspection built in |

**N+1 in resolvers:** a GraphQL query for `users { posts { comments } }` naively issues one query per user for posts, and one query per post for comments. Solution: DataLoader batches and deduplicates child queries into single `IN (...)` queries.

**When GraphQL wins:** frontend-heavy apps with multiple consumers (mobile, web, admin) that need different data shapes; rapid iteration; BFF (Backend for Frontend) pattern.

**When REST wins:** stable domain with well-defined resources; CDN caching is critical; team is not familiar with GraphQL; simpler rate limiting and monitoring.

**Interview trap:** "GraphQL will replace REST." It won't — each solves different problems. A common architecture: REST for the data plane (high-throughput, CDN-cached), GraphQL as a BFF aggregation layer on top. Also: subscriptions (real-time) in GraphQL use WebSocket, which adds infrastructure complexity.
**Tags:** graphql, rest, n-plus-1, dataloader, over-fetching

---

## Q-API-015 [bloom: understand] [level: regular]
**Question:** What is content negotiation in HTTP, and how do `Accept`, `Content-Type`, and `Vary` headers interact?
**Model answer:** Content negotiation is the mechanism by which a client and server agree on the format of the response.

**`Content-Type`** — set by the *sender* to declare the format of the request or response body (`Content-Type: application/json`, `Content-Type: application/xml`).

**`Accept`** — set by the *client* to declare acceptable response formats, with optional quality weights:
```
Accept: application/json;q=1.0, application/xml;q=0.8, */*;q=0.5
```
The server picks the highest-quality format it can produce. If none match, it returns `406 Not Acceptable`.

**`Accept-Language`** — preferred language for the response (`Accept-Language: en-US, pl;q=0.9`). Server returns `Content-Language` to indicate what it chose.

**`Accept-Encoding`** — compression preference (`Accept-Encoding: gzip, br`). Server returns `Content-Encoding: gzip`.

**`Vary` header** — tells caches (CDN, proxies) which request headers affect the cache key:
```
Vary: Accept-Language
```
This means the CDN will cache separate entries for `en` vs `pl` responses for the same URL. Without `Vary`, all language variants would share one cache entry and serve the wrong language to some users.

**Interview trap:** `Vary: *` effectively disables shared caching — every request is considered unique. Specific `Vary` values are safe; wildcard is a performance killer. Also: forgetting `Vary: Accept-Encoding` when responses are gzip-compressed can cause compressed content to be served to clients that did not request it.
**Tags:** content-negotiation, accept, vary, http-headers

---

## Q-API-016 [bloom: understand] [level: regular]
**Question:** What is rate limiting, what are the common algorithms, and how do you signal it to clients?
**Model answer:** Rate limiting restricts how many requests a client can make in a time window. Goals: protect against DoS, enforce fair usage, control backend costs.

**Algorithms:**

| Algorithm | Description | Burst behavior |
|-----------|-------------|----------------|
| Fixed window | Count requests in a fixed-size bucket; reset at window end | Double burst at window boundary (59s + 0s of next window) |
| Sliding window log | Store timestamps of all requests; count those in last N seconds | Accurate but memory-intensive |
| Sliding window counter | Interpolate between adjacent fixed-window counts | Approximate but memory-efficient |
| Token bucket | Bucket refills at rate R; each request consumes one token; max burst = bucket capacity | Allows bursts up to capacity |
| Leaky bucket | Requests queued and processed at a constant rate | Smoothes traffic; drops bursts |

**Token bucket** is the most common for APIs — allows bursts up to the bucket size while enforcing a sustained rate.

**Implementation:** Redis atomic Lua script to update the bucket state. All app instances share one Redis key — distributed, consistent.

**Client response on limit:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1714986060

{
  "type": "https://api.example.com/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit of 100 req/min exceeded. Retry in 30 seconds.",
  "retry_after_sec": 30
}
```

**`Retry-After`** can be a number of seconds or an HTTP date. Clients that honor it will back off gracefully. Without it, clients retry immediately and amplify the load.

**Interview trap:** Rate limiting per IP is easily bypassed by VPN or NAT. Rate limit per authenticated API key or user ID is accountable. Multiple app instances each keeping their own counter will each allow the full limit — centralized store (Redis) is required for correctness. Also: different limits for different tiers (free vs pro vs enterprise) require per-key policy lookup.
**Tags:** rate-limiting, token-bucket, 429, retry-after, redis

---

## Q-API-017 [bloom: understand] [level: regular]
**Question:** What is the difference between a DTO and a domain entity, and why does the distinction matter in REST API design?
**Model answer:** A **domain entity** (or JPA entity in Java) represents a concept in the business domain as persisted in the database. It contains the full state, relationships, and behavior of that concept — including fields that should never leave the server (internal IDs, audit timestamps, password hashes, sensitive flags).

A **DTO (Data Transfer Object)** is a plain data carrier designed specifically for a particular API request or response. It contains only the fields relevant to that contract.

**Why the separation matters:**
1. **Security:** returning a JPA entity directly serializes all fields including sensitive ones (e.g., `@Column(name="password_hash")` becomes `"passwordHash":"$2b$10$..."` in the JSON response).
2. **Decoupling:** the API contract is decoupled from the database schema. You can rename database columns, refactor entities, or change persistence strategy without changing the public API (and vice versa).
3. **Versioning:** DTOs can coexist in multiple versions (`ProductDtoV1`, `ProductDtoV2`) without touching entity code.
4. **Validation:** DTOs carry `@NotNull`, `@Size`, `@Valid` annotations suited to the API contract; entities have persistence-level constraints.
5. **Shape:** a response DTO can aggregate data from multiple entities, compute derived fields, and format values (e.g., `BigDecimal` as string for JSON precision).

**Typical pattern in Spring:**
- Controller receives `ProductCreateRequest` (DTO).
- Service maps it to a domain entity via a mapper (`MapStruct` or manual).
- Persistence layer saves the entity.
- Service returns a `ProductResponse` DTO (mapped from entity) to the controller.

**Interview trap:** "Spring's `@ResponseBody` with a JPA entity is simpler." It is, until you accidentally expose lazy-loaded relations (triggering N+1 in serialization), reveal password hashes, or cause infinite recursion on bidirectional `@OneToMany` — all common real-world bugs. DTOs prevent all three.
**Tags:** dto, entity, api-design, security, spring

---

## Q-API-018 [bloom: apply] [level: senior]
**Question:** Design a REST endpoint for a product pricing resource. Show the full endpoint set with HTTP methods, status codes, and the important headers for each operation.
**Model answer:**
```
GET    /api/v1/products
       200 OK — {data: [...], pagination: {next_cursor, has_more}}
       Query: ?country=PL&segment=B2B&cursor=<token>&limit=20
       Response headers: Cache-Control: private, max-age=60

GET    /api/v1/products/{id}
       200 OK — full product object
       404 Not Found if missing
       Response headers: ETag: "v123", Cache-Control: public, max-age=300

POST   /api/v1/products
       Request headers: Idempotency-Key: <uuid>
       201 Created + Location: /api/v1/products/{new_id}
       400 Bad Request — unparseable body
       422 Unprocessable Entity — business validation failure (RFC 7807)
       409 Conflict — SKU already exists

PUT    /api/v1/products/{id}
       Request headers: If-Match: "v123"  (optimistic lock)
       200 OK or 204 No Content
       404 Not Found
       412 Precondition Failed — concurrent modification

PATCH  /api/v1/products/{id}
       Content-Type: application/merge-patch+json
       200 OK
       404 Not Found
       422 Unprocessable Entity — result would be invalid

DELETE /api/v1/products/{id}
       204 No Content (soft delete; idempotent — second call also 204 or 404)
       404 Not Found

GET    /api/v1/products/{id}/prices
       Sub-resource: prices per country/segment

POST   /api/v1/products/{id}/restore
       Custom action — restores a soft-deleted product
       200 OK or 404
```

**Design notes:**
- URI path versioning (`/v1/`) — explicit, cache-friendly.
- POST carries `Idempotency-Key` to make mobile retries safe.
- PUT uses `If-Match` for optimistic concurrency — returns `412` on conflict.
- PATCH uses `Content-Type: application/merge-patch+json` (RFC 7396) — only included fields are modified.
- Sub-resources (`/prices`) for related entities rather than embedding everything in the product response.
- Custom actions (restore) as POST to a sub-resource — avoids verbs in the URL.

**Interview trap:** "Verb in URL" (`POST /products/createNew`) is an anti-pattern. REST uses HTTP verbs for semantics. "Should DELETE be hard or soft?" — the HTTP spec does not dictate; it is an application decision. For pricing, soft delete (sets `deleted_at`) with `DELETE` returning 204 is standard — declare it in the API spec.
**Tags:** api-design, rest, crud, status-codes, idempotency

---

## Q-API-019 [bloom: apply] [level: senior]
**Question:** A mobile client POSTs to create an order. The network dies during the request — the client does not know if the server processed it. Design the idempotency pattern end-to-end.
**Model answer:** **Stripe-style idempotency key pattern.**

**Client-side:**
1. Before sending, generate a UUID (`UUID.randomUUID()`) and associate it with this order creation attempt.
2. Send every retry with the same UUID — never generate a new one unless the user explicitly starts a new order.
3. Stop retrying when receiving any non-5xx, non-network-error response.

```
POST /api/v1/orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{"customer_id": 123, "items": [...], "total": 199.99}
```

**Server-side flow:**
```
1. Hash or use key directly: Redis key = "idem:orders:{api_key_id}:{idempotency_key}"
2. SETNX → response stored with 24h TTL:
   SET idem:orders:client1:550e... {cached_response_json} NX EX 86400
3. If SET succeeded → process the order → store result → return result
4. If SET failed (key existed) → return cached response immediately
5. For concurrent requests with same key → second request SETNX fails → returns cached value
   (if first is still in-flight, use a short wait + retry for the in-progress case)
```

**Redis NX handles the race condition:** if two retries arrive simultaneously, only one gets `NX` success. The other finds the key and returns the cached response.

**Database safety net:** add a unique constraint on `(customer_id, idempotency_key)` in the `orders` table. Even if the Redis layer fails, the DB insert fails for a duplicate, which the application catches and converts to a 200 (not a 500).

**Same key, different body:** if the client sends the same key with different JSON, return `422 Unprocessable Entity` — the key is already bound to a different request.

**TTL reasoning:** 24 hours covers any realistic retry window. After that, the key expires and a new order attempt should use a new key — the user would have noticed the missing order by then.

**Interview trap:** The window between "executed the operation" and "wrote to Redis" is a potential gap for duplication. The DB unique constraint closes this gap. Also: the cached response must include headers (especially `Location`) so that retried 201s return the same URL.
**Tags:** idempotency, post, retry, redis, distributed-systems

---

## Q-API-020 [bloom: apply] [level: senior]
**Question:** Implement cursor-based pagination for a product list endpoint in Spring Boot. Show the SQL query, cursor encoding, and the response structure.
**Model answer:**
**Controller:**
```java
@GetMapping("/products")
public ResponseEntity<ProductPage> list(
    @RequestParam(defaultValue = "20") int limit,
    @RequestParam(required = false) String after
) {
    Long afterId = decodeCursor(after); // null for first page
    List<ProductDto> items = productService.findPage(afterId, limit + 1);
    boolean hasMore = items.size() > limit;
    if (hasMore) items = items.subList(0, limit);
    
    String nextCursor = hasMore
        ? encodeCursor(items.get(items.size() - 1).getId())
        : null;
    
    return ResponseEntity.ok(new ProductPage(items, nextCursor, hasMore));
}
```

**SQL (via JDBC or JPA native query):**
```sql
SELECT * FROM product
WHERE (:afterId IS NULL OR id > :afterId)
ORDER BY id ASC
LIMIT :limitPlusOne
```
Index: `CREATE INDEX idx_product_id ON product(id)` — the `WHERE id > X` is an index seek, O(log n) regardless of depth.

**Cursor encode/decode:**
```java
String encodeCursor(Long id) {
    return Base64.getUrlEncoder().encodeToString(
        ("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8));
}
Long decodeCursor(String cursor) {
    if (cursor == null) return null;
    String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    return objectMapper.readTree(json).get("id").asLong();
}
```

**Response structure:**
```json
{
  "data": [
    {"id": 124, "name": "Widget", "price": "99.99"},
    ...
  ],
  "pagination": {
    "next_cursor": "eyJpZCI6MTQzfQ",
    "has_more": true
  }
}
```

**Multi-column sort (e.g., by price then id):**
Cursor becomes `{"price":"99.99","id":143}` and SQL uses row constructor comparison:
```sql
WHERE (price, id) > (:price, :id) ORDER BY price ASC, id ASC
```
Requires composite index on `(price, id)`.

**Interview trap:** Cursor-based pagination cannot jump to page 5 — it is forward-only. If the UX requires numbered pages, use offset pagination. Also: the cursor encodes internal IDs — consider HMAC-signing it if you do not want clients to decode it (`Base64(json + ":" + HMAC-SHA256(json, secret))`).
**Tags:** pagination, cursor, keyset, spring, sql

---

## Q-API-021 [bloom: apply] [level: senior]
**Question:** Design HTTP caching for a `GET /prices/{product_id}?country=PL` endpoint so that both browser clients and CDN cache effectively, with a maximum staleness of 5 minutes. Show the full set of cache-related headers.
**Model answer:**
```
GET /api/v1/prices/123?country=PL
HTTP/1.1 200 OK
ETag: "sha256:a1b2c3d4"
Cache-Control: public, max-age=300, s-maxage=600, stale-while-revalidate=60, must-revalidate
Last-Modified: Wed, 03 Jun 2026 10:00:00 GMT
Vary: Accept-Language
Content-Type: application/json

{"product_id": 123, "country": "PL", "price": "99.99", "currency": "PLN"}
```

**Header breakdown:**
- `public` — CDN may cache (this is not user-specific data).
- `max-age=300` — browser/client caches for 5 minutes.
- `s-maxage=600` — CDN caches for 10 minutes (longer because CDN serves many concurrent users; the cost of a stale window is amortized across many hits).
- `stale-while-revalidate=60` — during the 60 seconds after expiry, the CDN/client may serve the stale response while refreshing in the background; eliminates cache-miss latency spikes.
- `must-revalidate` — once fully stale (past `max-age + stale-while-revalidate`), must check with server; cannot serve indefinitely stale.
- `ETag: "sha256:..."` — the client uses `If-None-Match: "sha256:..."` on revalidation; server returns `304 Not Modified` with empty body if unchanged.
- `Vary: Accept-Language` — tells CDN to maintain separate cache entries per language.

**Cache invalidation on price change:**
When a price is updated in the database, the server-side ETag changes. For proactive invalidation:
1. Publish a `price_changed` event (Kafka/SNS).
2. Consumer calls CDN purge API (CloudFront `create_invalidation`, Cloudflare cache purge).
3. Until purge completes, clients that hit CDN get stale within the `max-age` window (5 min max by design).

**`Vary` pitfall:** `Vary: Authorization` — every user gets a separate cache entry at the CDN, effectively disabling CDN caching. For user-specific pricing, use `Cache-Control: private` and drop from CDN, relying on app-level cache (Redis).

**Interview trap:** Forgetting `Vary: Accept-Language` when responses differ by language — the CDN would serve one language to all users. `Vary: *` is the nuclear option — disables all shared caching. Also: `stale-while-revalidate` and `must-revalidate` are slightly contradictory — `must-revalidate` applies after the stale-while-revalidate window ends.
**Tags:** caching, etag, cache-control, cdn, http-headers

---

## Q-API-022 [bloom: apply] [level: senior]
**Question:** Design a webhook system where your API notifies external clients when a price changes. Cover delivery guarantees, security, and retry logic.
**Model answer:** **Webhook flow:** when a price changes, the server POSTs a JSON payload to the registered callback URL.

**Payload:**
```json
{
  "event_type": "price.updated",
  "event_id":   "evt_abc123",
  "occurred_at": "2026-06-03T10:00:00Z",
  "data": {
    "product_id": 123,
    "country": "PL",
    "old_price": "89.99",
    "new_price": "99.99",
    "currency": "PLN"
  }
}
```

**Delivery guarantee (at-least-once):**
1. Store the event in a `webhook_events` table with status `pending` before dispatching.
2. Send the HTTP POST. If 2xx response: mark `delivered`. If non-2xx or timeout: mark `failed`.
3. Retry with exponential backoff: 30s, 2m, 10m, 1h, 24h (5 attempts). After max retries: mark `dead`.
4. Use a background worker (scheduled job or queue consumer) to process the retry queue.
5. Include `event_id` for idempotency — the receiver deduplicates by this ID.

**Security — HMAC signature:**
```
X-Webhook-Signature: sha256=abc123def456...
X-Webhook-Timestamp: 1717408800
```
The server computes `HMAC-SHA256(secret, timestamp + "." + body)` and sends it in the header. The receiver recomputes and compares — verifies origin and body integrity. The timestamp prevents replay attacks (reject if `|now - timestamp| > 5 minutes`).

**Receiver contract:**
- Must respond with 2xx within a timeout (e.g., 5 seconds). Long processing must be async — accept and queue.
- Should be idempotent: same `event_id` arriving twice must produce the same final state.

**Registration endpoint:**
```
POST /api/v1/webhooks
{
  "url": "https://client.example.com/webhooks/price",
  "events": ["price.updated", "price.deleted"],
  "secret": "<client-provided or server-generated>"
}
```

**Operational concerns:**
- Circuit-break unresponsive endpoints after repeated failures — do not hammer a dead URL.
- Expose a webhook delivery log endpoint so clients can inspect delivery history.
- Provide a "re-deliver" endpoint for manual replay.

**Interview trap:** Synchronous delivery inside the price-update transaction is dangerous — if the webhook endpoint is slow or down, it blocks the transaction. Always dispatch webhook delivery asynchronously (via queue or Transactional Outbox). Also: HTTP 200 from the receiver does not mean the receiver processed correctly — the event-id deduplication must be on both sides.
**Tags:** webhooks, event-driven, hmac, retry, delivery-guarantees

---

## Q-API-023 [bloom: apply] [level: senior]
**Question:** Implement request validation with RFC 7807 error responses in Spring Boot for a `POST /orders` endpoint.
**Model answer:**
**DTO with Bean Validation:**
```java
public record CreateOrderRequest(
    @NotNull Long customerId,
    @NotEmpty @Valid List<OrderItemRequest> items,
    @NotNull @DecimalMin("0.00") BigDecimal declaredTotal
) {
    @AssertTrue(message = "declaredTotal must equal sum of (price × quantity)")
    public boolean isDeclaredTotalValid() {
        if (items == null || declaredTotal == null) return true;
        BigDecimal computed = items.stream()
            .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        return computed.compareTo(declaredTotal.setScale(2, RoundingMode.HALF_UP)) == 0;
    }
}

public record OrderItemRequest(
    @NotNull Long productId,
    @Min(1) int quantity,
    @NotNull @DecimalMin(value = "0.01", message = "price must be positive") BigDecimal price
) {}
```

**Controller:**
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request) {
        // service call
    }
}
```

**Global exception handler returning RFC 7807:**
```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setType(URI.create("https://api.example.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(req.getRequestURI()));
        
        List<Map<String, String>> errors = ex.getBindingResult()
            .getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
            .toList();
        problem.setProperty("errors", errors);
        
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJson(HttpServletRequest req) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed JSON");
        problem.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.badRequest().body(problem);
    }
}
```

**Example 422 response:**
```json
{
  "type": "https://api.example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 422,
  "instance": "/api/v1/orders",
  "errors": [
    {"field": "items[1].quantity", "message": "must be greater than or equal to 1"},
    {"field": "declaredTotal", "message": "declaredTotal must equal sum of (price × quantity)"}
  ]
}
```

**Interview trap:** `BigDecimal.equals()` vs `compareTo()` — `new BigDecimal("1.00").equals(new BigDecimal("1.0"))` is `false` (different scale); `compareTo()` returns 0. Always use `compareTo()` for value equality in pricing. Also: `MethodArgumentNotValidException` is thrown for `@RequestBody` validation; `ConstraintViolationException` is thrown for `@PathVariable`/`@RequestParam` validation — need separate handlers or a parent class catch.
**Tags:** validation, bean-validation, rfc7807, problem-detail, spring

---

## Q-API-024 [bloom: apply] [level: senior]
**Question:** Your API needs filtering, sorting, and sparse fieldsets on `GET /products`. Design the query parameter contract and show the server-side implementation sketch.
**Model answer:** **Query parameter contract:**
```
GET /api/v1/products
  ?filter[country]=PL               # equality filter
  ?filter[price][gte]=50            # range filter (gte, lte, gt, lt)
  ?filter[name][contains]=Widget    # string contains
  ?sort=-price,+name                # multi-column sort: - = desc, + or none = asc
  ?fields=id,name,price             # sparse fieldset — only these fields in response
  ?cursor=eyJpZCI6MTIzfQ&limit=20   # pagination
```

**Parsing filters safely (Spring):**
```java
@GetMapping("/products")
public ResponseEntity<ProductPage> list(
    @RequestParam Map<String, String> allParams,
    @RequestParam(required = false) String fields,
    @RequestParam(required = false) String sort
) {
    ProductFilter filter = FilterParser.parse(allParams);   // whitelist allowed fields
    List<SortOrder> sortOrders = SortParser.parse(sort);    // whitelist sortable columns
    Set<String> fieldSet = FieldParser.parse(fields);       // whitelist projectable fields
    return ResponseEntity.ok(productService.find(filter, sortOrders, fieldSet, pageable));
}
```

**Whitelist, never pass user input to SQL directly:**
```java
private static final Set<String> SORTABLE = Set.of("id", "name", "price", "created_at");
private static final Set<String> FILTERABLE = Set.of("country", "name", "price");

List<SortOrder> parse(String sort) {
    if (sort == null) return List.of(SortOrder.asc("id")); // default
    return Arrays.stream(sort.split(","))
        .map(col -> col.startsWith("-")
            ? SortOrder.desc(validateColumn(col.substring(1), SORTABLE))
            : SortOrder.asc(validateColumn(col.replaceFirst("^\\+", ""), SORTABLE)))
        .toList();
}
String validateColumn(String col, Set<String> allowed) {
    if (!allowed.contains(col)) throw new BadRequestException("Unknown sort field: " + col);
    return col;
}
```

**Sparse fieldsets** — at the JPA layer, use a Projection interface or dynamic `@Query` with explicit `SELECT`. In practice, constructing dynamic SELECT in JPQL is messy; consider using a Criteria API or jOOQ for clean field selection.

**OpenAPI documentation:** define query parameters with `schema.enum` for sortable fields so clients get validation and documentation.

**Interview trap:** Never pass filter/sort values directly into SQL strings — SQL injection. Always whitelist against known column names. Also: sorting by a user-controlled expression (e.g., `sort=SLEEP(5)`) is an injection vector if not validated. Document which fields are sortable and filterable in the OpenAPI spec so clients know what is supported.
**Tags:** filtering, sorting, sparse-fieldsets, api-design, sql-injection-prevention

---

## Q-API-025 [bloom: analyze] [level: senior]
**Question:** Your team debates whether to adopt contract-first (spec-first) OpenAPI or code-first. What are the architectural consequences of each choice and what would you recommend for a team that exposes an API to external clients?
**Model answer:** **Code-first:** annotations on controllers generate the spec (springdoc-openapi, Springfox). Developers stay in code; no separate artifact to maintain.

*Problems at scale:*
- Spec reflects what the code does, not what was agreed. If a developer adds a field without reviewing the contract, it silently becomes part of the API.
- External clients generate SDK from the spec. A code-first spec that changes without notice breaks generated clients in CI.
- Harder to do API design reviews — reviewers must read Java annotations, not a clean YAML document.
- Spec drift: annotations get stale as code evolves; generated spec diverges from actual behavior.

**Contract-first:** team writes the OpenAPI spec first, gets it reviewed (API governance), then generates server stubs and client SDKs.

*Benefits:*
- API contract is a first-class artifact, reviewed before implementation.
- Multiple teams (mobile, frontend, partner integrations) can work against a mock server generated from the spec before the backend is done.
- Breaking change is visible as a diff to the spec file in the PR — easy to gate in CI.
- Server stub generation (`openapi-generator`) produces interfaces; implementation must match.

*Problems:*
- More ceremony: spec must be updated before code changes are allowed.
- Tooling for stub generation can be brittle for complex specs.
- Teams that are not disciplined treat the spec as documentation after the fact, losing the benefit.

**Recommendation for external API:** contract-first, enforced in CI:
1. OpenAPI spec lives in its own repo (or `/api-spec/` folder).
2. PR to change the spec triggers: (a) breaking change detection (`openapi-diff`), (b) mock server deployment for consumer testing, (c) client SDK regeneration in dependent repos.
3. Server implements the generated interface; if it drifts, CI fails (`openapi-validator` against live server responses).
4. Semantic versioning on the spec: minor bumps for additive changes, major bumps for breaking.

**Interview trap:** "Code-first is fine if annotations are kept up to date." In practice they are not — developer pressure to ship means annotations lag. External clients that rely on the spec get burned. Contract-first is more effort upfront but prevents expensive breakage downstream. The key is making the CI gate mandatory, not advisory.
**Tags:** openapi, contract-first, code-first, api-governance, ci

---

## Q-API-026 [bloom: analyze] [level: senior]
**Question:** A client complains that the same GET request returns different prices within a 30-second window. Walk through your diagnosis methodology.
**Model answer:** The root causes fall into five buckets:

**1. Caching layer inconsistency:**
- Multiple cache layers (CDN, app Redis L2, in-process Caffeine L1) with different TTLs — different layers return different generations of data.
- Cache invalidation race: a price-update event flushed some app instances' caches but not others. In a 3-instance cluster, one instance may still serve stale.
- `Vary` header missing or incorrect: CDN serving a cached response for a different `Accept-Language` or currency variant.

**2. Database read replica lag:**
- Write goes to the primary; subsequent read hits a replica that has not yet applied the WAL entry. Replication lag can be milliseconds to seconds.
- "Read your writes" pattern: the client that just updated the price reads from a replica that does not have it yet.
- Fix: direct writes-then-reads to primary for the same session (`@Transactional(readOnly=false)` or `@Primary` routing for write callers).

**3. Application-level non-determinism:**
- Feature flag / A/B test routing: some requests go to a canary instance with a different pricing rule.
- Floating-point arithmetic in pricing logic (e.g., `double` instead of `BigDecimal`) producing different results depending on JVM state or call order.
- Order-sensitive aggregation on a data structure without a stable iteration order.

**4. Time-based pricing rule transitions:**
- A pricing rule has `valid_from`/`valid_to` timestamps. Requests straddling the boundary return different prices.
- Timezone handling: server comparing UTC rule boundaries against a local-time input.

**5. Cache key collision:**
- Missing or wrong cache key dimension — e.g., the key is `(product_id, country)` but the actual rule is `(product_id, country, customer_segment)`. Different segments collide on the same key.

**Diagnosis steps:**
1. Reproduce deterministically: script 50 identical requests in a loop and log `X-Instance-Id`, `X-Cache` headers, response body, and request timestamps.
2. Check if variance correlates with instance ID (deployment inconsistency), cache hit/miss (staleness), or time (rule transition).
3. Distributed tracing: compare spans for two diverging requests — where do they first differ?
4. Query the database directly for the same `(product_id, country)` — does the DB agree with both responses? If yes, the issue is above the DB (cache/logic). If not, the DB has a replication problem.

**Interview trap:** "It must be a cache bug." Sometimes it is; sometimes it is replication lag, a pricing rule boundary, or a feature flag. Do not assume — measure. A logging statement that captures `cache_hit=true/false`, `instance_id`, and `rule_id` on every response would have made this trivially diagnosable.
**Tags:** debugging, caching, consistency, replication, observability

---

## Q-API-027 [bloom: analyze] [level: master]
**Question:** You are designing a public REST API that must support versioning for the next 5 years across three consumer types: browser SPA, mobile apps, and partner integrations via server-to-server. What versioning strategy would you choose, and how would you operationalize the full lifecycle from introduction through sunset?
**Model answer:** **Strategy choice: URI path versioning (`/v1/`, `/v2/`).**

Rationale for this consumer mix:
- Browser SPA: developers use Chrome DevTools to inspect traffic; visible URL versioning reduces confusion.
- Mobile apps: URL-based versioning means the app can hard-code `/v1/` and the server can route it reliably even through CDN or API gateway without inspecting headers.
- Partner integrations: explicit URI versioning makes their API gateway rules, logging, and documentation simpler.
- Header versioning would require all three client types to set `Accept: application/vnd.api.v2+json` correctly and requires CDN `Vary` configuration — more failure surface.

**Lifecycle phases:**

*1. Introduction (v2 released alongside v1):*
- Both versions deployed simultaneously.
- `/v2/` endpoints are additive: new or restructured resources, not just "v1 with a new field" (use backward-compatible evolution for that — no new version needed).
- Announce v2 GA with migration guide and SDK updates.

*2. Deprecation announcement:*
- Add to all v1 responses: `Deprecation: Wed, 01 Jun 2027 00:00:00 GMT` and `Sunset: Fri, 01 Sep 2028 00:00:00 GMT` (RFC 8594).
- Publish changelog, migration guide, and contact details.
- Email all registered v1 API key owners.
- Minimum notice periods: SPAs (3 months — deploy anytime), mobile apps (6–12 months — App Store review cycles, users who do not update), partners (12 months — contract-bound integration cycles).

*3. Active deprecation period:*
- Track usage telemetry per version and per client: `SELECT api_key_id, COUNT(*) FROM request_log WHERE version='v1' GROUP BY api_key_id`.
- Proactively contact high-volume v1 users who have not migrated.
- Optional: return a `Warning: 299 - "Deprecated API version"` header from v1 responses.

*4. Sunset:*
- On the sunset date, v1 returns `410 Gone` with `ProblemDetail` including the v2 migration URL.
- Do not 301 redirect — clients might blindly follow and send v1-formatted payloads to v2 endpoints, causing data corruption.

*5. Post-sunset:*
- Remove v1 code after a 6-month grace period where `410` is served from a stub.
- Archive the v1 OpenAPI spec in the docs system permanently.

**Backward-compatible evolution (no version bump needed):**
- Adding optional fields to response.
- Adding optional request fields (backward-compatible with existing clients).
- Adding new endpoints.
- Adding new enum values (document that clients must handle unknown enums gracefully).

**Breaking change (requires version bump):**
- Removing or renaming fields.
- Changing field semantics (type, format, valid range).
- Changing authentication scheme.
- Removing endpoints.

**Interview trap:** "Can't you just make everything backward-compatible forever?" For long-lived public APIs, yes you accumulate backward-compatible cruft — and this has a cost in documentation complexity, test surface, and developer cognitive load. A versioned bump lets you clean the slate. The discipline is making version bumps rare (1–2 per year) by investing in backward-compatible evolution first.
**Tags:** versioning, api-lifecycle, deprecation, sunset, rfc8594

---

## Q-API-028 [bloom: analyze] [level: master]
**Question:** Design the complete error handling strategy for a public REST API: from input validation through business exceptions through unexpected errors. Cover format, granularity, security, and observability.
**Model answer:** **Principle:** errors are part of the API contract. They must be machine-readable, actionable, and not leak internals.

**Error taxonomy and status codes:**
| Layer | Condition | Status | ProblemDetail type |
|-------|-----------|--------|--------------------|
| Protocol | Malformed JSON, wrong Content-Type | 400 | `.../malformed-request` |
| Input validation | Field constraint violated | 422 | `.../validation-failed` |
| Authentication | Missing/invalid credentials | 401 | `.../unauthenticated` |
| Authorization | Authenticated but not permitted | 403 | `.../forbidden` |
| Not found | Resource does not exist | 404 | `.../not-found` |
| Conflict | Duplicate key, optimistic lock | 409 | `.../conflict` |
| Rate limit | Quota exceeded | 429 | `.../rate-limit-exceeded` |
| Business rule | Domain validation (e.g., price < MAP) | 422 | `.../business-rule-violation` |
| Unexpected | Unhandled exception | 500 | `.../internal-error` |
| Dependency | Upstream service failure | 502/503 | `.../upstream-unavailable` |

**RFC 7807 response with extensions:**
```json
{
  "type":      "https://api.example.com/errors/validation-failed",
  "title":     "Validation Failed",
  "status":    422,
  "detail":    "2 fields failed validation.",
  "instance":  "/api/v1/orders/attempt-abc123",
  "trace_id":  "4f3d2c1b",
  "errors": [
    {"field": "items[0].quantity", "code": "MIN", "message": "must be >= 1"},
    {"field": "deliveryDate",      "code": "FUTURE", "message": "must be in the future"}
  ]
}
```

**Security rules:**
- 500 responses: return only `trace_id` in the body. Stack traces, SQL messages, and internal paths go to the logging system, not the client.
- 404 for unauthorized access to private resources (IDOR): do not return 403 for resources the caller should not know exist. Return 404 to avoid confirming the resource exists.
- Correlation `trace_id` in every error response — allows the client to file a support ticket; allows the server to find the full trace in Jaeger/Splunk.

**Spring global handler:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, ...) { ... }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> businessRule(BusinessRuleException ex, ...) { ... }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(EntityNotFoundException ex, ...) { ... }

    @ExceptionHandler(Exception.class)  // catch-all
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
        String traceId = MDC.get("traceId");
        log.error("Unhandled exception [traceId={}]", traceId, ex); // full stack in logs
        ProblemDetail pd = ProblemDetail.forStatus(500);
        pd.setTitle("Internal Error");
        pd.setProperty("trace_id", traceId);
        return ResponseEntity.internalServerError().body(pd);
    }
}
```

**Observability:**
- Emit a metric per error type: `api.error.count{type="validation-failed",endpoint="/orders",method="POST"}`.
- Alert on sudden spike of 5xx or 4xx (excluding 404) — unusual 4xx rate often indicates a client integration bug or an attack.
- Log every 422/409 at INFO (they are expected); log every 500 at ERROR with full trace.

**Interview trap:** Returning the same generic 500 for all unexpected errors makes debugging impossible remotely. The `trace_id` is the bridge between the opaque client-facing error and the detailed server-side trace. Also: some teams return 200 with an `error` flag in the body for all errors — this breaks HTTP tooling, monitoring, and client error handling. Use proper status codes.
**Tags:** error-handling, rfc7807, security, observability, spring, problem-detail

---

## Q-API-029 [bloom: analyze] [level: master]
**Question:** A high-traffic public API (`GET /prices`) is experiencing a thundering-herd cache stampede at TTL expiry. Explain the problem in detail and describe three mitigation strategies with their trade-offs.
**Model answer:** **The problem:** a popular cache entry (e.g., price for product 123/PL) expires simultaneously for thousands of concurrent users. All of them get a cache miss at the same time, all fire a database query for the same data, overloading the database — which then slows down, causing even more cache misses downstream. The load spike is disproportionate to the actual request volume.

**Why it is systemic:** fixed TTLs cause correlated expiry. In a CDN, thousands of edge nodes may all expire the same key at the same instant if they all cached it at the same time (e.g., after a deploy or a cache flush).

**Mitigation 1: Stale-While-Revalidate (background refresh):**
- Set `Cache-Control: max-age=60, stale-while-revalidate=30`.
- When the entry is between 60–90 seconds old, the CDN or app-level cache serves the stale response immediately but triggers a single background refresh.
- **Trade-off:** clients may get slightly stale data. Acceptable for pricing (5 min stale max); not acceptable for inventory or order status.

**Mitigation 2: Probabilistic early expiration (XFetch / jitter):**
- Do not expire at a fixed TTL. Instead, as the remaining TTL decreases, each request has an increasing probability of triggering a refresh *before* expiry.
- Algorithm: `if (remaining_ttl < random * beta * recompute_time) → refresh`.
- **Trade-off:** harder to implement in standard HTTP caching; works better at the application cache layer (Redis/Caffeine). Requires knowing the expected recompute time.

**Mitigation 3: TTL jitter + staggered warm-up:**
- Add random jitter to TTLs: instead of all entries expiring at `T+300`, expire at `T + 300 + rand(0, 60)`. This staggers the expiry across a 60-second window, distributing the refresh load.
- For cache warm-up after a deploy: pre-warm the top-N hot keys before routing traffic to new instances.
- **Trade-off:** adds complexity to the TTL computation; slight inconsistency in how stale different clients' data is.

**Bonus — Locking pattern (single-flight / mutex):**
- When a cache miss occurs, one thread/process acquires a lock (`SETNX cache:lock:product-123 1 EX 5`) and performs the DB query. All other concurrent misses wait or return the previous stale value.
- Guava `LoadingCache` and Caffeine implement single-flight internally: concurrent loads for the same key resolve with a single underlying fetch.
- **Trade-off:** adds latency for waiting threads; complexity in distributed lock management (lock expiry edge cases).

**Interview trap:** "Just increase the TTL." Longer TTL reduces stampede frequency but does not eliminate it and increases staleness. The correct solution is stale-while-revalidate or jitter — they are complementary, not alternatives to each other.
**Tags:** caching, thundering-herd, stampede, stale-while-revalidate, ttl-jitter, performance

---

## Q-API-030 [bloom: analyze] [level: master]
**Question:** Compare gRPC, REST, and GraphQL across five dimensions — performance, caching, schema enforcement, streaming, and operational complexity — and propose a concrete architecture for a backend that serves both a web SPA and internal microservices simultaneously.
**Model answer:**

**Comparison table:**
| Dimension | REST/JSON | gRPC/Protobuf | GraphQL |
|-----------|-----------|---------------|---------|
| **Payload size** | Verbose JSON (~10x vs protobuf) | Compact binary | JSON but only requested fields |
| **Serialization speed** | Moderate | Fast (protobuf) | Moderate (JSON) |
| **HTTP caching** | Native (GET + Cache-Control) | Not applicable (HTTP/2, all POST) | Hard (POST; need persisted queries) |
| **Schema enforcement** | Optional (OpenAPI) | Mandatory (`.proto`) | Mandatory (SDL) |
| **Streaming** | SSE (server-push), WebSocket (separate) | Native: server, client, bidi | Subscriptions over WebSocket |
| **Browser support** | Native | Requires grpc-web proxy | Native |
| **Tooling maturity** | Highest | High, but more specialized | High, growing |
| **Breaking change visibility** | Manual (OpenAPI diff) | Compiler/tooling enforced | Schema diff tools |
| **Observability** | Standard HTTP metrics | Requires gRPC-specific middleware | Per-query metrics needed |

**Proposed architecture for SPA + internal microservices:**

```
Browser SPA
    │  REST/JSON (HTTPS, HTTP/2)
    ▼
API Gateway (Kong / AWS API Gateway)
    ├── Rate limiting, auth (JWT validation), TLS termination
    │
    ├── /api/v1/* → REST API (Spring Boot)
    │       │  REST (JSON over HTTP/1.1 or HTTP/2)
    │       ▼
    │   Backend services (pricing, orders, catalog)
    │       │  gRPC (protobuf/HTTP2, internal only)
    │       ▼
    │   Databases, caches
    │
    └── /graphql → GraphQL BFF (Apollo or Spring GraphQL)
            │  Aggregates from multiple REST/gRPC services
            ▼
        Used only for admin dashboard and complex reporting views
```

**Rationale:**
- **Edge (SPA):** REST over HTTPS — browser-native, CDN-cacheable GET endpoints, no proxy needed. OpenAPI spec for SDK generation.
- **Internal (service-to-service):** gRPC — protobuf for efficient serialization at 10k+ calls/sec, strongly typed contracts prevent integration bugs, bidirectional streaming for real-time price feed updates.
- **Admin/reporting:** GraphQL BFF — admin needs flexible queries across multiple domains (product + price + order history). GraphQL serves this aggregation use case without building bespoke REST endpoints for every admin view.

**Operational considerations:**
- API gateway centralizes cross-cutting concerns (auth, rate limit, observability) — do not implement in each service.
- gRPC services are internal only (not exposed beyond the gateway) — reduces attack surface.
- GraphQL receives dedicated rate limiting by query complexity, not just request count.

**Interview trap:** "Just pick one and use it everywhere." One size does not fit all. A system that uses REST for public-facing, gRPC for internal, and GraphQL (optionally) for aggregation is not over-engineered — it is fitting the tool to the job. The complexity is in the gateway configuration and the team's skills, not the protocols themselves.
**Tags:** grpc, rest, graphql, architecture, api-gateway, microservices
