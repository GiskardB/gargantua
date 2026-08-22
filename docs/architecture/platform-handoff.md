# Platform Handoff — Gargantua AI Operating System

**Read this first when resuming work on the *platform* (any repo).** It is the
cross-repository map: where every component lives, what is built, what is decided,
and what comes next. It complements — does not replace — two narrower docs:

- [`../project-handoff.md`](../project-handoff.md) — orientation for the **Runtime
  repository** internals (modules, invariants, delivery modes).
- [`ai-operating-system.md`](ai-operating-system.md) — the **vision** (the north
  star, not a sprint plan).

**Last updated:** 2026-08. If a fact here disagrees with the code, the code wins —
fix this doc.

**Recent progress (most recent first):** governance envelope (`metadata.governance`,
shared `Governed` trait) → Agent Loadout (`spec.loadout`, knowledge bases first-class,
shared model bumped to `1.3.0-SNAPSHOT`) → Docker Compose vertical slice
(`gargantua-compose`, with `start.bat`/`stop.bat`, ports in the 18xxx/19xxx range). See §7
for the roadmap and what's next.

---

## 1. What Gargantua is (one paragraph)

Gargantua is an open-source, distributed **AI Operating System**: you *declare* an
agent (its model, skills, capabilities, MCP tools, memory, guardrails) and the
platform builds, catalogs, deploys and runs it. It borrows the Kubernetes control
loop shape — a **Control Plane defines** desired state, a **Runtime applies** it —
but **Kubernetes is never required to use it**. The whole platform must be runnable
locally with Docker Compose; K8s/the Operator/CRDs are an additive Phase 5.

## 2. Repository map

Development happens locally in **Cave** (web IDE + terminal); **origin is Forgejo**
(`http://forgejo:3000/gbrescia/<repo>.git`). All repos are on branch `main`. Some
also mirror to GitHub (`GiskardB`). Sibling repos are checked out side by side under
`/home/dev/workspace/`.

| Repo | Role | Stack | Status (2026-08) |
|---|---|---|---|
| `gargantua` | **Runtime** + execution-side Kernel; **home of all architecture docs**; publishes `agent-core` | Java 25 / Spring Boot 4.1, 9 Maven modules | Phase 1 + loadout + governance parsing; docs hub; on `1.3.0-SNAPSHOT` |
| `gargantua-control-plane` | **Control Plane**: Registry + Catalog + Policy + Deployment | Java 25 / Boot 4.1, `agent-core` | **MVP** — publish→index→discovery works; 18 tests |
| `gargantua-studio-backend` | **Studio BFF**: builds `gargantua.ai/v1` manifests from form drafts; gateway to the Control Plane | Java 25 / Boot 4.1, `agent-core` | **MVP** — builds loadout + governance; 30 tests |
| `gargantua-studio` | **Studio** frontend (Agent + Skill Designer, Loadout & Governance sections, catalog views) | React 18 / Vite 5 / React Flow / Monaco / Zustand / React Router (HashRouter) | **MVP** — wired to backend, offline fallback; 8 tests |
| `gargantua-compose` | **Local vertical slice** (Docker Compose) wiring the agent-creation flow; `start.bat`/`stop.bat` | Compose v2 | **Done** — ports in 18xxx/19xxx (off Cave's range); authored + statically verified |
| `gargantua-gateway` | Agent Gateway (Intent/Capability/Version routing) | TBD | **Not built** — Phase 4; decision: *evaluate `agentgateway`* first |
| `gargantua-operator` | Kubernetes Operator + CRDs | Java Operator SDK (planned) | **Not built** — Phase 5 |

## 3. The shared domain model — `agent-core`

The single most important structural decision after the multi-repo split:
**all JVM components share ONE canonical domain model**, not local mirrors.

- Coordinates: `io.github.giskardb:agent-core` — released versions are **published to
  Maven Central** (`1.2.20` is the latest released; the working tree is now on
  **`1.3.0-SNAPSHOT`** locally after the Agent Loadout change — install it to `.m2`, see
  below). Released versions mean sibling repos and Docker builds resolve it normally; you
  do **not** need the Runtime checked out to build the Control Plane or Studio backend.
  A SNAPSHOT, however, is local-only until tagged/released — see roadmap §7.2.
- It is a **module of `gargantua-parent`** (which extends `spring-boot-starter-parent
  4.1.0`). Pure domain: **no Spring, no Jackson annotations.** YAML↔record binding
  lives in the Runtime's `agent-bundle/ManifestParser` and in the Studio backend's
  `ManifestBuilder` — never in the model.
- To refresh the local `.m2` after changing it, from the Runtime repo:
  `mvn -N install && mvn -pl agent-core install -DskipTests`.
- Key types: `WorkloadManifest` (apiVersion/kind/metadata/spec; `CURRENT_API_VERSION
  = "gargantua.ai/v1"`), `WorkloadMetadata` (name/version/description/owner/labels +
  **`governance`**, implements `Governed`), `AgentSpec` (runtime, capabilities, model,
  mcpServers, memoryLayers, defaultSkill, guardrails, allowedRoles, **`loadout`**),
  `Loadout`/`KnowledgeRef`/`ResourceRef` (`core.workload`),
  `GovernanceEnvelope`/`Visibility`/`Governed` (`core.governance`), `Capability`
  (name, description, version, inputSchema, outputSchema, **`implementedBy`**,
  `Set<String> tags`), `SkillMeta`/`SkillCard`, `RagConfig`, `McpServerSpec`,
  `MemoryLayer`, A2A (`AgentCard`), rag ports (`EmbeddingPort`, `VectorStorePort`).

See [gargantua-domain-model.md](gargantua-domain-model.md) for the full model.

## 4. How to run the whole thing locally (the `gargantua-compose` slice)

This is the answer to *"does Studio emit something the rest of the platform accepts?"*

```
browser ─▶ studio            nginx: serves the SPA, proxies /api same-origin
              └─▶ studio-backend   BFF: builds gargantua.ai/v1 manifests
                     └─▶ control-plane   Registry + Catalog
                            ├─▶ postgres   control-plane + studio state
                            └─▶ minio      bundle manifest blobs
```

```bash
cd gargantua-compose
cp .env.example .env                    # optional; defaults work
docker compose up -d --build --wait     # Windows: start.bat  (stop.bat / stop.bat clean)
./smoke/smoke.sh                        # build → publish → catalog round-trip
```

Studio UI at `http://localhost:18081` (or `http://<CAVE_HOST>:18081`). The SPA talks
to the backend **same-origin via nginx**, so it works on localhost or a LAN IP
without CORS or a per-host rebuild. Published ports live in a high range (**18080** CP,
**18090** backend, **18081** Studio, **19000/19001** MinIO) so they never clash with
Cave's own services; override any of them in `.env`.

**Scope of the slice:** creating an agent needs only Studio → backend → Control
Plane. *Running* an agent (the Runtime, with Mongo/Redis) is deliberately out of
this slice; it comes later.

**Caveats:** `docker` is **not installed in Cave's IDE env** — the slice was authored
and statically verified (compose YAML valid, frontend build clean, smoke bash ok) but
not executed here; run it where you have a Docker daemon. Ports already default to the
18xxx/19xxx range so they don't clash with Cave's own services (including its `minio` on
9000/9001). Remember the `agent-core 1.3.0-SNAPSHOT` release caveat (§7.2) — the Docker
builds need it on Maven Central.

**Build toolchain note (2026-08):** the Cave IDE box was re-provisioned mid-session
**without Java/Maven**. If `mvn`/`java` are missing, install a portable toolchain (this is
what the recent JVM work was built/verified with):
```bash
# Temurin JDK 25 + Maven 3.9.9 under ~/tools
curl -fsSL -o ~/tools/jdk25.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
curl -fsSL -o ~/tools/maven.tar.gz "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
# extract both under ~/tools, then:
export JAVA_HOME=~/tools/jdk-25.0.4.1+1
export PATH=$JAVA_HOME/bin:~/tools/apache-maven-3.9.9/bin:$PATH
```

## 5. The agent-creation flow (contracts, end to end)

| Hop | Call | Notes |
|---|---|---|
| SPA → backend | `POST /api/studio/manifest/build` (body: `AgentDraft` JSON) | returns `{valid, yaml, errors}`; stringly-typed form draft → `agent-core` records → canonical YAML |
| SPA → backend | `POST /api/studio/publish` (body: `AgentDraft`) | builds then relays to the Control Plane; 201 created / 409 exists / 400 invalid |
| backend → CP | `POST /api/v1/registry/bundles` (body: `{"manifest": "<yaml>"}`) | `PublishRequest`; CP derives name/version/kind/capabilities from the manifest |
| CP internal | `RegistryService.publish` → `CatalogService.indexBundle` | publishing **auto-indexes** the Catalog — discovery stays in step |
| SPA → backend → CP | `GET /api/studio/{workloads,capabilities,policies,deployments}` | read-through to `/api/v1/{registry/bundles, catalog/capabilities, policies, deployments}` |

Ports: Control Plane **8080**, Studio backend **8090**, Studio (nginx) **8081**,
MinIO **9000/9001**. Postgres is unpublished (internal only). In dev the JVM services
default to embedded H2 + filesystem blobs; the compose slice activates the `postgres`
(and `minio`, for the CP) Spring profiles.

## 6. Frozen decisions (do not relitigate without a reason)

Full rationale in memory and in the linked docs; the load-bearing ones:

- **Docker Compose is a hard requirement.** Trying the whole platform must never
  require Kubernetes. (See `ai-operating-system.md`, and §4 above.)
- **The manifest keeps the `apiVersion/kind/metadata/spec` shape.** It is the agent's
  own declarative spec (Control-Plane-native, *not* consumed by kubectl); it only
  *borrows* the k8s envelope so the future Operator can transcribe it into a CRD
  almost mechanically. Confirmed with the user — not a k8s manifest. See
  [agent-manifest.md](agent-manifest.md).
- **Capability ≠ Skill, kept decoupled in the model.** A **Skill** is authored as a
  `SKILL.md` (Anthropic Agent Skills format: YAML frontmatter + markdown system
  prompt); a **Capability** is the external Catalog contract. Studio bridges them at
  the **UX layer**: the Skill Designer builds a canonical `SKILL.md`, and "Assign to
  agent" upserts a Capability keyed by `implementedBy = skill.name`. The domain
  decoupling stays (Runtime routing/Catalog depend on it). See
  [skills-and-routing.md](skills-and-routing.md).
- **Shared `agent-core` jar, not mirrored types.** (§3.)
- **Stack (frozen):** Java 25 LTS + Boot 4.1 + Maven everywhere; Postgres
  (control-plane state), MongoDB (runtime state), Redis (cache/rate-limit), MinIO/S3
  (bundle blobs); A2A + MCP as interop; React Flow / Monaco / Zustand for Studio.
  Vector search stays pluggable (pgvector / Qdrant / in-memory behind `EmbeddingPort`).
- **Gateway: evaluate `agentgateway` before building** (Rust; MCP/A2A/LLM proxy, CEL
  policy, RBAC, rate-limit, OTel). Gargantua keeps the **Intent Router** as its value
  *in front of* the gateway; delegates protocol proxy/security/telemetry. Rust/Go is
  fine — don't rewrite solved networking in Java for uniformity.

## 7. Roadmap — decided improvements, in order

Derived from a critical evaluation of an external agent-infra research doc; the full
ADOPT/ADAPT/EVALUATE/REJECT analysis, grounded in the codebase, is in
[13-open-source-patterns.md](13-open-source-patterns.md). The **decision lens** (adopt
its most useful contribution): for any idea ask — is it a protocol / domain object /
control-plane cap / runtime cap / context cap / *impl detail* / *external infra*? If
the last two, adopt a standard or component instead of reinventing it.

1. ✅ **Docker Compose vertical slice** — done (`gargantua-compose`).
2. ✅ **Agent Loadout (ADOPT, P0)** — done. `spec.loadout` (knowledge / memoryScopes /
   skills / resources) added to `agent-core` (`Loadout`, `KnowledgeRef`, `ResourceRef`;
   `AgentSpec.loadout`, additive), parsed by the Runtime's `ManifestParser`, emitted by the
   Studio backend's `ManifestBuilder`, and editable in the Studio Agent Designer (knowledge
   bases first-class, with `maxResults`/`minScore` retrieval overrides). Bumped the shared
   model to **`1.3.0-SNAPSHOT`**. Reported as an unapplied field for now — runtime
   provisioning of a loadout is not implemented (knowledge is still wired per skill via
   `SKILL.md metadata.knowledge-base`). **Caveat:** `1.3.0-SNAPSHOT` lives only in the local
   `.m2`; the `gargantua-compose` Docker builds resolve `agent-core` from Maven Central, so
   they need `1.3.0` **released** (tag `v1.3.0` → the release-maven-central workflow) before
   they will build again — or switch the JVM service Dockerfiles to build `agent-core` from
   source.
3. ✅ **Governance envelope (ADAPT, P1)** — first increment done. `core.governance`:
   `GovernanceEnvelope` (tenant / visibility[PRIVATE·INTERNAL·PUBLIC] / status / access /
   createdAt / updatedAt) + a `Governed` interface (a **shared trait**, *not* a
   `ContextAsset` supertype). Attached additively to `WorkloadMetadata` (`metadata.governance`)
   — parsed by the Runtime, emitted by the Studio backend, edited in the Studio Agent
   Designer. Timestamps are Control-Plane-assigned (never in a bundle). Reported, not yet
   enforced (Policy Manager owns enforcement). **Follow-on:** apply the same `Governed` trait
   to `Capability` / `SkillMeta` / memory / knowledge (cheap now the trait exists).
4. **◐ IN PROGRESS — Execution event model + Trace (ADOPT, P1).** Domain foundation laid
   in `agent-core` `core.execution`: `ExecutionEvent` (OTel-friendly: typed + attributes
   map), `ExecutionEventType`, `ExecutionTrace` (ordered timeline), and the
   `ExecutionEventPublisher` **port** with a `noOp()` default — the single seam that keeps
   the bus choice (NATS/Kafka/none) open. Distinct from `AuditEvent` (one post-hoc summary
   row) — this is the fine-grained live stream. **Follow-on:** emit events from the engine
   pipeline (routing/guardrail/tool/LLM/memory), an in-memory + OTel adapter, a Runtime
   trace API, and wire the Studio trace screen (already mocked) to it.
5. **Runtime supervisor + execution budgets (ADOPT, P1)** — timeout/loop/thrash/cost
   caps (partial today via `TokenBudgetManager`/cost tracking).
6. **Agent lifecycle + evaluation gate (ADAPT, P1)** — DRAFT→…→ACTIVE; no publish
   without passing evaluation.
7. **Phase 4:** agentgateway evaluation spike (§6). **Phase 5:** Operator + CRDs + GitOps.

Deferred/optional (keep the model open, don't build yet): Experience→Skill (P2),
progressive disclosure + per-source context budget, sandbox provider abstraction,
memory versioning/branching + graph memory (research).

**Explicitly resisted:** ContextAsset type-unification; adopting NATS now (hide behind
an `EventPublisher` if events land — NATS-vs-Kafka stays open); a "modular monolith"
(contradicts the distributed multi-repo architecture — decision stands); generating all
13 arch docs up front.

## 8. Known gaps & honest caveats

- **Not everything in the manifest is enforced yet** — see the Runtime's
  `ManifestProperties.unappliedFields()` and `project-handoff.md` §4. In particular
  `spec.loadout` and `metadata.governance` are **parsed and reported, not enforced**:
  loadout provisioning isn't implemented (knowledge is wired per skill via
  `SKILL.md metadata.knowledge-base`), and governance visibility/access await the Policy Manager.
- **Governance is only on the agent so far** — `Governed` is implemented by
  `WorkloadMetadata`; applying it to `Capability`/`SkillMeta`/memory/knowledge is a
  planned follow-on (see §7.3).
- **Execution events are model-only so far** — `core.execution` defines the types and the
  `ExecutionEventPublisher` port, but nothing emits or consumes them yet (see §7.4).
- **Bundle *signature* verification** is not implemented (SHA-256 checksum is).
- The Control Plane is an **MVP**: Registry/Catalog/Policy/Deployment exist; auth is
  permit-all in dev (OIDC/Keycloak profile stubbed), no RBAC enforcement yet.
- The Studio backend security is **permit-all in dev**, JWT under the `oidc` profile.
- Docker is unavailable in the Cave IDE env (§4); Java/Maven may be missing after a
  re-provision — restore a portable toolchain (§4 build note).

## 9. Where to go next in the docs

- Runtime internals & invariants → [`../project-handoff.md`](../project-handoff.md)
- Vision → [`ai-operating-system.md`](ai-operating-system.md)
- Binding decisions (ADR-001..006) → [`runtime-decisions.md`](runtime-decisions.md)
- Domain model → [`gargantua-domain-model.md`](gargantua-domain-model.md)
- Manifest schema & enforcement → [`agent-manifest.md`](agent-manifest.md)
- Skills & routing → [`skills-and-routing.md`](skills-and-routing.md)
- OSS pattern evaluation & the roadmap rationale → [`13-open-source-patterns.md`](13-open-source-patterns.md)
