# Agent Manifest Reference

The manifest is the declarative definition of a workload and the contract between the
Control Plane and the Runtime. It is the root document of an agent bundle.

Schema version: `gargantua.ai/v1` — see
[`WorkloadManifest`](../../agent-core/src/main/java/ai/gargantua/core/workload/WorkloadManifest.java).

The `apiVersion / kind / metadata / spec` shape mirrors a Kubernetes object so that the
eventual Custom Resource Definition is a transcription of this schema rather than a
second, divergent model.

**Relationship to PACT.** This manifest covers all seven pillars of PACT's conceptual
model (metadata, identity, purpose, capabilities, cognition, contract, interfaces — PACT
§3). In PACT's own terms, this manifest *is* the "Agent Manifest" layer (authority,
operational boundaries, governance — see PACT §16-17), composed with PACT's vendor-neutral
capability/cognition/contract/interfaces layer:

| PACT pillar | Where it lives here |
|---|---|
| Metadata | `metadata.name` / `.version` / `.description` |
| Identity | *derived* from `metadata.owner` — no dedicated field |
| Purpose | *derived* from `metadata.description` — no dedicated field |
| Capabilities | `spec.capabilities[].name` (≡ PACT `id`) / `.description` |
| Cognition | `spec.cognition` — new field, no prior equivalent |
| Contract | `spec.contract` — new field, no prior equivalent |
| Interfaces | `spec.interfaces` — new field, no prior equivalent |

`spec.cognition`, `spec.contract` and `spec.interfaces` are genuinely new fields with no
prior representation. Identity and Purpose are **not** new fields — they are *derived*
by [`PactManifest.from(WorkloadManifest)`](../../agent-core/src/main/java/ai/gargantua/core/pact/PactManifest.java)
from `metadata.owner`/`metadata.description`, which already answer close-enough
questions, rather than adding a redundant second place to say the same thing (same
reasoning as capability `name` ≡ PACT `id`, no new field either). This is a pragmatic
default, not a perfect semantic match: PACT's own Human Questions table (§24) treats
"what is it" (`metadata.description`) and "what is it for" (`purpose`) as different
questions. A manifest that genuinely needs to answer them differently has nowhere to put
the second answer yet — extend `PactManifest.from` if that need shows up for real, don't
add the field speculatively before it does.

`PactManifest.from(WorkloadManifest)` projects a manifest onto a standalone PACT v1 Core
document, for anyone who wants the portable subset without the rest. See
[PACT_v0.4_Agent_Contract_Specification.md](../../PACT_v0.4_Agent_Contract_Specification.md).

---

## Complete example

```yaml
apiVersion: gargantua.ai/v1
kind: Agent

metadata:
  name: customer-agent
  version: 1.2.0
  description: Handles customer payment enquiries and refunds
  owner: payments-team
  labels:
    env: prod
    tier: critical

spec:
  # Which runtime image this bundle needs. Omit to accept the platform default.
  # Name a custom image when the agent requires Java tools built in library mode.
  runtime:
    image: ghcr.io/giskardb/gargantua-runtime:1.4.0
    minVersion: "1.4.0"

  # Contracts advertised to the Catalog. Callers route on these, not on the agent name.
  capabilities:
    - name: refund-payment
      description: Handles a payment refund request
      version: 1.0.0
      implementedBy: refund-skill
      inputSchema: schemas/refund-input.json
      outputSchema: schemas/refund-output.json
      tags: [payments, gdpr]

    - name: payment-status
      description: Reports the current status of a payment
      version: 1.1.0

  # Model references only — endpoints and API keys come from the runtime environment.
  model:
    primary: gpt-4o
    fallback: claude-sonnet-4-20250514
    routing: phi4-mini
    temperature: 0.7
    maxTokens: 1000

  # Tools. In runtime mode this is where they all come from.
  mcp:
    servers:
      - name: payments-api
        transport: http
        url: https://mcp.internal/payments
        auth:
          type: bearer
          value: ${secrets.payments-api-token}
        allowedTools: [getPayment, refundPayment]

      - name: github
        transport: stdio
        command: npx
        args: ["-y", "@modelcontextprotocol/server-github"]
        env:
          GITHUB_TOKEN: ${secrets.github-token}

      - name: legacy-crm
        transport: sse
        url: https://crm.internal/sse
        enabled: false

  # Memory layers to enable. Omit for all three.
  memoryLayers: [WORKING, EPISODIC]

  defaultSkill: default-skill

  allowedRoles: [support-agent, super-admin]

  # Raw overrides applied on top of runtime guardrail configuration.
  guardrails:
    pii-input:
      enabled: true
    max-length:
      maxChars: 8000

  # PACT Core — see "Relationship to PACT" above. Declarative only; not enforced by the
  # runtime, by design (PACT §31 Declaration vs Verification).
  cognition:
    modalities: [text]
    capabilities: [reasoning, planning]
    models:
      primary:
        provider: anthropic
        family: claude

  contract:
    autonomy:
      level: 2
    permissions: [read_repository]

  interfaces:
    - protocol: a2a
      version: "1.0"
      endpoint: https://agents.internal/customer-agent/.well-known/agent.json
```

---

## `metadata`

| Field | Required | Description |
|---|---|---|
| `name` | yes | Unique workload name, stable across versions |
| `version` | yes | Semver of this revision |
| `description` | no | Shown in Studio and Catalog; defaults to empty |
| `owner` | no | Owning team, used for Catalog ownership and alerting |
| `labels` | no | Free-form key/value metadata for selection |
| `governance` | no | Cross-cutting governance envelope — see below |

## `metadata.governance`

The shared **governance envelope**: cross-cutting ownership, visibility and lifecycle that
the Catalog and Policy Manager reason over. The same shape is designed to attach to other
governed resources (skills, capabilities, memory, knowledge) — carried as a trait, not by
collapsing those types into one supertype.

```yaml
metadata:
  governance:
    tenant: acme                 # owning tenant/organisation; omit for the default tenant
    visibility: internal         # private (default) | internal | public
    status: active               # free-form lifecycle label (draft/active/deprecated/…)
    access: [ops, support]       # ACL: roles/principals granted access beyond `visibility`
```

`visibility` defaults to `private` and is only emitted when it departs from it. `createdAt`
and `updatedAt` are **assigned by the Control Plane** when a resource is registered — a
signed bundle manifest does not carry them, so they never appear here.

> **Reported, not enforced yet.** The runtime parses and validates the envelope; the Policy
> Manager is the component that will enforce visibility and access.

## `spec.runtime`

| Field | Required | Description |
|---|---|---|
| `image` | no | Container image required; omit for the platform default |
| `minVersion` | no | Minimum runtime version — recorded, not verified by the runtime |

Bundle and image version independently. See
[ADR-003](runtime-decisions.md#adr-003--bundles-are-declarative-and-contain-no-executable-code).

`minVersion` is intended for the Deployment Manager to honour when scheduling. The runtime
itself does not refuse to start on a version mismatch; it reports the declaration at
startup so the gap is visible rather than silent.

## `spec.capabilities[]`

The externally advertised contract. Distinct from a skill: a **skill** is how the agent
decides to behave, a **capability** is what it promises to others.

| Field | Required | Description |
|---|---|---|
| `name` | yes | Routing identifier, e.g. `refund-payment` |
| `description` | yes | Used by the Gateway for intent matching |
| `version` | yes | Semver of the capability contract itself |
| `implementedBy` | no | Skill handling this capability; omit to use normal routing |
| `inputSchema` | no | Accepted-input contract — see the note below |
| `outputSchema` | no | Produced-output contract — see the note below |
| `tags` | no | Catalog filtering labels |

Capability names must be unique within a manifest.

> **Schemas are carried, not yet resolved.** `inputSchema` and `outputSchema` are stored
> verbatim and surfaced for discovery. The runtime does not currently load a referenced
> file, nor validate a capability invocation against the schema — per-skill
> `metadata.output-schema` is the mechanism that is actually enforced today. Write either
> a bundle-relative path or an inline schema; both round-trip unchanged.

## `spec.model`

Every field is optional; `null` means inherit the runtime default. Bundles name models,
never credentials.

| Field | Constraint |
|---|---|
| `primary`, `fallback`, `routing` | Model aliases resolved by the runtime |
| `temperature` | Between `0.0` and `2.0` |
| `maxTokens` | Positive |

## `spec.mcp.servers[]`

| Field | Required | Description |
|---|---|---|
| `name` | yes | Unique within the manifest; namespaces discovered tools |
| `transport` | yes | `stdio`, `http`, or `sse` |
| `command` | for `stdio` | Executable to launch |
| `args` | no | Arguments passed to `command` |
| `env` | no | Child-process environment; supports placeholders |
| `url` | for `http`/`sse` | Endpoint |
| `auth` | no | `none`, `bearer`, `basic`, or `header` |
| `allowedTools` | no | Allow-list; empty exposes everything the server advertises |
| `enabled` | no | Defaults to `true`; lets an operator disable a server in place |

Validation is enforced at load time: a `stdio` server without a `command`, or a remote
server without a `url`, rejects the manifest rather than failing on first tool call.

## `spec.memoryLayers`

Subset of `WORKING`, `EPISODIC`, `KNOWLEDGE`. Omit or leave empty for all three.

> **Applied, agent-wide (2026-09).** Projected by `ManifestProperties` onto
> `agent.memory.layers`, the one input `MemoryComposer` reads for every request this
> agent serves — see `AgentProperties.Memory#getEnabledLayers()`. This used to be a
> per-skill decision via `metadata.memory-layers` in `SKILL.md`; that field still parses
> (`SkillCard.enabledMemoryLayers`), but the engine no longer reads it. Memory is a
> property of who's talking to the agent, not of which skill answers a given turn — a
> conversation can route across multiple skills, and per-skill restriction meant the same
> user's context silently changed shape mid-conversation depending on routing.

## `spec.allowedRoles`

Roles permitted to invoke the workload.

> **Not enforced at workload level yet.** RBAC is currently applied per skill, through
> `metadata.allowed-roles` in `SKILL.md`. As above, a manifest-level declaration is
> reported rather than applied.

## `spec.loadout`

The specific subset of knowledge, memory, skills and resources the agent is *equipped*
with — as opposed to `spec.memoryLayers` (which toggles which layers are on) and
`spec.capabilities` (what it advertises). The loadout provisions an agent deliberately:
two agents on the same image with the same layers can still carry different knowledge
bases and resources.

```yaml
spec:
  loadout:
    knowledge:                       # knowledge bases (vector collections) to equip
      - name: payments-kb            # required — the collection/index name
        description: Payment policies and refund rules
        maxResults: 8                # optional — overrides the skill/runtime retrieval default
        minScore: 0.55               # optional — similarity threshold in [0.0, 1.0]
      - name: refunds-kb             # name only → inherits retrieval defaults
    memoryScopes: [customer-history] # named memory collections, beyond the layer toggles
    skills: [refund-skill, status-skill]   # skills to equip, beyond spec.defaultSkill
    resources:                       # arbitrary named resources (files, datasets, endpoints)
      - name: refund-form
        type: file                   # optional hint: file | dataset | http | s3 | …
        uri: resources/refund.pdf    # optional; never inline secrets — use ${secrets.NAME}
```

All four parts are optional; an absent `loadout` means "nothing explicitly equipped" and
the agent relies on what its skills configure. `knowledge` is the first-class,
targeted-knowledge part — the same identifier a skill's `metadata.knowledge-base` points
at (see `RagConfig`). Knowledge-base and resource names must be unique within a loadout.

> **Not provisioned by the runtime yet.** A loadout is parsed, validated and reported at
> startup, but the runtime does not yet attach knowledge/memory/resources from it —
> knowledge bases are currently wired per skill via `metadata.knowledge-base` in
> `SKILL.md`. This is intent carried by the manifest for the Control Plane and future
> runtime provisioning.

## `spec.guardrails`

Raw overrides keyed by guardrail name, applied on top of runtime configuration.
Deliberately untyped, because guardrail settings vary per implementation and the runtime
binds them onto its own configuration objects. Keys are converted to kebab-case, so
`maxLengthChars` and `max-length-chars` both bind.

## `spec.cognition`

PACT Core's "Cognition" pillar: what kind of reasoning/modalities this agent exposes,
vendor-neutrally — distinct from `spec.model`, which names the *operational* model alias
the runtime resolves via environment.

```yaml
spec:
  cognition:
    modalities: [text, image]              # what the agent can understand/produce
    capabilities: [reasoning, planning]     # cognitive abilities offered
    models:
      primary: {provider: anthropic, family: claude}   # semantic, not an alias
      fallback: {provider: openai, family: gpt}
    requirements:                           # what the *substrate* must provide, not
      modalities:                           # what this agent itself offers — enables
        required: [text]                    # selection without naming a vendor
      capabilities:
        required: [reasoning]
      contextWindow:
        minimum: 64000
```

| Field | Required | Description |
|---|---|---|
| `modalities` | no | Open vocabulary; no controlled taxonomy |
| `capabilities` | no | Open vocabulary; same stance as `spec.capabilities[].tags` |
| `models.primary` / `models.fallback` | no | Semantic `{provider, family, name}`, not an alias |
| `requirements.*` | no | What the hosting substrate must support, for agent selection |

> **Declarative by design, not "not enforced yet."** Unlike `spec.loadout` or
> `spec.allowedRoles`, this is not a gap the runtime intends to close later — PACT itself
> says a declaration is not proof of capability (PACT §31). The runtime parses and
> reports it; nothing more is planned or implied.

## `spec.contract`

PACT Core's "Contract" pillar: the basic semantic conditions under which the agent may
act. Deliberately small, and **not a security control** — `spec.allowedRoles` and
`spec.guardrails` remain what the runtime actually enforces.

```yaml
spec:
  contract:
    autonomy:
      level: 2        # 0 passive · 1 assistive · 2 recommending · 3 executing · 4 autonomous
    permissions: [read_repository]   # claimed, not granted — open vocabulary
```

| Field | Required | Description |
|---|---|---|
| `autonomy.level` | no | Integer `0`-`4`; self-declared, not verified |
| `permissions` | no | Free-form strings the agent claims to need; no controlled vocabulary |

A capability does not imply permission, and neither does a contract: this is what the
agent *claims*, not what it is authorized to do. Same declarative status as
`spec.cognition` above.

The wire format stays the plain integer above — matching PACT's own examples — but
`AgentSpec.contract().autonomy()` is a typed
[`Autonomy`](../../agent-core/src/main/java/ai/gargantua/core/pact/Autonomy.java) enum in
Java, not a raw `int`. `Autonomy.ofLevel(int)`/`Autonomy.level()` convert at the
parse/serialize boundary.

## `spec.interfaces`

PACT Core's "Interfaces" pillar: how another system may reach this agent, beyond the
built-in A2A endpoint every agent already exposes at `/.well-known/agent.json`.

```yaml
spec:
  interfaces:
    - protocol: a2a
      version: "1.0"
      endpoint: https://agents.internal/customer-agent/.well-known/agent.json
    - protocol: mcp
      endpoint: https://agents.internal/customer-agent/mcp
```

| Field | Required | Description |
|---|---|---|
| `protocol` | yes | e.g. `a2a`, `mcp`, `http`; open vocabulary |
| `endpoint` | yes | URL the protocol is reachable at |
| `version` | no | Protocol version |

> **Not cross-checked against what the runtime actually serves.** Listing the built-in
> A2A endpoint here makes it discoverable from the manifest alone, without a live agent
> to ask — but nothing currently verifies the two agree.

---

## Enforcement status

Everything in a manifest parses and validates. Not all of it changes runtime behaviour
yet. `gargantua validate` prints the gaps for a specific bundle; this is the general
picture.

| Field | Status |
|---|---|
| `metadata.*` | Applied — becomes the agent identity and A2A card |
| `metadata.governance` | Reported, not applied — the Policy Manager will enforce visibility/access |
| `spec.capabilities` | Applied — advertised on the A2A Agent Card |
| `spec.capabilities[].inputSchema` / `outputSchema` | Carried for discovery; not resolved or validated |
| `spec.runtime.image` | Recorded; honoured by the Deployment Manager, not the runtime |
| `spec.runtime.minVersion` | Recorded; not verified |
| `spec.model.*` | Applied — binds onto `agent.llm.*` |
| `spec.mcp.servers` | Applied — connected at startup, tools discovered |
| `spec.defaultSkill` | Applied — binds onto `agent.routing.fallback-skill` |
| `spec.guardrails` | Applied — binds onto `agent.guardrail.*` |
| `spec.memoryLayers` | Applied (2026-09) — binds onto `agent.memory.layers`, agent-wide; supersedes the old per-skill `metadata.memory-layers` |
| `spec.allowedRoles` | Reported, not applied — use per-skill declaration |
| `spec.loadout` | Reported, not applied — knowledge bases wired per skill via `metadata.knowledge-base` |
| `spec.cognition` | Reported — declarative by design (PACT §31), not a gap to close |
| `spec.contract` | Reported — declarative by design (PACT §31); use `allowedRoles`/`guardrails` to enforce |
| `spec.interfaces` | Reported; not cross-checked against what the runtime actually serves |
| `metadata.json` checksum | Applied — a mismatch refuses to load |
| `metadata.json` signature | Recorded; not verified (needs key distribution) |

A declaration that is accepted but ignored is reported at startup rather than dropped
silently, because an operator who believes a constraint is in force when it is not is
worse off than one who sees it is missing.

---

## Placeholders

Any string value may contain:

- `${secrets.NAME}` — resolved through the runtime's `SecretResolver`; confidential
- `${env.NAME}` — resolved from the process environment; non-confidential wiring

Unresolved placeholders are left verbatim, so a misconfiguration surfaces as a visible
`${secrets.x}` in an error rather than a silently blank credential. See
[ADR-005](runtime-decisions.md#adr-005--secrets-are-referenced-by-name-never-embedded).

---

## Bundle layout

```
customer-agent.gbundle
├── manifest.yaml          this document
├── metadata.json          BundleDescriptor: checksum, signature, runtime image
├── skills/                SKILL.md directories
├── prompts/               prompt fragments referenced by skills
├── schemas/               JSON Schemas referenced by capabilities
└── policies/              policy documents
```

No compiled code, by design.
