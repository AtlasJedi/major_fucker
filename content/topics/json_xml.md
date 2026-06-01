# JSON / XML — question bank

> Context: pricing engine consuming XML feeds from legacy ERP, exposing JSON for modern clients. Parsing, validation, transformation, performance.

## Scope

- JSON: składnia, typy, ograniczenia (np. brak komentarzy, brak trailing commas standardowo)
- JSON Schema: walidacja struktury, typów, constraints
- Jackson w Javie: ObjectMapper, annotations, deserializacja, polymorfizm, custom deserializers
- JsonSlurper / JsonBuilder w Groovym
- XML: dobre formatowanie, valid (vs DTD/XSD), namespaces
- DOM vs SAX vs StAX vs StreamingAPI — model parsowania
- XPath, XSLT (basics)
- XSD — walidacja, typy, constraints, complexType vs simpleType
- Groovy XmlSlurper / XmlParser / MarkupBuilder / StreamingMarkupBuilder
- JSON ↔ XML konwersja: trade-offy
- performance: streaming dla wielkich plików, partial parsing
- bezpieczeństwo: XXE (XML External Entity), JSON injection, billion laughs attack

---

## Q-JX-001 [bloom: recall]
**Question:** Jakie typy danych ma JSON?
**Model answer:** JSON ma 6 typów: 1) **string** w cudzysłowach `"foo"` — Unicode, escape sequences (`\n`, `\"`, `A`). 2) **number** — int lub float, bez prefiksów, bez NaN/Infinity (te są nielegalne w JSON, choć niektóre parsery akceptują). 3) **boolean** — `true` lub `false`. 4) **null**. 5) **array** — `[v1, v2, ...]`, mieszane typy dozwolone. 6) **object** — `{"key": value, ...}` z keys-stringami. Brak: dat, BigInteger, BigDecimal, undefined (to JS, nie JSON), komentarzy. Komentarze nielegalne w standardzie (ale JSONC, JSON5 to dorzucają jako rozszerzenia).
**Interview trap:** „JSON wspiera daty" — false. Daty są stringami w JSON, format umowny (ISO 8601). „BigInteger w JSON" — number nie ma rozmiaru gwarantowanego, parsery często gubią precyzję ponad 2^53. Dla precyzji — string z notacją (`"123456789012345.99"`).
**Tags:** json, types, basics

## Q-JX-002 [bloom: recall]
**Question:** Co to jest JSON Schema?
**Model answer:** JSON Schema to standard opisu i walidacji dokumentów JSON. Sam jest dokumentem JSON — meta-schema. **Przykład:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["product_id", "price"],
  "properties": {
    "product_id": {"type": "integer", "minimum": 1},
    "price": {"type": "number", "minimum": 0, "exclusiveMinimum": false},
    "currency": {"type": "string", "enum": ["PLN", "EUR", "USD"]},
    "tags": {"type": "array", "items": {"type": "string"}, "uniqueItems": true}
  },
  "additionalProperties": false
}
```
**Co waliduje:** typ (string/number/array/...), wymagane pola, format (date, email, uri), constraints (min/max, minLength, pattern z regex), enum, oneOf/anyOf/allOf (logiczne kombinacje), conditional schemas (if/then/else). **Użycie:** OpenAPI używa JSON Schema do request/response bodies. Konsumenci dokumentacji + walidacja runtime. Generation modeli (TypeScript types z JSON Schema). **Tooling:** Ajv (Node.js), `jsonschema` (Python), `everit-org/json-schema` (Java), `networknt/json-schema-validator` (Java).
**Interview trap:** Drafts — JSON Schema ma wersje (draft-04, draft-06, draft-07, 2019-09, 2020-12). Różnice w składni (`$id` vs `id`, `$ref` semantyka). Walidator musi wspierać konkretną wersję podaną w `$schema`.
**Tags:** json-schema, validation

## Q-JX-003 [bloom: recall]
**Question:** Czym jest XML namespace?
**Model answer:** XML namespace pozwala uniknąć konfliktów nazw między dokumentami z różnych źródeł. Deklaracja: atrybut `xmlns="URI"` (default ns) lub `xmlns:prefix="URI"`. Przykład:
```xml
<root xmlns:price="http://example.com/pricing" xmlns:cust="http://example.com/customer">
  <price:amount currency="PLN">100</price:amount>
  <cust:name>Foo</cust:name>
</root>
```
URI to identyfikator namespace'u — nie musi prowadzić nigdzie, to globalnie unikalne ID. Prefix (`price`, `cust`) to lokalny alias. Domyślny namespace bez prefixu obowiązuje cały subtree dopóki nie redefinowany. **XPath musi uwzględniać namespace** — `//price:amount` (z bind-em prefix → URI w kontekście query).
**Interview trap:** „XML bez namespace działa tak samo z namespace" — false. Element `<amount>` (bez ns) ≠ `<amount>` (z default ns). Query bez deklaracji namespace nie znajdzie elementów z namespace. Druga: pomylenie URI z URL — URI nie musi być fetchable.
**Tags:** xml, namespaces

## Q-JX-004 [bloom: recall]
**Question:** Wymień 3 modele parsowania XML i krótko opisz.
**Model answer:** 1) **DOM (Document Object Model)** — parser ładuje cały XML do drzewa obiektów w pamięci. Plus: full random access, łatwa modyfikacja, XPath. Minus: pamięć O(rozmiar dokumentu) — duże pliki = OOM. Java: `DocumentBuilder`, Groovy: `XmlParser`. 2) **SAX (Simple API for XML)** — push parser. Generuje events (`startElement`, `characters`, `endElement`) podczas streamowania. Aplikacja implementuje `DefaultHandler` i reaguje. Plus: O(1) pamięć, fast. Minus: aplikacja sama buduje state, brak random access, jednokierunkowy. Java: `SAXParser`. 3) **StAX (Streaming API for XML)** — pull parser. Aplikacja iteruje przez events (`reader.next()`). Plus: O(1) pamięć, control flow w aplikacji (łatwiej niż callback w SAX), bidirectional pomocniczne tools. Java: `XMLStreamReader`/`XMLEventReader`. 4) Bonus: **JAXB / data binding** — mapuje XML na POJOs (annotacje). Wygodne, ale memory-bound jak DOM (full unmarshal).
**Interview trap:** „SAX jest faster than DOM" — niekoniecznie speed-wise; często OOM-bound DOM jest wolniejszy ze względu na GC. SAX is memory-efficient. Druga: zarządzanie state w SAX jest złożone — łatwo zrobić bug w handlerze.
**Tags:** xml, parsing, dom, sax, stax

## Q-JX-005 [bloom: recall]
**Question:** Co to jest XSD?
**Model answer:** XSD (XML Schema Definition) to standard W3C do opisu struktury XML — sam jest dokumentem XML. Następca DTD z bogatszymi możliwościami. **Definiuje:**
- Elementy i atrybuty z typami (`xs:string`, `xs:int`, `xs:date`, `xs:decimal`, custom types).
- Hierarchię (`complexType`, `sequence`, `choice`, `all`).
- Kardynalność (`minOccurs`, `maxOccurs`).
- Constraints (`pattern` regex, `enumeration`, `minInclusive`, `maxInclusive`).
- Inheritance (`extension`, `restriction`).

**Przykład:**
```xml
<xs:element name="product">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="id" type="xs:int"/>
      <xs:element name="price" type="xs:decimal"/>
      <xs:element name="currency">
        <xs:simpleType>
          <xs:restriction base="xs:string">
            <xs:enumeration value="PLN"/>
            <xs:enumeration value="EUR"/>
          </xs:restriction>
        </xs:simpleType>
      </xs:element>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

**Użycie:** runtime validation (parser z `setSchema(schema)`) — wszystkie błędy schema reportowane. Plus: code generation (XJC w JAXB generuje POJOs z XSD). XSD jest expressive ale verbose.

**Interview trap:** „XSD vs DTD" — DTD jest old, ograniczone (brak typowania, brak namespaces). XSD wygrywa. „XSD vs JSON Schema" — różne ekosystemy, koncepcyjnie podobne. XSD bardziej verbose, ale ma typy z fixed semantyką (xs:dateTime to ISO 8601 z timezone).
**Tags:** xml, xsd, validation, schema

## Q-JX-006 [bloom: recall]
**Question:** Czym jest XPath?
**Model answer:** XPath to język query'owania XML — wyrażenia wybierające zestawy nodów. **Składnia:**
- `/root/element` — absolutna ścieżka.
- `//element` — szukaj wszędzie w drzewie.
- `element[@attr='value']` — predykat na atrybucie.
- `element[1]` — indeks (1-based).
- `element[position() > 5]` — funkcje.
- `element/text()` — text content.
- `element/@attr` — wartość atrybutu.
- `*` — dowolny element.
- `..` — parent, `.` — current.
- `element[last()]` — ostatni.
- `element[contains(text(), "foo")]` — funkcje string.

**Przykład:** `//product[price/@currency='PLN' and price > 100]/name/text()` — nazwy produktów w PLN drożysze niż 100. **Wersje:** XPath 1.0 (najczęściej wspierany), 2.0, 3.0, 3.1 (XQuery-related, więcej typów). Większość Java implementacji to 1.0; Saxon ma 3.0+. **Użycie:** w XSLT, w kodzie aplikacji (Java `XPath`/`XPathExpression`), w XmlSlurper Groovy implicit, w narzędziach (xmllint --xpath).
**Interview trap:** Namespace must be declared w XPath context. `//ns:product` wymaga binding `ns` → URI. Bez tego query zwraca pusty result.
**Tags:** xpath, xml, query

## Q-JX-007 [bloom: recall]
**Question:** Co to jest XXE attack?
**Model answer:** XML External Entity — ataków na parser XML, który po default rozwija external entities w DTD. **Atak**: złośliwy XML zawiera DTD z entity referencującym lokalny plik:
```xml
<?xml version="1.0"?>
<!DOCTYPE foo [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<foo>&xxe;</foo>
```
Parser bez ograniczeń wczytuje `/etc/passwd`, wstawia w `&xxe;`, aplikacja może to wyeksponować w response (jeśli odbija input). **Inne warianty:** SSRF (`SYSTEM "http://internal-service/admin"`), DOS (billion laughs — entity rekurencyjnie referencuje siebie, eksplozja pamięci), out-of-band exfiltration. **Obrona:**
- **Disable DTD processing** (najlepsze): `factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`.
- Disable external entities: `factory.setFeature("http://xml.org/sax/features/external-general-entities", false)` + `external-parameter-entities = false`.
- Java: `XMLConstants.ACCESS_EXTERNAL_DTD = ""` i `ACCESS_EXTERNAL_SCHEMA = ""`.
- Groovy `XmlSlurper`/`XmlParser` od 2.5.x mają secure defaults, ale verify per version.
- Dla untrusted XML: użyj sandboxed parser, walidacja size limit, depth limit.
**Interview trap:** „SAX nie jest podatny" — false, każdy parser bez secure config. JAXB unmarshal też domyślnie podatny — `Source` przed unmarshal musi być sanitized. SOAP web services to klasyczny vector — XML body parsed by framework.
**Tags:** xml, security, xxe

## Q-JX-008 [bloom: recall]
**Question:** Czym różni się Jackson od Gson?
**Model answer:** Oba to biblioteki Java do JSON ↔ Object mapping. **Jackson** — rozwijany przez FasterXML, najwięcej featurów. Annotations: `@JsonProperty`, `@JsonIgnore`, `@JsonFormat`, `@JsonSubTypes` (polymorfizm), custom serializers, streaming API (`JsonParser`/`JsonGenerator`), data binding (`ObjectMapper.readValue()`/`writeValueAsString()`), tree model (`JsonNode`). Spring Boot używa Jackson by default. **Gson** — Google. Mniejszy, prostszy. Annotations: `@SerializedName`, `@Expose`. Brak natywnego polymorfizmu (trzeba `RuntimeTypeAdapterFactory`). Brak streaming aż tak rozwiniętego. Mniej annotacji. **Decyzja:**
- Jackson dla większości projektów Java (ekosystem, performance, integracje Spring).
- Gson dla Android (mniejszy footprint), dla prostych use-cases bez polymorphism.
- Inne: `kotlinx.serialization` (Kotlin native), Moshi (Square, mocniejsze niż Gson, KMP), JSON-B (standard Java EE/Jakarta).
**Interview trap:** Performance benchmarks — Jackson zazwyczaj wygrywa, ale Moshi/Gson są wystarczająco szybkie dla większości. Wymiar krytyczny zazwyczaj nie performance, tylko features (polymorfizm, custom types, mixins).
**Tags:** json, jackson, gson, java

---

## Q-JX-009 [bloom: understand]
**Question:** Wytłumacz różnicę między DOM a streaming dla XML 1 GB.
**Model answer:** **DOM** ładuje cały dokument do drzewa w pamięci. Dla 1 GB XML w-RAM reprezentacja zazwyczaj 3-10x większa (pointer overhead, Object headers, char arrays per text node) — czyli 3-10 GB RAM. Heap niemożliwy w typowym serwerze, OOM. **Streaming (SAX/StAX)** — parser czyta dokument liniowo. Aplikacja przetwarza event-po-event, w danym momencie trzyma tylko fragment (np. aktualny rekord). 1 GB plik można sparsować w 100 MB RAM albo mniej. **Trade-offy:** DOM łatwy w użyciu (XPath, modyfikacja), streaming wymaga ręcznego state managementu — wiesz że jesteś w `<product>` ale nie masz contextu poza nim. **Hybrid:** parser streamuje do levelu rekordu (np. `<product>`), dla każdego rekordu robi mały DOM (1 produkt to kilkadziesiąt nodów), przekazuje do business logic. Java `JAXB unmarshaller` z `XMLEventReader` source umie tak. **Konkretne wybory dla pricingu:**
- 1 GB ERP feed → StAX scanowanie do `<product>`, na każdym JAXB unmarshal pojedynczego produktu, batch insert do DB. 200 MB heap wystarczy.
- 100 MB wciąż często DOM jest szybszy (mniej overhead na event dispatch).
- 10 MB+ — DOM bezpieczny.
**Interview trap:** „Async parser" — XML parsery są generalnie sync I/O (read-then-process). Dla async streaming z network input — wrap w reactive (Project Reactor) i pull events.
**Tags:** xml, streaming, performance, scaling

## Q-JX-010 [bloom: understand]
**Question:** Jak Jackson obsługuje polymorfizm? Pokaż na przykładzie hierarchii `Promotion`/`PercentDiscount`/`FixedDiscount`.
**Model answer:** Jackson wspiera kilka strategii. **`@JsonTypeInfo` + `@JsonSubTypes`** to klasyk:
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = PercentDiscount.class, name = "percent"),
  @JsonSubTypes.Type(value = FixedDiscount.class, name = "fixed")
})
public abstract class Promotion {
  public abstract BigDecimal apply(BigDecimal price);
}

public class PercentDiscount extends Promotion {
  public BigDecimal pct;
  @Override
  public BigDecimal apply(BigDecimal price) { return price.multiply(BigDecimal.ONE.subtract(pct)); }
}

public class FixedDiscount extends Promotion {
  public BigDecimal amount;
  @Override
  public BigDecimal apply(BigDecimal price) { return price.subtract(amount); }
}
```
Serializacja:
```json
{"type": "percent", "pct": 0.1}
{"type": "fixed", "amount": 20}
```
Jackson czyta `type` field, decyduje którą subklasę zinstancjować. Strategie: `Id.NAME` (string label), `Id.CLASS` (full class name — security risk: nie używaj dla untrusted JSON), `Id.MINIMAL_CLASS`, `Id.CUSTOM`. Include strategies: `As.PROPERTY` (top-level field), `As.WRAPPER_OBJECT` (`{"percent": {...}}`), `As.WRAPPER_ARRAY`, `As.EXISTING_PROPERTY`, `As.DEDUCTION` (zgadnij z fields).

**Interview trap:** `Id.CLASS` = security risk. Atakujący może podsunąć klasy systemowe (np. `org.springframework.context.support.ClassPathXmlApplicationContext` z konstruktorem URL → RCE). Wszystkie warianty automatycznego loadowania klas są niebezpieczne dla untrusted input. Whitelist subtypes via `@JsonSubTypes`. Druga: `@JsonSubTypes` musi pokryć wszystkie subklasy używane runtime — niedopisany typ → exception.
**Tags:** jackson, polymorphism, security

## Q-JX-011 [bloom: understand]
**Question:** Co to jest XSLT i jakie jest typowe zastosowanie?
**Model answer:** XSLT (Extensible Stylesheet Language Transformations) to język transformacji XML → XML/HTML/text. Sam jest dokumentem XML. Działa przez **template matching** — definiujesz templates pasujące do węzłów XPath, każdy template generuje fragment outputu. Wersje 1.0, 2.0, 3.0. **Przykład — transformacja XML pricelist do HTML table:**
```xml
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="/pricelist">
    <html><body><table>
      <xsl:apply-templates select="product"/>
    </table></body></html>
  </xsl:template>
  <xsl:template match="product">
    <tr>
      <td><xsl:value-of select="name"/></td>
      <td><xsl:value-of select="price"/></td>
    </tr>
  </xsl:template>
</xsl:stylesheet>
```
**Typowe zastosowania:**
- Konwersja XML feedów między systemami (ERP A ↔ ERP B z różnymi schematami).
- XML → HTML dla raportów.
- XML → XML reduction/filtering (np. usunięcie sensitive fields).
- Dokumentacja: XML → PDF przez XSL-FO.

**Engines:** Saxon (3.0 support, najmocniejszy), Xalan (1.0/2.0, Apache, w JDK domyślnie ale przestarzały). **Performance:** XSLT 1.0 dla małych, Saxon 3 z streaming dla dużych.

**Alternatywy:** w pricing engine zazwyczaj XSLT to legacy. Nowy kod to częściej imperative transformation w Javie/Groovym (więcej elastyczności, łatwiej testować). XSLT przeżywa gdzie business analysts pisali transformacje samodzielnie.

**Interview trap:** „XSLT jest zawsze deklaratywne" — w teorii. W praktyce ekspresywność zmusza do sztuczek (recursion zamiast loops, `xsl:variable` z workaroundem na immutability). XSLT 2.0+ z `xsl:function` jest cywilizowany.
**Tags:** xslt, xml, transformation

## Q-JX-012 [bloom: understand]
**Question:** Wyjaśnij co robi `@JsonInclude(JsonInclude.Include.NON_NULL)` i jakie są inne wartości.
**Model answer:** `@JsonInclude` kontroluje które pola są SERIALIZOWANE (deserializacja nieaffected). Wartości:
- **`ALWAYS`** (default) — zawsze, włącznie z null.
- **`NON_NULL`** — pomija pola null. Mniejszy JSON output, czytelniejszy.
- **`NON_EMPTY`** — pomija null + empty (pusta lista, pusty string, pusta mapa, Optional.empty()).
- **`NON_DEFAULT`** — pomija jeśli pole ma wartość domyślną typu (`int=0`, `boolean=false`, `null` dla obiektów). Useful dla minimal output.
- **`NON_ABSENT`** — pomija null i `Optional.empty()`/`AtomicReference(null)`.
- **`USE_DEFAULTS`** — używa default config z ObjectMappera.
- **`CUSTOM`** + `valueFilter` — custom logic.

**Przykład:**
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {
  public String name;     // serializowane jeśli !null
  public BigDecimal price;
  public String description; // serializowane jeśli !null
}

new Product(name="Foo", price=100, description=null)
// → {"name":"Foo","price":100}  (description pominięte)
```

**Globalnie:** `objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)`. Konflikt: per-field `@JsonInclude` przeważa nad globalnym.

**Trade-off:** mniejszy JSON (NON_NULL/NON_EMPTY) vs explicit kontrakt (klient widzi "null" zamiast nieistnienia pola). API design decision.

**Interview trap:** Dla collections — NON_EMPTY usuwa też pustą listę. Klient deserializing może oczekiwać empty list a dostaje brak pola → NPE w klientckim kodzie. Czasem ALWAYS lepiej niż NON_EMPTY dla kontraktu API.
**Tags:** jackson, serialization, annotations

## Q-JX-013 [bloom: understand]
**Question:** Czym różni się `XmlSlurper` od `XmlParser` w Groovym? (Patrz Q-GRV-012 ale z fokusu JSON/XML.)
**Model answer:** **`XmlSlurper`** zwraca `GPathResult` — leniwy wrapper. Nawigacja `xml.product.name` zwraca proxy, dopiero `.text()` lub `.toString()` materializuje wartość. Pamięciowo tańszy dla query (parser leniwie buduje tylko to co potrzebne). Mutability: ograniczona — XML jest read-mostly. **`XmlParser`** zwraca pełne drzewo `Node`. Każdy element jest mutowalny. Pamięć większa (cały DOM). **Performance dla read-only:** Slurper często wygrywa. **Dla modify + serialize:** Parser. **Składnia:**
- Slurper: `xml.product[0].@id` (atrybut przez `.@`), `xml.product*.name` (collect names of all products).
- Parser: `xml.product[0].'@id'` (string-style atrybut), `xml.product.collect { it.name.text() }`.

**Konwersja:** czasem chcesz Slurper-style query, ale potem mutować — `XmlNodePrinter` może serializować tree po Parserze, dla Slurperowych wyników jest `StreamingMarkupBuilder` pattern.

**Bezpieczeństwo:** od Groovy 2.5+ oba mają secure defaults dla XXE — nie rozwijają external entities, `disallow-doctype-decl=true`. Pre-2.5 wymagało manual config.

**Interview trap:** „Slurper jest immutable" — nie do końca. Jest read-oriented, ale ma `.replaceNode { ... }` i podobne. Modyfikacja jest niewygodna, dlatego Parser preferowany do edycji.
**Tags:** groovy, xml, slurper, parser

## Q-JX-014 [bloom: understand]
**Question:** Jak Jackson radzi sobie z BigDecimal i czemu to ma znaczenie w pricingu?
**Model answer:** Jackson serializuje `BigDecimal` zachowując precyzję — domyślnie do JSON jako number bez naukowej notacji. Deserializacja: jeśli pole jest `BigDecimal`, Jackson używa `parseBigDecimal()`. Ale: **JSON number nie ma gwarantowanej precyzji**. Klient JS deserializujący `{"price": 99.99}` w JS dostaje float 99.99 — który jest faktycznie `99.98999999999999...` w binarnej reprezentacji.

**W pricingu** musisz to rozwiązać. **Strategie:**
1. **String w JSON dla wartości pieniężnych:** `{"price": "99.99"}`. Jackson custom serializer:
   ```java
   @JsonSerialize(using = ToStringSerializer.class)
   private BigDecimal price;
   ```
   Plus: brak utraty precyzji u klienta. Klient parsuje string → BigDecimal/Decimal.js. Minus: clientcode musi wiedzieć że to liczba.
2. **Cents jako int:** `{"price_cents": 9999}`. Plus: prosty int, no precision loss in JS Number (do 2^53). Minus: trzeba wszędzie /100.
3. **`USE_BIG_DECIMAL_FOR_FLOATS` w deserializerze:** wymusza BigDecimal nawet z JSON number. Server-side OK.

**Best practice w produkcyjnym pricingu:** pieniądze jako struktura `{amount: "99.99", currency: "PLN"}` z amount jako string. Jasny kontrakt, brak ambiguity.

**Interview trap:** `Float`/`Double` w pricingu = bug. `0.1 + 0.2 = 0.30000000000000004`. BigDecimal albo cents-int. `BigDecimal.ROUND_HALF_UP` vs `ROUND_HALF_EVEN` (banker's rounding) — wybierz ROUND_HALF_UP dla pricingu (intuicyjne dla biznesu).
**Tags:** jackson, bigdecimal, pricing, precision

## Q-JX-015 [bloom: understand]
**Question:** Co to jest "billion laughs attack"?
**Model answer:** XML denial-of-service przez rekurencyjne entity. Atakujący wysyła:
```xml
<?xml version="1.0"?>
<!DOCTYPE lolz [
  <!ENTITY lol "lol">
  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
  ...
  <!ENTITY lol9 "&lol8;...">
]>
<lolz>&lol9;</lolz>
```
Każde entity jest 10x większe od poprzedniego. `&lol9;` = 10^9 = miliard razy "lol" → 3 GB RAM/CPU, OOM. **Obrona:**
- Parser config: `entity expansion limit` (Java: `-DentityExpansionLimit=64000`).
- Disable DTD entirely: `disallow-doctype-decl=true`.
- Limit document size przed parserem (max input bytes).
- Limit nesting depth (`maxOccur` / parser feature).

**Powiązane: XXE** (rozszerza external resource), **billion laughs** (rozszerza inline). Oba to klasy „entity expansion attacks". Java `secureProcessing` feature flagi blokuje większość.

**Interview trap:** „Disable DTD" wystarczy, jeśli aplikacja nie potrzebuje. Jeśli potrzebuje (legacy ERP feed z DTD) — limit expansion + size limit. Druga: „już blokuję XXE więc OK" — nie. Billion laughs nie wymaga external resource, atakuje rozwijanie wewnętrzne.
**Tags:** xml, security, dos

## Q-JX-016 [bloom: understand]
**Question:** Gdy konwertujesz XML do JSON (lub odwrotnie), jakie są fundamentalne różnice które utrudniają mapowanie?
**Model answer:** **XML i JSON nie są izomorficzne.** Różnice:
1. **Atrybuty vs zawartość:** XML ma `<elem attr="x">value</elem>`. JSON nie ma atrybutów. Konwencja: `{"elem": {"@attr": "x", "#text": "value"}}` lub flatten do `{"elem_attr": "x", "elem_text": "value"}`. Niejednoznaczne.
2. **Mixed content:** XML pozwala na `<p>Text <b>bold</b> more text</p>` — text przeplatany elementami. JSON nie ma natywnego sposobu na zachowanie kolejności text+elements bez array of objects ze wskaźnikami typu.
3. **Namespace:** XML ma `xmlns:foo="..."` z prefixami. JSON nie. Konwencje: prefiks w nazwie klucza (`"foo:price"`), albo separate `_namespaces` field.
4. **Order of elements:** XML jest sekwencyjne (kolejność istotna). JSON object teoretycznie nieuporządkowany (choć większość parserów zachowuje kolejność wstawień). Powtórzone elementy w XML (`<item/><item/>`) → JSON array `[...]`. Pojedyncze → object lub array? Niejednoznaczne.
5. **Komentarze:** XML ma `<!--  -->`. JSON standard nie.
6. **Typy:** XML wszystko to string (chyba że XSD daje typ). JSON ma 6 typów.
7. **Schema:** XSD vs JSON Schema — różne expressiveness.
8. **CDATA:** XML ma `<![CDATA[...]]>` dla raw content. JSON nie potrzebuje (escape w stringu).

**Praktyczne podejście:** nie próbuj generic XML↔JSON conversion. Map per use case — użyj data binding (JAXB dla XML, Jackson dla JSON) na wspólne POJOs, między nimi konwersja nie jest text-level ale model-level.

**Interview trap:** „Generic XML ↔ JSON converter" istnieje (org.json.XML.toJSONObject). Działa dla prostych przypadków, łamie się na atrybutach, mixed content, namespaces. Niewolno traktować jako universal solution.
**Tags:** xml, json, conversion, integration

---

## Q-JX-017 [bloom: apply]
**Question:** Sparsuj poniższy JSON w Javie z Jackson i zwróć Map<String, BigDecimal> (currency → max price). JSON: `{"products":[{"name":"A","price":100,"currency":"PLN"},{"name":"B","price":50,"currency":"PLN"},{"name":"C","price":120,"currency":"EUR"}]}`.
**Model answer:**
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

ObjectMapper mapper = new ObjectMapper();
mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

JsonNode root = mapper.readTree(json);
Map<String, BigDecimal> maxPrices = new HashMap<>();

for (JsonNode product : root.path("products")) {
    String currency = product.path("currency").asText();
    BigDecimal price = product.path("price").decimalValue();
    maxPrices.merge(currency, price, BigDecimal::max);
}
// maxPrices = {"PLN": 100, "EUR": 120}
```

**Wariant POJO-based (preferowany dla strict typed):**
```java
class ProductDto {
    public String name;
    public BigDecimal price;
    public String currency;
}
class ResponseDto {
    public List<ProductDto> products;
}

ResponseDto resp = mapper.readValue(json, ResponseDto.class);
Map<String, BigDecimal> maxPrices = resp.products.stream()
    .collect(Collectors.toMap(
        p -> p.currency,
        p -> p.price,
        BigDecimal::max
    ));
```

**Tree model vs data binding:** tree model (JsonNode) elastyczny dla unknown structure, data binding type-safe i czytelniejszy dla known structure. **`USE_BIG_DECIMAL_FOR_FLOATS`** wymusza BigDecimal w deserialization JSON number → unika precision loss przez double.

**Interview trap:** `path()` vs `get()`. `path()` zwraca `MissingNode` dla brakujących pól (chain-friendly), `get()` zwraca `null` (NPE risk). Druga: `merge()` w `HashMap` jest atomic dla single-thread, ale dla concurrent `ConcurrentHashMap.merge()`.
**Tags:** jackson, json, parsing, java, pricing

## Q-JX-018 [bloom: apply]
**Question:** Wygeneruj XML cennika z mapy w Javie używając JAXB (lub bez biblioteki).
**Model answer:**
**Z JAXB:**
```java
@XmlRootElement(name = "priceList")
class PriceList {
    @XmlElement(name = "item")
    public List<Item> items = new ArrayList<>();
}

class Item {
    @XmlAttribute public String country;
    @XmlElement public BigDecimal price;
}

PriceList list = new PriceList();
list.items.add(new Item() {{ country = "PL"; price = new BigDecimal("100"); }});
list.items.add(new Item() {{ country = "DE"; price = new BigDecimal("120"); }});

JAXBContext ctx = JAXBContext.newInstance(PriceList.class);
Marshaller m = ctx.createMarshaller();
m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
m.marshal(list, System.out);
```
Output:
```xml
<priceList>
  <item country="PL"><price>100</price></item>
  <item country="DE"><price>120</price></item>
</priceList>
```

**Bez biblioteki, z `XMLStreamWriter` (StAX):**
```java
XMLOutputFactory factory = XMLOutputFactory.newFactory();
StringWriter sw = new StringWriter();
XMLStreamWriter writer = factory.createXMLStreamWriter(sw);
writer.writeStartDocument();
writer.writeStartElement("priceList");
for (Map.Entry<String, BigDecimal> e : prices.entrySet()) {
    writer.writeStartElement("item");
    writer.writeAttribute("country", e.getKey());
    writer.writeStartElement("price");
    writer.writeCharacters(e.getValue().toPlainString());
    writer.writeEndElement(); // price
    writer.writeEndElement(); // item
}
writer.writeEndElement(); // priceList
writer.writeEndDocument();
writer.close();
```

**JAXB plusy:** annotations-driven, mniej kodu, bidirectional (read+write). **JAXB minusy:** od Java 11+ jako osobna dependency (`jakarta.xml.bind`), warm-up overhead. **StAX plusy:** lekki, streaming-friendly dla wielkich plików, full control. **StAX minusy:** verbose kod.

**Interview trap:** JAXB w Java 11+ — usunięte z core JDK. Trzeba `javax.xml.bind:jaxb-api` lub `jakarta.xml.bind:jakarta.xml.bind-api` jako dep. Druga: `BigDecimal.toString()` może dawać scientific notation (`1E+2`); `toPlainString()` zawsze prosty zapis.
**Tags:** xml, jaxb, stax, java, pricing

## Q-JX-019 [bloom: apply]
**Question:** Dostajesz XML feed cennika, ale niektóre elementy mogą być null lub brakować. Pokaż jak bezpiecznie sparsować w Groovym (XmlSlurper).
**Model answer:**
```groovy
import groovy.xml.XmlSlurper

def xmlString = '''
<pricelist>
  <product id="1">
    <name>Foo</name>
    <price>100.00</price>
    <currency>PLN</currency>
  </product>
  <product id="2">
    <name>Bar</name>
    <!-- price missing -->
    <currency>EUR</currency>
  </product>
  <product id="3">
    <name></name>
    <price>0.00</price>
  </product>
</pricelist>
'''

def xml = new XmlSlurper().parseText(xmlString)

def products = xml.product.collect { p ->
  [
    id: p.@id.toString() ?: null,
    name: p.name.text()?.trim() ?: null,
    price: p.price.size() > 0 && p.price.text()?.isNumber() 
            ? new BigDecimal(p.price.text()) 
            : null,
    currency: p.currency.text() ?: 'PLN'  // default
  ]
}

// Filter validne
def valid = products.findAll { it.id && it.name && it.price != null }
```
**Kluczowe sprawdzenia:**
- `p.price.size() > 0` — sprawdza czy element istnieje (Slurper zwraca pustą GPathResult dla brakujących).
- `.text()?.trim() ?: null` — pusty string traktuj jak null.
- `.isNumber()` (Groovy extension method) — sprawdź czy to liczba przed konwersją.
- `?: defaultValue` — default dla brakujących pól (np. waluta).

**Walidacja na wyjściu:** filter `findAll` odrzuca rekordy bez minimalnych danych. **Logging missing:** w pricingu chcesz wiedzieć ile rekordów odrzucono (audit log).

**Alternative: XSD validation pre-parse:** zamiast defensive parsing, walidacja przeciw XSD przed parsowaniem. Wszystko co przejdzie ma znane elementy. Trade-off: XSD validation slower; missing fields w XSD muszą być `minOccurs=0`.

**Interview trap:** „Wartość 0 a brak" — `p.price.text()` zwraca pusty string dla brakującego elementu, ale `0` dla `<price>0</price>`. Subtle difference. Druga: `p.@nonexistent.toString()` zwraca `""` (Slurper zwraca empty Attribute), nie null. Stąd `?: null` na koniec.
**Tags:** xml, groovy, slurper, defensive, pricing

## Q-JX-020 [bloom: apply]
**Question:** Klient REST wysyła JSON z zagnieżdżoną strukturą zamówienia. Walidacja: każdy item musi mieć `quantity > 0`, `total = sum(items.price * quantity)` musi się zgadzać. Implementacja w Spring Boot z Jackson + Bean Validation.
**Model answer:**
```java
public class OrderDto {
    @NotNull
    private Long customerId;
    
    @NotEmpty
    @Valid
    private List<OrderItemDto> items;
    
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal total;
    
    @AssertTrue(message = "Total must equal sum of (price * quantity)")
    public boolean isTotalValid() {
        if (items == null || total == null) return true; // let other validators handle
        BigDecimal computed = items.stream()
            .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return computed.compareTo(total) == 0;
    }
    // getters/setters
}

public class OrderItemDto {
    @NotNull
    private Long productId;
    
    @Min(1)
    private Integer quantity;
    
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal price;
    // getters/setters
}

@RestController
public class OrderController {
    @PostMapping("/orders")
    public ResponseEntity<?> create(@Valid @RequestBody OrderDto order) {
        // service call
    }
}
```

**`@Valid` na endpoint method param** uruchamia walidację. **`@Valid` na liście** propaguje do elementów listy. **`@AssertTrue`** custom rule — metoda boolean musi zwracać true (Bean Validation specific). 

**Globalna obsługa błędów:**
```java
@ControllerAdvice
public class ValidationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getAllErrors().stream()
            .map(err -> err.getDefaultMessage())
            .collect(Collectors.toList());
        return ResponseEntity.unprocessableEntity()
            .body(Map.of("error", "validation_failed", "details", errors));
    }
}
```
Zwraca 422 Unprocessable Entity z listą problemów.

**Custom validator dla complex rules** zamiast `@AssertTrue`:
```java
@Constraint(validatedBy = TotalConsistencyValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TotalConsistent { ... }

public class TotalConsistencyValidator implements ConstraintValidator<TotalConsistent, OrderDto> {
    public boolean isValid(OrderDto o, ConstraintValidatorContext ctx) { ... }
}
```
Bardziej reusable niż `@AssertTrue`.

**Interview trap:** `BigDecimal.equals()` ≠ `compareTo() == 0`. `new BigDecimal("1.00").equals(new BigDecimal("1.0"))` = false (różny scale), `compareTo() == 0` = true. Dla porównań wartości w pricingu — ZAWSZE `compareTo`. Druga: walidacja `total = sum` jest wrażliwa na precision; jeśli mnożenie generuje long decimal — porównaj z tolerancją albo zaokrąglij oba do scale 2.
**Tags:** json, validation, bean-validation, spring, pricing

## Q-JX-021 [bloom: apply]
**Question:** Użyj XmlSlurper i XPath-style do wyciągnięcia z XML feedu wszystkich produktów z kategorii "Electronics" i ceną w PLN > 500.
**Model answer:**
```groovy
def xml = '''
<catalog>
  <product id="1" category="Electronics">
    <name>Phone</name>
    <prices>
      <price currency="PLN">800</price>
      <price currency="EUR">200</price>
    </prices>
  </product>
  <product id="2" category="Books">
    <name>Novel</name>
    <prices><price currency="PLN">50</price></prices>
  </product>
  <product id="3" category="Electronics">
    <name>Tablet</name>
    <prices>
      <price currency="PLN">300</price>
      <price currency="USD">80</price>
    </prices>
  </product>
</catalog>
'''

def doc = new XmlSlurper().parseText(xml)

def matches = doc.product.findAll { p ->
  p.@category.toString() == 'Electronics' &&
  p.prices.price.find { it.@currency == 'PLN' && it.toBigDecimal() > 500 }
}.collect { p ->
  [
    id: p.@id.toString(),
    name: p.name.text(),
    pln_price: p.prices.price.find { it.@currency == 'PLN' }.toBigDecimal()
  ]
}
// matches = [[id:"1", name:"Phone", pln_price:800]]
```

**Mechanika:**
- `doc.product.findAll { ... }` — filter top-level products.
- `p.@category.toString() == 'Electronics'` — atrybut category. `.toString()` materializuje (bo Slurper zwraca proxy).
- `p.prices.price.find { ... }` — szuka pierwszego matching price.
- `it.toBigDecimal() > 500` — konwersja text content na BigDecimal i porównanie.

**Bardziej "XPath" Groovy syntax**: `doc.'**'.findAll { ... }` (`**` to descendant-or-self). Ale dla prostego query plain GPath jest czytelniejszy.

**Z XPath direct (Java-style w Groovy):**
```groovy
import javax.xml.xpath.XPathFactory

def docBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
def domDoc = docBuilder.parse(new ByteArrayInputStream(xml.bytes))
def xpath = XPathFactory.newInstance().newXPath()
def nodes = xpath.evaluate("//product[@category='Electronics']", domDoc, javax.xml.xpath.XPathConstants.NODESET)
```
Verbose, ale prawdziwy XPath (jeśli zespół już go zna z Java).

**Interview trap:** `find` (Slurper) returns `GPathResult` (może być empty). Sprawdzenie `if (result)` w Groovy truth: empty GPathResult jest falsy, więc `find` zwraca empty → cała koniunkcja false. Działa, ale subtle. Druga: `toBigDecimal` na pustym text → NumberFormatException. Defense: `it.text() && it.toBigDecimal() > 500`.
**Tags:** xml, groovy, slurper, xpath, pricing

## Q-JX-022 [bloom: apply]
**Question:** Twój system odbiera JSON z zewnętrznego ERP, gdzie pole `price` może przyjść jako number (`100.50`) albo string (`"100.50"`). Custom Jackson deserializer.
**Model answer:**
```java
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class FlexibleBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonToken token = p.currentToken();
        switch (token) {
            case VALUE_NUMBER_FLOAT:
            case VALUE_NUMBER_INT:
                return p.getDecimalValue();
            case VALUE_STRING:
                String s = p.getText().trim();
                if (s.isEmpty()) return null;
                try {
                    return new BigDecimal(s);
                } catch (NumberFormatException e) {
                    throw new InvalidFormatException(p, "Cannot parse '" + s + "' as BigDecimal", s, BigDecimal.class);
                }
            case VALUE_NULL:
                return null;
            default:
                throw new InvalidFormatException(p, "Unexpected token: " + token, null, BigDecimal.class);
        }
    }
}

// Użycie per pole:
public class ProductDto {
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal price;
}

// Lub globalnie w ObjectMapper:
SimpleModule module = new SimpleModule();
module.addDeserializer(BigDecimal.class, new FlexibleBigDecimalDeserializer());
mapper.registerModule(module);
```

**Pełna kontrola nad parsowaniem.** Można dodać:
- Logging suspicious format (number z quotes).
- Lokalizacja: ERP z FR daje `100,50` (przecinek) → przekonwertuj.
- Currency parsing: `"$100.50"` → strip prefix.

**Why not just `JsonNode.decimalValue()`?** Tree model jest jeden ze sposobów. Custom deserializer integruje się z data binding (POJOs), bardziej type-safe.

**Trade-off:** elastyczność vs strict contract. **Strict** (reject malformed) jest preferred dla pricing (bug w ERP feedzie powinien być widoczny natychmiast, nie cicho akceptowany). Defensive deserialization zachowuje system działający kosztem invisible bugs.

**Interview trap:** `p.getDoubleValue()` zamiast `getDecimalValue()` — utrata precyzji. Już samo poproszenie Jacksona o decimal jest poprawne; alternatywa przez double rozwiewa precyzję.
**Tags:** jackson, deserialization, custom, pricing

## Q-JX-023 [bloom: apply]
**Question:** Zaimplementuj walidację dokumentu XML przeciw XSD w Javie.
**Model answer:**
```java
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ErrorHandler;

import java.io.File;
import java.util.*;

public class XmlValidator {
    public static class ValidationResult {
        public boolean valid;
        public List<String> errors = new ArrayList<>();
    }

    public static ValidationResult validate(File xmlFile, File xsdFile) {
        ValidationResult result = new ValidationResult();
        result.valid = true;
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Security: disable external schema lookup
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Schema schema = factory.newSchema(xsdFile);
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            
            validator.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) { result.errors.add("WARN: " + e.getMessage()); }
                public void error(SAXParseException e) { 
                    result.errors.add("ERROR line " + e.getLineNumber() + ": " + e.getMessage()); 
                    result.valid = false;
                }
                public void fatalError(SAXParseException e) { 
                    result.errors.add("FATAL: " + e.getMessage()); 
                    result.valid = false;
                }
            });

            validator.validate(new StreamSource(xmlFile));
        } catch (Exception e) {
            result.valid = false;
            result.errors.add("VALIDATION FAILED: " + e.getMessage());
        }
        return result;
    }
}
```

**Kluczowe punkty:**
- `SchemaFactory` per W3C_XML_SCHEMA_NS_URI dla XSD.
- **Security:** `ACCESS_EXTERNAL_DTD/SCHEMA = ""` — blokuje XXE-style ataki.
- `ErrorHandler` — niedomyślnie błędy są tylko thrownięte; chcemy zebrać wszystkie, nie failure-fast.
- `Validator.validate(Source)` — Source może być StreamSource (file/stream), DOMSource, SAXSource.

**Performance dla dużych XML:**
- StreamSource używa SAX-style streaming — nie ładuje całego dokumentu.
- Schema object jest immutable & thread-safe — load raz, reuse w wielu validatorach (Validator NIE jest thread-safe — instancja per thread).

**JAXB integration:** unmarshaller też może walidować przy unmarshal (`unmarshaller.setSchema(schema)`).

**Interview trap:** Schema bez disabled external resources → XXE risk. Druga: bez ErrorHandler validator throws na pierwszym błędzie — żeby zebrać wszystkie, custom handler.
**Tags:** xml, xsd, validation, java, security

## Q-JX-024 [bloom: apply]
**Question:** Konwertuj duży XML feed (1 GB) z systemu ERP do bazy danych. Streaming approach.
**Model answer:**
```java
import javax.xml.stream.*;
import javax.xml.bind.*;
import java.io.FileInputStream;
import java.sql.*;

XMLInputFactory factory = XMLInputFactory.newInstance();
factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
factory.setProperty(XMLInputFactory.SUPPORT_DTD, false); // security

XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream("feed.xml"));
JAXBContext jaxb = JAXBContext.newInstance(Product.class);
Unmarshaller unmarshaller = jaxb.createUnmarshaller();

Connection conn = dataSource.getConnection();
conn.setAutoCommit(false);
PreparedStatement ps = conn.prepareStatement(
    "INSERT INTO product (id, name, price, currency, country) VALUES (?, ?, ?, ?, ?) " +
    "ON CONFLICT (id) DO UPDATE SET price = EXCLUDED.price, currency = EXCLUDED.currency"
);

int batchSize = 0;
while (reader.hasNext()) {
    int event = reader.next();
    if (event == XMLStreamConstants.START_ELEMENT && "product".equals(reader.getLocalName())) {
        Product p = unmarshaller.unmarshal(reader, Product.class).getValue();
        ps.setLong(1, p.getId());
        ps.setString(2, p.getName());
        ps.setBigDecimal(3, p.getPrice());
        ps.setString(4, p.getCurrency());
        ps.setString(5, p.getCountry());
        ps.addBatch();
        batchSize++;
        
        if (batchSize >= 1000) {
            ps.executeBatch();
            conn.commit();
            batchSize = 0;
        }
    }
}
if (batchSize > 0) {
    ps.executeBatch();
    conn.commit();
}
ps.close();
reader.close();
conn.close();
```

**Mechanika:** StAX streamuje XML; gdy hit `<product>` start, JAXB unmarshal pojedynczego elementu (nie całego dokumentu) → POJO. Insert do bazy w batch-ach po 1000. Commit per batch — minimizing transaction size, ale wciąż transactional.

**Optymalizacje:**
- **Skip unrelated content:** `reader.next()` szybciej niż unmarshalling jeśli nie potrzebujesz.
- **Multithreading:** parsing w jednym wątku, batch inserts w drugim (BlockingQueue jako bridge). Boost ~2-3x.
- **PostgreSQL COPY:** zamiast `INSERT`, w batch-style `COPY FROM STDIN` jest 5-10x szybszy. Wymaga building CSV w pamięci albo PG `copyAPI`.
- **Disable indexes** na docelowej tabeli przed importem, rebuild po (jak Q-SQL-026).

**Resilience:**
- Idempotent insert (`ON CONFLICT`) — restart import po crash bez duplikatów.
- Resume from checkpoint: zapisuj ostatni `product.id` w tabeli `import_state`, restart-uj od następnego.
- Error handling: log błędne rekordy do `import_errors`, kontynuuj. Po imporcie raport ile fail.

**Memory profile:** 1 GB XML na heap ~150-300 MB (zależnie od reader buffer size). Bez StAX/streaming → ~5 GB RAM (DOM).

**Interview trap:** „Commit po każdym insercie" → 1M COMMIT-ów = wolno (każdy fsync). „Commit raz na końcu" → transakcja na 1M wierszy może lockować, OOM, jeśli error → wszystko od początku. Compromise: batches of 1000-10000.
**Tags:** xml, streaming, jaxb, batch, pricing

---

## Q-JX-025 [bloom: analyze]
**Question:** Twój system konsumuje XML feed z ERP, ale jego format zmienia się raz na rok i każda zmiana wymaga 2-tygodniowego sprintu. Co byś zmienił?
**Model answer:** Diagnoza: tightly coupled XML schema z business logic. **Strategie poprawy:**

1. **Anti-corruption layer (DDD pattern):** osobny moduł odpowiedzialny za parsing XML i konwersję na **kanoniczny model wewnętrzny**. Reszta systemu zna tylko canonical model. Zmiana XML → tylko ACL się zmienia, business logic intact. Plus: izolacja zmian. Minus: duplication kodu (XML schema + canonical schema).

2. **Schema versioning:** ERP wysyła XML z version markerem (`<feed version="2.5">`). System ma per-version parsers. Stara wersja: `ParserV2_4`, nowa: `ParserV2_5`. Nowa wersja wsparta od release, stara wciąż działa na backward compat. Plus: smooth migration. Minus: divergence kodu po wielu wersjach.

3. **Tolerant reader pattern:** parser czyta optymistycznie — pomija nieznane elementy, używa `Optional` dla missing fields, akceptuje zmiany typów (string ↔ number flexibility). Mniej crashy przy nowej wersji.

4. **XSLT-driven transformation:** zamiast hard-coded parser, ERP-specific XSLT przekonwertowuje feed na canonical XML, parsing canonical XML jest stable. Zmiana ERP = zmiana XSLT, nie kodu. Plus: XSLT może być zmieniany przez integration team, nie devs. Minus: XSLT debugging gorsze niż kod.

5. **Event-driven approach:** zamiast batch XML feedu, ERP publikuje eventy (Kafka topic) per zmiana. Schema events kontrolowane przez schema registry (Avro/Protobuf). Wersjonowanie wbudowane w protokół. Plus: real-time. Minus: zmiana architektury ERP — zazwyczaj poza zasięgiem.

6. **Contract testing:** test suite weryfikujący kontrakt — pokrywa kazdy element XSD, missing field handling, error cases. Każda zmiana ERP — failing test wskazuje dokładnie co się zmieniło. Plus: szybsza identyfikacja co poprawić. Minus: trzeba inwestować w test infrastructure.

**Decyzja praktyczna:** ACL + tolerant reader + contract tests. To kombinacja chroni system przed coupling bez przepisywania architektury ERP. Schema versioning jeśli ERP wysyła version tag. XSLT jeśli zespół jest XSLT-friendly. Eventy jeśli ERP się otwiera na pub-sub.

**Interview trap:** „Po prostu update schemę XSD" — symptomatic fix, nie root cause. Problem to tight coupling. Druga: rebuilding everything from scratch — overengineering, gdy ACL może być dorzucony incrementally.
**Tags:** xml, integration, architecture, decision, ddd

## Q-JX-026 [bloom: analyze]
**Question:** JSON vs XML dla nowego API integracji z partnerami biznesowymi. Co wybierasz i dlaczego?
**Model answer:** **JSON** — domyślny wybór dla nowego API w 2026, chyba że specific reason for XML. Argumenty:

**Za JSON:**
- Lekki (mniejszy payload niż XML, brak `</closingTag>` overhead).
- Native dla web (JSON.parse w JS, fetch trywialny).
- Większość modern languages ma first-class wsparcie (Java/Jackson, Python/json, Go/encoding/json, etc.).
- Łatwiejszy do debugowania (curl + jq).
- JSON Schema wystarcza dla most validation needs.
- REST + JSON = de facto standard.

**Za XML (kiedy to ma sens):**
- **Legacy partners** — niektórzy wciąż używają SOAP, EDI, B2B XML standards (UBL, RosettaNet, GS1).
- **Mixed content** — dokumenty z text-flow przeplatanym tagami (np. dokumenty XML, XHTML). JSON nie pasuje natywnie.
- **Complex schema validation** — XSD jest expressive (np. constraint że `endDate >= startDate` w XSD 1.1).
- **Compliance** — niektóre regulacje (e-invoicing UBL EU) wymagają XML.
- **Industry-specific** — ERP integrations często XML-first (SAP IDoc, Oracle XML schemas).
- **Digital signatures** — XMLDSig jest mature, JSON ma JWS ale newer.

**Hybrid strategy:** wystawić oba formaty — `Accept: application/json` zwraca JSON, `Accept: application/xml` XML. Spring MessageConverters obsługują z box. Plus: pojedynczy API obsługuje different consumers. Minus: dwukrotny test surface.

**Decyzja:** dla nowego B2B API z partnerami — **JSON pierwsze**. XML jako fallback gdy partner się upiera (legacy ERP integrations) — z explicit endpoint lub negotiation. Dokumentuj w OpenAPI.

**W pricingu specyficznie:** internal services (microservices) → JSON. Zewnętrzne ERP → cokolwiek partner ma. Nowi klienci → JSON-only zazwyczaj akceptują.

**Interview trap:** „XML jest standard B2B" — nie w 2026. Wiele branży ma JSON-first. „SOAP jest dead" — w niektórych branżach (banking, insurance) wciąż żyje. Nie generalizuj.
**Tags:** json, xml, integration, decision, b2b

## Q-JX-027 [bloom: analyze]
**Question:** Twój API zwraca JSON 1 MB per request, klient narzeka na latency. Co zrobisz?
**Model answer:** Diagnoza first — czy 1 MB jest faktycznie potrzebny? Strategie redukcji:

1. **Pagination** — zamiast całej listy, strony po 50-100. Klient pobiera incrementalnie. Plus: szybkie initial load. Minus: nie ma sensu jeśli klient i tak potrzebuje wszystkiego.

2. **Field selection / sparse fieldsets:** `GET /products?fields=id,name,price` zamiast pełnego DTO. JSON:API to ma standardowo, GraphQL natywnie. Reduces payload znacząco.

3. **Compression — gzip/brotli:** `Content-Encoding: gzip` na response. JSON kompresuje bardzo dobrze (5-10x). Większość frameworków robi automatycznie (Spring `@EnableCompression`). Klient: `Accept-Encoding: gzip, br`. Zazwyczaj first thing to enable.

4. **HTTP/2 lub HTTP/3:** multipleksuje wiele requestów na jednym connection, header compression (HPACK). Niewymaga zmian w aplikacji, tylko w infrastruktrze (load balancer, web server). HTTP/3 (QUIC) jeszcze better dla mobile.

5. **Conditional GET — ETag + If-None-Match:** klient cachuje, dla powtórzonych requestów dostaje 304 Not Modified bez body. Network round trip + status code, body skipped.

6. **Streaming JSON:** dla bardzo dużych zbiorów (np. eksport cennika) — ndjson (newline-delimited JSON) lub Server-Sent Events. Klient processuje stream-style, brak full buffer in memory.

7. **Binary formats:** Protobuf, MessagePack, CBOR są 30-50% mniejsze niż JSON dla tego samego data. Trade-off: trudniejsze debug, wymaga generated code. Sensible dla service-to-service, nie public API.

8. **Cache na CDN:** dla cache-able responses (publiczne pricelists), CDN serwuje, backend zero load. `Cache-Control: public, max-age=300`.

9. **Optymalizacja na poziomie serializacji:** Jackson `WRITE_BIGDECIMAL_AS_PLAIN` (mniej bytes), `@JsonInclude(NON_NULL)` (skip null fields), nazwy pól krótsze (`p` zamiast `price` — mikrooptymalizacja, tylko jeśli desperate).

**Decyzja krokami:**
1. Włącz gzip — to free win.
2. Włącz HTTP/2.
3. Sprawdź czy klient potrzebuje pełnego payloadu — pewnie nie. Sparse fieldsets / pagination.
4. ETag dla repeated reads.
5. Streaming dla bardzo dużych zbiorów.
6. Binary protocol dla service-to-service heavy traffic.

**Pricing-specyficznie:** pricelist są często cache-able + sparse fields-friendly. CDN + gzip wykonują 90% pracy.

**Interview trap:** „Microbenchmark Jackson vs Gson" — to są pico-second differences. Real wins: gzip + caching + pagination. Drugi błąd: „zmień wszystko na Protobuf" — overkill jeśli problem jest 5MB JSON sending without compression.
**Tags:** json, performance, compression, optimization

## Q-JX-028 [bloom: analyze]
**Question:** Pricing platforma używa XML feedów. Zespół chce przejść na JSON. Migration strategy?
**Model answer:** **Phase'd migration with parallel support.**

**Faza 1 — assessment (1-2 sprinty):**
- Inventaryzacja: które XML feedy, jakie schemas, kto jest konsumentem/producentem.
- Klasyfikacja: legacy ERP partners (XML staje), wewnętrzne services (JSON migrowalne), API publiczne (JSON pożądane).
- Risk per system: jak duża zmiana, co może pójść źle, fallback.

**Faza 2 — internal services first:**
- Microservices między sobą — najłatwiejsze do migracji (kontrolowane endpoints).
- Add JSON endpoints obok XML. Spring `@PostMapping(consumes = {"application/xml", "application/json"})`.
- Zmiana konsumentów po jednym, monitoring per format usage.
- Po wszystkich migrated → deprecate XML wewnętrznie.

**Faza 3 — public API:**
- New endpoints JSON-only (`/api/v2/...`). Old `/api/v1` (XML) deprecated z sunsetowaniem (3-6 miesięcy notice).
- Documentation update (OpenAPI z JSON schemas).
- SDK generation dla klientów w popularnych językach.

**Faza 4 — partner integrations (najwolniejsza):**
- Legacy ERP partnerzy często wymagają XML — zachowaj w ACL (anti-corruption layer).
- New partners — JSON only przez umowę.
- Konwerter XML ↔ JSON na warstwie integracyjnej (XSLT lub kod) dla legacy partnerów.

**Tooling decyzje:**
- Jackson zamiast JAXB jako default (ale zatrzymaj JAXB w ACL dla XML legacy).
- JSON Schema zamiast XSD dla nowych kontraktów.
- OpenAPI jako spec format.

**Risks:**
- **Precision** — XML często używa string-based numbers; JSON może utracić precyzję bez care. Decyzja: BigDecimal as string in JSON, lub Jackson `USE_BIG_DECIMAL_FOR_FLOATS`.
- **Namespaces** — XML ma; JSON nie. Mapping przez prefiksy/podstruktury.
- **Schema validation** — XSD zostawia ślad; JSON Schema ekspertyza w zespole?
- **Tooling change** — XSLT staff potrzebuje retrain.

**Komunikacja:**
- Roadmap dla zespołu i partnerów.
- Migration guide — przykłady before/after dla każdego endpointu.
- Performance benefits showcased (mniejszy payload, szybciej parsowanie).

**Decyzja krytyczna:** zachować XML support tam gdzie partnerzy nie migrują (industries: banking, ERP integrations). Forced migration ZE strony pricing engine może popsuć relacje biznesowe.

**Interview trap:** „Big bang migration" — disaster. Partnerzy mają własne timeline'y, tooling, regulacje. Phased approach z parallel support to standard approach.
**Tags:** migration, json, xml, architecture, decision

## Q-JX-029 [bloom: analyze]
**Question:** Strict vs lenient parsing JSON — kiedy które?
**Model answer:** **Strict** — odrzucaj wszystko co nie pasuje do schematu. **Lenient** — akceptuj wariacje, defaulty dla missing, ignoruj unknown.

**Strict plusy:**
- **Bug detection** — zmiana w producencie (np. `price` → `amount`) wybucha natychmiast, nie cicho.
- **Contract enforcement** — kontrakt jest jeden, łamiący nie przejdzie.
- **Security** — unknown fields mogą być atakiem (np. extra fields wymyślone przez attackera). Jackson `FAIL_ON_UNKNOWN_PROPERTIES` jest defensive.
- **Documentation truth** — schema jest jedyna prawda.

**Strict minusy:**
- **Brittle** — każda mała zmiana w producencie crashuje konsumenta. Coupling.
- **Hard to evolve** — backward compat trudny.

**Lenient plusy:**
- **Tolerance** — minor schema changes (dodatkowe pole) nie psują flow.
- **Postel's law:** „be conservative in what you do, be liberal in what you accept" — robust system tolerates noise.
- **Easier evolution** — można dodawać pola bez psucia konsumentów.

**Lenient minusy:**
- **Hidden bugs** — typo w polu (`prce` zamiast `price`) może być cicho zignorowane, leading to wrong calculations.
- **Schema drift** — z czasem actual data diverguje od documented schema.
- **Security** — extra fields mogą być smuggled in (mass assignment vulnerability — known PHP/Rails issue, też możliwe w Java).

**Reguła:**
- **Internal services (you control both ends)** — STRICT. Schema versioned in git, contract tests, fail fast on drift.
- **External partners (legacy ERP, third-party)** — LENIENT (tolerant reader). Może być noise, niejasne pola, missing optional fields. Don't crash, log + continue.
- **Public API (you accept input)** — STRICT na typach (`@Valid` Bean Validation, JSON Schema), LENIENT na unknown fields (ignore, log warning).

**Jackson knobs:**
- `FAIL_ON_UNKNOWN_PROPERTIES` — strict on unknown fields (deserialization).
- `FAIL_ON_NULL_FOR_PRIMITIVES` — strict null handling.
- `ACCEPT_SINGLE_VALUE_AS_ARRAY` — lenient: `"foo"` parsuje jako `["foo"]`.
- `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT` — lenient: `""` → null.

**Decyzja w pricingu:** strict dla outgoing API (klienci muszą respektować nasz kontrakt), lenient dla incoming z partnerów (anti-corruption layer absorbuje weirdness).

**Interview trap:** „Strict bo bezpieczniej" — niekoniecznie. Tight coupling jest też zagrożeniem dla dostępności (jedna zmiana w producencie kładzie konsumenta). Postel's law: liberal in input, conservative in output.
**Tags:** json, parsing, strict, lenient, robustness

## Q-JX-030 [bloom: analyze]
**Question:** Twój monitoring pokazuje że parsowanie XML feedów zajmuje 80% CPU aplikacji. Diagnoza i poprawa.
**Model answer:** Diagnoza pierwsza:

1. **Profiluj** — async-profiler, JFR. Zobacz exact stack trace top CPU consumers. Jackson? JAXB? StAX? Custom code?
2. **Sample sizes** — jak duże są feedy? Ile per minutę? 80% CPU może być realnie konieczne dla volume, lub może być problem implementation.
3. **Memory profile** — jeśli GC dominate, może problem to allocation pressure (DOM-style buffering), nie raw parsing.

**Możliwe przyczyny i mitygacje:**

**A) DOM parsing wielkich plików:** każda iteracja ładuje pełny tree → GC churn → CPU. Solution: streaming (StAX) — patrz Q-JX-009. Może spaść z 80% do 30% CPU.

**B) Bez schema validation już parsowanie jest expensive (XSD validation jest O(input × schema complexity)):** jeśli walidacja nie jest critical na hot path — przenieś validation do offline / pre-flight. Lub validate sample (10% inputów random).

**C) Reflection w JAXB:** JAXB unmarshalling używa reflection — slower than direct code. Solution: 
- Cache `JAXBContext` i `Unmarshaller` (są thread-safe per context, expensive to create).
- Przejdź na MOXy (`org.eclipse.persistence.jaxb.JAXBContextFactory`) — szybszy implementation.
- Compile-time generated parser (np. zamiast JAXB, code-gen przez `XJC` z optimizations).

**D) String concatenation / GString allocation:** XmlSlurper w Groovy może być expensive ze względu na lazy proxy and Map-based access. Jeśli code path is hot, przepisz na `XMLStreamReader` direct.

**E) Repeated parsing tego samego XML:** cache parsed result. Jeśli feed pricelist repeated parsed (np. każdy request od nowa), → cache (Caffeine) z TTL = feed update frequency.

**F) Threading:** XML parsing jest CPU-bound, jeden CPU per parsing. Jeśli masz wiele feedów paralelnie — parallel processing przez ThreadPool może zwiększyć throughput (CPU usage % staje się 80% × N cores zamiast 80% × 1 core).

**G) Zmiana formatu:** czy XML jest faktycznie potrzebny? Jeśli można negocjować z producentem — Protobuf/Avro/Parquet są 10-50x szybsze do parsowania. Big architectural change, ale long-term win.

**H) Hardware:** możesz mieć właśnie undersized cluster. Mierz before-after — czasem dorzucenie cores tańsze niż dni inżyniera.

**Quick wins (sprawdź pierwsze):**
1. Cache `JAXBContext` (jeśli używasz JAXB) — tworzenie jest expensive.
2. Disable XSD validation w hot path jeśli nie krytyczna.
3. Switch DOM → StAX dla wielkich plików.
4. Compress feed (gzip) — paradoksalnie ZMIENIEJSZA CPU bo less I/O wait.

**Interview trap:** „CPU 80% to znak że trzeba rewrite" — nie. Najpierw diagnose, potem fix najtańsze. Często 5-line config change daje 50% improvement. Drugi: ignorowanie I/O wait — XML parsing czasem czeka na disk/network, CPU% nie odzwierciedla wąskiego gardła.
**Tags:** xml, performance, profiling, optimization

## Q-JX-031 [bloom: analyze]
**Question:** Code review: programista parsuje JSON ręcznie regex-em zamiast używać Jacksona, mówiąc „Jackson to overhead". Reaguj.
**Model answer:** **Reaguj merytorycznie, nie persona-driven.** Argumenty przeciwko regex JSON parsing:

1. **JSON nie jest regular language** — formalnie nie da się sparsować generic JSON regexem (zagnieżdżenia, escape sequences, edge cases). Każdy „regex parser" działa na ograniczonych input shapes — łamie się na nieoczekiwanych.

2. **Edge cases:**
   - Escape sequences w stringach (`"foo\"bar"`, `"fooAbar"`).
   - Whitespace tolerance (`{"a":1}` vs `{ "a" : 1 }`).
   - Numbers (scientific notation `1e10`, negative, decimal).
   - Unicode keys.
   - Nested arrays/objects.
   Każdy edge case = bug w regex.

3. **Performance:** Jackson jest **mocno** zoptymalizowany — streaming parser, native code paths, JIT-friendly. „Overhead" Jacksona jest zazwyczaj <1ms. Regex może być wolniejszy dla complex JSON. „Overhead" to często hand-wavy claim bez measurement.

4. **Maintenance:** regex code „wygląda jak konieczność" — kto będzie go utrzymywał? Co jak format JSON się zmieni? Jackson update = bump version. Regex update = przepisanie + testy.

5. **Security:** regex może być vulnerable na ReDoS (catastrophic backtracking). Jackson ma audited security stance.

**Pytania do programisty:**
- „Jakim measurement udowodniłeś że Jackson jest overhead?" — zazwyczaj brak.
- „Pokrywasz wszystkie JSON edge cases?" — zazwyczaj nie. Sample input ≠ all possible inputs.
- „Co jak format zmieni się?" — regex breakage.

**Kiedy ręczny parser BYWA OK:**
- **Bardzo specific format** (CSV, log line) gdzie struktura jest płaska i ograniczona.
- **Streaming z very tight memory** — czasem hand-written może być bardziej memory-efficient niż library.
- **Bezpieczeństwo (sandboxed input)** — czasem chcesz strict, custom format dla audit.

Ale nie dla generic JSON. To 30+ years solved problem.

**Decyzja code review:** odrzuć patch. Sugeruj Jackson z konkretnym pattern (streaming jeśli memory-conscious, data binding jeśli strict types). Mierz performance jeśli faktycznie overhead jest worry — `JsonParser` (streaming) jest fastest, `ObjectMapper.readValue` jest middle, tree model najwolniejszy. Wybór per use case.

**Interview trap:** „Programista jest zdolny, niech robi po swojemu" — autorska klęska zaufania. Decyzje techniczne mają konsekwencje team-wide. Code review powinno być merytoryczne i twardogłowe.
**Tags:** code-review, json, jackson, antipatterns

## Q-JX-032 [bloom: analyze]
**Question:** Pricing platforma używa Jackson do JSON. Programista mówi: „Mamy memory leak, podejrzewam Jacksona". Twoja diagnoza?
**Model answer:** Jackson **per se** rzadko ma memory leak — jest zaufany, mature. **Najprawdopodobniejsze przyczyny:**

1. **Fresh `ObjectMapper` per request:** `new ObjectMapper()` jest expensive. Niektórzy tworzą per call myśląc że to lekkie. ObjectMapper trzyma cache deserializerów per type — jeśli per-request → leak rośnie linear (chaina cache się kasuje, ale crearion overhead jest też duży). Solution: jeden `ObjectMapper` per app, reused (thread-safe). Spring beans `@Bean` to OK.

2. **TypeReference caching:** `mapper.readValue(json, new TypeReference<List<Foo>>(){})` — anonymous class per call. Jeśli leak nie jest direct memory ale GC pressure / Metaspace — może to być symptom.

3. **Custom modules / serializers w hot path:** jeśli registrujesz module per request (`mapper.registerModule(...)`), wewnętrzny cache rośnie.

4. **`@JsonTypeInfo` z `Id.CLASS`:** niebezpieczne dla classloading w niektórych aplikacjach (klasy ładowane na pamięć i nigdy unloaded).

5. **Streaming parser nie zamknięty:** `JsonParser` lub `JsonGenerator` musi być closed (try-with-resources). Bez tego — leakage internal buffers.

6. **Big JSON na stack/heap:** jednorazowy 1 GB JSON nie jest leak, ale OOM. Może być confused z leak. Solution: streaming.

7. **Cyclic reference w POJO:** jeśli serializujesz obiekt z self-reference bez `@JsonManagedReference`/`@JsonBackReference`, Jackson rzuca StackOverflow lub recursive serialization fills heap.

**Diagnoza process:**
1. **Heap dump** podczas leaku. Eclipse MAT, VisualVM, JProfiler. Find dominant objects.
2. **Class histogram** — która klasa dominuje? Jeśli `byte[]`, `char[]`, `JsonNode` — Jackson-owned. Jeśli `LinkedHashMap`, `String` — może być Jackson buffer albo custom code.
3. **Reference chain** — gdzie te objekty są retained? Path to GC root pokazuje source.
4. **Memory profiling over time** — leak rośnie linearly with traffic? Per-request leak. Step-function? Specific operation. Przy starcie ramping up potem stable? Cache fill, no leak.

**Likely culprits poza Jacksonem:**
- **DB connection pool** holding too many statements.
- **HTTP client** not closing connections.
- **Static caches** without eviction.
- **Listener / observer pattern** without unregister.
- **Thread locals** per task.

**Quick check:** wyłącz Jackson temporarily (mock), zobacz czy leak persists. Jeśli tak → not Jackson. Jeśli leak znika → Jackson-related, ale **prawdopodobnie misconfiguration**, nie bug w Jackson.

**Decyzja:** zacznij od heap dump, nie od „obwiniania Jackson". Profile-driven debugging.

**Interview trap:** Obwinianie biblioteki przed diagnozą = unprofessional. Drugi: założenie że memory leak = bug w third-party, kiedy zazwyczaj to misuse w application code.
**Tags:** debugging, memory, jackson, profiling
