# LLM Configuration & Routing Rules

Gargantua supports multiple LLM providers simultaneously. You can use one provider for conversations, another as failover, a local model for routing, and define rules that dynamically select the best model based on context (domain, user tier, input length, time of day).

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
      provider: ${LLM_PRIMARY_PROVIDER:openai}       # openai | azure-openai | anthropic
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
| **Routing** | Skill routing, session summaries, eval judge, topic scope guardrail | Multiple times per request (internally) | `ollama` / `phi4-mini` | **Free** (local) |

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

---

## Advanced Setup — Model Catalog + Routing Rules

For organizations with multiple providers, different models for different use cases, or A/B testing needs, Gargantua supports a **model catalog** with **rule-based routing**.

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
        api-key: ${OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # Cost-effective model for simple tasks
      gpt-4o-mini:
        provider: openai
        model: gpt-4o-mini
        api-key: ${OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # High-capability alternative provider
      claude-sonnet:
        provider: anthropic
        model: claude-sonnet-4-20250514
        api-key: ${ANTHROPIC_API_KEY}
        temperature: 0.7
        max-tokens: 1000

      # Budget model for free-tier users
      claude-haiku:
        provider: anthropic
        model: claude-haiku-4-5-20251001
        api-key: ${ANTHROPIC_API_KEY}
        temperature: 0.7
        max-tokens: 500

      # Large context model for long documents
      gpt-4o-large:
        provider: openai
        model: gpt-4o
        api-key: ${OPENAI_API_KEY}
        temperature: 0.7
        max-tokens: 4000    # Higher token limit for this alias

    # Default model when no routing rule matches
    primary: gpt-4o

    # Failover model when the selected model fails
    fallback: claude-sonnet

    # Local model for internal operations (skill routing, summaries, eval)
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

Each condition has an operator and a value. Conditions can be combined with `AND` / `OR`.

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

The **routing model** (Ollama / phi4-mini) is separate from this chain — it's only used for internal operations (skill routing, session summaries, eval judge), never for user-facing conversations.

---

## Admin Endpoints

Manage routing rules at runtime without restarting the agent:

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/llm/rules` | List all rules with enabled status and match counts |
| `POST` | `/api/admin/llm/rules/{name}/toggle` | Enable or disable a rule at runtime (e.g. activate an A/B test) |
| `POST` | `/api/admin/llm/simulate` | **Simulate** which model would be selected for a given context — without making an LLM call |

### Simulate endpoint

Test your rule configuration before applying it:

```bash
curl -X POST http://localhost:8080/api/admin/llm/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "skillName": "financial-skill",
    "skillDomain": "financial",
    "userTier": "premium",
    "inputLength": 2000
  }'
```

Response:
```json
{
  "selectedModel": "gpt-4o-large",
  "matchedRule": "long-context",
  "evaluatedRules": [
    { "name": "high-stakes-domains", "matched": false },
    { "name": "free-tier-users", "matched": false },
    { "name": "long-context", "matched": true, "targetModel": "gpt-4o-large" }
  ]
}
```

---

## Metrics

Track model usage, costs, and performance across providers:

| Metric | Labels | Description |
|--------|--------|-------------|
| `agent.llm.routing.rule.matched` | `rule_name`, `model` | How many times each rule was triggered |
| `agent.llm.routing.model.selected` | `model` | Distribution of models selected across all requests |
| `agent.llm.routing.fallback.used` | `original_model` | How often Resilience4j failover kicked in |
| `agent.llm.model.latency` | `model`, `skill` | Response latency per model and skill |
| `agent.llm.model.error_rate` | `model` | Error rate per model (for circuit breaker monitoring) |

These metrics feed into Prometheus/Grafana dashboards. Combined with [Cost Tracking](extending.md#cost-tracking), they give you full visibility into which models are used, how much they cost, and how they perform.
