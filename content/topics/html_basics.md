# HTML basics — question bank

> Context: the position isn't frontend-heavy, but the recruiter checks basics of HTML5 semantics, forms, accessibility, and REST interaction. Questions designed for quick warmup in a mock, not deep expertise.

## Scope

- HTML5 semantyka: header/nav/main/section/article/aside/footer
- block vs inline elementy
- formularze: input types, attributes (required, pattern, min, max, autocomplete), GET vs POST
- a11y (accessibility): ARIA roles, alt text, label-input association
- DOM podstawy: querySelector, addEventListener, event delegation
- HTML vs XHTML, DOCTYPE
- meta tags: viewport, charset, og: tags
- AJAX podstawy: fetch API, async/await
- `<script defer>` vs `async` vs `module`
- HTML i pricing context: rendering pricing list, formularz oferty

---

## Q-HTM-001 [bloom: recall]
**Question:** Co to jest semantic HTML i wymień 5 nowych semantic tagów z HTML5.
**Model answer:** Semantic HTML to używanie tagów których nazwa odpowiada znaczeniu zawartości — pomaga przeglądarkom, screen readerom, SEO i developerom. **HTML5 tagi:**
- `<header>` — nagłówek strony lub sekcji.
- `<nav>` — nawigacja (linki menu).
- `<main>` — główna zawartość (jedna na stronę).
- `<section>` — tematyczna sekcja content.
- `<article>` — niezależna jednostka content (post, komentarz, produkt).
- `<aside>` — pomocniczy content (sidebar, quote).
- `<footer>` — stopka strony lub sekcji.
- `<figure>` + `<figcaption>` — obraz z podpisem.
- `<time datetime="...">` — datowanie machine-readable.
- `<mark>` — highlight.
**Stary sposób:** `<div class="header">`, `<div class="nav">` — działało, ale brak semantyki. Screen reader nie wiedział że to nawigacja.
**Interview trap:** „W HTML5 nie używamy div" — false. Div wciąż używamy do styling/layout where no semantic meaning. Spans dla inline. Semantic tags mają znaczenie, divs są neutralne.
**Tags:** html5, semantics

## Q-HTM-002 [bloom: recall]
**Question:** Czym różni się element block od inline?
**Model answer:** **Block** elementy zaczynają nowy wiersz, zajmują pełną dostępną szerokość, można im ustawić width/height (`<div>`, `<p>`, `<h1>-<h6>`, `<section>`, `<article>`, `<form>`, `<table>`). **Inline** są w linii z otaczającym tekstem, zajmują tylko tyle ile ich content, width/height domyślnie ignorowane (`<span>`, `<a>`, `<strong>`, `<em>`, `<img>`, `<input>`). **Inline-block** — w linii ale akceptuje width/height (`<button>` defaultowo, niektóre form elements). CSS `display` może to zmienić: `display: block` / `inline` / `inline-block` / `flex` / `grid` / `none`.
**Interview trap:** „img jest block bo ma width/height" — nie. `<img>` jest inline-block: w linii z tekstem (jak emoji), ale ma wymiary. „Float" zmienia behavior — float-owany inline staje się quasi-block.
**Tags:** html, css, layout

## Q-HTM-003 [bloom: recall]
**Question:** Wymień 5 typów `<input>` w HTML5.
**Model answer:** HTML5 dorzuciło wiele typów ponad starsze `text`, `password`, `submit`. **Nowe:** `email` (walidacja format @), `tel` (wybór keyboard mobile), `url`, `number` (numeric keypad mobile, `min/max/step`), `date` (date picker), `time`, `datetime-local`, `month`, `week`, `range` (slider), `color` (color picker), `search`, `file`. **Poza tym (pre-HTML5):** `text`, `password`, `checkbox`, `radio`, `submit`, `button`, `hidden`, `image`, `reset`. Atrybuty: `required`, `pattern` (regex), `min`/`max`, `step`, `placeholder`, `autocomplete`, `autofocus`, `readonly`, `disabled`.
**Interview trap:** „type=email waliduje email" — tylko basic format check (zawiera @). Server-side validation zawsze konieczna. „type=number" — w niektórych przeglądarkach pozwala na wpisanie liter (mobile), wartość parsowana jako string.
**Tags:** forms, input, html5

## Q-HTM-004 [bloom: recall]
**Question:** Co robi atrybut `<form action="..." method="...">`?
**Model answer:** `action` — URL gdzie formularz zostanie wysłany. `method` — metoda HTTP: `GET` lub `POST`. **GET:** parametry w URL query string (`?name=foo&email=bar`). Limity długości, widoczne w historii, idempotentne, cachowalne, OK dla search forms. **POST:** parametry w body request, bez limitu długości, niewidoczne w URL, OK dla mutation. Inne `method` (`PUT`, `DELETE`) standardowo nie są wspierane przez form HTML — wymagają JS (fetch/XHR). **Other attributes:** `enctype` — `application/x-www-form-urlencoded` (default), `multipart/form-data` (file upload), `text/plain` (debug only). `target` — gdzie się otworzy response (`_self`, `_blank`, named iframe). `novalidate` — wyłącz built-in HTML5 validation.
**Interview trap:** „REST API przez form" — można, ale ograniczone do GET/POST + standard form encoding. Modern apps i tak używają fetch z JSON body.
**Tags:** forms, http

## Q-HTM-005 [bloom: recall]
**Question:** Co to jest `alt` attribute na `<img>` i czemu jest ważny?
**Model answer:** `alt` to tekst alternatywny dla obrazu — pokazany gdy obraz się nie załaduje LUB czytany przez screen reader. Powinien opisywać znaczenie obrazu w kontekście. **Przykłady:**
- Decorative img (nic nie znaczy semantycznie): `alt=""` (pusty — screen reader pomija). Nigdy `alt` brak — wtedy reader czyta plik filename.
- Logo firmy: `alt="Acme Corp logo"`.
- Wykres: `alt="Sprzedaż wzrosła 30% w Q3 2025"` — opisuje INFORMACJĘ z wykresu.
- Foto produktu: `alt="Czerwona koszulka XL z bawełny organicznej"`.
**Co NIE robić:** `alt="image"`, `alt="photo.jpg"`, `alt="click here"` — bezużyteczne. `alt` z keyword stuffing dla SEO — Google wyłapie i zignoruje.
**Czemu ważny:**
- **A11y** — screen reader users dostają ekwiwalent obrazu.
- **Slow connections** — alt text shown gdy image loading lub failed.
- **SEO** — Google używa alt do image search.
- **Walidacja** — WCAG wymaga alt na images.
**Interview trap:** „decorative images nie potrzebują alt" — potrzebują `alt=""` (explicitly empty), nie pominięcia. Bez alt-u screen reader czyta nazwy plików.
**Tags:** a11y, accessibility, images

## Q-HTM-006 [bloom: recall]
**Question:** Wytłumacz `<label for="...">` i `<input id="...">`.
**Model answer:** `<label for="email">Email</label> <input type="email" id="email">` — wiąże label z input przez ID. **Co to daje:**
1. **Click on label** focusuje input — większa interaktywność (np. checkboxy dużo łatwiej kliknąć).
2. **Screen reader** czyta label gdy input focused — user wie co wpisać.
3. **Form validation messages** mogą używać label do referencji.
**Alternatywa: nesting** — `<label>Email <input type="email"></label>` — bez `for` i `id`. Działa jak wyżej, ale `for/id` daje większą elastyczność (label może być daleko od input w DOM).
**ARIA fallback:** `<input aria-label="Email">` lub `aria-labelledby="elem-id"` — gdy label nie da się użyć (np. icon-only button).
**Interview trap:** Popularny błąd — placeholder zamiast labelu (`<input placeholder="Email">`). Placeholder znika gdy user zaczyna pisać → user zapomina co wpisać. Labels muszą być persistent. Drugi: ID musi być unique w dokumencie.
**Tags:** a11y, forms, labels

## Q-HTM-007 [bloom: recall]
**Question:** Czym jest DOCTYPE i czemu się go używa?
**Model answer:** `<!DOCTYPE html>` na początku HTML deklaruje że dokument używa HTML5. Działa jak wskazówka dla przeglądarki: użyj **standards mode** parsing/rendering. Bez DOCTYPE przeglądarki domyślnie idą w **quirks mode** — emulacja zachowań starych IE (różne layout bugs, broken CSS). **Historycznie:** HTML4 i XHTML miały dłuższe DOCTYPE z DTD (`<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://...">`). HTML5 simplifies do `<!DOCTYPE html>` (case-insensitive). **Czemu wciąż się używa:** standards mode jest essential dla consistent rendering w modern przeglądarkach. Quirks mode = bugs.
**Interview trap:** „DOCTYPE jest częścią HTML" — false, jest meta-instrukcją parser (preprocessor directive). Druga: nie pominąć w PWA / SPA — nawet gdy generujesz HTML w JS, base template potrzebuje DOCTYPE.
**Tags:** html, doctype, browsers

## Q-HTM-008 [bloom: recall]
**Question:** Co to jest `<meta name="viewport">`?
**Model answer:** Meta tag w `<head>` instruujący mobile browsers jak skalować i renderować stronę. Standardowy: `<meta name="viewport" content="width=device-width, initial-scale=1">`. **Co znaczy:**
- `width=device-width` — szerokość viewport = szerokość ekranu device (np. 375px na iPhone).
- `initial-scale=1` — początkowy zoom 1:1.
- Inne (rzadziej): `maximum-scale=1` (zakaz zoom — zazwyczaj antypattern bo a11y), `user-scalable=no` (też zła praktyka).
**Bez viewport meta** mobile browser renderuje stronę w 980px wide i scaluje down — strona wygląda jak desktop zminiaturyzowana, nieczytelna. Z viewport — strona dostosowuje layout do szerokości urządzenia (responsive design). **Modern web** zawsze ma viewport tag.
**Interview trap:** `user-scalable=no` blokuje zoom — to a11y violation. Users z słabym wzrokiem nie mogą powiększyć. WCAG to flaguje. Druga: viewport tylko dla mobile — desktop ignoruje (default rendering).
**Tags:** mobile, responsive, meta

---

## Q-HTM-009 [bloom: understand]
**Question:** Wytłumacz różnicę między `<script>`, `<script async>`, i `<script defer>`.
**Model answer:** Wszystkie ładują JS. Różnica w **kiedy** i **w jakiej kolejności**:
- **`<script>`** (no attribute) — synchroniczne. Browser zatrzymuje HTML parsing, fetch+execute scriptu, potem kontynuuje. Blokuje rendering. Dlatego klasycznie umieszcza się scripty na końcu `<body>`.
- **`<script async>`** — fetch równolegle do parsing, execute natychmiast po pobraniu (przerywa parsing). Kolejność scriptów asynchronicznych: w jakiej zostały pobrane (NIE w kolejności w HTML). Dla niezależnych scriptów (analytics, ads).
- **`<script defer>`** — fetch równolegle do parsing, execute DOPIERO PO parsing (w kolejności w HTML). Idealny dla scriptów które operują na DOM lub zależą od kolejności.
- **`<script type="module">`** — domyślnie defer-like. Kolejność jak w HTML. Top-level await wspierany. Strict mode by default.

**Decision tree:**
- Niezależny analytics → `async`.
- DOM-related main app script → `defer`.
- ES modules → `type="module"` (defer-like).
- Legacy / blocking critical → no attribute (rare).

**Interview trap:** „defer = async + na końcu body" — nie do końca. Defer execution jest po DOMContentLoaded ALE PRZED `load`. Multiple defer-y zachowują kolejność, async nie. Druga: inline scripts nie wspierają async/defer (są zawsze sync).
**Tags:** scripts, performance, loading

## Q-HTM-010 [bloom: understand]
**Question:** Co to jest event bubbling i event delegation?
**Model answer:** **Event bubbling** — gdy event (np. click) odpali się na elemencie, „bąbluje" w górę DOM tree, odpalając handlery na wszystkich rodzicach. Default behavior. Kolejność: target → parent → grandparent → ... → document. (Capture phase działa odwrotnie — od document w dół do target.) **Event delegation** — pattern: zamiast wieszania handlerów na 1000 elementach, wieszasz JEDEN handler na common parent, sprawdzasz `event.target` żeby zidentyfikować konkretny element. **Przykład:**
```javascript
// Bez delegation: każda komórka ma handler — wolno, dużo memory
document.querySelectorAll('.item').forEach(el => {
  el.addEventListener('click', handleItemClick);
});

// Z delegation: jeden handler na liście
document.getElementById('list').addEventListener('click', e => {
  const item = e.target.closest('.item');
  if (item) handleItemClick({target: item});
});
```
**Plusy delegation:**
- Performance (1 handler vs N).
- Działa dla elementów dynamicznie dodawanych (no need do re-attach po insertions).
- Mniej memory.
**Minusy:** wszystkie events na parent muszą być filtrowane (overhead per non-matching event).
**Stop bubbling:** `event.stopPropagation()` w handlerze — event nie idzie wyżej. `event.preventDefault()` — różne, anuluje default browser action (np. submit form). 
**Interview trap:** „stopPropagation zatrzymuje też capture phase" — false, tylko bubbling. `stopImmediatePropagation` zatrzymuje też inne handlery na tym samym elemencie. Druga: niektóre events nie bąblują (`focus`, `blur`, `mouseenter`, `mouseleave`) — do tego są bubbling alternatives (`focusin`, `focusout`).
**Tags:** events, dom, javascript

## Q-HTM-011 [bloom: understand]
**Question:** Co to są ARIA attributes i kiedy ich używać?
**Model answer:** ARIA (Accessible Rich Internet Applications) — atrybuty dodające semantykę dla a11y, gdy native HTML nie wystarczy. **Główne kategorie:**
- **Roles:** `role="button"`, `role="dialog"`, `role="alert"`, `role="navigation"`. Mówią screen reader CO to jest.
- **States:** `aria-checked`, `aria-expanded`, `aria-selected`, `aria-disabled`. Mówią CO obecny stan.
- **Properties:** `aria-label` (text label gdy visual label brakuje), `aria-labelledby` (reference), `aria-describedby`, `aria-hidden="true"` (hide from screen readers).
- **Live regions:** `aria-live="polite"` / `assertive` — dynamic content updates ogłaszane przez screen reader.
**Pierwsza zasada ARIA:** „No ARIA is better than bad ARIA". Native HTML elements mają built-in semantics. `<button>` jest auto `role="button"` z keyboard support, focus, etc. `<div role="button">` wymaga ręcznego dodania keyboard handlers (`Enter`, `Space`), focus management — łatwo zepsuć.
**Kiedy używać:**
- Custom widgets (modal, accordion, tabs) bez native equivalent.
- Dynamic states (loading, expanded, error).
- Visual-only relationships (form field z error message wskazane przez `aria-describedby`).
**Kiedy NIE:**
- Gdy native HTML wystarczy (`<button>`, `<a>`, `<input>` — używaj zamiast `<div>` z aria).
- Redundancja: `<button role="button">` — niepotrzebne.
**Interview trap:** Custom dropdown z `<div>` zamiast `<select>` zazwyczaj kończy się a11y disaster. Native `<select>` wygląda nudnie ale jest accessible by default. Nowe (2024+): `<dialog>` HTML5 element + `popover` attribute redukują potrzebę ARIA dla overlays.
**Tags:** a11y, aria, accessibility

## Q-HTM-012 [bloom: understand]
**Question:** Wytłumacz Cross-Origin Resource Sharing (CORS) z perspektywy frontend.
**Model answer:** CORS chroni klientów (przeglądarki) przed wysyłaniem requestów do innych origins (= protokół + domena + port) bez zgody serwera. Przeglądarka: gdy JS robi `fetch('https://api.other.com')` z `https://app.com`, sprawdza czy serwer akceptuje cross-origin requests. **Flow:**

**Simple request** (GET/HEAD/POST z safe headers): browser wysyła request z `Origin: https://app.com`. Serwer odpowiada `Access-Control-Allow-Origin: https://app.com` (lub `*` dla public). Browser sprawdza match → jeśli OK, JS dostaje response. Jeśli nie → fetch promise rejects, console error.

**Preflight** (PUT/DELETE/PATCH lub custom headers, lub Content-Type: application/json): browser pierw wysyła `OPTIONS` z `Access-Control-Request-Method` i `Access-Control-Request-Headers`. Serwer odpowiada listą dozwolonych. Browser cache-uje preflight (`Access-Control-Max-Age`). Dopiero potem wysyła actual request.

**Z punktu widzenia frontend:**
```javascript
fetch('https://api.other.com/data', {
  method: 'POST',
  credentials: 'include', // wysyła cookies — wymaga konkretnego ACAO (nie *) i ACAC: true po stronie serwera
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({...})
})
```
Jeśli backend nie wystawi proper CORS headers — fetch fails, error w konsoli. Frontend nic nie zrobi — to jest backend problem (lub proxy/gateway).

**Workarounds:**
- **Same origin:** najlepsze. Wystawić API pod tą samą domeną (proxy z frontend hostem).
- **Backend-for-frontend (BFF):** dedykowany backend per frontend, robi proxy do internal APIs.
- **JSONP** (legacy, deprecated): GET-only, security holes.
- Mówienie partnerom „dodajcie CORS header".

**Interview trap:** CORS NIE chroni servera przed niczym (server-to-server requests ignoruje CORS — to jest browser-only mechanism). Druga: `*` z credentials nie działa — security spec.
**Tags:** cors, security, frontend

## Q-HTM-013 [bloom: understand]
**Question:** Co to jest `fetch API` i jak go używać z REST endpointem?
**Model answer:** Fetch API to modern browser API do HTTP requests, zastąpienie XMLHttpRequest. Promise-based. **Basic GET:**
```javascript
const response = await fetch('/api/products/123');
if (!response.ok) {
  throw new Error(`HTTP ${response.status}`);
}
const data = await response.json();
```

**POST z JSON:**
```javascript
const response = await fetch('/api/products', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({name: 'Foo', price: 100})
});
```

**Error handling subtle:** fetch RESOLVES (nie rejects) dla non-2xx status. Tylko network error rejects promise. Stąd wzorzec:
```javascript
try {
  const response = await fetch(url);
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(response.status, error.message || response.statusText);
  }
  return await response.json();
} catch (e) {
  // network error LUB nasz throw
}
```

**Cancel:** `AbortController`:
```javascript
const controller = new AbortController();
fetch(url, {signal: controller.signal});
// later
controller.abort();
```

**Streaming:** `response.body` to ReadableStream. Można process chunk-by-chunk (np. NDJSON parsing).

**Inne opcje:**
- `cache: 'no-cache' | 'reload' | 'force-cache' | ...` — cache strategy.
- `credentials: 'include' | 'same-origin' | 'omit'` — czy wysyłać cookies.
- `mode: 'cors' | 'no-cors' | 'same-origin'` — explicit CORS mode.
- `keepalive: true` — request kontynuuje po zamknięciu page (analytics).

**Alternatywy:** Axios (popularna libka — auto JSON, interceptors), Angular HttpClient (RxJS-based), browser fetch wystarczy w 90% przypadków.

**Interview trap:** „fetch rejects on 404" — false. Tylko sieć. 404 to legitimate response. „body w GET" — fetch nie pozwala body w GET (zgodnie ze specyfikacją HTTP).
**Tags:** fetch, api, javascript

## Q-HTM-014 [bloom: understand]
**Question:** Co to są Web Components / Custom Elements?
**Model answer:** Web Components to standard browser do tworzenia własnych HTML elements. **3 podstawowe API:**
1. **Custom Elements:** `class MyButton extends HTMLElement { ... } customElements.define('my-button', MyButton);`. Używasz jako `<my-button>` w HTML.
2. **Shadow DOM:** ukryty DOM tree dla komponentu, izolowany od reszty strony (CSS/JS encapsulation). `this.attachShadow({mode: 'open'})`.
3. **HTML Templates:** `<template id="my-tpl">...</template>` — niezrenderowany HTML do reuse.

**Przykład:**
```javascript
class PriceTag extends HTMLElement {
  static observedAttributes = ['amount', 'currency'];
  
  attributeChangedCallback(name, oldVal, newVal) {
    this.render();
  }
  
  render() {
    const amount = this.getAttribute('amount') || '0';
    const currency = this.getAttribute('currency') || 'PLN';
    this.shadowRoot.innerHTML = `<style>...</style><span class="price">${amount} ${currency}</span>`;
  }
  
  connectedCallback() {
    this.attachShadow({mode: 'open'});
    this.render();
  }
}
customElements.define('price-tag', PriceTag);
```
Użycie: `<price-tag amount="99.99" currency="EUR"></price-tag>`.

**Plusy:** framework-agnostic (działa wszędzie), encapsulation, browser native. **Minusy:** verbose vs framework components, ekosystem mniejszy niż React/Angular.

**Wartość w pricing platformie:** możesz wystawić `<price-tag>` do osadzenia przez partnerów na ich stronach — bez wymuszania ich frameworka.

**Interview trap:** „Web Components = React" — false. Web Components są lower-level (raw API). React/Angular dodają state management, lifecycle, JSX/template syntax. Można owrap React w Web Component (stencil, lit) ale to inny use case.
**Tags:** web-components, custom-elements, modern-html

## Q-HTM-015 [bloom: understand]
**Question:** Co to jest `localStorage` vs `sessionStorage` vs `cookie`?
**Model answer:** Storage mechanisms w browserze:

**localStorage:**
- Persistent (survive close/reopen).
- Per origin (origin = protokół+host+port).
- Synchroniczne API (`localStorage.setItem('key', 'value')`, `getItem('key')`, `removeItem`, `clear`).
- ~5-10 MB limit (zależy od browser).
- Tylko strings (JSON.stringify dla objects).
- Dostępny do JS — XSS może wykraść.

**sessionStorage:**
- Tylko dla bieżącej zakładki — close = clear.
- Inne tabsy nie widzą.
- Inny origin → inny storage.
- Reszta jak localStorage.

**Cookies:**
- Wysyłane w każdym HTTP request do origin (overhead).
- Limit ~4 KB per cookie, ~50 cookies per domain.
- Z konfiguracją: `Expires`, `Max-Age` (persistence), `Secure` (only HTTPS), `HttpOnly` (no JS access — chroni przed XSS), `SameSite=Strict|Lax|None` (CSRF protection).
- Document.cookie API (string, niepraktyczny) lub server `Set-Cookie` header.

**Decyzja:**
- **Auth tokens:** httpOnly cookie (most secure) lub memory + refresh token w cookie. NIE localStorage.
- **User preferences (theme, language):** localStorage.
- **Form state w trakcie multi-step (np. wizard):** sessionStorage.
- **Authentication session:** cookie.
- **Caching API responses:** localStorage (dla małych) lub IndexedDB (dla większych structured data).

**IndexedDB:** powyżej powyższych — async, structured data, transactions, large quantities. Skomplikowany API; biblioteki Dexie, idb-keyval upraszczają.

**Interview trap:** „localStorage to bezpieczne miejsce na tokeny" — XSS-podatne. Real attack: malicious npm package, third-party widget, content injection. Token w localStorage = token wykradziony. HttpOnly cookie immune.
**Tags:** storage, cookies, security

## Q-HTM-016 [bloom: understand]
**Question:** Co robi `<form>` `enctype="multipart/form-data"`?
**Model answer:** `enctype` określa format encoding form data dla submission. Wartości:
- **`application/x-www-form-urlencoded`** (default) — pary `key=value` separowane `&`, URL-encoded. Idealne dla simple text fields.
- **`multipart/form-data`** — każde pole jako separate part w body, z headers. **Wymagane dla file uploadu** (binary files, no encoding loss).
- **`text/plain`** — debug only, niesensible dla prod.

**Multipart format:**
```
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=----xxx

------xxx
Content-Disposition: form-data; name="title"

Mój produkt
------xxx
Content-Disposition: form-data; name="image"; filename="photo.jpg"
Content-Type: image/jpeg

<binary data>
------xxx--
```

**Boundary** — losowy string oddzielający parts. Każdy part może mieć własny `Content-Type`, `Content-Disposition` (z `name` field, opcjonalnie `filename`).

**Server side parse:**
- Spring Boot: `@RequestParam("file") MultipartFile file`.
- Java raw: `Part` z `HttpServletRequest.getPart()`.
- Express.js: `multer` middleware.

**Interview trap:** Bez `multipart` upload pliku przez form nie zadziała — file będzie tylko nazwą jako string. Druga: client-side `<input type="file">` można JS-em wysłać przez `fetch` z `FormData`:
```javascript
const formData = new FormData(form); // wszystkie pola form
formData.append('file', fileInput.files[0]);
fetch('/upload', {method: 'POST', body: formData}); // browser ustawia Content-Type sam
```
Nie ustawiaj `Content-Type` ręcznie przy FormData — browser sam dorzuci boundary.
**Tags:** forms, file-upload, http

---

## Q-HTM-017 [bloom: apply]
**Question:** Napisz HTML formularza tworzenia produktu z polami: name (required, min 3 znaki), price (required, > 0), category (select z 3 opcjami), description (textarea, opcjonalna).
**Model answer:**
```html
<form action="/api/products" method="POST" id="product-form">
  <div>
    <label for="name">Nazwa produktu *</label>
    <input 
      type="text" 
      id="name" 
      name="name" 
      required 
      minlength="3" 
      maxlength="100"
      autocomplete="off"
    >
  </div>

  <div>
    <label for="price">Cena netto (PLN) *</label>
    <input 
      type="number" 
      id="price" 
      name="price" 
      required 
      min="0.01" 
      step="0.01"
      inputmode="decimal"
    >
  </div>

  <div>
    <label for="category">Kategoria *</label>
    <select id="category" name="category" required>
      <option value="">-- wybierz --</option>
      <option value="electronics">Elektronika</option>
      <option value="books">Książki</option>
      <option value="clothing">Odzież</option>
    </select>
  </div>

  <div>
    <label for="description">Opis (opcjonalny)</label>
    <textarea 
      id="description" 
      name="description" 
      rows="4" 
      maxlength="500"
    ></textarea>
  </div>

  <button type="submit">Zapisz produkt</button>
</form>
```
**Co jest tu poprawnie:**
- `<label for>` + `<input id>` — a11y binding.
- `required`, `minlength`, `min`, `step` — built-in HTML5 validation.
- `inputmode="decimal"` — mobile keyboard z separatorem.
- `name` attribute — submission key dla server (`name=...&price=...`).
- Pierwsza opcja `<option value="">` — zmusza wybór (HTML5 sprawdza `required` na select).
- `type="submit"` na button — domyślne, ale explicit dla jasności.

**Server-side (Spring Boot):** `@PostMapping("/api/products") @ResponseBody Product create(@Valid @ModelAttribute ProductDto dto) { ... }`. Jeśli `enctype="application/json"` — to JSON via JS fetch, nie native form.

**Bez JS, native HTML5 walidacja:** browser zatrzyma submit jeśli `required` puste lub `minlength` nie spełnione, pokaże popup z błędem.

**Interview trap:** `type="number"` daje rozróżnienie 0,01 vs 0.01 zależnie od locale — niektóre browsery pozwalają comma, inne nie. Server-side musi obsłużyć oba lub zwrócić jasny error. `step="0.01"` ogranicza precision (dla pricingu często wystarczy).
**Tags:** forms, validation, html5, pricing

## Q-HTM-018 [bloom: apply]
**Question:** Pokaż jak narzucić w HTML responsive table dla cennika (mobile-friendly).
**Model answer:**
```html
<style>
  table { width: 100%; border-collapse: collapse; }
  th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }

  /* Mobile: stack rows as cards */
  @media (max-width: 600px) {
    table, thead, tbody, tr, th, td { display: block; }
    thead { display: none; } /* hide headers */
    tr { 
      margin-bottom: 1em; 
      border: 1px solid #ddd; 
      padding: 8px; 
    }
    td {
      border: none;
      padding-left: 50%;
      position: relative;
    }
    td::before {
      content: attr(data-label);
      position: absolute;
      left: 8px;
      width: 45%;
      font-weight: bold;
    }
  }
</style>

<table>
  <thead>
    <tr>
      <th>Produkt</th>
      <th>Kraj</th>
      <th>Cena</th>
      <th>Waluta</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td data-label="Produkt">Phone XYZ</td>
      <td data-label="Kraj">Polska</td>
      <td data-label="Cena">2 999.00</td>
      <td data-label="Waluta">PLN</td>
    </tr>
    <tr>
      <td data-label="Produkt">Tablet ABC</td>
      <td data-label="Kraj">Niemcy</td>
      <td data-label="Cena">499.99</td>
      <td data-label="Waluta">EUR</td>
    </tr>
  </tbody>
</table>
```

**Mechanika:**
- Desktop: standard table layout.
- Mobile (≤600px): table elements stają się block. Headers ukryte. Każda komórka ma `data-label` które pseudo-element `::before` renderuje przed wartością. Każdy row wygląda jak karta.

**Alternatives:**
- **CSS Grid table:** więcej kontroli, mniej hacky.
- **Horizontal scroll:** `overflow-x: auto` wrapper — table zostaje table, użytkownik scrolluje. Mniej finezji ale prościej.
- **Server-side responsive:** wykrycie mobile (User-Agent) i serve different markup. Antypattern w 2026 — preferuj client-side responsive.

**a11y:**
- `<caption>` dla nazwy tabeli (screen reader).
- `scope="col"` na `<th>` (col headers).
- `aria-label` na buttonach interaktywnych w komórkach.

**Interview trap:** „display: block na td" łamie semantic HTML — screen reader może mieć problem. Solution: `role="presentation"` na table w mobile. Lub: nie używać `<table>` dla data tabular, tylko CSS Grid (ale wtedy stracone semantyka). Trade-off między a11y a responsive.
**Tags:** responsive, table, css, pricing

## Q-HTM-019 [bloom: apply]
**Question:** JavaScript pobiera listę produktów przez REST i renderuje do `<ul>`. Pokaż implementację.
**Model answer:**
```html
<ul id="product-list">
  <li>Loading...</li>
</ul>

<script>
async function loadProducts() {
  const list = document.getElementById('product-list');
  try {
    const response = await fetch('/api/products?limit=20');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    
    if (!data.items || data.items.length === 0) {
      list.innerHTML = '<li>Brak produktów</li>';
      return;
    }
    
    // Bezpieczna dynamic insertion — no innerHTML z user data!
    list.innerHTML = '';
    for (const product of data.items) {
      const li = document.createElement('li');
      
      const name = document.createElement('strong');
      name.textContent = product.name;
      li.appendChild(name);
      
      li.appendChild(document.createTextNode(` — ${product.price} ${product.currency}`));
      
      list.appendChild(li);
    }
  } catch (e) {
    console.error('Failed to load products:', e);
    list.innerHTML = '<li class="error">Błąd ładowania. Spróbuj ponownie.</li>';
  }
}

loadProducts();
</script>
```

**Kluczowe rzeczy:**
- `async/await` — czytelniejsze niż then-callbacks.
- `response.ok` check przed `.json()` — fetch nie rejects na 4xx/5xx.
- `createElement` + `textContent` — bezpieczne, brak XSS. Nie `innerHTML` z danymi z API.
- Error state w UI — user widzi że coś poszło nie tak, nie tylko zmrożony „Loading".
- Empty state — gdy brak danych.

**Z innerHTML i template literal:**
```javascript
// NIEBEZPIECZNE jeśli `product.name` zawiera HTML/scripts
list.innerHTML = data.items.map(p => `<li><strong>${p.name}</strong> — ${p.price}</li>`).join('');

// BEZPIECZNE — escape:
function escape(s) { 
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); 
}
list.innerHTML = data.items.map(p => `<li><strong>${escape(p.name)}</strong> — ${escape(p.price)}</li>`).join('');
```

**Frameworks:** w prawdziwym SPA użyłbyś Angular/React/Vue z auto-escaping w templates. Ten przykład to vanilla dla nauki podstaw.

**Interview trap:** XSS via `innerHTML` z user/API content — KLASYK. Atakujący wstrzykuje `<img onerror=...>`. Default approach: textContent + createElement. innerHTML tylko ze sterowanym (przez ciebie) markupem.
**Tags:** dom, fetch, security, xss

## Q-HTM-020 [bloom: apply]
**Question:** Dodaj live walidację formularza (real-time feedback przed submit) JS-em.
**Model answer:**
```html
<form id="product-form">
  <label for="name">Nazwa</label>
  <input type="text" id="name" name="name" required minlength="3">
  <span class="error" id="name-error" aria-live="polite"></span>

  <label for="price">Cena</label>
  <input type="number" id="price" name="price" required min="0.01" step="0.01">
  <span class="error" id="price-error" aria-live="polite"></span>

  <button type="submit">Zapisz</button>
</form>

<script>
const form = document.getElementById('product-form');
const inputs = form.querySelectorAll('input[required]');

function validateInput(input) {
  const errorEl = document.getElementById(`${input.id}-error`);
  if (input.validity.valueMissing) {
    errorEl.textContent = 'Pole wymagane';
    return false;
  }
  if (input.validity.tooShort) {
    errorEl.textContent = `Min ${input.minLength} znaków`;
    return false;
  }
  if (input.validity.rangeUnderflow) {
    errorEl.textContent = `Wartość musi być ≥ ${input.min}`;
    return false;
  }
  if (input.validity.typeMismatch) {
    errorEl.textContent = 'Nieprawidłowy format';
    return false;
  }
  errorEl.textContent = '';
  return true;
}

// Live validation on blur (po wyjściu z pola)
inputs.forEach(input => {
  input.addEventListener('blur', () => validateInput(input));
  // Re-validate on input gdy pole już ma error
  input.addEventListener('input', () => {
    if (input.classList.contains('invalid')) validateInput(input);
  });
});

form.addEventListener('submit', e => {
  let allValid = true;
  inputs.forEach(input => {
    if (!validateInput(input)) {
      input.classList.add('invalid');
      allValid = false;
    } else {
      input.classList.remove('invalid');
    }
  });
  if (!allValid) e.preventDefault();
});
</script>

<style>
.error { color: red; font-size: 0.875em; }
input.invalid { border-color: red; }
</style>
```

**Mechanika:**
- HTML5 native validation: `input.validity` object zawiera `valueMissing`, `tooShort`, `rangeUnderflow`, `typeMismatch`, etc. — flag-bool dla każdego rodzaju błędu.
- `aria-live="polite"` na error span — screen reader anonsuje zmianę.
- Validation strategy: blur first time (less aggressive), `input` event po pierwszym error (immediate feedback).
- Submit: re-validate everything, prevent default jeśli błędy.

**Server-side validation wciąż MUSI być.** Client-side to UX, nie security. JS może być wyłączony, manipulowany.

**Modern alternative:** native `<form>` z `required`, `pattern` etc. już daje validation. Browser pokazuje built-in tooltips. Custom messages: `input.setCustomValidity('Twój tekst')`.

**Frameworki (React/Angular/Vue) mają form libraries** — Formik, react-hook-form, Angular ReactiveForms — które abstraktują boilerplate.

**Interview trap:** „Live validate during typing dla email" — frustrujące, user dopiero pisze pierwszą literę a już error. Best UX: validate on blur, re-check on input gdy już błąd. Subtle ale ważne.
**Tags:** forms, validation, javascript, ux

## Q-HTM-021 [bloom: apply]
**Question:** Pokaż prosty Web Component `<price-display>` który formatuje BigDecimal z currency.
**Model answer:**
```html
<script>
class PriceDisplay extends HTMLElement {
  static observedAttributes = ['amount', 'currency', 'locale'];
  
  attributeChangedCallback() {
    if (this.shadowRoot) this.render();
  }
  
  connectedCallback() {
    this.attachShadow({mode: 'open'});
    this.render();
  }
  
  render() {
    const amount = parseFloat(this.getAttribute('amount') || '0');
    const currency = this.getAttribute('currency') || 'PLN';
    const locale = this.getAttribute('locale') || 'pl-PL';
    
    const formatted = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
    
    this.shadowRoot.innerHTML = `
      <style>
        :host { display: inline-block; font-weight: bold; }
        .amount { color: #2c5f2d; }
      </style>
      <span class="amount">${formatted}</span>
    `;
  }
}

customElements.define('price-display', PriceDisplay);
</script>

<!-- Użycie -->
<price-display amount="99.99" currency="PLN"></price-display>
<price-display amount="120.50" currency="EUR" locale="de-DE"></price-display>
<price-display amount="1234.56" currency="USD" locale="en-US"></price-display>
```
**Wynik:**
- `99,99 zł` (PLN, pl-PL)
- `120,50 €` (EUR, de-DE)
- `$1,234.56` (USD, en-US)

**Mechanika:**
- `observedAttributes` — lista atrybutów które obserwujemy (callback gdy zmiana).
- `attributeChangedCallback` — odpala się przy zmianie atrybutu (np. `el.setAttribute('amount', '50')`).
- Shadow DOM — CSS encapsulated, nie wycieka do/z parent stylesheets.
- `Intl.NumberFormat` — built-in browser API, pełen locale support, currency formatting per kraj.

**Edge cases pricing:**
- BigDecimal precision: `parseFloat` ma utratę precyzji. Dla pricing — przekaż amount jako string i użyj decimal.js / big.js do precision (small overhead, sensowne tylko gdy precise math). Dla display tylko `Intl.NumberFormat` jest OK.
- Negative price (zniżka): `formatted` automatycznie pokazuje `-99,99 zł`. Można custom: jeśli `amount < 0`, dodać class `discount`.

**Reuse:** w prawdziwym pricing platformie taki komponent może być wystawiony do partnerów (ich strony) jako embedded:
```html
<script src="https://cdn.example.com/price-display.js"></script>
<price-display amount="99.99" currency="PLN"></price-display>
```

**Interview trap:** Locale mismatch — `pl-PL` z USD daje `99,99 USD` (currency code, nie symbol $). Browser-specific. Druga: `Intl.NumberFormat` jest expensive — dla wielu instancji rozważ memoization (jeden formatter per locale+currency, reused).
**Tags:** web-components, intl, pricing, formatting

## Q-HTM-022 [bloom: apply]
**Question:** Pokaż jak osadzić w stronie `<iframe>` widget od third-party (np. mapę Google Maps), z security best practices.
**Model answer:**
```html
<iframe 
  src="https://www.google.com/maps/embed?pb=..." 
  width="600" 
  height="450" 
  style="border:0"
  allowfullscreen=""
  loading="lazy"
  referrerpolicy="no-referrer-when-downgrade"
  sandbox="allow-scripts allow-same-origin"
  title="Lokalizacja sklepu"
></iframe>
```

**Co tu jest:**
- **`src`** — URL iframe content. Powinien być HTTPS.
- **`width`/`height`** — wymiary. Modern: `style="aspect-ratio: 16/9"` lub CSS responsive.
- **`loading="lazy"`** — iframe ładuje się dopiero gdy w widoku. Performance win.
- **`referrerpolicy`** — kontroluje co Referer header ujawnia third-party. `strict-origin-when-cross-origin` jest sensible default.
- **`sandbox`** — najważniejsze security. Restrykcje na content iframe:
  - bez `sandbox` (default) — pełen dostęp.
  - `sandbox=""` — wszystko wyłączone (no scripts, no forms, no same-origin, no popups, no top navigation).
  - `sandbox="allow-scripts"` — pozwala JS wewnątrz, ale w isolated origin.
  - `sandbox="allow-scripts allow-same-origin"` — JS + same origin (ostrożnie — defacto wyłącza sandbox jeśli iframe jest na tej samej domenie).
  - inne flagi: `allow-forms`, `allow-popups`, `allow-modals`, `allow-top-navigation`.
- **`title`** — a11y — screen reader anonsuje co to za iframe.
- **`allowfullscreen`** — czy iframe może zażądać fullscreen (np. video).

**Bezpieczeństwo:**
- **Clickjacking protection** dla TWOJEJ strony: serwer wysyła `X-Frame-Options: DENY` (lub `SAMEORIGIN`) — nikt nie może wstawić twojej strony w iframe. Lub CSP `frame-ancestors 'none'`.
- **Content Security Policy** (CSP) na twojej stronie: `frame-src 'self' https://maps.google.com` — kontroluje skąd iframe może być.
- **postMessage** dla komunikacji JS między parent a iframe (cross-origin secure channel).

**Kiedy NIE iframe:**
- Pełen content (cała strona) — lepiej window.open.
- User input forms — phishing risk jeśli third-party.
- Sensitive data — nie embedduj iframe z untrusted source.

**Pricing-specific:** widgety partnerskie (np. checkout od third-party payment provider) często są iframe. Sandbox to MUST. PCI compliance często wymaga konkretnej konfiguracji.

**Interview trap:** „Sandbox z allow-same-origin + allow-scripts" jest pełen dostęp. Sandbox ma sens przy untrusted content (user-generated, ads). Druga: `loading=lazy` w iframe nie jest wszędzie wspierany — fallback IntersectionObserver jeśli krytyczne.
**Tags:** iframe, security, embed, third-party

## Q-HTM-023 [bloom: apply]
**Question:** Dodaj Open Graph meta tags do strony cennika produktu, żeby ładnie wyglądała udostępniana w social media.
**Model answer:**
```html
<head>
  <!-- Standard meta -->
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Phone XYZ — 2 999 PLN | Acme Store</title>
  <meta name="description" content="Phone XYZ za 2 999 PLN. Bezpłatna dostawa. 24-miesięczna gwarancja.">

  <!-- Open Graph (Facebook, LinkedIn, etc.) -->
  <meta property="og:type" content="product">
  <meta property="og:title" content="Phone XYZ — 2 999 PLN">
  <meta property="og:description" content="Bezpłatna dostawa, 24-miesięczna gwarancja.">
  <meta property="og:image" content="https://acme.com/products/phone-xyz.jpg">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:url" content="https://acme.com/products/phone-xyz">
  <meta property="og:site_name" content="Acme Store">
  <meta property="og:locale" content="pl_PL">
  
  <!-- Product-specific OG -->
  <meta property="product:price:amount" content="2999.00">
  <meta property="product:price:currency" content="PLN">
  <meta property="product:availability" content="in stock">
  
  <!-- Twitter Card -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="Phone XYZ — 2 999 PLN">
  <meta name="twitter:description" content="Bezpłatna dostawa, gwarancja 24m.">
  <meta name="twitter:image" content="https://acme.com/products/phone-xyz.jpg">
  
  <!-- JSON-LD Structured Data (Google) -->
  <script type="application/ld+json">
  {
    "@context": "https://schema.org",
    "@type": "Product",
    "name": "Phone XYZ",
    "image": "https://acme.com/products/phone-xyz.jpg",
    "description": "...",
    "offers": {
      "@type": "Offer",
      "url": "https://acme.com/products/phone-xyz",
      "priceCurrency": "PLN",
      "price": "2999.00",
      "availability": "https://schema.org/InStock"
    }
  }
  </script>
</head>
```

**Co to daje:**
- **Open Graph** — gdy ktoś udostępnia link na FB/LinkedIn, fb fetch-uje meta tags i renderuje ładny preview z obrazkiem, tytułem, opisem.
- **Twitter Card** — analogicznie dla X.
- **JSON-LD Schema.org** — Google Search rich snippets (cena, availability w wynikach search), Google Shopping integration.

**Best practices:**
- **og:image** ≥ 1200×630px, < 5 MB, JPEG/PNG/WebP. Aspect 1.91:1.
- **Title** < 60 znaków (cuts off w preview).
- **Description** < 200 znaków.
- **Validate:** Facebook Sharing Debugger, Twitter Card Validator, Google Rich Results Test.

**Pricing context:** dla pricing platformy publicznej, te tags = critical. Bez nich shared link wygląda goło. Z nimi — visibility w social, lepszy CTR.

**Interview trap:** „og:image musi być publiczny" — tak, dostępny bez auth. Druga: Facebook agresywnie cache-uje OG data — po update treści musisz re-scrape przez Facebook Debugger. Trzecia: locale, np. `pl_PL` (z underscore, nie hyphen jak w HTML lang).
**Tags:** seo, social-media, meta, structured-data

## Q-HTM-024 [bloom: apply]
**Question:** Pokaż implementację infinite scroll dla listy produktów (paginacja z REST cursor-based).
**Model answer:**
```html
<ul id="products"></ul>
<div id="loader" style="display:none;">Ładowanie...</div>
<div id="end-marker" style="display:none;">Koniec listy</div>

<script>
let cursor = null;
let loading = false;
let hasMore = true;

const list = document.getElementById('products');
const loader = document.getElementById('loader');
const endMarker = document.getElementById('end-marker');

async function loadPage() {
  if (loading || !hasMore) return;
  loading = true;
  loader.style.display = 'block';

  try {
    const url = cursor 
      ? `/api/products?after=${encodeURIComponent(cursor)}&limit=20`
      : `/api/products?limit=20`;
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    
    for (const product of data.items) {
      const li = document.createElement('li');
      li.innerHTML = `<strong></strong> — <span></span>`;
      li.querySelector('strong').textContent = product.name;
      li.querySelector('span').textContent = `${product.price} ${product.currency}`;
      list.appendChild(li);
    }
    
    cursor = data.next_cursor;
    hasMore = data.has_more;
    
    if (!hasMore) {
      endMarker.style.display = 'block';
      observer.disconnect();
    }
  } catch (e) {
    console.error('Load failed:', e);
    loader.textContent = 'Błąd. Kliknij by spróbować ponownie.';
    loader.onclick = () => { loader.onclick = null; loader.textContent = 'Ładowanie...'; loadPage(); };
    return;
  } finally {
    loading = false;
    loader.style.display = 'none';
  }
}

// IntersectionObserver — odpala loadPage gdy "sentinel" element pojawi się w viewport
const sentinel = document.createElement('div');
sentinel.id = 'sentinel';
sentinel.style.height = '1px';
list.parentNode.insertBefore(sentinel, list.nextSibling);

const observer = new IntersectionObserver((entries) => {
  if (entries[0].isIntersecting) loadPage();
}, {rootMargin: '200px'});  // załaduj zanim user dotrze do końca

observer.observe(sentinel);

loadPage(); // initial load
</script>
```

**Mechanika:**
- **IntersectionObserver** — modern, performant. Odpala callback gdy element wszedł/wyszedł z viewport. `rootMargin: '200px'` — trigger 200px przed faktycznym dotknięciem dna (preload, brak migotania).
- **Sentinel pattern** — niewidoczny element na końcu listy. Gdy widoczny → load more.
- **Cursor pagination** — server zwraca `next_cursor` (opaque string), klient wysyła w next request.
- **`loading` flag** — chroni przed concurrent calls (user szybko scrolluje).
- **Error handling** — pokazuje retry option.

**Stary sposób (scroll event):**
```javascript
window.addEventListener('scroll', () => {
  if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 200) {
    loadPage();
  }
});
```
Działa, ale gorszy performance (event firing constantly), reflow risk. IntersectionObserver wygrywa.

**A11y:** infinite scroll jest a11y-hostile dla keyboard users (nie da się dotrzeć do footera). Solutions: button „Pokaż więcej" zamiast/oprócz infinite scroll. Lub announce nowych elementów przez `aria-live`.

**Interview trap:** Browser back button + infinite scroll = scroll position lost. UX: zapisz position w sessionStorage, restore on navigation back. Druga: bardzo długie listy → DOM puchnie → wolniej. Virtual scrolling (np. Angular CDK Virtual Scroll) renderuje tylko widoczne elementy.
**Tags:** infinite-scroll, intersection-observer, pagination, javascript

---

## Q-HTM-025 [bloom: analyze]
**Question:** Twój zespół rozważa SPA (Angular) vs MPA (klasyczne strony serwerowe z Thymeleaf) dla pricing admin panel. Argumenty?
**Model answer:**

**SPA (Single Page Application — Angular/React/Vue):**
- **Plusy:** rich interactivity, smooth UX, pojedynczy bundle JS, route changes bez full reload, łatwiejsza integracja z REST API (frontend i backend osobno deployable), dev experience modern (TypeScript, hot reload), reusable components.
- **Minusy:** initial bundle size (kilkadziesiąt-kilkaset KB), SEO challenges (SSR/prerendering wymagany), state management complexity (Redux, NgRx), tooling stack (Webpack, Vite — overhead), potrzebny zespół frontend.

**MPA (Multi-Page Application — Thymeleaf/JSP):**
- **Plusy:** simple — server renders HTML, browser displays. SEO trivial. Szybki initial paint (HTML jest gotowe). Mniej JS = mniej bug surface. Tighter integration z backend (Spring Security, validation w jednym stacku).
- **Minusy:** każdy click = full reload (slower UX), interactive features wymagają vanilla JS lub jQuery (legacy feel), trudniej skalować zespół (frontend vs backend rozdzielenie nieostre).

**Hybrid:**
- **Hotwire / HTMX** — server-rendered HTML z partial updates przez AJAX. Best of both, modern revival of MPA.
- **Astro / Next.js** — SPA-like dev, SSR-first delivery, hydration where needed.

**Decyzja per use case (pricing admin):**

**Argumenty za SPA (Angular):**
- Admin panel = power users, expected rich UX (drag-drop, real-time updates, complex forms).
- Reusable widgets across views (price tables, charts, audit logs).
- Frontend team może rozwijać niezależnie od backend.
- Skala: jeśli wielu adminów, bandwidth na bundle to inwestycja jednorazowa, potem cache.

**Argumenty za MPA:**
- Mały zespół, full-stack devs.
- Prosty CRUD, mało interaktywności.
- SEO nie ma znaczenia (admin za auth).
- Rapid prototyping.

**W pricingu specyficznie:** admin panel zwykle wybiera SPA (rich UX dla power users), public pricing pages mogą być MPA/SSR (SEO + szybki initial load).

**Decision criteria:**
1. **Team skills:** zespół ma frontend devs? → SPA realistyczne.
2. **Use case complexity:** rich interactivity → SPA. Simple CRUD → MPA OK.
3. **SEO:** publiczne strony → MPA/SSR. Internal admin → SPA OK.
4. **Time to market:** prosty MPA ships faster.
5. **Budget long-term:** SPA więcej inwestycji w infra (CI/CD, build optimization).

**Interview trap:** „SPA zawsze lepsze bo modern" — cargo cult. Wiele admin paneli żyje w stack Spring + Thymeleaf przez lata produktywnie. Druga: „MPA = old school" — Hotwire / HTMX to MPA renaissance, often simpler and lighter than SPA.
**Tags:** spa, mpa, architecture, frontend, decision

## Q-HTM-026 [bloom: analyze]
**Question:** Jak zapewnisz że twoja strona dobrze radzi sobie z wolnym połączeniem mobile (3G)?
**Model answer:** Performance budget + measurement. **Strategie:**

**1. Bundle size:**
- Code splitting (Angular: lazy modules, route-based chunks).
- Tree shaking (eliminate dead code).
- Compress (gzip/brotli, Brotli ~20% mniej niż gzip).
- Cel: critical JS < 100 KB gzipped, total per route < 250 KB.

**2. Critical rendering path:**
- Inline critical CSS (above-the-fold styles in `<style>` head).
- Defer non-critical CSS (`<link rel="stylesheet" media="print" onload="this.media='all'">`).
- Defer non-critical JS (`defer` lub `async`).

**3. Images:**
- Modern formats: WebP, AVIF (50-80% smaller than JPEG).
- Responsive images: `srcset` + `sizes` — browser fetches appropriate size.
- Lazy loading: `loading="lazy"` na `<img>` i `<iframe>`.
- Placeholder strategy: blur-up (small low-quality image, swap to full).

**4. Fonts:**
- `font-display: swap` — fallback font shown immediately, swap when custom loaded.
- Subset (only used glyphs).
- WOFF2 only (most efficient).
- Self-host (avoid extra DNS/connection to Google Fonts).

**5. Network:**
- HTTP/2 (multiplex requests, header compression).
- HTTP/3 (QUIC, better mobile recovery).
- Preconnect / preload / prefetch headers for critical resources.
- CDN: serve from edge close to user.

**6. Cache:**
- `Cache-Control: immutable` for hashed assets (css/js with hash in filename).
- ETag for HTML.
- Service Worker for offline (PWA): cache shell, runtime cache for API.

**7. Server-side:**
- SSR (Server-Side Rendering) lub prerendering — first paint dostarcza już HTML, nie blank screen czekając na JS.
- Reduce TTFB (Time to First Byte) — fast backend, CDN cache for HTML if cache-able.

**8. Avoid:**
- Heavy frameworks gdy nie potrzeba (SPA dla landing page = overkill).
- Third-party scripts (analytics, ads, widgets) — często 50%+ bundle. Audit i strip.
- Render-blocking resources w `<head>`.

**9. Measurement:**
- **Core Web Vitals:** LCP (Largest Contentful Paint, < 2.5s), CLS (Cumulative Layout Shift, < 0.1), INP (Interaction to Next Paint, < 200ms).
- Tools: Lighthouse, WebPageTest, PageSpeed Insights.
- Real User Monitoring (RUM): Cloudflare Analytics, Sentry, custom beacon.
- Throttle network in DevTools to simulate 3G — test before deploy.

**10. Progressive enhancement:**
- Strona musi być użyteczna z basic HTML/CSS, JS tylko enhancement. Slow connection user wciąż widzi content gdy JS ładuje się 5s.

**Decision priorities:**
1. Audit current state — Lighthouse, find biggest losses.
2. Fix biggest first (often bundle size or images).
3. Measure improvement.
4. Iterate.

**Interview trap:** Microoptymalizacje (which loop is faster) gdy bundle ma 1 MB obrazu. Optymalizacja kolejnościowa: największe wins first.
**Tags:** performance, mobile, web-vitals, optimization

## Q-HTM-027 [bloom: analyze]
**Question:** Jak zaprojektujesz a11y-friendly formularz zamówienia w pricing app?
**Model answer:** Pricing forms często mają complex validation, dynamic prices, multiple steps. A11y musi być part of design, not afterthought.

**Foundations:**
1. **Semantic HTML** — `<form>`, `<label for>`, `<fieldset>`/`<legend>` dla grup, native `<button type="submit">`.
2. **Keyboard navigation** — Tab order matches visual flow. `tabindex="0"` for custom interactive elements; `-1` for elements not in flow but focusable programatically.
3. **Focus management:**
   - Visual focus indicator (`:focus-visible` styling).
   - Focus first invalid field on submit failure.
   - Focus first field on form open.
   - Trap focus in modals.
4. **Error messages:**
   - `<span role="alert">` lub `aria-live="assertive"` dla server errors (immediate announce).
   - `aria-describedby` na input wskazuje na error span.
   - Errors visible AND machine-readable (don't rely on color alone — `<span class="error">⚠ Wymagane</span>`).

**Pricing-specific:**
1. **Dynamic prices update** — gdy zmiana ilości / wariantu zmienia cenę, ogłoś przez `aria-live="polite"`:
   ```html
   <p>Cena: <span id="price-display" aria-live="polite">99,99 PLN</span></p>
   ```
2. **Currency input** — `inputmode="decimal"`, `step="0.01"`, jasne formatowanie. Akceptuj "99,99" i "99.99" (locale tolerance).
3. **Quantity stepper** — używaj native `<input type="number">` LUB custom z proper ARIA: `role="spinbutton"`, `aria-valuemin`, `aria-valuemax`, `aria-valuenow`.
4. **Long forms (multi-step):**
   - Step indicator z `aria-current="step"`.
   - Progress info: „Krok 2 z 4".
   - Save state — refresh nie traci progress.
   - Allow nawigację wstecz (review steps).

**Validation:**
1. **Client-side** for UX — inline po blur, summary at submit.
2. **Server-side** is source of truth — błędy server side też muszą być a11y-announced.
3. **Error summary at top** of form (especially long forms): lista linków do invalid fields. Click → focus + scroll do pola.

**Visual:**
1. **Color contrast** ≥ 4.5:1 dla normal text (WCAG AA).
2. **Don't rely on color alone** — czerwone błędy + ikona + tekst.
3. **Focus indicator** widoczny (nie usuwaj `outline` bez zastąpienia).

**Testing:**
- Keyboard-only (no mouse) flow through whole form.
- Screen reader (NVDA Windows, VoiceOver Mac) test.
- axe-core / Lighthouse a11y audit.
- Real users: invitation users with disabilities for usability test.

**Tooling:**
- ESLint plugin jsx-a11y (React/Angular).
- @axe-core/playwright dla automated testów.
- Storybook + a11y addon.

**Interview trap:** „A11y dorobimy później" — fixed scope, never gets done. Add to definition-of-done. Druga: passing axe ≠ usable. Real user testing reveals issues automation misses.
**Tags:** a11y, forms, pricing, validation

## Q-HTM-028 [bloom: analyze]
**Question:** Twoja landing page e-commerce ma Lighthouse score 50. Co byś zrobił first?
**Model answer:** **Audit first, fix systematically.**

**Krok 1 — uruchom Lighthouse na pełnej stronie z mobile + desktop modes.** Note breakdowns:
- Performance (kluczowe dla 50 score)
- Accessibility
- Best Practices
- SEO

Score 50 zazwyczaj oznacza issue w Performance. Sprawdź konkretne metryki:
- **LCP (Largest Contentful Paint)** — jeśli > 4s, hero image / fonts / blocking resources problem.
- **FID/INP (Interaction)** — jeśli > 200ms, JS execution heavy on main thread.
- **CLS (Cumulative Layout Shift)** — jeśli > 0.25, images/iframes/ads bez wymiarów.
- **TBT (Total Blocking Time)** — long tasks > 50ms blocking interactivity.

**Krok 2 — identyfikuj największe wins:**

**Common landing page issues:**
1. **Hero image not optimized** — 5 MB JPEG — łatwy fix: WebP, responsive sizes, compression. Może spaść z 5 MB do 100 KB. LCP improvement 2-3 sekund.
2. **Render-blocking CSS:** wszystkie CSS w `<head>` blokują render. Solution: critical inline + defer rest.
3. **Render-blocking JS:** scripts bez async/defer. Solution: defer lub async.
4. **Third-party scripts:** Google Analytics, Facebook Pixel, ads — często 30-50% load time. Audit, lazy-load po user interaction, lub backend proxy.
5. **No CDN:** static assets z origin servera — slow. Solution: Cloudflare, Cloudfront, BunnyCDN.
6. **No compression:** brak gzip/brotli na server. Solution: enable w nginx/CDN.
7. **Large web fonts:** kilka pełnych font families = 500 KB+. Solution: subset, woff2, font-display: swap.
8. **No image lazy loading:** all images load on initial. Solution: `loading="lazy"`.
9. **Layout shifts:** images bez width/height — content moves po fetch. Solution: explicit dimensions lub `aspect-ratio`.

**Krok 3 — fix top 3 (Pareto)**: zazwyczaj 3 fixes daje 70% improvement. Nie próbuj wszystkiego naraz.

**Krok 4 — re-measure**. Lighthouse w lokalnym CI lub przez WebPageTest dla repeatable runs.

**Krok 5 — RUM (Real User Monitoring):** Lighthouse to lab data. RUM pokazuje real users — często gorzej niż lab (różne devices, networks). Cloudflare Web Analytics, Sentry Performance.

**Tooling:**
- Lighthouse (Chrome DevTools).
- WebPageTest (more detailed, multiple locations).
- PageSpeed Insights (combination of lab + field).
- Chrome DevTools Performance tab (frame-by-frame analysis).

**Wins specific dla pricing landing:**
- Pricing table często ma JS calculator → defer it (load on visible).
- Cookie banner — common LCP blocker — make it lightweight.
- Video hero — MASSIVE; replace with optimized image + autoplay short clip on hover.

**Interview trap:** „Score 100 to cel" — perfectionism. Score 90+ jest already good. Diminishing returns above 90. Druga: Lighthouse jest snapshot, nie complete picture — RUM ujawnia różnice.
**Tags:** performance, lighthouse, web-vitals, optimization

## Q-HTM-029 [bloom: analyze]
**Question:** Jak skonstruujesz HTML strony tak, żeby była indeksowana dobrze przez Google?
**Model answer:** SEO basics + technical SEO. **Stack:**

**1. Crawlability:**
- `robots.txt` w root — pozwala/zabrania konkretnym botom dostęp.
- `sitemap.xml` — lista wszystkich publicznych URL-i.
- Avoid `noindex` na ważnych stronach.
- Internal linking — każda strona dostępna z home przez < 3 clicks.

**2. Indexability:**
- Server returns 200 dla valid pages.
- Canonical URL: `<link rel="canonical" href="https://...">` — wskazuje preferowany URL gdy ten sam content na wielu URL-ach (np. duplicates z query params).
- `<meta name="robots" content="index,follow">` (default but explicit OK).

**3. Content:**
- Unique, useful content per page.
- `<title>` unique per page, < 60 znaków, zawiera primary keyword.
- `<meta name="description">` < 155 znaków, attractive (CTR factor).
- `<h1>` jeden per strona, opisuje main topic.
- Hierarchy `<h2>`, `<h3>`. Don't skip levels.
- Word count: tutaj zależy od typu — landing 500+, blog post 1000+, product page wystarczy 200-500 dobrych.

**4. Performance:**
- Core Web Vitals są ranking factor.
- LCP < 2.5s, CLS < 0.1, INP < 200ms.
- Mobile-first indexing — strona must work mobile.

**5. Structured data (Schema.org):**
- JSON-LD w `<head>` (preferred over microdata).
- Types: Product, Article, BreadcrumbList, FAQPage, Organization, Review.
- Rich snippets — Google pokazuje cenę / rating / FAQ w SERP results.

**6. Mobile-friendly:**
- Responsive design (`<meta viewport>`).
- Tap targets size > 48px.
- No horizontal scroll.

**7. URL structure:**
- Czytelne URL: `/products/phone-xyz` zamiast `/p?id=12345`.
- Use hyphens, not underscores.
- Lowercase.
- Limit depth (`/category/sub/sub/sub/page` is bad).

**8. International:**
- `hreflang` dla wielu języków: `<link rel="alternate" hreflang="pl" href="...">` + `hreflang="x-default"`.
- Country-specific top-level domains (ccTLD) lub subfolders (`/pl/`).

**9. Trust:**
- HTTPS (ranking factor).
- No malware / phishing.
- Author info, contact details (E-A-T: Expertise, Authoritativeness, Trustworthiness).

**10. Avoid:**
- Cloaking (different content for bots vs users).
- Hidden text.
- Doorway pages.
- Aggressive interstitials on mobile (Google penalizes).
- Heavy JavaScript dependency without SSR/prerendering — Google can render JS but slower indexing.

**Pricing platform specific:**
- Product pages with structured data (`@type: Product`, offers, price) → rich snippets in Google Shopping.
- FAQ schema for product Q&A.
- Sitemap with priority by importance.

**Interview trap:** „SEO = keywords stuffing" — gone. Modern SEO = quality content + technical excellence + UX. Druga: SPA bez SSR — Google rendering jest slower (waiting for JS to execute). For e-commerce — SSR or prerendering for SEO wins.
**Tags:** seo, html, technical-seo, structured-data

## Q-HTM-030 [bloom: analyze]
**Question:** Jak HTML / frontend integruje się z REST backendem pricingu — typowe problemy?
**Model answer:** **Common integration issues:**

**1. Loading states:**
- Problem: spinner forever / brak feedbacku.
- Solution: explicit loading + error + empty states. Skeleton screens (shimmer effect podczas load) lepsze niż spinners.

**2. Error handling:**
- Problem: alert() z generic „Error", lub silent failure.
- Solution: typed errors per scenario (network, 4xx, 5xx). Retry button. Friendly messages: „Connection lost. Retrying..." vs „Server error. Try again."

**3. Authentication state:**
- Problem: token expires mid-session, requesty zaczynają failować, user nie wie co się stało.
- Solution: HTTP interceptor (Angular `HttpInterceptor`, Axios interceptor) catches 401, próbuje refresh. Jeśli refresh fails — redirect do login.

**4. Race conditions:**
- Problem: user szybko klika, multiple requests in flight, response order = nondeterministic.
- Solution: cancel previous request (AbortController, Angular RxJS `switchMap`). Lub debounce dla search.

**5. Optimistic updates vs server state:**
- Problem: UI updates immediately, server fails — UI is wrong.
- Solution: optimistic update + rollback on failure. Show transient state (e.g., grayed item until confirmed).

**6. Stale data:**
- Problem: user widzi old prices przez minuty.
- Solution: server-sent events / WebSockets dla critical updates (price changes). Lub polling z exponential backoff.

**7. Form sync z backend validation:**
- Problem: client validates email format, server rejects po unique check. Two error layers, inconsistent UX.
- Solution: server returns structured errors (`{field: "email", code: "EMAIL_TAKEN"}`), frontend mapuje per-field. Same validation messages.

**8. Currency / locale handling:**
- Problem: server zwraca `99.99`, JS parses jako float (precision loss).
- Solution: server zwraca `"99.99"` jako string, frontend uses Intl.NumberFormat dla display. Dla calculation client-side — decimal.js / big.js.

**9. CORS:**
- Problem: `fetch` failing z „CORS error" w console.
- Solution: backend wystawia proper CORS headers. Lub same-origin (proxy backend pod tą samą domeną).

**10. CSRF:**
- Problem: cookie-based auth + POST without CSRF token = vulnerable.
- Solution: SameSite=Strict cookies, CSRF token w form, double-submit cookie pattern.

**11. Long requests / timeouts:**
- Problem: pricing calculation czasem 5s, user widzi spinner.
- Solution: optimistic UI (start showing partial), or async pattern (POST returns 202 + job id, poll).

**12. Type safety:**
- Problem: API change → frontend code breaks at runtime.
- Solution: TypeScript types generated z OpenAPI spec (openapi-generator, openapi-typescript). Single source of truth.

**13. State management:**
- Problem: prices in 5 components, each fetches separately, inconsistent.
- Solution: shared store (NgRx, Akita, Redux Toolkit). Single fetch, reused across components.

**14. Offline:**
- Problem: app crashes na bus tunnel.
- Solution: service worker + IndexedDB dla cache. Show offline state: „No connection. Showing cached data."

**Pricing specific:**
- Live pricing updates — WebSocket subscription dla zmian.
- Currency conversion — backend zwraca per requested currency, OR frontend converts używając rates fetched separately.
- Permission gating — UI hides/disables features per user role, ALE backend ma authoritative check (user widzi disabled button, ale backend też odrzuci — defense in depth).

**Interview trap:** „Frontend trust nothing, validate everything po stronie clienta" — partial. Client validation is for UX. Server is authoritative. Don't duplicate logic — share validation rules przez schema (JSON Schema, OpenAPI), generate client + server validators.
**Tags:** integration, frontend, rest, common-issues

## Q-HTM-031 [bloom: analyze]
**Question:** Pricing platforma będzie konsumowana przez 3 frontendy (admin Angular, public Astro, mobile React Native). Co byś zaplanował na froncie API?
**Model answer:** Multi-client API design. **Strategie:**

**1. Single REST API, multiple clients:**
- Plus: simple, jeden source of truth.
- Minus: API endpoints muszą serve heterogeneous needs. Admin chce wszystko, public chce minimum, mobile chce optimalizowane payloads.
- Workarounds: sparse fieldsets (`?fields=id,price,currency`), pagination params, separate endpoints per use case (`/api/admin/products` vs `/api/public/products` vs `/api/v1/m/products`).

**2. Backend-for-Frontend (BFF) pattern:**
- Plus: każdy frontend ma własny backend layer (Node.js/Express? Spring per BFF), agreguje calls do core services, kształtuje response per client needs. Mobile BFF zwraca minimalny JSON, admin BFF rich data.
- Minus: 3 BFFs do utrzymania. Łatwo divergence in business logic.

**3. GraphQL:**
- Plus: każdy klient pyta dokładnie co potrzebuje. Strong types przez schema. Single endpoint.
- Minus: kompromis cachowania (POST queries), security (query depth limits), tooling overhead.

**4. Hybrid REST + GraphQL:**
- REST dla data plane (high traffic, cacheable: pricelist).
- GraphQL dla view-specific aggregations (admin dashboards z 10 different data needs).

**Versioning strategy:**
- URL versioning (`/v1/`, `/v2/`) — explicit, łatwe do gestion deprecation.
- Backward compat OK to add fields, NOT OK to remove/rename.
- Sunset header z RFC 8594 dla deprecation timeline.

**Authentication:**
- OAuth2 client credentials dla service-to-service (mobile app → backend).
- User auth: OIDC z JWT. Same mechanism dla wszystkich frontendów.
- API keys (if needed) per client app dla rate limiting / analytics.

**Documentation:**
- OpenAPI 3.1 spec (single source). Generate SDKs per language (TypeScript dla web, Swift dla iOS, Kotlin dla Android).
- Live docs: Swagger UI, Redoc, Stoplight.
- Tutorials per use case (zamiast jeden monolithic doc).

**Performance:**
- CDN dla static / cacheable responses.
- Compression: gzip/brotli — wszyscy klienci.
- HTTP/2 minimum, HTTP/3 dla mobile (better recovery on flaky network).
- Pagination: cursor-based (better at scale).

**Errors:**
- Standard error format (RFC 7807 Problem Details for HTTP APIs):
  ```json
  {"type": "https://api.example.com/errors/invalid-price", "title": "Invalid price", "status": 422, "detail": "...", "instance": "..."}
  ```
- Same shape across all endpoints — clients code once.

**Cross-concern:**
- Idempotency-Key dla mutating operations.
- Rate limiting per client (mobile may have different limits than admin).
- Observability: distributed tracing (OpenTelemetry), spans per request — debug across clients/services.
- Feature flags — beta features for one client first.

**Decyzja krokami:**
1. Start z **REST + sparse fieldsets** (simplest, tested pattern).
2. Add BFF if specific client has unique needs (mobile often).
3. Add GraphQL if frontend teams complain o multiple roundtrips dla complex views.

**Pricing-specific:**
- Public API: read-mostly, heavy cache, public CDN.
- Admin: writes, audit, less traffic, behind auth.
- Mobile: mobile-optimized (smaller payloads, cursor pagination).

**Interview trap:** „GraphQL dla wszystkich" — premature. Start simple, add complexity gdy potrzeba. Drugi: ignorowanie versioning na początku — później migration headache.
**Tags:** api-design, multi-client, bff, architecture, decision

## Q-HTM-032 [bloom: analyze]
**Question:** Pokaż jak by wyglądał typowy mock interview pytanie HTML-ish „pokaż jak wyświetlasz cennik" — jakich rzeczy szukałbyś gdybyś był rekruterem?
**Model answer:** Z perspektywy rekrutera — pytanie open-ended, sprawdza myślenie kandydata. **Sygnały które oceniam:**

**Krok 1 — Czy zadaje pytania?**
Dobry kandydat doprecyzowuje:
- „Cennik dla kogo? Public e-commerce czy admin?"
- „Ile pozycji? 10 czy 10000?"
- „Static czy dynamic (zmiany live)?"
- „Stack? Vanilla HTML czy Angular?"
- „Mobile-first czy desktop-first?"
Brak pytań = naive. Doprecyzowanie wskazuje doświadczenie.

**Krok 2 — Czy myśli o REST API kontrakcie?**
- Endpoint jaki zwraca? `GET /products` z paginacją.
- Schema response — `{items: [...], pagination: {...}}` czy plain array?
- Currency, locale handling.
- Loading / error / empty states.

**Krok 3 — Code:** patrzę jakim podejściem rozwiązuje:

```html
<!-- Basic -->
<div id="pricelist" aria-busy="true">
  <p>Loading...</p>
</div>

<script>
async function loadPricelist() {
  const container = document.getElementById('pricelist');
  try {
    const response = await fetch('/api/products?limit=50');
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    
    container.setAttribute('aria-busy', 'false');
    
    if (data.items.length === 0) {
      container.innerHTML = '<p>Brak produktów</p>';
      return;
    }
    
    const list = document.createElement('table');
    list.innerHTML = `
      <thead>
        <tr>
          <th scope="col">Produkt</th>
          <th scope="col">Cena</th>
          <th scope="col">Waluta</th>
        </tr>
      </thead>
      <tbody></tbody>
    `;
    
    const tbody = list.querySelector('tbody');
    for (const product of data.items) {
      const row = tbody.insertRow();
      row.insertCell().textContent = product.name;
      row.insertCell().textContent = new Intl.NumberFormat('pl-PL', {
        style: 'decimal', minimumFractionDigits: 2
      }).format(product.price);
      row.insertCell().textContent = product.currency;
    }
    
    container.innerHTML = '';
    container.appendChild(list);
  } catch (e) {
    container.setAttribute('aria-busy', 'false');
    container.innerHTML = `<p role="alert">Błąd ładowania: ${e.message}</p>`;
  }
}

loadPricelist();
</script>
```

**Co rekruter odhaczy:**
- ✓ Semantic table z `<th scope="col">`.
- ✓ A11y: `aria-busy`, `role="alert"`.
- ✓ Loading / error / empty states.
- ✓ `Intl.NumberFormat` dla locale-aware formatting.
- ✓ `textContent` (no XSS) zamiast `innerHTML`.
- ✓ Error handling, async/await.
- ✗ Brakuje: pagination, sortowania, responsive (na mobilkach trzeba CSS).

**Krok 4 — Refleksja:**
Dobry kandydat sam doda komentarz typu „w produkcji byłbym Angular Component z RxJS, NgRx state, virtualScroll dla wielu wierszy — tu pokazuję raw HTML jako przykład". Pokazuje świadomość że produkcyjna app jest more.

**Krok 5 — Trudniejsze warianty rekrutera:**
- „Co jak 10000 wierszy?" → virtual scrolling.
- „Co jeśli ceny się zmieniają live?" → WebSocket / SSE / polling.
- „A11y dla niedowidzącego?" → sortowanie keyboard, screen reader announce of price changes.
- „SEO?" → SSR, structured data Product schema.

**Sygnały NEGATYWNE:**
- Wpis 200 linijek bez pytań ani planu.
- innerHTML z user data (XSS hole).
- Brak error handling.
- `var` zamiast `const`/`let` (legacy code style).
- Brak loading state.
- jQuery (dla nowych projektów, antypattern).

**Interview trap:** Próba pokazania too much (full Angular app w 5 minut) — rzadko czas pozwala. Better: simple working solution + komentarze o decisions and trade-offs. To pokazuje senior thinking nawet gdy code jest prosty.
**Tags:** mock-interview, html, api, rest, pricing
