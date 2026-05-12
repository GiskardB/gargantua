# Getting Started

Welcome to **Gargantua** — the AI agent framework that turns Java methods into a deployable agent service with skills, memory, guardrails, streaming, and multi-agent orchestration.

This guide walks you through installing, running, and customizing your first agent.

---

## Prerequisites

- **Java 21+** — the framework uses Virtual Threads (Project Loom)
- **Maven 3.9+**
- An OpenAI-compatible API key *(or any LangChain4j-supported provider)*
- *(Optional)* **Docker & Docker Compose** — for MongoDB, Redis, and Ollama in standard mode

---

## Try it in 60 seconds (Embedded mode)

> No Docker, no MongoDB, no Redis — everything runs in-memory.

### 1. Tell Maven where to find JitPack

The `maven-archetype-plugin` ignores `-DarchetypeRepository`, so the JitPack repository must live in `~/.m2/settings.xml`. Append this profile (create the file if missing):

```xml
<settings>
  <profiles>
    <profile>
      <id>jitpack</id>
      <repositories>
        <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository><id>jitpack.io</id><url>https://jitpack.io</url></pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>jitpack</activeProfile></activeProfiles>
</settings>
```

### 2. Generate a new agent project

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.github.giskardb.gargantua \
  -DarchetypeArtifactId=agent-archetype \
  -DarchetypeVersion=v1.2.18 \
  -DgroupId=com.mycompany -DartifactId=my-agent \
  -Dversion=1.0.0 -DagentName=MyAgent -DinteractiveMode=false
```

This generates a Maven project with:

```
my-agent/
├── pom.xml                          # depends on Gargantua engine
├── .env.example                     # documented env vars template
├── src/main/java/com/mycompany/
│   ├── MyAgentApplication.java      # @SpringBootApplication
│   └── tools/
│       └── SampleTool.java          # example @AgentTool
└── src/main/resources/
    ├── application.yml              # full config with defaults
    ├── application-embedded.yml     # embedded mode (no Docker needed)
    └── skills/
        ├── default-skill/SKILL.md   # fallback skill
        └── sample-skill/SKILL.md    # example skill
```

### 3. Run it

```bash
cd my-agent
LLM_PRIMARY_PROVIDER=openai \
LLM_PRIMARY_MODEL=gpt-4o \
LLM_PRIMARY_API_KEY=sk-your-key \
LLM_PRIMARY_ENDPOINT=https://api.openai.com/v1 \
SPRING_PROFILES_ACTIVE=embedded \
mvn spring-boot:run
```

### 4. Talk to your agent

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: me" -H "X-Session-Id: s1" -H "X-Tenant-Id: acme" \
  -d '{"message": "Hello, what can you do?"}'
```

That's a running agent with skill routing, guardrails, memory, streaming, and a REST API. Read on to add your own tools and skills.

---

## OpenAI-compatible providers

Gargantua works with any OpenAI-compatible endpoint:

| Provider     | `LLM_PRIMARY_PROVIDER` | `LLM_PRIMARY_ENDPOINT` |
|--------------|------------------------|--------------------------|
| OpenAI       | `openai`               | `https://api.openai.com/v1` |
| Anthropic    | `anthropic`            | *(default)* |
| Azure OpenAI | `azure-openai`         | `https://your-resource.openai.azure.com` |
| Ollama       | `ollama`               | `http://localhost:11434` |
| LiteLLM      | `openai`               | `http://localhost:4000` |
| vLLM         | `openai`               | `http://localhost:8000` |

Add **Google Gemini, Mistral, Cohere, AWS Bedrock**, etc. by including the corresponding LangChain4j module dependency. See [LLM Configuration](#llm-configuration) for details.

---

## Full Setup (with persistent storage)

For production-like setups with persistent memory, chat history, and a local routing model, follow this guide.

### 1. Generate the project

Use the same `mvn archetype:generate` command from step 2 above.

### 2. Start infrastructure

```bash
cd my-agent
docker compose up -d mongo redis ollama

# Pull the local routing model (one-time)
docker compose exec ollama ollama pull phi4-mini
```

| Service     | What it does                                                      | Port  |
|-------------|-------------------------------------------------------------------|-------|
| **MongoDB** | Chat history, session summaries, user profiles, costs              | 27017 |
| **Redis**   | Session memory, HITL approvals, tool cache, rate limits            | 6379  |
| **Ollama**  | Local routing model (zero API cost for routing & summaries)        | 11434 |

### 3. Configure your LLM providers

Gargantua uses **three LLM roles** — each can be a different provider and model:

| Role         | Purpose                                              | Default                                  | Cost                |
|--------------|------------------------------------------------------|------------------------------------------|---------------------|
| **Primary**  | Agent conversations — answers the user               | OpenAI `gpt-4o`                          | Per-token API cost  |
| **Fallback** | Auto-failover when primary fails                     | Anthropic `claude-sonnet-4-20250514`     | Per-token (only on failure) |
| **Routing**  | Internal: skill routing, session summaries           | Ollama `phi4-mini` (local)               | **Free** (if local) |

Copy `.env.example` to `.env` and fill in the primary provider:

```bash
# ── Primary LLM — the model that answers users ──────────────────
export LLM_PRIMARY_PROVIDER=openai
export LLM_PRIMARY_MODEL=gpt-4o
export LLM_PRIMARY_API_KEY=sk-your-key-here
export LLM_PRIMARY_ENDPOINT=https://api.openai.com/v1

# ── Fallback — optional, auto-failover on primary failure ───────
# export LLM_FALLBACK_PROVIDER=azure-openai
# export LLM_FALLBACK_MODEL=gpt-4o
# export LLM_FALLBACK_API_KEY=your-azure-key
# export LLM_FALLBACK_ENDPOINT=https://your-resource.openai.azure.com

# ── Routing — local Ollama by default, no config needed ─────────
# Override only to use a cloud provider for routing:
# export LLM_ROUTING_PROVIDER=openai
# export LLM_ROUTING_MODEL=gpt-4o-mini
# export LLM_ROUTING_API_KEY=sk-...
```

### 4. Run

```bash
mvn spring-boot:run
```

### 5. Test

```bash
# Chat via REST
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" -H "X-Session-Id: sess1" -H "X-Tenant-Id: acme" \
  -d '{"message": "Hello, what can you do?"}'

# See what skills are available
curl http://localhost:8080/.well-known/agent.json

# Chat web UI (dark theme, SSE streaming)
open http://localhost:8080/chat

# Interactive API docs
open http://localhost:8080/swagger-ui
```

---

## Add a Tool, Add a Skill

Once the project runs, you extend the agent in two pieces: **tools** (Java methods) and **skills** (Markdown files or `@AgentSkill` classes).

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

> Continue with [Skills & Routing](#skills-and-routing) to see all the SKILL.md options, and [Tools & Annotations](#tools-and-annotations) for retry, caching, and HITL approval patterns.

---

## Maven coordinates

Gargantua publishes to **two channels**. Pick the one that suits your stage:

| Channel | When to use | Coordinates                              | Versioning |
|---------|-------------|------------------------------------------|------------|
| **Maven Central** | Production — signed artifacts, immutable releases, no extra `<repository>` block. | `io.github.giskardb:agent-*` | semver, no prefix (`1.2.19`) |
| **JitPack** | Snapshots, intermediate tags, `develop-SNAPSHOT`, branch builds — built on-demand at the consumer end. | `com.github.giskardb.gargantua:agent-*` | mirrors Git tags (`v1.2.19`) |

Both serve **the same source code** for tagged releases; the choice is purely operational.

### Maven Central (recommended)

```xml
<properties>
    <gargantua.version>1.2.19</gargantua.version>
</properties>

<dependencies>
    <!-- Core engine: orchestrator, guardrails, routing, memory, REST API -->
    <dependency>
        <groupId>io.github.giskardb</groupId>
        <artifactId>agent-engine</artifactId>
        <version>${gargantua.version}</version>
    </dependency>

    <!-- Optional: MCP server gateway for Claude Desktop / Cursor -->
    <dependency>
        <groupId>io.github.giskardb</groupId>
        <artifactId>agent-mcp-server</artifactId>
        <version>${gargantua.version}</version>
    </dependency>
</dependencies>
```

No `<repositories>` entry needed — Maven Central is queried by default.

### JitPack (snapshots and intermediate builds)

```xml
<properties>
    <gargantua.version>v1.2.19</gargantua.version>
</properties>

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.giskardb.gargantua</groupId>
        <artifactId>agent-engine</artifactId>
        <version>${gargantua.version}</version>
    </dependency>
</dependencies>
```

Use JitPack for `develop-SNAPSHOT` or for fix branches that have not yet been tagged.

### Available artifacts

| Artifact | Maven Central groupId | JitPack groupId | Description |
|----------|------------------------|------------------|-------------|
| `agent-core` | `io.github.giskardb` | `com.github.giskardb.gargantua` | Pure domain: records, interfaces, annotations. Zero Spring deps. |
| `agent-memory-sdk` | `io.github.giskardb` | `com.github.giskardb.gargantua` | Standalone 3-layer memory (Redis + MongoDB). Reusable in any project. |
| `agent-engine` | `io.github.giskardb` | `com.github.giskardb.gargantua` | Auto-configuration, guardrails, routing, orchestrator, tool registry, REST controllers. |
| `agent-mcp-server` | `io.github.giskardb` | `com.github.giskardb.gargantua` | MCP Server gateway (optional). |
| `agent-skill-linter-maven-plugin` | `io.github.giskardb` | `com.github.giskardb.gargantua` | Build-time SKILL.md validation. |
| `agent-archetype` | `io.github.giskardb` | `com.github.giskardb.gargantua` | Maven archetype to scaffold new agent projects. |

> The Maven archetype today is published to JitPack only (it's typically resolved through `-DarchetypeRepository=https://jitpack.io` at generation time). Use the JitPack coord for `mvn archetype:generate` and the Maven Central coord for runtime dependencies.

---

## Embedded mode vs. standard mode

Embedded mode runs an agent with **zero infrastructure** — perfect for development, CI, demos, and learning the framework.

| What                  | Standard mode | Embedded mode |
|-----------------------|---------------|---------------|
| Working memory        | Redis         | ConcurrentHashMap |
| Episodic memory       | MongoDB       | ConcurrentHashMap |
| Knowledge memory      | MongoDB       | ConcurrentHashMap |
| Chat history          | MongoDB       | *(not available)* |
| HITL approvals        | Redis         | ConcurrentHashMap |
| Tool cache            | Redis         | *(not available)* |
| Cost tracking         | MongoDB       | *(not available)* |
| Audit trail           | MongoDB       | ConcurrentHashMap |
| Requires Docker       | Yes           | **No** |
| Data persisted        | Yes           | **No** (lost on restart) |

**When to use embedded:** local dev, prototyping, CI, demos.
**When NOT to use:** production, load testing.

```bash
# Switch any project to embedded mode at startup:
SPRING_PROFILES_ACTIVE=embedded mvn spring-boot:run
```

---

## Tech stack

| Component         | Version              |
|-------------------|----------------------|
| Java              | 21 (Virtual Threads) |
| Spring Boot       | 4.0.4                |
| Spring Framework  | 7.0.5                |
| LangChain4j       | 1.12.1               |
| MongoDB           | 8.0                  |
| Redis             | 7.4                  |
| springdoc-openapi | 3.0.2                |
| Resilience4j      | 2.3.0                |
| Caffeine          | 3.2.0                |
| MCP SDK           | 0.9.0                |
| GraalVM           | 21                   |

---

## Where to next?

- **[Skills & Routing](#skills-and-routing)** — declarative `SKILL.md`, hybrid semantic + LLM routing
- **[Tools & Annotations](#tools-and-annotations)** — `@AgentTool`, `@ToolRetry`, `@CacheableToolResult`, HITL
- **[Agent DSL](#agent-dsl)** — `@AgentSkill` in Java + `@AgentsFlow` multi-step pipelines
- **[Memory System](#memory-system)** — 3-layer working / episodic / knowledge memory
- **[Guardrails](#guardrails)** — PII, prompt-injection, rate limit, schema, RBAC
- **[LLM Configuration](#llm-configuration)** — multi-provider routing, failover, model catalogs
- **[Architecture Diagrams](#architecture-diagrams)** — animated request journey through the framework
