# Gargantua -- AI Agent Framework

**Build production-ready AI agents in Java. Write a skill file and a tool class. Ship.**

Gargantua gives you everything you need to go from idea to deployed AI agent: multi-provider LLM orchestration, semantic skill routing, 3-layer persistent memory, guardrails pipeline, streaming, human-in-the-loop approvals, eval framework, cost tracking, and Kubernetes manifests. All as Maven dependencies — add them to your project and start building.

Built on Java 21, Spring Boot 4.0.4, and LangChain4j 1.12.1.

---

## Try it in 60 seconds

> Requires: Java 21+, Maven, an OpenAI or Anthropic API key. No Docker needed.

```bash
# 1. Generate a new agent project
mvn archetype:generate \
  -DarchetypeGroupId=com.github.giskardb.gargantua \
  -DarchetypeArtifactId=agent-archetype \
  -DarchetypeVersion=v1.0.0 \
  -DarchetypeRepository=https://jitpack.io \
  -DgroupId=com.mycompany -DartifactId=my-agent \
  -Dversion=1.0.0 -DagentName=MyAgent -DinteractiveMode=false

# 2. Run it (embedded mode — no Docker, everything in-memory)
cd my-agent
LLM_PRIMARY_PROVIDER=openai \
LLM_PRIMARY_MODEL=gpt-4o \
LLM_PRIMARY_API_KEY=sk-your-key \
LLM_PRIMARY_ENDPOINT=https://api.openai.com/v1 \
SPRING_PROFILES_ACTIVE=embedded \
mvn spring-boot:run
#
# Other providers (any LangChain4j-supported provider works):
#   Anthropic:    LLM_PRIMARY_PROVIDER=anthropic     LLM_PRIMARY_ENDPOINT=https://api.anthropic.com
#   Azure OpenAI: LLM_PRIMARY_PROVIDER=azure-openai  LLM_PRIMARY_ENDPOINT=https://your-resource.openai.azure.com
#   Google Gemini: LLM_PRIMARY_PROVIDER=google-gemini LLM_PRIMARY_MODEL=gemini-2.5-pro
#   Mistral:      LLM_PRIMARY_PROVIDER=mistral       LLM_PRIMARY_MODEL=mistral-large-latest
#   Ollama local: LLM_PRIMARY_PROVIDER=ollama        LLM_PRIMARY_ENDPOINT=http://localhost:11434

# 3. Talk to your agent (pick one)

#    Option A — curl
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: me" -H "X-Session-Id: s1" -H "X-Tenant-Id: acme" \
  -d '{"message": "Hello, what can you do?"}'

#    Option B — interactive shell (in a second terminal)
java -jar agent-shell/target/agent-shell-1.0.0.jar
#    Then type: chat
#    You: Hello, what can you do?
#    Agent: I can help you with...
#    You: \exit
```

That's a running agent with skill routing, guardrails, memory, streaming, and a REST API. Read on to add your own tools and skills.

---

## Features

Every feature has dedicated documentation — click the link to dive deeper.

### Agent Development

| Feature | What it does | Docs |
|---------|-------------|------|
| **Declarative Skills** | Define agent behavior in `SKILL.md` files — system prompt, allowed tools, routing hints. No code changes to add a skill. | [Skills & Routing](docs/skills-and-routing.md) |
| **@AgentTool** | Annotate Java methods as agent actions. Add `@ToolRetry` for resilience, `@RequiresApproval` for HITL, `@CacheableToolResult` for caching. | [Tools & Annotations](docs/tools-and-annotations.md) |
| **Hybrid Routing** | Semantic similarity (all-MiniLM-L6-v2, in-process, ~2ms) + LLM fallback. The agent picks the right skill automatically. | [Skills & Routing](docs/skills-and-routing.md) |
| **RAG / Vector Store** | Skills declare `knowledge-base` in SKILL.md — the framework retrieves relevant documents and injects them into the prompt. Pluggable `VectorStorePort`. | [Extending](docs/extending.md) |
| **Structured Output** | Skills declare a JSON Schema — the framework validates and auto-retries on mismatch. | [Extending](docs/extending.md) |

### Memory & State

| Feature | What it does | Docs |
|---------|-------------|------|
| **3-Layer Memory** | Working memory (Redis, current chat), Episodic memory (MongoDB, compressed past sessions), Knowledge memory (MongoDB, user profile). | [Memory System](docs/memory-system.md) |
| **Session Summarizer** | When a session expires, the routing model compresses it into an episodic summary — zero cost via Ollama. | [Memory System](docs/memory-system.md) |
| **Token Budget Manager** | Automatically truncates memory to fit the model's context window, by priority. | [Extending](docs/extending.md) |

### Security & Compliance

| Feature | What it does | Docs |
|---------|-------------|------|
| **Guardrail Pipeline** | Chain of input/output filters: PII masking, prompt injection detection, rate limiting, schema validation. Add custom guardrails with `@Component` + `@Order`. | [Guardrails](docs/guardrails.md) |
| **RBAC + Multi-Tenancy** | Role-based access via `X-User-Roles` header. Skills restrict access with `allowed-roles`. Automatic tenant data isolation via `X-Tenant-Id`. | [Extending](docs/extending.md) |
| **Audit Trail** | Immutable log of every agent decision: input, routing, guardrails, tools, output, cost. For SOC 2, GDPR, EU AI Act. | [Extending](docs/extending.md) |
| **Human-in-the-Loop** | `@RequiresApproval` suspends the agent and waits for user confirmation before executing dangerous tools. | [Extending](docs/extending.md) |

### Integration & Interop

| Feature | What it does | Docs |
|---------|-------------|------|
| **Multi-Provider LLM** | Any provider supported by LangChain4j: OpenAI, Anthropic, Azure OpenAI, Google Gemini, Mistral, Groq, Cohere, Ollama, and more. Rule-based model selection + Resilience4j failover. | [LLM Configuration](docs/llm-configuration.md) |
| **A2A Protocol** | Agent-to-Agent interop. Discovery via `/.well-known/agent.json`, tasks via JSON-RPC 2.0. Call remote agents with `HttpA2AClient`. | [Extending](docs/extending.md) |
| **MCP Server** | Expose the agent to Claude Desktop, Cursor, VS Code via the Model Context Protocol. | [Extending](docs/extending.md) |
| **SSE Streaming** | Real-time token delivery, tool call events, approval requests — all via Server-Sent Events. | [API Reference](docs/api-reference.md) |

### Operations & Quality

| Feature | What it does | Docs |
|---------|-------------|------|
| **Eval Framework** | LLM-as-Judge: test agent behavior against golden datasets. CI fails if quality drops below threshold. | [Eval Framework](docs/eval-framework.md) |
| **Cost Tracking** | Per-request token usage and cost, broken down by skill, user, provider. Admin dashboards. | [Extending](docs/extending.md) |
| **Observability** | OpenTelemetry spans + Micrometer metrics with GenAI semantic conventions. | [Deployment](docs/deployment.md) |
| **Interactive CLI** | Spring Shell 4 — chat, manage skills, run evals, view costs from the terminal. | [Extending](docs/extending.md) |
| **GraalVM Native** | < 100ms startup, ~50MB image. Multi-stage Dockerfile included. | [Deployment](docs/deployment.md) |
| **Kubernetes** | Kustomize overlays (dev/staging/prod), Helm chart, KEDA autoscaling on SSE connections. | [Deployment](docs/deployment.md) |

---

## How it works

**You write:**

1. One or more `SKILL.md` files — each declares a skill: behavior, allowed tools, routing hints. You can also **import skills as Maven JARs** from the [SkillsJars](docs/skills-and-routing.md) ecosystem instead of writing them.
2. `@AgentTool` classes — Java methods that implement the actual actions (API calls, database queries, business logic)

**Gargantua handles everything else:**

```mermaid
graph TB
    subgraph "What YOU write"
        SKILL["SKILL.md<br/><i>Behavior, routing hints,<br/>allowed tools</i>"]
        TOOL["@AgentTool<br/><i>Your business logic<br/>(Java methods)</i>"]
    end

    subgraph "What GARGANTUA provides"
        direction TB

        ROUTE["Hybrid Routing<br/><i>Semantic + LLM fallback</i>"]
        RBAC["RBAC Guardrail<br/><i>Role check · Tenant isolation</i>"]
        GUARD_IN["Input Guardrails<br/><i>PII · Injection · Rate limit</i>"]
        RAG["RAG Enricher<br/><i>VectorStore retrieval</i>"]
        ORCH["Orchestrator Engine<br/><i>Full pipeline coordination</i>"]
        LLM["Multi-Provider LLM<br/><i>OpenAI · Anthropic · Ollama<br/>Rule-based routing + failover</i>"]
        MEM["3-Layer Memory<br/><i>Working (Redis)<br/>Episodic + Knowledge (MongoDB)</i>"]
        GUARD_OUT["Output Guardrails<br/><i>PII · Disclaimer · Schema</i>"]
        STREAM["SSE Streaming<br/><i>Real-time token delivery</i>"]
        HITL["Human-in-the-Loop<br/><i>@RequiresApproval</i>"]
    end

    subgraph "What CLIENTS consume"
        API["REST API<br/><i>/api/agent/chat</i>"]
        CLI["Agent Shell<br/><i>Interactive CLI</i>"]
        MCP["MCP Gateway<br/><i>Claude Desktop · Cursor</i>"]
        A2A["A2A Protocol<br/><i>Agent-to-Agent interop</i>"]
        DOCS["Swagger + Redoc<br/><i>Auto-generated docs</i>"]
    end

    subgraph "Operations"
        AUDIT["Audit Trail<br/><i>Immutable decision log</i>"]
        EVAL["Eval Framework<br/><i>LLM-as-Judge</i>"]
        COST["Cost Tracking<br/><i>Per skill · user · provider</i>"]
        OTEL["Observability<br/><i>OTel · Micrometer</i>"]
        K8S["Kubernetes<br/><i>Kustomize · Helm · KEDA</i>"]
    end

    SKILL --> ROUTE
    TOOL --> ORCH
    ROUTE --> RBAC
    RBAC --> GUARD_IN
    GUARD_IN --> RAG
    RAG --> ORCH
    ORCH --> LLM
    ORCH --> MEM
    LLM --> GUARD_OUT
    GUARD_OUT --> STREAM
    ORCH --> HITL
    STREAM --> API
    STREAM --> CLI
    STREAM --> MCP
    STREAM --> A2A
    API --> DOCS
    ORCH --> AUDIT
    ORCH --> EVAL
    ORCH --> COST
    ORCH --> OTEL

    style SKILL fill:#4CAF50,color:#fff,stroke:#388E3C
    style TOOL fill:#4CAF50,color:#fff,stroke:#388E3C
    style API fill:#2196F3,color:#fff,stroke:#1565C0
    style CLI fill:#2196F3,color:#fff,stroke:#1565C0
    style MCP fill:#2196F3,color:#fff,stroke:#1565C0
    style DOCS fill:#2196F3,color:#fff,stroke:#1565C0
```

### What happens when a user sends a message

When a client calls `POST /api/agent/chat/stream`, the Orchestrator Engine executes this pipeline:

```
  User message
       │
  1.   ▼  RBAC check — does this user have access?
  2.   ▼  Input guardrails — PII masking, injection detection, rate limit
  3.   ▼  Skill routing — semantic similarity + LLM fallback → select skill
  4.   ▼  RAG retrieval — if skill declares knowledge-base, search vector store
  5.   ▼  Memory compose — load working + episodic + knowledge (parallel)
  6.   ▼  Token budget — truncate if over context window limit
  7.   ▼  LLM call — stream tokens, call tools, handle @RequiresApproval
  8.   ▼  Output guardrails — PII redaction, disclaimer, schema validation
  9.   ▼  Persist — save to memory, chat history, cost tracking, audit trail
       │
       ▼
  SSE stream → client (token by token)
```

Each step is a pluggable component — replace any part by declaring your own `@Bean`. For the full sequence diagrams of every flow (routing, memory, HITL, eval, A2A), see [Architecture Diagrams](docs/architecture-diagrams.md).

---

## Full Setup Guide

The "60 seconds" quickstart uses **embedded mode** (everything in-memory, no Docker). For production use with persistent memory, chat history, and local routing model, follow this full setup.

### Prerequisites

- **Java 21+** — the framework uses Virtual Threads (Project Loom)
- **Maven 3.9+**
- **Docker & Docker Compose** — for MongoDB, Redis, and Ollama

### 1. Generate the project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.github.giskardb.gargantua \
  -DarchetypeArtifactId=agent-archetype \
  -DarchetypeVersion=v1.0.0 \
  -DarchetypeRepository=https://jitpack.io \
  -DgroupId=com.mycompany \
  -DartifactId=my-agent \
  -Dversion=1.0.0 \
  -DagentName=MyAgent \
  -DinteractiveMode=false
```

This generates:

```
my-agent/
├── pom.xml                          -- depends on Gargantua engine
├── .env.example                     -- documented env vars template
├── Dockerfile                       -- multi-stage JVM build
├── docker-compose.yml               -- app + MongoDB + Redis + Ollama
├── src/main/java/com/mycompany/
│   ├── MyAgentApplication.java      -- @SpringBootApplication
│   └── tools/
│       └── SampleTool.java          -- example @AgentTool
└── src/main/resources/
    ├── application.yml              -- full config with defaults
    ├── application-embedded.yml     -- embedded mode (no Docker needed)
    └── skills/
        ├── default-skill/SKILL.md   -- fallback skill
        └── sample-skill/SKILL.md    -- example skill
```

### 2. Start infrastructure

```bash
cd my-agent
docker compose up -d mongo redis ollama

# Pull the local routing model (one-time, after first start)
docker compose exec ollama ollama pull phi4-mini
```

| Service | What it does | Port |
|---------|-------------|------|
| **MongoDB** | Stores chat history, session summaries, user profiles, eval reports, costs | 27017 |
| **Redis** | Session memory, HITL approvals, tool cache, rate limits | 6379 |
| **Ollama** | Local routing model (zero API cost for skill routing and session summaries) | 11434 |

### 3. Configure your LLM providers

Gargantua uses **three LLM roles** — each can be a different provider and model:

| Role | Purpose | Default | Cost |
|------|---------|---------|------|
| **Primary** | Agent conversations — answers the user | OpenAI `gpt-4o` | Per-token API cost |
| **Fallback** | Auto-failover when primary fails | Anthropic `claude-sonnet` | Per-token (only on failure) |
| **Routing** | Internal: skill routing, session summaries, eval judge | Ollama `phi4-mini` (local) | **Free** (if local) |

By default the routing model runs locally via Ollama — but this is just a suggestion. All three roles accept **any LangChain4j provider**. You can configure routing to use OpenAI, Anthropic, or any other cloud provider exactly like primary and fallback — just set `LLM_ROUTING_PROVIDER`, `LLM_ROUTING_MODEL`, `LLM_ROUTING_API_KEY`, and `LLM_ROUTING_ENDPOINT`.

> **Supported providers:** Gargantua supports any LLM provider available in LangChain4j — OpenAI, Anthropic, Azure OpenAI, Google Gemini, Mistral, Groq, Cohere, Together AI, AWS Bedrock, Ollama, and more. Set the provider name and endpoint accordingly.

Copy `.env.example` to `.env` and fill in the primary provider:

```bash
cp .env.example .env
```

```bash
# ── Primary LLM — the model that answers users ──────────────────
# Provider: openai | anthropic | azure-openai | google-gemini |
#           mistral | groq | cohere | ollama | any LangChain4j provider
export LLM_PRIMARY_PROVIDER=openai
export LLM_PRIMARY_MODEL=gpt-4o
export LLM_PRIMARY_API_KEY=sk-your-key-here
export LLM_PRIMARY_ENDPOINT=https://api.openai.com/v1

# ── Fallback — optional, auto-failover on primary failure ───────
# export LLM_FALLBACK_PROVIDER=anthropic
# export LLM_FALLBACK_MODEL=claude-sonnet-4-20250514
# export LLM_FALLBACK_API_KEY=sk-ant-...
# export LLM_FALLBACK_ENDPOINT=https://api.anthropic.com

# ── Routing — local Ollama by default, no config needed ─────────
# Override only to use a cloud provider for routing:
# export LLM_ROUTING_PROVIDER=openai
# export LLM_ROUTING_MODEL=gpt-4o-mini
# export LLM_ROUTING_API_KEY=sk-...
```

> See [LLM Configuration](docs/llm-configuration.md) for advanced setups: model catalogs, rule-based routing, per-skill model overrides, A/B testing.

### 4. Run

```bash
mvn spring-boot:run
```

### 5. Test

```bash
# Option A — curl
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" -H "X-Session-Id: sess1" \
  -d '{"message": "Hello, what can you do?"}'

# Option B — interactive shell (in a second terminal)
java -jar agent-shell/target/agent-shell-1.0.0.jar
# Type: chat
# Then talk to your agent interactively. Type \exit to quit.

# See what skills are available
curl http://localhost:8080/api/capabilities

# Interactive docs
open http://localhost:8080/swagger-ui
```

---

## How It Works -- Add a Tool, Add a Skill

### Write a Tool

```java
@Component
public class OrderTool {

    @AgentTool(description = "Retrieves order status by order ID")
    @ToolRetry(maxAttempts = 3, waitDurationMs = 500)
    @CacheableToolResult(ttlSeconds = 60, scope = CacheScope.USER)
    public OrderStatus getOrderStatus(String orderId) {
        return orderService.getStatus(orderId);
    }

    @AgentTool(description = "Cancels an order — irreversible")
    @RequiresApproval(message = "Cancel order?", showParameters = {"orderId"}, dangerous = true)
    public CancelResult cancelOrder(String orderId) {
        return orderService.cancel(orderId);
    }
}
```

### Write a Skill

Create `src/main/resources/skills/order-skill/SKILL.md`:

```markdown
---
name: order-skill
description: >
  Manages customer orders. Use when the user asks about order status,
  tracking, or cancellations. Do NOT use for product queries.
version: 1.0.0
allowed-tools:
  - getOrderStatus
  - cancelOrder
metadata:
  active: true
  domain: ecommerce
---

## Role
You are an order management assistant.

## Behavior
- Always verify the order ID via tools before responding
- Never cancel without explicit user confirmation
- Provide tracking links when available

## Scope
Order-related queries only.
```

That's it. The framework handles routing, memory, guardrails, streaming, and everything else.

---

## Framework Libraries (Maven coordinates)

Gargantua is distributed as a set of Maven libraries. You don't clone this repo -- you add dependencies.

| Artifact | GroupId | Description |
|----------|---------|-------------|
| `agent-core` | `ai.gargantua` | Pure domain: records, interfaces, annotations. Zero Spring deps. |
| `agent-memory-sdk` | `ai.gargantua` | Standalone 3-layer memory (Redis + MongoDB). Reusable in any project. |
| `agent-engine` | `ai.gargantua` | Auto-configuration, guardrails, routing, orchestrator, tool registry, REST controllers, skill registries, admin endpoints. |
| `agent-mcp-server` | `ai.gargantua` | MCP Server gateway (optional). |
| `agent-shell` | `ai.gargantua` | Interactive CLI -- Spring Shell 4. |
| `agent-skill-linter-maven-plugin` | `ai.gargantua` | Build-time SKILL.md validation. |
| `agent-archetype` | `ai.gargantua` | Maven archetype to scaffold new agent projects. |

### Repository setup — JitPack (recommended)

Gargantua is published via **[JitPack](https://jitpack.io)** — no authentication, no `settings.xml`, just add the repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

> JitPack uses the groupId `com.github.giskardb.gargantua` and versions match Git tags (e.g. `v1.0.0`).
>
> If you used the Maven archetype, the repository is **already configured** in the generated `pom.xml`.

<details>
<summary>Alternative: GitHub Packages (requires authentication)</summary>

Add to `~/.m2/settings.xml`:
```xml
<servers>
    <server>
        <id>github-gargantua</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password><!-- read:packages scope -->
    </server>
</servers>
```

Add to `pom.xml`:
```xml
<repositories>
    <repository>
        <id>github-gargantua</id>
        <url>https://maven.pkg.github.com/giskardb/gargantua</url>
    </repository>
</repositories>
```
With GitHub Packages, use groupId `ai.gargantua` and version `1.0.0` (no `v` prefix).
</details>

### Typical dependency setup (JitPack)

```xml
<properties>
    <gargantua.version>v1.0.0</gargantua.version>
</properties>

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- Core engine: orchestrator, guardrails, routing, memory, REST API, admin endpoints, skill registries -->
    <dependency>
        <groupId>com.github.giskardb.gargantua</groupId>
        <artifactId>agent-engine</artifactId>
        <version>${gargantua.version}</version>
    </dependency>

    <!-- Optional: MCP server gateway -->
    <dependency>
        <groupId>com.github.giskardb.gargantua</groupId>
        <artifactId>agent-mcp-server</artifactId>
        <version>${gargantua.version}</version>
    </dependency>
</dependencies>
```

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 (Virtual Threads) |
| Spring Boot | 4.0.4 |
| Spring Framework | 7.0.5 |
| LangChain4j | 1.12.1 |
| MongoDB | 8.0 |
| Redis | 7.4 |
| Spring Shell | 4.0.1 |
| springdoc-openapi | 3.0.2 |
| Resilience4j | 2.3.0 |
| Caffeine | 3.2.0 |
| MCP SDK | 0.9.0 |
| GraalVM | 21 |

---

## Repository Structure (for framework contributors)

```
gargantua/
├── agent-core/                      -- Pure domain: records, interfaces, annotations
├── agent-memory-sdk/                -- Standalone memory library (Redis + MongoDB)
├── agent-engine/                    -- Core engine: auto-configuration, orchestrator, guardrails, routing, REST controllers, skill registries
├── agent-mcp-server/                -- MCP Server gateway (optional)
├── agent-example-fitcoach/                   -- Reference agent (weather/search tools)
├── agent-shell/                     -- Interactive CLI (Spring Shell 4)
├── agent-skill-linter-maven-plugin/ -- Build-time SKILL.md validation
├── agent-archetype/                 -- Maven archetype for scaffolding new projects
├── k8s/                             -- Kubernetes manifests (Kustomize + Helm)
├── Dockerfile                       -- Multi-stage (JVM + GraalVM native)
└── docker-compose.yml               -- Local dev (agent + MongoDB + Redis)
```

---

## Documentation

| Topic | Link |
|-------|------|
| Skills & Routing | [docs/skills-and-routing.md](docs/skills-and-routing.md) |
| Tools & Annotations | [docs/tools-and-annotations.md](docs/tools-and-annotations.md) |
| Memory System | [docs/memory-system.md](docs/memory-system.md) |
| Guardrails | [docs/guardrails.md](docs/guardrails.md) |
| LLM Configuration & Routing | [docs/llm-configuration.md](docs/llm-configuration.md) |
| Eval Framework | [docs/eval-framework.md](docs/eval-framework.md) |
| API Reference | [docs/api-reference.md](docs/api-reference.md) |
| Extending (CLI, MCP, Dry-Run, Cost, History) | [docs/extending.md](docs/extending.md) |
| Deployment (Docker, K8s, GraalVM) | [docs/deployment.md](docs/deployment.md) |
| Architecture Diagrams | [docs/architecture-diagrams.md](docs/architecture-diagrams.md) |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/.well-known/agent.json` | A2A Agent Card (standard discovery) |
| `POST` | `/a2a` | A2A JSON-RPC 2.0 (tasks/send, tasks/get, tasks/cancel) |
| `POST` | `/api/agent/chat` | Sync chat |
| `POST` | `/api/agent/chat/stream` | SSE streaming chat |
| `POST` | `/api/agent/session/new` | Create session |
| `POST` | `/api/agent/approval/{id}` | HITL approval |
| `GET` | `/api/capabilities` | Agent capabilities |
| `GET` | `/api/agent/chat/sessions/{userId}` | List sessions |
| `GET` | `/api/agent/chat/history/{userId}/{sessionId}` | Chat history |
| `GET` | `/api/agent/chat/export/{userId}/{sessionId}` | Export conversation |
| `DELETE` | `/api/agent/chat/history/{userId}` | GDPR delete all |
| `GET` | `/api/admin/skills` | List skills |
| `POST` | `/api/admin/skills/reload` | Reload skills |
| `GET` | `/api/admin/guardrails` | Guardrail pipeline |
| `POST` | `/api/admin/guardrails/{name}/toggle` | Toggle guardrail |
| `POST` | `/api/admin/evals/run/{skillName}` | Run eval suite |
| `GET` | `/api/admin/evals/reports/{skillName}/latest` | Latest eval report |
| `GET` | `/api/admin/costs/summary` | Cost summary |
| `GET` | `/api/admin/llm/rules` | LLM routing rules |
| `POST` | `/api/admin/llm/simulate` | Simulate LLM routing |
| `GET` | `/api/admin/audit` | Query audit trail (by user, tenant, session, eventId, count) |
| `GET` | `/swagger-ui` | Swagger UI |
| `GET` | `/docs` | Redoc documentation |

---

## Embedded Mode

Run an agent with **zero infrastructure** — no Docker, no MongoDB, no Redis:

```bash
export LLM_PRIMARY_PROVIDER=openai
export LLM_PRIMARY_MODEL=gpt-4o
export LLM_PRIMARY_API_KEY=sk-...
SPRING_PROFILES_ACTIVE=embedded mvn spring-boot:run
```

All storage uses in-memory ConcurrentHashMaps. Data is lost on restart.

| What | Standard mode | Embedded mode |
|------|--------------|---------------|
| Working memory | Redis | ConcurrentHashMap |
| Episodic memory | MongoDB | ConcurrentHashMap |
| Knowledge memory | MongoDB | ConcurrentHashMap |
| Chat history | MongoDB | ConcurrentHashMap |
| HITL approvals | Redis | ConcurrentHashMap |
| Tool cache | Redis | ConcurrentHashMap |
| Cost tracking | MongoDB | ConcurrentHashMap |
| Requires Docker | Yes | **No** |
| Data persisted | Yes | **No** (lost on restart) |

**When to use embedded mode:**
- Local development and prototyping
- CI/CD pipelines and automated testing
- Quick demos
- Learning the framework

**When NOT to use it:**
- Production (use MongoDB + Redis)
- Load testing (in-memory has no eviction policies)

---

## Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| **Infrastructure** | | |
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/gargantua` |
| `REDIS_URL` | Redis connection URL | `redis://localhost:6379` |
| `SERVER_PORT` | HTTP server port | `8080` |
| **Primary LLM** | Choose a provider, a model, and set the API key — all three are needed | |
| `LLM_PRIMARY_PROVIDER` | LLM provider: `openai`, `anthropic`, `azure-openai`, `google-gemini`, `mistral`, `groq`, `cohere`, `ollama`, or any LangChain4j provider | `openai` |
| `LLM_PRIMARY_MODEL` | Which model from that provider (e.g. `gpt-4o`, `claude-sonnet-4-20250514`) | `gpt-4o` |
| `LLM_PRIMARY_API_KEY` | API key for the chosen provider (OpenAI: `sk-...`, Anthropic: `sk-ant-...`) | **(required)** |
| `LLM_PRIMARY_ENDPOINT` | Provider API endpoint. Required for `azure-openai`. Defaults: OpenAI `https://api.openai.com/v1`, Anthropic `https://api.anthropic.com` | provider default |
| `LLM_PRIMARY_TEMPERATURE` | Sampling temperature (0.0 -- 1.0) | `0.7` |
| `LLM_PRIMARY_MAX_TOKENS` | Max tokens in LLM response | `1000` |
| **Fallback LLM** | Used automatically when primary provider fails | |
| `LLM_FALLBACK_PROVIDER` | Fallback provider | `anthropic` |
| `LLM_FALLBACK_MODEL` | Fallback model | `claude-sonnet-4-20250514` |
| `LLM_FALLBACK_API_KEY` | Fallback API key | *(optional)* |
| `LLM_FALLBACK_ENDPOINT` | Fallback endpoint | provider default |
| **Routing LLM** | Local model for skill routing, eval judge, session summaries (zero API cost via Ollama) | |
| `LLM_ROUTING_PROVIDER` | Routing model provider: `ollama`, `openai`, `anthropic` | `ollama` |
| `LLM_ROUTING_MODEL` | Routing model name | `phi4-mini` |
| `LLM_ROUTING_ENDPOINT` | Routing model endpoint (Ollama URL when running locally) | `http://localhost:11434` |
| `LLM_ROUTING_API_KEY` | Routing model API key (not needed for Ollama) | *(optional)* |
| **Routing** | | |
| `ROUTING_STRATEGY` | Skill routing: `hybrid`, `semantic`, `llm` | `hybrid` |
| `ROUTING_THRESHOLD` | Semantic similarity threshold (0.0 -- 1.0) | `0.82` |
| **Audit** | | |
| `AGENT_AUDIT_ENABLED` | Enable immutable audit trail | `true` |
| `AGENT_AUDIT_RETENTION_DAYS` | How long to retain audit events | `365` |

---

## License

Apache 2.0
