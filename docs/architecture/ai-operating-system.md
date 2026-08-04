# Gargantua AI Operating System
## Architecture Vision Document

**Versione:** 0.2 Draft
**Stato:** Architecture Vision
**Obiettivo:** definire la macro architettura evolutiva di Gargantua come piattaforma distribuita per il ciclo di vita, esecuzione e governance di workload AI.

---

# 1. Executive Summary

Gargantua nasce come framework per la costruzione ed esecuzione di agenti AI, ma la sua evoluzione naturale è quella di diventare un **AI Operating System distribuito**.

Un sistema operativo tradizionale fornisce:

- gestione dei processi
- memoria
- sicurezza
- networking
- discovery
- scheduling
- isolamento

Gargantua applica gli stessi principi al mondo AI.

Gli agenti AI rappresentano il primo tipo di workload gestito dalla piattaforma.

L'obiettivo non è quindi creare un ulteriore framework agentico, ma fornire un livello infrastrutturale capace di:

- definire workload AI dichiarativi
- eseguirli su runtime distribuiti
- governarne il ciclo di vita
- gestire sicurezza e policy
- scoprire dinamicamente capacità disponibili
- orchestrare interazioni tra agenti

---

# 2. Visione

## Da Agent Framework

Approccio tradizionale:

```
Developer
   ↓
Codice
   ↓
Agent
   ↓
Execution
```

## A AI Operating System

Approccio Gargantua:

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

Un workload AI è una unità eseguibile gestita dalla piattaforma.

Esempi:

- Agent
- Workflow AI
- Evaluator
- Classifier
- AI Service
- Batch AI Job

## Agent

Un Agent è il primo workload supportato.

Un agente è una combinazione di:

- comportamento
- capacità
- strumenti
- memoria
- policy
- conoscenza

## Capability

La capability rappresenta ciò che un workload AI è in grado di fornire.

Esempio:

```
Capability:
  refund-payment

Implementazione:
  Customer Agent v1.2
```

Il sistema non ragiona principalmente sul nome dell'agente.
Ragiona sulle capability disponibili.

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

## Ruolo

Il Control Center della piattaforma.
È il punto unico per progettare, testare e governare workload AI.

Lo Studio non esegue direttamente gli agenti.

## Capability

### Workload Designer

Creazione di:

- Agent
- Workflow
- AI Service
- Evaluator

### Agent Designer

Configurazione:

- Role
- Prompt
- Skills
- Memory
- MCP
- RAG
- Guardrail
- Model

### Capability Designer

Definizione delle capability offerte.

Esempio:

```yaml
capability:
  name: refund-payment
  description:
    Gestione richiesta rimborso pagamento
  input:
    paymentId
  output:
    refundStatus
```

### Playground

Permette:

- test conversazionali
- debugging
- tracing
- analisi comportamento

### Evaluation Studio

Permette:

- dataset di test
- benchmark
- confronto versioni
- scoring automatico

Metriche:

- accuracy
- latency
- costo
- hallucination rate
- tool success rate

### Gateway Designer

Configurazione:

- esposizione API
- A2A
- routing
- autenticazione
- rate limit
- priorità

### Security Designer

Configurazione:

- RBAC
- tenant
- ruoli
- applicazioni autorizzate
- policy dati
- audit

---

# 6. Control Plane

Il Control Plane gestisce lo stato desiderato della piattaforma.

Principio:

```
Desired State
     ↓
Control Plane
     ↓
Runtime State
```

## 6.1 Agent Registry

Repository degli artefatti.

Contiene:

- Agent Bundle
- versioni
- metadata
- checksum
- firma digitale

Responsabilità:

- storage
- versioning
- rollback
- promotion
- download

Non contiene stato runtime.

## 6.2 Agent Catalog

Sistema di discovery.

Non contiene codice o bundle.

Contiene:

- capability
- ownership
- descrizione
- SLA
- tag
- health
- endpoint
- versioni

Il Catalog viene popolato tramite discovery A2A.

## 6.3 Policy Manager

Gestione centralizzata delle policy.

Include:

- RBAC
- sicurezza
- compliance
- cost control
- privacy
- governance

## 6.4 Deployment Manager

Responsabile del lifecycle.

Supporta:

- deploy
- rollback
- rollout progressivo
- canary release
- promotion ambiente

---

# 7. AI Kernel

Il cuore della piattaforma.
Fornisce servizi comuni a tutti i workload AI.

## 7.1 Scheduler

Responsabilità:

- assegnazione workload
- selezione runtime
- priorità
- quota
- bilanciamento

## 7.2 Context Manager

Gestione del contesto:

- conversazione
- stato temporaneo
- informazioni di reasoning

## 7.3 Memory Manager

Gestisce:

**Short Memory** — contesto conversazione corrente.

**Long Memory** — preferenze e informazioni persistenti.

**Knowledge Memory** — RAG e conoscenza aziendale.

## 7.4 Skill Engine

Gestisce:

- caricamento skill
- discovery
- execution
- versioning

## 7.5 MCP Manager

Gestione:

- MCP Server
- autenticazione
- tool discovery
- lifecycle

## 7.6 Policy Engine

Applicazione delle policy:

- sicurezza
- privacy
- compliance
- costi
- limiti

## 7.7 A2A Layer

Ogni workload AI espone:

- metadata
- capability
- endpoint
- stato

tramite protocollo A2A.

---

# 8. Agent Gateway

## Visione

L'Agent Gateway è il punto di ingresso unico della piattaforma.

Non è un semplice API Gateway.
È il dispatcher del sistema operativo AI.

## Responsabilità

- Authentication
- Authorization
- Intent Routing
- Capability Routing
- Version Routing
- Rate Limiting
- Cost Control
- Observability

## Intent Routing

Esempio:

```
Input:
  Vorrei contestare un pagamento

Routing:
  Intent
    ↓
  Fraud Capability
    ↓
  Fraud Agent
```

## Capability Routing

Il Gateway non conosce staticamente gli agenti.
Interroga il Catalog.

## Version Routing

Supporta:

- blue/green deployment
- canary release
- rollback

---

# 9. Gargantua Runtime

Il Runtime è il processo esecutivo.
Riceve un workload e lo esegue.

Responsabilità:

- loading bundle
- inizializzazione agent
- skill execution
- memory management
- MCP invocation
- guardrail execution
- telemetry

---

# 10. Agent Bundle

Il Bundle è il contratto tra Control Plane e Runtime.

Esempio:

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

Caratteristiche:

- immutabile
- versionato
- firmato
- distribuibile

---

# 11. Runtime State

Separazione fondamentale.

## Configuration State

Gestito da Registry:

- agent definition
- bundle
- versioni

## Runtime State

Gestito da State Store:

- sessioni
- conversazioni
- memoria
- checkpoint
- execution history

---

# 12. Agent Discovery tramite A2A

Ogni runtime pubblica automaticamente:

```
Agent Metadata
      +
Capabilities
      +
Health
      +
Version
```

Flusso:

```
Runtime
   ↓
  A2A
   ↓
Catalog
   ↓
Gateway
```

---

# 13. Flusso completo operativo

```
Creazione
Utente crea un Agent nello Studio.
   ↓
Configura:
  capability
  skill
  MCP
  RAG
  memory
  policy
   ↓
Test
  Playground
   ↓
Evaluation
  Dataset automatici
   ↓
Publish
  Compiler genera bundle
   ↓
Registry
  Versione immutabile
   ↓
Deployment
  Runtime riceve workload
   ↓
Discovery
  A2A pubblica capability
   ↓
Catalog
  Indicizzazione
   ↓
Gateway
  Routing disponibile
   ↓
Client
  Invoca capability
```

---

# 14. Gap rispetto a Gargantua attuale

## Disponibile

| Capability | Stato |
|-----------|-------|
| Agent Engine | Presente |
| Skill | Presente |
| MCP | Presente |
| Memory | Presente |
| Guardrail | Presente |
| Routing | Presente |
| Spring Boot Runtime | Presente |
| RAG | Parziale |

## Da sviluppare

- AI Workload Model
- Agent Manifest
- Agent Bundle
- Compiler
- Registry completo
- Catalog
- Deployment Manager
- Runtime Discovery
- Agent Gateway
- Policy Engine
- Visual Studio
- RBAC Management
- Capability Model
- Kubernetes Operator
- CRD
- GitOps

---

# 15. Roadmap

## Phase 1 — Gargantua Runtime Foundation

- consolidamento engine
- agent manifest
- bundle format
- CLI

## Phase 2 — Control Plane

- registry
- catalog
- deployment
- API

## Phase 3 — Studio

- visual designer
- playground
- evaluation
- governance

## Phase 4 — AI Kernel

- scheduler
- gateway
- policy engine
- discovery

## Phase 5 — Distributed AI Operating System

- Kubernetes operator
- CRD
- federation
- marketplace
- multi-runtime

---

# 16. Visione finale

Gargantua diventa una piattaforma distribuita per workload AI.

Gli agenti non sono applicazioni isolate.
Sono processi intelligenti gestiti da un sistema operativo AI.

Il modello finale:

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

L'obiettivo non è creare un altro framework agentico.

L'obiettivo è creare l'infrastruttura su cui i workload AI del futuro vengono:

- progettati
- distribuiti
- governati
- eseguiti
- osservati
- orchestrati

---

# 17. Prossimo passo

Il documento successivo è `gargantua-domain-model.md`, che risponde alla domanda fondamentale:
**quali sono gli oggetti del sistema operativo?**

Definirà formalmente:

- AIWorkload
- Agent
- Capability
- Skill
- Tool
- MCPServer
- Bundle
- Runtime
- Deployment
- GatewayRoute
- Policy
- Memory
- Evaluation

con relazioni, lifecycle e API. Quello sarà il vero contratto su cui costruire tutto il resto.
