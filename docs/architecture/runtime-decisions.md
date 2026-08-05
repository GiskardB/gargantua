# Runtime Architecture Decisions

Decision log for the Gargantua **Runtime** repository, scoped to the AI Operating
System vision in [ai-operating-system.md](ai-operating-system.md).

Each entry records what was decided, why, and what it rules out. Decisions are
append-only: superseding one means adding a new entry, not editing an old one.

---

## ADR-001 — A runtime instance hosts exactly one agent

**Status:** Accepted

**Decision.** One runtime process executes one workload. Multiplexing across agents is
the responsibility of the Scheduler and the orchestrator above it (one Pod per agent).

**Rationale.** Isolation between agents becomes process isolation, which is real,
rather than classloader isolation, which is not. It also removes the need for
per-workload scoping inside the JVM: `AgentProperties`, `SkillRegistry` and
`ToolRegistry` remain singletons exactly as they are today, so no existing wiring has
to change.

**Consequences.** Horizontal scale is per-agent and maps directly onto a Kubernetes
Deployment. A runtime hosting many small agents costs one JVM each — acceptable, and
revisitable later without changing the bundle contract.

---

## ADR-002 — Library mode is retained as a first-class delivery mode

**Status:** Accepted

**Decision.** Gargantua continues to ship as an embeddable Spring Boot library. A
developer can depend on `agent-engine`, write `@AgentTool` methods, and run an agent
inside their own application exactly as before.

**Rationale.** The original developer experience is the reason the framework is
approachable, and it is what existing consumers and the Maven archetype depend on. It
is also the escape hatch for bespoke Java tooling under ADR-003.

**Consequences.** Two delivery modes must be supported by one engine:

| | Library mode | Runtime mode |
|---|---|---|
| Author | Developer, in Java | Studio, declaratively |
| Tools from | Compiled `@AgentTool` methods | MCP servers in the manifest |
| Artifact | The developer's application | Signed bundle |

Both flow through the same execution pipeline; only the tool source differs.

---

## ADR-003 — Bundles are declarative and contain no executable code

**Status:** Accepted

**Decision.** An agent bundle carries a manifest, prompts, skills, MCP server
declarations and policies. It never carries compiled classes. Tools in runtime mode come
exclusively from MCP servers.

**Rationale.** The bundle is specified as immutable, versioned and signed. Signing is
only meaningful over declarative data: certifying the origin of arbitrary JARs that then
execute in-process with full privileges establishes almost no trust boundary, and makes
any future marketplace a supply-chain liability. A code-free bundle can be verified,
inspected and safely distributed.

**Alternatives rejected.** Per-bundle child classloaders were considered and rejected:
they buy dynamic Java tools at the cost of classloader conflicts, incompatibility with
GraalVM native image, and isolation that is weak anyway inside a shared JVM. ADR-001
removes the multi-tenancy argument that would have justified the complexity.

**Consequences.** Teams needing bespoke Java tools build a custom runtime image in
library mode and reference it via `RuntimeSpec.image`. That is a build-time operation,
so no dynamic loading machinery is required. Bundle and image version independently: a
bundle rolls forward without rebuilding the image, and a patched image rolls out without
republishing bundles.

---

## ADR-004 — Repository scope is the Runtime and the runtime half of the Kernel

**Status:** Accepted

**Decision.** This repository implements the Runtime (§9) and the execution-side Kernel
services (§7): Context Manager, Memory Manager, Skill Engine, MCP Manager, policy
enforcement and the A2A layer. Studio, Control Plane (Registry, Catalog, Deployment
Manager), Gateway and the Kubernetes operator live in separate repositories.

**Rationale.** The Control Plane *defines* policy and desired state; the Runtime
*applies* it. Keeping that line sharp is what allows the phases of the roadmap to be
built in parallel rather than sequentially.

**Consequences.** Anything in this repository that needs Control Plane data reaches it
over an API or receives it in the bundle. The runtime never becomes the source of truth
for deployment or catalog state.

---

## ADR-005 — Secrets are referenced by name, never embedded

**Status:** Accepted

**Decision.** Manifest values may contain `${secrets.NAME}` and `${env.NAME}`
placeholders, resolved by the runtime at startup through the `SecretResolver` port.
Bundles contain no credentials.

**Rationale.** Follows directly from ADR-003: an immutable, signed artifact promoted
unchanged from staging to production cannot carry environment-specific credentials.
Model configuration follows the same rule — a bundle names *which* model to use, while
endpoint and API key come from the runtime environment.

**Consequences.** The default resolver reads process environment variables injected by
the Deployment Manager. A vault-backed resolver can be plugged in without any change to
the bundle format. Unresolved placeholders are deliberately left verbatim so that a
misconfiguration surfaces as a visible `${secrets.x}` rather than a silently blank
credential.

**Open.** Where the Control Plane stores secret *values*, and how the Studio presents
secret *references*, is not decided here — it is a cross-repository concern.

---

## ADR-006 — Tools are supplied by composable providers

**Status:** Accepted

**Decision.** Tools may come from sources outside the compiled application. `ToolRegistry`
keeps its own scan of `@AgentTool` methods and additionally composes a list of
`ToolProvider`s, owning the name-to-provider routing between them. One `McpToolProvider`
is created per declared MCP server.

**Rationale.** This is the seam that makes ADR-002 and ADR-003 hold at the same time.
Library mode and runtime mode differ solely in where tools come from, so isolating that
difference behind one interface means the orchestrator, prompt builder and tool-calling
loop stay identical for both.

An earlier draft of this work extracted annotation scanning into an
`AnnotationToolProvider`, making the registry a pure composer. That class was dropped
when this branch merged with `main`: the annotation-driven cross-cutting concerns —
`@RequiresRole`, `@RequiresApproval`, `@CacheableToolResult`, `@ToolRetry` — are enforced
by gates that sit in `ToolRegistry` and read Java annotations no external source has, so
splitting them out duplicated the registry's logic without buying isolation. Annotation
scanning therefore stayed where it was, and `ToolProvider` covers only external sources.

**Consequences.**

- Providers are consulted in order and the first to claim a name wins, so a local Java
  tool shadows a remote one of the same name. Collisions are logged, not rejected.
- A provider that fails discovery is logged and skipped: one unreachable MCP server does
  not stop the agent from starting. `fail-fast` opts into the stricter behaviour.
- `ToolDefinition` gained a provider-neutral `parameters` list so that agent-core can
  describe schemas without depending on any LLM SDK. Reflection-based discovery emits
  untyped string parameters, preserving previous model-visible behaviour exactly, while
  MCP tools pass their declared JSON Schema types through.
- The registry closes its providers on shutdown, which is what stops stdio MCP child
  processes from outliving the agent.
