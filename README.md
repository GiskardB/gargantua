# AgentKit -- AI Agent Boilerplate

A Maven multi-module **Java 21 + Spring Boot 4.0.4 + LangChain4j 1.12.1** boilerplate for building autonomous AI agents.

**You write two things:**

1. A `SKILL.md` file that declares what your agent can do (routing, guardrails, and system prompt included).
2. An `@AgentTool` class that implements the actual logic.

**Everything else is provided:** routing, memory, guardrails, streaming, HITL approval, cost tracking, eval, CLI, MCP gateway, Kubernetes manifests, and native image support.

---

## Features

- **Declarative skills via SKILL.md** -- define routing, system prompt, and tool bindings in a single Markdown file
- **@AgentTool annotation** for tools with `@ToolRetry`, `@RequiresApproval`, `@CacheableToolResult`
- **Hybrid routing** -- semantic similarity (all-MiniLM-L6-v2 in-process) with LLM fallback
- **3-layer memory** -- working (Redis) + episodic (MongoDB) + knowledge (MongoDB)
- **Guardrail pipeline** (input + output) with PII masking and prompt-injection detection
- **SSE streaming + sync endpoints**
- **Human-in-the-Loop (HITL)** approval flow
- **Structured output** with JSON Schema validation
- **Multi-provider LLM** with rule-based routing and Resilience4j failover
- **Token budget management**
- **Eval framework** (LLM-as-Judge)
- **Cost tracking** per skill / user / provider
- **Chat history** with GDPR delete + export (JSON / TXT / MD)
- **Dry-run mode**
- **Agent-as-Tool** for multi-agent delegation
- **Interactive CLI** (Spring Shell 4)
- **MCP Server gateway**
- **SkillsJars** -- package skills as Maven dependencies
- **SKILL.md linter** Maven plugin
- **GraalVM native image** support
- **Kubernetes manifests** (Kustomize + Helm + KEDA)
- **OpenAPI docs** (Swagger UI + Redoc)
- **RFC 9457 Problem Details** error handling
- **OpenTelemetry GenAI** semantic conventions

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 4.0.4 |
| Spring Framework | 7.0.5 |
| LangChain4j | 1.12.1 |
| MongoDB | latest |
| Redis | latest |
| Spring Shell | 4.0.1 |
| springdoc-openapi | 3.0.2 |
| Resilience4j | 2.3.0 |
| Caffeine | 3.2.0 |
| MCP SDK | 0.9.0 |
| GraalVM | 21 |
| Testcontainers | 1.20.4 |
| WireMock | 3.10.0 |

---

## Project Structure

```
agent-boilerplate/
├── agent-core/                      -- Pure domain: records, interfaces, annotations (zero Spring deps)
├── agent-memory-sdk/                -- Standalone memory library (Redis + MongoDB adapters)
├── agent-spring-boot-starter/       -- Auto-configuration, guardrails, routing, orchestrator
├── agent-adapters/                  -- REST controllers, skill registries, repositories
├── agent-mcp-server/                -- MCP Server gateway (optional)
├── agent-example/                   -- Runnable example agent with weather/search tools
├── agent-shell/                     -- Interactive CLI (Spring Shell 4)
├── agent-skill-linter-maven-plugin/ -- Build-time SKILL.md validation
├── k8s/                             -- Kubernetes manifests (Kustomize + Helm)
├── Dockerfile                       -- Multi-stage (JVM + GraalVM native)
└── docker-compose.yml               -- Local dev (agent + MongoDB + Redis)
```

---

## Quick Start

Total time: under 15 minutes.

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.9+ (or use the included `mvnw`)

### 1. Clone and build

```bash
git clone https://github.com/your-org/agent-boilerplate.git && cd agent-boilerplate
./mvnw clean package -DskipTests
```

### 2. Start infrastructure

```bash
docker compose up -d mongo redis
```

### 3. Run the example agent

```bash
cd agent-example
../mvnw spring-boot:run
```

### 4. Test it

```bash
# Sync chat
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -H "X-Session-Id: sess1" \
  -d '{"message": "What is the weather in Rome?"}'

# SSE streaming
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -H "X-Session-Id: sess1" \
  -H "Accept: text/event-stream" \
  -d '{"message": "What is the weather in Rome?"}'

# Capabilities
curl http://localhost:8080/api/capabilities

# Health
curl http://localhost:8080/actuator/health
```

### 5. Interactive CLI

```bash
java -jar agent-shell/target/agent-shell-1.0.0.jar
# Then type: chat
```

### 6. Docker (full stack)

```bash
docker compose up agent-jvm
# Or native:
docker compose --profile native up agent-native
```

---

## Build Your Own Agent

Three files. That's it.

### Step 1 -- Add the dependency

```xml
<dependency>
    <groupId>io.agentkit</groupId>
    <artifactId>agent-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2 -- Write a Tool

```java
@Component
public class MyTool {

    @AgentTool(description = "Fetches product info by ID")
    public ProductInfo getProduct(String productId) {
        return productService.find(productId);
    }
}
```

### Step 3 -- Write a Skill

Create `src/main/resources/skills/product-skill/SKILL.md`:

```markdown
---
name: product-skill
description: Helps users find product information.
version: 1.0.0
allowed-tools:
  - getProduct
metadata:
  active: true
  domain: ecommerce
---

## Role
You are a product assistant.

## Behavior
- Always call tools before answering.

## Scope
Only product-related questions.
```

### Step 4 -- Configure and run

```yaml
# application.yml (minimum)
agent:
  llm:
    primary:
      provider: openai
      model: gpt-4o
      api-key: ${OPENAI_API_KEY}
  skill:
    path: classpath:skills/
```

```bash
./mvnw spring-boot:run
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
| LLM Configuration & Routing Rules | [docs/llm-configuration.md](docs/llm-configuration.md) |
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
| `POST` | `/api/admin/llm/simulate` | Simulate routing |
| `GET` | `/swagger-ui` | Swagger UI |
| `GET` | `/docs` | Redoc documentation |

---

## License

Apache 2.0
