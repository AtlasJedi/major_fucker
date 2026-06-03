# Containers & Kubernetes — question bank

> Docker and Kubernetes are table stakes for every senior backend engineer in 2026. You containerize your Spring Boot service with Docker, ship it to a registry, and Kubernetes orchestrates it — handling scheduling, self-healing, scaling, and config injection. This bank covers the full stack: Docker image mechanics (layers, multi-stage builds, distroless), all core K8s objects (Pod through CronJob), production operations (probes wired to Spring Boot Actuator, resource limits, QoS, HPA, scheduling), deployment strategies, Helm packaging, and storage. These topics appear in senior backend interviews at companies like Allegro (1000+ services on K8s), Coforge, and any GCP/cloud-native shop. Gaps here signal "junior who got lucky in a DevOps team", not "senior who owns the full lifecycle".

## Scope

- Docker: image vs container, union filesystem / layer caching, multi-stage builds, distroless/slim base images
- Docker: .dockerignore, ENTRYPOINT vs CMD semantics, image tagging and registries, image scanning
- Kubernetes core objects: Pod, ReplicaSet, Deployment (rolling update, rollback), Service (ClusterIP/NodePort/LoadBalancer), Ingress
- Kubernetes objects: ConfigMap, Secret (base64 gotcha), Namespace, StatefulSet, DaemonSet, Job, CronJob
- Probes: liveness, readiness, startup — mechanics, failure behavior, and Spring Boot Actuator health groups wiring
- Resource requests vs limits, QoS classes (Guaranteed/Burstable/BestEffort), OOMKilled, CPU throttling
- Horizontal Pod Autoscaler (HPA) — how it works, metrics, lag
- Scheduling: nodeSelector, affinity/anti-affinity, taints and tolerations
- Deployment strategies: RollingUpdate, Recreate, blue/green, canary
- Helm: charts, values, releases, chart upgrades and rollbacks
- Persistent Volumes, PersistentVolumeClaims, StorageClass — when and why
- Self-healing: what K8s actually does vs what it cannot fix
- 12-factor config: env vars, ConfigMap injection, secrets never in images

---

## Q-K8S-001 [bloom: recall] [level: junior]
**Question:** What is the difference between a Docker image and a Docker container?

**Model answer:** A **Docker image** is an immutable, layered filesystem snapshot plus metadata (entrypoint, env vars, exposed ports). It lives on disk/registry and never runs by itself. A **Docker container** is a running instance of an image — it adds a writable layer on top of the read-only image layers, plus an isolated PID namespace, network namespace, and cgroup. Analogy: image = class, container = object instance. Multiple containers can run from the same image without affecting each other; writes from one container go into its own writable layer (copy-on-write), not into the image. Stopping a container does not delete its writable layer (unless you use `docker run --rm`); `docker commit` can snapshot that layer into a new image, though this is rarely done in CI.

**Interview trap:** "So containers share the image layers — does that mean container A can read files container B wrote?" No. Each container has its own isolated writable layer. The shared read-only image layers are shared in memory (page cache) but not mutated. Container-to-container file sharing requires explicit bind mounts or volumes.

**Tags:** docker, image, container, layers, copy-on-write

---

## Q-K8S-002 [bloom: recall] [level: junior]
**Question:** How does Docker image layer caching work, and what's the practical rule for ordering Dockerfile instructions?

**Model answer:** Docker images are built as a stack of immutable layers. Each instruction (`FROM`, `COPY`, `RUN`, `ENV`, etc.) produces one layer. The Docker daemon caches each layer by a hash of: the instruction text + the content of any files copied. On rebuild, if a layer's hash matches the cache, Docker reuses it — skipping the expensive `RUN mvn package` or `RUN apt-get install`. **Cache invalidation cascade:** once one layer is invalidated, ALL subsequent layers are also rebuilt (even if their content hasn't changed). Practical rule: **put the most stable instructions first, most volatile last**.

For a typical Java service Dockerfile:
```dockerfile
FROM eclipse-temurin:21-jre-alpine          # rarely changes
COPY pom.xml .                              # changes only on dep changes
RUN mvn dependency:go-offline -q            # expensive; cached until pom changes
COPY src/ src/                              # changes every commit
RUN mvn package -DskipTests
```
This way, `mvn dependency:go-offline` is cached across commits where only `src/` changes. If you `COPY . .` before the dependency step, every source change invalidates the dependency cache layer.

**Interview trap:** "What about `ADD` vs `COPY`?" `ADD` supports URL fetching and auto-extracting tar archives; `COPY` is a strict file copy. Prefer `COPY` — it's explicit and its behavior is predictable for cache purposes. `ADD` with a URL always invalidates cache because Docker can't know if the remote file changed.

**Tags:** docker, layer-caching, dockerfile, build-optimization

---

## Q-K8S-003 [bloom: understand] [level: junior]
**Question:** What is a multi-stage Docker build, and why should you use one for a Spring Boot application?

**Model answer:** A multi-stage build uses multiple `FROM` instructions in one Dockerfile. Each stage creates its own image; only the final stage is kept. Earlier stages are discarded — their build tools, intermediate artifacts, and source code never end up in the production image.

For a Spring Boot fat JAR:
```dockerfile
# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn package -DskipTests

# Stage 2 — runtime (distroless or slim JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why it matters:** The builder stage contains Maven, JDK, source code, test dependencies. The production image contains only the JRE + the JAR. Result: production image is ~150 MB instead of ~600 MB. Smaller image = smaller attack surface (fewer binaries to exploit), faster pull, faster startup in cold-start scenarios. This is the industry standard — shipping a Maven/Gradle image to production is a red flag in any code review.

For even smaller images, use Spring Boot's layered JAR feature + extract layers into separate Docker layers, so only the `application` layer (your code) is rebuilt on every push.

**Interview trap:** "What's a distroless image?" Google's distroless images contain only the runtime (JRE) and its direct dependencies — no shell, no package manager, no `bash`, no `curl`. `gcr.io/distroless/java21` is ~50 MB vs ~100 MB for `eclipse-temurin:21-jre-alpine`. Minimal attack surface — an attacker who breaks into the container has no shell to interact with. Tradeoff: debugging (`kubectl exec /bin/sh`) doesn't work; need ephemeral debug containers instead.

**Tags:** docker, multi-stage-build, distroless, image-size, security

---

## Q-K8S-004 [bloom: understand] [level: junior]
**Question:** What is the difference between ENTRYPOINT and CMD in a Dockerfile?

**Model answer:** Both define what runs when the container starts, but they serve different roles:

- **ENTRYPOINT** — the executable that is always run. It cannot be overridden by `docker run <image> <args>`; those args are appended to ENTRYPOINT instead.
- **CMD** — default arguments. If ENTRYPOINT is set, CMD provides its default arguments (overridable at `docker run`). If ENTRYPOINT is not set, CMD is the full command to run.

| Form | ENTRYPOINT | CMD | Result |
|------|-----------|-----|--------|
| exec (preferred) | `["java", "-jar", "app.jar"]` | `["--spring.profiles.active=prod"]` | `java -jar app.jar --spring.profiles.active=prod` |
| shell form | `java -jar app.jar` | — | spawns `/bin/sh -c "java -jar app.jar"` — PID 1 is `sh`, not Java |

**Critical gotcha: shell form vs exec form.** Shell form (`ENTRYPOINT java -jar app.jar`) spawns a `/bin/sh -c` subprocess. Java is NOT PID 1 — it's a child of sh. Kubernetes sends `SIGTERM` to PID 1 (sh), which may not forward the signal to Java. Your app won't get a graceful shutdown signal. **Always use exec form (JSON array) so the process is PID 1 and receives SIGTERM directly.**

```dockerfile
# Correct — exec form, Java is PID 1
ENTRYPOINT ["java", "-jar", "app.jar"]

# Wrong — shell form, Java is PID 2+, won't receive SIGTERM
ENTRYPOINT java -jar app.jar
```

**Interview trap:** "How do you pass JVM flags without hardcoding them in the image?" Use `JAVA_OPTS` env var and reference it in the ENTRYPOINT: `ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]`. The `exec` replaces sh with Java so it becomes PID 1.

**Tags:** docker, entrypoint, cmd, pid1, graceful-shutdown

---

## Q-K8S-005 [bloom: recall] [level: junior]
**Question:** What is .dockerignore and why does it matter?

**Model answer:** `.dockerignore` is a file in the build context directory that lists patterns for files/directories Docker should exclude from the build context sent to the daemon. Before any `COPY` instruction executes, Docker sends the entire build context (the directory you run `docker build` from) to the daemon. Without `.dockerignore`, this includes `target/`, `.git/`, `node_modules/`, IDE files — potentially hundreds of MB — over the Docker socket on every build.

Typical `.dockerignore` for a Java project:
```
target/
.git/
.github/
*.log
*.class
.idea/
*.iml
**/.DS_Store
```

**Why it matters:** (1) **Build speed** — sending a 500 MB `target/` directory on every `docker build` adds seconds of latency. (2) **Cache correctness** — `.git/` contains metadata that changes on every commit; including it in the context invalidates layer cache even when source hasn't changed. (3) **Security** — accidentally copying `.env` files, credentials, or private keys into the image is a critical vulnerability. `.dockerignore` is the last line of defense.

**Interview trap:** Does `.dockerignore` affect what's in the final image? No — it only affects the build context. The final image content is determined by your `COPY`/`ADD` instructions. But if you do `COPY . .` without a restrictive `.dockerignore`, you'll copy everything not excluded into the image.

**Tags:** docker, dockerignore, build-context, security

---

## Q-K8S-006 [bloom: recall] [level: junior]
**Question:** What is a Kubernetes Pod, and why is the Pod (not the container) the atomic unit of scheduling?

**Model answer:** A **Pod** is the smallest deployable unit in Kubernetes — one or more containers that are always scheduled together on the same node, share the same network namespace (same IP, same `localhost`, same port space), and optionally share volumes. The containers in a pod start and stop together.

Pod = "colocated execution unit". K8s schedules pods, not individual containers. This enables the **sidecar pattern**: a main application container + a sidecar container (e.g., Envoy proxy, log shipper, vault agent) sharing `localhost` with no network overhead between them. In a service mesh (Envoy, Istio), every pod gets an injected Envoy sidecar that intercepts all in/out traffic via iptables redirect — the main app container talks to `localhost:15001` and Envoy handles the real traffic.

A Pod has one IP address (in the pod's network namespace). Containers within the pod reach each other via `localhost:<port>`. Two different pods communicate via their pod IPs (or via a Kubernetes Service).

**Interview trap:** "Can you run multiple replicas of the same container in one Pod?" No — multiple containers in one Pod are different types of containers (main + sidecars/init containers), not replicas. For replicas, you use a ReplicaSet or Deployment that creates multiple Pods.

**Tags:** kubernetes, pod, sidecar, network-namespace, scheduling

---

## Q-K8S-007 [bloom: understand] [level: junior]
**Question:** What are the three Kubernetes Service types (ClusterIP, NodePort, LoadBalancer), and when would you use each?

**Model answer:** A Kubernetes **Service** is a stable virtual IP + DNS name that load-balances traffic to a set of Pods (selected by label selector). Services abstract away Pod churn (pods get new IPs on restart).

| Type | Reachable from | Use case |
|------|---------------|----------|
| **ClusterIP** (default) | Inside the cluster only | Service-to-service communication. `my-service.namespace.svc.cluster.local` resolves to the ClusterIP. kube-proxy implements it via iptables/IPVS rules on every node. |
| **NodePort** | External via `<NodeIP>:<NodePort>` (port 30000–32767) | Development, bare-metal without cloud LB. Exposes the service on every node's IP. Not production-grade — clients must know node IPs, single point of failure. |
| **LoadBalancer** | External via cloud load balancer | Production external traffic. Provisions a cloud LB (GCP GCLB, AWS ALB/NLB, Azure LB) automatically. Each service gets its own LB = expensive at scale. |

In practice: internal microservices use ClusterIP. External traffic enters via an Ingress controller (one LB fronting many services via HTTP routing rules) rather than one LoadBalancer per service.

**Interview trap:** "How does kube-proxy implement ClusterIP?" kube-proxy runs on every node. It watches the K8s API for Service/Endpoint changes and programs iptables (or IPVS in ipvs mode) rules that DNAT traffic destined for the ClusterIP to one of the backing pod IPs. No actual proxy process in the data path — it's kernel-level NAT.

**Tags:** kubernetes, service, clusterip, nodeport, loadbalancer, kube-proxy

---

## Q-K8S-008 [bloom: understand] [level: junior]
**Question:** What is a Kubernetes Deployment, and what happens during a rolling update?

**Model answer:** A **Deployment** manages a ReplicaSet, which manages N identical Pods. The Deployment controller continuously reconciles desired state (spec) with actual state (status). Key fields:

```yaml
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # can temporarily have 4 pods (3+1)
      maxUnavailable: 0  # never drop below 3 ready pods
  selector:
    matchLabels:
      app: my-service
  template:               # Pod template — changing this triggers rollout
    spec:
      containers:
      - name: app
        image: my-service:2.1.0
```

**Rolling update flow:** When you change the pod template (e.g., new image tag), K8s creates a NEW ReplicaSet with the new template, then gradually scales it up while scaling down the old ReplicaSet. With `maxUnavailable: 0, maxSurge: 1`: adds 1 new pod → waits for it to pass readiness probe → removes 1 old pod → repeat until new RS has 3, old RS has 0. Old RS is kept at 0 replicas (not deleted) to allow rollback.

**Rollback:** `kubectl rollout undo deployment/my-service` — K8s scales the old RS back up and scales down the new RS. History is limited to `revisionHistoryLimit` (default 10).

**Interview trap:** "What happens if the new pod never becomes ready?" The rolling update stalls — K8s doesn't proceed to the next batch and doesn't roll back automatically. You must detect this via a deployment timeout (`progressDeadlineSeconds`) and act. This is why readiness probes must be correct — a broken probe that always returns ready will let a broken deployment roll out fully.

**Tags:** kubernetes, deployment, rolling-update, rollback, replicaset, readiness-probe

---

## Q-K8S-009 [bloom: understand] [level: regular]
**Question:** What is a Kubernetes Ingress, and how does it differ from a LoadBalancer Service?

**Model answer:** An **Ingress** is an API object that defines HTTP(S) routing rules — routing by host, path, or headers to backend Services. It requires an **Ingress Controller** to actually implement the rules (nginx-ingress, Traefik, AWS ALB Ingress Controller, GKE Ingress, etc.). The controller watches Ingress objects and configures the underlying load balancer or proxy accordingly.

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /orders
        pathType: Prefix
        backend:
          service:
            name: order-service
            port: { number: 8080 }
      - path: /payments
        pathType: Prefix
        backend:
          service:
            name: payment-service
            port: { number: 8080 }
  tls:
  - hosts: [api.example.com]
    secretName: tls-cert-secret
```

**Ingress vs LoadBalancer Service:**
- LoadBalancer Service: one cloud LB per Service. 10 services = 10 LBs = expensive.
- Ingress: one LB (the ingress controller's) routes to many backend Services based on HTTP rules. TLS termination at ingress. Cost-effective.

**Interview trap:** "Does the Ingress object do anything without an Ingress Controller?" No. An Ingress object is just a config — it has no effect until a controller processes it. This is a common gotcha when first deploying to a bare cluster.

**Tags:** kubernetes, ingress, ingress-controller, loadbalancer, tls, routing

---

## Q-K8S-010 [bloom: understand] [level: regular]
**Question:** What are ConfigMaps and Secrets in Kubernetes? How do you inject them into a pod, and what is the critical security gotcha with K8s Secrets?

**Model answer:** **ConfigMap** stores non-sensitive configuration as key-value pairs (or files). **Secret** stores sensitive data — same structure, but base64-encoded. Both can be injected into pods as:

1. **Environment variables:**
```yaml
env:
- name: SPRING_DATASOURCE_URL
  valueFrom:
    configMapKeyRef:
      name: app-config
      key: datasource.url
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: app-secrets
      key: db-password
```

2. **Volume mounts** (files on disk — preferred for certs, multi-line configs):
```yaml
volumes:
- name: config-volume
  configMap:
    name: app-config
volumeMounts:
- mountPath: /config
  name: config-volume
```

**12-factor alignment:** Config in environment (or files mounted from env) — never baked into the image. `SPRING_PROFILES_ACTIVE=prod` set as env var in the Deployment spec; no profile config inside the JAR/image. Spring's relaxed binding maps `SPRING_DATASOURCE_URL` → `spring.datasource.url` automatically.

**Critical security gotcha:** Kubernetes Secrets are **base64-encoded, not encrypted**, in etcd by default. `kubectl get secret app-secrets -o jsonpath='{.data.db-password}' | base64 -d` reveals the plaintext. Anyone with `kubectl get secret` RBAC permissions sees the value. Mitigations: (1) enable **etcd encryption at rest** in the cluster; (2) use **external secrets** (Sealed Secrets, external-secrets-operator pulling from Vault or GCP Secret Manager); (3) tighten RBAC so only the service account that mounts the secret can read it.

**Interview trap:** "Can a ConfigMap update be picked up by a running pod without restart?" If mounted as a volume, yes — kubelet syncs ConfigMap files periodically (~1 min). If injected as env vars, no — env vars are set at pod startup and don't change. Spring `@RefreshScope` + ConfigMap volume mount + a Spring Cloud Bus refresh event can reload config without pod restart.

**Tags:** kubernetes, configmap, secret, 12-factor, env-vars, security, etcd-encryption

---

## Q-K8S-011 [bloom: understand] [level: regular]
**Question:** What are Kubernetes liveness, readiness, and startup probes? When should each fire, and how do you wire them to Spring Boot Actuator?

**Model answer:** Three independent probes, each independently configured and independently actionable:

| Probe | Question | Failure action | Use for |
|-------|----------|---------------|---------|
| **Liveness** | Is the process alive and not stuck? | Kill + restart the pod | Deadlocks, infinite loops, corrupted in-memory state — cases where the JVM is up but the app can never recover on its own |
| **Readiness** | Is it ready to serve traffic? | Remove pod from Service endpoints (no kill) | Startup warmup, waiting for caches to load, downstream dep temporarily unavailable |
| **Startup** | Has the app finished starting? | If fails before `failureThreshold * periodSeconds`, kill pod | Slow-starting apps (large Spring context, big caches) — disables liveness check during startup window |

Spring Boot Actuator (Boot 2.3+) exposes two dedicated health groups:

```yaml
# application.yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
# /actuator/health/liveness  → checks LivenessState
# /actuator/health/readiness → checks ReadinessState + custom ReadinessIndicators
```

Kubernetes Deployment spec:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
  failureThreshold: 3

startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30     # 30 * 10s = 5 min max startup
  periodSeconds: 10
```

**Critical rule:** Liveness probe must NOT check external dependencies (DB, Redis, downstream APIs). DB outage → liveness fails → pod restarted → still can't reach DB → restart loop that thrashes the cluster. Liveness = "is the JVM/app process functional". Readiness = "are all external deps available". Add a custom `ReadinessIndicator` bean for DB/Redis health; never add it to liveness.

**Interview trap:** "What happens between when a pod starts and when the readiness probe first succeeds?" The pod is not added to Service endpoints — it doesn't receive traffic. This is the grace period for Spring context startup, DB connection pool warmup, cache loads, etc. Without this, you'd get 502s during rolling deployments.

**Tags:** kubernetes, liveness-probe, readiness-probe, startup-probe, spring-boot-actuator, health-groups

---

## Q-K8S-012 [bloom: apply] [level: regular]
**Question:** Explain Kubernetes resource requests and limits. What are the three QoS classes, and how does OOMKilled happen?

**Model answer:** Every container in a pod can specify two resource constraints:

- **Request:** the amount of CPU/memory the container is *guaranteed*. The scheduler uses requests to decide which node has enough capacity to place the pod. A node is considered full when the sum of requests across all pods on it equals available capacity.
- **Limit:** the maximum the container can use. CPU limits are enforced by the CPU cgroup throttling; memory limits are enforced by the OOM killer.

```yaml
resources:
  requests:
    cpu: "250m"     # 0.25 CPU cores (millicores)
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"
```

**Three QoS classes (K8s assigns automatically):**

| QoS | Condition | Eviction priority | Effect |
|-----|-----------|------------------|--------|
| **Guaranteed** | All containers have `requests == limits` for both CPU and memory | Evicted last (only under extreme pressure) | Stable; no bursting |
| **Burstable** | At least one container has requests < limits | Evicted second | Can burst; may be evicted under memory pressure |
| **BestEffort** | No requests or limits set | Evicted first | No guarantee; any spare resources |

**OOMKilled:** When a container exceeds its memory limit, the Linux OOM killer terminates the container process. K8s restart policy kicks in (typically `Always`) and restarts the pod. The pod status shows `OOMKilled` in `kubectl describe pod`. Root causes: (1) JVM heap configured larger than the container limit (`-Xmx512m` on a container limited to 256Mi); (2) memory leak; (3) limit set too low for the actual workload. Fix: set JVM heap relative to container memory (`-XX:MaxRAMPercentage=75.0` lets the JVM calculate heap from cgroup memory limit).

**CPU throttling:** Unlike memory, CPU over-limit doesn't kill — it throttles. The process gets fewer CPU cycles. This is invisible (no event, no restart) but causes latency spikes. High `throttled_time` in cgroup CPU stats is the diagnostic signal. Monitored via `container_cpu_cfs_throttled_seconds_total` in Prometheus.

**Interview trap:** "Why is setting no resource limits dangerous?" BestEffort pods can consume all node memory and CPU, starving other pods. In production, every pod should have at least memory limits (and ideally Guaranteed QoS for critical services) to prevent noisy-neighbor problems.

**Tags:** kubernetes, resource-requests, resource-limits, qos, oomkilled, cpu-throttling, cgroup

---

## Q-K8S-013 [bloom: understand] [level: regular]
**Question:** What is StatefulSet and when would you use it instead of a Deployment?

**Model answer:** A **StatefulSet** manages pods that need stable, persistent identity across rescheduling. Unlike Deployment pods (which are interchangeable, get random names), StatefulSet pods have:

1. **Stable network identity:** pods are named `<name>-0`, `<name>-1`, ... These names persist across pod restarts. DNS: `<pod-name>.<service-name>.<namespace>.svc.cluster.local`.
2. **Stable persistent storage:** each pod gets its own PersistentVolumeClaim via `volumeClaimTemplates`. Pod `db-0` always gets volume `data-db-0`, even after reschedule.
3. **Ordered creation/deletion:** pods are created 0, 1, 2 in order; deleted 2, 1, 0 in order. Each pod must be Ready before the next starts.

**Use cases:** stateful data stores: PostgreSQL, MongoDB, Cassandra, Kafka brokers, ZooKeeper, Elasticsearch. Any workload where pod identity matters — e.g., a Kafka broker needs to be at the same hostname so producers/consumers can reconnect, and it needs to read the same disk it wrote to before.

**Vs Deployment:**
- Deployment: stateless replicas. Any pod can serve any request. Replace freely.
- StatefulSet: each pod is unique. `db-0` is the primary; `db-1` is the replica — these roles matter.

**Interview trap:** "Does a StatefulSet handle leader election or replication for you?" No. StatefulSet guarantees stable identity and storage; replication logic (e.g., Postgres streaming replication, Kafka leader election) is the responsibility of the software running in the pods or a separate Operator. K8s operators (e.g., Zalando Postgres Operator, Strimzi for Kafka) layer that logic on top of StatefulSet.

**Tags:** kubernetes, statefulset, persistent-storage, stable-identity, stateful-workloads

---

## Q-K8S-014 [bloom: understand] [level: regular]
**Question:** What are DaemonSet, Job, and CronJob? Give a concrete use case for each.

**Model answer:**

**DaemonSet:** ensures exactly one pod runs on every (or selected) node. When a new node joins the cluster, the DaemonSet controller automatically creates a pod on it. Removal from a node deletes the pod.
Use cases: log collectors (Fluentd, Filebeat — must run on every node to collect logs), metrics agents (Prometheus node-exporter — must see every node's metrics), network plugins (CNI agents — one per node), security scanning agents.

**Job:** runs one or more pods to completion. Unlike Deployment pods (which restart on failure), Job pods complete and exit. The Job controller tracks successful completions.
```yaml
spec:
  completions: 5      # need 5 successful completions
  parallelism: 2      # run up to 2 pods in parallel
  backoffLimit: 3     # retry failing pods up to 3 times
```
Use cases: database migrations, batch data processing, report generation, one-off data imports.

**CronJob:** creates a Job on a cron schedule. Wraps Job with a `schedule` field (standard cron syntax).
```yaml
spec:
  schedule: "0 2 * * *"   # daily at 2am
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: cleanup
            image: cleaner:1.0
```
Use cases: nightly data aggregation, scheduled report emails, cache warming, cleanup tasks, database backups.

**Interview trap:** "What happens if a CronJob's previous run is still going when the next one is due?" Controlled by `concurrencyPolicy`: `Allow` (default — both run), `Forbid` (skip the new run), `Replace` (cancel the old, start new). Also, `startingDeadlineSeconds` sets a window after which a missed execution is simply skipped (not retroactively run).

**Tags:** kubernetes, daemonset, job, cronjob, batch, scheduling

---

## Q-K8S-015 [bloom: apply] [level: regular]
**Question:** How does Horizontal Pod Autoscaler (HPA) work in Kubernetes? What metrics does it use, and what is the lag problem?

**Model answer:** HPA watches a metric and adjusts the `spec.replicas` of a Deployment (or StatefulSet) to keep the metric at a target value.

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70   # target: avg CPU across all pods <= 70%
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"      # custom metric via metrics-server or Prometheus Adapter
```

**How it works:** HPA controller polls metrics-server (or Prometheus Adapter for custom metrics) every `--horizontal-pod-autoscaler-sync-period` (default 15s). Calculates: `desiredReplicas = ceil(currentReplicas * (currentMetric / targetMetric))`. If current CPU is 140% of target → ceil(3 * 140/70) = 6 replicas.

**Lag problem:** there are multiple delays in the system:
1. Metric collection latency: Prometheus scrapes every 30–60s; averaging adds more delay.
2. HPA sync period: 15s default.
3. Pod startup time: new pods must pass readiness probes before receiving traffic. A Spring Boot app may take 20–60s to start.
4. Scale-down stabilization: HPA has a built-in 5-minute stabilization window to avoid flapping — scale-down won't happen until metrics stay below target for 5 minutes.

**Total lag for scale-up:** can be 60–120s from traffic spike to additional pods serving traffic. Design accordingly: set `minReplicas` high enough to absorb a traffic spike during the scale-up lag period.

**Interview trap:** "Does HPA work with StatefulSet?" Yes, but ordered scaling (StatefulSets scale in-order by default) can conflict with HPA's aggressive scale-up. And with StatefulSets for databases, autoscaling is usually inappropriate — you don't want K8s to randomly add a new DB pod. HPA is best for stateless services.

**Tags:** kubernetes, hpa, autoscaling, cpu-metrics, custom-metrics, scale-lag

---

## Q-K8S-016 [bloom: apply] [level: senior]
**Question:** Explain Kubernetes scheduling: nodeSelector, node affinity, pod affinity/anti-affinity, taints, and tolerations. When would you use each in a production microservices setup?

**Model answer:** K8s scheduling determines which node a pod lands on. The scheduler filters nodes, then scores them, then picks the highest-scoring node.

**nodeSelector** — simple key-value match on node labels. Hard requirement (pod won't schedule if no match):
```yaml
spec:
  nodeSelector:
    cloud.google.com/gke-nodepool: high-memory
```

**Node Affinity** — more expressive than nodeSelector; supports `requiredDuringScheduling` (hard) and `preferredDuringScheduling` (soft):
```yaml
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: cloud.google.com/gke-nodepool
          operator: In
          values: [high-memory, xlarge]
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 80
      preference:
        matchExpressions:
        - key: region
          operator: In
          values: [europe-west1]
```

**Pod Anti-affinity** — spread replicas across zones/nodes for HA:
```yaml
affinity:
  podAntiAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
    - labelSelector:
        matchLabels:
          app: order-service
      topologyKey: "kubernetes.io/hostname"   # one pod per node
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchLabels:
            app: order-service
        topologyKey: "topology.kubernetes.io/zone"   # prefer different zones
```

**Taints and Tolerations** — taints mark a node as "not for general use"; only pods with the matching toleration can schedule on it. Used for dedicated node pools (GPU nodes, high-memory nodes, spot nodes):
```yaml
# Node taint (applied to node):
kubectl taint nodes gpu-node-1 nvidia.com/gpu=true:NoSchedule

# Pod toleration — allows scheduling on tainted node:
spec:
  tolerations:
  - key: "nvidia.com/gpu"
    operator: "Equal"
    value: "true"
    effect: "NoSchedule"
```

**Production patterns:**
- Spread stateless services across zones with pod anti-affinity (topologyKey: zone).
- Dedicate spot/preemptible nodes with a taint; batch jobs tolerate them, critical services don't.
- Large-memory search/ML pods use nodeAffinity to land on high-memory nodes.

**Interview trap:** "What is `IgnoredDuringExecution` in the affinity name?" Once a pod is scheduled and running, if the node's labels change (or a pod it was attracted to leaves), the running pod is NOT evicted — K8s ignores the changed state. `RequiredDuringExecution` would evict it (a planned feature, not yet widely used). So affinity rules are scheduling-time only, not runtime enforcement.

**Tags:** kubernetes, scheduling, nodeSelector, affinity, anti-affinity, taints, tolerations, topology-spread

---

## Q-K8S-017 [bloom: apply] [level: senior]
**Question:** Describe the RollingUpdate, Recreate, blue/green, and canary deployment strategies. What are the trade-offs, and how would you implement blue/green on Kubernetes?

**Model answer:**

**RollingUpdate** (K8s native): gradually replace old pods with new. Zero-downtime if readiness probes work correctly. Slow rollback (re-roll the update). Both old and new versions serve traffic simultaneously during the rollout — API must be backward-compatible. Controlled by `maxSurge` / `maxUnavailable`. Best for most stateless services.

**Recreate**: terminate all old pods, then start all new pods. Causes downtime. Use only when you cannot run two versions simultaneously (DB schema migration that's not backward-compatible, breaking API change). Fast rollout; simple.

**Blue/Green**: run two identical environments (blue = current, green = new). Switch traffic atomically by updating the Service's label selector (or Ingress rules) from `version: blue` to `version: green`. Instant rollback = flip back the selector.

Implementation on K8s:
```yaml
# Two Deployments
# Blue (current production):
metadata:
  name: order-service-blue
  labels:
    app: order-service
    version: blue

# Green (new version, running but not receiving traffic):
metadata:
  name: order-service-green
  labels:
    app: order-service
    version: green

# Service selects blue:
spec:
  selector:
    app: order-service
    version: blue   # switch to "green" to cut over

# Cutover command (atomic):
kubectl patch service order-service -p '{"spec":{"selector":{"version":"green"}}}'
```

Downside: double the resource cost during cutover window. Both versions must be deployable simultaneously.

**Canary**: route a small percentage of traffic to the new version, gradually increasing. Detect problems with real traffic before full rollout. Requires weighted traffic splitting — K8s alone can't do fractional traffic; need an Ingress with weight support (nginx, ALB) or a service mesh (Istio VirtualService with weights). ArgoCD Rollouts provides a native canary implementation.

```yaml
# Istio VirtualService example
spec:
  http:
  - route:
    - destination:
        host: order-service
        subset: stable
      weight: 90
    - destination:
        host: order-service
        subset: canary
      weight: 10
```

**Interview trap:** "Can you do blue/green with a StatefulSet and a database?" This is where it gets hard — the DB schema must support both blue and green code simultaneously during cutover. Additive schema changes only (new column nullable, new table) — never destructive. The "expand/contract" migration pattern. Blue/green is primarily for stateless services.

**Tags:** kubernetes, deployment-strategy, rolling-update, blue-green, canary, istio, argocd-rollouts

---

## Q-K8S-018 [bloom: apply] [level: senior]
**Question:** What is Helm, and what problem does it solve? Explain charts, values, releases, and how you'd use Helm to manage a multi-environment Spring Boot service deployment.

**Model answer:** **Helm** is the package manager for Kubernetes. Raw K8s manifests are static YAML — you'd need to duplicate or manually diff them for dev/staging/prod environments. Helm introduces templating and packaging.

**Core concepts:**
- **Chart**: a directory of K8s manifest templates + `values.yaml` defaults + `Chart.yaml` metadata. Versioned artifact, shareable (Helm repository or OCI registry).
- **Values**: key-value configuration injected into templates at render time. `values.yaml` provides defaults; `--set key=value` or `-f override.yaml` overrides at install/upgrade time.
- **Release**: a named instance of a chart installed in a cluster. `helm install order-service-prod ./order-chart --namespace prod -f values-prod.yaml` creates release `order-service-prod`. Multiple releases of the same chart can coexist in the same or different namespaces.
- **Template rendering**: `helm template` renders YAML without installing — great for GitOps pipelines.

Example chart structure:
```
order-chart/
  Chart.yaml
  values.yaml           # defaults: replicas: 1, image.tag: latest
  values-prod.yaml      # prod overrides: replicas: 5, image.tag: 2.1.0
  templates/
    deployment.yaml
    service.yaml
    configmap.yaml
    hpa.yaml
    ingress.yaml
```

Template snippet:
```yaml
# templates/deployment.yaml
spec:
  replicas: {{ .Values.replicas }}
  template:
    spec:
      containers:
      - name: {{ .Chart.Name }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        resources:
          {{- toYaml .Values.resources | nindent 10 }}
```

**Upgrade and rollback:**
```bash
helm upgrade order-service-prod ./order-chart -f values-prod.yaml --set image.tag=2.2.0
helm rollback order-service-prod 1   # rollback to revision 1
helm history order-service-prod       # show release history
```

**Multi-environment pattern:** one chart, environment-specific values files. The values file drives the diff — same chart, different replicas, image tags, resource limits, Ingress hostnames between dev/staging/prod.

**Interview trap:** "What's the difference between `helm upgrade` and `helm upgrade --install`?" Without `--install`, upgrade fails if the release doesn't exist. `--upgrade --install` creates or upgrades — idempotent. Essential for CI/CD pipelines where you don't know if this is a first deploy or an update.

**Tags:** kubernetes, helm, chart, values, release, gitops, multi-environment

---

## Q-K8S-019 [bloom: apply] [level: senior]
**Question:** What are PersistentVolumes, PersistentVolumeClaims, and StorageClass? When must you use them, and what are the gotchas with StatefulSets?

**Model answer:** Kubernetes pods are ephemeral — their filesystem is destroyed when the pod is deleted. For data that must survive pod restarts/reschedules, use the PV system.

**PersistentVolume (PV):** cluster-scoped resource representing an actual storage device (GCE PD, AWS EBS, NFS share, local disk). Has a capacity, access mode, and reclaim policy (`Retain`, `Delete`, `Recycle`).

**PersistentVolumeClaim (PVC):** namespace-scoped request for storage. A pod claims storage by requesting a PVC with a size and access mode. K8s binds the PVC to a matching PV.

**StorageClass:** defines a "class" of storage with a provisioner. Enables **dynamic provisioning** — K8s automatically creates a PV when a PVC is created (no pre-provisioning needed). In GKE, `storageClassName: standard-rwo` provisions a regional GCE persistent disk.

```yaml
# PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data
spec:
  accessModes: [ReadWriteOnce]   # RWO: one node at a time
  storageClassName: standard-rwo
  resources:
    requests:
      storage: 50Gi

# Mount in pod
volumes:
- name: data
  persistentVolumeClaim:
    claimName: postgres-data
volumeMounts:
- mountPath: /var/lib/postgresql/data
  name: data
```

**Access modes:**
- `ReadWriteOnce (RWO)`: one node at a time (most block storage)
- `ReadWriteMany (RWX)`: multiple nodes simultaneously (NFS, GCS FUSE) — required for shared file storage
- `ReadOnlyMany (ROX)`: multiple nodes, read-only

**StatefulSet + volumeClaimTemplates:** each pod gets its own PVC automatically:
```yaml
volumeClaimTemplates:
- metadata:
    name: data
  spec:
    accessModes: [ReadWriteOnce]
    storageClassName: standard-rwo
    resources:
      requests:
        storage: 20Gi
# Creates: data-db-0, data-db-1, data-db-2
```

**Gotchas:** (1) When a StatefulSet pod is deleted and rescheduled, its PVC is reattached — but if the new pod lands on a different node than the PV's zone, it may not be able to mount (EBS/GCE PD are zone-locked). Use `volumeBindingMode: WaitForFirstConsumer` in StorageClass to delay PV provisioning until the pod is scheduled, so the PV is created in the same zone. (2) Deleting a StatefulSet does NOT delete its PVCs — deliberate, to protect data. Manual cleanup required.

**Interview trap:** "What's the difference between emptyDir and a PVC?" `emptyDir` is ephemeral — lives for the pod's lifetime, shared between containers in the pod, but destroyed when the pod exits. Not a replacement for PVC. Use `emptyDir` for temp scratch space (e.g., caching between sidecar and main container).

**Tags:** kubernetes, persistent-volume, pvc, storageclass, statefulset, dynamic-provisioning, rwo-rwx

---

## Q-K8S-020 [bloom: analyze] [level: senior]
**Question:** A Spring Boot service on Kubernetes enters a crash loop (CrashLoopBackOff). Walk through your diagnostic approach step by step.

**Model answer:** CrashLoopBackOff means the container exits shortly after starting, and K8s backs off exponentially before retrying (10s, 20s, 40s... up to 5 minutes between retries).

**Step 1 — Get the exit code:**
```bash
kubectl describe pod <pod-name> -n <namespace>
# Look for: Last State / Exit Code
# Exit code 1: app threw uncaught exception / main() crashed
# Exit code 137: OOMKilled (memory limit exceeded)
# Exit code 143: SIGTERM received (graceful shutdown requested — shouldn't crash; check why it's not coming up first)
# Exit code 255: miscellaneous JVM crash
```

**Step 2 — Read the logs of the previous container instance:**
```bash
kubectl logs <pod-name> -n <namespace> --previous
# --previous: logs from the last terminated container (current may not have logs yet)
```
Look for: Spring context startup failures (missing bean, DB connection refused, property not found), JVM OOM errors (`java.lang.OutOfMemoryError`), port binding failures.

**Step 3 — If exit code 137 (OOMKilled):**
```bash
kubectl describe pod | grep -A5 "OOMKilled"
# Check: memory limit vs actual memory usage
kubectl top pod <pod-name> -n <namespace>
```
Fix: increase memory limit, or fix the JVM heap config (`-XX:MaxRAMPercentage=75`).

**Step 4 — If Spring context fails:**
Common causes: (a) missing ConfigMap/Secret key → `IllegalArgumentException: Could not resolve placeholder 'DB_PASSWORD'`; (b) DB not reachable at startup → Spring tries to validate DataSource and fails; (c) wrong image — contains a bad JAR.

For DB connection issue: add `spring.datasource.hikari.initialization-fail-timeout=-1` to not fail if DB is temporarily unavailable during startup; or use an `initContainer` to wait for DB readiness before the main container starts.

**Step 5 — Check events:**
```bash
kubectl get events -n <namespace> --sort-by='.lastTimestamp'
# May show: ImagePullBackOff (wrong image tag), FailedMount (missing ConfigMap/PVC), OOMKilling
```

**Step 6 — Inspect the image:**
```bash
kubectl run debug-pod --image=<your-image>:tag --restart=Never --rm -it -- /bin/sh
# For distroless: use ephemeral debug containers instead
kubectl debug <pod-name> -it --image=busybox --target=<container-name>
```

**Interview trap:** "What's the difference between CrashLoopBackOff and OOMKilled in terms of K8s behavior?" Both result in pod restart, but OOMKilled is the reason (exit code 137), while CrashLoopBackOff is the state K8s enters when a pod keeps crashing regardless of reason. OOMKilled is one of many causes for CrashLoopBackOff.

**Tags:** kubernetes, crashloopbackoff, debugging, oomkilled, logs, events, spring-boot

---

## Q-K8S-021 [bloom: apply] [level: senior]
**Question:** What is Kubernetes self-healing, and what are its limits? What does K8s NOT fix automatically?

**Model answer:** Kubernetes self-healing is the automatic reconciliation of desired state vs actual state, implemented by controllers running in a continuous control loop.

**What K8s heals automatically:**
- **Pod crash / OOMKill:** restarts the pod (respecting `restartPolicy` — default `Always`).
- **Node failure:** ReplicaSet controller detects pods on failed node, creates replacements on healthy nodes (after `--pod-eviction-timeout`, default ~5 min).
- **Failed liveness probe:** kills and restarts the specific container in the pod.
- **Pod removed from Service endpoints:** when readiness probe fails, traffic stops being routed to that pod.
- **Deployment drift:** if someone manually deletes a pod or scales down a ReplicaSet, the Deployment controller reconciles back to desired replicas.

**What K8s does NOT fix:**
- **Application logic bugs:** a pod that starts successfully but returns 500s — K8s won't restart it (unless liveness probe detects it).
- **Slow memory leaks:** the pod may OOMKill eventually, but K8s won't proactively detect degraded performance.
- **Data corruption:** if a pod writes corrupt data to a PVC before crashing, K8s restores the pod but the data stays corrupt.
- **Database connectivity issues:** if the DB is down, K8s can remove the pod from Service endpoints (readiness), but it cannot fix the DB.
- **Configuration errors:** a wrong env var injected from ConfigMap — K8s will keep restarting the pod with the same wrong config. The crashloop is K8s trying to heal but the fix requires human intervention.
- **Node pool exhaustion:** if no node has enough CPU/memory to schedule a pod, it stays `Pending` indefinitely (K8s can request Cluster Autoscaler to add nodes, but that's a separate component).
- **Split-brain in StatefulSets:** K8s won't resolve DB cluster split-brain; that requires application-level or operator-level intervention.

**Operational reality:** self-healing reduces the operational burden of transient failures (network blip, occasional OOM, node reboot) but cannot substitute for proper health indicators, alerting, and runbooks for persistent failures.

**Interview trap:** "If a pod is OOMKilled repeatedly, does K8s eventually stop restarting it?" No — K8s will keep restarting it indefinitely (with exponential backoff up to 5 min between retries), but never gives up on `restartPolicy: Always`. The pod stays in CrashLoopBackOff forever until you fix the root cause.

**Tags:** kubernetes, self-healing, reconciliation, control-loop, limits, crashloopbackoff

---

## Q-K8S-022 [bloom: analyze] [level: senior]
**Question:** A rolling deployment is stuck — pods are not progressing and the old pods are still running. How do you investigate and what are the common causes?

**Model answer:** A stuck rolling deployment means the new ReplicaSet's pods are not becoming Ready, so K8s halts the rollout to avoid disrupting the running old pods.

**Diagnostic commands:**
```bash
kubectl rollout status deployment/my-service -n prod
# Output: "Waiting for deployment to finish: 1 out of 3 new replicas have been updated..."

kubectl describe deployment my-service -n prod
# Check: "Available Replicas", "Unavailable Replicas", "Conditions"
# Look for: "ReplicaSetUpdated", "Available", "Progressing" conditions

kubectl get pods -n prod -l app=my-service
# Look for pods in: Pending, ImagePullBackOff, CrashLoopBackOff, Running but not Ready

kubectl describe pod <new-pod-name> -n prod
kubectl logs <new-pod-name> -n prod --previous
```

**Common causes and fixes:**

1. **Readiness probe never passes:** New image has a bug (Spring context fails, wrong config, bad health endpoint path). Fix: fix the image or rollback (`kubectl rollout undo deployment/my-service`).

2. **ImagePullBackOff:** Wrong image tag, image doesn't exist in registry, or missing imagePullSecret for a private registry. Fix: check `kubectl describe pod` for the exact error.

3. **Insufficient resources:** New pods can't be scheduled because nodes lack CPU/memory. Check `kubectl get events -n prod | grep FailedScheduling`. Fix: add nodes, reduce requests, or check for resource quota limits.

4. **progressDeadlineSeconds exceeded:** K8s marks the deployment as failed and stops progressing after this timeout (default 600s). The deployment controller creates a `DeploymentCondition` with reason `ProgressDeadlineExceeded`.

5. **PodDisruptionBudget:** A PDB may be blocking pod eviction. Check `kubectl get pdb -n prod`.

**Rollback if stuck:**
```bash
kubectl rollout undo deployment/my-service -n prod
# Optional: specify revision
kubectl rollout undo deployment/my-service -n prod --to-revision=2
```

**Interview trap:** "Does rolling back via `kubectl rollout undo` affect the Deployment's `spec.template`?" Yes — it reverts the template to the previous revision's state, which triggers another rolling update (back to old pods). The Deployment's history (`kubectl rollout history`) records all revisions including the undo.

**Tags:** kubernetes, rolling-update, stuck-deployment, rollback, debugging, progressdeadline, imagepullbackoff

---

## Q-K8S-023 [bloom: analyze] [level: senior]
**Question:** Explain image scanning and security best practices for Docker images in a production CI/CD pipeline.

**Model answer:** Shipping an unscanned image to production is shipping unknown CVEs to production. Image scanning analyzes layers for known vulnerabilities in OS packages and language libraries.

**Scanning tools:**
- **Trivy** (Aqua Security, OSS) — scans image layers for CVEs in OS packages + language packages (Go, Java, Python, npm). Fast, easy CI integration.
- **Grype** (Anchore OSS) — similar to Trivy.
- **Snyk Container** — commercial, deep integration with registries.
- **Google Container Analysis** (Artifact Registry) — automatic scanning on push; blocks deployment via Binary Authorization.

**CI/CD integration pattern:**
```bash
# In Jenkins/GitHub Actions — after docker build, before docker push:
trivy image --exit-code 1 --severity HIGH,CRITICAL my-service:$TAG
# Exit code 1 if HIGH or CRITICAL CVEs found — fail the build
```

**Image hardening best practices:**
1. **Use minimal base images:** distroless or Alpine. Fewer packages = smaller attack surface. `gcr.io/distroless/java21` has ~10 packages vs `ubuntu:22.04` with 400+.
2. **Run as non-root:** never run as root inside the container. Add a `USER` instruction:
   ```dockerfile
   RUN addgroup --system appgroup && adduser --system appuser --ingroup appgroup
   USER appuser
   ```
3. **Read-only root filesystem:** `securityContext.readOnlyRootFilesystem: true` in the pod spec. Forces explicit volume mounts for any writes.
4. **No secrets in images:** no env vars with real credentials, no baked-in `.env` files. This is a high-severity finding in any security audit.
5. **Pin base image by digest, not tag:** `FROM eclipse-temurin:21-jre-alpine@sha256:abc123...` — prevents tag mutation (someone overwriting `latest`).
6. **Multi-stage builds:** build tools + source never in production image.
7. **Regular base image updates:** even distroless images get OS-level CVE patches. Rebuild images regularly (weekly automated rebuild pipeline).

**Interview trap:** "What's Binary Authorization in GKE?" A GCP security control that enforces that only images with an attestation (cryptographic proof that they passed CI steps including scanning) can be deployed to GKE. If the image wasn't scanned and signed by the CI pipeline, `kubectl apply` is rejected at the admission controller level.

**Tags:** docker, image-scanning, trivy, distroless, security, non-root, binary-authorization, ci-cd

---

## Q-K8S-024 [bloom: analyze] [level: master]
**Question:** Explain the Kubernetes control plane architecture: etcd, API server, scheduler, controller manager, kubelet. What happens end-to-end when you run `kubectl apply -f deployment.yaml`?

**Model answer:** The K8s control plane is a set of components that maintain the desired state of the cluster.

**Components:**
- **etcd:** distributed key-value store. Single source of truth for all cluster state. All control plane components read/write via the API server (not directly to etcd). Uses Raft consensus for distributed consistency. If etcd goes down, the cluster state cannot be changed (running workloads continue, but no scheduling or updates).
- **kube-apiserver:** the only component that talks to etcd. Authenticates, authorizes, validates, and persists all requests. Implements admission controllers (NamespaceLifecycle, ResourceQuota, PodSecurityAdmission, etc.) that can mutate or reject objects. Exposes the K8s REST API.
- **kube-scheduler:** watches for unscheduled pods (`spec.nodeName` is empty). Runs filter + score phases: filter nodes that can't run the pod (insufficient resources, taints, affinity), score remaining nodes, pick highest-scored node. Writes `spec.nodeName` to the pod object via the API server.
- **kube-controller-manager:** runs all built-in controllers (Deployment controller, ReplicaSet controller, Node controller, Endpoints controller, etc.) in a single process. Each controller watches relevant objects via informers and reconciles actual vs desired state.
- **kubelet:** agent on every worker node. Watches for pods assigned to its node (via API server watch). Creates/starts containers via the container runtime (containerd, CRI-O) using the CRI (Container Runtime Interface). Reports pod status back to API server. Runs probes.

**End-to-end flow of `kubectl apply -f deployment.yaml`:**

1. `kubectl` reads the YAML, serializes it, sends `PATCH` (or `PUT`) to `kube-apiserver /apis/apps/v1/namespaces/default/deployments/my-service`.
2. **API server:** authenticates (JWT/cert), authorizes (RBAC), runs admission controllers (mutate defaults like imagePullPolicy, validate schema, check ResourceQuota), serializes the Deployment object to etcd.
3. **Deployment controller** (in controller-manager): watching for Deployment changes via an informer (watch stream from API server). Detects the new/changed Deployment. Computes the desired ReplicaSet state. Creates/updates the ReplicaSet object in etcd via API server.
4. **ReplicaSet controller** detects ReplicaSet with `replicas: 3` but 0 actual pods. Creates 3 Pod objects in etcd (status: Pending, no `spec.nodeName`).
5. **kube-scheduler** detects 3 unscheduled pods. Runs filter + score per pod. Assigns `spec.nodeName` for each pod.
6. **kubelet** on the assigned nodes detects pods with its `spec.nodeName`. Pulls the image (if not cached), creates containers via containerd. Reports pod phase as Running.
7. **Endpoints controller** detects Ready pods with matching label selector. Creates/updates the Endpoints object for the Service. kube-proxy reads the updated Endpoints and programs iptables rules.

The total time from `kubectl apply` to traffic routing: typically 15–60 seconds (dominated by image pull time and Spring Boot startup time).

**Interview trap:** "What if etcd loses quorum (2 of 3 nodes down in a 3-node etcd cluster)?" All control plane operations halt — no new pods can be scheduled, no config changes applied. Running pods continue running (kubelet and kube-proxy operate without the control plane once programmed), but the cluster is effectively frozen. Recovery requires restoring etcd from backup. This is why HA etcd (3 or 5 nodes, across AZs) is mandatory for production.

**Tags:** kubernetes, control-plane, etcd, api-server, scheduler, controller-manager, kubelet, watch, reconciliation

---

## Q-K8S-025 [bloom: analyze] [level: master]
**Question:** How does Kubernetes networking work? Explain the CNI, how pods communicate across nodes, and how a Service's ClusterIP is implemented.

**Model answer:** K8s networking has three fundamental rules: (1) every pod gets a unique IP; (2) pods can communicate with any other pod without NAT; (3) agents on a node (kubelet) can communicate with all pods on that node.

**CNI (Container Network Interface):** a plugin spec. When kubelet creates a pod, it calls the CNI plugin to set up the pod's network namespace: assign an IP from the pod CIDR, create a veth pair (one end in the pod ns, one on the node), and connect it to the node's network. CNI plugins: Calico, Flannel, Cilium, AWS VPC CNI. Each implements cross-node routing differently:

- **Flannel:** encapsulates pod-to-pod traffic in VXLAN (or UDP). Overlay network. All cross-node pod traffic is wrapped in UDP packets. Simple, but encapsulation adds overhead.
- **Calico (BGP mode):** programs route tables on each node — packets routed natively at L3, no encapsulation. High performance. Requires underlay network to support BGP peering (works in many cloud VPCs).
- **Cilium:** uses eBPF instead of iptables for routing/policy enforcement. Bypasses kernel IP stack for high performance, lower CPU overhead at scale.

**Cross-node pod communication:** Pod A (node 1, IP 10.244.1.5) → Pod B (node 2, IP 10.244.2.10). With Calico/BGP: node 1 has a route `10.244.2.0/24 via node-2-IP` in its routing table. Packet leaves pod A's veth, hits node 1's routing table, goes to node 2's NIC, enters pod B's veth.

**Service ClusterIP implementation (iptables mode):**
When a Service is created with ClusterIP `10.96.0.1` and endpoints `[10.244.1.5:8080, 10.244.2.10:8080]`, kube-proxy writes iptables rules on every node:

```
PREROUTING chain: DNAT tcp --dst 10.96.0.1 --dport 80 → randomly select one endpoint
→ rule 1: 50% probability → DNAT to 10.244.1.5:8080
→ rule 2: 50% probability → DNAT to 10.244.2.10:8080
```

This is statistically load-balanced L4 NAT. No proxy process in the hot path. IPVS mode uses a Linux kernel hash table instead of linear iptables chain scan — scales better for clusters with 1000s of Services.

**Kube-dns / CoreDNS:** DNS resolution inside pods. `my-service.default.svc.cluster.local` → queries CoreDNS → returns the Service's ClusterIP. Pod DNS is configured with `ndots: 5` (searches `default.svc.cluster.local`, `svc.cluster.local`, `cluster.local` before external DNS).

**Interview trap:** "At Allegro with 1000+ services, why did they build their own control plane (Envoy Control) rather than rely on kube-proxy?" kube-proxy does L4 load balancing (connection-level). Envoy is an L7 proxy — it understands HTTP/gRPC, can do retry logic, circuit breaking, header-based routing, mTLS, request tracing. These are application-layer concerns that iptables NAT simply cannot implement. kube-proxy and Envoy are complementary, not competing — Envoy sits in each pod, kube-proxy handles the node-level plumbing.

**Tags:** kubernetes, networking, cni, calico, flannel, cilium, kube-proxy, iptables, ipvs, service-discovery, coredns

---

## Q-K8S-026 [bloom: analyze] [level: master]
**Question:** A production K8s cluster has nodes hitting memory pressure. Some pods are being evicted. Explain the eviction mechanism, which pods get evicted first, and how you'd design workloads to avoid unwanted eviction.

**Model answer:** When a node's available memory drops below `evictionHard` thresholds (e.g., `memory.available < 100Mi`), the kubelet starts evicting pods to reclaim memory. This is distinct from OOMKill (which is kernel-level for a specific container over its cgroup limit).

**Eviction order (kubelet's algorithm):**

1. **BestEffort pods first** (no requests/limits set) — consume resources with no guaranteed allocation.
2. **Burstable pods** that have exceeded their requests — the ones using more than they requested.
3. **Burstable pods** that are below their requests — less aggressive eviction.
4. **Guaranteed pods** — evicted last, and only under extreme pressure.

Within each QoS class, pods are sorted by "usage above request" — the pod using the most memory above its request gets evicted first.

**Threshold types:**
- `evictionHard`: kubelet evicts pods immediately when crossed. Defaults: `memory.available < 100Mi`, `nodefs.available < 10%`.
- `evictionSoft`: kubelet waits `evictionSoftGracePeriod` (e.g., 90s) before evicting. Less disruptive for transient pressure.

**Pod Disruption Budget (PDB):** limits how many pods of a Deployment can be unavailable simultaneously (from voluntary disruptions including eviction). Doesn't protect against hardware failure.
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
spec:
  selector:
    matchLabels:
      app: order-service
  minAvailable: 2   # always keep at least 2 pods running
  # or: maxUnavailable: 1
```

**Design patterns to avoid unwanted eviction:**
1. **Use Guaranteed QoS** for critical services: `requests == limits` for all containers. Evicted last.
2. **Right-size requests:** set memory requests accurately (use VPA recommendations). Pods with accurate requests are evicted after those that overshoot.
3. **PDB** on all production Deployments: prevents simultaneous eviction of too many replicas.
4. **Vertical Pod Autoscaler (VPA):** recommends and optionally sets resource requests based on historical usage — prevents chronic under-requesting.
5. **Cluster Autoscaler:** adds nodes when pods are pending. Reduces pressure before eviction threshold is hit.
6. **Separate critical pods to dedicated node pools** (taint/toleration): isolate critical services from batch/BestEffort workloads that might cause memory spikes.

**Interview trap:** "What's the difference between eviction and preemption?" **Eviction:** kubelet removes a running pod to reclaim resources on a node under pressure. **Preemption:** scheduler removes lower-priority running pods to make room for a higher-priority pod that can't be scheduled. Controlled by `PriorityClass`. Both result in pod termination; different triggers (node pressure vs scheduling pressure).

**Tags:** kubernetes, eviction, memory-pressure, qos, pod-disruption-budget, vpa, cluster-autoscaler, priorityclass

---

## Q-K8S-027 [bloom: analyze] [level: master]
**Question:** Design the complete Kubernetes deployment spec for a Spring Boot microservice running at Allegro-scale (production-grade). What fields matter and why?

**Model answer:** A production-grade Deployment must address: availability, resource isolation, security, graceful shutdown, observability, and scheduling.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
  labels:
    app: order-service
    version: "2.1.0"
spec:
  replicas: 5
  revisionHistoryLimit: 5
  progressDeadlineSeconds: 300    # fail rollout after 5 min of no progress
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0            # zero-downtime rollout

  selector:
    matchLabels:
      app: order-service

  template:
    metadata:
      labels:
        app: order-service
        version: "2.1.0"
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8080"

    spec:
      serviceAccountName: order-service-sa    # least-privilege SA
      terminationGracePeriodSeconds: 60        # allow Spring Boot graceful shutdown (default 30s spring.lifecycle.timeout-per-shutdown-phase)

      # Anti-affinity: spread replicas across nodes and zones
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchLabels:
                app: order-service
            topologyKey: "kubernetes.io/hostname"    # one per node
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app: order-service
              topologyKey: "topology.kubernetes.io/zone"   # prefer diff zones

      containers:
      - name: order-service
        image: europe-docker.pkg.dev/myproject/backend/order-service:2.1.0
        imagePullPolicy: IfNotPresent

        ports:
        - containerPort: 8080
          name: http

        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JAVA_OPTS
          value: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-service-secrets
              key: db-password
        envFrom:
        - configMapRef:
            name: order-service-config

        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "1000m"
            memory: "1Gi"           # Burstable QoS; use requests==limits for Guaranteed

        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
          failureThreshold: 3
          timeoutSeconds: 5

        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
          failureThreshold: 3
          timeoutSeconds: 3

        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          failureThreshold: 30       # 30 * 10s = 5 min max startup window
          periodSeconds: 10

        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 5"]   # give LB time to drain connections before SIGTERM

        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          readOnlyRootFilesystem: true
          allowPrivilegeEscalation: false
          capabilities:
            drop: ["ALL"]

        volumeMounts:
        - mountPath: /tmp
          name: tmp-dir            # writable tmp for read-only rootfs

      volumes:
      - name: tmp-dir
        emptyDir: {}
```

Key decisions explained:
- `terminationGracePeriodSeconds: 60` — Spring Boot needs time for in-flight requests to drain. Must be > `spring.lifecycle.timeout-per-shutdown-phase` (default 30s).
- `preStop: sleep 5` — kube-proxy iptables rules update has latency. Without this sleep, new connections arrive after SIGTERM but before iptables is updated, causing connection refused errors.
- `-XX:MaxRAMPercentage=75.0` — JVM heap = 75% of cgroup memory limit. Without this, JVM defaults to host memory and may allocate a heap larger than the container limit → OOMKilled.
- `readOnlyRootFilesystem: true` — forces explicit tmpfs mounts; security hardening.
- `podAntiAffinity` on hostname — no two replicas on the same node; a node failure never takes more than 1/5 of capacity.

**Interview trap:** "What's missing for true Guaranteed QoS?" Set `requests.cpu == limits.cpu` and `requests.memory == limits.memory`. In the above spec, CPU requests != limits, so QoS is Burstable. For critical real-time services, Guaranteed QoS prevents eviction under memory pressure.

**Tags:** kubernetes, deployment, production-grade, security-context, graceful-shutdown, anti-affinity, resource-management, spring-boot

---

## Q-K8S-028 [bloom: analyze] [level: master]
**Question:** What are the trade-offs between running a service on Kubernetes vs serverless containers (e.g., Cloud Run)? When would each be the right choice for a Spring Boot microservice?

**Model answer:** This is a real architectural decision at every team deploying Spring Boot services today.

| Dimension | Kubernetes (GKE) | Serverless containers (Cloud Run) |
|-----------|-----------------|----------------------------------|
| **Scaling** | HPA (15–120s lag, min replicas > 0) | Automatic, scales to 0, near-instant for HTTP |
| **Cold start** | Pod startup (~30–90s for Spring Boot) affects only first pod | Every scale-from-zero has a cold start — Spring Boot is slow |
| **Cost** | Pay for reserved node capacity regardless of traffic | Pay per request/CPU-ms. Idle = free |
| **Infrastructure mgmt** | Manage node pools, upgrades, CNI, RBAC | Zero — fully managed |
| **Long-running connections** | WebSockets, gRPC streaming, persistent consumers | Only HTTP request lifecycle (requests have 60-min timeout) |
| **Sidecars** | Full sidecar support (Envoy, Vault agent, etc.) | No sidecar support |
| **Persistent workloads** | StatefulSets, DaemonSets, background workers | Request-driven only |
| **Custom networking** | Full CNI, Network Policies, service mesh | VPC connector, no pod-level network control |
| **Multi-tenancy** | Namespaces, RBAC, Network Policies | Service-level isolation |
| **Customization** | Custom schedulers, operators, admission webhooks | None |

**When Kubernetes:**
- Services that need sidecar injection (service mesh, Vault agent sidecar)
- Persistent background workers (Kafka consumers — long-running poll loops)
- StatefulSets (databases, Kafka brokers)
- Workloads with bursty sustained traffic (min replicas > 0 always)
- Multi-team platform where you want uniform operational model
- Services needing custom scheduling (GPU nodes, high-memory nodes)
- Allegro's model: 1000+ services, need centralized mesh (Envoy Control), polyglot runtime — K8s is the only viable choice

**When Cloud Run:**
- Simple stateless HTTP request/response services
- Event-driven workloads (Pub/Sub push subscriptions)
- Traffic with idle periods (scale to zero saves money)
- Small team without platform engineering capacity
- Prototypes and low-traffic internal services

**The Spring Boot cold start problem:** Spring Boot typically takes 5–30s to start (context scan, JPA schema validation, connection pool warmup). Cloud Run's scale-from-zero means each cold start adds 5–30s of latency for the first request. Mitigation: Spring AOT + GraalVM native image reduces startup to <1s — makes Cloud Run viable. Or use `min-instances: 1` to keep one warm instance.

**Interview trap:** "Can Cloud Run do Kafka consumption?" Not natively — Kafka requires a persistent consumer group with long-poll loops, which doesn't fit the request-response model. You'd need a sidecar Kafka consumer on K8s that POSTs to a Cloud Run endpoint (pull→push bridge), or use the Pub/Sub push pattern with a Kafka→Pub/Sub connector.

**Tags:** kubernetes, cloud-run, serverless, trade-offs, spring-boot, cold-start, graalvm-native, scaling
