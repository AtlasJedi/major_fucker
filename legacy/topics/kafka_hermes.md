# Kafka + Hermes — bank pytań

> Apache Kafka is the distributed log backbone for event-driven systems at Allegro. Hermes is Allegro's own open-source pub/sub platform that wraps Kafka behind a REST interface — publishers POST JSON/Avro over HTTP, Hermes stores messages in Kafka, and Hermes Consumers push batches to subscriber HTTP endpoints. For a Kotlin developer on the recommendations team, Kafka fundamentals explain *why* things behave the way they do, and Hermes internals explain the contract you code against every day.

## Scope

- Kafka: broker, topic, partition, offset, consumer group, replication, producer acks, compaction
- Hermes: architecture (Frontend / Consumers / Management), delivery semantics, subscription policies, schema registry, DLQ
- Idempotency patterns for at-least-once delivery
- Kotlin code for Hermes publishers and Spring subscribers
- Trade-off analysis vs raw Kafka and vs other pub/sub systems

---

## Q-KH-001 [bloom: recall]
**Question:** Define the following Kafka primitives in one sentence each: broker, topic, partition, offset, consumer group.

**Model answer:**
- **Broker** — a single Kafka server process that stores partitions and handles produce/fetch requests. A cluster is multiple brokers.
- **Topic** — a named, durable log that producers append records to and consumers read from. A topic is split into one or more partitions.
- **Partition** — an ordered, immutable sequence of records within a topic. Ordering is guaranteed only within a partition, not across partitions of the same topic.
- **Offset** — the position (monotonically increasing integer) of a record within a partition. Consumers track offsets to know where they stopped reading.
- **Consumer group** — a named group of consumer instances that divide partition ownership among themselves. Each partition is owned by exactly one consumer in the group at any time, providing parallel, load-balanced consumption.

**Interview trap:** Interviewers ask "can Kafka guarantee global message ordering?" — the answer is no, only per-partition ordering. If you need total order, you must use a single-partition topic (which kills parallelism) or encode ordering into the business logic.

**Tags:** kafka, fundamentals, topic, partition, consumer-group

---

## Q-KH-002 [bloom: recall]
**Question:** How does Kafka decide which partition a producer message lands in?

**Model answer:**
Three modes:

1. **Key present, default partitioner** — Kafka hashes the key (murmur2 by default) and takes `hash(key) % numPartitions`. All records with the same key always go to the same partition, preserving per-key ordering.
2. **No key (null key)** — prior to Kafka 2.4 this was strict round-robin across partitions. Since 2.4 the default is the *sticky partitioner*: fill a batch for one partition, then switch. This reduces latency vs pure round-robin.
3. **Custom partitioner** — implement `Partitioner` and configure `partitioner.class` on the producer. Useful when you want semantic routing (e.g., route by region).

Practical implication: if you need ordering for a user's events, always set a key (e.g., `userId`). If you don't care about ordering and want maximum throughput, leave the key null.

**Interview trap:** "Does adding more partitions after the fact break key routing?" — yes. Because the number of partitions changes, `hash(key) % numPartitions` resolves to a different partition for existing keys. Hot data (e.g., a popular product ID) will silently move. Repartitioning must be planned carefully.

**Tags:** kafka, partitioning, producer, key

---

## Q-KH-003 [bloom: recall]
**Question:** What is a consumer group and why is the number of active consumers bounded by the partition count?

**Model answer:**
A consumer group is a set of consumer instances that cooperate to consume a topic. Kafka assigns each partition to exactly one consumer in the group (via the group coordinator and a partition assignment strategy). This means:

- **Parallelism ceiling** = partition count. If you have 12 partitions and 12 consumers, each consumer owns one partition — maximum parallelism.
- **If consumers > partitions**, the extra consumers sit idle. They're hot standbys that take over if an active consumer dies, but they do no work under normal conditions.
- **If consumers < partitions**, each consumer handles multiple partitions, which is fine but reduces isolation.

Scaling tip: when you create a Kafka topic at Allegro/Hermes, choose the initial partition count generously because you can add partitions but not remove them without data re-ordering side effects.

**Interview trap:** "Can two consumers in the same group read the same message?" — No, that's the point of a group. If you need fan-out (every service reads every message), each service must use a **different consumer group**.

**Tags:** kafka, consumer-group, parallelism, scaling

---

## Q-KH-004 [bloom: recall]
**Question:** What is the difference between committed offset and current position in a Kafka consumer, and what is the risk of auto-commit?

**Model answer:**
- **Current position** — the offset of the next record the consumer will fetch. Advances with every `poll()` call.
- **Committed offset** — the offset durably stored in the `__consumer_offsets` topic (or Zookeeper in old setups), marking "I have processed everything up to here." On restart or rebalance, a consumer resumes from the committed offset, not the in-memory position.

**Auto-commit risk (`enable.auto.commit=true`):** Kafka commits at a configurable interval (default 5 s). If the app fetches records, begins processing, and crashes before the interval fires, the offset is never committed. On restart, those records are redelivered — that's fine, you get at-least-once. But if the app commits *before* processing is actually complete (which auto-commit can do if you call `poll()` again), you get at-most-once: the record is committed but never fully processed.

**Best practice for reliability:** use `enable.auto.commit=false` and commit manually after you've durably persisted the result.

**Interview trap:** "If I set auto-commit to false and crash mid-batch, do I lose data?" — No, records are redelivered. The danger is the *opposite*: if your processing is not idempotent, you will process them twice.

**Tags:** kafka, offset, auto-commit, at-least-once

---

## Q-KH-005 [bloom: recall]
**Question:** Explain replication factor and ISR (in-sync replicas) in Kafka.

**Model answer:**
**Replication factor** — how many broker copies each partition has. A factor of 3 means one leader partition and two follower replicas. Typical production setting.

**ISR (In-Sync Replicas)** — the subset of replicas that are fully caught up with the leader (within `replica.lag.time.max.ms`). The leader tracks ISR membership:
- A follower that falls too far behind is removed from ISR.
- A follower that catches up is added back.

**Why ISR matters for durability:**
- When `acks=all`, the producer waits for acknowledgment from all ISR members, not all replicas. If ISR shrinks to 1 (just the leader) because followers lag, `acks=all` offers no stronger durability guarantee than `acks=1` — unless you also set `min.insync.replicas=2` on the topic, which forces at least 2 ISR members before the produce succeeds (the produce blocks or errors out otherwise).

**Interview trap:** "`acks=all` does NOT mean all physical replicas acknowledged — it means all *currently ISR* replicas acknowledged." This distinction breaks a lot of "I thought it was safe" assumptions.

**Tags:** kafka, replication, ISR, durability

---

## Q-KH-006 [bloom: recall]
**Question:** What do producer acknowledgment modes `acks=0`, `acks=1`, and `acks=all` mean? What do you trade in each case?

**Model answer:**
| `acks` | Durability | Latency | Risk |
|--------|-----------|---------|------|
| `0` (fire-and-forget) | None — producer doesn't wait | Lowest | Message lost if broker crashes before writing |
| `1` | Leader writes to disk | Low | Message lost if leader crashes before followers replicate |
| `all` (or `-1`) | All ISR members write | Highest | Produce can block/fail if ISR shrinks; requires `min.insync.replicas > 1` to be meaningful |

**When to use what:**
- `acks=0` — metrics, high-frequency telemetry where occasional loss is acceptable.
- `acks=1` — most internal async events where throughput matters and rare loss is tolerable.
- `acks=all` + `min.insync.replicas=2` — financial events, order placement, anything where loss is unacceptable.

Hermes configures topic durability for you based on subscription class; you rarely set this directly, but you must understand it to reason about why Hermes guarantees at-least-once and not exactly-once.

**Interview trap:** "Can I lose data with `acks=all`?" — Yes, if `min.insync.replicas=1` (default), a single-broker ISR means `acks=all` degrades to `acks=1` semantics.

**Tags:** kafka, producer, acks, durability

---

## Q-KH-007 [bloom: recall]
**Question:** What is a compacted topic in Kafka and when would you use one?

**Model answer:**
A **compacted topic** has `cleanup.policy=compact`. Instead of deleting records by age/size (default `delete` policy), Kafka retains the **last record per key** indefinitely (or until a tombstone — a record with a null value — is written for that key).

**Use cases:**
- **Change data capture (CDC)** — keep the latest state of each database row by primary key.
- **Event sourcing materialized views** — a compacted topic acts as a distributed key-value store. New consumers can replay it to rebuild state.
- **Configuration distribution** — store the latest config per service ID; new instances start by consuming the whole compact log to hydrate their state.

**Contrast with regular topics:** regular topics keep all records within the retention window; compacted topics guarantee the latest value per key survives forever.

**Interview trap:** "Does compaction happen immediately?" — No. Compaction runs asynchronously in the background. There is always a "dirty" section of uncompacted records. Your consumer may see multiple records for the same key; always handle the latest-wins logic.

**Tags:** kafka, compaction, cleanup-policy, log-compaction

---

## Q-KH-008 [bloom: recall]
**Question:** Describe what Hermes is in one paragraph and name its three main components.

**Model answer:**
Hermes (github.com/allegro/hermes) is Allegro's open-source pub/sub messaging platform that wraps Apache Kafka behind a REST interface, decoupling publishers and subscribers from the operational complexity of native Kafka clients. Instead of managing Kafka consumer groups, offset commits, and serialization libraries directly, teams publish via a simple HTTP POST and subscribe by exposing an HTTP endpoint. Hermes handles durable storage, fan-out, delivery retries, and back-pressure internally.

**Three main components:**

1. **Hermes Frontend** — the ingestion gateway. Publishers POST JSON or Avro payloads to `https://hermes/topics/{topicName}`. Frontend validates the schema, writes to Kafka, and returns `201 Created` synchronously.
2. **Hermes Consumers** — the delivery engine. Consumes from Kafka and pushes batches of messages to subscriber HTTP endpoints. Manages retry logic, back-pressure, rate limiting, and dead-letter handling.
3. **Hermes Management** — the control plane. REST API + Web UI for creating topics, registering subscriptions, managing schema registry, inspecting subscription health and lag.

**Interview trap:** "Is Hermes a replacement for Kafka?" — No. Kafka is still the durable store underneath. Hermes is an abstraction layer and operational platform on top of it.

**Tags:** hermes, architecture, components, fundamentals

---

## Q-KH-009 [bloom: recall]
**Question:** When would you use Avro instead of JSON in Hermes, and what role does the schema registry play?

**Model answer:**
**JSON** — quick to start, human-readable, schema-optional. Good for low-volume internal tools, debugging, or when the consumer is a webhook destination that already expects JSON with no schema enforcement.

**Avro** — binary encoding with a mandatory schema attached. Use it when:
- Message volume is high and payload size matters (Avro is 3–10x smaller than equivalent JSON).
- You need schema evolution guarantees (backward/forward compatibility enforced at publish time).
- Multiple services consume the same topic and you can't afford silent schema drift.

**Schema registry role:** Hermes integrates with a schema registry (typically Confluent-compatible). When a publisher posts to an Avro topic:
1. Hermes Frontend fetches the topic's registered schema from the registry.
2. It validates the payload against the schema. Invalid payloads are rejected with `400 Bad Request`.
3. The schema ID is embedded in the Kafka record so consumers know which schema version to use for deserialization.

This gives you a contract: producers and consumers agree on the schema version, and breaking changes are caught before they reach production.

**Interview trap:** "Can you publish JSON to an Avro topic?" — No. Hermes enforces the topic's declared content type. You'll get a 400 at the Frontend.

**Tags:** hermes, avro, json, schema-registry

---

## Q-KH-010 [bloom: recall]
**Question:** What delivery semantics does Hermes provide and why can retries cause duplicate processing?

**Model answer:**
Hermes guarantees **at-least-once delivery**. A message is delivered to the subscriber at least once; it may be delivered more than once.

**Why duplicates happen:**
1. Hermes Consumers POST a batch to the subscriber endpoint.
2. The subscriber processes the batch and persists results.
3. Before the subscriber responds (or while the response is in-flight), a network timeout occurs.
4. From Hermes's perspective, the delivery failed. It retries.
5. The subscriber processes the same batch again.

This is not a bug — it's an intentional design choice. At-least-once with retries is far simpler to implement at scale than exactly-once (which requires distributed transactions across Kafka and the subscriber's data store).

**Consequence:** every Hermes subscriber endpoint MUST be idempotent. Processing the same message twice must produce the same result as processing it once. This is a hard contract.

**Interview trap:** "Can Hermes guarantee exactly-once?" — No, and be suspicious of any system claiming exactly-once across a network boundary without 2PC. Even Kafka's transactional exactly-once is scoped to within-Kafka reads and writes, not to external systems.

**Tags:** hermes, delivery-semantics, at-least-once, idempotency, retries

---

## Q-KH-011 [bloom: understand]
**Question:** Explain idempotent producer and transactional producer in Kafka. What does each give you and where do their guarantees stop?

**Model answer:**
**Idempotent producer (`enable.idempotence=true`):**
The broker assigns the producer a `PID` (producer ID) and tracks a sequence number per partition. If the broker receives a duplicate record (same PID + sequence), it deduplicates silently. This prevents duplicates caused by producer retries (network errors, broker failover). Guarantee: **exactly-once delivery per partition within a single producer session**. If the producer restarts (new PID), deduplication is lost for that producer's history.

**Transactional producer (`transactional.id=...`):**
Extends idempotence across multiple partitions and topics in a single atomic transaction. The producer calls `beginTransaction()`, sends records to multiple topics/partitions, then `commitTransaction()` (or `abortTransaction()`). Consumers configured with `isolation.level=read_committed` only see records from committed transactions.

**Where guarantees stop:**
Both mechanisms are scoped to **within Kafka** — produce-to-consume within the Kafka cluster. Once Hermes Consumers push the record to a subscriber's HTTP endpoint, Kafka's transactional guarantees don't protect you. The subscriber's database is outside the transaction boundary. This is why Hermes is at-least-once and why subscriber idempotency is mandatory.

**Interview trap:** "Does `acks=all` + idempotent producer give you exactly-once end-to-end?" — No. Exactly-once to an external system requires idempotent write logic on the consumer side. Kafka's guarantees are Kafka-internal only.

**Tags:** kafka, idempotent-producer, transactions, exactly-once

---

## Q-KH-012 [bloom: understand]
**Question:** Why do partitions exist? Explain both the throughput scaling argument and the ordering guarantee they provide.

**Model answer:**
**Throughput scaling:**
A single sequential log on a single broker has bounded I/O throughput. Partitions allow a topic to be spread across multiple brokers, each serving a subset of the partitions. Producers and consumers operate on partitions in parallel. A topic with 12 partitions across 3 brokers can saturate 4x the broker I/O of a single-partition topic (roughly — network and coordinator overhead apply). This is why the partition count is the primary throughput knob.

**Ordering guarantee:**
Within a single partition, records are strictly ordered by offset. Kafka appends to the partition log sequentially and consumers read in offset order. This is an absolute guarantee. However, across partitions of the same topic, there is no ordering guarantee. If you need all events for `userId=42` to be processed in order, you must key all those events with `userId=42` so they land in the same partition via consistent hashing.

**Implication for recommendation events:** impression events for a user should be keyed by `userId` if downstream processing requires per-user ordering (e.g., computing session sequences). If you only need aggregate statistics, keying is optional and load-balancing may be preferable.

**Interview trap:** "Can I have ordering across multiple users on the same partition?" — Yes, but inter-user ordering is incidental, not guaranteed by Kafka contract. Only intra-key ordering is guaranteed.

**Tags:** kafka, partition, ordering, throughput, scaling

---

## Q-KH-013 [bloom: understand]
**Question:** Compare Hermes push-over-HTTP vs native Kafka pull from the subscriber's perspective. Go deep on the consumer-side operational mechanics — what you gain and what you give up with each model.

**Model answer:**
**Native Kafka pull (consumer group):**
The consumer process owns its offset, calls `poll()` in a loop, deserializes Avro, commits offsets. Advantages: fine-grained control over fetch size, offset management, pause/resume per partition, exactly-once-within-Kafka with transactions, batch size tuning per consumer. Disadvantages: every service must run a JVM Kafka client with correct configs, handle rebalances, manage schema registry clients, run offset monitoring, operate consumer group lag alerting. A fleet of 50 microservices each consuming 10 topics = 50 consumer groups to monitor, lag to track, rebalance storms to debug.

**Hermes push:**
Hermes owns the Kafka consumer group on behalf of all subscribers. Your service exposes one HTTP endpoint and receives POST batches. Advantages: no Kafka client in your service, no offset management, no rebalance handling, subscription health visible in Hermes Management UI, rate limiting and back-pressure configured declaratively (not in code), retry policy centralized. Disadvantages: you lose partition-level control (you can't pause a specific partition from your service), per-message latency has an extra HTTP hop, HTTP endpoint must handle concurrent POSTs from Hermes Consumers, back-pressure on the subscriber propagates as HTTP 5xx/429 responses which Hermes must interpret correctly.

**Critical consumer-side mechanic difference:** In native pull, your consumer controls the pace — slow down `poll()` and you apply back-pressure to Kafka directly. In Hermes push, your service signals back-pressure by returning `429 Too Many Requests` or `503`; Hermes respects `inflightSize` and `rate` subscription limits to throttle itself. If your service is slow and you return `200 OK` anyway, Hermes has no visibility into your internal queue depth — you must return the right status codes.

**Interview trap:** "If my Hermes subscriber is slow, does it slow down other subscribers on the same topic?" — In Hermes, each subscription is a separate Kafka consumer group internally, so a slow subscriber does not block others. This is a key operational advantage over a single shared consumer group.

**Tags:** hermes, kafka, push-vs-pull, consumer-mechanics, back-pressure

---

## Q-KH-014 [bloom: understand]
**Question:** Explain each Hermes subscription policy field and how they interact: `rate`, `inflightSize`, `messageTtl`, `requestTimeout`, `retryClientErrors`, and the backoff strategy.

**Model answer:**
- **`rate`** — maximum message delivery rate in messages per second to the subscriber endpoint. Hermes Consumers throttle delivery to respect this ceiling. Set too low: unnecessary lag. Set too high: subscriber overload.
- **`inflightSize`** — maximum number of messages in-flight (POSTed but not yet acknowledged) at a time per subscription. This is the concurrency window. If `inflightSize=100` and 100 messages are pending ACK, Hermes pauses dispatching new messages. Primary back-pressure knob.
- **`messageTtl`** — time-to-live for a message in seconds from when it was published. If the message has not been successfully delivered within TTL, it is moved to the dead-letter queue (DLQ). Prevents unbounded retry storms for messages that have already expired in business terms.
- **`requestTimeout`** — HTTP timeout for a single POST to the subscriber. If the subscriber doesn't respond within this window, Hermes treats the delivery as failed and retries. Must be < `messageTtl`. A too-short `requestTimeout` with a slow subscriber causes unnecessary retries and duplicate processing.
- **`retryClientErrors`** — boolean. If `true`, Hermes retries on 4xx responses. If `false` (recommended default), 4xx is treated as permanent failure (the message is dropped or DLQ'd depending on config). You should set this to `false` unless you intentionally want Hermes to retry on subscriber-side logic errors, which usually indicates a bug.
- **Backoff strategy** — controls the wait between retries. Typically exponential with jitter: `initialDelay * 2^attempt + jitter`. Prevents thundering herd when a subscriber recovers after downtime.

**Interaction gotcha:** if `requestTimeout=30s` and `messageTtl=60s`, a message that times out will be retried at most once before TTL expires. Tune these together.

**Interview trap:** "What happens if `retryClientErrors=true` and your subscriber has a bug that returns 400 for all messages?" — Hermes retries the message until TTL expires, pounding your subscriber with invalid requests and filling the retry queue. Always set `retryClientErrors=false` for production subscriptions.

**Tags:** hermes, subscription-policy, rate-limiting, retry, ttl

---

## Q-KH-015 [bloom: understand]
**Question:** How does dead-letter handling work in Hermes? When do messages end up in the DLQ and how do you inspect or replay them?

**Model answer:**
**When messages go to DLQ:**
1. Delivery to the subscriber returns a non-retryable error (4xx with `retryClientErrors=false`).
2. `messageTtl` expires before successful delivery.
3. Maximum retry count exceeded (if a retry limit is configured).

**DLQ mechanics:** Hermes stores dead-lettered messages on a special Kafka topic: `${originalTopic}.${subscriptionName}.DLQ` (naming may vary by Hermes version). The message body and headers (including original publish timestamp, attempt count, last error) are preserved.

**Inspecting:** The Hermes Management UI shows DLQ depth per subscription and allows browsing individual messages. Via the Management API you can `GET /topics/{topic}/subscriptions/{sub}/undelivered/last100` (or similar endpoint) to fetch recent DLQ entries with full diagnostic metadata.

**Replaying:** Hermes Management provides a retry endpoint — `PUT /topics/{topic}/subscriptions/{sub}/undelivered/retransmit` — which re-enqueues DLQ messages for delivery. This is the standard way to replay after fixing a subscriber bug. Replay inherits the same subscription policy (rate, inflightSize) so you won't accidentally flood a recovering service.

**Interview trap:** "Can you replay indefinitely?" — Replaying re-runs through the subscription policy including TTL. If the DLQ message's original TTL has expired, replaying may immediately re-DLQ unless the subscription TTL is extended first or TTL is measured from replay time (check Hermes version behavior).

**Tags:** hermes, dlq, dead-letter, replay, subscription

---

## Q-KH-016 [bloom: understand]
**Question:** Explain Avro schema evolution: what are backward, forward, and full compatibility? Which changes are safe and which break compatibility?

**Model answer:**
Avro schemas evolve across versions. Compatibility rules govern whether a new schema version can be read by old consumers or whether old producers can write to a new schema.

**Backward compatible** (new schema can read data written with old schema): you can add optional fields (with defaults) and remove optional fields. Consumers upgraded to the new schema can still process old messages.

**Forward compatible** (old schema can read data written with new schema): you can add optional fields. Old consumers simply ignore unknown fields.

**Full compatible** (both backward and forward): only add optional fields with defaults. This is the safest and recommended default for production Avro topics.

**Safe changes:**
- Add a field with a `"default"` value.
- Add a new enum value (in some registries, only forward-compatible).
- Rename a field using `"aliases"`.

**Breaking changes:**
- Remove a required field (no default) — old consumers fail to deserialize.
- Change a field's type (e.g., `int` → `string`).
- Add a required field (no default) — old producers can't produce valid messages.
- Rename a field without adding an alias.

In Hermes, the schema registry enforces the topic's configured compatibility level at publish time. A publisher posting a schema-breaking payload gets `400 Bad Request` before the message ever reaches Kafka.

**Interview trap:** "Can I rename a field safely?" — Only if you add the old name as an alias in the new schema: `"aliases": ["oldName"]`. Without the alias, it's a breaking change.

**Tags:** avro, schema-evolution, compatibility, schema-registry

---

## Q-KH-017 [bloom: apply]
**Question:** Write a Kotlin Spring REST controller that acts as a Hermes subscription endpoint. It receives a batch of `UserImpressionEvent` Avro-deserialized objects as JSON, persists them idempotently, and returns the correct HTTP status codes for the Hermes retry contract.

**Model answer:**
```kotlin
data class UserImpressionEvent(
    val eventId: String,       // UUID — natural idempotency key
    val userId: Long,
    val productId: Long,
    val timestamp: Instant
)

@RestController
@RequestMapping("/hermes/user-impression")
class UserImpressionConsumer(
    private val repository: ImpressionRepository
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun handleBatch(@RequestBody events: List<UserImpressionEvent>): ResponseEntity<Void> {
        return try {
            repository.insertIgnoreDuplicates(events)  // idempotent upsert
            ResponseEntity.ok().build()                 // 200 = delivered, do not retry
        } catch (e: PermanentBusinessException) {
            // 4xx = permanent failure, Hermes should NOT retry if retryClientErrors=false
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build()  // 422
        } catch (e: TransientException) {
            // 5xx = transient, Hermes WILL retry
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()   // 503
        }
    }
}

// Repository: idempotent insert using PostgreSQL ON CONFLICT DO NOTHING
@Repository
class ImpressionRepository(private val jdbcTemplate: JdbcTemplate) {
    fun insertIgnoreDuplicates(events: List<UserImpressionEvent>) {
        val sql = """
            INSERT INTO user_impressions (event_id, user_id, product_id, event_ts)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
        """.trimIndent()
        jdbcTemplate.batchUpdate(sql, events.map { e ->
            arrayOf(e.eventId, e.userId, e.productId, e.timestamp)
        })
    }
}
```

Key decisions:
- `ON CONFLICT (event_id) DO NOTHING` — idempotency at the DB layer. Re-delivery of the same `eventId` is a no-op.
- Return `200` for success (or partial success — duplicates silently skipped).
- Return `422` for permanent business errors — `retryClientErrors=false` means Hermes will not retry; message goes to DLQ.
- Return `503` for transient errors — Hermes will retry with backoff.
- Never return `200` if processing failed. Never return `500` if the failure is a business logic error (that would cause infinite retries).

**Interview trap:** "What if you return `200` but some events in the batch failed?" — Hermes does not support partial batch ACK. The entire batch is either delivered (`200`) or retried. Design your idempotency so that partial success is safe to retry: skip already-processed events, process new ones.

**Tags:** hermes, kotlin, spring, subscriber, idempotency, http-contract

---

## Q-KH-018 [bloom: apply]
**Question:** You need to configure a Hermes subscription for a high-volume analytics ingest: 5,000 messages per second, the downstream system can tolerate up to 1 hour of delivery delay, and 4xx responses from the subscriber must NOT trigger retries. Show the subscription policy fields you would set and explain each choice.

**Model answer:**
```json
{
  "subscriptionPolicy": {
    "rate": 5500,
    "inflightSize": 1000,
    "messageTtl": 7200,
    "requestTimeout": 15000,
    "retryClientErrors": false,
    "backoff": {
      "initialDelay": 1000,
      "multiplier": 2,
      "maxDelay": 60000
    }
  }
}
```

Rationale:
- **`rate: 5500`** — 10% headroom above the 5k/s target to handle burst spikes without hitting the ceiling immediately.
- **`inflightSize: 1000`** — with 5k/s and ~200ms subscriber response time, you need `rate × latency = 5000 × 0.2 = 1000` in-flight slots to saturate throughput. Tune based on observed p99 latency.
- **`messageTtl: 7200`** — 2 hours (7200 seconds). The business tolerates 1-hour delay, so 2 hours gives headroom for a subscriber outage before DLQ. Do not set exactly 3600 — you want margin.
- **`requestTimeout: 15000`** — 15 seconds per POST. For batch analytics ingestion, a generous timeout prevents spurious retries when the subscriber is temporarily slow. Must be well below TTL.
- **`retryClientErrors: false`** — required. 4xx from the subscriber is a permanent error (bad data, schema mismatch, authorization). Retrying would waste capacity and fill DLQ faster.
- **Exponential backoff with jitter** — prevents thundering herd on subscriber recovery. `maxDelay: 60000` (60s) means worst-case 1 retry per minute per message.

**Interview trap:** "Why not set `rate` to exactly 5000?" — Because Hermes enforces the rate limit strictly. Any burst spike above 5000 is throttled. 10% headroom absorbs natural traffic variance.

**Tags:** hermes, subscription-policy, high-throughput, analytics, configuration

---

## Q-KH-019 [bloom: apply]
**Question:** Design an idempotency scheme for a Hermes consumer where the business key is `(userId, productId, timestamp)`. Show the schema and insert logic for both PostgreSQL and Cassandra.

**Model answer:**
**Why a composite business key:** `eventId` UUID is ideal when producers set it. If the event schema has no UUID, `(userId, productId, timestamp)` is the natural deduplication key — it means "this specific user viewed this specific product at this exact millisecond."

**PostgreSQL:**
```sql
CREATE TABLE product_impressions (
    user_id     BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    event_ts    TIMESTAMPTZ NOT NULL,
    raw_payload JSONB,
    inserted_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, product_id, event_ts)
);
```
```kotlin
fun insertIgnoreDuplicates(events: List<UserImpressionEvent>) {
    val sql = """
        INSERT INTO product_impressions (user_id, product_id, event_ts, raw_payload)
        VALUES (?, ?, ?, ?::jsonb)
        ON CONFLICT (user_id, product_id, event_ts) DO NOTHING
    """.trimIndent()
    jdbcTemplate.batchUpdate(sql, events.map { e ->
        arrayOf(e.userId, e.productId, e.timestamp, objectMapper.writeValueAsString(e))
    })
}
```

**Cassandra:**
```cql
CREATE TABLE product_impressions (
    user_id    BIGINT,
    product_id BIGINT,
    event_ts   TIMESTAMP,
    payload    TEXT,
    PRIMARY KEY ((user_id, product_id), event_ts)
) WITH default_time_to_live = 604800;  -- 7 days TTL
```
```kotlin
fun insertIgnoreDuplicates(events: List<UserImpressionEvent>) {
    // Cassandra LWT: IF NOT EXISTS — lightweight transaction for idempotency
    val ps = session.prepare("""
        INSERT INTO product_impressions (user_id, product_id, event_ts, payload)
        VALUES (?, ?, ?, ?) IF NOT EXISTS
    """)
    events.forEach { e ->
        session.execute(ps.bind(e.userId, e.productId, e.timestamp, objectMapper.writeValueAsString(e)))
    }
}
```

**Cassandra caveat:** `IF NOT EXISTS` uses a lightweight transaction (Paxos) — it has ~5-10x higher latency than a regular insert. For true high-volume ingestion (5k/s), prefer idempotent writes without LWT: since Cassandra last-write-wins on the same primary key with the same timestamp, a duplicate write with identical data is naturally idempotent without LWT (at the cost of not knowing whether the row was new).

**Interview trap:** "What if two events have the same `(userId, productId)` but different timestamps that collide due to clock skew?" — They are different events. Cassandra treats them as separate rows (different clustering key). PostgreSQL inserts both. Clock skew produces false negatives (failed dedup), not false positives — generally acceptable for analytics.

**Tags:** hermes, idempotency, postgresql, cassandra, consumer-design

---

## Q-KH-020 [bloom: apply]
**Question:** Write a Kotlin coroutine-friendly function that POSTs an event to Hermes with retry + timeout, returning `Result<Unit>`. Map 4xx to non-retryable failure and 5xx to retryable failure.

**Model answer:**
```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlin.math.min

sealed class HermesError : Exception() {
    data class Permanent(override val message: String) : HermesError()  // 4xx — do not retry
    data class Transient(override val message: String) : HermesError()  // 5xx / network — retry
}

suspend fun postToHermes(
    client: HttpClient,
    topicUrl: String,
    payload: String,
    maxAttempts: Int = 3,
    timeoutMs: Long = 5_000L
): Result<Unit> {
    var attempt = 0
    var delay = 1_000L
    while (attempt < maxAttempts) {
        attempt++
        val result = runCatching {
            withTimeout(timeoutMs) {
                client.post(topicUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            }
        }
        when {
            result.isFailure -> {
                // Network error / timeout — transient
                if (attempt == maxAttempts) return Result.failure(HermesError.Transient("Network error after $attempt attempts: ${result.exceptionOrNull()?.message}"))
            }
            else -> {
                val response = result.getOrThrow()
                when (response.status.value) {
                    in 200..299 -> return Result.success(Unit)
                    in 400..499 -> return Result.failure(
                        HermesError.Permanent("4xx permanent error: ${response.status} — ${response.bodyAsText()}")
                    )
                    else -> {
                        // 5xx — transient, retry
                        if (attempt == maxAttempts) return Result.failure(
                            HermesError.Transient("5xx after $attempt attempts: ${response.status}")
                        )
                    }
                }
            }
        }
        delay(delay)
        delay = min(delay * 2, 30_000L)  // exponential backoff, cap 30s
    }
    return Result.failure(HermesError.Transient("Exhausted $maxAttempts attempts"))
}
```

Key points:
- `withTimeout(timeoutMs)` — per-attempt timeout, not total timeout. Prevents a single hung request from blocking all retries.
- `4xx` → `HermesError.Permanent` → not retried, caller logs and discards (or alerts).
- `5xx` / network failure → `HermesError.Transient` → retry with exponential backoff.
- Jitter not shown for brevity — add `delay += Random.nextLong(0, delay / 2)` in production.
- `runCatching` wraps network exceptions without try-catch clutter.

**Interview trap:** "Should you retry on `429 Too Many Requests`?" — Yes, 429 is a transient condition (rate limit), but respect the `Retry-After` header if present. Treating it as 4xx permanent would drop valid messages.

**Tags:** hermes, kotlin, coroutines, publisher, retry, result

---

## Q-KH-021 [bloom: apply]
**Question:** Below is a Hermes consumer implementation. Identify all the bugs.

```kotlin
@RestController
class ImpressionConsumer(
    private val db: Database,
    private val kafkaProducer: KafkaProducer<String, String>
) {
    @PostMapping("/hermes/impressions")
    suspend fun handle(@RequestBody events: List<ImpressionEvent>): ResponseEntity<Void> {
        events.forEach { event ->
            val existing = withContext(Dispatchers.IO) { db.find(event.productId) }
            if (existing == null) {
                withContext(Dispatchers.IO) { db.save(event) }
            }
        }
        return ResponseEntity.ok().build()
    }
}
```

**Model answer:**
Four bugs:

**1. Idempotency is keyed on `productId` alone, not on the full event key.**
`db.find(event.productId)` checks if *any* event for that product exists, not if *this specific event* was already processed. The first impression for `productId=42` is stored. All subsequent impressions for `productId=42` — including legitimate new ones from different users — are silently dropped. Correct key: `(userId, productId, eventId)` or a stable event UUID.

**2. Race condition — no DB-level uniqueness constraint.**
Two concurrent Hermes POSTs can both call `find()`, both get `null`, and both call `save()` — resulting in duplicate rows. The find-then-insert pattern is not atomic. Fix: use `INSERT ... ON CONFLICT DO NOTHING` (PostgreSQL) or an equivalent atomic upsert. Never do read-then-write for deduplication.

**3. No dead-letter handling for poison pills.**
If a single malformed `event` causes `db.save()` to throw a `DataException`, the entire batch returns `500` and Hermes retries the whole batch indefinitely until TTL. The malformed event blocks all others. Fix: catch per-event errors, log the bad event to a DLQ topic or dead-letter store, and continue processing the rest. Return `200` if the good events succeeded; poison pill events should not cause infinite retries.

**4. `suspend` function with `withContext(Dispatchers.IO)` in a Spring MVC controller — incorrect threading model.**
`@RestController` in Spring MVC runs on a Servlet thread pool, not a coroutine dispatcher. Using `suspend` functions requires Spring WebFlux (reactive) or a coroutine-aware dispatcher. In Spring MVC, `suspend` will not be dispatched as a coroutine; it will behave unexpectedly. Fix: either switch to Spring WebFlux + `@RestController` with Coroutine support, or remove `suspend` and use blocking JDBC directly on the Servlet thread pool (acceptable for moderate throughput).

**Interview trap:** "Bug #4 might not crash in tests" — because in tests you often use `runBlocking {}` which creates a coroutine context. The issue surfaces in production Spring MVC where no coroutine dispatcher is present for `@PostMapping` handlers unless explicitly configured.

**Tags:** hermes, consumer, bugs, idempotency, kotlin, coroutines, spring

---

## Q-KH-022 [bloom: analyze]
**Question:** A colleague proposes replacing Hermes with raw Kafka consumers for the recommendations system's high-throughput impression ingest. Analyze the trade-offs in depth: throughput and latency math, schema management, observability, and what you lose at fleet scale.

**Model answer:**
**Throughput and latency:**
Raw Kafka consumers eliminate the Hermes HTTP hop. Hermes Frontend → Kafka → Hermes Consumers → HTTP POST to subscriber adds at minimum one round-trip (100–300ms latency depending on batch flush intervals). A native Kafka consumer fetching directly from the broker can achieve sub-10ms end-to-end latency within the same datacenter. For impression ingest at 50k/s, raw Kafka can sustain higher throughput per instance because you avoid HTTP overhead, JSON body parsing in Hermes Frontend, and the Hermes Consumer fan-out. At 5k/s (the given use case), Hermes's overhead is negligible and well within its design envelope.

**Schema management:**
Raw Kafka requires every consuming service to embed an Avro deserializer + schema registry client, manage cache invalidation, handle schema version negotiation, and write schema-evolution-aware deserialization code. With Hermes, schema validation and evolution enforcement happen at the Frontend, invisible to consumers. With raw Kafka, a schema-breaking change can silently corrupt data in a downstream service if it doesn't validate schema versions. At fleet scale (20+ services consuming the impressions topic), this means 20 independent points of schema failure. Hermes centralizes this to one.

**Observability:**
Hermes Management provides per-subscription lag, DLQ depth, retry rate, and throughput metrics out of the box with no instrumentation required. With raw Kafka consumers, you need consumer group lag monitoring (e.g., Burrow, or Kafka's own JMX metrics), per-service dashboards, and manual alerting on consumer group disappearance (rebalance storms, dead consumers). In a 20-service fleet, this is a significant operational burden. You also lose Hermes's per-subscription DLQ — you must implement your own dead-letter logic.

**What you lose at fleet scale:**
- **Delivery policy centralization** — rate limits, TTLs, retry counts are per-subscription in Hermes Management. With raw Kafka, these are per-service config files, potentially inconsistent.
- **Multi-tenancy isolation** — each Hermes subscription is an independent consumer group. A slow subscriber doesn't affect others. With raw Kafka consumer groups, a consumer that holds a lock or blocks `poll()` can cause group rebalances affecting co-located consumers.
- **HTTP endpoint simplicity** — new services need no Kafka client dependency. At Allegro scale (hundreds of services), avoiding a heavy Kafka client in every service is significant for startup time and dependency management.

**Verdict for impression ingest:** raw Kafka is justified **only** if you have hard latency SLOs below ~50ms or throughput requirements that exceed Hermes's rate limits. For analytics ingest tolerating 1-hour delay, Hermes wins on operational simplicity by a wide margin.

**Interview trap:** "Native Kafka is always faster" — true in theory but for batch analytics the extra 100ms from Hermes is irrelevant. The operational cost of 20 raw Kafka consumer groups monitored by a team of 6 engineers dwarfs the throughput gain.

**Tags:** hermes, kafka, trade-offs, fleet-scale, recsys, observability

---

## Q-KH-023 [bloom: analyze]
**Question:** Hermes Consumers experience back-pressure when one slow subscriber slows down delivery to others. Analyze the failure mode and how Hermes mitigates it (referencing the concept of dynamic workload balancing).

**Model answer:**
**Failure mode without workload balancing:**
Hermes Consumers run as a fleet of worker threads, each assigned to deliver messages for one or more subscriptions. In a naive static assignment, if subscription A has a slow subscriber (high latency, many retries, frequently returning 503), the worker thread assigned to A spends most of its time waiting. Meanwhile, subscription B (a healthy, fast subscriber) is also assigned to the same worker thread and starves — it doesn't get dispatch time even though it could deliver instantly. At scale with hundreds of subscriptions, a handful of "hot" slow subscriptions can monopolize the worker pool, causing artificial lag for healthy subscriptions.

**Dynamic workload balancing (Allegro's 2023 approach, conceptually):**
The core idea is to continuously measure the delivery rate and per-subscription "weight" (how much CPU/IO it consumes) and dynamically redistribute subscriptions across the worker pool. Rather than static assignment at startup, the scheduler monitors:
- Delivery throughput per subscription (messages/sec actually delivered)
- In-flight slot utilization (is a subscription's `inflightSize` always full, indicating back-pressure?)
- Retry rate and response latency per subscription

Subscriptions consuming disproportionate worker time are identified as "heavy." The scheduler redistributes work so heavy subscriptions don't block light ones. A light subscription (fast subscriber, low retry rate) gets dispatch priority on available workers even if a heavy subscription on the same worker thread is blocked waiting for a retry backoff.

**Practical implication for service developers:**
A Hermes subscriber that is consistently slow (p99 response > requestTimeout) is not just hurting itself — it is a bad citizen in the fleet. The correct response is to: (1) return 503 quickly if overloaded rather than timing out, (2) tune `requestTimeout` and `inflightSize` appropriately, and (3) scale out subscriber instances. Dynamic workload balancing is a mitigation at the platform level, not a substitute for a properly tuned subscriber.

**Interview trap:** "Does dynamic workload balancing fix an overloaded subscriber?" — No. It protects *other* subscriptions from being affected. The overloaded subscriber still experiences lag. The fix for an overloaded subscriber is to scale the subscriber, not to adjust Hermes's scheduler.

**Tags:** hermes, back-pressure, dynamic-workload-balancing, consumers, fleet-scale

---

## Q-KH-024 [bloom: analyze]
**Question:** Compare Hermes to AWS SNS+SQS, Google Cloud Pub/Sub, NATS, and RabbitMQ. Where does Hermes clearly win? Where would you choose a competitor instead?

**Model answer:**
**Hermes vs AWS SNS+SQS:**
SNS+SQS is a pull-based fan-out pattern (SNS distributes to SQS queues; consumers poll SQS). Hermes is push-based. Hermes wins on latency (push vs pull polling delay) and on operational simplicity for HTTP subscribers — no SQS client needed in every service. SNS+SQS wins if you're in an AWS-native stack, need Lambda trigger integration, or need cross-region fan-out without running your own infrastructure. SNS+SQS has no Avro/schema registry integration out of the box.

**Hermes vs Google Cloud Pub/Sub:**
Pub/Sub supports both push (HTTP) and pull modes, making it the closest architectural analog to Hermes. Pub/Sub wins for GCP-native shops, global distribution without operating Kafka, and serverless scalability. Hermes wins when you need to self-host (no vendor lock-in), need fine-grained subscription policies, and are already invested in Kafka. Pub/Sub's schema registry is more limited than a full Confluent-compatible registry.

**Hermes vs NATS (JetStream):**
NATS JetStream is lower-latency (sub-millisecond) and simpler to operate than Kafka. But NATS lacks Kafka's durability guarantees for high-volume persistent logs and has a less mature schema ecosystem. Hermes+Kafka provides stronger at-rest durability and replay capabilities. Choose NATS for IoT, ultra-low-latency control plane events, or microservice RPC. Hermes for durable event logs with complex fan-out.

**Hermes vs RabbitMQ:**
RabbitMQ uses AMQP (TCP push), not HTTP. RabbitMQ wins for complex routing (topic exchanges, header routing, dead-letter exchanges with fine control). Hermes wins for HTTP-native microservice architectures and for services that shouldn't run an AMQP client. RabbitMQ's durability model (quorum queues) is competitive but not Kafka-scale. RabbitMQ has no built-in Avro/schema registry story.

**Where Hermes clearly wins:**
- Large fleet of HTTP microservices that would otherwise each embed a Kafka client.
- Need for centralized subscription policy management (rate, TTL, retry) without per-service config.
- Already running Kafka and want a managed abstraction layer.
- Team prefers REST/HTTP as the integration protocol.

**Where you would NOT pick Hermes:**
- Startup or small team: Hermes has operational overhead (Frontend + Consumer + Management + Zookeeper + Kafka). For 5 services, raw RabbitMQ or NATS is simpler.
- Sub-10ms latency requirements: the HTTP hop kills you.
- Non-Allegro infrastructure where you'd need to self-host the entire Hermes stack from scratch with no institutional knowledge.
- Cloud-native deployment where managed Pub/Sub or SNS+SQS removes operational toil entirely.

**Interview trap:** "Is Hermes production-ready for non-Allegro companies?" — Yes, it's open source and some external companies use it. But its documentation, community, and operational tooling are optimized for Allegro's internal infrastructure. Adopting it externally requires significant investment in runbooks and monitoring.

**Tags:** hermes, comparison, sns-sqs, pubsub, nats, rabbitmq, trade-offs
