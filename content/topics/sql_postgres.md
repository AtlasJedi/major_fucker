# SQL & PostgreSQL — question bank

> SQL and PostgreSQL are mandatory knowledge for any senior Java/Kotlin backend engineer. Every production system touches a relational database, and the gap between a junior who "can write queries" and a senior who understands execution plans, MVCC, locking, index internals, and transaction isolation is enormous. This bank covers ANSI SQL fundamentals, PostgreSQL-specific behavior (MVCC, VACUUM, SSI, index types), query optimization, schema design, and operational concerns. A 5-year backend engineer preparing for senior interviews must be able to reason about performance, explain PostgreSQL internals, and make defensible schema decisions — not just produce syntactically correct SQL.

## Scope

- JOINs: INNER, LEFT, RIGHT, FULL OUTER, CROSS, SELF, semi-join, anti-join; WHERE-filter trap on outer joins
- GROUP BY, HAVING, execution order (FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT)
- Subqueries: scalar, correlated vs non-correlated, EXISTS/IN, NOT EXISTS vs NOT IN (NULL trap)
- CTEs: WITH clause, materialized vs inlined (PG 12+), recursive CTEs (WITH RECURSIVE), cycle protection
- Window functions: ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, SUM/AVG OVER (PARTITION BY / ORDER BY / ROWS/RANGE frame)
- Indexes: B-tree (default), Hash (equality only), GIN (JSONB/arrays/full-text), GiST (geometry/ranges), BRIN (append-only time-series), partial index, expression/functional index, composite + leftmost-prefix rule, covering index (INCLUDE), when indexes hurt
- EXPLAIN / EXPLAIN ANALYZE: reading cost estimates, Seq Scan vs Index Scan vs Index Only Scan vs Bitmap Heap Scan, join strategies (Nested Loop / Hash Join / Merge Join), rows-estimate vs actual, BUFFERS option
- Sargability: what makes a predicate sargable, function-on-column anti-pattern, implicit casts
- Transaction isolation levels: READ UNCOMMITTED / READ COMMITTED / REPEATABLE READ / SERIALIZABLE; anomalies: dirty read, non-repeatable read, phantom read, lost update; PostgreSQL defaults and SSI
- MVCC: xmin/xmax, row versions (tuples), snapshot isolation, readers do not block writers, dead tuples, table bloat
- VACUUM / autovacuum: what VACUUM does vs VACUUM FULL, autovacuum tuning (scale_factor), txid wraparound, pg_stat_user_tables monitoring
- Locking: row-level (FOR UPDATE / FOR SHARE), table-level, advisory locks, deadlocks, DEFERRABLE constraints
- ACID: atomicity, consistency, isolation, durability; WAL / fsync
- Normalization: 1NF, 2NF, 3NF, BCNF; when to denormalize
- Connection pooling: HikariCP (per-JVM), PgBouncer (external proxy, session/transaction/statement modes), pool sizing
- Partitioning: range, list, hash; partition pruning, local indexes, archiving via DETACH
- JSONB: storage format vs JSON, operators (->, ->>, @>, ?), GIN index, expression index for specific paths
- UPSERT: INSERT ... ON CONFLICT (DO UPDATE / DO NOTHING), EXCLUDED pseudo-table, sargability of conflict target
- Pagination: OFFSET/LIMIT (deep-page performance collapse) vs keyset pagination (WHERE id > last_id ORDER BY id LIMIT n)

---

## Q-SQL-001 [bloom: recall] [level: junior]
**Question:** What is the difference between INNER JOIN and LEFT JOIN? Give an example of when LEFT JOIN is the right choice.

**Model answer:** `INNER JOIN` returns only rows that have a matching row on both sides of the join condition. If there is no match, the row is excluded entirely. `LEFT JOIN` returns all rows from the left table plus matched rows from the right; when there is no match, columns from the right table are NULL. `RIGHT JOIN` is the symmetric opposite and is rarely used in practice — you can always flip the table order and use a LEFT JOIN instead. `FULL OUTER JOIN` returns all rows from both sides, with NULLs where no match exists on the respective side.

Example where LEFT JOIN is correct: find all customers who have never placed an order.
```sql
SELECT c.id, c.name
FROM customer c
LEFT JOIN orders o ON o.customer_id = c.id
WHERE o.id IS NULL;
```
With INNER JOIN, customers without orders would be eliminated.

**Interview trap:** "LEFT JOIN ... WHERE right_table.column = X" — the WHERE filter eliminates NULL rows from the right side, effectively converting the LEFT JOIN into an INNER JOIN. Filters that must preserve non-matching rows belong in the ON clause: `LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'active'`.

**Tags:** join, basics, null

---

## Q-SQL-002 [bloom: recall] [level: junior]
**Question:** What does GROUP BY do and when do you need HAVING instead of WHERE?

**Model answer:** `GROUP BY` collapses rows that share the same values in the specified columns into a single group. Every column in SELECT must either appear in GROUP BY or be wrapped in an aggregate function (SUM, COUNT, AVG, MIN, MAX). `WHERE` filters individual rows before grouping. `HAVING` filters groups after aggregation — it can reference aggregate expressions.

SQL logical execution order: FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT. This ordering is why you cannot use a SELECT alias in WHERE or HAVING (the alias is not yet computed at those stages). Exception: PostgreSQL allows column aliases in ORDER BY and sometimes in GROUP BY.

```sql
-- Customers with more than 10 orders in the last 30 days
SELECT customer_id, COUNT(*) AS order_count
FROM orders
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY customer_id
HAVING COUNT(*) > 10;
```

`WHERE COUNT(*) > 10` would be a syntax error — aggregates are not allowed in WHERE.

**Interview trap:** Can you use a column alias from SELECT in GROUP BY? In standard SQL no (alias defined later in pipeline). PostgreSQL does allow it in GROUP BY for convenience but not in HAVING. Know the rule and the exception.

**Tags:** aggregation, group-by, having, basics

---

## Q-SQL-003 [bloom: recall] [level: junior]
**Question:** What are the four ACID properties and what does each guarantee?

**Model answer:** ACID defines the reliability guarantees a database transaction must provide:

- **Atomicity** — the transaction is all-or-nothing. If any statement fails, the entire transaction is rolled back and the database is as if the transaction never happened. Implemented via undo log / WAL.
- **Consistency** — a transaction takes the database from one valid state to another. All constraints (NOT NULL, FK, CHECK, UNIQUE) are satisfied after commit. The application also bears responsibility for logical consistency.
- **Isolation** — concurrent transactions do not see each other's partial changes. The degree of isolation is configurable via isolation levels. Without isolation, dirty reads, non-repeatable reads, and phantom reads are possible.
- **Durability** — once a transaction commits, its changes survive crashes. PostgreSQL achieves this via Write-Ahead Logging (WAL): changes are written to the WAL and fsync'd to disk before the commit is acknowledged.

**Interview trap:** "NoSQL databases don't have ACID." False — MongoDB has multi-document transactions since 4.0, FoundationDB provides full ACID. ACID is a tradeoff (cost of consensus) not an SQL-only property. The real differentiation is that many NoSQL systems traded ACID for availability/partition tolerance.

**Tags:** transactions, acid, basics, durability

---

## Q-SQL-004 [bloom: recall] [level: junior]
**Question:** Name and briefly describe three window functions. How do they differ from GROUP BY aggregates?

**Model answer:** Window functions compute a value across a set of rows related to the current row (the "window") without collapsing rows the way GROUP BY does. Each row keeps its individual identity; the aggregate is added alongside it.

Three key window functions:

1. **`ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)`** — assigns a unique sequential integer to each row within the partition. Ties get different numbers (non-deterministic order among tied rows).

2. **`RANK() / DENSE_RANK() OVER (...)`** — like ROW_NUMBER but handles ties. RANK skips rank numbers after ties (1, 1, 3, 4). DENSE_RANK does not skip (1, 1, 2, 3). Use DENSE_RANK when you want "top N distinct rank positions."

3. **`LAG(salary, 1) OVER (PARTITION BY dept ORDER BY hire_date)`** — returns the value of `salary` from the previous row in the window. `LEAD` is the forward-looking version. Used to compute period-over-period deltas without a self-join.

Others worth knowing: `SUM/COUNT/AVG OVER (...)` for running totals, `FIRST_VALUE`, `LAST_VALUE`, `NTILE(n)`, `PERCENT_RANK`, `CUME_DIST`.

Key difference from GROUP BY: window functions run after GROUP BY and produce one output row per input row. `SELECT id, salary, SUM(salary) OVER () FROM emp` returns every employee row with the company-wide total alongside — GROUP BY would collapse to one row.

**Interview trap:** Window functions are evaluated after WHERE, GROUP BY, and HAVING but before ORDER BY and LIMIT. You cannot filter on a window function result in WHERE — you must wrap in a subquery or CTE.

**Tags:** window-functions, rank, lag, lead, basics

---

## Q-SQL-005 [bloom: recall] [level: junior]
**Question:** What is a CTE (Common Table Expression) and what is the syntax for a recursive CTE?

**Model answer:** A CTE is a named, temporary result set defined with the `WITH` keyword, scoped to the single query that follows it. It improves readability by breaking complex queries into named steps and allows the same subquery to be referenced multiple times.

```sql
WITH recent_orders AS (
  SELECT customer_id, SUM(total) AS total_spend
  FROM orders
  WHERE created_at >= NOW() - INTERVAL '30 days'
  GROUP BY customer_id
)
SELECT c.name, ro.total_spend
FROM customer c
JOIN recent_orders ro ON ro.customer_id = c.id
ORDER BY ro.total_spend DESC;
```

A **recursive CTE** (`WITH RECURSIVE`) consists of an anchor member (non-recursive base case) and a recursive member joined by `UNION ALL`. It iterates until the recursive member returns no rows:

```sql
WITH RECURSIVE org_tree AS (
  -- anchor: root employees (no manager)
  SELECT id, manager_id, name, 1 AS depth
  FROM employee
  WHERE manager_id IS NULL

  UNION ALL

  -- recursive: add direct reports
  SELECT e.id, e.manager_id, e.name, ot.depth + 1
  FROM employee e
  JOIN org_tree ot ON e.manager_id = ot.id
)
SELECT * FROM org_tree ORDER BY depth, name;
```

**Interview trap:** Before PostgreSQL 12, CTEs were always "optimization fences" — the planner could not push predicates into them, which sometimes caused poor plans. Since PG 12, CTEs are inlined by default unless you write `WITH foo AS MATERIALIZED (...)` to force materialization. Knowing this version boundary is a senior signal.

**Tags:** cte, recursive-cte, with, basics

---

## Q-SQL-006 [bloom: recall] [level: junior]
**Question:** What are the four standard transaction isolation levels and which anomalies does each prevent?

**Model answer:** The SQL standard defines four isolation levels with these anomaly guarantees:

| Level | Dirty Read | Non-repeatable Read | Phantom Read |
|-------|-----------|---------------------|--------------|
| READ UNCOMMITTED | possible | possible | possible |
| READ COMMITTED | prevented | possible | possible |
| REPEATABLE READ | prevented | prevented | possible (standard) |
| SERIALIZABLE | prevented | prevented | prevented |

Anomaly definitions:
- **Dirty read** — transaction A reads data written by transaction B that has not yet committed. If B rolls back, A read data that never existed.
- **Non-repeatable read** — transaction A reads the same row twice; transaction B updates and commits between the two reads; A gets different values.
- **Phantom read** — transaction A executes the same range query twice; transaction B inserts/deletes a row matching the range and commits between the reads; A gets a different set of rows.

**PostgreSQL specifics:** PG has no true READ UNCOMMITTED — it silently treats it as READ COMMITTED. PG default is READ COMMITTED. Under PG REPEATABLE READ, phantom reads are also prevented (PG uses snapshot isolation which blocks phantoms). PG SERIALIZABLE uses SSI (Serializable Snapshot Isolation) — it may abort a transaction and requires a retry.

**Interview trap:** MySQL InnoDB default is REPEATABLE READ; Oracle and PostgreSQL default is READ COMMITTED; MS SQL default is READ COMMITTED (lock-based). Always qualify which DBMS you are discussing. Also: PostgreSQL never allows dirty reads even at READ UNCOMMITTED.

**Tags:** transactions, isolation, dirty-read, phantom, basics

---

## Q-SQL-007 [bloom: recall] [level: junior]
**Question:** What is a B-tree index and for what types of queries is it suited? When does a B-tree index NOT help?

**Model answer:** A B-tree (balanced tree) index is the default index type in PostgreSQL. It stores sorted key values in a balanced tree structure with O(log n) lookup. Leaf nodes contain key values plus heap tuple pointers (CTIDs). It supports:

- Equality: `WHERE col = ?`
- Range: `WHERE col > ?`, `WHERE col BETWEEN ? AND ?`
- Prefix matching: `WHERE col LIKE 'foo%'` (but NOT `LIKE '%foo'`)
- `ORDER BY` on the indexed column (avoids an explicit sort step)
- `IS NULL` / `IS NOT NULL` (PG specific)

B-tree does NOT help when:
1. **Low cardinality** — a `boolean` column or a 3-value `status` field where one value covers 90% of rows. The optimizer prefers a sequential scan over random heap I/O for large fractions of the table.
2. **Function applied to the column** in the predicate: `WHERE LOWER(email) = 'x'` cannot use an index on `email` — the sort order of the index does not match the transformed values. Fix: create a functional index `ON users (LOWER(email))`.
3. **Implicit type cast** in the predicate: `WHERE numeric_col = '42'` — the cast can prevent index use. Always match types.
4. **Very small tables** — the planner prefers seq scan when the whole table fits in a few pages.

**Interview trap:** Hash indexes — equality-only, O(1) lookup, no range support, no ORDER BY. In PostgreSQL they are WAL-logged since PG 10 (previously unsafe for crash recovery). In practice the planner rarely chooses them over B-tree for equality because the planning overhead is not worth the marginal benefit.

**Tags:** indexes, b-tree, performance, basics

---

## Q-SQL-008 [bloom: recall] [level: junior]
**Question:** What is UPSERT and how do you write it in PostgreSQL?

**Model answer:** UPSERT is an atomic "insert if not exists, update if exists" operation. In PostgreSQL it is written with `INSERT ... ON CONFLICT`:

```sql
INSERT INTO product_price (product_id, country, price, updated_at)
VALUES (123, 'PL', 99.99, NOW())
ON CONFLICT (product_id, country)
DO UPDATE SET
  price     = EXCLUDED.price,
  updated_at = EXCLUDED.updated_at
WHERE product_price.price IS DISTINCT FROM EXCLUDED.price;
```

Key points:
- `ON CONFLICT (cols)` requires a unique constraint or unique index on those columns; without it the conflict cannot be detected.
- `EXCLUDED` is a pseudo-table containing the row that would have been inserted (the proposed values).
- `DO NOTHING` silently skips the row on conflict instead of updating.
- The optional `WHERE` on the DO UPDATE clause prevents unnecessary writes when the value has not changed — reduces WAL volume and avoids triggering row-level triggers.
- `IS DISTINCT FROM` is a null-safe inequality (`NULL IS DISTINCT FROM 5` = TRUE; regular `<>` with NULL = NULL).

PostgreSQL 15+ also supports ANSI `MERGE` syntax.

**Interview trap:** Doing `SELECT then INSERT/UPDATE` in application code is a TOCTOU race condition. Two concurrent threads can both see "row does not exist," both attempt INSERT, and one gets a unique constraint violation. `ON CONFLICT` handles this atomically.

**Tags:** upsert, on-conflict, postgres, concurrency

---

## Q-SQL-009 [bloom: understand] [level: regular]
**Question:** Explain the difference between correlated and non-correlated subqueries and when each matters for performance.

**Model answer:** A **non-correlated subquery** is self-contained — it executes once and its result is used by the outer query. The optimizer can materialize it and reuse the result.

```sql
-- Non-correlated: subquery runs once, returns a set
SELECT * FROM customer
WHERE country_code IN (SELECT code FROM active_countries);
```

A **correlated subquery** references columns from the outer query, so it must be re-evaluated for each row the outer query processes. Naively this is O(N * M).

```sql
-- Correlated: re-executed per customer row
SELECT c.name
FROM customer c
WHERE EXISTS (
  SELECT 1 FROM orders o
  WHERE o.customer_id = c.id AND o.total > 1000
);
```

**Performance reality:** modern optimizers (including PostgreSQL) often rewrite correlated subqueries to semi-joins or hash joins internally — check EXPLAIN to verify. `EXISTS` is generally preferred over `IN` for correlated checks because:
1. EXISTS short-circuits on the first match.
2. `NOT IN (subquery)` returns no rows if the subquery contains any NULL (because `x NOT IN (..., NULL)` evaluates to UNKNOWN). `NOT EXISTS` does not have this bug.

**Interview trap:** `NOT IN (SELECT ... FROM t WHERE ...)` — if any row in `t` has the correlated column as NULL (even unrelated rows), the entire NOT IN returns false for every outer row. This is the classic SQL NULL trap that trips up experienced developers. Always use NOT EXISTS for anti-joins.

**Tags:** subquery, correlated, exists, not-in, null, performance

---

## Q-SQL-010 [bloom: understand] [level: regular]
**Question:** How do you read EXPLAIN ANALYZE output in PostgreSQL? What are the key nodes to recognize and what signals indicate a problem?

**Model answer:** `EXPLAIN` shows the planner's estimated execution plan without running the query. `EXPLAIN ANALYZE` runs the query and shows both estimates and actuals. Read the plan tree bottom-up — leaf nodes are data sources, parent nodes are operations on their children.

**Key plan nodes:**

| Node | Meaning |
|------|---------|
| Seq Scan | Full table scan. Fine for small tables or large fractions of a big table. |
| Index Scan | Looks up rows via index, then fetches the heap for non-covered columns (random I/O). |
| Index Only Scan | All required columns are in the index — no heap fetch needed. Fastest. |
| Bitmap Index Scan | Builds a bitmap of matching heap pages, then fetches them in bulk (ordered I/O). Good for moderate selectivity. |
| Nested Loop | For each outer row, scans inner side. Good for small inner sets; terrible for large cross products. |
| Hash Join | Builds a hash table from the smaller side, probes with the larger. Good for large equi-joins. |
| Merge Join | Both sides must be pre-sorted. Good when inputs are already sorted on the join key. |
| Hash Aggregate / Sort | Aggregation and sorting operators. Expensive if spilling to disk. |

**Red flags to look for:**
- **Seq Scan on a large table with a filter** — check if an index exists on the filter column and why the planner is not using it (check selectivity, statistics, function-on-column).
- **Rows estimate vs actual differ by >10x** — statistics are stale; run `ANALYZE table_name`.
- **Sort with `Sort Method: external merge`** — query is spilling to disk; increase `work_mem` or add an index that provides pre-sorted output.
- **Nested Loop on two large tables** — almost always a problem; investigate missing indexes or wrong join order.
- **Index Scan where Index Only Scan was expected** — the visibility map may need VACUUM to enable index-only scans.

Reading cost syntax: `cost=startup..total rows=N width=W`. Costs are planner-internal units (abstract disk page fetches), not milliseconds. `actual time=X..Y` in ANALYZE output is milliseconds.

**Interview trap:** `EXPLAIN ANALYZE` on a DELETE or UPDATE actually executes the statement and modifies data. Always wrap in `BEGIN; EXPLAIN ANALYZE ...; ROLLBACK;` in production or on important data. Add `BUFFERS` for cache hit/miss analysis: `EXPLAIN (ANALYZE, BUFFERS)`.

**Tags:** explain, query-plan, seq-scan, index-scan, performance, postgres

---

## Q-SQL-011 [bloom: understand] [level: regular]
**Question:** What is the leftmost prefix rule for composite indexes in PostgreSQL? Give an example of a query that will and will not use an index on (country, status, created_at).

**Model answer:** A composite B-tree index `(country, status, created_at)` stores rows sorted first by country, then by status within each country, then by created_at within each (country, status) pair. The index can only be entered from the leftmost key — like looking up a word in a dictionary that is sorted by first letter, then second letter, etc.

**Queries that CAN use the index (leftmost prefix present):**
```sql
WHERE country = 'PL'                                         -- uses prefix: country
WHERE country = 'PL' AND status = 'active'                  -- uses prefix: country, status
WHERE country = 'PL' AND status = 'active' AND created_at > '2026-01-01'  -- full key
WHERE country = 'PL' ORDER BY created_at                    -- index scan, country filter + sort
```

**Queries that CANNOT efficiently use the index:**
```sql
WHERE status = 'active'                -- skips leading column country; likely seq scan
WHERE created_at > '2026-01-01'        -- skips both leading columns
WHERE status = 'active' AND created_at > '2026-01-01'  -- still no leading column
```

For the non-leading-column queries: PostgreSQL older than 18 has no native skip scan. The planner may use a Bitmap Index Scan if the leading column has low cardinality, but generally will fall back to seq scan for high-cardinality leading columns.

**Interview trap:** The index IS useful for range conditions on the LAST key even when earlier keys are equality-matched. `WHERE country = 'PL' AND status = 'active' AND created_at > ?` — the range on `created_at` is fine because `country` and `status` are equality-pinned. But `WHERE country BETWEEN 'A' AND 'M'` blocks efficient use of `status` and `created_at` — once the leading key is a range, the remaining keys cannot be used for index lookup.

**Tags:** indexes, composite-index, leftmost-prefix, query-optimization

---

## Q-SQL-012 [bloom: understand] [level: regular]
**Question:** What makes a query predicate "sargable" and what are the common anti-patterns that break sargability?

**Model answer:** A predicate is **sargable** (Search ARGument ABLE) if the database engine can use an index to satisfy it. Non-sargable predicates force a full scan even when an index exists.

**Common non-sargable patterns and their fixes:**

1. **Function applied to indexed column:**
```sql
-- Non-sargable (index on email is useless here)
WHERE LOWER(email) = 'john@example.com'

-- Fix: functional index  OR store data pre-normalized
CREATE INDEX ON users (LOWER(email));
WHERE LOWER(email) = 'john@example.com'  -- now uses the functional index
```

2. **Implicit type cast:**
```sql
-- Non-sargable if id is INTEGER
WHERE id = '42'   -- string '42' requires cast; may prevent index use

-- Fix: use correct type
WHERE id = 42
```

3. **Leading wildcard in LIKE:**
```sql
WHERE name LIKE '%smith'   -- cannot use B-tree prefix; requires full scan or GIN trigram index
WHERE name LIKE 'smith%'   -- sargable with B-tree
```

4. **Arithmetic on column:**
```sql
WHERE created_at + INTERVAL '1 day' > NOW()  -- column modified; not sargable
WHERE created_at > NOW() - INTERVAL '1 day'  -- sargable: constant on right side
```

5. **NOT IN with NULLs** (semantic issue, but also affects plan):
```sql
WHERE id NOT IN (SELECT parent_id FROM category)  -- if parent_id has NULLs, returns nothing
```

**Interview trap:** The optimizer sometimes compensates for non-sargable predicates via expression indexes or implicit casts — always verify with EXPLAIN ANALYZE. Never assume; measure.

**Tags:** sargable, indexes, performance, function-on-column, implicit-cast

---

## Q-SQL-013 [bloom: understand] [level: regular]
**Question:** What is a covering index and what is the difference between a key column and an INCLUDE column in PostgreSQL?

**Model answer:** A covering index contains all columns required by a query, allowing PostgreSQL to satisfy the query entirely from the index without accessing the heap (the main table data). This is called an **Index Only Scan** and eliminates random heap I/O.

In PostgreSQL (since PG 11), `INCLUDE` columns are stored only in the index leaf pages — they are not part of the sort key:

```sql
CREATE INDEX idx_orders_by_customer
ON orders (customer_id, created_at DESC)
INCLUDE (id, total, status);
```

This index can answer: `SELECT id, total, status FROM orders WHERE customer_id = ? ORDER BY created_at DESC LIMIT 10` entirely from the index.

**Key column vs INCLUDE column:**
- **Key columns** `(customer_id, created_at)` determine the sort order and are used for index scans and range lookups. Bloom filters, bitmap intersection — all done on key columns.
- **INCLUDE columns** `(id, total, status)` are just along for the ride — stored at leaf level only, not sorted, not usable in WHERE or ORDER BY. They make the index larger but add no lookup capability.

**When to use INCLUDE:** when the extra columns are too large or too volatile to be key columns (adding a frequently-updated column as a key column forces index updates on every modification), but the query needs them to avoid a heap fetch.

**Interview trap:** Index Only Scan in PostgreSQL still requires the visibility map to be up-to-date per page. If a page has not been vacuumed recently, PostgreSQL must visit the heap to check visibility even with a covering index. Heavy write workloads without adequate VACUUM will see Index Only Scan fall back to Bitmap Heap Scan.

**Tags:** indexes, covering-index, include, index-only-scan, postgres

---

## Q-SQL-014 [bloom: understand] [level: regular]
**Question:** When would you use a partial index and an expression (functional) index? Give concrete examples of each.

**Model answer:** **Partial index** — indexes only the rows that match a predicate. Smaller, faster to update, and often the right tool for low-cardinality status fields:

```sql
-- Index only active users; perfect for WHERE active = true queries
CREATE INDEX idx_active_users ON users (email) WHERE active = true;

-- Index only pending payments; 'pending' = ~1% of rows
CREATE INDEX idx_pending_payments ON payments (customer_id) WHERE status = 'pending';
```

Benefits: the index is tiny (covers only a fraction of the table), write cost is paid only for rows that match the predicate, and the planner uses it for queries that include the partial predicate.

**Expression (functional) index** — indexes the result of an expression rather than a raw column value:

```sql
-- Enables sargable case-insensitive email lookup
CREATE INDEX idx_users_email_lower ON users (LOWER(email));
-- Query must use the exact same expression:
WHERE LOWER(email) = 'john@example.com'

-- Index a JSONB field for equality queries on a specific path
CREATE INDEX idx_users_role ON users ((data->>'role'));
WHERE data->>'role' = 'admin'
```

The query's WHERE clause must use the exact same expression as the index definition for the planner to match them.

**Interview trap:** For the planner to use a functional index, the predicate in the query must be written identically to the index expression. `WHERE LOWER(email) = ?` uses `idx_email_lower`; `WHERE email ILIKE ?` does not. Also: expression indexes update on every row modification that changes the expression's result, which adds write overhead — benchmark on write-heavy tables.

**Tags:** indexes, partial-index, expression-index, functional-index, performance

---

## Q-SQL-015 [bloom: understand] [level: regular]
**Question:** What are GIN, GiST, and BRIN indexes in PostgreSQL? When would you use each?

**Model answer:** PostgreSQL supports pluggable index access methods beyond B-tree and Hash:

**GIN (Generalized Inverted Index):** Designed for composite values where you want to index the individual elements. Each element points back to the containing row.
- Use for: JSONB (`@>`, `?` operators), arrays (`@>`, `&&`), full-text search (`@@` with `tsvector`).
- Build cost: high (slow to build/update). Use `fastupdate` setting to batch updates.
- Query speed: fast for containment and key-exists queries.
```sql
CREATE INDEX ON products USING GIN (tags);          -- array of tags
CREATE INDEX ON users USING GIN (data);             -- JSONB document
CREATE INDEX ON articles USING GIN (to_tsvector('english', body));  -- full-text
```

**GiST (Generalized Search Tree):** A framework for custom index structures supporting arbitrary operators. Used for types that have no natural total sort order.
- Use for: geometric types and PostGIS (`&&`, `<->`, `@>`), range types (`&&`, `@>`), nearest-neighbor searches.
- Also used internally by full-text search when combined with `pg_trgm`.
```sql
CREATE INDEX ON locations USING GIST (geom);        -- PostGIS geometry
CREATE INDEX ON events USING GIST (during);         -- tsrange / daterange
```

**BRIN (Block Range INdex):** Stores min/max values per block range rather than per row. Tiny size (tens of kilobytes for a billion rows). Effective only when column values are correlated with physical storage order.
- Use for: append-only time-series tables where `created_at` increases monotonically, large IoT/log tables.
- NOT useful for randomly inserted data — the min/max per block ranges overlap everywhere.
```sql
CREATE INDEX ON sensor_readings USING BRIN (recorded_at);
```

**Interview trap:** GIN indexes are expensive to update. For very write-heavy JSONB columns, consider whether a dedicated column + B-tree is better than GIN on the whole document. Also: BRIN has very low precision — it can return many false positives (rows that the block range overlaps but do not actually match), requiring a recheck on the heap. The planner knows this and accounts for it.

**Tags:** indexes, gin, gist, brin, jsonb, full-text, postgres

---

## Q-SQL-016 [bloom: understand] [level: regular]
**Question:** Explain optimistic vs pessimistic locking. When would you choose each in a backend service?

**Model answer:** **Pessimistic locking** — explicitly lock the row when you read it, preventing other transactions from modifying it until you release the lock.

```sql
BEGIN;
SELECT * FROM inventory WHERE product_id = 42 FOR UPDATE;
-- other transactions trying to lock or update this row will block here
UPDATE inventory SET stock = stock - 1 WHERE product_id = 42;
COMMIT;
```

Guarantees no concurrent modification. Downside: locks are held for the duration of the transaction; under contention this serializes access and risks deadlocks.

**Optimistic locking** — no database lock. Read the row along with a `version` field. On update, include `WHERE version = :known_version` in the condition. If another transaction committed a change first, the update affects 0 rows — application detects this and retries or rejects.

```sql
-- Read
SELECT id, stock, version FROM inventory WHERE product_id = 42;
-- Update attempt
UPDATE inventory SET stock = stock - 1, version = version + 1
WHERE product_id = 42 AND version = :known_version;
-- rows_affected == 0 → conflict detected → retry
```

JPA `@Version` implements this automatically.

**When to choose each:**
- **Pessimistic:** high contention on the same row (stock decrement for a flash sale), short critical section, conflict = unacceptable (financial transfer).
- **Optimistic:** low contention (different users editing different records), long user-think-time sessions (web form, edit workflows), high throughput read-dominant systems.

**Interview trap:** Optimistic locking under high contention causes a retry storm — many transactions see version conflict and retry simultaneously, creating CPU and I/O bursts. If contention is genuinely high, pessimistic locking (or queue-based serialization) may be more efficient. "Optimistic is always better for throughput" is a myth when conflicts are frequent.

**Tags:** locking, optimistic, pessimistic, concurrency, for-update, transactions

---

## Q-SQL-017 [bloom: understand] [level: regular]
**Question:** What is keyset pagination and why is it better than OFFSET/LIMIT for large tables?

**Model answer:** **OFFSET/LIMIT** pagination scans and discards the first N rows on every page request. At page 1000 with limit 20, PostgreSQL must scan 20,000 rows, discard 19,980, and return 20. Performance degrades linearly with page depth. Even with an index, the index must be traversed for all N+limit rows.

```sql
-- OFFSET/LIMIT — O(offset) cost; slow at deep pages
SELECT id, name, created_at FROM orders
ORDER BY created_at DESC
LIMIT 20 OFFSET 10000;
```

**Keyset pagination (cursor-based)** uses the last-seen value as a bookmark instead of a count:

```sql
-- First page
SELECT id, name, created_at FROM orders
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Next page — pass last seen (created_at, id) from previous page
SELECT id, name, created_at FROM orders
WHERE (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

With an index on `(created_at DESC, id DESC)`, this executes in O(1) regardless of which page you are on — the engine seeks to the bookmark position and reads the next 20 rows.

**Limitations of keyset pagination:**
- Cannot jump to an arbitrary page number ("go to page 500").
- Sort order must be stable and based on indexed columns.
- Requires the sort key to be unique or augmented with a tie-breaker (hence `id` as second column).

**Interview trap:** Spring Data's `Pageable` uses OFFSET pagination under the hood. For large datasets (>100k rows) with deep pagination needs, OFFSET is a performance bomb and Slice/keyset is the answer. Also: OFFSET pagination can show duplicate or missing rows if rows are inserted/deleted between pages — keyset pagination is immune to this because the bookmark is row-content-based.

**Tags:** pagination, keyset, offset, performance, spring-data

---

## Q-SQL-018 [bloom: apply] [level: regular]
**Question:** Tables: `employee (id, dept_id, name, salary)`, `department (id, name)`. Write a query returning each department's name, the top earner's name, and their salary. Use window functions, not a correlated subquery.

**Model answer:**
```sql
WITH ranked_employees AS (
  SELECT
    e.id,
    e.name        AS emp_name,
    e.salary,
    d.name        AS dept_name,
    DENSE_RANK() OVER (PARTITION BY e.dept_id ORDER BY e.salary DESC) AS rnk
  FROM employee e
  JOIN department d ON d.id = e.dept_id
)
SELECT dept_name, emp_name, salary
FROM ranked_employees
WHERE rnk = 1
ORDER BY dept_name;
```

Why `DENSE_RANK` instead of `ROW_NUMBER`: if two employees share the top salary in a department, `ROW_NUMBER` would arbitrarily pick one. `DENSE_RANK = 1` returns all tied top earners. Choose based on the requirement — if the business wants exactly one winner, `ROW_NUMBER` with a deterministic tie-breaker (`ORDER BY salary DESC, id`) is appropriate.

The CTE separates the ranking computation from the filtering, making the query readable and easy to test in isolation.

**Interview trap:** "Can you filter with HAVING instead of a subquery/CTE?" — no. Window functions are computed after HAVING, so you cannot use them in HAVING. You must wrap in a subquery or CTE to filter on a window function result. Also: the planner will typically execute only one pass over the data despite the CTE — verify with EXPLAIN.

**Tags:** window-functions, dense-rank, cte, join, apply

---

## Q-SQL-019 [bloom: apply] [level: regular]
**Question:** Write a recursive CTE to traverse a category hierarchy. Table: `category (id, parent_id, name)`. Return each category with its full path (e.g., "Electronics > Phones > Smartphones") and depth level.

**Model answer:**
```sql
WITH RECURSIVE category_tree AS (
  -- Anchor: root categories (no parent)
  SELECT
    id,
    parent_id,
    name,
    name::text        AS path,
    1                 AS depth,
    ARRAY[id]         AS visited   -- for cycle detection
  FROM category
  WHERE parent_id IS NULL

  UNION ALL

  -- Recursive: attach children
  SELECT
    c.id,
    c.parent_id,
    c.name,
    ct.path || ' > ' || c.name,
    ct.depth + 1,
    ct.visited || c.id
  FROM category c
  JOIN category_tree ct ON c.parent_id = ct.id
  WHERE NOT c.id = ANY(ct.visited)   -- guard against cycles
)
SELECT id, name, path, depth
FROM category_tree
ORDER BY path;
```

PostgreSQL 14+ offers a native `CYCLE` clause:
```sql
WITH RECURSIVE category_tree (...) AS (
  ...
) CYCLE id SET is_cycle USING cycle_path
SELECT * FROM category_tree WHERE NOT is_cycle;
```

Key points:
- `UNION ALL` not `UNION` — deduplication is unnecessary (each node is visited once in a tree) and expensive.
- The anchor selects root nodes (`parent_id IS NULL`). Adjust the base condition for DAGs or subhierarchies.
- Without cycle protection, a cycle in the data causes an infinite loop until `max_recursion` is hit.

**Interview trap:** "What happens with a DAG (node with two parents)?" — a node can appear multiple times, once via each parent path. The visited array prevents infinite loops but does not deduplicate paths. For DAGs, add a `WHERE depth < :max` safety limit or use cycle detection more carefully.

**Tags:** recursive-cte, hierarchy, tree, cycle-detection, postgres

---

## Q-SQL-020 [bloom: apply] [level: regular]
**Question:** Table `price_change (product_id, valid_from TIMESTAMP, price NUMERIC)`. For each product, compute the price delta and percentage change compared to the previous price. Use a window function.

**Model answer:**
```sql
SELECT
  product_id,
  valid_from,
  price,
  LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from) AS prev_price,
  price - LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from)  AS delta,
  ROUND(
    (price - LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from))
    / NULLIF(LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from), 0)
    * 100,
  2)  AS pct_change
FROM price_change
ORDER BY product_id, valid_from;
```

To avoid repeating the LAG expression three times, use a CTE:
```sql
WITH changes AS (
  SELECT
    product_id,
    valid_from,
    price,
    LAG(price) OVER (PARTITION BY product_id ORDER BY valid_from) AS prev_price
  FROM price_change
)
SELECT
  product_id,
  valid_from,
  price,
  prev_price,
  price - prev_price                                  AS delta,
  ROUND((price - prev_price) / NULLIF(prev_price, 0) * 100, 2)  AS pct_change
FROM changes
ORDER BY product_id, valid_from;
```

`NULLIF(prev_price, 0)` prevents division-by-zero. The first row per product has `prev_price = NULL`, so delta and pct_change are also NULL — correct behavior.

**Interview trap:** `LAG(price, 1, 0)` provides a default of 0 when there is no previous row. That may seem convenient but produces a misleading 100% change for the first data point. Leave it NULL to signal "no prior data." Also: `LAG(price, 2)` returns two rows back, not the second-most-recent.

**Tags:** window-functions, lag, analytics, pricing

---

## Q-SQL-021 [bloom: apply] [level: senior]
**Question:** A table `orders (id BIGINT, customer_id INT, created_at TIMESTAMP, total NUMERIC)` has 100 million rows. The query `SELECT * FROM orders WHERE customer_id = ? ORDER BY created_at DESC LIMIT 10` is slow. Walk through your diagnosis and fix.

**Model answer:** Start with `EXPLAIN (ANALYZE, BUFFERS)` — never optimize blind.

**Likely findings and actions:**

1. **No index on `customer_id`** → Seq Scan on 100M rows. Fix: `CREATE INDEX ON orders (customer_id, created_at DESC)`. Composite index means: B-tree traversal to the first matching `customer_id`, then rows within that partition are already in `created_at DESC` order — no sort step needed. PostgreSQL can return the first 10 rows immediately.

2. **Index on `customer_id` only, no `created_at`** → fast lookup but sort of all customer's orders. Fix: same composite index as above.

3. **Composite index exists but planner ignores it** → stale statistics. Run `ANALYZE orders`. Also check `pg_stat_user_indexes` for `idx_scan = 0` (index never used).

4. **Customer has 500k orders** → even with the composite index, fetching and discarding 499,990 rows costs heap I/O. Fix: covering index `(customer_id, created_at DESC) INCLUDE (id, total)` enables Index Only Scan.

5. **Table has severe bloat** (many dead tuples) → VACUUM or `pg_repack` (online repack without table lock).

6. **Partitioning** — at 100M rows consider range partitioning by `created_at` (yearly or monthly). Queries with a date range filter get partition pruning. Query by `customer_id` alone does not benefit from range partitioning; hash partitioning on `customer_id` would help that case.

7. **Application cache** — top-10 recent orders per customer changes infrequently; cache in Redis with a short TTL (30s–5m).

**Interview trap:** Adding a separate index on each column individually (`idx_customer_id` + `idx_created_at`) is worse than the composite — the planner may use a Bitmap Index Scan combining both, but it is slower than a single composite index that covers both columns in the right order. Column order in the composite index matters.

**Tags:** indexes, composite-index, performance, optimization, postgres, explain

---

## Q-SQL-022 [bloom: apply] [level: senior]
**Question:** You need to run EXPLAIN ANALYZE on a slow UPDATE in production. What precautions do you take and what do you look for in the output?

**Model answer:** `EXPLAIN ANALYZE` on a write statement actually executes it — data changes are real. Standard precaution:

```sql
BEGIN;
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
  UPDATE orders SET status = 'archived'
  WHERE created_at < NOW() - INTERVAL '2 years';
ROLLBACK;  -- never commit
```

The ROLLBACK undoes the mutation. The plan output is still valid — the optimizer chose it, and actual timing/rows are correct.

**What to look for in the output:**

- **Seq Scan vs Index Scan** on the WHERE predicate. If Seq Scan on 100M rows with a date filter — the planner may think too many rows match (check estimate vs actual). Add an index if selectivity is sufficient.
- **Rows estimated vs actual** — if 10k estimated but 5M actual, statistics are very stale. Run ANALYZE.
- **Shared Buffers hit vs read** (BUFFERS option) — `Buffers: shared hit=X read=Y`. High `read` relative to `hit` means data is not cached; query is I/O bound.
- **Lock wait** — if the update blocks waiting for locks, the ANALYZE plan does not show it, but `pg_locks` / `pg_stat_activity` will.
- **Sort spills** — `Sort Method: external merge Disk: Xkb` — increase `work_mem` for the session.
- **Filter** rows vs **rows removed by filter** — a large "rows removed by filter" number after a scan indicates the filter is applied too late (no index, predicate not sargable).

**Interview trap:** The BUFFERS option only shows buffer hits for the current session; it does not reveal whether other sessions are competing for the same pages. Also: `EXPLAIN ANALYZE` timing includes lock acquisition time for the row locks the UPDATE must take — this can inflate actual time vs estimated cost significantly on a contentious table.

**Tags:** explain, analyze, update, performance, buffers, production

---

## Q-SQL-023 [bloom: apply] [level: senior]
**Question:** You have a `payments (id, customer_id, amount, status)` table where `status` ∈ {'pending', 'processing', 'completed', 'failed'}. 99% of rows are 'completed'. The query `WHERE status = 'pending' AND customer_id = ?` is slow. Design the optimal index strategy.

**Model answer:** First: understand the data distribution. If 'completed' = 99%, 'pending' = ~0.5%, then an index covering 'pending' rows covers only 0.5% of the table — a very small, highly selective index.

**Option 1 — Partial index (best for this exact query pattern):**
```sql
CREATE INDEX idx_pending_payments_by_customer
ON payments (customer_id)
WHERE status = 'pending';
```
Only ~0.5% of rows are indexed. Fast updates (most inserts/updates on completed rows do not touch this index), tiny in size, perfect for `WHERE status = 'pending' AND customer_id = ?`.

**Option 2 — Composite index (if queries on multiple status values are needed):**
```sql
CREATE INDEX ON payments (customer_id, status);
```
`customer_id` as the leading column (high cardinality) makes this effective. Can serve `WHERE customer_id = ? AND status IN (...)`. Larger than the partial index.

**Option 3 — Separate pending queue table (if 'pending' is a processing queue):**
Move pending rows to a `pending_payments` table; delete on completion. Main table query performance is unaffected. Downside: schema complexity, migration needed.

**Recommendation for this case:** Option 1 (partial index) — it is small, fast, and precisely covers the stated query. Add Option 2 if you also query by customer + completed for reporting.

**Interview trap:** Adding an index on `status` alone — with 99% 'completed', the planner will ignore this index for any query touching 'completed' rows (selectivity too low). For the rare-value query on 'pending' it might help, but a partial index is better. Common mistake: applying the same indexing strategy regardless of data distribution.

**Tags:** indexes, partial-index, low-cardinality, performance, optimization

---

## Q-SQL-024 [bloom: apply] [level: senior]
**Question:** What are PostgreSQL advisory locks? Give a use case where they are preferable to row-level locks.

**Model answer:** Advisory locks are application-level cooperative locks that PostgreSQL stores in memory (not tied to any row or table). The application decides when to acquire and release them. They do not block DDL or DML — only other sessions that also explicitly request the same lock key.

```sql
-- Session-level advisory lock (held until session ends or explicit unlock)
SELECT pg_advisory_lock(42);           -- exclusive; blocks if another session holds it
SELECT pg_advisory_unlock(42);

-- Transaction-level advisory lock (released at COMMIT/ROLLBACK)
SELECT pg_advisory_xact_lock(hashtext('process_daily_report'));

-- Try-lock variant (non-blocking)
SELECT pg_try_advisory_lock(42);       -- returns true if acquired, false if not
```

**Use cases where advisory locks shine:**

1. **Preventing duplicate cron job execution** — multiple app instances may try to run the same scheduled job simultaneously. A `pg_try_advisory_lock(job_id)` at job start ensures only one instance runs; others skip immediately.

2. **Logical resource locking across microservices sharing a DB** — if two processes must not run concurrently on the same "entity" (e.g., billing cycle for customer 123) but the entity is not a single row to lock with FOR UPDATE, an advisory lock on the customer ID provides safe coordination.

3. **Serializing a sequence of operations** — e.g., a complex multi-step process that must run serially per tenant without holding a long transaction open (which would block VACUUM).

**Interview trap:** Advisory locks are not cleaned up automatically on crash if they are session-level (transaction-level ones are cleaned on ROLLBACK/COMMIT). In connection-pooled environments (PgBouncer transaction mode), session-level advisory locks are dangerous — the session is reused by different application threads, so a lock acquired by one thread may be held when another thread gets the same connection. Use transaction-level advisory locks with PgBouncer.

**Tags:** locking, advisory-locks, concurrency, pgbouncer, postgres

---

## Q-SQL-025 [bloom: analyze] [level: senior]
**Question:** Explain PostgreSQL's MVCC mechanism. How does it allow readers to not block writers? What are the operational consequences (dead tuples, bloat, VACUUM)?

**Model answer:** PostgreSQL implements **MVCC (Multi-Version Concurrency Control)** by never overwriting rows in place. Every row (called a tuple) has two system columns:
- **`xmin`** — transaction ID that created (inserted) this tuple version.
- **`xmax`** — transaction ID that deleted (or superseded) this tuple version (0 if still live).

When a transaction updates a row, PostgreSQL writes a new tuple with the new values (xmin = current txid, xmax = 0) and marks the old tuple as dead by setting its xmax. The old version is not physically removed immediately.

**How snapshot isolation works:** at the start of a transaction (or each statement in READ COMMITTED), PostgreSQL takes a snapshot: a record of which transaction IDs are active. When reading a row, the engine checks: is xmin committed and visible? Is xmax either 0 (not deleted) or not yet committed? This visibility check is done entirely in-memory with no locking — readers never block writers and writers never block readers.

**Operational consequences:**

1. **Dead tuples** — old tuple versions accumulate. An `UPDATE` on 1M rows leaves 1M dead tuples. Dead tuples consume disk space and slow seq scans (more pages to read).

2. **Table bloat** — the physical table file grows as dead tuples accumulate and free space is not returned to the OS.

3. **VACUUM** — marks dead tuples as free space, reusable for future inserts. Does NOT shrink the file size. Non-blocking (shares pages with concurrent queries).

4. **VACUUM FULL** — rewrites the entire table to a new file, compacting it. Frees disk space but takes an exclusive lock on the table — avoid in production during business hours. Use `pg_repack` for online compaction.

5. **autovacuum** — background daemon that triggers VACUUM automatically. Default `autovacuum_vacuum_scale_factor = 0.2` means VACUUM triggers when 20% of the table has dead tuples. For large tables (10M+ rows), lower this to 0.01–0.02: `ALTER TABLE big_table SET (autovacuum_vacuum_scale_factor = 0.01)`.

6. **Long-running transactions** — a transaction holds a snapshot. VACUUM cannot reclaim dead tuples visible to that snapshot. A single forgotten open transaction (e.g., idle connection with `idle in transaction`) can cause unbounded bloat. Monitor `pg_stat_activity` for long transactions.

7. **Transaction ID wraparound** — PostgreSQL uses 32-bit transaction IDs. After ~2 billion transactions, IDs wrap around and older tuples become invisible. The autovacuum performs "anti-wraparound vacuum" before the limit. If it falls behind, PostgreSQL will go into read-only safety mode. Monitor `pg_stat_user_tables.n_dead_tup` and `age(datfrozenxid)` in `pg_database`.

**Interview trap:** "VACUUM reclaims disk space." — regular VACUUM does NOT shrink the file; it only marks space as reusable within the file. Only VACUUM FULL / pg_repack return pages to the OS. This surprises engineers who expect disk usage to drop after a VACUUM run.

**Tags:** mvcc, vacuum, bloat, dead-tuples, xmin, xmax, autovacuum, postgres, txid-wraparound

---

## Q-SQL-026 [bloom: analyze] [level: senior]
**Question:** A senior engineer says "we should always use SERIALIZABLE isolation to avoid concurrency bugs." Evaluate this claim. What does PostgreSQL's SSI actually do and when is it appropriate?

**Model answer:** The claim is an oversimplification. SERIALIZABLE eliminates all anomalies (dirty reads, non-repeatable reads, phantom reads, write skew) but comes with a real cost.

**What PostgreSQL SSI does:** SSI (Serializable Snapshot Isolation) does not use traditional lock-based serializability. Instead it tracks read/write dependencies between concurrent transactions. When it detects a cycle in the dependency graph that would make the serial-equivalent ordering impossible, it aborts one of the transactions with a serialization error (`ERROR: could not serialize access due to concurrent update`).

This means the application must be prepared to **retry on serialization failure** — a constraint that complicates code and can create retry storms under high contention.

**Cost of SERIALIZABLE in PostgreSQL:**
- More memory used to track predicate locks and dependency graph.
- Higher abort rate under contention — wasted work on retried transactions.
- Reduced throughput on write-heavy workloads.

**When SERIALIZABLE is appropriate:**
- Financial transfers where write skew is a genuine risk (e.g., checking aggregate balance across accounts in two concurrent transactions).
- Operations that read and conditionally write based on the read (check-then-act patterns).
- Compliance requirements demanding strict isolation.

**When READ COMMITTED is sufficient (most OLTP):**
- Simple CRUD operations where each statement is atomic enough.
- The application uses optimistic locking (`@Version`) to detect concurrent modification.
- The domain logic tolerates the anomalies possible at READ COMMITTED.

**REPEATABLE READ** is a sweet spot for many cases: eliminates non-repeatable reads and phantom reads (in PG), does not abort transactions due to serialization conflicts, lower overhead than SERIALIZABLE.

**Interview trap:** "REPEATABLE READ prevents phantom reads in standard SQL but not in PostgreSQL." Actually the opposite — PostgreSQL's REPEATABLE READ uses snapshot isolation that blocks phantom reads. The SQL standard says REPEATABLE READ allows phantoms, but PG's implementation is stronger than required. This is a known PostgreSQL deviation from the standard — know it.

**Tags:** transactions, isolation, serializable, ssi, repeatable-read, postgres, concurrency

---

## Q-SQL-027 [bloom: analyze] [level: senior]
**Question:** Your backend uses PgBouncer in transaction mode in front of PostgreSQL. A developer wants to use `SET search_path = my_schema` to scope queries. What breaks and why?

**Model answer:** In PgBouncer transaction mode, each transaction may be served by a different server-side PostgreSQL connection. Session-level state — `SET` variables, prepared statements, `SET LOCAL` outside a transaction, temporary tables, advisory locks (session-level) — is NOT preserved between transactions.

`SET search_path = my_schema` is a session-level setting. After the transaction commits, PgBouncer returns the server connection to the pool. The next transaction from this or another client gets that connection with `my_schema` still set as `search_path` — it bleeds to unrelated sessions. Or conversely, a transaction that expected `my_schema` to be set gets a clean connection where it is not.

**What breaks:**
- `SET search_path` applied in one transaction leaks to subsequent, unrelated transactions on the same server connection (security issue in multi-tenant systems).
- Session-level prepared statements (`PREPARE foo AS ...`) are bound to the server connection, not the PgBouncer client connection. They are invisible to other clients but hold resources.
- `pg_advisory_lock` (session-level) held past transaction end will be released when the connection returns to pool — or worse, may be transferred to another client.
- Temporary tables are connection-scoped — a temp table created in transaction 1 may vanish or conflict in transaction 2 on a different server connection.

**Fixes:**
- Use `SET LOCAL search_path = my_schema` inside an explicit transaction — `SET LOCAL` reverts at transaction end, so it does not leak.
- Set `search_path` at the application user level in PostgreSQL (`ALTER ROLE myapp SET search_path = my_schema`).
- Use fully qualified table names (`my_schema.my_table`).
- For prepared statements: use protocol-level prepared statements only if they are managed per client connection, or avoid them with PgBouncer transaction mode.

**Interview trap:** PgBouncer session mode preserves session state (equivalent to a dedicated connection per client) but sacrifices multiplexing efficiency — 100 app connections = 100 server connections. Transaction mode achieves N:M multiplexing but breaks session state. Statement mode is even more aggressive but breaks multi-statement transactions entirely. Know which mode you are running and its constraints.

**Tags:** pgbouncer, connection-pooling, session-state, postgres, transaction-mode

---

## Q-SQL-028 [bloom: analyze] [level: senior]
**Question:** Design the index strategy for a multi-tenant SaaS application. Table: `events (id BIGSERIAL, tenant_id INT, user_id INT, event_type VARCHAR, payload JSONB, created_at TIMESTAMP)`. Expected queries: (A) list events for a user within a tenant, time-descending; (B) search events by event_type within a tenant; (C) filter events with a specific payload field value; (D) aggregate event counts per type per tenant for dashboards.

**Model answer:** Design each index for the most selective leading column first, considering query patterns:

**Query A — user events, time-descending (hot path):**
```sql
CREATE INDEX idx_events_tenant_user_time
ON events (tenant_id, user_id, created_at DESC);
-- Supports: WHERE tenant_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT n
-- Enables keyset pagination: AND (created_at, id) < (:last_ts, :last_id)
```

**Query B — event_type within tenant:**
```sql
CREATE INDEX idx_events_tenant_type_time
ON events (tenant_id, event_type, created_at DESC);
-- Supports: WHERE tenant_id = ? AND event_type = ? ORDER BY created_at DESC
```

**Query C — JSONB payload filter:**
```sql
-- Option 1: GIN index on entire payload (flexible, larger)
CREATE INDEX idx_events_payload ON events USING GIN (payload);
-- Supports: WHERE payload @> '{"status": "error"}'

-- Option 2: expression index on a specific field (smaller, faster for known fields)
CREATE INDEX idx_events_payload_status ON events ((payload->>'status'));
-- Supports: WHERE payload->>'status' = 'error'
```

**Query D — aggregation dashboard:**
Avoid running aggregation queries on the live table at scale. Options:
- **Materialized view** refreshed on schedule: `SELECT tenant_id, event_type, date_trunc('day', created_at), COUNT(*) GROUP BY ...`
- **Incremental summary table** updated via trigger or background job.
- **BRIN index** on `created_at` for partition-style range scans on append-only data.

**Partitioning consideration:** at billions of rows, range-partition by `created_at` (monthly). Add tenant_id filter to all queries for partition pruning benefit. Local indexes per partition replace global indexes.

**Interview trap:** GIN on the full JSONB payload is expensive to update (every row modification updates every key in the payload). For write-heavy tables with large JSONB, prefer expression indexes on specific fields that are actually queried. Also: too many indexes on a write-heavy events table will bottleneck ingest throughput — profile and keep only indexes that are actually used (check `pg_stat_user_indexes.idx_scan`).

**Tags:** indexes, gin, jsonb, composite-index, partitioning, multi-tenant, design

---

## Q-SQL-029 [bloom: analyze] [level: master]
**Question:** Explain PostgreSQL's transaction ID wraparound problem. How does it happen, what are the consequences, and how do you monitor and prevent it?

**Model answer:** PostgreSQL uses 32-bit transaction IDs (txids). With ~4 billion possible values, IDs wrap around to 0 after ~2 billion transactions from any given point. PostgreSQL uses modular arithmetic: transaction IDs in the "past" (visible) are those within 2^31 steps behind the current txid; those "in the future" (invisible) are the other half.

**The problem:** if a tuple's `xmin` falls more than 2 billion txids behind the current txid, PostgreSQL considers it "in the future" — the tuple becomes invisible. Entire tables can appear empty. This is catastrophic data loss.

**Freeze mechanism:** VACUUM ages out old xmin values by replacing them with the special `FrozenTransactionId` (2), which is always considered visible. A tuple with `xmin = FrozenTransactionId` never ages out. This process is called "freezing."

`autovacuum_freeze_max_age` (default 200M transactions) — autovacuum is forced to freeze when a table's oldest unfrozen xmin is more than this many transactions behind current. PostgreSQL will also force an anti-wraparound vacuum at 2 billion transactions behind `datfrozenxid`, the oldest non-frozen txid in the database.

**Monitoring:**
```sql
-- Age of oldest non-frozen transaction ID in each database
SELECT datname, age(datfrozenxid) AS txid_age
FROM pg_database
ORDER BY txid_age DESC;
-- Alert if age > 1.5 billion

-- Per-table
SELECT schemaname, relname, age(relfrozenxid) AS txid_age
FROM pg_stat_user_tables
ORDER BY txid_age DESC
LIMIT 20;
```

**Prevention:**
- Ensure autovacuum is not disabled and is keeping up (check `pg_stat_user_tables.last_autovacuum`).
- Avoid long-running transactions — they prevent VACUUM from freezing tuples. Long idle-in-transaction connections are the #1 cause of wraparound risk.
- Tune `autovacuum_vacuum_cost_delay` and `autovacuum_freeze_max_age` for high-transaction systems.
- On systems with hundreds of millions of transactions per day, monitor `age(datfrozenxid)` and alert at 1.5B.

**Consequences of missing the deadline:** PostgreSQL will refuse to accept new transactions and enter a "shutdown" safety mode with the message: "database is not accepting commands to avoid wraparound data loss." Recovery requires starting in single-user mode and running VACUUM FREEZE manually.

**Interview trap:** "Just disable autovacuum and run manual VACUUM on a schedule" — manual vacuum must be run frequently enough, and if it falls behind (due to lock contention, I/O pressure, or large tables), wraparound risk grows silently. autovacuum is designed to handle this; tune it rather than disable it.

**Tags:** mvcc, txid-wraparound, vacuum, autovacuum, postgres, master, production

---

## Q-SQL-030 [bloom: analyze] [level: master]
**Question:** A developer proposes replacing a PostgreSQL-backed system with a NoSQL database "because PostgreSQL can't scale." Walk through how you would first exhaust PostgreSQL's scaling options before migrating, and explain where the boundary is where NoSQL genuinely wins.

**Model answer:** "PostgreSQL can't scale" is almost always a failure of the architecture around PostgreSQL, not PostgreSQL itself. Before any migration, identify what is actually slow and why.

**PostgreSQL scaling toolkit (in order of complexity):**

1. **Query optimization** — EXPLAIN ANALYZE, missing indexes, sargability issues. A single bad query on 10M rows can look like "scaling problem" but is really a missing index. Fix this first.

2. **Connection pooling (PgBouncer)** — PostgreSQL spawns an OS process per connection. 200 app threads × 5 service instances = 1000 connections. PostgreSQL is degraded. PgBouncer in transaction mode allows 1000 app connections through 20-50 server connections.

3. **Read replicas** — streaming replication is built in. Route read-heavy workloads to replicas. A primary + 3 replicas handles 4x the read throughput. Caveat: replication lag (typically < 1s on LAN); application must tolerate stale reads for non-critical paths.

4. **Caching layer** — Redis for hot read paths (current prices, session data, computed aggregates). Cache-aside pattern. Cache hit rate > 95% on a read-heavy pricing system eliminates most read load from the database.

5. **Partitioning** — range, hash, or list partitioning on the primary access dimension. Partition pruning makes queries touch only relevant partitions. Allows archiving old partitions cheaply.

6. **Materialized views** — pre-compute expensive aggregations. Refresh on schedule (CONCURRENTLY to avoid blocking reads).

7. **Vertical scaling** — larger instances with more RAM (more of the working set fits in `shared_buffers` and OS cache) and faster NVMe storage. Often cheaper than a migration.

8. **Citus / Distributed Postgres** — horizontal sharding via the Citus extension. PostgreSQL stays the query interface; data is distributed across shards.

**Where NoSQL genuinely wins over PostgreSQL:**

- **Key-value at extreme write throughput** — Redis, DynamoDB at millions of writes/second with sub-millisecond latency. PostgreSQL with WAL cannot match this for pure KV workloads.
- **Truly schemaless, highly variable document structure** — MongoDB when documents genuinely vary in shape and SQL joins add no value.
- **Time-series at IoT scale** — InfluxDB, TimescaleDB (PostgreSQL extension) when the write rate and compression requirements exceed what vanilla PostgreSQL partitioning can handle.
- **Graph traversal at depth** — Neo4j when the primary query pattern is multi-hop graph traversal with variable depth (recursive CTEs work but are slower than a dedicated graph engine at scale).
- **Eventual consistency / high availability over strong consistency** — Cassandra when partition tolerance and write availability matter more than ACID guarantees.

**Interview trap:** "We need NoSQL for eventual consistency" — if the business logic requires ACID (financial transactions, inventory management, pricing), eventual consistency is a correctness risk, not a feature. Migrating to NoSQL for scalability and then implementing manual compensating transactions to restore correctness is often harder than scaling PostgreSQL. Do not trade a performance problem for a correctness problem.

**Tags:** nosql, scaling, architecture, postgres, read-replicas, caching, partitioning

---

## Q-SQL-031 [bloom: analyze] [level: master]
**Question:** Describe a deadlock scenario in PostgreSQL, how the engine resolves it, and what application-level patterns cause deadlocks in practice. How do you diagnose and prevent them?

**Model answer:** A deadlock occurs when transaction A holds a lock that transaction B wants, and transaction B holds a lock that transaction A wants — a circular wait from which neither can proceed.

**Concrete example:**
```
T1: UPDATE accounts SET balance = balance - 100 WHERE id = 1;  -- acquires lock on row 1
T2: UPDATE accounts SET balance = balance - 50  WHERE id = 2;  -- acquires lock on row 2
T1: UPDATE accounts SET balance = balance + 50  WHERE id = 2;  -- waits for T2
T2: UPDATE accounts SET balance = balance + 100 WHERE id = 1;  -- waits for T1 → DEADLOCK
```

**PostgreSQL resolution:** PostgreSQL detects deadlocks by running a deadlock detection cycle periodically (tuned by `deadlock_timeout`, default 1 second). When a cycle is detected, PostgreSQL picks one of the transactions as the victim (typically the one that has done less work), aborts it with `ERROR: deadlock detected`, and the other transaction proceeds. The application must catch this error and retry the transaction.

**Common application-level causes:**

1. **Inconsistent lock ordering** — two code paths update the same set of rows but in different orders (row 1 then 2 vs row 2 then 1). Fix: always acquire locks in a canonical order (e.g., ascending by ID).

2. **Implicit locks from cascading updates/deletes** — `ON DELETE CASCADE` on an FK may lock child rows that another transaction is also modifying.

3. **Batch operations without consistent ordering** — a batch job locks rows in arbitrary order as it iterates a result set. Use `ORDER BY id` to ensure consistent ordering.

4. **Lock escalation** — application holds a row lock, then tries to acquire a table-level lock elsewhere. Minimize lock scope and duration.

**Diagnosis:**
```sql
-- Current blocking queries
SELECT pid, query, wait_event_type, wait_event, pg_blocking_pids(pid)
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';

-- PostgreSQL logs deadlocks at log_min_messages = LOG
-- log_lock_waits = on logs lock waits > deadlock_timeout
```

**Prevention checklist:**
- Lock rows in a consistent order across all code paths.
- Keep transactions short — acquire locks as late as possible, release as early as possible.
- Use `SELECT ... FOR UPDATE SKIP LOCKED` for queue-style processing (each worker claims a batch of rows atomically, no contention with other workers).
- Avoid user input (think time) inside a transaction.

**Interview trap:** "Set `deadlock_timeout` to a very small value to detect deadlocks faster" — this increases CPU overhead (more frequent deadlock detection cycles) and causes legitimate lock waits to be prematurely aborted. 1 second is a reasonable default. Tune `lock_timeout` (abort if waiting for a lock more than N ms) instead, which gives application-level control without the detection overhead.

**Tags:** deadlock, locking, transactions, for-update, skip-locked, postgres, master

---

## Q-SQL-032 [bloom: analyze] [level: master]
**Question:** Compare OFFSET/LIMIT pagination, keyset pagination, and cursor-based pagination. How do you implement stable, efficient pagination over a result set that is being concurrently modified?

**Model answer:** **OFFSET/LIMIT problems at scale:**
- `OFFSET N LIMIT 20` requires the engine to scan and discard N rows every time. At offset 100,000, you scan 100,020 rows and return 20. With an index the scan is fast per row, but the cumulative cost is O(offset).
- **Instability under concurrent modification** — if a row is inserted before the current page boundary between page 1 and page 2 requests, page 2 will skip a row (or duplicate one). The result set shifts under the cursor.
- Fine for small datasets (< 10k rows) or when pages are rarely deep-linked.

**Keyset pagination:**
```sql
-- Page 1
SELECT id, name, created_at FROM orders
WHERE tenant_id = ?
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Page 2 — use last row's (created_at, id) from page 1
SELECT id, name, created_at FROM orders
WHERE tenant_id = ?
  AND (created_at, id) < (:prev_created_at, :prev_id)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

With a composite index on `(tenant_id, created_at DESC, id DESC)`, each page request is O(1) — seek to bookmark, read 20 rows, stop. Stable under concurrent inserts: new rows inserted before the bookmark do not affect pages already delivered; new rows after do appear on future pages.

**Limitations of keyset:**
- No random page access ("jump to page 500").
- Sort key must be indexed and tie-broken to be unique (hence `id` as tiebreaker).
- Requires the client to pass the opaque cursor (last-row values) on each request.

**For truly stable snapshots under concurrent modification:** wrap the pagination session in a transaction with REPEATABLE READ. This freezes the snapshot at the start of the transaction. All page fetches within the transaction see the same consistent view. Trade-off: long-running transaction holds a snapshot, preventing VACUUM from cleaning dead tuples.

```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
-- page 1
SELECT ...;
-- page 2
SELECT ...;
COMMIT;
```

This is practical for short-lived export jobs but not for interactive "infinite scroll" UIs where the session spans many minutes.

**Hybrid approach:** encode a snapshot timestamp or transaction ID as the cursor. Each page request uses `WHERE created_at <= :snapshot_time` to approximate a consistent view. Not perfectly stable (inserts after snapshot_time but with earlier created_at are excluded), but close enough for most UIs.

**Interview trap:** "Keyset pagination requires a unique sort column." Correct — without a tie-breaker, rows with the same sort value may be duplicated or skipped across pages. Always augment the sort key with `id` or another unique column as a tiebreaker. Also: Spring Data's `Slice` still uses OFFSET internally — you need a custom query or `QuerydslPredicateExecutor` with cursor support for true keyset pagination.

**Tags:** pagination, keyset, offset, cursor, concurrency, snapshot, performance, master
