# PACT — Agent Contract Specification

**Version:** 0.3 Draft
**Status:** Design proposal

> **PACT — the open contract for AI agents.**

**Changes since v0.2:** tightened the Cognition self-declaration vs. Cognition
Requirements distinction (§11, §13); added a taxonomy disclaimer to `contract.permissions`
(§14), mirroring the one `capabilities` already had; gave each autonomy level a one-line
operational meaning and an explicit stopping-authority cross-reference (§15); added a
Versioning Policy (§34), previously deferred to "future work" with no concrete answer; and
added a short, non-normative Reference Implementations note (§46). All from validating
Core against a real implementation, per the v0.2 "Draft Status" call to do exactly that —
not from speculation.

## 1. Executive Summary

PACT is a deliberately small, human-readable specification for describing an AI agent in a machine-readable way.

PACT answers:

> **What is this agent, what can it do, what kind of cognition does it provide, and under what basic contract can it operate?**

PACT is not an agent runtime, framework, orchestration engine, agent-to-agent protocol, tool protocol, governance framework, registry, or LLM API.

PACT is a **semantic description and contract layer** designed to coexist with A2A, MCP, ANP/ADP, Agent Manifest and other standards.

The core principle is:

> **The specification must be easier to understand and implement than the problem it describes.**

---

## 2. Design Goals

1. **Simple first** — a minimal manifest should be understandable at a glance.
2. **Human-readable** — YAML and JSON should map naturally to the conceptual model.
3. **Machine-readable** — a deterministic schema and validator must exist.
4. **Vendor-neutral** — no mandatory model provider, framework, cloud or database.
5. **Protocol-neutral** — PACT describes interfaces but does not define their protocols.
6. **Composable** — reuse existing standards instead of duplicating them.
7. **Progressive complexity** — advanced semantics belong in optional profiles/extensions.

---

## 3. Conceptual Model

```text
Agent
│
├── Metadata
├── Identity
├── Purpose
├── Capabilities
├── Cognition
├── Contract
└── Interfaces
```

The key questions are:

```text
Capability  → What can the agent do?
Cognition   → What kind of AI capabilities does it provide?
Contract    → Under what basic conditions may it act?
Interface   → How can another system interact with it?
```

---

## 4. Minimal Manifest

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: architecture-agent
  version: 1.0.0

capabilities:
  - architecture-analysis

interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

Everything else is optional.

---

## 5. Extended Manifest

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: architecture-agent
  version: 2.1.0
  description: Analyze software architectures and source code.

identity:
  provider: Example Corporation

purpose:
  description: Produce architecture analysis and recommendations.

capabilities:
  - id: architecture-analysis
    description: Analyze software architectures
  - id: source-code-review
    description: Review source code

cognition:
  modalities:
    - text
    - image

  capabilities:
    - reasoning
    - planning
    - code-understanding
    - structured-output

  requirements:
    contextWindow:
      minimum: 64000

  models:
    primary:
      provider: anthropic
      family: claude

contract:
  autonomy:
    level: 2
  permissions:
    - read_repository

interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
  - protocol: mcp
    endpoint: https://example.com/mcp
```

---

## 6. Agent

An Agent is a software entity capable of performing one or more declared capabilities.

PACT does not define the implementation. An agent may be LLM-based, multimodal, deterministic, hybrid, a RAG system, a coding agent, or a multi-agent orchestrator.

PACT therefore does not require an LLM.

---

## 7. Metadata

```yaml
metadata:
  name: architecture-agent
  version: 1.0.0
  description: Analyze software architectures
```

An optional stable identifier may be provided:

```yaml
metadata:
  id: urn:example:agent:architecture
```

---

## 8. Identity

Identity describes who publishes or owns the agent.

```yaml
identity:
  provider: Example Corporation
```

PACT does not mandate DID, certificates, blockchain identity, OAuth or a particular identity provider.

---

## 9. Purpose

Purpose explains the intended role of the agent.

```yaml
purpose:
  description: Analyze software architectures and produce recommendations.
```

Purpose is descriptive and does not grant permission.

---

## 10. Capabilities

A capability describes something the agent can do.

```yaml
capabilities:
  - id: architecture-analysis
    description: Analyze software architectures

  - id: source-code-review
    description: Review source code
```

PACT does not define a universal global capability taxonomy. Domain-specific registries may exist independently.

### Capability vs Tool

```text
Capability → what the agent can accomplish
Tool       → a mechanism used to accomplish it
```

PACT describes capabilities; MCP can describe tools.

---

## 11. Cognition (Self-Declaration)

Cognition is a first-class PACT concept.

It describes semantic characteristics of the agent's AI/reasoning system without prescribing its implementation.

```yaml
cognition:
  modalities:
    - text
    - image

  capabilities:
    - reasoning
    - planning
    - code-understanding
    - structured-output
```

Capabilities answer **what the agent does**.

Cognition answers **what kind of AI capabilities it provides**.

**This is a self-declaration**, not a requirement: `cognition.modalities` and
`cognition.capabilities` describe what *this agent itself* offers, as measured or claimed
by whoever authored the manifest. This is distinct from `cognition.requirements` (§13),
which instead constrains what the agent's *hosting substrate* must provide — a
requirement the agent places on its environment, not a fact about the agent. Confusing
the two directions has caused real interoperability bugs in early drafts of this
specification; keep them in separate blocks and never merge them.

---

## 12. Model Semantics

PACT permits optional semantic information about the model or models used by the agent.

```yaml
cognition:
  models:
    primary:
      provider: anthropic
      family: claude
```

A concrete model may optionally be declared:

```yaml
cognition:
  models:
    primary:
      provider: anthropic
      family: claude
      name: claude-example-model
```

Changing the concrete model should not automatically invalidate the agent's identity or capabilities.

PACT uses `models`, not `llm`, because an agent may use LLMs, SLMs, VLMs, multimodal models, specialized models, ensembles, routers or future reasoning systems.

`cognition.models` is a **semantic** declaration, meant for discovery and matching. It is
deliberately separate from whatever *operational* model reference an implementation's own
manifest or config uses to actually resolve a deployment (an alias, an endpoint, a
routing table entry) — that operational reference usually needs environment-specific
indirection PACT has no reason to standardize. A manifest may keep both, and they are not
required to name the same string.

---

## 13. Cognition Requirements

An agent may declare requirements for its cognitive runtime.

```yaml
cognition:
  requirements:
    modalities:
      required:
        - text
        - image

    capabilities:
      required:
        - reasoning
        - planning

    contextWindow:
      minimum: 64000
```

This enables semantic agent selection without requiring a specific vendor or model.

**This is a requirement, not a declaration** (contrast with §11): it says what the
agent's hosting cognitive substrate must be able to do, not what the agent itself is
known to do. A selection/matching system should read `cognition.capabilities` as "the
agent claims this" and `cognition.requirements.capabilities.required` as "the runtime
must supply this" — the two lists may overlap, but they answer different questions and
must not be collapsed into one field.

---

## 14. Contract

The PACT Core contract is deliberately small.

```yaml
contract:
  autonomy:
    level: 2

  permissions:
    - read_repository
```

A capability does not automatically imply permission.

An agent may have a `production-deployment` capability without having permission to perform production deployment.

PACT should not become a complete enterprise governance specification.

**`permissions` has no controlled vocabulary**, by the same reasoning as `capabilities`
(§10): PACT does not define a universal permission taxonomy, and domain-specific registries
may standardize one independently. Two implementations declaring `permissions` are not
guaranteed to mean the same thing by the same string unless they also agree on an external
taxonomy — this is a known, currently-open interoperability gap (see §47, open question 10),
not an oversight.

---

## 15. Autonomy

PACT proposes a simple conceptual scale:

```text
0  Passive       — observes or reports only; takes no independent action
1  Assistive     — supports a human who remains the actor
2  Recommending  — proposes actions; a human must approve before they happen
3  Executing     — carries out approved or narrowly scoped actions itself
4  Autonomous    — acts and adapts on an ongoing basis without per-action approval
```

Example:

```yaml
contract:
  autonomy:
    level: 2
```

This is a semantic declaration, not a security control.

**Levels 3 and 4 are exactly where a stopping-authority mechanism starts to matter**, and
PACT deliberately declares none: "who can stop it" is Agent Manifest's concern (§16-17),
not Core's. A manifest describing an `executing` or `autonomous` agent should be read
alongside whatever governance layer it composes with — PACT's autonomy level is a hint
for that layer to act on, not a substitute for it.

The five-level scale itself is a proposal, not settled — see open question 4 (§47).

---

## 16. What PACT Does Not Own

The following should generally remain outside PACT Core:

- detailed risk management;
- data governance;
- legal compliance;
- audit implementation;
- stopping mechanisms;
- authority frameworks;
- certification;
- trust attestations;
- enterprise policy engines.

These may be represented through extensions or other standards.

In particular, **Agent Manifest already provides strong concepts around authority, operational boundaries, risk, data handling, human oversight and stopping authority.**

PACT should compose with such specifications rather than duplicate them.

---

## 17. Relationship With Agent Manifest

The strongest distinction is:

```text
PACT
  ├── What can the agent do?
  ├── What kind of cognition does it provide?
  ├── What model semantics does it expose?
  └── How can it be interacted with?

Agent Manifest
  ├── Under whose authority does it operate?
  ├── What are its operational boundaries?
  ├── What is its risk profile?
  ├── What data does it handle?
  └── Who can stop it?
```

PACT should therefore be composable with Agent Manifest.

It should not compete by adding more governance fields.

---

## 18. Relationship With A2A

A2A defines agent-to-agent interoperability.

PACT defines the semantic description of the agent.

```text
PACT
  │ describes
  ▼
Agent
  │ communicates through
  ▼
A2A
```

Example:

```yaml
interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

PACT does not redefine A2A task lifecycle, messages, Agent Card semantics or transport.

Where A2A already owns equivalent semantics, PACT should reference or map them rather than duplicate them.

---

## 19. Relationship With MCP

MCP defines interaction with tools, resources and prompts.

PACT may declare an MCP interface:

```yaml
interfaces:
  - protocol: mcp
    endpoint: https://example.com/mcp
```

PACT does not redefine MCP tools, resources, prompts or transports.

Conceptually:

```text
PACT Capability
       │
       └── may be implemented using MCP tools
```

---

## 20. Relationship With ANP / ADP

ANP/ADP addresses agent identity, description, discovery and network interaction.

PACT should avoid becoming another complete agent networking protocol.

A future PACT ANP/ADP profile can define mappings.

---

## 21. PACT as a Semantic Layer

```text
                 Agent Semantic Layer

                    ┌─────────┐
                    │  PACT   │
                    └────┬────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
      Agent Manifest     A2A            MCP
       Governance      Interaction      Tools
          │              │              │
          └──────────────┼──────────────┘
                         │
                    Agent Runtime
```

PACT is not intended to own the entire stack.

---

## 22. Interfaces

Interfaces declare how an agent can be reached.

```yaml
interfaces:
  - protocol: a2a
    version: "1.0"
    endpoint: https://example.com/a2a

  - protocol: mcp
    endpoint: https://example.com/mcp
```

Possible protocols include A2A, MCP, HTTP, WebSocket, gRPC or custom protocols.

PACT does not define them.

---

## 23. Progressive Complexity

### Level 0 — Identity

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: weather-agent
```

### Level 1 — Capability

```yaml
capabilities:
  - weather
```

### Level 2 — Cognition

```yaml
cognition:
  capabilities:
    - reasoning
```

### Level 3 — Contract

```yaml
contract:
  autonomy:
    level: 1
```

### Level 4 — Interface

```yaml
interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

Advanced requirements belong in profiles and extensions.

---

## 24. Human Questions

Every Core field should map to a simple human question.

| Field | Human question |
|---|---|
| `metadata.name` | What is it called? |
| `metadata.description` | What is it? |
| `identity.provider` | Who publishes it? |
| `purpose` | What is it for? |
| `capabilities` | What can it do? |
| `cognition.modalities` | What can it understand? |
| `cognition.capabilities` | What cognitive abilities does it provide? |
| `cognition.models` | What model family does it use? |
| `cognition.requirements` | What does it require? |
| `contract.autonomy` | How independently may it act? |
| `contract.permissions` | What is it allowed to do? |
| `interfaces` | How can I interact with it? |

If a field cannot be explained with a simple human question, it should probably not be Core.

---

## 25. Simplicity Rule

> **If describing a basic agent requires a long manifest, PACT is too complex.**

Advanced systems should use extensions rather than making the Core schema large.

The Core should prefer:

```yaml
capabilities:
  - code-review
```

over deeply nested ontologies unless additional semantics are actually necessary.

---

## 26. No Performance Declarations

PACT intentionally does not define:

```yaml
latency:
cost:
throughput:
availability:
```

These are operational properties rather than stable semantic characteristics.

PACT makes no latency, cost, throughput or SLA guarantees.

---

## 27. No Runtime

PACT does not define an execution runtime, scheduler, executor or orchestration engine.

It can describe agents implemented in any language or framework.

---

## 28. No Required Framework

PACT must be implementable without LangChain, LangGraph, AutoGen, CrewAI, Semantic Kernel, Spring AI, LangChain4j or any other framework.

Frameworks may generate PACT manifests automatically.

---

## 29. Authoring and HTML

HTML is **not part of the PACT protocol**.

It is an optional authoring and demonstration mechanism.

The canonical model remains:

```text
PACT Specification
       │
       ├── YAML
       └── JSON
```

An authoring application may use HTML:

```text
HTML Form
    │
    ▼
PACT Generator
    │
    ▼
PACT Manifest
```

The same experience could be implemented with a desktop UI, CLI, IDE extension or visual editor.

The HTML tool must consume the PACT schema rather than define a second schema.

---

## 30. Machine Validation

PACT should provide a JSON Schema and a small reference validator.

Example:

```bash
pact validate agent.pact.yaml
```

A validator should distinguish:

- structural validity;
- profile validity.

Validation does not prove that an agent actually possesses its declared capabilities.

---

## 31. Declaration vs Verification

A PACT manifest is a declaration.

It says:

```text
"This agent declares architecture-analysis as a capability."
```

It does not prove:

```text
"This agent really can perform architecture analysis."
```

Trust, certification and attestation are separate concerns.

---

## 32. Extensions

PACT Core must remain small.

Potential extensions include:

```text
security
identity
governance
trust
knowledge
memory
compliance
enterprise
```

Example:

```yaml
extensions:
  security:
    authentication:
      - oauth2
```

Extensions must not silently redefine Core semantics.

---

## 33. Profiles

A profile is a standardized collection of extensions or mappings.

Potential profiles:

```text
PACT A2A Profile
PACT MCP Profile
PACT Agent Manifest Profile
PACT ANP Profile
PACT Security Profile
PACT Enterprise Profile
```

Profiles allow integration with the ecosystem without making Core complex.

---

## 34. Versioning Policy

PACT was published from the start with a fixed `apiVersion: pact/v1` in every example,
while the specification itself was still a "0.x Draft" — the same immaturity gap found
and flagged in an early implementation's own manifest format. This section exists so PACT
does not repeat that mistake once real manifests start citing `pact/v1`.

**Schema versions are `pact/v<major>`.** Only the major number appears in `apiVersion`;
there is no minor/patch in the wire format.

**Within a major version (`pact/v1`), only additive, non-breaking changes are permitted:**

- adding a new optional Core field;
- adding a new value to an open vocabulary (`capabilities`, `cognition.capabilities`,
  `cognition.modalities`, `contract.permissions`, interface `protocol`);
- adding a new extension or profile;
- clarifying prose that does not change validation behavior.

**A new major version (`pact/v2`) is required for anything breaking:**

- removing or renaming a Core field;
- changing a Core field's type or meaning;
- turning an optional field into a required one;
- changing what a validator must reject that it previously accepted, or vice versa.

**Implementations should not hard-fail on an unrecognized *optional* field** within a
version they otherwise understand — this is what lets additive changes actually be
additive in practice, not just on paper. Implementations **should** hard-fail on an
unrecognized `apiVersion` major, exactly as they would refuse an unknown `kind`.

**Deprecation:** a field may be marked deprecated in prose (with a suggested replacement)
for at least one draft cycle before removal in the next major version. PACT v0.x drafts
are exempt from this — nothing is stable enough yet to owe a deprecation window — but the
policy takes effect no later than a `1.0` release.

---

## 35. Registry Use

PACT does not define a registry.

A registry may index PACT manifests and support queries such as:

```text
capability = architecture-analysis
planning = true
modality = image
interface = a2a
```

The registry owns discovery and matching.

PACT supplies the semantic data.

---

## 36. Agent Selection

PACT enables a conceptual selection pipeline:

```text
Task requirements
        │
        ▼
Capability matching
        │
        ▼
Cognition matching
        │
        ▼
Contract matching
        │
        ▼
Interface matching
        │
        ▼
Selected agent
```

PACT does not prescribe the matching algorithm.

---

## 37. Examples

### Coding Agent

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: coding-agent
  version: 1.0.0

capabilities:
  - code-generation
  - code-review
  - test-generation

cognition:
  capabilities:
    - reasoning
    - planning
    - code-understanding

contract:
  autonomy:
    level: 2
  permissions:
    - read_repository

interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

### RAG Agent

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: knowledge-agent
  version: 1.0.0

capabilities:
  - document-search
  - question-answering

cognition:
  capabilities:
    - retrieval
    - reasoning
  modalities:
    - text

interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

### Multi-Agent Orchestrator

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: agent-orchestrator
  version: 1.0.0

capabilities:
  - agent-routing
  - task-decomposition
  - agent-delegation

cognition:
  capabilities:
    - reasoning
    - planning
    - delegation

contract:
  autonomy:
    level: 3

interfaces:
  - protocol: a2a
    endpoint: https://example.com/a2a
```

### Non-LLM Agent

```yaml
apiVersion: pact/v1
kind: Agent

metadata:
  name: fraud-rule-agent

capabilities:
  - transaction-risk-evaluation

contract:
  autonomy:
    level: 3

interfaces:
  - protocol: http
    endpoint: https://example.com/evaluate
```

---

## 38. Interoperability Strategy

Before adding a concept to PACT Core:

1. If another standard owns the semantics, reference it.
2. If PACT adds useful semantic information, define a mapping.
3. If the concept is fundamentally different, consider Core.
4. If the concept is advanced, use a profile or extension.

This rule should prevent PACT from becoming another monolithic agent specification.

---

## 39. Prior-Art Positioning

| Standard | Primary focus |
|---|---|
| A2A | Agent-to-agent interaction |
| MCP | Tools, resources and prompts |
| ANP/ADP | Agent network identity/discovery |
| Agent Manifest | Authority, autonomy, boundaries and governance |
| PACT | Simple semantic agent description and cognition contract |

The goal is complementarity, not replacement.

---

## 40. What Makes PACT Different

PACT should differentiate through:

### Simplicity

A minimal manifest is very small.

### Cognition

Cognition is explicitly represented.

### Model semantics

Model information can be declared without making a vendor mandatory.

### Composability

PACT works alongside A2A, MCP, Agent Manifest and other standards.

### Progressive complexity

Advanced requirements do not contaminate Core.

### Human-first authoring

The model is intentionally easy to expose through forms, generators, IDEs and other tooling.

HTML is one possible authoring implementation, not part of the protocol.

---

## 41. Core vs Extension Decision Rule

Before adding a field to Core, ask:

1. Is it useful to almost every agent?
2. Is the concept stable?
3. Can a developer understand it immediately?
4. Does another standard already own the semantic?
5. Can it be an extension instead?

If the answer is not clearly positive, it should probably not be Core.

---

## 42. Proposed Repository

```text
pact/
│
├── README.md
├── LICENSE
├── SPECIFICATION.md
├── PRINCIPLES.md
│
├── schema/
│   └── pact-v1.schema.json
│
├── profiles/
│   ├── a2a.md
│   ├── mcp.md
│   ├── agent-manifest.md
│   └── anp.md
│
├── examples/
│   ├── minimal.yaml
│   ├── coding-agent.yaml
│   ├── rag-agent.yaml
│   └── orchestrator.yaml
│
├── tools/
│   └── validator/
│
└── authoring/
    └── html-example/
```

The HTML example is tooling/demo material, not part of Core.

---

## 43. Development Order

```text
1. Prior-art analysis
        ↓
2. Conceptual model
        ↓
3. Core specification
        ↓
4. JSON Schema
        ↓
5. Minimal examples
        ↓
6. Reference validator
        ↓
7. Interoperability mappings
        ↓
8. Optional authoring tool
        ↓
9. Community review
        ↓
10. AAIF proposal
```

The project should not begin with an SDK or runtime.

---

## 44. Success Criteria

PACT should pass these tests:

### Developer test

A developer unfamiliar with PACT understands a minimal manifest within minutes.

### Implementation test

A developer can implement Core without adopting an AI framework.

### Portability test

The same manifest can describe agents from different vendors and frameworks.

### Interoperability test

PACT can reference A2A and MCP without redefining them.

### Composition test

PACT can coexist with Agent Manifest without duplicating governance semantics.

### Authoring test

A simple graphical form can generate a valid PACT manifest without requiring the user to understand YAML or JSON Schema.

---

## 45. Future Work

Potential future work:

- formal JSON Schema;
- YAML/JSON canonical mapping;
- capability registry conventions;
- A2A mapping;
- MCP mapping;
- Agent Manifest mapping;
- ANP/ADP mapping;
- PACT validator;
- visual authoring tool;
- IDE integrations;
- registry integrations;
- trust/attestation profile;
- security profile.

These should not be added to Core without evidence that they belong there.

---

## 46. Reference Implementations

*Non-normative.* Listed here to ground the specification against real usage, per the
development order in §43 (step 9, community review, benefits from at least one working
implementation to react to) — not to endorse a vendor or bind Core to one stack's
choices.

- **Gargantua** (Runtime + Studio) carries `cognition`, `contract` and `interfaces` as
  additive fields directly on its own agent manifest, which plays the role PACT calls
  "Agent Manifest" in §17 — authority, operational boundaries and governance — and
  projects a standalone PACT Core document from it on request. `contract.autonomy` is
  modeled as a closed, typed enum internally (informing open question 4, §47), while the
  manifest wire format stays the plain integer this document specifies — the two are
  independent by design (§34). This was the exercise that produced the
  §11/§13/§14/§15/§34 clarifications in this revision: every wording ambiguity fixed here
  came from something that was genuinely unclear while implementing it, not from a
  hypothetical review.

Further implementations should be added here as they appear, with a one-line description
of what they compose PACT with — the point of this section is evidence of portability
(§44, Portability test), which requires more than one entry to actually mean anything.

---

## 47. Open Questions

The following remain intentionally open during v0.x:

1. Should `identity` be Core or optional?
2. What is the smallest useful Cognition vocabulary?
3. Should `model.family` have a controlled vocabulary?
4. Should autonomy use the proposed five-level scale? **Tentatively yes** — the reference
   implementation (§46) committed to it as a closed, typed enum (not just descriptive
   prose) and had no trouble expressing every example in §37 with it. Still only one data
   point; reopen if a second implementation needs a level this scale doesn't have. The
   wire format stays the plain integer `0`-`4` from §15 either way — the enum is a
   Java-side implementation detail, not a manifest format change.
5. How should capabilities reference external taxonomies?
6. How should PACT map to A2A Agent Cards without duplication?
7. How should PACT compose with Agent Manifest?
8. What should the extension namespace mechanism be?
9. Should `requirements` be Core Cognition or a profile? (Kept in Core for now — an
   early implementation needed it immediately for agent selection — but that is one data
   point, not a resolution.)
10. Which fields are genuinely required for interoperability? In particular,
    `contract.permissions` has no agreed taxonomy across implementations (§14) — two
    manifests can use the same string to mean different things today.

The goal is to answer these through implementation and community feedback rather than speculation.

---

## 48. Positioning

Primary tagline:

> **PACT — The open contract for AI agents.**

Technical positioning:

> **A lightweight semantic specification for AI agent capabilities, cognition and contracts.**

A simpler explanation:

> **PACT describes what an agent is, what it can do, how it reasons, and how to interact with it.**

---

## 49. Final Design Position

PACT should remain deliberately modest.

It should not become:

> the protocol for everything agents do.

It should become:

> **the simple specification for describing what an agent is.**

The intended ecosystem is:

```text
                    PACT
           Agent semantic contract
                     │
        ┌────────────┼────────────┐
        │            │            │
   Agent Manifest    A2A          MCP
    Governance     Interaction    Tools
        │            │            │
        └────────────┼────────────┘
                     │
                Agent Runtime
```

The central rule is:

> **PACT should be small enough to implement, expressive enough to be useful, and simple enough to explain in one diagram.**

HTML, visual editors, CLI tools and SDKs may make PACT easier to adopt, but they remain outside the protocol itself.

---

## 50. Draft Status

PACT v0.3 is a design proposal, not a finalized industry standard.

The next activity should be validation against existing specifications and real implementations, especially:

- Agent Manifest;
- A2A;
- MCP;
- ANP/ADP;
- other emerging agent-description specifications.

The objective is not to add more features.

It is to find the **smallest useful semantic layer that is not already adequately standardized elsewhere**.

That constraint is fundamental to PACT.
