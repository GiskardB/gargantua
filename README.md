# Gargantua -- AI Agent Framework

A **distributable** Java 21 framework for building autonomous AI agents.
Built on Spring Boot 4.0.4 and LangChain4j 1.12.1.

**You write two things:**

1. A `SKILL.md` file that declares what your agent can do
2. A `@AgentTool` class that implements the actual logic

**Everything else is provided out-of-the-box** as library dependencies: orchestration, routing, 3-layer memory, streaming, guardrails, HITL, eval, cost tracking, CLI, MCP gateway, and more.

---

## Quick Start -- Create a New Agent

### Prerequisites

- **Java 21+** — the framework uses Virtual Threads (Project Loom)
- **Maven 3.9+**
- **Docker & Docker Compose** — for MongoDB and Redis *(not needed in embedded mode)*

> **Why MongoDB and Redis?**
> In production, Gargantua uses two infrastructure services:
>
> | Service | Why it's needed | What it stores |
> |---------|----------------|----------------|
> | **MongoDB** | Persistent storage | Episodic memory (session summaries), knowledge memory (user profiles), chat history, eval reports, cost tracking |
> | **Redis** | Fast session storage + caching | Working memory (current conversation), HITL approval requests, tool output cache, rate limiting counters |
>
> Both start via `docker compose`. No manual schema setup needed.
>
> **Want to skip Docker entirely?** Use **embedded mode** — everything runs in-memory:
> ```bash
> export LLM_PRIMARY_API_KEY=sk-...
> SPRING_PROFILES_ACTIVE=embedded mvn spring-boot:run
> ```
> Data is lost on restart, but it's perfect for prototyping and demos. See [Embedded Mode](#embedded-mode) below.

### 1. Generate the project from the archetype

```bash
mvn archetype:generate \
  -DarchetypeGroupId=ai.gargantua \
  -DarchetypeArtifactId=agent-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.mycompany \
  -DartifactId=my-agent \
  -Dversion=1.0.0 \
  -DagentName=MyAgent \
  -DinteractiveMode=false
```

This generates a ready-to-run project:

```
my-agent/
├── pom.xml                          -- depends on Gargantua libraries
├── Dockerfile                       -- multi-stage JVM build
├── docker-compose.yml               -- app + MongoDB + Redis
├── src/main/java/com/mycompany/
│   ├── MyAgentApplication.java      -- @SpringBootApplication
│   └── tools/
│       └── SampleTool.java          -- example @AgentTool
└── src/main/resources/
    ├── application.yml              -- full config with defaults
    └── skills/
        ├── default-skill/SKILL.md   -- fallback skill
        └── sample-skill/SKILL.md    -- example skill
```

### 2. Start MongoDB and Redis

```bash
cd my-agent
docker compose up -d mongo redis
```

> Both services must be running before the agent starts. The agent connects to:
> - MongoDB at `mongodb://localhost:27017` (configurable via `MONGODB_URI`)
> - Redis at `redis://localhost:6379` (configurable via `REDIS_URL`)

### 3. Configure your LLM provider and run

The only **mandatory** configuration is the LLM API key. Everything else has sensible defaults.

```bash
# ┌─────────────────────────────────────────────────────────┐
# │ MANDATORY — at least one LLM provider API key          │
# └─────────────────────────────────────────────────────────┘
export LLM_PRIMARY_API_KEY=sk-...

# ┌─────────────────────────────────────────────────────────┐
# │ OPTIONAL — defaults shown                               │
# └─────────────────────────────────────────────────────────┘
# export LLM_PRIMARY_PROVIDER=openai          # openai | azure-openai | anthropic
# export LLM_PRIMARY_MODEL=gpt-4o            # model name
# export LLM_PRIMARY_ENDPOINT=               # required only for azure-openai
# export LLM_FALLBACK_API_KEY=               # auto-failover to fallback provider
# export MONGODB_URI=mongodb://localhost:27017/my-agent
# export REDIS_URL=redis://localhost:6379

mvn spring-boot:run
```

### 4. Test it

```bash
# Sync chat
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -H "X-Session-Id: sess1" \
  -d '{"message": "Hello, what can you do?"}'

# SSE streaming
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -H "X-Session-Id: sess1" \
  -H "Accept: text/event-stream" \
  -d '{"message": "Look up the meaning of life"}'

# Capabilities
curl http://localhost:8080/api/capabilities

# Swagger UI
open http://localhost:8080/swagger-ui

# Redoc
open http://localhost:8080/docs
```

### 5. Full Docker stack

```bash
docker compose up
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
| `agent-spring-boot-starter` | `ai.gargantua` | Auto-configuration, guardrails, routing, orchestrator, tool registry. |
| `agent-adapters` | `ai.gargantua` | REST controllers, skill registries, admin endpoints. |
| `agent-mcp-server` | `ai.gargantua` | MCP Server gateway (optional). |
| `agent-shell` | `ai.gargantua` | Interactive CLI -- Spring Shell 4. |
| `agent-skill-linter-maven-plugin` | `ai.gargantua` | Build-time SKILL.md validation. |
| `agent-archetype` | `ai.gargantua` | Maven archetype to scaffold new agent projects. |

### Typical dependency setup

```xml
<dependencies>
    <!-- Core framework (includes starter + memory + core) -->
    <dependency>
        <groupId>ai.gargantua</groupId>
        <artifactId>agent-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- REST API layer (controllers, admin endpoints, export) -->
    <dependency>
        <groupId>ai.gargantua</groupId>
        <artifactId>agent-adapters</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Optional: MCP server gateway -->
    <dependency>
        <groupId>ai.gargantua</groupId>
        <artifactId>agent-mcp-server</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Optional: SKILL.md linter at build time -->
        <plugin>
            <groupId>ai.gargantua</groupId>
            <artifactId>agent-skill-linter-maven-plugin</artifactId>
            <version>1.0.0</version>
            <executions>
                <execution>
                    <phase>verify</phase>
                    <goals><goal>lint</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## Features

- **Declarative skills** via SKILL.md -- routing, system prompt, and tool bindings in one file
- **@AgentTool** with `@ToolRetry`, `@RequiresApproval`, `@CacheableToolResult`
- **Hybrid routing** -- semantic (all-MiniLM-L6-v2, in-process) + LLM fallback
- **3-layer memory** -- working (Redis) + episodic (MongoDB) + knowledge (MongoDB)
- **Guardrail pipeline** -- PII masking, prompt injection, rate limiting, custom guardrails via `@Order`
- **SSE streaming + sync** endpoints
- **Human-in-the-Loop** approval flow with TTL
- **Structured output** with JSON Schema validation + auto-retry
- **Multi-provider LLM** with rule-based routing + Resilience4j failover
- **Token budget manager** with priority truncation
- **Eval framework** (LLM-as-Judge) with golden datasets per skill
- **Cost tracking** per skill / user / provider / phase
- **Chat history** with search, export (JSON/TXT/MD), GDPR delete
- **Dry-run mode** -- zero side-effects, full execution trace
- **Agent-as-Tool** for multi-agent delegation
- **Interactive CLI** (Spring Shell 4)
- **MCP Server** gateway for Claude Desktop / Cursor
- **SkillsJars** -- import skills as Maven dependencies
- **GraalVM native image** support (startup < 100ms)
- **Kubernetes** manifests (Kustomize + Helm + KEDA)
- **OpenAPI** docs (Swagger UI + Redoc)
- **RFC 9457** Problem Details error handling
- **OpenTelemetry** GenAI semantic conventions

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
├── agent-spring-boot-starter/       -- Auto-configuration, orchestrator, guardrails, routing
├── agent-adapters/                  -- REST controllers, skill registries, repositories
├── agent-mcp-server/                -- MCP Server gateway (optional)
├── agent-example/                   -- Reference agent (weather/search tools)
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
| API Reference | [docs/api-reference.md](docs/api-reference.md) |
| CLI (Agent Shell) | [docs/cli-agent-shell.md](docs/cli-agent-shell.md) |
| LLM Configuration & Routing | [docs/llm-configuration.md](docs/llm-configuration.md) |
| Eval Framework | [docs/eval-framework.md](docs/eval-framework.md) |
| Deployment (Docker, K8s, GraalVM) | [docs/deployment.md](docs/deployment.md) |
| MCP Server | [docs/mcp-server.md](docs/mcp-server.md) |
| Extending the Framework | [docs/extending.md](docs/extending.md) |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
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
| `GET` | `/swagger-ui` | Swagger UI |
| `GET` | `/docs` | Redoc documentation |

---

## Embedded Mode

Run an agent with **zero infrastructure** — no Docker, no MongoDB, no Redis:

```bash
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
| **Primary LLM** | | |
| `LLM_PRIMARY_PROVIDER` | LLM provider: `openai`, `azure-openai`, `anthropic` | `openai` |
| `LLM_PRIMARY_MODEL` | Model name (e.g. `gpt-4o`, `claude-sonnet-4-20250514`) | `gpt-4o` |
| `LLM_PRIMARY_API_KEY` | API key for the primary provider | **(required)** |
| `LLM_PRIMARY_ENDPOINT` | Base URL / endpoint (required for `azure-openai`) | provider default |
| `LLM_PRIMARY_TEMPERATURE` | Sampling temperature (0.0 -- 1.0) | `0.7` |
| `LLM_PRIMARY_MAX_TOKENS` | Max tokens in LLM response | `1000` |
| **Fallback LLM** | Used automatically when primary provider fails | |
| `LLM_FALLBACK_PROVIDER` | Fallback provider | `anthropic` |
| `LLM_FALLBACK_MODEL` | Fallback model | `claude-sonnet-4-20250514` |
| `LLM_FALLBACK_API_KEY` | Fallback API key | *(optional)* |
| `LLM_FALLBACK_ENDPOINT` | Fallback endpoint | provider default |
| **Routing LLM** | Cheap model for skill routing, eval judge, session summaries | |
| `LLM_ROUTING_PROVIDER` | Routing model provider | `openai` |
| `LLM_ROUTING_MODEL` | Routing model name | `gpt-4o-mini` |
| `LLM_ROUTING_API_KEY` | Routing model API key | same as primary |
| **Routing** | | |
| `ROUTING_STRATEGY` | Skill routing: `hybrid`, `semantic`, `llm` | `hybrid` |
| `ROUTING_THRESHOLD` | Semantic similarity threshold (0.0 -- 1.0) | `0.82` |

---

## License

Apache 2.0
