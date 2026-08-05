# Project Handoff

Orientation document for anyone picking up this repository — a human joining the
project or an agent starting a fresh session. It answers *where things are and
why*, and points at the documents that answer *how*.

Read this first, then follow the links. Nothing here is duplicated from the other
docs on purpose: this is the map, not the territory.

---

## 1. What this repository is

Gargantua is a Java framework for building and running AI agents, evolving into
the **Runtime** layer of a distributed AI Operating System. The full vision is in
[architecture/ai-operating-system.md](architecture/ai-operating-system.md).

**Scope boundary — this matters.** This repository implements the Runtime and the
execution-side half of the Kernel: Context Manager, Memory Manager, Skill Engine,
MCP Manager, policy enforcement, A2A layer.

Studio, Control Plane (Registry, Catalog, Deployment Manager), Gateway and the
Kubernetes operator live in **separate repositories** and are not built here. The
Control Plane *defines* desired state and policy; the Runtime *applies* it. When
something in this repo needs Control Plane data, it reaches it over an API or
receives it in the bundle — it never becomes the source of truth for deployment
or catalog state. See ADR-004.

## 2. The two delivery modes

This is the single most important thing to understand before changing anything,
because most design tension in the codebase comes from supporting both.

| | **Library mode** | **Runtime mode** |
|---|---|---|
| Who builds the agent | A developer, in Java | Studio, declaratively |
| Where tools come from | `@AgentTool` methods, compiled | MCP servers declared in the manifest |
| Artifact | The developer's own application | A signed `.gbundle` |
| Docker image | Custom, built by the team | Generic `gargantua-runtime` |

Both run through the **same execution pipeline**. Only the tool source differs.

Library mode is not legacy and is not going away — it is the reason the framework
is approachable, it is what the Maven archetype and existing consumers depend on,
and it is the escape hatch for teams that need bespoke Java tooling. See
[delivery-modes.md](delivery-modes.md) and ADR-002.

## 3. Module map

Nine Maven modules. The dependency direction is always *inward*: adapters depend
on ports, never the reverse.

| Module | Spring? | What it holds |
|---|---|---|
| `agent-core` | **No** | Domain model and **ports** (interfaces). 20 packages: orchestrator, tool, memory, guardrail, workload, skill, rag, mcp, hitl, flow, a2a, session, security, secret, audit, llm, cost, capability, bundle, exception. Depends on nothing framework-specific. |
| `agent-engine` | Yes | The **adapters** and Spring auto-configuration. This is where almost all behaviour lives: `ToolRegistry`, `GuardrailPipeline`, `DefaultOrchestratorEngine`, the web controllers. |
| `agent-memory-sdk` | Partly | Memory and vector store implementations (in-memory, Mongo, Redis, embedded). |
| `agent-mcp-client` | **No** | Consumes external MCP servers. One `McpToolProvider` per declared server. |
| `agent-bundle` | **No** | `.gbundle` format: `ManifestParser`, `BundleLoader`, checksum, zip-slip protection. |
| `agent-runtime` | Yes | The standalone runtime: CLI (`run` / `validate`), loads a bundle, maps the manifest onto `agent.*` properties. |
| `agent-mcp-server` | Yes | The *other* direction — exposing this agent's tools **as** an MCP server. |
| `agent-skill-linter-maven-plugin` | — | Build-time validation of `SKILL.md` files. |
| `agent-archetype` | — | `mvn archetype:generate` starting point. |

If you are looking for where a behaviour is implemented, it is almost certainly
in `agent-engine/src/main/java/ai/gargantua/autoconfigure/`.

## 4. Invariants worth knowing before you edit

These are the things that are easy to break because nothing obvious warns you.

**Tool discovery is split in two.** `@AgentTool` methods are scanned by
`ToolRegistry` itself. `ToolProvider` is for **external** sources only (MCP). The
annotation-driven gates — RBAC, HITL approval, cache, retry — live in the registry
because they read Java annotations no external source has. An earlier attempt to
extract an `AnnotationToolProvider` was reverted for exactly this reason; see
ADR-006.

**`ToolDefinition.approvalShowParameters` must not be cloned.** A record compares
array components by *identity*. Callers rely on sharing one empty array so that
two otherwise-equal descriptors compare equal. Cloning it in the compact
constructor silently breaks an equality test.

**Unresolved secret placeholders are left verbatim, deliberately.**
`${secrets.NAME}` that resolves to nothing stays as the literal string. A
misconfiguration must surface as a visible `${secrets.x}`, never as a silently
blank credential. See ADR-005.

**Not everything in the manifest is enforced yet.**
`ManifestProperties.unappliedFields()` reports which manifest fields — memory
layers, allowed roles, minimum version — are parsed but **not** acted on. This is
honest by design: read it before assuming a manifest field does something.

**One runtime process hosts exactly one agent.** Isolation between agents is
process isolation. This is why `AgentProperties`, `SkillRegistry` and
`ToolRegistry` can stay singletons. See ADR-001.

**Bundles contain no executable code.** Signing is only meaningful over
declarative data. Teams needing custom Java tools build a custom image in library
mode instead. See ADR-003.

## 5. Building and verifying

```bash
mvn -q clean install -DskipTests    # build all 9 modules
mvn test                            # 823 tests
```

The examples live in a **separate repository**,
[GiskardB/gargantua-examples](https://github.com/GiskardB/gargantua-examples) —
22 projects, ~220 tests. They are the framework's real integration suite: several
regressions in this codebase were caught by an example and by nothing else. If
you change public behaviour, build the examples against your branch before
believing it works.

The `embedded` Spring profile excludes the Mongo and Redis auto-configurations,
which takes boot from ~41s to ~6.5s. Use it for anything iterative.

`AgentProperties` exposes 149 bindable `agent.*` paths. If you are unsure whether
a property in a document actually exists, reflect over that class rather than
trusting the prose — several documented properties turned out to bind to nothing.

## 6. Where the project stands

**Phase 1 (Runtime Foundation) is implemented**: workload and capability domain
model, agent manifest (`gargantua.ai/v1`, Kubernetes-shaped), bundle format and
loader, standalone runtime, MCP client, secret referencing, composable tool
providers.

**Phase 2 (Control Plane)** — registry, catalog, deployment, API — has not
started and belongs in a different repository.

Still open *here*:

- bundle **signature** verification (the SHA-256 checksum exists; signatures do not)
- workload-level enforcement of RBAC and memory layers (see §4)
- `CatalogRegistrar`, once a Catalog API exists

Branching: **`develop` is the working branch** and is the most up-to-date. `main`
holds released state. Development continues on `develop`.

## 7. Reading order

1. [`../README.md`](../README.md) — what the framework does, and a 60-second start
2. [`getting-started.md`](getting-started.md) — first agent, end to end
3. [`delivery-modes.md`](delivery-modes.md) — library vs runtime, in depth
4. [`architecture/ai-operating-system.md`](architecture/ai-operating-system.md) — the wider vision
5. [`architecture/runtime-decisions.md`](architecture/runtime-decisions.md) — ADR-001..006, the binding decisions
6. [`architecture/agent-manifest.md`](architecture/agent-manifest.md) — manifest schema and what is actually enforced
7. [`extending.md`](extending.md) — every port and its default adapter

Feature-specific: [`tools-and-annotations.md`](tools-and-annotations.md),
[`skills-and-routing.md`](skills-and-routing.md),
[`memory-system.md`](memory-system.md), [`guardrails.md`](guardrails.md),
[`llm-configuration.md`](llm-configuration.md), [`deployment.md`](deployment.md),
[`api-reference.md`](api-reference.md).

## 8. A note on the decision log

`architecture/runtime-decisions.md` is **append-only**. Superseding a decision
means adding a new ADR that says so, not editing the old one. The one exception
made so far was correcting a factual description of code that had been deleted —
the decision itself was left standing.
