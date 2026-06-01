# REST API — question bank

> Context: pricing engine exposed as a REST API for the frontend (Angular) and other systems (ERP integration). HTTP/JSON consumers. Key areas: idempotency, status code semantics, pagination, versioning.

## Scope

- HTTP metody: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
- semantyka idempotencji i bezpieczeństwa metod
- kody odpowiedzi: 1xx informational, 2xx success, 3xx redirect, 4xx client error, 5xx server error
- REST vs RPC vs GraphQL — porównanie
- HATEOAS i Richardson Maturity Model
- caching: ETag, Last-Modified, If-None-Match, If-Modified-Since, Cache-Control, max-age, no-cache, no-store
- paginacja: offset/limit vs cursor-based vs keyset, Link header
- wersjonowanie: URI (/v1), header (Accept), media type
- uwierzytelnianie: Basic, Bearer (JWT), OAuth2 grant types, API keys, mTLS
- rate limiting: token bucket, leaky bucket, fixed/sliding window, 429 + Retry-After
- OpenAPI/Swagger
- CORS i preflight OPTIONS
- Content-Type, negocjacja: Accept, Accept-Encoding, Accept-Language

---

## Q-RST-001 [bloom: recall]
**Question:** Wymień główne metody HTTP i powiedz krótko, co każda robi.
**Model answer:** **GET** — odczyt zasobu. Bezpieczna i idempotentna. Body w GET jest niestandardowe (nie wszyscy klienci/proxy je przetworzą). **POST** — tworzy zasób lub wykonuje akcję. Ani bezpieczna, ani idempotentna (POST dwa razy zazwyczaj tworzy dwa zasoby). **PUT** — pełna podmiana zasobu. Idempotentna (PUT dwa razy = ten sam stan). **PATCH** — częściowa modyfikacja. Niekoniecznie idempotentna (zależy od formatu — JSON Merge Patch jest, JSON Patch z `op:add` może nie być). **DELETE** — usuwa zasób. Idempotentna (drugi DELETE zwróci 404 albo 204, ale stan końcowy ten sam). **HEAD** — jak GET ale bez body, dla metadata/cache check. **OPTIONS** — pyta jakie metody są wspierane (CORS preflight to przypadek użycia).
**Interview trap:** „PUT vs POST" — częsty trick. Reguła: PUT jak klient generuje URL (`PUT /products/123`), POST jak serwer (`POST /products` zwraca 201 Created z Location). PUT idempotentne, POST nie.
**Tags:** http-methods, basics

## Q-RST-002 [bloom: recall]
**Question:** Co to jest idempotencja i czemu ma znaczenie w REST?
**Model answer:** Idempotentna metoda to taka, której wielokrotne wywołanie z tymi samymi parametrami daje ten sam stan systemu (rezultat odpowiedzi może się różnić, ale state jest stały). PUT, DELETE, GET, HEAD są idempotentne. POST i PATCH nie są (z założenia). **Czemu ma znaczenie:** sieć jest zawodna. Klient wysyła PUT, dostaje timeout — czy to dotarło i serwer zaktualizował, czy nie? Z idempotentną metodą — klient po prostu retry-uje, bezpiecznie. Z nieidempotentną (POST) — retry tworzy duplikat. Stąd patterns: 1) **Idempotency-Key header** — klient generuje UUID, serwer cachuje response na ten klucz. POST staje się idempotent na poziomie aplikacji. 2) **PUT-based create** — klient generuje ID, używa PUT zamiast POST. 3) **Background reconciliation** — wykrywanie i scalanie duplikatów.
**Interview trap:** „GET zawsze idempotentne" — w API tak, ale jeśli GET ma side effect (np. tracking analytics), to idempotencja na poziomie state'u, nie obserwowanego efektu. „Idempotencja = same response" — false. PUT 1: 200 OK. PUT 2: też 200 OK ale stan się nie zmienił → idempotent.
**Tags:** idempotency, semantics

## Q-RST-003 [bloom: recall]
**Question:** Wymień najważniejsze kody HTTP 2xx, 4xx i 5xx i co oznaczają.
**Model answer:** **2xx success:** 200 OK (default success), 201 Created (nowy zasób, zazwyczaj POST/PUT, body opcjonalne, Location header z URL nowego zasobu), 202 Accepted (przyjęte do przetwarzania, async), 204 No Content (sukces, brak body — częste w DELETE, PUT bez return). **4xx client error:** 400 Bad Request (request malformed, np. zła JSON), 401 Unauthorized (brak uwierzytelnienia — myląca nazwa, powinno być "Unauthenticated"), 403 Forbidden (zalogowany ale brak uprawnień), 404 Not Found, 405 Method Not Allowed (np. POST gdzie tylko GET), 409 Conflict (np. concurrent modification, version conflict), 422 Unprocessable Entity (semantically malformed — walidacja biznesowa), 429 Too Many Requests (rate limit). **5xx server error:** 500 Internal Server Error (generic crash), 502 Bad Gateway (upstream error), 503 Service Unavailable (czasowo niedostępny — Retry-After), 504 Gateway Timeout. Inne: 301 Moved Permanently, 304 Not Modified (cache), 307 Temporary Redirect (zachowuje method, w przeciwieństwie do 302), 308 Permanent Redirect.
**Interview trap:** 401 vs 403 — 401 to „nie wiem kim jesteś", 403 to „wiem kim jesteś ale nie wolno ci". 422 vs 400 — 400 to JSON się nie sparsował, 422 to JSON ok ale wartości łamią reguły biznesowe.
**Tags:** http-codes, basics

## Q-RST-004 [bloom: recall]
**Question:** Co to jest HATEOAS?
**Model answer:** Hypermedia As The Engine Of Application State — klient nawiguje po API przez linki zwracane w odpowiedziach, a nie przez hardcoded URL-e. Odpowiedź zawiera `_links` (JSON HAL) lub `links` z URL-ami i ich semantyką: np. produkt zwraca `_links: { self, prices, related_products, edit }`. Klient odkrywa co może zrobić z zasobem dynamicznie. **Richardson Maturity Model:** Level 0 — pojedyncze URI (RPC over HTTP). Level 1 — wiele URI (jeden per zasób). Level 2 — HTTP verbs i kody (większość „REST" API jest na tym poziomie). Level 3 — HATEOAS. **Praktyka:** czysty HATEOAS jest rzadki w produkcji. Większość API jest Level 2 + opcjonalnie OpenAPI dla discovery. Argumenty za HATEOAS — luźniejsza ewolucja, klient nie zależy od dokładnych URL-i. Argumenty przeciw — zwiększa rozmiar payloadu, klienci zazwyczaj i tak hardcodują flow.
**Interview trap:** „Czy to jest REST jak nie ma HATEOAS?" — według Roy Fielding'a (autor pracy doktorskiej REST) bez HATEOAS to jest „HTTP API", nie REST. W codziennym żargonie REST oznacza Level 2 i to jest OK.
**Tags:** hateoas, richardson-model, theory

## Q-RST-005 [bloom: recall]
**Question:** Co robi nagłówek `ETag`?
**Model answer:** ETag (Entity Tag) to identyfikator wersji zasobu — najczęściej hash zawartości lub version number. Serwer zwraca `ETag: "abc123"` w odpowiedzi. Klient zapamiętuje. Następne pytanie: `GET /resource` z `If-None-Match: "abc123"`. Jeśli zasób się nie zmienił — serwer zwraca `304 Not Modified` (puste body, klient używa cached version). Jeśli się zmienił — `200 OK` z nowym body i nowym ETag-iem. **Drugie zastosowanie:** optimistic concurrency. Klient: `PUT /resource` z `If-Match: "abc123"`. Jeśli zasób w międzyczasie zmieniony (ETag się różni) — serwer zwraca `412 Precondition Failed`, klient wie że ktoś go ubiegł. **Strong vs weak ETag:** strong (`"abc"`) — bit-identyczność. Weak (`W/"abc"`) — semantyczna równoważność (np. ten sam zasób z różną kompresją).
**Interview trap:** ETag bywa pomylony z version number. Format ETag: opaque string opaque dla klienta. Server może liczyć jak chce — hash content, MD5, version counter, timestamp. Klient nie powinien parsować, tylko porównywać.
**Tags:** caching, etag, http

## Q-RST-006 [bloom: recall]
**Question:** Co to jest CORS i kiedy się pojawia preflight OPTIONS?
**Model answer:** CORS (Cross-Origin Resource Sharing) to mechanizm bezpieczeństwa w przeglądarkach — domyślnie JS z domeny `app.com` nie może robić fetch do `api.com` (different origin). Serwer musi explicite zezwolić przez header `Access-Control-Allow-Origin`. **Simple requests** (GET/POST/HEAD z safe headers) — przeglądarka wysyła request, serwer odpowiada z ACAO header, jeśli match — JS dostaje response, jeśli nie — błąd. **Preflight** — dla "non-simple" (np. PUT, DELETE, custom headers, Content-Type: application/json) przeglądarka NAJPIERW wysyła OPTIONS request z `Access-Control-Request-Method`, `Access-Control-Request-Headers`, `Origin`. Serwer odpowiada `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`, `Access-Control-Max-Age` (cache preflight). Jeśli OK — przeglądarka dopiero wysyła właściwy request. **Headers:** `Access-Control-Allow-Origin: *` (lub konkretna domena), `Access-Control-Allow-Credentials: true` (dla cookie-based auth — wymaga konkretnego origin, nie *), `Access-Control-Expose-Headers` (które headers JS może czytać).
**Interview trap:** „CORS to security" — częściowo. CORS broni klienta przed exfiltration cookies do attacker-controlled servera. Nie broni servera przed niczym (server-to-server CORS nie obowiązuje). „Wystarczy `Access-Control-Allow-Origin: *`" — dla credentials nie zadziała.
**Tags:** cors, security, browsers

## Q-RST-007 [bloom: recall]
**Question:** Co to jest JWT (JSON Web Token)?
**Model answer:** JWT to compact, URL-safe token — trzy części oddzielone kropkami: `header.payload.signature`. Każda część base64url-encoded. **Header**: `{"alg":"HS256","typ":"JWT"}` — algorytm podpisu. **Payload**: claims, np. `{"sub":"user123","exp":1234567890,"role":"admin"}` — standardowe claims (iss, sub, exp, iat, nbf, aud, jti) i custom. **Signature**: HMAC lub RSA z header.payload + secret/private key. Serwer weryfikuje podpis, ufa payloadowi (nie czyta z bazy). Stosowanie: nagłówek `Authorization: Bearer eyJ...`. **Plusy:** stateless (serwer nie trzyma sesji), self-contained (claims w środku), scalable (dowolny serwer w klastrze może zwalidować). **Minusy:** brak revocation (token jest ważny do `exp`, nie da się invalidate przed czasem bez dodatkowej infrastruktury — blacklist/short TTL + refresh token). Rozmiar — większy niż session id. Jeśli secret wycieknie — wszystkie tokeny do podrobienia.
**Interview trap:** „JWT są szyfrowane" — false, są tylko PODPISANE. Każdy może odczytać payload (base64). Jeśli chcesz szyfrowane — JWE. Stąd: nie wkładaj sekretów do JWT payloadu. „alg: none" — historyczna luka: niektóre biblioteki pozwalały na alg=none (brak podpisu) i akceptowały. Update biblioteki, walidacja alg whitelist.
**Tags:** jwt, auth, tokens

## Q-RST-008 [bloom: recall]
**Question:** Co to jest OpenAPI (Swagger)?
**Model answer:** OpenAPI (dawniej Swagger) to standard opisu REST API — YAML/JSON document opisujący wszystkie endpointy, parametry, request/response schemas, kody błędów, security. Aktualna wersja 3.1 (2021), kompatybilna z JSON Schema. **Co dostarcza:** 1) **Dokumentacja interaktywna** (Swagger UI, Redoc) — przeglądasz API w przeglądarce, klikasz „Try it out". 2) **Code generation** — `openapi-generator` generuje SDK klientów w różnych językach (TypeScript, Java, Python). 3) **Server stubs** — można wygenerować boilerplate serwera. 4) **Validation** — request/response można walidować przeciw schemie automatycznie. 5) **Mocking** — generowanie mock servera dla frontendu zanim backend istnieje. **Przykładowy spec:** `paths: /products: get: responses: 200: description: OK ...`. **Approaches:** spec-first (piszesz spec, generujesz code) vs code-first (annotacje w kodzie generują spec — Spring `@Operation`, `springdoc-openapi`). Code-first powszechniejsze, spec-first bardziej dyscyplinowane.
**Interview trap:** OpenAPI ≠ REST. To opis — możesz opisać RPC, gRPC bridge'owany przez HTTP/JSON, cokolwiek z REST-like. Drugi błąd: nie aktualizować spec gdy API się zmienia → spec drifts → false confidence. Dlatego CI generuje spec z kodu lub waliduje runtime.
**Tags:** openapi, swagger, documentation

---

## Q-RST-009 [bloom: understand]
**Question:** Wytłumacz różnicę między PUT a PATCH.
**Model answer:** **PUT** to pełna podmiana zasobu: wysyłasz całą reprezentację, serwer zastępuje. Brak pól = pola nie istnieją (lub mają domyślną wartość). Idempotentne. Przykład: `PUT /products/123` z `{name: "X", price: 100}` ustawia oba pola, jeśli był jeszcze `description`, zazwyczaj zostaje skasowany. **PATCH** to częściowa modyfikacja: wysyłasz tylko pola które chcesz zmienić. Reszta nietknięta. **Formaty PATCH:**
- **JSON Merge Patch (RFC 7396):** `{name: "X"}` zmienia tylko name. `null` w polu = usuń to pole. Idempotentne.
- **JSON Patch (RFC 6902):** array operacji `[{op: "replace", path: "/name", value: "X"}, {op: "add", path: "/tags/-", value: "new"}]`. Bardziej ekspresywny (operacje add/remove/replace/move/copy/test). Niektóre operacje (add do array) mogą być nieidempotentne.
- **Custom format** — np. firma definiuje własny PATCH format. Niezalecane.
**Praktyka:** PUT używaj gdy klient chce ustawić zasób na konkretny pełny stan. PATCH gdy chce zmienić jedno pole. Dla pricingu — `PATCH /product/123` z `{price: 99}` żeby zmienić tylko cenę bez ryzyka usunięcia name.
**Interview trap:** „PUT z brakującymi polami zachowa stare wartości?" — formalnie nie, PUT to pełna podmiana. Ale wiele API tak nie robi — efektywnie traktują jako merge. Niezgodne ze specyfikacją, ale powszechne. Discovery przez OpenAPI/dokumentację.
**Tags:** put, patch, semantics

## Q-RST-010 [bloom: understand]
**Question:** Wyjaśnij paginację offset-based vs cursor-based. Plusy i minusy każdej.
**Model answer:** **Offset-based**: `GET /products?offset=100&limit=20` zwraca elementy 100-119. Plusy: prosta, klient może skoczyć do dowolnej strony, nawigacja stronicowa naturalna („strona 5 z 100"). Minusy: 1) **performance** — `OFFSET 100000 LIMIT 20` w SQL i tak skanuje 100020 wierszy w bazie, drogie. 2) **inconsistency** — jeśli między pobraniem strony 1 i 2 ktoś usunie element, strony się przesuną i element przeskoczysz lub zobaczysz dwa razy. **Cursor-based** (a.k.a. keyset pagination): `GET /products?after=eyJpZCI6MTIzfQ&limit=20` — kursor zakodowany (zazwyczaj base64) zawiera ostatni widziany id/sort_value. Server: `WHERE id > 123 ORDER BY id LIMIT 20`. Plusy: 1) **stable** — niezależnie od insertów/deletów, kolejność jest deterministyczna. 2) **fast** — index seek na `WHERE id > X` jest O(log n) zawsze, niezależnie od głębokości. Minusy: 1) **brak random access** — nie skoczysz do strony 5, idziesz tylko forward (lub backward). 2) **trudniej UX-owo** dla user-facing pagination. **Hybryda:** offset dla UI z numerowanymi stronami, cursor dla API consumed by other services lub infinite scroll.
**Interview trap:** Cursor pagination wymaga sortowania po STABLE column (`id`, `created_at + id` jako tie-breaker). Bez stabilności (np. sort po name z duplikatami) cursor się gubi.
**Tags:** pagination, performance, cursor

## Q-RST-011 [bloom: understand]
**Question:** Wyjaśnij Bearer JWT auth flow w typowej Single Page App.
**Model answer:** Klasyczny flow: 1) **Login**: SPA wysyła `POST /auth/login` z credentials. Backend waliduje, generuje JWT (signed, exp ~15 min) + refresh token (długoterminowy, np. 30 dni, stored httpOnly cookie albo backend session). Zwraca w response. 2) **API calls**: SPA dorzuca `Authorization: Bearer <jwt>` do każdego wywołania. Backend waliduje podpis i exp, czyta claims, autoryzuje. 3) **Token expires**: backend zwraca 401. SPA wykrywa, używa refresh tokena (`POST /auth/refresh`) by dostać nowy JWT. Jeśli refresh failed — relogin. 4) **Logout**: SPA czyści JWT z pamięci, opcjonalnie wywołuje `POST /auth/logout` żeby invalidate refresh token (server-side blacklist). **Storage trade-offs JWT w SPA:**
- **localStorage** — dostępne dla JS, podatne na XSS (atakujący JS-em wykrada token).
- **sessionStorage** — j.w. + traci się przy zamknięciu zakładki.
- **httpOnly cookie** — niewidoczne dla JS (lepsze przeciwko XSS), ale podatne na CSRF (potrzebuje SameSite=Strict + CSRF token).
- **Memory only (in-app variable)** — najbezpieczniejsze, ale traci się przy refresh strony (musisz refresh tokenem odzyskać).
**Best practice:** JWT w memory + refresh token w httpOnly cookie z SameSite=Strict.
**Interview trap:** „localStorage jest OK" — naïve. XSS ≠ niemożliwe (npm supply chain attack, third-party widget z JS). Druga: silent refresh — JWT exp 15 min, refresh przez tichym fetch przed expiry, żeby user nie zobaczył 401.
**Tags:** jwt, auth, spa, security

## Q-RST-012 [bloom: understand]
**Question:** Czemu używamy versioning API i jakie są strategie?
**Model answer:** Versioning chroni przed breaking changes — gdy API ewoluuje, starzy klienci nie umrą natychmiast. **Strategie:**
1. **URI versioning**: `/v1/products`, `/v2/products`. Plus: explicit, łatwo cache-ować, debug-friendly (URL pokazuje wersję). Minus: zmiana wersji oznacza zmianę URL — formalnie różne zasoby. Mainstreamowy wybór.
2. **Header versioning**: `Accept: application/vnd.myapi.v1+json` lub custom `X-API-Version: 1`. Plus: URL stabilny (resource = same). Minus: trudniejsze do testowania w przeglądarce, cache musi uwzględnić header w key.
3. **Query param**: `/products?version=1`. Plus: prosty. Minus: niesystemowy, łatwo zapomnieć, mieszanie z innymi query params.
4. **Content-Type negotiation** — najbardziej REST-orthodox, ale rzadkie w praktyce.
**Best practices:** Semantic versioning na API ma sens (major.minor.patch). Major = breaking. Minor = backward-compatible additions. Patch = bug fixes. **Deprecation:** `Deprecation: <date>` header, `Sunset: <date>` header z RFC 8594, ogłoszenie z wyprzedzeniem (3-12 miesięcy), telemetria użycia per wersja. **Anti-pattern:** wersjonowanie pojedynczych endpointów (`/v1/products` ale `/v2/orders`) — chaos. Wersjonuj cały API jako spójną całość.
**Interview trap:** „Wystarczy backward-compatible changes, wersji nie potrzeba" — często prawda. Ale czasem trzeba zmienić semantykę (np. `price` zwracało int, teraz BigDecimal jako string) — to breaking. Wersjonowanie jest narzędziem na takie sytuacje.
**Tags:** versioning, api-design, evolution

## Q-RST-013 [bloom: understand]
**Question:** Co to jest rate limiting i jak go zaimplementujesz po stronie serwera?
**Model answer:** Rate limiting ogranicza ile requestów per czas może wykonać klient (lub IP, lub API key, lub user). Cele: ochrona przed DoS, fair usage między klientami, kontrola kosztów backend (DB, third-party API). **Algorytmy:**
- **Fixed window**: zliczasz requesty w bucket per minutę. Reset co minutę. Prosty. Problem: burst na granicy okna (59. sek + 0. sek = 2x limit przez 1 sek).
- **Sliding window log**: zapisujesz timestamps wszystkich requestów. Liczysz ile w ostatnich 60s. Dokładny ale memory-intensive.
- **Sliding window counter**: kombinacja — interpolacja między buckets. Mniej memory, mniej accuracy.
- **Token bucket**: bucket pojemności N, regenerujący się R tokens/sec. Każdy request konsumuje token. Burst do N, sustained R/sec. Najbardziej elastyczny.
- **Leaky bucket**: jak token bucket ale wolumin „wycieka" stałym tempem — wymusza smooth rate.
**Implementacja:**
- **Redis-based**: `INCR key WITH EXPIRE` lub `lua script` dla atomicznego token bucket. Redis cluster dla scale.
- **API gateway**: Kong, Tyk, AWS API Gateway, NGINX limit_req — ready-made. Preferowane w prod.
- **Per-user** wymaga identyfikacji (API key, JWT claim, session).
**Response na limit:** `429 Too Many Requests` + headers: `Retry-After: 60` (sekundy), `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (unix timestamp). Klient powinien respektować Retry-After.
**Interview trap:** Rate limit per IP łatwo obejść przez NAT albo VPN. Rate limit per API key wymaga identyfikacji każdego klienta. „Rate limit + load balancer" — load balancer rozsyła requesty po wielu instancjach, każda swój licznik = łamanie limitu. Stąd centralny store (Redis).
**Tags:** rate-limiting, security, performance

## Q-RST-014 [bloom: understand]
**Question:** Co robi nagłówek `Cache-Control` i jakie są typowe wartości?
**Model answer:** Cache-Control kontroluje cachowanie po stronie klienta i pośrednich proxy. **Najczęstsze dyrektywy:**
- `max-age=N` — TTL w sekundach. Po `N` cache jest stale, klient powinien revalidate.
- `no-cache` — *zawsze* revalidate przed użyciem (z ETag/If-Modified-Since), ale wciąż można cachować. Mylące — nazwa sugeruje brak cache, ale to znaczy „check before serving".
- `no-store` — w ogóle nie cachuj. Sensitive data, single-use tokens.
- `public` — można cachować w shared cache (CDN). 
- `private` — tylko per-user cache (browser local), nie CDN.
- `must-revalidate` — gdy stale, OBOWIĄZKOWO check, nie serwuj stale (default jest opcjonalny).
- `stale-while-revalidate=N` — możesz serwować stale przez N sec dopóki revalidacja w tle.
- `s-maxage=N` — jak max-age ale tylko dla shared cache.
- `immutable` — zawartość nigdy się nie zmieni (np. hashed asset URL). Klient nie revalidate nigdy.
**Przykłady scenariuszy:**
- Static assets z hashem: `Cache-Control: public, max-age=31536000, immutable`.
- HTML index: `Cache-Control: no-cache, must-revalidate` (always check, but cache locally).
- API response: `Cache-Control: private, max-age=60` (per-user, 1 min).
- Sensitive endpoint: `Cache-Control: no-store, private`.
**Interview trap:** Każda intermediate cache (CDN, ISP proxy, corporate proxy) widzi nagłówki. Brak `private` na response z user-specific data → CDN cachuje i serwuje innym userom — disaster.
**Tags:** caching, http-headers

## Q-RST-015 [bloom: understand]
**Question:** Wytłumacz różnicę między REST a GraphQL.
**Model answer:** **REST** — multiple endpoints, jeden zasób per URL, semantyka przez HTTP verbs i kody. Klient pyta dokładnie ten resource, server zwraca pełną reprezentację. **GraphQL** — jeden endpoint (`/graphql`), klient deklaratywnie określa co chce dostać (które pola, które relacje), server zwraca dokładnie to. Schema w SDL z typami i query/mutation/subscription. **Plusy GraphQL:** 1) brak over-fetchingu (REST często zwraca pola których klient nie używa), 2) brak under-fetchingu (REST wymaga N+1 calls dla nested data — GraphQL jeden), 3) typowanie strict, 4) introspection out of the box. **Plusy REST:** 1) caching trywialny (HTTP cache), 2) prostsze infrastrukturalnie (zwykły HTTP, CDN, proxies, monitoring działają), 3) discoverable (URL == zasób), 4) ekosystem narzędzi (curl, Postman, swagger). **Trade-offy GraphQL:** caching trudniejszy (POST z body — HTTP cache nie pomoże), N+1 problem przeniesiony do resolverów (DataLoader), security (query depth limits, complexity analysis żeby chronić przed `query { user { friends { friends { friends { ... } } } } }` DoS), trudniejszy file upload, harder rate limiting (per-query a nie per-endpoint).
**Pricing engine specyficznie:** REST zwykle wystarczy — endpointy są dobrze zdefiniowane, dane są strukturalnie ograniczone. GraphQL ma sens przy frontend-heavy app gdzie client potrzebuje różnych shape'ów danych (np. mobile vs web). **Trzecia opcja:** gRPC — lepsze dla service-to-service, gdy klienci są zarządzeni.
**Interview trap:** „GraphQL zastąpi REST" — hype. GraphQL ma świetne use-cases, REST ma świetne use-cases, gRPC i MessagePack też mają. Decyzja per project.
**Tags:** rest, graphql, comparison

## Q-RST-016 [bloom: understand]
**Question:** Co to jest OAuth2 i jakie są główne grant types?
**Model answer:** OAuth2 to framework autoryzacji — pozwala third-party app na dostęp do zasobów usera bez przekazywania hasła. Strony: **Resource Owner** (user), **Client** (app trzeciej strony), **Authorization Server** (np. Google, Auth0), **Resource Server** (API). **Główne grant types:**
1. **Authorization Code (z PKCE)** — dla web/mobile apps z user interakcją. Flow: app redirects do authz server, user się loguje i autoryzuje, authz server redirects back z `code`, app wymienia `code` na `access_token` + `refresh_token` przez backend channel. PKCE (RFC 7636) chroni przed code interception — szczególnie ważne dla SPA i mobile bez confidential client secret. **To jest dziś rekomendowany default dla user-facing flow.**
2. **Client Credentials** — dla service-to-service (machine-to-machine). App ma `client_id` + `client_secret`, wysyła do authz server, dostaje `access_token`. Brak usera w flow.
3. **Implicit** (deprecated) — historyczny dla SPA, dziś zastąpiony przez Auth Code + PKCE.
4. **Resource Owner Password Credentials** (deprecated) — user daje hasło bezpośrednio aplikacji. Antypattern, używać tylko w scenariuszach legacy.
5. **Refresh Token** — pomocniczy: gdy access_token wygaśnie, refresh token pozwala dostać nowy bez ponownego loginu.
6. **Device Code** — dla TV/console gdzie klawiatura niewygodna; user wpisuje kod na innym urządzeniu.
**OpenID Connect** — warstwa identity nad OAuth2: dodaje `id_token` (JWT z user info), standardowe claims, discovery endpoint.
**Interview trap:** „OAuth2 = login" — nie. OAuth2 to autoryzacja (delegacja dostępu), nie autentykacja. OpenID Connect dodaje autentykację. Wielu używa OAuth2 do logowania niepoprawnie — token może mieć puste user identity.
**Tags:** oauth2, oidc, auth

---

## Q-RST-017 [bloom: apply]
**Question:** Zaprojektuj REST API dla produktu cennikowego: get listy, get pojedynczy, create, update, delete. Pokaż endpointy z metodami i kody odpowiedzi.
**Model answer:**
```
GET    /api/v1/products              List products (paginated)
                                     200 OK z {data: [...], pagination: {...}}
                                     ?country=PL&segment=B2B&page=2&size=20

GET    /api/v1/products/{id}         Get single product
                                     200 OK z product object
                                     404 Not Found jeśli brak

POST   /api/v1/products              Create new product
                                     Body: {name, sku, base_price, ...}
                                     201 Created + Location header z URL
                                     400 Bad Request jeśli malformed
                                     422 Unprocessable Entity jeśli walidacja biznesowa
                                     409 Conflict jeśli SKU już istnieje

PUT    /api/v1/products/{id}         Replace product (full)
                                     Body: pełna reprezentacja
                                     200 OK lub 204 No Content
                                     404 Not Found
                                     412 Precondition Failed (jeśli If-Match nie zgadza się)

PATCH  /api/v1/products/{id}         Partial update
                                     Body: JSON Merge Patch lub JSON Patch
                                     Header: Content-Type: application/merge-patch+json
                                     200 OK
                                     404 Not Found
                                     422 jeśli result invalid

DELETE /api/v1/products/{id}         Soft-delete product
                                     204 No Content
                                     404 Not Found (idempotent: można też 204 dla brakującego)

GET    /api/v1/products/{id}/prices  Sub-resource: ceny per kraj/segment
GET    /api/v1/products/{id}/history History audit
```
**Sub-resources** dla relacji (prices belong to product). **Pagination** w GET listy. **Filtering** przez query params. **HATEOAS** opcjonalnie: response zawiera `_links: {self, edit, prices}`. **Idempotency-Key header** dla POST gdzie potrzeba retry-safe create.
**Interview trap:** „Czy DELETE jest hard czy soft?" — semantyka HTTP nie określa. Polityka aplikacji. Pricing zazwyczaj soft (audit). Deklaruj w dokumentacji. „Verb in URL" (`POST /products/createNew`) — antypattern, REST używa metod HTTP.
**Tags:** api-design, rest, crud, pricing

## Q-RST-018 [bloom: apply]
**Question:** Klient mobilny robi POST tworzący zamówienie. Sieć wybucha podczas requestu — klient nie wie czy zamówienie utworzono. Jak zaprojektujesz API by klient mógł bezpiecznie retry?
**Model answer:** **Idempotency-Key pattern** (Stripe-style). Klient generuje UUID dla próby utworzenia, wysyła:
```
POST /api/v1/orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Body: { customer_id: 123, items: [...], total: 999 }
```
Serwer:
1. Przyjmując request, sprawdza czy `Idempotency-Key` był już użyty (lookup w store, np. Redis z TTL 24h).
2. Jeśli był: zwraca cached response (ten sam status + body co poprzednio).
3. Jeśli nie: wykonuje akcję (utworzenie order), zapisuje response w cache z key.
**Implementacja po stronie serwera:**
- Atomic operacja: `SET key value NX EX 86400` (Redis NX: only if not exists). Jeśli key jest — to retry, zwróć cached.
- Race condition: dwa concurrent requests z tym samym key → oba próbują insert. Redis NX gwarantuje że tylko jeden wygra. Drugi czeka albo retry (krótka pauza).
- W bazie: jeśli order ma unique constraint na `(customer_id, idempotency_key)`, to też chroni.
- TTL: 24h to sensible. Po tym kluczu jest już przedawnione, nowy retry tworzy nowy order — co jest OK w praktyce, klient po 24h godzinach zazwyczaj się zorientował.
**Klient flow:** generate UUID, retry z tym samym UUID dopóki dostaje sieciowy błąd. Dopiero gdy dostaje 2xx/4xx z odpowiedzią — wie co stało.
**Alternatywne podejścia:**
- **PUT-based create**: klient generuje order_id, używa `PUT /orders/{client_generated_id}`. PUT jest idempotent natywnie.
- **Optimistic create + reconciliation**: pozwól duplikatów, scal je background jobem.
**Interview trap:** Idempotency-Key musi obejmować body. Klient retry-uje ten sam request. Jeśli wyśle ten sam key z innym body — server powinien odrzucić (422 Unprocessable Entity, key conflict).
**Tags:** idempotency, retry, distributed-systems

## Q-RST-019 [bloom: apply]
**Question:** Klient front-endu w Angularze ma listę produktów z paginacją. Pokaż jak zaimplementować cursor-based paginację po stronie API i jak klient ma to konsumować.
**Model answer:**
**API:**
```
GET /api/v1/products?limit=20&after=eyJpZCI6MTIzfQ

Response 200:
{
  "data": [
    {"id": 124, "name": "Foo", "price": 100},
    ...
    {"id": 143, "name": "Bar", "price": 200}
  ],
  "pagination": {
    "next_cursor": "eyJpZCI6MTQzfQ",
    "has_more": true
  }
}
```
Cursor `eyJpZCI6MTIzfQ` to base64 z `{"id":123}`. Backend SQL: `SELECT * FROM product WHERE id > 123 ORDER BY id LIMIT 21` (21 by sprawdzić czy jest 21. element = has_more). Zwraca 20 elementów + `next_cursor` to id ostatniego.

**Headers approach (RFC 5988 Web Linking):**
```
Link: <https://api.example.com/products?after=eyJpZCI6MTQzfQ>; rel="next",
      <https://api.example.com/products>; rel="first"
```
Plus: standardowe RFC, klienty (np. `octokit`) parsują automatycznie. Minus: parse w Angularze trochę pracy, JSON pagination object jest często wygodniejszy.

**Angular klient:**
```typescript
loadMore(cursor?: string) {
  const params = cursor ? {after: cursor, limit: 20} : {limit: 20};
  this.http.get<ProductPage>('/api/v1/products', {params})
    .subscribe(page => {
      this.products = [...this.products, ...page.data];
      this.nextCursor = page.pagination.next_cursor;
      this.hasMore = page.pagination.has_more;
    });
}
```
Infinite scroll: triggeruj `loadMore(this.nextCursor)` przy IntersectionObserver na ostatnim elemencie listy.

**Multi-column sort:** kursor zawiera tuple `{sort_value: 99.99, id: 123}`, SQL: `WHERE (sort_value, id) > (99.99, 123)` — row constructor compare. Wymaga indeksu na `(sort_value, id)`.

**Interview trap:** Cursor ujawnia internal id — niektórzy preferują obfuscation (HMAC signed cursor). Dla pricingu zazwyczaj OK pokazać id. Drugi: cursor nie umie skoczyć do strony 5 — UX musi być infinite scroll lub „Load more", nie pagination dots.
**Tags:** pagination, cursor, angular, api-design

## Q-RST-020 [bloom: apply]
**Question:** Zaprojektuj endpoint do bulk update cen produktów (np. 1000 produktów w jednym requeście). Jak obsłużysz partial failures?
**Model answer:**
**API:**
```
POST /api/v1/products/prices/bulk-update
Idempotency-Key: <uuid>
Body:
{
  "items": [
    {"product_id": 123, "country": "PL", "price": 99.99},
    {"product_id": 456, "country": "DE", "price": 119.99},
    ...
  ]
}
```
**Strategie obsługi błędów:**

**Wariant A — All-or-nothing (transactional):** wszystkie 1000 wpisów w jednej transakcji. Jeden błąd → rollback, klient dostaje 422 z listą problemów. Plus: simple consistency. Minus: jeden zły item blokuje cały batch — frustrujące dla użytkownika.

**Wariant B — Per-item, partial success:** każdy item przetwarzany niezależnie. Response zawiera per-item status:
```
207 Multi-Status (lub 200 OK)
{
  "results": [
    {"index": 0, "product_id": 123, "status": "success"},
    {"index": 1, "product_id": 456, "status": "error", "error": {"code": "INVALID_PRICE", "message": "Price must be positive"}},
    ...
  ],
  "summary": {"total": 1000, "success": 998, "failed": 2}
}
```
Klient wie dokładnie co się udało. Plus: granularny feedback. Minus: złożoność transakcyjna (każdy item potrzebuje własnego savepointa lub retry).

**Wariant C — Async batch:** klient POST, server zwraca 202 Accepted + `Location: /batch-jobs/{id}`. Klient pollyje status. Idealny dla naprawdę dużych batches (>10k). Plus: nie blokuje HTTP request, możesz uruchomić heavy processing. Minus: more complex (job tracking).

**Decyzja:** Dla 1000 wpisów wariant B jest sensible balance. Dla 100k+ — wariant C async.

**Walidacja przed zapisem:** preflight pass — sprawdź wszystkie items walidacją statyczną (price > 0, country valid, product exists), potem zapis. Wcześnie wykryte błędy → fail fast, nie zaczynaj transakcji.

**Pricing-specific:** 
- Audit log per zmiana (`price_history` insert per item).
- Validation tier-pricing rules (np. nie może być niższa niż MAP).
- Notify subscribers (Kafka event per change albo bulk event).

**Interview trap:** „1000 itemów w jednej transakcji" — w pricingu może lock-blockować innych. Lepiej batches po 100 w osobnych transakcjach. Trade-off między atomicity a concurrency.
**Tags:** bulk-operations, partial-failure, pricing, api-design

## Q-RST-021 [bloom: apply]
**Question:** API zwraca cache-able dane (cennik). Zaprojektuj cache control headers tak by klient i CDN cachowali, ale dane były stale-fresh.
**Model answer:**
```
GET /api/v1/products/{id}/price?country=PL

Response 200:
ETag: "v123abc456"
Cache-Control: public, max-age=300, s-maxage=600, stale-while-revalidate=60, must-revalidate
Last-Modified: Wed, 06 May 2026 12:00:00 GMT
Vary: Accept-Language

{
  "product_id": 123,
  "country": "PL",
  "price": 99.99,
  "currency": "PLN"
}
```
**Co znaczy:**
- `public` — CDN może cachować (ten zasób nie jest user-specific).
- `max-age=300` — browser/client cache przez 5 min.
- `s-maxage=600` — CDN cache przez 10 min (dłużej, bo CDN serves wielu).
- `stale-while-revalidate=60` — przez 60 sec po expiry, klient/CDN może zwrócić stale, w tle revalidating.
- `must-revalidate` — po pełnej expiracji obowiązkowo revalidate, nie podawaj stale.
- `ETag` — wersja zasobu, klient użyje `If-None-Match` przy revalidacji.
- `Vary: Accept-Language` — cache key zależy od tego nagłówka (ten sam URL, różne języki = różne entries).

**Klient flow:**
1. First request → hit DB → response z ETag.
2. Subsequent in 5 min → browser zwraca z cache, brak network.
3. After 5 min, before 6 min (s-w-r) → browser zwraca stale, async fetch w tle.
4. After 6 min → fetch z `If-None-Match: "v123abc456"`. Server porównuje. Jeśli ten sam — `304 Not Modified` (puste body, fast). Jeśli inny — `200 OK` z nowymi danymi i nowym ETag.

**Invalidation:** gdy cena się zmienia w DB:
- Stary ETag staje się invalid → next revalidation pobierze nową wersję.
- Dla aktywnej invalidation (chcemy by klient od razu się dowiedział) — purge CDN cache po API call (np. CloudFront create-invalidation, Cloudflare cache.purge).
- Stałe TTL 300s = max stale window 5 min — często akceptowalne dla pricingu (nie real-time stocks).

**Interview trap:** `Vary: *` (wildcard) defacto wyłącza cache shared. Specyficzne `Vary` (Authorization, Accept-Language, Accept-Encoding) zwiększa rozdrobnienie cache, ale jest precyzyjne. Druga: brak ETagu → revalidation przez `If-Modified-Since` mniej precyzyjne (1-second resolution).
**Tags:** caching, etag, cache-control, cdn, pricing

## Q-RST-022 [bloom: apply]
**Question:** Pokaż jak zaimplementujesz uwierzytelnianie API key (per-client) w REST API.
**Model answer:**
**Header pattern (preferowane):**
```
GET /api/v1/products
Authorization: Bearer <API_KEY>
```
Lub własny header dla rozróżnienia od JWT:
```
X-API-Key: <key_value>
```

**Generation:** API key to losowy string (np. 32 bytes z `openssl rand -base64 32`), prefix dla identyfikacji typu (`pk_live_xxx`, `sk_test_xxx`). Stripe-style: `sk_live_51H...`. Prefix pomaga w incident response (zauważasz secret w GitHubie po prefiksie).

**Storage po stronie servera:**
- **Hash key, nie plain.** Tabela `api_keys (id, key_hash SHA256, client_id, name, scopes, created_at, expires_at, last_used_at, revoked_at)`. Plain key pokazujesz tylko raz — przy create — i nie zapisujesz.
- Po przyjęciu requestu — hash incoming key, lookup po hash. Constant-time compare.
- Rate limit per key.
- Audit log usage per key (`last_used_at` minimum, opcjonalnie pełny log).

**Validation flow:**
```python
def authenticate(request):
    raw_key = request.headers.get('X-API-Key')
    if not raw_key:
        raise UnauthorizedException("Missing API key")
    key_hash = sha256(raw_key)
    record = db.query("SELECT * FROM api_keys WHERE key_hash = ? AND revoked_at IS NULL", key_hash)
    if not record:
        raise UnauthorizedException("Invalid API key")
    if record.expires_at and record.expires_at < now():
        raise UnauthorizedException("Key expired")
    db.update("UPDATE api_keys SET last_used_at = ? WHERE id = ?", now(), record.id)
    return record.client_id, record.scopes
```

**Best practices:**
- HTTPS only (klucz w plaintext header — TLS chroni in-transit).
- Scopes/permissions per key (`read:products`, `write:prices`).
- Expiration (każdy key ma TTL, np. 1 rok).
- Rotation flow (klient może wygenerować nowy, używać oba przez okres przejściowy, revoke stary).
- Multi-factor: API key + IP allowlist dla wrażliwych operacji.
- Logging: nigdy nie loguj plain key. W logach max-prefix (8 chars) plus hash.

**Interview trap:** Klucz w query param (`?api_key=xxx`) — antypattern: leaks w logach (proxy, CDN, browser history), w referrer headerach. Header zawsze. Drugi: API key w env var po stronie klienta backendowego = OK, w SPA = NIE (każdy widzi w bundle).
**Tags:** api-key, auth, security

## Q-RST-023 [bloom: apply]
**Question:** Zaprojektuj endpoint do przesłania CSV z cennikiem (multipart/form-data). Jak obsłużysz dużo plik (50 MB)?
**Model answer:**
**Synchroniczne przyjęcie + async processing:**
```
POST /api/v1/pricelist/import
Content-Type: multipart/form-data; boundary=----xxx
Content-Length: 52428800

------xxx
Content-Disposition: form-data; name="file"; filename="prices_2026_05.csv"
Content-Type: text/csv

product_id,country,price
123,PL,99.99
456,DE,119.99
...
------xxx--
```
**Server flow:**
1. Stream upload do object storage (S3/GCS/Azure Blob) — nie do pamięci serwera. Spring `MultipartFile` → S3 multipart upload albo bezpośrednio presigned URL (klient uploaduje do S3, daje server tylko reference).
2. Zwróć **202 Accepted** + `Location: /api/v1/jobs/{job_id}`:
   ```
   202 Accepted
   Location: /api/v1/jobs/abc123
   {"job_id": "abc123", "status": "processing", "uploaded_at": "..."}
   ```
3. Job uruchamia się asynchronicznie (Spring `@Async`, queue: RabbitMQ/Kafka, worker reads file z S3, parsuje, wstawia do DB w batches).
4. Klient pollyje `GET /api/v1/jobs/abc123` co 5 sec:
   ```
   {"job_id":"abc123", "status":"processing|completed|failed", "progress": {"total": 10000, "processed": 5000, "errors": 12}, "result_url": "/api/v1/jobs/abc123/result"}
   ```
5. Lub **WebSocket / Server-Sent Events** dla push updates zamiast pollingu.
**Result download:**
```
GET /api/v1/jobs/abc123/result → CSV z błędami per row + status final
```

**Why async:** 50 MB CSV może mieć 1M wierszy. Synchroniczne przetwarzanie = HTTP timeout (zazwyczaj 30s-5min limit). Worker może mielić godzinę.

**Direct-to-S3 alternative:** klient pyta `POST /api/v1/pricelist/import/upload-url` → server zwraca presigned S3 URL ważny 15 min. Klient PUT do S3 bezpośrednio. Server dostaje S3 event (S3 → SNS → SQS → worker), uruchamia job. Plus: server nigdy nie widzi bytów file, zero load. Świetne dla bardzo dużych plików.

**Walidacja & error reporting:**
- Per-row validation. Jeden błędny wiersz nie blokuje reszty.
- Error CSV download z details `(row_number, error_message)` — biznes może otworzyć w Excelu, naprawić, reupload.

**Interview trap:** Synchroniczne `POST /import` z dużym CSV — częsty antywzorzec. Timeouts, OOM, klient blokowany. Async + job pattern jest standard. Drugi błąd: brak progress reportingu → klient nie wie czy działa.
**Tags:** file-upload, async, batch, pricing

## Q-RST-024 [bloom: apply]
**Question:** Zaprojektuj rate limiting dla API: 100 requests/min per API key, 10 requests/sec burst limit.
**Model answer:**
**Algorytm: token bucket** (oba limity).
- **Sustained:** bucket pojemność 100, regeneration 100/60 ≈ 1.67 tokens/sec → 100 requests per minute averaged.
- **Burst:** drugi bucket pojemność 10, regeneration 10/sec → max 10 requests w jednym momencie.
- Każdy request konsumuje 1 token z OBU bucketów. Jeśli któryś pusty — 429.

**Implementacja w Redis:**
```lua
-- KEYS[1]: bucket key (np. "ratelimit:api_key:abc:sustained")
-- ARGV[1]: capacity (100)
-- ARGV[2]: refill_rate (per sec) (1.67)
-- ARGV[3]: now timestamp
-- ARGV[4]: cost (1)

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or ARGV[1]
local last_refill = tonumber(bucket[2]) or ARGV[3]
local now = tonumber(ARGV[3])
local elapsed = now - last_refill
local refilled = math.min(tonumber(ARGV[1]), tokens + elapsed * tonumber(ARGV[2]))

if refilled >= tonumber(ARGV[4]) then
  redis.call('HMSET', KEYS[1], 'tokens', refilled - tonumber(ARGV[4]), 'last_refill', now)
  redis.call('EXPIRE', KEYS[1], 3600)
  return 1  -- allowed
else
  redis.call('HMSET', KEYS[1], 'tokens', refilled, 'last_refill', now)
  return 0  -- denied
end
```
Wykonujesz Lua script atomic per request, dla obu bucketów.

**Response when allowed:**
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1714986000
X-RateLimit-Burst-Remaining: 6
```

**Response when denied:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 17
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1714986000

{
  "error": "rate_limit_exceeded",
  "message": "Too many requests. Retry in 17 seconds.",
  "retry_after_sec": 17
}
```

**Per-tier limits:** różne plany (free, pro, enterprise) mają różne limity. Lookup per API key → load policy → apply limits.

**Distributed scenario:** wszystkie nody serwera używają tego samego Redis. Race condition mitigated przez atomic Lua script.

**Interview trap:** „Rate limit per IP" — łatwo obejść (NAT, VPN). Per API key (lub user) jest accountable. Drugi: brak `Retry-After` → klienci robią retry-storm i pogłębiają problem. `Retry-After` z exponential backoff po stronie klienta jest właściwe.
**Tags:** rate-limiting, redis, token-bucket, implementation

---

## Q-RST-025 [bloom: analyze]
**Question:** Twój zespół debatuje czy używać REST czy GraphQL dla nowego API pricingu. Co zdecydujesz i dlaczego?
**Model answer:** **Diagnoza pierwsza — co jest realne potrzebą?** Pricing API typowo: a) lookup pojedynczych cen, b) bulk pricelist export, c) admin CRUD na cennikach, d) historia/audyt. Te access patterns są dobrze zdefiniowane, nie zmienne — to grunt dla REST, nie GraphQL.
**Argumenty za REST:**
- HTTP cache (CDN dla pricelistów) działa out of the box. GraphQL POST = brak HTTP cache, trzeba persisted queries lub APQ.
- Łatwy rate limiting per endpoint (`/products/*` może mieć inne limity niż `/admin/*`).
- Standard infrastructure: monitoring, logging, distributed tracing — wszystko działa bez specjalnej konfiguracji.
- Łatwiej eksplicit-ne kontrakty per use case (klient nie zaskoczy server-a query'em który wytwarza N+1).
- Łatwiejsze API key auth, scopes per endpoint.
- Frontend zespół zna REST i ma narzędzia (Angular HttpClient, Postman).
- OpenAPI spec → SDK generation prostszy.

**Argumenty za GraphQL:**
- Frontend potrzebuje różnych shape'ów danych (niektóre views potrzebują tylko cen, inne pełnych produktów + history). REST musiałby mieć kilka wariantów lub field selection (`?fields=id,price`).
- Mobile + web + admin — różni konsumenci, różne potrzeby.
- Realtime przez subscriptions jeśli pricing live-updateowany.

**Decyzja konkretna w pricing engine:** **REST jako baseline.** Pricing to stable domain z dobrze zdefiniowanymi resource. CDN-able pricelist endpoints są ogromnym wygraną. Jeśli za rok zespół zauważy że frontend potrzebuje kompozycji danych — można dodać GraphQL gateway nad REST (np. Apollo Federation), bez wyrzucania REST core.

**Trzecia opcja — hybryda**: REST dla data plane (wysoki throughput, cache-friendly), GraphQL dla aggregation/BI views.

**Interview trap:** „GraphQL bo Facebook" — cargo cult. Decision based on team capability + real use cases. „REST jest old-school" — REST jest dokładnie odpowiedni dla domen z stable resources. Hype-driven decisions kończą się rewriting po 2 latach.
**Tags:** rest, graphql, decision, pricing, architecture

## Q-RST-026 [bloom: analyze]
**Question:** API odpowiada 500ms średnio. Gdzie szukasz wąskiego gardła?
**Model answer:** Diagnoza — measure first, guess never. **Warstwy do prześledzenia:**
1. **Network/Edge:** TLS handshake, DNS, CDN. Jeśli klient i serwer w różnych regionach, sam network dodaje 100-300ms RTT. Sprawdź z różnych lokalizacji (curl with `-w "time_connect: %{time_connect} time_starttransfer: %{time_starttransfer} time_total: %{time_total}\n"`).
2. **Load balancer:** czy LB nie kolejkuje? `health checks`, connection pool exhausted?
3. **Application server:** worker thread pool wyczerpany? GC pauses (Java)? Sprawdź metryki JVM: heap, GC time, thread states.
4. **Application logic:** profiler (async-profiler, Spring Boot Actuator + Micrometer + Prometheus). Najczęstsze:
   - **N+1 queries** — JPA/Hibernate lazy loading. Klasyk: pobierasz 100 products, każdy lazy-loaduje 10 prices. 1001 query. Solution: JOIN FETCH lub batch fetching.
   - **Missing indexes** — slow query log w bazie, EXPLAIN suspicious queries.
   - **Synchronous calls do innych services** — łańcuch service-to-service. Każdy +50ms = lawina. Solution: parallelize, cache, fail-fast.
   - **JSON serialization** — duże response = latency. Solution: paginate, compress, mniej pól.
5. **Database:** slow query log, `pg_stat_statements`. Top 10 queries by total time + by mean time. Indexowanie, query rewrite, materialized views, denormalization.
6. **External APIs:** distributed tracing (Jaeger, Zipkin) pokaże span breakdown. Czasem 80% czasu to call do CRM albo płatności.
7. **Cache:** brak cache na kosztownych operacjach? Cache hit rate w dashboardzie. Cache miss for hot data → optymalizuj.

**Tooling:**
- **Distributed tracing** (OpenTelemetry, Jaeger) — most powerful, pokazuje cały waterfall.
- **APM** (DataDog, New Relic) — często out of the box.
- **Custom timing logs** — gdy nic innego.

**Common findings w pricingu:**
- Calculation per request bez cache (cennik per (product, country) — cache, hit rate >95%).
- N+1 dla customer.discounts.
- Synchronous call do tax service per request.
- Brakujący composite index `(product_id, country, valid_from)`.

**Interview trap:** „Dorzuć więcej maszyn" — bez diagnozy = waste. Słowny optymalizm: „przepisz na Rusta" — zazwyczaj problem jest w logice, nie w runtime. Knuth: premature optimization.
**Tags:** performance, debugging, observability

## Q-RST-027 [bloom: analyze]
**Question:** Twój API używa session-based auth (cookie). Klient mobilny chce z niego korzystać. Co zalecasz?
**Model answer:** Session-based auth w mobile jest niewygodny: 1) cookies w native mobile wymagają specjalnej obsługi (nie wszystkie HTTP libraries mają cookie jar by default), 2) brak browser context = brak SameSite/Secure flags, 3) session w bazie wymaga sticky session albo shared session store. **Alternatywy do rekomendacji:**

**Wariant 1 — JWT (preferowane dla green-field mobile):** klient autoryzuje się raz (login → POST z credentials), dostaje access JWT (15 min) + refresh token (długoterminowy). Każdy request ma `Authorization: Bearer <jwt>`. Plus: stateless, scaluje, działa wszędzie. Minus: revocation tricky (krótki TTL + refresh token rotation).

**Wariant 2 — OAuth2 z token endpoint:** dla integracji z third-party identity (Google, Apple). Mobile robi authorization code + PKCE flow, dostaje access_token. Standard OAuth2.

**Wariant 3 — API keys per device:** prosty pattern dla M2M-like mobile (np. IoT). Klient ma key wygenerowany przy onboarding. Minus: brak per-user accountability bez dodatkowej warstwy.

**Wariant 4 — Hybrid:** mobile używa session token zwracany w response body przy login (nie cookie). Klient zapisuje w secure storage (iOS Keychain, Android Keystore), wstawia w `Authorization` header. Backend traktuje jak session id (lookup w sessions table). To jest „session token" pattern — stateful jak cookie session, ale przesyłany jak Bearer.

**Decyzja zazwyczaj — JWT.** Dla pricing engine z rosnącym mobile + web traffic — JWT scales lepiej. Migracja: 1) dodaj `/auth/token` endpoint co generuje JWT, 2) każdy chroniony endpoint accepts oba — cookie i Bearer, 3) deprecate cookie path po migracji wszystkich klientów.

**Considerations:**
- **Refresh token rotation** — przy każdym refresh, server invaliduje stary i wydaje nowy. Detekcja kradzieży: jeśli ktoś użyje już rotated refresh token → session compromised, wyloguj.
- **Secure storage on device** — Keychain (iOS), Keystore + EncryptedSharedPreferences (Android). Nie SharedPreferences plain.
- **Token binding to device** — claim `device_id` w JWT, server może revoke per device.

**Interview trap:** „Po prostu wystaw cookie API dla mobile" — działa, ale to retrofit. Drugi: long-lived JWT bez refresh — wycieknie i nie da się odwołać. Krótki access token + refresh token to compromise.
**Tags:** auth, mobile, jwt, session, decision

## Q-RST-028 [bloom: analyze]
**Question:** Production API ma zwracać 500 error na 0.5% requestów. Jakie kroki podejmujesz?
**Model answer:** Triage → diagnose → mitigate → fix → prevent. **Krok 1 — triage:** czy to incident (high impact) czy chronic? 0.5% to często chronic, ale zależy od volume. Sprawdź alerting, business impact (czy to specific endpoint krytyczny?). **Krok 2 — diagnose:**
- **Logs/APM:** filter na 500s ostatnie 24h. Pattern recognition: ten sam endpoint? Ten sam user/region? Konkretna godzina (np. związane z batch jobem)?
- **Stack traces:** grupuj po unique exception. 5 różnych przyczyn ≠ 1 root cause.
- **Distributed tracing:** w którym serwisie pęka? Backend pricing? DB connection? External tax service?
- **Metrics:** correlation z innymi metrykami (DB connection pool, GC pauses, memory, CPU). Czy 500 koreluje z jakimś metryką?
- **Recent deploys:** czy był deploy w czasie zwiększonego error rate? `git log` + deployment timeline.

**Częste przyczyny 500:**
1. **Race condition** — sporadic, hard to repro. Triage: `synchronized`, locks, transaction isolation, optimistic lock conflicts.
2. **Resource exhaustion** — DB connection pool pełny, thread pool, file descriptors. Sprawdź metryki pool.
3. **External dependency** — third party flaky. Add circuit breaker (Resilience4j, Hystrix), retry z exponential backoff.
4. **Edge case w danych** — null gdzie nie powinno, divide by zero, malformed input. Add walidacja, defensive code.
5. **Memory leak** — heap rośnie, eventual OOM. Heap dump analysis.
6. **Bug w nowej feature** — obejrzyj recent changes.

**Krok 3 — mitigate (immediate):**
- Circuit breaker / degradation (zwracaj cached lub default response zamiast 500).
- Rate limit external service calls (jeśli wina po stronie zewnętrznej).
- Rollback recent deploy jeśli skorelowane.
- Scale up (więcej instancji) jeśli resource exhaustion.

**Krok 4 — fix:** root cause. Patch, deploy.

**Krok 5 — prevent:**
- Test case który by reprodukował (regression test).
- Monitoring: alert dla >0.1% error rate na endpoint.
- Better validation/error handling.
- Postmortem: dlaczego nie wykryliśmy w testach?

**Interview trap:** Patch bez root cause → wraca. „Dodaj try-catch i loguj" maskuje problem. Real fix wymaga zrozumienia DLACZEGO się rzuca.
**Tags:** debugging, observability, incident-response

## Q-RST-029 [bloom: analyze]
**Question:** Pricing engine eksponuje endpoint `GET /price?product_id=X&country=Y`. Dostaje 50k req/sec. Jak skalujesz?
**Model answer:** **Strategie warstwami:**

**1. Cache (most impactful):**
- **In-memory L1 cache** (Caffeine) per app instance. Hot pricelist hit rate 95%+, latencyq <1ms. TTL 60s, size limit 100k entries.
- **Distributed L2 cache** (Redis) shared. Reduces DB load globally. TTL 300s.
- **CDN cache** dla GET (jeśli pricing publiczny). `Cache-Control: public, max-age=300`. CDN edge serves majority of requests, never hits backend.
- **Cache invalidation:** event-driven (Kafka topic `price_changed`) → app instances flush local cache, invalidate Redis. Kosztuje, ale mniejszy stale window niż polling.

**2. Database:**
- **Read replicas** dla read-heavy load. Pricing queries idą na replica, writes do master. Spring `@Transactional(readOnly = true)` + routing datasource.
- **Indeksy** — composite `(product_id, country, valid_from)` covering. Index Only Scan.
- **Connection pooling** (HikariCP) — sized properly (~ N CPUs * 2-4 per app instance, capped by DB).
- **Query optymalizacja** — `EXPLAIN ANALYZE`, materialized views dla complex aggregations.

**3. Application layer:**
- **Horizontal scaling** — autoscaling app instances based on CPU/RPS. K8s HPA, AWS ASG.
- **Async processing** — wszystko co nie musi być w request path → kolejka.
- **Connection limits** — limit concurrent DB connections per instance, żeby nie zalać DB.

**4. Architecture:**
- **CQRS dla pricingu** — Read model osobny od Write. Read jest denormalized, super szybki. Write goes through aggregator → projects to read model (eventual consistency, ms lag).
- **Microservices split** — pricing read service (lekki, scaled wide) osobny od admin/management (rzadki, mniej instancji).
- **Edge computing** — CloudFlare Workers, Vercel Edge, AWS Lambda@Edge — execute pricing lookup at edge, hit DB tylko on miss.

**5. Frontend optimizations:**
- Bulk endpoint: `GET /prices?product_ids=1,2,3,4,...` — jeden request zamiast 100. Zmniejsza overhead.
- Client-side cache (localStorage z TTL).

**6. Observability:**
- p50, p95, p99 latency metrics.
- Cache hit rate dashboard.
- Tracing dla diagnozy outliers.

**Decyzja prioritetowa:** start z cache (L1+L2), to przyspieszy 90%+ requestów. Potem read replica dla DB. Dopiero potem horizontal app scaling. Architecture (CQRS, microservices) — gdy proste rzeczy nie wystarczą.

**50k req/sec realistic targets:**
- Single app instance (Java, dobra app) ~5-10k RPS dla in-memory cache hits.
- 10 instances * 5k/s = 50k RPS doable z cache.
- Bez cache, hitting DB każdy request — DB = bottleneck, nie zlewamy.

**Interview trap:** „Dorzuć więcej replik DB" — bez cache to musisz mieć 50k+ DB queries/sec, replika pomaga ale samej replice też daleko do 50k. Cache jest the win.
**Tags:** scaling, caching, performance, pricing, architecture

## Q-RST-030 [bloom: analyze]
**Question:** Klient skarży się że API zwraca różne wyniki dla tego samego pytania w krótkim czasie. Co badasz?
**Model answer:** Możliwe przyczyny:

**1. Cache inconsistency:**
- **Multiple cache layers** (CDN, app L2, app L1) z różnymi TTL — różne odpowiedzi z różnych warstw.
- **Cache invalidation race** — invalidate dotarło do jednych instancji, nie wszystkich. Eventual consistency.
- **Stale-while-revalidate** — service returns stale dla niektórych, fresh dla innych w fazie revalidation.

**2. Load balancer + replicas:**
- **Read replicas z lagiem** — write idzie do master, replica still catching up. Klient hit master → fresh; hit replica → stale.
- **Sticky sessions broken** — klient ma stateful session, ale LB nie route consistently.
- **Different app instances z różną konfiguracją** — jedna instance ma feature flag X enabled, druga nie. Bug w deployment.

**3. Time-based behavior:**
- **Pricing rule z `valid_from`/`valid_to`** — moment naznaczania może wpadać w przejściu między rules.
- **Promotion stacking** — promotion zaczyna/kończy się o konkretnej godzinie, requesty w okolicy są flaky.

**4. Concurrent updates:**
- **Read your writes** — klient zapisuje, od razu czyta — czyta z replica która jeszcze nie ma tych danych.
- **Transaction isolation** — read committed widzi commited zmiany innych transakcji w trakcie własnej.

**5. Non-determinism w logice:**
- **Random / hash-based** — A/B test, feature flag oparty na hash(user_id) albo random z różnymi seed.
- **Floating-point** w pricingu — niedeterministyczne sumy. Pricing musi być na BigDecimal.
- **Order-sensitive** — agregacja zależna od kolejności (np. `Map` iteracja w niektórych implementacjach).

**6. Bug w cache key:**
- **Brak Vary header** — różne Accept-Language daje różne wyniki, cache miksuje.
- **User-specific data cached publicly** — user A widzi swoją cenę, user B widzi cenę usera A.

**Diagnoza:**
- Reproduce pomocą scriptu: 100 calls w pętli, sprawdź variance.
- Log każdy request z full context: instance ID, cache hit/miss, query timing, replica vs master.
- Distributed tracing — które komponenty obsługują który request.
- Compare DB row direct vs API response.

**Interview trap:** „Cache zawsze winny" — często prawda, ale czasem to logika biznesowa (rules transitions). Nie skacz do conclusion. Drugi: ignorować lag replikacji DB jako „milisekundy" — w pricingu które idzie do user-facing app, ms może mieć znaczenie (read your write).
**Tags:** consistency, debugging, caching, replication

## Q-RST-031 [bloom: analyze]
**Question:** Soft delete vs HTTP DELETE — jak to pogodzić w REST API?
**Model answer:** Konflikt: HTTP DELETE semantycznie sugeruje hard delete, ale aplikacja często chce soft delete (audit). **Rozwiązania:**

**1. DELETE = soft delete by default, hard delete jako osobna akcja:**
```
DELETE /products/123              → soft delete (sets deleted_at)
DELETE /products/123?force=true   → hard delete (admin only)
```
Plus: standardowe URL. Minus: query param na destructive action może być pominięty (security risk?).

**2. DELETE = soft, archive endpoint dla hard:**
```
DELETE /products/123              → soft delete
POST   /admin/products/123/purge  → hard delete (admin endpoint)
```
Plus: explicit, audit trail. Minus: dwa endpoints.

**3. DELETE = hard, custom endpoint dla soft:**
```
PATCH  /products/123 { "deleted": true }  → soft delete (semantics through field)
DELETE /products/123                       → hard delete
```
Plus: REST orthodox. Minus: PATCH dla state change to dziwny pattern.

**4. Status field jako resource:**
```
PUT /products/123/status { "status": "archived" }
DELETE /products/123  → hard
```
Plus: full lifecycle through status. Minus: więcej endpointów.

**Decyzja:** w pricing engine zazwyczaj #1 lub #2. Soft delete jest default (audit), hard delete jest rzadki (compliance retention end). 
- DELETE returns 204 No Content (zarówno dla soft jak hard z perspektywy klienta — zniknął).
- GET on deleted: 404 (lub 410 Gone z opcjonalnym body) — klient nie widzi soft-deleted records (chyba że admin endpoint).
- Restore: `POST /products/123/restore` (custom action).

**Audit trail:**
- `deleted_at TIMESTAMP NULL` na tabeli.
- `deleted_by` — kto skasował.
- Event w audit log (kto, kiedy, dlaczego — z `Reason` headerem lub body).

**Idempotency:** DELETE jest idempotent. Drugi DELETE na soft-deleted → 404 (lub 204 dla strict idempotency, depending on philosophy).

**Interview trap:** „Zwracaj 200 z ciałem zamiast 204" — niektóre API tak robią dla wygody klienta. Ale 204 bardziej REST-orthodox. Drugi: cascading deletes — soft delete parent, co z children? Polityka: soft cascade też (żeby nie pojawiły się sieroty), albo block delete jeśli children istnieją (FK + hint). Pricing: soft cascade — produkty soft-deleted nie pokazują się w pricelistach, ale historia zamówień zachowana.
**Tags:** rest, soft-delete, api-design, audit

## Q-RST-032 [bloom: analyze]
**Question:** REST API ma być wystawione publicznie. Wymień security checklist top-10 rzeczy do sprawdzenia.
**Model answer:**

1. **HTTPS only** — TLS 1.2 minimum, lepiej 1.3. HSTS header (`Strict-Transport-Security: max-age=31536000`). Redirect HTTP → HTTPS. Cert auto-renewal (Let's Encrypt + cert-manager).

2. **Authentication & authorization** — każdy endpoint ma jasną politykę. Brak „accidentally public" endpoints. OAuth2/JWT properly validated (signature, exp, iss, aud claims). Scopes per token, principle of least privilege.

3. **Input validation:** schema validation (OpenAPI runtime check, Bean Validation). Reject early. Whitelist (allowed values), nie blacklist. Sanityzacja inputu (HTML escape, SQL parameterization always).

4. **Output encoding** — unikać data leakage. PII / secrets nie w response (np. password hash, internal IDs). Sensitive data masked (`****1234`).

5. **Rate limiting** — per IP, per API key, per user. 429 + Retry-After. Cap dla kosztownych operacji (search, exports).

6. **CORS configured tightly** — `Access-Control-Allow-Origin` to konkretna lista origins (nie `*` z credentials), `Access-Control-Allow-Methods` ograniczona.

7. **Security headers:**
   - `Strict-Transport-Security`
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `Content-Security-Policy` (jeśli serwujesz HTML)
   - `Referrer-Policy: no-referrer`
   - `Permissions-Policy`

8. **Error handling** — nie wyciekaj stack traces, internal paths, DB error messages. Generic 500: „Internal error, ref: <correlation_id>". Detail w logach, nie w response.

9. **Logging & monitoring** — wszystko auditable: kto, co, kiedy. PII redacted in logs. Alert na anomalies (sudden 500 spike, unusual access patterns, brute force attempts).

10. **Dependency management** — known vulnerabilities (Snyk, Dependabot, OWASP Dep Check). Patch ASAP. Container images scanned. Base images minimal (distroless).

**Bonus:**
- **CSRF** — dla cookie-based auth, SameSite=Strict, anti-CSRF token.
- **API key rotation** — process for rotating, revocation.
- **Secrets management** — Vault, AWS Secrets Manager. NIE w env vars committed do repo.
- **OWASP Top 10** as baseline check (Broken Access Control, Crypto failures, Injection, Insecure Design, Security Misconfig, Vulnerable Components, Auth failures, Software/Data integrity failures, Logging/Monitoring, SSRF).
- **Penetration testing** — periodic external test.

**Interview trap:** Najczęstsze incydenty to **Broken Access Control** — ktoś wystawi `/admin/*` bez auth, ktoś nie sprawdza czy user X może czytać resource Y (IDOR). Authorization to jest temat per-request, nie per-endpoint. „Mam OAuth więc OK" — false. OAuth daje tylko authentication. Authorization to jeszcze trzeba zaimplementować.
**Tags:** security, owasp, checklist, production
