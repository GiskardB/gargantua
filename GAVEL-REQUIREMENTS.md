# Gavel — LLM-as-Judge Platform for AI Agents

## Requirements Document v1.0

> Passare questo documento a Claude Code nella nuova sessione sul repository Gavel.

---

## 1. Cosa è Gavel

Gavel è una piattaforma standalone per valutare la qualità di agenti AI. Funziona con qualsiasi agente che espone un endpoint REST di chat. Chiama l'agente, giudica le risposte con un LLM (o plugin custom), produce report, e offre una dashboard web per gestire tutto.

**Tagline:** "The verdict on your AI agent."

---

## 2. Architettura

```
┌─────────────────────────────────────────────────────────────┐
│                    Gavel Platform                            │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐ │
│  │ Dashboard │  │ REST API │  │ Eval     │  │ Plugin     │ │
│  │ (Web UI)  │  │          │  │ Engine   │  │ System     │ │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬──────┘ │
│        │             │             │              │         │
│        └─────────────┴─────────────┴──────────────┘         │
│                          │                                  │
│                    ┌─────┴─────┐                            │
│                    │  MongoDB  │                            │
│                    └───────────┘                            │
└─────────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
  ┌──────────────┐              ┌──────────────┐
  │ Agent Under  │              │  LLM Judge   │
  │ Test (REST)  │              │  (OpenAI,    │
  │              │              │   Ollama,    │
  │ Any agent    │              │   any)       │
  └──────────────┘              └──────────────┘
```

---

## 3. Stack Tecnologico

| Componente | Tecnologia |
|-----------|------------|
| **Linguaggio** | Java 21 (Virtual Threads) |
| **Framework web** | Spring Boot 4.0.x |
| **Database** | MongoDB (eval cases, reports, config) |
| **LLM calls** | java.net.http.HttpClient (OpenAI-compatible API) |
| **Plugin discovery** | Java ServiceLoader |
| **Dashboard** | Single HTML page (inline JS/CSS, servita da Spring) — NO framework frontend |
| **Containerizzazione** | Docker + docker-compose |
| **Build** | Maven, fat JAR via spring-boot-maven-plugin |

---

## 4. Modalità di Esecuzione

### 4.1 CLI (senza dashboard)

```bash
java -jar gavel.jar eval \
  --evals-dir ./evals \
  --agent-url http://localhost:8080 \
  --judge-endpoint https://api.openai.com/v1 \
  --judge-model gpt-4o-mini \
  --judge-key sk-... \
  --threshold 0.70
```

Exit code 0 = passed, 1 = failed. Per CI/CD.

### 4.2 Server (con dashboard)

```bash
java -jar gavel.jar server --port 3000
```

Oppure:

```bash
docker run -p 3000:3000 -v ./data:/data gargantua/gavel
```

Apre la dashboard su `http://localhost:3000`.

### 4.3 Docker Compose (tutto incluso)

```yaml
services:
  gavel:
    image: gargantua/gavel
    ports:
      - "3000:3000"
    environment:
      MONGODB_URI: mongodb://mongo:27017/gavel
      JUDGE_ENDPOINT: http://ollama:11434/v1
      JUDGE_MODEL: phi4-mini
    depends_on:
      - mongo
      - ollama

  mongo:
    image: mongo:8.0
    volumes:
      - gavel-data:/data/db

  ollama:
    image: ollama/ollama
    volumes:
      - ollama-data:/root/.ollama

volumes:
  gavel-data:
  ollama-data:
```

---

## 5. Modello Dati (MongoDB)

### 5.1 Collection: `eval_suites`

Un eval suite è un insieme di test case per un agente specifico.

```json
{
  "_id": "ObjectId",
  "name": "weather-skill-tests",
  "description": "Tests for the weather skill",
  "agentUrl": "http://localhost:8080",
  "tags": ["weather", "tools"],
  "cases": [
    {
      "id": "weather-001",
      "description": "Current weather query",
      "input": "What's the weather in Rome?",
      "expectedBehaviors": ["Calls getWeather", "Mentions temperature"],
      "forbiddenBehaviors": ["Invents data without tool"],
      "tags": ["happy-path"]
    }
  ],
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### 5.2 Collection: `eval_runs`

Ogni esecuzione di una suite produce un run.

```json
{
  "_id": "ObjectId",
  "suiteId": "ObjectId",
  "suiteName": "weather-skill-tests",
  "status": "running | completed | failed | cancelled",
  "progress": {
    "total": 10,
    "completed": 7,
    "passed": 5,
    "failed": 1,
    "partial": 1
  },
  "config": {
    "agentUrl": "http://localhost:8080",
    "judgeEndpoint": "http://localhost:11434/v1",
    "judgeModel": "phi4-mini",
    "plugin": "llm-judge",
    "threshold": 0.70
  },
  "overallScore": 0.85,
  "results": [
    {
      "caseId": "weather-001",
      "input": "What's the weather in Rome?",
      "agentResponse": "The weather in Rome is 22°C...",
      "verdict": "PASS",
      "score": 0.95,
      "reason": "All expected behaviors satisfied",
      "passedBehaviors": ["Calls getWeather", "Mentions temperature"],
      "failedBehaviors": [],
      "durationMs": 1240,
      "plugin": "llm-judge"
    }
  ],
  "startedAt": "ISODate",
  "completedAt": "ISODate",
  "durationMs": 12400
}
```

### 5.3 Collection: `eval_configs`

Configurazioni salvate riutilizzabili.

```json
{
  "_id": "ObjectId",
  "name": "production-openai",
  "agentUrl": "https://my-agent.company.com",
  "judgeEndpoint": "https://api.openai.com/v1",
  "judgeModel": "gpt-4o-mini",
  "judgeKey": "sk-...",
  "plugin": "llm-judge",
  "threshold": 0.70,
  "isDefault": true
}
```

---

## 6. REST API

### Eval Suites (CRUD)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/suites` | Lista tutte le suite |
| `POST` | `/api/suites` | Crea una suite (body JSON o upload file) |
| `GET` | `/api/suites/{id}` | Dettaglio suite con casi |
| `PUT` | `/api/suites/{id}` | Aggiorna suite |
| `DELETE` | `/api/suites/{id}` | Elimina suite |
| `POST` | `/api/suites/{id}/import` | Importa casi da file JSON |

### Eval Runs (esecuzione + storico)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/runs` | Lancia un run (async, ritorna subito con runId) |
| `GET` | `/api/runs` | Lista run recenti (paginata) |
| `GET` | `/api/runs/{id}` | Dettaglio run con risultati |
| `GET` | `/api/runs/{id}/progress` | SSE stream dello stato di avanzamento |
| `POST` | `/api/runs/{id}/cancel` | Cancella un run in corso |
| `GET` | `/api/runs/{id}/report` | Report HTML del run |

### Configs

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/configs` | Lista configurazioni salvate |
| `POST` | `/api/configs` | Salva configurazione |
| `DELETE` | `/api/configs/{id}` | Elimina configurazione |

---

## 7. Dashboard Web (Single Page HTML)

**URL:** `http://localhost:3000`

### 7.1 Layout

```
┌─────────────────────────────────────────────────────────────┐
│  🔨 Gavel                              [+ New Suite] [⚙]  │
├──────────┬──────────────────────────────────────────────────┤
│          │                                                  │
│ Sidebar  │  Main Content Area                               │
│          │                                                  │
│ Suites   │  (changes based on selection)                    │
│ • suite1 │                                                  │
│ • suite2 │                                                  │
│          │                                                  │
│ Runs     │                                                  │
│ • Latest │                                                  │
│ • History│                                                  │
│          │                                                  │
│ Settings │                                                  │
│          │                                                  │
└──────────┴──────────────────────────────────────────────────┘
```

### 7.2 Pagine / Viste

**Suite List** — tabella con nome, n° casi, ultimo score, ultimo run, azioni (run, edit, delete)

**Suite Editor** — form per creare/editare suite:
- Nome, descrizione, agent URL, tags
- Lista casi con editor inline (add/edit/remove)
- Import da JSON file (drag & drop o file picker)
- Preview JSON

**Run Launcher** — modal per lanciare un run:
- Seleziona suite
- Seleziona o crea configurazione (agent URL, judge, plugin, threshold)
- Bottone "Run" → lancia async, redirect a Run Progress

**Run Progress** — real-time via SSE:
- Barra di avanzamento (N/M casi completati)
- Risultati che appaiono in tempo reale man mano che i casi finiscono
- Score parziale aggiornato live
- Bottone "Cancel" per fermare
- Al completamento: score finale, verdict PASS/FAIL, link al report

**Run Detail / Report** — dopo il completamento:
- Summary: score, pass/fail/partial, durata, config usata
- Per ogni caso: card espandibile con input, risposta agente, verdict, score, reason, behaviors
- Confronto con run precedente (score delta) se disponibile
- Download report JSON
- Download report HTML

**Run History** — lista di tutti i run per suite, con trend score nel tempo (sparkline o mini chart)

**Settings** — configurazioni salvate:
- CRUD configurazioni (agent URL, judge, plugin)
- Plugin disponibili

### 7.3 Stile

- Sfondo bianco, design pulito, professionale
- Colori: blu primario (#2563eb), verde pass (#10b981), rosso fail (#ef4444), giallo partial (#f59e0b)
- Font: system-ui / -apple-system
- Responsive (funziona su tablet)
- Single HTML file con CSS/JS inline (come la chat UI di Gargantua)
- Fetch API per chiamare il backend REST
- SSE per progress real-time

---

## 8. Plugin System

### 8.1 Interfaccia

```java
public interface EvalPlugin {
    String name();
    String description();
    PluginResult evaluate(PluginContext context);
}

public record PluginContext(
    EvalCase evalCase,
    String agentResponse,
    Map<String, String> config
) {}

public record PluginResult(
    double score,           // 0.0-1.0
    String verdict,         // PASS | FAIL | PARTIAL
    String reason,
    List<String> passed,
    List<String> failed
) {}
```

### 8.2 Plugin Built-in

| Plugin | Name | Descrizione |
|--------|------|-------------|
| **LLM Judge** | `llm-judge` | Chiama un LLM OpenAI-compatible per scoring |
| **Keyword Match** | `keyword-match` | Match keyword nelle expected behaviors (no LLM, veloce) |
| **Regex** | `regex` | Pattern matching regex sulle expected behaviors |

### 8.3 Plugin Custom

L'utente crea un JAR con una classe che implementa `EvalPlugin` e un file `META-INF/services/ai.gargantua.gavel.plugin.EvalPlugin`. Mette il JAR nella cartella `plugins/` e Gavel lo scopre automaticamente via ServiceLoader.

---

## 9. Esecuzione Asincrona dei Run

### 9.1 Come funziona

1. `POST /api/runs` crea il run in MongoDB con status `running`
2. Ritorna subito `201 Created` con il `runId`
3. Un thread background (Virtual Thread) esegue i casi:
   - Per ogni caso: chiama l'agente → chiama il judge → salva risultato
   - Aggiorna `progress` in MongoDB dopo ogni caso
   - I casi possono eseguire in parallelo (configurabile: `parallelism`)
4. Il client può fare polling su `GET /api/runs/{id}` o ascoltare SSE su `GET /api/runs/{id}/progress`
5. Al completamento: status diventa `completed`, `overallScore` calcolato

### 9.2 SSE Progress Events

```
event: progress
data: {"completed": 3, "total": 10, "passed": 2, "failed": 1, "currentScore": 0.75}

event: case-result
data: {"caseId": "weather-001", "verdict": "PASS", "score": 0.95, "durationMs": 1240}

event: done
data: {"overallScore": 0.85, "passed": 8, "failed": 1, "partial": 1, "durationMs": 12400}

event: error
data: {"message": "Agent unreachable at http://localhost:8080"}
```

### 9.3 Cancellazione

`POST /api/runs/{id}/cancel` imposta un flag che il worker controlla prima di ogni caso. I casi già completati restano, quelli non avviati vengono skippati.

---

## 10. Struttura Progetto

```
gavel/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── src/main/java/ai/gargantua/gavel/
│   ├── GavelApplication.java              — @SpringBootApplication + CLI mode
│   ├── config/
│   │   └── GavelProperties.java           — @ConfigurationProperties
│   ├── model/
│   │   ├── EvalSuite.java                 — MongoDB document
│   │   ├── EvalRun.java                   — MongoDB document
│   │   ├── EvalCase.java                  — embedded in suite
│   │   ├── EvalConfig.java                — MongoDB document
│   │   └── CaseResult.java               — embedded in run
│   ├── repository/
│   │   ├── SuiteRepository.java
│   │   ├── RunRepository.java
│   │   └── ConfigRepository.java
│   ├── service/
│   │   ├── RunExecutor.java               — async run execution
│   │   ├── AgentClient.java               — calls agent via REST
│   │   └── PluginManager.java             — ServiceLoader discovery
│   ├── plugin/
│   │   ├── EvalPlugin.java                — interface
│   │   ├── PluginContext.java
│   │   ├── PluginResult.java
│   │   ├── LlmJudgePlugin.java
│   │   ├── KeywordMatchPlugin.java
│   │   └── RegexPlugin.java
│   ├── web/
│   │   ├── SuiteController.java
│   │   ├── RunController.java
│   │   ├── ConfigController.java
│   │   └── DashboardController.java       — serve la pagina HTML
│   └── cli/
│       └── CliRunner.java                 — modalità CLI (no server)
├── src/main/resources/
│   ├── application.yml
│   ├── static/
│   │   ├── index.html                     — Dashboard (single page)
│   │   └── logo.svg                       — Logo Gavel
│   └── META-INF/services/
│       └── ai.gargantua.gavel.plugin.EvalPlugin
├── src/test/java/
│   └── ...
└── examples/
    ├── weather-agent/evals.json
    └── fitcoach/evals.json
```

---

## 11. Configurazione (application.yml)

```yaml
server:
  port: ${GAVEL_PORT:3000}

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/gavel}

gavel:
  judge:
    endpoint: ${JUDGE_ENDPOINT:http://localhost:11434/v1}
    model: ${JUDGE_MODEL:phi4-mini}
    key: ${JUDGE_KEY:}
  default-plugin: llm-judge
  default-threshold: 0.70
  parallelism: 4
  plugins-dir: ./plugins
```

---

## 12. Logo

Il logo è un martelletto del giudice stilizzato (gavel). Colore primario: blu (#2563eb). Stile: minimalista, moderno. Formato SVG.

---

## 13. Priorità Implementazione

| Fase | Cosa | Note |
|------|------|------|
| **1** | REST API + MongoDB + Eval engine | Backend funzionante, testabile via curl |
| **2** | Dashboard HTML (suite CRUD + run launcher + progress) | Frontend minimale ma funzionale |
| **3** | Plugin system (ServiceLoader + 3 built-in) | Estendibilità |
| **4** | CLI mode (java -jar gavel.jar eval ...) | Per CI/CD |
| **5** | Docker + docker-compose | Deployment |
| **6** | Report HTML scaricabile | Export |

---

## 14. Non in Scope v1.0

- Autenticazione/RBAC sulla dashboard
- Multi-tenant
- Notifiche email/slack sui risultati
- Confronto A/B tra modelli
- Scheduling automatico (cron)
- Integrazione diretta con CI (GitHub Actions action)

Queste sono tutte feature v2.0 se il prodotto trova utenti.

---

## 15. Note per Claude Code

- Il progetto è un singolo modulo Maven (non multi-modulo)
- Spring Boot come framework web + MongoDB driver
- La dashboard è UNA SINGOLA pagina HTML con CSS/JS inline (come la chat UI di Gargantua)
- NON usare framework frontend (React, Vue, etc.)
- Usa java.net.http.HttpClient per le chiamate all'agente e al judge (non LangChain4j)
- Il plugin system usa Java ServiceLoader standard
- Virtual Threads per l'esecuzione parallela dei casi
- Il design della dashboard deve essere pulito, bianco, professionale
- Il logo del martelletto va creato come SVG
