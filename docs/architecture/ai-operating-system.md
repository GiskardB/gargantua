# Gargantua AI Operating System
## Architecture Vision Document

**Versione:** 0.3 Draft
**Stato:** Architecture Vision — Phase 1 implementata
**Obiettivo:** definire la macro architettura evolutiva di Gargantua come piattaforma distribuita per il ciclo di vita, esecuzione e governance di workload AI.

---

# 1. Executive Summary

Gargantua nasce come framework per la costruzione ed esecuzione di agenti AI, ma la sua evoluzione naturale è quella di diventare un **AI Operating System distribuito**.

Un sistema operativo tradizionale fornisce gestione dei processi, memoria, sicurezza, networking, discovery, scheduling e isolamento. Gargantua applica gli stessi principi al mondo AI.

Gli agenti AI rappresentano il primo tipo di workload gestito dalla piattaforma.

L'obiettivo non è creare un ulteriore framework agentico, ma fornire un livello infrastrutturale capace di:

- definire workload AI dichiarativi
- eseguirli su runtime distribuiti
- governarne il ciclo di vita
- gestire sicurezza e policy
- scoprire dinamicamente capacità disponibili
- orchestrare interazioni tra agenti

---

# 2. Visione

## Da Agent Framework

```
Developer → Codice → Agent → Execution
```

## A AI Operating System

```
AI Workload Definition
   ↓
Control Plane
   ↓
AI Kernel
   ↓
Execution Runtime
   ↓
Capability Discovery
```

---

# 3. Concetti fondamentali

## AI Workload

Un workload AI è una unità eseguibile gestita dalla piattaforma: Agent, Workflow AI, Evaluator, Classifier, AI Service, Batch AI Job.

## Agent

Un Agent è il primo workload supportato. È una combinazione di comportamento, capacità, strumenti, memoria, policy e conoscenza.

## Capability vs Skill

Distinzione fondamentale, spesso confusa:

- **Capability** — il contratto *esterno*. Ciò che il workload dichiara di saper fare, pubblicato sulla agent card A2A e indicizzato dal Catalog. Ha uno schema di input e output.
- **Skill** — il comportamento *interno*. Come l'agente realizza quel contratto. Non è necessariamente visibile all'esterno.

Una capability può essere implementata da più skill; una skill può non corrispondere ad alcuna capability pubblicata.

```
Capability:      refund-payment
Implementazione: Customer Agent v1.2
```

Il sistema non ragiona principalmente sul nome dell'agente. Ragiona sulle capability disponibili.

---

# 4. Macro Architettura

```
                     +--------------------------------+
                     |       Gargantua Studio         |
                     +----------------+---------------+
                                      |
                               Desired State
                                      |
                     +----------------v---------------+
                     |        Control Plane           |
                     |--------------------------------|
                     | Registry                       |
                     | Catalog                        |
                     | Policy Manager                 |
                     | Deployment Manager             |
                     +----------------+---------------+
                                      |
                     +----------------v---------------+
                     |          AI Kernel             |
                     |--------------------------------|
                     | Scheduler                      |
                     | Context Manager                |
                     | Memory Manager                 |
                     | Skill Engine                   |
                     | MCP Manager                    |
                     | Policy Engine                  |
                     | A2A Layer                      |
                     +----------------+---------------+
                                      |
                +---------------------+---------------------+
                |                                           |
      +---------v---------+                       +---------v---------+
      | Gargantua Runtime |                       | Gargantua Runtime |
      +---------+---------+                       +---------+---------+
                |                                           |
                +---------------------+---------------------+
                                      |
                               Runtime State
                                      |
                            Observability Platform
                                      ^
                                      |
                              Agent Gateway
                                      ^
                                      |
                            Client Applications
```

---

# 5. Gargantua Studio

Il Control Center della piattaforma. Punto unico per progettare, testare e governare workload AI. **Lo Studio non esegue direttamente gli agenti.**

- **Workload Designer** — creazione di Agent, Workflow, AI Service, Evaluator
- **Agent Designer** — Role, Prompt, Skills, Memory, MCP, RAG, Guardrail, Model
- **Capability Designer** — definizione delle capability offerte
- **Playground** — test conversazionali, debugging, tracing
- **Evaluation Studio** — dataset, benchmark, confronto versioni, scoring (accuracy, latency, costo, hallucination rate, tool success rate)
- **Gateway Designer** — esposizione API, A2A, routing, autenticazione, rate limit, priorità
- **Security Designer** — RBAC, tenant, ruoli, applicazioni autorizzate, policy dati, audit

Esempio di capability definita nello Studio:

```yaml
capability:
  name: refund-payment
  description: Gestione richiesta rimborso pagamento
  input:  paymentId
  output: refundStatus
```

**Nota di design:** la configurazione del client MCP di un agente si autora qui. Lo Studio produce le dichiarazioni dei server MCP che finiscono nel manifest del bundle; il Runtime le consuma e basta.

---

# 6. Control Plane

Gestisce lo stato desiderato della piattaforma.

```
Desired State → Control Plane → Runtime State
```

## 6.1 Agent Registry

Repository degli artefatti: Agent Bundle, versioni, metadata, checksum, firma digitale.
Responsabilità: storage, versioning, rollback, promotion, download.
**Non contiene stato runtime.**

## 6.2 Agent Catalog

Sistema di discovery. **Non contiene codice o bundle.**
Contiene: capability, ownership, descrizione, SLA, tag, health, endpoint, versioni.
Viene popolato tramite discovery A2A.

## 6.3 Policy Manager

RBAC, sicurezza, compliance, cost control, privacy, governance.

## 6.4 Deployment Manager

Deploy, rollback, rollout progressivo, canary release, promotion tra ambienti.

---

# 7. AI Kernel

Il cuore della piattaforma. Fornisce servizi comuni a tutti i workload AI.

| Componente | Responsabilità |
|---|---|
| **Scheduler** | assegnazione workload, selezione runtime, priorità, quota, bilanciamento |
| **Context Manager** | conversazione, stato temporaneo, informazioni di reasoning |
| **Memory Manager** | Short (conversazione corrente), Long (preferenze persistenti), Knowledge (RAG) |
| **Skill Engine** | caricamento, discovery, execution, versioning delle skill |
| **MCP Manager** | server MCP, autenticazione, tool discovery, lifecycle |
| **Policy Engine** | sicurezza, privacy, compliance, costi, limiti |
| **A2A Layer** | metadata, capability, endpoint, stato di ogni workload |

---

# 8. Agent Gateway

Il punto di ingresso unico. Non è un semplice API Gateway: è il **dispatcher del sistema operativo AI**.

Responsabilità: Authentication, Authorization, Intent Routing, Capability Routing, Version Routing, Rate Limiting, Cost Control, Observability.

## Intent Routing

```
Input: "Vorrei contestare un pagamento"
   ↓ Intent
   ↓ Fraud Capability
   ↓ Fraud Agent
```

## Capability Routing

Il Gateway **non conosce staticamente gli agenti**. Interroga il Catalog.

## Version Routing

Blue/green deployment, canary release, rollback.

---

# 9. Gargantua Runtime

Il processo esecutivo. Riceve un workload e lo esegue.

Responsabilità: loading bundle, inizializzazione agent, skill execution, memory management, MCP invocation, guardrail execution, telemetry.

---

# 10. Agent Bundle

Il Bundle è **il contratto tra Control Plane e Runtime**.

```
customer-agent.gbundle
  manifest.yaml
  skills/
  prompts/
  memory/
  mcp/
  policies/
  metadata.json
```

Caratteristiche: immutabile, versionato, firmato, distribuibile.

---

# 11. Runtime State

Separazione fondamentale.

- **Configuration State** — gestito dal Registry: agent definition, bundle, versioni
- **Runtime State** — gestito dallo State Store: sessioni, conversazioni, memoria, checkpoint, execution history

---

# 12. Agent Discovery tramite A2A

Ogni runtime pubblica automaticamente `Agent Metadata + Capabilities + Health + Version`.

```
Runtime → A2A → Catalog → Gateway
```

---

# 13. Flusso completo operativo

```
Creazione — utente crea un Agent nello Studio
   ↓ Configura: capability, skill, MCP, RAG, memory, policy
   ↓ Test — Playground
   ↓ Evaluation — dataset automatici
   ↓ Publish — il Compiler genera il bundle
   ↓ Registry — versione immutabile
   ↓ Deployment — il Runtime riceve il workload
   ↓ Discovery — A2A pubblica le capability
   ↓ Catalog — indicizzazione
   ↓ Gateway — routing disponibile
   ↓ Client — invoca la capability
```

---

# 14. Stato attuale

## Implementato nel repository Runtime

| Componente | Stato |
|---|---|
| Agent Engine | Presente |
| Skill / Routing / Memory / Guardrail | Presente |
| MCP **server** (esporre tool) | Presente |
| MCP **client** (consumare tool) | **Phase 1** — modulo `agent-mcp-client` |
| AI Workload Model | **Phase 1** — `WorkloadManifest`, `WorkloadSpec`, `AgentSpec` |
| Capability Model | **Phase 1** — `Capability`, `CapabilityRegistry`, pubblicata su agent card |
| Agent Manifest | **Phase 1** — `gargantua.ai/v1`, forma Kubernetes |
| Agent Bundle + Loader | **Phase 1** — modulo `agent-bundle` |
| Runtime standalone | **Phase 1** — modulo `agent-runtime`, immagine generica |
| Secret referencing | **Phase 1** — `${secrets.NAME}` / `${env.NAME}` |
| ToolProvider SPI | **Phase 1** — sorgenti di tool componibili |
| RAG | Parziale |

Struttura a 9 moduli: `agent-core`, `agent-memory-sdk`, `agent-mcp-client`, `agent-bundle`, `agent-engine`, `agent-runtime`, `agent-mcp-server`, `agent-skill-linter-maven-plugin`, `agent-archetype`.

## Implementato negli altri repository (2026-08)

Il progetto è ormai **multi-repo** (vedi [platform-handoff.md](platform-handoff.md) per la
mappa completa e lo stato aggiornato).

| Componente | Repository | Stato |
|---|---|---|
| Control Plane (Registry + Catalog + Policy + Deployment) | `gargantua-control-plane` | **MVP** — Spring Boot 4.1 / Java 25, publish→index→discovery funzionante, usa `agent-core` condiviso |
| Studio (frontend) | `gargantua-studio` | **MVP** — React Flow / Monaco / Zustand, Agent Designer + Skill Designer, collegato al backend, fallback offline |
| Studio backend (BFF) | `gargantua-studio-backend` | **MVP** — costruisce manifest `gargantua.ai/v1` da form, gateway verso il Control Plane |
| Modello di dominio condiviso | `agent-core` jar (Maven Central) | **Fatto** — un solo modello canonico Spring-free, dipeso dai componenti JVM |
| Vertical slice locale | `gargantua-compose` | **Fatto** — Docker Compose Studio→backend→CP + Postgres + MinIO, **senza Kubernetes** |

## Da sviluppare

Prossime migliorie decise (dettaglio e razionale in
[13-open-source-patterns.md](13-open-source-patterns.md) e in
[platform-handoff.md](platform-handoff.md)): **Agent Loadout** nel manifest, envelope di
governance, event model di esecuzione + trace, runtime supervisor + budget, agent lifecycle
con gate di evaluation. Poi: Compiler, Deployment Manager completo, Agent Gateway
(valutazione di `agentgateway`), RBAC Management, Kubernetes Operator, CRD, GitOps (Phase 5).

Rimasti nel Runtime: verifica della **firma** dei bundle (il checksum SHA-256 c'è già),
enforcement di RBAC e memory layer a livello di workload, `CatalogRegistrar` verso il Catalog.

---

# 15. Le due modalità di consegna

Questa è la decisione strutturale della Phase 1 e va capita prima di toccare il codice.

| | **Library mode** | **Runtime mode** |
|---|---|---|
| Chi crea l'agente | Sviluppatore, in Java | Studio, dichiarativamente |
| Da dove vengono i tool | Metodi `@AgentTool` compilati | Server MCP dichiarati nel manifest |
| Artefatto | L'applicazione dello sviluppatore | Bundle firmato |
| Immagine Docker | Custom, prodotta dal team | Generica `gargantua-runtime` |

Entrambe passano per la **stessa pipeline di esecuzione**: cambia solo la sorgente dei tool.

Il modello operativo: il bundle gira come immagine Docker che legge la configurazione dall'esterno. Chi ha bisogno di estendere Gargantua con costrutti custom resta in modalità libreria e produce un'immagine Docker propria, che dallo Studio viene poi agganciata a uno specifico agente.

---

# 16. Decisioni architetturali già prese

Sono vincolanti. Un log completo sta in `docs/architecture/runtime-decisions.md`.

**ADR-001 — Un runtime ospita esattamente un agente.**
L'isolamento tra agenti diventa isolamento di processo, che è reale, invece di isolamento per classloader, che non lo è. Mappa direttamente su un Deployment Kubernetes. Il multiplexing è responsabilità dello Scheduler.

**ADR-002 — La modalità libreria resta di prima classe.**
È il motivo per cui il framework è accessibile, ed è ciò da cui dipendono i consumatori esistenti e l'archetipo Maven. Non va sacrificata al runtime.

**ADR-003 — I bundle sono dichiarativi e non contengono codice eseguibile.**
La firma ha senso solo su dati dichiarativi: certificare l'origine di JAR arbitrari che poi girano in-process con pieni privilegi non stabilisce quasi nessun confine di fiducia, e renderebbe un futuro marketplace una vulnerabilità di supply chain. Chi ha bisogno di tool Java custom costruisce un'immagine runtime in modalità libreria — operazione di build-time, nessun caricamento dinamico.

**ADR-004 — Questo repository copre il Runtime e la metà esecutiva del Kernel.**
Studio, Control Plane, Gateway e operator Kubernetes stanno in repository separati. Il Control Plane *definisce* le policy, il Runtime le *applica*. Tenere netta questa linea è ciò che permette di costruire le fasi in parallelo invece che in sequenza.

**ADR-005 — I segreti si referenziano per nome, mai si incorporano.**
Un artefatto immutabile e firmato, promosso invariato da staging a produzione, non può portarsi dietro credenziali specifiche dell'ambiente. I placeholder non risolti restano **verbatim** di proposito: un errore di configurazione deve emergere come `${secrets.x}` visibile, non come credenziale silenziosamente vuota.

**ADR-006 — I tool arrivano da provider componibili.**
È la giuntura che fa reggere ADR-002 e ADR-003 insieme. `ToolProvider` copre le sorgenti esterne (un `McpToolProvider` per server MCP dichiarato); i metodi `@AgentTool` restano scansionati dal `ToolRegistry`, accanto ai gate RBAC, approvazione, cache e retry che leggono quelle stesse annotazioni. Un tool Java locale oscura uno remoto con lo stesso nome. Un server MCP irraggiungibile viene loggato e saltato, non blocca l'avvio dell'agente, salvo `fail-fast`.

---

# 17. Roadmap

| Fase | Contenuto | Stato |
|---|---|---|
| **Phase 1** — Runtime Foundation | consolidamento engine, agent manifest, bundle format, CLI | **Completata** |
| **Phase 2** — Control Plane | registry, catalog, deployment, API | Da iniziare, repository separato |
| **Phase 3** — Studio | visual designer, playground, evaluation, governance | — |
| **Phase 4** — AI Kernel | scheduler, gateway, policy engine, discovery | — |
| **Phase 5** — Distributed AI OS | Kubernetes operator, CRD, federation, marketplace, multi-runtime | — |

---

# 18. Visione finale

Gargantua diventa una piattaforma distribuita per workload AI.

Gli agenti non sono applicazioni isolate. Sono processi intelligenti gestiti da un sistema operativo AI.

```
AI Workload
     ↓
Gargantua Control Plane
     ↓
Gargantua AI Kernel
     ↓
Gargantua Runtime
     ↓
Capability Discovery
     ↓
Enterprise AI Ecosystem
```

L'obiettivo non è creare un altro framework agentico. È creare l'infrastruttura su cui i workload AI del futuro vengono progettati, distribuiti, governati, eseguiti, osservati e orchestrati.

---

# 19. Prossimo passo

Il documento successivo è [`gargantua-domain-model.md`](gargantua-domain-model.md), che risponde alla domanda fondamentale: **quali sono gli oggetti del sistema operativo?**

Definisce formalmente AIWorkload, Agent, Capability, Skill, Tool, MCPServer, Bundle, Runtime, Deployment, GatewayRoute, Policy, Memory, Evaluation — con relazioni, lifecycle e stato di implementazione. È il contratto condiviso su cui costruire tutti i repository della piattaforma.

Parte del modello esiste già in codice nella Phase 1 (`WorkloadManifest`, `AgentSpec`, `Capability`, `McpServerSpec`): il documento parte da lì, non da zero.
