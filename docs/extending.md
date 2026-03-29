# Extending the Framework

This guide shows how to customize and extend Gargantua beyond the default behavior.
Each section is independent — pick what you need.

---

## Prerequisites

Before extending, make sure your agent runs correctly with the base setup:

| Requirement | Purpose | How to start |
|-------------|---------|-------------|
| **MongoDB** | Episodic memory, chat history, knowledge, evals, costs | `docker compose up -d mongo` |
| **Redis** | Working memory, HITL approvals, tool cache, rate limits | `docker compose up -d redis` |
| **Ollama** | Local routing model (skill routing, session summaries, eval judge) | `docker compose up -d ollama` |
| **LLM API key** | Primary model for agent responses | `export LLM_PRIMARY_API_KEY=sk-...` |

```bash
# Start everything
docker compose up -d mongo redis ollama

# Pull the routing model (one-time, after first Ollama start)
docker compose exec ollama ollama pull phi4-mini
```

> **Don't want Docker?** Use `SPRING_PROFILES_ACTIVE=embedded` — all storage runs in-memory. See [Embedded Mode](../README.md#embedded-mode).

---

## Custom Guardrails

Guardrails are filters that run **before** (input) or **after** (output) every LLM call. Gargantua ships with built-in guardrails (PII masking, prompt injection, rate limiting), but you can add your own.

### How it works

Each guardrail is a Spring `@Component` with an `@Order` annotation that determines its position in the pipeline. Input guardrails can **block** a request; output guardrails can **transform** the response.

### Example: block profanity in user messages

```java
import ai.gargantua.core.guardrail.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)  // Runs after built-in guardrails (10–50)
public class ProfanityGuardrail implements InputGuardrail {

    @Override
    public String name() {
        return "profanity-filter";
    }

    @Override
    public boolean isEnabled(Object props) {
        return true;  // Always active; or read from config to make it toggleable
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (containsProfanity(ctx.userMessage())) {
            return GuardrailResult.block(name(), "Message contains inappropriate language");
        }
        return GuardrailResult.pass(name());
    }

    private boolean containsProfanity(String text) {
        // Your detection logic here
        return false;
    }
}
```

**That's it.** No registration, no config changes. Spring discovers the `@Component` and inserts it into the pipeline at `@Order(60)`.

You can also toggle it at runtime via `POST /api/admin/guardrails/profanity-filter/toggle`.

### Built-in guardrail order

| Order | Guardrail | Type |
|-------|-----------|------|
| 10 | MaxLength | Input |
| 20 | PromptInjection | Input |
| 30 | TopicScope | Input |
| 40 | PiiMasking | Input |
| 50 | RateLimit | Input |
| **60+** | **Your custom guardrails** | Input |
| 10 | PiiOutput | Output |
| 20 | Disclaimer | Output |
| 30 | ScopeValidator | Output |
| 40 | SchemaValidator | Output |

---

## Context Enrichers

A Context Enricher injects **runtime data** into the system prompt at request time — without touching the SKILL.md file. This is how you pass user-specific information (plan, language, account balance) to the LLM.

### Where enricher output appears in the prompt

```
┌─────────────────────────┐
│ SKILL.md body           │  ← Static (from file)
├─────────────────────────┤
│ Enricher: user_context  │  ← Dynamic (your enricher)
│ Enricher: account_data  │  ← Dynamic (another enricher)
├─────────────────────────┤
│ Episodic memory         │  ← From MongoDB
│ Knowledge segments      │  ← From MongoDB
├─────────────────────────┤
│ Working memory (chat)   │  ← From Redis
└─────────────────────────┘
```

### Example: inject user profile into prompt

```java
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import org.springframework.stereotype.Component;

@Component
public class UserProfileEnricher implements ContextEnricher {

    private final UserProfileRepository userRepo;

    public UserProfileEnricher(UserProfileRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public String sectionName() {
        return "user_context";  // Appears as "### USER_CONTEXT" in the prompt
    }

    @Override
    public int order() {
        return 10;  // Lower = inserted first
    }

    @Override
    public String enrich(EnricherContext ctx) {
        UserProfile profile = userRepo.findById(ctx.userId()).orElse(null);
        if (profile == null) return null;  // Returning null = section is skipped

        return """
            User plan: %s
            Preferred language: %s
            Member since: %s
            """.formatted(profile.plan(), profile.language(), profile.memberSince());
    }
}
```

### Restrict an enricher to a specific skill

Override `targetSkill()` to make the enricher run only for one skill:

```java
@Override
public String targetSkill() {
    return "financial-skill";  // Only active when this skill is selected
}
```

### Pass custom data from the client via HTTP headers

Any header starting with `X-Context-` is automatically available in `EnricherContext.attributes()`:

```
HTTP header:  X-Context-Language: it
HTTP header:  X-Context-Region: EU

In enricher: ctx.attributes().get("language") → "it"
              ctx.attributes().get("region")   → "EU"
```

---

## Override a Memory Adapter

Every memory adapter uses `@ConditionalOnMissingBean` — if you declare your own bean of the same type, it replaces the default. This works because the framework registers its beans **only if no bean of that type already exists**.

### Example: replace Redis working memory with a custom implementation

```java
import ai.gargantua.core.memory.WorkingMemoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomMemoryConfig {

    @Bean
    public WorkingMemoryPort workingMemory() {
        return new MyCustomWorkingMemory();
        // The framework's RedisWorkingMemoryAdapter will NOT be registered
    }
}
```

### Replaceable ports

| Port interface | Default implementation | Storage |
|---------------|----------------------|---------|
| `WorkingMemoryPort` | `RedisWorkingMemoryAdapter` | Redis |
| `EpisodicMemoryPort` | `MongoEpisodicMemoryAdapter` | MongoDB |
| `KnowledgeMemoryPort` | `MongoKnowledgeMemoryAdapter` | MongoDB |
| `ApprovalStore` | `RedisApprovalStore` | Redis |
| `OrchestratorEngine` | `DefaultOrchestratorEngine` | — |
| `TokenBudgetManager` | `DefaultTokenBudgetManager` | — |

---

## Agent-as-Tool (Multi-Agent Delegation)

One agent can delegate sub-tasks to another agent by wrapping it as a tool. The sub-agent runs in an isolated session and returns its response as a tool result.

### Example: delegate financial analysis to a specialized agent

```java
import ai.gargantua.core.orchestrator.AgentAsToolPort;
import ai.gargantua.core.orchestrator.AgentToolRequest;
import ai.gargantua.core.orchestrator.AgentToolResponse;
import ai.gargantua.core.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DelegationTools {

    private final AgentAsToolPort pfmAgent;

    public DelegationTools(AgentAsToolPort pfmAgent) {
        this.pfmAgent = pfmAgent;
    }

    @AgentTool(description = """
        Delegates a financial analysis task to the PFM Agent.
        Use when the user asks for spending analysis or anomaly detection.
        """)
    public AgentToolResponse analyzeFinances(String task, String userId) {
        return pfmAgent.invoke(
            new AgentToolRequest(task, userId, null, Map.of())
        );
    }
}
```

The sub-agent uses its own skill routing, memory, and guardrails — completely independent from the parent agent.

---

## Dry-Run Mode

Dry-run executes the full pipeline (routing, guardrails, tool calling) **without side effects** — no data is persisted, no real tool calls are made, no costs are tracked. Useful for testing, debugging, and CI.

### Activate via HTTP header

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-User-Id: test" \
  -H "X-Session-Id: test" \
  -H "X-Dry-Run: true" \
  -d '{"message": "What is the weather in Rome?"}'
```

### Provide fake tool responses

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-Dry-Run: true" \
  -H 'X-Dry-Run-Tool-Stubs: {"getWeather": {"temperature": 20, "conditions": "sunny"}}' \
  -d '{"message": "What is the weather in Rome?"}'
```

### Configuration

```yaml
agent:
  dry-run:
    enabled: true           # Set to false in application-prod.yml
    allowed-profiles:
      - dev
      - test
      - staging
```

The dry-run response includes a full execution trace: which skill was selected, which guardrails ran, which tools were called (with stub markers), routing confidence, and token usage.

---

## MCP Server

Expose your agent as an [MCP](https://modelcontextprotocol.io/) server so that Claude Desktop, Cursor, VS Code, or other MCP-compatible clients can call it as a tool.

### Enable

```yaml
agent:
  mcp:
    enabled: true
    mode: gateway            # gateway (recommended) | transparent
    server:
      name: my-agent
      version: 1.0.0
    transport:
      type: sse
      path: /mcp
```

### Gateway mode (default)

One MCP tool: `chat`. The client sends a message, the agent routes it through the full pipeline (guardrails, routing, memory, tools) and returns the response.

```
MCP Client → tool: chat(message, sessionId?, skillName?) → OrchestratorEngine → response
```

### Transparent mode

Exposes fine-grained primitives for advanced integrations:

| MCP primitive | What it maps to |
|---------------|-----------------|
| Tool `invoke_skill` | OrchestratorEngine with forceSkill |
| Resource `gargantua://capabilities` | CapabilitiesService |
| Resource `gargantua://skill/{name}` | SkillRegistry (description only, no system prompt) |
| Prompt `use-skill` | SKILL.md body as prompt template |

### Connect Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "my-agent": {
      "url": "http://localhost:8080/mcp/sse",
      "transport": "sse"
    }
  }
}
```

After restarting Claude Desktop, a `chat` tool appears in the tool list.

### SSE endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/mcp/sse` | SSE initialization stream |
| `POST` | `/mcp/message` | JSON-RPC 2.0 messages from client |

The path prefix is configurable via `agent.mcp.transport.path`.

### Security

```yaml
agent:
  mcp:
    security:
      require-api-key: true
      api-key: ${MCP_API_KEY}
```

When enabled, the client must send `Authorization: Bearer <key>` on the SSE connection.

### MCP client + server coexistence

The agent can simultaneously:
- **Be an MCP server** (this feature) — invoked by Claude Desktop, other agents
- **Be an MCP client** (`langchain4j-mcp` in the engine) — calling external MCP servers as tools

Both directions work at the same time.

---

## Interactive CLI (Agent Shell)

The Agent Shell is a command-line interface built on Spring Shell 4.0.1 for interacting with your agent from the terminal. Useful for development, debugging, and running evals.

### Start the shell

```bash
# Embedded mode — runs the full agent in-process (needs MongoDB + Redis or embedded profile)
java -jar agent-shell/target/agent-shell-1.0.0.jar

# Remote mode — connects to a running agent via HTTP
java -jar agent-shell/target/agent-shell-1.0.0.jar \
  --agent.shell.mode=remote \
  --agent.shell.remote.url=http://localhost:8080

# Single message — non-interactive (for scripts and CI)
java -jar agent-shell/target/agent-shell-1.0.0.jar chat --message "Hello"
```

### Chat command

Start an interactive conversation:

```
agent:shell> chat

You: What's the weather in Rome?

[routing: weather-skill (semantic, 0.94)] [tools: getWeather]
Agent: The weather in Rome is currently sunny with 22°C (72°F).
[487ms | in: 134 tok | out: 42 tok | $0.0004]

You: \exit
```

Special commands inside the chat session:

| Command | Action |
|---------|--------|
| `\exit` or `\q` | Return to shell prompt |
| `\new` | Start a new session (fresh memory) |
| `\dry` | Toggle dry-run mode on/off |
| `\skill <name>` | Force the next message to use a specific skill |
| `\history` | Show last 10 messages |
| `\info` | Show session info (skills used, total tokens, cost) |
| `\clear` | Clear the terminal screen |

### Other commands

```bash
# Skills
skill list                        # Table of all skills with status, version, domain
skill show weather-skill          # Show skill detail
skill reload                      # Hot reload skills from filesystem

# Sessions
session new                       # Create a new session
session list                      # List recent sessions
session resume <sessionId>        # Resume a previous session

# Evals
eval run --skill weather-skill    # Run eval suite for one skill
eval run --all                    # Run all eval suites

# Costs
cost summary --days 7             # Cost breakdown for the last 7 days
```

### Configuration

```yaml
agent:
  shell:
    mode: embedded           # embedded | remote
    user-id: dev-user        # Default userId for shell sessions
    show-meta: true          # Show routing info after each response
    show-timing: true        # Show duration and token counts
    ansi: auto               # auto | always | never — ANSI color support
    remote:
      url: http://localhost:8080
      timeout-ms: 30000
```

---

## Token Budget Manager

The budget manager prevents the prompt from exceeding the model's context window. It estimates tokens for each prompt component and truncates from the least important sections first.

### What gets truncated (in order of priority)

| Priority | Component | Truncatable? |
|----------|-----------|-------------|
| 1 (highest) | System prompt (SKILL.md body) | Never |
| 2 | User message | Never |
| 3 | Tool descriptions | Never |
| 4 | Enricher output | Never |
| 5 | References (SKILL.md `references/` folder) | Yes — removed first |
| 6 | Knowledge segments | Yes — oldest removed |
| 7 (lowest) | Episodic summaries | Yes — oldest removed |

### Configuration

```yaml
agent:
  memory:
    composer:
      max-context-tokens: 3000   # Total token budget for the composed prompt
```

---

## Cost Tracking

Every LLM call is tracked in MongoDB with provider, model, token counts, estimated cost, skill, user, and phase (routing / agent / summarizer / eval).

### Configuration

```yaml
agent:
  cost-tracking:
    enabled: true
    retention-days: 365
    pricing:
      openai:
        gpt-4o:
          input-per-1k-tokens: 0.0025
          output-per-1k-tokens: 0.010
      anthropic:
        claude-sonnet-4-20250514:
          input-per-1k-tokens: 0.003
          output-per-1k-tokens: 0.015
```

> Ollama routing calls have zero cost and are tracked with `estimatedCostUsd: 0.0`.

### Admin endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/admin/costs/summary?from=...&to=...` | Total cost per skill and provider |
| `GET /api/admin/costs/by-skill?from=...&to=...` | Breakdown by skill |
| `GET /api/admin/costs/by-user/{userId}?from=...&to=...` | Per-user consumption |
| `GET /api/admin/costs/daily?from=...&to=...` | Daily time series (for dashboards) |

---

## Chat History & Export

All messages are stored in MongoDB and available via REST API.

| Operation | Endpoint | Description |
|-----------|----------|-------------|
| List sessions | `GET /api/agent/chat/sessions/{userId}` | Paginated list of user sessions |
| View messages | `GET /api/agent/chat/history/{userId}/{sessionId}` | Messages in a session |
| Full-text search | `GET /api/agent/chat/history/{userId}/search?q=keyword` | Search across all messages |
| Export | `GET /api/agent/chat/export/{userId}/{sessionId}?format=md` | Download as JSON, TXT, or Markdown |
| Delete session | `DELETE /api/agent/chat/history/{userId}/{sessionId}` | Remove one session |
| GDPR delete all | `DELETE /api/agent/chat/history/{userId}` | Remove all data for a user |

---

## Structured Output (JSON Schema Validation)

If a skill needs to return structured JSON instead of free text, add a JSON Schema and reference it in the SKILL.md frontmatter.

### Step 1 — Create the schema

`src/main/resources/skills/my-skill/assets/schema.json`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["answer", "confidence"],
  "properties": {
    "answer": { "type": "string" },
    "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
  }
}
```

### Step 2 — Reference it in SKILL.md

```yaml
---
name: my-skill
description: ...
version: 1.0.0
allowed-tools:
  - lookup
metadata:
  active: true
  output-schema: assets/schema.json   # ← enables schema validation
---
```

### What happens at runtime

1. The LLM is instructed to respond with JSON matching the schema
2. `SchemaValidatorGuardrail` validates the response
3. If validation fails, the framework **automatically retries** with a corrective prompt (up to 2 retries by default)
4. After max retries, a `SchemaValidationException` is returned to the client

```yaml
agent:
  output:
    validation-retries: 2   # Number of retry attempts on schema mismatch
```
