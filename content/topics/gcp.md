# Google Cloud Platform — question bank

> GCP is the cloud platform this learner runs production workloads on (Java 17 / Spring Boot microservices, Cloud Run, Cloud SQL, Pub/Sub). Senior interviews at a Java backend shop will probe compute-option trade-offs, managed data services, IAM/security, networking, observability, and CI/CD — all through the lens of someone who has actually operated services, debugged incidents, and made cost decisions. Shallow answers ("Cloud Run is serverless") get a polite nod and a trap question designed to expose that you don't know what you're talking about. The questions here enforce the depth an interviewer expects from a 5-year engineer who claims GCP on their CV.

## Scope

- Compute options: Compute Engine, GKE, Cloud Run, App Engine, Cloud Functions — internals and decision criteria
- Pub/Sub: topics/subscriptions, push vs pull, at-least-once delivery, ordering keys, dead-letter topics, comparison with Kafka
- Cloud Storage: storage classes, lifecycle policies, signed URLs, global namespace gotcha
- Cloud SQL: managed Postgres/MySQL, Cloud SQL Auth Proxy, HA, connection limits, PgBouncer
- Cloud Spanner: TrueTime, Paxos, external consistency, cost vs Cloud SQL trade-off
- Firestore and Bigtable: document store vs wide-column, use-case selection
- BigQuery: serverless columnar OLAP, partitioning, clustering, streaming inserts vs batch load
- Memorystore Redis: managed Redis, eviction, use cases
- IAM: principals, role types (primitive/predefined/custom), policy hierarchy, least privilege
- Service accounts and Workload Identity: key files vs WI, attack surface
- Secret Manager: versioning, audit logs, comparison with K8s Secrets and env vars
- VPC: global VPC, regional subnets, firewall rules, Private Google Access, Cloud NAT
- Shared VPC vs VPC Peering
- Load Balancing: L4 vs L7, global vs regional, anycast, NEG, HTTP(S) LB component chain
- Cloud Armor and Cloud CDN
- Cloud Operations Suite: Cloud Logging, Monitoring, Trace, Error Reporting, structured logging
- Cloud Build and Artifact Registry: CI/CD pipeline, cloudbuild.yaml
- Application Default Credentials (ADC) and gcloud auth
- Terraform on GCP: state in GCS, key resources
- Regions and zones: availability model, zonal vs regional resources
- Autoscaling: Cloud Run concurrency, GKE HPA, Compute Engine managed instance groups
- Cost levers: CUDs, Sustained Use Discounts, Spot VMs, Cloud Run to zero

---

## Q-GCP-001 [bloom: recall] [level: junior]
**Question:** What are the five compute options GCP offers, ordered from most infrastructure control to least, and what is each one in one sentence?
**Model answer:** From most to least control:

1. **Compute Engine** — IaaS; you get a VM; you manage OS, runtime, patches, everything above the hypervisor.
2. **GKE (Google Kubernetes Engine)** — managed Kubernetes; Google runs the control plane; you manage workloads (Deployments, Services, HPA).
3. **Cloud Run** — serverless containers; you push a container image, Cloud Run handles scaling, networking, and TLS; requests drive execution.
4. **App Engine** — PaaS; you deploy app code + `app.yaml`; Google manages servers, scaling, networking. Largely legacy; new projects rarely choose it.
5. **Cloud Functions** — FaaS; you deploy a single function handler; triggered by events (Pub/Sub message, HTTP, Cloud Storage event); maximum simplicity, maximum constraints.

The rule of thumb: pick the lowest abstraction level that satisfies your operational requirements. More abstraction = less control but less operational burden.
**Interview trap:** "App Engine Standard vs Flexible — what's the difference?" Standard runs on a sandboxed runtime (limited languages, no arbitrary binaries, scales to zero). Flexible runs your Docker container on a Compute Engine VM under the hood — more permissive but does not scale to zero, minimum one instance. In 2024+ there's almost no reason to choose either over Cloud Run.
**Tags:** compute, cloud-run, gke, compute-engine, app-engine, cloud-functions, basics

---

## Q-GCP-002 [bloom: recall] [level: junior]
**Question:** What does Cloud Run do when it receives no traffic? What are the billing implications?
**Model answer:** Cloud Run scales to **zero instances** when there is no traffic. When a request arrives, Cloud Run cold-starts a new container instance (typically 0.5–5 s depending on image size and startup latency). Billing is per 100 ms of CPU and memory actually consumed while handling requests — idle time costs nothing.

This makes Cloud Run the lowest-cost compute option for low-QPS internal services or services with bursty/unpredictable traffic. The trade-off is cold-start latency, which matters for user-facing synchronous APIs. You can configure **minimum instances** (Cloud Run's equivalent of "keep-warm") to trade a small always-on cost against cold-start elimination.
**Interview trap:** "What happens to WebSocket connections when Cloud Run scales to zero?" Cloud Run is request-scoped; it is not designed for persistent connections. A WebSocket requires a long-lived connection that outlives a request, which Cloud Run does not support in the serverless mode. For WebSockets or long-lived gRPC streams you need GKE or Compute Engine. (Cloud Run does support HTTP/2 and some gRPC patterns, but not long-lived bidirectional streams.)
**Tags:** cloud-run, autoscaling, cost, cold-start

---

## Q-GCP-003 [bloom: recall] [level: junior]
**Question:** What is a GCP region and what is a zone? What is the practical difference for a Spring Boot service that needs high availability?
**Model answer:** A **region** is a geographic area (e.g. `europe-west1` in Belgium, `us-central1` in Iowa). Each region contains at least three **zones** — isolated failure domains within the region (physically separate data centres with independent power, cooling, networking). Zone names look like `europe-west1-b`.

For HA in a single region:
- Deploy across **multiple zones**. A zonal failure (hardware outage, network partition) takes down one zone but not the region.
- GKE: spread pods across zones using `topologySpreadConstraints` or the default scheduler affinity.
- Cloud Run: managed; Google automatically distributes instances across zones in the region — you get zonal redundancy without any configuration.
- Compute Engine: use a **regional managed instance group** (MIG) which distributes VMs across zones and auto-heals.

Cross-region deployment (multi-region) protects against full regional outages but adds complexity: global load balancing, data replication latency, and higher cost.
**Interview trap:** "Is Cloud SQL HA cross-region?" No. Cloud SQL HA is a **regional replica** — the standby is in a different zone within the same region. Automatic failover takes ~60 s. For cross-region DR you need a **read replica in another region** and a manual/scripted promotion procedure. There is no automated cross-region failover in Cloud SQL.
**Tags:** regions, zones, availability, cloud-sql, cloud-run, gke

---

## Q-GCP-004 [bloom: recall] [level: junior]
**Question:** What are the four Cloud Storage storage classes, and when do you pick each?
**Model answer:** Cloud Storage is object storage (flat namespace: bucket → object key). Objects live in one class; class determines storage cost and retrieval cost.

| Class | Min retention | Retrieval cost | Use for |
|-------|---------------|----------------|---------|
| **Standard** | None | None | Hot data accessed frequently (images served on every request, active logs) |
| **Nearline** | 30 days | Per-GB retrieval fee | Data accessed ~monthly (monthly backups, monthly reports) |
| **Coldline** | 90 days | Higher per-GB retrieval | Data accessed ~quarterly (DR archives, compliance snapshots) |
| **Archive** | 365 days | Highest per-GB retrieval | Data accessed ~yearly or less (long-term regulatory retention) |

The penalty for early deletion: if you delete a Nearline object before 30 days, you are charged as if it stayed 30 days. Same logic for Coldline (90 d) and Archive (365 d).

Lifecycle policies automate class transitions and deletions — set a JSON rule on the bucket, evaluated daily by GCS.
**Interview trap:** "Bucket names are globally unique across all GCP projects. What's the security implication?" Don't embed customer IDs or sensitive identifiers in bucket names since they appear in public URIs and are globally enumerable. Use opaque names and expose content via **signed URLs** with time-limited access rather than public bucket ACLs.
**Tags:** cloud-storage, storage-classes, lifecycle, cost

---

## Q-GCP-005 [bloom: recall] [level: junior]
**Question:** In GCP IAM, what are the three role types? Which should you use in production and why?
**Model answer:** Three role types:

1. **Primitive roles** — `Owner`, `Editor`, `Viewer`. Pre-date IAM, extremely broad. `Editor` on a project gives write access to almost every GCP service. Never use in production; violates least privilege.
2. **Predefined roles** — GCP-managed, fine-grained per-service roles. Examples: `roles/storage.objectViewer`, `roles/cloudsql.client`, `roles/run.invoker`. Cover ~95% of production needs. Start here.
3. **Custom roles** — you define the exact set of permissions. Use when the narrowest predefined role is still too broad for your threat model, or when you need a permission that is not bundled in any predefined role.

Production rule: **always use predefined roles scoped to the narrowest resource** (bucket, not project; specific Cloud SQL instance, not all Cloud SQL). Create custom roles only when predefined options genuinely cannot express the required permission set.

IAM policy = set of bindings `{role: [members]}`. Policies attach to resources and are inherited down the hierarchy: org → folder → project → resource. Effective permissions = union of all bindings at every level.
**Interview trap:** "Can you deny a permission in GCP IAM?" Not with standard IAM — GCP IAM is additive only (allow-only model). The exception is **IAM Deny policies** (newer feature), which let you explicitly deny specific permissions overriding any allow bindings. Most shops don't use Deny policies yet; the operational alternative is don't grant the permission in the first place.
**Tags:** iam, roles, least-privilege, security

---

## Q-GCP-006 [bloom: recall] [level: junior]
**Question:** What is a GCP Service Account and how is it different from a regular Google account?
**Model answer:** A **service account** is an identity for a non-human principal — a workload (VM, Cloud Run service, GKE pod, Cloud Build job). It has an email address in the form `name@project.iam.gserviceaccount.com`.

Key differences from a Google user account:
- No password. Authentication happens via either an RSA key pair (downloadable JSON key file) or short-lived OAuth tokens issued by the GCP metadata server to the runtime.
- Can be assigned IAM roles on GCP resources, just like a user.
- Can be attached to compute resources: VMs, Cloud Run services, GKE node pools. When attached, the runtime's ADC (Application Default Credentials) automatically gets tokens for that service account — no credentials stored in code or config.

In Java, the GCP client libraries call ADC automatically. Locally, ADC uses `gcloud auth application-default login`. In production (Cloud Run, GKE, Compute Engine), ADC hits the instance metadata server — no code change between environments.
**Interview trap:** "What's wrong with using service account key JSON files?" The key file is a long-lived credential (no automatic expiry, doesn't rotate itself). If it leaks (committed to git, baked into a Docker image, exfiltrated from a CI system) — it's a full incident. Key files must be rotated manually, tracked, and revoked. Workload Identity (GKE) and attached service accounts (Cloud Run, Compute Engine) provide the same IAM access with zero long-lived key files.
**Tags:** iam, service-accounts, adc, security

---

## Q-GCP-007 [bloom: recall] [level: junior]
**Question:** What does Cloud Pub/Sub's at-least-once delivery guarantee mean for a consumer application?
**Model answer:** **At-least-once delivery** means Pub/Sub guarantees that every published message will be delivered to a subscription at least once — but may deliver the same message **more than once**. Duplicates happen when:
- The subscriber processes the message and sends `acknowledge()`, but the ack is lost in transit — Pub/Sub retries.
- The subscriber's ack deadline expires before `acknowledge()` is called — Pub/Sub re-delivers.
- Pub/Sub internals retry due to network issues.

Consequence: **consumers must be idempotent**. Processing the same message twice must produce the same outcome as processing it once. Common patterns:
- Database upsert (INSERT ... ON CONFLICT DO NOTHING or ON CONFLICT DO UPDATE).
- Deduplicate by message ID in a Redis set or database table with unique constraint.
- Design operations to be naturally idempotent (setting a state to "PAID" is safe; incrementing a counter is not).

The ack deadline is configurable (10 s – 600 s). For slow consumers, call `modifyAckDeadline()` to extend it rather than letting Pub/Sub re-deliver prematurely.
**Interview trap:** "Does Pub/Sub support exactly-once delivery?" Not in standard Pub/Sub. **Pub/Sub Lite** has ordering guarantees but is regional and has different scaling characteristics. For true exactly-once semantics you need Dataflow's deduplication (at higher latency cost) or implement dedup in your consumer. Accepting at-least-once and writing idempotent consumers is the standard operational pattern.
**Tags:** pubsub, at-least-once, idempotency, messaging

---

## Q-GCP-008 [bloom: understand] [level: regular]
**Question:** Compare Cloud Run and GKE for deploying a Spring Boot microservice. When does each win?
**Model answer:** The decision hinges on what the service needs beyond stateless HTTP request handling.

**Cloud Run wins when:**
- The service is stateless and HTTP/gRPC request-driven (each request is independent).
- You want zero idle cost — scale to zero when no traffic.
- You don't want to manage Kubernetes objects, node pools, or cluster upgrades.
- Burst traffic patterns: Cloud Run scales up within seconds, no pre-provisioned node capacity needed.
- Deployment simplicity: `gcloud run deploy` or a single CI step, no Helm/kubectl manifests.
- Cold-start latency is acceptable (or you configure `--min-instances 1`).

**GKE wins when:**
- You need **persistent background jobs** — long-running processes not tied to a request lifecycle.
- **WebSockets or long-lived gRPC bidirectional streams** — these outlive a request, which Cloud Run's model does not support.
- **Sidecar containers** — e.g. Envoy proxy, Cloud SQL Auth Proxy sidecar, Datadog agent. Cloud Run supports one container per service (Cloud Run multi-container is in preview but limited).
- **Custom Kubernetes operators or CRDs** — your org has platform tooling built on K8s primitives.
- **Fine-grained resource scheduling** — CPU/memory guarantees, GPU nodes, taint/toleration-based placement.
- **Stateful workloads** (StatefulSets) — databases, Kafka brokers, ZooKeeper.
- Multi-team platform: GKE's namespace + RBAC model scales to many teams sharing one cluster.

Operational reality: most Java microservices at 5 QPS–5000 QPS with stateless REST APIs fit Cloud Run perfectly. Move to GKE when you hit its constraints, not preemptively.
**Interview trap:** "Cloud Run can now run jobs (Cloud Run Jobs) — does that change the decision?" Cloud Run Jobs handles batch/one-shot workloads (no incoming HTTP). It's appropriate for ETL jobs, report generation, data migration scripts. It does NOT solve the persistent-connection problem. GKE is still required for workloads that need a continuously running process serving stateful connections.
**Tags:** cloud-run, gke, compute, microservices, trade-offs

---

## Q-GCP-009 [bloom: understand] [level: regular]
**Question:** Explain the difference between Pub/Sub push and pull subscriptions. When do you choose each in a GCP-native architecture?
**Model answer:** Both subscription types consume messages from the same topic. The difference is who initiates delivery.

**Pull subscriptions:**
- Your consumer calls `pull()` (or `StreamingPull()` for a long-lived gRPC stream), receives a batch of messages, processes them, calls `acknowledge()`.
- Consumer controls the rate of consumption (backpressure is natural — stop calling `pull()` and messages queue up).
- Good for: batch processing, latency-tolerant workloads, consumers running on GKE/Compute Engine with dedicated worker threads.
- Downside: requires a continuously running polling loop. Wasteful for very low-message-rate topics (CPU spinning on empty pulls).

**Push subscriptions:**
- Pub/Sub acts as the HTTP client — it POSTs each message as a JSON payload to your configured HTTPS endpoint.
- Your service handles the POST, does its work, returns HTTP 200. Non-200 responses trigger re-delivery.
- Good for: Cloud Run and Cloud Functions — these scale on incoming HTTP requests, so push naturally drives scaling. No polling thread needed.
- Pub/Sub adjusts push rate based on your endpoint's response latency (flow control built in).
- Requires a publicly accessible HTTPS endpoint (or at least one Pub/Sub can reach — Cloud Run generates an HTTPS endpoint automatically).

**Decision rule:**
- Cloud Run or Cloud Functions consumer → push (let Pub/Sub drive your scaling).
- GKE or Compute Engine consumer that needs backpressure or batch → pull (StreamingPull for low latency).
- High-throughput, ordered processing → pull with ordering keys; push with ordering keys is supported but harder to tune.
**Interview trap:** "With push subscriptions, how does Pub/Sub handle a slow consumer?" Pub/Sub uses exponential backoff on error responses and tracks the endpoint's 99th percentile latency to throttle the push rate. If your Cloud Run service is overloaded and returning 429 or 500, Pub/Sub backs off. The dead-letter topic is the safety valve: after `max_delivery_attempts` failures, the message is forwarded there instead of re-trying indefinitely.
**Tags:** pubsub, push, pull, cloud-run, messaging, backpressure

---

## Q-GCP-010 [bloom: understand] [level: regular]
**Question:** What is the Cloud SQL Auth Proxy and why do you use it instead of a direct connection?
**Model answer:** Cloud SQL Auth Proxy is a local proxy process (runs as a sidecar or as a binary) that handles:

1. **IAM-based authentication** — instead of opening firewall rules for a specific IP range, the proxy authenticates with GCP IAM using the runtime's service account. Only principals with `roles/cloudsql.client` on that instance can connect through it.
2. **Automatic TLS encryption** — wraps all traffic between the proxy and Cloud SQL in TLS. No certificate management on the application side.
3. **Private IP access without VPC peering complexity** — if your workload is in a VPC and Cloud SQL is in another project's VPC, the proxy handles the cross-project connection via the Cloud SQL API.

Typical setup in GKE:
```yaml
# pod spec excerpt
containers:
  - name: cloud-sql-proxy
    image: gcr.io/cloud-sql-connectors/cloud-sql-proxy:2
    args:
      - "--structured-logs"
      - "--port=5432"
      - "PROJECT:REGION:INSTANCE"
  - name: app
    env:
      - name: SPRING_DATASOURCE_URL
        value: "jdbc:postgresql://127.0.0.1:5432/mydb"
```

The app connects to `127.0.0.1:5432` (the proxy). The proxy handles auth and TLS transparently.

In Cloud Run, the proxy is now built into the Cloud Run infrastructure via the **Cloud SQL connection name** flag (`--add-cloudsql-instances`) — no sidecar needed.
**Interview trap:** "What's the connection limit problem with Cloud SQL and how do you solve it?" Cloud SQL Postgres has a hard max connections (e.g. 1000 for db-n1-standard-8). Each application instance opens a JDBC connection pool (e.g. HikariCP default pool size 10). With many Cloud Run instances or GKE pods, you hit the limit fast. Solutions: (1) **PgBouncer** in transaction pooling mode — many application connections multiplex onto a few server connections; (2) **Cloud SQL's built-in connection pooling** (uses pgpool internally, available for Postgres); (3) reduce HikariCP `maximumPoolSize` per instance and design for smaller pools.
**Tags:** cloud-sql, auth-proxy, networking, security, connection-pooling

---

## Q-GCP-011 [bloom: understand] [level: regular]
**Question:** What is GCP Workload Identity in GKE, and why is it preferred over service account key files?
**Model answer:** **Workload Identity** is the mechanism by which a Kubernetes Service Account (KSA) in a GKE pod maps to a GCP Service Account (GSA), allowing the pod to obtain GCP tokens without any credential file on disk.

How it works:
1. You enable Workload Identity on the GKE cluster (`--workload-pool=PROJECT.svc.id.goog`).
2. You annotate the KSA: `iam.gke.io/gcp-service-account: mysa@project.iam.gserviceaccount.com`.
3. You grant the GSA the `roles/iam.workloadIdentityUser` role on the GSA itself, bound to the KSA identity.
4. Pods using that KSA hit the GKE metadata server (running as a DaemonSet on each node). The metadata server exchanges the pod's KSA token for a short-lived GCP access token for the mapped GSA.
5. GCP client libraries pick this up via ADC automatically — zero code changes.

Why this beats key files:
- No JSON key file to rotate, store, or leak.
- Tokens are short-lived (1 hour) and auto-refreshed.
- Revoke access by removing the IAM binding — instant effect, no "did we rotate the key everywhere?" question.
- Audit logs show which pod/workload accessed what resource, not just "this service account key was used."

Security posture: a leaked key file is an incident lasting until manual rotation. A Workload Identity compromise requires an attacker to be running code inside the pod — a much harder bar.
**Interview trap:** "Does Workload Identity work outside GKE?" For non-GKE workloads (Compute Engine, Cloud Run), you attach a service account directly to the resource — no key file needed, same ADC mechanism. For workloads outside GCP entirely (GitHub Actions, on-prem Jenkins), use **Workload Identity Federation** — map an external identity (OIDC token from GitHub Actions) to a GCP service account without long-lived keys.
**Tags:** iam, workload-identity, gke, security, service-accounts

---

## Q-GCP-012 [bloom: understand] [level: regular]
**Question:** Describe the full component chain of a GCP Global HTTP(S) Load Balancer. What role does each component play?
**Model answer:** GCP's Global HTTP(S) Load Balancer is a layer-7, anycast proxy — a single global IP, traffic enters the GCP PoP nearest to the user, and GCP's backbone routes it to your backend.

Component chain:
```
Client
  ↓ anycast IP
Forwarding Rule        (static IP + port → target proxy)
  ↓
Target HTTPS Proxy     (handles SSL termination, references URL map + cert)
  ↓
URL Map                (routes requests by host/path to backend services)
  ↓
Backend Service        (load balancing policy, health checks, Cloud Armor WAF attach point,
                        Cloud CDN attach point)
  ↓
Backend (one of):
  - Instance Group (Compute Engine VMs)
  - Serverless NEG (Cloud Run, App Engine, Cloud Functions)
  - Container NEG (GKE pods directly, bypassing kube-proxy)
  - Internet NEG (external backend)
```

Key operational facts:
- **Cloud Armor** attaches to the Backend Service — WAF rules, OWASP preconfigured rulesets, rate limiting, geo-blocking.
- **Cloud CDN** also attaches to the Backend Service — caches responses at GCP PoPs globally.
- **Serverless NEG**: to put Cloud Run behind a Global LB, create a Serverless NEG pointing to the Cloud Run service, attach it to a Backend Service in the LB chain.
- **Health checks** are regional probes; configure them on the Backend Service to match your `/actuator/health` path.
**Interview trap:** "Why can't you use a Regional Internal LB to serve global users with low latency?" A Regional Internal LB is inside a single region's VPC — it has a regional IP, not anycast. Traffic from Europe to a us-central1 regional LB travels the public internet to reach the region, then enters GCP. The Global LB's anycast means traffic enters at the nearest PoP — often 20–50 ms faster for international users.
**Tags:** networking, load-balancing, cloud-armor, cdn, cloud-run, gke

---

## Q-GCP-013 [bloom: understand] [level: regular]
**Question:** What is structured logging on GCP and how do you implement it in a Spring Boot application?
**Model answer:** **Structured logging** means emitting log lines as JSON objects rather than plain text strings. GCP Cloud Logging auto-parses JSON logs from stdout and populates searchable fields in Log Explorer.

GCP understands these special JSON fields automatically:
- `severity` → maps to log severity (INFO, WARNING, ERROR, CRITICAL)
- `message` → the human-readable text
- `httpRequest` → structured HTTP request/response info (method, URL, status, latency)
- `logging.googleapis.com/trace` → links the log entry to a Cloud Trace span
- `logging.googleapis.com/labels` → arbitrary key-value labels for filtering

In Spring Boot, use `logstash-logback-encoder` or `spring-cloud-gcp`'s built-in JSON appender:

```xml
<!-- logback-spring.xml -->
<appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <fieldNames>
      <message>message</message>
      <levelValue>[ignore]</levelValue>
    </fieldNames>
    <customFields>{"service":"order-service"}</customFields>
  </encoder>
</appender>
```

Or use `spring-cloud-gcp-starter-logging` which configures the JSON layout and injects the Cloud Trace ID from the `X-Cloud-Trace-Context` header automatically.

Operational benefit: in Log Explorer you can filter on `severity="ERROR" AND jsonPayload.traceId="abc123"` across thousands of log lines from hundreds of instances — impossible with unstructured text.
**Interview trap:** "What's the default log retention and what happens if you need longer?" Default retention for the `_Default` log bucket is **30 days**. Admin Activity audit logs are 400 days. For compliance (SOC 2, GDPR, PCI) you may need 90 days to 7 years. Solution: create a **log sink** before retention expires — export to Cloud Storage (cheapest long-term archival), BigQuery (queryable analytics), or Pub/Sub (stream to a SIEM). Sinks are a one-time config; don't wait for an audit to discover 30-day retention isn't enough.
**Tags:** logging, monitoring, structured-logging, spring-boot, observability

---

## Q-GCP-014 [bloom: understand] [level: regular]
**Question:** What is Cloud Secret Manager and when would you use it instead of a Kubernetes ConfigMap or an environment variable in a Cloud Run deployment?
**Model answer:** **Secret Manager** is GCP's managed secrets store: AES-256 encrypted at rest (CMEK option for your own keys), versioned, audit-logged via Cloud Audit Logs, with fine-grained IAM access control per secret.

Comparison:

| | Secret Manager | K8s ConfigMap | Env var in deploy manifest |
|--|---------------|---------------|---------------------------|
| Encrypted at rest | Yes (CMEK option) | No (etcd, base64 only) | No |
| Audit log (who read what when) | Yes | No | No |
| Versioned with rollback | Yes | Manual (recreate) | Manual redeploy |
| Rotation with event-driven notification | Yes (Pub/Sub event on new version) | Manual | Manual redeploy |
| Fine-grained IAM per secret | Yes | Namespace RBAC only | No |

**Use Secret Manager for:** database passwords, API keys, TLS private keys, OAuth client secrets — anything that constitutes a credential.

**Use ConfigMap / env vars for:** non-sensitive configuration (feature flags, timeouts, log levels, endpoint URLs without credentials).

In Spring Boot:
```properties
# application.properties
spring.datasource.password=${sm://db-password}
```
Requires `spring-cloud-gcp-starter-secretmanager`. At startup, Spring resolves `sm://` references by calling the Secret Manager API with the service account's ADC credentials.

In Cloud Run, you can also mount a secret as an environment variable or a volume-mounted file — the secret value is injected at container startup.
**Interview trap:** "K8s Secrets are encrypted, right?" By default, K8s Secrets are **base64-encoded in etcd, not encrypted**. Anyone with `kubectl get secret` RBAC permission or direct etcd access sees the decoded value. etcd encryption at rest must be explicitly configured and is not on by default in most GKE clusters. Secret Manager eliminates this concern entirely.
**Tags:** secret-manager, iam, security, spring-boot, kubernetes

---

## Q-GCP-015 [bloom: apply] [level: senior]
**Question:** You have a Spring Boot service on Cloud Run receiving Pub/Sub push messages. Under load, some messages are failing processing and ending up re-delivered repeatedly. Walk through how you would design the full error handling pipeline — from the Pub/Sub subscription configuration to the Cloud Run service to the dead-letter topic.
**Model answer:** Full pipeline design:

**1. Pub/Sub subscription configuration:**
```
Topic: orders.created
Subscription: orders-created-processor
  ack_deadline: 60s         (give the service time to process; extend via modifyAckDeadline for long jobs)
  max_delivery_attempts: 5  (re-try at most 5 times before dead-lettering)
  dead_letter_topic: orders-created-dlq
  retry_policy:
    minimum_backoff: 10s
    maximum_backoff: 300s   (exponential backoff between retries)
```

**2. Cloud Run service (push endpoint):**
```java
@PostMapping("/pubsub/orders")
public ResponseEntity<Void> handleOrder(@RequestBody PubSubMessage msg) {
    try {
        String payload = new String(Base64.getDecoder().decode(msg.getData()));
        orderService.process(parseOrder(payload));
        return ResponseEntity.ok().build();   // ACK: HTTP 200
    } catch (TransientException e) {
        // Tell Pub/Sub to retry: return non-2xx
        log.warn("Transient failure, will retry", e);
        return ResponseEntity.status(503).build();  // NACK: causes re-delivery
    } catch (PermanentException e) {
        // Poison message — return 200 to ACK (consume it), log + alert separately
        log.error("Permanent failure, discarding message", e);
        errorReportingService.report(e, payload);
        return ResponseEntity.ok().build();   // ACK to prevent infinite retry
    }
}
```

**3. Dead-letter topic:**
- After `max_delivery_attempts` Pub/Sub moves the message to `orders-created-dlq`.
- Attach a subscription to the DLQ: either a separate Cloud Run service that logs/alerts, or an alerting policy on `subscription/dead_letter_message_count` metric in Cloud Monitoring.
- Operations team investigates DLQ messages, fixes the root cause, re-publishes corrected messages if needed.

**4. Idempotency:**
- Pub/Sub may re-deliver before `max_delivery_attempts` is hit (e.g. ack deadline expiry).
- Service must deduplicate by message ID or use idempotent DB operations (upsert by order ID with `ON CONFLICT DO NOTHING`).

**5. Observability:**
- Cloud Monitoring alert on `pubsub.googleapis.com/subscription/dead_letter_message_count > 0`.
- Cloud Logging structured log with `orderId` and Pub/Sub message ID for correlation.
- Cloud Trace span covering the full message processing path.
**Interview trap:** "What if your Cloud Run service takes 90 seconds to process a message but the ack deadline is 60 seconds?" The deadline expires; Pub/Sub re-delivers. The service is now processing two copies simultaneously. Fix: call `modifyAckDeadline()` partway through processing to extend the deadline. The Spring `spring-cloud-gcp` library's `PubSubMessageHandler` does this automatically when configured with `ackMode = AUTO_ACK` — it extends the deadline during processing. Alternatively, design the processing to be fast (< ack_deadline) and offload long work to async tasks.
**Tags:** pubsub, cloud-run, error-handling, dead-letter, idempotency, observability

---

## Q-GCP-016 [bloom: apply] [level: senior]
**Question:** Describe Cloud Spanner's consistency model and explain why it is fundamentally different from Cloud SQL Postgres. When would you choose Spanner despite its higher cost?
**Model answer:** **Cloud Spanner's consistency model: external consistency (a.k.a. strict serializability).**

Spanner provides a consistency guarantee stronger than anything a traditional RDBMS offers across multiple nodes: read-write transactions appear to execute atomically at a single point in real time, and the ordering is consistent with real-time clock order across all nodes globally. This means:
- A read that starts after a committed write (by wall-clock time) always sees that write — even if the read and write happened on nodes in different continents.
- No stale reads possible in a strong-read, even across regions.

**How Spanner achieves this: TrueTime + Paxos.**
- **TrueTime** is Google's globally synchronized clock API that bounds clock uncertainty (typically < 7 ms). Each committed timestamp carries a confidence interval `[earliest, latest]`. Spanner waits out the uncertainty before committing — this is the "commit wait" that adds ~7 ms latency.
- **Paxos** replication groups manage each data shard; majority quorum writes ensure replicas agree before commit.
- Multi-shard transactions use 2-Phase Commit across Paxos groups, but the transaction coordinator ensures the global TrueTime ordering.

**Cloud SQL Postgres by contrast:**
- Postgres is a single-node RDBMS (or single-primary with streaming replication).
- HA replica is a hot standby, not a scale-out node.
- Replication is asynchronous by default (you can use synchronous, but it caps throughput).
- Cross-node consistency is simply not the model — there's one writer.

**When to pay for Spanner (3–10× Cloud SQL cost):**
- Your write volume requires horizontal scale-out — you need multiple writer nodes globally.
- You have a multi-region application where users in Asia and Europe both write and read from the same data, and you cannot tolerate stale reads or write conflicts.
- Strong consistency across shards is a business requirement (financial ledger, inventory with global reservations).
- You are past the limits of a single Postgres instance (multi-TB write-heavy workloads).

**When to stay on Cloud SQL:**
- Single-region application.
- Postgres-specific extensions (PostGIS, pg_trgm) that Spanner doesn't support.
- Team familiar with standard SQL and ORM tooling — Spanner's SQL dialect has caveats (no sequences, different timestamp handling, interleaved tables for locality).
**Interview trap:** "Can you just use Cloud SQL read replicas for horizontal scale?" Read replicas help for read-heavy workloads. But they introduce replication lag — a read replica may lag the primary by milliseconds to seconds. If your application reads its own writes (user submits a form, immediately reads the result) you can hit a replica that hasn't caught up yet. You need read-your-writes logic (send reads to primary after a write, or use Postgres `pg_last_wal_replay_lsn` tracking) to avoid this. Spanner's strong reads have no such problem.
**Tags:** spanner, cloud-sql, consistency, truetime, paxos, database, trade-offs

---

## Q-GCP-017 [bloom: apply] [level: senior]
**Question:** You need to add observability to a Spring Boot service on GKE — traces, metrics, and logs all correlated. Walk through your GCP-native implementation.
**Model answer:** **Three pillars: Cloud Trace, Cloud Monitoring, Cloud Logging — all stitched together by a shared trace ID.**

**1. Traces — Cloud Trace via OpenTelemetry:**
```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.google.cloud</groupId>
  <artifactId>spring-cloud-gcp-starter-trace</artifactId>
</dependency>
<!-- or use OpenTelemetry SDK + GCP exporter -->
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```
Spring Cloud GCP Trace auto-instruments Spring MVC, RestTemplate, WebClient, and Feign. Each request gets a `traceId` and `spanId`. Spans are exported to Cloud Trace via gRPC.

**2. Metrics — Micrometer → Cloud Monitoring:**
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-stackdriver</artifactId>
</dependency>
```
Spring Boot Actuator + Micrometer automatically exports JVM metrics, HTTP request counts/latencies, HikariCP pool metrics, and any custom `@Timed` / `MeterRegistry` metrics to Cloud Monitoring as custom metrics (`custom.googleapis.com/...`).

Set up alerting policies in Cloud Monitoring on:
- `http_server_requests_seconds_max > 2` (latency SLO breach)
- `hikaricp_connections_pending > 5` (connection pool exhaustion warning)

**3. Logs — structured JSON with trace correlation:**
Configure Logback JSON encoder to include `logging.googleapis.com/trace` using the active span's trace ID:
```json
{
  "severity": "ERROR",
  "message": "Order failed: inventory_exhausted",
  "logging.googleapis.com/trace": "projects/MY_PROJECT/traces/abc123def",
  "logging.googleapis.com/spanId": "deadbeef00000001",
  "orderId": "ORD-9991"
}
```
Spring Cloud GCP Trace injects the trace ID into the MDC automatically. The Logback appender picks it up.

**Outcome:** In Cloud Trace, click on a slow trace → click "View logs" → Log Explorer filters to log lines with that exact trace ID across all pod instances. Correlation is automatic.

**Alerting:** Cloud Monitoring alerting policy on a log-based metric (e.g. ERROR log count > 10 in 5 minutes) pages the on-call.
**Interview trap:** "Cloud Trace samples 100% of traces by default — is that a problem?" In high-throughput services (1000 req/s+), 100% sampling generates enormous trace data volume, adds latency per request (exporting spans), and incurs cost. Configure the sampling rate:
```properties
spring.cloud.gcp.trace.sampling-rate=0.01  # 1% sampling
```
Alternatively use tail-based sampling (sample 100% of traces with errors or high latency, 1% of normal traces) — requires a Collector like the OTel Collector with `tail_sampling` processor.
**Tags:** observability, cloud-trace, cloud-monitoring, cloud-logging, spring-boot, gke, opentelemetry

---

## Q-GCP-018 [bloom: apply] [level: senior]
**Question:** Design a Cloud Build pipeline for a Spring Boot service that builds, tests, pushes to Artifact Registry, and deploys to Cloud Run. What are the key security and operational considerations?
**Model answer:** **cloudbuild.yaml:**
```yaml
steps:
  # 1. Unit + integration tests
  - name: 'maven:3.9-eclipse-temurin-17'
    entrypoint: 'mvn'
    args: ['test', '--no-transfer-progress']
    env:
      - 'TESTCONTAINERS_HOST_OVERRIDE=host-gateway'

  # 2. Build Docker image
  - name: 'gcr.io/cloud-builders/docker'
    args:
      - 'build'
      - '--build-arg=JAR_FILE=target/app.jar'
      - '-t'
      - 'europe-docker.pkg.dev/$PROJECT_ID/backend/order-service:$SHORT_SHA'
      - '.'

  # 3. Push to Artifact Registry
  - name: 'gcr.io/cloud-builders/docker'
    args:
      - 'push'
      - 'europe-docker.pkg.dev/$PROJECT_ID/backend/order-service:$SHORT_SHA'

  # 4. Deploy to Cloud Run
  - name: 'gcr.io/cloud-builders/gcloud'
    args:
      - 'run'
      - 'deploy'
      - 'order-service'
      - '--image=europe-docker.pkg.dev/$PROJECT_ID/backend/order-service:$SHORT_SHA'
      - '--region=europe-west1'
      - '--platform=managed'
      - '--service-account=order-service-sa@$PROJECT_ID.iam.gserviceaccount.com'
      - '--no-allow-unauthenticated'

substitutions:
  _REGION: 'europe-west1'

options:
  logging: CLOUD_LOGGING_ONLY
  machineType: 'E2_HIGHCPU_8'
```

**Security considerations:**
- **Cloud Build service account** needs only: `roles/run.developer` + `roles/artifactregistry.writer` + `roles/iam.serviceAccountUser` (to set the run service account). Not `roles/editor`.
- **Secrets in the pipeline**: use `secretManager` integration in cloudbuild.yaml, not env vars. Never hardcode credentials.
- **Image signing**: optionally use Binary Authorization — sign images after push, enforce that only signed images deploy to Cloud Run.
- **Artifact Registry over Container Registry**: Container Registry (`gcr.io`) is deprecated. Artifact Registry is regional (reduces egress cost, improves latency), supports vulnerability scanning, and has fine-grained IAM per repository.

**Operational considerations:**
- Tag images with `$SHORT_SHA` (git commit hash) — immutable, traceable. Never use `latest` in production deployments.
- Use `--no-allow-unauthenticated` on Cloud Run — put a Global LB + IAP or an internal audience-bound service account in front.
- `CLOUD_LOGGING_ONLY` in options routes build logs directly to Cloud Logging — no GCS bucket needed, cheaper.
- Set up **build triggers** per branch: `main` → deploy to staging; release tag `v*` → deploy to prod.
**Interview trap:** "How do you prevent a bad deploy without rollback ceremony?" Cloud Run supports **traffic splitting**: deploy the new revision but send it 0% of traffic (`--no-traffic`). Validate the revision (smoke test, canary check), then shift traffic: `gcloud run services update-traffic order-service --to-revisions=REVISION=100`. If something goes wrong, roll back in seconds by shifting traffic back to the previous revision — no redeploy needed.
**Tags:** cloud-build, artifact-registry, cloud-run, ci-cd, security, deployment

---

## Q-GCP-019 [bloom: apply] [level: senior]
**Question:** Explain the GCP VPC model. How is it different from AWS VPC, and what are the implications of the global VPC design for a multi-region microservice architecture?
**Model answer:** **GCP VPC is global; AWS VPC is regional.** This is the fundamental architectural difference.

In GCP:
- A VPC network spans all regions. You create subnets per region, but they are all part of the same network.
- A VM in `europe-west1` can communicate via internal IP with a VM in `us-central1` on the same VPC over Google's private backbone — no public internet, no separate VPN gateway required.
- Firewall rules are global — one rule can apply to VMs in any region.

In AWS:
- A VPC is scoped to a single region. For multi-region connectivity you need VPC Peering (or Transit Gateway) between per-region VPCs. Each VPC has its own route tables, IGW, NAT gateway configuration.

**Implications for multi-region microservices on GCP:**
- Internal service-to-service calls (gRPC between microservices) can use private IPs across regions with no extra networking setup — low latency on Google's backbone.
- **Private Google Access** per subnet allows VMs without external IPs to call GCP APIs (Pub/Sub, Cloud Storage) without going through NAT — enable this on every subnet in production.
- **Cloud NAT** provides outbound internet for VMs without external IPs (software-defined, no gateway VM to maintain, auto-scales).
- **Shared VPC**: in a multi-project org, one "host project" owns subnets; "service projects" attach to them. Network policy lives in one place. Alternative: each project has its own VPC with VPC Peering between them. VPC Peering is not transitive — if A peers with B and B peers with C, A cannot reach C via B.
- **VPC Service Controls**: creates a security perimeter around GCP APIs within your org — even if credentials are stolen, data exfiltration is blocked because the API calls must originate from within the perimeter.
**Interview trap:** "Shared VPC vs VPC Peering — which supports transitive routing?" Neither does natively. Shared VPC avoids the transitive problem because all projects share the same VPC (same IP space, no peering needed). VPC Peering pairs two VPCs but is not transitive — A ↔ B and B ↔ C does not give A ↔ C. If you need hub-and-spoke transitive routing, you need a Network Connectivity Center (NCC) or a custom router VM.
**Tags:** networking, vpc, multi-region, private-google-access, shared-vpc, vpc-peering

---

## Q-GCP-020 [bloom: apply] [level: senior]
**Question:** Your service on GKE needs to read from BigQuery and write to Cloud Storage. Walk through setting up the least-privilege IAM and Workload Identity to make this work securely.
**Model answer:** **Goal: zero key files, minimal permissions, auditable.**

**Step 1: Create a dedicated GCP Service Account:**
```bash
gcloud iam service-accounts create order-exporter \
  --display-name="Order Exporter Service"
```

**Step 2: Grant only the permissions needed:**
```bash
# BigQuery: read access to the specific dataset
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:order-exporter@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/bigquery.dataViewer" \
  --condition="resource.name.startsWith('projects/PROJECT_ID/datasets/orders_dataset')"

# Cloud Storage: write access to a specific bucket only, not the whole project
gsutil iam ch \
  serviceAccount:order-exporter@PROJECT_ID.iam.gserviceaccount.com:roles/storage.objectCreator \
  gs://order-exports-bucket
```

Note: grant BigQuery `dataViewer` on the dataset, not the project. Grant Storage `objectCreator` on the bucket, not the project. Never use `roles/editor`.

**Step 3: Enable Workload Identity on the GKE cluster (if not already):**
```bash
gcloud container clusters update MY_CLUSTER \
  --workload-pool=PROJECT_ID.svc.id.goog
```

**Step 4: Create Kubernetes Service Account and annotate it:**
```yaml
# k8s manifest
apiVersion: v1
kind: ServiceAccount
metadata:
  name: order-exporter-ksa
  namespace: production
  annotations:
    iam.gke.io/gcp-service-account: order-exporter@PROJECT_ID.iam.gserviceaccount.com
```

**Step 5: Bind the KSA to the GSA:**
```bash
gcloud iam service-accounts add-iam-policy-binding \
  order-exporter@PROJECT_ID.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="serviceAccount:PROJECT_ID.svc.id.goog[production/order-exporter-ksa]"
```

**Step 6: Deploy pod using the KSA:**
```yaml
spec:
  serviceAccountName: order-exporter-ksa
  containers:
    - name: order-exporter
      image: ...
      # No env vars with credentials — ADC picks up tokens via metadata server
```

The Java GCP client libraries (BigQuery client, Storage client) call ADC at startup, get a token for `order-exporter@...`, and all API calls are authorized.
**Interview trap:** "What if you accidentally grant `roles/bigquery.admin` at the project level instead of dataset level?" Every service in the project can now be granted that broad access. But more importantly, the `order-exporter` service account itself now has admin access to all BigQuery datasets in the project — if the pod is compromised, the blast radius covers all BigQuery data. Least privilege at the resource level (dataset/bucket, not project) limits the blast radius to only what this specific workload actually needs.
**Tags:** iam, workload-identity, gke, bigquery, cloud-storage, least-privilege, security

---

## Q-GCP-021 [bloom: analyze] [level: senior]
**Question:** Compare Pub/Sub and Kafka for an event-driven microservices platform on GCP. Under what conditions would you choose one over the other?
**Model answer:** Both are distributed pub/sub messaging systems. The decision is architectural and operational, not just feature comparison.

**Pub/Sub strengths:**
- **Fully managed, zero ops**: no brokers to provision, patch, scale, or rebalance. Google handles all of it.
- **Global by default**: topics are global; push/pull subscriptions from any region with no cross-region configuration.
- **Serverless scaling**: handles tens of millions of messages/second without pre-provisioning. Auto-scales.
- **Dead-letter and retry**: built in, configurable per subscription.
- **GCP-native integration**: direct triggers for Cloud Run, Cloud Functions, Dataflow, BigQuery subscriptions (stream messages directly into BigQuery tables).
- **Cost model**: pay per message data volume. No idle broker cost.

**Pub/Sub limitations:**
- **No persistent replay by default**: message retention is up to 7 days. Kafka log compaction gives you permanent key-based state.
- **No consumer groups with offset management**: Pub/Sub subscriptions are not equivalent to Kafka consumer groups with explicit offset commit and lag tracking. Each subscription gets all messages independently; you can't have multiple competing consumers within one subscription do ordered partition-based work.
- **Ordering is subscription-scoped, not partition-scoped**: ordering keys guarantee order to a single subscription, but you can't have the same ordering guarantees across multiple consumers of the same message (like Kafka consumers in a consumer group sharing partitions).
- **No log compaction**: you can't use Pub/Sub as a change data capture (CDC) event log where the latest value per key is always queryable.

**Kafka strengths (on GCP, typically via Confluent Cloud or self-managed on GKE):**
- **Replay from any offset**: consumers can re-read history. Essential for event sourcing, rebuilding projections, reprocessing after a bug.
- **Consumer group / partition model**: multiple consumers share processing of a topic's partitions, with offset tracking per consumer group. Enables horizontal scaling with per-partition ordering.
- **Log compaction**: last value per key is retained indefinitely. Perfect for CDC (Debezium) and materialized views.
- **Exact-once semantics (EOS)**: Kafka transactions + idempotent producers give exactly-once delivery — no dedup needed at the consumer.
- **Rich ecosystem**: Kafka Streams, ksqlDB, Flink/Kafka integration, Debezium CDC.

**When to choose each:**
| Requirement | Pub/Sub | Kafka |
|-------------|---------|-------|
| Zero-ops managed messaging on GCP | Win | Lose (ops overhead) |
| At-most 7-day replay | Win | — |
| Indefinite replay / event sourcing | — | Win |
| CDC / log compaction | No | Win |
| Consumer group with offset tracking | Awkward | Win |
| Exact-once without app-level dedup | No | Win (EOS) |
| Trigger Cloud Run / Cloud Functions | Native | Via connector |
| Cost for low-volume infrequent traffic | Cheaper | More expensive (idle brokers) |
| >1 million msg/s without pre-planning | Win | Needs pre-provisioning |

**Operational verdict**: for a GCP-native shop doing async microservice decoupling (emit events, no replay needed), Pub/Sub is strictly superior operationally. Introduce Kafka only when event replay, consumer-group partition semantics, or CDC are genuine requirements.
**Interview trap:** "Pub/Sub has 'Seek' — doesn't that give you replay?" `Seek` allows you to reset a subscription's ack state to a timestamp or snapshot within the 7-day retention window. It's not the same as Kafka's durable, indefinite log. You can't seek to 30 days ago. You can't retain messages after the subscription has acknowledged them unless you explicitly didn't ack (which blocks all subsequent messages). Kafka's log retention is decoupled from consumer position — consumers can replay without affecting each other's offsets.
**Tags:** pubsub, kafka, messaging, event-driven, trade-offs, architecture

---

## Q-GCP-022 [bloom: analyze] [level: senior]
**Question:** You are designing a data platform for a backend that processes 50,000 orders/day and must answer ad-hoc analytical queries (e.g. "revenue by product category last 30 days") as well as serve real-time operational lookups (e.g. "get all orders for user X"). Describe which GCP storage services you would use for each need and why.
**Model answer:** This is a classic OLTP vs OLAP split. Use the right tool for each query pattern.

**OLTP / operational layer — Cloud SQL (Postgres):**
- 50k orders/day is ~0.6 writes/second on average. Cloud SQL Postgres handles this trivially.
- Operational queries: "get orders for user X" = indexed point lookup by `user_id`. Cloud SQL executes this in milliseconds.
- Relational model fits: orders → line_items → products with foreign keys, transactions, constraints.
- Cloud SQL Auth Proxy + private IP for the application tier.

**OLAP / analytics layer — BigQuery:**
- BigQuery is a serverless columnar OLAP engine. Analytical queries scan columns across large datasets.
- "Revenue by product category last 30 days" requires scanning 50k × 30 rows (~1.5M rows), aggregating sums and grouping. Cloud SQL can do this, but it blocks OLTP connections and gets slow at scale.
- BigQuery decouples analytics from OLTP: zero impact on the operational database.
- Load strategy: use **Datastream** (CDC from Cloud SQL Postgres to BigQuery via change logs) or a nightly scheduled export via Cloud Build/Cloud Scheduler → BigQuery load job. For near-real-time (<5 min lag), Datastream CDC is preferred.
- Partition BigQuery tables by `order_date` → queries on "last 30 days" prune partitions = less data scanned = lower cost.
- Cluster by `product_category_id` within partitions → collocated storage = further performance boost.

**Caching layer — Memorystore Redis:**
- Frequently-accessed operational data: user's order count, last order status, session data.
- Cache-aside pattern in Spring Boot: check Redis first, miss → query Cloud SQL, write to Redis with TTL.
- Memorystore Redis is fully managed (no connection management, auto-patching), regional, VPC-peered.

**Optional — Firestore (if requirements grow):**
- If you need flexible document storage for user profiles or product catalogue with varied attributes, Firestore's document model fits better than Postgres columns.
- Firestore's real-time listeners are useful for order status UI updates without polling.

**Summary architecture:**
```
Spring Boot app → Cloud SQL Postgres (operational writes/reads)
                → Memorystore Redis (hot cache)
                → Firestore (optional: catalogue / flexible docs)

Datastream CDC → BigQuery (analytical queries, dashboards, reports)
```
**Interview trap:** "Why not just use BigQuery for everything — operational and analytical?" BigQuery has high query latency (seconds, even for small queries due to slot scheduling overhead) and charges per byte scanned. It is not a transactional database — no row-level locking, no FK constraints, eventual consistency on streaming inserts. An order system needs sub-millisecond lookups, ACID transactions, and constraints. BigQuery excels at analytical workloads over large data; it's the wrong tool for operational OLTP.
**Tags:** bigquery, cloud-sql, memorystore, firestore, architecture, olap, oltp, data-platform

---

## Q-GCP-023 [bloom: analyze] [level: senior]
**Question:** A Cloud Run service is experiencing intermittent high latency spikes. Walk through your GCP-native debugging methodology.
**Model answer:** Systematic debugging: metrics → traces → logs → root cause.

**Step 1: Cloud Monitoring — establish the shape of the problem.**
In Cloud Monitoring, check the Cloud Run service dashboard:
- `run.googleapis.com/request_latencies` — 50th, 95th, 99th percentile. Is the spike at p50 (all requests slow) or p99 (occasional slow outliers)?
- `run.googleapis.com/container/instance_count` — is it spiking around scaling events? Cold starts cause latency.
- `run.googleapis.com/container/cpu/utilization` and `memory/utilization` — resource exhaustion?
- `run.googleapis.com/request_count` grouped by response code — are 5xx errors spiking alongside latency?

**Step 2: Cloud Trace — find the slow spans.**
Filter Cloud Trace for requests with latency > threshold. Click into a slow trace and examine the span waterfall:
- Is the slow span in the service code, or in a downstream call (Cloud SQL, external API)?
- Is there a span for "waiting for a connection" (HikariCP pool exhaustion)?
- Are there gaps (time between spans) suggesting thread-starvation or scheduling delays?

**Step 3: Cloud Logging — correlate with log events.**
In Log Explorer, filter `resource.type="cloud_run_revision"` + the timeframe of the spike:
- Filter by `severity >= WARNING`.
- Look for connection pool timeout messages (`HikariPool: Timeout waiting for connection`).
- Look for GC pause logs or OOMKilled events.
- Check for `Cloud SQL Auth Proxy` connection errors.

**Step 4: Common root causes and fixes:**
| Symptom | Root cause | Fix |
|---------|-----------|-----|
| Latency spikes correlate with instance count jumps | Cold starts | Set `--min-instances 1` or increase startup caching |
| Slow span on DB connection acquisition | HikariCP pool exhaustion | Reduce `maximumPoolSize`, add PgBouncer, or limit concurrency |
| Latency spikes every ~2 minutes | Cloud SQL connection recycling at proxy | Tune `idleTimeout` and `keepaliveTime` in HikariCP |
| p99 slow, p50 fine | GC full GC pause (heap pressure) | Tune `-Xmx`, use G1GC flags, check for memory leaks |
| All requests slow during traffic surge | CPU throttling | Cloud Run CPU is allocated only during request handling by default; switch to `--cpu-always-allocated` |

**Step 5: Cloud Run CPU allocation mode.**
By default, Cloud Run only allocates CPU during active request handling. If your app does background work (cache warming, health checks to Pub/Sub) between requests, those tasks get throttled. `--cpu-always-allocated` gives the instance CPU even between requests — at slightly higher cost.
**Interview trap:** "You found the slow span is in Cloud SQL — HikariCP wait time is 300 ms. You increase maxPoolSize from 5 to 20. Does this fix it?" Maybe, but it might make it worse. Cloud SQL Postgres has a max_connections limit (e.g. 500 for db-n1-standard-8). If you have 50 Cloud Run instances each with pool size 20, that's 1000 connections — exceeding the limit causes connection refused errors. The right fix is PgBouncer (transaction-mode pooling): many application connections multiplex onto a small number of server-side connections. PgBouncer itself runs as a sidecar or a separate Cloud Run service.
**Tags:** cloud-run, debugging, cloud-monitoring, cloud-trace, cloud-logging, cloud-sql, performance

---

## Q-GCP-024 [bloom: analyze] [level: master]
**Question:** You need to design a multi-region active-active architecture on GCP for a payments service — sub-100ms p99 globally, zero cross-region RPO (no data loss on regional failure), and the ability to survive a full regional outage with automatic failover. What are the components, trade-offs, and failure modes?
**Model answer:** This is an expert-level design question. Active-active globally consistent payments is one of the hardest distributed systems problems. Be honest about the trade-offs.

**Data layer — only Cloud Spanner satisfies the constraints:**
Zero RPO + globally consistent writes + automatic failover requires a system that replicates synchronously across regions before committing. Cloud Spanner with a multi-region configuration (`nam-eur-asia1` or a custom 5-region config) provides:
- Writes committed via Paxos majority quorum across replicas in ≥2 regions.
- External consistency (TrueTime) — no stale reads globally.
- Automatic leader re-election on regional failure (Paxos elects a new leader from surviving replicas within ~20–30 s).
- RPO = 0 because Paxos will not commit until a quorum (spanning regions) acknowledges.
- RTO = ~30–60 s for leader failover.

Trade-off: Spanner multi-region adds ~5–20 ms commit latency (TrueTime commit wait + cross-region replication round-trip). For payments, this is acceptable. For sub-millisecond gaming — it is not.

**Compute layer — Cloud Run in multiple regions:**
- Deploy the payments service to e.g. `europe-west1`, `us-central1`, `asia-east1`.
- Each region serves traffic independently; all regions write to the same Spanner instance.

**Traffic routing — Global HTTP(S) Load Balancer:**
- Single anycast IP; traffic routed to the nearest healthy region.
- Backend services per region with health checks (`/actuator/health`).
- If a region's Cloud Run service returns unhealthy or the health check fails, the Global LB routes traffic to the next nearest region automatically (no manual DNS TTL games).
- Failover is in-flight request re-routing — typically completed within seconds.

**Idempotency — payment deduplication:**
- Clients send a `Payment-Idempotency-Key` header.
- Service writes an idempotency record to Spanner atomically with the payment. If the same key arrives at any region, the Spanner read returns the existing result (no double charge).
- Spanner's strong reads guarantee the idempotency check sees all previously committed payments regardless of which region committed them.

**Pub/Sub — event emission after payment:**
- After a successful payment commit to Spanner, emit to a global Pub/Sub topic.
- Pub/Sub delivery is at-least-once; downstream consumers (ledger, notification service) must be idempotent.

**Failure modes and mitigations:**

| Failure | Behavior | Mitigation |
|---------|----------|------------|
| One region's Cloud Run fully down | Global LB routes away immediately | Health checks + failover timeout tuning |
| Network partition between regions | Spanner Paxos blocks writes if quorum unreachable | Accept writes-unavailable during partition (CAP: CP) |
| Spanner leader region lost | New leader elected in ~30 s; writes blocked during election | Design payment retries with idempotency keys |
| Spanner quorum lost (2/3 regions down) | Writes unavailable | Not survivable by design; run 5-region config for f=2 fault tolerance |

**CAP trade-off acknowledgment:**
Multi-region Spanner chooses CP (Consistency + Partition tolerance) over AP. During a network partition where quorum is unreachable, Spanner blocks writes. For payments (money movement), this is the correct choice — it is better to be unavailable for 30 seconds than to accept a payment that might be double-committed.
**Interview trap:** "Why not use Cloud SQL with async cross-region replication as a cheaper alternative?" Async replication means RPO > 0 — in a failover, you lose the uncommitted writes in flight. For payments this means lost or double-charged transactions. You would need to implement your own reconciliation system to detect and correct discrepancies. The operational complexity and business risk of that reconciliation usually costs more than Spanner's premium pricing. The right answer is: Spanner is expensive, and it is the only correct answer for zero-RPO globally consistent payments without building a distributed consensus layer yourself.
**Tags:** spanner, multi-region, architecture, availability, consistency, payments, cloud-run, global-load-balancer, master

---

## Q-GCP-025 [bloom: analyze] [level: master]
**Question:** Describe how Cloud Run's concurrency model works, how it interacts with autoscaling, and where it can cause resource contention in a Spring Boot service.
**Model answer:** **Cloud Run concurrency model:**
Cloud Run instances handle multiple requests simultaneously. The `--concurrency` flag (default: 80 for managed Cloud Run) sets the maximum number of concurrent requests a single instance will process before Cloud Run provisions a new instance.

This is fundamentally different from traditional thread-per-request models:

**Autoscaling logic:**
- Cloud Run monitors `concurrent requests / max concurrency` across all instances.
- When `total active requests / (instances × max_concurrency) > some threshold` → provision new instance.
- Scale-down: when instances are underutilized for a sustained period.
- Target utilization is configurable (`--max-instances`, `--min-instances`).

With `max_concurrency=80`, one instance can handle 80 simultaneous in-flight requests. If you get 800 concurrent requests, Cloud Run spins up ~10 instances. With `max_concurrency=1`, one request per instance — straightforward but expensive (1000 requests = 1000 instances).

**Interaction with Spring Boot (blocking I/O / thread pool model):**
Spring Boot (Spring MVC) uses a thread-per-request model (Tomcat thread pool, default 200 threads). Each thread blocks during I/O (DB query, HTTP call). This means:

- If Cloud Run allows 80 concurrent requests but your Tomcat thread pool is 50, you hit thread exhaustion before hitting Cloud Run's concurrency limit — 51st request queues waiting for a thread.
- If thread pool is 200 and concurrency is 80, you have headroom — but each of those 80 threads may block on DB connections, and HikariCP pool size of 10 means most threads are waiting on a connection.

**Contention chain:**
```
Cloud Run: 80 concurrent requests allowed
  → Tomcat: 50 thread pool → thread exhaustion at request 51
  → HikariCP: 10 pool size → connection wait at request 11
  → Cloud SQL: 500 max connections (with PgBouncer: multiplexed to ~50 server connections)
```

**Tuning guidance:**
1. Set Cloud Run concurrency to match your Tomcat thread pool size (or slightly below).
2. Size HikariCP pool: for blocking I/O, `pool_size = (core_count * 2) + effective_spindle_count`. For Cloud Run with 1 vCPU: pool of ~3–5 is often optimal (avoid connection pile-up at Cloud SQL).
3. For reactive Spring WebFlux / Kotlin coroutines: blocking I/O on virtual threads (Java 21 virtual threads with Tomcat) — concurrency can be much higher because threads are cheap. In this case, set Cloud Run concurrency higher (100–200) and let the JVM schedule virtual threads.

**Practical: Java 21 virtual threads on Cloud Run:**
```properties
spring.threads.virtual.enabled=true  # Spring Boot 3.2+
```
With virtual threads, 80 concurrent requests on 1 vCPU is feasible without thread pool exhaustion. The bottleneck moves to the HikariCP pool and downstream I/O, not CPU threads.
**Interview trap:** "If you set Cloud Run `--concurrency=1`, is your service safe from thread contention issues?" Yes for thread contention — only one request at a time. But cost explodes at scale: 1000 concurrent users = 1000 Cloud Run instances × 1 vCPU minimum = significant cost. More importantly, `--concurrency=1` forces Cloud Run to provision a new instance for every concurrent request, dramatically increasing cold-start frequency and latency spikes. The right approach is to tune concurrency to match your actual thread/connection capacity, not set it to 1 as a safety net.
**Tags:** cloud-run, autoscaling, concurrency, spring-boot, tomcat, hikaricp, performance, master

---

## Q-GCP-026 [bloom: analyze] [level: master]
**Question:** Explain the full BigQuery architecture — how does it store data, how does query execution work, and what are the operational decisions (partitioning, clustering, streaming vs batch load) that separate a well-tuned BigQuery table from a poorly-tuned one?
**Model answer:** **BigQuery architecture:**

BigQuery separates storage from compute (Dremel execution engine + Colossus distributed storage).

**Storage — Capacitor columnar format on Colossus:**
- Data stored in Capacitor, GCP's proprietary columnar format (similar to Parquet).
- Each column stored independently. A query selecting 3 columns from a 300-column table reads only 3 columns' data — no row-scan overhead.
- Data is split into row groups and replicated across Colossus (GCP's distributed file system).
- Colossus is shared infrastructure; "serverless" means you don't manage it.

**Query execution — Dremel:**
- Distributed query engine. A query is compiled into a DAG of operations, distributed across thousands of "slots" (units of CPU/RAM).
- On-demand pricing: $5/TB scanned. Flat-rate pricing: reserved slot capacity.
- Query coordinator → tree of execution nodes → leaf nodes read Colossus data.
- Results are materialized at each level, shuffled, aggregated. Petabyte-scale queries run in seconds because of massive parallelism.

**Partitioning — reduces bytes scanned:**
```sql
CREATE TABLE orders
PARTITION BY DATE(order_timestamp)
```
- Each partition is a separate set of Colossus files.
- A query with `WHERE order_timestamp >= '2026-05-01'` prunes to only relevant partitions — 10 days of data scanned instead of 3 years.
- Partition types: by time (`DATE`, `TIMESTAMP`, `DATETIME` columns), by integer range, or by ingestion time (`_PARTITIONTIME`).
- **Partition expiration** — auto-delete old partitions for data retention compliance.

**Clustering — collocates data within partitions:**
```sql
CREATE TABLE orders
PARTITION BY DATE(order_timestamp)
CLUSTER BY customer_id, product_category
```
- Within a partition, rows with the same `customer_id` and `product_category` are physically stored together.
- A query filtering on `customer_id = 'C001'` reads only the relevant file blocks — further pruning beyond partition-level.
- Unlike a traditional B-tree index, clustering is metadata-assisted file pruning, not a separate index structure.
- Clustering degrades over time as new data is inserted — BigQuery runs automatic recluster jobs (transparent, no maintenance window needed).

**Streaming inserts vs batch load jobs:**

| | Streaming Inserts (`insertAll`) | Batch Load (Storage Write API / load job) |
|--|---|----|
| Latency | Seconds (data queryable immediately) | Minutes (batch job) |
| Cost | $0.01/200MB — more expensive | Free for batch loads from GCS |
| De-duplication | Best-effort (within 1 min window) | N/A — load is atomic |
| Row size limit | 1 MB | 100 MB |
| Use for | Real-time dashboards, CDC streams | Nightly ETL, bulk migration |

**Preferred for new architectures: BigQuery Storage Write API** — replaces the older streaming inserts. Supports:
- Exactly-once semantics (committed streams).
- Default stream (streaming-like, no explicit commit needed).
- Much lower cost than the legacy `insertAll` streaming API.

**Common anti-patterns:**
- `SELECT *` on a 300-column table — reads all columns, costs 100× more than `SELECT id, total`.
- No partitioning on a time-series table — every query scans the full table.
- Joining two un-partitioned, un-clustered billion-row tables — massive shuffle cost, slow, expensive.
- Using `LIMIT` without `WHERE` — BigQuery scans all rows then limits; LIMIT does not reduce scan cost.
**Interview trap:** "BigQuery has an external table pointing at Cloud Storage Parquet files. Is that faster or slower than a native BigQuery table for analytical queries?" Slower in most cases. External tables read from Cloud Storage on each query without BigQuery's Capacitor columnar optimization and without partition metadata intelligence. For repeated query patterns, load data into native BigQuery tables (Storage Write API or load jobs). External tables are useful for one-off exploration of raw files or for keeping data in GCS as the source of truth without duplicating into BigQuery for ad-hoc analysis.
**Tags:** bigquery, architecture, partitioning, clustering, storage-write-api, olap, master

---

## Q-GCP-027 [bloom: analyze] [level: master]
**Question:** Describe the Bigtable data model. When does it outperform Cloud Spanner, Cloud SQL, and BigQuery, and what are the schema design rules that determine whether a Bigtable table is fast or pathologically slow?
**Model answer:** **Bigtable data model:**
Bigtable is a wide-column store (not relational, not document). The data model:
- **Row key** — the only indexed dimension. Rows are sorted lexicographically by row key.
- **Column families** — groups of columns defined at table creation. A row can have thousands of columns in a family.
- **Columns** — dynamic; each cell identified by `(row_key, column_family:column_qualifier, timestamp)`.
- **Cells** — each cell can have multiple timestamped versions (history).
- **No JOINs, no secondary indexes, no foreign keys, no transactions across rows** (single-row atomic operations only in standard Bigtable; cross-row transactions via Bigtable's distributed transactions feature in newer releases are limited).

Example schema for time-series IoT data:
```
Row key: device_id#INVERTED_TIMESTAMP (e.g. "sensor-42#9999999999")
Column family: metrics
  metrics:temperature -> 23.5 (at ts T)
  metrics:humidity    -> 61.2 (at ts T)
```

**When Bigtable wins:**
- **Time-series data at scale** — billions of rows, millisecond point lookups and range scans by `(device_id, time_range)`. Cloud SQL is too slow at this volume; Spanner is overkill (and more expensive) for simple key-range queries; BigQuery is too high latency.
- **Wide rows with many dynamic columns** — e.g. event metadata with varying attributes, user feature vectors with thousands of feature IDs.
- **Very high write throughput** — Bigtable is optimized for millions of writes/second. Cloud SQL would need extreme sharding; Spanner is strong-consistent but has higher commit latency.
- **Low-latency key lookups** — single-digit millisecond latency for row key lookups. BigQuery: seconds minimum.

**When Bigtable loses:**
- **Ad-hoc analytical queries (SQL, aggregations, JOINs)** — BigQuery wins. Bigtable has no SQL engine.
- **Transactional consistency across rows** — Cloud Spanner wins.
- **Relational data with FK constraints, complex SQL** — Cloud SQL wins.
- **Small-scale** — Bigtable minimum billing is 3 nodes × ~$0.65/hour = significant fixed cost. Don't use it below ~1 TB of data or ~100k operations/second.

**Schema design rules — the difference between fast and broken:**

1. **Row key design is everything.** Bigtable can only scan efficiently by row key prefix. All "queries" must be expressible as "give me rows from key X to key Y."
   - Good: `user_id#inverted_timestamp` for user event timeline.
   - Bad: storing multiple query dimensions in separate columns and expecting to filter by them — Bigtable will full-scan.

2. **Avoid row key hotspots.** Sequential timestamps as row keys concentrate writes on the last tablet (the "hot tablet" problem). Distribute writes:
   - Invert the timestamp: `MAX_LONG - timestamp` spreads recent writes.
   - Hash-prefix the key: `MD5(user_id)[:4]#user_id#timestamp` randomizes across tablets.

3. **Column family count = tablet split boundary consideration.** Keep column family count low (1–3). Each family has its own GC policy.

4. **Cell size < 10 MB.** Very large cells cause performance degradation in compaction.

5. **GC policies on column families.** Without a GC policy, every version of every cell accumulates forever. Define `MaxVersionsGCRule` or `MaxAgeGCRule` on each family to control data growth.
**Interview trap:** "You need to query Bigtable for 'all events for user X in the last 7 days' — how do you design the row key?" Row key: `user_id#inverted_unix_ts`. Inverted timestamp means recent events have lexicographically smaller keys. A range scan from `user_id#(inverted_now)` to `user_id#(inverted_7days_ago)` returns the last 7 days in order. If you used a forward timestamp, recent events would be at the "end" of the table for that user prefix — same range scan works, but you'd scan forward. The inversion is conventional for "get latest N" queries via `ReadRows` with a limit.
**Tags:** bigtable, wide-column, schema-design, row-key, time-series, architecture, master
