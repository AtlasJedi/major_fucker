# Spring Data / JPA / Hibernate — question bank

> Spring Data JPA is the standard persistence stack for Java/Kotlin backend systems built on Spring Boot. Senior interviews probe it relentlessly because it is the #1 source of silent performance and correctness bugs in production: N+1 queries, LazyInitializationException, optimistic lock races, open-in-view connection leaks, and broken batch inserts hiding behind IDENTITY generation. You will be expected to explain not just what annotations do, but what SQL they generate, why they generate it, and under what conditions they silently do the wrong thing. This bank covers JPA (spec), Hibernate (provider), and Spring Data (repository layer) as an integrated system.

## Scope

- JPA vs Hibernate vs Spring Data: roles, layering, when to drop down
- EntityManager and persistence context: lifecycle, first-level cache, dirty checking, flush modes
- Entity states: transient, managed, detached, removed — transitions and pitfalls
- N+1 problem: cause, detection tools, fixes (JOIN FETCH, @EntityGraph, @BatchSize, DTO projection)
- LAZY vs EAGER defaults per association type; LazyInitializationException cause and fixes
- Association mapping: @OneToMany, @ManyToOne, @ManyToMany; owning side, mappedBy, bidirectional sync
- Cascade types and orphanRemoval: what each does, dangerous combinations
- Repository hierarchy: Repository, CrudRepository, PagingAndSortingRepository, JpaRepository
- Derived query methods: naming rules and generated JPQL
- @Query: JPQL vs native, @Modifying, clearAutomatically
- Page vs Slice: count-query cost, offset pagination degradation, keyset alternative
- Optimistic locking: @Version, OptimisticLockException, retry pattern
- Pessimistic locking: LockModeType, PESSIMISTIC_READ/WRITE, deadlock risk
- Identifier generation: IDENTITY vs SEQUENCE vs TABLE — batching implications
- Second-level cache: scope, providers, concurrency strategies, multi-node pitfall
- Open-session-in-view (OSIV) anti-pattern: what it does, why it hurts, how to disable
- Batch inserts: hibernate.jdbc.batch_size, flush/clear loop, IDENTITY-breaks-batching
- @Transactional readOnly: optimizations it enables, when to use it
- equals()/hashCode() on entities: why identity-based breaks in Hibernate proxies
- @Transactional propagation and self-invocation trap
- DTO projections: interface projection vs class (JPQL constructor), native query aliases

---

## Q-SPRD-001 [bloom: recall] [level: junior]
**Question:** What are JPA, Hibernate, and Spring Data JPA — and what is the relationship between them?

**Model answer:** They are three distinct layers that work together:

- **JPA (Jakarta Persistence API)** is a specification — a set of interfaces and annotations in `jakarta.persistence.*`. It defines how Java objects map to relational tables, how queries are written (JPQL), and what an EntityManager must do. JPA has no runtime code of its own.
- **Hibernate** is the most popular JPA implementation. It provides the actual runtime: SQL generation, connection management, caching, dirty checking. When your app runs, it is Hibernate doing the work.
- **Spring Data JPA** is an abstraction layer on top of JPA. It generates repository implementations at runtime (no boilerplate DAO code), provides derived query parsing, and integrates with Spring's transaction management. Spring Data JPA delegates to a JPA `EntityManager`, which is backed by Hibernate under the hood.

The chain: `Spring Data JPA → JPA EntityManager → Hibernate → JDBC → Database`.

You can drop below Spring Data any time: `entityManager.createQuery(...)` for custom JPQL, or `entityManager.unwrap(Session.class)` to access Hibernate-specific APIs like `StatelessSession` for bulk operations.

**Interview trap:** "Is Hibernate mandatory with Spring Data JPA?" No — EclipseLink is a valid JPA provider too. But in practice Hibernate is the default via `spring-boot-starter-data-jpa` and almost universal in the industry. Know that swapping providers is theoretically possible but behaviorally non-trivial (caching behavior, sequence allocation, SQL dialect differ).

**Tags:** jpa, hibernate, spring-data, architecture, basics

---

## Q-SPRD-002 [bloom: recall] [level: junior]
**Question:** What are the four lifecycle states of a JPA entity, and what triggers transitions between them?

**Model answer:** JPA defines four entity states relative to the persistence context:

| State | Description | How to get there |
|-------|-------------|-----------------|
| **Transient** | Object exists in JVM heap but Hibernate knows nothing about it; no DB row, no tracking | `new Order()` — just constructed |
| **Managed** | Tracked by the active persistence context; every field change will be flushed to DB at flush time | `em.persist(entity)`, `em.find(...)`, `em.merge(detached)`, query result inside a tx |
| **Detached** | Was managed, but persistence context closed (transaction ended) or `em.detach(entity)` called; changes NOT tracked | Transaction commit, `em.close()`, `em.detach()`, `em.clear()` |
| **Removed** | Scheduled for DELETE on next flush | `em.remove(managedEntity)` |

Key transitions: `persist()` → transient→managed; `remove()` → managed→removed; transaction end → managed→detached; `merge()` → detached entity's state copied into a new managed copy (original stays detached).

**Interview trap:** "What does `merge()` return?" It returns the managed copy — NOT the same object you passed in. A common bug: calling `merge(detached)` and continuing to modify the `detached` instance, expecting those changes to be tracked. They won't be. Always use the returned managed entity.

**Tags:** entity-lifecycle, persistence-context, entity-states, managed, detached

---

## Q-SPRD-003 [bloom: recall] [level: junior]
**Question:** What is the persistence context and how does it act as a first-level cache?

**Model answer:** The persistence context is Hibernate's unit-of-work object — it's the runtime environment owned by an `EntityManager`. By default, its scope is a single transaction (`PersistenceContextType.TRANSACTION`).

It acts as a first-level (L1) cache: every entity loaded within a transaction is stored by identity (`type + id`). If you call `findById(42)` twice in the same transaction, Hibernate issues one SQL query and returns the same Java object reference on the second call — no second DB roundtrip.

Three important consequences:
1. **Dirty checking**: at flush time Hibernate compares the current state of each managed entity against the snapshot taken at load time. Changed fields → `UPDATE` statement generated automatically, no explicit `save()` needed.
2. **Identity guarantee**: within one persistence context, `findById(42) == findById(42)` (reference equality). Across persistence contexts (different transactions), this does not hold.
3. **Memory risk**: loading thousands of entities into the same persistence context fills heap and slows dirty checking. For bulk reads, use DTO projections or `StatelessSession`.

**Interview trap:** "Does the L1 cache survive transaction boundaries?" No — when the transaction commits, the persistence context closes and entities become detached. The L1 cache is destroyed. Only the second-level cache (disabled by default) survives across transactions.

**Tags:** persistence-context, first-level-cache, dirty-checking, entitymanager

---

## Q-SPRD-004 [bloom: recall] [level: junior]
**Question:** What are the default fetch types for @ManyToOne, @OneToOne, @OneToMany, and @ManyToMany?

**Model answer:**

| Annotation | Default Fetch |
|------------|--------------|
| `@ManyToOne` | **EAGER** |
| `@OneToOne` | **EAGER** |
| `@OneToMany` | **LAZY** |
| `@ManyToMany` | **LAZY** |

The rule of thumb from the JPA spec: to-one associations default EAGER (single row, cheap); to-many default LAZY (potentially large collection, expensive).

EAGER means: when the owning entity is loaded, the associated entity/collection is loaded in the same query (or immediately after). LAZY means: a proxy/wrapper is injected; the actual SQL fires only when you access the association.

**Interview trap:** "Should you ever use EAGER in production?" Almost never on to-many. EAGER on `@ManyToOne` is also frequently harmful — every query for the entity will JOIN or immediately SELECT the associated entity even when you don't need it. The safe default is to mark everything `fetch = FetchType.LAZY` and use `JOIN FETCH` or `@EntityGraph` to eagerly load only when needed for a specific use case.

**Tags:** fetch-type, eager, lazy, associations, basics

---

## Q-SPRD-005 [bloom: recall] [level: junior]
**Question:** What is the Spring Data repository hierarchy? List the interfaces from most basic to most feature-rich.

**Model answer:**

| Interface | Key additions |
|-----------|--------------|
| `Repository<T, ID>` | Marker only — zero methods. Used to limit exposure at module boundaries. |
| `CrudRepository<T, ID>` | `save`, `findById`, `existsById`, `findAll`, `deleteById`, `count` (returns `Iterable`) |
| `PagingAndSortingRepository<T, ID>` | `findAll(Pageable)`, `findAll(Sort)` |
| `JpaRepository<T, ID>` | `findAll()` returns `List`, `saveAll`, `flush`, `saveAndFlush`, `deleteAllInBatch`, `getReferenceById`, `getById` |

`JpaRepository` is JPA-specific (requires Hibernate under the hood). The others are provider-agnostic — `CrudRepository` works with Spring Data MongoDB, Redis, etc.

`getReferenceById(id)` returns a Hibernate proxy without hitting the DB immediately — useful when you only need a reference for a FK relationship, not actual data. Will throw `EntityNotFoundException` lazily if the ID doesn't exist.

**Interview trap:** "When would you NOT extend JpaRepository?" When building a shared module or library that must stay provider-agnostic, or when you want to limit API surface (prevent callers from calling `flush()` or `deleteAllInBatch()`). Extend `CrudRepository` or `Repository` + custom interface instead.

**Tags:** repository, spring-data, crudrepository, jparepository, hierarchy

---

## Q-SPRD-006 [bloom: recall] [level: junior]
**Question:** What is `LazyInitializationException`, when does it occur, and what are the main fixes?

**Model answer:** `LazyInitializationException` (Hibernate) is thrown when code tries to access a LAZY-loaded association after the persistence context (transaction) has closed and there is no active session to execute the additional query.

Typical pattern: repository method returns entity → transaction ends → service returns entity → controller or view accesses `entity.getItems()` → exception.

**Fixes, ordered from best to worst:**
1. **DTO projection**: load only what you need in the query. Never return entities from service to web layer. This is the architecturally correct fix.
2. **JOIN FETCH / @EntityGraph** in the repository query: eagerly load the needed association within the transaction.
3. **@Transactional on service method**: keeps the persistence context alive while the service method executes; access associations inside the transaction boundary.
4. **Open-session-in-view (OSIV)**: Spring Boot enables this by default (`spring.jpa.open-in-view=true`) — keeps persistence context open through HTTP request. Hides the exception but is an anti-pattern (see Q-SPRD-022).
5. **Hibernate.initialize(entity.getItems())**: explicit trigger inside transaction. Ugly but functional.

**Interview trap:** "Why is OSIV bad if it fixes LazyInitializationException?" Because it ties a DB connection to the HTTP thread for the entire request lifecycle — including time spent in view rendering, serialization, and other non-DB logic. Under load, this exhausts the connection pool. It also hides N+1 bugs that would otherwise be caught in dev.

**Tags:** lazy, lazyinitializationexception, osiv, fetch, dto-projection

---

## Q-SPRD-007 [bloom: understand] [level: regular]
**Question:** Explain the N+1 problem in JPA/Hibernate: what causes it, how do you detect it, and what are the available fixes?

**Model answer:** **Cause**: loading a list of N entities and then accessing an association on each one triggers N additional SELECT statements — one per entity. Classic example:

```java
List<Order> orders = orderRepo.findAll(); // SELECT * FROM orders  → 1 query
for (Order o : orders) {
    System.out.println(o.getCustomer().getName()); // SELECT * FROM customer WHERE id = ? → N queries
}
```
With EAGER fetch on `@ManyToOne`: the N queries fire immediately. With LAZY: they fire on association access. Either way you get N+1.

**Detection:**
- `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true` — count repeated similar queries in logs
- `spring.jpa.properties.hibernate.generate_statistics=true` — logs query count per session; alarm when count >> expected
- **p6spy** or **datasource-proxy**: log all SQL with caller stack trace in dev
- **Hypersistence Optimizer**: static analysis tool that detects N+1 at compile/test time

**Fixes:**
1. **JOIN FETCH** in JPQL:
   ```java
   @Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
   List<Order> findByStatusWithCustomer(@Param("status") Status status);
   ```
   Generates one SQL with JOIN. Caveat: can produce duplicate root entities with collections — use `DISTINCT` or `LinkedHashSet` result type.

2. **@EntityGraph** — declarative, no query modification:
   ```java
   @EntityGraph(attributePaths = {"customer", "items"})
   List<Order> findByStatus(Status status);
   ```

3. **@BatchSize** — Hibernate fetches in IN-clause chunks:
   ```java
   @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
   @BatchSize(size = 20)
   private List<Item> items;
   ```
   Reduces N+1 to ceil(N/batchSize)+1 queries. Not zero, but acceptable.

4. **DTO projection** — don't load entities at all; project to a flat DTO:
   ```java
   @Query("SELECT new com.app.OrderDto(o.id, o.status, c.name) FROM Order o JOIN o.customer c")
   List<OrderDto> findOrderSummaries();
   ```

5. **`hibernate.default_batch_fetch_size`** global property — applies `@BatchSize` behaviour to all lazy collections/associations without per-field annotation.

**Interview trap:** "Can JOIN FETCH be combined with Pageable?" No safely. Hibernate warns: `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`. It loads ALL rows, paginates in JVM memory. Fix: use a two-query approach (first page IDs, then fetch by ID with JOIN FETCH) or use `@BatchSize` instead.

**Tags:** n+1, join-fetch, entitygraph, batchsize, dto-projection, performance

---

## Q-SPRD-008 [bloom: understand] [level: regular]
**Question:** Explain @Transactional propagation levels. When would you use REQUIRES_NEW vs NESTED?

**Model answer:** Propagation controls what happens when a `@Transactional` method is called from another `@Transactional` method.

| Propagation | Behaviour |
|-------------|-----------|
| `REQUIRED` (default) | Join existing tx if present; create new one if none |
| `REQUIRES_NEW` | Always start a NEW tx; suspend existing tx until new one commits/rollbacks |
| `NESTED` | Create a savepoint inside existing tx; inner rollback returns to savepoint; outer tx still succeeds |
| `SUPPORTS` | Join tx if present; run without tx if none |
| `NOT_SUPPORTED` | Always run without tx; suspend existing if present |
| `NEVER` | Throw if a tx is active |
| `MANDATORY` | Throw if NO tx is active |

**REQUIRES_NEW** use case: audit logging that must persist even if the calling transaction rolls back.
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeAuditLog(String event) { ... } // always commits independently
```

**NESTED** use case: a batch step that can fail without aborting the whole batch. Uses a JDBC savepoint — requires JDBC driver support. Less common in practice; REQUIRES_NEW is simpler to reason about.

Critical gotcha: **self-invocation bypasses the proxy**. If `methodA()` in the same class calls `methodB()` with `@Transactional(propagation = REQUIRES_NEW)`, the annotation is silently ignored because the call never goes through the Spring proxy. Fix: extract to a separate bean, or inject self with `@Autowired private MyService self`.

**Interview trap:** "What's the difference between REQUIRES_NEW and NESTED?" REQUIRES_NEW creates a fully separate transaction at the DB level — its commit is independent of the outer tx. NESTED creates a savepoint within the same DB transaction — the savepoint rollback is partial, but the outer tx still controls the final commit/rollback. NESTED requires DB/driver savepoint support and is less portable.

**Tags:** transactional, propagation, requires-new, nested, savepoint, self-invocation

---

## Q-SPRD-009 [bloom: understand] [level: regular]
**Question:** What does `@Transactional(readOnly = true)` do and when should you use it?

**Model answer:** `readOnly = true` is a hint to both Spring and Hibernate:

**Spring / DataSource level:** passes the `readOnly` flag to the JDBC connection. Some JDBC drivers and connection pools (HikariCP) will route the connection to a read replica if configured. Also prevents accidental writes on read-only data sources.

**Hibernate level:**
- Skips **dirty checking** at flush time — no snapshot comparison, no UPDATE generation. For large read queries loading many entities, dirty checking scan is O(entities) — skipping it is measurable.
- Skips registering entities in the **persistence context write-through** path — slight memory and CPU saving.
- Sets flush mode to `MANUAL` (equivalent), preventing accidental flushes.

When to use:
- All `findBy*`, query methods, report/read endpoints that never modify entities
- Service methods that aggregate or transform data for display

When NOT to use:
- Any method that modifies state — even indirectly. If Hibernate skips dirty checking, your changes won't be flushed. If you mark a service method `readOnly = true` but it calls a helper that modifies an entity, the UPDATE will be silently dropped.

```java
@Transactional(readOnly = true)
public List<OrderDto> getOrdersForCustomer(Long customerId) {
    return orderRepo.findByCustomerId(customerId)
                    .stream().map(mapper::toDto).toList();
}
```

**Interview trap:** "Does readOnly = true prevent all DB writes?" No — it doesn't lock the DB. It tells Hibernate not to generate dirty-check UPDATEs and tells Spring to flag the connection as read-only. A `@Modifying @Query` inside a `readOnly = true` transaction would still execute if called (though this is a design bug).

**Tags:** transactional, readonly, dirty-checking, performance, hibernate

---

## Q-SPRD-010 [bloom: understand] [level: regular]
**Question:** What are the flush modes in Hibernate, and what does AUTO actually mean?

**Model answer:** Flush is the act of synchronizing the persistence context state to the DB (writing pending SQL). The flush mode controls when this happens automatically:

| Mode | When flushes occur |
|------|--------------------|
| `AUTO` (default) | Before every JPQL/HQL/Criteria query that might be affected by pending changes, AND at transaction commit |
| `COMMIT` | Only at transaction commit; never before a query |
| `MANUAL` | Never automatic; only when `em.flush()` is called explicitly |

`AUTO` is smarter than it sounds: Hibernate checks whether the pending dirty state overlaps with the query's entity types. If you have a dirty `Order` entity and execute a query on `Product`, Hibernate may skip the flush. If the query is on `Order`, it flushes first to ensure the query sees the latest data.

Practical implications:
- `COMMIT` mode is useful for read-heavy operations where you don't want intermediate flushes: set it on a batch processing method where you control when to flush.
- `readOnly = true` effectively sets MANUAL because no dirty checking occurs.

```java
Session session = em.unwrap(Session.class);
session.setHibernateFlushMode(FlushMode.COMMIT); // bypass auto-flush for this session
```

**Interview trap:** "What's the difference between flush and commit?" Flush writes SQL to the DB within the current transaction — rows are visible to the same transaction but not yet committed. Commit (connection.commit()) makes the changes durable and visible to other transactions. You can flush multiple times before committing.

**Tags:** flush-modes, auto, commit, hibernate, persistence-context

---

## Q-SPRD-011 [bloom: understand] [level: regular]
**Question:** Explain the owning side of a bidirectional JPA relationship. What is `mappedBy` and why does it matter?

**Model answer:** In a bidirectional relationship (both sides have a reference to each other), JPA/Hibernate must know which side "owns" the relationship — meaning which side's FK column to write to the database.

**Owning side**: the side without `mappedBy`. This side's field state is what Hibernate reads to write the JOIN column or join table. Typically the `@ManyToOne` side (it holds the FK column).

**Inverse side**: annotated with `mappedBy = "fieldName"` pointing to the owning side's field. Hibernate ignores the inverse side for persistence — it's only there for navigation in Java.

```java
@Entity
public class Order {
    @OneToMany(mappedBy = "order") // inverse side — no FK column here
    private List<Item> items;
}

@Entity
public class Item {
    @ManyToOne  // owning side — holds order_id FK column
    @JoinColumn(name = "order_id")
    private Order order;
}
```

**The critical consequence**: if you only set `order.getItems().add(item)` without setting `item.setOrder(order)`, Hibernate will NOT write the FK because you only updated the inverse side. Both sides must be kept in sync manually:

```java
public void addItem(Item item) {
    items.add(item);
    item.setOrder(this);  // BOTH sides synchronized
}
```

**Interview trap:** "What happens if you set mappedBy on the @ManyToOne side?" Compilation succeeds but behavior breaks — `mappedBy` on the wrong side causes Hibernate to treat the relationship as uni-directional from the other direction or throw a mapping exception. `mappedBy` only goes on the inverse (non-FK) side.

**Tags:** owning-side, mappedby, bidirectional, onetomany, manytoone, associations

---

## Q-SPRD-012 [bloom: understand] [level: regular]
**Question:** What are cascade types in JPA? What is the difference between `cascade = CascadeType.REMOVE` and `orphanRemoval = true`?

**Model answer:** Cascade types propagate `EntityManager` operations from parent to child:

| Type | What it propagates |
|------|--------------------|
| `PERSIST` | `em.persist(parent)` also persists new children |
| `MERGE` | `em.merge(parent)` also merges children |
| `REMOVE` | `em.remove(parent)` also removes children |
| `REFRESH` | `em.refresh(parent)` also refreshes children |
| `DETACH` | `em.detach(parent)` also detaches children |
| `ALL` | All of the above |

**`CascadeType.REMOVE`**: when you explicitly call `em.remove(parent)`, Hibernate also removes the children. But if you remove a child from the parent's collection (`parent.getItems().remove(item)`), nothing happens — the child stays in the DB.

**`orphanRemoval = true`**: also covers the collection-removal case. When a child is removed from the parent collection (orphaned), Hibernate schedules it for `DELETE`. This implies `REMOVE` cascade but is broader.

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Item> items;
```

Dangerous combination: `@ManyToMany(cascade = CascadeType.REMOVE)` or `cascade = ALL` on a many-to-many. Removing a `Tag` from a `Post`'s tag list would cascade-delete the `Tag` entity itself — which is almost certainly wrong. On `@ManyToMany`, only cascade `PERSIST` and `MERGE`.

**Interview trap:** "Does `orphanRemoval` require `cascade = REMOVE`?" No — `orphanRemoval = true` handles its own removal semantics. But `CASCADE_REMOVE` does NOT imply `orphanRemoval`. They are independent settings, though `orphanRemoval = true` effectively includes the behavior of `REMOVE` cascade for explicit `remove()` calls.

**Tags:** cascade, orphan-removal, onetomany, manytomany, remove

---

## Q-SPRD-013 [bloom: understand] [level: regular]
**Question:** Explain `Page<T>` vs `Slice<T>` in Spring Data. When would you choose one over the other?

**Model answer:** Both support pagination via `Pageable`, but differ on whether a count query is executed:

| Type | COUNT query? | Extra info | Returned by |
|------|-------------|------------|------------|
| `Page<T>` | **Yes** — always executes `SELECT COUNT(*) FROM ...` | `getTotalElements()`, `getTotalPages()`, `hasNext()`, `hasPrevious()` | `findAll(Pageable)` returning `Page<T>` |
| `Slice<T>` | **No** — fetches `pageSize + 1` rows to determine `hasNext()` | `hasNext()` only; no total count | `findAll(Pageable)` returning `Slice<T>` |

**When to use `Page`:** UI components with "1 of 42 pages" / "showing 20 of 840 results". The extra count query is the price of this information.

**When to use `Slice`:** "Load more" / infinite scroll / cursor-based navigation. You only need to know "is there a next page?" — no need for total count. Especially valuable on large tables where `COUNT(*)` is expensive (no covering index, needs full scan, or materialized views not present).

**Performance caveat with `Page`:** the COUNT query is generated as a separate SQL `SELECT COUNT(*) FROM (original_query_as_subquery)`. On complex JOINs, this count query can be expensive. Spring Data 2.5+ allows `@Query(countQuery = "...")` to specify a cheaper custom count query:
```java
@Query(value = "SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :s",
       countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :s")
Page<Order> findByStatus(@Param("s") Status s, Pageable p);
```

**Offset pagination limit**: both `Page` and `Slice` use OFFSET under the hood (`LIMIT n OFFSET m`). At high offsets (page 5000 of large table), the DB must scan and discard thousands of rows. For high-offset or streaming use cases, implement keyset (cursor) pagination manually.

**Interview trap:** "Can you use JOIN FETCH with Page?" No safely — see Q-SPRD-007. JOIN FETCH + Pageable causes in-memory pagination. Use a two-query approach or `@BatchSize` instead.

**Tags:** page, slice, pagination, count-query, spring-data, offset

---

## Q-SPRD-014 [bloom: apply] [level: senior]
**Question:** Walk me through what happens step by step when you call `orderRepository.findAll(PageRequest.of(0, 20))` with a `@OneToMany(fetch = LAZY)` on items. What queries fire, and what could go wrong in a REST endpoint that returns the orders with their items?

**Model answer:** Step by step:

1. Spring Data generates: `SELECT o FROM Order o` + `LIMIT 20 OFFSET 0` and a `SELECT COUNT(o) FROM Order o` (Page return type).
2. 20 `Order` entities are loaded into the persistence context. Each `items` collection is a Hibernate proxy — not loaded yet.
3. Transaction ends when the repository call returns (if called from a service with `@Transactional`, at method exit; if no `@Transactional`, each repository call has its own micro-transaction).
4. Control returns to the controller. Controller calls `orders.get(0).getItems()` or a Jackson serializer does.
5. **Three possible outcomes:**

   a) **If OSIV is on** (`spring.jpa.open-in-view=true`, Spring Boot default): persistence context is still open, Hibernate fires `SELECT * FROM items WHERE order_id = ?` for EACH of the 20 orders → **N+1 hidden by OSIV**. 21 total queries.

   b) **If OSIV is off and you access items outside transaction**: `LazyInitializationException` thrown.

   c) **If you used JOIN FETCH but also wanted Page**: Hibernate loads ALL matching orders into memory, paginates in JVM. Memory bomb on large tables.

**Correct fix:** Use a DTO projection inside the transaction:
```java
@Query("SELECT new com.app.OrderSummaryDto(o.id, o.status, COUNT(i)) " +
       "FROM Order o LEFT JOIN o.items i GROUP BY o.id, o.status")
Page<OrderSummaryDto> findOrderSummaries(Pageable p);
```
Or two-phase: page the IDs first, then JOIN FETCH by IDs.

**Interview trap:** "How do you page + eagerly load associations without the in-memory pagination warning?" Two-query pattern: `findAll(pageable)` returns a `Page<Long>` of IDs (count query included), then `findByIdIn(ids, sort)` with JOIN FETCH fetches the full entities. One page query + one batch query.

**Tags:** n+1, pagination, lazy, osiv, join-fetch, page, performance

---

## Q-SPRD-015 [bloom: apply] [level: senior]
**Question:** How do you implement optimistic locking with JPA, what happens at the DB level when a conflict occurs, and how should the application handle it?

**Model answer:** Optimistic locking assumes conflicts are rare. No DB lock is held during the read — conflict is detected at write time.

**Setup:**
```java
@Entity
public class Product {
    @Id
    private Long id;
    private int stock;

    @Version
    private Long version;  // Hibernate manages this — never set manually
}
```

**What Hibernate generates:** instead of `UPDATE product SET stock = ? WHERE id = ?`, it generates:
```sql
UPDATE product SET stock = ?, version = ? + 1 WHERE id = ? AND version = ?
```
If another transaction incremented `version` between your read and write, the `WHERE version = ?` clause matches 0 rows. Hibernate detects this (JDBC `getUpdateCount() == 0`) and throws `OptimisticLockException` (JPA) / `StaleObjectStateException` (Hibernate).

**Conflict handling:**
```java
@Retryable(value = OptimisticLockException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 50, multiplier = 2))
@Transactional
public void decrementStock(Long productId, int qty) {
    Product p = productRepo.findById(productId).orElseThrow();
    p.setStock(p.getStock() - qty);
    // dirty checking flushes on tx commit; OptimisticLockException thrown here
}
```

Options: retry (for low-conflict scenarios), return 409 Conflict HTTP status to client, or merge/resolve conflict manually.

**`@Version` type choices:** `int`/`long`/`Integer`/`Long` → numeric increment. `Timestamp`/`Instant` → timestamp-based (less reliable under high concurrency — same millisecond collisions). Use numeric.

**Interview trap:** "Does `@Version` protect against dirty reads?" No — it protects against lost updates (concurrent writers). It does not isolate reads; for read isolation use transaction isolation levels or pessimistic locking.

**Tags:** optimistic-locking, version, optimisticlockexception, concurrency, retry

---

## Q-SPRD-016 [bloom: apply] [level: senior]
**Question:** Explain pessimistic locking in JPA. What is the difference between PESSIMISTIC_READ and PESSIMISTIC_WRITE, and when do you choose pessimistic over optimistic?

**Model answer:** Pessimistic locking acquires a DB-level lock immediately on read, preventing concurrent modifications until the transaction commits.

```java
// In repository:
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") Long id);

// Or via EntityManager:
Product p = em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE);
```

| Lock Mode | SQL generated (PostgreSQL) | Effect |
|-----------|---------------------------|--------|
| `PESSIMISTIC_READ` | `SELECT ... FOR SHARE` | Others can read but not write |
| `PESSIMISTIC_WRITE` | `SELECT ... FOR UPDATE` | Exclusive — blocks all reads and writes from other transactions |
| `PESSIMISTIC_FORCE_INCREMENT` | `SELECT ... FOR UPDATE` + version increment | Exclusive + updates @Version |

**Pessimistic vs Optimistic:**

| Scenario | Prefer |
|----------|--------|
| Low contention, rare conflicts | Optimistic |
| High contention (inventory, seat booking, wallet balance) | Pessimistic |
| Long-lived operations (multi-step workflow) | Optimistic (don't hold locks) |
| Short critical section, no retry budget | Pessimistic |
| Conflict = user-facing error (payment) | Pessimistic |

**Deadlock risk**: two transactions both lock resource A then try to lock resource B in opposite order → deadlock. Mitigation: always acquire locks in a consistent order, set `jakarta.persistence.lock.timeout` to avoid indefinite blocking (Spring Boot 3.x / Jakarta EE; use `javax.persistence.lock.timeout` for Spring Boot 2.x):
```java
Map<String, Object> hints = Map.of("jakarta.persistence.lock.timeout", 3000);
em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE, hints);
```

**Interview trap:** "Does PESSIMISTIC_READ prevent dirty reads?" It prevents other transactions from acquiring PESSIMISTIC_WRITE on the same row. But it's not an isolation level — it doesn't replace transaction isolation settings. Non-locking reads (standard SELECT) may still proceed depending on DB and isolation level.

**Tags:** pessimistic-locking, lockmodetype, pessimistic-write, deadlock, concurrency

---

## Q-SPRD-017 [bloom: apply] [level: senior]
**Question:** Compare IDENTITY, SEQUENCE, and TABLE identifier generation strategies. Why does SEQUENCE allow batch inserts but IDENTITY does not?

**Model answer:**

| Strategy | Mechanism | Batch inserts | When to use |
|----------|-----------|---------------|-------------|
| `IDENTITY` | DB auto-increment (`SERIAL`, `AUTO_INCREMENT`) — Hibernate gets ID via `getGeneratedKeys()` after INSERT | **Broken** — forces per-row flush | Simple schemas, legacy DBs, when sequence not available |
| `SEQUENCE` | DB sequence object — Hibernate pre-fetches IDs in blocks (`allocationSize`) | **Works** — IDs known before INSERT | PostgreSQL, Oracle — preferred |
| `TABLE` | Simulates sequence with a dedicated lock table | **Theoretically works, but bottleneck** | Avoid — portability legacy, single-row locking kills throughput |

**Why IDENTITY breaks batching:** Hibernate needs to know the entity's ID before writing it to related entities and the persistence context. With `IDENTITY`, the ID is assigned by the DB only AFTER the INSERT. Hibernate cannot batch INSERTs because it must execute each one individually and read back the generated key. Batching requires that Hibernate knows the ID before INSERT.

**Why SEQUENCE enables batching:** Hibernate calls `nextval('user_id_seq')` to pre-allocate a block of IDs into memory (`allocationSize`). It can then assign IDs to entities before any INSERT, build up a batch, and flush them all at once with a single JDBC batch statement.

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
@SequenceGenerator(name = "order_seq", sequenceName = "order_id_seq", allocationSize = 50)
private Long id;
```

The DB sequence `INCREMENT BY` must match `allocationSize` or IDs will collide/gap. `allocationSize = 50` means Hibernate fetches one sequence value and uses IDs `[n, n+49]` before fetching again — 1 sequence call per 50 inserts.

**Interview trap:** "Can you batch with IDENTITY if you use `saveAll()`?" No. `spring.jpa.properties.hibernate.jdbc.batch_size` is ignored for IDENTITY. You'll see individual INSERTs in the SQL log even with `saveAll()`. Switch to SEQUENCE or use `StatelessSession` with manual ID assignment if you need batching on a legacy schema.

**Tags:** identity, sequence, table, id-generation, batch-inserts, performance

---

## Q-SPRD-018 [bloom: apply] [level: senior]
**Question:** How do you implement efficient batch inserts with JPA/Hibernate? Walk through the configuration and the flush/clear pattern.

**Model answer:** Naive `saveAll(largeList)` loads all entities into the persistence context — memory grows linearly and dirty checking scan becomes O(n). Correct batch insert requires:

**Step 1 — enable JDBC batching:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50       # group 50 INSERTs into one JDBC batch
        order_inserts: true    # sort by entity type to allow batching across saveAll calls
        order_updates: true
```

**Step 2 — use SEQUENCE (not IDENTITY)** — see Q-SPRD-017.

**Step 3 — flush/clear loop inside one transaction:**
```java
@Transactional
public void bulkInsert(List<Order> orders) {
    for (int i = 0; i < orders.size(); i++) {
        em.persist(orders.get(i));
        if (i % 50 == 49) {
            em.flush();   // send batch to DB
            em.clear();   // evict all entities from L1 cache — prevent heap growth
        }
    }
}
```

`em.clear()` is critical: without it, every persisted entity stays in the persistence context. Dirty checking on 100k managed entities is catastrophically slow.

**Alternative — StatelessSession:** Hibernate's `StatelessSession` bypasses the first-level cache entirely. No dirty checking, no cascade, no events. Fastest path for raw bulk inserts — use when you don't need entity lifecycle.

**Verification:** enable `hibernate.generate_statistics=true` and check `PreparedStatement` batch executions in logs, or set `logging.level.org.hibernate.engine.jdbc.batch=DEBUG`.

**Interview trap:** "Can you use Spring Data's `saveAll()` for batch inserts?" You CAN, but only if SEQUENCE strategy is used AND `batch_size` is configured AND `order_inserts = true`. With IDENTITY, `saveAll()` still issues individual INSERTs regardless of batch_size. Verify with SQL logging.

**Tags:** batch-inserts, flush-clear, stateless-session, jdbc-batch, performance

---

## Q-SPRD-019 [bloom: apply] [level: senior]
**Question:** What are @Query JPQL queries and native queries in Spring Data? When should you use each, and what does @Modifying do?

**Model answer:** Spring Data `@Query` supports two modes:

**JPQL (default):** queries against the entity model — table names are entity class names, column names are field names. Portable across JPA providers and databases. Hibernate translates to SQL.
```java
@Query("SELECT u FROM User u WHERE u.email = :email AND u.active = true")
Optional<User> findActiveByEmail(@Param("email") String email);
```

**Native SQL:** `nativeQuery = true` — raw SQL sent directly to the DB. Use when JPQL can't express the query (DB-specific functions, window functions, recursive CTEs, full-text search, complex subqueries).
```java
@Query(value = "SELECT * FROM users WHERE email = :email AND active = true",
       nativeQuery = true)
Optional<User> findActiveByEmailNative(@Param("email") String email);
```

**@Modifying:** required for `UPDATE` or `DELETE` queries. Without it, Spring Data treats the query as a SELECT and won't execute write operations.

```java
@Modifying(clearAutomatically = true)
@Transactional
@Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
int updateStatusBatch(@Param("status") Status status, @Param("ids") List<Long> ids);
```

`clearAutomatically = true` evicts all entities from the first-level cache after the bulk update. Without it, stale managed entities in the current persistence context won't reflect the DB change — you'd read the old status from L1 cache.

`flushAutomatically = true` (since Spring Data 2.x) flushes pending changes to DB before the modifying query executes — prevents the bulk query from missing recently changed (but not yet flushed) entities.

**Interview trap:** "Does `@Modifying @Query` participate in Hibernate dirty checking?" No — bulk UPDATE/DELETE statements in JPQL/SQL bypass dirty checking entirely. They write directly to the DB, bypassing any managed entities. This is why `clearAutomatically` exists — to reconcile the L1 cache state afterward.

**Tags:** query, jpql, native-query, modifying, clear-automatically, spring-data

---

## Q-SPRD-020 [bloom: apply] [level: senior]
**Question:** Explain the second-level cache in Hibernate. What is it, how is it different from the first-level cache, and what is the critical pitfall in a multi-node deployment?

**Model answer:** The **first-level cache (L1)** is the persistence context — per-transaction, always on, transparent. Entities loaded in one transaction are not shared with another.

The **second-level cache (L2)** is a session-factory-level cache shared across all transactions and sessions within the same JVM. It caches entity data (by ID), query results, and collection state. **Disabled by default.**

**Configuration:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Country { ... }  // reference data — good L2 candidate
```

| Strategy | For |
|----------|-----|
| `READ_ONLY` | Immutable reference data (currencies, countries) |
| `NONSTRICT_READ_WRITE` | Rarely updated; brief stale windows acceptable |
| `READ_WRITE` | Frequently read, occasionally updated — uses soft locks |
| `TRANSACTIONAL` | Requires JTA transaction manager; XA-capable cache |

**Multi-node critical pitfall:** L2 cache is per-JVM. In a cluster of 3 Spring Boot instances, each node has its own L2 cache. If node A writes `Country.name = "France"`, nodes B and C still serve the old value from their local cache until TTL expires or they reload. **Consequence: stale reads in production under load.**

Fix: use a **distributed cache provider** (Hazelcast, Redis via Redisson's Hibernate module, Infinispan) that invalidates/propagates across nodes. Or simply don't use L2 cache for mutable entities — only for genuinely immutable reference data.

**Interview trap:** "Is L2 cache always beneficial?" No. It adds complexity, can serve stale data, and adds memory overhead. For frequently-changing data it's actively harmful. Use L2 cache selectively: reference tables, slowly-changing configuration, immutable domain objects.

**Tags:** second-level-cache, l2-cache, hibernate-cache, multi-node, distributed-cache

---

## Q-SPRD-021 [bloom: apply] [level: senior]
**Question:** Explain derived query methods in Spring Data JPA. What is the naming convention, and what are the limits of derived queries?

**Model answer:** Spring Data parses the method name at startup and generates JPQL automatically. The convention: `findBy<Property><Condition>[And|Or<Property><Condition>][OrderBy<Property>Asc|Desc]`.

Examples:
```java
List<User> findByEmail(String email);
// SELECT u FROM User u WHERE u.email = ?

List<Order> findByStatusAndCreatedAtBetween(Status status, Instant from, Instant to);
// SELECT o FROM Order o WHERE o.status = ? AND o.createdAt BETWEEN ? AND ?

Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable p);

List<User> findTop10ByActiveOrderByCreatedAtDesc(boolean active);
// SELECT u FROM User u WHERE u.active = ? ORDER BY u.createdAt DESC LIMIT 10
```

Supported keywords: `Is`, `Equals`, `Not`, `In`, `NotIn`, `IsNull`, `IsNotNull`, `Like`, `NotLike`, `StartingWith`, `EndingWith`, `Containing`, `Between`, `LessThan`, `GreaterThan`, `Before`, `After`, `True`, `False`.

**Limits of derived queries:**
1. **No JOIN FETCH**: derived queries don't support eager loading via JOIN FETCH. Use `@EntityGraph` as an annotation on top of a derived query to add attribute paths.
2. **Readability collapses beyond 3 conditions**: `findByStatusAndCustomerCountryAndCreatedAtBetweenOrderByCreatedAtDesc` is unreadable. Use `@Query` with JPQL instead.
3. **No aggregations**: derived queries cannot produce COUNT, SUM, AVG. Use `@Query`.
4. **No native SQL features**: window functions, CTEs, DB-specific syntax — use `@Query(nativeQuery = true)`.

```java
// Derived query + @EntityGraph combo:
@EntityGraph(attributePaths = {"items", "customer"})
List<Order> findByStatus(Status status);
```

**Interview trap:** "Does Spring Data validate derived queries at startup?" Yes — Spring Data parses all derived query method names during application context initialization. A typo like `findByEmial` throws `PropertyReferenceException` at startup, not at runtime. This is a benefit over raw JPA/JDBC.

**Tags:** derived-queries, spring-data, naming-convention, entitygraph, query-methods

---

## Q-SPRD-022 [bloom: analyze] [level: senior]
**Question:** What is the open-session-in-view (OSIV) pattern? Why does Spring Boot enable it by default, and why should you disable it in production?

**Model answer:** **What it does:** OSIV (`spring.jpa.open-in-view=true`, Spring Boot default) opens a Hibernate session (and binds it to the current HTTP request thread) at the start of the HTTP request — before the controller is called — and closes it after the view is rendered and the response is committed. This means the persistence context remains open through the entire HTTP request lifecycle.

**Why Spring Boot enables it:** prevents `LazyInitializationException` when views/templates or Jackson serializers access lazy associations after the service method returns. It works silently and prevents a category of runtime errors for developers who don't understand the persistence context lifecycle.

**Why it's an anti-pattern:**

1. **DB connection held for the full request duration**: the HTTP request thread holds an open DB connection (from HikariCP) for the entire lifecycle — including time spent in JSON serialization, view rendering, filter processing, and any other non-DB logic. Under load, this exhausts the connection pool while connections sit idle.

2. **Hidden N+1**: lazy associations accessed in the controller or serializer fire queries that are invisible at the service layer. You won't see these in service-level profiling. They appear only in HTTP-level monitoring.

3. **Business logic bleeds into the presentation layer**: associations lazily loaded in a controller mean the controller has implicit data access coupling.

4. **Connection pool exhaustion under concurrent load**: with 10 concurrent requests each holding a connection for 200ms of serialization overhead, you exhaust a 10-connection pool. Services calling your API time out.

**Fix:**
```yaml
spring:
  jpa:
    open-in-view: false
```

Then fix any `LazyInitializationException` that surfaces by using DTO projections (preferred), ensuring associations are loaded within service transactions, or using `@EntityGraph`.

**Interview trap:** "Disabling OSIV will break things — isn't it safer to leave it on?" Leaving it on hides problems. Disabling in production exposes them in development/testing where they can be fixed properly. Every `LazyInitializationException` that surfaces after disabling OSIV is a legitimate bug that should be fixed by loading the data correctly, not by keeping a DB connection open.

**Tags:** osiv, open-in-view, connection-pool, lazy, anti-pattern, performance

---

## Q-SPRD-023 [bloom: analyze] [level: senior]
**Question:** Explain why `equals()` and `hashCode()` on JPA entities must NOT be based on object identity (`super.equals()`) and what the correct implementation strategies are.

**Model answer:** By default, `Object.equals()` uses reference equality (`==`). This breaks JPA entities in several ways:

**Problem 1 — Hibernate proxies:** Hibernate creates proxy subclasses for lazy-loaded entities. `proxy.equals(loadedEntity)` would be `false` with identity equality even though they represent the same DB row.

**Problem 2 — Detached/reattached:** loading the same entity in two separate transactions gives two different Java objects. If you put both in a `HashSet`, you'd have two entries for the same row.

**Problem 3 — Before persist:** a transient entity has `id = null`. If you add it to a `Set` with an ID-based `equals()`, then persist it (ID assigned), its hashCode changes — it's now effectively "lost" in the Set because it went into the wrong bucket.

**Strategies:**

**Option A — Business/natural key:**
```java
@Override public boolean equals(Object o) {
    if (!(o instanceof Order other)) return false;
    return orderNumber != null && orderNumber.equals(other.orderNumber);
}
@Override public int hashCode() { return getClass().hashCode(); } // stable across lifecycle
```
Use when entity has a stable natural key (email, SKU, UUID assigned before persist). `hashCode()` returns `getClass().hashCode()` (constant per type) — not perfect but stable across persist/detach.

**Option B — UUID assigned in constructor:**
```java
@Id
private UUID id = UUID.randomUUID(); // assigned pre-persist
```
ID is always non-null → safe to use in `equals()`/`hashCode()`. Best approach for new designs.

**Option C — Never put entities in Sets** — use Lists + don't compare by identity. Not always possible.

**Interview trap:** "Can you use database-assigned Long id in equals()?" Only if `id` is never null when `equals()` is called. If you add a transient entity to a Set before `persist()`, then persist it, the entity's hashCode changed and it's lost in the Set. UUID assigned in constructor avoids this entirely.

**Tags:** equals-hashcode, entity, proxy, hibernate, collections, uuid

---

## Q-SPRD-024 [bloom: analyze] [level: master]
**Question:** You're running a Spring Boot microservice handling 2000 req/sec and notice P99 response time spiking to 3 seconds during order listing. SQL logs show 200+ queries per request on the `/orders` endpoint. Walk through your complete diagnosis and remediation.

**Model answer:** This is a textbook N+1 crisis with OSIV hiding it. Systematic approach:

**Step 1 — Confirm and quantify:**
- `hibernate.generate_statistics=true` → check `queries executed` per HTTP request in APM (Datadog, New Relic)
- p6spy in staging: captures every SQL with execution time and caller stack trace
- The stack trace shows the lazy access point: likely Jackson serializer, a `toString()`, or controller code

**Step 2 — Identify the access pattern:**
Looking at stacks: `Jackson → Order.getCustomer().getName()` and `Jackson → Order.getItems()` fire outside the service transaction (OSIV keeps session open, masking the bug as "working").

**Step 3 — Disable OSIV first:**
```yaml
spring.jpa.open-in-view: false
```
Now `LazyInitializationException` surfaces in tests — pinpoints every lazy access outside transaction.

**Step 4 — Fix the queries:**

For the list endpoint returning `OrderSummaryDto`:
```java
@Query("SELECT new com.app.dto.OrderSummaryDto(o.id, o.status, c.name, COUNT(i)) " +
       "FROM Order o JOIN o.customer c LEFT JOIN o.items i " +
       "GROUP BY o.id, o.status, c.name")
Page<OrderSummaryDto> findSummaries(Pageable p);
```
One query. No entities. No lazy loading.

For a detail endpoint needing full items:
```java
// Two-phase: page IDs, then fetch with JOIN FETCH
@Query("SELECT o.id FROM Order o WHERE o.status = :s")
Page<Long> findIdsByStatus(@Param("s") Status s, Pageable p);

@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items JOIN FETCH o.customer WHERE o.id IN :ids")
List<Order> findByIdsWithItemsAndCustomer(@Param("ids") List<Long> ids);
```

**Step 5 — Add monitoring guard:**
```yaml
hibernate.generate_statistics: true
logging.level.org.hibernate.stat: DEBUG
```
Add a test: `StatisticsAssert.assertThat(statistics).hasQueryExecutionCount().lessThan(5)` using Hypersistence Optimizer or manual `Statistics` bean.

**Step 6 — Review connection pool sizing:**
With OSIV off and N+1 fixed, P99 drops. Also verify `maximum-pool-size` in HikariCP vs actual DB connection budget.

**Interview trap:** "Would adding a cache fix this?" Caching N+1 results in application cache hides the symptom. The N+1 still fires on cache miss. And cache thrashing in a high-write system means near-zero hit rate. Fix the query first.

**Tags:** n+1, performance, osiv, diagnosis, dto-projection, production, statistics

---

## Q-SPRD-025 [bloom: analyze] [level: master]
**Question:** Describe a scenario where `@Transactional` on a service method silently does nothing, and explain all the root causes and their fixes.

**Model answer:** There are three independent root causes for silent `@Transactional` no-ops:

**Root cause 1 — Self-invocation (most common):**
```java
@Service
public class OrderService {
    @Transactional
    public void processOrder(Long id) {
        saveAudit(id); // @Transactional IGNORED — calls this.saveAudit, not proxy.saveAudit
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void saveAudit(Long id) { ... }
}
```
Spring AOP wraps `OrderService` in a proxy. External calls go through the proxy (transaction applied). `this.saveAudit()` bypasses the proxy — no transaction.

Fixes: (a) extract `saveAudit` to a separate `@Service` bean and inject it; (b) inject self (`@Autowired private OrderService self`) and call `self.saveAudit()`; (c) use AspectJ compile-time/load-time weaving instead of proxy AOP.

**Root cause 2 — Private method:**
```java
@Transactional  // silently IGNORED
private void doUpdate() { ... }
```
Spring proxy can't override private methods. No exception thrown — annotation silently has no effect. Must be `public` (or `protected`/package-private for CGLIB proxies with some configuration).

**Root cause 3 — Exception type mismatch:**
```java
@Transactional  // does NOT rollback on checked exceptions by default
public void transfer() throws InsufficientFundsException {
    // debit account A
    // credit account B
    throw new InsufficientFundsException(); // transaction COMMITS, not rolls back
}
```
`@Transactional` only rolls back on `RuntimeException` and `Error` by default. Checked exceptions cause COMMIT.

Fix: `@Transactional(rollbackFor = InsufficientFundsException.class)` or make the exception extend `RuntimeException`.

**Root cause 4 — Bean not managed by Spring:**
If you `new OrderService()` instead of getting it from the container, no proxy is created → no AOP → `@Transactional` does nothing.

**Interview trap:** "Does `@Transactional` on a class-level cover all methods?" Yes for `public` methods in the class. But self-invocation and private methods still bypass the proxy. Class-level `@Transactional` sets a default; method-level overrides it.

**Tags:** transactional, self-invocation, proxy, aop, private-method, rollback, pitfalls

---

## Q-SPRD-026 [bloom: analyze] [level: master]
**Question:** Design the persistence layer for a high-write inventory system that must prevent overselling (stock < 0). Walk through the locking strategy, failure modes, and how you'd handle the retry budget.

**Model answer:** Inventory is a classic high-contention, lost-update problem. Optimistic locking breaks down when conflict rate is high (retry storm). Pessimistic locking has deadlock risk. Must choose deliberately.

**Analysis:**
- If 100 concurrent requests try to decrement stock simultaneously, optimistic locking generates ~99 `OptimisticLockException`s and one success. 99 retries = 99 additional DB roundtrips = cascading load. **Not viable for high-write flash sales.**
- Pessimistic (`SELECT FOR UPDATE`) serializes access. Throughput limited to `1 tx / lock_hold_time`. For a 10ms stock check + decrement, 100 RPS max per item.

**Preferred approach — DB-level atomic check-and-decrement:**
```java
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.stock = i.stock - :qty " +
       "WHERE i.productId = :productId AND i.stock >= :qty")
int decrementStockIfAvailable(@Param("productId") Long id, @Param("qty") int qty);
```
If `UPDATE` returns 0 rows → insufficient stock → throw `OutOfStockException`. No application-level locking. One atomic SQL statement. DB handles isolation.

This works because `UPDATE` statement has row-level exclusive locking at the DB. Concurrent updates serialize at DB level. No explicit `SELECT FOR UPDATE` needed — the `UPDATE` itself locks.

**For multi-item transactions (order with multiple products):**
Risk of deadlock if two transactions lock items in different order. Mitigation: sort item IDs before locking:
```java
productIds.stream().sorted().forEach(id -> lockAndDecrement(id, qtys.get(id)));
```

**Retry budget if using optimistic:**
```java
@Retryable(
    value = OptimisticLockException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2, random = true)  // jitter prevents thundering herd
)
```
3 attempts max, exponential backoff with jitter. Expose `409 Conflict` to client on exhaustion.

**Monitoring:** track `OptimisticLockException` rate in Micrometer. If > 5% of requests → switch to pessimistic or atomic update pattern.

**Interview trap:** "Why not just use synchronized in Java?" `synchronized` only works within one JVM. In a multi-node deployment, two nodes can both read stock=1 and both proceed. DB-level locking is the only correct solution.

**Tags:** optimistic-locking, pessimistic-locking, inventory, oversell, atomic-update, concurrency, production

---

## Q-SPRD-027 [bloom: analyze] [level: master]
**Question:** A JPQL `@Query` with JOIN FETCH is producing duplicate results when the target entity has a one-to-many collection. Explain why, and describe all correct ways to handle it.

**Model answer:** This is a fundamental JPA behavior caused by how SQL JOIN works on one-to-many relationships.

**The SQL root cause:**
```sql
SELECT o.*, i.* FROM orders o
JOIN items i ON i.order_id = o.id
WHERE o.status = 'OPEN'
```
If an order has 3 items, this produces 3 rows per order. JPA maps each row to an `Order` entity, resulting in 3 `Order` objects with the same id in the result list. `List<Order>` has duplicates; `Set<Order>` depends on `equals()`/`hashCode()`.

**Fixes:**

**Option 1 — `DISTINCT` in JPQL:**
```java
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.status = :s")
List<Order> findOpenOrdersWithItems(@Param("s") Status s);
```
`DISTINCT` in JPQL tells Hibernate to deduplicate at the Java level (by entity identity), NOT to add `SELECT DISTINCT` to SQL (prior to Hibernate 6). From Hibernate 6 onward, `SELECT DISTINCT` IS passed to SQL.

Pre-Hibernate 6 hint to avoid DB-level DISTINCT (cheaper):
```java
@QueryHints(@QueryHint(name = "hibernate.query.passDistinctThrough", value = "false"))
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.status = :s")
List<Order> findOpenOrdersWithItems(@Param("s") Status s);
```

**Option 2 — Use a `Set` return type:**
Not recommended because Set semantics depend on correct `equals()`/`hashCode()` implementation.

**Option 3 — Two-query approach (no duplicates, pagination-safe):**
```java
// Query 1: get IDs with pagination
@Query("SELECT o.id FROM Order o WHERE o.status = :s")
Page<Long> findOpenOrderIds(@Param("s") Status s, Pageable p);

// Query 2: fetch with JOIN FETCH using IDs
@Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items WHERE o.id IN :ids")
List<Order> findByIdsWithItems(@Param("ids") List<Long> ids);
```
No duplicates, pagination works correctly, single JOIN FETCH for the actual data.

**Option 4 — @EntityGraph (same JOIN FETCH semantics, declarative):** same duplicate issue applies; combine with DISTINCT.

**Interview trap:** "Does `DISTINCT` in JPQL add `SELECT DISTINCT` to SQL?" In Hibernate 5 (with `passDistinctThrough = false`): No — it's Java-level dedup only, no SQL overhead. In Hibernate 6 (default): Yes, SQL DISTINCT is added. Understanding this changed between major versions is an advanced gotcha interviewers use to probe.

**Tags:** join-fetch, distinct, duplicates, onetomany, jpql, hibernate-6, query

---

## Q-SPRD-028 [bloom: analyze] [level: master]
**Question:** Explain DTO projections in Spring Data JPA — interface projections, class-based projections, and native query projections. What are the behavioral differences and pitfalls of each?

**Model answer:** Projections load a subset of fields without instantiating full entities. Three types:

**Interface projection — Spring generates a proxy:**
```java
public interface OrderSummary {
    Long getId();
    String getStatus();
    String getCustomerName();  // from JOIN: must alias in @Query as "customerName"
}

@Query("SELECT o.id AS id, o.status AS status, c.name AS customerName " +
       "FROM Order o JOIN o.customer c WHERE o.id = :id")
Optional<OrderSummary> findSummaryById(@Param("id") Long id);
```
Spring creates a JDK dynamic proxy implementing the interface. Field access goes through the proxy via reflection. **Pitfall:** nested associations (e.g., `getCustomer().getCity()`) still trigger queries for the nested object — interface projection is NOT automatically flat. Explicit JOIN + alias is required.

**Class-based (DTO) projection — JPQL constructor expression:**
```java
public record OrderSummaryDto(Long id, String status, String customerName) {}

@Query("SELECT new com.app.dto.OrderSummaryDto(o.id, o.status, c.name) " +
       "FROM Order o JOIN o.customer c WHERE o.status = :s")
List<OrderSummaryDto> findSummaries(@Param("s") Status s);
```
No proxy — real object constructed. Slightly faster than interface projection for large result sets. Requires matching constructor. Works with `record` since Java 16. **Pitfall:** doesn't support dynamic projections or nested projections.

**Native query + interface projection:**
```java
@Query(value = "SELECT o.id, o.status, c.name AS customerName FROM orders o " +
               "JOIN customers c ON c.id = o.customer_id WHERE o.status = :s",
       nativeQuery = true)
List<OrderSummary> findSummariesNative(@Param("s") String s);
```
Column aliases must match interface getter names (case-insensitive). Works for complex SQL not expressible in JPQL. **Pitfall:** loses portability; column aliasing errors give null values silently (not exceptions).

**When to use which:**
- **Interface projection**: flexibility, Spring MVC integration, simple read models
- **Class projection**: maximum performance, explicit DTO contract, records
- **Native + interface**: complex SQL, DB-specific features, reporting queries

**Interview trap:** "Does interface projection prevent N+1?" Not automatically. `getCustomer().getName()` on an interface projection will trigger a separate query for the `customer` unless you alias it directly in the @Query. Test with SQL logging to verify.

**Tags:** dto-projection, interface-projection, jpql-constructor, native-query, spring-data, performance
