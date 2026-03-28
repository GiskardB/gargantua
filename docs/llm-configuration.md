# LLM Configuration & Routing Rules

## Multi-Provider Setup
```yaml
agent:
  llm:
    models:
      gpt-4o:
        provider: azure-openai
        model: gpt-4o
        api-key: ${AZURE_OPENAI_KEY}
        endpoint: ${AZURE_OPENAI_ENDPOINT}
      claude-sonnet:
        provider: anthropic
        model: claude-sonnet-4-20250514
        api-key: ${ANTHROPIC_API_KEY}
      gpt-4o-mini:
        provider: openai
        model: gpt-4o-mini
        api-key: ${OPENAI_API_KEY}
    primary: gpt-4o
    fallback: claude-sonnet
    routing-model:
      provider: openai
      model: gpt-4o-mini
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
