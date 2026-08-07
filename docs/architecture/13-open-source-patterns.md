# Open-Source Pattern Analysis & Architecture Research

**Status:** research evaluation — decisions here are *directional*, not frozen. Each
pattern is judged against what Gargantua already is, not adopted on faith.
**Audience:** contributors and maintainers.
**Origin:** an external research document (2026-08) surveying agent-infra projects
(TencentDB Agent Memory, OpenViking, Acontext, Letta Agent File, Agentgateway, A2A,
Memoria, AgenticMemory, OpenAkita). This file is Gargantua's *answer* to it.

> The most useful thing the source document contributed is not a component list — it's
> a decision lens. Before turning any idea into Gargantua code, ask:
>
> **Is this (1) a protocol, (2) a domain object, (3) a control-plane capability,
> (4) a runtime capability, (5) a context capability, (6) an implementation detail,
> or (7) external infrastructure?** If the honest answer is (6) or (7), do **not**
> reinvent it as a proprietary Gargantua feature — adopt a standard or an existing
> component. Gargantua's value is the Domain Model + Control Plane + Context Plane +
> Agent Lifecycle + Governance + Runtime Contract, not re-implemented protocols/infra.

---

## A. Already in Gargantua (the research validates existing choices)

Much of the source document restates decisions already made and shipped. Recording
them so we don't "adopt" what we already have:

| Concept | Where it already lives |
|---|---|
| Plane separation (Studio → Control Plane → Runtime → Catalog → Gateway) | ADR-001/004 (`runtime-decisions.md`), 5-repo split |
| Bundle is data, never code — signable, immutable, promotable | ADR-003; `core.bundle.BundleDescriptor` (`checksum`, `signature`, `runtimeImage`) |
| Control Plane defines desired state / Runtime applies it | ADR-004 |
| Secrets by reference, never in the bundle | ADR-005; `core.secret.SecretPlaceholders`, `McpAuth` (`${secrets.*}`) |
| A2A as the interop/discovery standard | `core.a2a.AgentCard`, `A2AClient`, `A2ATask` |
| MCP as the tool protocol, distinct from Capability and Skill | `core.mcp.McpServerSpec`; `core.capability.Capability`; `core.skill.SkillCard` |
| Skill = structured artifact (Anthropic Agent Skills format) | `SKILL.md` → `SkillMeta`/`SkillCard`; Studio Skill Designer emits it |
| Retrieval as a pluggable abstraction (not "RAG = a database") | `core.rag.EmbeddingPort`, `VectorStorePort`, `RagConfig`; pgvector/Qdrant/in-memory adapters |
| Capability model as a first-class routing unit | `core.capability.Capability` (`implementedBy`, schemas, tags) |
| Cost tracking + token budgets | `core.cost.CostTrackingEvent`; `core.orchestrator.TokenBudgetManager`, `BudgetAllocation` |
| Guardrails (input/output) | `core.guardrail.*` |
| Studio produces a compatible artifact | Agent Designer → `gargantua.ai/v1` manifest; Skill Designer → `SKILL.md`, both via the shared `agent-core` model |

Corrections to the source document: it says **Java 21** — Gargantua is on **Java 25**.
It says **"Modular Monolith, don't start with microservices"** (§74) while its own
§77 lists **11 repos**; Gargantua already chose a distributed multi-repo layout
(ADR-001) and explicitly rejected a single-deployable modular monolith. That decision
stands.

---

## B. Pattern evaluations

Template per the source doc §82: *Source · Problem · How · Gargantua Fit · Decision ·
Impact · Alternative*.

### B1. Agent Loadout — **ADOPT (P0, cheap, highest leverage)**
- **Source:** TencentDB Agent Memory.
- **Problem:** an agent should be *equipped* with a specific subset of memory,
  knowledge, skills and resources — not given access to everything.
- **How:** a declarative `loadout` block binding named assets to the agent.
- **Gargantua fit:** today `AgentSpec` binds `capabilities / mcpServers / memoryLayers /
  model / defaultSkill / guardrails` but **cannot** bind specific knowledge bases or
  resources. This is a real gap and a clean additive change to the manifest.
- **Decision:** ADOPT. Add `spec.loadout` (memory / knowledge / skills / resources) to
  the manifest and `AgentSpec`, surfaced in the Studio Agent Designer.
- **Impact:** new fields in `agent-core` (backward-compatible, all optional), manifest
  reader/writer, Studio form. Skills already bind via `implementedBy`; loadout
  generalises that to the other asset kinds.
- **Alternative:** keep ad-hoc per-kind fields (rejected — doesn't scale to knowledge/resources).

### B2. Agentgateway — **EVALUATE before building a proprietary Gateway (P0 strategic)**
- **Source:** `github.com/agentgateway/agentgateway` (AI-native proxy: MCP/A2A/LLM
  gateway, CEL policy, RBAC, rate-limit, TLS, OpenTelemetry, tool federation).
- **Problem:** the Gateway (Phase 4) is **not yet built** — the ideal moment to decide
  build-vs-reuse.
- **Gargantua fit:** protocol proxy + connectivity security + rate-limit + telemetry are
  question (7) *external infrastructure*, not Gargantua's differentiation. Reusing a
  mature component beats hand-rolling a proxy in Java for stack uniformity.
- **Decision:** EVALUATE for direct integration (see §E). Gargantua keeps the **Intent
  Router** (user intent → capability → candidate agents → policy → selected agent) as
  its own value; agentgateway handles the wire.
- **Impact:** none until the spike. Keep the Gateway repo a thin placeholder.
- **Alternative:** proprietary Java gateway (higher cost, weaker MCP/A2A maturity).

### B3. Governance envelope on assets — **ADAPT (P1) — take the envelope, reject the type-collapse**
- **Source:** TencentDB Memory Asset governance; A2A curated registry.
- **Problem:** every asset/agent needs `owner / tenant / version / status / visibility /
  ACL / timestamps` for a real multi-tenant, community-grade platform.
- **Gargantua fit:** genuinely missing and valuable for the OSS/governance story.
- **Decision:** ADAPT. Introduce a shared **governance envelope** (a trait/record) applied
  to agents, skills, capabilities, memory and knowledge. **Do NOT** collapse
  Memory/Knowledge/Skill/Capability into one `ContextAsset` supertype (source doc §59) —
  Gargantua deliberately keeps these domain concepts distinct (Capability = external
  contract; Skill = internal behavior; Memory ≠ Knowledge). Take the metadata, keep the
  types.
- **Impact:** additive metadata; Control Plane persistence and Studio filters.
- **Alternative:** unified `Asset` type (rejected — muddies clean domain boundaries).

### B4. Execution Event Model + Trace — **ADOPT (P1)**
- **Source:** OpenHands / general agentic runtimes.
- **Problem:** an execution should be an observable sequence of events (intent, plan,
  skill selected, context retrieved, model call, tool call, policy decision, response).
- **Gargantua fit:** today there's audit (`MongoAuditStore`) and cost events, but no
  structured execution-event stream. It's the backbone of the Studio "Execution Trace"
  screen (already mocked) and, later, the Experience→Skill pipeline.
- **Decision:** ADOPT. Define an `ExecutionEvent` model + emit over OpenTelemetry.
- **Impact:** Runtime event emission, Studio trace consumer. Not event-sourcing — the DB
  stays source of truth (source doc §76 agrees).
- **Alternative:** rely on OTel spans only (loses domain-level semantics).

### B5. Runtime Supervisor + execution budget — **ADOPT (P1)**
- **Source:** OpenAkita / OpenHands.
- **Problem:** runaway executions (loops, tool-thrashing, cost/time blowout).
- **Gargantua fit:** budgets/cost partially exist (`TokenBudgetManager`, cost tracking);
  formalise as a supervisor enforcing timeout / token / cost / iteration / tool-call caps
  as an **execution policy**, not model config.
- **Decision:** ADOPT.
- **Impact:** Runtime supervisor component; budget fields on the manifest/deployment.

### B6. Agent Lifecycle + evaluation gate — **ADAPT (P1)**
- **Source:** the document's lifecycle (§37–38).
- **Problem:** "production ready" should be a gated state, not a vibe.
- **Gargantua fit:** environments + promotion exist in the Control Plane; add an explicit
  state machine (DRAFT → VALIDATED → EVALUATED → APPROVED → PUBLISHED → DEPLOYED → ACTIVE
  → SUSPENDED/DEPRECATED/RETIRED) and the rule "no publish without an associated
  evaluation".
- **Decision:** ADAPT (align states to existing promotion semantics).

### B7. Experience → Skill pipeline — **ADAPT (P2, future)**
- **Source:** Acontext ("skill is memory").
- **Problem:** controlled self-improvement without letting an agent mutate production behavior.
- **Gargantua fit:** produces a `SkillCandidate` → human review → the *same* `SKILL.md`
  format the Skill Designer already builds. Clean fit, but depends on B4 (events).
- **Decision:** ADAPT later. Keep the domain open now; build after events + evaluation exist.

### B8. Progressive Disclosure + Context Budget per source — **ADAPT (P1/P2)**
- **Source:** OpenViking / Tencent / Acontext.
- **Problem:** context-window explosion; dumping all memory/knowledge into the prompt.
- **Gargantua fit:** RAG exists; per-source token budgets and summary→detail retrieval do
  not. Make Context Budget a **Kernel** capability (not per-provider).
- **Decision:** ADAPT when the Context Plane is built; specify now, implement later.

### B9. Memory versioning/branching & Graph memory — **RESEARCH / OPTIONAL PROVIDER (P2/P3)**
- **Source:** Memoria (git-for-memory); AgenticMemory (cognitive graph).
- **Gargantua fit:** don't build now. Keep the domain open behind a `MemoryProvider`
  abstraction — already Gargantua's pattern (cf. `EmbeddingPort`/`VectorStorePort`) — so
  `VectorMemoryProvider` / `GraphMemoryProvider` / `HybridMemoryProvider` remain possible.
- **Decision:** RESEARCH; ensure the model doesn't *preclude* `MemoryVersion`/`MemoryBranch`.

### B10. L0–L4 memory hierarchy & Context Filesystem — **ADAPT PATTERN ONLY (P2/P3)**
- **Source:** Tencent L0–L3; OpenViking `context://` filesystem.
- **Decision:** adopt the *pattern* (raw evidence → facts → context → stable knowledge →
  experience) if/when memory synthesis is built. Don't implement the filesystem paradigm
  or the exact hierarchy now.

### B11. Sandbox provider — **ADOPT AS ABSTRACTION (P1/P2)**
- **Source:** OpenHands / OpenAkita.
- **Gargantua fit:** aligns with ADR-001 process isolation. Define `SandboxProvider`
  (Docker / K8s Pod / gVisor / Firecracker / local-restricted) as an abstraction, not a
  hard dependency.
- **Decision:** ADOPT the abstraction; pick implementations later.

### Explicitly resisted
- **`ContextAsset` type-unification** (§59) — take governance metadata, keep distinct types.
- **NATS/JetStream now** (§75) — real decision, but unnecessary for the current vertical
  slice. If events are added, hide them behind an `EventPublisher` so NATS-vs-Kafka stays
  open (source doc §85 agrees).
- **Generating all 13 architecture docs up front** (§81) — scope creep. This single
  consolidated file + updates to the existing domain-model doc is the right dose.
- **The "AI Operating System" framing as a build order** — excellent north star, poor
  sprint plan. Keep shipping vertical slices; the framing is the *why*, not the *next PR*.

---

## C. Decision matrix

| Pattern | Decision | Priority | Status |
|---|---|---|---|
| A2A Agent Card | ADOPT | P0 | **done** (`core.a2a`) |
| MCP as tool protocol | ADOPT | P0 | **done** (`core.mcp`) |
| Pluggable retrieval | ADOPT | P0 | **done** (`core.rag`) |
| Code-free signable bundle | ADOPT | P0 | **done** (ADR-003) |
| Skill as `SKILL.md` artifact | ADOPT | P0 | **done** (Skill Designer) |
| Agent Loadout | ADOPT | P0 | **todo** |
| Agentgateway | EVALUATE | P0 | **spike (Phase 4)** |
| Governance envelope | ADAPT | P1 | todo |
| Execution event model + trace | ADOPT | P1 | todo |
| Runtime supervisor + budgets | ADOPT | P1 | partial (cost/budget) |
| Agent lifecycle + eval gate | ADAPT | P1 | partial (promotion) |
| Progressive disclosure / context budget | ADAPT | P1/P2 | todo |
| Experience → Skill | ADAPT | P2 | future |
| Sandbox provider | ADOPT (abstraction) | P1/P2 | todo |
| Memory versioning/branching | RESEARCH | P2 | keep model open |
| Graph memory provider | OPTIONAL | P2 | keep model open |
| L0–L4 / context filesystem | ADAPT pattern | P2/P3 | future |
| Kubernetes CRD/Operator | FUTURE | P3 | Phase 5 |

---

## D. Repository layout note

The source document's §77 layout (model, sdk, runtime, context, memory, skill, policy,
control-plane, gateway, studio, operator) is close to Gargantua's actual multi-repo
split. Context/memory/skill are currently modules inside the Runtime + `agent-core`
rather than separate repos; promoting them to their own repos is a *future* refactor, not
a prerequisite. **Docker Compose remains a hard requirement**: the whole platform must be
runnable locally without Kubernetes (K8s/Operator is additive, Phase 5).

---

## E. Gateway direction (decided: evaluate agentgateway)

Before writing any Phase-4 Gateway code, run a spike evaluating **agentgateway** as the
reusable Execution-Plane gateway. Gargantua contributes Agent Catalog, policy
configuration, routing intent and capability metadata; agentgateway handles protocol
proxy (MCP/A2A/LLM), auth, RBAC, rate-limiting, TLS and telemetry. Gargantua keeps the
**Intent Router** (capability→agent selection) as its own capability *in front of* the
gateway.

Evaluate on: API & configurability · extensibility · licensing · operational model ·
Kubernetes integration · performance · security · A2A/MCP maturity · how cleanly the
Catalog/Policy feed its config. A Rust/Go implementation is acceptable — preferring
"Java everywhere" over reusing a solved networking/security component would be optimising
the wrong thing.

---

## F. Recommended sequencing

1. **Vertical slice via Docker Compose** (Studio → studio-backend → Control Plane +
   Postgres/MinIO) — this *is* the "does Studio emit something compatible with the rest?"
   test the source document poses as its Final Thesis (§84).
2. **Agent Loadout** (B1) — cheapest high-value domain addition; validate it in the slice.
3. **Governance envelope** (B3) and **Execution events + trace** (B4).
4. **Phase 4:** agentgateway evaluation spike (§E) before any gateway code.

---

## References
- TencentDB Agent Memory — Memory Assets, Loadout, L0–L3, Skill lifecycle, Knowledge/CodeGraph.
- OpenViking — Context Database, filesystem paradigm, hierarchical context, progressive loading.
- Acontext — skill-as-memory, experience→skill, file-based progressive disclosure.
- Letta Agent File — portable serialization of stateful agents (state vs configuration).
- Agentgateway — MCP/A2A/LLM gateway, policy, security, observability.
- A2A — Agent Card, discovery, curated registry, capability model.
- Memoria — versioned memory (snapshots/branches/rollback).
- AgenticMemory — cognitive graph memory.
- OpenAkita — multi-agent, plugin lifecycle, resource budgets, sandbox, graph memory.
