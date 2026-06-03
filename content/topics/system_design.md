# System Design — question bank

> Covers the canonical senior backend system design interview: how to structure an answer, how to scale read-heavy and write-heavy systems, and how to defend every trade-off under pressure. Context: you are a 5-year backend engineer targeting a senior role at a company like Allegro — you will face open-ended design questions (notifications, rate limiting, recommendations serving, URL shorteners, feed systems) with a 45-minute time box. The interviewer grades on reasoning and trade-offs, not on finding the "right" answer. This bank trains you to drive the conversation, name numbers, and anticipate gotchas.

## Scope

- Interview framework: clarify → estimate → API → data model → high-level design → deep dive → bottlenecks
- Back-of-the-envelope estimation: QPS, storage, bandwidth, server count
- Latency reference numbers every senior engineer must know
- Scalability: vertical vs horizontal, statelessness, scale-out patterns
- Load balancing: L4 vs L7, algorithms (round-robin, least-connections, consistent hash), health checks, sticky sessions
- Caching layers: client, CDN, application, database; cache-aside vs read-through vs write-through vs write-behind
- Cache invalidation, eviction policies (LRU, LFU, TTL), thundering herd / cache stampede and mitigations
- Redis: data structures, pub/sub, Lua scripts, cluster, persistence modes (RDB vs AOF)
- Database scaling: read replicas, sharding (hash, range, directory), hot partitions, resharding, consistent hashing
- CAP theorem: CP vs AP, PACELC, eventual consistency in practice
- Message queues for decoupling: at-least-once, idempotency, dead-letter queues, backpressure
- Microservices resilience: Circuit Breaker, Bulkhead (Resilience4j), Saga (choreography vs orchestration), Transactional Outbox
- Rate limiting: token bucket, leaky bucket, sliding window log, fixed window counter, distributed rate limiting with Redis
- Idempotency: why it matters, idempotency keys, exactly-once semantics
- CDN: origin pull, push model, cache-control headers, cache purge
- SLA / SLO / SLI definitions and error budgets
- Tail latency: p50/p95/p99/p999, hedged requests, timeout strategies
- Leader election: ZooKeeper, etcd, Raft consensus basics
- Observability: metrics (RED/USE), distributed tracing, structured logging, alerting
- Worked example: recommendation serving path (~20k RPS, p99 ~40ms, Faiss ANN)

---

## Q-SD-001 [bloom: recall] [level: junior]
**Question:** What is the standard framework for answering a system design interview question? List the phases in order.
**Model answer:** A repeatable structure that senior engineers use to drive a 45-minute design session:

1. **Clarify requirements (5 min):** ask about scale, consistency, availability, latency requirements, known constraints. "Is this read-heavy or write-heavy?" "Do we need strong consistency or is eventual OK?" "What's the expected QPS / DAU?" Write answers on the whiteboard — they become your constraints.

2. **Capacity / back-of-the-envelope (5 min):** estimate QPS, storage/day, bandwidth. Round aggressively. Purpose: drives architectural choices (do we need sharding? CDN?).

3. **Define the API (5 min):** concrete endpoint signatures or message formats. Drives the data model. "What does the client send? What does it receive?"

4. **Data model (5 min):** entities, relationships, primary keys, access patterns. Pick a storage engine and justify it (RDBMS vs NoSQL vs blob).

5. **High-level design (10 min):** box-and-arrow diagram. Client → LB → app servers → DB, with caching and queuing as needed.

6. **Deep dive (10 min):** pick 1-2 hard parts the interviewer cares about. Nail the internals.

7. **Bottlenecks and trade-offs (5 min):** where does the system fail? What are you trading off? What would you do differently at 10x scale?

The framework is not a script — it's a checklist so you don't skip anything. Interviewers value candidates who drive the session, not wait for prompts.
**Interview trap:** "Can we just start drawing boxes?" — If you skip requirements clarification and get the scale wrong, you'll design a toy. Interviewers penalize this. Always clarify first, even for 2 minutes.
**Tags:** interview-framework, methodology, requirements, estimation

---

## Q-SD-002 [bloom: recall] [level: junior]
**Question:** What are back-of-the-envelope estimation techniques? Walk through estimating QPS, storage, and bandwidth for a system with 10 million DAU, each performing 5 reads and 1 write per day.
**Model answer:** Back-of-the-envelope uses rough constants to check if a design is in the right ballpark. Key numbers to have memorized:

- 1 day ≈ 86,400 seconds ≈ 10^5 seconds (close enough for estimates)
- 1 million requests/day ÷ 10^5 sec = ~10 RPS
- 1 billion/day ÷ 10^5 = ~10,000 RPS

**Example — 10M DAU:**
- **Read QPS:** 10M users × 5 reads/day = 50M reads/day ÷ 86,400 ≈ **580 read RPS** (call it ~600). Peak is typically 2-3× average → peak ~1,800 RPS.
- **Write QPS:** 10M × 1 write/day ÷ 86,400 ≈ **115 write RPS** → peak ~350 RPS.
- **Storage:** assume 1KB per write → 10M writes/day × 1KB = 10GB/day. Over 5 years: 10GB × 365 × 5 ≈ **18TB**. That's a partitioned DB problem, not a single-instance problem.
- **Bandwidth:** assume 10KB response per read → 600 RPS × 10KB = 6MB/s egress. CDN-worthy at scale.

**Why it matters:** 600 read RPS fits on a single well-tuned server. 600K read RPS does not — you need horizontal scaling and caching. The estimate drives the architecture.
**Interview trap:** "Isn't this too rough?" — Yes, and that's the point. The goal is to identify the order of magnitude (single server vs cluster vs multi-region). Never waste interview time on false precision.
**Tags:** estimation, qps, storage, bandwidth, back-of-the-envelope

---

## Q-SD-003 [bloom: recall] [level: junior]
**Question:** What latency numbers should every backend engineer have memorized? Give approximate values for common operations.
**Model answer:** Jeff Dean's famous latency numbers (approximate, 2023-era hardware — trends hold):

| Operation | Latency |
|---|---|
| L1 cache reference | 1 ns |
| L2 cache reference | 4 ns |
| RAM reference | 100 ns |
| Read 1MB sequentially from RAM | 250 µs |
| SSD random read | 100 µs |
| SSD sequential read (1MB) | 1 ms |
| HDD seek | 10 ms |
| Round trip within same datacenter | 0.5 ms |
| Round trip across datacenters (US–EU) | 150 ms |
| Read 1MB over 1Gbps network | 10 ms |

**Practical implications:**
- Redis (in-memory, same DC): ~0.5ms RTT. Fits inside a 40ms p99 budget with room to spare.
- PostgreSQL synchronous replication cross-DC: adds 150ms — use async replication for cross-region.
- Disk-backed DB without cache: 10ms+ per random access → need caching or SSDs for low latency paths.
- A request bouncing through 5 services × 0.5ms each = 2.5ms in pure network overhead. At 10 hops = 5ms. Factor this into SLA math.
**Interview trap:** "Does it matter that these are approximations?" — For design interviews, no. The ratio between operations matters more than exact values. Knowing RAM is 1000x faster than disk is what drives "put a cache in front of the DB."
**Tags:** latency, performance, numbers, memory-hierarchy

---

## Q-SD-004 [bloom: recall] [level: junior]
**Question:** What is the difference between SLA, SLO, and SLI?
**Model answer:** These are three levels of the same reliability concept, from abstract to concrete:

**SLI (Service Level Indicator):** a quantitative measure of service behavior. Raw metric. Examples: request success rate, p99 latency, error rate, availability (uptime fraction).

**SLO (Service Level Objective):** a target value for an SLI. An internal commitment. Examples: "p99 latency < 200ms", "error rate < 0.1%", "availability ≥ 99.9%". SLOs are what your team is held to. Breaching an SLO triggers an incident and consumes error budget.

**SLA (Service Level Agreement):** a contractual commitment to customers, backed by financial penalties. SLA is typically less strict than SLO — you want headroom so internal breaches don't immediately breach the customer contract.

**Error budget:** `error_budget = 1 - SLO`. A 99.9% availability SLO gives you 0.1% = ~8.7 hours/year of allowed downtime. It's a deployment/change velocity currency — if you're spending it too fast, freeze deployments.

Example relationship:
- SLI: measured p99 = 85ms
- SLO: p99 ≤ 100ms (internal target)
- SLA: p99 ≤ 200ms (customer-facing contract)
**Interview trap:** "Is 99.9% availability enough?" — depends on the business. 99.9% = 8.7h downtime/year. 99.99% = 52min/year. 99.999% = 5min/year. Five-9s requires multi-region active-active, auto-failover in under 30 seconds — expensive. Ask: what does 1 hour of downtime cost the business?
**Tags:** sla, slo, sli, reliability, error-budget, availability

---

## Q-SD-005 [bloom: recall] [level: junior]
**Question:** What is the difference between vertical scaling and horizontal scaling? When does each apply?
**Model answer:** **Vertical scaling (scale up):** add more resources (CPU, RAM, SSD) to the same machine. Simpler — no code changes, no distributed system complexity. Hard limit: the biggest machine available (and it's expensive). Single point of failure unless you also add redundancy.

**Horizontal scaling (scale out):** add more machines and distribute load across them. Cheaper at scale (commodity hardware), no theoretical ceiling, built-in redundancy. Requires: statelessness in application tier (so any request can hit any server), a load balancer, distributed state management (sessions in Redis, not in-process).

**Statelessness rule:** for horizontal scaling to work, each application server must be interchangeable. No local session storage, no local cache that diverges between instances. Sticky sessions are an anti-pattern for scale — they tie users to specific servers, making rolling deployments and failover messy.

**When to choose:**
- **Vertical first** — fast path, no code changes. Works until DB or app reaches a size/cost inflection.
- **Horizontal next** — once vertical hits the ceiling (cost, machine limit), or when you need fault tolerance, or when throughput exceeds what a single machine can saturate a network card at.
- **Database** — reads scale horizontally with read replicas; writes are harder (sharding, or vertical for as long as possible since write scaling is complex).
**Interview trap:** "Can I just keep scaling vertically?" — For application tier, maybe, but databases have a write throughput ceiling. A 128-core machine with NVMe doesn't give you 128x the IOPS of a 1-core machine. And it's a SPOF. At some scale, horizontal is unavoidable.
**Tags:** scalability, vertical-scaling, horizontal-scaling, statelessness

---

## Q-SD-006 [bloom: recall] [level: junior]
**Question:** What is a CDN and what types of content should (and should not) go through it?
**Model answer:** A **Content Delivery Network** is a geographically distributed network of edge servers that cache content close to users, reducing origin load and latency.

**How it works:** DNS resolves the CDN domain to the nearest edge PoP (Point of Presence). Edge checks its cache:
- **Cache hit:** serve from edge (fast, low latency).
- **Cache miss (origin pull):** edge fetches from origin, caches it, serves to user. Subsequent requests hit the cache.

**Content types — CDN appropriate:**
- Static assets: JS bundles, CSS, images, fonts, videos
- Publicly cacheable API responses (product pages, catalog browsing — read-only, same for all users)
- Large file downloads

**CDN not appropriate (or requires care):**
- User-specific content (personalized recommendations, account data) — can be cached with cache key including user ID, but risk of cache poisoning / cross-user data leaks
- Highly dynamic data (live stock levels, prices during a flash sale)
- Write requests (POST/PUT/DELETE) — route these directly to origin

**Cache-Control headers** drive CDN behavior: `Cache-Control: max-age=3600, s-maxage=86400` — browser caches 1h, CDN caches 24h. `Cache-Control: private` — browsers can cache but CDN must not.

**Cache invalidation at CDN:** either set short `max-age` (simple, but stale window exists) or purge via CDN API (instant but adds operational complexity for every deploy).
**Interview trap:** "Can you cache personalized content on CDN?" — Technically yes, using `Vary` header or including user identifier in cache key. But at scale this explodes cache key cardinality and fills the CDN with effectively private content. Better pattern: cache the shell (HTML) on CDN, fetch personalized data client-side via API.
**Tags:** cdn, caching, static-assets, cache-control, edge

---

## Q-SD-007 [bloom: understand] [level: regular]
**Question:** Explain cache-aside, read-through, write-through, and write-behind caching strategies. When would you use each?
**Model answer:** Four patterns for how application and cache interact:

**Cache-aside (lazy loading):**
```
read(key):
  val = cache.get(key)
  if val == null:
    val = db.get(key)
    cache.set(key, val, TTL)
  return val
```
Application manages the cache explicitly. Cache is populated on misses, so cold start has no warming. Resilient to cache failures (fall through to DB). Most common in practice (Redis + application code). Downside: first read after expiry hits the DB (cold start on TTL expiry).

**Read-through:** cache sits in front of DB, handles the DB fetch itself. Application only talks to cache. Simpler application code but couples cache to DB schema. Less common as a standalone product — some ORMs/caches support it.

**Write-through:**
```
write(key, val):
  db.write(key, val)
  cache.set(key, val)
```
Every write goes to DB **and** cache atomically. Cache always consistent with DB. Downside: writes are slower (two writes per operation). Cache gets populated with data that may never be read (wasteful if write-heavy with few re-reads).

**Write-behind (write-back):**
```
write(key, val):
  cache.set(key, val)
  queue.publish(WriteEvent(key, val))   // async DB write
```
Write is acknowledged immediately after cache update. DB updated asynchronously. Very fast writes. Dangerous: if cache node dies before async flush, data is lost. Use only when eventual consistency is acceptable (view counts, analytics).

**Choosing:**
- Read-heavy, tolerate brief staleness → cache-aside with TTL
- Must always serve fresh data from cache → write-through
- Ultra-high write throughput, can lose some data → write-behind
- Simple code, heavy reads → read-through
**Interview trap:** "Write-through guarantees consistency, right?" — Not perfectly. There's a race: if the DB write succeeds but the cache write fails, or vice versa, they're inconsistent. Fully consistent write-through requires distributed transactions. In practice you accept this small window and use short TTLs as a safety net.
**Tags:** caching, cache-aside, write-through, write-behind, read-through, redis

---

## Q-SD-008 [bloom: understand] [level: regular]
**Question:** What is the thundering herd problem (cache stampede)? When does it happen and what are the standard mitigations?
**Model answer:** **Cache stampede** (thundering herd) occurs when a popular cache entry expires and many concurrent requests simultaneously hit the DB to re-populate it. If 10,000 RPS are hitting the same cached key and it expires, you get 10,000 simultaneous DB queries — the DB falls over before any of them can write the result back to cache.

**When it happens:**
- High-traffic key with a fixed TTL (all instances expire at the same moment)
- After a cache flush / restart
- Cold cache after a deploy

**Mitigations:**

1. **Mutex / distributed lock:** first thread to see a miss acquires a lock (Redis `SET NX EX`), fetches from DB, populates cache, releases lock. Other threads wait or return stale. Simple but adds latency for waiting threads.

2. **Probabilistic early expiry (XFetch algorithm):** before TTL actually expires, start a race to refresh with probability that increases as expiry approaches. Staggered refreshes prevent the cliff. Formula: `time_to_expire < beta * delta * ln(rand())` where delta is compute time of value. No lock needed.

3. **Background refresh:** cache layer tracks TTL; before expiry, a background thread refreshes the entry. Client always gets cached value, never a miss. Requires the cache layer to know how to recompute the value.

4. **Jitter on TTL:** instead of `TTL=3600`, use `TTL = 3600 + random(-300, 300)`. Staggered expiry across replicas/pods. Cheap but doesn't eliminate the problem entirely.

5. **Stale-while-revalidate:** serve stale data immediately, trigger async refresh. HTTP `Cache-Control: stale-while-revalidate=60`. Acceptable when brief staleness is tolerable.

**In Redis specifically:** use `SET key value EX 3600 NX` as a primitive for lock-based refresh. Keep lock TTL short (= expected DB query time × 2) so it auto-releases on crash.
**Interview trap:** "Just increase the TTL." — That delays the problem, doesn't solve it. The stampede still happens when the longer TTL expires, just less frequently. The spike will be larger because more changes accumulate. TTL tuning ≠ stampede mitigation.
**Tags:** thundering-herd, cache-stampede, redis, ttl, mitigation, caching

---

## Q-SD-009 [bloom: understand] [level: regular]
**Question:** Compare LRU and LFU cache eviction policies. When does each fail, and when would you use a more sophisticated alternative?
**Model answer:** **LRU (Least Recently Used):** evicts the item that was accessed longest ago. Assumes recency = future value. Implemented as a doubly-linked list + hash map: O(1) get and put.

**LFU (Least Frequently Used):** evicts the item accessed the fewest times. Assumes frequency = future value. More complex to implement efficiently (min-heap or frequency buckets). Retains "evergreen" content that's accessed constantly.

**Where LRU fails:**
- **Scan pollution:** a large sequential scan (e.g., batch job reading 10M rows) evicts hot entries because every new row is the "most recent." Seen in PostgreSQL buffer cache when full table scans compete with hot OLTP queries.
- **Temporal shift:** a key that was hot yesterday and cold today stays in cache because it was accessed frequently in the past.

**Where LFU fails:**
- **Cold start for new keys:** a newly cached item that will be very popular starts with frequency=1 and is immediately evictable.
- **Frequency decay:** a viral item from last week holds its frequency count even after it goes cold.

**Better alternatives:**
- **LFU with decay / W-TinyLFU (used in Caffeine and Redis 4.0+):** maintains a frequency sketch (count-min sketch) per item, but applies time decay so old frequency counts fade. Caffeine (Java) uses W-TinyLFU and is 2-3x better hit rate than LRU on real-world workloads.
- **ARC (Adaptive Replacement Cache):** balances LRU and LFU dynamically. Used in ZFS.
- **TTL + LRU combination:** most practical. Set a TTL so stale entries expire regardless of access pattern, and LRU handles memory pressure.

**Redis eviction policies:** `volatile-lru`, `volatile-lfu`, `volatile-ttl`, `allkeys-lru`, `allkeys-lfu`, `noeviction` (throws on full). `allkeys-lfu` is recommended for general caching; `volatile-lru` when only TTL'd keys should be evictable.
**Interview trap:** "Is Redis LFU exact?" — No. Redis uses a Morris counter approximation (the `lfu_log_factor` config). It samples `maxmemory-samples` keys (default 5) and evicts the one with the lowest approximate frequency. Trade-off: O(1) eviction cost, ~1-2% accuracy loss.
**Tags:** lru, lfu, eviction, cache, redis, caffeine, w-tinylfu

---

## Q-SD-010 [bloom: understand] [level: regular]
**Question:** Explain L4 vs L7 load balancing. What are the trade-offs, and what algorithms (round-robin, least-connections, consistent hash) are used?
**Model answer:** **L4 load balancing** operates at the transport layer (TCP/UDP). Decisions are made on IP address + port, without inspecting HTTP headers or body. Fast (minimal processing), transparent to the protocol. Cannot route based on URL path, host header, or cookies. Examples: AWS NLB, HAProxy in TCP mode, LVS.

**L7 load balancing** operates at the application layer (HTTP/HTTPS). Can inspect headers, URL, cookies, body. Enables: host-based routing, path-based routing, SSL termination, sticky sessions by cookie, A/B traffic splitting by header, gRPC load balancing. More CPU intensive. Examples: Nginx, HAProxy in HTTP mode, AWS ALB, Envoy.

**Algorithms:**

| Algorithm | How it works | Best for |
|---|---|---|
| Round-robin | Requests distributed cyclically | Uniform-size requests, identical servers |
| Weighted round-robin | Same, but servers get weight proportional to capacity | Heterogeneous servers |
| Least connections | Route to server with fewest active connections | Long-lived connections, variable request time |
| Least response time | Route to server with lowest avg response time | Latency-sensitive |
| IP hash | Hash(client_IP) → server | Sticky sessions without cookies |
| Consistent hash | Hash(request_key) → server ring | Cache locality (same key → same server) |
| Random | Pick server at random | Simple, surprisingly effective for stateless services |

**Health checks:** LBs poll backend `/health` (or TCP SYN). Remove unhealthy servers from rotation. Types: passive (watch real traffic error rates) + active (periodic health check calls). Active is faster to detect failures but adds load.

**Sticky sessions:** bind a client to a specific server (IP hash or session cookie). Needed when server holds session state locally. Breaks horizontal scaling cleanly — avoid by externalizing state to Redis.
**Interview trap:** "Why does consistent hashing matter for caching?" — With round-robin, adding or removing a cache server causes almost all keys to be remapped → mass cache miss. With consistent hashing, only K/N keys move (where K=keys, N=nodes). Essential for distributed caches and sharded DBs.
**Tags:** load-balancing, l4, l7, round-robin, consistent-hashing, health-checks, sticky-sessions

---

## Q-SD-011 [bloom: understand] [level: regular]
**Question:** Explain rate limiting algorithms: token bucket, leaky bucket, fixed window counter, and sliding window log. Which is best for a public API?
**Model answer:** Four standard algorithms with distinct characteristics:

**Fixed window counter:** divide time into buckets (e.g., 1 minute). Count requests per bucket per client. Allow ≤ N per bucket. Simple, O(1) storage. Fatal flaw: a client can send N at 00:59 and N at 01:00 for 2N requests in a 2-second window at the boundary.

**Sliding window log:** maintain a timestamp log of all requests per client. Count entries within `[now - window, now]`. Accurate, no boundary spike. Cost: O(requests_per_window) memory per client — expensive at scale.

**Sliding window counter (hybrid):** count for current window + weighted count from previous window. `rate = current_count + prev_count × (1 - elapsed_in_current_window / window_size)`. Approximation of sliding window log with O(1) storage. Used by Cloudflare.

**Token bucket:** each client gets a bucket with capacity B tokens. N tokens added per second (refill rate). Each request consumes 1 token. If bucket empty → reject. Allows bursts up to B (the bucket size). Natural for "allow burst, then sustain at rate".

**Leaky bucket:** requests enter a queue (the bucket). Processed at a fixed rate (the leak). Excess requests dropped. Smooths output rate — no bursts. Good for protecting downstream that needs steady throughput.

**For a public REST API — token bucket or sliding window counter:**
- Token bucket: allows clients to burst (good UX — front-loading acceptable), sustain at defined rate, simple to implement in Redis:
```lua
-- Redis Lua: atomic token bucket
local tokens = tonumber(redis.call('GET', KEYS[1]) or ARGV[1])
if tokens > 0 then
  redis.call('SET', KEYS[1], tokens - 1, 'EX', ARGV[2])
  return 1  -- allowed
end
return 0  -- rejected
```
- Spring Cloud Gateway has `RedisRateLimiter` built-in using token bucket semantics: `replenishRate` (steady state) and `burstCapacity`.

**Response:** HTTP 429 Too Many Requests + `Retry-After: <seconds>` + `X-RateLimit-Remaining` header.
**Interview trap:** "Where do you store rate limit counters?" — In Redis, not in-process. Multiple app server instances share the same counters. In-process = each instance allows N requests, total = N × servers. Use Redis INCR + EXPIRE for atomic operations.
**Tags:** rate-limiting, token-bucket, leaky-bucket, sliding-window, redis, spring-cloud-gateway

---

## Q-SD-012 [bloom: understand] [level: regular]
**Question:** What is idempotency and why is it critical in distributed systems? How do you implement idempotency for a payment endpoint?
**Model answer:** **Idempotency:** an operation is idempotent if performing it multiple times has the same effect as performing it once. `f(f(x)) = f(x)`. HTTP GET, PUT, DELETE are idempotent by definition. POST is not.

**Why it's critical:** in distributed systems, networks are unreliable. A client sends a request, the server processes it, but the response is lost. The client retries — now the server receives the same request twice. Without idempotency: double charges, duplicate orders, duplicate emails.

**Implementation for payments (the canonical hard case):**

1. **Idempotency key:** client generates a unique UUID for each payment intent and includes it in the request header: `Idempotency-Key: uuid-v4`. This key identifies "this specific payment attempt."

2. **Server stores the key + result:**
```sql
CREATE TABLE idempotency_keys (
  key UUID PRIMARY KEY,
  created_at TIMESTAMPTZ,
  request_hash VARCHAR,   -- hash of the request body
  response_status INT,
  response_body JSONB,
  completed BOOLEAN
);
```

3. **On receipt:**
   - Check if `key` exists in the table.
   - If yes and `completed = true`: return the stored response (idempotent replay). No double charge.
   - If yes and `completed = false`: another request is in-flight. Return 409 Conflict (or wait).
   - If no: insert the key (mark in-progress), process the payment, update the row with the result.

4. **Request hash:** include a hash of the body alongside the key. If the same idempotency key arrives with a different body, return 422 — the client is misusing the key.

5. **TTL:** idempotency keys can be garbage-collected after 24–48 hours (Stripe's policy).

**At-least-once delivery (Kafka, Hermes):** your consumer MUST be idempotent. Pattern: `(userId, itemId, eventTimestamp)` as a natural idempotency key. `INSERT ON CONFLICT DO NOTHING` in Postgres.
**Interview trap:** "Isn't this just deduplication?" — Idempotency is broader. Deduplication drops duplicates. Idempotency returns the *same result* as the original — including success responses from previous calls. A payment system must return the same charge ID, not just drop the duplicate.
**Tags:** idempotency, payments, distributed-systems, kafka, at-least-once, retry

---

## Q-SD-013 [bloom: understand] [level: regular]
**Question:** Explain the CAP theorem. Give a concrete example of a CP system and an AP system, and describe what happens to each during a network partition.
**Model answer:** **CAP theorem (Brewer 2000, Gilbert & Lynch 2002):** a distributed data system can guarantee at most two of three properties simultaneously:

- **C (Consistency):** every read receives the most recent write or an error (linearizability).
- **A (Availability):** every request receives a non-error response (may be stale).
- **P (Partition Tolerance):** the system continues operating despite network partitions (message loss between nodes).

**P is not optional** — networks partition. The real choice is **CP vs AP when a partition occurs.**

**CP example — ZooKeeper / etcd / HBase:** during a partition, the minority partition stops serving requests rather than risk stale reads. Leader-based consensus (Raft/Paxos). Trade-off: reduced availability during partition. Right choice for: leader election, distributed locks, payment ledgers where stale reads cause double-charges.

**AP example — Cassandra / DynamoDB / Couchbase:** during a partition, all nodes continue accepting reads and writes, potentially diverging. Nodes reconcile when partition heals (last-write-wins or vector clocks). Trade-off: you may read stale data. Right choice for: shopping carts, view counts, user profiles, recommendation events — where stale-for-seconds is acceptable and availability is critical.

**PACELC extension (Abadi 2012):** CAP only covers partition behavior. PACELC adds: even without a partition (E), you trade Latency vs Consistency. A strongly consistent DB (synchronous replication) has higher write latency than an eventually consistent one.

**In practice:** most systems allow configuring the trade-off per operation. Cassandra's `QUORUM` consistency = CP-leaning; `ONE` = AP-leaning. PostgreSQL with sync replication = CP; async replication = AP on replica reads.
**Interview trap:** "Can I have all three?" — No. The theorem is mathematically proven. What you can do is choose CP vs AP per operation, minimize partition duration, and design for eventual consistency with conflict resolution.
**Tags:** cap-theorem, consistency, availability, partition-tolerance, cassandra, zookeeper, pacelc

---

## Q-SD-014 [bloom: understand] [level: regular]
**Question:** What are message queues used for in distributed systems? Explain at-least-once delivery, idempotency, dead-letter queues, and backpressure.
**Model answer:** Message queues (Kafka, RabbitMQ, SQS, Hermes-over-Kafka) decouple producers from consumers. Key properties:

**Why queues:**
1. **Decoupling:** producer doesn't need to know consumer is alive. Orders service publishes events; inventory, email, analytics all consume independently.
2. **Smoothing spikes:** flash sale creates 50k orders/second; inventory can process at its own pace.
3. **Durability:** queue persists messages if consumer is down — no data loss.
4. **Fan-out:** one event, multiple consumers.

**Delivery semantics:**
- **At-most-once:** fire and forget. Fast, may lose messages.
- **At-least-once:** producer retries until acked; consumer may process the same message multiple times. Most queues default to this. **Consumers must be idempotent.**
- **Exactly-once:** extremely hard. Kafka transactions provide idempotent producer + transactional offsets — exactly-once within Kafka. Cross-system exactly-once (Kafka → DB) requires transactional outbox pattern.

**Dead-letter queue (DLQ):** when a message fails processing N times (configurable retry limit), it's moved to the DLQ instead of re-queued forever. Prevents poison messages from blocking the queue. Alert on DLQ depth — it means messages are failing silently.

**Backpressure:** when consumers are slower than producers, the queue grows. Strategies:
- **Buffer (queue absorbs):** fine short-term, but queue memory/disk is finite.
- **Drop:** lose messages under load (only for non-critical data like analytics).
- **Block producer:** propagate pressure upstream. Kafka consumer lag triggers alert → scale consumers or throttle producers.
- **Rate limit producer** at source.

**Hermes at Allegro:** wraps Kafka with REST push delivery to subscribers. Hermes Consumers manage retries with exponential backoff and DLQ — consumer services don't implement this themselves.
**Interview trap:** "Can't I just use HTTP callbacks instead of a queue?" — HTTP callbacks are push, not durable. If your receiver is down, the event is lost unless the sender implements retry + backoff (which is just re-implementing a queue). Queues give you durability, replay, fan-out, and backpressure for free.
**Tags:** message-queue, kafka, at-least-once, dead-letter-queue, backpressure, idempotency, hermes

---

## Q-SD-015 [bloom: apply] [level: senior]
**Question:** Design a database sharding strategy for a 10TB user activity table that is receiving 50k writes/second and 200k reads/second. Cover: shard key selection, hash vs range sharding, hot partitions, and resharding.
**Model answer:** At 10TB and 250k total RPS, a single PostgreSQL instance is at its ceiling. Horizontal sharding is necessary.

**Shard key selection — the most important decision:**

Good shard key criteria:
1. **High cardinality:** enough distinct values to distribute across shards (user_id: millions of distinct values).
2. **Even distribution:** no single value dominates (avoid sharding by country — US would be 50% of traffic).
3. **Query locality:** most queries touch one shard, not all shards (shard by user_id → a user's full activity on one shard, no scatter-gather for per-user reads).
4. **Not monotonically increasing** (for hash sharding): avoid auto-increment IDs as shard key in range sharding — all writes go to the last shard (hot partition).

**Hash vs range:**

**Hash sharding:** `shard = hash(user_id) % N`. Even distribution. No ordering within a shard by key. Cross-shard range queries require scatter-gather. Resharding = remapping all keys.

**Range sharding:** `shard = user_id < 1M → shard0, 1M–2M → shard1, ...`. Natural range scans within a shard. Risk: hot partitions if one range is accessed far more than others (e.g., new users clustered in highest ID range → last shard hot).

**For user activity by user_id → hash sharding**: user queries are point lookups (single user_id), not range scans. Hash sharding distributes load evenly.

**Hot partition problem:** if user_id is skewed (1% of celebrity users drive 50% of traffic), hash sharding still creates hot shards. Mitigations:
- **Shard splitting:** detect hot shards, split into sub-shards with a finer hash.
- **Read replicas per shard:** hot shards get more replicas.
- **Application-level caching:** hot user activity cached in Redis, bypassing DB.

**Resharding (the painful part):** when you grow from 16 to 32 shards, 50% of all data must move. Live resharding strategies:
1. **Consistent hashing:** virtual nodes minimize data movement. Adding a node only moves K/N keys.
2. **Double-write period:** write to old and new shard simultaneously, backfill in background, cut over read traffic, then remove old data.
3. **Directory-based sharding:** a routing table maps key ranges → shard IDs. Resharding = updating the directory, no data movement schema changes. Cost: extra lookup per query.

**Read replicas per shard:** reads at 200k RPS at 10 shards = 20k reads/shard. A well-tuned Postgres can handle ~10-50k simple reads/sec. Add 2-3 async read replicas per shard for the read path.
**Interview trap:** "Just shard by created_at." — All writes go to the current time shard → severe write hot spot. This is a classic mistake with monotonic shard keys. Never shard by a timestamp for write-heavy tables.
**Tags:** sharding, shard-key, hash-sharding, range-sharding, hot-partition, resharding, consistent-hashing, database-scaling

---

## Q-SD-016 [bloom: apply] [level: senior]
**Question:** Design a URL shortening service (bit.ly). Cover: API, data model, shortcode generation, scaling reads, analytics, and the 301 vs 302 redirect trade-off.
**Model answer:** A classic system design exercise with subtleties on scale.

**Requirements clarification:**
- 100M new URLs/day (write: ~1,160/sec), 10B redirects/day (read: ~115,000 RPS)
- Read/write ratio: 100:1. Read-dominated.
- Analytics: click counts, referrers, geography.
- URL expiry: optional.

**API:**
```
POST /api/shorten
Body: {"long_url": "https://...", "custom_alias": null, "ttl_days": null}
Response: {"short_code": "aB3xQ9", "short_url": "https://sho.rt/aB3xQ9"}

GET /{code}  → 301/302 redirect to long_url
GET /api/analytics/{code}  → click stats
```

**Shortcode generation:**
- 6-character alphanumeric (a-z, A-Z, 0-9) = 62^6 = ~56 billion codes. Sufficient for years.
- **Option 1: Counter-based + Base62 encoding.** Auto-increment ID from DB → Base62 encode. Simple, sequential, predictable. Weakness: sequential codes are guessable.
- **Option 2: MD5/SHA-256 of long URL → take first 6 chars.** Collision risk (~0.01% at 56B codes). Must check for collision on insert.
- **Option 3: Pre-generated code pool.** Background worker generates random codes, stores in a "keys" table as unused. Redirect service pops one atomically. No collision check at request time.

**Data model:**
```sql
CREATE TABLE urls (
  code        CHAR(7)      PRIMARY KEY,
  long_url    TEXT         NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMPTZ,
  user_id     BIGINT,
  click_count BIGINT       DEFAULT 0  -- not authoritative, see analytics
);
```

**Scaling reads (115k RPS):**
- **Cache layer:** cache `{code → long_url}` in Redis. TTL = URL TTL or 24h. 100M URLs × 500 bytes avg = 50GB — fits in a Redis cluster with room. After cache warm, >99% of redirects served from Redis without hitting DB.
- **Read replicas:** for the cache-miss path.

**Analytics:** never update `click_count` synchronously on redirect (write amplification, contention). Instead: publish click events to Kafka → stream consumer aggregates per-code counts in Druid or ClickHouse. Analytics endpoint reads from the analytics store, not the main DB.

**301 vs 302:**
- `301 Moved Permanently`: browser caches the redirect. Future clicks go directly to destination — no hop through the shortener. **You lose analytics data for repeat visitors.**
- `302 Found` (temporary): browser always hits the shortener. Analytics complete, but extra RTT on every click.
- **For click analytics: use 302.** For SEO or when analytics don't matter: 301.
**Interview trap:** "Just use 301 for performance." — You permanently lose the ability to track clicks or change the destination URL. Once browsers cache a 301, you can never redirect that short code to a new URL. 302 is correct for a URL shortener with analytics.
**Tags:** url-shortener, system-design, base62, redis, caching, 301-vs-302, analytics, kafka

---

## Q-SD-017 [bloom: apply] [level: senior]
**Question:** What is p99 tail latency? Why is it harder to optimize than p50, and what techniques specifically reduce tail latency?
**Model answer:** **p99 latency** means 99% of requests complete within this time. The 1% "tail" — the slowest requests — matters disproportionately because:

1. **Fan-out amplification:** if a page makes 100 parallel service calls and each has 1% chance of a slow response, the page has a 63% chance of hitting at least one slow call. Tail latency compounds.
2. **Long-lived connections:** in recommendation serving at 20k RPS, even 0.1% of requests taking 1 second creates a queue buildup — those goroutines/threads hold resources.
3. **SLA contracts:** "p99 < 40ms" is the Allegro recsys target. A 200ms p99 breaks that SLA even if p50 is 5ms.

**Why p99 is harder than p50:**
- p50 optimization: eliminate common-case inefficiencies (slow algorithms, missing indexes).
- p99 optimization: eliminate rare-path inefficiencies — GC pauses, lock contention, slow DNS lookups, JIT compilation warming, OS scheduler jitter, TCP retransmits, garbage collected buffer pools.

**Techniques to reduce tail latency:**

1. **Hedged requests (speculative execution):** after a brief delay (e.g., 10ms), send the same request to a second server. Use whichever responds first, cancel the other. Reduces p99 dramatically at cost of ~5-10% extra load. Used by Bigtable, Spanner.

2. **Timeout + fallback:** every downstream call has a `withTimeout(15ms)`. Timeout → serve stale cache or degraded response. Never let a slow downstream make the whole request slow.

3. **Connection pooling:** prevents TCP handshake overhead on slow paths. Keep connections warm.

4. **GC tuning (JVM):** GC pauses are major tail latency sources. G1GC or ZGC with tuned heap sizes. Pre-allocate critical objects. Avoid allocation-heavy hot paths.

5. **Thread pool sizing:** if thread pool is full, incoming requests queue → tail latency spikes. Size thread pools based on `Little's Law`: `pool_size = RPS × avg_latency_seconds`.

6. **Eliminate synchronized hot paths:** `synchronized` or unfair locks under contention add jitter. Use lock-free structures (ConcurrentHashMap, AtomicLong) for hot paths.

7. **Bounded queues:** unbounded queues cause requests to wait indefinitely when backend is slow. Bounded queue with backpressure fails fast → predictable p99.

8. **Percentile-aware load balancing:** route to the server with the lowest recent p99, not just fewest connections.
**Interview trap:** "Just increase the timeout." — A longer timeout makes p99 *worse*, not better. It allows slow requests to stay in the system longer, consuming resources. The goal is to reduce the duration of slow requests, or fail them fast.
**Tags:** tail-latency, p99, hedged-requests, gc-tuning, timeout, jvm, latency-optimization

---

## Q-SD-018 [bloom: apply] [level: senior]
**Question:** Design a distributed rate limiter that works across multiple application server instances using Redis. Handle the sliding window algorithm and race conditions.
**Model answer:** A per-user rate limiter that must work across N app servers (shared state in Redis).

**Algorithm: Sliding Window Counter (hybrid — O(1) space, approximate sliding window):**

For time window W (e.g., 60s), track two counters:
- `prev_count`: requests in the previous full window
- `curr_count`: requests in the current window
- `elapsed`: time elapsed in current window

Estimated rate: `curr_count + prev_count × (1 - elapsed/W)`

Redis implementation with atomic Lua script:

```lua
-- KEYS[1] = "rl:{userId}:curr", KEYS[2] = "rl:{userId}:prev"
-- ARGV[1] = limit, ARGV[2] = window_size_seconds, ARGV[3] = current_epoch_second

local window = tonumber(ARGV[2])
local now    = tonumber(ARGV[3])
local slot   = math.floor(now / window)

local curr_key = KEYS[1] .. ":" .. slot
local prev_key = KEYS[1] .. ":" .. (slot - 1)

local curr = tonumber(redis.call('GET', curr_key) or 0)
local prev = tonumber(redis.call('GET', prev_key) or 0)
local elapsed_fraction = (now % window) / window
local estimated = curr + prev * (1 - elapsed_fraction)

if estimated >= tonumber(ARGV[1]) then
  return 0  -- rejected
end

redis.call('INCR', curr_key)
redis.call('EXPIRE', curr_key, window * 2)
return 1  -- allowed
```

**Why Lua script:** Lua scripts in Redis are atomic. Without atomicity: two concurrent requests both read `curr=99` (below limit 100), both increment → 101 requests allowed. With Lua, the read-increment-check is one atomic operation.

**Race condition — no Lua:** use `MULTI/EXEC` (transaction) or `SET NX EX` (optimistic lock with retry). Lua is simpler and preferred.

**Redis Cluster considerations:** all keys for a single user must hash to the same slot. Use hash tags: `{userId}:curr` and `{userId}:prev` — the `{userId}` part determines the slot, so both keys land on the same node and are accessible in one Lua call.

**Fallback:** if Redis is unreachable, fail-open (allow) or fail-closed (reject). Fail-open is usually safer for user experience; fail-closed for security-critical APIs.
**Interview trap:** "What if two Redis shards get the user's keys?" — With naive key naming this happens and makes Lua cross-slot scripts fail. Hash tags (`{userId}`) force all user keys to one slot. This is a real production gotcha.
**Tags:** rate-limiting, redis, lua, sliding-window, distributed, race-condition, hash-tags

---

## Q-SD-019 [bloom: apply] [level: senior]
**Question:** Explain leader election in distributed systems. Why is it needed, and how do ZooKeeper, etcd, and Raft implement it?
**Model answer:** **Leader election** is the process by which a cluster of nodes agrees on a single leader that coordinates actions requiring single-node authority: writing to a primary DB, executing a scheduled job exactly once, holding a distributed lock.

**Why needed:** in a distributed system without a leader, multiple nodes might simultaneously perform the same action (double-execution of a cron job, split-brain in a DB cluster). Leader election serializes these actions.

**ZooKeeper — ephemeral sequential znodes:**
1. Each candidate creates an ephemeral sequential node at `/leader/candidate-` (e.g., `/leader/candidate-0000000001`).
2. Each candidate lists all nodes and watches the one with the next-lower sequence number.
3. The candidate holding the lowest sequence number is the leader.
4. If the leader dies, its ephemeral node is deleted (ZK session timeout), triggering a watch on the candidate with sequence n+1. That candidate becomes the new leader.
5. Ephemeral = auto-deleted on session expiry (node crash/disconnect). No manual cleanup.

**etcd — lease-based election:**
1. Candidate calls `Grant(TTL=15s)` → gets a lease ID.
2. Calls `Put("/leader", "candidate-id", lease=leaseID)` with `WithPrevKV` + `CompareAndSwap` (only succeed if no current leader).
3. Periodically calls `KeepAlive` to refresh the lease.
4. If leader crashes, lease expires after TTL → key deleted → another candidate wins the CAS.
5. Lower complexity than ZooKeeper's sequential node pattern. `clientv3/concurrency.Election` in the etcd Go client automates this.

**Raft (underlies etcd, CockroachDB, Consul):** distributed consensus algorithm:
1. Nodes are in `Follower`, `Candidate`, or `Leader` state.
2. No heartbeat from leader → follower becomes Candidate → increments term → requests votes.
3. Candidate with majority vote → Leader. Leader sends heartbeats to prevent new elections.
4. Log replication: all writes go through Leader → appended to log → replicated to quorum → committed.
5. Safety: at most one leader per term. Leader always has the most up-to-date log (a node can't be elected without a quorum agreeing it's up to date).

**When to use each:**
- ZooKeeper: battle-tested, strong consistency. Operational overhead (Java, ZK ensemble). Kafka uses it for broker metadata (moving to KRaft).
- etcd: simpler to operate, native Kubernetes dependency. Kubernetes uses it as its data store.
- Homegrown Raft: don't. Use a library (hashicorp/raft, etcd). Raft is hard to implement correctly.
**Interview trap:** "Can't I just use a database row as a lock for leader election?" — Yes for simplicity (`SELECT FOR UPDATE` on a `leader` row). But it only works if the DB is always available. If the DB partitions, no leader can be elected. Real distributed election handles the case where the DB itself is partitioned.
**Tags:** leader-election, zookeeper, etcd, raft, distributed-systems, consensus

---

## Q-SD-020 [bloom: apply] [level: senior]
**Question:** Design the observability stack for a microservices-based system. Cover the three pillars (metrics, logs, traces), the RED and USE methods, alerting strategy, and what to instrument in a Kotlin/Spring Boot service.
**Model answer:** **Three pillars of observability:**

**1. Metrics (time-series, aggregated):**
Tool: Prometheus (scrape-based) + Grafana dashboards.

**RED method** (for request-driven services):
- **R**ate: requests per second (QPS by endpoint)
- **E**rror rate: `sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))`
- **D**uration: p50, p95, p99, p999 latency histograms

**USE method** (for infrastructure/resources):
- **U**tilization: CPU %, memory %, disk I/O %
- **S**aturation: queue depth, thread pool fullness, GC pressure
- **E**rrors: OOM events, disk full, network errors

Spring Boot Actuator + Micrometer auto-exposes RED metrics. Add `management.metrics.distribution.percentiles-histogram.http.server.requests=true` for histogram-based p99 in Prometheus.

**2. Logs (discrete events, searchable):**
Structured JSON logs. Every log line has: `traceId`, `spanId`, `userId`, `serviceId`, `level`, `message`, `timestamp`.
```json
{"ts":"2026-06-03T10:00:01Z","level":"WARN","service":"recs","traceId":"abc123","userId":"u987","msg":"Faiss timeout","latency_ms":52,"fallback":"cache"}
```
Aggregation: ELK stack or Loki + Grafana. Logback with `logstash-logback-encoder` in Spring Boot.

**3. Traces (cross-service request flow):**
OpenTelemetry (vendor-neutral) → Jaeger or Tempo. Auto-instrumentation via `opentelemetry-javaagent.jar`. Every service call gets `traceId` + `spanId` propagated in HTTP headers (`traceparent`). Dashboard: a request's full tree across services, with timing per span.

**Alerting strategy:**
- Alert on **symptoms**, not causes. "p99 > 200ms for 5 minutes" beats "CPU > 80%".
- Alert on **SLO burn rate** (error budget consumption rate). Multi-window multi-burn-rate alerts (Google SRE book).
- Alert routing: PagerDuty/Opsgenie for on-call. Slack for warnings. No alert fatigue from low-severity noise.

**Spring Boot instrumentation checklist:**
- `spring-boot-starter-actuator` — health, info, metrics endpoints
- `micrometer-registry-prometheus` — Prometheus metrics export
- `opentelemetry-spring-boot-starter` — auto-instrument HTTP, Kafka, JDBC spans
- Add `@Timed` or `Timer.record()` around critical internal methods
- `management.endpoints.web.exposure.include=health,info,prometheus`
**Interview trap:** "Logs are enough, why add tracing?" — A log line tells you what happened in one service. Tracing tells you *why* — which upstream call caused the latency. At 1000+ microservices and 10+ hops per request, a log without a traceId is nearly useless for RCA.
**Tags:** observability, metrics, logging, tracing, prometheus, opentelemetry, red-method, use-method, spring-boot-actuator

---

## Q-SD-021 [bloom: apply] [level: senior]
**Question:** Design the read replica strategy for a PostgreSQL-backed service with 100k reads/second and 5k writes/second. Cover: replication lag, read-your-writes consistency, and when a replica is not enough.
**Model answer:** At 100k reads/second, a single PostgreSQL primary will be at or past its ceiling (typical OLTP Postgres ceiling: 10-50k simple reads/sec on commodity hardware depending on query complexity).

**Architecture:**
```
Writes (5k RPS) → Primary (Postgres)
                       │ streaming replication (async, ~ms lag)
                       ├── Replica 1
                       ├── Replica 2
                       └── Replica 3 (3-5 replicas, read: 100k / 4 = 25k RPS each)
```

**Async vs sync replication:**
- **Async (default):** primary commits without waiting for replica ack. Lag ~1-100ms. If primary crashes and last N transactions weren't replicated → data loss. Good for read scaling.
- **Sync:** primary waits for at least one replica to ack before committing. Zero data loss. +latency on writes (adds one network RTT). `synchronous_commit = on` + `synchronous_standby_names = '...'` in `postgresql.conf`.

**Read-your-writes consistency problem:** user writes a post, then immediately reads — but reads go to a replica that hasn't replicated the write yet. User sees stale data.

Mitigations:
1. **Sticky read-after-write:** for a time window after a write (e.g., 5 seconds), route that user's reads to primary. Implemented at the connection pool level or application level.
2. **Replication lag check:** check `pg_stat_replication` lag; if lag > threshold, promote to primary read. Complex to implement per-request.
3. **Logical replication token:** primary returns a WAL LSN position with the write. Read request passes LSN to replica; replica waits until it has replayed that LSN. PgBouncer and some ORMs support this.
4. **Write primary for a bounded window, then fallback:** simplest. Track `last_write_time` per user in Redis; if < 5 seconds ago, read from primary.

**When replicas aren't enough:**
- Write throughput at 5k RPS with complex transactions: single primary will hit WAL write bottleneck.
- Extremely large tables: even read replicas can't serve efficiently without partitioning.
- Solutions: **CQRS** (separate write model from read model), **sharding** the primary, or move to a distributed SQL DB (CockroachDB, Spanner) or denormalized read stores (Elasticsearch, Redis materialized views).
**Interview trap:** "More replicas = linear read scaling." — Not quite. Adding replica N means it receives 1/N of reads, but you also add replication load on primary (primary must send WAL to all N replicas). At some point primary's WAL sender becomes the bottleneck. Also: managing replica lag across N nodes adds operational complexity.
**Tags:** read-replicas, postgresql, replication-lag, read-your-writes, database-scaling, cqrs

---

## Q-SD-022 [bloom: apply] [level: senior]
**Question:** Design the serving path for a recommendation system that must handle ~20,000 RPS at p99 ≤ 40ms, using Two-Tower embeddings and Faiss ANN. Be concrete: budget the latency, design fallbacks, and address index refresh.
**Model answer:** This is Allegro's actual production system (arxiv 2508.03702). Architecture:

**Latency budget (total p99 budget: 40ms):**

| Stage | Budget | Notes |
|---|---|---|
| Feature fetch (Redis/Cassandra) | 5ms | Hot-key cache for item/user features |
| Query tower encoding | 8ms | Single forward pass, in-process model |
| Faiss ANN search | 12ms | In-process JNI, top-500 candidates |
| Re-rank (business rules) | 3ms | Diversity, recency, blacklists — in-memory |
| Response serialization | 2ms | JSON/protobuf |
| **Total expected** | **~30ms** | **10ms p99 headroom** |

**Kotlin implementation sketch:**
```kotlin
suspend fun getRecommendations(req: RecsRequest): RecsResponse {
    val features = withTimeout(8.milliseconds) {
        featureStore.get(req.itemId)         // Redis hot cache
    }
    val queryEmbedding = withTimeout(12.milliseconds) {
        encoder.encode(features)             // in-process model
    }
    val candidates = withTimeout(18.milliseconds) {
        faissIndex.search(queryEmbedding, topK = 500)
    }
    val ranked = reranker.rank(candidates, req.context)
    telemetry.logImpression(req, ranked)     // fire-and-forget to Kafka
    return RecsResponse(ranked.take(20))
}
```

**Faiss deployment — in-process vs service:**
- In-process (JNI): zero network RTT. Saves 10-20ms. Each pod carries full index in RAM.
- At 20k RPS with 40ms p99: in-process is likely correct — a remote Faiss call would consume 25-50% of the p99 budget.

**Index refresh (daily rebuild pattern):**
1. Offline pipeline (GCP, GPU): train → compute item embeddings → build Faiss index → upload to object storage.
2. Serving pod: background thread downloads new index to second in-memory slot. Atomic pointer swap. Old index GC'd.
3. Blue/green at pod level: deploy pods with new index as a rolling update, readiness probe gates traffic until index is loaded.

**Fallback tiers:**
```kotlin
try {
    getRecommendationsFromANN(req)
} catch (e: TimeoutCancellationException) {
    val cached = recommendationCache.get(req.itemId)  // Redis, TTL=1h
    if (cached != null) return cached
    return popularItemsFallback.get(req.context)       // pre-computed per category
}
```
Each fallback tier emits a metric tag (`recs.fallback.cache_hit`, `recs.fallback.popular`). A spike in these metrics pages on-call: Faiss is down.

**A/B testing:** impression logged to Kafka includes `abVariant`, `modelVersion`, `indexTimestamp`, `fallbackTier`. Analysis must segment by fallbackTier — fallback impressions contaminate variant CTR metrics if not filtered.
**Interview trap:** "Why not run a cross-encoder ranker directly over the full catalog?" — At 10M items, a cross-encoder that jointly encodes (query, item) is O(items) per request. At 1ms per item, that's 10,000 seconds per request. Two-Tower pre-computes item embeddings offline, reducing live serving to one encode + one ANN lookup.
**Tags:** recsys, faiss, ann, system-design, latency, kotlin, two-tower, fallback, ab-testing

---

## Q-SD-023 [bloom: apply] [level: senior]
**Question:** Consistent hashing: explain the algorithm, why it minimizes key movement when nodes are added/removed, and what virtual nodes solve.
**Model answer:** **Problem with modulo hashing:** `shard = hash(key) % N`. When you change N (add or remove a node), almost all keys remap. At N=10 → N=11, ~91% of keys move. For a distributed cache, this = a mass cache miss → thundering herd on the DB.

**Consistent hashing (Karger 1997):**
1. Map both cache nodes and keys to positions on a hash ring (0 to 2^32 - 1).
2. Each key is served by the **first node encountered clockwise** on the ring.
3. Adding a node: only keys between the new node and its predecessor move to the new node. All other keys stay. Average movement = K/N keys.
4. Removing a node: only its keys move to the next node clockwise.

**Math:** with N nodes, adding/removing one node moves K/N keys (where K = total keys). At N=10 and K=1M keys: only 100k keys move instead of 900k.

**Virtual nodes (vnodes):** a problem with basic consistent hashing is uneven distribution — nodes might cluster on the ring, leaving some nodes with much more load.

Solution: each physical node is assigned V virtual nodes at V different ring positions. Load is distributed more evenly. V=100-150 is typical (Cassandra uses 256 vnodes per node by default).

Benefits of vnodes:
- Even load distribution across heterogeneous hardware.
- When a node is added, it steals small fractions from many existing nodes → smooth rebalancing.
- When a node fails, its load is distributed across many nodes, not dumped on one neighbor.

**Used in practice:**
- Apache Cassandra: token ring with vnodes
- Amazon Dynamo (DynamoDB internals): consistent hashing with preference lists
- Distributed caches: Twemproxy, Redis Cluster (16384 hash slots ≈ vnode ring)
- Load balancers routing to stateful backends
**Interview trap:** "Consistent hashing eliminates all key movement." — It minimizes it, doesn't eliminate it. You still move 1/N of keys. And with vnodes, the implementation complexity increases (the routing table tracking which vnode → physical node must be maintained).
**Tags:** consistent-hashing, sharding, distributed-cache, vnodes, cassandra, dynamo

---

## Q-SD-024 [bloom: analyze] [level: master]
**Question:** Your recommendation service is serving 20k RPS at p99 40ms. After a routine index refresh (daily rebuild), you observe p99 spike to 300ms for 8 minutes. Root-cause this and propose a fix that prevents it.
**Model answer:** This is a real production failure mode at the intersection of JVM memory management and Faiss index swap.

**Root cause analysis — the likely culprits in order:**

**1. JVM GC pressure during index swap (most likely):**
- Loading a new Faiss index (say, 5GB) allocates a large off-heap or direct memory region.
- The old index is released, making it eligible for GC. If it's on-heap, a major GC (G1GC Full GC or CMS remark) pauses the JVM for 200-400ms.
- During the GC pause, all in-flight coroutines are suspended → p99 spikes to GC pause duration.

Diagnostic: `jstat -gcutil <pid>` or GC logs. Look for `[GC pause (G1 Full) 5GB heap...]` timed exactly at the p99 spike.

Fix: Use off-heap / native memory for the Faiss index (Faiss does this by default via JNI — it's in C++ heap). Ensure the Java wrapper holding the old index reference is `null`'d before the new index is swapped in, and a `System.gc()` is *not* called (let GC be natural). Verify `Xmx` is not set so tight that the new index triggers GC during load.

**2. Index load time blocking request processing:**
If the new index is loaded on a thread pool shared with request handling, requests queue during the load.

Fix: load new index on a dedicated non-blocking thread. Use double-buffering: `AtomicReference<FaissIndex>` with read via `get()` (lock-free). New index loaded in background, `compareAndSet` to swap atomically. Requests continue serving from old index during load.

**3. "Thundering herd" from cache invalidation:**
When the new index is swapped, the per-item recommendation cache (Redis, TTL=1h) becomes stale. If you flush the cache on index swap, all 20k RPS suddenly become full-path requests (no cache hits) for the duration of the cache warm-up.

Fix: do NOT flush the cache on index swap. Let TTL-based expiry naturally rotate the cache over 1 hour. The new model's recommendations will gradually replace the old ones. If the new index has materially different results, accept 1h of "stale" recommendations rather than a p99 spike.

**4. Pod restart / readiness probe timing:**
If the index refresh is implemented as a pod restart (new pod with new index), Kubernetes might route traffic to the pod before the index is fully loaded, causing requests to hit an uninitialized state.

Fix: implement a readiness probe that returns 200 only when the Faiss index is fully loaded and the service can handle requests. `@ReadinessState` in Spring Boot Actuator; custom `ReadinessStateContributor` that checks `faissIndex.isReady()`.

**Prevention strategy:**
1. Double-buffered index swap with `AtomicReference`.
2. Readiness probe gated on index load completion.
3. No cache flush on swap.
4. GC monitoring alert: alert if `gc_pause_seconds_p99 > 100ms`.
5. Canary rollout of index refreshes: route 1% of traffic to new index first, confirm p99 is stable, then cut over.
**Interview trap:** "Just reduce the index refresh frequency to reduce disruption." — Reducing frequency means recommendations go stale for longer. The question is fixing the swap mechanism, not avoiding it. Daily refresh is a feature, not a bug.
**Tags:** faiss, jvm, gc-tuning, index-swap, p99, tail-latency, double-buffering, observability, recsys

---

## Q-SD-025 [bloom: analyze] [level: master]
**Question:** Design a globally distributed, multi-region active-active system for a write-heavy e-commerce order service. Address: conflict resolution, causal consistency, replication topology, and what consistency model you'll offer users.
**Model answer:** This is a genuinely hard problem. Multi-region active-active for writes means two users in different regions can write conflicting updates simultaneously, and the system must reconcile.

**Why active-active vs active-passive:**
- Active-passive (primary EU, standby US): US users get 150ms write latency (RTT to EU primary). Unacceptable for checkout.
- Active-active: users write to nearest region. Sub-10ms local write latency. Conflict resolution required.

**Conflict types for orders:**
1. **Write-write conflict:** two sessions update the same order simultaneously in different regions (uncommon — orders are usually per-user, but possible with concurrent tab/device).
2. **Inventory conflicts:** user in US and EU both try to buy the last unit of stock. Both succeed locally, stock goes negative.

**Conflict resolution strategies:**

**Last-write-wins (LWW):** timestamp-based. Later write wins. Simple. Problem: clocks across DCs can drift; a write from EU at T+1ms "wins" over US at T even if US write was causally later from the user's perspective.

**Vector clocks / version vectors:** each node maintains a per-node counter. A write `{EU:3, US:2}` is causally after `{EU:2, US:2}` but concurrent with `{EU:3, US:1}`. Concurrent writes → conflict to be resolved by application logic (merge, or user prompt). Used by Amazon Dynamo, Riak.

**CRDTs (Conflict-free Replicated Data Types):** data structures designed so concurrent updates from multiple nodes always merge deterministically without conflicts. Examples: G-Counter (increment-only), PN-Counter (inc/dec), OR-Set (add/remove set with tombstones). **For inventory:** a PN-Counter where each region only decrements their local allocation. Total stock = sum of all regional allocations.

**Inventory allocation strategy (avoid negative inventory):**
1. Pre-allocate inventory to each region proportionally (e.g., 60% EU, 40% US).
2. Each region only sells from its allocation. No cross-region conflict.
3. Background balancer redistributes unsold allocation between regions every N minutes.
4. When a region runs out: fallback to cross-region order (sync with 150ms latency) or "backordered."

**Replication topology options:**
- **Full mesh:** every region replicates to every other. O(N^2) connections. Simple but expensive at N>3.
- **Hub-and-spoke:** one "primary" region replicates to all others. Single point for global ordering. Bottleneck at the hub.
- **Ring:** EU → US → APAC → EU. Reduced connections, higher replication lag (must traverse multiple hops).
- **CockroachDB / Spanner model:** Raft per range across regions. Strong consistency but high write latency due to Paxos round trips.

**Consistency model offered to users:**
- **Strong consistency (linearizability):** only achievable with synchronous cross-region coordination. Write latency = max(region_to_region_RTT) = 150ms+. Acceptable for financial transactions.
- **Causal consistency:** guarantee that if you see event B, you've also seen event A that caused B. Implemented via logical clocks. Lower latency than strong, higher guarantee than eventual. Good for orders: "if you see your order placed, you'll see your inventory deducted."
- **Eventual consistency:** fastest, lowest latency. Conflicts resolved asynchronously. OK for analytics, view counts, recommendation events.

**Recommended for orders:** causal consistency within a region, eventual consistency cross-region with CRDT-based inventory. Financial settlement uses strong consistency on a central ledger.
**Interview trap:** "Just use a global strongly-consistent DB and it's solved." — Spanner gives you strong consistency but at 200ms+ p99 for cross-region writes (CAP theorem applies across regions). For a checkout flow where 200ms feels slow, this is a real trade-off, not a free lunch.
**Tags:** multi-region, active-active, conflict-resolution, crdt, vector-clocks, causal-consistency, cap-theorem, distributed-systems

---

## Q-SD-026 [bloom: analyze] [level: master]
**Question:** Walk through the complete design of a real-time notification service (push, email, SMS) at 100M users, 10M notifications/day. Focus on fan-out strategy, at-least-once delivery, ordering guarantees, and deduplication.
**Model answer:** A notification service is a fan-out system: one business event → many user notifications via multiple channels.

**Scale estimates:**
- 10M notifications/day ÷ 86,400 = ~115 notifications/sec average
- Peak (e.g., flash sale "your item is in stock"): 100x average = ~11,500/sec
- Each notification may fan out to multiple channels (push + email + SMS) → up to 35k channel sends/sec at peak

**Architecture layers:**

**1. Event ingestion:**
```
Business events (OrderPlaced, ItemBackInStock, PriceDropped)
    ↓ Kafka topic: notification-events
    ↓ partitioned by event_type or user_id
```

**2. Notification router (consumer group):**
Reads from Kafka. For each event:
- Looks up user preferences (which channels enabled, notification opt-ins) from a preferences DB (Redis cache)
- Generates N channel-specific tasks (push task, email task, SMS task)
- Publishes each to a channel-specific Kafka topic (`notifications-push`, `notifications-email`, `notifications-sms`)

**3. Channel workers:**
Dedicated consumers per channel. Each calls the external provider:
- Push: FCM (Firebase) for Android, APNs for iOS
- Email: SendGrid / AWS SES
- SMS: Twilio

**Fan-out strategies:**
- **Single-level fan-out (above):** router writes one message per user per channel. Works at 35k/sec.
- **Two-level fan-out for massive events** (e.g., notify 10M users of a platform-wide sale): router writes one "broadcast" message → channel workers read once but deliver to all users in their partition. Avoids 10M Kafka messages for a single event.

**At-least-once delivery:**
Kafka consumers commit offset only after successful channel delivery. If FCM call fails → don't commit → message redelivered. **Channel workers MUST be idempotent:** FCM and APNs accept idempotency keys; for email, `ON CONFLICT DO NOTHING` on a `notifications_sent` table keyed by `(userId, eventId, channel)`.

**Deduplication:**
```sql
CREATE TABLE notifications_sent (
  user_id    BIGINT,
  event_id   UUID,
  channel    VARCHAR(20),
  sent_at    TIMESTAMPTZ,
  PRIMARY KEY (user_id, event_id, channel)
);
-- Insert with ON CONFLICT DO NOTHING
```
Before delivering to a channel, check + insert. Deduplication window: TTL the table rows after 48h.

**Ordering guarantees:**
- Notifications to the same user should be ordered (don't send "your order shipped" before "your order placed").
- Kafka partition by `user_id` ensures ordering per user within a topic.
- Cross-channel ordering is not guaranteed (push may arrive before email). Accept this — it's an acceptable UX trade-off.

**Rate limiting per channel:** external providers (FCM, SendGrid) have rate limits. Use a `RateLimiter` per provider. If rate limited, push to a delay queue (Kafka with `delay_until` header, or a scheduled retry topic).

**Dead-letter handling:** after N retries (e.g., 3), move to DLQ. Alert on DLQ depth. Manual review for business-critical notifications.
**Interview trap:** "Just write directly to FCM/email on the business event handler." — Synchronous delivery in the order handler couples checkout latency to notification provider latency (FCM can take 100ms+). It also means if FCM is down, orders fail. Decoupling via Kafka makes notifications an async side effect with independent retry.
**Tags:** notifications, fan-out, kafka, at-least-once, deduplication, fcm, push, email, sms, system-design

---

## Q-SD-027 [bloom: analyze] [level: master]
**Question:** Analyze the trade-offs of synchronous vs asynchronous inter-service communication in a microservices architecture. When does asynchronous (Kafka) actually *hurt*, and what are the hidden costs of eventual consistency?
**Model answer:** This is a design philosophy question masquerading as a technical one. The canonical answer "async is better" is wrong. Here is the full trade-off analysis:

**When synchronous (HTTP/gRPC) is correct:**
1. **Result needed to continue:** an order service checking inventory before confirming checkout. If inventory is unavailable, the order must not be placed. You cannot accept "we'll check asynchronously."
2. **User-facing response requires the result:** page rendering that needs product data must get it synchronously. A placeholder + async fill is a design choice, not a technical requirement.
3. **Simple queries with low latency:** reading a product title from a catalog service over HTTP is 1ms. Publishing to Kafka + consuming + responding is 100ms+. Async for a simple query is wasteful.
4. **Strong consistency required:** some operations must be atomic (debit account + create order). Two-phase commit over sync calls, or a Saga, but at least the participants know immediately.

**When async (Kafka) is correct:**
1. **Fire-and-forget side effects:** order placed → send confirmation email. Email delivery doesn't need to happen in the same request.
2. **Decoupling:** the order service should not know that analytics, recommendations, and inventory services exist.
3. **Spike absorption:** flash sale → 50k orders/sec → inventory service processes at its own pace.
4. **Fan-out:** one event, many consumers, no back-pressure from the slowest consumer to the publisher.

**When async hurts:**
1. **Hidden eventual consistency bugs:** a user places an order, immediately visits "My Orders" — but the order read model hasn't caught up. The user sees no order. They place it again → duplicate order. This is a real support ticket.
2. **Debug complexity:** a failure in an async chain is invisible to the original caller. "The order was placed but the email was never sent" requires correlating Kafka consumer logs, topic offset positions, DLQ entries.
3. **Saga failure handling:** a Saga across 5 services requires 5 × 2 = 10 compensating transactions in the worst case. The code complexity is non-trivial. Getting rollback ordering right is hard.
4. **Message ordering:** Kafka guarantees ordering within a partition. If two events for the same user land on different partitions (from different producers), their order is undefined. This breaks state machines (ItemAdded followed by ItemRemoved, but consumer sees them reversed).
5. **Latency:** async by definition means the result is not immediate. For any flow requiring a response within 100ms, async adds latency via the broker roundtrip.

**The hidden costs of eventual consistency:**
- **Compensation logic:** when the DB is eventually consistent, every read must handle the case "this may be stale." Every write must handle "the thing I'm updating may have been changed by another process since I read it." This doubles the number of code paths.
- **Causality violations visible to users:** user A adds an item to a cart; user B (sharing the cart) immediately reads the cart from a replica with replication lag → doesn't see the item. This is confusing and hard to explain.
- **Testing:** eventual consistency is easy to test at p50. Testing the failure mode where a consumer is 10 minutes behind requires dedicated infrastructure (delayed consumers, chaos tooling).

**Recommendation for interviews:** never say "async is better." Say: "I'd start with synchronous for any flow that needs a result, and add async for side-effects, fan-out, and spike absorption. Then measure where the bottlenecks are." Interviewers at Allegro specifically probe this because they have both Hermes/Kafka and direct HTTP flows.
**Interview trap:** "Kafka gives us exactly-once, so we don't need to worry about duplicates." — Kafka transactions give exactly-once within the Kafka broker. The moment you write to a database as part of consumption, you have two systems. Getting exactly-once across Kafka + DB requires either the transactional outbox pattern or idempotent DB writes. There is no free lunch.
**Tags:** synchronous, asynchronous, kafka, eventual-consistency, microservices, saga, trade-offs, system-design

---

## Q-SD-028 [bloom: analyze] [level: master]
**Question:** You need to design a global feature flag and A/B testing system that operates at 500k RPS with sub-millisecond overhead. Walk through the architecture and address: consistency of flag state across DCs, gradual rollouts, and correctness of experiment attribution.
**Model answer:** Feature flags and A/B testing have seemingly simple requirements ("is this flag enabled for this user?") but become a distributed systems problem at 500k RPS and multi-DC.

**Requirements clarification:**
- Evaluation latency: < 1ms per flag check (must not add observable latency to serving path)
- Flag state propagation: eventually consistent (seconds lag tolerable), not real-time
- Experiment attribution: must be deterministic (same user always in same variant) and stable across flag config reloads
- Scale: 500k RPS means flag evaluation is in the hot path

**Architecture:**

**1. Flag config store (control plane):**
- Source of truth: database (PostgreSQL) + admin UI for flag definitions and targeting rules.
- Published to a distributed config propagation layer (ZooKeeper, etcd, or a custom pub/sub via Kafka) on change.
- Evaluated at the edge, not by calling the control plane per request.

**2. Local in-process flag cache (data plane — the key insight):**
- Each service instance maintains an **in-memory copy** of all flag configs.
- Updated via a background thread polling the config store (or push via Server-Sent Events / WebSocket).
- Flag evaluation = local hash map lookup + deterministic hash. Zero network I/O.
- 1000 flags × 1KB each = ~1MB in-process. Trivially fits in RAM.

```kotlin
class FeatureFlagService(private val configStore: FlagConfigStore) {
    @Volatile private var flagConfigs: Map<String, FlagConfig> = emptyMap()

    // Called by background thread every 5 seconds
    fun refresh() { flagConfigs = configStore.fetchAll() }

    fun isEnabled(flagKey: String, userId: String): Boolean {
        val config = flagConfigs[flagKey] ?: return false
        return when (config.type) {
            BOOLEAN -> config.enabled
            PERCENTAGE -> deterministicHash(flagKey, userId) < config.rolloutPercent
            TARGETING -> config.targetingRules.any { it.matches(userId) }
        }
    }
}

fun deterministicHash(flagKey: String, userId: String): Int {
    // MurmurHash3 of (flagKey + ":" + userId) mod 100
    // Same input always returns same value → stable variant assignment
    return Hashing.murmur3_32().hashString("$flagKey:$userId", Charsets.UTF_8).asInt().absoluteValue % 100
}
```

**3. Consistency across DCs:**
- Background polling (5s interval): flag propagation lag = up to 5 seconds. Acceptable for feature flags (turning on dark launch is not instant anyway).
- For critical safety flags (kill switches): use shorter polling (1s) or push via a low-latency broadcast (Redis pub/sub, SSE from config service). Accept that 1 second of inconsistency exists.
- During the propagation window, some DCs serve variant A while others serve variant B. For A/B experiments, this introduces noise; for kill switches, this is the acceptable trade-off for not having a synchronous flag evaluation call in the hot path.

**4. Gradual rollout:**
Rollout percentage stored in flag config. `deterministicHash(flagKey, userId) < rolloutPercent`. Incrementing rolloutPercent from 0 → 100 in steps (1%, 5%, 20%, 50%, 100%) via the admin UI. Each increment propagates within 5 seconds.

**5. Experiment attribution correctness:**
- **Deterministic hash:** `hash(flagKey + userId)` not `random()`. Same user always in same bucket. Re-hashing each request is fine — it's O(1).
- **Impression logging:** log the variant **at serving time**, not at click time. If you only log variant on click, users who saw the feature but didn't click are missing from the denominator → biased CTR.
- **Log the flag version / config hash:** when the flag config changes mid-experiment (you change targeting), old and new configs are mixed in your analysis. Flag version in the impression log lets you segment.
- **Holdout groups:** some users are always excluded from all experiments (holdout). Used to measure cumulative A/B test interaction effects. Must be baked into the assignment logic.
**Interview trap:** "Why not call a centralized flag service per request?" — A centralized RPC call at 500k RPS adds: (a) network latency (sub-1ms becomes impossible), (b) a new single point of failure (all traffic blocked if flag service is down), (c) a new bottleneck requiring horizontal scaling proportional to all your other services. In-process evaluation with async config refresh is the industry standard (LaunchDarkly, Unleash all use this model).
**Tags:** feature-flags, ab-testing, system-design, deterministic-hash, eventual-consistency, rollout, experiment-attribution, in-process-cache

---

## Q-SD-029 [bloom: understand] [level: regular]
**Question:** What are the Circuit Breaker and Bulkhead patterns in microservices? Explain how each prevents cascading failures, and give concrete implementation details for a Java/Kotlin service.
**Model answer:** Both patterns are failure-isolation mechanisms. Without them, a slow or failing downstream service can bring down the entire system.

**Circuit Breaker (Fowler 2014):**
Models a state machine with three states:

- **Closed (normal):** requests pass through. Failures are counted. If error rate crosses the threshold (e.g., 50% failures in a 10-second window), trip to Open.
- **Open (tripped):** all requests fail immediately with a fallback (no call to downstream). After a timeout (e.g., 30 seconds), transition to Half-Open.
- **Half-Open:** allow a limited number of probe requests through. If they succeed → back to Closed. If they fail → back to Open.

**Why it helps:** a slow downstream makes threads wait, filling the thread pool. Circuit breaker fails fast instead of waiting, freeing threads for requests that can succeed.

**Bulkhead:**
Isolates resources (thread pools, connection pools, semaphores) per downstream dependency. Borrowed from ship design: flooding one compartment doesn't sink the ship.

- **Thread pool isolation:** each downstream call (InventoryService, EmailService) gets its own bounded thread pool. If InventoryService is slow and fills its 10-thread pool, no impact on EmailService's 10 threads.
- **Semaphore isolation:** limit concurrent calls to a downstream via a semaphore. Lighter than thread pools (same thread, just gated), but no timeout enforcement.

**Java/Kotlin — Resilience4j (preferred, replaces Hystrix):**
```kotlin
val circuitBreakerConfig = CircuitBreakerConfig.custom()
    .failureRateThreshold(50f)           // trip at 50% failures
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .slidingWindowSize(10)               // last 10 calls
    .build()

val bulkheadConfig = BulkheadConfig.custom()
    .maxConcurrentCalls(25)              // max 25 concurrent calls to this service
    .maxWaitDuration(Duration.ofMillis(500))
    .build()

val decorated = Decorators.ofSupplier { inventoryService.checkStock(itemId) }
    .withCircuitBreaker(circuitBreaker)
    .withBulkhead(bulkhead)
    .withFallback(listOf(CallNotPermittedException::class.java)) { getCachedStock(itemId) }
    .decorate()
```

**Spring Cloud Circuit Breaker** wraps Resilience4j with Spring-friendly abstraction. `@CircuitBreaker(name = "inventory", fallbackMethod = "inventoryFallback")`.

**Key operational insight:** circuit breakers require health metrics to tune thresholds. Set `slidingWindowSize` too small → flaps on transient errors. Set `failureRateThreshold` too high → never trips when you need it.
**Interview trap:** "Isn't a timeout enough?" — A timeout prevents one call from hanging forever. But if you have 100 concurrent calls to a failing service, each timing out after 10 seconds, you have 100 threads blocked for 10 seconds each → thread pool exhausted → your service appears to fail. Circuit breaker fails fast without waiting for the timeout.
**Tags:** circuit-breaker, bulkhead, resilience4j, microservices, fault-tolerance, fallback, hystrix

---

## Q-SD-030 [bloom: apply] [level: senior]
**Question:** Explain the Saga pattern for distributed transactions. Compare choreography vs orchestration. Then explain the Transactional Outbox pattern and why it is the correct solution for reliable event publishing.
**Model answer:** In microservices, a single business operation (place an order) spans multiple services (Order, Inventory, Payment, Notification). There is no global transaction manager. Saga is the pattern for managing distributed transactions without 2PC.

**Saga:** a sequence of local transactions. Each step publishes an event or message that triggers the next step. If a step fails, compensating transactions undo previous steps.

**Example — order placement saga:**
1. Order Service: create order (status=PENDING) → emit OrderCreated
2. Inventory Service: reserve items → emit ItemsReserved (or InsufficientStock)
3. Payment Service: charge card → emit PaymentCharged (or PaymentFailed)
4. Order Service: confirm order (status=CONFIRMED) → emit OrderConfirmed

**On failure at step 3 (payment fails):**
- Compensate step 2: Inventory Service releases reservation
- Compensate step 1: Order Service marks order CANCELLED

**Choreography (event-driven):**
Each service listens for events and decides what to do next. No central coordinator.

- **Pro:** loose coupling, each service only knows about its own events, easy to add new participants.
- **Con:** difficult to reason about the overall saga flow — it's implicit in which service handles which event. Debugging requires correlating events across services. Cycle detection and failure handling become complex.

**Orchestration (command-driven):**
A dedicated Saga Orchestrator (a state machine) sends commands to each service and waits for replies. It drives the saga end-to-end.

- **Pro:** saga flow is explicit and visible in one place. Easier to add compensation logic. Simpler to debug (orchestrator state = saga state).
- **Con:** introduces a coordinator service that becomes a coupling point. The orchestrator knows about all participants.

**Rule of thumb:** choreography for simple 2-3 step flows; orchestration for complex multi-step flows with error handling. Temporal.io and AWS Step Functions are popular orchestration engines.

**Transactional Outbox pattern:**
The fundamental problem: a service must atomically "write to DB AND publish an event." These are two separate systems — there is no distributed transaction.

**Naive (broken) approach:**
```kotlin
orderRepository.save(order)         // step 1: DB write
kafkaProducer.send(OrderCreated)    // step 2: may fail or duplicate
```
If step 2 fails or the process crashes between steps, the event is never published. If step 2 is retried, you may publish a duplicate.

**Transactional Outbox (correct):**
```sql
-- Single DB transaction:
INSERT INTO orders (id, status) VALUES (...);
INSERT INTO outbox (id, topic, payload, created_at, sent_at)
  VALUES (uuid, 'order.created', '{"orderId":...}', NOW(), NULL);
-- COMMIT
```
A separate **Outbox Relay** process (e.g., Debezium CDC on the outbox table, or a polling worker) reads unsent rows (`sent_at IS NULL`), publishes to Kafka, marks as sent.

**Why it's correct:**
- DB write and outbox insert are in the same local transaction → atomic. If the service crashes, both succeed or both fail together.
- Relay publishes at-least-once (may publish duplicate on retry). Consumer must be idempotent.
- Debezium uses PostgreSQL WAL (logical replication) to capture INSERT events → zero polling overhead on the application DB.

**Interview trap:** "Can I use a two-phase commit instead?" — 2PC requires a coordinator that holds locks across services, blocking all participants while waiting for votes. Under failures, it can leave the system in a blocked state indefinitely. 2PC is impractical across independent microservices and databases. Outbox + idempotent consumers is the production-grade replacement.
**Tags:** saga, choreography, orchestration, transactional-outbox, distributed-transactions, microservices, debezium, cdc, idempotency
