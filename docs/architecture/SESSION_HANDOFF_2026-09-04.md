# Session Handoff — 2026-09-04

**Scope:** A `gargantua-studio` + `gargantua` (Runtime) + `gargantua-examples` session. Started as a UX critique of the Studio Agent Designer ("half these fields don't make sense, they feel duplicated") and iterated through several rounds of live-verified simplification. Along the way, one design disagreement turned into a real Runtime behavior change: memory-layer selection moved from per-skill to agent-level enforcement. Two unrelated bugs were found and fixed. Every change in this doc was verified against the actual running compose stack or a real test run, not just read.

---

## What was done this session

### 1. Agent Designer critical review → restructure → simplify (`gargantua-studio`)

Iterative, each round driven by direct user feedback on the running form:

1. **Reorganized by real-world topic instead of manifest-tree position** — Cognition moved next to Model (its semantic mirror), the old "Loadout" catch-all was split so Memory/Knowledge/Skills fields sit next to their actual siblings, Contract paired with Guardrails as "Constraints."
2. **Merged Model + Cognition into one section** — six always-visible fields (Provider/Family/Name × Primary/Fallback) were 95% auto-derived echo of Model.primary/fallback; collapsed into an inline "PACT declares: x/y" hint plus a `<details>` override, only shown if the heuristic guesses wrong.
3. **Dropped PACT from the form entirely** ("lasciamo stare pact, è troppo complicato") — Cognition and Contract removed. The manifest schema, `PactManifest`, and the live `GET /.well-known/pact.json` endpoint are untouched; Studio just stopped asking users to fill in a declaration nobody wanted to author. Interfaces stayed (useful on its own merits) but rebuilt as a checkbox pair against the two protocols gargantua actually serves as an interface (A2A, MCP-server-mode) instead of free-text protocol + URL.
4. **Removed every field `ManifestProperties.unappliedFields()` confirms the Runtime ignores at workload level** — full Loadout (Knowledge bases, Memory scopes, extra equipped Skills, Resources), `allowedRoles`, `runtime.minVersion`. A control that visibly does nothing is worse than no control. Resources was kept on one pass as a requested exception, then cut again on a later pass ("togli anche resources, non ha senso").
5. **Capabilities: `inputSchema`/`tags` added as editable fields, then removed again** — first pass found them unreachable from the UI (real gap); user then clarified editing/JSON-authoring belongs to the Skill Designer only, Capabilities stays pure reference (skill picker + derived fields).
6. **Skill Designer: "Memory layers" field removed** — see §4 below for why; knowledge-base/RAG fields stayed (correctly per-skill, confirmed against `RagEnricher`/`RagConfig`).

### 2. Two real bugs found and fixed (unrelated to layout)

- **Unauthenticated remote MCP servers passed silently everywhere.** `validateDraft()` warned when a non-`none` auth type was missing its value, never the reverse — choosing `none` for an `http`/`sse` transport (an external network endpoint) produced zero warning, in Studio or the built manifest. Verified live: published exactly such an agent before the fix, no friction anywhere. Fixed with one added warning in `gargantua-studio/frontend/src/lib/validate.ts`.
- **Deleting a Registry bundle left a dangling Catalog entry forever.** `RegistryService.delete()` (gargantua-control-plane) removed the Registry entry and blobs but never told the Catalog — `Catalog` had no removal method at all. Added `Catalog.deindexProvider(coordinate)`, implemented in both `InMemoryCatalog` and `JdbcCatalog`, wired into `RegistryService.delete()`. Verified live: publish → delete → confirmed gone from `GET /api/v1/catalog/capabilities`; a capability shared by another provider survives the delete (dedicated test).

### 3. Memory layers moved from per-skill to agent-level — a real Runtime change

User's premise ("gargantua already implements the 3-layer toggle at the agent level") was checked and found **false** initially: `ManifestProperties.unappliedFields()` explicitly warned `spec.memoryLayers` is parsed but not read by anything; the live mechanism was `metadata.memory-layers` per skill in `SKILL.md`, consumed by `MemoryComposer` via `SkillCard.enabledMemoryLayers()`. Presented as a decision point (not silently "fixed" either way) — user chose to make it true: move enforcement to the agent.

**A dedicated example module (`agent-example-memory-layers`) existed specifically to demonstrate the per-skill mechanism as a feature** — found mid-implementation, presented as a second decision point (hybrid agent-default-with-skill-override vs. full agent-level override). User chose full override and to update the example rather than preserve backward compatibility.

**Changes (`gargantua`):**
- `agent-engine/.../AgentProperties.java` — `Memory` gets a `layers` field (`List<String>`) + `getEnabledLayers()` (parses to `Set<MemoryLayer>`, mirrors `SkillMdParser`'s frontmatter-parsing pattern).
- `agent-engine/.../DefaultOrchestratorEngine.java`, `.../ChatStreamController.java` — the two `memoryComposer.compose(...)` call sites now pass `properties.getMemory().getEnabledLayers()` instead of `skillCard.enabledMemoryLayers()`.
- `agent-runtime/.../ManifestProperties.java` — projects `spec.memoryLayers` onto `agent.memory.layers`; removed the now-stale "not implemented" warning from `unappliedFields()`.
- `agent-runtime/.../ManifestPropertiesTest.java` — new tests: `spec.memoryLayers` binds onto `agent.memory.layers` and is no longer reported as a gap; an unrestricted manifest doesn't emit the property (composer default is already "all three," nothing to project).
- **Deliberately untouched:** `SkillCard.enabledMemoryLayers`, `SkillMdParser`'s frontmatter parsing. The field still parses correctly (`skillCardEnabledLayersFromFrontmatter` test still passes) — the engine just stopped reading it. No breaking change to `agent-core`'s public API (a Maven-Central-published artifact).

**`agent-example-memory-layers` updated** to describe the new reality: README rewritten (per-skill "opt-out" framing → agent-level `agent.memory.layers` as the real lever, with a migration note), `greeter-skill`/`assistant-skill` SKILL.md comments corrected (their declarations still parse, no longer affect live chat), `application.yml` gets a documented `agent.memory.layers` example.

**Verified live**, not just by unit test (neither `DefaultOrchestratorEngine` nor `ChatStreamController` had any prior test coverage at all — a pre-existing gap, not something this session could close without a large new mock harness disproportionate to a two-line change): ran the example with `agent.memory.layers=working`, POSTed a chat forcing `assistant-skill` (declares no per-skill override — would previously default to all three layers), and the `MemoryComposer` log showed `layers=[WORKING]` — the agent-level setting fully superseded the skill default.

### 4. Documentation corrected to match

- `docs/architecture/agent-manifest.md` — `spec.memoryLayers` section and the enforcement-status table both said "reported, not applied, use per-skill declaration"; now say "applied, agent-wide."
- `docs/architecture/platform-handoff.md` — "Recent progress" and a dated `### 2026-09-04` entry added; a stale claim that "the Studio Agent Designer authors `spec.cognition`/`spec.contract`/`spec.interfaces` through real form sections" corrected (Cognition/Contract authoring was removed from Studio this session — the manifest fields and PACT endpoint themselves are unaffected).

---

## Files changed

| Repo | File | Change |
|---|---|---|
| `gargantua` | `agent-engine/.../AgentProperties.java` | `Memory.layers` + `getEnabledLayers()` |
| `gargantua` | `agent-engine/.../DefaultOrchestratorEngine.java` | memory compose call now agent-level |
| `gargantua` | `agent-engine/.../ChatStreamController.java` | memory compose call now agent-level |
| `gargantua` | `agent-runtime/.../ManifestProperties.java` | projects `spec.memoryLayers`; dropped stale warning |
| `gargantua` | `agent-runtime/.../ManifestPropertiesTest.java` | 2 new tests |
| `gargantua` | `docs/architecture/agent-manifest.md` | `spec.memoryLayers` enforcement status corrected |
| `gargantua` | `docs/architecture/platform-handoff.md` | this session's summary + stale PACT-authoring claim fixed |
| `gargantua-studio` | `frontend/src/components/AgentDesigner.tsx` | full restructure/simplification (see §1) |
| `gargantua-studio` | `frontend/src/components/SkillDesigner.tsx` | Memory layers field removed |
| `gargantua-studio` | `frontend/src/lib/validate.ts` | unauthenticated remote MCP warning |
| `gargantua-control-plane` | `src/main/java/.../catalog/Catalog.java` (+ `InMemoryCatalog`, `JdbcCatalog`, `CatalogService`, `RegistryService`) | `deindexProvider` on delete |
| `gargantua-examples` | `agent-example-memory-layers/{README.md,application.yml,skills/*/SKILL.md}` | updated for agent-level memory-layers |

---

## Current verified state

| Repo | Notes |
|---|---|
| `gargantua` | `agent-runtime` 27/27 tests, `agent-engine` 265/265 tests |
| `gargantua-studio` | `tsc --noEmit` clean, 14/14 frontend tests, rebuilt + redeployed against the live compose stack |
| `gargantua-control-plane` | Catalog deindex fix committed and pushed earlier in the session |
| `gargantua-examples` | `agent-example-memory-layers` 10/10 tests; live-verified via `mvn spring-boot:run` + a real chat request |

Three live round-trips against the running compose stack this session: an unauthenticated external MCP server end to end (build → publish → Registry → Catalog → downloadable bundle → cleanup), a full manifest exercising every remaining Designer field, and the agent-level memory-layer override.

---

## What's still open / needs review

### Medium priority
1. **No per-skill memory-layer override exists anymore.** If a real use case needs mixed memory profiles within one agent (e.g. one skill needs full history, another is stateless), that's not representable today — raised as a known limitation in the `agent-example-memory-layers` README, not solved.
2. **`AgentGraph.tsx`'s memory-layer toggle** (Studio's radial graph editor) was already targeting the correct agent-level `draft.memoryLayers` field before this session — no change needed, but worth knowing it exists as a second UI surface for the same field.

### Low priority
3. The optional deeper fix flagged in a prior session — renaming `MemoryLayer.KNOWLEDGE` (the enum value) to something that doesn't collide with "Knowledge base" in raw YAML manifests — is still just flagged, not done. Studio's UI already shows "User profile" as the display label; the wire value is untouched.

---

## Decisions made this session (do not relitigate)

1. **PACT stays in the platform, leaves the Studio form.** Cognition/Contract were "declarative by design, never enforced" (PACT §31) — after a live walkthrough they read as confusing duplication, not useful declaration. The manifest schema and `/.well-known/pact.json` are unaffected; a future UI (or direct YAML/API authoring) can still set these fields.
2. **Interfaces is a fixed checkbox set, not free text** — `agent-core`'s `InterfaceEndpoint.protocol` javadoc calls it "open vocabulary" by design, but gargantua itself only ever serves two of them (A2A always-on, MCP-server-mode opt-in via `agent.mcp.enabled`, itself not manifest-driven). A `<datalist>`-suggested free-text field would have been more "correct" to the open-vocabulary spec; a fixed checkbox pair is more honest about what gargantua actually does.
3. **Memory-layer enforcement moves to the agent, full override, no hybrid.** Considered a hybrid (agent-level default, skill can still override) specifically to preserve `agent-example-memory-layers`'s existing demonstrated behavior — rejected in favor of full override plus updating the example, on the reasoning that memory is a property of who's talking, not of which skill answers a given turn, and a hybrid keeps the ambiguity around indefinitely instead of resolving it.
4. **`SkillCard.enabledMemoryLayers` stays in `agent-core`, unused by the engine, rather than being removed.** Consistent with every other "remove the authoring surface, keep the data plumbing" call made this session (Cognition/Contract/Loadout/etc.) — avoids a breaking change to a Maven-Central-published record's canonical constructor, for a field that costs nothing to leave parseable.
5. **No new test harness for `DefaultOrchestratorEngine`/`ChatStreamController`.** Neither class had any test coverage before this session — building one from scratch (dozens of mocked dependencies) to cover a two-line change would be disproportionate. Verified live instead, against a real Spring context and a real HTTP request, which is more representative than a synthetic mock anyway.
