# Session Handoff — 2026-08-29

**Scope:** A long Studio-focused session driven directly by the user testing the live
Cave deployment (including from a phone). Fixed a chain of real bugs that made the
Playground unusable off `localhost`, redesigned the Agent Graph, closed a Control-Plane
gap that made published bundles permanently undeletable, and — the biggest structural
change — moved agent launching from "one shared runtime, replaced on every launch" to
**real concurrent multi-agent hosting** (one container + one host port per agent).

---

## What was done this session

### 1. Workload Designer UX
- Cards are no longer one giant click target — explicit **Edit / Launch / Delete**
  buttons per card (`WorkloadDesigner.tsx`).
- Delete is a real bordered button (red hover), not a bare text link.
- **"draft" → "published"**: a bundle saved to the Control Plane isn't a draft (that
  implied "not saved anywhere"); it's just not promoted to an environment. The
  "updated" footer line was a hardcoded placeholder word (`updated published`, literally)
  — now shows the bundle's real publish date (`descriptor.createdAt`) from the Control Plane.
- Deleting a bundle that's `409 still deployed` now shows the blocking deployment(s)
  inline with an **Undeploy** button each — click it and the delete auto-retries. This
  needed a new Control-Plane capability (see §3).

### 2. Agent Designer
- Version is precompiled: `1.0.0` on first save, auto-bumped (patch+1) when opening an
  already-published workload to update it (`nextVersion()` in `types/draft.ts`).
- Name is locked (read-only, both Form and Graph tabs) while editing an existing workload.
- **Capabilities are now skill-derived, not hand-typed twice.** A capability card is just
  a required skill picker; name/version/description/output-schema come from the selected
  skill and are shown read-only (`applySkillToCapability`). Input-schema/tags dropped —
  a skill doesn't have them.
- Manifest preview only renders while actually editing/creating an agent — it used to
  show on the landing page too, with a stale/leftover draft.
- Save & Publish now navigates to Workload Designer on success instead of leaving you on
  the edit screen.
- **Agent Graph rewritten**: it never actually drew edges (`edges={[]}`) and laid nodes
  out in five wide fixed-pixel columns — unreadable on a phone (tiny zoomed-out text,
  constant panning, no visible relationships). Replaced with a radial hub-and-spoke
  layout: the agent at the centre, every capability/MCP server/memory layer/knowledge
  base on a circle around it, connected by a colour-coded edge. Compact by construction,
  so `fitView` never has to zoom out far. The minimap (ate a big chunk of an already-small
  mobile canvas) is hidden under the mobile breakpoint.

### 3. Control Plane: Deployment subsystem gained real teeth
- `DeploymentManager` could `save`/`find`/`list` but never **delete** — so once Studio's
  Launch flow started creating deployment records (to back the Playground's "active
  agent" list), a bundle that was ever launched became permanently undeletable
  (`RegistryService#delete` refuses while *any* deployment references it). Added
  `delete()` to both implementations + `DeploymentService#undeploy` +
  `DELETE /api/v1/deployments/{id}`, wired through Studio
  (`DELETE /api/studio/deployments/{id}`) to the new Undeploy button above.
- `Deployment` gained a **`port`** field (nullable). `updateState` now optionally carries
  the port and keeps whatever was on record if a later call doesn't know it. This is what
  makes concurrent multi-agent hosting observable — see §5.

### 4. Playground — three compounding bugs that only showed up off `localhost`
The user tested from a phone and reported "still doesn't work" after an earlier pass.
Root-caused three independent, stacking bugs:
1. **`crypto.randomUUID()` crashed the whole screen.** That API requires a secure
   context (HTTPS or `http://localhost`) and is `undefined` otherwise — any `http://`
   access from a LAN address or a phone blanked the page on mount. Fixed with a
   `crypto.getRandomValues()`-based fallback (`lib/randomId.ts`) — that call has no such
   restriction.
2. **Runtime URL defaulted to a hardcoded `localhost:18100`.** Only resolves when the
   browser and the Docker host are the same machine — never true from a phone. Now
   derived from `window.location.hostname` (`runtimeStore.ts`).
3. **CORS was pinned to Studio's own origin.** Any other origin (LAN, phone) got
   rejected by the runtime's browser-facing API. Widened to `*` in the launch templates
   (already documented local/dev-only).
4. Also: `LaunchService` was reporting a deployment `HEALTHY` the instant `docker run -d`
   exited, before the agent inside had actually finished booting (can take 30–90s with
   slow/broken MCP servers). Now polls `actuator/health` before reporting state, so the
   Playground only ever lists agents that can really answer. And Runtime error bodies are
   RFC 7807 (`detail`/`title`), not the Studio backend's `message`/`error` shape — the
   frontend only checked the latter, so failures showed as a bare `404` with no reason.

### 5. Real concurrent multi-agent launch (the big one)
Discovered by the user asking, after publishing three unrelated agents (a diet/workout
coach, a comedian, a story/poem/song writer) and launching all three: *"does this
actually start 3 containers, or is there a smarter approach I'm not seeing?"* Answer at
the time: **no** — one container, one host port (`gargantua-runtime:18100`), and every
launch did `docker rm -f` + recreate. Confirmed live with `docker ps` between each of the
three launches. The Control Plane, meanwhile, had just been taught to track deployments
(§3), so it was showing all three as `HEALTHY` even though only the last one was actually
reachable — a real correctness gap once bundles could be launched at all.

Chose **true concurrency** over "just fix the stale state." Changes:
- `LaunchService#allocatePort` — each agent **name** gets its own host port, allocated
  once and persisted (via the same `SettingsStore` the launch command template lives in),
  reused on every relaunch of that name. New agent names get the next free port,
  starting at **18101** (18100 stays reserved for the older, always-on
  `--profile agent-runtime` demo service).
- Container name and health-check URL are now per-agent too:
  `gargantua-runtime-{name}`. The launch command template gained a `{port}` placeholder
  (already had `{name}`/`{version}`/`{env}`).
- Reports the assigned port to the Control Plane alongside the HEALTHY/FAILED state.
- **Playground**: the runtime URL now *follows* the selected agent (previously it only
  filled an empty field once — switching the dropdown silently kept talking to whatever
  was there before). Dropdown options show the port.
- **Launch dialog**: command preview resolves `{port}` from an existing deployment for
  that agent name when known; after a real launch, shows the exact command that ran
  (previously only the output).
- Also added a structured **environment-variable form** (key/value rows) to the Launch
  dialog, independent of hand-editing the raw command — values are shell-quoted and
  spliced in server-side at the `{env}` placeholder.
- Also **shrank the launch command** from 9 `-e` flags to 2 (`GARGANTUA_BUNDLE_URL` +
  `SPRING_PROFILES_ACTIVE=embedded,cave`): the Ollama LLM routing + open CORS that used
  to be repeated on every launch now live in a `cave` Spring profile baked into the
  `gargantua-runtime:local` image (`gargantua-compose/runtime-cave.yml`, activated via
  the `cave` profile flag). They existed only because the image's own defaults point at
  paid providers (OpenAI/Anthropic) Cave has no key for.

**Verified live**: published `wellness-coach` (diet-skill + workout-skill),
`comedian-agent` (puns-skill), `creative-writer-agent` (story/poem/song-skill) via the
Studio API directly (manifest + skills inline, matching `AgentDraftRequest`/
`SkillDraftRequest`), launched all three, confirmed three
`gargantua-runtime-<name>` containers on three different ports via `docker ps`
simultaneously, and chatted with all three independently through both curl and the real
Playground UI — each routed to the correct skill.

### 6. Mobile responsiveness (ongoing thread across the session)
- Off-canvas nav drawer (hamburger button) below 860px; topbar sheds low-priority info
  (tenant/prod pill, connectivity text labels) to fit.
- Dialogs capped to `min(480px, 92vw)`; the launch/publish dialog action rows wrap.
- **CSS Grid "blowout" fix**: `.grid.two/.three`, `.agent-split`, `.skill-layout` all used
  bare `1fr` columns — shorthand for `minmax(auto, 1fr)`, which does **not** shrink below
  its content's intrinsic width. On a phone, one wide input was enough to push the whole
  Agent/Skill Designer form past the right edge of the screen. Fixed everywhere by
  switching to `minmax(0, 1fr)` (the standard fix). Also caught the same bug clipping the
  manifest preview's Copy/Export buttons.

---

## Current verified state

| Repo | Branch | HEAD after this session | Tests |
|---|---|---|---|
| `gargantua` | main | (this doc) | — |
| `gargantua-control-plane` | main | `ececed1` | 28 (was 20) |
| `gargantua-studio` | main | `996114e` | 55 backend (was 30) / 8 frontend / TS clean |
| `gargantua-compose` | main | `bb257d7` | authored + verified live |

All four repos' `main` pushed to `origin` (Forgejo) at the end of this session.

**Live on the deployed compose stack** (verified during the session, may have moved on
since): `wellness-coach`, `comedian-agent`, `creative-writer-agent` running concurrently
on ports 18101–18103; `customer-agent`/`customer-agenteee` published but not launched.

---

## What's still open / needs review

### High priority
1. **CP-optional Studio workflow** — the user wants it *proven*, not just claimed, that
   Studio is fully usable with the Control Plane switched off (create/save drafts and
   skills locally; publish/launch simply disabled). Verification requested for the next
   part of this session — not yet re-confirmed against the multi-agent-era code.
2. **Multi-Control-Plane configuration** — requested next: a Settings section in Studio
   to register multiple Control Planes (name + base URL, maybe more), pick which one is
   "active" (connect/disconnect), and have publish/launch/health-checks/deployments all
   go through whichever is currently selected — i.e. multiple named deploy-environment
   configs. Today `ControlPlaneClient` is wired to a single `RestClient` bean with a
   base-url fixed at startup (`gargantua.control-plane.base-url`) — this needs to become
   dynamic per-request against a stored, switchable list. Not started as of this doc.

### Medium priority (carried over, still true)
3. **Undeploy is bookkeeping-only.** It clears the Control Plane's record so a bundle can
   be deleted; it does not stop the actual container. Given each agent now has an
   identifiable container name (`gargantua-runtime-{name}`), a real "stop" action is
   cheap to add later if wanted.
4. **Stale deployment records on relaunch of the *same* agent name.** Relaunching
   `wellness-coach@1.1.0` after `1.0.0` reuses the same container/port (correct), but the
   *old* deployment record for `1.0.0` is never marked superseded — it'll keep showing
   `HEALTHY` in the Playground list next to the new one, both pointing at the same
   (now-`1.1.0`) container. Harmless (same port, same container, so chatting with either
   "version" reaches the current agent) but a little confusing. Not fixed this session —
   true concurrency solved the *cross-agent* version of this bug; the *same-agent-relaunch*
   version is a smaller, separate loose end.
5. **`gargantua-runtime-1` stray container** (`Exited (143)`, several days old, predates
   this session's naming scheme) seen in `docker ps -a` during testing — harmless, just
   clutter; not cleaned up.
6. Governance enforcement, loadout provisioning, OTel exporter, bundle signature — all
   still open, see `platform-handoff.md` §8 (unchanged this session).

### Low priority
7. `gargantua-runtime:local`'s Ollama model (`qwen2.5:0.5b`) is small enough that
   generated text (especially non-English) is often grammatically rough — a model-size
   limitation of the local/free demo setup, not a platform bug. Worth a note if a demo
   needs cleaner output: point `runtime-cave.yml` at a larger local model or a real
   provider with a key.

---

## Decisions made this session (do not relitigate)

1. **Concurrent multi-agent hosting is now real**, not aspirational — one container +
   one host port per agent *name*, ports allocated once and persisted. This supersedes
   the earlier (2026-08-28) assumption that "the Runtime hosts one agent per process" meant
   *the platform* could only run one agent at a time — that was a launch-template
   limitation, not an ADR-001 requirement. ADR-001 (one process = one agent) still holds;
   it now just means N processes for N agents instead of 1-shared-slot-replaced-on-launch.
2. **Cave-specific LLM/CORS defaults live in a `cave` Spring profile baked into the
   runtime image**, not repeated on every launch command. If the image needs to run
   somewhere the `ollama` service doesn't exist, drop the `cave` profile flag and pass the
   real provider config explicitly.
3. **CORS on a launched runtime is wildcard (`*`), not the Studio origin.** This launcher
   is already documented local/dev-only; a single hardcoded origin can't match "whatever
   host the browser used," which is inherent to how Cave is reached (LAN address, phone).
4. **"draft" is retired as a workload state.** Use "published" for "in the registry, not
   promoted to an environment."

---

## Known gotchas (new this session)

- **`docker exec -i` needed for heredoc stdin.** `docker exec <container> sh -c "cat >
  file" <<'EOF' ... EOF` silently writes an empty file without `-i` — the container
  never sees stdin. Cost real time twice this session before catching it.
- **Shell single-quoted JSON with Italian text breaks on apostrophes.** `dell'utente`,
  `sull'autunno` etc. terminate a `'...'` shell argument early, corrupting everything
  after it (including, confusingly, *later* commands in the same script, since the shell
  keeps hunting for the next `'` to close the string). Heredocs with a quoted delimiter
  (`cat <<'JSON' > file`) sidestep this completely — no shell interpolation at all, so
  apostrophes are just literal bytes. Prefer that over inline single-quoted `curl -d '...'`
  whenever the payload has free-text content.
- **This box has no local `java`/`mvn`.** Backend changes were compiled/tested via a
  throwaway `maven:3.9-eclipse-temurin-25-alpine` Docker image with the
  `gargantua-m2-4` named volume mounted at `/root/.m2` for the cached `agent-core`
  dependency — `docker run --rm -v gargantua-m2-4:/root/.m2 <image> sh -c "mvn -o test"`.
  Rebuilding the *actual* compose image is separate (`docker compose build <service>`)
  and slower; the throwaway-image loop is for fast iterate-test-fix cycles.
- **Background `docker compose build` commands get auto-backgrounded by the harness**
  when they run long; a `TaskStop`/interrupted session doesn't mean the build failed —
  Docker keeps building server-side regardless of the calling process. Check
  `docker images` for a fresh tag before assuming a rebuild needs retrying.

---

## Quick start for next session

```bash
cd /home/dev/workspace/gargantua-compose
DOCKER_BUILDKIT=1 docker compose up -d --build --wait

# Verify
docker exec gargantua-control-plane-1 curl -s http://localhost:8080/actuator/health
docker exec gargantua-studio-1 curl -s http://localhost:8090/actuator/health
docker exec gargantua-studio-1 docker ps --filter name=gargantua-runtime

# Studio UI: http://<CAVE_HOST>:18081 (or http://localhost:18081 on the Docker host itself)
```

---

## Links to read next

1. **Cross-repo map** → `gargantua/docs/architecture/platform-handoff.md` (update this
   session — re-read §3.1, §7, §8 for what changed)
2. **Previous session** → `SESSION_HANDOFF_2026-08-28.md`
3. **Runtime internals** → `gargantua/docs/project-handoff.md`
4. **Manifest schema & enforcement** → `agent-manifest.md`
5. **Skills & routing model** → `skills-and-routing.md`
