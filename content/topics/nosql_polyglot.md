# NoSQL & Polyglot Persistence — question bank

> NoSQL databases are no longer exotic — they are first-class production tools in any serious backend stack. A senior engineer must know when to reach for a relational store vs. a document/wide-column/graph/search store, understand the distributed-systems theory behind consistency trade-offs (CAP, PACELC, BASE vs ACID), and be able to design schemas, diagnose failures, and reason about dual-write hazards in a polyglot architecture. Allegro's stack (Cassandra, MongoDB, PostgreSQL, Elasticsearch, Redis, Druid) is a canonical example of this pattern. Interviewers at companies like Allegro, Booking, Adyen, and Delivery Hero will probe all of these layers.

## Scope

- NoSQL families: key-value (Redis), document (MongoDB), wide-column (Cassandra), graph, search (Elasticsearch)
- When NoSQL wins over RDBMS and when it does not
- CAP theorem — partition tolerance is mandatory in a distributed system; real choice is C vs A
- PACELC — extends CAP to include latency vs consistency trade-off when there is no partition
- BASE vs ACID — what each guarantee means operationally
- Consistency models: strong, eventual, causal, read-your-writes, monotonic reads
- Cassandra internals: partition key vs clustering key, model-per-query, write path (commitlog + memtable + SSTable), compaction, tunable consistency, QUORUM, LWT
- MongoDB internals: replica sets, oplog, sharding (hashed vs ranged), aggregation pipeline
- Redis internals: data structures, persistence (RDB vs AOF), eviction policies, cluster mode, Sentinel
- Redis use cases: cache, distributed lock (Redlock), rate limiter, pub/sub
- Elasticsearch internals: inverted index, analyzers, near-real-time (NRT), shards, replicas, relevance scoring
- Polyglot persistence: right store per bounded context, Allegro example
- Dual-write problem and solutions (outbox pattern, CDC, saga)

---

## Q-NOSQL-001 [bloom: recall] [level: junior]
**Question:** Name the four main families of NoSQL databases and give one representative product for each.

**Model answer:** The four main families are:

| Family | What it stores | Representative product |
|---|---|---|
| Key-value | Opaque values addressed by a single key | Redis, DynamoDB |
| Document | Semi-structured JSON/BSON documents | MongoDB, Couchbase |
| Wide-column | Rows with dynamic column families, optimized for sparse data | Cassandra, HBase |
| Graph | Nodes and edges with properties | Neo4j, Amazon Neptune |

A fifth family — **search engines** (Elasticsearch, OpenSearch, Solr) — is commonly treated separately but is effectively a document store with an inverted index as the primary access path.

The key-value family trades query flexibility for extreme throughput. Document stores add query/filter capability on fields. Wide-column stores are optimized for time-series and write-heavy workloads. Graph stores make traversal of relationships a first-class operation.

**Interview trap:** Elasticsearch is often used as "NoSQL" in job descriptions, but it is NOT a primary store — it lacks transactional guarantees and loses data on misconfiguration. The trap: "Can I use Elasticsearch as my only database?" — No. It is a search index, and you should have a source of truth behind it.

**Tags:** nosql, families, key-value, document, wide-column, graph, elasticsearch

---

## Q-NOSQL-002 [bloom: recall] [level: junior]
**Question:** What does the CAP theorem say? State all three letters and what each means.

**Model answer:** CAP theorem (Brewer, 2000, formally proven by Gilbert & Lynch, 2002) states that a distributed data store can guarantee at most **two** of the following three properties simultaneously:

- **C — Consistency:** Every read receives the most recent write or an error. All nodes see the same data at the same time (linearizability).
- **A — Availability:** Every request receives a response (not an error) — though it might not contain the most recent write.
- **P — Partition tolerance:** The system continues operating even when network messages between nodes are dropped or delayed (a network partition).

**The critical insight:** In any realistic distributed system, network partitions WILL happen (hardware fails, cables are cut, GC pauses cause timeouts). Therefore P is not optional — you must tolerate partitions. The real design choice is: **during a partition, do you sacrifice C or A?**

- **CP systems** (e.g., HBase, Zookeeper, etcd): return an error or timeout during a partition. All reads are consistent.
- **AP systems** (e.g., Cassandra, DynamoDB, CouchDB): keep serving requests during a partition but may return stale data.
- **CA** is only achievable if you have a single node (no distribution) — i.e., a standalone RDBMS. The moment you have replication, CA is impossible.

**Interview trap:** The classic wrong answer is "we chose CA." That's only valid for a single-node system. The moment you replicate, you're dealing with P. Don't say "CA in production."

**Tags:** cap-theorem, consistency, availability, partition-tolerance, distributed-systems

---

## Q-NOSQL-003 [bloom: recall] [level: junior]
**Question:** What is BASE and how does it contrast with ACID?

**Model answer:** **ACID** (relational databases) and **BASE** (most NoSQL stores) are two competing philosophies for what a database guarantees:

| Property | ACID | BASE |
|---|---|---|
| **A** | Atomicity — all-or-nothing | **B**asically Available — system guarantees availability (AP in CAP) |
| **C** | Consistency — DB constraints never violated | **S**oft state — state can change even without input (replicas converging) |
| **I** | Isolation — concurrent txns don't interfere | **E**ventually consistent — will become consistent given time and no new writes |
| **D** | Durability — committed data persists through crashes | |

**ACID** is right for financial ledgers, order management, inventory — any domain where correctness violations cost money or violate laws.

**BASE** is right for user-generated content, catalogs, clickstream, recommendations — high throughput domains where approximate/stale reads are acceptable and availability matters more than perfect consistency.

**Operational reality:** Many NoSQL stores (Cassandra with `QUORUM`, MongoDB with `w: majority`) can be tuned toward stronger consistency at the cost of latency. "NoSQL = no consistency" is a myth. The real question is what the default is and what you pay to raise the bar.

**Interview trap:** "Does Cassandra support transactions?" — Not multi-partition transactions. Single-partition lightweight transactions (LWT/`IF EXISTS`) exist but are expensive (Paxos round). Don't say "Cassandra has no transactions" and don't say "yes, full ACID."

**Tags:** acid, base, consistency, transactions, nosql

---

## Q-NOSQL-004 [bloom: recall] [level: junior]
**Question:** What are Redis's core data structures? List at least five.

**Model answer:** Redis is a data structure server. Core types:

| Type | Commands | Common use case |
|---|---|---|
| **String** | GET, SET, INCR, APPEND | Counter, cache value, session token |
| **List** | LPUSH/RPUSH, LRANGE, BLPOP | Queue, recent activity feed |
| **Hash** | HSET, HGET, HMGET | Object fields (user profile), shopping cart |
| **Set** | SADD, SMEMBERS, SINTER, SUNION | Unique visitors, tag indexes |
| **Sorted Set (ZSet)** | ZADD, ZRANGE, ZRANGEBYSCORE | Leaderboard, rate limiter sliding window, priority queue |
| **Bitmap** | SETBIT, GETBIT, BITCOUNT | Feature flags per user, daily active users |
| **HyperLogLog** | PFADD, PFCOUNT | Approximate unique count (12KB for ~0.81% error) |
| **Stream** | XADD, XREAD, XGROUP | Persistent append-only log, event sourcing light |
| **Geo** | GEOADD, GEODIST, GEORADIUS | Nearby stores, delivery radius |

Sorted Sets are the most interview-relevant structure because they power: leaderboards, rate limiters, scheduling (score = future timestamp), and ranked search results.

**Interview trap:** "How do you implement a rate limiter in Redis?" — Sliding window with Sorted Set: ZADD with timestamp as score, ZREMRANGEBYSCORE to trim old entries, ZCARD to count. Or fixed window with INCR + EXPIRE. The interviewer wants to see you reach for Sorted Set, not just INCR.

**Tags:** redis, data-structures, sorted-set, string, hash, list, set, hyperloglog

---

## Q-NOSQL-005 [bloom: recall] [level: junior]
**Question:** What is eventual consistency? Give a concrete example.

**Model answer:** **Eventual consistency** is a liveness guarantee: if no new updates are made to a data item, all replicas will eventually converge to the same value. There is no bound on how long "eventually" takes, and during the convergence window different clients may see different values.

**Concrete example — Cassandra:**
- Write `user:email = "new@example.com"` with `consistency level = ONE` (written to 1 of 3 replicas).
- Immediately after, a second client reads with `CL = ONE` from a different replica — it gets `"old@example.com"`.
- After Cassandra's hinted handoff / read repair completes (seconds to minutes), all replicas agree.

**DNS propagation** is a classic non-database example: after changing an A record, different resolvers around the world return the old IP for up to TTL hours.

**Shopping cart at Amazon** — the famous Dynamo paper justification: it is better to let the user add an item (available) than to refuse the write (consistent). Conflicts are resolved at checkout.

**Operational reality:** Eventual consistency is fine for reads of user-generated content, product views, recommendation results. It is NOT fine for bank balances, stock levels, or idempotency keys.

**Interview trap:** "Is eventual consistency the same as no consistency?" — No. It is a specific guarantee (convergence will happen). Systems can also offer **monotonic reads** (you never read older data than what you already read) or **read-your-writes** on top of eventual consistency without going all the way to linearizability.

**Tags:** eventual-consistency, distributed-systems, consistency-models, cassandra, dynamo

---

## Q-NOSQL-006 [bloom: understand] [level: junior]
**Question:** When would you choose a NoSQL database over a relational one, and when would you NOT?

**Model answer:** **Choose NoSQL when:**

1. **Massive write throughput** — Cassandra handles millions of writes/sec by design; a single Postgres primary cannot without extreme sharding complexity.
2. **Flexible / evolving schema** — MongoDB lets you ship new document shapes without a migration. Early product iteration benefits from this.
3. **Horizontal scale is a first-class requirement** — NoSQL stores were designed to scale out from day one. Postgres scales up (vertical) first, then sharding is a significant operational lift.
4. **Access pattern is known and narrow** — Cassandra's model-per-query is very fast when you know your partition key upfront.
5. **Data model is naturally non-relational** — social graphs (graph DB), search indexes (Elasticsearch), time-series clickstream (Cassandra/Druid).

**Do NOT choose NoSQL when:**

1. **Multi-entity transactions are required** — order creation that atomically decrements inventory + creates an order + charges a wallet needs ACID across tables. Distributed saga workarounds exist but add complexity.
2. **Ad-hoc queries are needed** — Cassandra doesn't support joins or arbitrary filtering. If your query patterns change frequently, a relational store wins.
3. **Relationships are complex** — if your data is naturally relational (foreign keys, normalization), forcing it into documents creates denormalization and update anomalies.
4. **Strong consistency is non-negotiable** — financial ledgers, compliance data, idempotency keys — use Postgres with proper isolation levels.
5. **The team is small** — polyglot persistence multiplies operational surface: backups, monitoring, version upgrades, on-call expertise. A startup with 3 engineers should start with one store.

**Allegro context:** they use PostgreSQL for transactional order data, Cassandra for high-scale event/catalog data, Elasticsearch for search, Redis for caching — each chosen for its workload. They have the SRE depth to operate all of them.

**Interview trap:** "NoSQL is faster" — wrong. A well-indexed Postgres query on a warm cache can be sub-millisecond. Speed depends on access pattern fit, not the label "NoSQL."

**Tags:** nosql-vs-rdbms, trade-offs, polyglot-persistence, architectural-decision

---

## Q-NOSQL-007 [bloom: understand] [level: regular]
**Question:** Explain the PACELC model. Why is it considered a more complete framing than CAP?

**Model answer:** **PACELC** (Daniel Abadi, 2012) extends CAP by recognizing that CAP only describes behavior **during a network partition (P)**, but says nothing about the rest of the time. The full framing:

```
if Partition:       choose between Availability (A)   vs Consistency (C)
else (no partition): choose between Latency (L)        vs Consistency (C)
```

Notation: **PAC** / **ELC** — a system is classified as PA/EL, PC/EL, PA/EC, or PC/EC.

**Why CAP is incomplete:** Most of the time there is no partition. But there is ALWAYS a latency/consistency trade-off even in normal operation. For example:

- **Cassandra (PA/EL):** during partition = available (AP); normally = low latency, eventual consistency (EL). `CL=ONE` returns as soon as one replica responds.
- **DynamoDB (PA/EL):** similar.
- **Zookeeper / etcd (PC/EC):** during partition = consistent (CP); normally = consistent reads (EC) — you wait for quorum even without a partition, adding latency.
- **MySQL with synchronous replication (PC/EC):** waits for replica ACK on every write — consistent but higher write latency.
- **Spanner (PC/EC):** uses TrueTime to achieve external consistency, but every commit waits for clock uncertainty — explicit latency/consistency trade-off.

**Practical relevance:** when designing a system you don't just ask "what happens during a disaster?" You ask "what is my read latency in the normal case, and how stale can reads be?" PACELC forces that conversation.

**Interview trap:** "We chose AP, so we don't need to worry about consistency." Wrong. PA/EL Cassandra with `CL=QUORUM` behaves more like PC/EC in practice. The letters in PACELC describe defaults and tendencies, not hard constraints. Tunable consistency blurs the lines.

**Tags:** pacelc, cap-theorem, consistency, latency, distributed-systems

---

## Q-NOSQL-008 [bloom: understand] [level: regular]
**Question:** Explain Cassandra's write path from client to durable storage. What is a commitlog, memtable, and SSTable?

**Model answer:** Cassandra's write path is designed for maximum write throughput — it turns random disk writes into sequential writes.

**Step by step:**

1. **Client sends write** to a coordinator node (any node in the cluster).
2. **Coordinator routes** the write to replica nodes based on the partition key hash and replication strategy.
3. **On each replica:**
   a. Write appended to the **commitlog** — a sequential, append-only file on disk. This is for crash recovery (fsync behavior is configurable — `periodic` for speed, `batch` for safety).
   b. Write applied to the in-memory **memtable** for that column family.
   c. Once the commitlog is written and memtable updated, the write is ACKed to the coordinator.
4. **Coordinator waits** for the number of replica ACKs dictated by the consistency level (e.g., `QUORUM` = majority).

**Flush to disk (SSTable):**
- When a memtable exceeds a memory threshold (or `nodetool flush` is called), it is flushed to disk as an **SSTable** (Sorted String Table) — immutable, sorted on disk.
- Once flushed, the corresponding commitlog segment can be discarded.

**Compaction:**
- Multiple SSTables accumulate. Reads must check all of them (plus the memtable), which degrades read performance.
- **Compaction** merges SSTables, discards tombstones (deleted data), and re-sorts. Strategy choices:
  - **STCS (Size-Tiered Compaction Strategy):** merges SSTables of similar size. Default; good for write-heavy.
  - **LCS (Leveled Compaction Strategy):** files are organized in levels; better read performance, higher write amplification.
  - **TWCS (Time-Window Compaction Strategy):** for time-series data; compacts within time windows, then never touches old windows.

**Tombstones:** deletes are actually writes (a tombstone marker). They must survive until the `gc_grace_seconds` period (default 10 days) to ensure they propagate to all replicas before being garbage-collected. Forgetting this causes "zombie data" resurrection after repairs.

**Interview trap:** "If a node crashes after writing to the commitlog but before flushing the memtable, is data lost?" — No. On restart, Cassandra replays the commitlog to rebuild the memtable. The data is safe.

**Tags:** cassandra, write-path, commitlog, memtable, sstable, compaction, tombstone

---

## Q-NOSQL-009 [bloom: understand] [level: regular]
**Question:** What is the difference between a partition key and a clustering key in Cassandra? Why does Cassandra require model-per-query design?

**Model answer:** In Cassandra's data model, the **primary key** has two parts:

```cql
PRIMARY KEY ((partition_key_columns), clustering_key_columns)
```

**Partition key:**
- Determines which node(s) store the row (via consistent hashing of the partition key's hash).
- All rows with the same partition key are guaranteed to be co-located on the same node(s).
- Queries MUST filter on the partition key — without it, Cassandra performs a full cluster scan (extremely expensive).
- Choose a partition key with high cardinality and even distribution. A bad choice (e.g., `date`) causes "hot partition" where one node gets all the writes.

**Clustering key:**
- Determines the sort order of rows **within** a partition.
- Enables efficient range scans: `WHERE partition_key = X AND clustering_key >= Y AND clustering_key <= Z`.
- Rows within a partition are stored sorted by clustering key on disk — makes range reads fast.

**Example — user activity feed:**
```cql
CREATE TABLE user_activity (
  user_id   UUID,
  event_ts  TIMESTAMP,
  event_type TEXT,
  payload   TEXT,
  PRIMARY KEY ((user_id), event_ts)
) WITH CLUSTERING ORDER BY (event_ts DESC);
```
Partition key = `user_id` (all of a user's activity on one node), clustering key = `event_ts` (sorted newest-first). Query: `WHERE user_id = ? LIMIT 50` — blazing fast.

**Model-per-query (denormalization):**
- Cassandra has no joins and no server-side sort across partitions.
- For each query pattern, you create a dedicated table materialized for that access path.
- Example: if you need activity by user AND by event_type, you create two tables.
- Consequence: data is duplicated. Updates must write to all copies (application responsibility or use Materialized Views, which have their own bugs).

**Interview trap:** "Can I use `ALLOW FILTERING`?" — Technically yes, but it's a table scan within a partition or across partitions. It exists for development convenience. In production with large datasets it will kill performance and should be forbidden in code review.

**Tags:** cassandra, partition-key, clustering-key, data-modeling, model-per-query, denormalization

---

## Q-NOSQL-010 [bloom: understand] [level: regular]
**Question:** How does Cassandra's tunable consistency work? What is QUORUM and when would you use ONE vs QUORUM vs ALL?

**Model answer:** Cassandra lets you specify a **consistency level (CL)** per operation, defining how many replicas must acknowledge a read or write before the coordinator returns success. This is separate from the replication factor (RF) which is set at keyspace creation.

**Key consistency levels (RF=3 assumed):**

| Level | Writes required | Reads required | Notes |
|---|---|---|---|
| ONE | 1 replica | 1 replica | Fastest, lowest consistency |
| TWO | 2 replicas | 2 replicas | |
| QUORUM | majority (⌊RF/2⌋+1 = 2 of 3) | majority | **Strong consistency** if W+R > RF |
| LOCAL_QUORUM | majority in local DC | majority in local DC | Multi-DC best practice |
| ALL | all replicas (3 of 3) | all replicas | Highest consistency, least available |

**The magic formula:** Strong consistency (linearizable reads after writes) is guaranteed when:
```
Write CL + Read CL > Replication Factor
```
With RF=3: QUORUM + QUORUM = 2 + 2 = 4 > 3. Strong consistency.
With ONE + ONE = 2, not > 3. Eventual consistency.

**When to use what:**
- **ONE:** Maximum throughput, can tolerate stale reads. User clickstream, metrics.
- **LOCAL_QUORUM:** Multi-DC setups. Writes/reads stay within the local datacenter; don't cross WAN for every operation. Allegro-type: order status reads that must be consistent but should stay in EU DC.
- **QUORUM:** Single-DC, strong consistency needed (pricing, inventory).
- **ALL:** Almost never — one node down = unavailable. Use only in extraordinary cases.

**LWT (Lightweight Transactions):** For "compare-and-set" semantics (`IF NOT EXISTS`, `IF col = X`), Cassandra uses Paxos. This is 4-round-trip consensus. It's slow (10-50x vs regular writes) and should be used sparingly (idempotency key dedup, user registration uniqueness check).

**Interview trap:** "If I use QUORUM for writes, can I use ONE for reads and still be consistent?" — No. W+R must exceed RF. QUORUM write + ONE read = 2+1=3, not > 3. You need at least QUORUM on the read side too.

**Tags:** cassandra, consistency-level, quorum, tunable-consistency, lwt, replication-factor

---

## Q-NOSQL-011 [bloom: understand] [level: regular]
**Question:** How does MongoDB's replica set work? What is the oplog?

**Model answer:** A MongoDB **replica set** is a group of `mongod` processes that maintain the same dataset. It consists of:

- **1 Primary** — accepts all writes. Elected by the replica set members via Raft-like consensus.
- **1+ Secondaries** — replicate from the primary asynchronously. Can serve reads (if `readPreference: secondary` is set). Participate in elections.
- **Arbiter (optional)** — votes in elections but holds no data. Used to achieve an odd number of votes without the storage cost of a full replica.

**Oplog (operations log):**
- A special capped collection (`local.oplog.rs`) on every member.
- The primary writes every operation as an **idempotent** BSON document to its oplog.
- Secondaries tail the primary's oplog and replay operations to stay in sync (async replication by default).
- Because it is a capped collection, the oplog has a fixed size. If a secondary falls too far behind (oplog window exhausted), it must full-resync.

**Write concern and read concern:**
- `w: 1` (default) — acknowledged after primary writes.
- `w: majority` — acknowledged after a majority of data-bearing members write. Prevents rollbacks if primary crashes.
- `readConcern: majority` — reads only data committed to majority. Prevents reading data that might be rolled back.
- For strong consistency: use `w: majority` + `readConcern: majority`.

**Failover:**
- If the primary goes down, secondaries hold an election. The member with the freshest oplog and highest priority wins.
- Election takes ~10-30 seconds. During this window, writes fail (CP behavior during partition).

**Interview trap:** "Is replication synchronous in MongoDB?" — Default is async. With `w: majority`, the client waits for majority ACK but the write is still happening in sequence (not 2PC). There can still be a small window where uncommitted writes are lost if the primary crashes before secondaries replicate — hence `w: majority` is the production best practice.

**Tags:** mongodb, replica-set, oplog, write-concern, read-concern, failover, replication

---

## Q-NOSQL-012 [bloom: understand] [level: regular]
**Question:** What is an inverted index and how does Elasticsearch use it for full-text search?

**Model answer:** An **inverted index** is a data structure that maps from terms (words/tokens) to the documents that contain them — the reverse of a forward index (document -> list of words). It is the backbone of all full-text search engines.

**Construction:**
1. Document ingestion triggers **analysis**: tokenization, lowercasing, stemming, stop-word removal, synonym expansion (configurable per analyzer).
2. Example: "The Quick Brown Foxes" → tokens: `quick`, `brown`, `fox` (stemmed).
3. Inverted index entry: `fox` → [doc1, doc5], `quick` → [doc1, doc3, doc7]...
4. Each entry also stores term frequency (TF), positions, and offsets for scoring.

**Query execution:**
- A query for "brown fox" looks up both terms in the inverted index, intersects the doc lists, and scores each result using **BM25** (Elasticsearch 5+; previously TF-IDF).
- BM25 penalizes very long documents (saturation) and rewards term rarity (IDF).

**Elasticsearch architecture:**
- An **index** is split into **shards** (default 1 primary shard since ES 7.0). Each shard is a Lucene instance with its own inverted index.
- Each primary shard has **replica shards** for HA and read throughput.
- **Near-real-time (NRT):** Lucene buffers new documents in memory. A **refresh** (default every 1 second) moves them to an in-memory segment and makes them searchable. A **flush** writes to disk (translog → segment). This is why Elasticsearch is NRT, not real-time — there is a ~1s visibility lag.

**Analyzers matter:**
- The analyzer at index time must match the analyzer at query time, or searches will miss results.
- Common mistake: using `standard` analyzer for an `email` field — it splits on `@` and `.`, so searching for `user@example.com` becomes `user`, `example`, `com`.

**Interview trap:** "Can Elasticsearch be the primary database?" — No. Elasticsearch is optimized for search, not durability or transactions. Data is lost if a node goes down before a flush (translog helps but is not a WAL). Always have a primary store (Postgres/Mongo) and replicate to Elasticsearch for search.

**Tags:** elasticsearch, inverted-index, full-text-search, analyzer, nrt, shards, bm25, lucene

---

## Q-NOSQL-013 [bloom: apply] [level: regular]
**Question:** Design a schema in Cassandra for storing user events (userId, eventType, timestamp, payload). Requirements: (1) fetch last 50 events for a user, (2) fetch events by userId and eventType in the last 7 days.

**Model answer:** Two tables — one per query pattern (model-per-query).

**Table 1 — all events for a user, newest first:**
```cql
CREATE TABLE user_events (
    user_id    UUID,
    event_ts   TIMESTAMP,
    event_id   UUID,         -- tie-breaker for same-millisecond events
    event_type TEXT,
    payload    TEXT,
    PRIMARY KEY ((user_id), event_ts, event_id)
) WITH CLUSTERING ORDER BY (event_ts DESC, event_id DESC)
  AND default_time_to_live = 2592000;  -- 30-day TTL, auto-eviction
```
Query: `SELECT * FROM user_events WHERE user_id = ? LIMIT 50;`

**Table 2 — events by userId + eventType:**
```cql
CREATE TABLE user_events_by_type (
    user_id    UUID,
    event_type TEXT,
    event_ts   TIMESTAMP,
    event_id   UUID,
    payload    TEXT,
    PRIMARY KEY ((user_id, event_type), event_ts, event_id)
) WITH CLUSTERING ORDER BY (event_ts DESC, event_id DESC)
  AND default_time_to_live = 2592000;
```
Query: `SELECT * FROM user_events_by_type WHERE user_id = ? AND event_type = ? AND event_ts >= ? AND event_ts <= ?;`

**Key design decisions:**
- Partition key `(user_id, event_type)` in table 2 creates one partition per user-eventType combination — high cardinality, even distribution (assuming userId is UUID).
- Clustering key `event_ts DESC` means newest-first scan is the natural on-disk order — no sort needed.
- `event_id` as UUID tie-breaker prevents overwrite of same-millisecond events.
- TTL at table level automatically evicts old data without tombstones piling up (important for write-heavy event tables).

**Application responsibility:** every write must go to BOTH tables atomically (Cassandra **BATCH** with logged=true for same-partition atomicity, or accept dual-write risk for cross-partition).

**Interview trap:** "What if a user has 10 million events — is the partition too large?" — Yes, an unbounded partition is a hot partition problem. Fix: bucket the partition key: `(user_id, date_bucket)` where date_bucket = `TO_DATE(event_ts)`. Now each day is a separate partition. Query spans multiple partitions for a date range — acceptable with token-range reads.

**Tags:** cassandra, data-modeling, schema-design, partition-key, clustering-key, ttl, model-per-query

---

## Q-NOSQL-014 [bloom: apply] [level: regular]
**Question:** How does MongoDB sharding work? What is the difference between hashed and ranged sharding?

**Model answer:** MongoDB **sharding** horizontally distributes data across multiple shards. Components:

- **Config servers (3-node replica set):** store cluster metadata and chunk-to-shard mappings.
- **Mongos (query router):** receives client queries, consults config servers, routes to correct shards.
- **Shards:** each is a replica set storing a subset of the data.

**Chunks and shard key:**
- Data is partitioned by a **shard key** — a field (or compound field) in each document.
- The range of shard key values is divided into **chunks** (default 128MB). Each chunk is assigned to a shard.
- The **balancer** migrates chunks between shards to keep them roughly equal in size.

**Hashed sharding:**
- MongoDB hashes the shard key value, distributes documents uniformly across shards.
- **Pros:** even distribution, no hot shards even if keys are monotonically increasing (e.g., ObjectId timestamps).
- **Cons:** range queries must scatter-gather to all shards (no range scan optimization).
- **Use when:** writes are the bottleneck; queries are mostly by exact key.

**Ranged sharding:**
- Documents with adjacent shard key values are stored in the same chunk/shard.
- **Pros:** range queries (`WHERE ts >= X AND ts <= Y`) can go to a single shard — efficient.
- **Cons:** monotonically increasing keys (ObjectId, timestamps) cause writes to pile up on the "last" shard (hot shard problem).
- **Use when:** range queries are frequent; key distribution is not monotonic.

**Scatter-gather vs targeted:**
- A query that includes the shard key = **targeted** (goes to 1 shard).
- A query without the shard key = **scatter-gather** (sent to all shards, results merged by mongos). Expensive at scale.
- Rule: every high-frequency query should include the shard key in its filter.

**Interview trap:** "Can I change the shard key after data is loaded?" — As of MongoDB 4.4, you can refine a shard key (add a suffix field). As of 5.0, you can reshard to a completely different key, but it is an online but expensive operation. In practice: choose your shard key very carefully upfront. Wrong choices are painful to fix.

**Tags:** mongodb, sharding, shard-key, hashed-sharding, ranged-sharding, scatter-gather, balancer

---

## Q-NOSQL-015 [bloom: apply] [level: senior]
**Question:** What is the dual-write problem in a polyglot persistence architecture? How do you solve it?

**Model answer:** **The problem:** When a service writes to two data stores (e.g., Postgres + Elasticsearch, or Cassandra + Redis), there is no distributed atomic transaction across them. If the first write succeeds and the second fails — or the process crashes between the two — the stores are inconsistent. This is the **dual-write problem**.

**Example:**
```kotlin
// BAD — dual-write without atomicity
fun updateProduct(product: Product) {
    productRepository.save(product)   // Postgres write
    searchIndexService.index(product) // Elasticsearch write — can fail
}
```
If Elasticsearch is down, Postgres has the update but Elasticsearch is stale — search results are wrong until the next full reindex.

**Solution 1 — Transactional Outbox Pattern (recommended):**
1. In the SAME database transaction as the business write, also write an "event" row to an `outbox` table.
2. A separate **relay process** (or CDC connector) reads the outbox and publishes to the second store.
3. Guarantees: the business write and the outbox entry are atomic (same Postgres transaction). The relay delivers at-least-once. Second store is eventually consistent.

```sql
BEGIN;
UPDATE products SET name = 'New Name' WHERE id = ?;
INSERT INTO outbox (aggregate_id, event_type, payload) VALUES (?, 'PRODUCT_UPDATED', ?);
COMMIT;
```

**Solution 2 — Change Data Capture (CDC):**
- Use Debezium (or Maxwell) to stream Postgres WAL or MongoDB oplog changes to Kafka.
- Kafka consumers write to Elasticsearch/Cassandra/cache.
- Same eventual-consistency guarantee, but no outbox table needed — CDC reads the WAL directly.
- Allegro uses a similar pattern for keeping Elasticsearch in sync with their primary stores.

**Solution 3 — Single write, derived view:**
- Only write to one store (the primary). The second store is a pure derived view.
- On read, compute or cache the derived view.
- Works when the second store is a cache (Redis) — just invalidate/refresh after the primary write.

**Solution 4 — Saga (for microservices):**
- If the two writes span microservices, use Saga (choreography or orchestration) with compensating transactions.
- More complex; overkill for in-process dual writes.

**Interview trap:** "Can you just use a try-catch and retry the second write?" — Retries don't solve the atomicity problem. If the process crashes before the retry, the inconsistency is permanent until manual intervention or a periodic reconciliation job discovers it. The outbox pattern gives you durable retry.

**Tags:** dual-write, polyglot-persistence, outbox-pattern, cdc, debezium, data-consistency, kafka

---

## Q-NOSQL-016 [bloom: apply] [level: senior]
**Question:** How would you implement a distributed rate limiter using Redis? Compare the fixed window and sliding window approaches.

**Model answer:** A rate limiter controls how many requests a client can make in a given time window.

**Fixed Window Counter (simple, biased):**
```kotlin
fun isAllowed(userId: String, limitPerMinute: Int): Boolean {
    val key = "rl:$userId:${System.currentTimeMillis() / 60_000}"  // minute bucket
    val count = redis.incr(key)
    if (count == 1L) redis.expire(key, 120)  // 2-minute safety TTL
    return count <= limitPerMinute
}
```
**Flaw:** boundary burst — a user can make `2 * limit` requests in 2 seconds spanning a window boundary (e.g., 100 at 00:59 and 100 at 01:01).

**Sliding Window with Sorted Set (precise, more memory):**
```kotlin
fun isAllowed(userId: String, limitPerMinute: Int): Boolean {
    val key = "rl:$userId"
    val now = System.currentTimeMillis()
    val windowStart = now - 60_000
    val requestId = UUID.randomUUID().toString()

    redis.multi {               // atomic pipeline
        zremrangeByScore(key, 0.0, windowStart.toDouble())   // drop old entries
        zadd(key, now.toDouble(), "$now-$requestId")          // add this request
        zcard(key)                                            // count in window
        expire(key, 61)                                       // TTL cleanup
    }.let { results ->
        val count = results[2] as Long
        return count <= limitPerMinute
    }
}
```
**Key:** score = timestamp. ZREMRANGEBYSCORE trims entries older than 1 minute. ZCARD counts current window. Atomic via MULTI/EXEC or Lua script.

**Lua script approach (truly atomic, single round-trip):**
```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local id = ARGV[4]

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
redis.call('ZADD', key, now, id)
local count = redis.call('ZCARD', key)
redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
return count
```

**Comparison:**

| Approach | Memory | Accuracy | Complexity |
|---|---|---|---|
| Fixed window | O(1) per key | Allows 2x bursts at boundary | Trivial |
| Sliding window (ZSet) | O(requests in window) | Exact | Medium |
| Token bucket (INCR + Lua) | O(1) | Good, allows controlled bursts | Medium |

**Production note:** Redis Sorted Set sliding window is the standard choice for API rate limiting. Token bucket is better for burst-friendly APIs (e.g., allow 10 req burst, replenish 1/sec). Libraries like Resilience4j or Bucket4j handle this in JVM.

**Interview trap:** "Is MULTI/EXEC sufficient for atomicity in Redis cluster mode?" — No. MULTI/EXEC guarantees atomicity on a single node. In cluster mode, all keys in a MULTI block must hash to the same slot. For the rate limiter key `rl:userId`, this is naturally single-key, so it's fine. But if you try to use MULTI across two different user keys in one transaction in cluster mode — it will fail with a CROSSSLOT error.

**Tags:** redis, rate-limiter, sorted-set, sliding-window, fixed-window, lua, redis-cluster, atomic

---

## Q-NOSQL-017 [bloom: apply] [level: senior]
**Question:** Explain Redis persistence modes: RDB snapshots vs AOF. How do you choose, and what is the risk of each?

**Model answer:** Redis offers three durability options:

**RDB (Redis Database Snapshot):**
- Periodically forks the process and writes a point-in-time snapshot of all data to `dump.rdb`.
- Configured via `save 900 1` (snapshot if >=1 key changed in 900s), `save 300 10`, etc.
- Fork is copy-on-write — no blocking of reads/writes during snapshot, but the fork itself can take time on large datasets (seconds for multi-GB instances).
- **Risk:** you can lose all writes between the last snapshot and a crash. For a 15-minute save interval, you lose up to 15 minutes of data.
- **Good for:** dev environments, cache-only Redis (data loss is acceptable), smaller datasets.

**AOF (Append-Only File):**
- Every write command is appended to `appendonly.aof`.
- `appendfsync` policy:
  - `always` — fsync after every write. Safest, but very slow (disk I/O on every write).
  - `everysec` — fsync once per second. At most 1 second of data loss. **Production default.**
  - `no` — let the OS decide (typically every 30s). Fastest, least safe.
- AOF rewrite: over time the AOF grows. Redis rewrites it periodically (compacting it to the minimal set of commands to reproduce current state).
- **Risk:** AOF files are larger than RDB; rewrite can be CPU-intensive. On very high write throughput, `everysec` still means up to 1 second of loss.

**AOF + RDB (hybrid mode — Redis 4+):**
- `aof-use-rdb-preamble yes` — AOF starts with an RDB snapshot, then appends AOF diffs.
- Best of both: fast restore (RDB) + near-zero data loss (AOF).
- **Recommended for production.**

**Eviction policies (for cache use):**
When Redis hits `maxmemory`, it must evict keys. Policies:
- `allkeys-lru` — evict least-recently-used keys. Good for general cache.
- `volatile-lru` — only evict keys with a TTL set.
- `allkeys-lfu` — evict least-frequently-used (Redis 4+). Better for skewed access patterns.
- `noeviction` — return OOM error. Use when Redis is a primary store, not cache.
- `volatile-ttl` — evict the key with the soonest TTL.

**Interview trap:** "We have Redis as our session store. Is it safe to use RDB only?" — No. With RDB every 15 minutes, a crash could invalidate up to 15 minutes worth of sessions — users get logged out. Use AOF `everysec` or hybrid for session stores.

**Tags:** redis, persistence, rdb, aof, eviction, durability, hybrid-persistence, maxmemory

---

## Q-NOSQL-018 [bloom: apply] [level: senior]
**Question:** How does MongoDB's aggregation pipeline work? Walk through a non-trivial example.

**Model answer:** MongoDB's **aggregation pipeline** processes documents through a series of transformation stages. Each stage receives documents from the previous stage and outputs documents to the next.

**Core stages:**

| Stage | Purpose |
|---|---|
| `$match` | Filter documents (like WHERE). Put early to use indexes. |
| `$project` | Reshape documents, include/exclude fields, compute expressions |
| `$group` | Group by key, apply accumulators (sum, avg, count, push, addToSet) |
| `$sort` | Sort documents |
| `$limit` / `$skip` | Pagination |
| `$lookup` | Left outer join to another collection |
| `$unwind` | Deconstruct array field into multiple documents |
| `$facet` | Multiple sub-pipelines in parallel (for faceted search) |
| `$bucket` | Categorize documents into ranges |
| `$out` / `$merge` | Write pipeline output to a collection |

**Non-trivial example — top 5 categories by revenue for orders in the last 30 days:**
```javascript
db.orders.aggregate([
  // Stage 1: filter recent orders
  { $match: {
      status: "COMPLETED",
      createdAt: { $gte: new Date(Date.now() - 30*24*60*60*1000) }
  }},
  // Stage 2: explode order items array into individual documents
  { $unwind: "$items" },
  // Stage 3: join with products to get category
  { $lookup: {
      from: "products",
      localField: "items.productId",
      foreignField: "_id",
      as: "product"
  }},
  { $unwind: "$product" },
  // Stage 4: group by category, sum revenue
  { $group: {
      _id: "$product.category",
      totalRevenue: { $sum: { $multiply: ["$items.quantity", "$items.price"] } },
      orderCount: { $sum: 1 }
  }},
  // Stage 5: sort by revenue
  { $sort: { totalRevenue: -1 } },
  // Stage 6: top 5 only
  { $limit: 5 }
])
```

**Performance rules:**
1. `$match` first — reduces documents early, can use indexes.
2. `$project` early — reduces document size for subsequent stages.
3. `$lookup` is expensive — it's a join. Avoid inside hot loops; consider denormalizing frequently-joined fields.
4. `allowDiskUse: true` for pipelines that exceed the 100MB in-memory sort limit.

**Interview trap:** "Is `$lookup` as efficient as a SQL JOIN?" — No. MongoDB's `$lookup` is a nested-loop join (or a hash join from 5.1+ with indexed fields). It does not have the query planner sophistication of Postgres's join optimizer. For complex multi-join queries, consider whether a relational store is the right choice.

**Tags:** mongodb, aggregation-pipeline, lookup, group, match, unwind, performance

---

## Q-NOSQL-019 [bloom: analyze] [level: senior]
**Question:** You inherit a system where a service writes user events to Cassandra (primary) and also to Elasticsearch for search. Occasionally, Elasticsearch is out of sync. Diagnose the root cause and propose a resilient architecture.

**Model answer:** **Diagnosis — likely root causes:**

1. **Dual-write failure (most likely):** The service writes to Cassandra, then makes an HTTP call to Elasticsearch. If ES is slow, times out, or the service crashes between the two writes, ES is stale. No retry + no dead-letter queue = silent data loss.
2. **Retry without idempotency:** Retried ES writes may have race conditions (retry writes an older version of a document over a newer one).
3. **Elasticsearch indexing lag:** Writes to ES go into a buffer; even if both writes succeed, ES's 1-second refresh means ES is ~1s behind by design (NRT). This is expected, not a bug.
4. **Elasticsearch bulk rejection:** ES rejects documents when the indexing queue is full (`429 Too Many Requests`). If the service doesn't handle this with backoff+retry, writes are silently dropped.
5. **Mapping conflicts:** A schema change in Cassandra wasn't reflected in the ES mapping — documents are accepted but fields are not indexed correctly.

**Resilient architecture — Outbox + CDC:**

```
Service
  └─ Cassandra write (business data)
  └─ Cassandra outbox table write (same partition, logged batch)

CDC Connector (Debezium reading Cassandra commitlog or oplog)
  └─ Publishes changes to Kafka topic: user-events

Elasticsearch Sink Consumer (Kafka Connect ES Sink or custom)
  └─ Reads from Kafka, writes to Elasticsearch
  └─ At-least-once; idempotent via document _id = event_id
```

**Benefits:**
- Cassandra write + outbox write = atomic (logged batch within same partition).
- Kafka is durable; if ES is down, events queue up and catch up when ES recovers.
- ES Sink uses `_id` from event, making re-delivery idempotent.
- Circuit breaker in the Sink prevents Kafka consumer from crashing on ES failures.

**Monitoring to add:**
- Kafka consumer lag on the ES sink topic — leading indicator of ES falling behind.
- ES rejected write rate (metric: `es_index_rejected_total`).
- Cassandra outbox table row count — if rows pile up, the relay is stuck.

**Interview trap:** "Can we just make the Elasticsearch write synchronous with a distributed transaction (XA)?" — In theory, a 2PC across Cassandra and Elasticsearch. In practice: Cassandra doesn't support XA; Elasticsearch has no transaction coordinator. This approach is a dead end. CDC/outbox is the industry standard.

**Tags:** dual-write, outbox-pattern, cdc, debezium, kafka, elasticsearch, cassandra, resilience, incident-diagnosis

---

## Q-NOSQL-020 [bloom: analyze] [level: senior]
**Question:** Describe the polyglot persistence approach at a company like Allegro. What store would you assign to each of these bounded contexts, and why: (1) product catalog, (2) order management, (3) full-text product search, (4) real-time recommendation serving, (5) clickstream analytics?

**Model answer:** Allegro explicitly practices polyglot persistence — no single DB fits all workloads. The assignment:

**1. Product catalog → MongoDB (document store)**
- Product data is semi-structured and varies by category (electronics have different fields than clothing).
- MongoDB's flexible schema handles this naturally without a 200-column sparse table.
- Writes are moderate; reads are key-based (by product ID) or category-filtered.
- MongoDB's aggregation pipeline handles catalog analytics.
- Alternative: Postgres with JSONB columns if you need strong relational constraints.

**2. Order management → PostgreSQL (relational ACID)**
- Orders involve multiple entities: order, order items, payment, inventory reservation.
- ACID transactions are non-negotiable — decrementing inventory and creating an order must be atomic.
- Complex queries: "all orders by user in last 30 days, with items, payment status" — SQL shines here.
- Auditability and compliance require strong consistency and no data loss.

**3. Full-text product search → Elasticsearch**
- Full-text search, faceted filtering (price range, brand, category), relevance ranking — all built into ES.
- Inverted index makes "laptop red under 1000 EUR" queries millisecond-fast.
- NOT the source of truth — ES is populated via CDC from MongoDB/Postgres.
- Near-real-time is acceptable (1-second lag for new products appearing in search).

**4. Real-time recommendation serving → Redis (+ vector store for embeddings)**
- Precomputed recommendation lists (top-N items for userId) must be served in <5ms at 20k RPS.
- Redis: cache precomputed lists with TTL (e.g., `recs:userId:contextA` → [item1, item2, ...]).
- User embeddings: Redis with RedisVector or a specialized store (Faiss in-process, Milvus). Allegro uses Faiss for ANN search on user embeddings.
- Hot users: cache the final reranked response for 60 seconds.

**5. Clickstream analytics → Druid (or ClickHouse)**
- Clickstream = billions of immutable, append-only events per day.
- Queries: "how many users clicked item X in the last 24h?" "CTR by category, hourly."
- Druid is a columnar OLAP store optimized for time-series aggregations with sub-second latency on TB datasets.
- Data ingested from Kafka in near-real-time.
- Alternative: ClickHouse (Yandex-built, columnar, also excellent for clickstream).
- NOT Cassandra for analytics — Cassandra's columnar model doesn't support aggregations efficiently.

**Operational cost:**
- Each store needs its own monitoring, backup, on-call runbook, upgrade cycle.
- The justification at Allegro's scale: performance gains vastly outweigh operational overhead, especially with a self-service platform (App Console) that templatizes new service setup.
- A small team should start with Postgres + Redis and add stores only when a clear bottleneck justifies it.

**Interview trap:** "Can you just use Cassandra for everything?" — Write-heavy? Yes. Ad-hoc analytical queries? No. Full-text search? No. Strong ACID? No. Using a single store across all workloads is a form of premature optimization in reverse — you build complex workarounds instead of using the right tool.

**Tags:** polyglot-persistence, allegro, architecture, mongodb, postgresql, elasticsearch, redis, druid, bounded-context

---

## Q-NOSQL-021 [bloom: analyze] [level: senior]
**Question:** Walk through the Cassandra read path. Why is reading harder than writing in Cassandra, and what tuning options exist?

**Model answer:** Cassandra's write path is optimized for throughput (sequential appends). Reading is harder because data for a partition can be spread across multiple SSTables, the memtable, and row caches.

**Read path step by step:**
1. Client sends read to coordinator.
2. Coordinator routes to replica nodes (based on partition key and consistency level).
3. **On each replica:**
   a. Check **row cache** (if enabled) — exact partition hit, fastest.
   b. Check **memtable** — most recent unflushed writes.
   c. Check **key cache** — stores partition key → SSTable offset mapping. Avoids full index scan.
   d. Consult **bloom filter** per SSTable — probabilistic check: "does this SSTable maybe contain this partition?" If bloom filter says no, skip the SSTable. False positives possible; false negatives are not.
   e. For SSTables not excluded by bloom filter: check the **partition index** and **summary index** to find the partition offset.
   f. Read the relevant data from the SSTable file.
4. **Read repair (background or foreground):** coordinator compares digests from replicas. If inconsistent (stale replica), it sends the latest write to lagging replicas.
5. Coordinator returns the merged/reconciled result.

**Why reading is harder:**
- Multiple SSTables (before compaction) means multiple disk reads per query — read amplification.
- Tombstones must be read and applied (delete markers scattered across SSTables). High tombstone density is a major read performance killer.
- Without compaction, an old partition key can require reading 10+ SSTables.

**Tuning options:**

| Lever | Effect | Trade-off |
|---|---|---|
| Compaction (STCS → LCS) | Reduces read amplification | Higher write amplification |
| Key cache (enabled by default) | Skip partition index scan | Memory usage |
| Row cache (disabled by default) | Serve entire partition from RAM | Memory intensive; invalidated on write to partition |
| Bloom filter FP rate (e.g., 0.01) | Fewer false positives, fewer SSTable probes | Larger bloom filter in memory |
| TTL on data | Prevents tombstone accumulation | Data expiry |
| `nodetool compact` / `nodetool cleanup` | Force compaction, remove stale data | I/O spike during execution |
| Read consistency level = ONE | Single replica, no coordination overhead | Stale reads possible |

**Tombstone danger:** if rows are frequently deleted (or expire via TTL creating implicit tombstones), and compaction hasn't run, a single read can encounter thousands of tombstones — triggering a `TombstoneOverwhelmingException` or severe read latency. Monitor `tombstone_scanned` metric.

**Interview trap:** "Should I enable the row cache?" — Rarely. Row cache is invalidated on any write to that partition. For write-heavy tables, it is constantly invalidated and wastes memory. Better to rely on OS page cache + key cache. Row cache is only useful for read-mostly, rarely-updated hot partitions.

**Tags:** cassandra, read-path, sstable, bloom-filter, key-cache, row-cache, tombstone, compaction, read-amplification

---

## Q-NOSQL-022 [bloom: analyze] [level: senior]
**Question:** What is Redis Cluster? How does it shard data, and what are its limitations compared to a single Redis instance?

**Model answer:** **Redis Cluster** provides horizontal scaling and automatic sharding across multiple Redis nodes without a proxy.

**Hash slots:**
- The keyspace is divided into **16,384 hash slots** (0–16383).
- Each master node owns a subset of slots: e.g., nodes A, B, C each own ~5461 slots.
- The slot for a key is computed as: `CRC16(key) mod 16384`.
- Clients are directed to the right node by the MOVED redirect response, or pre-loaded with the cluster map.

**Topology:**
- Each master has 1+ replica nodes (for HA).
- Cluster uses gossip protocol for node discovery and failure detection.
- Failover: if a master dies, one of its replicas is promoted (election, ~10-30s).

**Hash tags for co-location:**
- To ensure related keys hash to the same slot (required for MULTI/EXEC, pipelines, Lua scripts): use hash tags `{tag}`.
- `{user:123}:session` and `{user:123}:cart` both hash the `user:123` part → same slot → same node.

**Limitations vs single instance:**

| Feature | Single Redis | Redis Cluster |
|---|---|---|
| Multi-key commands | Any keys | Only keys in same slot |
| MULTI/EXEC (transactions) | Any keys | Same slot only |
| Pub/Sub | All nodes | Works but subscribe/publish must go to same node |
| Lua scripts | Any keys | Same slot only |
| SCAN | One call | Must SCAN all master nodes |
| Database index (`SELECT`) | 0–15 | Cluster only supports DB 0 |
| `KEYS *` | Works | Must query all nodes |

**When to use Cluster vs Sentinel:**
- **Sentinel:** HA for a single master + replicas. Automatic failover. Good for medium workloads. Clients use Sentinel-aware connection pool.
- **Cluster:** Sharding + HA. Required when data doesn't fit in one node or throughput exceeds single-node limits. More complex client requirements.
- **No sharding needed but HA needed?** Use Sentinel + read replicas.

**Interview trap:** "We use Redis MULTI/EXEC to atomically update two keys for a user. Will this work in Redis Cluster?" — Only if both keys hash to the same slot. Use hash tags: `{user:123}:key1` and `{user:123}:key2`. Without hash tags, you'll get a `CROSSSLOT` error.

**Tags:** redis-cluster, hash-slots, hash-tags, sentinel, sharding, crossslot, multi-exec, cluster-limitations

---

## Q-NOSQL-023 [bloom: analyze] [level: master]
**Question:** Deep-dive on CAP's partition tolerance assumption. Why is P not a choice in practice? What happens to a CP system during a partition — specifically, how does etcd (Raft-based) behave?

**Model answer:** **Why P is mandatory:**

Network partitions are not theoretical. In any distributed system:
- NIC failures, cable cuts, switch misconfigurations cause real partitions.
- GC pauses (especially in JVM systems) can cause a node to miss heartbeats, triggering an erroneous partition detection.
- Cloud VMs can be migrated, paused, or have network congestion.
- AWS, GCP, Azure all have had multi-AZ partition events in their history.

If you choose to sacrifice P (CA) — you're saying: "when there's a network partition, I'll shut down rather than risk inconsistency or unavailability." For a real distributed system, this means the system is unavailable whenever there's any network hiccup. That is almost always unacceptable.

Therefore: **every distributed system must be partition-tolerant**. The actual choice is: during a partition, do I prioritize Consistency or Availability?

**etcd (Raft-based, CP behavior) during a partition:**

etcd uses the **Raft consensus algorithm**:
- A cluster of N nodes (typically 5). A quorum is ⌊N/2⌋+1 = 3 nodes.
- Writes require acknowledgment from a quorum before committing.
- Reads from the leader are linearizable (strongly consistent).

**Partition scenario (5-node cluster, split 3-2):**
```
Partition A: node1 (leader), node2, node3  → quorum intact
Partition B: node4, node5                   → no quorum
```
- **Partition A (majority):** continues to elect a leader (or keeps existing leader), processes reads and writes normally. Fully consistent.
- **Partition B (minority):** cannot form quorum, cannot elect a leader, refuses all writes, **and by default also refuses reads** (to prevent serving stale data). Returns errors. **This is the CP choice — prefer error over stale data.**

If the partition heals, nodes in B reconnect, receive the missed log entries from A's leader, and catch up. No data divergence.

**Contrast with Cassandra (AP) during a partition:**
- Cassandra with `CL=ONE`: both sides of the partition keep accepting writes. After the partition heals, **conflict resolution** (last-write-wins by timestamp, or client-side merge) must reconcile diverged writes.
- This is the AP trade-off: availability preserved, but you might read stale or conflicting data.

**The PACELC addition:**
Even without a partition, etcd's quorum requirement adds latency: every write must cross the network to 2 additional nodes and get their ACK before committing. This is PACELC's EC/L trade-off — consistency at the cost of latency, even in the normal case.

**Interview trap:** "Can Raft have split-brain?" — Raft explicitly prevents split-brain. A leader can only be elected with a quorum majority. Two leaders simultaneously would require two separate quorums, impossible if `quorum = ⌊N/2⌋+1`. This is unlike old-style primary-secondary replication where split-brain was possible if heartbeats failed.

**Tags:** cap-theorem, partition-tolerance, raft, etcd, split-brain, consensus, cp-system, quorum, distributed-systems

---

## Q-NOSQL-024 [bloom: analyze] [level: master]
**Question:** Cassandra achieves "high availability" but it is technically an AP system. Explain the exact failure scenario where Cassandra serves inconsistent data despite AP guarantees being explicit, and what a developer must do to prevent the most dangerous class of inconsistency.

**Model answer:** **The scenario — unacknowledged write + eventual read:**

Setup: RF=3, two clients A and B, CL=ONE for both reads and writes.

1. Client A writes `balance = 500` to replicas R1, R2, R3. Coordinator gets ACK from R1, considers write successful. R2 and R3 haven't received the write yet (network delay).
2. Client B immediately reads from R3 — gets `balance = 400` (old value). Stale read.

This is expected and documented. The dangerous scenario is with **concurrent writes**:

3. Client A: `SET balance = 500` (timestamp T1)
4. Client B: `SET balance = 200` (timestamp T2, T2 > T1)
5. Due to network delays, R1 gets B's write first (200), then A's write (500 with older timestamp T1). **Last-Write-Wins (LWW) by timestamp means R1 ends up with 200 (correct).**

But: if clock drift exists between nodes (NTP not perfect), T1 might be numerically larger than T2 even though B wrote after A. **R1 ends up with 500 — B's write was silently overwritten.**

**The fundamental issue: Cassandra uses client timestamps (or server-assigned micros) for LWW conflict resolution. NTP clock skew of even a few milliseconds can cause lost writes with no error.**

**Danger class 1 — Lost update (concurrent writes):**
- Two clients read `balance = 100`, both add 50, both write `balance = 150`. One write is lost.
- Fix: **Lightweight Transactions (LWT)**: `UPDATE accounts SET balance = 150 WHERE id = ? IF balance = 100`. Uses Paxos — only one of the two will succeed; the other sees the current value and must retry.
- Cost: LWT is ~10x slower than a regular write (4 Paxos round-trips).

**Danger class 2 — Tombstone resurrection:**
- Delete a row with `CL=ONE`. One replica misses the tombstone (was down).
- Before `gc_grace_seconds` elapses, run a repair. Good.
- If you DON'T run repair and the replica comes back after `gc_grace_seconds` — the tombstone has been garbage-collected on the other replicas, but the replica has the old data. The old data "resurrects."
- Fix: run `nodetool repair` within `gc_grace_seconds` (default 10 days) on all nodes.

**Danger class 3 — Phantom read with counter tables:**
- Cassandra's counter columns are designed for distributed increment but are only "approximately" consistent. They use a distributed counter with conflict resolution, but under network partitions, counter increments can be lost.
- Fix: don't use Cassandra counters for exact financial counts. Use them only for approximate metrics.

**What developers must do:**
1. Use `CL=QUORUM` (or `LOCAL_QUORUM`) for any data where inconsistency has business impact.
2. Use LWT for check-and-set operations (balance updates, user registration uniqueness).
3. Set up regular `nodetool repair` jobs (weekly at minimum, daily recommended).
4. Monitor for clock drift on all Cassandra nodes.
5. Design data models to avoid patterns that require read-modify-write (they are inherently racy in distributed systems).

**Interview trap:** "Can I use Cassandra for a bank account balance?" — Only with LWT for every update (expensive) and `CL=QUORUM` for every read/write. At that point you're using Cassandra as an overpriced Postgres. The honest answer: use PostgreSQL for financial balances. Use Cassandra for event streams, activity logs, and other high-write, no-read-modify-write workloads.

**Tags:** cassandra, consistency, lost-update, lwt, tombstone-resurrection, clock-skew, last-write-wins, distributed-safety, repair

---

## Q-NOSQL-025 [bloom: analyze] [level: master]
**Question:** What are the consistency models between strong consistency and eventual consistency? Define causal consistency, read-your-writes, and monotonic reads — and give an example of when each matters in a production system.

**Model answer:** The consistency spectrum from strongest to weakest:

```
Linearizability (strict) > Sequential > Causal > Read-your-writes + Monotonic > Eventual
```

**Linearizability (strong consistency):**
- All operations appear to execute instantaneously at some point between invocation and completion.
- External observer sees operations in real-time order.
- Example: etcd reads from leader; Postgres with `SERIALIZABLE` isolation.
- Cost: requires quorum coordination on every operation — high latency.

**Causal consistency:**
- Operations that are causally related (one depends on another) are seen by all processes in the same order. Concurrent (causally independent) operations may be seen in different orders.
- Example: social media feed. If User A comments on User B's post, User C should see the original post BEFORE the comment. Without causal consistency, C might see the comment referencing a post that hasn't arrived yet.
- Implementation: vector clocks or version vectors tracking causal dependencies.
- MongoDB's causally consistent sessions (using `clusterTime` and `operationTime`) implement this.

**Read-your-writes (session consistency):**
- After a client writes a value, subsequent reads by the SAME client will see that write (or a later one).
- Does not guarantee that other clients see the write immediately.
- Example: user changes their profile picture. They immediately navigate to their profile — they must see the new picture, not the old one. Other users can see the old one briefly.
- Implementation: after a write, the client includes a "read-after" token (timestamp/LSN). Reads are routed to a replica that has applied at least that LSN. MongoDB's `afterClusterTime` implements this.

**Monotonic reads:**
- If a client reads a value at time T, all subsequent reads by the same client will see the same or a more recent value. You never read an older version than you previously read.
- Example: without monotonic reads, a user reloads a page and sees fewer comments than they saw a second ago (because a different replica was selected with older data). Confusing and broken UX.
- Implementation: sticky sessions (always route same client to same replica), or track last-seen version and only read from replicas that have caught up.

**Monotonic writes:**
- Writes by the same client are applied in order on all replicas.
- Example: a user sends two messages in sequence. Without monotonic writes, the second message could appear before the first on some replicas.

**Production relevance:**
- **Read-your-writes** is almost always required for user-facing mutation APIs. Most session-aware databases (MongoDB, DynamoDB with sessions, Postgres replicas with LSN tracking) support this.
- **Causal consistency** is important for collaborative features, event-sourced systems, and any "you need to see X before you can see Y" relationship.
- **Monotonic reads** is essential for any paginated or real-time UI. Without it, users see data "jump backward" which breaks trust in the product.

**Interview trap:** "Is read-your-writes the same as strong consistency?" — No. Read-your-writes only guarantees the writing client sees its own writes. Other clients can still see stale data. It is a much weaker guarantee that can be implemented cheaply (route reads to primary for a window after write, then to any replica).

**Tags:** consistency-models, causal-consistency, read-your-writes, monotonic-reads, session-consistency, mongodb, distributed-systems

---

## Q-NOSQL-026 [bloom: analyze] [level: master]
**Question:** You are designing the data layer for a multi-tenant e-commerce platform expecting 100k writes/sec to order events, 500k reads/sec to product catalog (read-heavy, eventual consistency OK), and 10k writes/sec to financial ledger (strong consistency, ACID). Propose the storage architecture with specific products, replication factors, consistency levels, and failure handling.

**Model answer:** **Architecture overview:**

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                      │
└──────────┬───────────────┬──────────────────┬────────────┘
           │               │                  │
   Order Events        Product Catalog    Financial Ledger
           │               │                  │
    Cassandra 5-node    Redis (L1)         PostgreSQL
    RF=3, LOCAL_QUORUM  Mongo (L2)         + Citus sharding
                        RF=3               Primary + 2 sync
                                           replicas
```

**1. Order Events → Cassandra**
- 100k writes/sec: Cassandra's append-only write path handles this trivially. With 5 nodes, each node sees ~20k writes/sec — well within limits.
- Schema: `PRIMARY KEY ((tenant_id, date_bucket), event_ts, order_id)` — partition by tenant + day (bounded partition size), cluster by timestamp.
- Consistency: `LOCAL_QUORUM` for writes (RF=3, quorum=2). Reads can be `ONE` or `LOCAL_ONE` for analytics; `LOCAL_QUORUM` for order status checks.
- Compaction: TWCS (time-window) — daily buckets naturally map to time windows.
- Failure: losing 1 node (RF=3, LOCAL_QUORUM) still works. Hinted handoff queues writes for the failed node for up to `max_hint_window_in_ms` (3 hours default).

**2. Product Catalog → Redis (L1) + MongoDB (L2)**
- 500k reads/sec of product data: Redis as L1 cache (TTL 60s, allkeys-LRU eviction). Hot products always in memory.
- Cache miss → MongoDB (source of truth). RF=3 (`w: majority` on writes, `readPreference: secondaryPreferred` on reads).
- MongoDB sharding: shard key = `{categoryId: "hashed"}` — even distribution, reads usually by productId (targeted).
- Cache invalidation: on product update, write to MongoDB with `w: majority`, then invalidate Redis key. Or use CDC (Debezium → Kafka → cache invalidation consumer) for eventual cache consistency.
- Failure: Redis cluster down → fallback to MongoDB directly (higher latency but correct). Add circuit breaker: if MongoDB p99 > 50ms, serve stale cache for up to 5 minutes.

**3. Financial Ledger → PostgreSQL + Citus**
- 10k writes/sec: single Postgres primary handles ~5-10k TPS for simple INSERT/UPDATE. Fine for now.
- ACID critical: `BEGIN/COMMIT` around every balance operation. Optimistic locking or `SELECT ... FOR UPDATE` for concurrent balance updates.
- Strong consistency: `synchronous_commit = on`, synchronous standby with `synchronous_standby_names` for at least 1 replica (semi-sync). RPO near-zero.
- If 10k/sec exceeds single-primary capacity: Citus for horizontal sharding (shard by `tenant_id`). Each tenant's ledger is on one shard — all ACID guarantees preserved within-shard.
- Failure: with synchronous replica, primary failure = promote replica (no data loss). Patroni + etcd manages automatic failover. RTO ~30 seconds.

**Cross-cutting concerns:**
- **Dual-write:** catalog writes go Mongo + invalidate Redis. Order writes go Cassandra only (no secondary sync needed). Ledger writes go Postgres only.
- **Observability:** per-store Prometheus metrics: Cassandra write latency + pending compaction; Redis hit rate + eviction rate; Postgres lock wait + transaction throughput.
- **Disaster recovery:** Cassandra: multi-DC replication factor; MongoDB: cross-region replica; Postgres: async standby in second region with WAL shipping (RPO ~seconds, tolerable for business audit, not for real-time ops).

**Interview trap:** "Why not put the ledger in Cassandra with LWT?" — LWT is Paxos-based, slower than Postgres transactions, and lacks multi-entity atomicity. Cassandra doesn't support joins or cross-partition transactions. Using it for financial data means building a transaction coordinator on top — reinventing PostgreSQL badly. Use the right tool.

**Tags:** system-design, polyglot-persistence, cassandra, mongodb, redis, postgresql, consistency-levels, replication, failure-handling, multi-tenant

---

## Q-NOSQL-027 [bloom: analyze] [level: master]
**Question:** Explain Elasticsearch's near-real-time (NRT) semantics in detail. What is the refresh cycle, what is the translog, and what happens to data during a node crash before a flush?

**Model answer:** Elasticsearch (built on Lucene) has a layered durability model that separates **searchability** from **durability**.

**Lucene segments and the NRT gap:**
- When a document is indexed, it goes into an in-memory buffer (the "indexing buffer").
- A **refresh** (default: every 1 second) creates a new **Lucene segment** from the buffer and opens it for search. The segment is searchable but NOT yet on disk.
- **This is the NRT gap:** after indexing a document, it may take up to 1 second (the refresh interval) before it's visible in search results.
- Refresh is cheap (in-memory segment creation) but not free — lowering `refresh_interval` to 100ms increases CPU/memory pressure.

**Lucene flush (fsync to disk):**
- Eventually, in-memory segments are written to OS filesystem pages and fsynced to disk. This is the **Lucene commit** (or "flush" in ES terminology). Very expensive.
- Without flushing, a process crash would lose all in-memory segments.

**Translog (the durability mechanism):**
- For every indexing operation, ES also appends to the **translog** (transaction log) — a file that IS fsynced to disk (configurable frequency).
- Default `index.translog.durability = request` — fsync translog on every index/delete request. This guarantees no data loss if the ES process crashes.
- `index.translog.durability = async` (with `sync_interval`) — faster, but can lose last `sync_interval` of data on crash. Use only for search indexes with a durable source of truth.

**Full flush cycle:**
```
Index request → in-memory buffer → [refresh: ~1s] → in-memory segment (searchable)
                                                     ↓ (also)
               → translog append → fsync (per request or async)
                                     ↓ (periodically or when translog hits size limit)
                                     Lucene commit (merge + fsync segments to disk)
                                     Translog truncated (no longer needed for recovery)
```

**Crash recovery:**
1. ES restarts.
2. Loads the last committed Lucene segments from disk.
3. Replays the translog entries that occurred since the last Lucene commit.
4. All data from the translog is restored — no data loss (if `durability = request`).
5. In-memory segments that hadn't been refreshed yet ARE lost — these were never in the translog (translog is written at index time, not at refresh time). This is the known limitation: documents can be indexed (in translog) but not yet searchable, so they won't appear in results until refresh runs.

**Controlling the behavior:**
- `refresh_interval: -1` — disable automatic refresh (for bulk ingest; manually call `POST /_refresh` after load).
- `index.translog.flush_threshold_size: 512mb` — trigger Lucene commit when translog hits this size.
- `?refresh=true` query param on index API — force immediate refresh for this document (expensive; for testing only).
- `?refresh=wait_for` — wait until next refresh cycle, then return. Better than `true` for production.

**Interview trap:** "If I index a document and immediately search for it, will I find it?" — Not necessarily. Default refresh is 1 second. Use `?refresh=wait_for` if your use case requires immediate searchability. Don't use `?refresh=true` under load — it forces a new segment on every write, destroying Elasticsearch's merge optimization.

**Tags:** elasticsearch, nrt, refresh, translog, lucene, durability, crash-recovery, segment, flush

---

## Q-NOSQL-028 [bloom: analyze] [level: master]
**Question:** Describe the Redlock algorithm for distributed locking with Redis. What are its assumptions, failure modes, and why do some distributed systems experts (Kleppmann) argue it is unsafe?

**Model answer:** **Redlock** is Antirez's (Redis creator) algorithm for distributed locking across multiple Redis instances. It was designed to solve the single-point-of-failure problem of locking with a single Redis master.

**Algorithm (N = 5 independent Redis instances):**
1. Record start time `T0`.
2. Try to `SET lock_key unique_value NX PX ttl_ms` on all 5 instances, with a small timeout per attempt (e.g., 5ms).
3. Count successful acquisitions. If >= 3 (majority, N/2+1):
   - Compute elapsed time: `drift = now - T0`.
   - If `drift < ttl_ms - clock_drift_correction`, the lock is valid. Lock validity = `ttl_ms - drift`.
4. If fewer than 3 successes (or lock validity is negative): release all acquired locks and retry with backoff.
5. On lock release: `DEL lock_key` using a Lua script that checks the unique_value (prevents releasing another client's lock).

**The purpose of N independent instances:** if one master fails, two others still hold the quorum. No single point of failure.

**Martin Kleppmann's critique (2016, "How to do distributed locking"):**

**Problem 1 — Clock assumptions:**
Redlock relies on TTL for safety. TTL is measured in wall-clock time. If a Redis node has clock skew (e.g., NTP adjusts the clock forward), the TTL effectively shortens or lengthens unpredictably. Kleppmann argues that process pauses (GC, VM migration) can also cause a client to hold a lock but be paused longer than the TTL — the lock expires, another client acquires it, and then the paused client wakes up and proceeds believing it still holds the lock.

**Problem 2 — No fencing token:**
Even if the lock is acquired correctly, a process pause after lock acquisition but before the protected operation means two clients can execute the critical section "simultaneously." The solution (fencing token): a monotonically increasing token issued with the lock; the backend storage must reject writes with a token lower than the last seen. Redlock doesn't provide this.

**Problem 3 — Delayed messages:**
In an asynchronous network, a lock acquisition message can be delayed. By the time it arrives at the locked resource, the lock has expired.

**Antirez's response:** These scenarios require severe, unusual failures; Redlock is "good enough" for most practical distributed locking needs. The debate continues.

**Practical guidance:**
- **Use Redlock for:** best-effort distributed coordination where lock correctness is "mostly important" — distributed cron, leader election hints.
- **Do NOT use Redlock for:** anything where two clients entering the critical section simultaneously would cause data corruption or financial loss (e.g., "charge the customer once"). Use a fencing-token-aware store (ZooKeeper/etcd with distributed locks using `compare-and-swap`) for strong safety.
- In Java: **Redisson** implements Redlock (and its own single-instance lock with Lua atomicity). For strong guarantees, use Curator (ZooKeeper) or etcd client.

**Interview trap:** "Is Redlock safe for distributed transactions?" — No. It's advisory coordination, not a transaction protocol. Even if the lock is held, the actual writes to datastores are not atomic. Combine with idempotency keys and optimistic concurrency in the underlying stores.

**Tags:** redis, redlock, distributed-locking, kleppmann, fencing-token, clock-skew, gc-pause, distributed-systems, redisson

