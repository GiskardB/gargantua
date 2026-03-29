# LLM Configuration & Routing Rules

## Simple Setup (single provider)

The simplest configuration uses environment variables for a primary provider, an optional fallback, and an optional routing model:

```yaml
agent:
  llm:
    primary:
      provider: ${LLM_PRIMARY_PROVIDER:openai}
      model: ${LLM_PRIMARY_MODEL:gpt-4o}
      api-key: ${LLM_PRIMARY_API_KEY:}
      endpoint: ${LLM_PRIMARY_ENDPOINT:}
      temperature: ${LLM_PRIMARY_TEMPERATURE:0.7}
      max-tokens: ${LLM_PRIMARY_MAX_TOKENS:1000}
    fallback:
      provider: ${LLM_FALLBACK_PROVIDER:anthropic}
      model: ${LLM_FALLBACK_MODEL:claude-sonnet-4-20250514}
      api-key: ${LLM_FALLBACK_API_KEY:}
    routing-model:
      provider: ${LLM_ROUTING_PROVIDER:ollama}
      model: ${LLM_ROUTING_MODEL:phi4-mini}
      api-key: ${LLM_ROUTING_API_KEY:}
      endpoint: ${LLM_ROUTING_ENDPOINT:http://localhost:11434}
      temperature: 0.0
      max-tokens: 50
```

The only **mandatory** value is `LLM_PRIMARY_API_KEY`. Everything else has sensible defaults.

| Role | Purpose | Default |
|------|---------|---------|
| **Primary** | Main model for all agent conversations | `openai` / `gpt-4o` |
| **Fallback** | Auto-failover when primary fails (timeout, 5xx, rate limit). Backed by Resilience4j circuit breaker. | `anthropic` / `claude-sonnet-4-20250514` |
| **Routing** | Cheap/fast model used internally for skill routing, session summaries, eval judge, topic scope guardrail. Runs locally via Ollama by default (zero API cost). | `ollama` / `phi4-mini` |

## Advanced Setup (model catalog + routing rules)

For multi-provider deployments with rule-based model selection, define a catalog of named models and routing rules:

```yaml
agent:
  llm:
    models:
      gpt-4o:
        provider: openai
        model: gpt-4o
        api-key: ${LLM_PRIMARY_API_KEY}
      claude-sonnet:
        provider: anthropic
        model: claude-sonnet-4-20250514
        api-key: ${LLM_FALLBACK_API_KEY}
      gpt-4o-mini:
        provider: openai
        model: gpt-4o-mini
        api-key: ${LLM_PRIMARY_API_KEY}
    primary: gpt-4o
    fallback: claude-sonnet
    routing-model:
      provider: ollama
      model: phi4-mini
      endpoint: http://localhost:11434
      temperature: 0.0
```

## Rule-Based Routing

Rules evaluated by priority (lowest first). First match determines the model.

```yaml
    routing-rules:
      - name: high-stakes-domains
        priority: 10
        condition:
          domain:
            operator: IN
            values: [medical, legal]
        target-model: claude-sonnet

      - name: free-tier
        priority: 20
        condition:
          user-tier:
            operator: EQ
            value: free
        target-model: gpt-4o-mini
```

### Available Conditions
domain, skill, user-tier, input-length (GT/LT), estimated-tokens, time-window (from/to HH:mm), day-of-week, attribute-match, random-sampling (A/B test), input-contains

### Skill-Level Override
```markdown
metadata:
  preferred-model: claude-sonnet
```

### Resolution Order
1. Skill preferred-model (if set)
2. Routing rules (by priority)
3. Primary model (default)
4. Resilience4j failover → fallback model

## Admin Endpoints
- GET /api/admin/llm/rules — list with match counts
- POST /api/admin/llm/rules/{name}/toggle — enable/disable
- POST /api/admin/llm/simulate — test which model would be selected

## Metrics
agent.llm.routing.rule.matched{rule_name, model}, agent.llm.routing.model.selected{model}, agent.llm.model.latency{model, skill}
