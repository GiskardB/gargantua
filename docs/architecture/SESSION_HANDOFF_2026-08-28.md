# Session Handoff — 2026-08-28

**Scope:** This session made structural changes across **Control Plane**, **Studio**, and **Compose** to remove MinIO/S3 blob storage, fix a CP boot crash, and make the Studio graph editor interactive with a publish dialog.

---

## What was done this session

### 1. MinIO → Postgres-only blob storage (Control Plane)
- **New:** `PostgresBlobStore` (`gargantua-control-plane/src/main/java/ai/gargantua/controlplane/storage/PostgresBlobStore.java`) — stores bundle bytes in `cp_document.blob` and `bundle_zip` columns via the existing JDBC document store. No separate object store.
- **Removed:** `S3BlobStore.java`, `application-minio.yml`, MinIO dependency from `pom.xml`.
- **Compose:** Removed `minio` service, `minio_data` volume, and all MinIO env vars from `docker-compose.yml`.
- **Docs:** Updated `platform-handoff.md` to remove all MinIO references (architecture diagram, stack table, run instructions, ports table).

### 2. Fixed Control Plane boot crash (namespace mismatch)
- **Bug:** `application.yml` had launch config under `server.launch.*` but `RuntimeLauncher` reads `gargantua.launch.*`.
- **Fix:** Moved `gargantua.launch.command-template` and `gargantua.launch.workdir` to top-level in `application.yml:20-34`. CP now starts healthy.

### 3. Interactive Graph Editor + Publish Dialog (Studio)
**Files added/modified:**
- `frontend/src/components/AgentGraph.tsx` — **Rewritten**: draggable React Flow nodes, click-to-edit detail panel (capabilities, MCP servers, memory layers, knowledge refs, model, agent metadata). Changes sync live to Zustand draft.
- `frontend/src/components/PublishDialog.tsx` — **New**: modal shown on "Save & Publish", prompts version, calls publish, handles loading/error states.
- `frontend/src/components/screens/AgentDesignerScreen.tsx` — Wires Graph/Form tabs, state for `showPublishDialog`, calls `usePublish` hook.
- `frontend/src/styles.css` — Added `.graph-detail-overlay`, `.graph-detail`, `.publish-dialog-overlay`, `.publish-dialog` styles.

### 4. Full test suite verification
- **CP:** 24 tests (RegistryService 12, PolicyService 6, ControlPlaneApi 5, App 1) — all green
- **Studio backend:** 47 tests (StudioApi 14, Skill 17, Manifest 9, Drafts 6, App 1) — all green
- **Studio frontend:** 8 tests (buildManifest) — all green
- **TypeScript:** `tsc -b --noEmit` clean

### 5. Docker Compose stack verified healthy
| Service | Port | Health |
|---------|------|--------|
| `control-plane` | 18080 | UP (db: PostgreSQL) |
| `studio` | 18081 | UP |
| `postgres` | (internal) | UP |

Verified via internal container probes:
- `GET /api/v1/registry/bundles` → `[customer-agent@1.2.0]`
- `GET /api/studio/skills` → `[2weather-skill@1.2.0]`
- `GET /actuator/health` on both → `{"status":"UP"}`

---

## Current verified state

### Repositories
| Repo | Branch | Last commit | Notes |
|------|--------|-------------|-------|
| `gargantua` | main | 89721f5 | Docs hub; Runtime unchanged this session |
| `gargantua-control-plane` | main | a89cd3e | PostgresBlobStore, MinIO removed |
| `gargantua-studio` | main | 3c7bbfa | Merged SPA+BFF, graph editor, publish dialog |
| `gargantua-compose` | main | 3643f07 | Postgres-only, ports 18080/18081 |

### Architecture changes
- **CP storage:** Single Postgres for *all* state (documents + bundle blobs). No MinIO profile.
- **Studio:** Single image (Spring serves SPA + `/api`), no nginx.
- **Launch config:** Now under `gargantua.launch.*` in both CP and Studio (was `server.launch.*` in CP).

### Key files to know

**Control Plane**
```
/gargantua-control-plane/
├── src/main/java/ai/gargantua/controlplane/storage/
│   ├── BlobStore.java                 # interface
│   ├── PostgresBlobStore.java         # NEW: Postgres-only impl
│   └── FilesystemBlobStore.java       # dev profile (!postgres)
├── src/main/resources/application.yml  # gargantua.launch.* config
├── pom.xml                            # MinIO dep removed
└── src/test/resources/application.yml  # test stubs for launch config
```

**Studio**
```
/gargantua-studio/frontend/src/
├── components/
│   ├── AgentGraph.tsx                 # REWRITTEN: interactive graph
│   ├── PublishDialog.tsx              # NEW: publish modal
│   ├── AgentDesigner.tsx              # Form view (unchanged logic)
│   ├── ManifestPreview.tsx            # YAML preview panel
│   └── screens/AgentDesignerScreen.tsx  # Main screen, tabs, dialogs
├── store/
│   ├── draftStore.ts                  # Zustand: AgentDraft state
│   ├── skillsStore.ts                 # Skill drafts from backend
│   └── platformStore.ts               # CP online status
├── lib/
│   ├── useManifestPreview.ts          # build/publish hooks
│   └── api.ts                         # backend + CP calls
├── types/draft.ts                     # AgentDraft, CapabilityDraft, etc.
└── styles.css                         # Graph overlay + dialog styles
```

**Compose**
```
/gargantua-compose/
├── docker-compose.yml                 # MinIO removed; postgres only
├── runtime.Dockerfile                 # Runtime image (bundles fetched by URL)
└── README.md                          # Updated for Postgres-only
```

---

## What's still to do / needs review

### High priority (next session)

1. **Studio: Edit existing workload from Workload Designer**
   - `WorkloadDesigner.tsx` lists published agents; clicking one should open `AgentDesignerScreen` with `?workload=name:version` and prefill the form. Currently wired but untested end-to-end.

2. **Studio: Skill Designer — capability sync**
   - "Assign to agent" in `SkillDesigner.tsx` upserts a `CapabilityDraft(implementedBy=skillName)` to the Agent Designer draft. Verify this still works with the new Graph tab (both tabs share the same draft store).

3. **Studio: Reference file upload → bundle**
   - `ReferenceFileUpload.tsx` uploads to `/api/studio/reference-files`; `buildManifest.ts` should include them in the bundle zip. Verify the CP `RegistryService.publish()` assembles `references/` into the zip (commit `a89cd3e` says it does, but verify).

4. **CP: Signature verification (not checksum)**
   - SHA-256 checksum exists; digital signature on bundle zip is **not implemented**. Roadmap item.

5. **CP: Deployment Manager — track running runtimes**
   - Currently a stub. The Launch button starts a runtime but CP doesn't track it. Need `Deployment` CRUD + state machine.

### Medium priority

6. **Governance enforcement**
   - `metadata.governance` is parsed and exposed in Studio, but Policy Manager doesn't enforce visibility/access yet. Follow-on from §7.3 in `platform-handoff.md`.

7. **Trace Explorer → live runtime traces**
   - `TraceExplorer.tsx` shows sample data. Needs a Runtime URL picker to fetch `GET /api/traces` from a real runtime (runtime opts into CORS via `agent.web.cors.allowed-origins`).

8. **Bundle zip includes skill markdown**
   - `SkillBuilder` generates `SKILL.md` files. Verify they land in `bundle/skills/<skill>/SKILL.md` inside the published zip.

9. **Model/Capability/Skill governance trait**
   - `Governed` is on `WorkloadMetadata` only. Apply to `Capability` / `SkillMeta` / memory / knowledge (cheap now the trait exists).

### Low priority / tech debt

10. **Frontend: Graph layout persistence**
    - Node positions are ephemeral (lost on rebuild). Could persist per-draft or per-session.

11. **Frontend: Undo/redo for draft changes**
    - Zustand store has no history. Could add `past`/`future` arrays for undo stack.

12. **CP: `minio` Spring profile still exists in code**
    - `FilesystemBlobStore` is `@Profile("!postgres")` — but the `minio` profile was never fully removed from all `@Profile` conditions. Audit and clean.

13. **Runtime: Loadout provisioning**
    - `spec.loadout` (knowledge bases, memory scopes, skills, resources) is parsed and reported via `unappliedFields()`, not enforced. Runtime doesn't wire knowledge bases declared in loadout — they're still per-skill via `SKILL.md metadata.knowledge-base`.

---

## Quick start for next session

```bash
# From gargantua-compose
cd /home/dev/workspace/gargantua-compose
DOCKER_BUILDKIT=1 docker compose up -d --build --wait

# Verify
docker exec gargantua-control-plane-1 curl -s http://localhost:8080/actuator/health
docker exec gargantua-studio-1 curl -s http://localhost:8090/actuator/health

# Test Studio UI at http://localhost:18081
# - Click "New Agent" → Form tab → fill → "Graph" tab → click nodes → edit
# - "Save & Publish" → PublishDialog → "Publish"
# - "Download bundle" → .gbundle zip
```

---

## Decisions made this session (do not relitigate)

1. **MinIO is gone.** Bundle blobs live in `cp_document.blob` + `bundle_zip` in the same Postgres as the rest of CP state. This is the final decision for blob storage in the compose slice.

2. **Launch config namespace is `gargantua.launch.*`** everywhere (CP + Studio). The old `server.launch.*` was a bug.

3. **Studio is one repo, one image.** The `gargantua-studio-backend` repo is deprecated/merged. Don't look for it.

4. **Graph editor is the second tab** in Agent Designer (not a separate screen). It shares the exact same draft as the Form tab.

5. **Publish is explicit.** "Download bundle" is CP-independent; "Save & Publish" requires CP online and opens a confirmation dialog.

---

## Known gotchas

- **Port mapping in Cave:** The Docker daemon runs on a different host than the shell. `curl localhost:18080` fails even when containers are healthy — use the actual Docker host address (`<CAVE_HOST>`) or probe from inside containers (`docker exec <container> curl ...`).

- **BuildKit required:** `additional_contexts` in `docker-compose.yml` needs `DOCKER_BUILDKIT=1` (or BuildKit as default). The classic builder rejects it.

- **agent-core SNAPSHOT:** The compose images build `agent-core` from source (`runtime_src=../gargantua`). If you change the domain model, the next compose build picks it up. But released sibling repos still need `1.3.0` published to Maven Central.

- **Beforeunload warning:** Agent Designer shows a browser confirmation on tab close when a draft is open (Form or Graph). This is intentional.

---

## Links to read next

1. **Cross-repo map** → `gargantua/docs/architecture/platform-handoff.md` (updated this session)
2. **Runtime internals** → `gargantua/docs/project-handoff.md`
3. **OSS pattern analysis & roadmap** → `gargantua/docs/architecture/13-open-source-patterns.md`
4. **Skills & routing model** → `gargantua/docs/architecture/skills-and-routing.md`
5. **Manifest schema & enforcement** → `gargantua/docs/architecture/agent-manifest.md`