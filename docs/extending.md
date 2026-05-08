# Extending the Framework

This guide shows how to customize and extend Gargantua beyond the default behavior.
Each section is independent — pick what you need.

---

## Prerequisites

Before extending, make sure your agent runs correctly with the base setup:

| Requirement | Purpose | How to start |
|-------------|---------|-------------|
| **MongoDB** | Episodic memory, chat history, knowledge, costs | `docker compose up -d mongo` |
| **Redis** | Working memory, HITL approvals, tool cache, rate limits | `docker compose up -d redis` |
| **Ollama** | Local routing model (skill routing, session summaries) | `docker compose up -d ollama` |
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
| 5 | RbacGuardrail | Input |
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

Headers prefixed `X-Context-` are extracted by both the synchronous and streaming chat controllers, the prefix is stripped, the remainder is lowercased and the pairs are forwarded into `EnricherContext.attributes()` (and the input-guardrail attribute map) before any enricher runs. The same map is also seeded onto `AgentRequest.contextAttributes()` so it shows up in `LlmRoutingContext.attributes` for `attribute-match` routing rules.

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

> 🚧 **Planned — not yet wired.** A first-class `AgentAsToolPort` for in-process multi-agent delegation is on the roadmap. Today, two paths cover the same use case:
>
> 1. **A2A (cross-process or cross-host).** Wrap the call to another agent as an `@AgentTool` method using `HttpA2AClient` — see the [A2A section](#a2a-protocol--call-other-agents) below. This is the recommended path even for co-located agents.
> 2. **`@AgentsFlow` (in-process, sequential)**. Chain skills with the DSL — see [Agent DSL](agent-dsl.md). This handles the most common "agent A then agent B" pattern.

When the dedicated `AgentAsToolPort` ships, it will let one orchestrator engine delegate to another agent's full pipeline (its own routing, memory, guardrails, audit) within a single JVM, returning the response as a tool result.

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

One MCP tool — name configurable via `agent.mcp.gateway.tool-name` (default `agent-chat`). The client sends a message, the agent routes it through the full pipeline (guardrails, routing, memory, tools) and returns the response.

```
MCP Client → tool: agent-chat(userMessage, userId?, sessionId?) → OrchestratorEngine → response.text
```

The `userId` defaults to `mcp-client` and `sessionId` is auto-generated as a UUID when the client doesn't supply one. Pass a stable `sessionId` from the client to enable working memory across calls.

### Capabilities resource

The MCP resource `agent://capabilities` returns a live snapshot of the agent — sourced from the running `SkillRegistry` and `ToolRegistry` so it always reflects the current configuration (including hot-reloaded skills):

```jsonc
{
  "name": "my-agent", "version": "1.0.0", "description": "...",
  "mode": "standalone",
  "tools":      { "agent-chat": "Send a message to the AI agent for processing" },
  "skills":     [ { "name": "default-skill", "description": "...", "version": "1.0.0", "domain": "general", "active": true } ],
  "agentTools": [ { "name": "getOrderStatus", "description": "...", "requiresApproval": false, "dangerous": false, "parallelizable": true } ]
}
```

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

After restarting Claude Desktop, the configured MCP gateway tool appears in the tool list (default `agent-chat`, configurable via `agent.mcp.gateway.tool-name`).

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

Every LLM call is tracked in MongoDB with provider, model, token counts, estimated cost, skill, user, and phase (routing / agent / summarizer).

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

## RAG / Vector Store

Skills can declare a knowledge base to enable retrieval-augmented generation. When a skill has `metadata.knowledge-base` set, the `RagEnricher` automatically queries the vector store and injects the most relevant documents into the system prompt. If a skill has no `knowledge-base`, there is zero overhead.

### Declare a knowledge base in SKILL.md

```yaml
---
name: hr-assistant
description: Answers HR policy questions
version: 1.0.0
allowed-tools:
  - lookupEmployee
metadata:
  active: true
  knowledge-base: hr-docs         # Activates RAG for this skill
  rag-max-results: 5              # Max documents to retrieve (default: 5)
  rag-min-score: 0.3              # Minimum similarity score (default: 0.3)
---
```

### Implement a custom VectorStorePort

The framework provides `InMemoryVectorStore` for embedded mode. For production, implement `VectorStorePort` with your preferred vector database:

```java
import ai.gargantua.core.rag.VectorStorePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStorePort vectorStore() {
        return new PineconeVectorStore(pineconeClient);
        // The framework's InMemoryVectorStore will NOT be registered
    }
}
```

`VectorStorePort` follows the same `@ConditionalOnMissingBean` pattern as all other adapters.

---

## RBAC & Multi-Tenancy

### SecurityContext

The `SecurityContext` is built from HTTP headers on every request:

| Header | Field | Description |
|--------|-------|-------------|
| `X-User-Id` | `userId` | User identifier (already required) |
| `X-Tenant-Id` | `tenantId` | Tenant identifier for data isolation |
| `X-User-Roles` | `roles` | Comma-separated roles (e.g. `financial-advisor,viewer`) |

### Restrict skills by role

Add `allowed-roles` to SKILL.md frontmatter:

```yaml
metadata:
  allowed-roles:
    - financial-advisor
    - super-admin
```

The `RbacGuardrail` (@Order 5) runs before all other guardrails and blocks users without a matching role. The `super-admin` role bypasses all restrictions.

### Restrict tools by role

Annotate tool methods with `@RequiresRole`:

```java
@AgentTool(description = "Transfers funds between accounts")
@RequiresRole("financial-operator")
public TransferResult transfer(String fromAccount, String toAccount, BigDecimal amount) {
    return accountService.transfer(fromAccount, toAccount, amount);
}
```

### Multi-tenancy data isolation

When `X-Tenant-Id` is set, all storage keys (memory, chat history, audit trail) are automatically prefixed with the tenantId. This provides transparent data isolation between tenants without any changes to skill or tool code.

---

## A2A Protocol (Agent-to-Agent)

Gargantua implements the [A2A protocol](https://google.github.io/A2A/) for agent-to-agent interoperability.

### Agent Card discovery

The Agent Card is served at:
- `GET /.well-known/agent.json` -- A2A standard well-known URI

The card advertises `protocolVersion: "1.0"` and lists all active skills.

### JSON-RPC 2.0 endpoint

`POST /a2a` accepts JSON-RPC 2.0 requests with methods: `message/send`, `tasks/get`, `tasks/cancel`.

### Calling remote A2A agents

Use `HttpA2AClient` to invoke other A2A-compatible agents. The client is auto-configured as a Spring bean — inject it; do not new it up:

```java
import ai.gargantua.autoconfigure.HttpA2AClient;
import ai.gargantua.core.a2a.A2ATask;

@Component
public class MultiAgentTools {

    private final HttpA2AClient a2aClient;

    public MultiAgentTools(HttpA2AClient a2aClient) {
        this.a2aClient = a2aClient;
    }

    @AgentTool(description = "Delegates research to a specialized agent")
    public String research(String query) {
        A2ATask task = a2aClient.sendTask(
            "https://research-agent.example.com",
            query,
            null   // optional skillHint
        );
        return task.artifacts().toString();
    }
}
```

The agent's identity and capabilities are described by the `AgentCard` type.

---

## Audit Trail

Every agent decision is captured as an immutable `AuditEvent` record, providing a complete decision log for compliance (SOC 2, GDPR) and debugging.

### What is captured

Each `AuditEvent` records: input message, routing decision, guardrails applied, tools called, output response, token usage, estimated cost, and duration.

### Storage

- **MongoAuditStore**: production adapter; writes to an append-only `audit_trail` MongoDB collection
- **InMemoryAuditStore**: embedded mode; data lost on restart

### Configuration

```yaml
agent:
  audit:
    enabled: true            # default: true
    retention-days: 365      # default: 365
```

### Querying the audit trail

```bash
# By user (userId is required; limit defaults to 50)
curl "http://localhost:8080/api/admin/audit?userId=user-42&limit=20"

# By tenant
curl "http://localhost:8080/api/admin/audit/tenant?tenantId=acme&limit=50"

# By session (path variable)
curl "http://localhost:8080/api/admin/audit/session/sess_abc123"

# By event ID (path variable)
curl "http://localhost:8080/api/admin/audit/evt_xyz789"

# Count audit events in a time range
curl "http://localhost:8080/api/admin/audit/count"
```

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
| GDPR delete all | `DELETE /api/agent/chat/history/{userId}` | Removes all chat history for a user from MongoDB. **Note:** working memory in Redis and the audit trail are not touched by this endpoint — for a full GDPR erasure, also clear the user's Redis keys and decide on an audit-trail retention policy. |

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
3. On failure, `DefaultOrchestratorEngine` appends a corrective user message (`"Your previous response failed JSON-schema validation: <reason>. Re-emit a valid response..."`) and re-invokes the LLM. The retry loop runs up to `agent.output.validation-retries` times; if the final attempt still fails, a `SchemaValidationException` is surfaced to the client.

```yaml
agent:
  output:
    validation-retries: 2   # number of corrective-prompt retries (default 2)
```

---

## Custom LLM Provider

Gargantua supports any LLM provider via LangChain4j. Built-in: OpenAI, Anthropic, Azure OpenAI, Ollama.

To add a new provider (e.g., Google Gemini):

1. Add `langchain4j-google-ai-gemini` to your POM
2. Create a `@Bean` that returns a `ChatModel`
3. Reference it in your routing rules

See [LLM Configuration — Adding a provider](llm-configuration.md#adding-a-langchain4j-provider-eg-google-gemini-mistral-cohere) for step-by-step instructions.
