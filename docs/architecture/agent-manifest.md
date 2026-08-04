# Agent Manifest Reference

The manifest is the declarative definition of a workload and the contract between the
Control Plane and the Runtime. It is the root document of an agent bundle.

Schema version: `gargantua.ai/v1` — see
[`WorkloadManifest`](../../agent-core/src/main/java/ai/gargantua/core/workload/WorkloadManifest.java).

The `apiVersion / kind / metadata / spec` shape mirrors a Kubernetes object so that the
eventual Custom Resource Definition is a transcription of this schema rather than a
second, divergent model.

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
    image: ghcr.io/giskardb/gargantua-runtime:1.0
    minVersion: "1.0"

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

> **Not enforced at workload level yet.** Memory-layer selection is currently a per-skill
> decision, made through `metadata.memory-layers` in `SKILL.md`. A manifest-level
> declaration is accepted and reported at startup, but does not restrict anything.

## `spec.allowedRoles`

Roles permitted to invoke the workload.

> **Not enforced at workload level yet.** RBAC is currently applied per skill, through
> `metadata.allowed-roles` in `SKILL.md`. As above, a manifest-level declaration is
> reported rather than applied.

## `spec.guardrails`

Raw overrides keyed by guardrail name, applied on top of runtime configuration.
Deliberately untyped, because guardrail settings vary per implementation and the runtime
binds them onto its own configuration objects. Keys are converted to kebab-case, so
`maxLengthChars` and `max-length-chars` both bind.

---

## Enforcement status

Everything in a manifest parses and validates. Not all of it changes runtime behaviour
yet. `gargantua validate` prints the gaps for a specific bundle; this is the general
picture.

| Field | Status |
|---|---|
| `metadata.*` | Applied — becomes the agent identity and A2A card |
| `spec.capabilities` | Applied — advertised on the A2A Agent Card |
| `spec.capabilities[].inputSchema` / `outputSchema` | Carried for discovery; not resolved or validated |
| `spec.runtime.image` | Recorded; honoured by the Deployment Manager, not the runtime |
| `spec.runtime.minVersion` | Recorded; not verified |
| `spec.model.*` | Applied — binds onto `agent.llm.*` |
| `spec.mcp.servers` | Applied — connected at startup, tools discovered |
| `spec.defaultSkill` | Applied — binds onto `agent.routing.fallback-skill` |
| `spec.guardrails` | Applied — binds onto `agent.guardrail.*` |
| `spec.memoryLayers` | Reported, not applied — use per-skill declaration |
| `spec.allowedRoles` | Reported, not applied — use per-skill declaration |
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
