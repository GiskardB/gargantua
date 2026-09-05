# Session Handoff — 2026-09-05

**Scope:** Follow-on to `SESSION_HANDOFF_2026-09-04.md`. The user asked for a genuinely complete live test of a multi-skill, multi-property bundle (memory, bundled reference content, an unauthenticated external MCP tool) actually running against the real Runtime — and, when two things didn't work, insisted on a real root-cause fix, not a workaround, verified identically in both delivery modes (bundle/Runtime and Maven-archetype/Library). That insistence surfaced three real, previously-undiscovered bugs — two in `agent-engine`/`agent-mcp-server`, one in `agent-archetype` itself.

---

## What was done this session

### 1. Bundled reference files never reached the LLM (real bug, `agent-engine`)

Live-testing a bundle with a `skills/billing-skill/references/billing-policy.md` file, the model gave a plausible-sounding but completely fabricated answer to a fact only present in that file. Traced end to end rather than assumed:

- `FilesystemSkillRegistry.load()` → `appendFolderReferences()` — confirmed correct with a new real-filesystem test (`@TempDir`, no mocks; every existing test mocked `ResourcePatternResolver` and never actually exercised Ant-style path matching against real files).
- `DefaultOrchestratorEngine` — found `PromptBuilder.build()` is called *before* `TokenBudgetManager.allocate()` runs, and the allocation's result (truncated references/episodic/knowledge) is never fed back into the prompt that was already built. That's a second, larger finding, described but not fixed this session (see "Known gaps" below) — fixing it means reordering the pipeline in two call sites (`DefaultOrchestratorEngine`, `ChatStreamController`), a bigger change than today's reported symptom needed.
- Root cause of the reported symptom: `PromptBuilder.build()` itself never read `skillCard.references()` at all — not a truncation issue, a straight omission.

**Fix:** `PromptBuilder` now appends a "Reference material" section from `skillCard.references()`. 3 new tests in `PromptBuilderTest`, 1 new real-filesystem test in `FilesystemSkillRegistryTest`.

**Verified live, twice, with the exact same fabricated-vs-exact contrast:** once through a republished bundle on a rebuilt `gargantua-runtime:local`, once through a project generated fresh from `agent-archetype` (see §3) — both went from a fabricated answer to a verbatim-correct one on a distinctive fact ("9am-5pm CET" / "purple octopus named Glorb").

### 2. `agent-mcp-server`'s MCP transport was never actually wired (real bug)

Building a second container to serve as an unauthenticated external MCP tool for the test, `agent.mcp.enabled=true` logged a convincing "MCP Server initialized" message but every client request 404'd. Traced with `javap`/bytecode disassembly against the `io.modelcontextprotocol.sdk` jars (no source jars available): `AgentMcpServerAutoConfiguration` registered `ChatMcpTool`/`CapabilitiesMcpResource` as plain beans, but nothing ever constructed a `McpSyncServer` or exposed `WebMvcSseServerTransportProvider#getRouterFunction()` as a Spring bean — so Spring MVC never dispatched anything to `/mcp`. The feature has apparently never worked since it was introduced.

**Fix:** `AgentMcpServerAutoConfiguration` now builds the real `WebMvcSseServerTransportProvider` + `McpSyncServer`, wraps the gateway tool and capabilities resource as real MCP feature specifications, and returns the transport's router function as a `@Bean`. Discovered mid-fix, live: the real embedded-profile app has no `ObjectMapper` bean available at this auto-configuration's construction time (unlike my first unit test, which supplied one and masked the gap) — switched both new bean methods to `ObjectProvider<ObjectMapper>` with a `new ObjectMapper()` fallback, matching this class's existing defensive style for optional dependencies. Test fixture updated to *not* provide an `ObjectMapper` either, so it now faithfully reproduces the real scenario instead of hiding it.

**Verified live, full round trip, two separate Docker containers:** a real MCP protocol handshake (`Protocol: 2024-11-05`), the `ask-the-agent` tool actually invoked by the LLM, the callee running its own full pipeline (routing → memory → LLM) and returning a real response — confirmed via logs on both sides, not just an HTTP 200.

### 3. `agent-archetype` pinned to a years-stale JitPack tag (real bug, found while verifying #1/#2 in Library mode)

The user required both delivery modes (bundle/Runtime and Maven-archetype/Library) to behave identically. Generating a project via `mvn archetype:generate` (per the README's own "Try it in 60 seconds") and adding the same reference-file test reproduced the *original*, unfixed symptom — even with today's fixes already built. Cause: the archetype's template `pom.xml` hardcodes `com.github.giskardb.gargantua:agent-engine:v1.2.2` (JitPack, an ancient tag), not `io.github.giskardb` (Maven Central, what the main README documents as the recommended path and what every other doc in this repo assumes). Every project this archetype has ever generated pulls in a version of the framework many releases behind current — including missing both of today's fixes, and by extension whatever else has shipped since `v1.2.2`.

**Fix:** template now depends on `io.github.giskardb:agent-engine`/`agent-mcp-server`/`agent-skill-linter-maven-plugin` at `1.2.20` (the latest actual Maven Central release — not `1.4.0-SNAPSHOT`, which isn't published anywhere a real user's `mvn archetype:generate` could reach). Removed the now-unneeded JitPack `<repositories>`/`<pluginRepositories>` blocks from the default template; left a comment pointing at the README's JitPack instructions for anyone who genuinely needs a snapshot/branch build.

**Also found and fixed while re-verifying:** `agent-archetype`'s own integration test (`archetype:integration-test`, which generates a project from the template and builds it) was disabled outright — `<skip>true</skip>` in `agent-archetype/pom.xml`, with a comment explaining it was skipped *specifically because* JitPack resolution made CI unreliable. Re-enabled it now that the default is a stable Maven Central release. Ran it: it *also* failed, independently — `src/test/resources/projects/basic/goal.txt` said `generate`, not a valid Maven lifecycle phase for building the already-generated project (confusing the archetype-generation goal with a build phase to run against the result). This test had apparently never been run successfully even once. Fixed `goal.txt` to `verify` (also exercises the SKILL.md linter, bound to that phase). Confirmed green: generates, runs the generated project's own test, packages, lints — `BUILD SUCCESS`.

---

## Files changed

| Repo | File | Change |
|---|---|---|
| `gargantua` | `agent-engine/.../PromptBuilder.java` | appends `skillCard.references()` |
| `gargantua` | `agent-engine/.../PromptBuilderTest.java` | 3 new tests |
| `gargantua` | `agent-engine/.../FilesystemSkillRegistryTest.java` | 1 new real-filesystem test |
| `gargantua` | `agent-mcp-server/.../AgentMcpServerAutoConfiguration.java` | real MCP transport wiring |
| `gargantua` | `agent-mcp-server/.../AgentMcpServerAutoConfigurationTest.java` | new transport test; removed the `ObjectMapper` stub that had masked the real gap |
| `gargantua` | `agent-archetype/pom.xml` | re-enabled the integration test |
| `gargantua` | `agent-archetype/.../archetype-resources/pom.xml` | JitPack `v1.2.2` → Maven Central `1.2.20` |
| `gargantua` | `agent-archetype/src/test/resources/projects/basic/goal.txt` | `generate` → `verify` |

---

## Current verified state

668 tests, 0 failures, across `agent-core`/`agent-memory-sdk`/`agent-bundle`/`agent-engine`/`agent-mcp-server`/`agent-runtime`, plus `agent-archetype`'s integration test now green (was disabled).

Three full live round-trips this session, each comparing broken-vs-fixed behavior directly rather than assuming the fix worked:
1. Reference-file content, Runtime mode (bundle + rebuilt `gargantua-runtime:local`).
2. Reference-file content, Library mode (fresh `agent-archetype`-generated project).
3. Unauthenticated MCP tool call, two-container round trip, real protocol handshake.

---

## Known gaps & honest caveats (carried forward, not fixed today)

- **`TokenBudgetManager`'s allocation is disconnected from the real prompt.** `DefaultOrchestratorEngine`/`ChatStreamController` both call `promptBuilder.build(skillCard, memory, enricherContext)` *before* `tokenBudgetManager.allocate(...)` runs, and never use the allocation's truncated references/episodic/knowledge afterward — episodic summaries are even passed into the `BudgetRequest` as a hardcoded empty list, separately from what actually reaches the prompt. Today's fix makes references *appear* in the prompt (the reported bug); it does not make them *subject to the budget truncation the codebase already computes for them*. A skill with a very large reference file, or a long episodic/knowledge history, has no real protection against blowing the context window today. Properly fixing this means reordering pipeline steps in two files so the budget is computed first and the final prompt is built from its (possibly-truncated) output — a real but distinct piece of work, flagged here rather than folded into today's fix under time pressure.
- **`agent.mcp.security.auth-required` still does nothing.** The property exists, binds, and is logged, but nothing checks it — an MCP server with `auth-required: true` doesn't actually require anything today. Same class of gap as the two fixed this session (a config knob that's computed but never enforced); not fixed, flagged for a future session.
- **No per-skill memory-layer override** (carried forward from 2026-09-04): a real use case needing mixed memory profiles within one agent isn't representable since memory-layer selection moved to the agent level.
