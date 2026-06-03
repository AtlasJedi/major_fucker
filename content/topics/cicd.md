# CI/CD — question bank

> CI/CD is the operational backbone of every modern backend shop. A senior engineer is expected to own pipelines end-to-end — not just "we have Jenkins" but knowing why declarative beats scripted, how artifact repositories fit the promotion model, which deployment strategy to pick for zero-downtime in a given context, how DORA metrics expose process pain, and when GitFlow creates more problems than it solves. This bank covers the full spectrum: Jenkins internals, GitHub Actions, Maven/Gradle lifecycle, artifact repos, deployment strategies, branching models, semantic versioning, test gating, static analysis, IaC with Terraform, rollback strategies, and DORA metrics.

## Scope

- CI vs continuous delivery vs continuous deployment — definitions and distinctions
- Pipeline stages: build → test → scan → package → deploy
- Jenkins: declarative vs scripted Jenkinsfile, agents, stages, parallel, shared libraries, credentials
- GitHub Actions: workflows, jobs, steps, runners, matrix builds, secrets, caching, reusable workflows
- Maven vs Gradle build lifecycle
- Artifact repositories: Nexus, Artifactory, GCP Artifact Registry
- Deployment strategies: blue-green, canary, rolling, recreate, feature flags
- Branching strategies: trunk-based development vs GitFlow vs GitHub Flow
- Semantic versioning and Conventional Commits
- Test gating and fail-fast principle
- Static analysis and coverage gates: SonarQube, JaCoCo
- IaC with Terraform: state management, plan/apply workflow, environment promotion
- Rollback strategy
- Environment promotion (dev → staging → prod)
- DORA metrics: deployment frequency, lead time, MTTR, change failure rate

---

## Q-CICD-001 [bloom: recall] [level: junior]
**Question:** What is the difference between Continuous Integration, Continuous Delivery, and Continuous Deployment? Give a one-sentence definition of each.

**Model answer:** **Continuous Integration (CI):** developers merge code to a shared branch frequently (at least daily), each merge triggers an automated build and test run — the goal is to catch integration failures as early as possible.

**Continuous Delivery (CD — delivery):** every passing build is automatically packaged and promoted through environments up to a production-ready state, but the final push to production is a deliberate human-triggered action.

**Continuous Deployment (CD — deployment):** every passing build that clears all gates is deployed to production automatically, with no human approval required. Zero-touch from commit to live.

Key axis: CI is about fast integration feedback. Delivery = automation up to the door of prod. Deployment = automation through that door. Many teams practice CI + delivery but not full deployment (risk appetite, compliance requirements).

**Interview trap:** "So continuous delivery means you deploy automatically?" — No. That's continuous *deployment*. Delivery means the artifact is *ready to deploy* at any time; the act of deploying is still manual. The interviewer is testing whether you know the nuance.

**Tags:** ci-cd, continuous-integration, continuous-delivery, continuous-deployment, definitions

---

## Q-CICD-002 [bloom: recall] [level: junior]
**Question:** What are the typical stages of a CI/CD pipeline for a Java/Spring Boot service? List them in order.

**Model answer:** A typical pipeline for a Spring Boot service:

1. **Checkout** — clone the repo at the triggering commit/branch.
2. **Build** — compile source code (`mvn compile` or `gradle compileJava`). Fail-fast: compilation errors stop here.
3. **Unit tests** — fast, isolated tests; no external dependencies. Gate: if any test fails, pipeline aborts.
4. **Static analysis / lint** — SonarQube scan, SpotBugs, checkstyle. Coverage gate enforced here.
5. **Integration tests** — tests that require DB, message broker, etc. (Testcontainers). Slower, run after unit.
6. **Security scan** — dependency vulnerability check (OWASP Dependency-Check, Snyk, Trivy).
7. **Package** — produce the deployable artifact: fat JAR (`mvn package`), Docker image (build + tag).
8. **Artifact publish** — push JAR to Nexus/Artifactory or Docker image to registry.
9. **Deploy to staging** — apply new version to staging environment.
10. **Smoke / acceptance tests** — basic end-to-end validation against deployed staging.
11. **Deploy to production** — manual gate (delivery) or automated (deployment).

The exact split between stages varies, but the ordering principle is: **cheapest/fastest gates first** (fail-fast), most expensive last.

**Interview trap:** "Why run unit tests before integration tests?" — Integration tests are slower and require infrastructure. If unit tests fail, you learn the same information faster. Fail-fast principle: abort early, save compute and time.

**Tags:** pipeline-stages, ci-cd, fail-fast, build, test, deploy

---

## Q-CICD-003 [bloom: recall] [level: junior]
**Question:** What is semantic versioning? Explain the MAJOR.MINOR.PATCH scheme and when each component increments.

**Model answer:** **Semantic Versioning (SemVer)** is a version numbering convention: `MAJOR.MINOR.PATCH` (e.g. `2.4.1`).

| Component | Increments when | Example trigger |
|-----------|----------------|-----------------|
| `PATCH` | Backwards-compatible bug fix | `fix: null pointer in UserService` |
| `MINOR` | New backwards-compatible feature | `feat: add JWT refresh endpoint` |
| `MAJOR` | Breaking API change | `feat!: remove /api/v1 endpoints` |

Rules:
- `1.0.0` = first stable public API. `0.x.y` = pre-stable, anything can break.
- When `MINOR` bumps, `PATCH` resets to 0. When `MAJOR` bumps, both reset to 0.
- Pre-release: `1.0.0-SNAPSHOT`, `1.0.0-RC.1` — lower precedence than the release.
- Build metadata: `1.0.0+build.456` — ignored for precedence comparison.

**Conventional Commits** maps directly to SemVer: `fix:` → PATCH, `feat:` → MINOR, `feat!:` or `BREAKING CHANGE:` footer → MAJOR. Tools like `semantic-release` or `release-please` parse commit history and automate tagging + changelog generation in CI.

**Interview trap:** "What happens if you publish `1.2.0` and it has a breaking change?" — You violated SemVer. Consumers who pin `^1.x.x` (compatible range) will get broken automatically. This is exactly the implicit contract SemVer creates and why discipline matters.

**Tags:** semver, semantic-versioning, conventional-commits, versioning

---

## Q-CICD-004 [bloom: recall] [level: junior]
**Question:** What is a Jenkinsfile and why should it live in the repository alongside the application code?

**Model answer:** A **Jenkinsfile** is a text file that defines a Jenkins pipeline as code using a Groovy-based DSL. It lives in the root of the application repository (or a sub-path) and is checked into version control.

Why it belongs in the repo:
1. **Versioning** — the pipeline definition changes with the code. A PR that changes application behavior can simultaneously change the build/test/deploy steps. History is unified.
2. **Code review** — pipeline changes go through the same PR + approval process as application code. No rogue pipeline changes via the Jenkins UI.
3. **Reproducibility** — checking out any historical commit also gives you the pipeline that was used to build it. Debugging old releases becomes possible.
4. **Multibranch Pipelines** — Jenkins auto-discovers branches and creates per-branch pipeline runs, each using the Jenkinsfile from that branch. PR builds work automatically.
5. **Disaster recovery** — if the Jenkins master is rebuilt, pipelines are re-imported from the repo, not lost with UI config.

The alternative (UI-defined Freestyle jobs) is opaque, unversioned, and a maintenance nightmare at scale.

**Interview trap:** "What's the security implication of storing the Jenkinsfile in the repo?" — Anyone who can merge a PR can modify the pipeline, potentially exfiltrating secrets or bypassing security stages. Mitigate with: branch protection rules, required reviewers for Jenkinsfile changes, and keeping secrets in the credentials store (never in the Jenkinsfile itself).

**Tags:** jenkins, jenkinsfile, pipeline-as-code, version-control

---

## Q-CICD-005 [bloom: recall] [level: junior]
**Question:** What is an artifact repository and why do pipelines use one? Name two examples.

**Model answer:** An **artifact repository** is a versioned binary store for build outputs — JARs, Docker images, npm packages, Helm charts. Pipelines publish artifacts to the repository after a successful build; deployment steps pull from it.

Why pipelines use one (not just the filesystem or S3):
- **Immutability** — a published version is frozen. No rebuilding the same artifact in a later stage means the tested artifact is the deployed artifact (traceability).
- **Versioning and search** — find `service:1.4.2` easily; also supports snapshots (mutable) vs releases (immutable).
- **Dependency proxying** — proxy Maven Central/Docker Hub, cache upstream dependencies internally. Reduces external traffic, protects against upstream outages.
- **Access control** — repo-level ACLs. Not every service needs push access to every repo.
- **Retention policies** — auto-clean old snapshots, keep N releases.

Examples:
- **Nexus Repository Manager** (Sonatype) — on-prem classic; supports Maven, npm, Docker, PyPI.
- **JFrog Artifactory** — enterprise-grade, strong UI, supports everything + Xray for security scanning.
- **GCP Artifact Registry** — cloud-native, regional, integrated with Cloud Build/Cloud Run/GKE.
- **GitHub Packages** — simple, free for public repos, tied to GitHub ecosystem.

**Interview trap:** "Can't you just re-run `mvn package` in the deployment stage?" — You'd be building from the same source, but the resulting artifact is not guaranteed identical (timestamps, non-deterministic builds, different tool versions on different agents). The tested JAR and the deployed JAR might differ. Artifact repos solve this by publish-once, deploy-many.

**Tags:** artifact-repository, nexus, artifactory, artifact-registry, ci-cd

---

## Q-CICD-006 [bloom: understand] [level: junior]
**Question:** What is the difference between a SNAPSHOT and a RELEASE version in Maven, and why does it matter in a CI/CD pipeline?

**Model answer:** In Maven's versioning convention:

**SNAPSHOT** (e.g. `1.3.0-SNAPSHOT`):
- Mutable — Maven re-downloads the artifact on every build if `updatePolicy` allows, or if the remote is newer.
- Stored in the snapshot repository in Nexus/Artifactory.
- Intended for active development: the version exists but is still changing.

**RELEASE** (e.g. `1.3.0`):
- Immutable — once published, the coordinates (`groupId:artifactId:version`) are frozen. You cannot overwrite a release (well-configured repo enforces this).
- Stored in the releases repository.
- Intended for stable, shipped artifacts.

CI/CD implications:
1. **Traceability** — pipelines should publish releases (not snapshots) for anything that goes to production. A SNAPSHOT in production is an antipattern: you can't guarantee what code it contains.
2. **Caching** — build caches can safely cache release JARs. SNAPSHOT caching is unreliable.
3. **Deployment gating** — a promotion gate can enforce "no SNAPSHOT in the staging → prod promotion step."
4. **Dependency hygiene** — having SNAPSHOT dependencies in a released artifact is a red flag; Maven Enforcer plugin can enforce no-snapshots in releases.

**Interview trap:** "Our staging pipeline uses SNAPSHOT versions. Is that a problem?" — For staging itself, acceptable. But production must use released, immutable versions. If staging runs SNAPSHOTs, the staging → prod promotion must bump to a release version and republish first.

**Tags:** maven, snapshot, release, versioning, artifact-repository

---

## Q-CICD-007 [bloom: understand] [level: regular]
**Question:** Explain the difference between Jenkins Declarative and Scripted Pipeline syntax. When would you choose scripted over declarative?

**Model answer:** Both are DSLs for `Jenkinsfile`, both run on Groovy.

| Aspect | Declarative | Scripted |
|--------|-------------|---------|
| Syntax wrapper | `pipeline { ... }` | `node { ... }` (raw Groovy) |
| Pre-run validation | Yes — schema-checked before execution | No — fails at runtime |
| Structure | Rigid (`stages`, `steps`, `post` blocks) | Arbitrary Groovy code |
| Shared library use | Yes | Yes |
| `script { }` escape hatch | Yes — limited inline Groovy | Not needed, it's all Groovy |
| IDE/linter support | Better | Worse |
| Readability | Higher for standard cases | Lower, more code |
| Preferred for new pipelines | Yes | No |

**Declarative** is the default choice: it enforces a consistent structure, validates before running (catching syntax errors before wasting agent time), and is easier to read in code review.

**Choose scripted when:**
- You need complex conditional logic that declarative's `when` directive can't express cleanly.
- You're generating stages dynamically at runtime (e.g., generating one stage per microservice from a list).
- You're wrapping legacy scripts that predate declarative and the migration cost isn't worth it.
- You need Groovy control flow (loops, try/catch) at the top level, not inside `script { }` blocks.

**Mixing:** declarative pipeline with `script { }` blocks is possible but is a code smell — heavy `script { }` usage means you've outgrown declarative and should use shared libraries to extract the logic.

**Interview trap:** "Declarative validates before running — what does that mean in practice?" — Jenkins parses the Jenkinsfile against the declarative schema before allocating an agent or running any step. Syntax errors are caught immediately (build shows as failed with parse error, no compute wasted). Scripted pipelines fail only when the broken line is reached at runtime, potentially after minutes of prior work.

**Tags:** jenkins, declarative-pipeline, scripted-pipeline, jenkinsfile, groovy

---

## Q-CICD-008 [bloom: understand] [level: regular]
**Question:** How does Jenkins handle secrets in pipelines? Describe the `withCredentials` pattern and explain what NOT to do.

**Model answer:** Jenkins has a built-in **Credentials Store** that holds secrets as typed objects: `Secret text`, `Username+password`, `SSH private key`, `Certificate`, `Secret file`. Each credential has an ID used to reference it in pipelines.

**The correct pattern — `withCredentials`:**
```groovy
withCredentials([
  usernamePassword(
    credentialsId: 'gcp-registry-sa',
    usernameVariable: 'REGISTRY_USER',
    passwordVariable: 'REGISTRY_PASS'
  )
]) {
  sh 'docker login -u "$REGISTRY_USER" -p "$REGISTRY_PASS" gcr.io'
}
```

What this does:
- Injects credentials as environment variables scoped to the `withCredentials` block.
- **Automatically masks** the secret values in all console output — if `REGISTRY_PASS` appears in a log line, Jenkins replaces it with `****`.
- Variables are unavailable outside the block scope (reduced exposure window).

**What NOT to do:**
1. **Hardcode secrets in Jenkinsfile** — they end up in Git history, readable by anyone with repo access, forever.
2. **Pass secrets as plain build parameters** — parameters are logged and visible in build history.
3. **Print secrets via `echo`** — masking is pattern-based; `echo "pass=${REGISTRY_PASS}"` may slip through if the masking pattern doesn't match exactly.
4. **Use environment block for secrets** — `environment { TOKEN = credentials('token-id') }` works but makes the variable available pipeline-wide (larger exposure window than `withCredentials`).

**At scale:** for Kubernetes-based deployments, prefer Vault (HashiCorp) or GCP Secret Manager integration instead of Jenkins store — gives rotation, audit, fine-grained ACLs, and removes secrets from Jenkins entirely.

**Interview trap:** "Jenkins masks secrets in logs — so printing them is fine?" — No. Masking is a best-effort pattern match. If the secret is split across multiple echo calls, encoded, or embedded in a JSON payload, masking may miss it. Defense in depth: don't log secrets, period.

**Tags:** jenkins, credentials, secrets, withCredentials, security, pipeline

---

## Q-CICD-009 [bloom: understand] [level: regular]
**Question:** Describe GitHub Actions' architecture: what are workflows, jobs, steps, and runners? How do matrix builds work?

**Model answer:** **GitHub Actions** is GitHub's built-in CI/CD platform. Configuration lives in `.github/workflows/*.yml` in the repository.

**Architecture:**

- **Workflow** — a YAML file defining an automation. Triggered by events (`push`, `pull_request`, `schedule`, `workflow_dispatch`). A repo can have multiple workflows.
- **Job** — a unit of work within a workflow. Jobs run in parallel by default; sequential ordering via `needs: [job-id]`. Each job gets a fresh runner environment.
- **Step** — a single task within a job. Either a shell command (`run: mvn test`) or a reusable action (`uses: actions/checkout@v4`). Steps within a job share the same runner filesystem and environment.
- **Runner** — the compute that executes a job. GitHub-hosted (Ubuntu, Windows, macOS — ephemeral VMs) or self-hosted (your own infrastructure).

**Matrix builds:**
```yaml
strategy:
  matrix:
    java: [17, 21]
    os: [ubuntu-latest, windows-latest]
```
This generates 4 jobs (2×2), one per combination. Used for: testing across Java versions, OS compatibility, different DB backends. `matrix.exclude` prunes specific combinations; `matrix.include` adds extra variables to specific combinations.

**Key features:**
- **Secrets** — `${{ secrets.MY_SECRET }}`, set in repo/org settings, injected as env vars, masked in logs.
- **Caching** — `actions/cache` keyed by hash (e.g., `hashFiles('**/pom.xml')`). Reduces `~/.m2/repository` download time from minutes to seconds.
- **Reusable workflows** — `workflow_call` trigger; call with `uses: org/repo/.github/workflows/build.yml@main`. DRY across many repos.
- **Composite actions** — bundle multiple steps into a reusable action defined in a repo.
- **Environments** — define `staging`, `production` environments with required reviewers, secrets scoped to env.

**Interview trap:** "Jobs in a workflow run in parallel by default — what's the implication for a deploy pipeline?" — If deploy-to-staging and deploy-to-prod are both jobs in the same workflow with no `needs:` dependency, they will both start simultaneously. You must explicitly chain them with `needs: [build, test, deploy-staging]` to enforce ordering.

**Tags:** github-actions, workflows, jobs, steps, runners, matrix-builds, ci-cd

---

## Q-CICD-010 [bloom: understand] [level: regular]
**Question:** Compare Maven and Gradle build lifecycle. What phases does `mvn package` execute? What is Gradle's equivalent and what makes Gradle different?

**Model answer:** **Maven lifecycle** is a fixed, opinionated sequence of phases. `mvn package` executes all phases from `validate` through `package`:

```
validate → initialize → generate-sources → process-sources →
generate-resources → process-resources → compile →
process-classes → generate-test-sources → process-test-sources →
generate-test-resources → process-test-resources → test-compile →
process-test-classes → test → prepare-package → package
```

Key phases:
- `compile` — compile `src/main/java`
- `test` — run unit tests (Surefire plugin)
- `package` — produce JAR/WAR

`mvn install` additionally runs `verify` and `install` (copies to `~/.m2/repository`). `mvn deploy` also publishes to the remote repository.

**Gradle** does not have a fixed lifecycle — it has a **task graph**. Tasks declare dependencies on other tasks; Gradle executes the minimal required subgraph. `./gradlew build` runs: `compileJava → processResources → classes → jar → compileTestJava → test → check → build`.

Key differences:

| Aspect | Maven | Gradle |
|--------|-------|--------|
| Configuration | XML (pom.xml) | Groovy/Kotlin DSL (build.gradle) |
| Lifecycle | Fixed phases | Dynamic task graph |
| Incremental builds | Limited | First-class: only rebuild changed inputs |
| Build cache | No (local only via extensions) | Yes — local + remote cache, shareable across CI |
| Performance | Slower for large multi-module | Significantly faster (incremental + daemon) |
| Learning curve | Lower | Higher |
| Android | Not used | Default (required) |

**Gradle daemon** keeps the JVM warm between builds — massive speedup (no JVM startup overhead). `--configuration-cache` caches the task graph itself.

**Interview trap:** "`mvn test` doesn't rebuild if nothing changed — or does it?" — Maven has no built-in incremental compilation. Every `mvn test` recompiles everything unless you explicitly skip compilation or use the `incremental` option in compiler plugin (limited). Gradle is smarter: it checks input/output fingerprints and skips UP-TO-DATE tasks. In a large project this difference is minutes vs seconds.

**Tags:** maven, gradle, build-lifecycle, ci-cd, incremental-builds

---

## Q-CICD-011 [bloom: understand] [level: regular]
**Question:** What is SonarQube and how does it integrate into a CI pipeline as a quality gate?

**Model answer:** **SonarQube** is a static analysis platform that measures code quality across four dimensions: Bugs, Vulnerabilities, Code Smells, and Security Hotspots. It also aggregates test coverage (from JaCoCo or similar) and duplication metrics.

**How it works in a pipeline:**

1. **Analysis step** — after tests run, the Sonar scanner (Maven plugin or standalone) sends the source code + compiled bytecode + coverage report to the SonarQube server:
   ```bash
   mvn sonar:sonar \
     -Dsonar.projectKey=my-service \
     -Dsonar.host.url=$SONAR_URL \
     -Dsonar.login=$SONAR_TOKEN
   ```
2. **Quality Gate** — a set of conditions defined in SonarQube (e.g., coverage >= 80%, no new critical bugs, no new vulnerabilities). SonarQube evaluates these against the submitted analysis.
3. **Pipeline gate** — the scanner (or a separate `sonar-quality-gate` step) polls SonarQube and **fails the build** if the quality gate fails. This is the enforcement mechanism.

**JaCoCo integration:**
```xml
<!-- pom.xml — generate XML coverage report for Sonar -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```

**Quality Gate concepts:**
- **New code gate** — stricter conditions apply only to code changed since the last analysis (Sonar's "new code period"). Legacy debt doesn't block new features; new debt does.
- **Overall gate** — conditions on the whole codebase.

**Interview trap:** "We have 80% coverage — does that mean the code is well-tested?" — No. Coverage tells you which lines were *executed*, not whether the assertions were meaningful. 80% coverage with only `assertNotNull` assertions is noise. Branch coverage is more meaningful than line coverage. Mutation testing (PIT) is the real signal, but expensive.

**Tags:** sonarqube, static-analysis, quality-gate, jacoco, coverage, ci-cd

---

## Q-CICD-012 [bloom: understand] [level: regular]
**Question:** What are the four DORA metrics? What does each measure and what do "elite performer" benchmarks look like?

**Model answer:** **DORA (DevOps Research and Assessment) metrics** are four evidence-based measures of software delivery performance. They're the industry-standard KPIs for pipeline health.

| Metric | What it measures | Elite benchmark |
|--------|-----------------|-----------------|
| **Deployment Frequency** | How often code is deployed to production | Multiple times per day |
| **Lead Time for Changes** | Time from commit to running in production | < 1 hour |
| **Mean Time to Restore (MTTR)** | Time to recover from a production incident | < 1 hour |
| **Change Failure Rate** | % of deployments that cause an incident/rollback | 0–15% |

**What they diagnose:**
- **Low deployment frequency** — large batch sizes, fear of deploying, slow pipelines, manual approvals.
- **High lead time** — long-running tests, slow environments, manual steps in the pipeline, feature branch strategy with long-lived branches.
- **High MTTR** — poor observability, slow rollback mechanisms, lack of on-call ownership.
- **High change failure rate** — insufficient test coverage, no staging parity, skipping quality gates.

**Relationship to CI/CD:**
- CI practices (fast automated tests, trunk-based development) directly drive deployment frequency and lead time.
- CD practices (automated deployment, blue-green/canary rollout) directly drive MTTR and change failure rate.

DORA research (State of DevOps report) shows that elite performers are 973x more likely to meet reliability targets and have 44x more frequent deployments than low performers.

**Interview trap:** "Our deployment frequency is once per month. How do you improve it without increasing risk?" — Smaller batch sizes (trunk-based development, feature flags to decouple deploy from release), invest in automated tests to make each deploy less scary, automated rollback to reduce the cost of failure.

**Tags:** dora-metrics, deployment-frequency, lead-time, mttr, change-failure-rate, devops

---

## Q-CICD-013 [bloom: apply] [level: regular]
**Question:** Compare GitFlow and trunk-based development. When does each make sense? What are the operational costs of GitFlow in a microservices context?

**Model answer:** **GitFlow** uses multiple long-lived branches: `main` (production), `develop` (integration), `feature/*`, `release/*`, `hotfix/*`. A feature is developed on a feature branch, merged to `develop`, periodically a `release/*` branch is cut, tested, then merged to both `main` and `develop`.

**Trunk-Based Development (TBD):** all developers commit to `main` (trunk) at least daily. Short-lived feature branches (1-2 days max) or commit directly. Incomplete features are hidden behind **feature flags**, not in branches.

| Dimension | GitFlow | Trunk-Based |
|-----------|---------|-------------|
| Branch lifetime | Days to weeks | Hours to 1-2 days |
| Merge conflicts | High — diverge over time | Low — small diffs, frequent integration |
| Deployment model | Versioned, planned releases | Continuous, main is always releasable |
| Feature flag need | Low | High — essential |
| CI pipeline triggers | Per-branch | Every commit to main |
| Rollback story | Revert release branch merge | Feature flag off, or revert small commit |
| DORA lead time | High | Low |
| Best for | Libraries, versioned products, regulated batched releases | SaaS, microservices, high-velocity teams |

**GitFlow operational costs in microservices:**
1. **Merge hell** — `develop` branch diverges rapidly when 5 teams push features independently. Merging `release/1.4` back to `develop` becomes a conflict festival.
2. **Long feedback cycles** — a feature merged to `develop` might not hit staging for a week if release branches are cut on a schedule.
3. **Double maintenance** — hotfixes must be cherry-picked to both `main` and `develop`. Easy to forget one.
4. **Pipeline complexity** — Jenkins multibranch must build `main`, `develop`, all `feature/*`, and `release/*` branches. Resource and noise amplification.

**When GitFlow wins:** SDK libraries with multiple supported versions (you genuinely need `release/1.x` and `release/2.x` alive simultaneously), regulated software with hard release windows (banking core, medical devices).

**Interview trap:** "What keeps trunk-based development from shipping half-baked features?" — Feature flags. The code ships to production but is gated off for users. This decouples deploy from release. Canary releases add another layer: enable the flag for 1% of users before full rollout.

**Tags:** gitflow, trunk-based-development, branching-strategy, feature-flags, ci-cd

---

## Q-CICD-014 [bloom: apply] [level: senior]
**Question:** You are designing a zero-downtime deployment for a Spring Boot service on Kubernetes. Compare blue-green, canary, and rolling deployment strategies — trade-offs, failure scenarios, and when you'd pick each.

**Model answer:** **Blue-Green:**
- Maintain two identical environments: `blue` (current live) and `green` (new version).
- Deploy v2 to `green`, run smoke tests, then switch the load balancer (or k8s Service selector) from `blue` to `green` atomically.
- **Rollback:** flip the selector back to `blue`. Near-instant (< 30s typically).
- **Cost:** 2x infrastructure during the transition window.
- **Failure scenario:** green deployment works but a subtle bug only surfaces under real traffic. You're 100% on broken green before you notice. Canary would have caught this earlier.
- **Best for:** high-criticality services where instant rollback is non-negotiable, stateless services where both versions can coexist without DB conflicts.

**Canary:**
- Route a small percentage of traffic to v2 (e.g., 5%) while the rest stays on v1. Gradually shift (5% → 25% → 100%) based on SLO metrics (error rate, latency).
- Kubernetes: via Ingress weight annotations, Istio VirtualService, or Argo Rollouts.
- **Rollback:** set canary weight back to 0%. Only a fraction of users were affected.
- **Cost:** minimal extra resources (canary pods are a small fleet).
- **Failure scenario:** requires meaningful traffic volume to detect rare bugs. A bug affecting 0.1% of requests needs significant canary time to surface.
- **Best for:** detecting regressions before full rollout, A/B testing behavior changes.

**Rolling:**
- Replace pods one-by-one (or N-at-a-time): k8s terminates one old pod, starts one new pod, waits for readiness, then continues.
- Zero extra infrastructure, but both v1 and v2 run simultaneously during the rollout.
- **Rollback:** `kubectl rollout undo deployment/my-service` — rolls back pod-by-pod in reverse.
- **Failure scenario:** if v2 has a DB schema change that is not backwards-compatible with v1, the mixed state during rolling update will cause errors. You must maintain backwards-compatible DB schema across at least one version.
- **Best for:** most standard deployments, stateless services with backwards-compatible changes.

**Recreate:**
- Scale old version to 0, then scale new version up. Guaranteed no mixed-version state.
- **Downtime:** yes, there is a gap.
- **Use only when:** the service is internal, downtime window is acceptable, or the DB migration is so destructive that mixed versions cannot coexist at all.

**DB constraint:** All zero-downtime strategies (blue-green, canary, rolling) require the new version to be able to run with the old DB schema. The Expand-Contract pattern: v2 adds new columns in `nullable` mode, app handles both old and new schema, v1 is retired, then a cleanup migration makes columns non-nullable.

**Interview trap:** "Blue-green is the safest — why not use it everywhere?" — Cost (2x infra), state management complexity (what happens to in-flight requests when you switch?), and session stickiness issues. Also, blue-green doesn't protect you from a bug that only manifests under real traffic patterns — canary does.

**Tags:** deployment-strategy, blue-green, canary, rolling, kubernetes, zero-downtime

---

## Q-CICD-015 [bloom: apply] [level: senior]
**Question:** What is a Jenkins Shared Library? Describe the directory structure, how to load it, and what problems it solves at scale.

**Model answer:** A **Jenkins Shared Library** is a Git repository containing reusable Groovy code loaded by Jenkins pipelines at runtime via `@Library('lib-name')`. It solves the "copy-paste Jenkinsfile" problem at scale — 50 microservices shouldn't each maintain their own deploy logic.

**Directory structure:**
```
shared-library/
├── vars/               # global variables — callable as pipeline steps
│   ├── deployToGke.groovy    # def call(Map config) { ... }
│   └── runSonar.groovy
├── src/                # Groovy classes (proper OOP, testable)
│   └── org/company/
│       ├── PipelineUtils.groovy
│       └── SlackNotifier.groovy
└── resources/          # static resources (shell scripts, JSON templates)
    └── deploy-template.sh
```

**Loading the library in a Jenkinsfile:**
```groovy
@Library('company-pipeline-lib@main') _   // @ pins a branch/tag/commit

pipeline {
  agent any
  stages {
    stage('Build') {
      steps { sh 'mvn clean package -DskipTests' }
    }
    stage('Deploy') {
      steps {
        deployToGke(                    // from vars/deployToGke.groovy
          service: 'user-service',
          environment: 'staging',
          image: "gcr.io/project/user-service:${env.BUILD_NUMBER}"
        )
      }
    }
  }
}
```

**What it solves:**
1. **DRY** — standardize deploy steps, notification patterns, SonarQube calls across all teams.
2. **Centralized security** — the shared library handles credentials retrieval; individual Jenkinsfiles don't need to know credential IDs.
3. **Versioning** — pin the library at a release tag (`@v2.3.1`). Teams can upgrade on their schedule.
4. **Testability** — `src/` classes can be unit-tested with JenkinsPipelineUnit framework.

**Operational considerations:**
- Library code runs with high trust — by default, shared library code is approved to run without script approval (unlike Jenkinsfile code from untrusted sources).
- Pin to a branch/tag, not `@master` — untested changes to the library will break all pipelines consuming it.
- Global libraries (configured in Jenkins global config) are implicitly loaded — good for company-wide defaults.

**Interview trap:** "What if the shared library itself has a bug and breaks all pipelines?" — This is the real risk. Mitigate with: versioned tags (not floating `main`), test the library with `JenkinsPipelineUnit` before merging, canary rollout to a few pipelines first, and always have an escape hatch in each Jenkinsfile to override specific steps.

**Tags:** jenkins, shared-library, pipeline-as-code, groovy, dry, ci-cd-at-scale

---

## Q-CICD-016 [bloom: apply] [level: senior]
**Question:** Walk through GitHub Actions caching for a Maven project. What key do you use, what does a cache miss look like, and what are the failure modes?

**Model answer:** GitHub Actions provides `actions/cache` to persist directories between runs. For Maven, the goal is to cache `~/.m2/repository` — the local dependency cache — so repeated runs don't re-download artifacts.

**Standard setup:**
```yaml
- name: Cache Maven dependencies
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-
```

**How the key works:**
- `key` is an exact match key. If `pom.xml` hasn't changed, `hashFiles('**/pom.xml')` produces the same hash → cache hit → `~/.m2/repository` is restored from the previous run's snapshot.
- `restore-keys` is a prefix fallback list. If the exact key misses (pom.xml changed), Actions falls back to the most recent cache with prefix `ubuntu-latest-maven-`, giving you a warm (but potentially stale) cache. Maven will then download only the newly added dependencies.

**Cache miss flow:**
1. No matching cache → fresh runner, `~/.m2/repository` is empty.
2. Maven downloads all dependencies from central (or Nexus proxy).
3. After the job completes, Actions saves `~/.m2/repository` under the new key for future runs.

**Failure modes:**
1. **Stale cache after dependency removal** — you remove a dependency from `pom.xml`. Hash changes, new cache key, full re-download. Old cache entry is orphaned (deleted after 7 days of non-use). This is correct behavior but wastes one run.
2. **Corrupted cache** — partial download during the save phase can produce a corrupt cache entry. Symptoms: `mvn` fails with `Could not resolve artifact`. Fix: delete the cache entry in the GitHub UI (`Actions → Caches`) and re-run.
3. **Cache poisoning** — a compromised workflow run saves a malicious artifact into the cache. Mitigation: pin actions to SHAs, not tags; restrict who can trigger workflows on main.
4. **Multi-module + partial match** — in a monorepo where only one module's `pom.xml` changed, `hashFiles('**/pom.xml')` still changes, missing the cache even though most modules' dependencies haven't changed. More granular caching requires more granular keys per module.

**Performance numbers (rough):** cold cache → downloading 300+ dependencies: 3-5 min. Cache hit: restore in 15-30s. For a Java service with a big dependency graph, caching can save 80% of dependency resolution time.

**Interview trap:** "Does caching `~/.m2/repository` cache SNAPSHOT artifacts?" — Yes, and that's a problem. SNAPSHOTs are mutable; a cached SNAPSHOT may be stale. Add `-U` to `mvn` to force SNAPSHOT updates when freshness matters, or explicitly exclude SNAPSHOT repos from the cache path.

**Tags:** github-actions, caching, maven, ci-cd, performance

---

## Q-CICD-017 [bloom: apply] [level: senior]
**Question:** What is a feature flag and how does it decouple deployment from release? What are the operational risks of accumulating feature flags?

**Model answer:** A **feature flag** (also: feature toggle, feature switch) is a conditional in application code that controls whether a feature is active at runtime, without requiring a redeploy.

```java
if (featureFlags.isEnabled("new-checkout-flow", userId)) {
    return newCheckoutService.process(order);
} else {
    return legacyCheckoutService.process(order);
}
```

**Decoupling deploy from release:**
- **Deploy** = pushing the new code to production. The feature code is *present* but the flag is off (0% traffic).
- **Release** = turning the flag on (10%, 50%, 100% of users, or specific user segments).
- This separation means: deploy anytime (CI/CD goal), release when the business is ready (marketing, support readiness, A/B test readiness).
- Enables trunk-based development: incomplete features can live in `main` behind a flag without affecting users.

**Use cases:**
- **Dark launches** — run new code paths silently alongside old ones, compare results (shadow mode).
- **Canary releases** — enable for 5% of users, watch metrics, expand.
- **Kill switch** — instantly disable a misbehaving feature without a deploy.
- **A/B testing** — different features for different user groups.

**Operational risks of accumulating flags:**
1. **Technical debt** — every flag doubles code paths. After the feature is stable, the old path is dead code but nobody removes the flag. Over time: dozens of stale flags, nobody knows what's safe to delete.
2. **Testing combinatorial explosion** — 10 flags = 2^10 = 1024 possible combinations. You can't test them all.
3. **Cognitive load** — reading code with `if (flagA && !flagB && flagC)` is a maintenance nightmare.
4. **Flag dependency bugs** — Flag B assumes Flag A is on. Someone disables Flag A; Flag B misbehaves in unexpected ways.
5. **Performance overhead** — flag evaluation on every request (especially remote flag checks via LaunchDarkly/Unleash) adds latency. Mitigate with local caching.

**Mitigation:** treat flags as technical debt from day 0. Add a ticket to remove the flag and the old code path immediately when the flag reaches 100% + stable. Short-lived flags (days/weeks) are fine; permanent flags should be replaced with proper configuration.

**Interview trap:** "Feature flags are in application code — can you deploy a flag change without redeploying?" — Yes, that's the point. The flag value (on/off/percentage) is stored in a remote config service (LaunchDarkly, Unleash, GCP Remote Config, Consul) or a DB table. Application reads it at runtime. Flag flip = instant behavior change in production with zero deploy.

**Tags:** feature-flags, deployment-decoupling, trunk-based-development, continuous-deployment, technical-debt

---

## Q-CICD-018 [bloom: apply] [level: senior]
**Question:** Describe the environment promotion model (dev → staging → prod). What makes staging fail as a pre-production gate, and how do you mitigate it?

**Model answer:** **Environment promotion** is the controlled advancement of a build artifact through a sequence of environments, where each environment applies progressively stronger quality gates.

Typical chain:
```
[build] → dev → staging → pre-prod (optional) → production
```

Each stage:
- **Dev** — fast feedback, deployed on every push to a feature branch or main. Used by developers for integration smoke tests. Data is synthetic. May be shared or per-developer.
- **Staging** — mirrors production in topology (same k8s node classes, same network policies). Used for QA, integration tests, UAT. Should run on the same artifact hash that will go to prod (no rebuild).
- **Pre-prod** — optional. Used for load testing, performance benchmarking, smoke tests with production-size data (anonymized).
- **Production** — real traffic.

**Why staging fails as a pre-production gate:**

1. **Data mismatch** — staging has a fraction of production data volume. Queries that are fast on 10k rows are slow on 100M rows. Performance bugs slip through.
2. **Environment drift** — staging configuration diverges from production over time (different env vars, different secret values, different network topology). "Works in staging" doesn't mean "works in prod."
3. **External dependencies** — staging may call sandbox/mock versions of third-party APIs. Production calls the real ones (different rate limits, different latencies, different failure modes).
4. **Traffic patterns** — synthetic QA traffic doesn't match real user traffic distributions. Edge cases in real usage never hit staging.
5. **Shared staging** — multiple teams use the same staging environment. Noisy neighbor: one team's deploy breaks another team's tests.

**Mitigations:**
- **Infrastructure as Code** — keep staging and prod defined from the same Terraform modules with environment-specific variables. Drift is minimized.
- **Production-like data** — use anonymized production data snapshots in staging, or at least production-representative volume.
- **Dark launches / canary in production** — accept that staging will miss things; use canary deployments in prod as the real safety net.
- **Chaos engineering** — inject failures in staging that don't occur naturally (Chaos Monkey).
- **Dedicated staging per team** — namespace-level isolation in Kubernetes to eliminate noisy neighbor.

**Interview trap:** "If staging is unreliable, should we skip it?" — No, but be honest about what it gates. Staging catches obvious integration breaks, config issues, and build regressions. It doesn't catch production traffic patterns or data volume issues. Layer it with production canary releases for defense in depth.

**Tags:** environment-promotion, staging, ci-cd, testing, infrastructure

---

## Q-CICD-019 [bloom: apply] [level: senior]
**Question:** What is a rollback strategy for a production deployment? Compare artifact rollback, database rollback, and feature flag rollback.

**Model answer:** Rollback is the ability to return to a known-good state after a bad deployment. The three layers are independent and must be considered together.

**Artifact / application rollback:**
- **Blue-green:** flip the load balancer back to the blue environment. Seconds. Cleanest rollback.
- **Kubernetes rolling rollback:** `kubectl rollout undo deployment/my-service`. Rolls back the pod template to the previous spec. Takes ~1-2 minutes (same time as the rollout).
- **Helm:** `helm rollback my-service 2` (rollback to revision 2). Equivalent to undoing the Helm upgrade.
- **Container image rollback:** redeploy previous Docker image tag. Only works if the old tag is retained in the registry (set retention policies to keep N recent tags).

**Database rollback:**
- This is the hard part. If the deployment included a DB migration (Flyway/Liquibase), rolling back the app without rolling back the schema creates a mismatch.
- **Forwards-only philosophy:** many teams never rollback schema changes. Instead, write migrations that are always backwards-compatible with the previous app version (Expand-Contract pattern). If v2 fails, v1 can still run against the v2 schema.
- **Destructive rollback:** if you must, Flyway supports undo scripts (paid), or Liquibase `rollbackCount`. But this deletes data in newly created columns — only safe if no production traffic ran against v2.
- **Rule of thumb:** schema rollback is rarely feasible after any real traffic has touched the new schema. Design migrations to be reversible or acceptably non-reversible.

**Feature flag rollback:**
- Fastest: flip the flag to off (0% exposure) in the flag management system. No deploy, no schema change. Sub-second propagation.
- The deployment stays in place; the behavior is restored.
- This is why feature flags are the primary risk mitigation tool for CI/CD at high velocity.

**Rollback strategy tiering:**
| Strategy | Speed | Risk | When to use |
|----------|-------|------|-------------|
| Feature flag off | Seconds | None | Any feature behind a flag |
| Blue-green flip | < 1 min | None (if blue is intact) | Critical services |
| k8s rollout undo | 1-3 min | Low | Most k8s deployments |
| Helm rollback | 1-3 min | Low | Helm-managed releases |
| Full redeploy of previous artifact | 3-10 min | Medium | When rollout undo isn't available |
| DB rollback | High risk | Data loss possible | Last resort, only if no traffic touched new schema |

**Interview trap:** "We rolled back the app but the users are still seeing errors. Why?" — The DB schema was already changed (e.g., a column was renamed). The old app version references the old column name, but the DB now has the new name. Expand-Contract pattern would have prevented this: add the new column first (backwards-compatible), run both versions, then drop the old column after the app is fully migrated.

**Tags:** rollback, deployment, blue-green, kubernetes, database-migration, feature-flags

---

## Q-CICD-020 [bloom: apply] [level: senior]
**Question:** How does Terraform manage infrastructure state, and what problems arise in a CI/CD pipeline when multiple pipeline runs execute simultaneously?

**Model answer:** Terraform tracks the mapping between your configuration (`.tf` files) and the actual cloud resources in a **state file** (`terraform.tfstate`). The state file is JSON, contains resource IDs, attribute values, and dependency metadata.

**State in CI/CD — remote backends:**
```hcl
terraform {
  backend "gcs" {
    bucket = "company-tfstate-prod"
    prefix = "services/user-service"
  }
}
```
State stored in GCS (or S3, Terraform Cloud). Every `terraform plan` and `terraform apply` reads and writes this file.

**The concurrency problem:**
Two pipeline runs execute `terraform apply` simultaneously:
1. Run A reads state (v1), computes plan.
2. Run B reads state (v1), computes plan.
3. Run A applies, writes state (v2).
4. Run B applies based on a stale plan, writes state (v3) — potentially overwriting A's changes or creating conflicting resources.

**Solution — state locking:**
Remote backends (GCS + Cloud Spanner lock, S3 + DynamoDB lock, Terraform Cloud) implement a distributed lock. `terraform apply` acquires the lock before writing, releases it after. A concurrent run trying to acquire the same lock will either wait or fail with:
```
Error: Error acquiring the state lock
Lock Info:
  ID:        abc-123
  Operation: apply
  Who:       runner@github-actions
```

**Plan/apply workflow in CI:**
```
PR opened → terraform plan → post plan diff as PR comment
PR merged → terraform apply (with lock, serialized)
```

Best practice: **plan in CI, apply gated by approval**. Never `terraform apply -auto-approve` on prod without a human review of the plan output.

**State file security:**
- State contains plaintext secret values (passwords, connection strings) if resources contain them.
- Never commit `terraform.tfstate` to Git.
- Encrypt the GCS bucket with CMEK. Enable versioning on the bucket (state file history = rollback capability).

**`terraform workspace` for environments:**
- Separate workspaces for `dev`, `staging`, `prod` within the same backend bucket.
- Workspace name becomes part of the state path: `terraform/state/<workspace>/terraform.tfstate`.
- Alternative: separate backend prefixes or separate accounts per environment (stronger isolation).

**Interview trap:** "What happens if the pipeline fails mid-apply?" — Terraform may leave resources in a partially-applied state. The state file reflects what was successfully created/modified. On the next `apply`, Terraform reconciles the remaining resources. However, if the state file itself was being written when the crash happened, it may be corrupted — this is why backend state locking and atomic writes (GCS uses object versioning) matter. Always check `terraform state list` before re-running a failed apply.

**Tags:** terraform, iac, state-management, ci-cd, concurrency, remote-backend

---

## Q-CICD-021 [bloom: analyze] [level: senior]
**Question:** A pipeline that previously took 8 minutes now takes 22 minutes. Walk through your systematic diagnosis and the optimizations you'd apply.

**Model answer:** Systematic approach: measure first, optimize the bottleneck.

**Step 1 — Profile the pipeline:**
- Look at stage timing in the Jenkins Blue Ocean view or GitHub Actions workflow summary.
- Find the single longest stage. Optimize the bottleneck; optimizing a 30-second stage when a 15-minute stage exists is waste.

**Common culprits and fixes:**

**Dependency resolution slow (e.g., `mvn install` downloading 200 artifacts):**
- Add artifact cache (GitHub Actions: `actions/cache` on `~/.m2/repository`; Jenkins: workspace-level cache or Nexus proxy).
- Use Nexus/Artifactory as a proxy for Maven Central — LAN speeds instead of internet.

**Integration tests slow:**
- Identify which tests are slow: `mvn test -pl integration-tests -Dsurefire.useFile=true` + report.
- Parallelize: Surefire `<forkCount>` (parallel JVMs), or split test classes across pipeline stages using `@Tag` and `includeTags`.
- Replace slow integration tests with contract tests (Pact) or Testcontainers parallelism.

**Sequential stages that could be parallel:**
```groovy
// Before: sequential
stage('Unit Tests')  { steps { sh 'mvn test -pl unit' } }
stage('Sonar Scan')  { steps { sh 'mvn sonar:sonar' } }

// After: parallel
stage('Quality Gates') {
  parallel {
    stage('Unit Tests') { steps { sh 'mvn test -pl unit' } }
    stage('Sonar Scan')  { steps { sh 'mvn sonar:sonar' } }
  }
}
```

**Docker build slow:**
- Layer caching: order `COPY pom.xml .` + `RUN mvn dependency:resolve` before `COPY src .` so dependency layer is cached between builds.
- BuildKit: `DOCKER_BUILDKIT=1` enables parallel layer building.
- Multi-stage builds: separate compile and runtime images, smaller final layer.

**Agent startup overhead (Jenkins):**
- Dynamic Kubernetes agents spin up a pod per build — pod scheduling + image pull overhead can be 60-90 seconds.
- Use pre-pulled images on nodes, or switch to long-lived agents with workspace reuse for non-sensitive builds.

**SonarQube timeout:**
- Sonar analysis blocks waiting for the quality gate result (polling). Set a reasonable timeout (`sonar.qualitygate.wait=true` + `sonar.qualitygate.timeout=300`).

**Test flakiness masquerading as slowness:**
- Flaky tests that fail and retry inflate timing. Fix or quarantine flaky tests.

**Fail-fast enforcement:**
- Unit tests should run before integration tests. A failing unit test aborting the pipeline after 2 minutes is better than failing after 20.

**Typical result of full optimization pass:** 22 min → 8-10 min through: caching (save 5 min), parallelizing unit+sonar (save 4 min), Docker layer caching (save 2 min).

**Interview trap:** "You parallelized everything and the pipeline is still slow — what's left?" — Check if there's a single serial bottleneck that all parallel stages converge into (e.g., a slow publish step at the end). Also check runner/agent queue wait time — if 5 pipelines are competing for 2 agents, queue wait inflates clock time but not CPU time.

**Tags:** pipeline-optimization, ci-cd, parallelism, caching, performance, build-time

---

## Q-CICD-022 [bloom: analyze] [level: senior]
**Question:** Describe a canary deployment implementation on Kubernetes using Argo Rollouts or Istio. What metrics do you monitor to decide whether to proceed or abort?

**Model answer:** **Canary with Argo Rollouts** (easier k8s-native approach):

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: user-service
spec:
  replicas: 10
  strategy:
    canary:
      steps:
      - setWeight: 10          # 10% of traffic to canary
      - pause: {duration: 5m}  # wait 5 minutes, watch metrics
      - setWeight: 50
      - pause: {duration: 10m}
      - setWeight: 100         # promote to full rollout
      canaryMetadata:
        labels: {track: canary}
      stableMetadata:
        labels: {track: stable}
      analysis:
        templates:
        - templateName: success-rate-check
        startingStep: 1
```

```yaml
# AnalysisTemplate — abort if error rate > 5%
kind: AnalysisTemplate
metadata:
  name: success-rate-check
spec:
  metrics:
  - name: success-rate
    interval: 1m
    successCondition: result[0] >= 0.95
    failureLimit: 3
    provider:
      prometheus:
        address: http://prometheus:9090
        query: |
          sum(rate(http_requests_total{job="user-service",status!~"5.."}[1m]))
          / sum(rate(http_requests_total{job="user-service"}[1m]))
```

**Istio approach:** uses `VirtualService` with traffic weight splitting:
```yaml
http:
- match: []
  route:
  - destination: {host: user-service, subset: stable}
    weight: 90
  - destination: {host: user-service, subset: canary}
    weight: 10
```
Istio gives finer-grained routing (by header, cookie, user ID) compared to Argo Rollouts' percentage-only split.

**Metrics to watch during canary promotion:**

| Metric | Alert threshold | What it catches |
|--------|-----------------|-----------------|
| HTTP 5xx error rate | > stable baseline + 1% | Application errors |
| p99 latency | > stable p99 × 1.5 | Performance regression |
| JVM heap usage | Trending upward (memory leak) | OOM risk |
| DB query duration | > baseline + 20% | Slow queries in new code |
| Business metric | Conversion rate, orders/min | Silent logic bugs |

**Abort criteria:** if any metric breaches its threshold for > 2 consecutive intervals (reduce flapping), Argo Rollouts automatically sets canary weight to 0 and marks the rollout as `Degraded`. No human needed.

**Manual abort:**
```bash
kubectl argo rollouts abort user-service
```

**Interview trap:** "The canary at 10% looks fine. You promote to 50% and suddenly errors spike. Why?" — Traffic volume effect. At 10%, low-frequency edge cases don't surface. At 50%, the absolute number of requests hitting the new code is 5x higher, so rare bugs become visible. Also: certain user segments (power users, specific regions) may be over-represented at higher traffic percentages if routing isn't purely random.

**Tags:** canary-deployment, kubernetes, argo-rollouts, istio, observability, deployment-strategy

---

## Q-CICD-023 [bloom: analyze] [level: master]
**Question:** Your team is moving from monthly releases (GitFlow) to weekly deployments. What are the five biggest engineering risks in this transition and how do you mitigate each?

**Model answer:** This is a process-and-engineering hybrid question. The risks span code, pipeline, DB, team discipline, and observability.

**Risk 1 — Incomplete features in the release stream**
Monthly releases allow long-lived feature branches. Weekly releases mean code must be in a shippable state every week.
- Mitigation: feature flags for in-progress work. Trunk-based development. Teams commit small, shippable increments daily. Features are "dark launched" (deployed but hidden) until ready.

**Risk 2 — Database migrations at weekly frequency**
With monthly releases, a schema migration could soak for weeks in staging. Weekly means a migration may be in production within days of being written.
- Mitigation: enforce Expand-Contract pattern. Every migration is backwards-compatible with N-1 app version. Add a CI gate (Flyway validation in tests) that confirms schema is compatible. Never rename/drop columns in a single migration with the code change — separate migration from app change by one deploy cycle.

**Risk 3 — Pipeline capacity / speed**
Monthly builds have time to be slow. Weekly releases are blocked if the pipeline takes 45 minutes and flaky tests cause frequent retries.
- Mitigation: pipeline must complete in < 15 minutes. Profile and optimize (parallelism, caching, test split). Fix or quarantine flaky tests immediately — flaky tests are a production incident in a weekly-release model.

**Risk 4 — Insufficient observability for fast incident response**
Monthly release cycles mean incidents are infrequent but tolerated (MTTR measured in days). Weekly deployments mean higher deployment frequency → higher probability of incidents in any given week. You need fast detection and rollback.
- Mitigation: structured logging (JSON), distributed tracing, RED metrics dashboards per service. Automated canary analysis (abort on SLO breach). Feature flags as instant kill switch. Alert on deployment events — every deploy should increment a Grafana annotation so you can correlate metric changes with deployments.

**Risk 5 — Team discipline and review throughput**
Weekly releases require PRs to be reviewed and merged within days, not weeks. If review bottlenecks cause code to pile up, you end up with a "weekly release" that contains 3 weeks of changes — negating the benefit.
- Mitigation: WIP limits (max 2 open PRs per engineer), async review culture (24h SLA for first review), pair/mob programming for complex features (reduces review cycle). Definition of Done includes: merged to main, deployed to staging, smoke tested, within the same week as development.

**Bonus risk — Customer-visible change frequency**
Stakeholders and support teams used to monthly predictability may struggle with weekly changes.
- Mitigation: decouple deploy from release with feature flags. The business still controls when features are visible to customers.

**Interview trap:** "We've mitigated the risks — can we go to daily deployments?" — The technical stack changes are largely the same (the above mitigations apply equally to daily). The real limiter is team culture and trust. Daily deployments require: < 10 min pipeline, zero manual QA gates, automated canary analysis, on-call rotation with SLA, and psychological safety to deploy and roll back without ceremony.

**Tags:** ci-cd-transformation, gitflow, trunk-based-development, deploy-frequency, dora-metrics, organizational-change

---

## Q-CICD-024 [bloom: analyze] [level: master]
**Question:** Design a multi-region CI/CD pipeline for a critical Spring Boot service that must maintain 99.99% availability. What are the deployment sequencing and rollback implications when regions are interdependent via a shared database?

**Model answer:** This is an architecture question that touches deployment strategy, database consistency, and operational complexity.

**Constraints established upfront:**
- 99.99% SLA = ~52 minutes downtime/year. Zero-downtime deployment is non-negotiable.
- Multi-region with a shared database means the DB is either global (Cloud Spanner, CockroachDB, Aurora Global) or the regions share a primary DB via replication.
- The app must tolerate mixed versions across regions during deployment.

**Deployment sequencing — canary-per-region:**

```
Region order: eu-west1 (canary, ~5% traffic) → us-east1 (20% traffic) → ap-southeast1 (75% traffic)

Phase 1: Deploy v2 to eu-west1 only
  - Bake: 30 minutes at full regional traffic
  - Automated analysis: error rate, p99, business metrics
  - Gate: human approval or automated SLO check

Phase 2: Deploy v2 to us-east1
  - Both eu-west1 (v2) and us-east1 (v2), ap-southeast1 (v1) live
  - Mixed-version state — DB must be backwards-compatible with v1

Phase 3: Deploy v2 to ap-southeast1
  - All regions on v2
  - Remove old code paths, flag cleanup in next release
```

**Database schema constraint:**
With regions deploying at different times, v1 and v2 run simultaneously for up to hours. The schema must support both:
- Phase 1: eu-west1 on v2 writes new `payment_method_v2` column. ap-southeast1 on v1 ignores it (column nullable, not read by v1).
- Phase 3: all regions on v2, v1 app code is fully retired. Cleanup migration drops old column.

This is Expand-Contract enforced by the multi-region deployment sequence.

**Rollback in multi-region:**
- **Rollback eu-west1 only:** flip to v1 pods in that region. No other region affected. DB state already has v2 data written by eu-west1 — v1 app must tolerate the new columns (Expand-Contract handles this).
- **Full rollback after all regions on v2:** kubectl rollout undo in all regions. If v2 had a destructive migration (dropped columns), rollback is impossible without data restoration. This is why destructive migrations are forbidden during the transition window.

**Feature flags in multi-region:**
- Flag state must be consistent across regions. Use a global flag store (LaunchDarkly, GCP Firebase Remote Config) not a regional DB table. Otherwise, feature is on in eu-west1 but off in ap-southeast1.

**Operational considerations:**
1. **Pipeline concurrency:** separate pipelines per region, sequenced by the orchestration pipeline. Parallel regional deploys would violate the bake-and-observe model.
2. **Traffic routing during deploy:** Anycast/GSLB (Global Server Load Balancing) routes users to nearest region. During eu-west1 deployment, GSLB can temporarily route eu users to us-east1 to eliminate even the brief pod-restart downtime.
3. **Database replication lag:** if using Postgres with read replicas per region, v2 writes new schema data to the primary. Replicas receive it after replication lag. If v2 reads from the replica before the schema change replicates, it will fail. Mitigation: read from primary for schema-critical reads during the deploy window, or use globally consistent DB.
4. **Deployment pipeline as a service:** use Spinnaker or Argo Rollouts with multi-cluster support for orchestrated multi-region deployment rather than hand-crafted Jenkins pipelines.

**Interview trap:** "You could just deploy all regions simultaneously to minimize the mixed-version window." — True, but: (a) if the deployment has a bug, it hits 100% of traffic simultaneously — no canary safety net. (b) All regions deploying in parallel requires the DB to handle simultaneous schema migration from multiple regions, which can cause locking. (c) The risk of a full-blast bad deployment at 99.99% SLA is catastrophic. Sequential deployment with bake windows is the right trade-off.

**Tags:** multi-region, deployment-strategy, database-migration, high-availability, canary, ci-cd-architecture

---

## Q-CICD-025 [bloom: analyze] [level: master]
**Question:** You have 10 microservices, each with its own pipeline. Explain how you'd measure and systematically improve your team's DORA metrics from "low performer" to "high performer" over 6 months.

**Model answer:** DORA transformation is a system problem — you can't improve one metric in isolation without affecting others.

**Baseline measurement (Month 1):**
First, instrument reality. Many teams don't know their actual metrics.

- **Deployment Frequency:** count actual prod deploys per service per week from deployment logs or the CI system. If you're at monthly, that's 0.25/week.
- **Lead Time:** measure from the first commit in a PR to the deploy event in production. Git + CI timestamps give this. Typical low performer: 1-6 months.
- **MTTR:** from first alert to service restoration. Pull from PagerDuty/incident management tool. Low performer: 1 week+.
- **Change Failure Rate:** count deploys that resulted in an incident (rollback, hotfix, or postmortem) / total deploys. Low performer: > 45%.

**6-month roadmap:**

**Months 1-2 — Fix the foundation (pipeline speed + test reliability):**
- Goal: pipeline < 15 minutes for all 10 services.
- Action: profile each pipeline, add caching, parallelize stages. Fix or quarantine top-10 flaky tests per service.
- Why: you can't increase deploy frequency if each deploy costs 45 minutes of pipeline time + manual fixes.

**Months 2-3 — Unlock deployment decoupling (feature flags):**
- Deploy a feature flag service (Unleash self-hosted or LaunchDarkly).
- Mandate: no feature in production that isn't behind a flag or fully complete. This unblocks trunk-based development.
- Transition from GitFlow to short-lived branches (max 2 days).
- Expected impact: lead time decreases (no more 2-week feature branches), deployment frequency increases.

**Months 3-4 — Automate quality gates:**
- Add SonarQube quality gates to all 10 pipelines (coverage > 70%, no new critical bugs).
- Add OWASP dependency check.
- Add automated canary analysis (Argo Rollouts with Prometheus-based success-rate check).
- Expected impact: change failure rate decreases (automated gates catch regressions before prod).

**Months 4-5 — Improve rollback speed (MTTR):**
- All services must have a feature flag kill switch.
- Blue-green or canary for all production deployments (no more Recreate strategy).
- SLO dashboards per service. Grafana deploy annotations.
- PagerDuty integration: automated alert when a new deploy degrades SLO.
- Expected impact: MTTR drops from days to < 1 hour (feature flag flip + automated detection).

**Month 6 — Move toward continuous deployment:**
- Remove manual approval gates for staging → prod for low-risk services (internal services first).
- Deploy frequency target: daily per service.
- Measure final DORA metrics, compare to baseline.

**Expected trajectory:**

| Metric | Month 0 | Month 6 target |
|--------|---------|----------------|
| Deploy frequency | Monthly (0.25/week) | Weekly → Daily |
| Lead time | 1-4 weeks | 1-3 days |
| MTTR | 1 week | < 1 hour |
| Change failure rate | 45% | < 15% |

**Key insight:** DORA metrics are correlated. Small batch sizes (trunk-based) → fewer conflicts → lower change failure rate. Fast pipeline → fearless deploys → higher frequency. Good observability → fast detection → lower MTTR. Pull one lever and others improve.

**Interview trap:** "You can just mandate daily deploys and the metrics will improve automatically." — Mandating deploy frequency without fixing the underlying pipeline speed, test reliability, and rollback story will increase change failure rate and MTTR. Teams will start deploying broken code under pressure. Fix the system first, deploy frequency is an outcome, not a lever.

**Tags:** dora-metrics, ci-cd-transformation, deployment-frequency, lead-time, mttr, change-failure-rate, devops-culture

---

## Q-CICD-026 [bloom: analyze] [level: master]
**Question:** Describe the Expand-Contract database migration pattern. Why is it required for zero-downtime deployments, and what does each phase look like for renaming a column?

**Model answer:** **Expand-Contract** (also: parallel change pattern) is the technique for making backwards-incompatible schema changes in a zero-downtime deployment. It splits what would be one breaking migration into three phases, each deployed separately.

**Why it's required:**
In any deployment strategy that runs two versions simultaneously (rolling, blue-green with traffic mixing, canary), both v1 and v2 of the application are running against the *same database* for some time window. A migration that deletes or renames a column breaks v1 immediately.

**Example: rename column `user_name` → `display_name` in `users` table:**

**Phase 1 — Expand (safe to deploy with v1 running):**
```sql
-- Migration V2__add_display_name.sql
ALTER TABLE users ADD COLUMN display_name VARCHAR(255);
```
App v2 code: write to both `user_name` and `display_name`. Read from `display_name` (with fallback to `user_name` if null). This is the transition logic.
```java
// v2 app reads:
String name = user.getDisplayName() != null 
    ? user.getDisplayName() 
    : user.getUserName();
// v2 app writes both:
user.setUserName(request.getName());
user.setDisplayName(request.getName());
```

Deploy v2. Both v1 (writes `user_name`) and v2 (writes both) now run fine. No downtime.

**Phase 2 — Migrate data (background job or migration script):**
```sql
-- Migration V3__backfill_display_name.sql
UPDATE users SET display_name = user_name WHERE display_name IS NULL;
-- Run in batches to avoid lock escalation on large tables
```
After this: `display_name` is fully populated.

**Phase 3 — Contract (remove old column — only safe after v1 is fully retired):**
```sql
-- Migration V4__drop_user_name.sql (in NEXT release, not the same as Phase 2)
ALTER TABLE users DROP COLUMN user_name;
```
App v3 code: remove `user_name` references entirely. `display_name` is the only column now.

**Timeline:**
```
Deploy v2 (Expand) → v1 and v2 both happy → promote fully to v2 → 
backfill data → confirm → Deploy v3 (Contract/Drop) → done
```

**Why you must wait for v3:**
If you drop the column in the same release as v2, and v2's rolling deployment is in progress (50% pods on v1, 50% on v2), v1 pods will immediately fail reads on `user_name` — the column no longer exists. You've just caused a production incident during a "zero-downtime" deployment.

**Real-world complication — adding NOT NULL constraint:**
```sql
-- Cannot do this in one shot on a large table with running app:
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
-- This acquires AccessExclusiveLock for the full table scan
-- On 100M rows = minutes of lock = downtime
```
PostgreSQL workaround (from PG 11+): add NOT NULL via CHECK CONSTRAINT NOT VALID first, then VALIDATE CONSTRAINT in a separate transaction:
```sql
ALTER TABLE users ADD CONSTRAINT display_name_notnull 
  CHECK (display_name IS NOT NULL) NOT VALID;
-- Later (separate migration):
ALTER TABLE users VALIDATE CONSTRAINT display_name_notnull;
-- VALIDATE uses ShareUpdateExclusiveLock — doesn't block reads/writes
```

**Interview trap:** "This is three deployments for one column rename — isn't that over-engineering?" — At a small scale with acceptable downtime, yes. At scale with a 99.9% SLA and 100M rows, a 2-minute table lock costs you your monthly error budget in one deploy. The pattern is the price of running at scale with continuous deployment. Automate it (migration linting tools like `squawk` or `pgmigrate`) so it's not a manual discipline tax.

**Tags:** database-migration, expand-contract, zero-downtime, flyway, liquibase, postgresql, schema-evolution

