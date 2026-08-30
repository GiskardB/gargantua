# Session Handoff — 2026-08-30

**Scope:** A `gargantua`-only session (no Studio/Control-Plane/Compose changes). The user
shared a personal, unpublished draft — **PACT v0.2**, an agent-description specification
they intend to eventually propose to AAIF — and asked for an honest assessment, then to
close the gap between it and Gargantua's own manifest (`gargantua.ai/v1`), making
Gargantua the spec's first real reference implementation. The session ended with a full
audit-and-fix pass over every markdown file in the repo, per explicit request.

---

## What was done this session

### 1. PACT assessment and integration design

Read PACT v0.2 in full (1177 lines) and cross-referenced it against Gargantua's actual
manifest implementation (`WorkloadManifest`/`AgentSpec`/`ManifestParser`/
`ManifestProperties`, all read directly, not assumed). Findings:

- PACT explicitly treats "Agent Manifest" (authority/governance/boundaries) as a
  **separate, composable** layer, not something it redefines. The user confirmed
  Gargantua's own manifest plays that role.
- Real, load-bearing overlap existed (capabilities, model info) but turned out to be
  **less severe than it first looked**: `Capability.name` already maps 1:1 to PACT's
  `id`, and Gargantua's `ModelSpec` is an *operational alias* (resolved by the runtime
  environment) while PACT's `cognition.models` is a *semantic* declaration — different
  purposes, no real conflict once the actual code was read.
- Gargantua already exposes a real A2A Agent Card at `/.well-known/agent.json` — i.e. it
  already partially satisfies PACT's "Interfaces" pillar operationally, just not as an
  explicit manifest declaration.

Design principle applied throughout: **reuse before adding a field.** Only add a new
manifest field where Gargantua had zero prior representation.

### 2. New PACT Core fields — `core.pact` package (`agent-core`)

Genuinely new, additive, optional `AgentSpec` fields (backward-compatible constructor
added, exactly like the Loadout precedent — no existing caller or test needed to change):

- **`Cognition`** (`modalities`, `capabilities`, `models` → `CognitionModels`/
  `ModelDescriptor`, `requirements` → `CognitionRequirements`) — what kind of reasoning
  an agent exposes, vendor-neutrally. No prior equivalent existed.
- **`Contract`** (`autonomy`, `permissions`) — the basic semantic conditions an agent
  operates under. Declarative only, explicitly not a substitute for
  `allowedRoles`/`guardrails`.
- **`Autonomy`** — a **typed enum** (`PASSIVE`/`ASSISTIVE`/`RECOMMENDING`/`EXECUTING`/
  `AUTONOMOUS`), per explicit user preference over a plain `Integer`. The manifest wire
  format stays the plain `0`-`4` integer PACT's own examples use —
  `Autonomy.ofLevel(int)`/`.level()` convert at the parse/serialize boundary, so this is
  a Java-side type-safety improvement, not a schema change.
- **`InterfaceEndpoint`** (`protocol`, `endpoint`, `version`) — how another system can
  reach the agent, e.g. `{protocol: a2a, endpoint: .../.well-known/agent.json}`.

**Identity and Purpose — the other two PACT pillars — got no new field.** They are
*derived* by the new `PactManifest.from(WorkloadManifest)` projector from
`metadata.owner`/`metadata.description`, which already answer close-enough questions
(same reasoning as capability `name` ≡ PACT `id`). Documented as a pragmatic
approximation, not a perfect semantic match (PACT itself distinguishes "what is it" from
"what is it for"). `null` when the source field is blank — no synthesized empty objects.

All three PACT-declared fields are reported (not enforced) via
`ManifestProperties.unappliedFields()`, but with different wording than
Loadout/Governance: PACT's own "Declaration vs Verification" principle (§31) means no
enforcement is planned for these, ever — it isn't a gap, it's by design, and the doc/code
comments say so explicitly.

### 3. `PactManifest` — the projection

`PactManifest.from(WorkloadManifest)` (pure Java, no Spring) builds a standalone PACT v1
Core document — all seven pillars — from an existing Gargantua manifest. No new storage,
no sync step. Not yet wired to any HTTP endpoint (see "What's still open" below).

### 4. Reactor version bump

`1.3.0-SNAPSHOT` → `1.4.0-SNAPSHOT` across all 10 `pom.xml` files (root + 9 modules),
following the same convention as the earlier Loadout bump.

### 5. Tests

18 new tests in `agent-core` (`CognitionTest`, `ContractTest`, `AutonomyTest`,
`InterfaceEndpointTest`, `PactManifestTest` — including the Identity/Purpose
derivation/blank-input cases), 6 new in `ManifestParserTest`, 1 new in
`ManifestPropertiesTest`. Full reactor verified **869 tests, all green** (up from 842 —
verified by an actual build capturing Surefire summaries per module, not estimated), via
a throwaway `maven:3.9-eclipse-temurin-25` (glibc, not Alpine — musl breaks onnxruntime's
native lib in `agent-engine`'s RAG tests, unrelated to this session's changes) Docker
image, cleaned up after each run.

### 6. PACT doc rewrite (v0.2 → v0.3, file renamed)

Kept PACT Core **vendor-neutral throughout** — deliberately did not scatter "Gargantua"
mentions through the spec body, to protect its credibility as an AAIF candidate. Changes:

- Tightened the Cognition self-declaration vs. Cognition Requirements distinction (§11,
  §13) — was genuinely ambiguous.
- Added a taxonomy disclaimer to `contract.permissions` (§14), mirroring the one
  `capabilities` already had — flagged as a real, currently-open interoperability gap.
- Gave each autonomy level (§15) a one-line operational meaning, and an explicit
  stopping-authority cross-reference to Agent Manifest for levels 3-4.
- **New §34, Versioning Policy** — previously `apiVersion: pact/v1` appeared in every
  example with zero evolution policy, the same immaturity gap the Runtime's own manifest
  had. Now defines additive-vs-breaking within a major version.
- **New §46, Reference Implementations** — short, explicitly non-normative — the only
  place PACT names Gargantua, and only to record that a real implementation exists and
  what it learned (including committing to the 5-level autonomy enum, informing open
  question 4 with a tentative "yes").
- Updated 2 of the 10 open questions with what the implementation exercise actually
  taught, per the doc's own "validate against a real implementation" call to action.

### 7. Full documentation audit (explicit request: "tutti i markdown devono essere letti verificati aggiornati")

Read and verified all ~24 documentation markdown files (excluded: SKILL.md template
fixtures under `agent-archetype`/`agent-skill-linter-maven-plugin` test resources —
scaffolding samples, not project documentation). Delegated first-pass surveys to two
parallel research agents (architecture/platform docs; feature/reference docs), then
personally verified every claim before acting — one flagged "wrong Maven coordinates"
turned out to be an intentional two-channel (Maven Central vs JitPack) comparison table
in most files, genuinely wrong in only one (`memory-system.md`, which also had a third,
outright-invalid groupId in its prose). Fixes applied:

- **Version staleness** (`1.3.0-SNAPSHOT` → `1.4.0-SNAPSHOT`, Java 21 → 25, Spring Boot
  4.0.4 → 4.1.0, test count 842 → 869 verified): `platform-handoff.md`,
  `project-handoff.md`, `getting-started.md`, `README.md`.
- **Stale cross-repo claims**: `gargantua-studio-backend` still described as a separate
  repo in `ai-operating-system.md` and `project-handoff.md` (merged into `gargantua-studio`
  weeks ago); MinIO still mentioned in `ai-operating-system.md` (removed, Postgres-only).
- **Wrong package/field enumerations**: `agent-core` package count (20 → 23, missing
  `governance`/`execution`/`pact`) in `project-handoff.md`; `AgentSpec` field list missing
  `loadout`/`cognition`/`contract`/`interfaces` in `gargantua-domain-model.md`.
- **Genuine bugs found while reading, unrelated to PACT**: a malformed JSON code block in
  `api-reference.md` (an embedded `//` comment ran into the next token with no line
  break); an internal self-contradiction in `skills-and-routing.md` (claimed a
  `forceSkill` request-body field exists, then correctly said it doesn't, 50 lines
  later); a dangling reference to a nonexistent `agent-example-rag` module in both
  `extending.md` and `skills-and-routing.md`; duplicate "6." in `project-handoff.md`'s
  reading-order list; wrong Maven coordinates + a third, invented groupId in
  `memory-system.md`.
- **`gargantua-domain-model.md`** got a "freshness note" at the top rather than a full
  re-audit of every status badge — it's a "0.1 Draft" object-vocabulary doc predating
  Studio/CP/Compose entirely; the vocabulary (§1-17) is still accurate and was verified,
  but §18-20's "which repo has started" narrative is now pointed at `platform-handoff.md`
  instead of re-litigated line by line (disproportionate scope for a vocabulary
  reference doc).
- Roadmap item added to `platform-handoff.md` §7 (PACT Core, done) and §7 item 9 (PACT
  Designer support, not started).

### 8. README repositioned

Explicit user ask: *"probabilmente il readme di gargantua deve dare un concetto diverso
rispetto a quello che c'è."* Previous framing was single-repo-library-only — accurate but
incomplete, since a reader would have no way to discover the Control Plane/Studio/Compose
ecosystem or PACT from this file. Kept everything that still works (the 60-second
quickstart stays first and unchanged — it's genuinely good and still true) and added:

- A short paragraph right after the tagline, plus a **"Optional companions"** section
  (repo table + status, links to `platform-handoff.md`/`ai-operating-system.md`) and a
  **PACT section** — honest about draft status, what's implemented (manifest fields,
  projection) vs. not (Studio UI, live endpoint).
- Fixed version badges (Java 21→25, Boot 4.0.4→4.1.0) and the Tech Stack table to match.
- Added `platform-handoff.md`, `project-handoff.md`, `gargantua-domain-model.md`, and the
  PACT spec itself to the Documentation table — none of the first three were linked from
  the README before, despite `platform-handoff.md` calling itself "read this first."

**Correction mid-session, from the user:** the first pass of this section called
Gargantua "the Runtime/kernel of a larger platform" and titled the section "Part of a
larger platform" — wording that inverts the actual dependency direction. The user
clarified: Gargantua is a **complete, self-contained framework/runtime** that can be used
entirely on its own — hand-write a manifest and a bundle per the spec, `gargantua run`
it, done, no other repo involved. Studio/Control-Plane/Compose are **optional add-ons
built on top of** a complete Gargantua (for a visual authoring UI or multi-agent fleet
management), not something Gargantua is "part of" or incomplete without. Rewrote the
section as **"Optional companions"** with that direction made explicit, and softened the
intro paragraph to match. **This framing is the one to keep** — don't re-introduce
"kernel of a platform" language in future edits.

### 9. Studio: PACT Core in the Agent Designer

Closed the day's last open item: Studio's `agent-core`/`agent-bundle` dependency bumped
to `1.4.0-SNAPSHOT`, and the Agent Designer gained three new form sections (Cognition,
Contract, Interfaces) placed after Guardrails, using the exact same "stringly" draft
convention as the rest of the form (comma-separated text for open vocabularies).

- **Backend**: `AgentDraftRequest` gained `Cognition`/`Contract`/`InterfaceEndpoint`
  nested records; `ManifestBuilder` wires them through `toManifest()`/`toDraft()`/
  `toYaml()`/`precheck()`, mirroring the existing Loadout/Governance treatment exactly.
- **Frontend**: matching TypeScript types (`draft.ts`, `manifest.ts`), offline preview
  support in `buildManifest.ts`, and `validateDraft()` rules mirroring the backend's
  (autonomy level 0-4, required interface protocol/endpoint).
- The new sections reuse the existing `Section`/`Field`/`grid` components verbatim, so
  they inherited this session's earlier mobile CSS fixes for free — no new CSS needed,
  confirmed via Playwright at 1440px and 390px, zero overflow, zero console errors.
- **Verified live, not just unit-tested**: built a real manifest through the deployed
  Studio backend with all three PACT sections populated (`curl` against
  `/api/studio/manifest/build`) and got back valid YAML matching the
  `agent-manifest.md` reference shape exactly.
- Backend suite run for real (not skipped) inside the Docker build: green. Frontend:
  12/12 (4 new). `gargantua-studio` commit `8ff658f`.

### 10. The live `/.well-known/pact.json` endpoint (the last open item, now closed)

User said "procedi pure" (go ahead) after the push, so this continued straight into the
one remaining gap: no HTTP endpoint served a PACT document.

- **`PactManifest.toWireMap()`** (agent-core) — deliberately *not* left to a generic
  object mapper. Returning `PactManifest` straight to Jackson would serialize `Autonomy`
  as its enum name (`"RECOMMENDING"`) instead of PACT's actual wire format
  (`{"level": 2}`), and would leave `CognitionRequirements` flattened Java-side instead of
  nested under `modalities.required`/`capabilities.required`/`contextWindow.minimum`.
  `toWireMap()` builds the correct `LinkedHashMap`/`ArrayList` tree by hand — no Jackson
  dependency, matching agent-core's existing "no Spring, no Jackson annotations" rule, and
  mirroring the same manual-tree pattern Studio's `ManifestBuilder.toYaml()` already uses.
- **`PactController`** (new, `agent-runtime`, package `ai.gargantua.runtime`) — serves
  `GET /.well-known/pact.json`. Lives in agent-runtime rather than agent-engine (where
  the sibling `/.well-known/agent.json` lives) because **agent-engine does not depend on
  agent-bundle** — `LoadedBundle` (which the controller needs for the full
  `WorkloadManifest`, not just properties) simply isn't importable there. This is also
  why the endpoint is inherently Runtime-mode-only: Library-mode apps have no
  `gargantua.ai/v1` manifest to project from at all.
- Confirmed `LoadedBundle` is already retained as a Spring singleton bean
  (`registerSingleton("loadedBundle", bundle)` in `GargantuaRuntime.installBundle`) and
  already consumed the same way by `RuntimeConfiguration.capabilityRegistry(LoadedBundle)`
  — the controller is a two-line consumer of an existing pattern, not a new one.
- **Verified live, for real**: rebuilt `gargantua-runtime:local` from source, booted a
  container against a hand-authored bundle with all three PACT fields populated
  (`SPRING_PROFILES_ACTIVE=embedded`, no Mongo/Redis/Ollama needed), connected it to the
  `cave` Docker network to reach it from the shell, and curled the endpoint. Response
  matched exactly: `contract.autonomy.level: 2` (an integer, not `"RECOMMENDING"`),
  `cognition.models.primary` correctly nested, `Cache-Control: max-age=60`, and
  `/.well-known/agent.json` still answering correctly on the same container (no
  regression). Full reactor: **874 tests** (was 869), all green, verified via
  `mvn test` inside a real Docker build, not skipped.

**Nothing PACT-related is open in either repo anymore.**

---

## Current verified state

| Repo | Branch | HEAD after this session | Tests |
|---|---|---|---|
| `gargantua` | main | `4e0d5d8` + this endpoint work (pending commit) | 874 (was 842), all green on `1.4.0-SNAPSHOT` |
| `gargantua-control-plane` | main | unchanged | 28 |
| `gargantua-studio` | main | `8ff658f` | 68 backend (was 65) / 12 frontend (was 8) |
| `gargantua-compose` | main | unchanged | — |

`gargantua` (`edae152`, `4e0d5d8`) and `gargantua-studio` (`8ff658f`) were pushed to
`origin` mid-session. The live-endpoint work (§10) is committed locally as of this doc
revision; push pending.

---

## What's still open / needs review

### High priority
1. ✅ ~~Studio/Designer has zero PACT support~~ — done, see §9.
2. ✅ ~~No live PACT artifact~~ — done, see §10. `PactController` serves
   `GET /.well-known/pact.json`, verified against a real running container.
3. **PACT itself has no JSON Schema or reference validator yet** (`pact validate` is
   aspirational syntax in the spec, matching Gargantua's own `gargantua validate` CLI in
   spirit, not yet in existence for PACT). This is a PACT-ecosystem gap, not
   Gargantua-specific — the only PACT-shaped work left, and it isn't Gargantua's to do
   alone (it's about the spec, not this implementation of it).

### Medium priority (carried over from 2026-08-29, still true)
4. Undeploy is bookkeeping-only (Studio); stale deployment records on same-agent relaunch;
   `gargantua-runtime-1` stray container — see `SESSION_HANDOFF_2026-08-29.md`.
5. Governance enforcement, loadout provisioning, OTel exporter, bundle signature — all
   still open, see `platform-handoff.md` §8.

### Low priority
6. `contract.permissions` has no agreed cross-implementation taxonomy (documented as an
   open question in PACT §47 item 10, not a bug — just worth remembering next time two
   systems need to agree on a permission string).
7. `spec.interfaces` is still not cross-checked against what the runtime actually serves
   (a manifest could declare an `a2a` interface pointing anywhere; nothing verifies it
   matches this agent's real `/.well-known/agent.json`). Noted in `ManifestProperties`'s
   own warning text; not fixed, since fixing it means deciding what "verify" would even
   mean for a URL that might point at a different host entirely.

---

## Decisions made this session (do not relitigate)

1. **PACT Core stays vendor-neutral in its own document.** Gargantua is named exactly
   once, in a clearly-marked non-normative "Reference Implementations" section — not
   scattered through Core, to protect PACT's credibility as an eventual AAIF submission.
2. **Reuse before adding a field.** Identity/Purpose are derived from existing metadata,
   not given dedicated manifest fields, because Gargantua already had close-enough
   equivalents. Extend `PactManifest.from` later if real divergence shows up — don't
   pre-build for a need that hasn't appeared.
3. **`Autonomy` is a typed Java enum; the manifest wire format is still a plain integer.**
   Explicit user preference, resolved without changing what a `.yaml` manifest looks like.
4. **Since PACT is unreleased and authored in this repo, its own open questions can be
   tentatively resolved using evidence from this implementation** (autonomy scale,
   §47 item 4) rather than left abstractly open — a data point from a real implementation
   is exactly what the spec's own "Draft Status" section (§50) asks for.
5. **`gargantua-domain-model.md`'s historical sections are pointed at `platform-handoff.md`
   rather than re-audited line-by-line** — proportionate scope for a vocabulary-reference
   doc whose actual contract (the object shapes) was fully re-verified.
6. **PACT's own wire shape is never left to a generic object mapper.** `PactManifest`
   holds Java-ergonomic domain types (an `Autonomy` enum, flattened `CognitionRequirements`
   fields); `toWireMap()` is a separate, explicit step that reshapes them into what the
   spec actually says. This follows the codebase's existing rule that agent-core stays
   Jackson-annotation-free and a dedicated mapper owns wire format — never assume a
   record's natural Java shape matches its serialized form.
7. **New Runtime-only HTTP surface goes in `agent-runtime`, not `agent-engine`,** whenever
   it needs the full `WorkloadManifest`/`LoadedBundle` rather than a Spring-property
   projection — agent-engine has no dependency on agent-bundle by design (Library mode
   has no manifest at all), so anything needing the raw parsed manifest can only live
   where that dependency already exists.
