# Gargantua Domain Model
## Il contratto degli oggetti del sistema operativo AI

**Versione:** 0.1 Draft
**Stato:** Domain Model — parte già implementata in Phase 1
**Prerequisito:** [`ai-operating-system.md`](ai-operating-system.md) (visione) e
[`runtime-decisions.md`](runtime-decisions.md) (ADR vincolanti).

Questo documento risponde alla domanda posta dal §19 del documento di visione:
**quali sono gli oggetti del sistema operativo AI, e come si relazionano?**

Non parte da zero. Una parte del modello **esiste già in codice** nella Phase 1
del Runtime; questo documento la descrive fedelmente e marca ciò che è ancora da
costruire. Ogni oggetto porta un badge di stato e, dove esiste, il tipo Java che
lo realizza.

Legenda stato:

- 🟢 **Implementato** — esiste in `agent-core` (Phase 1), è il contratto vigente
- 🟡 **Parziale** — parte in codice, parte da definire
- ⚪ **Da definire** — appartiene a un repository non ancora avviato

Regola d'oro (ADR-004): questo modello è **condiviso** tra tutti i repository
della piattaforma. Il Runtime possiede l'implementazione degli oggetti di
esecuzione; il Control Plane, lo Studio, il Gateway e l'Operator possiedono i
propri, ma tutti parlano *questo* vocabolario.

> **Nota di freschezza.** Questo documento è uno "0.1 Draft" scritto quando
> Control Plane/Studio/Compose erano repository non ancora avviati (badge ⚪).
> Il *vocabolario* degli oggetti (§1-17) resta accurato ed è stato riverificato;
> le sezioni §18-20 sul "cosa esiste in quale repo" sono invece datate — oggi
> Studio, Control Plane e Compose esistono e sono in stato MVP. Per lo stato
> corrente cross-repo, la fonte di verità è
> [`platform-handoff.md`](platform-handoff.md), non le sezioni finali qui sotto.

---

## 1. Mappa degli oggetti

```
                        AIWorkload  (kind: AGENT | WORKFLOW | EVALUATOR | ...)
                             │
                    ┌────────┴─────────┐
                    │  WorkloadManifest │  apiVersion / kind / metadata / spec
                    └────────┬─────────┘
                             │ spec (sealed)
                        ┌────▼─────┐
                        │ AgentSpec │
                        └────┬─────┘
      ┌───────────┬─────────┼──────────┬────────────┬──────────────┐
      ▼           ▼         ▼          ▼            ▼              ▼
  Capability   ModelSpec  MemoryLayer McpServerSpec RuntimeSpec  guardrails/roles
      │                                   │
      │ implementedBy                     │ fornisce
      ▼                                   ▼
   (Skill interna)                     Tool (esterni via MCP)
                                          ▲
                                          │ oppure
                                    Tool (@AgentTool, library mode)

  Bundle  ──contiene──▶  WorkloadManifest + prompts + skills + policies
     │
     │ pubblicato nel Registry, deployato come
     ▼
  Runtime (1 processo = 1 Agent, ADR-001)
     │ pubblica via A2A
     ▼
  AgentCard (Capabilities + Skills + Health + Version)
     │ indicizzata dal
     ▼
  Catalog ──consultato da──▶ GatewayRoute
```

---

## 2. AIWorkload — 🟢 Implementato

L'unità eseguibile radice. Tutto ciò che la piattaforma gestisce è un workload.

**Codice:** `WorkloadManifest` (record), `WorkloadKind` (enum),
`WorkloadMetadata` (record), `WorkloadSpec` (sealed interface).

```
WorkloadManifest
  apiVersion : String   // "gargantua.ai/v1" — l'unico oggi supportato
  kind       : WorkloadKind
  metadata   : WorkloadMetadata
  spec       : WorkloadSpec   // sealed: oggi solo AgentSpec
```

`WorkloadKind` — `AGENT`, `WORKFLOW`, `EVALUATOR`, `CLASSIFIER`, `SERVICE`,
`BATCH_JOB`. Solo `AGENT` ha una spec implementata in Phase 1; gli altri kind
sono dichiarati ma non ancora realizzati (`WorkloadSpec` è `sealed permits
AgentSpec`).

`WorkloadMetadata` — `name`, `version` (entrambi obbligatori), `description`,
`owner`, `labels` (Map). Il `name:version` è l'identità (`coordinates()`).

**Forma Kubernetes** deliberata (`apiVersion`/`kind`/`metadata`/`spec`): è ciò
che rende naturale, in Phase 5, mapparlo su una CRD nell'Operator.

**Invariante:** `kind` e `spec.kind()` devono coincidere — il costruttore lo
impone. Un manifest AGENT con una spec diversa è rifiutato all'atto della
costruzione.

Dettaglio completo dello schema del manifest: [`agent-manifest.md`](agent-manifest.md).

---

## 3. Agent — 🟢 Implementato

Il primo (e oggi unico) workload di prima classe. Combinazione dichiarativa di
comportamento, capacità, strumenti, memoria e policy.

**Codice:** `AgentSpec implements WorkloadSpec`.

```
AgentSpec
  runtime       : RuntimeSpec           // dove/come gira
  capabilities  : List<Capability>      // contratto esterno
  model         : ModelSpec             // quale modello
  mcpServers    : List<McpServerSpec>   // sorgenti di tool (runtime mode)
  memoryLayers  : Set<MemoryLayer>      // WORKING / EPISODIC / KNOWLEDGE
  defaultSkill  : String                // skill di ingresso
  guardrails    : Map<String,Object>    // configurazione guardrail
  allowedRoles  : Set<String>           // RBAC a livello di workload
  loadout       : Loadout               // knowledge/memoryScopes/skills/resources equipaggiati
  cognition     : Cognition             // PACT Core — vedi §3.1
  contract      : Contract              // PACT Core — vedi §3.1
  interfaces    : List<InterfaceEndpoint> // PACT Core — vedi §3.1
```

**Invarianti** (imposti dal costruttore): niente nomi di capability duplicati,
niente nomi di server MCP duplicati. `memoryLayers` vuoto significa "usa tutti i
layer" (`usesAllMemoryLayers()`), non "nessuno".

**Nota di enforcement onesta:** non tutto è ancora applicato a runtime.
`allowedRoles`, `memoryLayers` e `loadout` a livello di workload sono *parsati*
ma non ancora *enforced* — vedi `ManifestProperties.unappliedFields()` e il
project-handoff §4/§6. Il domain model li definisce; l'enforcement completo è
lavoro ancora aperto nel Runtime.

### 3.1 Cognition / Contract / Interfaces — 🟢 Implementato (pacchetto `ai.gargantua.core.pact`)

Tre campi aggiuntivi, additivi e opzionali, che implementano i pilastri
Cognition/Contract/Interfaces di [PACT](../../PACT_v0.4_Agent_Contract_Specification.md)
(la spec di descrizione agente che questo progetto sta scrivendo — vedi il file
alla radice del repo). A differenza di `allowedRoles`/`memoryLayers`/`loadout`
sopra, questi **non sono un gap da chiudere**: PACT stesso dice che una
dichiarazione non è una prova di capacità (PACT §31) — il runtime li parsa e li
riporta, senza enforcement previsto.

```
Cognition        : modalities, capabilities, models, requirements
Contract         : autonomy (enum Autonomy: PASSIVE..AUTONOMOUS), permissions
InterfaceEndpoint: protocol, endpoint, version
```

Gli altri due pilastri PACT (Identity, Purpose) non hanno un campo dedicato:
`PactManifest.from(WorkloadManifest)` li deriva da `metadata.owner` e
`metadata.description`, che rispondono già a domande equivalenti — vedi
`docs/architecture/agent-manifest.md` per la tabella di mappatura completa.

---

## 4. Capability — 🟢 Implementato

Il **contratto esterno**. Ciò che il workload dichiara di saper fare, pubblicato
sulla agent card A2A e (in futuro) indicizzato dal Catalog. Ha uno schema di
input e di output.

**Codice:** `Capability` (record).

```
Capability
  name         : String
  description  : String
  version      : String
  inputSchema  : String   // JSON Schema, nullable
  outputSchema : String   // JSON Schema, nullable
  implementedBy: String   // quale agente/versione la realizza
  tags         : Set<String>
```

**Distinzione fondamentale — Capability vs Skill** (vedi visione §3): la
Capability è il *cosa* pubblico; la Skill è il *come* interno. Una capability
può essere implementata da più skill; una skill può non corrispondere ad alcuna
capability pubblicata. Il sistema (in particolare il Gateway) **ragiona sulle
capability, non sui nomi degli agenti**.

Relazione: `Capability.implementedBy` punta a un `Agent` (per
`name:version`). Il Catalog invertirà questa relazione: da capability → elenco
di runtime che la offrono.

---

## 5. Skill — 🟢 Implementato

Il **comportamento interno**. Come l'agente realizza una capability. Non
necessariamente visibile all'esterno.

**Codice:** `AgentSkill`, `SkillCard`, `SkillMeta`, `SkillRegistry`,
`SkillSource` (pacchetto `core.skill`). Definite in file `SKILL.md` validati a
build-time da `agent-skill-linter-maven-plugin`.

Sull'agent card A2A la skill appare come `AgentCard.AgentSkill` (`id`, `name`,
`description`, `domain`, `tags`, `examples`) — ma questo è ciò che l'agente
sceglie di *esporre* per la discovery, non il meccanismo interno di routing.

Relazione: una Skill appartiene a un Agent. `AgentSpec.defaultSkill` nomina la
skill di ingresso. Il routing tra skill è responsabilità dello Skill
Engine/Router nel Runtime (vedi [`skills-and-routing.md`](../skills-and-routing.md)).

---

## 6. Tool — 🟢 Implementato

L'azione concreta che una skill può invocare. Due sorgenti, **stessa pipeline**
(ADR-006):

1. **Library mode** — metodi `@AgentTool` compilati, scansionati da
   `ToolRegistry`. Portano annotazioni cross-cutting (`@RequiresRole`,
   `@RequiresApproval`, `@CacheableToolResult`, `@ToolRetry`).
2. **Runtime mode** — tool esterni esposti da server MCP, forniti da
   `ToolProvider` (un `McpToolProvider` per server dichiarato).

**Codice:** `AgentTool`, `ToolDefinition`, `ToolParameter`, `ToolProvider`,
`ToolExecutionContext` e le annotazioni (pacchetto `core.tool`).

**Invariante (dal handoff §4):** `ToolDefinition.approvalShowParameters` non va
clonato — l'uguaglianza dei record confronta gli array per identità e i chiamanti
condividono un unico array vuoto. E i placeholder tool locale vs remoto: il primo
provider che rivendica un nome vince, quindi **un tool Java locale oscura uno
remoto omonimo**; le collisioni si loggano, non si rifiutano.

Relazione: un Tool è consumato da una Skill a runtime. In runtime mode i Tool
arrivano dai `McpServerSpec` dell'`AgentSpec`.

---

## 7. MCPServer — 🟢 Implementato

Dichiarazione di un server MCP esterno da cui l'agente consuma tool.

**Codice:** `McpServerSpec`, `McpAuth`, `McpTransport` (pacchetto `core.mcp`).

```
McpServerSpec
  name         : String
  transport    : McpTransport   // STDIO | HTTP | SSE
  command,args,env : ...        // per STDIO (child process)
  url          : String         // per HTTP/SSE
  auth         : McpAuth        // none | bearer | ...
  allowedTools : Set<String>    // allowlist; vuota = tutti
  enabled      : boolean
```

**Invarianti:** STDIO richiede `command`; HTTP/SSE richiedono `url`. `permits()`
applica l'allowlist. La configurazione si **autora nello Studio**; il manifest la
trasporta; il Runtime la consuma e basta (visione §5).

**Comportamento a runtime (ADR-006):** un server MCP irraggiungibile viene
loggato e saltato — non blocca l'avvio dell'agente — salvo modalità `fail-fast`.
`ToolRegistry` chiude i provider allo shutdown, così i child process stdio non
sopravvivono all'agente.

---

## 8. Bundle — 🟢 Implementato (firma esclusa)

Il **contratto tra Control Plane e Runtime**: un artefatto immutabile,
versionato, distribuibile che impacchetta un workload dichiarativo.

**Codice:** `BundleDescriptor` (record, in `core.bundle`); formato e loader nel
modulo `agent-bundle` (`ManifestParser`, `BundleLoader`, protezione zip-slip,
checksum SHA-256).

```
BundleDescriptor
  name, version : String
  checksum      : String   // SHA-256 — 🟢 presente
  signature     : String   // 🟡 campo presente, verifica NON implementata
  runtimeImage  : String   // immagine su cui girare
  createdAt     : Instant
  labels        : Map<String,String>
```

Contenuto del `.gbundle` (vedi visione §10): `manifest.yaml`, `skills/`,
`prompts/`, `memory/`, `mcp/`, `policies/`, `metadata.json`.

**ADR-003 (vincolante):** il bundle **non contiene codice eseguibile**. La firma
ha senso solo su dati dichiarativi. Chi ha bisogno di tool Java custom costruisce
un'immagine runtime in modalità libreria (build-time), non carica codice
dinamicamente.

**Aperto nel Runtime:** verifica della **firma** (`isSigned()` esiste, la
verifica no). Vedi handoff §6.

---

## 9. Runtime — 🟢 Implementato

Il processo esecutivo. Riceve un workload e lo esegue.

**Codice:** modulo `agent-runtime` (`GargantuaRuntime`, `ManifestProperties`,
CLI `run`/`validate`, immagine generica). `RuntimeSpec` (in `core.workload`)
dichiara `image` e `minVersion`.

**ADR-001 (vincolante):** **un processo runtime ospita esattamente un agente.**
L'isolamento è isolamento di processo, non di classloader. Questo è ciò che
mantiene `AgentProperties`/`SkillRegistry`/`ToolRegistry` come singleton e mappa
1:1 su un Pod Kubernetes. Il multiplexing è responsabilità dello Scheduler
(Phase 4), non del Runtime.

Relazione: un Runtime carica un Bundle → istanzia un Agent → pubblica una
AgentCard via A2A → produce Runtime State.

---

## 10. Runtime State vs Configuration State — 🟢 concetto / 🟡 store

Separazione fondamentale (visione §11):

- **Configuration State** — agent definition, bundle, versioni. Immutabile,
  posseduto dal **Registry** (Control Plane). Nel Runtime arriva via bundle.
- **Runtime State** — sessioni, conversazioni, memoria, checkpoint, execution
  history. Posseduto da uno **State Store**. Nel Runtime è gestito dai layer di
  memoria.

Il Runtime **non** è mai la fonte di verità della configuration state (ADR-004).

---

## 11. Memory — 🟢 Implementato

Lo stato conoscitivo dell'agente, in tre layer.

**Codice:** `MemoryLayer` (enum), `WorkingMemoryPort`, `EpisodicMemoryPort`,
`KnowledgeMemoryPort`, `ComposedMemory` (pacchetto `core.memory`);
implementazioni in `agent-memory-sdk` (in-memory, Mongo, Redis, embedded).

```
MemoryLayer
  WORKING    // contesto della conversazione corrente (Short)
  EPISODIC   // preferenze e storia persistenti (Long)
  KNOWLEDGE  // RAG / conoscenza aziendale
```

Relazione: `AgentSpec.memoryLayers` seleziona quali layer l'agente usa (vuoto =
tutti). Mappatura sui nomi della visione §7: Working→Short, Episodic→Long,
Knowledge→RAG. Vedi [`memory-system.md`](../memory-system.md).

---

## 12. Model — 🟢 Implementato

Quale modello LLM usare, riferito **per nome** — mai con credenziali (ADR-005).

**Codice:** `ModelSpec` (record).

```
ModelSpec
  primary, fallback : String   // nomi di modello
  routing           : String
  temperature       : Double   // 0.0..2.0
  maxTokens         : Integer
```

`ModelSpec.inherit()` = "usa il default del runtime". Endpoint e API key vengono
dall'ambiente via `SecretResolver`, non dal bundle. Vedi
[`llm-configuration.md`](../llm-configuration.md).

---

## 13. A2A / AgentCard — 🟢 Implementato

Il meccanismo con cui ogni runtime **si autoannuncia**: metadata, capability,
skill esposte, health, versione.

**Codice:** `AgentCard`, `A2AClient`, `A2ATask` (pacchetto `core.a2a`).

```
AgentCard
  name, description, version, url, protocolVersion
  capabilities : AgentCapabilities(streaming, pushNotifications)
  skills       : List<AgentSkill>
  provider     : AgentProvider
  authSchemes  : List<AgentAuthScheme>
```

Flusso (visione §12): `Runtime → A2A → Catalog → Gateway`. Oggi il Runtime
produce la card; il `CatalogRegistrar` che la pubblica su un Catalog è previsto
ma non implementato (nessun Catalog esiste ancora — handoff §6).

---

## 14. Policy — 🟡 Parziale

Regole di sicurezza, privacy, compliance, costo e limiti.

**Codice oggi:** enforcement locale distribuito nel Runtime — annotazioni
`@RequiresRole`/`@RequiresApproval` sui tool, `allowedRoles` nell'`AgentSpec`,
guardrail (`AgentSpec.guardrails` + `GuardrailPipeline` nell'engine), cost
tracking. Pacchetti `core.security`, guardrail nell'engine.

**Da definire (⚪ Control Plane):** un **Policy Manager** centralizzato e un
oggetto `Policy` di prima classe che il Control Plane *definisce* e il Runtime
*applica* (ADR-004). Oggi non esiste un tipo `Policy` unico: è la prossima
formalizzazione naturale.

---

## 15. Deployment — ⚪ Da definire

L'atto di far girare una specifica versione di un bundle in un ambiente, con una
strategia di rollout.

**Ownership:** `gargantua-control-plane` (Deployment Manager) definisce *cosa* e
*dove*; `gargantua-operator` (Phase 5) lo traduce in risorse Kubernetes (1 Pod =
1 agente, ADR-001). Nulla di questo esiste in codice oggi.

Attributi previsti: bundle `coordinates`, ambiente target, strategia
(blue/green, canary, progressive), stato del rollout, cronologia per rollback.

---

## 16. GatewayRoute — ⚪ Da definire

La regola che porta una richiesta client verso il runtime giusto **per
capability**, non per nome di agente.

**Ownership:** `gargantua-gateway` (Phase 4). Configurata dal Gateway Designer
nello Studio, risolta interrogando il Catalog. Supporta intent routing,
capability routing, version routing (blue/green, canary). Nulla in codice oggi.

Relazione: `GatewayRoute` → `Capability` → (via Catalog) → `Runtime` endpoint.

---

## 17. Evaluation — ⚪ Da definire

Misura della qualità di un workload: dataset, benchmark, confronto versioni,
scoring (accuracy, latency, costo, hallucination rate, tool success rate).

**Ownership:** Evaluation Studio in `gargantua-studio` (Phase 3). `EVALUATOR` è
già un `WorkloadKind` riservato, ma senza spec implementata. Nulla di runnabile
oggi.

---

## 18. Ciclo di vita di un Agent (end-to-end)

Mette in fila gli oggetti sopra, marcando dove ciascuno stadio vive oggi:

```
1. Design      Studio ⚪    → AgentSpec + Capability + Skill + McpServerSpec
2. Test        Studio ⚪    → Playground
3. Evaluate    Studio ⚪    → Evaluation
4. Compile     Studio/CP ⚪ → Bundle (manifest + asset), checksum 🟢, firma 🟡
5. Register    Control Plane ⚪ → Registry (versione immutabile)
6. Deploy      Control Plane ⚪ / Operator ⚪ → Runtime (1 Pod)
7. Execute     Runtime 🟢   → carica Bundle, istanzia Agent, esegue Skill+Tool
8. Announce    Runtime 🟢   → AgentCard via A2A
9. Index       Catalog ⚪   → capability → endpoint
10. Route      Gateway ⚪   → GatewayRoute risolve capability → Runtime
11. Serve      Client → Gateway → Runtime
```

Gli stadi 7 e 8 esistono in codice (Phase 1). Tutto il resto è dichiarato in
questo modello ma appartiene a repository non ancora avviati.

---

## 19. Tabella riepilogativa

| Oggetto | Stato | Codice / Repository |
|---|---|---|
| AIWorkload | 🟢 | `WorkloadManifest`, `WorkloadKind`, `WorkloadSpec` |
| Agent | 🟢 | `AgentSpec` |
| Capability | 🟢 | `Capability` |
| Skill | 🟢 | `core.skill.*`, `SKILL.md` + linter |
| Tool | 🟢 | `core.tool.*`, `ToolProvider` |
| MCPServer | 🟢 | `McpServerSpec`, `McpAuth`, `McpTransport` |
| Bundle | 🟢 / 🟡 firma | `BundleDescriptor`, modulo `agent-bundle` |
| Runtime | 🟢 | `RuntimeSpec`, modulo `agent-runtime` |
| Memory | 🟢 | `MemoryLayer`, `core.memory.*`, `agent-memory-sdk` |
| Model | 🟢 | `ModelSpec` |
| A2A / AgentCard | 🟢 | `core.a2a.*` |
| Policy | 🟡 | enforcement locale nel Runtime; Policy Manager ⚪ (Control Plane) |
| Deployment | ⚪ | Control Plane + Operator |
| GatewayRoute | ⚪ | Gateway |
| Evaluation | ⚪ | Studio (`EVALUATOR` kind riservato) |

---

## 20. Prossimo passo (storico — vedi la nota di freschezza in cima al documento)

*Questa sezione descrive cosa era pianificato quando il documento è stato
scritto (0.1 Draft). [`gargantua-studio`](../../../gargantua-studio) è stato
da allora avviato ed è oggi in stato MVP: produce esattamente gli oggetti 🟢 di
questo modello (`AgentSpec`, `Capability`, `McpServerSpec`, skill), rispettando
il formato congelato in [`agent-manifest.md`](agent-manifest.md). Per il
prossimo passo reale della piattaforma, vedi la roadmap in
[`platform-handoff.md`](platform-handoff.md) §7.*
