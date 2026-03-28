# Guardrails

## Pipeline Architecture -- Chain of Responsibility

Input guardrails run sequentially before the LLM call. Output guardrails run after. Each guardrail is a Spring `@Component` with `@Order`.

```
Request -> MaxLength -> PromptInjection -> TopicScope -> PiiMasking -> RateLimit -> [custom] -> LLM
LLM -> PiiOutput -> Disclaimer -> ScopeValidator -> SchemaValidator -> Response
```

Input pipeline stops at first BLOCK. Output pipeline runs all (chained transformation).

## Built-in Input Guardrails

| Order | Name | Behavior | Config key |
|-------|------|----------|------------|
| 10 | MaxLengthGuardrail | Blocks input > max chars | `agent.guardrail.input.max-chars` (default: 2000) |
| 20 | PromptInjectionGuardrail | Pattern matching for injection attempts | `agent.guardrail.input.injection-guard.enabled/patterns` |
| 30 | TopicScopeGuardrail | Blocks off-topic via keyword matching | `agent.guardrail.input.topic-scope.enabled/blocked-topics` |
| 40 | PiiInputGuardrail | Masks PII (email, IBAN, phone, fiscal code) | `agent.guardrail.input.pii-masking.enabled/patterns` |
| 50 | RateLimitGuardrail | Sliding window rate limit per userId | `agent.guardrail.input.rate-limit.enabled/requests-per-minute` |

## Built-in Output Guardrails

| Order | Name | Behavior |
|-------|------|----------|
| 10 | PiiOutputGuardrail | Masks/de-anonymizes PII in response |
| 20 | DisclaimerInjectorGuardrail | Appends domain-specific disclaimer |
| 30 | ScopeValidatorGuardrail | Validates response is on-topic (disabled by default) |
| 40 | SchemaValidatorGuardrail | Validates JSON against skill's output-schema |

## Adding a Custom Guardrail

```java
@Component
@Order(60)
public class MyBusinessRuleGuardrail implements InputGuardrail {

    @Override
    public String name() {
        return "my-business-rule";
    }

    @Override
    public boolean isEnabled(Object props) {
        return true;
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (ctx.userMessage().contains("forbidden")) {
            return GuardrailResult.block(name(), "Forbidden content");
        }
        return GuardrailResult.pass(name());
    }
}
```

Zero framework changes -- auto-discovered by Spring.

## Configuration

```yaml
agent:
  guardrail:
    input:
      enabled: true
      block-on-fail: true
      max-chars: 2000
      injection-guard:
        enabled: true
        patterns:
          - "ignore previous instructions"
          - "you are now"
          - "disregard all"
          - "system prompt"
      topic-scope:
        enabled: false
        blocked-topics:
          - "politics"
          - "religion"
      pii-masking:
        enabled: false
        patterns:
          email: "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
          iban: "[A-Z]{2}\\d{2}[A-Z0-9]{11,30}"
          phone: "\\+?\\d{7,15}"
          fiscal-code: "[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]"
      rate-limit:
        enabled: true
        requests-per-minute: 30
    output:
      enabled: true
      block-on-fail: false
      pii-output:
        enabled: false
        action: redact          # redact | mask | de-anonymize
      disclaimer:
        enabled: false
        text: "This response is AI-generated and may contain inaccuracies."
      scope-validator:
        enabled: false
      schema-validator:
        enabled: true
    pii-detection:
      enabled: false
      action: redact
```

## Admin Endpoints

- **GET** `/api/admin/guardrails` -- list the full pipeline with current enabled/disabled status for each guardrail.
- **POST** `/api/admin/guardrails/{name}/toggle` -- enable or disable a specific guardrail at runtime without restarting the application.
