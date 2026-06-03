# Kafka & Messaging — question bank

> Apache Kafka is the distributed log backbone for event-driven, high-throughput backend systems. For a senior Java/Kotlin engineer, this topic covers the full stack: broker internals, producer durability guarantees, consumer group mechanics and rebalancing, exactly-once semantics, retention and compaction, schema management, Kafka Streams/KTable, the Transactional Outbox pattern via Debezium, and Hermes — Allegro's open-source REST-over-Kafka pub/sub layer. Interviewers at senior level expect you to reason about trade-offs under failure, not just recite config names.

## Scope

- Topics, partitions, offsets — what they are and why the model exists
- Producer acks (0/1/all), idempotent producer (enable.idempotence), transactions for exactly-once
- Key-to-partition routing, per-partition ordering guarantee and its limits
- Consumer groups, partition assignment, cooperative/sticky rebalancing
- Offset management: auto-commit vs manual, at-most / at-least / exactly-once semantics
- Consumer lag, max.poll.records, backpressure patterns
- Retention: time-based, size-based, log compaction (cleanup.policy=compact)
- Replication: leader/follower, ISR, min.insync.replicas, unclean leader election
- Dead-letter queue (DLQ) patterns in Kafka
- Schema registry and Avro schema evolution (backward/forward/full compatibility)
- Kafka vs RabbitMQ — log vs queue architectural difference
- Kafka Streams and KTable basics
- Hermes (Allegro) — Frontend/Consumers/Management, subscription policy, delivery semantics
- Transactional Outbox via Kafka Connect / Debezium

---

## Q-KAFKA-001 [bloom: recall] [level: junior]
**Question:** Define the following Kafka primitives in one sentence each: broker, topic, partition, offset, consumer group.
**Model answer:**
- **Broker** — a single Kafka server process that stores partitions, handles produce/fetch requests, and participates in the cluster. A cluster is composed of multiple brokers.
- **Topic** — a named, durable log that producers append records to and consumers read from. A topic is split into one or more partitions for parallelism.
- **Partition** — an ordered, immutable sequence of records within a topic. Ordering is guaranteed only within a single partition, never across partitions of the same topic.
- **Offset** — the monotonically increasing integer position of a record within a partition. Consumers track their offset to know where they left off. Offsets are per-partition and start at 0.
- **Consumer group** — a named set of consumer instances that cooperate to consume a topic. The group coordinator assigns each partition to exactly one consumer in the group, providing parallel, load-balanced consumption without duplication within the group.
**Interview trap:** "Can Kafka guarantee global message ordering across a topic?" — No. Only per-partition ordering is guaranteed. A single-partition topic gives global order at the cost of parallelism. If you need total order across a high-throughput topic, encode ordering into the business logic (sequence numbers, vector clocks).
**Tags:** kafka, fundamentals, topic, partition, offset, consumer-group

---

## Q-KAFKA-002 [bloom: recall] [level: junior]
**Question:** What do producer acknowledgment modes `acks=0`, `acks=1`, and `acks=all` mean? What durability/latency trade-off does each represent?
**Model answer:**
| `acks` | Who acknowledges | Durability | Latency |
|--------|-----------------|------------|---------|
| `0` (fire-and-forget) | Nobody — producer doesn't wait | None. Loss if broker crashes before write | Lowest |
| `1` | Leader partition only | Leader wrote to its log | Low |
| `all` (or `-1`) | All ISR members | All in-sync replicas wrote | Highest; can block if ISR shrinks |

**When to use each:**
- `acks=0` — high-frequency telemetry, metrics, click-tracking where occasional loss is acceptable.
- `acks=1` — most internal async events where throughput matters and rare loss is tolerable.
- `acks=all` + `min.insync.replicas=2` — financial events, order placement, anything where message loss is unacceptable.

**Critical nuance:** `acks=all` means all *currently ISR* replicas acknowledged, not all physical replicas. If ISR shrinks to 1 (just the leader), `acks=all` offers no stronger guarantee than `acks=1`. Always pair `acks=all` with `min.insync.replicas >= 2` on the topic/broker.
**Interview trap:** "Can I lose data with `acks=all`?" — Yes, if `min.insync.replicas=1` (the default). A single-member ISR makes `acks=all` semantically equivalent to `acks=1`. The config pair is `acks=all` + `min.insync.replicas=2`.
**Tags:** kafka, producer, acks, durability, min-insync-replicas

---

## Q-KAFKA-003 [bloom: recall] [level: junior]
**Question:** Explain replication factor and ISR (in-sync replicas) in Kafka. Why do both matter for durability?
**Model answer:**
**Replication factor** is how many broker copies each partition has. A factor of 3 means one leader partition plus two follower replicas on different brokers. Typical production setting. RF=1 means no redundancy.

**ISR (In-Sync Replicas)** is the subset of replicas fully caught up with the leader — specifically, within `replica.lag.time.max.ms` (default 30s). The leader maintains the ISR list:
- A follower that falls behind is removed from ISR.
- A follower that catches up is added back.

**Why ISR matters:** When `acks=all`, the leader waits for acknowledgment from all ISR members before responding to the producer. If ISR = {leader, follower1, follower2}, you get true three-copy durability. If follower2 lags out of ISR, `acks=all` only waits for leader + follower1 — still two copies, but the physical third copy is out of sync. This is not a bug; it's a deliberate availability-over-durability trade-off. `min.insync.replicas` is the safeguard that forces the produce to fail rather than accept data into a degraded ISR.
**Interview trap:** "`acks=all` does NOT mean all physical replicas acknowledged — it means all *currently ISR* replicas acknowledged." This distinction breaks most "I thought it was safe" assumptions in interviews.
**Tags:** kafka, replication, isr, durability, min-insync-replicas

---

## Q-KAFKA-004 [bloom: recall] [level: junior]
**Question:** What is a compacted topic in Kafka and when would you use one?
**Model answer:**
A **compacted topic** uses `cleanup.policy=compact`. Instead of deleting records by age or size (the default `delete` policy), Kafka's log cleaner retains the **last record per key** indefinitely. A tombstone — a record with a `null` value — signals deletion of a key; the compaction process eventually removes even that tombstone.

**Use cases:**
- **Change data capture (CDC)** — keep the current state of each database row keyed by primary key.
- **Materialized view hydration** — a new consumer replays the compacted topic to rebuild in-memory state (e.g., a Kafka Streams KTable).
- **Configuration distribution** — latest config per service ID; new instances start by consuming the full compact log.
- **Event sourcing snapshots** — store the latest snapshot per entity ID alongside a regular event topic.

**Contrast with `delete` policy:** `delete` removes records outside the retention window regardless of key. `compact` retains the latest value per key forever (until tombstone). You can combine both: `cleanup.policy=compact,delete` retains the latest value per key AND enforces a time-based floor.
**Interview trap:** "Does compaction happen immediately?" — No. Compaction is asynchronous background work. A "dirty" (uncompacted) section always exists. Consumers may see multiple records for the same key; always apply latest-wins logic and never assume the log is already fully compacted.
**Tags:** kafka, compaction, cleanup-policy, log-compaction, tombstone

---

## Q-KAFKA-005 [bloom: recall] [level: junior]
**Question:** What is consumer lag and why does it matter operationally?
**Model answer:**
**Consumer lag** is the difference between the latest offset in a partition (the log end offset, LEO) and the consumer's committed offset for that partition. It represents how far behind the consumer is — measured in number of unprocessed records.

**Why it matters:**
- **Backlog signal:** Rising lag means the consumer is not keeping up with producer throughput. This can lead to unbounded memory/disk growth on the broker if retention is long.
- **Latency signal:** In event-driven systems, lag = event processing delay. A lag of 100k records at 10k/s = 10 seconds of end-to-end latency.
- **Alerting:** Consumer group lag is the primary SLO metric for async pipelines. Teams typically alert on lag exceeding a threshold (e.g., >50k records or lag growing for >5 minutes).
- **Capacity planning:** Sustained lag tells you the consumer fleet needs horizontal scaling (add instances up to the partition count ceiling).

**Tools:** `kafka-consumer-groups.sh --describe`, Burrow, Confluent Control Center, or JMX metrics (`records-lag-max` on the consumer).
**Interview trap:** "Can lag be zero even when the consumer is slow?" — Yes, if the producer slows down or stops and the consumer catches up. Lag is a relative metric; always look at *lag trend* (growing/stable/shrinking), not a snapshot.
**Tags:** kafka, consumer-lag, monitoring, backpressure, slo

---

## Q-KAFKA-006 [bloom: recall] [level: junior]
**Question:** What is Hermes and what are its three main components?
**Model answer:**
**Hermes** (github.com/allegro/hermes) is Allegro's open-source pub/sub messaging platform that wraps Apache Kafka behind a REST interface, decoupling publishers and subscribers from native Kafka client complexity. Publishers POST JSON or Avro payloads over HTTP; subscribers expose an HTTP endpoint that Hermes calls. Hermes manages Kafka consumer groups, offset commits, retries, DLQ, and schema validation internally.

**Three components:**
1. **Hermes Frontend** — the ingestion gateway. Publishers `POST` to `https://hermes/topics/{topicName}`. Frontend validates the schema (if Avro), writes to Kafka, and returns `201 Created` synchronously. Stateless, horizontally scalable.
2. **Hermes Consumers** — the delivery engine. Consumes from Kafka and pushes batches of messages to subscriber HTTP endpoints. Manages retry logic, rate limiting, back-pressure (`inflightSize`), dead-letter routing, and subscription policy enforcement.
3. **Hermes Management** — the control plane. REST API + Web UI for creating topics, registering subscriptions, managing the schema registry, inspecting lag, and browsing DLQ entries.

**Interview trap:** "Is Hermes a replacement for Kafka?" — No. Kafka is still the durable log underneath. Hermes is an abstraction and operational platform on top of it. The data lives in Kafka; Hermes provides the HTTP contract and operational tooling.
**Tags:** hermes, architecture, components, fundamentals

---

## Q-KAFKA-007 [bloom: recall] [level: junior]
**Question:** What delivery semantics does Hermes guarantee and what does it require of subscriber implementations?
**Model answer:**
Hermes guarantees **at-least-once delivery**. A message is delivered to the subscriber at least once; it may be delivered more than once.

**How duplicates happen:**
1. Hermes Consumers POST a batch to the subscriber endpoint.
2. The subscriber processes the batch and persists results.
3. Before the subscriber responds (or while the response is in-flight), a network timeout or subscriber crash occurs.
4. From Hermes's perspective, the delivery failed. It retries.
5. The subscriber processes the same batch again.

This is not a bug — at-least-once with retries is far simpler to implement at scale than exactly-once, which would require distributed transactions across Kafka and the subscriber's data store.

**Hard contract on subscribers:** every Hermes subscriber MUST be **idempotent**. Processing the same message twice must produce the same result as processing it once. The standard implementation is a DB-level uniqueness constraint on the event ID: `INSERT ... ON CONFLICT DO NOTHING`.
**Interview trap:** "Can Hermes guarantee exactly-once?" — No. Even Kafka's transactional exactly-once is scoped to within-Kafka reads and writes. Once Hermes pushes to an HTTP endpoint, the transaction boundary ends at Kafka. Cross-system exactly-once requires the subscriber to implement idempotent writes.
**Tags:** hermes, delivery-semantics, at-least-once, idempotency

---

## Q-KAFKA-008 [bloom: understand] [level: regular]
**Question:** How does Kafka decide which partition a producer message lands in? What happens to key-based routing when you add more partitions?
**Model answer:**
Three routing modes:

1. **Key present, default partitioner** — Kafka applies murmur2 hash to the key bytes and computes `hash(key) % numPartitions`. All records with the same key always go to the same partition, preserving per-key ordering.
2. **Null key** — before Kafka 2.4: strict round-robin. Since 2.4: the **sticky partitioner** fills a batch for one partition before switching. This reduces latency vs round-robin by batching more records per partition per flush.
3. **Custom `Partitioner` implementation** — configure `partitioner.class` on the producer. Useful for semantic routing (e.g., route by region, priority tier, or tenant ID).

**The repartitioning problem:** if you add partitions to a topic after data exists, `hash(key) % numPartitions` resolves to a *different partition* for many existing keys. A user whose events were on partition 3 may now land on partition 7. Any downstream state keyed by partition (e.g., a Kafka Streams state store) becomes inconsistent. Repartitioning a live topic requires coordinating all producers and downstream consumers — it is a disruptive operation that must be planned, not done casually.

**Practical guideline:** if per-key ordering matters, set a meaningful key from day one (`userId`, `orderId`, etc.) and provision sufficient partitions upfront. Changing partition count post-launch is painful.
**Interview trap:** "Does adding more partitions break key routing?" — Yes. The hash is stable but `% numPartitions` changes the assignment for roughly half the keyspace. Hot data (e.g., a popular product ID) silently moves to a different partition, breaking any partition-local ordering assumptions downstream.
**Tags:** kafka, partitioning, producer, key, routing, murmur2

---

## Q-KAFKA-009 [bloom: understand] [level: regular]
**Question:** Explain idempotent producer and transactional producer in Kafka. What does each give you and where do the guarantees stop?
**Model answer:**
**Idempotent producer (`enable.idempotence=true`):**
The broker assigns the producer a `PID` (producer ID) and tracks a monotonically increasing sequence number per `(PID, partition)`. If the broker receives a record with a duplicate `(PID, sequence)` — caused by a producer retry on a network error — it silently deduplicates. Guarantee: **exactly-once delivery per partition within a single producer session**. Enabling idempotence also forces `acks=all`, `max.in.flight.requests.per.connection <= 5`, and retries enabled. If the producer restarts (new PID), deduplication history is lost.

**Transactional producer (`transactional.id` configured):**
Extends idempotence across multiple partitions and topics atomically. The flow:
```java
producer.initTransactions();
producer.beginTransaction();
producer.send(record1ToTopicA);
producer.send(record2ToTopicB);
producer.commitTransaction(); // or abortTransaction()
```
Consumers configured with `isolation.level=read_committed` only see records from committed transactions. Records from aborted transactions are invisible to committed-read consumers. This enables **read-process-write** exactly-once within Kafka (consume from topic A, transform, produce to topic B — atomically).

**Where guarantees stop:**
Both are scoped to **within the Kafka cluster**. The moment data leaves Kafka — HTTP call, database write, file write — Kafka's transactional guarantees do not protect you. A Hermes Consumer pushing to an HTTP subscriber is outside the transaction boundary. That is why Hermes is at-least-once and why subscriber idempotency is mandatory.
**Interview trap:** "Does `acks=all` + `enable.idempotence=true` give you exactly-once end-to-end?" — No. Exactly-once to an external system requires idempotent write logic on the consumer side. Kafka's guarantees are Kafka-internal only.
**Tags:** kafka, idempotent-producer, transactions, exactly-once, pid, isolation-level

---

## Q-KAFKA-010 [bloom: understand] [level: regular]
**Question:** What is a consumer group rebalance? Compare eager (stop-the-world) rebalancing with cooperative/sticky rebalancing.
**Model answer:**
A **rebalance** is the process by which Kafka redistributes partition assignments across the members of a consumer group. It is triggered by: a consumer joining the group, a consumer leaving (crash, shutdown, heartbeat timeout), a topic partition count change, or a group coordinator change.

**Eager (stop-the-world) rebalancing (pre-Kafka 2.4 default):**
1. The group coordinator signals all members to revoke all their partitions.
2. All consumers stop processing (the "stop-the-world" pause).
3. The group leader recomputes the assignment and distributes it.
4. All consumers resume with their new assignments.

During the pause, no messages are processed. For large groups or high-frequency rebalances (e.g., deployments, autoscaling), this causes significant lag spikes.

**Cooperative/incremental rebalancing (Kafka 2.4+, `CooperativeStickyAssignor`):**
1. Only the partitions that need to move are revoked — consumers keep partitions they retain.
2. A first rebalance revokes only the partitions being reassigned.
3. A second rebalance assigns the revoked partitions to the new owner.

This means most consumers never pause. Throughput continues during rebalancing. Only the consumers involved in the specific partition movement experience a brief pause.

**Sticky assignment:** the `StickyAssignor` and `CooperativeStickyAssignor` minimize partition movement by keeping existing assignments where possible. A consumer that was processing partitions {0,1,2} is likely to keep them after rebalancing, reducing offset commit overhead and warming of caches.

**Configuration:** `partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor`
**Interview trap:** "Why would you still see rebalance-induced lag with cooperative rebalancing?" — A consumer crash (not a graceful leave) still triggers an eager-like revocation for that consumer's partitions, because the coordinator doesn't know the consumer will never return. Session timeout (`session.timeout.ms`) governs how fast the coordinator detects the crash.
**Tags:** kafka, consumer-group, rebalancing, cooperative, sticky, stop-the-world

---

## Q-KAFKA-011 [bloom: understand] [level: regular]
**Question:** Compare auto-commit vs manual offset commit in Kafka consumers. When does each mode produce at-most-once or at-least-once semantics?
**Model answer:**
**Auto-commit (`enable.auto.commit=true`, interval default 5s):**
Kafka commits the consumer's current offset at a fixed interval, independent of whether processing is complete. The result depends on the order of operations:
- **At-most-once risk:** if you process records immediately on `poll()` and the app crashes between processing and the next auto-commit interval, the offset is never committed. On restart, those records are re-fetched — **but** if your code called `poll()` again before the crash, auto-commit committed on the previous `poll()` cycle, marking records as consumed before processing finished. That's at-most-once: committed but not durably processed.
- **At-least-once risk:** if the app crashes after processing but before the 5s commit window fires, records are redelivered on restart.

In practice auto-commit usually gives at-least-once, but the race condition makes it unreliable for either guarantee without careful design.

**Manual commit (`enable.auto.commit=false`):**
- `commitSync()` — blocks until the commit succeeds. Use after `batchUpdate()` or after the DB transaction commits. Gives true at-least-once.
- `commitAsync()` — non-blocking, fires and forgets. Use in high-throughput paths; handle `OffsetCommitCallback` for retry logic.

**Achieving at-most-once deliberately (rare):** commit *before* processing. Useful for idempotent sinks where duplicate processing is more expensive than data loss (e.g., billing deduplication managed elsewhere).

**Exactly-once (within Kafka):** use transactional producers with `isolation.level=read_committed` on consumers. For external sinks, combine manual commit with idempotent writes in a single DB transaction (transactional outbox pattern).
**Interview trap:** "If I set `enable.auto.commit=false` and crash mid-batch, do I lose data?" — No, records are redelivered from the last committed offset. The danger is the opposite: non-idempotent processing will process them twice.
**Tags:** kafka, offset-commit, auto-commit, at-least-once, at-most-once, exactly-once

---

## Q-KAFKA-012 [bloom: understand] [level: regular]
**Question:** What is `max.poll.records` and how does it relate to consumer backpressure? What happens if processing time exceeds `max.poll.interval.ms`?
**Model answer:**
**`max.poll.records`** (default 500) limits the number of records returned by a single `poll()` call. It controls the batch size a consumer processes between polls.

**Backpressure mechanism:**
In a Kafka consumer loop:
```java
while (true) {
    ConsumerRecords<K, V> records = consumer.poll(Duration.ofMillis(100));
    process(records); // takes variable time
}
```
If `process()` is slow, you simply don't call `poll()` again until you're ready. Kafka's pull model means the consumer naturally applies backpressure by not fetching more data than it can handle. Lower `max.poll.records` reduces the batch size, giving finer-grained control.

**`max.poll.interval.ms`** (default 5 minutes) is the maximum time between two consecutive `poll()` calls before the group coordinator considers the consumer dead and triggers a rebalance. If your `process()` batch takes longer than this interval — e.g., a slow DB write, an external API call per record, or a large batch with `max.poll.records=1000` — the consumer is evicted from the group, its partitions are reassigned, and then it rejoins, causing a rebalance loop.

**Tuning strategy:**
- Reduce `max.poll.records` if processing is slow per record.
- Increase `max.poll.interval.ms` if processing is inherently slow but bounded.
- Move slow work to async threads, but then you must commit offsets carefully after async completion.
- Use `consumer.pause(partitions)` to pause fetch for specific partitions during downstream overload without triggering a rebalance.
**Interview trap:** "What happens if you process asynchronously and commit offsets before async completion?" — You get at-most-once: the offset is committed but the async processing may not have finished. If the app crashes, those records are silently skipped.
**Tags:** kafka, max-poll-records, max-poll-interval, backpressure, consumer, rebalance

---

## Q-KAFKA-013 [bloom: understand] [level: regular]
**Question:** Explain Kafka vs RabbitMQ — not just "one is a log, one is a queue" but the concrete operational and semantic differences that matter in a senior interview.
**Model answer:**
**Fundamental model:**
- **RabbitMQ** is a traditional message broker: messages are routed through exchanges to queues. Once a consumer ACKs a message, it is deleted from the queue. The broker is the source of truth for routing logic. Supports complex routing: topic exchanges, header routing, dead-letter exchanges.
- **Kafka** is a distributed commit log: records are appended immutably to partitions. Consumers read by offset; records are retained based on time/size, not on consumption. Multiple consumer groups can read the same data independently without re-publishing.

**Key semantic differences:**

| Aspect | Kafka | RabbitMQ |
|--------|-------|----------|
| Message retention | Time/size-based, independent of consumers | Deleted after ACK (unless DLQ) |
| Consumer replay | Yes — seek to any offset | No — message gone after ACK |
| Fan-out | Multiple consumer groups, each reads independently | Requires binding to multiple queues per exchange |
| Ordering | Per-partition guaranteed | Per-queue guaranteed (single consumer) |
| Throughput | Millions/sec with batching; sequential disk I/O | Hundreds of thousands/sec; random access patterns |
| Schema | Avro + Schema Registry ecosystem | No built-in schema story |
| Routing | Partition by key only | Rich exchange routing (topic, header, fanout) |
| Long-term storage | First-class (compacted topics, weeks of retention) | Not designed for it |

**When RabbitMQ wins:**
- Complex routing logic (route by message attributes, priority queues).
- Task queues where exactly-one-consumer semantics matter and you don't need replay.
- Low message volume where Kafka's operational overhead isn't worth it.
- Legacy AMQP integrations.

**When Kafka wins:**
- High throughput (>100k/s per topic).
- Multiple independent consumers on the same data (audit log + analytics + downstream services).
- Event replay, time travel, rebuilding derived state.
- Event sourcing, CDC, data pipeline integration.
**Interview trap:** "Can RabbitMQ do what Kafka does?" — With quorum queues and streams (RabbitMQ 3.9+), RabbitMQ added an append-only stream primitive. But its ecosystem, throughput ceiling, and schema tooling still lag Kafka for high-volume event-log use cases.
**Tags:** kafka, rabbitmq, comparison, log-vs-queue, trade-offs

---

## Q-KAFKA-014 [bloom: understand] [level: regular]
**Question:** What is Avro schema evolution and what changes are backward-compatible, forward-compatible, and breaking?
**Model answer:**
Avro schemas evolve across versions. Compatibility rules govern whether consumers/producers using old schemas can interoperate with new schemas.

**Backward compatible** (new schema can read data written with old schema):
- Safe: add optional field with a default value; remove an optional field.
- Consumers upgraded to the new schema process old messages (field absent → use default).

**Forward compatible** (old schema can read data written with new schema):
- Safe: add optional field with a default value.
- Old consumers ignore unknown fields; they don't fail.

**Full compatible** (both backward AND forward):
- Only add optional fields with defaults. This is the recommended default for production Avro topics.

**Breaking changes (require version coordination):**
- Remove a required field (no default) — old consumers can't deserialize.
- Add a required field (no default) — old producers can't produce valid records.
- Change a field's type (`int` → `string`).
- Rename a field without adding the old name as an alias.

**Schema registry enforcement:** in Hermes/Confluent-compatible setups, the schema registry enforces the topic's configured compatibility level at publish time. A publisher posting a schema-breaking payload gets `400 Bad Request` before the message reaches Kafka. The schema ID is embedded in each Kafka record header so consumers know exactly which schema version to use for deserialization.

**Safe rename:** use `"aliases": ["oldName"]` — this is backward-compatible.
**Interview trap:** "Can I rename a field safely?" — Only with an alias. `"aliases": ["oldFieldName"]` in the new schema allows Avro to map the old name during deserialization. Without the alias, it is a breaking change.
**Tags:** avro, schema-evolution, backward-compatible, forward-compatible, schema-registry

---

## Q-KAFKA-015 [bloom: understand] [level: regular]
**Question:** What is Kafka's unclean leader election and what does enabling it trade against disabling it?
**Model answer:**
**Context:** when a partition's leader goes down, Kafka normally elects a new leader from the ISR (in-sync replicas). ISR members are guaranteed to have all committed messages. If the ISR is empty (all replicas are behind), there is no clean leader candidate.

**`unclean.leader.election.enable=true` (default was `true` pre-Kafka 0.11, now `false`):**
Kafka elects the most up-to-date out-of-sync replica as the new leader even though it may be missing some committed messages. The topic remains available but **data loss occurs** — the messages the old leader had but this replica didn't are permanently gone. Any consumer that already read those messages has data the cluster no longer has.

**`unclean.leader.election.enable=false` (recommended for durability):**
If ISR is empty, the partition becomes unavailable (producers get errors, consumers can't read). The partition stays offline until an ISR member recovers. **No data loss**, but the topic is unavailable for the duration of the outage.

**When to use which:**
- `false` (default, recommended): financial events, order data, anything where loss is worse than downtime.
- `true`: metrics, analytics, logs — situations where availability matters more than complete data and some loss is acceptable.

**ISR-empty root causes:** a broker goes down while the other replicas are far behind (slow followers, network partition), or all replicas of a partition happen to be on brokers that fail simultaneously. With `RF=3`, this requires at least 2 simultaneous failures — rare but not impossible.
**Interview trap:** "If `unclean.leader.election.enable=false` and ISR is empty, what happens to new produce requests?" — Producers with `acks=1` or `acks=all` get a `NotLeaderForPartitionException` or `LeaderNotAvailableException`. The partition is completely offline. This is the correct behavior when you've traded availability for durability.
**Tags:** kafka, unclean-leader-election, isr, durability, availability

---

## Q-KAFKA-016 [bloom: apply] [level: senior]
**Question:** Design a dead-letter queue (DLQ) strategy for a Kafka consumer that processes payment events. The consumer can encounter three failure categories: transient infrastructure failures, poison-pill malformed messages, and retryable business logic failures. Show the routing logic and what each failure category needs.
**Model answer:**
Three failure categories require three different DLQ strategies:

**1. Transient infrastructure failures (DB down, downstream timeout):**
- Do NOT DLQ immediately. Block the consumer with bounded retries + exponential backoff in-process (e.g., 3 retries, 1s/2s/4s).
- If all retries exhausted, pause consumption on that partition (`consumer.pause()`) until recovery, or publish to a **retry topic** with a delay (`payment-events.retry.1`, `payment-events.retry.2` at increasing delays).
- Resume from retry topic after delay using a scheduler that polls it.
- Only after retry TTL exceeded → move to DLQ.

**2. Poison-pill / malformed messages (deserialization error, schema mismatch):**
- Catch `SerializationException` or validation failure.
- Do NOT retry — retrying a malformed message is pointless and blocks the partition.
- Immediately route to `payment-events.DLQ` with metadata headers: original topic, partition, offset, error message, stack trace, timestamp.
- Commit the offset past the poison pill so the consumer can continue.
- Alert the team; poison pills require a schema fix at the producer.

**3. Retryable business logic failures (insufficient balance in a transient external check, idempotency key collision):**
- Distinguish by exception type in the catch block.
- Route to `payment-events.retry` with a `retry-count` header.
- A separate retry consumer reads from `payment-events.retry`, honors the retry count, and re-processes.
- After `max_retries` (e.g., 5), route to `payment-events.DLQ`.

**DLQ record structure (headers):**
```
x-original-topic: payment-events
x-original-partition: 3
x-original-offset: 10042
x-error-type: DESERIALIZATION_FAILURE
x-error-message: "Field 'amount' type mismatch"
x-failed-at: 2026-06-03T12:00:00Z
x-retry-count: 0
```

**Replay mechanism:** a DLQ replay tool reads from `payment-events.DLQ`, strips the `x-*` headers, and re-publishes to `payment-events` (or a replay topic). Replay must be gated by a human approval for payment events.
**Interview trap:** "Why not just retry everything from the DLQ automatically?" — Poison pills will re-enter the DLQ immediately and loop forever, creating a retry storm. Retries must be conditional on error type, with circuit-breaker protection.
**Tags:** kafka, dlq, dead-letter, retry, poison-pill, payment, consumer-design

---

## Q-KAFKA-017 [bloom: apply] [level: senior]
**Question:** Explain the Transactional Outbox pattern and how Kafka Connect + Debezium implements it. What problem does it solve and what are the failure modes it prevents?
**Model answer:**
**The problem:** you need to atomically update a database row AND publish a Kafka event. If you do them sequentially:
- DB commit succeeds, Kafka produce fails → event is lost, DB is inconsistent with the event log.
- Kafka produce succeeds, DB commit fails → duplicate event, no DB record.
- 2PC across DB + Kafka is impractical (Kafka doesn't participate in XA transactions).

**The Transactional Outbox pattern:**
Instead of producing directly to Kafka, the service writes the event to an `outbox` table inside the same DB transaction as the business mutation. The DB transaction is the single atomic operation. A separate process reads from the outbox and publishes to Kafka.

```sql
-- Inside the same @Transactional block:
UPDATE orders SET status = 'CONFIRMED' WHERE id = 42;
INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, created_at)
VALUES ('Order', 42, 'OrderConfirmed', '{"orderId": 42, ...}', now());
-- Single COMMIT — both happen atomically or neither does
```

**Debezium (Kafka Connect source connector):**
Debezium runs as a Kafka Connect plugin. It connects to the DB's replication log (PostgreSQL WAL, MySQL binlog, etc.) and streams row-level changes as Kafka records. The `outbox` table changes are captured from the WAL — not by polling. This means:
- **No polling overhead:** events flow in near-real-time (typically <1s latency from DB commit to Kafka record).
- **No missed events:** WAL is the ground truth; even if Debezium is down, it resumes from the last WAL LSN (log sequence number).
- **Exactly-once within the outbox-to-Kafka path:** Debezium uses Kafka Connect's offset tracking to ensure each WAL event is published exactly once to Kafka (with `enable.idempotence=true` on the Connect producer).

**Failure modes eliminated:**
- Network failure between DB commit and Kafka produce → outbox row persists, Debezium publishes when it recovers.
- Debezium crash mid-stream → resumes from last committed WAL offset, no events lost.
- Kafka broker unavailable → Debezium buffers (with Connect's internal retry), eventually publishes when broker recovers.

**Failure mode NOT eliminated:**
- The outbox table grows unboundedly if Debezium is down for a long time. Need outbox cleanup (delete after published + retention window).
- Business logic bugs (wrong payload in the outbox INSERT) — garbage in, garbage out.
**Interview trap:** "Can you use the outbox pattern without Debezium?" — Yes, with polling (a scheduled job reads unpublished outbox rows, publishes, marks as published). Polling adds latency and operational burden. Debezium's CDC approach is strictly superior for production.
**Tags:** kafka, outbox, debezium, kafka-connect, cdc, transactional-outbox, exactly-once

---

## Q-KAFKA-018 [bloom: apply] [level: senior]
**Question:** Describe how Kafka Streams and KTable work. Give a concrete use case for a senior backend engineer and explain the state store internals.
**Model answer:**
**Kafka Streams** is a Java library (no separate cluster needed) for stream processing directly on top of Kafka. Each instance of a Streams application is a consumer + producer. Streams provides a high-level DSL (`KStream`, `KTable`, `KGroupedStream`) and a processor API for lower-level control.

**KStream vs KTable:**
- `KStream<K, V>` — unbounded stream of records. Every record is an independent event. Analogous to an append-only log.
- `KTable<K, V>` — changelog stream. Each record is an update to the current value for key K. Only the latest value per key is "active." Semantically a materialized view of a compacted topic.

**Concrete use case — real-time order count per category:**
```java
StreamsBuilder builder = new StreamsBuilder();
KStream<String, Order> orders = builder.stream("orders-topic");

KTable<String, Long> countByCategory = orders
    .groupBy((key, order) -> order.category())
    .count(Materialized.as("order-count-store"));

countByCategory.toStream().to("order-counts-topic");
```

**State store internals:**
Kafka Streams stores aggregation state in a **local RocksDB instance** per task (one task per partition). The state store is backed by a **changelog topic** in Kafka (a compacted topic named `<appId>-<storeName>-changelog`). On recovery:
1. The instance reads the changelog topic to restore the RocksDB state.
2. Then resumes processing from the input topic offset.

This means state is durable even if a Streams instance crashes — it reconstructs from the changelog. For large state stores, this rebuild can take minutes (tunable via standby replicas: `num.standby.replicas`).

**Join semantics:**
- `KStream-KTable join` — for each stream record, look up the current value in the KTable. Non-blocking, uses local state. Useful for enriching events with reference data (e.g., enrich order event with product metadata from a product KTable).
- `KStream-KStream join` — windowed join; both sides must arrive within the window.
**Interview trap:** "Is Kafka Streams suitable for stateless transformations?" — Yes, and it's simpler there (no state store overhead). But the main value prop is stateful operations (aggregations, joins) that would otherwise require external DB calls.
**Tags:** kafka-streams, ktable, kstream, state-store, rocksdb, streaming, aggregation

---

## Q-KAFKA-019 [bloom: apply] [level: senior]
**Question:** Write a Kotlin Spring controller that acts as a Hermes subscription endpoint. It receives a batch of `OrderCreatedEvent` objects as JSON, persists them idempotently, and returns the correct HTTP status codes for the Hermes retry contract.
**Model answer:**
```kotlin
data class OrderCreatedEvent(
    val eventId: String,       // UUID — natural idempotency key
    val orderId: Long,
    val userId: Long,
    val totalAmount: BigDecimal,
    val timestamp: Instant
)

@RestController
@RequestMapping("/hermes/order-created")
class OrderCreatedConsumer(
    private val repository: OrderEventRepository
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun handleBatch(@RequestBody events: List<OrderCreatedEvent>): ResponseEntity<Void> {
        return try {
            repository.insertIgnoreDuplicates(events)
            ResponseEntity.ok().build()                          // 200 = delivered, do not retry
        } catch (e: DataIntegrityViolationException) {
            // Unexpected DB constraint — treat as permanent failure
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build()  // 422
        } catch (e: CannotAcquireLockException) {
            // Transient DB contention — Hermes will retry with backoff
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()   // 503
        } catch (e: Exception) {
            // Unknown error — treat as transient to avoid silent data loss
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build() // 500
        }
    }
}

@Repository
class OrderEventRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insertIgnoreDuplicates(events: List<OrderCreatedEvent>) {
        val sql = """
            INSERT INTO order_events (event_id, order_id, user_id, total_amount, event_ts)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
        """.trimIndent()
        jdbcTemplate.batchUpdate(sql, events.map { e ->
            arrayOf(e.eventId, e.orderId, e.userId, e.totalAmount, e.timestamp)
        })
    }
}
```

Key decisions:
- `ON CONFLICT (event_id) DO NOTHING` — idempotency at the DB layer. Re-delivery of the same `eventId` is a silent no-op. The entire batch succeeds even if some events are duplicates.
- `200` for success (including all-duplicate batches — they were processed, just skipped).
- `422` for permanent business errors where `retryClientErrors=false` — Hermes will not retry; message goes to DLQ.
- `503`/`500` for transient errors — Hermes retries with backoff.
- Never return `200` if processing failed. Never return a 4xx for a transient error (would DLQ valid messages).
**Interview trap:** "What if only some events in the batch fail?" — Hermes does not support partial batch ACK. Return `200` only if all processable events were handled (duplicates count as handled). Poison pills (malformed events) should be caught per-event, logged, and the batch should still return `200` with the good events inserted — never block the whole batch on one bad record.
**Tags:** hermes, kotlin, spring, subscriber, idempotency, http-contract, status-codes

---

## Q-KAFKA-020 [bloom: apply] [level: senior]
**Question:** You have a Kafka consumer processing 50,000 events/second. The downstream database can handle 10,000 writes/second. Describe at least three concrete techniques to prevent consumer lag from growing indefinitely without losing data.
**Model answer:**
The consumer is 5x faster than the sink. Backpressure must be applied without dropping records. Three concrete techniques:

**1. Batch database writes (primary lever):**
Instead of one write per record, accumulate records in-memory and do a single `batchUpdate()` per `poll()` cycle. A batch insert of 500 records to PostgreSQL is ~10-50x more efficient than 500 individual inserts due to reduced round trips and WAL write amplification. At 50k/s consumer, `max.poll.records=500` + batch insert can bring the DB write rate from 50k ops/s down to 100 batch ops/s (500 records × 100 batch/s = 50k records/s).

**2. Async writes with `consumer.pause()` / `consumer.resume()`:**
Decouple the Kafka poll loop from the DB write:
```kotlin
val buffer = ArrayDeque<ConsumerRecord<K, V>>()
while (true) {
    val records = consumer.poll(Duration.ofMillis(100))
    buffer.addAll(records)
    if (buffer.size >= FLUSH_THRESHOLD || flushTimerExpired()) {
        try {
            db.batchInsert(buffer.drainToList())
            consumer.commitSync()
        } catch (e: DbOverloadException) {
            consumer.pause(consumer.assignment()) // stop fetching
            Thread.sleep(backoffMs)
            consumer.resume(consumer.assignment())
        }
    }
}
```
Pausing prevents the consumer from fetching more data while the DB is saturated, without exceeding `max.poll.interval.ms` (as long as you still call `poll()` on a separate thread or within the interval).

**3. Horizontal scaling + partition increase:**
Add more consumer instances (up to the partition count). Each instance handles a subset of partitions and a proportional share of the DB write load. If you have 24 partitions and 4 consumer instances, each handles ~12,500 writes/second — within the DB's per-connection budget. Scale the DB connection pool proportionally.

**4. Write-behind cache / tiered storage (advanced):**
Write to a fast local write buffer (Redis, Aerospike) first, return `200` quickly, then asynchronously flush to the DB. The cache absorbs burst. Risk: if the cache crashes before flush, data is lost — only acceptable with a WAL on the cache.

**5. Prioritized consumption with `consumer.pause()`:**
Pause low-priority partitions during overload, drain high-priority ones first.
**Interview trap:** "Can you just increase `max.poll.interval.ms` to give the consumer more time?" — That only prevents the group coordinator from kicking the consumer out during slow processing. It doesn't reduce the actual lag. The underlying throughput mismatch remains.
**Tags:** kafka, backpressure, consumer, batch-writes, pause-resume, scaling, throughput

---

## Q-KAFKA-021 [bloom: apply] [level: senior]
**Question:** Explain the Hermes subscription policy fields `rate`, `inflightSize`, `messageTtl`, `requestTimeout`, and `retryClientErrors`. Show how to configure them for a high-volume analytics ingest endpoint receiving 5,000 messages/second with 4xx errors that must NOT trigger retries.
**Model answer:**
**Field explanations:**
- **`rate`** — max message delivery rate (messages/second) to the subscriber. Hermes Consumers throttle to this ceiling. Too low = artificial lag. Too high = subscriber overload.
- **`inflightSize`** — max concurrent in-flight POSTs at a time. Primary backpressure knob. When `inflightSize` slots are all waiting for ACK, Hermes pauses dispatching. Rule of thumb: `inflightSize ≈ rate × p99_response_time_seconds`.
- **`messageTtl`** — seconds from publish time before a message is moved to DLQ (if not successfully delivered). Prevents unbounded retry storms for business-expired messages.
- **`requestTimeout`** — HTTP timeout per POST. If the subscriber doesn't respond within this window, Hermes treats the delivery as failed and retries. Must be significantly less than `messageTtl`.
- **`retryClientErrors`** — if `false`, 4xx responses are permanent failures: the message is DLQ'd (or dropped), not retried. If `true`, 4xx responses trigger retries — almost always wrong; it causes retry storms on buggy subscribers.

**Configuration for the given scenario:**
```json
{
  "subscriptionPolicy": {
    "rate": 5500,
    "inflightSize": 1100,
    "messageTtl": 7200,
    "requestTimeout": 15000,
    "retryClientErrors": false,
    "backoff": {
      "initialDelayMillis": 1000,
      "multiplier": 2,
      "maxDelayMillis": 60000
    }
  }
}
```

Rationale:
- `rate: 5500` — 10% headroom above 5k/s for natural burst variance.
- `inflightSize: 1100` — with 5k/s and ~200ms average response time: `5000 × 0.2 = 1000` + 10% margin.
- `messageTtl: 7200` — 2 hours. Business tolerates 1 hour delay; 2 hours gives margin for a full subscriber outage before DLQ.
- `requestTimeout: 15000` — 15s per POST. Generous for analytics batch ingestion; prevents spurious retries on slow-but-healthy subscribers.
- `retryClientErrors: false` — required. 4xx from subscriber is a permanent error (bad data, schema mismatch). Retrying wastes capacity.
- Exponential backoff with `maxDelayMillis: 60000` — worst case 1 retry/minute per message during outage. Prevents thundering herd on recovery.

**Interaction gotcha:** if `requestTimeout=30s` and `messageTtl=60s`, a timed-out message retries at most once before TTL expires. Tune these two together: `requestTimeout × max_retries < messageTtl`.
**Interview trap:** "Why not set `rate` exactly to 5000?" — Hermes enforces the rate strictly as a ceiling. Any burst above 5000 is throttled. 10% headroom absorbs natural traffic variance without artificially inducing lag.
**Tags:** hermes, subscription-policy, rate-limiting, inflight, ttl, retry, configuration

---

## Q-KAFKA-022 [bloom: analyze] [level: senior]
**Question:** A service consuming from a Kafka topic is experiencing rebalance storms — the consumer group rebalances every 30 seconds. Walk through your diagnosis and remediation steps.
**Model answer:**
**Symptoms indicating a rebalance storm:**
- Consumer group logs show repeated `Revoking previously assigned partitions` followed by `Setting newly assigned partitions`.
- Consumer lag spikes every ~30 seconds (processing pauses during rebalance).
- `kafka-consumer-groups.sh --describe` shows consumer IDs changing rapidly.

**Root cause investigation (5 categories):**

**1. Heartbeat timeout (`session.timeout.ms` / `heartbeat.interval.ms`):**
If the consumer's heartbeat thread is blocked by the main application thread (GC pause, large synchronous batch processing, thread starvation), the broker declares the consumer dead. Check: `session.timeout.ms` (default 45s in newer Kafka, was 10s). Ensure `heartbeat.interval.ms < session.timeout.ms / 3`. Increase `session.timeout.ms` if processing is legitimately slow but bounded.

**2. `max.poll.interval.ms` exceeded:**
If processing between `poll()` calls takes longer than `max.poll.interval.ms` (default 5 minutes), the group coordinator evicts the consumer. Check processing time per batch. Remediation: reduce `max.poll.records`, optimize processing, or increase `max.poll.interval.ms`.

**3. Consumer process crashing and restarting:**
A JVM OOM, uncaught exception, or deployment rolling restart causes consumers to leave and rejoin. Check application logs for exceptions. Remediation: fix the application bug; ensure graceful shutdown calls `consumer.close()` so the coordinator can immediately reassign rather than waiting for session timeout.

**4. Broker connectivity (network partition, broker restart):**
If brokers restart or the network is unstable, consumers lose the connection to the group coordinator and trigger rejoins. Check broker logs for leader elections and network events during the rebalance windows.

**5. Over-eager rebalancing (eager assignor + large group):**
With the default `RangeAssignor` or `RoundRobinAssignor`, any consumer join/leave causes all partitions to be revoked from all consumers (stop-the-world). Remediation: switch to `CooperativeStickyAssignor` (`partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor`) — only partitions that need to move are revoked.

**Priority of fixes:**
1. Switch to cooperative sticky assignor (low risk, high reward).
2. Reduce `max.poll.records` to ensure processing completes well within `max.poll.interval.ms`.
3. Add consumer health metrics (poll rate, heartbeat lag) to dashboards.
4. Investigate application crashes if #1 and #2 don't resolve it.
**Interview trap:** "Increasing `session.timeout.ms` to 5 minutes — does that fix a rebalance storm?" — It reduces the frequency if the cause is heartbeat timeout, but it also means a crashed consumer holds its partitions for 5 minutes before reassignment, increasing lag. It's a symptom treatment, not a root cause fix.
**Tags:** kafka, rebalance-storm, diagnosis, cooperative-assignor, session-timeout, max-poll-interval, operations

---

## Q-KAFKA-023 [bloom: analyze] [level: senior]
**Question:** Compare Hermes push-over-HTTP to native Kafka pull from a senior engineer's perspective. When is each the right choice? Go deep on operational mechanics.
**Model answer:**
**Native Kafka pull (consumer group):**
The consumer process owns its offset, calls `poll()` in a loop, deserializes Avro using a local schema registry client, manages rebalances, commits offsets. Every service must:
- Embed a Kafka client (JVM dependency, configuration surface).
- Handle schema registry client caching and versioning.
- Implement DLQ logic, retry logic, lag monitoring, rebalance hooks.
- Run consumer group lag alerting.

At fleet scale (50+ microservices), this is 50 consumer groups to monitor, 50 alert configurations, 50 independent schema registry integrations.

**Benefits of native pull:** fine-grained control (pause/resume per partition, custom assignors), lower latency (no HTTP hop, no Hermes batch overhead), exactly-once-within-Kafka with transactions, tunable fetch sizes per consumer.

**Hermes push-over-HTTP:**
Hermes owns the Kafka consumer group on behalf of all subscribers. A service exposes one HTTP `POST` endpoint and receives batches. Hermes manages retries, DLQ, rate limiting, lag monitoring. The service needs no Kafka client.

**Benefits:** zero Kafka operational overhead per service, centralized subscription policy management, per-subscription DLQ and replay UI, back-pressure via HTTP status codes (429/503), schema validation at ingestion (Frontend), each subscription is an independent consumer group (slow subscriber doesn't affect others).

**Critical consumer-side mechanics difference:**
Native pull: slow down `poll()` → direct backpressure to Kafka broker. Hermes push: signal overload by returning `429` or `503` — Hermes respects `inflightSize` and `rate` limits. If you return `200` while internally overwhelmed (queue full), Hermes has zero visibility into your internal backlog. You must return the right status codes.

**Decision framework:**
| Use native Kafka if... | Use Hermes if... |
|------------------------|-----------------|
| Latency SLO < 50ms | Service is HTTP-native, no Kafka client desired |
| Throughput > Hermes rate limit ceiling | Need centralized policy/DLQ/observability |
| Exactly-once within Kafka pipeline needed | Large fleet of independent subscribers |
| Kafka Streams / KTable processing | Team prefers REST integration |
| Custom partition assignment logic | Analytics with 1+ hour tolerable delay |
**Interview trap:** "If my Hermes subscriber is slow, does it slow down other subscribers on the same topic?" — No. Each subscription is an independent Kafka consumer group internally. A slow subscriber only affects itself. This is a key architectural advantage vs a single shared consumer group.
**Tags:** hermes, kafka, push-vs-pull, consumer-mechanics, fleet-scale, trade-offs

---

## Q-KAFKA-024 [bloom: analyze] [level: master]
**Question:** Explain how Kafka achieves high throughput on a single partition. Walk through the full path of a produce request: from producer client to broker disk to consumer fetch. Name every optimization that makes Kafka fast.
**Model answer:**
Kafka's throughput on a single partition comes from a set of deliberately chosen optimizations at every layer:

**Producer side:**
1. **Batching** — the producer accumulates records in memory for up to `linger.ms` (default 0ms, but typically set to 5-10ms) or until `batch.size` (default 16KB) is full. A single network round-trip sends many records. Fewer round trips = dramatically higher throughput.
2. **Compression** — `compression.type=lz4|snappy|zstd` compresses the entire batch before sending. Compressed network transfer + smaller disk footprint = better I/O. LZ4 has the best ratio of speed to compression for most workloads; zstd for best compression.
3. **Pipelining** — `max.in.flight.requests.per.connection` (default 5) allows the producer to have up to 5 in-flight requests without waiting for ACK. Hides network round-trip latency.

**Broker disk:**
4. **Sequential I/O** — Kafka only appends to the end of partition log files. No random disk seeks. Sequential writes on spinning disks achieve ~100-200 MB/s; on SSDs, even faster. This is orders of magnitude better than databases with random-access patterns.
5. **Page cache / OS buffer** — Kafka does not manage its own memory cache. It relies on the OS page cache for reads. Actively consumed data is hot in page cache; no extra copy to a JVM heap. This also means Kafka's JVM heap can be small (~6-8 GB) while the OS page cache uses the rest of RAM.
6. **Log segments** — each partition is stored as a sequence of log segment files (default 1 GB each). The active segment is append-only. Older segments are immutable and can be deleted by the cleaner thread without locking writes to the active segment.

**Consumer fetch (zero-copy):**
7. **`sendfile()` system call (zero-copy)** — when a consumer fetches records, the broker uses the `sendfile()` syscall (or `transferTo()` in Java NIO) to transfer data directly from the page cache to the network socket without copying to user-space memory. CPU is barely involved. This is why a single Kafka broker can serve hundreds of MB/s of read throughput without high CPU.

**End-to-end path:**
```
Producer (batch, compress) 
  → TCP send → Broker receives → writes to page cache + disk (sequential append)
  → Leader acknowledges to ISR followers (replicated via fetch)
  → ISR followers acknowledge to leader
  → Leader responds to producer (if acks=all)
  → Consumer poll() → Broker sendfile() from page cache to socket → Consumer deserializes
```

**Why per-partition throughput is bounded:** a single partition is a single sequential log on one broker. Its ceiling is approximately `min(network_card_bandwidth, disk_write_speed, leader_CPU)`. Typical: 500 MB/s disk sequential write → rough ceiling of 500k records/s at 1 KB/record per partition. Multiple partitions across brokers scale linearly.
**Interview trap:** "Why is Kafka faster than a relational database for this workload?" — (1) Sequential I/O vs random I/O; (2) zero-copy consumer reads; (3) no WAL + B-tree + MVCC overhead; (4) no row-level locking; (5) page cache shared with OS, no double-buffering.
**Tags:** kafka, performance, throughput, zero-copy, sequential-io, page-cache, batching, compression

---

## Q-KAFKA-025 [bloom: analyze] [level: master]
**Question:** Design a system that provides end-to-end exactly-once semantics for a payment processing pipeline: REST API → Kafka → stream processor → database. Identify every boundary where exactly-once breaks and how to seal each one.
**Model answer:**
**The pipeline:**
```
Payment REST API → Kafka (payment-events) → Kafka Streams processor → PostgreSQL
```

**Boundary 1: REST API → Kafka (at-least-once ingestion):**
The API receives a POST, must produce to Kafka. If the produce times out, did it succeed? The producer may retry, creating a duplicate.

**Seal with:** idempotent producer (`enable.idempotence=true`) + a client-provided idempotency key (`X-Idempotency-Key` header). The broker deduplicates producer retries (same PID+sequence). Store the idempotency key in Kafka record headers. The API returns `202 Accepted` with the idempotency key; clients can safely retry.

**Boundary 2: Kafka producer restart (new PID):**
`enable.idempotence=true` only deduplicates within a single producer session. If the API pod restarts, new PID, history lost.

**Seal with:** transactional producer with a stable `transactional.id` (e.g., `"payment-api-pod-0"`). The transactional coordinator handles fencing: if `transactional.id` is reused by a new producer epoch, the old epoch is fenced off. Old in-flight transactions are aborted.

**Boundary 3: Kafka Streams read-process-write:**
Streams consumes from `payment-events`, transforms, writes to `payment-processed`. Must be atomic: don't commit the input offset without writing the output record, and vice versa.

**Seal with:** Kafka Streams transactions. When `processing.guarantee=exactly_once_v2` (Kafka 2.5+), Streams uses Kafka transactions internally: each task's input offset commit and output record produce are wrapped in a single transaction. Committed-read consumers of `payment-processed` see only atomically committed records.

**Boundary 4: Kafka → PostgreSQL (the hardest boundary):**
Streams produces to `payment-processed`. A separate consumer writes to PostgreSQL. This crosses the Kafka transaction boundary — PostgreSQL is not a Kafka transaction participant.

**Seal with: Transactional Outbox in reverse (read-side idempotency):**
- The consumer reads from `payment-processed` (committed records only, `isolation.level=read_committed`).
- Wraps DB write + offset commit in a single PostgreSQL transaction using the Kafka offset as a unique key:
  ```sql
  BEGIN;
  INSERT INTO kafka_offsets (topic, partition, offset) VALUES ('payment-processed', 0, 10042)
    ON CONFLICT (topic, partition) DO UPDATE SET offset = EXCLUDED.offset WHERE offset < EXCLUDED.offset;
  INSERT INTO payments (payment_id, amount, ...) VALUES (...) ON CONFLICT (payment_id) DO NOTHING;
  COMMIT;
  ```
- Commit Kafka offset only after PostgreSQL transaction commits.
- On restart, the consumer reads the last committed offset from `kafka_offsets` and seeks to it, skipping already-persisted records.

**What this achieves per boundary:**
| Boundary | Technique | Guarantee |
|----------|-----------|-----------|
| REST → Kafka | `enable.idempotence=true` + `transactional.id` | Exactly-once produce |
| Kafka internal (Streams) | `processing.guarantee=exactly_once_v2` | Exactly-once transform |
| Kafka → PostgreSQL | DB idempotent write + offset-in-same-transaction | Exactly-once sink |

**Residual risk:** the PostgreSQL transaction can commit but the Kafka offset commit can fail (crash between the two). On restart, the consumer re-reads the same Kafka record, but the DB insert is a no-op (`ON CONFLICT DO NOTHING`). True exactly-once achieved at the cost of DB uniqueness constraint overhead.
**Interview trap:** "Can you just use `acks=all` and call it exactly-once?" — No. `acks=all` is a durability guarantee (the record is stored), not an exactly-once guarantee (it may be stored multiple times on producer retry). Exactly-once requires deduplication at every boundary, not just durability at one.
**Tags:** kafka, exactly-once, transactions, kafka-streams, debezium, payment, end-to-end, idempotency

---

## Q-KAFKA-026 [bloom: analyze] [level: master]
**Question:** You are designing the Kafka topic topology for a recommendation engine serving 100 million events/day. Walk through your partition count decision, retention settings, compaction strategy, consumer group topology, and what breaks at scale.
**Model answer:**
**Step 1: Throughput math:**
100M events/day = ~1,157 events/second average. With typical diurnal peak 5x average: ~5,800 events/second peak.
Assume average record size = 500 bytes. Peak throughput: 5,800 × 500 bytes ≈ 2.9 MB/s.

A single partition can handle ~50-100 MB/s on modern hardware. So throughput alone doesn't mandate many partitions. But parallelism does.

**Step 2: Partition count:**
Rule: partition count = max expected consumer parallelism (now and near future).
- Recommendation engine has 3 downstream consumer groups: offline feature pipeline, real-time ranker, audit log.
- Each needs up to 20 consumer instances (based on processing SLOs).
- Set partition count = `max(20 consumers × groups, headroom) = 24` (next power of 2 for even distribution). Use 24 partitions across 3 brokers = 8 partitions/broker. Provides headroom for 5x peak traffic without repartitioning.

**Step 3: Retention:**
- **Raw events topic (`impression-events`):** `retention.ms=604800000` (7 days) + `retention.bytes` per partition as a safeguard. 7 days lets you replay for ML training reruns and debug incidents.
- **Derived/materialized topics (e.g., `user-features-current`):** `cleanup.policy=compact` — retain only latest value per userId. No time expiry needed; the latest feature vector is always valid. Combine: `cleanup.policy=compact,delete` with `min.compaction.lag.ms=3600000` (1 hour minimum age before compaction, to let consumers see all recent updates).

**Step 4: Consumer group topology:**
```
impression-events (24 partitions)
  ├── cg-offline-feature-pipeline   (20 consumers, batch reads every 10min, high lag OK)
  ├── cg-realtime-ranker             (20 consumers, lag SLO < 5s, manual offset commit)
  └── cg-audit-log                   (3 consumers, lag SLO < 5min, sequential archival)
```
Each group is independent — a lagging offline pipeline does not affect the real-time ranker.

**Step 5: What breaks at scale:**

1. **Partition count too low for future scale:** cannot remove partitions; hash routing breaks when adding. Provision generously (24 vs 12).
2. **Consumer group coordinator hotspot:** all groups' heartbeats and rebalances hit the same internal partition of `__consumer_offsets`. At 24 partitions × 3 groups × 20 consumers = 60 consumers, the coordinator is fine. At 500 consumers on one broker, you may need to increase `offsets.topic.num.partitions` (default 50) — requires broker config change and coordinator migration.
3. **Compaction lag:** with 100M events/day on a compacted topic, the dirty section can grow large between compaction runs. Tune `log.cleaner.threads` and `log.cleaner.io.max.bytes.per.second`. A single cleaner thread is often the bottleneck.
4. **Schema registry contention:** at 5,800/s peak, the schema registry is hit for schema lookups. Enable schema caching on all producers/consumers (default TTL 60s). Without caching, the registry becomes a synchronous bottleneck.
5. **Rebalance storms on the real-time ranker:** `cg-realtime-ranker` has a 5s lag SLO. Any rebalance causes processing to pause. Mitigate: `CooperativeStickyAssignor`, Kubernetes pod disruption budgets (don't restart more than 2 pods at once), pre-warm standby replicas in Kafka Streams if used.
**Interview trap:** "Why not use 200 partitions for maximum parallelism?" — (1) Each partition has overhead: a file descriptor, memory for the replica buffer, and a ZooKeeper/KRaft metadata entry. 200 partitions × 3 replicas = 600 log files per broker. At scale this adds meaningful memory and GC pressure. (2) More partitions = more time for a full ISR sync after a broker restart = longer unavailability window. (3) Kafka Streams state stores rebuild per partition — 200 partitions × state stores = expensive cold starts. The rule: provision for realistic parallelism, not theoretical max.
**Tags:** kafka, partition-design, retention, compaction, consumer-groups, scale, recsys, operations

---

## Q-KAFKA-027 [bloom: analyze] [level: master]
**Question:** Hermes Consumers experience throughput degradation when one slow subscriber starves other subscriptions on the same worker pool. Explain the failure mode, how dynamic workload balancing addresses it, and what the correct subscriber-side remediation is.
**Model answer:**
**Failure mode without workload balancing:**
Hermes Consumers run as a pool of worker threads. In a static assignment, each worker thread handles delivery for a fixed set of subscriptions. If subscription A has a slow subscriber (high HTTP response latency, frequent 503s, many retries), the worker thread assigned to A spends most of its time blocked waiting for responses or retrying. Meanwhile, subscription B (a healthy, fast subscriber) is also on the same worker thread and starves — it doesn't get dispatch time even though it could deliver instantly. At fleet scale (hundreds of subscriptions), a handful of "sick" subscriptions can monopolize the worker pool, causing artificial lag for healthy subscriptions that are otherwise fine.

**Metrics that indicate this is happening:**
- Subscription B's lag grows despite the subscriber being healthy (responds in <50ms).
- Worker thread utilization is high, but subscription B's delivery rate is far below its configured `rate`.
- Subscription A's retry rate and response latency metrics are spiking simultaneously.

**Dynamic workload balancing (Allegro's approach):**
The scheduler continuously measures per-subscription metrics:
- Delivery throughput (records/second actually delivered)
- In-flight slot utilization (is `inflightSize` always saturated → indicates backpressure from subscriber)
- Retry rate and average response latency

Subscriptions consuming disproportionate worker time are identified as "heavy." The scheduler redistributes: heavy subscriptions are isolated or given their own dedicated thread budget; light subscriptions (fast ACK, low retry rate) get dispatch priority on available threads. A light subscription no longer shares a thread with a blocked heavy one. This protects the majority of subscriptions from a minority of sick ones.

**What dynamic balancing does NOT fix:**
It protects other subscriptions. The heavy subscription itself still experiences lag. Hermes can't make a slow subscriber faster.

**Correct subscriber-side remediation (in order of priority):**
1. **Return `503` fast when overloaded** — don't let Hermes wait for `requestTimeout` (15s) before learning the subscriber is down. A fast `503` (returned in <100ms) with exponential backoff is better than a 15s timeout that holds a worker thread.
2. **Tune `inflightSize` and `rate`** to match the subscriber's actual throughput capacity, not an aspirational value.
3. **Scale out subscriber instances** — add pods, increase the upstream connection pool, or shard the subscriber.
4. **Fix the root cause** — slow DB queries, synchronous external calls, GC pressure.

**Design principle:** dynamic workload balancing is a platform-level mitigation, not a substitute for a properly tuned subscriber. It's an airbag, not a seat belt.
**Interview trap:** "Does dynamic workload balancing fix an overloaded subscriber?" — No. It protects *other* subscriptions from being affected. The overloaded subscriber still experiences its own lag. The fix is to scale the subscriber or fix the performance problem.
**Tags:** hermes, back-pressure, dynamic-workload-balancing, consumer-fleet, slow-subscriber, operations
