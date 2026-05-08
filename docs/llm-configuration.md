# LLM Configuration & Routing Rules

Gargantua supports multiple LLM endpoints simultaneously via the OpenAI chat completions API protocol. You can use one endpoint for conversations, another as failover, a local model for routing, and define rules that dynamically select the best model based on context (domain, user tier, input length, time of day). Any API that speaks the OpenAI protocol works: OpenAI, Azure OpenAI, Ollama, LiteLLM, Bifrost gateways, and similar proxies.

## Supported LLM Providers

Gargantua uses [LangChain4j](https://docs.langchain4j.dev/) for LLM API calls. The following providers work out of the box:

| Provider | Config value | Protocol | Notes |
|----------|-------------|----------|-------|
| **OpenAI** | `openai` | OpenAI API | GPT-4o, GPT-4o-mini, etc. |
| **Anthropic** | `anthropic` | Anthropic Messages API | Claude Sonnet, Haiku, Opus |
| **Azure OpenAI** | `azure-openai` | OpenAI-compatible | Set endpoint to your Azure resource URL |
| **Ollama** | `ollama` | OpenAI-compatible | Local models. Default for routing. |
| **LiteLLM** | `openai` | OpenAI-compatible | Set endpoint to your LiteLLM proxy |
| **vLLM** | `openai` | OpenAI-compatible | Set endpoint to your vLLM server |
| **Any OpenAI-compatible** | `openai` | OpenAI-compatible | Any server that speaks the `/v1/chat/completions` protocol |

> **Key insight:** Any server that implements the OpenAI chat completions API works with `provider: openai` — just set the `endpoint` to your server URL. This includes LiteLLM, vLLM, LocalAI, text-generation-inference, and many others.

### Adding a LangChain4j provider (e.g., Google Gemini, Mistral, Cohere)

`LlmProviderFactory.buildModel` consults the Spring `ApplicationContext` for a `ChatModel` bean named after the alias (preferred) or the provider name. If a user-defined bean is found it is used directly; otherwise the built-in switch (`openai`-compatible / `anthropic`) is applied. The same lookup runs for `StreamingChatModel`, where the bean name is suffixed with `Streaming` (e.g. `gemini` → `geminiStreaming`).

LangChain4j supports 20+ providers via dedicated modules. To add one:

**Step 1 — Add the LangChain4j module to your POM:**

```xml
<!-- Example: Google Gemini -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
</dependency>

<!-- Example: Mistral -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mistral-ai</artifactId>
</dependency>
```

**Step 2 — Register a custom `ChatModel` bean:**

Create a `@Configuration` class in your project that provides a named `ChatModel`:

```java
@Configuration
public class GeminiConfig {

    @Bean("gemini")
    public ChatModel geminiModel(
            @Value("${LLM_GEMINI_API_KEY}") String apiKey,
            @Value("${LLM_GEMINI_MODEL:gemini-2.0-flash}") String model) {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)
            .build();
    }
}
```

**Step 3 — Reference it in your config:**

```yaml
agent:
  llm:
    models:
      gemini:
        provider: gemini
        model: gemini-2.0-flash
    routing-rules:
      - name: use-gemini-for-creative
        priority: 10
        condition:
          domain:
            operator: EQ
            value: creative
        target-model: gemini
```

**Available LangChain4j provider modules:**

| Provider | Artifact |
|----------|----------|
| Google Gemini | `langchain4j-google-ai-gemini` |
| Mistral AI | `langchain4j-mistral-ai` |
| Cohere | `langchain4j-cohere` |
| AWS Bedrock | `langchain4j-bedrock` |
| Google Vertex AI | `langchain4j-vertex-ai` |
| Hugging Face | `langchain4j-hugging-face` |
| Groq | `langchain4j-open-ai` (OpenAI-compatible, set endpoint) |
| Together AI | `langchain4j-open-ai` (OpenAI-compatible, set endpoint) |
| Fireworks | `langchain4j-open-ai` (OpenAI-compatible, set endpoint) |
| Deepseek | `langchain4j-open-ai` (OpenAI-compatible, set endpoint) |

> Full list: [LangChain4j Integrations](https://docs.langchain4j.dev/category/integrations)

---

## Simple Setup — One Provider

The minimal configuration. Set the API key and you're done:

```bash
export LLM_PRIMARY_API_KEY=sk-...
```

The default `application.yml` uses environment variables with sensible defaults:

```yaml
agent:
  llm:
    primary:
      provider: ${LLM_PRIMARY_PROVIDER:openai}       # openai | azure-openai | ollama | any OpenAI-compatible endpoint
      model: ${LLM_PRIMARY_MODEL:gpt-4o}
      api-key: ${LLM_PRIMARY_API_KEY:}
      endpoint: ${LLM_PRIMARY_ENDPOINT:}               # Required only for azure-openai
      temperature: ${LLM_PRIMARY_TEMPERATURE:0.7}
      max-tokens: ${LLM_PRIMARY_MAX_TOKENS:1000}
    fallback:
      provider: ${LLM_FALLBACK_PROVIDER:anthropic}
      model: ${LLM_FALLBACK_MODEL:claude-sonnet-4-20250514}
      api-key: ${LLM_FALLBACK_API_KEY:}
    routing-model:
      provider: ${LLM_ROUTING_PROVIDER:ollama}
      model: ${LLM_ROUTING_MODEL:phi4-mini}
      endpoint: ${LLM_ROUTING_ENDPOINT:http://localhost:11434}
      temperature: 0.0
      max-tokens: 50
```

### What each role does

| Role | What it's used for | When it's called | Default | Cost |
|------|--------------------|------------------|---------|------|
| **Primary** | Agent conversations — the LLM that answers the user | Every chat request | `openai` / `gpt-4o` | Per-token API cost |
| **Fallback** | Automatic failover when primary fails (timeout, HTTP 5xx, rate limit) | Only on primary failure | `anthropic` / `claude-sonnet` | Per-token API cost |
| **Routing** | Skill routing, session summaries, topic scope guardrail | Multiple times per request (internally) | `ollama` / `phi4-mini` | **Free** (local) |

> **Why Ollama for routing?** The routing model is called frequently (every request for skill selection, periodically for session summaries). Using a local model eliminates API costs for these internal operations. The `phi4-mini` model is small (~2GB) and fast enough for classification tasks.

### How failover works

```
Request → Primary LLM
            │
            ├── Success → use response
            │
            └── Failure (timeout / 5xx / rate limit)
                    │
                    ▼
              Resilience4j Circuit Breaker
                    │
                    ▼
              Fallback LLM → use response
```

The circuit breaker tracks failures. After repeated failures, it **opens** and routes directly to fallback without waiting for primary to timeout. It periodically retries primary to check if it's recovered.

### Rate limiting

Each LLM provider is rate-limited via Resilience4j to prevent API quota exhaustion. The default limit is **60 requests per 60 seconds per provider** and is configurable.

```yaml
agent:
  llm:
    rate-limit:
      max-requests: 60          # default 60
      window-seconds: 60        # default 60
```

Each alias (`primary`, `fallback`, `routing`, plus any custom alias under `agent.llm.models.*`) gets its own `RateLimiter` instance built from these values.

Rate limiting works in conjunction with the circuit breaker: if the provider returns a `429 Too Many Requests` response, the circuit breaker counts it as a failure, accelerating the switch to fallback.

---

## Advanced Setup — Model Catalog + Routing Rules

For organizations with multiple providers, different models for different use cases, or A/B testing needs, Gargantua supports a **model catalog** with **rule-based routing**. The catalog (`agent.llm.models.*`), aliases (`agent.llm.primary-alias` / `fallback-alias`), routing rules (`agent.llm.routing-rules`) and skill-level `preferred-model` overrides are all evaluated by `LlmRouter` on every request, with the live trace exposed via `POST /api/admin/llm/simulate`.

### Step 1: Define the model catalog

Each entry is a named model configuration. The name (e.g. `gpt-4o`, `claude-sonnet`) is used as an alias in routing rules.

```yaml
agent:
  llm:
    models:
      # High-capability model for complex tasks
      gpt-4o:
        provider: openai
        model: gpt-4o
        api-key: ${LLM_OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # Cost-effective model for simple tasks
      gpt-4o-mini:
        provider: openai
        model: gpt-4o-mini
        api-key: ${LLM_OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # High-capability alternative provider
      claude-sonnet:
        provider: anthropic
        model: claude-sonnet-4-20250514
        api-key: ${LLM_ANTHROPIC_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # Budget model for free-tier users
      claude-haiku:
        provider: anthropic
        model: claude-haiku-4-5-20251001
        api-key: ${LLM_ANTHROPIC_API_KEY}
        temperature: 0.7
        max-tokens: 500

      # Large context model for long documents
      gpt-4o-large:
        provider: openai
        model: gpt-4o
        api-key: ${LLM_OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 4000    # Higher token limit for this alias

    # Default model when no routing rule matches (alias from the catalog above)
    primary-alias: gpt-4o

    # Failover model when the selected model fails
    fallback-alias: claude-sonnet

    # Local model for internal operations (skill routing, summaries)
    routing-model:
      provider: ollama
      model: phi4-mini
      endpoint: http://localhost:11434
      temperature: 0.0
```

### Step 2: Define routing rules

Rules are evaluated **in order of priority** (lowest number first). The first rule that matches determines which model handles the request. If no rule matches, the `primary` model is used.

```yaml
    routing-rules:

      # Rule 1: High-stakes domains get the most capable model
      - name: high-stakes-domains
        priority: 10
        description: "Medical and legal queries require the most capable model"
        condition:
          domain:
            operator: IN
            values: [medical, legal]
        target-model: claude-sonnet

      # Rule 2: Free-tier users get the budget model
      - name: free-tier-users
        priority: 20
        description: "Cost optimization for free-tier users"
        condition:
          user-tier:
            operator: EQ
            value: free
        target-model: gpt-4o-mini

      # Rule 3: Long inputs need a large context window
      - name: long-context
        priority: 30
        description: "Inputs over 1500 chars need more context window"
        condition:
          input-length:
            operator: GT
            value: 1500
        target-model: gpt-4o-large

      # Rule 4: Off-peak hours, upgrade premium users to best model
      - name: premium-off-peak
        priority: 40
        description: "Premium users get top model during off-peak hours"
        condition:
          AND:
            - time-window:
                from: "22:00"
                to: "06:00"
            - user-tier:
                operator: EQ
                value: premium
        target-model: claude-sonnet

      # Rule 5: A/B test — send 10% of traffic to a new model
      - name: ab-test-haiku
        priority: 50
        description: "A/B test: 10% traffic on Claude Haiku"
        enabled: false    # Activate via admin API when ready
        condition:
          random-sampling:
            percentage: 10
        target-model: claude-haiku
```

### How rules are evaluated

```
Incoming request
    │
    ▼
Rule "high-stakes-domains" (priority 10)
    │ domain IN [medical, legal]?
    ├── YES → use claude-sonnet ─────────────────────────▶ Done
    └── NO  ↓
Rule "free-tier-users" (priority 20)
    │ user-tier == free?
    ├── YES → use gpt-4o-mini ──────────────────────────▶ Done
    └── NO  ↓
Rule "long-context" (priority 30)
    │ input-length > 1500?
    ├── YES → use gpt-4o-large ─────────────────────────▶ Done
    └── NO  ↓
  ... more rules ...
    │
    └── No rule matched → use primary (gpt-4o) ─────────▶ Done
                │
                └── If fails → Resilience4j → fallback (claude-sonnet)
```

---

## Available Rule Conditions

`RoutingRuleEvaluator` (in `agent-engine`) runs every entry in a rule's `condition:` map as an implicit `AND`. Each condition has an operator and a value. Conditions can also be combined explicitly with `AND` / `OR`. Unknown keys fall back to attribute-equality so user-defined headers keep working.

| Condition | Operators | Example | What it checks |
|-----------|-----------|---------|----------------|
| `domain` | `EQ`, `IN`, `NOT_IN` | `domain: { operator: IN, values: [medical, legal] }` | The `metadata.domain` field from the activated SKILL.md |
| `skill` | `EQ`, `IN`, `NOT_IN` | `skill: { operator: EQ, value: experimental-skill }` | The skill name selected by routing |
| `user-tier` | `EQ`, `IN`, `NOT_IN` | `user-tier: { operator: EQ, value: free }` | Custom attribute from `X-Context-User-Tier` header |
| `input-length` | `GT`, `LT`, `GTE`, `LTE` | `input-length: { operator: GT, value: 1500 }` | Length of user message in characters |
| `estimated-tokens` | `GT`, `LT`, `GTE`, `LTE` | `estimated-tokens: { operator: GT, value: 2000 }` | Estimated token count (chars / 4) |
| `time-window` | — | `time-window: { from: "22:00", to: "06:00" }` | Server local time (for off-peak routing) |
| `day-of-week` | — | `day-of-week: { days: [SAT, SUN] }` | Day of the week |
| `attribute-match` | `EQ`, `CONTAINS`, `REGEX` | `attribute-match: { key: priority, operator: EQ, value: high }` | Custom attributes from `X-Context-*` headers |
| `random-sampling` | — | `random-sampling: { percentage: 10 }` | Random % of traffic (for A/B testing) |
| `input-contains` | — | `input-contains: { patterns: ["urgent", "emergency"] }` | Keywords in the user message |

### Combining conditions

Use `AND` or `OR` to combine multiple conditions:

```yaml
condition:
  AND:
    - time-window:
        from: "22:00"
        to: "06:00"
    - user-tier:
        operator: EQ
        value: premium
```

---

## Skill-Level Override

If a specific skill always needs a particular model (e.g. a skill that requires advanced reasoning), you can override the routing rules directly in the SKILL.md frontmatter:

```markdown
---
name: complex-analysis-skill
description: Deep multi-step financial analysis.
version: 1.0.0
allowed-tools:
  - getTransactions
  - getPortfolio
metadata:
  active: true
  domain: financial
  preferred-model: claude-sonnet    # ← Always uses this model, bypasses all rules
---
```

This takes the **highest priority** — it overrides all routing rules.

---

## Complete Resolution Order

When a request comes in, the model is selected in this order:

```
1. Skill preferred-model (SKILL.md frontmatter)    ← Highest priority
   │  If set → use that model, skip rules
   ▼
2. Routing rules (evaluated by priority)
   │  First matching rule → use target-model
   ▼
3. Primary model (default from config)
   │  No rule matched → use primary
   ▼
4. Resilience4j circuit breaker → fallback model    ← Safety net
   │  Primary failed → automatic switch to fallback
```

The **routing model** (Ollama / phi4-mini) is separate from this chain — it's only used for internal operations (skill routing, session summaries), never for user-facing conversations.

---

## Admin Endpoints

Manage routing rules at runtime without restarting the agent:

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/llm/rules` | List all rules with enabled status and match counts |
| `POST` | `/api/admin/llm/rules/{name}/toggle` | Enable or disable a rule at runtime (e.g. activate an A/B test) |
| `POST` | `/api/admin/llm/simulate` | **Simulate** which model would be selected for a given context — without making an LLM call |

### Simulate endpoint

Test your rule configuration before applying it. The endpoint runs the live evaluator (no LLM call) and returns the alias the router would pick.

```bash
curl -X POST http://localhost:8080/api/admin/llm/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is my portfolio performance?",
    "skillName": "financial-skill",
    "skillDomain": "financial",
    "userId": "user-123",
    "userTier": "premium",
    "inputLength": 36,
    "attributes": {"priority": "high"}
  }'
```

Response:
```json
{
  "selectedAlias": "claude-sonnet",
  "selectedModel": "claude-3-5-sonnet-20241022",
  "selectedProvider": "anthropic",
  "matchedRule": "domain-specialization",
  "skillName": "financial-skill",
  "skillDomain": "financial",
  "userTier": "premium",
  "inputLengthChars": 36,
  "estimatedTokens": 9,
  "evaluatedRules": [
    { "name": "domain-specialization", "priority": 10, "enabled": true, "matched": true,  "targetModel": "claude-sonnet" },
    { "name": "cost-optimization",     "priority": 20, "enabled": true, "matched": false, "targetModel": "gpt-4o-mini" },
    { "name": "premium-tier",          "priority": 30, "enabled": true, "matched": true,  "targetModel": "gpt-4o" }
  ]
}
```

The `evaluatedRules` trace is sorted by `priority` (lowest first). The first rule with `matched: true` wins — later matches still appear in the trace for visibility but do not change `selectedAlias`. Optional fields (`skillDomain`, `userTier`, `inputLength`, `attributes`) default to neutral values when omitted, and the legacy 3-field body (`message` / `skillName` / `userId`) keeps working.

---

## Metrics

`LlmRouter` and `LlmProviderFactory` register the following Micrometer meters when a `MeterRegistry` bean is on the classpath (auto-wired by `spring-boot-starter-actuator`). They show up at `/actuator/prometheus` alongside the standard Resilience4j Circuit Breaker / Rate Limiter metrics.

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `agent.llm.routing.rule.matched` | Counter | `rule_name`, `model` | Incremented every time a rule matches (the first match per request also drives `selectedAlias`). |
| `agent.llm.routing.model.selected` | Counter | `model` | Distribution of aliases selected across all routing decisions (including primary fallback). |
| `agent.llm.routing.fallback.used` | Counter | `original_model` | How often the primary alias failed and the call switched to the configured fallback. |
| `agent.llm.model.latency` | Timer | `model`, `skill` | Wall-clock latency per `(model, skill)` pair, recorded around the LLM invocation. |
| `agent.llm.model.error_rate` | Counter | `model` | Errors raised by an alias (used to feed circuit-breaker dashboards). |

Combined with [Cost Tracking](extending.md#cost-tracking), they give full visibility into which models are used, how much they cost, and how they perform.
