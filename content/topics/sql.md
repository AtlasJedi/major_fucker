# SQL — bank pytań

> Kontekst: pricing engine na bazie relacyjnej (Postgres/Oracle/MS SQL). Pytania ułożone od podstaw przez analitykę po decyzje architektoniczne. Składnia w odpowiedziach głównie ANSI SQL z notatkami dialect-specific.

## Zakres

- DDL: CREATE/ALTER/DROP, constraints (PK, FK, UNIQUE, CHECK, NOT NULL)
- DML: INSERT, UPDATE, DELETE, MERGE/UPSERT
- SELECT: WHERE, GROUP BY, HAVING, ORDER BY, LIMIT/OFFSET
- JOIN: INNER, LEFT, RIGHT, FULL, CROSS, SELF, semi-join, anti-join
- subquery: scalar, correlated, EXISTS/IN
- CTE i recursive CTE (WITH)
- window functions: ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, SUM/COUNT OVER (PARTITION BY ... ORDER BY ... ROWS/RANGE)
- aggregations: SUM, COUNT, AVG, MIN, MAX, STRING_AGG/GROUP_CONCAT
- indeksy: B-tree, hash, GIN/GiST, partial, covering, clustered
- query plan: EXPLAIN, ANALYZE, seq scan vs index scan, nested loop vs hash join
- transakcje: BEGIN/COMMIT/ROLLBACK, ACID
- isolation levels: READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE; phenomena: dirty/non-repeatable/phantom
- locking: row-level, table-level, optimistic vs pessimistic, deadlocks
- normalizacja: 1NF, 2NF, 3NF, BCNF; denormalization trade-offs

---

## Q-SQL-001 [bloom: recall]
**Pytanie:** Czym różni się `INNER JOIN` od `LEFT JOIN`?
**Modelowa odpowiedź:** `INNER JOIN A ON ...` zwraca tylko wiersze, dla których jest dopasowanie z obu stron — brak dopasowania = brak wiersza. `LEFT JOIN A ON ...` zwraca wszystkie wiersze z lewej tabeli plus pasujące z prawej; jeśli brak dopasowania w prawej, kolumny z prawej są `NULL`. Praktyczne zastosowanie LEFT JOIN: znaleźć klientów bez zamówień (`SELECT c.* FROM customer c LEFT JOIN order o ON o.customer_id = c.id WHERE o.id IS NULL`). RIGHT JOIN to symetryczny LEFT JOIN — w praktyce rzadko używany, bo można odwrócić tabele.
**Pułapka rozmowna:** „LEFT JOIN ... WHERE right.column = X" — to filtruje wiersze, w tym te z NULL po stronie right, więc efektywnie staje się INNER JOIN. Filtr na right side musi iść do `ON` (`LEFT JOIN ... ON ... AND right.column = X`), nie do `WHERE`.
**Tagi:** join, basics

## Q-SQL-002 [bloom: recall]
**Pytanie:** Co robi `GROUP BY` i kiedy potrzebujesz `HAVING`?
**Modelowa odpowiedź:** `GROUP BY` agreguje wiersze w grupy po wskazanych kolumnach — wszystkie pozostałe kolumny w SELECT muszą być albo w `GROUP BY`, albo w funkcji agregującej (`SUM`, `COUNT`, `AVG`). `HAVING` filtruje grupy PO agregacji (`WHERE` filtruje wiersze PRZED). Przykład: `SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id HAVING COUNT(*) > 10` — klienci z ponad 10 zamówieniami. `WHERE COUNT(*) > 10` byłoby błędem.
**Pułapka rozmowna:** Kolejność wykonania: FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT. Stąd nie można użyć aliasu z SELECT w WHERE/GROUP BY/HAVING (poza Postgresem który czasami pozwala) — alias jest „later in the pipeline".
**Tagi:** aggregation, basics

## Q-SQL-003 [bloom: recall]
**Pytanie:** Co to jest klucz główny (PRIMARY KEY) i czym różni się od UNIQUE?
**Modelowa odpowiedź:** PRIMARY KEY: niepowtarzalny identyfikator wiersza w tabeli. Cechy: 1) `NOT NULL` (zawsze), 2) tylko jeden PK na tabelę, 3) automatycznie tworzy unique index, 4) często clustered index (MS SQL) lub primary index (Postgres heap + B-tree). UNIQUE: ograniczenie na unikalność wartości w kolumnie/zestawie kolumn. Cechy: 1) może być wiele UNIQUE per tabela, 2) zazwyczaj NULLowalne (NULL nie konfliktuje z innym NULL w większości baz), 3) tworzy unique index. **Klucz różnicujący:** PK reprezentuje tożsamość wiersza, UNIQUE — biznesowy constraint nieredundancji (np. unique email użytkownika).
**Pułapka rozmowna:** „Czy NULL == NULL?" — w SQL, NULL nie jest równe niczemu, włącznie z innym NULL. Stąd UNIQUE na nullowalnej kolumnie pozwala wielu NULL-om (z wyjątkiem MS SQL gdzie tylko jeden NULL).
**Tagi:** schema, constraints, basics

## Q-SQL-004 [bloom: recall]
**Pytanie:** Wymień 3 funkcje okienkowe (window functions) i krótko opisz, do czego służą.
**Modelowa odpowiedź:** 1) **`ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)`** — przypisuje unikalne, kolejne numery wierszom w obrębie partycji. Przy remisach kolejność niedeterministyczna, każdy wiersz ma inny numer. 2) **`RANK() OVER (...)`** — jak ROW_NUMBER, ale przy remisach wiersze dostają ten sam rank, kolejny rank "skacze" (1, 1, 3, 4). `DENSE_RANK` nie skacze (1, 1, 2, 3). 3) **`LAG(column, n) OVER (...)`** — wartość kolumny n wierszy wcześniej (w obrębie partycji, według ORDER BY). `LEAD` to symetrycznie naprzód. Użycie: porównanie wiersza z poprzednim — np. zmiana ceny dzień do dnia. Inne: `SUM/COUNT/AVG OVER (PARTITION BY ...)` — agregat per partycja bez GROUP BY, `FIRST_VALUE`, `LAST_VALUE`, `NTH_VALUE`, `NTILE(n)`, `PERCENT_RANK`, `CUME_DIST`.
**Pułapka rozmowna:** „Czemu nie GROUP BY?" — window functions dają agregat **obok** wierszy szczegółowych (nie zamiast nich). `SELECT id, val, SUM(val) OVER () FROM t` — masz każdy wiersz I sumę. GROUP BY by zwinął.
**Tagi:** window-functions, analytics

## Q-SQL-005 [bloom: recall]
**Pytanie:** Czym jest CTE (Common Table Expression)?
**Modelowa odpowiedź:** CTE to nazwana, tymczasowa subquery zdefiniowana klauzulą `WITH`, którą można odwołać później w zapytaniu. Przykład: `WITH recent_orders AS (SELECT * FROM orders WHERE created_at > NOW() - INTERVAL '7 days') SELECT customer_id, COUNT(*) FROM recent_orders GROUP BY customer_id`. Plusy: czytelność (rozbicie complex query na nazwane kroki), wielokrotne użycie tej samej subquery, łatwiejszy debug. Recursive CTE (`WITH RECURSIVE`) pozwala na hierarchie i grafy: `WITH RECURSIVE descendants AS (SELECT id, parent_id FROM cat WHERE id = ? UNION ALL SELECT c.id, c.parent_id FROM cat c JOIN descendants d ON c.parent_id = d.id) SELECT * FROM descendants`.
**Pułapka rozmowna:** Dawniej (Postgres przed 12) CTE były „optimization fence" — query planner nie wpychał predicates do CTE. Po 12 Postgres inlinuje CTE chyba że użyjesz `WITH foo AS MATERIALIZED (...)`. W innych bazach inline'owanie CTE bywa różne — to ma znaczenie dla performance.
**Tagi:** cte, syntax

## Q-SQL-006 [bloom: recall]
**Pytanie:** Co to jest indeks i jaka jest różnica między B-tree a hash?
**Modelowa odpowiedź:** Indeks to oddzielna struktura danych przyspieszająca wyszukiwanie, kosztująca miejsce i koszt utrzymania (każdy INSERT/UPDATE musi go zaktualizować). **B-tree** — drzewo zbalansowane, log(n) lookup, idealne dla równości (`=`), zakresów (`<`, `>`, `BETWEEN`), prefiksów (`LIKE 'foo%'`), order by. To domyślny indeks w większości baz. **Hash** — bucket-based, O(1) lookup ale **tylko równość**, brak zakresów, brak ORDER BY. W Postgresie historycznie nie WAL-logged (niesafe), od 10 ok. W MS SQL nie ma osobnego hash index, ale są hash buckets w in-memory tables. Inne typy: GIN/GiST (Postgres — fulltext, JSON, GIS), bitmap (Oracle, kolumny low-cardinality), columnstore (analytics).
**Pułapka rozmowna:** „Indeks zawsze przyspiesza" — false. Małe tabele (kilka tysięcy wierszy) mogą być szybsze przez seq scan. Indeks na kolumnie często modyfikowanej zwalnia INSERT/UPDATE. Indeks z low cardinality (np. boolean) jest często bezsensowny.
**Tagi:** indexes, performance

## Q-SQL-007 [bloom: recall]
**Pytanie:** Czym jest transakcja i co oznacza ACID?
**Modelowa odpowiedź:** Transakcja to atomic unit of work — sekwencja operacji wykonana jako całość lub w ogóle. ACID: **A — Atomicity:** wszystko albo nic; rollback przy błędzie cofa cały zestaw. **C — Consistency:** transakcja prowadzi z jednego valid state do drugiego (constraints, triggery zachowują integralność). **I — Isolation:** jedna transakcja nie widzi częściowych zmian drugiej (poziomy: read uncommitted → serializable). **D — Durability:** po `COMMIT` zmiany są persistent (zapisane na dysk, przeżyją crash). Implementowane przez WAL (write-ahead log) + fsync. Klasyczne BEGIN/COMMIT/ROLLBACK. Savepoints (`SAVEPOINT s1; ... ROLLBACK TO s1`) pozwalają na nested-like rollback.
**Pułapka rozmowna:** „NoSQL nie ma ACID" — false. MongoDB ma multi-document transactions od 4.0, Postgres-compatible Cassandra (LWT) ma częściowe ACID, FoundationDB pełne. ACID to konkretny tradeoff (cost of consensus), nie SQL-only.
**Tagi:** transactions, acid, basics

## Q-SQL-008 [bloom: recall]
**Pytanie:** Wymień 4 poziomy izolacji transakcji i jakie zjawiska eliminują.
**Modelowa odpowiedź:** Standard SQL: 1) **READ UNCOMMITTED** — najsłabszy. Możliwe: dirty read (czytanie niezatwierdzonych zmian), non-repeatable read, phantom read. 2) **READ COMMITTED** — eliminuje dirty read. Domyślny w Postgresie i Oracle. 3) **REPEATABLE READ** — eliminuje non-repeatable read (te same wiersze odczytane drugi raz dają to samo). Phantom read teoretycznie wciąż możliwe (standard), ale Postgres używa MVCC i w praktyce eliminuje też phantoms na tym poziomie. 4) **SERIALIZABLE** — eliminuje wszystko. Transakcje wyglądają jakby wykonywały się jedna po drugiej. W Postgresie to SSI (serializable snapshot isolation) — może wykryć konflikt i abortować transakcję. Zjawiska: **dirty read** (czytasz niezatwierdzone), **non-repeatable read** (ten sam wiersz w 2 odczytach daje różne wyniki), **phantom read** (zbiór wierszy spełniających WHERE zmienia się między odczytami przez INSERT z innej transakcji), **lost update** (overwrite zmian innej transakcji).
**Pułapka rozmowna:** Każdy DBMS ma własną interpretację. MySQL InnoDB default REPEATABLE READ, Postgres default READ COMMITTED, MS SQL default READ COMMITTED z lock-based isolation. Postgres MVCC robi REPEATABLE READ tańszym niż lock-based MS SQL.
**Tagi:** transactions, isolation, concurrency

---

## Q-SQL-009 [bloom: understand]
**Pytanie:** Wytłumacz różnicę między correlated a non-correlated subquery i kiedy to ma znaczenie dla performance.
**Modelowa odpowiedź:** **Non-correlated subquery** wykonuje się raz, niezależnie od outer query. Przykład: `SELECT * FROM customer WHERE country IN (SELECT code FROM country WHERE active = TRUE)`. Subquery `SELECT code FROM country WHERE active = TRUE` zwraca listę raz, potem outer ją filtruje. **Correlated subquery** referencuje kolumny outer query — musi być wykonana per wiersz outer. Przykład: `SELECT c.* FROM customer c WHERE EXISTS (SELECT 1 FROM order o WHERE o.customer_id = c.id AND o.total > 1000)`. Subquery musi być sprawdzona dla każdego klienta. **Performance:** correlated jest naïwnie O(N*M), non-correlated O(N+M). Ale modern optimizers często rewriteują correlated do JOIN albo semi-join — patrz EXPLAIN. **Praktyczna reguła:** użyj `EXISTS` (correlated) gdy chcesz boolean check, użyj JOIN (lub `IN` z subquery) dla zwracania danych. Dla anti-join: `NOT EXISTS` lepsze niż `NOT IN` (NULL handling).
**Pułapka rozmowna:** `NOT IN (subquery z NULL)` zwraca pustą listę zawsze — bo `x NOT IN (..., NULL, ...)` to UNKNOWN, traktowane jak FALSE. `NOT EXISTS` nie ma tego problemu. Klasyczna pułapka rekrutacyjna.
**Tagi:** subquery, performance, exists

## Q-SQL-010 [bloom: understand]
**Pytanie:** Co to jest query plan i jak czytać `EXPLAIN`?
**Modelowa odpowiedź:** Query plan to drzewo operacji, które optimizer wybrał do wykonania zapytania — sekwencja Scan → Join → Filter → Sort → Aggregate → Output. Czytanie od dołu do góry: liście to scany tabel (Seq Scan, Index Scan, Index Only Scan, Bitmap Scan), wyższe poziomy to operatory (Nested Loop, Hash Join, Merge Join, Hash Aggregate, Sort, Limit). **Postgres `EXPLAIN`** pokazuje cost estimate (`cost=0.00..123.45 rows=100 width=20`). **`EXPLAIN ANALYZE`** dodatkowo wykonuje query i pokazuje real time per node + actual rows. **Kluczowe sygnały do przeglądu:**
- **Seq Scan na dużej tabeli** + filter — brak indeksu na predykacie. Sprawdź czy jest indeks i czemu się nie używa.
- **Hash Join** dla małych tabel jest OK. **Nested Loop** na wielkich — często wąskie gardło.
- **Sort** disk-based (jeśli `Sort Method: external merge`) — `work_mem` za niski lub trzeba indeksu by uniknąć.
- **Bitmap Heap Scan** — kombinacja indeks + heap fetch, dobre przy średnio selektywnych warunkach.
- **Rows estimated vs actual** — jeśli różnica >10x, statystyki tabeli nieświeże, zrób `ANALYZE`.
**Pułapka rozmowna:** Cost w EXPLAIN to abstract units, nie sekundy. `EXPLAIN ANALYZE` to ms. Costs są używane do porównywania planów wewnętrznie. Drugi błąd: `EXPLAIN ANALYZE` na DELETE/UPDATE faktycznie wykona zmianę — opakuj w `BEGIN; EXPLAIN ANALYZE ...; ROLLBACK;`.
**Tagi:** query-plan, performance, postgres

## Q-SQL-011 [bloom: understand]
**Pytanie:** Wytłumacz, czemu indeks na kolumnie z low cardinality (np. boolean, status z 3 wartościami) zazwyczaj nie pomaga.
**Modelowa odpowiedź:** Indeks B-tree zwraca pointer-y do wierszy. Jeśli filter pasuje na 30% wierszy tabeli, optimizer i tak musi przeczytać 30% tabeli — i robi to przez random I/O (po pointer-ach z indeksu) zamiast sequential scan (czytanie tabeli liniowo z dysku). Random I/O jest ~10-100x wolniejszy niż sequential. Stąd dla low-cardinality (np. `is_active boolean` gdzie 90% true) — index jest worse than seq scan. **Wyjątki:** 1) **Partial index** — `CREATE INDEX ... WHERE is_active = false` — indeksuje tylko 10% wierszy, użyteczne dla rare values. 2) **Covering index** — indeks zawiera wszystkie potrzebne kolumny, można odczytać bez heap fetch (Index Only Scan). 3) **Bitmap index** (Oracle, MS SQL columnstore) — wydajny dla low-cardinality. 4) **Composite index** — `(status, created_at)` może pomóc dla query z obu kolumn. **Reguła kciuka:** wybiorczość >5% — rozważ inne podejścia (partial index, denormalizacja, materialized view).
**Pułapka rozmowna:** Wielu wciąż dorzuca indeks „na wszelki wypadek" na każdej FK. Każdy indeks to koszt write I/O i miejsca. W pricing engine z dużym write volume — disciplined indexing critical.
**Tagi:** indexes, cardinality, performance

## Q-SQL-012 [bloom: understand]
**Pytanie:** Czym różni się `UNION` od `UNION ALL`? Kiedy które?
**Modelowa odpowiedź:** Oba łączą wyniki dwóch zapytań w jeden. **`UNION`** dodatkowo robi DEDUP — usuwa duplikaty (wymaga sortowania lub hashing wszystkich wierszy). **`UNION ALL`** zwraca wszystko, łącznie z duplikatami — szybsze, bo brak dedup. **Kiedy które:** UNION ALL gdy: 1) wiesz że nie ma duplikatów (np. dwa rozłączne zakresy dat), 2) chcesz zachować wszystkie wystąpienia. UNION gdy faktycznie potrzebujesz unikalnych wyników. **Performance:** UNION ALL jest często znacząco szybszy — UNION dla 10M wierszy musi sortować 10M, a to drogie. **Pułapka:** w Postgres UNION ALL może czasem nawet pozwalać na lepsze plany (parallel execution).
**Pułapka rozmowna:** „UNION jest jak OR" — semantycznie czasem tak, ale praktycznie często łatwiej zoptymalizować OR niż UNION (zależy od optimizera).
**Tagi:** union, set-operations

## Q-SQL-013 [bloom: understand]
**Pytanie:** Co to jest „covering index" i kiedy go użyć?
**Modelowa odpowiedź:** Covering index to indeks zawierający wszystkie kolumny potrzebne do zapytania — wynik query można wyczytać sam z indeksu, bez sięgania do heap (tabeli głównej). W Postgresie: `CREATE INDEX idx ON t (a, b) INCLUDE (c, d)` — `(a,b)` to klucz indeksu, `(c,d)` to dodatkowe kolumny tylko-do-odczytu (od PG11). W MS SQL `INCLUDE` od dawna. W MySQL — tylko klucz indeksu jest „covering". **Kiedy używać:** hot path queries, gdzie SELECT zwraca mało kolumn i WHERE/JOIN na innych. Klasyk: `SELECT id, name FROM customer WHERE country = 'PL'` — `CREATE INDEX ON customer (country) INCLUDE (id, name)`. Plan: Index Only Scan, brak heap fetch. **Trade-offy:** indeks rośnie (więcej miejsca), więcej write cost przy update kolumn w INCLUDE. Tylko gdy query jest faktycznie hot.
**Pułapka rozmowna:** Index Only Scan w Postgresie wymaga aktualnego visibility map (per page) — jeśli tabela ma dużo updateów bez VACUUM, plan może spaść do Bitmap Scan + heap fetch nawet z covering indexem. ANALYZE / VACUUM mają znaczenie.
**Tagi:** indexes, performance, postgres

## Q-SQL-014 [bloom: understand]
**Pytanie:** Optimistic locking vs pessimistic locking — czym się różnią i kiedy które?
**Modelowa odpowiedź:** **Pessimistic** — explicit lock przy odczycie/modyfikacji (`SELECT ... FOR UPDATE`). Inne transakcje muszą czekać na release. Plus: gwarancja że nikt nie zmieni dancyh w trakcie. Minus: trzymanie locka długo blokuje innych, ryzyko deadlocków. **Optimistic** — bez locka. Czytasz wiersz z polem `version` (lub `updated_at`). Przy zapisie: `UPDATE ... SET ..., version = version + 1 WHERE id = ? AND version = ?`. Jeśli `WHERE version = ?` nie pasuje (ktoś zmienił), `UPDATE` wpływa na 0 wierszy → wykrywasz konflikt → retry / abort / merge. Plus: brak locków, lepsza skalowalność. Minus: konflikty wykrywane późno, retry logic potrzebny. **Kiedy:** pessimistic — krótki critical section, wysokie kontencje, gdy konflikt = prawdziwy problem (np. transfer pieniężny). Optimistic — rare conflicts (różni klienci edytują różne rzeczy), długie sesje (UI z formularzem), high throughput. JPA `@Version` to optimistic out of the box. **W pricingu:** zazwyczaj optimistic dla cenników (rzadkie edycje, długie sesje), pessimistic dla decrement stocku (konflikt = krytyczny).
**Pułapka rozmowna:** „Czemu nie zawsze pessimistic?" — w roli z dużo czytania mało pisania, locki niepotrzebnie zwalniają system. Plus deadlock risk. „Czemu nie zawsze optimistic?" — przy wysokiej kontencji retry storm zabija performance.
**Tagi:** concurrency, locking, transactions

## Q-SQL-015 [bloom: understand]
**Pytanie:** Co to są normalizacja 1NF, 2NF, 3NF i kiedy się denormalizuje?
**Modelowa odpowiedź:** **1NF** — atomowe wartości (no repeating groups, no comma-separated lists w polu). Każde pole — pojedyncza wartość. **2NF** — 1NF plus każde non-key pole zależy od CAŁEGO klucza głównego (eliminacja partial dependencies przy composite PK). Przykład problemu 2NF: tabela `(order_id, product_id, product_name, quantity)` z PK `(order_id, product_id)` — `product_name` zależy tylko od `product_id`, więc trzeba wyciągnąć do osobnej tabeli `product`. **3NF** — 2NF plus brak tranzytywnych zależności. Non-key pole nie powinno zależeć od innego non-key pola. Przykład: `(employee_id, department_id, department_name)` — `department_name` zależy od `department_id`, nie od `employee_id` przez `department_id`. Wyciągnij `department` osobno. **BCNF** — surowsza wersja 3NF. **Denormalizacja:** świadome powtórzenie danych dla performance. Klasyk: `order_line` ma `product_name_snapshot` — żeby raport historyczny pokazywał nazwę produktu w czasie zamówienia, niezależnie od późniejszych zmian. Lub: `order` ma cached `total_amount` żeby uniknąć JOIN-a per query. **Trade-off:** szybsze reads, droższe writes, ryzyko inconsistency (dwa źródła prawdy).
**Pułapka rozmowna:** „Zawsze normalizuj do 3NF" — naïve. Real systems mixują — core entities normalized, reporting tables / read models denormalized. Pricing engine: cennik często read-heavy → denormalizacja często wygrywa.
**Tagi:** normalization, schema-design

## Q-SQL-016 [bloom: understand]
**Pytanie:** `MERGE` (UPSERT) — co robi i jakie są pułapki?
**Modelowa odpowiedź:** `MERGE` (ANSI SQL, Oracle, MS SQL, Postgres 15+) atomowo robi „insert if not exists, update if exists". W Postgresie historycznie było `INSERT ... ON CONFLICT (col) DO UPDATE SET ...`. Przykład:
```sql
INSERT INTO product_price (product_id, country, price)
VALUES (123, 'PL', 100)
ON CONFLICT (product_id, country) DO UPDATE
SET price = EXCLUDED.price, updated_at = NOW();
```
**Pułapki:** 1) **Race conditions** — dwa concurrent inserts mogą i tak collide w niektórych sytuacjach. ON CONFLICT używa unique index do detekcji — bez tego indeksu nie zadziała. 2) **MERGE nie jest atomic w starych implementacjach** (Oracle pre-12c miał race window). 3) **Triggery** mogą się odpalić różnie dla update vs insert path — trzeba testować oba. 4) **Returning** — `RETURNING` w Postgresie zwraca tylko nowe/zaktualizowane wiersze; jeśli chcesz wiedzieć który był insert a który update, dodaj `xmax = 0` (insert) check albo computed column. 5) **NULL semantics** — UNIQUE indeks z NULL może nie wykryć konfliktu (NULL ≠ NULL). **Bulk MERGE** często szybszy przez staging table + `INSERT ... SELECT ... ON CONFLICT`.
**Pułapka rozmowna:** Wielu robi `if exists update else insert` w kodzie aplikacji — to TOCTOU race. UPSERT atomowy w bazie to właściwa droga.
**Tagi:** upsert, merge, postgres, concurrency

---

## Q-SQL-017 [bloom: apply]
**Pytanie:** Masz tabele `customer (id, name, country)`, `order (id, customer_id, created_at, total)`, `order_line (order_id, product_id, quantity, net_price)`. Napisz query zwracające top 10 klientów (wg sumarycznej wartości netto) za ostatnie 90 dni, z podziałem na kraje.
**Modelowa odpowiedź:**
```sql
SELECT 
  c.country,
  c.id AS customer_id,
  c.name,
  SUM(ol.quantity * ol.net_price) AS total_net,
  RANK() OVER (PARTITION BY c.country ORDER BY SUM(ol.quantity * ol.net_price) DESC) AS country_rank
FROM customer c
JOIN order o ON o.customer_id = c.id
JOIN order_line ol ON ol.order_id = o.id
WHERE o.created_at >= NOW() - INTERVAL '90 days'
GROUP BY c.country, c.id, c.name
QUALIFY country_rank <= 10;  -- BigQuery/Snowflake; w Postgres patrz subquery
```
W Postgres bez `QUALIFY`:
```sql
WITH ranked AS (
  SELECT 
    c.country, c.id, c.name,
    SUM(ol.quantity * ol.net_price) AS total_net,
    RANK() OVER (PARTITION BY c.country ORDER BY SUM(ol.quantity * ol.net_price) DESC) AS rk
  FROM customer c
  JOIN order o ON o.customer_id = c.id
  JOIN order_line ol ON ol.order_id = o.id
  WHERE o.created_at >= NOW() - INTERVAL '90 days'
  GROUP BY c.country, c.id, c.name
)
SELECT country, id, name, total_net
FROM ranked
WHERE rk <= 10
ORDER BY country, rk;
```
Krok po kroku: JOIN customer→order→order_line, filter ostatnie 90 dni, GROUP BY (country, customer), suma netto, window function `RANK() PARTITION BY country` daje ranking w każdym kraju, filter po rank ≤ 10.
**Pułapka rozmowna:** „Czy `RANK` czy `ROW_NUMBER`?" — RANK przy remisach pokaże oba klientów na 10. miejscu (więcej niż 10 wyników na kraj). ROW_NUMBER zawsze 10 unikalnych. Decyzja zależy od wymagań biznesu. „GROUP BY z window function" — często pomyłka: window function operuje PO GROUP BY, więc liczy ranking grup, nie pojedynczych wierszy.
**Tagi:** window-functions, join, pricing, ranking

## Q-SQL-018 [bloom: apply]
**Pytanie:** Napisz recursive CTE generujące hierarchię kategorii produktów. Tabela: `category (id, parent_id, name)`. Zwróć każdą kategorię z jej pełną ścieżką w drzewie (np. `Electronics > Phones > Smartphones`).
**Modelowa odpowiedź:**
```sql
WITH RECURSIVE category_tree AS (
  -- anchor: kategorie root (bez parenta)
  SELECT 
    id, 
    parent_id, 
    name,
    name::text AS path,
    1 AS depth
  FROM category
  WHERE parent_id IS NULL
  
  UNION ALL
  
  -- recursive: dorzuć dzieci, doklej do path
  SELECT 
    c.id,
    c.parent_id,
    c.name,
    ct.path || ' > ' || c.name,
    ct.depth + 1
  FROM category c
  JOIN category_tree ct ON c.parent_id = ct.id
)
SELECT id, name, path, depth FROM category_tree
ORDER BY path;
```
Mechanika: `WITH RECURSIVE` ma dwie części złączone `UNION ALL`. Anchor (non-recursive) — root kategorie. Recursive — JOIN dzieci do CTE samego do siebie. Kontynuuje dopóki recursive część zwraca wiersze. **Cycle protection:** w prawdziwych danych dorzuć kolumnę `visited` (array of ids) i `WHERE NOT (c.id = ANY(ct.visited))`, albo `CYCLE` clause (Postgres 14+). W innym wypadku nieskończona pętla.
**Pułapka rozmowna:** „Czemu UNION ALL a nie UNION?" — UNION ALL bo deduplikacja jest zbędna (nigdy nie odwiedzasz tej samej node) i drogo (każda iteracja sortuje). „Czy działa dla DAG?" — bez cycle protection nie. „Co jeśli cycle?" — bez ochrony — infinite loop, fail po `max_recursion`.
**Tagi:** recursive-cte, hierarchy, advanced

## Q-SQL-019 [bloom: apply]
**Pytanie:** Tabela `price_change (product_id, price, valid_from)`. Dla każdego produktu znajdź różnicę między aktualną ceną a poprzednią. Użyj window function.
**Modelowa odpowiedź:**
```sql
SELECT 
  product_id,
  valid_from,
  price,
  LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from) AS prev_price,
  price - LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from) AS price_diff,
  ROUND(
    (price - LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from)) 
    / NULLIF(LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from), 0) * 100, 
    2
  ) AS pct_change
FROM price_change
ORDER BY product_id, valid_from;
```
Mechanika: `LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from)` zwraca cenę z poprzedniego wiersza w obrębie partycji per produkt, posortowanej rosnąco po dacie. Pierwszy wiersz dla każdego produktu ma `prev_price = NULL`. `NULLIF(..., 0)` chroni przed dzieleniem przez 0 (gdyby kiedyś była cena 0). **Optymalizacja:** powtórzony `LAG(...)` można schować w CTE/subquery, żeby się obliczył raz.
**Pułapka rozmowna:** „LAG(price, 2)" zwraca dwa wiersze wcześniej. „LAG(price, 1, 0)" zwraca poprzednią cenę albo 0 jak nie ma. Bez NULLIF dzielenie przez NULL daje NULL, ale dzielenie przez 0 daje błąd.
**Tagi:** window-functions, lag, pricing, analytics

## Q-SQL-020 [bloom: apply]
**Pytanie:** Wykonaj UPSERT cennika w Postgres: tabela `product_price (product_id, country, price, updated_at)` z unique constraint `(product_id, country)`. Wstaw lub zaktualizuj cenę dla `(123, 'PL', 99.99)`.
**Modelowa odpowiedź:**
```sql
INSERT INTO product_price (product_id, country, price, updated_at)
VALUES (123, 'PL', 99.99, NOW())
ON CONFLICT (product_id, country) 
DO UPDATE SET 
  price = EXCLUDED.price,
  updated_at = EXCLUDED.updated_at
WHERE product_price.price IS DISTINCT FROM EXCLUDED.price;  -- opcjonalne: nie robi update jak nic się nie zmieniło
```
Mechanika: `ON CONFLICT (cols)` wymaga unique constraint/index na tych kolumnach. `EXCLUDED` to pseudo-tabela z wartościami z VALUES (czyli „to co próbowałem wstawić"). Klauzula `WHERE` na końcu jest opcjonalna — pozwala uniknąć niepotrzebnych zapisów (np. trigger nie odpali, mniej WAL, mniej locków). `IS DISTINCT FROM` — null-safe `<>` (NULL <> 5 daje NULL, NULL IS DISTINCT FROM 5 daje TRUE).
**Pułapka rozmowna:** „Czemu nie SELECT then UPDATE/INSERT?" — TOCTOU race. Dwa concurrent inserts mogą oba dostać „nie istnieje" → oba INSERT → unique violation. ON CONFLICT robi to atomowo. Drugi błąd: pominięcie `updated_at = EXCLUDED.updated_at` przy update — pole zostaje stare.
**Tagi:** upsert, postgres, on-conflict, pricing

## Q-SQL-021 [bloom: apply]
**Pytanie:** Tabela `order (id, customer_id, created_at, total)` rośnie do 100M wierszy. Query `WHERE customer_id = ? ORDER BY created_at DESC LIMIT 10` zwalnia. Co byś zrobił?
**Modelowa odpowiedź:** Zacznij od EXPLAIN ANALYZE — diagnoza pierwsza. Możliwe znaleziska i działania:
1. **Brak indeksu na `customer_id`** → seq scan na 100M, oczywiście wolno. Solution: `CREATE INDEX ON order (customer_id, created_at DESC)`. **Composite index** rozwiązuje problem: lookup po customer_id jest fast, dla każdego klienta wiersze są już posortowane po created_at DESC — nie trzeba sortu.
2. **Indeks tylko na customer_id (bez created_at)** → szuka klienta szybko, ale potem sortuje wszystkie zamówienia po dacie. Solution: composite index jak wyżej.
3. **Indeks jest, ale nie używany** → statystyki nieświeże (`ANALYZE order`), albo planner wybiera Bitmap Scan zamiast Index Scan. Sprawdź `pg_stat_user_indexes`.
4. **Customer ma 100k zamówień** → nawet z indeksem heap fetch dla 10 latest może być wolny. Solution: covering index `(customer_id, created_at DESC) INCLUDE (id, total)`.
5. **Tabela bardzo "luźna"** (dużo dead tuples) → VACUUM. W skrajnych przypadkach `VACUUM FULL` lub `pg_repack`.
6. **Partycjonowanie** — przy 100M zamówień rozważ partycjonowanie po `created_at` (range partitioning) albo po `customer_id` (hash). Query po customer_id z range datą skorzysta z partition pruning.
7. **Cache aplikacyjny** — top 10 zamówień klienta to mało zmienne dane, można cachować w Redis z TTL.
**Pułapka rozmowna:** Naïve „dorzucam indeks na każdą kolumnę". Indeks composite (customer_id, created_at) służy też dla query po samym customer_id. Pojedyncze indeksy na każdej kolumnie marnują miejsce i write cost.
**Tagi:** indexes, performance, optimization, scaling

## Q-SQL-022 [bloom: apply]
**Pytanie:** Tabela `payment (id, customer_id, amount, status)`. Status jest VARCHAR z 'pending', 'completed', 'failed'. Query `WHERE status = 'pending' AND customer_id = ?` jest wolne. Co poprawisz?
**Modelowa odpowiedź:** Diagnoza: status z 3 wartościami — low cardinality, prawdopodobnie 'completed' = 95%, 'failed' = 4%, 'pending' = 1%. Indeks na samym `status` nie pomoże (selektywność za niska). **Rozwiązanie 1 — composite index z odpowiednią kolejnością:** `CREATE INDEX ON payment (customer_id, status)` — selektywność customer_id wysoka, jako leading column. Filter po status to refinement. **Rozwiązanie 2 — partial index:** `CREATE INDEX ON payment (customer_id) WHERE status = 'pending'` — indeksuje tylko ~1% wierszy (małe), idealnie dla często szukanych pending payments. Jeszcze szybsze niż composite, mniej miejsca, mniej write cost. **Rozwiązanie 3 — denormalizacja:** osobna tabela `payment_pending` (queue-style) — wstawiaj tu na pending, usuwaj na completed. Zerowa kontencja na main table. **Decyzja:** dla tej query — partial index (#2). Composite jeśli też pytasz o `status = 'completed'`. Tabela osobna jeśli jest dużo procesów które czytają tylko pending.
**Pułapka rozmowna:** Domyślne dorzucanie composite indexu — bez analizy selektywności. Partial index często jest „magic bullet" dla low-cardinality status fields, ale wielu o nim nie pamięta.
**Tagi:** indexes, partial-index, low-cardinality, optimization

## Q-SQL-023 [bloom: apply]
**Pytanie:** Tabela `audit_log (id BIGSERIAL, ts TIMESTAMP, user_id INT, action VARCHAR, payload JSONB)`. 1B wierszy, ostatnie 30 dni jest hot, reszta archiwum. Jak zoptymalizujesz?
**Modelowa odpowiedź:** **Partycjonowanie po `ts`** (range partitioning). Postgres deklaratywne partitioning od PG10:
```sql
CREATE TABLE audit_log (...) PARTITION BY RANGE (ts);
CREATE TABLE audit_log_2026_05 PARTITION OF audit_log FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
-- itd. miesięczne partycje
```
**Plusy:** 1) Query z `WHERE ts BETWEEN ...` korzysta z partition pruning — czyta tylko relevantne partycje. 2) Operacje administracyjne per-partycja: VACUUM/ANALYZE partycji jest tańszy. 3) **DROP archiwum** = `DROP TABLE audit_log_2024_01` — instant, vs. DELETE z 100M wierszy. 4) Lepsze cache locality — hot partycje fit w shared_buffers.
**Indeksy:** indeks per partycja (Postgres tworzy dla każdej automatycznie z partitioned table indeksu). **Lokalne** czy **global**? Global w Postgresie nie ma — każdy indeks lokalny per partycja. **Archiwizacja:** DETACH starych partycji, eksport do object storage (S3/Glacier) jako Parquet. **Alternatywa do partitioning:** TimescaleDB (rozszerzenie Postgres) dla time-series — automatic partitioning + dedicated optimizations.
**Pułapka rozmowna:** Partycje wymagają planowania klucza partycjonowania. Po fakcie zmiana klucza wymaga recreate. Zbyt drobne partycje (per dzień) → metadata overhead. Zbyt grube (per rok) → utrata zysków. Reguła: ~1-100 GB per partycja jest sweet spot.
**Tagi:** partitioning, time-series, scaling, postgres

## Q-SQL-024 [bloom: apply]
**Pytanie:** Napisz query zwracające „klientów którzy zamówili w styczniu, ALE NIE w lutym" tego samego roku. Tabela `order (id, customer_id, created_at)`.
**Modelowa odpowiedź:**
```sql
-- Wariant 1: NOT EXISTS (preferowany — null-safe, czytelny)
SELECT DISTINCT o.customer_id
FROM order o
WHERE o.created_at >= '2026-01-01' 
  AND o.created_at < '2026-02-01'
  AND NOT EXISTS (
    SELECT 1 FROM order o2 
    WHERE o2.customer_id = o.customer_id
      AND o2.created_at >= '2026-02-01' 
      AND o2.created_at < '2026-03-01'
  );

-- Wariant 2: EXCEPT
SELECT customer_id FROM order 
WHERE created_at >= '2026-01-01' AND created_at < '2026-02-01'
EXCEPT
SELECT customer_id FROM order 
WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01';

-- Wariant 3: LEFT JOIN + IS NULL (anti-join)
SELECT DISTINCT o1.customer_id
FROM order o1
LEFT JOIN order o2 
  ON o2.customer_id = o1.customer_id 
  AND o2.created_at >= '2026-02-01' 
  AND o2.created_at < '2026-03-01'
WHERE o1.created_at >= '2026-01-01' 
  AND o1.created_at < '2026-02-01'
  AND o2.id IS NULL;
```
Wszystkie trzy działają. **NOT EXISTS** zazwyczaj wygrywa: czytelność, performance (semi-anti join), null-safety. **EXCEPT** robi DISTINCT implicit, ale wymaga że obie strony mają ten sam typ kolumn. **LEFT JOIN + IS NULL** klasyk, ale czasem produkuje wolniejszy plan (zależy od optymalizatora).
**Pułapka rozmowna:** `NOT IN (subquery z NULL)` — rzecz znana z Q-SQL-009. Stąd preferencja NOT EXISTS. Drugi błąd: zakres dat z `<= '2026-01-31'` zamiast `< '2026-02-01'` — gubi część 31 stycznia (ostatnie godziny po północy), albo łapie 1 lutego — half-open intervals są clean.
**Tagi:** anti-join, exists, set-operations, dates

---

## Q-SQL-025 [bloom: analyze]
**Pytanie:** Twój zespół ma debatę: cennik trzymać w jednej tabeli `price (product_id, country, customer_segment, price, valid_from, valid_to)` czy w wielu tabelach (per kraj, per segment)? Argumenty?
**Modelowa odpowiedź:** **Single table:** plus — generic, query po dowolnym wymiarze proste, schema simple. Minus — może rosnąć do gigantów (10k produktów × 30 krajów × 5 segmentów × historia = setki M wierszy), partycjonowanie po jednym wymiarze suboptymalne dla query po innym. **Multiple tables:** plus — mniejsze tabele, fast lookups jeśli zawsze pytasz po kraju (bo kraj = tabela). Minus — schema explosion, ALTER schema na 30 tabelach, queries cross-country są UNION-em horror, kod aplikacyjny musi wiedzieć w którą tabelę pytać. **Realistyczne kompromisowe rozwiązania:**
1) **Single table z partycjonowaniem** po `country` (kluczowy access pattern w pricingu). Hot countries dostają własne partycje, rare = partycja default.
2) **Pricing Repository pattern** — single table backend, ale w aplikacji warstwy `priceFor(productId, country)` ukrywające szczegóły.
3) **Materialized view per kraj** — dla read-heavy reporting per kraj.
4) **Historia w osobnej tabeli** — `price_current` (aktualne) + `price_history` (versioned). Większość queries idzie do `price_current`, audyt do history.
**Decyzja:** zależy od proportions. Jeśli pricing zmienia się rzadko + czytany tysiąckrotnie częściej — single + materialized views. Jeśli wysoki write volume + query patterns są deterministic per kraj — partycjonowanie po kraju. Strawmen ekstrema (jedna tabela vs. tabela per kraj) rzadko są optymalne.
**Pułapka rozmowna:** Schema decision driven by „it might grow" zamiast measurement. Real architectures iterate — start single-table, partycjonuj gdy pojawi się problem skalowania.
**Tagi:** schema-design, pricing, partitioning, architecture

## Q-SQL-026 [bloom: analyze]
**Pytanie:** Nightly batch importuje 5M wierszy do tabeli `order_line`. Każdy import trwa 4h. Co byś zrobił by przyspieszyć?
**Modelowa odpowiedź:** Diagnoza pierwsza — co jest wolne. Możliwe winy:
1. **Indeksy** — każdy INSERT aktualizuje wszystkie indeksy. 5M × 5 indeksów = 25M write operations. Solution: **DROP indexes przed importem, CREATE po**. Bulk index build jest często 10x szybszy niż per-row update.
2. **Triggery** — per-row trigger 5M razy = trigger CPU dominuje. Disable triggers (`ALTER TABLE ... DISABLE TRIGGER ALL`), import, enable. Lub przepisać triggery na statement-level.
3. **WAL volume** — masowy INSERT generuje masowy WAL. **`COPY`** zamiast `INSERT` (Postgres) — minimal WAL overhead, native bulk load. Jak nie COPY, to multi-row INSERT (`INSERT ... VALUES (...), (...), ...`) zamiast row-by-row.
4. **Foreign keys** — sprawdzanie FK per wiersz. **Defer FK** (`SET CONSTRAINTS ALL DEFERRED` + transaction) lub disable + revalidate.
5. **Autocommit** — per-row commit jest piekielny. Batch w transakcji 5k-50k wierszy.
6. **Locking** — jeśli inne queries chodzą równolegle, wzajemne blokowanie. Window czasowy importu lub partycjonowanie + `INSERT INTO tymczasowa_partycja` + atomic swap.
7. **Sieć** — jeśli batch leci z innej maszyny over network, latency dominates. Compress + chunked transfer, lub kopiuj z S3 native.
8. **Hardware** — IOPS dysku. Sprawdź `iostat`, jak disk saturated na 100% to nawet idealny SQL nic nie pomoże. SSD/NVMe albo write-optimized config.
**Mierzona praktyka:** dropy indexes + COPY + batch transakcje często skraca 4h do <30 min. Plus profiluj.
**Pułapka rozmowna:** „Dorzuć więcej hardware" bez diagnozy. Hardware nie naprawi 5M per-row commits.
**Tagi:** bulk-import, performance, postgres, optimization

## Q-SQL-027 [bloom: analyze]
**Pytanie:** Kandydat napisał query z 6-poziomowym subquery. Code review — co byś zmienił?
**Modelowa odpowiedź:** **Pierwszy ruch:** rozbij na CTE. 6 nested subqueries to nieczytelne. CTE z `WITH a AS (...), b AS (...), c AS (...) SELECT * FROM ... JOIN ...` daje liniowy flow, nazwane kroki, łatwo debugować pojedyncze etapy. **Drugi:** zapytaj kandydata co kod robi — często nesting wynika z incremental development „dorzucam jeszcze jeden warunek" zamiast holistycznego myślenia. Refactor pozwala odkryć że trzy z subqueries dają to samo, można je zlać. **Trzeci:** sprawdź czy nie ma cross joinów / kartesianów ukrytych. **Czwarty:** EXPLAIN. Optimizer może dostać apopleksję. Czasem 6-level subquery generuje plan z 10 nested loops gdzie hash join by wystarczył. **Piąty:** pomyśl o readability dla zespołu. Performant ale unmaintainable query to liability — następny kto to czyta będzie się skarżył miesiąc. **Konkretne refactoring patterns:**
- Subquery z `IN`/`NOT IN` → `EXISTS`/`NOT EXISTS` lub anti-join.
- Scalar subquery w SELECT → JOIN z aggregate.
- Korelowana subquery powtarzająca tę samą logikę → window function.
- 3+ JOINy do tej samej tabeli → CTE z kluczową agregacją.
**Pułapka rozmowna:** „Działa, nie ruszać" — debt rośnie. Inwestycja w refactor zwraca się przy następnej zmianie.
**Tagi:** code-review, cte, refactoring

## Q-SQL-028 [bloom: analyze]
**Pytanie:** Pricing platforma używa Postgresa. Klient pyta: „przesiąść się na NoSQL żeby skalować". Twoja rekomendacja?
**Modelowa odpowiedź:** Naïve rekomendacja „tak, NoSQL = scale" jest niebezpieczna. Pytania pierwsze:
1. **Co nie skaluje?** Reads vs writes? CPU vs IO vs locking? Bez diagnozy przesiadka jest cargo cult.
2. **Co jest data model?** Pricing ma silne wymagania spójności (cena musi być zaaplikowana atomowo, batch update musi być transakcyjny). NoSQL eventual consistency zazwyczaj złamie te wymagania.
3. **Co to za queries?** Pricing zazwyczaj robi: lookup po (product, country, customer) — to działa idealnie w KV store (Redis, DynamoDB). Ale też ad-hoc reports, analytics, history — to trudniej w NoSQL.
**Realistyczne ścieżki skalowania Postgres ZANIM porzucisz:**
- **Read replicas** — odczyt z replik, write do mastera. Skala 5-10x na read.
- **Connection pooling** (PgBouncer) — eliminacja overhead per connection.
- **Partycjonowanie** — patrz Q-SQL-023.
- **Materialized views** — pre-computed aggregates dla raportów.
- **Caching layer** — Redis dla hot pricelist lookups (cache-aside pattern). Cena pricinga rzadko zmienia się — cache hit rate >95%.
- **Sharding** — Citus extension, Vitess-style. Z trudem ale działa.
- **Read-heavy replicas with logical replication.**
**Kiedy faktycznie NoSQL sensowne:** wysoki volume KV (np. session store, real-time scoring) gdzie ACID-y się nie przydają, ALBO denormalized document store dla complex nested structures gdzie SQL JOIN-y są piekielne.
**Pricing engine specyfika:** zazwyczaj Postgres + Redis cache + partitioning + materialized views to optymalna stack. Czysty NoSQL łamie spójność cennika, a hybrid jest ok.
**Pułapka rozmowna:** „MongoDB skaluje" — w teorii. W praktyce wiele startupów wracało z MongoDB do Postgres po odkryciu że complex queries są bolesne i transactional guarantees są potrzebne. Don't outscale problems you don't have.
**Tagi:** nosql, scaling, architecture, pricing, decision

## Q-SQL-029 [bloom: analyze]
**Pytanie:** Index na `(country, customer_segment, product_id)` — czy pomoże w query `WHERE customer_segment = 'B2B' AND product_id = 123`? Wyjaśnij.
**Modelowa odpowiedź:** **NIE bezpośrednio**, dlatego że composite B-tree index działa „od lewej". Indeks `(country, customer_segment, product_id)` jest sortowany najpierw po country, potem (w obrębie kraju) po segment, potem po product. Query bez `country` w WHERE nie może użyć indeksu jako primary lookup — musiałby przeskanować wszystkie wartości country żeby znaleźć segment+product. **Co planner zrobi:** 1) Może zrobić **Index Skip Scan** (Oracle, MySQL 8.0+, częściowo Postgres 18+) — efektywnie iteruje po distinct country i robi lookup w każdym. Działa ok dla low-cardinality leading column. Postgres < 18 nie ma natywnie. 2) Może zrobić **Bitmap Index Scan** + filter na rest. 3) Najpewniej jednak **Seq Scan + Filter**, jeśli kraj jest high-cardinality. **Rozwiązania:**
- Dodać osobny indeks: `CREATE INDEX ON ... (customer_segment, product_id)`.
- Zmienić leading column composite indexu na najczęstszy filter.
- Jeśli query po (segment, product) jest hot — covering index `(customer_segment, product_id) INCLUDE (country, price)`.
**Reguła kciuka:** composite index helps queries that have leading column(s) in WHERE. Order matters. Always benchmark with `EXPLAIN ANALYZE`.
**Pułapka rozmowna:** Pomylenie B-tree (sorted) z bitmap (set ops). Bitmap ma inne właściwości — kombinuje wiele indeksów. Drugi błąd: zakładanie że Postgres robi skip scan jak MySQL. Sprawdź wersję.
**Tagi:** indexes, composite-index, query-optimization

## Q-SQL-030 [bloom: analyze]
**Pytanie:** Soft delete (`deleted_at TIMESTAMP NULL`) vs hard delete vs separate `archived` table. Trade-offy.
**Modelowa odpowiedź:** **Hard delete** (`DELETE FROM ...`): plus — schema simple, queries simple, brak NULL noise w aplikacji, zwolnione miejsce. Minus — utracony audit trail, brak możliwości undo, FK cascade musisz przemyśleć (np. `ON DELETE CASCADE` rzeczywiście usuwa zależne dane). **Soft delete** (`UPDATE SET deleted_at = NOW()`): plus — undo możliwy, audit, FK się nie psuje. Minus — każde query musi pamiętać `WHERE deleted_at IS NULL` (łatwo zapomnieć — bug). UNIQUE constraints na nullowalnych kolumnach łamią się (np. unique email — soft-deleted user trzyma email, nowy user nie może użyć). Performance: tabela rośnie, indeksy rosną, queries muszą filtrować. **Separate archive table**: plus — main table mała i szybka, archive osobny lifecycle (osobne backup/retention), partycjonowanie sensowne. Minus — query by historię wymaga UNION over both, schema duplication, migration complexity gdy schema się zmieni. **Decyzja per case:**
- **Compliance / audit wymagany** (financial, healthcare) → archive table z immutable, plus event sourcing event log.
- **Soft delete jako default w domenie z normalnym CRUD** (users, products) → soft delete, ale z dyscypliną (view filtering, lub partial indexes).
- **High volume + low compliance** → hard delete, ewentualnie z `deleted_audit_log` osobnym.
**Pricing engine konkretnie:** historia cen = bardzo ważna (chargebacki, audyt rozliczeń). Wzorzec: `price` aktualne + `price_history` archive. Hard delete na pricach = nigdy. Aktualne ceny w `price` (write-heavy queries fast), historia w `price_history` (rzadko czytana, ale must exist).
**Pułapka rozmowna:** Soft delete robi się trudniejszy z czasem. Po roku połowa rekordów to soft-deleted i performance siada. Reguła: jeśli soft delete, planuj process pełnego usuwania starych soft-deletes po retention period.
**Tagi:** soft-delete, archive, schema-design, audit, pricing

## Q-SQL-031 [bloom: analyze]
**Pytanie:** Twój pricing engine generuje raport „top 10 produktów per region" co 5 minut. Wymaga pełnego JOIN-a 5 tabel po 10M wierszy. Jak zrobisz to wydajnie?
**Modelowa odpowiedź:** Naïve rerun every 5 min — nie wytrzyma. **Strategie:**
1. **Materialized view** z `REFRESH MATERIALIZED VIEW CONCURRENTLY top_products_by_region;` raz na 5 min. CONCURRENTLY pozwala równoczesne odczyty (ale wymaga unique index na MV). Plus — query staje się trywialne `SELECT * FROM top_products_by_region`. Minus — refresh kosztuje zasób.
2. **Incremental aggregation** — gdy nowe order_line wpada, update aggregate w `top_products_aggregate (region, product_id, total)` triggerem albo asynchronicznie z message queue. Plus — koszt rozłożony, raport zawsze świeży. Minus — complexity, eventual consistency window.
3. **Redis sorted sets** — `ZADD top_products:PL <score> <product_id>`. Top 10 = `ZREVRANGE top_products:PL 0 9`. Update na każde new order asynchronicznie. O(log n) updates, O(log n + k) reads. Idealne dla real-time leaderboards.
4. **OLAP cube / data warehouse** — jeśli reportowanie urosło do prawdziwego BI, oddzielny system (Snowflake, BigQuery, ClickHouse) z ETL co 5 min. Pricing engine zostaje OLTP.
5. **Query-level optimization** — partycjonowanie order_line po regionie, covering indexes, statystyki, parallel query. Może wystarczy skrócić czas pojedynczego runu z 4 min do 30s, wtedy 5-min refresh nie boli.
**Decyzja:** zacznij od materialized view + dobrego indeksu. Jeśli to OK — skończone. Jeśli nie — incremental aggregation. Real-time scoring pricing? Redis. Heavy BI? Hurtownia.
**Pułapka rozmowna:** „Zrób materialized view i koniec" — bez planowania REFRESH (concurrency, blocking, lag) → znajdziesz się w sytuacji „raport jest stale przez 8 godzin bo refresh nigdy się nie kończy".
**Tagi:** materialized-view, real-time, pricing, scaling, decision

## Q-SQL-032 [bloom: analyze]
**Pytanie:** Code review: programista usunął foreign key constraint mówiąc „spowalnia INSERT-y, sprawdzimy w aplikacji". Zgadzasz się?
**Modelowa odpowiedź:** **Nie zgadzam się — i poproszę o uzasadnienie measurementem.** Argumenty:
1. **FK to safety net.** Aplikacja może mieć bug, mogą być batch jobs piszące spoza aplikacji (np. SQL z konsoli admina), migration scripts, third-party integration. Bez FK, każdy z tych source'ów może wprowadzić orphans. Database constraint to last line of defense.
2. **Performance hit FK często przeszacowany.** Indeksowane FK (FK kolumna ma indeks) są tanie w validation. FK bez indeksu — TAK, problem (każdy INSERT scanuje parent table).
3. **Refactor without FK = dłuższy debugging.** Bug typu „klient skasowany, ale jego payment'y wiszą" w produkcji = godziny investigation. Z FK by wybuchło natychmiast.
4. **"Sprawdzimy w aplikacji" = false promise.** W praktyce nikt nie sprawdza. Multiplier service'ów, microservices, batch jobs — kto będzie wszystkie pamiętał? Konstruktorzy DDD czasem argumentują against FK na poziomie agregatów, ale to inne ramy filozoficzne i wymagają dyscypliny.
**Co zrobiłbym zamiast usuwania:**
- Jeśli FK realnie wolne — sprawdź czy FK column ma indeks (zazwyczaj brakuje).
- `DEFERRABLE INITIALLY DEFERRED` — sprawdzenie FK na końcu transakcji, nie per-row.
- Bulk inserts z FK temporarily disabled (`ALTER TABLE DISABLE TRIGGER ALL` + revalidate) — tylko podczas batch import, nie permanentnie.
- Partycjonowanie + FK na partycje (Postgres 12+).
**Pułapka rozmowna:** „W microservices nie ma FK bo tabele są w różnych DB" — prawda dla cross-service. Ale w obrębie service'u FK są nadal sensowne. „Performance" jako wymówka bez liczb — czerwona flaga w code review.
**Tagi:** foreign-keys, code-review, integrity, design
