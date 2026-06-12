# Gargantua Runtime Observability — Requirements Specification v1.0

## Scope

Tre feature coerenti che trasformano Gargantua da "framework che esegue agenti" a "runtime che mostra cosa succede dentro un agente":

1. **Event Bus** — infrastruttura interna per emettere, ascoltare, e persistere eventi runtime
2. **Session Recording & Replay** — registra ogni esecuzione, riproducila step-by-step
3. **Execution Graph Viewer** — pagina web che mostra il grafo di esecuzione in tempo reale

Tutte e tre sono **disabilitabili** via configurazione e **estendibili** via plugin/connettori.

---

## 1. Event Bus

### 1.1 Obiettivo

Ogni componente del pipeline emette eventi strutturati su un bus interno. I consumer (persistenza, UI, metriche, connettori esterni) si registrano come listener. L'event bus è il fondamento: senza di esso, recording e graph viewer non funzionano.

### 1.2 Requisiti Funzionali

#### RF-EB-01 — Interfaccia EventBus (in agent-core, zero Spring)

```java
package ai.gargantua.core.event;

/**
 * Internal event bus for runtime observability. Implementations
 * can be in-memory (default), Redis Pub/Sub, Kafka, or custom.
 *
 * Discovered via @ConditionalOnMissingBean — override with your own.
 */
public interface AgentEventBus {
    void publish(AgentEvent event);
    void subscribe(String eventType, AgentEventListener listener);
    void unsubscribe(String eventType, AgentEventListener listener);
}

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
```

#### RF-EB-02 — Record AgentEvent (in agent-core)

```java
package ai.gargantua.core.event;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(
    String eventId,           // UUID
    String type,              // vedi RF-EB-03
    String sessionId,
    String userId,
    String tenantId,          // nullable
    Instant timestamp,
    long durationMs,          // 0 se non applicabile
    Map<String, Object> data  // payload specifico per tipo
) {}
```

#### RF-EB-03 — Tipi di evento standard

| Tipo | Quando viene emesso | Payload `data` chiave |
|------|--------------------|-----------------------|
| `request.received` | Inizio pipeline | `message`, `forceSkill` |
| `guardrail.input.started` | Prima dei guardrail input | `guardrailCount` |
| `guardrail.input.result` | Dopo ogni guardrail | `guardrailName`, `verdict`, `reason` |
| `guardrail.input.blocked` | Se un guardrail blocca | `guardrailName`, `reason` |
| `routing.started` | Inizio routing | `strategy`, `skillCount` |
| `routing.completed` | Routing completato | `skillName`, `method`, `confidence` |
| `memory.compose.started` | Inizio composizione memoria | — |
| `memory.compose.completed` | Memoria composta | `workingCount`, `episodicCount`, `knowledgeCount`, `totalTokens` |
| `rag.search.started` | Inizio ricerca RAG | `knowledgeBase`, `query` |
| `rag.search.completed` | RAG completato | `chunksFound`, `bestScore` |
| `llm.call.started` | Inizio chiamata LLM | `provider`, `model`, `alias` |
| `llm.call.completed` | LLM ha risposto | `provider`, `model`, `inputTokens`, `outputTokens`, `durationMs` |
| `llm.call.failed` | LLM fallito | `provider`, `error`, `willRetry` |
| `llm.fallback.activated` | Failover a fallback | `originalProvider`, `fallbackProvider` |
| `tool.call.started` | Tool invocato | `toolName`, `arguments` |
| `tool.call.completed` | Tool completato | `toolName`, `resultPreview`, `durationMs` |
| `tool.call.failed` | Tool fallito | `toolName`, `error` |
| `tool.approval.required` | HITL richiesta | `toolName`, `requestId`, `message` |
| `tool.approval.resolved` | HITL risposta | `toolName`, `requestId`, `decision` |
| `guardrail.output.result` | Guardrail output | `guardrailName`, `verdict` |
| `response.completed` | Risposta finale | `skillUsed`, `totalTokens`, `estimatedCost`, `durationMs` |
| `memory.persist.completed` | Persistenza completata | `workingMemorySaved`, `chatHistorySaved` |
| `audit.recorded` | Audit event salvato | `auditEventId` |
| `flow.started` | Flow avviato | `flowName`, `stepCount` |
| `flow.step.completed` | Step flow completato | `flowName`, `stepIndex`, `skillName`, `durationMs` |
| `flow.completed` | Flow completato | `flowName`, `totalDurationMs`, `finalScore` |

#### RF-EB-04 — Implementazione in-memory (default)

```java
package ai.gargantua.autoconfigure.event;

/**
 * In-memory event bus using CopyOnWriteArrayList per event type.
 * Suitable for single-instance deployments.
 * For multi-instance: override with Redis Pub/Sub or Kafka connector.
 */
public class InMemoryEventBus implements AgentEventBus { ... }
```

- Thread-safe (CopyOnWriteArrayList per tipo)
- Async dispatch (Virtual Thread per evento)
- Buffer circolare degli ultimi N eventi (configurabile, default 1000) per query
- Zero dipendenze esterne

#### RF-EB-05 — Connettore plugin interface

```java
package ai.gargantua.core.event;

/**
 * Extension point for forwarding events to external systems.
 * Discovered via ServiceLoader or Spring @Component.
 *
 * Built-in connectors: InMemory, SSE (browser), Slf4j (logging)
 * Custom connectors: Redis Pub/Sub, Kafka, RabbitMQ, Webhook, etc.
 */
public interface EventBusConnector {
    String name();
    void initialize(Map<String, String> config);
    void forward(AgentEvent event);
    void shutdown();
}
```

Connettori built-in:

| Connettore | Cosa fa |
|------------|---------|
| `InMemoryConnector` | Salva nel buffer circolare (per query API e Graph Viewer) |
| `SseConnector` | Forwarda eventi a client SSE connessi (per Graph Viewer real-time) |
| `Slf4jConnector` | Logga ogni evento come JSON strutturato |

#### RF-EB-06 — Punti di emissione nel pipeline

Il `DefaultOrchestratorEngine` emette eventi in ogni step del pipeline. Ogni step è wrappato:

```java
eventBus.publish(new AgentEvent(uuid, "routing.started", sessionId, userId, tenantId, now, 0, Map.of(...)));
// ... esegui routing ...
eventBus.publish(new AgentEvent(uuid, "routing.completed", sessionId, userId, tenantId, now, duration, Map.of("skillName", skill, "method", method)));
```

Anche: `ChatStreamController` (per streaming events), `FlowExecutor` (per flow events), `ToolRegistry` (per tool events).

#### RF-EB-07 — Configurazione

```yaml
agent:
  event-bus:
    enabled: true              # false = nessun evento emesso, zero overhead
    buffer-size: 1000          # ultimi N eventi in memoria per query
    async: true                # dispatch asincrono (Virtual Thread)
    connectors:
      slf4j:
        enabled: false         # true = logga ogni evento come JSON
        level: DEBUG
      sse:
        enabled: true          # true = forwarda a client SSE (Graph Viewer)
```

### 1.3 Requisiti Non Funzionali

- **Zero overhead quando disabilitato** — `if (!eventBus.isEnabled()) return;` prima di ogni publish
- **Thread-safe** — publish da Virtual Thread multipli
- **Non bloccante** — publish non deve rallentare il pipeline (async dispatch)
- **Extensible** — aggiungere un connettore = implementare `EventBusConnector` + `@Component`

---

## 2. Session Recording & Replay

### 2.1 Obiettivo

Registrare ogni esecuzione come sequenza di eventi. Riprodurla step-by-step per debugging, auditing, e confronto.

### 2.2 Requisiti Funzionali

#### RF-SR-01 — SessionRecording (in agent-core)

```java
package ai.gargantua.core.event;

import java.time.Instant;
import java.util.List;

/**
 * A recorded session — the complete trace of an agent execution.
 * Saved to MongoDB for later replay and analysis.
 */
public record SessionRecording(
    String recordingId,       // UUID
    String sessionId,
    String userId,
    String tenantId,
    Instant startedAt,
    Instant completedAt,
    long totalDurationMs,
    String skillUsed,
    String routingMethod,
    int totalTokens,
    double estimatedCost,
    String status,             // completed | failed | blocked
    List<AgentEvent> events    // ordered by timestamp
) {}
```

#### RF-SR-02 — Recording automatico

Un `EventBusConnector` dedicato (`RecordingConnector`) raccoglie tutti gli eventi di una sessione e li salva come `SessionRecording` in MongoDB al completamento.

- Attivato da `agent.recording.enabled=true`
- Salva in collection `session_recordings`
- TTL configurabile (default 30 giorni)
- Incluso solo se `event-bus.enabled=true`

#### RF-SR-03 — API REST per recordings

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/recordings` | Lista recordings recenti (paginata) |
| `GET` | `/api/admin/recordings/{recordingId}` | Recording completo con tutti gli eventi |
| `GET` | `/api/admin/recordings/{recordingId}/events` | Solo gli eventi (leggero) |
| `DELETE` | `/api/admin/recordings/{recordingId}` | Elimina un recording |
| `GET` | `/api/admin/recordings/session/{sessionId}` | Recordings per sessione |

#### RF-SR-04 — Replay endpoint

```
GET /api/admin/recordings/{recordingId}/replay
Accept: text/event-stream
```

Emette gli eventi della sessione registrata come SSE, rispettando i timing originali (o velocità configurabile). Il Graph Viewer si connette a questo endpoint per "replayare" una sessione passata.

Query params:
- `speed=1.0` — velocità replay (2.0 = doppia velocità, 0.5 = metà)
- `from=0` — indice evento da cui partire

#### RF-SR-05 — Confronto tra recordings

```
GET /api/admin/recordings/diff?a={recordingIdA}&b={recordingIdB}
```

Ritorna le differenze tra due esecuzioni:
- Skill diverse selezionate
- Tool diversi chiamati
- Token usage differente
- Durata differente
- Output diverso

Utile per: regression detection, A/B testing prompt, confronto provider.

#### RF-SR-06 — Configurazione

```yaml
agent:
  recording:
    enabled: true              # false = nessun recording
    ttl-days: 30               # auto-delete dopo N giorni
    max-events-per-session: 500  # tronca sessioni troppo lunghe
    exclude-event-types:       # non registrare certi eventi (privacy)
      - "memory.persist.completed"
```

### 2.3 Requisiti Non Funzionali

- **Append-only** — recordings non vengono mai modificati dopo il salvataggio
- **Privacy-aware** — opzione per escludere certi tipi di evento o mascherare il messaggio utente
- **Leggero** — gli eventi sono già in memoria nel buffer dell'event bus, il recording li copia in MongoDB async

---

## 3. Execution Graph Viewer

### 3.1 Obiettivo

Una pagina web (`/graph`) che mostra il grafo di esecuzione di un agente in tempo reale. L'utente vede ogni step del pipeline come un nodo, con stato, durata, e dettagli cliccabili.

### 3.2 Requisiti Funzionali

#### RF-GV-01 — Pagina HTML single-file

Come la chat UI (`/chat`), il graph viewer è una singola pagina HTML con CSS/JS inline, servita dal framework. Nessun framework frontend (React, Vue).

URL: `/graph`
Configurabile: `agent.graph-viewer.enabled=true|false`

#### RF-GV-02 — Layout del grafo

Il grafo mostra il pipeline di esecuzione come nodi verticali connessi da frecce:

```
┌─────────────────┐
│ Request Received │ ← grigio quando idle, blu quando attivo, verde quando completo
│ "What's the..."  │
│ 0ms              │
└────────┬────────┘
         │
┌────────▼────────┐
│ Input Guardrails │ ← espandibile: mostra ogni guardrail come sotto-nodo
│ 3 passed, 0 blocked│
│ 12ms             │
└────────┬────────┘
         │
┌────────▼────────┐
│ Skill Routing    │
│ → weather-skill  │
│ SEMANTIC (0.94)  │
│ 5ms              │
└────────┬────────┘
         │
    ... altri nodi ...
         │
┌────────▼────────┐
│ LLM Call         │ ← nodo più grande, mostra provider + modello
│ openai/gpt-4o    │
│ 245 in / 67 out  │
│ 1.2s             │
└────────┬────────┘
         │
┌────────▼────────┐
│ Tool: getWeather │ ← nodo tool con icona diversa
│ city="Rome"      │
│ 142ms            │
└────────┬────────┘
         │
    ... LLM call #2 (se tool calling loop) ...
         │
┌────────▼────────┐
│ Response         │ ← verde se completata, rosso se errore
│ "The weather..." │
│ Total: 1.4s      │
└─────────────────┘
```

#### RF-GV-03 — Modalità tempo reale

Il Graph Viewer si connette via SSE a `/api/admin/events/stream`:

```
GET /api/admin/events/stream?sessionId={sessionId}
Accept: text/event-stream
```

- Gli eventi arrivano in tempo reale dall'event bus
- Ogni evento aggiorna il nodo corrispondente nel grafo
- I nodi si animano: grigio → blu (in corso) → verde (completato) / rosso (errore)
- Se non viene specificato un sessionId, mostra l'ultima sessione attiva

#### RF-GV-04 — Modalità replay

Il Graph Viewer può anche riprodurre una sessione passata:

```
/graph?replay={recordingId}&speed=1.0
```

Si connette a `/api/admin/recordings/{recordingId}/replay` e anima i nodi con i timing originali.

#### RF-GV-05 — Node Inspection (click)

Cliccando un nodo, un pannello laterale mostra i dettagli:

| Tipo nodo | Dettagli mostrati |
|-----------|-------------------|
| **Request** | Messaggio utente, headers, sessionId, userId |
| **Guardrail** | Nome, verdetto, reason, matched patterns |
| **Routing** | Skill selezionata, metodo, confidence, alternative considerate |
| **Memory** | Working messages count, episodic summaries, knowledge segments, token budget |
| **RAG** | Knowledge base, query, chunks trovati con score |
| **LLM Call** | Provider, model, system prompt (troncato), input tokens, output tokens, costo, latenza |
| **Tool Call** | Nome tool, argomenti JSON, risultato (troncato), durata |
| **HITL** | Tool name, approval message, decision, wait time |
| **Response** | Testo risposta (troncato), skill usata, token totali, costo, durata totale |

#### RF-GV-06 — Timeline (barra in basso)

Una barra orizzontale in basso mostra la timeline degli eventi con:
- Segmenti colorati per fase (guardrails=grigio, routing=blu, LLM=arancione, tools=verde)
- Marker per eventi significativi (tool calls, errors)
- Scrubber trascinabile in modalità replay
- Durata totale e breakdown per fase

#### RF-GV-07 — Stile

- Sfondo bianco (#f8f9fa), coerente con la chat UI
- Font Inter (come chat UI)
- Colori nodi: grigio (idle), blu (#1d72e8 in corso), verde (#10b981 completato), rosso (#ef4444 errore), arancione (#f59e0b warning)
- Animazioni fluide per transizioni di stato
- Responsive (funziona su tablet)
- Pannello inspection a destra (collapsibile)

#### RF-GV-08 — API eventi per il viewer

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/events/stream` | SSE di eventi real-time (filtro per sessionId) |
| `GET` | `/api/admin/events/recent` | Ultimi N eventi dal buffer (polling fallback) |
| `GET` | `/api/admin/events/sessions` | Lista sessioni attive/recenti |

#### RF-GV-09 — Configurazione

```yaml
agent:
  graph-viewer:
    enabled: true              # false = /graph non disponibile
```

Dipende da `agent.event-bus.enabled=true` e `agent.event-bus.connectors.sse.enabled=true`.

### 3.3 Requisiti Non Funzionali

- **Nessuna dipendenza frontend** — vanilla JS + CSS, nessun React/Vue/Angular
- **Leggero** — la pagina HTML deve pesare < 50KB
- **Performante** — deve gestire sessioni con 100+ eventi senza lag
- **Disabilitabile** — in produzione, `/graph` può essere disabilitato senza impatto

---

## 4. Struttura Implementativa

### 4.1 Nuovi file in agent-core (zero Spring)

```
agent-core/src/main/java/ai/gargantua/core/event/
├── AgentEvent.java              ← record
├── AgentEventBus.java           ← interface
├── AgentEventListener.java      ← functional interface
├── EventBusConnector.java       ← plugin interface
└── SessionRecording.java        ← record
```

### 4.2 Nuovi file in agent-engine

```
agent-engine/src/main/java/ai/gargantua/autoconfigure/event/
├── InMemoryEventBus.java        ← implementazione default
├── SseConnector.java            ← forwarda a client SSE
├── Slf4jConnector.java          ← logga eventi come JSON
├── RecordingConnector.java      ← salva recordings in MongoDB
├── EventBusAutoConfiguration.java
└── EventApiController.java      ← /api/admin/events/*

agent-engine/src/main/java/ai/gargantua/adapters/web/
├── RecordingController.java     ← /api/admin/recordings/*
└── GraphViewerController.java   ← serve /graph redirect

agent-engine/src/main/resources/static/
└── graph.html                   ← Execution Graph Viewer (single file)
```

### 4.3 Modifiche a file esistenti

| File | Modifica |
|------|----------|
| `DefaultOrchestratorEngine` | Emette eventi in ogni step |
| `ChatStreamController` | Emette `request.received` e `response.completed` |
| `FlowExecutor` | Emette `flow.started`, `flow.step.completed`, `flow.completed` |
| `ToolRegistry` | Emette `tool.call.started/completed/failed` |
| `AgentProperties` | Aggiunge sezioni `event-bus`, `recording`, `graph-viewer` |
| `WebMvcConfig` | Aggiunge redirect `/graph` → `/graph.html` |
| `AutoConfiguration.imports` | Aggiunge `EventBusAutoConfiguration` |

### 4.4 Priorità implementazione

| Fase | Cosa | Dipende da |
|------|------|------------|
| **1** | `AgentEvent`, `AgentEventBus`, `EventBusConnector` (core) | Nulla |
| **2** | `InMemoryEventBus`, `Slf4jConnector`, `EventBusAutoConfiguration` (engine) | Fase 1 |
| **3** | Emissione eventi in `DefaultOrchestratorEngine` | Fase 2 |
| **4** | `EventApiController` + SSE endpoint | Fase 3 |
| **5** | `SessionRecording`, `RecordingConnector`, `RecordingController` | Fase 4 |
| **6** | `graph.html` (Execution Graph Viewer) | Fase 4 |
| **7** | Replay endpoint + timeline nella graph UI | Fase 5+6 |
| **8** | Diff comparison endpoint | Fase 5 |

---

## 5. Configurazione Completa

```yaml
agent:
  # ── Event Bus ──────────────────────────────────────────────────
  event-bus:
    enabled: true
    buffer-size: 1000
    async: true
    connectors:
      slf4j:
        enabled: false
        level: DEBUG
      sse:
        enabled: true

  # ── Session Recording ─────────────────────────────────────────
  recording:
    enabled: true
    ttl-days: 30
    max-events-per-session: 500
    exclude-event-types: []

  # ── Graph Viewer ───────────────────────────────────────────────
  graph-viewer:
    enabled: true

  # ── Chat UI (existing) ────────────────────────────────────────
  chat-ui:
    enabled: true
```

In produzione:
```yaml
# application-prod.yml
agent:
  event-bus:
    enabled: true
    connectors:
      slf4j:
        enabled: true         # logga per Kibana/Loki
      sse:
        enabled: false        # no SSE in produzione
  recording:
    enabled: false            # no recording in produzione (costo MongoDB)
  graph-viewer:
    enabled: false            # no debug UI in produzione
  chat-ui:
    enabled: false
```

---

## 6. Note per l'implementazione

- L'Event Bus è in `agent-core` (zero Spring) — le implementazioni sono in `agent-engine`
- `EventBusConnector` usa ServiceLoader per discovery (come `EvalPlugin` in agent-eval)
- L'event bus NON deve rallentare il pipeline — publish async, fire-and-forget
- Il Graph Viewer è un singolo file HTML (< 50KB) come la chat UI
- Usare D3.js o vanilla SVG per il rendering del grafo (nessun framework pesante)
- Il replay usa gli stessi SSE events del real-time — il Graph Viewer non distingue
- La timeline è una semplice `<canvas>` o `<svg>` con segmenti colorati
