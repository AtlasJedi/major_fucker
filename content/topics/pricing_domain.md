> Pre-built topic bank. Your /onboard will generate personalized content for your target role.

# Pricing domain — bank pytań

> Kontekst: stanowisko Software Engineer w platformie do zarządzania ceną. Ten temat to NIE głęboka ekspertyza — to język domeny pricingowej, którym posługuje się rekruter techniczny i z którym musisz brzmieć kompetentnie. Pytania ułożone na poziomie zrozumieniowym i applied; analyze pyta o decyzje architektoniczne na styku biznes-tech.

## Zakres

- price waterfall (lista → net price → invoice → pocket)
- list price, gross/net price, base price, transfer price
- markup vs margin (i ich obliczenia)
- discount types: trade discount, volume discount, cash discount, promotional discount
- rebate vs discount (off-invoice vs on-invoice)
- promotion: stacking, exclusivity, hierarchies
- pricing strategies: cost-plus, value-based, dynamic, competitive, penetration, skimming
- price elasticity (intuition; nie matematyka zaawansowana)
- price exception management i agreement management
- CPQ (Configure-Price-Quote)
- multi-currency: settlement, base currency, FX rate management
- price calendar, scheduled price changes
- what-if scenario / simulation
- RGM (Revenue Growth Management)
- B2B vs B2C pricing differences
- MAP (Minimum Advertised Price), MSRP, MRP
- contracted pricing, customer-specific pricing
- channels, segments, geography as pricing dimensions
- pricing audit, price history, version-based pricing

---

## Q-PRC-001 [bloom: recall]
**Pytanie:** Co to jest "price waterfall"?
**Modelowa odpowiedź:** Price waterfall to sekwencja cen (i deduktów) od ceny katalogowej (list price) do realnego pocket price (kwoty którą firma zatrzymuje). Standardowe etapy:
1. **List price** — cena z cennika, "z półki".
2. **Invoice price** = list - on-invoice discounts (np. trade discount, volume discount).
3. **Net price** ≈ invoice price.
4. **Pocket price** = invoice - off-invoice discounts (rebate, cooperative advertising allowance, slotting fees, payment terms discount).
5. **Pocket margin** = pocket price - cost of goods sold (COGS).

Każdy "krok w dół" to deduktor. Prawdziwa marża firmy jest po waterfall — nie po list price. **Klasyk McKinsey paper:** firmy często znają list price ale nie pocket price, co prowadzi do bardzo rentownych segmentów ukrytych w danych.
**Pułapka rozmowna:** „Pocket price = co klient płaci" — niekoniecznie. Klient może zapłacić więcej (taxes, fees), pocket to NETTO które firma zatrzymuje po wszystkich rabatach i prowizjach.
**Tagi:** waterfall, pricing-fundamentals

## Q-PRC-002 [bloom: recall]
**Pytanie:** Czym różni się markup od margin?
**Modelowa odpowiedź:** Oba liczą zysk vs koszt, ale różnym mianownikiem. **Markup** = (sale price - cost) / cost × 100%. **Margin** (gross margin) = (sale price - cost) / sale price × 100%. **Przykład:** koszt 100, cena 150 → zysk 50.
- Markup: 50/100 = **50%**
- Margin: 50/150 = **33.3%**

Konwersja: `margin = markup / (1 + markup)`, `markup = margin / (1 - margin)`. **Praktyka:**
- **Markup** popularny w retail/distribution (sklepy myślą „ile dorzucić do kosztu").
- **Margin** popularny w finance/sales (P&L pokazuje % marży na revenue).
- Te same dane, różne perspektywy. Mylenie tych dwóch = klasyczny rookie mistake.

**Pułapka rozmowna:** „Markup 100%" oznacza cena = 2× koszt. „Margin 100%" niemożliwe (to znaczyłoby że koszt = 0). „Margin 50%" = markup 100%.
**Tagi:** markup, margin, pricing-math

## Q-PRC-003 [bloom: recall]
**Pytanie:** Co to jest rebate i czym różni się od discount?
**Modelowa odpowiedź:** **Discount** (rabat on-invoice) — obniżka stosowana NA FAKTURZE, w czasie sprzedaży. Klient płaci od razu mniej. Widoczne w transakcji. **Rebate** — kredyt zwrócony klientowi PO sprzedaży, najczęściej za osiągnięcie celu (np. zakup ≥10k EUR/kwartał daje 5% rebate, wypłacany na koniec kwartału). Off-invoice. **Dlaczego rozróżnienie ma znaczenie:**
- **Reporting:** discount obniża revenue od razu; rebate to obowiązek (accrual) — księgowany jako liability dopóki nie wypłacony.
- **Cash flow:** klient z dużymi rebatami płaci pełen invoice price upfront, wypłata później — firma trzyma cash dłużej.
- **Sales psychology:** rebate motywuje do trafienia w cele; discount jest "od razu" satisfying ale nie incentivuje.
- **Tax implications:** różny treatment per jurisdiction.

**Typowe rebate types:**
- **Volume rebate** — % od sumarycznego zakupu (po ✓-ach progowych).
- **Mix rebate** — za zakup konkretnego mix produktów.
- **Loyalty rebate** — za multi-year contract.
- **Performance rebate** — za hit konkretnych KPI (np. shelf placement).

**Pułapka rozmowna:** Off-invoice rebates są często „hidden margin erosion" — sales team dorzuca rebate jako negocjacyjne narzędzie, a finance odkrywa po fakcie. RGM (Revenue Growth Management) tools pomagają śledzić.
**Tagi:** rebate, discount, off-invoice

## Q-PRC-004 [bloom: recall]
**Pytanie:** Co oznacza CPQ?
**Modelowa odpowiedź:** **Configure-Price-Quote** — kategoria oprogramowania do generowania ofert dla klientów B2B. **Etapy:**
1. **Configure** — kustomizacja produktu (np. samochód: model, silnik, opcje, kolory). Validation: jakie kombinacje są możliwe (np. „4WD wymaga V6+").
2. **Price** — kalkulacja ceny configured product, z uwzględnieniem cennika klienta, rabatów, promocji, margin floor.
3. **Quote** — generowanie formalnego dokumentu (PDF, e-podpis, integracja z CRM/ERP).

**Przykłady CPQ tools:** Salesforce CPQ, Oracle CPQ, SAP CPQ, Apttus, Conga, PROS, IBM CPQ.

**Use case w pricingu:** CPQ to często główny consumer pricing engine — sales rep konfiguruje, CPQ pyta pricing engine o cenę. Engine musi być fast (sub-second response), accurate (right rules applied), auditable.

**Bottlenecks typowo:**
- Complex bundle pricing (item A + B + C ma rabat za bundle).
- Multi-tier pricing (volume w Bundle).
- Approval workflow (rabat > X% wymaga approval).
- Multi-currency, multi-region.

**Pułapka rozmowna:** „CPQ to tylko quote generation" — częściowo. Configure + Price są równie skomplikowane, czasem bardziej. Configure ma własną logikę (constraint solver — Drools, OptaPlanner). Druga: CPQ vs e-commerce — CPQ B2B-oriented (negotiation, approval); e-commerce B2C (fixed prices, instant checkout).
**Tagi:** cpq, b2b, sales

## Q-PRC-005 [bloom: recall]
**Pytanie:** Czym jest MAP (Minimum Advertised Price) i MSRP?
**Modelowa odpowiedź:** **MSRP — Manufacturer's Suggested Retail Price** — cena rekomendowana przez producenta. Sugestia, nie obowiązek. Klient sprzedający (retailer, dystrybutor) może sprzedawać po dowolnej cenie. Często widoczna na metce, w katalogu — "sugerowana cena producenta". **MAP — Minimum Advertised Price** — minimum, za które retailer może REKLAMOWAĆ produkt. To jest umowne, retailer może sprzedać poniżej MAP, ale nie pokazywać ceny niższej w reklamie. Cele MAP:
- **Brand protection** — drogi brand nie powinien być widziany za $20 w landing page.
- **Channel parity** — retailerzy nie podcinają sobie cen reklamowo.
- **Margin floor** — encourages stable pricing, retailers margin protected.

**Inne pojęcia:**
- **MRP — Maximum Retail Price** (głównie India, niektóre kraje azjatyckie) — prawnie wymuszony max retail price drukowany na opakowaniu.
- **MAP enforcement** — producent monitoruje i może pociągnąć retailerów do odpowiedzialności kontraktowej za naruszenia.

**W pricing engine:** MAP/MSRP to pola na produkcie. Logika kalkulacji ceny musi szanować — np. dynamic pricing engine nie może zaproponować ceny < MAP w channel "retail advertising".

**Pułapka rozmowna:** Legalność MAP zależy od jurysdykcji. W US — generally OK (Leegin Creative case 2007). W EU — bardziej restrictive (price fixing concerns). **Resale Price Maintenance (RPM)** — wymuszanie ceny — generalnie illegal w EU, MAP jako workaround (advertised price tylko, transaction price free).
**Tagi:** map, msrp, channel, regulations

## Q-PRC-006 [bloom: recall]
**Pytanie:** Co to jest dynamic pricing?
**Modelowa odpowiedź:** Dynamic pricing — strategia ustalania cen w real-time lub pseudo real-time na podstawie zmiennych: popytu, podaży, czasu, segmentu klienta, channel, zachowań competitorów, weather, eventów. **Przykłady:**
- **Airlines** — cena lotu zmienia się x razy dziennie, zależy od booking pace, days-to-departure, kompetencji.
- **Uber surge pricing** — gdy popyt > podaż drivers, cena rośnie.
- **Amazon** — produkty mają tysiące zmian dziennie (algorytmiczne).
- **Hotele** — peak season vs off-season, last-minute deals.
- **E-commerce** — competitive matching, abandonment recovery.

**Przeciwieństwo:** **static pricing** — cennik zmieniany rzadko (raz na kwartał / rok).

**Implementacja w engine:**
- **Rules engine** — deklaratywne reguły (`IF demand > 0.9 THEN price *= 1.15`).
- **ML model** — features (history, weather, day-of-week, competitor prices) → predicted optimal price.
- **A/B testing infrastructure** — dla validacji.
- **Audit trail** — każda zmiana zapisana (compliance, debugging).

**Pułapki:**
- **Customer perception** — surge pricing może być widziane jako exploitation. PR risk.
- **Algorithmic collusion** — multiple companies używają similar ML, ceny się stabilizują → antitrust risk.
- **Personalization vs discrimination** — różne ceny per user może być dyskryminacja. Legal risks especially in EU (GDPR).
- **Customer trust** — ujawnienie że ten sam produkt ma 5 cen w 5 minut może podważać zaufanie.

**Pułapka rozmowna:** „Dynamic = automated" — częściowo. Dynamic może być rule-based (semi-manual), ML-based, lub hybrid. Druga: „Always optimal" — ML często optymalizuje short-term revenue, ignoruje long-term retention. Pricing wymaga balanced metrics.
**Tagi:** dynamic-pricing, strategy, ml

## Q-PRC-007 [bloom: recall]
**Pytanie:** Co to jest cost-plus pricing vs value-based pricing?
**Modelowa odpowiedź:** Dwa fundamentalnie różne podejścia. **Cost-plus:** cena = koszt + markup. Prosty: dostać cost data, nałożyć margin (np. 30%), gotowe. **Plusy:** prosty, defensywny ("to jest nasz koszt + uczciwa marża"), guarantees minimum margin. **Minusy:** ignoruje WTP (willingness-to-pay) klientów. Może niedoceniać produkty wartościowe (zostawiać money on table) lub przeceniać niechciane (sklep się nie obraca).

**Value-based:** cena = funkcja postrzeganej wartości dla klienta. Niezależna od kosztu. **Process:**
1. Identifikacja value drivers per segment (np. „dla CFO ten software oszczędza 2 etaty = 200k/rok").
2. Pricing odpowiednio (np. 50k/rok = 25% wartości — fair).
3. Continuous validation przez win/loss analysis.

**Plusy:** maksymalizuje revenue, premium positioning, zachęca do innowacji (innovation = more value = price). **Minusy:** trudny do uzasadnienia („kosztuje tyle bo tyle"), wymaga deep customer understanding, sales process complex (negotiation per customer).

**Inne strategie:**
- **Competition-based:** cena podobna do konkurencji.
- **Penetration:** niska cena żeby wejść na rynek (build market share).
- **Skimming:** wysoka cena na początku (early adopters), obniżać z czasem (Apple iPhone strategia).
- **Freemium:** free tier + paid upgrades.
- **Loss leader:** sell some products below cost to attract customers (cross-sell margin).

**Real world:** firmy stosują mix. **B2B SaaS** często value-based + tiers. **Commodity** często cost-plus + competitive. **Premium fashion** value-based + exclusive availability.

**Pułapka rozmowna:** „Cost-plus = stara szkoła, value-based = modern" — naïve. Niektóre commodity nie mają luksusu value-based (rynek dyktuje). Druga: „Value = co klient chce" — value to dolary uratowane / zarobione przez klienta dzięki produktowi. Mierzalne, nie emocje.
**Tagi:** strategies, cost-plus, value-based

## Q-PRC-008 [bloom: recall]
**Pytanie:** Co to jest "price elasticity"?
**Modelowa odpowiedź:** **Price elasticity of demand** — miara jak bardzo popyt reaguje na zmianę ceny. **Formuła:** `elasticity = % change in quantity / % change in price`. **Klasyfikacja:**
- **|E| > 1** — elastic. Mała zmiana ceny → duża zmiana quantity. Klasyczne: luxury goods, substitutes available. Obniżka 10% → wzrost wolumenu 20%, total revenue rośnie.
- **|E| < 1** — inelastic. Cena rośnie, klienci wciąż kupują. Klasyczne: necessities, addictive goods (paliwo, leki), monopol. Podwyżka 10% → spadek 5%, total revenue rośnie.
- **|E| ≈ 1** — unit elastic. Total revenue stable.
- **|E| ≈ 0** — perfectly inelastic. Cena nie wpływa.
- **|E| → ∞** — perfectly elastic. Każda podwyżka eliminuje popyt.

**Praktyczne implikacje:**
- **Inelastic product** — masz pricing power. Możesz podnieść cenę bez znaczącej utraty wolumenu.
- **Elastic product** — careful with raises. Możesz robić promo (drop price), wolumeny więcej niż kompensują.
- **Cross elasticity** — popyt na produkt A zależy od ceny B. Dwa produkty są substitutes (positive cross — wzrost ceny B → wzrost popytu A) lub complements (negative cross — wzrost B → spadek A; np. drukarki + tonery).

**Mierzenie:** historyczne dane (jak wzrastały sprzedaże po obniżkach), A/B testy cen w channels, surveys (van Westendorp), conjoint analysis. Niedoskonałe — jest fundamentalna trudność (nie kontroluje się wszystkiego).

**W pricing engine:** elastycity model może informować dynamic pricing — drop price when elastic + capacity available, raise when inelastic + demand high.

**Pułapka rozmowna:** Elastycity nie jest stała w czasie. Sezonowość, inflacja, change of competition — wszystko zmienia. Modele wymagają continuous calibration. Druga: „Niskie ceny zawsze zwiększają wolumeny" — false dla luxury (Veblen goods — wyższa cena = więcej kupujących, signaling).
**Tagi:** elasticity, demand, economics

---

## Q-PRC-009 [bloom: understand]
**Pytanie:** Wytłumacz różnicę między B2B a B2C pricing.
**Modelowa odpowiedź:** Fundamentalne różnice w transactional model i przez to w pricing systems.

**B2C (Business-to-Consumer):**
- **Stała cena per channel** (online sklep, fizyczna półka, app).
- **Krótki sales cycle** (klikany checkout, single transaction).
- **Pricing decyzja podejmowana centrally** (marketing/pricing team).
- **Brak negocjacji** zazwyczaj (poza B2B-like premium).
- **Cena widoczna upfront**.
- **Volume discount tylko w okazjonalnych przypadkach** (bulk SKU, family pack).
- **Channel uniformity** (omnichannel oczekiwanie — ta sama cena online i in-store).

**B2B (Business-to-Business):**
- **Customer-specific pricing** — każdy klient ma negocjowany cennik (contracted pricing).
- **Long sales cycle** — RFP, negotiation, multi-stakeholder approval.
- **Quote-based** — sales rep pyta CPQ → quote → klient zatwierdza/negocjuje.
- **Volume discounts standard** (tier pricing, slab/range based).
- **Rebates** typowe (off-invoice).
- **Multi-year contracts** z escalator clauses.
- **Payment terms** as pricing dimension (NET-30 vs NET-60 vs prepaid).
- **Custom pricing per region / channel / segment**.
- **Approval workflows** — rabat > threshold wymaga approval.

**Implikacje techniczne:**

| Aspect | B2C system | B2B system |
|--------|-----------|-----------|
| Number of price points | Few per product | Hundreds/thousands per product |
| Price calculation latency | <100ms (real-time checkout) | Seconds OK (CPQ) |
| Audit trail | Optional | CRITICAL (contract compliance) |
| API consumers | E-commerce, mobile app | CPQ, ERP, sales team CRM |
| Schema complexity | Simple | Complex (segment × geo × tier × channel × promo) |
| Edge cases | Few | Lots — every contract has exceptions |

**Hybrydy:**
- **B2B SaaS** — często stała cennik (Slack, Notion: per-seat pricing) bardziej B2C-like.
- **Wholesale e-commerce** (Alibaba, Faire) — B2B z B2C-like UX.
- **Marketplaces** (Amazon Business) — middle ground.

**Pricing engine architecture:**
- B2C: cache-heavy, read-heavy, simple lookup.
- B2B: rule engine, complex aggregation, customer-specific configurations, lower throughput per customer ale more complex queries.

**Pułapka rozmowna:** „B2B = enterprise" — częściowo. Even small B2B (np. supplier do café) ma negotiated pricing logic. Skala biznesowa nie definuje, struktura transakcji definiuje. Druga: „W B2B nie ma list price" — ma. List price + customer-specific discounts. List jest punktem startowym negocjacji.
**Tagi:** b2b, b2c, pricing-models, architecture

## Q-PRC-010 [bloom: understand]
**Pytanie:** Wytłumacz "promotion stacking" — co to jest i jakie są pułapki?
**Modelowa odpowiedź:** **Promotion stacking** — kilka promocji aplikowanych do tego samego produktu/zamówienia jednocześnie. **Przykłady:**
- 20% zniżki na cały koszyk (storewide promo).
- BOGO (Buy One Get One) na konkretny produkt.
- Cashback 5% za płatność kartą.
- Newsletter signup 10% off.

Klient kupuje produkt który łapie wszystkie 4 → ile faktycznie płaci?

**Strategies:**

**1. Best-of (winner-takes-all):** użyć JEDNEJ najlepszej promocji, ignorować pozostałe. Plus: predictable margin floor. Minus: customer może być sfrustrowany („mam 4 kupony, ale tylko 1 działa").

**2. Stacked (additive):** wszystkie aplikowane sekwencyjnie. Plus: customer happy. Minus: margin może spaść do straty. Wymaga floor checks.

**3. Hierarchical:** kategorie promocji (storewide, item, payment, loyalty) — w każdej kategorii winner-takes-all, ale CATEGORIES stack. Compromise.

**4. Custom rules per promo:** każda promocja deklaruje compatibility (`stacks_with: ["loyalty"]`, `excludes: ["BOGO"]`). Engine resolve conflict.

**Pułapki:**

**A) Order of application matters:**
- Apply % first, then $ off → różny wynik niż reverse.
- Tax — applied on pre-discount or post-discount? Legal treatment varies per region.
- Round to cents — może wprowadzić inconsistencies.

**B) Margin floor:**
- Stacked promos mogą sprzedać poniżej kosztu. Engine MUSI mieć floor check (minimum margin %, MAP enforcement).

**C) Combinatorial complexity:**
- N promotions = 2^N possible combinations. Testing wszystkich niemożliwe. Pareto: pokryć top combinations.

**D) Customer experience:**
- Cart shows price before/after each promo applied — transparency builds trust.
- "Saved $X total" — psychological win.
- Konfliktujące promocje muszą być explicit ("Promo X excludes promo Y").

**E) Audit and reporting:**
- Each transaction stores which promos applied + savings per promo.
- Marketing wants per-promo ROI; finance wants per-customer accounting.

**F) Race conditions:**
- Promo limited to first 1000 customers — concurrent transactions claim simultaneously, must be atomic counter.

**Implementation patterns:**
- **Rule engine** (Drools, OptaPlanner) — declarative rules.
- **Sequential pipeline** — list of promo evaluators applied in order, each calculates discount.
- **CPQ-style** — quote builder asks all eligible promos, ranks, applies winners.

**Pricing engine specifically:**
- **Promo schema** — `(promo_id, type, conditions JSONB, action JSONB, stack_priority, valid_from, valid_to, max_uses, segments, channels)`.
- **Eligibility check** — fast (pre-filter na poziomie SQL).
- **Conflict resolution** — explicit rules.
- **Result audit** — per-line per-promo savings, total.

**Pułapka rozmowna:** „Najwyższa zniżka wygrywa" — uproszczenie. Czasem kombinacja (5% + 10%) wygrywa nad najwyższą pojedynczą (12%). „Stacking == addytywne" — false. 10% + 10% w stack-ed kontekście może dać 19% (sequential: cena * 0.9 * 0.9 = 0.81), nie 20%.
**Tagi:** promotions, stacking, rules, pricing-engine

## Q-PRC-011 [bloom: understand]
**Pytanie:** Co to jest contracted pricing i czemu wymaga osobnej logiki w engine?
**Modelowa odpowiedź:** **Contracted pricing** (a.k.a. negotiated pricing, customer-specific pricing) — każdy klient B2B ma podpisany kontrakt z określonymi warunkami: ceny, rabaty, rebate'y, payment terms, validity period. **Charakterystyki:**
- **Per-customer scope** — Customer A ma cenę 95 PLN za produkt X, Customer B ma 87 PLN.
- **Validity windows** — kontrakt obowiązuje od / do.
- **Override hierarchy** — kontrakt może mieć priorytet nad list price + standard promo (lub odwrotnie, zależy od policy).
- **Multi-dimensional** — nie tylko produkty, ale też czasem channels, regions, services.

**Przykład schema:**
```
customer_contract:
  contract_id, customer_id, signed_date, effective_from, effective_to, 
  status (DRAFT/ACTIVE/EXPIRED), terms_doc_url

contract_pricing_rule:
  contract_id, product_id, country, list_override OR discount_pct OR fixed_price,
  min_quantity, max_quantity, valid_from, valid_to
  
contract_rebate:
  contract_id, threshold (q-tarter sales > X), rebate_pct, payout_period
```

**Lookup logic:**
1. Identify customer i ich active contract.
2. For requested (product, country, quantity, date): query contract rules.
3. If match → use contracted price.
4. Else → fallback to list price + standard pricing logic.
5. Apply standard promotions if contract allows stacking.

**Czemu osobna logika:**

**1. Cardinality** — z 10k klientów × 10k produktów = potencjalnie 100M contract rules. Bez optymalizacji query lookup może być wolny. Solution: indexing, caching per customer.

**2. Audit trail** — każda transakcja musi pokazać który contract rule applied. Compliance, dispute resolution.

**3. Lifecycle management** — contracts mają end date, auto-renewals, amendments. Pricing system synced z contract management system.

**4. Pre-payment vs post-payment models:**
- Pre-paid commitment — customer paid 100k upfront for credits, drawn down per transaction.
- Post-paid — invoiced after period, with rebate calculations.

**5. Approval workflow:**
- Contract terms requires legal review.
- Sales discount > threshold needs approval.
- Pricing engine should integrate / respect approval state.

**6. Reporting:**
- Per-contract revenue / margin tracking.
- Variance from standard pricing (how much "below list" we sold).
- Renewal pipeline.

**System design considerations:**
- **Contract repository** — versioned, audited, often integrated with CRM (Salesforce, Dynamics).
- **Pricing API** must accept `customer_id` parameter and resolve contract.
- **Cache invalidation** when contracts change (reactive update via events).
- **What-if simulation** — sales rep wants to see "if we sign new contract X, what's the projected revenue?".

**Pricing engine implementation:**
```sql
-- Lookup priority
SELECT 
  COALESCE(
    contract_price.fixed_price,
    list_price.price * (1 - COALESCE(contract_pricing.discount_pct, 0))
  ) AS effective_price
FROM list_price 
LEFT JOIN customer_contract cc ON cc.customer_id = $customer_id 
  AND cc.status = 'ACTIVE' 
  AND $request_date BETWEEN cc.effective_from AND cc.effective_to
LEFT JOIN contract_pricing_rule cpr ON cpr.contract_id = cc.contract_id 
  AND cpr.product_id = $product_id
WHERE list_price.product_id = $product_id 
  AND list_price.country = $country;
```

**Pułapka rozmowna:** „Contract price always wins over list" — może też być na odwrót w niektórych biznesach. Definitely customer-specific, ale konflikty z standard promos to design decision per organizacji. Druga: temporal validity — kontrakt może mieć multiple amendments, każde z innym effective range. SCD (Slowly Changing Dimensions) Type 2 pattern w schema.
**Tagi:** contracted-pricing, b2b, schema-design

## Q-PRC-012 [bloom: understand]
**Pytanie:** Co to jest "price calendar" i kiedy potrzebny?
**Modelowa odpowiedź:** **Price calendar** — schedule planowanych zmian cen w czasie. Zamiast „zmieniam cenę produktu X dziś", definiujesz „cena produktu X to 100 PLN, ale od 1.06.2026 to 110 PLN, od 1.09.2026 wraca do 100 PLN (sezonowe podwyżki)".

**Use cases:**
- **Seasonal pricing** — zima vs lato (turystyka, klimatyzacja).
- **Quarterly / annual reviews** — pricing committee zatwierdza nowe ceny obowiązujące od konkretnej daty.
- **Promotions** — Black Friday, Cyber Monday — cena zmienia się tylko na te dni.
- **Contract escalation** — kontrakt z klientem ma roczną indeksację (CPI + 2%).
- **Multi-step price increase** — gradual raise (mniej szok dla klientów niż 30% od razu, więcej +5% co kwartał przez rok).

**Schema:**
```
price_calendar:
  product_id, country, price, valid_from, valid_to, 
  effective_priority, source (manual / automated / contract)
```

**Query w czasie t:**
```sql
SELECT price 
FROM price_calendar 
WHERE product_id = ? AND country = ?
  AND ? >= valid_from 
  AND (valid_to IS NULL OR ? < valid_to)
ORDER BY effective_priority DESC, valid_from DESC
LIMIT 1;
```

`effective_priority` rozstrzyga konflikty (np. promotion ma higher priority niż base price).

**Edge cases:**

1. **Overlapping calendar entries** — bug-prone. Validation: no two ACTIVE entries dla same (product, country) z overlapping date range, chyba że priority różny.
2. **Timezone handling** — 1.06 w Polsce ≠ 1.06 w US. UTC + per-region effective time.
3. **Backdated changes** — czasem trzeba retro-update (compliance, error correction). Effects on existing orders? Audit critical.
4. **Future scheduled changes visible** — sales rep widzi cennik "as-of-future-date" do plan negotiations.
5. **What-if simulation** — what if I shift this price increase by 30 days?

**Implementation:**
- **DB-driven** (most common) — table z entries.
- **Rule engine** — for complex scheduling logic (cron-like).
- **Event-driven** — Kafka topic `price_change_scheduled`, downstream services subscribe.
- **Materialized view** — pre-compute "current price as of day X".

**Pricing engine integration:**
- Lookup z `effective_date` parameter — zawsze wiadomo as-of-when.
- `getCurrentPrice(productId, country)` = `getPriceAt(productId, country, now())`.
- For batch (nightly recalc), iterate per future date.

**Pułapka rozmowna:** „Po prostu schedule a job to update price na 1.06" — działa, ale: a) job może failować, b) `valid_from`-based query jest bardziej resilient (price change "happens" automatically when date arrives, no job needed), c) audit trail in calendar table jest cleaner. Druga: timezone. „1.06.2026" — kiedy dokładnie? UTC, local server time, customer's timezone? Conflicts happen.
**Tagi:** price-calendar, scheduling, temporal

## Q-PRC-013 [bloom: understand]
**Pytanie:** Co to jest "Revenue Growth Management" (RGM)?
**Modelowa odpowiedź:** **RGM (Revenue Growth Management)** — discyplina i kategoria oprogramowania optymalizująca pricing, promo, mix, channel mix dla maksimum revenue/profit. Najczęściej w consumer goods (FMCG), retail, beverage. **Główne lewary RGM (5):**

1. **Pricing** — base list price decisions per SKU/region.
2. **Promotion** — when, how deep, on which products. ROI per promo.
3. **Mix** — which products to push (margin per SKU mix optimization).
4. **Trade investment** — co wydajesz w channels (retailers, distributors): off-invoice rebates, slotting fees, listing fees, co-op marketing.
5. **Assortment** — which SKUs in which channels. Channel-specific lineup.

**Cele RGM:**
- Top-line growth (revenue, market share).
- Bottom-line growth (margin, profit).
- Long-term brand value (avoid race-to-bottom).

**Tools / vendors:** Nielsen, IRI, Vistex, Vantage, PROS, Pricefx, Vendavo, Salesforce Revenue Cloud, Anaplan.

**Activities:**
- **Promo evaluation** — historic ROI: czy ta promo zarobiła czy straciła? Często „big promotion" nie pokrywa cost (margin loss > incremental revenue).
- **Pricing scenarios** — what if I raise base 5% but increase promo depth to compensate?
- **Trade spend optimization** — gdzie wydajesz najwięcej, gdzie zwrot najgorszy.
- **Mix shifting** — push SKU A (high margin) over SKU B (low margin) where customers indifferent.
- **Assortment rationalization** — which SKUs to drop (low volume + low margin = candidates).

**Data sources:**
- POS / sales data (transactional).
- Promo schedule.
- Cost data.
- Competitive prices (Nielsen scanner data).
- Customer/segment data.

**Tech challenges:**
- **Data integration** — data z 100s of retailers, formats różne.
- **Forecasting** — how will customers respond to new pricing?
- **Simulation** — what-if scenarios in sub-second (UI requirement).
- **Optimization** — find best price/promo plan given constraints (margin floor, brand price ladder, etc.).
- **A/B testing** — controlled experimentation.

**ML applications:**
- Elasticity modeling per SKU/segment.
- Promotion uplift prediction.
- Assortment recommendations.

**Realistic role of pricing engineer:**
- Build pricing API z fast lookup.
- Integrate z RGM analytical tools.
- Provide audit data for RGM teams.
- Implement RGM team's decisions (config-driven, no code change for new pricing).

**Pułapka rozmowna:** RGM nie jest „set price once and forget". Kontinuous optimization, mierzenie, iterate. Druga: RGM często wymaga collaboration z sales (sales lubi promo bo łatwiej sprzedać; RGM walczy o margin). Polityczne, nie tylko techniczne.
**Tagi:** rgm, optimization, strategy

## Q-PRC-014 [bloom: understand]
**Pytanie:** Co to jest "what-if scenario" w pricingu i jak wspiera business?
**Modelowa odpowiedź:** **What-if scenario** — symulacja efektu zmiany pricing/promo bez actual implementation. Pozwala biznesowi przewidzieć konsekwencje przed commit.

**Typowe pytania:**
- "Co jeśli podniosę cenę produktu X o 5% w Polsce — jaki revenue impact?"
- "Co jeśli wprowadzę promotion 20% off na 2 tygodnie — koszt vs incremental revenue?"
- "Co jeśli zmienimy customer segment B z 10% rabatu na 5% rabat + 5% rebate — net effect?"
- "Co jeśli konkurent obniży o 10% — jak nasz wolumen zareaguje?"

**Wymagania techniczne:**

**1. Historical baseline:**
- Real sales data jako reference.
- Aggregations per (product, region, segment, channel) per period.

**2. Simulation engine:**
- Apply hypothetical changes to baseline.
- Re-calculate price waterfall.
- Apply elasticity (jak quantity zareaguje).
- Apply promotion uplift / lift factor.

**3. Scenarios management:**
- Save / share scenarios.
- Compare A vs B vs current.
- Audit who created what.

**4. Performance:**
- Sub-second response dla interactive UI.
- Batch mode dla complex (overnight runs).

**5. Visualization:**
- Charts (revenue over time, margin over time).
- Heat maps (region × product impact).
- Sensitivity analysis (what % change drives biggest revenue lift?).

**Implementation patterns:**

**A) Materialized history + on-the-fly simulation:**
```python
def simulate(scenario):
    baseline = load_history()
    for change in scenario.changes:
        impact_factor = elasticity_model(change.product, change.region, change.delta_pct)
        baseline.apply(change, impact_factor)
    return baseline.aggregate()
```

**B) Rule replay:**
- Re-run pricing rules engine with hypothetical config.
- Apply to historical orders, see what they would've been.

**C) Monte Carlo:**
- Run scenario 1000 times with random customer behavior variations.
- Get distribution of outcomes (P10/P50/P90).

**Tech stack:**
- DuckDB / ClickHouse for fast analytical queries.
- Pandas / Polars dla in-memory simulation.
- ML models served via REST.
- React/Angular dashboard for UI.

**Edge cases:**
- **Cannibalization:** product A drops price → ludzie kupują A zamiast B. Model must capture cross-elasticity.
- **Lag effects:** raise causes immediate decline, but customers adapt over months. Time-aware simulation.
- **Discontinuities:** promo at 19% might do nothing, at 20% triggers viral effect. Non-linearity hard to model.

**Realistic limitations:**
- Models are approximate. „Predicted +12% revenue" w real life może być ±15%.
- Validation: backtest model on historical data.
- Sensitivity to assumptions — RGM teams often run multiple scenarios with different elasticity assumptions to bracket the answer.

**Pułapka rozmowna:** „Simulation jest dokładna" — nigdy. Direction directional w 90% przypadków, magnitude często off. Use as decision support, not as oracle. Druga: people overweight simulation bo „liczbka" — confirmation bias. Sales mogą zawsze argument „simulation jest pesymistyczna, w prawdziwym życiu to lepsze".
**Tagi:** what-if, simulation, decision-support

## Q-PRC-015 [bloom: understand]
**Pytanie:** Multi-currency pricing — jakie są wyzwania?
**Modelowa odpowiedź:** Pricing global znaczy multiple currencies. Nieoczywiste decisions:

**1. Base currency vs local pricing:**
- **Base currency (np. USD):** wszystkie ceny przechowywane w USD, FX conversion przy display/sale. Plus: simple central management. Minus: customer experience gorszy ("11.97 EUR po przeliczeniu" wygląda dziwnie vs round 12.00 EUR).
- **Local pricing:** każdy kraj ma własną cenę w lokalnej walucie. Plus: clean numbers, market-tuned. Minus: more management, FX risk, inconsistencies.

Praktyka: **hybrid** — local pricing dla major markets (EUR, USD, GBP, JPY), base + FX dla long-tail markets.

**2. FX rate management:**
- **Spot vs daily vs monthly rate** — at what frequency convert?
- **FX rate source** — central bank? Reuters? Internal treasury?
- **Locked rates** — kontrakt z klientem może być w EUR ale invoice w USD locked at signing rate (chroni klienta przed FX volatility).
- **Mid-market vs bid/ask** — banks użyją bid/ask, you might get mid (negotiation point).

**3. Display rules per region:**
- Decimal separator: 1,234.56 (US) vs 1.234,56 (DE).
- Currency symbol position: $123 (US) vs 123 € (FR) vs 123,- € (DE).
- Number formatting via `Intl.NumberFormat` (JS) / `NumberFormat` (Java) z locale.

**4. Tax inclusion:**
- B2C ceny zazwyczaj **inclusive** of tax/VAT in EU, **exclusive** in US.
- B2B ceny zazwyczaj **exclusive**.
- Same display price w UE i US może mean different things to customer.

**5. Rounding rules:**
- "0.99 endings" — 9.99, 19.99 — cultural in some markets.
- Round up/down/nearest do nearest cent / 10 cent / 50 cent — varies.
- Pricing engine MUST be deterministic w rounding.

**6. Settlement vs catalog currency:**
- Catalog: 100 EUR.
- Customer in US sees: 109.50 USD (after FX + markup).
- Settlement to merchant: zawsze 100 EUR.
- Or: merchant accepts USD, FX risk z merchant.

**7. Hedging:**
- Long-term contracts in foreign currency expose to FX risk.
- Treasury hedges (forward contracts, options).
- Pricing system flags exposure for treasury (analytic feeds).

**8. Tax engine integration:**
- Avalara, Vertex, Sovos — calculate tax per jurisdiction.
- Pricing engine sends pre-tax price + customer location, tax engine returns tax amount.

**9. Refund handling:**
- Customer paid 100 USD when EUR/USD was 1.10. Refund 6 months later when 1.05.
- Refund 100 USD or 90.91 EUR (original) ≈ 95.45 USD now?
- Policy decision; consistent application critical.

**10. Audit and reporting:**
- Each transaction stores: currency at time of sale, FX rate used, base equivalent.
- Multi-currency reports (revenue in local + USD reporting equivalent).

**Pricing engine architecture:**
- `Money` value object: `{amount, currency}` — never bare number.
- FX service injectable.
- Locale awareness in display layer.
- Tax integration as side service.

**Pułapka rozmowna:** „Just convert at runtime" — works for display, fails for accounting. Each transaction must record actual FX used. Druga: storing prices in cents/lowest-unit per currency — varies (JPY no cents, BHD has 3 decimals). Use BigDecimal or `Money` library that respects per-currency decimal scale.
**Tagi:** multi-currency, fx, internationalization

## Q-PRC-016 [bloom: understand]
**Pytanie:** Co to jest "price exception management"?
**Modelowa odpowiedź:** **Price exception** — sytuacja gdy proposed price odbiega od standardowego. **Examples:**
- Sales rep oferuje rabat 25% (standard cap to 15%).
- Custom price dla VIP klienta poniżej list.
- Obniżka by match competitor offer.
- Pricing error correction (sprzedano za 100, powinno być 200).

**Czemu trzeba managować:**

1. **Margin protection** — niekontrolowane exceptions → erozja marży.
2. **Compliance** — w niektórych branżach (medical, finance) deviation from standard wymaga formal approval.
3. **Audit trail** — kto co kiedy zaakceptował.
4. **Pattern detection** — duża liczba exception w tym samym channel = znak że standard pricing jest niewłaściwy (signal do review).

**Workflow management:**

**Standard exception workflow:**
1. **Request:** sales rep proposes deviation w CPQ. Specifies rabat, reason.
2. **Validation:** system checks against rules (e.g., rabat ≤30%, customer eligible).
3. **Approval routing:**
   - Discount ≤5%: auto-approved.
   - 5-10%: sales manager approval.
   - 10-20%: regional director.
   - >20%: VP / executive.
4. **Notification:** approver gets email/Slack/in-app notification z context.
5. **Decision:** approve / reject / counter-offer.
6. **Audit:** stored permanently. Quote includes exception ID for traceability.

**Schema:**
```
price_exception:
  exception_id, request_date, requester_id, customer_id, product_id,
  standard_price, requested_price, deviation_pct, reason, status,
  approver_id, approval_date, approval_comment, expires_at
```

**Edge cases:**

- **Bulk exceptions** — sales chce dla całego customer multi-product. Bulk approval flow.
- **Time-bound exceptions** — valid only for this specific quote, expires in 30 days.
- **Renewals** — last year's exception expires; auto-extend or re-approve?
- **Multi-level conflicts** — manager A approved 15%, but customer później chce 20%. Re-route to higher level?

**Anti-patterns:**

- **No threshold:** every transaction needs approval → process bottleneck.
- **Too lenient:** managers approve everything (rubber stamp) → no real control.
- **No audit:** can't see why prices were exceptions → can't learn.
- **Manual process** (email approvals): slow, lossy, no enforcement.

**Pricing engine integration:**

- Engine returns "standard price" + "max discount allowed" given context.
- Sales rep enters proposed price; engine returns "needs approval" + which level.
- Once approved, engine treats it as authorized → applies in calculation.
- Quote document references exception ID.

**Reporting:**
- # exceptions per period, per region, per product.
- Average deviation %.
- Exception → Win rate (does giving rabat actually close the deal?).
- Frequent exceptions = signal że standard price is too high for that segment.

**Pułapka rozmowna:** „Approve everything tj. don't lose deal" — short term win, long term margin disaster. „Reject everything" — sales frustration, lost deals. Sweet spot: data-driven thresholds, fast approval for reasonable, hard scrutiny for outliers.
**Tagi:** exception-management, approval, governance

---

## Q-PRC-017 [bloom: apply]
**Pytanie:** Pokaż jak zaprojektować schema bazy dla cennika multi-tier (volume-based discounts).
**Modelowa odpowiedź:**
```sql
CREATE TABLE product (
  id SERIAL PRIMARY KEY,
  sku VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(255),
  base_price DECIMAL(12, 2) NOT NULL,
  currency CHAR(3) NOT NULL,
  active BOOLEAN DEFAULT TRUE
);

-- Tier-based pricing: each row jest tier
CREATE TABLE volume_pricing_tier (
  id SERIAL PRIMARY KEY,
  product_id INT NOT NULL REFERENCES product(id),
  country CHAR(2) NOT NULL,
  segment VARCHAR(20),  -- B2B, B2C, VIP, etc.; NULL = all segments
  
  -- Range definition (slab approach)
  min_quantity INT NOT NULL,
  max_quantity INT,  -- NULL = no upper bound
  
  -- Pricing
  unit_price DECIMAL(12, 2) NOT NULL,
  -- ALTERNATIVELY: discount_pct DECIMAL(5, 4) — discount off base
  
  -- Validity
  valid_from TIMESTAMP NOT NULL,
  valid_to TIMESTAMP,  -- NULL = open-ended
  
  -- Audit
  created_at TIMESTAMP DEFAULT NOW(),
  created_by VARCHAR(50),
  
  -- Constraints
  CHECK (min_quantity >= 1),
  CHECK (max_quantity IS NULL OR max_quantity >= min_quantity),
  CHECK (unit_price >= 0)
);

-- Indexy dla szybkiego lookup
CREATE INDEX idx_vpt_lookup ON volume_pricing_tier (product_id, country, segment, valid_from);

-- Unique constraint: nie ma overlapping tier-ów
-- (uproszczenie — w prod może być bardziej skomplikowane z exclusion constraint)
ALTER TABLE volume_pricing_tier ADD CONSTRAINT uq_no_overlap 
EXCLUDE USING gist (
  product_id WITH =,
  country WITH =,
  segment WITH =,
  int4range(min_quantity, COALESCE(max_quantity, 2147483647), '[]') WITH &&,
  tstzrange(valid_from, COALESCE(valid_to, 'infinity'::timestamp), '[)') WITH &&
);
```

**Lookup query:**
```sql
SELECT unit_price 
FROM volume_pricing_tier
WHERE product_id = ? 
  AND country = ?
  AND (segment = ? OR segment IS NULL)
  AND ? >= min_quantity
  AND (max_quantity IS NULL OR ? <= max_quantity)
  AND ? >= valid_from
  AND (valid_to IS NULL OR ? < valid_to)
ORDER BY 
  segment IS NULL ASC,  -- specific segment beats wildcard
  valid_from DESC       -- most recent if multiple
LIMIT 1;
```

**Different approach: cumulative tier (slab discount):**
- Buy 10: each unit 100. Total 1000.
- Buy 50: each unit 90. Total 4500.
- Buy 100: each unit 80. Total 8000.

Each "buy 50" wszystkie 50 sztuk po cenie 90. Threshold-based.

**Vs. progressive tier (true tier):**
- First 10 units: 100/each.
- Next 40 units (10-50): 95/each.
- Next 50 units (50-100): 90/each.
- Buy 60: 10×100 + 40×95 + 10×90 = 5700. Mixed pricing.

**Slab is simpler, more common** in B2B. Progressive używany w utilities (electricity tier pricing).

**Validation logic:**
- No gaps: tier (10-50) followed by (51-100) — gap [50] missing → bug.
- No overlaps: (10-50) and (40-80) — conflict.
- Sensible direction: typically `unit_price` decreases as quantity increases (volume discount). Could enforce with check trigger.

**Edge cases:**
- Quantity 0 — typically reject.
- Quantity beyond highest tier — uses last tier.
- Brak match — fallback do `product.base_price`.

**Pricing engine code:**
```java
public BigDecimal getPriceForQuantity(Long productId, String country, String segment, int quantity, Instant asOf) {
    Optional<VolumeTier> tier = repo.findActiveTier(productId, country, segment, quantity, asOf);
    if (tier.isPresent()) {
        return tier.get().getUnitPrice();
    }
    return productRepo.findById(productId).getBasePrice();
}
```

**Pułapka rozmowna:** Bez `EXCLUDE` constraint (Postgres) overlaps mogą się wkraść — niedobry user input → bugi w lookups (multiple tier match for same quantity, ORDER BY decyduje randomly). Druga: customer-specific contract pricing przepisuje tier pricing — multi-level resolve potrzebny.
**Tagi:** schema-design, volume-pricing, tiers

## Q-PRC-018 [bloom: apply]
**Pytanie:** Implementuj kalkulację price waterfall: list price → trade discount → cash discount → invoice price → off-invoice rebate → pocket price.
**Modelowa odpowiedź:**
```java
import java.math.*;

public record WaterfallStep(String name, BigDecimal price, BigDecimal deduction, String reason) { }

public class PriceWaterfall {
    public static class Result {
        public BigDecimal listPrice;
        public BigDecimal invoicePrice;
        public BigDecimal pocketPrice;
        public List<WaterfallStep> steps = new ArrayList<>();
    }
    
    public static Result calculate(WaterfallInput input) {
        Result r = new Result();
        BigDecimal current = input.getListPrice();
        r.listPrice = current;
        r.steps.add(new WaterfallStep("LIST_PRICE", current, BigDecimal.ZERO, "List price from catalog"));
        
        // Trade discount (on-invoice, % off list)
        if (input.getTradeDiscountPct() != null && input.getTradeDiscountPct().signum() > 0) {
            BigDecimal deduction = current.multiply(input.getTradeDiscountPct())
                .setScale(2, RoundingMode.HALF_UP);
            current = current.subtract(deduction);
            r.steps.add(new WaterfallStep("TRADE_DISCOUNT", current, deduction, 
                "Trade discount " + input.getTradeDiscountPct() + " (channel: " + input.getChannel() + ")"));
        }
        
        // Cash discount (on-invoice, % off for early payment)
        if (input.getCashDiscountPct() != null && input.getCashDiscountPct().signum() > 0) {
            BigDecimal deduction = current.multiply(input.getCashDiscountPct())
                .setScale(2, RoundingMode.HALF_UP);
            current = current.subtract(deduction);
            r.steps.add(new WaterfallStep("CASH_DISCOUNT", current, deduction, 
                "Cash discount " + input.getCashDiscountPct() + " (NET-" + input.getPaymentTermsDays() + ")"));
        }
        
        // Invoice price = list - on-invoice discounts
        r.invoicePrice = current;
        
        // Off-invoice rebate (% off invoice, paid later)
        if (input.getRebatePct() != null && input.getRebatePct().signum() > 0) {
            BigDecimal deduction = current.multiply(input.getRebatePct())
                .setScale(2, RoundingMode.HALF_UP);
            current = current.subtract(deduction);
            r.steps.add(new WaterfallStep("REBATE", current, deduction,
                "Rebate " + input.getRebatePct() + " (paid quarterly)"));
        }
        
        // Cooperative advertising / co-op
        if (input.getCoopAllowance() != null && input.getCoopAllowance().signum() > 0) {
            BigDecimal deduction = input.getCoopAllowance(); // fixed amount, not %
            current = current.subtract(deduction);
            r.steps.add(new WaterfallStep("COOP_AD", current, deduction,
                "Co-op advertising allowance"));
        }
        
        // Slotting fees (retailer charges to put product on shelf)
        if (input.getSlottingFee() != null && input.getSlottingFee().signum() > 0) {
            BigDecimal deduction = input.getSlottingFee();
            current = current.subtract(deduction);
            r.steps.add(new WaterfallStep("SLOTTING_FEE", current, deduction,
                "Slotting fee per unit"));
        }
        
        r.pocketPrice = current;
        r.steps.add(new WaterfallStep("POCKET_PRICE", r.pocketPrice, BigDecimal.ZERO, 
            "Final pocket price after all deductions"));
        
        return r;
    }
}

// Usage
WaterfallInput input = WaterfallInput.builder()
    .listPrice(new BigDecimal("100.00"))
    .channel("RETAIL")
    .tradeDiscountPct(new BigDecimal("0.15"))    // 15% trade discount
    .cashDiscountPct(new BigDecimal("0.02"))     // 2% cash discount NET-10
    .paymentTermsDays(10)
    .rebatePct(new BigDecimal("0.05"))           // 5% volume rebate
    .coopAllowance(new BigDecimal("3.00"))       // 3 PLN co-op per unit
    .slottingFee(new BigDecimal("1.50"))         // 1.50 PLN slotting per unit
    .build();

Result r = PriceWaterfall.calculate(input);
// Step 1: 100.00 (LIST_PRICE)
// Step 2: 85.00 (TRADE_DISCOUNT, -15.00)
// Step 3: 83.30 (CASH_DISCOUNT, -1.70)  -- 85*0.02=1.70
// Invoice: 83.30
// Step 4: 79.13 (REBATE, -4.16)         -- 83.30*0.05=4.165 round 4.17 (HALF_UP), 83.30-4.17=79.13
// Step 5: 76.13 (COOP_AD, -3.00)
// Step 6: 74.63 (SLOTTING_FEE, -1.50)
// Pocket: 74.63
// Margin erosion: 100 → 74.63 = 25.37% off list (visible vs hidden mix)
```

**Margin analysis:**
```java
public BigDecimal pocketMargin(Result r, BigDecimal cogs) {
    return r.pocketPrice.subtract(cogs).divide(r.pocketPrice, 4, RoundingMode.HALF_UP);
}
// Pocket price 74.63, COGS 50.00 → margin 33% (zdrowo? zależy od branży)
```

**Audit:**
- Każdy step zapisany w order line, czytelny breakdown.
- Sumę deduction per category (trade, cash, rebate) reportowane do finance.

**Edge cases:**
- All deductions zero → pocket = list (no pricing mechanics applied).
- Negative pocket — ostrzeżenie / błąd (sprzedaż poniżej zera = bug lub świadoma loss leader).
- Order of operations: trade → cash compound (cash applied to post-trade price). Inny order → inny wynik.

**Pułapka rozmowna:** Order matters. Konwencja: zawsze on-invoice first (trade, cash), potem off-invoice (rebate, fees). Druga: rounding cumulative — może wprowadzić cents drift. Standard: round at each step with consistent mode (HALF_UP).
**Tagi:** waterfall, pricing-math, java, pricing-engine

## Q-PRC-019 [bloom: apply]
**Pytanie:** Zaprojektuj API endpoint zwracający price quote z pełnym breakdown.
**Modelowa odpowiedź:**
```
POST /api/v1/pricing/quote
Content-Type: application/json
Authorization: Bearer <token>

{
  "customer_id": 12345,
  "items": [
    {"product_id": 100, "quantity": 50, "country": "PL"},
    {"product_id": 200, "quantity": 10, "country": "PL"}
  ],
  "currency": "PLN",
  "as_of_date": "2026-05-06",  // optional, default now
  "channel": "DIRECT_SALES",
  "promo_codes": ["SUMMER25"]
}

Response 200 OK:
{
  "quote_id": "Q-2026-05-06-abc123",
  "valid_until": "2026-05-13T23:59:59Z",
  "currency": "PLN",
  "customer": {
    "id": 12345,
    "name": "Acme Corp",
    "tier": "GOLD",
    "contract_id": "CTR-2024-0042"
  },
  "lines": [
    {
      "product_id": 100,
      "name": "Phone XYZ",
      "quantity": 50,
      "list_price": "299.00",
      "tier_price": "279.00",  // volume tier 50+
      "contract_price": "265.00",  // customer-specific override
      "applied_promotions": [
        {"code": "SUMMER25", "type": "PERCENT", "amount": "0.05", "savings": "13.25"}
      ],
      "unit_price_after_promo": "251.75",
      "line_subtotal": "12587.50",  // 50 * 251.75
      "tax": {"rate": "0.23", "amount": "2895.13"},
      "line_total": "15482.63"
    },
    {...}
  ],
  "totals": {
    "subtotal": "13442.50",
    "tax_total": "3091.78",
    "total": "16534.28",
    "savings": {
      "from_list": "1750.00",
      "from_tier": "1075.00",
      "from_contract": "390.00",
      "from_promo": "285.00",
      "total_savings": "3500.00"
    }
  },
  "applicable_rebates": [
    {"type": "QUARTERLY_VOLUME", "estimated_amount": "672.13", "payout_date": "2026-07-15"}
  ],
  "approvals_required": []  // empty if all auto-approved
}

Response 422 (validation):
{
  "error": "PRICING_ERROR",
  "message": "Cannot quote: contract expired",
  "details": [
    {"field": "customer_id", "code": "CONTRACT_EXPIRED", "message": "Contract CTR-2024-0042 expired 2026-04-01"}
  ]
}

Response 403 (authorization):
{
  "error": "FORBIDDEN",
  "message": "Discount exceeds your authorization level",
  "details": [
    {"line_index": 0, "max_allowed_discount": "0.10", "requested": "0.15"}
  ]
}
```

**Backend implementation outline:**
```java
@PostMapping("/api/v1/pricing/quote")
public ResponseEntity<QuoteResponse> generateQuote(@Valid @RequestBody QuoteRequest req) {
    // 1. Validate customer exists, contract active
    Customer customer = customerService.getActiveCustomer(req.customerId());
    
    // 2. For each line, calculate price (parallel)
    List<CompletableFuture<QuoteLine>> lineFutures = req.items().stream()
        .map(item -> CompletableFuture.supplyAsync(() -> 
            pricingService.priceLine(customer, item, req.asOfDate(), req.promoCodes())
        )).toList();
    
    List<QuoteLine> lines = lineFutures.stream()
        .map(CompletableFuture::join)
        .toList();
    
    // 3. Apply order-level promotions (e.g., free shipping if total > X)
    OrderLevelDiscount orderDiscount = promotionService.evaluateOrderLevel(lines, req.promoCodes());
    
    // 4. Calculate tax via tax service
    TaxBreakdown tax = taxService.calculate(lines, customer.getCountry());
    
    // 5. Calculate rebate eligibility
    List<RebateProjection> rebates = rebateService.project(customer, lines);
    
    // 6. Check if approvals needed
    List<ApprovalRequirement> approvals = approvalService.evaluate(customer, lines, currentUser());
    
    // 7. Generate quote document, save, return
    Quote quote = quoteRepository.save(new Quote(customer, lines, tax, rebates, approvals));
    auditService.log("QUOTE_GENERATED", quote.getId(), currentUser());
    
    return ResponseEntity.ok(QuoteResponse.from(quote));
}
```

**Performance considerations:**
- Lookup pricing per product is hot path → cache (per (product, country, segment) z TTL 5 min).
- Parallel per-line calculation — 50 items in parallel = ~same time as 1.
- Tax service may be slow (external) — async, with circuit breaker.

**Audit:**
- Each quote stored with full breakdown.
- Customer can request re-issue with same parameters.
- Modifications create new version (immutable history).

**Pułapka rozmowna:** Quote vs order: quote = price commitment for X days, order = actual purchase. Quote `valid_until` matters — after expiry, re-quote (price might've changed). Druga: tax calculation jurisdictional — for B2B in EU, customer's VAT ID changes treatment.
**Tagi:** api-design, quote, pricing, breakdown

## Q-PRC-020 [bloom: apply]
**Pytanie:** Pokaż jak zaprojektować "what-if scenario" feature: user simulate zmianę price 5% w produkcie, system pokazuje impact na revenue za ostatni kwartał.
**Modelowa odpowiedź:**
```
POST /api/v1/pricing/whatif/simulate
{
  "scenario_name": "Phone XYZ +5% in PL",
  "changes": [
    {
      "type": "PRICE_CHANGE",
      "product_id": 100,
      "country": "PL",
      "segment": null,
      "old_price": "299.00",
      "new_price": "313.95",
      "delta_pct": "0.05"
    }
  ],
  "baseline_period": {
    "from": "2026-01-01",
    "to": "2026-03-31"
  },
  "elasticity_assumption": "USE_HISTORICAL_MODEL"  // or specific value
}

Response 200:
{
  "scenario_id": "SIM-2026-05-06-xyz789",
  "baseline": {
    "period": "2026-Q1",
    "actual_revenue": "1500000.00",
    "actual_units": "5000",
    "actual_margin": "375000.00"
  },
  "projected": {
    "period": "2026-Q1 simulated",
    "projected_revenue": "1485000.00",
    "projected_units": "4750",  // 5% drop volume from elasticity
    "projected_margin": "395000.00"
  },
  "impact": {
    "revenue_delta_abs": "-15000.00",
    "revenue_delta_pct": "-0.01",
    "units_delta_abs": "-250",
    "units_delta_pct": "-0.05",
    "margin_delta_abs": "+20000.00",
    "margin_delta_pct": "+0.053"
  },
  "elasticity_used": "-1.0",  // unit elastic
  "confidence": "MEDIUM",  // based on data quality
  "warnings": [
    "Elasticity model based on past 12 months — may not reflect future behavior",
    "Cross-elasticity with substitute products NOT included"
  ]
}
```

**Implementation:**
```java
@PostMapping("/api/v1/pricing/whatif/simulate")
public SimulationResponse simulate(@Valid @RequestBody SimulationRequest req) {
    // 1. Load baseline data
    SalesBaseline baseline = baselineService.aggregate(
        req.changes().stream().map(c -> c.productId()).toList(),
        req.baselinePeriod()
    );
    
    // 2. Load elasticity model
    ElasticityModel model = elasticityRepository.getActiveModel();
    
    // 3. Apply changes, calculate projection
    ProjectionResult projection = projectionEngine.project(baseline, req.changes(), model);
    
    // 4. Save scenario
    Scenario scenario = scenarioRepository.save(new Scenario(currentUser(), req, projection));
    
    return SimulationResponse.from(scenario, baseline, projection);
}

public class ProjectionEngine {
    public ProjectionResult project(SalesBaseline baseline, List<Change> changes, ElasticityModel model) {
        BigDecimal projectedRevenue = baseline.getRevenue();
        BigDecimal projectedUnits = baseline.getUnits();
        BigDecimal projectedMargin = baseline.getMargin();
        
        for (Change change : changes) {
            BigDecimal elasticity = model.getElasticity(change.productId(), change.country());
            // delta_units / delta_price = elasticity
            // delta_units = elasticity * delta_price * baseline_units
            BigDecimal volumeDelta = elasticity
                .multiply(change.deltaPct())
                .multiply(baseline.getUnitsForProduct(change.productId()));
            
            // New units
            BigDecimal newUnits = baseline.getUnitsForProduct(change.productId()).add(volumeDelta);
            
            // New revenue = new_units * new_price
            BigDecimal newRevenue = newUnits.multiply(change.newPrice());
            BigDecimal oldRevenue = baseline.getRevenueForProduct(change.productId());
            BigDecimal revenueDelta = newRevenue.subtract(oldRevenue);
            
            // Margin = revenue - cost (assuming cost stable)
            BigDecimal marginDelta = revenueDelta; // simplification: full delta to margin
            
            projectedRevenue = projectedRevenue.add(revenueDelta);
            projectedUnits = projectedUnits.add(volumeDelta);
            projectedMargin = projectedMargin.add(marginDelta);
        }
        
        return new ProjectionResult(projectedRevenue, projectedUnits, projectedMargin);
    }
}
```

**Storage:**
```sql
CREATE TABLE scenario (
  id UUID PRIMARY KEY,
  name VARCHAR(255),
  created_by VARCHAR(50),
  created_at TIMESTAMPTZ,
  request JSONB,           -- input
  baseline_data JSONB,     -- snapshot of historical
  projection JSONB,        -- result
  status VARCHAR(20)        -- DRAFT, SAVED, ARCHIVED
);
```

**UI flow:**
1. User selects product + country + delta in UI.
2. UI calls POST simulate, displays charts.
3. User saves scenario or modifies and re-runs.
4. Comparison: select 2-3 scenarios, show side-by-side impacts.

**Advanced features:**
- **Cross-elasticity** — change product A price, model substitution to B.
- **Cannibalization** — promo on A reduces B sales.
- **Time-aware:** "if I change price now, projected impact over next 12 months month-by-month".
- **Confidence intervals:** "P10/P50/P90 — most likely range $X-$Y revenue impact".
- **Sensitivity:** "what if elasticity is -0.8 vs -1.2?".

**Pułapka rozmowna:** Models based on historical — may not reflect future (changed competition, weather, brand strength). Always show confidence/caveats. Druga: sales push back: „w prawdziwym życiu byłoby lepsze". Often. Use simulation as decision support, not single source of truth.
**Tagi:** what-if, simulation, elasticity, api

## Q-PRC-021 [bloom: apply]
**Pytanie:** Customer changes contract: zamiast 10% off list, teraz negocjuje 5% off + 5% rebate. Implementuj kalkulację dla obu i porównaj.
**Modelowa odpowiedź:**
```java
import java.math.*;

public class ContractComparisonService {
    
    public record ContractTerms(
        BigDecimal discountPct,    // on-invoice
        BigDecimal rebatePct,      // off-invoice
        String label
    ) {}
    
    public record ContractImpact(
        BigDecimal listPrice,
        BigDecimal invoicePrice,
        BigDecimal effectivePocketPrice,
        BigDecimal customerSaves,
        BigDecimal cashFlowDelay  // days till rebate paid
    ) {}
    
    public ContractImpact calculate(BigDecimal listPrice, ContractTerms terms, int rebatePayoutDays) {
        // On-invoice discount
        BigDecimal invoicePrice = listPrice.multiply(BigDecimal.ONE.subtract(terms.discountPct()))
            .setScale(2, RoundingMode.HALF_UP);
        
        // Off-invoice rebate (paid later)
        BigDecimal rebateAmount = invoicePrice.multiply(terms.rebatePct())
            .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal effectivePocketPrice = invoicePrice.subtract(rebateAmount);
        BigDecimal customerSaves = listPrice.subtract(effectivePocketPrice);
        
        return new ContractImpact(
            listPrice,
            invoicePrice,
            effectivePocketPrice,
            customerSaves,
            terms.rebatePct().signum() > 0 ? BigDecimal.valueOf(rebatePayoutDays) : BigDecimal.ZERO
        );
    }
    
    public ComparisonResult compare(BigDecimal listPrice) {
        ContractTerms oldTerms = new ContractTerms(
            new BigDecimal("0.10"),      // 10% discount
            BigDecimal.ZERO,              // 0% rebate
            "Old: 10% discount on-invoice"
        );
        
        ContractTerms newTerms = new ContractTerms(
            new BigDecimal("0.05"),      // 5% discount
            new BigDecimal("0.05"),      // 5% rebate (off-invoice, paid quarterly)
            "New: 5% discount + 5% rebate"
        );
        
        ContractImpact oldImpact = calculate(listPrice, oldTerms, 0);
        ContractImpact newImpact = calculate(listPrice, newTerms, 90); // quarterly
        
        return new ComparisonResult(oldImpact, newImpact);
    }
}

// Test
BigDecimal listPrice = new BigDecimal("1000.00");
ComparisonResult cmp = service.compare(listPrice);

// Old: 10% discount
// listPrice = 1000.00
// invoicePrice = 1000 * 0.90 = 900.00
// rebate = 0
// effectivePocket = 900.00
// customerSaves = 100.00
// cashFlowDelay = 0 (no rebate)

// New: 5% + 5%
// listPrice = 1000.00
// invoicePrice = 1000 * 0.95 = 950.00
// rebate = 950 * 0.05 = 47.50
// effectivePocket = 950 - 47.50 = 902.50
// customerSaves = 97.50
// cashFlowDelay = 90 days
```

**Analysis output:**

| Metric | Old (10% disc) | New (5%+5%) | Delta |
|--------|----------------|-------------|-------|
| List | 1000.00 | 1000.00 | - |
| Invoice price | 900.00 | 950.00 | +50.00 |
| Rebate | 0 | 47.50 | +47.50 (deferred) |
| Effective pocket | 900.00 | 902.50 | +2.50 |
| Customer total saves | 100.00 | 97.50 | -2.50 |
| Days till rebate | 0 | 90 | +90 |

**Implications:**

- **Customer pays MORE on invoice** (+50, 50/950 ≈ 5.3% more cash up front).
- **Customer's effective price slightly HIGHER** (+2.50, because rebate compounds: 5% off invoice 950 = less in absolute terms than 10% off 1000).
- **Cash flow win for vendor** — invoice 950 immediately, rebate 47.50 paid 90 days later.
- **Cash flow loss for customer** — pays more upfront, gets less back later.

**This is a SUBTLE move that benefits vendor** if customer doesn't analyze:
- Vendor gets cash sooner.
- If customer doesn't qualify for full rebate (e.g., min volume not met), vendor doesn't pay it out.
- Customer thinks "still 10% total discount" but effectively losing 0.25 percentage points + cash flow hit.

**Real-world considerations:**
- **Volume threshold for rebate:** customer must hit min sales to earn rebate. If not, they get 0% rebate, only 5% upfront discount → effective 5% off, much worse than 10% old.
- **Customer credit risk:** if customer goes bankrupt before rebate paid, vendor wins.
- **Reporting:** invoice revenue HIGHER under new (950 vs 900) — vendor's reported revenue improves, even though net economics similar.

**Negotiation insight:**
- Customer should demand 5% + 6% rebate to break even (or push back to 10% straight).
- Sales rep often pitches 5%+5% as „same total" which is mathematically false (compound vs linear).

**Pricing engine support:**
- Both formats supported in contract schema.
- Quote tool shows full breakdown — invoice price, rebate eligibility, effective pocket.
- Customer dashboard tracks YTD rebate accrual.

**Pułapka rozmowna:** „5% + 5% = 10%" — false jeśli compound. 5% off list + 5% off post-discount = 9.75% total off list. Subtle. Druga: rebate w accounting jest accrual liability — vendor records revenue 950 ale puts 47.50 in liability. Tax implications dla obu stron varying.
**Tagi:** rebate, discount, contract, pricing-math

## Q-PRC-022 [bloom: apply]
**Pytanie:** Implementuj pricing engine która respektuje multiple sources hierarchy: contract price → tier price (volume) → list price.
**Modelowa odpowiedź:**
```java
import java.math.*;
import java.time.*;
import java.util.*;

public class PricingEngine {
    private final ContractRepository contractRepo;
    private final VolumeTierRepository tierRepo;
    private final ProductRepository productRepo;
    private final PromotionService promotionService;
    
    public PriceResult calculatePrice(PricingContext ctx) {
        // Layer 1: Customer-specific contract price
        Optional<ContractPriceRule> contractRule = findActiveContractRule(ctx);
        if (contractRule.isPresent()) {
            return new PriceResult(
                contractRule.get().getPrice(),
                "CONTRACT",
                contractRule.get().getContractId()
            );
        }
        
        // Layer 2: Volume tier price
        Optional<VolumeTier> tier = tierRepo.findActiveTier(
            ctx.getProductId(),
            ctx.getCountry(),
            ctx.getCustomer().getSegment(),
            ctx.getQuantity(),
            ctx.getAsOfDate()
        );
        if (tier.isPresent()) {
            BigDecimal price = tier.get().getUnitPrice();
            // Apply standard promotions on top of tier price
            price = applyStandardPromotions(price, ctx);
            return new PriceResult(price, "VOLUME_TIER", tier.get().getId().toString());
        }
        
        // Layer 3: List price
        Product product = productRepo.findById(ctx.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(ctx.getProductId()));
        BigDecimal price = product.getBasePrice();
        price = applyStandardPromotions(price, ctx);
        return new PriceResult(price, "LIST", "BASE_" + product.getId());
    }
    
    private Optional<ContractPriceRule> findActiveContractRule(PricingContext ctx) {
        Optional<Contract> contract = contractRepo.findActiveContract(
            ctx.getCustomer().getId(),
            ctx.getAsOfDate()
        );
        if (contract.isEmpty()) return Optional.empty();
        
        return contractRepo.findRule(
            contract.get().getId(),
            ctx.getProductId(),
            ctx.getCountry(),
            ctx.getQuantity(),
            ctx.getAsOfDate()
        );
    }
    
    private BigDecimal applyStandardPromotions(BigDecimal basePrice, PricingContext ctx) {
        List<Promotion> applicablePromos = promotionService.findApplicable(
            ctx.getProductId(),
            ctx.getCustomer().getSegment(),
            ctx.getCountry(),
            ctx.getChannel(),
            ctx.getAsOfDate(),
            ctx.getPromoCodes()
        );
        
        BigDecimal result = basePrice;
        for (Promotion promo : applicablePromos) {
            result = applyPromotion(result, promo);
        }
        return result;
    }
    
    private BigDecimal applyPromotion(BigDecimal price, Promotion promo) {
        return switch (promo.getType()) {
            case PERCENT -> price.multiply(BigDecimal.ONE.subtract(promo.getValue()))
                .setScale(2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> price.subtract(promo.getValue()).max(BigDecimal.ZERO);
            case BUNDLE -> applyBundleLogic(price, promo);
        };
    }
}

public record PricingContext(
    Long productId,
    Customer customer,
    int quantity,
    String country,
    String channel,
    Instant asOfDate,
    List<String> promoCodes
) {}

public record PriceResult(
    BigDecimal price,
    String source,        // CONTRACT, VOLUME_TIER, LIST
    String sourceRef      // Contract ID, Tier ID, etc.
) {}
```

**Audit logging:**
```java
@Component
public class PricingAuditAspect {
    @AfterReturning(pointcut = "execution(* PricingEngine.calculatePrice(..))", returning = "result")
    public void log(PricingContext ctx, PriceResult result) {
        auditLog.info("PRICE_CALCULATED customer={}, product={}, quantity={}, country={}, " +
                     "result_price={}, source={}, ref={}, asOf={}",
            ctx.getCustomer().getId(), ctx.getProductId(), ctx.getQuantity(), ctx.getCountry(),
            result.getPrice(), result.getSource(), result.getSourceRef(), ctx.getAsOfDate());
    }
}
```

**Caching strategy:**
- L1 cache (Caffeine, in-memory): per (product, country, segment, customer, quantity_band, asOf_date) → result. TTL 5 min.
- L2 cache (Redis, distributed): jeśli wiele app instances.
- Invalidation: event-driven. `contract_changed`, `price_updated`, `promotion_started` events flushują relevant entries.

**Testing:**
```java
@Test
void contractPriceWinsOverTierAndList() {
    Customer customer = customerWithContract("CTR-2024", productId, fixedPrice("85.00"));
    PricingContext ctx = ctxFor(customer, productId, qty=50);
    
    PriceResult r = engine.calculatePrice(ctx);
    assertEquals(new BigDecimal("85.00"), r.getPrice());
    assertEquals("CONTRACT", r.getSource());
}

@Test
void tierPriceWinsWhenNoContract() {
    Customer customer = customerWithoutContract();
    setupVolumeTier(productId, country="PL", minQty=50, unitPrice="90.00");
    PricingContext ctx = ctxFor(customer, productId, qty=50);
    
    PriceResult r = engine.calculatePrice(ctx);
    assertEquals(new BigDecimal("90.00"), r.getPrice());
    assertEquals("VOLUME_TIER", r.getSource());
}

@Test
void listPriceWhenNothingMatches() {
    Customer customer = customerWithoutContract();
    setupBaselineProduct(productId, basePrice="100.00");
    PricingContext ctx = ctxFor(customer, productId, qty=1);  // below tier
    
    PriceResult r = engine.calculatePrice(ctx);
    assertEquals(new BigDecimal("100.00"), r.getPrice());
    assertEquals("LIST", r.getSource());
}
```

**Edge cases:**
- Contract has rule for some products but not all → fallback to tier/list per product.
- Multiple active contracts for same customer (rare but possible) → priority by signed_date or explicit priority field.
- Date boundary: contract ends 23:59:59, query at 00:00:00 next day → use closed-open intervals consistently.

**Pułapka rozmowna:** Returning only price, not source — debugging nightmare („why this customer paid X?"). Always source + ref. Druga: applying promotions to contract price — depends on policy. Some contracts say „contract price IS final, no promo stack". Others allow. Make it explicit in contract terms.
**Tagi:** pricing-engine, hierarchy, contract, implementation

## Q-PRC-023 [bloom: apply]
**Pytanie:** Implementuj approval workflow dla price exception (rabat > 15% wymaga manager approval).
**Modelowa odpowiedź:**
```java
public enum ApprovalLevel {
    AUTO,                  // < 5%
    SALES_MANAGER,         // 5-15%
    REGIONAL_DIRECTOR,     // 15-25%
    VP_SALES,              // > 25%
    DENIED                 // > 50% (configurable cap)
}

public class ApprovalService {
    private final ApprovalRepository repo;
    private final NotificationService notifications;
    private final UserService userService;
    
    public ApprovalLevel determineLevel(BigDecimal discountPct, BigDecimal orderValue) {
        BigDecimal pct = discountPct.abs(); // ujemne = price drop, dodatnie = increase, wszystkie znaczne to issue
        
        if (pct.compareTo(new BigDecimal("0.50")) > 0) return ApprovalLevel.DENIED;
        if (pct.compareTo(new BigDecimal("0.25")) > 0) return ApprovalLevel.VP_SALES;
        if (pct.compareTo(new BigDecimal("0.15")) > 0) return ApprovalLevel.REGIONAL_DIRECTOR;
        if (pct.compareTo(new BigDecimal("0.05")) > 0) return ApprovalLevel.SALES_MANAGER;
        return ApprovalLevel.AUTO;
    }
    
    public ExceptionRequest submitException(ExceptionRequestInput input, User requester) {
        ApprovalLevel level = determineLevel(input.getDiscountPct(), input.getOrderValue());
        
        if (level == ApprovalLevel.DENIED) {
            throw new IllegalArgumentException("Discount " + input.getDiscountPct() + " exceeds maximum allowed");
        }
        
        ExceptionRequest req = ExceptionRequest.builder()
            .id(UUID.randomUUID())
            .requesterId(requester.getId())
            .customerId(input.getCustomerId())
            .productId(input.getProductId())
            .standardPrice(input.getStandardPrice())
            .requestedPrice(input.getRequestedPrice())
            .discountPct(input.getDiscountPct())
            .reason(input.getReason())
            .approvalLevel(level)
            .status(level == ApprovalLevel.AUTO ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofDays(7)))
            .build();
        
        repo.save(req);
        
        if (level != ApprovalLevel.AUTO) {
            User approver = userService.findApproverFor(requester, level);
            notifications.notify(approver, req);
        }
        
        return req;
    }
    
    public ExceptionRequest approve(UUID requestId, User approver, String comment) {
        ExceptionRequest req = repo.findById(requestId)
            .orElseThrow(() -> new RequestNotFoundException(requestId));
        
        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request already " + req.getStatus());
        }
        
        // Check that approver has authority for this level
        if (!userService.hasAuthority(approver, req.getApprovalLevel())) {
            throw new InsufficientAuthorityException(approver, req.getApprovalLevel());
        }
        
        // Cannot approve own request
        if (Objects.equals(approver.getId(), req.getRequesterId())) {
            throw new SelfApprovalException();
        }
        
        req.setStatus(ApprovalStatus.APPROVED);
        req.setApprovedBy(approver.getId());
        req.setApprovedAt(Instant.now());
        req.setApprovalComment(comment);
        repo.save(req);
        
        notifications.notify(userService.findById(req.getRequesterId()), req); // notify requester
        return req;
    }
    
    public ExceptionRequest reject(UUID requestId, User approver, String reason) {
        // Similar to approve but sets REJECTED status
    }
}

// REST endpoint
@PostMapping("/api/v1/pricing/exceptions")
public ResponseEntity<ExceptionRequestDto> submit(@Valid @RequestBody ExceptionRequestInput input, 
                                                    @AuthenticationPrincipal User user) {
    ExceptionRequest req = approvalService.submitException(input, user);
    return ResponseEntity
        .status(req.getStatus() == ApprovalStatus.APPROVED ? HttpStatus.OK : HttpStatus.ACCEPTED)
        .body(ExceptionRequestDto.from(req));
}

@PostMapping("/api/v1/pricing/exceptions/{id}/approve")
public ExceptionRequestDto approve(@PathVariable UUID id, 
                                    @RequestBody ApprovalCommand cmd,
                                    @AuthenticationPrincipal User user) {
    ExceptionRequest req = approvalService.approve(id, user, cmd.getComment());
    return ExceptionRequestDto.from(req);
}
```

**State machine:**
```
DRAFT → PENDING (after submit, if not auto)
DRAFT → APPROVED (auto if level == AUTO)
PENDING → APPROVED (after approver action)
PENDING → REJECTED (after approver action)
PENDING → EXPIRED (after expires_at)
APPROVED → CANCELLED (if requester cancels)
```

**Notifications:**
- Email + Slack/Teams for approver.
- Inbox in CRM/sales tool.
- SLA: approval within X hours, escalate if not.

**Audit:**
```sql
CREATE TABLE approval_audit (
  id UUID PRIMARY KEY,
  request_id UUID REFERENCES exception_request(id),
  action VARCHAR(20),  -- CREATED, APPROVED, REJECTED, EXPIRED, CANCELLED
  actor_id BIGINT,
  occurred_at TIMESTAMPTZ,
  details JSONB
);
```

**Reporting:**
- Approval throughput: average time to approve, by level.
- Rejection rate: how often approvers reject.
- Exception frequency: per region, per product, per sales rep.
- Hint: if same product gets exceptions repeatedly, list price might be wrong.

**Edge cases:**
- Approver out of office — delegation rules.
- Multi-level (rabat 30% needs both regional + VP) — sequential approval.
- Bulk approvals (whole quote) — single decision, multiple lines covered.
- Re-submission after rejection — new request or revised existing?

**Pułapka rozmowna:** Self-approval — requester == approver. Block in code, audit any attempt. Druga: approval expiry — request pending forever blocks deal. Auto-reject after timeout, force re-submit (with reasons).
**Tagi:** approval-workflow, exception-management, pricing

## Q-PRC-024 [bloom: apply]
**Pytanie:** Implementuj price calendar query — wszystkie zaplanowane zmiany cen na produkt w nadchodzących 6 miesiącach.
**Modelowa odpowiedź:**
```java
public class PriceCalendarService {
    private final PriceCalendarRepository repo;
    
    public List<PriceCalendarEntry> getUpcomingChanges(Long productId, String country, Duration window) {
        Instant now = Instant.now();
        Instant horizon = now.plus(window);
        
        return repo.findUpcoming(productId, country, now, horizon);
    }
    
    public List<PriceTimeline> getTimeline(Long productId, String country, Duration window) {
        // Get current price + all upcoming changes
        Instant now = Instant.now();
        Instant horizon = now.plus(window);
        
        BigDecimal currentPrice = repo.getEffectivePrice(productId, country, now);
        List<PriceCalendarEntry> upcoming = repo.findUpcoming(productId, country, now, horizon);
        
        List<PriceTimeline> timeline = new ArrayList<>();
        timeline.add(new PriceTimeline(now, currentPrice, "CURRENT"));
        
        for (PriceCalendarEntry entry : upcoming) {
            timeline.add(new PriceTimeline(
                entry.getValidFrom(),
                entry.getPrice(),
                entry.getReason()
            ));
        }
        
        return timeline;
    }
}

@Repository
public class PriceCalendarRepository {
    @Query("""
        SELECT * FROM price_calendar
        WHERE product_id = :productId
          AND country = :country
          AND valid_from > :from
          AND valid_from <= :to
        ORDER BY valid_from ASC
        """)
    List<PriceCalendarEntry> findUpcoming(
        @Param("productId") Long productId,
        @Param("country") String country,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
    
    @Query("""
        SELECT price FROM price_calendar
        WHERE product_id = :productId
          AND country = :country
          AND valid_from <= :asOf
          AND (valid_to IS NULL OR valid_to > :asOf)
        ORDER BY effective_priority DESC, valid_from DESC
        LIMIT 1
        """)
    BigDecimal getEffectivePrice(
        @Param("productId") Long productId,
        @Param("country") String country,
        @Param("asOf") Instant asOf
    );
}

// REST endpoint
@GetMapping("/api/v1/products/{id}/price-calendar")
public PriceCalendarResponse getCalendar(
    @PathVariable Long id,
    @RequestParam String country,
    @RequestParam(defaultValue = "180") int days
) {
    Duration window = Duration.ofDays(days);
    List<PriceTimeline> timeline = service.getTimeline(id, country, window);
    return PriceCalendarResponse.from(timeline);
}
```

**Response example:**
```json
GET /api/v1/products/100/price-calendar?country=PL&days=180

{
  "product_id": 100,
  "country": "PL",
  "current_date": "2026-05-06T12:00:00Z",
  "horizon_date": "2026-11-02T12:00:00Z",
  "timeline": [
    {
      "effective_from": "2026-05-06T12:00:00Z",
      "price": "299.00",
      "reason": "CURRENT",
      "source": "manual_pricing_review_q1"
    },
    {
      "effective_from": "2026-06-01T00:00:00Z",
      "price": "315.00",
      "reason": "scheduled_quarterly_review",
      "delta_pct": "+0.0535",
      "source": "pricing_committee_approved_2026_05_15"
    },
    {
      "effective_from": "2026-09-01T00:00:00Z",
      "price": "299.00",
      "reason": "post_summer_promotion_end",
      "delta_pct": "-0.0508"
    }
  ]
}
```

**UI representation:**

```
Price Calendar: Phone XYZ in PL

  [Today]  ─────────  299 PLN  ─────────  [Jun 1]  ↑ 5.35%
                                          315 PLN  ─────────  [Sep 1]  ↓ 5.08%
                                                              299 PLN
```

**Edge cases:**
- **No upcoming changes** — return only current.
- **Multiple entries for same date** — `effective_priority` determines winner.
- **Promotions overlap base changes** — separate query level (calendar = base + scheduled, promotions = separate stream).
- **Backdated entries** — entries with `valid_from < now`. Don't include in upcoming, but in `getEffectivePrice` they're already applied.

**What-if integration:** API can take `?as_of=2026-06-15` to show timeline as it would look on that date (useful for pre-publishing communication).

**Notifications:** when price about to change, notify subscribers (sales reps, customers via email, etc.).

**Pułapka rozmowna:** Timezone in `valid_from` — UTC vs local. Document. Druga: `effective_priority` dla overrides — promo z priority 100 może override base price z priority 10 nawet jeśli base ma later valid_from. Subtle, audit critical.
**Tagi:** price-calendar, query, api, temporal

---

## Q-PRC-025 [bloom: analyze]
**Pytanie:** Jako engineer pricing platformy, jaki design pattern wybierasz dla complex pricing rules: hardcoded if-else, rule engine (Drools), DSL (Groovy), albo data-driven config?
**Modelowa odpowiedź:** Trade-offy każdego:

**1. Hardcoded if-else (Java/Groovy logic):**
- ✓ Performance: native code, fastest.
- ✓ Type-safe, compile-time check.
- ✓ Easy to debug.
- ✗ Every rule change = code change + deploy.
- ✗ Business team can't edit.
- ✗ Scale: 100+ rules become unmanageable.
- **Verdict:** OK for very stable pricing (rarely changing) lub small team.

**2. Rule engine (Drools, Easy Rules, Decision Tables):**
- ✓ Declarative, business-readable rules.
- ✓ Hot-reload (change rules without restart).
- ✓ Decision tables (Excel-style) — non-techy can edit.
- ✗ Performance overhead (rule evaluation engine).
- ✗ Learning curve.
- ✗ Debugging trickier (rule firing order, conflicts).
- ✗ Drools heavy dependency.
- **Verdict:** Good for many complex rules, business team participation, frequently changing.

**3. DSL (Groovy / Kotlin / custom):**
- ✓ Expressive — business rules look like business rules.
- ✓ Sandboxed Groovy can be evaluated dynamically.
- ✓ Powerful — full programming constructs available.
- ✗ Security risk if untrusted input (sandbox is hard).
- ✗ Performance vs compiled.
- ✗ Hard to validate before deploy.
- **Verdict:** Sweet spot for technical pricing teams who want rule expression power. Common in enterprise pricing engines (e.g., proprietary DSLs in vendors like Vendavo, PROS).

**4. Data-driven config (DB-stored configs, JSON-based):**
- ✓ Simple to update (CRUD on table).
- ✓ Auditable changes.
- ✓ No code changes for new rules of supported type.
- ✗ Limited expressiveness — only types of rules code supports.
- ✗ For new rule types, code change still needed.
- **Verdict:** Best for templated rules (e.g., "discount X% if customer in segment Y" — well-defined pattern).

**Hybrid approach (real-world):**
- **Config-driven for 80% common patterns** (volume tier, segment discount, promo % off).
- **Rule engine OR DSL for complex 20%** (multi-product bundle with cascading discounts, customer-specific overrides).
- **Hardcoded for performance-critical hot path** (e.g., final margin floor enforcement).

**Implementation example for pricing engine:**
```java
public interface PricingRule {
    boolean applies(PricingContext ctx);
    BigDecimal apply(BigDecimal currentPrice, PricingContext ctx);
    int getPriority();
}

// Config-driven simple rules
public class SegmentDiscountRule implements PricingRule {
    private String segment;
    private BigDecimal discountPct;
    // ... loaded from DB
}

// Programmatic complex rules
public class BundleDiscountRule implements PricingRule {
    public BigDecimal apply(BigDecimal currentPrice, PricingContext ctx) {
        // Logic: if cart has products A AND B, discount C by 10%
        if (ctx.cartHas(A, B) && ctx.getProductId() == C) {
            return currentPrice.multiply(new BigDecimal("0.90"));
        }
        return currentPrice;
    }
}

// Engine applies rules in priority order
public class RuleBasedPricingEngine {
    public BigDecimal calculate(PricingContext ctx) {
        BigDecimal price = ctx.getBasePrice();
        List<PricingRule> rules = ruleLoader.loadAll();
        rules.sort(Comparator.comparing(PricingRule::getPriority));
        
        for (PricingRule rule : rules) {
            if (rule.applies(ctx)) {
                price = rule.apply(price, ctx);
            }
        }
        return price;
    }
}
```

**Pricing-specific recommendation:**

For pricing engine startup → **config-driven first**. Common patterns covered, fast to ship.

When complexity grows (after PMF) → **add Groovy DSL or Drools** for the 20% complex cases. Don't start there — premature complexity.

For massive enterprise → **commercial pricing platforms** (Pricefx, Vendavo) which combine all approaches with workflow, audit, simulations.

**Pułapka rozmowna:** „Drools jest standard" — was. Many companies migrate away from Drools (heavy, complex). Modern alternatives: simpler libraries (RuleBook, Easy Rules), or custom DSL. Druga: „Config-driven jest enough" — for stable small business yes; for evolving complex pricing, programmable rules become necessity.
**Tagi:** rule-engine, dsl, architecture, decision

## Q-PRC-026 [bloom: analyze]
**Pytanie:** Pricing platform ma zostać udostępniona partnerom (white-label / multi-tenant). Jakie wyzwania pricingowe?
**Modelowa odpowiedź:** Multi-tenant pricing platform — każdy partner customer-tenant ma własne dane, własne reguły, ale shared infrastructure. **Wyzwania:**

**1. Data isolation:**
- **Schema-per-tenant** — każdy tenant own DB schema. Plus: strict isolation, per-tenant backup. Minus: schema migrations skalują N×.
- **Row-level security** — single schema, każda tabela ma `tenant_id`, queries auto-filter. Plus: simpler operational. Minus: query bugs leak data, performance contention.
- **DB-per-tenant** — extreme isolation. Costly, but compliance-friendly.

**2. Customization vs standardization:**
- Tenant A wants tier discounts in increments of 5%. Tenant B wants free-form.
- Tenant A's rule "BOGO" = "get B free". Tenant B's BOGO = "B at 50% off".
- **Solution:** flexible config schema (JSONB columns), customizable rule builder UI.

**3. Performance / noisy neighbor:**
- One tenant with 10M products + 1B daily price queries dominates resources.
- **Solution:** rate limiting per tenant, dedicated resources for premium tiers, isolation at app level (separate Kubernetes pods per tenant).

**4. Pricing for the platform itself:**
- How do you charge tenants for using your platform?
- Per-API-call, per-product, per-revenue percentage, flat fee, tiered.
- Most successful: tiered (flat fee for small, % for enterprise).

**5. White-label branding:**
- Tenant's UI must show their brand, not yours.
- Sub-domains (tenant.platform.com) or custom domains (pricing.tenant.com).
- Custom logos, colors, terms of service.

**6. Compliance per-tenant:**
- Tenant in EU needs GDPR compliance.
- Tenant in healthcare needs HIPAA.
- Tenant in financial needs SOC 2.
- Platform must support all, or restrict tenants to compatible profiles.

**7. Integration ecosystem:**
- Tenant's ERP (SAP, Oracle, Dynamics) varies.
- Tenant's CRM varies.
- **Solution:** standard webhook + adapter layer per major integration. Marketplace of integrations.

**8. SLAs & reliability:**
- Tenant A's outage of platform = tenant A loses revenue.
- Platform downtime = ALL tenants down.
- High availability requirements (99.95% uptime SLA).

**9. Cross-tenant features (controversial):**
- Benchmarking (how does my pricing compare to similar businesses)?
- Privacy concerns — anonymized aggregates only.
- Differential privacy techniques.

**10. Migration and onboarding:**
- New tenant joins → import their pricing data (CSV, API, legacy system).
- Self-service vs assisted onboarding.
- Time-to-value: how quickly can tenant start using?

**11. Tenant lifecycle:**
- Free trial → conversion to paid.
- Upgrade tiers.
- Suspended (non-payment) — keep data, block API.
- Off-boarding — export, delete (GDPR right to erasure).

**12. Per-tenant feature flags:**
- Tenant A on enterprise tier has feature X.
- Tenant B on starter tier doesn't.
- Feature flag system per-tenant + per-feature.

**13. Localization:**
- Different tenants in different countries — currency, language, locale defaults.
- Per-tenant configuration (default currency, locale).

**14. Audit:**
- Each tenant's audit log isolated.
- Platform admins can see all (with permission).
- Tenant admins see only own.

**15. Support and incident response:**
- Tenant raises ticket — agent needs context (their tenant data).
- Tools to "impersonate" tenant for debugging (with audit).

**Architecture decisions:**
- **API gateway per tenant** — routing, rate limiting, auth (JWT with tenant_id claim).
- **Service mesh** — observability per tenant.
- **Database** — start with shared schema + RLS for cost; migrate hot tenants to dedicated DBs as scale.
- **Background jobs** — separate queue per tenant or priority lanes.

**Common patterns:**
- **Tenant context** propagated in every API call (header `X-Tenant-Id` or JWT claim).
- **Spring Boot multi-tenancy:** Hibernate filter, RLS, or schema-per-tenant.

**Real-world examples:** Stripe (payments), Shopify (e-commerce), Vendavo (B2B pricing), all are multi-tenant pricing-related platforms.

**Pułapka rozmowna:** Data isolation by app-level alone (without DB-level RLS) — if app code has bug, leaks. Defense in depth: app filtering + DB RLS + audit. Druga: assuming all tenants are similar — sales pitches everything; reality is each tenant has unique edge cases. Build in flexibility.
**Tagi:** multi-tenant, platform, architecture, decision

## Q-PRC-027 [bloom: analyze]
**Pytanie:** Twój pricing engine został dotknięty incidentem: wszystkie ceny zwracały 0 przez 30 minut. Jak prowadzisz post-mortem?
**Modelowa odpowiedź:** Post-mortem template (blameless, focused on systemic improvements):

**1. Incident summary:**
- **Title:** "Pricing API zwracało 0 PLN dla 30 minut, 2026-05-06 14:30 - 15:00 UTC"
- **Severity:** SEV-1 (revenue impact, customer-facing).
- **Affected systems:** Pricing API, dependent services (CPQ, e-commerce, partner integrations).
- **Detection:** Monitoring alarm at 14:35 (5 min from start) — error rate > 50%, business metric "price = 0" anomaly.

**2. Timeline (from logs, alerts, chat history):**
```
14:30 — Deploy of v2.45 of pricing-api begins.
14:33 — Deploy completes, traffic shifts to new version.
14:35 — Datadog alert fires: "anomaly: price_zero_count > 100/min".
14:36 — On-call engineer paged.
14:42 — Engineer confirms issue. Slack incident channel opened.
14:48 — Initial hypothesis: misconfig. Decision: rollback.
14:55 — Rollback initiated.
15:00 — Rollback complete, prices return.
15:05 — All-clear declared.
```

**3. Impact:**
- **Customer-facing:** ~50,000 quote requests, 10,000 orders attempted with 0 PLN.
- **Revenue:** 200 orders successfully placed at 0 PLN before mitigation = ~50,000 PLN loss (need finance to confirm).
- **Trust:** customer support tickets spike, PR risk if widely noticed.
- **Internal:** dev team time, on-call disruption.

**4. Root cause analysis (5 whys):**
- **Why did API return 0?** Bug in v2.45 — division by zero in promotion stacking returned 0 instead of original price.
- **Why was bug not caught in tests?** Test suite did not cover edge case of zero-discount promotion.
- **Why did test suite miss this?** Coverage gap — promotion test focused on common cases, not edge cases.
- **Why was edge case not considered?** Code review missed it; lack of rigorous edge-case checklist.
- **Why no defense in depth?** No floor check (`if calculated_price <= 0: fall back to last known good`).

**5. What went well:**
- Monitoring detected within 5 min.
- On-call rotation worked (engineer paged, ack'd quickly).
- Rollback was fast (10 min from decision to complete).
- Incident channel coordinated communication.

**6. What went poorly:**
- Bug shipped to production despite tests.
- Initial 5-minute detection lag (alarm triggered after 100 zero-prices).
- No automatic rollback / canary that would catch this.
- 30-minute total impact too long for SEV-1.

**7. Action items (with owners + dates):**

**Immediate (this week):**
- [Owner: Alice] Patch bug, add test for zero-discount edge case. Deploy as 2.46.
- [Owner: Bob] Add price floor check: if calculated_price ≤ 0, fall back to product.base_price + log alert.
- [Owner: Carol] Refund 200 affected orders + customer notification.

**Short-term (this month):**
- [Owner: Dan] Implement canary deployment: 1% traffic for 10 min, check error rate, then 100%.
- [Owner: Eve] Synthetic monitoring: every 5 min, call price API, assert > 0. Alert on first failure.
- [Owner: Alice] Edge case coverage audit: list all edge cases, ensure tests for each.

**Long-term (next quarter):**
- [Owner: Frank] Investigate progressive deployment (Argo Rollouts).
- [Owner: Grace] Pricing engine load tests + chaos testing in staging.

**8. Communication:**
- Internal: this post-mortem published to engineering wiki, reviewed in eng all-hands.
- External (if needed): customer notice, status page update.

**9. Follow-up:**
- 30 days later: review action items completion. Any not done — re-prioritize.
- 90 days later: any similar incidents? If yes, action items insufficient.

**Cultural elements:**
- **Blameless:** focus on systemic causes, not individual blame. Even if Alice wrote the bug, the system allowed it through (no canary, no synthetic monitoring) — the systemic gaps are the problem.
- **Action-oriented:** every "what went wrong" → action item.
- **Time-boxed:** post-mortem within 5 days of incident, otherwise memory fades.

**Tools:**
- Templates (Atlassian Confluence template, Google Docs).
- Incident retrospective tools (Pagerduty Incident Management, Blameless).

**Specific to pricing:**
- Audit of in-flight transactions — refund / re-process.
- Communication with sales team (some quotes might've gone out at wrong prices — recall).
- Compliance — depending on jurisdiction, big pricing errors might need regulatory disclosure.

**Pułapka rozmowna:** „Find who broke it and fire them" — antipattern. Punishing individuals discourages reporting, kills psychological safety, doesn't fix systemic causes. Druga: „Add more tests" alone — without process changes (canary, monitoring), more tests still miss edge cases. Defense in depth: tests + canary + monitoring + safeguards.
**Tagi:** post-mortem, incident-response, reliability

## Q-PRC-028 [bloom: analyze]
**Pytanie:** Twój zespół rozważa migracji starego pricing engine (Java EE, monolit, 15 lat) do nowego stack (Spring Boot microservices). Strategia?
**Modelowa odpowiedź:** Klasyczny strangler fig pattern. Big-bang rewrite = wszystko może rozjebać.

**Phase 0 — Discovery (1-2 miesiące):**
- **Inventory** wszystkich features starego engine. Często zaskakuje ile rzeczy jest.
- **Codebase analysis** — what's in use, what's dead. Tools: code coverage in prod (e.g., JaCoCo agent), static analysis.
- **API contracts** — list endpoints + consumers. Często stare engine ma "ghost" endpoints — nikt już nie używa, ale są.
- **Data model** — schema, integrations, data volumes.
- **Tech debt** assessment.
- **Stakeholder interviews** — sales, finance, business: what hurts most? What's working well?

**Phase 1 — Foundation (2-3 miesiące):**
- **New service stub** — Spring Boot, basic infrastructure (DB, cache, monitoring, CI/CD).
- **API gateway** — placed in front. Routes traffic. Today: 100% to old. Future: split.
- **Event bus** — Kafka. Old engine starts emitting events for changes (audit, will help dual-write).
- **Test infrastructure** — contract testing (Pact), end-to-end tests against old as baseline.

**Phase 2 — Strangler (12-24 miesiące — critical phase):**
- **Pick first feature to migrate** — ideally:
  - Self-contained (few cross-dependencies).
  - Active (so you'll see impact, not just historic code).
  - Pain point (so business is motivated).
  - Example: "List price lookup" — clear contract, frequent usage.
- **Build feature in new service** — same API contract.
- **Dual-write** — for changes, both old and new updated. (Or replicate via events/CDC.)
- **Shadow mode** — new service receives real traffic in parallel, results compared (not used). Find discrepancies, fix bugs.
- **Cutover by route** — API gateway routes 1% traffic to new, 99% old. Monitor. Increase: 5%, 25%, 50%, 100%.
- **Decommission** old code path for that feature. Delete dead code.
- **Iterate** — next feature.

**Phase 3 — Decommission:**
- After all features migrated (years for big systems), shut down old engine.
- Final data export / archive.

**Risks i mitigations:**

**Risk: Data inconsistency between old and new during dual-write.**
Mitigation: idempotent writes, event sourcing, periodic reconciliation.

**Risk: New service has different behavior (subtle bugs).**
Mitigation: shadow mode + extensive comparison testing.

**Risk: Migration takes forever, "big-bang at the end".**
Mitigation: ship something every quarter. Show progress to leadership.

**Risk: Two systems to maintain (cost!).**
Mitigation: planned schedule, defined exit date for old.

**Risk: Team morale (old code support sucks).**
Mitigation: rotate ownership, celebrate wins (each migrated feature).

**Risk: Business changes mid-migration.**
Mitigation: prioritize features that align with business roadmap.

**Specific to pricing:**

**1. Audit and history.**
Pricing data has long retention requirements (compliance). Keep old engine readable for read-only audit even after writes migrated.

**2. Performance regression risk.**
Old engine, despite warts, is tuned over years. New might be slower. Performance testing parity required.

**3. Edge cases.**
Pricing has weird historical edge cases (specific customer A has rule X exception that nobody knows why). Carry over carefully.

**4. Multi-currency, time zones, locale.**
Subtle bugs in conversion logic. Test extensively.

**5. Integration points.**
Pricing connects to ERP, CRM, e-commerce, CPQ. Each has its own quirks. Migrate consumer integrations one at a time.

**Tools:**
- **API Gateway:** Kong, Traefik, AWS API Gateway, NGINX.
- **CDC for data replication:** Debezium.
- **Feature flags:** LaunchDarkly, Unleash.
- **Comparison testing:** custom or framework like Diffy (Twitter-open-sourced).

**Realistic timeline:**
- Small system (< 100k LoC): 6-12 months.
- Medium system: 1-2 years.
- Large legacy enterprise: 2-5 years (sometimes never fully complete).

**Decision criteria — "rewrite vs strangler":**
- Strangler almost always wins for non-trivial systems.
- Exception: very small systems, or new domain (existing system unsalvageable).
- Joel Spolsky's classic "Things You Should Never Do, Part 1": rewrite is one of the worst strategic mistakes.

**Pułapka rozmowna:** „We can't migrate piece by piece, everything is interconnected" — usually solvable with good API design + adapters. „Big bang in 3 months" — almost never works for legacy. Druga: „We'll just rewrite it on the side" — without strangler discipline, becomes vapor or replaces with the same kludges.
**Tagi:** migration, legacy, strangler, architecture

## Q-PRC-029 [bloom: analyze]
**Pytanie:** Discount stacking — wybór: declarative rules (data-driven) vs imperative code. Co dla pricingu?
**Modelowa odpowiedź:** **Declarative (rules in DB / config):**
- Plus: business team can edit (within bounds), no code change for new promo, audit-friendly, A/B testable.
- Minus: limited to predefined rule types, hard to express conditional logic, performance overhead of rule engine, debugging trickier.

**Imperative (code-based):**
- Plus: full power of programming, complex logic expressible, debug like normal code, type-safe.
- Minus: every new rule = code change + deploy, sales/marketing can't experiment without engineering, slow iteration.

**Declarative example (what business team can do):**
```json
{
  "promo_id": "SUMMER25",
  "name": "Summer 25% off",
  "type": "PERCENT_OFF",
  "value": 0.25,
  "applies_to": {
    "product_categories": ["electronics", "clothing"],
    "exclusions": ["sale_items"]
  },
  "conditions": {
    "min_cart_value": 100.00,
    "customer_segments": ["B2C", "VIP"],
    "date_range": {"from": "2026-06-01", "to": "2026-08-31"}
  },
  "stacking": {
    "stacks_with": ["LOYALTY", "EMAIL_SIGNUP"],
    "exclusive_with": ["WHOLESALE"]
  },
  "limits": {
    "max_per_customer": 1,
    "total_budget_pln": 500000
  }
}
```

**Imperative example (engineering domain):**
```java
public BigDecimal applyComplexBundleRule(Cart cart, BigDecimal currentPrice) {
    // "Buy iPhone + AirPods + Charger, get 15% off bundle, but only if cart total < 5000"
    boolean hasIPhone = cart.contains(p -> p.getCategory().equals("phones") && p.getBrand().equals("Apple"));
    boolean hasAirPods = cart.contains(p -> p.getName().contains("AirPods"));
    boolean hasCharger = cart.contains(p -> p.getCategory().equals("chargers"));
    BigDecimal cartTotal = cart.getTotal();
    
    if (hasIPhone && hasAirPods && hasCharger && cartTotal.compareTo(new BigDecimal("5000")) < 0) {
        return currentPrice.multiply(new BigDecimal("0.85"));
    }
    return currentPrice;
}
```

**Hybrid approach (recommended for production):**

**Layer 1 — Declarative for 80% standard cases:**
- Volume tier (qty ≥ X → price Y).
- Segment discount (segment X → discount Y%).
- Time-bound % off promotions.
- Free shipping above threshold.
- Promo code with simple conditions.

**Layer 2 — Imperative for 20% complex cases:**
- Multi-product bundles with conditional logic.
- Customer-specific rules with weird requirements.
- Cross-sell logic ("buy A get 50% off B").
- Complex eligibility (e.g., "only customers who bought product X in last 90 days").

**Layer 3 — DSL for advanced power users (optional):**
- Groovy / custom DSL for technical pricing analysts.
- Sandboxed evaluation.
- Approval workflow before deploy.

**Architecture:**
```
Pricing Engine
├── DeclarativeRuleEvaluator
│   └── reads DB rules table, applies type-mapped logic
├── ImperativeRuleSet
│   └── coded as Spring beans, ordered by priority
└── DSLRuleEvaluator (optional)
    └── Groovy script per rule, executed sandboxed
```

**Promotion stacking strategy:**

**Conflict resolution rules** (per company policy):
1. **Exclusive promotions** — listed promotion can declare "exclusive" — no others apply.
2. **Stack groups** — group A (storewide) + group B (item-specific) + group C (payment) = stack across groups, winner-takes-all within group.
3. **Cap on total discount** — even if stacked, max combined discount X% (margin floor protection).

**Implementation:**
```java
public List<AppliedPromotion> evaluateStacking(List<EligiblePromotion> eligible) {
    List<EligiblePromotion> exclusives = eligible.stream()
        .filter(EligiblePromotion::isExclusive)
        .toList();
    
    if (!exclusives.isEmpty()) {
        // Pick best exclusive
        EligiblePromotion best = exclusives.stream()
            .max(Comparator.comparing(EligiblePromotion::computeDiscount))
            .get();
        return List.of(new AppliedPromotion(best, best.computeDiscount()));
    }
    
    // No exclusives — stack within groups
    Map<String, List<EligiblePromotion>> byGroup = eligible.stream()
        .collect(Collectors.groupingBy(EligiblePromotion::getGroup));
    
    List<AppliedPromotion> applied = new ArrayList<>();
    for (Map.Entry<String, List<EligiblePromotion>> entry : byGroup.entrySet()) {
        EligiblePromotion winner = entry.getValue().stream()
            .max(Comparator.comparing(EligiblePromotion::computeDiscount))
            .get();
        applied.add(new AppliedPromotion(winner, winner.computeDiscount()));
    }
    
    // Apply cap
    BigDecimal totalDiscount = applied.stream()
        .map(AppliedPromotion::getDiscount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal maxAllowed = settings.getMaxStackedDiscountPct();
    if (totalDiscount.compareTo(maxAllowed) > 0) {
        // Scale down proportionally
        BigDecimal scaleFactor = maxAllowed.divide(totalDiscount, 4, RoundingMode.HALF_UP);
        applied = applied.stream()
            .map(ap -> ap.scale(scaleFactor))
            .collect(Collectors.toList());
    }
    
    return applied;
}
```

**Decision per pricing platform:**
- **Startup MVP** — declarative-only z 5 standard promo types. Ship fast.
- **Growing** — add imperative for special cases.
- **Mature enterprise** — full hybrid with DSL.

**Pułapka rozmowna:** „Pure declarative jest enough" — naïve. Real businesses always have „one weird rule" requiring imperative. Plan for it. Druga: „All in code" — sales/marketing can't iterate, you become bottleneck.
**Tagi:** declarative, imperative, dsl, stacking, decision

## Q-PRC-030 [bloom: analyze]
**Pytanie:** Sales rep żąda nowej funkcji w pricing engine: dla niektórych klientów cena mają być widoczna jako "Call for price" zamiast number. Twoja reakcja jako engineer?
**Modelowa odpowiedź:** **Don't reject knee-jerk. Don't accept blindly.** Pytania pierwsze:

**1. Why?**
- "Call for price" suggests price negotiable, custom quote needed. Why niektórzy klienci?
- Use cases: high-value customer who always negotiates; product where pricing is sensitive (medical, government); product not yet priced; competitive intelligence (don't show competitors prices); regulatory.
- Each use case has different best implementation.

**2. Who decides per customer?**
- Sales rep manually marks customers? — adds operational burden.
- Customer tier? — automatic but rigid.
- Product configuration? — (e.g., custom-built products always need quote).
- Combination?

**3. What about "list price"? Still shown to internal sales?**
- For sales rep planning, maybe show a guideline ("Internal estimate: 1500-2000 PLN, customer-facing: Call for price").
- For external e-commerce, hide entirely.

**4. Edge cases:**
- Customer in cart, all products call-for-price → cart total is also call-for-price.
- Discount calculations — internally still computed for sales rep, externally hidden.
- Tax calculations — when applied? At quote stage, after negotiation.

**5. Compliance / customer expectations:**
- B2B context — standard.
- B2C context — could be illegal (consumer protection requires upfront pricing in many jurisdictions).
- Document carefully where allowed.

**6. Technical implementation:**

```java
public class PriceDisplay {
    BigDecimal price;
    String displayMode;  // "STANDARD", "CALL_FOR_PRICE", "QUOTE_REQUIRED"
    String fallbackText;  // for non-standard modes
}

public PriceDisplay getDisplayPrice(PricingContext ctx) {
    if (shouldHidePrice(ctx)) {
        return new PriceDisplay(null, "CALL_FOR_PRICE", "Skontaktuj się z handlowcem");
    }
    BigDecimal price = pricingEngine.calculate(ctx);
    return new PriceDisplay(price, "STANDARD", null);
}

private boolean shouldHidePrice(PricingContext ctx) {
    Customer c = ctx.getCustomer();
    Product p = ctx.getProduct();
    
    // Rule 1: Customer-specific override
    if (c.getDisplayMode() == DisplayMode.QUOTE_ONLY) return true;
    
    // Rule 2: Product flagged
    if (p.isQuoteRequired()) return true;
    
    // Rule 3: Combination (e.g., enterprise customer + custom product)
    if (c.getTier() == Tier.ENTERPRISE && p.isConfigurable()) return true;
    
    return false;
}
```

**7. UI implications:**
- Frontend handles `displayMode` to render either price OR call-to-action.
- "Request a Quote" button → opens form, sales rep notified.
- Lead capture for marketing.

**8. Backend impact:**
- API still returns internal price for sales/admin views.
- Public API filters per request context.
- Quotes / orders proceed as normal once sales rep manually enters negotiated price.

**9. Reporting:**
- "Call for price" requests metric — how many leads, conversion rate.
- Time to respond.
- Sales productivity.

**10. Refactor implication:**
- If this becomes common (multiple categories), generalize → "price display strategy" pattern, multiple display types.

**Code review attitude:**

**Engineer's good response:**
- "Yes, we can do this. Here are the considerations: A, B, C. Can we discuss with PM about: 1, 2, 3? I want to make sure the implementation matches your actual workflow."
- Asks clarifying questions, doesn't just code.
- Proposes phases (MVP for highest-impact case, expand later).

**Engineer's bad response:**
- "Yes, easy" → goes off, builds tightly-coupled feature, sales rep finds it doesn't fit their workflow.
- "No, that's not how pricing should work" → blocks business need without alternative.

**Pułapka rozmowna:** „Sales asks for X, build X" — without questioning, builds the wrong thing. Real ask might be different. „Sales asks for X, build Y" — the engineer's idea — without alignment, builds something nobody uses. Right balance: understand intent, propose options, align, build.
**Tagi:** product-thinking, requirements, decision, communication

## Q-PRC-031 [bloom: analyze]
**Pytanie:** Twój pricing engine ma audit log dla każdej kalkulacji. Jak zarządzasz storage / cost dla logów które rosną do TB-ów?
**Modelowa odpowiedź:** Audit logs scaling — common problem. Zaczyna się jako "save everything", kończy w TB i sluggish queries.

**Strategie:**

**1. Tiered storage:**
- **Hot tier (last 7 days):** Postgres / MySQL. Indexed, fast queries. Wholesale cost: $$$ per GB-month.
- **Warm tier (7-90 days):** S3 / Object storage with pure DB metadata. Slower query but cheap. $-$$.
- **Cold tier (>90 days):** S3 Glacier, Azure Archive. Very cheap, retrieval costs.
- Lifecycle policies (S3 lifecycle, AWS) auto-move data.

**2. Compression:**
- JSON logs compress well (5-10x). Brotli, Zstandard.
- Format: Parquet for analytical queries (columnar, compressed, fast aggregation).
- Avoid: text JSON (human-readable but bulky).

**3. Sampling:**
- Not every event needs full audit. Sampling: 100% for high-value transactions (orders > 1000 PLN), 10% for lookups, 1% for cache hits.
- Can be tier-based: enterprise customers = full, standard = sampled.

**4. Aggregation:**
- Don't store every individual log forever. Aggregate older data.
- E.g., daily summaries: "Customer X had Y price queries on day Z, avg price $A, P95 $B". Detail purged after 30 days, summary kept forever.

**5. Retention policies:**
- Define explicit retention per data type.
- "Pricing calculations: 7 days hot, 90 days warm, 7 years cold (compliance), then delete."
- "Audit of contract changes: 7 years cold (regulatory)."
- Document jurisdictional requirements (GDPR right to erasure, financial audit requirements).

**6. Schema optimization:**
- Don't store redundant data. Foreign keys, not denormalized.
- Wide table schema → many columns, sparse data → wasteful. Use JSONB for variable fields.
- Indexed only on frequently-queried fields.

**7. Database choice:**
- **OLTP (Postgres):** for hot data, transactional consistency.
- **Time-series (TimescaleDB, Influx):** for chronological aggregations, automatic compression of old data.
- **OLAP (ClickHouse, BigQuery, Snowflake):** for analytical queries on huge data.
- **Data lake (S3 + Athena/Presto):** for raw event archive, query on-demand.

**8. Query patterns:**
- Real-time queries (sales rep checks last 7 days): fast hot tier.
- Analytics (revenue by product Q1 2026): batch query on warm/cold, OK to be slow.
- Compliance (regulator asks: show all 2024): cold tier retrieval, accept hours of latency.

**9. Cost optimization:**
- Monitor per service: how much data emitted, where, what's queried.
- Identify dead data (never queried) — purge.
- Identify hot patterns — optimize those queries first.

**10. Privacy / compliance:**
- PII redaction in logs (or hashing).
- Encryption at rest (S3 SSE).
- Access controls (audit who reads audit logs).

**Pricing-specific scenarios:**
- **Compliance:** financial regulator asks "show all pricing for customer X in year Y". Retrieve from cold, decrypt, present. Document SLA.
- **Dispute resolution:** customer complains "you charged me wrong". Look up specific transaction in hot/warm tier.
- **Sales analytics:** "which promotion drove most volume Q1?". Aggregate query on OLAP.

**Architecture:**
```
Pricing Engine
   ↓ (write)
Kafka topic: pricing_events
   ↓
Splitter:
  ├→ Postgres (hot, last 7 days)
  ├→ S3 (warm/cold archive, partitioned by date)
  └→ ClickHouse (analytical, aggregated)
   ↓ (query)
Query gateway routes based on query date range:
  - 0-7 days → Postgres
  - 7-90 days → S3 + Athena
  - >90 days → S3 Glacier + bulk retrieval
```

**Schema for hot tier:**
```sql
CREATE TABLE pricing_audit_log (
  id UUID PRIMARY KEY,
  customer_id BIGINT,
  product_id BIGINT,
  event_type VARCHAR(50),
  occurred_at TIMESTAMPTZ,
  payload JSONB,
  source VARCHAR(50)
) PARTITION BY RANGE (occurred_at);

-- Daily partitions
CREATE TABLE pricing_audit_log_2026_05_06 PARTITION OF pricing_audit_log
  FOR VALUES FROM ('2026-05-06') TO ('2026-05-07');

CREATE INDEX ON pricing_audit_log_2026_05_06 (customer_id, occurred_at);
CREATE INDEX ON pricing_audit_log_2026_05_06 (product_id, occurred_at);
```

Old partitions:
- After 7 days, export to S3 (Parquet compressed).
- DROP partition (instant, vs DELETE which is slow).

**Pułapka rozmowna:** „Save everything forever, just in case" — runaway cost. Define retention upfront. „Archive to S3 = no thinking about it" — when need to query archived data, retrieval cost + time can be brutal. Plan query patterns before archiving format.
**Tagi:** audit, storage, scaling, cost, retention

## Q-PRC-032 [bloom: analyze]
**Pytanie:** Rekruter pyta na rozmowie: "Widziałeś nasz pricing platform? Co byś poprawił?". Jak odpowiadasz?
**Modelowa odpowiedź:** Pułapka. Cel: pokaż research i myślenie produktowe, ale nie rozjeb produktu rekrutera (insulting + nie wiesz constraints). 

**Strategia odpowiedzi:**

**1. Oprzyj się na researchu:**
Jeśli widziałeś produkt — wymień konkrety co podobało się i dlaczego. Pokazuje że zrobiłeś homework.
- "Widziałem demo — module XYZ wygląda solidnie, zwłaszcza part o multi-currency obsługi. To rzecz która często ucieka konkurencji."

**2. Reframe "improve" jako "explore further":**
Zamiast "would fix this and that", powiedz "would want to understand more about X before suggesting changes". Pokorne, ale aktywne.
- "Z zewnątrz widzę X. Zanim sugerowałbym zmiany, chciałbym zrozumieć: czemu tak zbudowane? Jakie były constraints? Co już próbowano?"

**3. Konkrety z domeny pricingu (jeśli jesteś gotowy):**
Wybierz coś technicznego, nie cosmetic.
- "Z perspektywy engineera ciekawi mnie performance pricing API w peak — czy macie cache layer, jak invalidacja działa, jakiego typu queries są bottleneckiem?"
- "Audit trail przy stacked promotions — czy customer może zobaczyć dlaczego dostał taką cenę? To jest często undervalued ale builds trust."
- "Multi-tenant aspects — czy integracja partner_X i partner_Y jest izolowana? To często source of incidents."

**4. Soft skills sygnał:**
Pokaż że umiesz dyskutować w nuance, nie pomawiać.
- "Mam swoje pomysły, ale jestem ostrożny z 'fixami' z zewnątrz. Każda zmiana w pricing może mieć cascading effects. W pierwszych miesiącach chciałbym observować, learn, potem propose."

**5. Pokaż ciekawość:**
- "Co jest największym engineering challenge w roku 2026 dla waszej platformy? Z czym się wewnętrznie ścieracie najbardziej?"
- Tym pytaniem przesuwasz rozmowę na ich grunt. Reverse-interview pattern.

**Czego unikać:**

❌ "Wasz UI jest brzydki" — opinia, nie wartość, łatwo personally affronting.
❌ "Powinniście używać GraphQL" — bez rozumienia constraints.
❌ "Nie skaluje się" — bez evidence.
❌ "Rewrite w Rust" — naiwne, wykończenia rekrutera.
❌ "Wszystko musi być cloud-native" — buzzwords without substance.

**Co ZNALEŹĆ jako research przed rozmową (homework):**

1. **Public docs / API documentation** — jeśli jest, przeczytaj.
2. **Press releases / case studies** — dowiesz się o klientach, use cases.
3. **GitHub** — czasem pricing platforms mają open SDKs. Look at code, ask quality questions.
4. **Glassdoor / employee reviews** — co inni mówią o tech stack, challenges.
5. **LinkedIn** — kto na zespole, na co specjalizują, recent posts (current focus).
6. **Industry articles** — pricing industry trends, competitors.
7. **Customer reviews** (G2, TrustPilot) — pain points customers report.

**Format odpowiedzi (60-90 sekund):**

> "Z research na waszą stronę i kilka case studies widziałem, że [coś specyficznego, pozytywnie]. To jest mocne. 
> Z zewnątrz mam kilka pytań technicznych, bo z 1-godzinnego demo trudno o realne sugestie. Najbardziej ciekawi mnie [konkretny obszar, np. cache layer, multi-tenant isolation]. Nie wiem ile macie load i jakie wzorce — to są decyzje contextual.
> Chętnie zobaczyłbym kawałek codebase albo architecture diagram żeby kalibrować, gdzie warto inwestować, a gdzie obecny stan jest already optimal. Mam swoje opinie, ale chcę zrozumieć przed proponowaniem zmian."

**Sygnały które rekruter łapie:**

✓ Research zrobiony.
✓ Pokora — nie wiesz wszystkiego z zewnątrz.
✓ Technical curiosity (specific topics).
✓ Communication — nie szpilek, konstruktywne.
✓ Seniority — wie że "fix everything" to junior mindset.

**Jeśli rekruter naciska na konkretny improvement:**
- Wybierz coś niskiej wagi (nie revolution).
- Uzasadnij data ("z mojego doświadczenia X").
- Otwarte na pushback.
- Przykład: "Zaintrygowała mnie wasza approach do CPQ — czy myśleliście o cachingu wyników configuration validation? W dużych config trees może być expensive. Ale to spekulacja z zewnątrz."

**Pułapka rozmowna:** Próba zrobić wrażenie wyzwaniem statu quo — często traktowane jako arrogant junior. Senior engineer pokazuje że szanuje complexity, pyta przed sądzeniem. Druga: nie powiedzieć nic substantywnie ("everything looks great") — flat affect, no engagement.
**Tagi:** interview, soft-skills, communication, product-thinking
