# Extending the Framework

## Add a Custom Guardrail
```java
@Component
@Order(60)
public class ProfanityGuardrail implements InputGuardrail {
    @Override public String name() { return "profanity-filter"; }
    @Override public boolean isEnabled(Object props) { return true; }
    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (containsProfanity(ctx.userMessage())) {
            return GuardrailResult.block(name(), "Profanity detected");
        }
        return GuardrailResult.pass(name());
    }
}
```

## Add a Context Enricher
Inject runtime data into the system prompt without modifying SKILL.md:

```java
@Component
public class UserProfileEnricher implements ContextEnricher {
    @Override public String sectionName() { return "user_context"; }
    @Override public int order() { return 10; }

    @Override
    public String enrich(EnricherContext ctx) {
        UserProfile profile = userRepo.findById(ctx.userId()).orElse(null);
        if (profile == null) return null;
        return "User plan: %s\nLanguage: %s".formatted(profile.plan(), profile.language());
    }
}
```

Enricher output is inserted between SKILL.md body and memory context in the system prompt.

### Target specific skills
```java
@Override public String targetSkill() { return "financial-skill"; }
```

### Custom attributes from HTTP headers
Headers `X-Context-*` are propagated to EnricherContext.attributes:
```
X-Context-Language: it  →  ctx.attributes().get("language") == "it"
```

## Override a Memory Adapter
```java
@Bean
public WorkingMemoryPort workingMemory() {
    return new MyCustomWorkingMemory();  // replaces Redis adapter
}
```

## Agent-as-Tool — Multi-Agent Delegation
```java
@Component
public class AnalysisTools {
    private final AgentAsToolPort pfmAgent;

    @AgentTool(description = "Delegates financial analysis to PFM agent")
    public AgentToolResponse analyzeFinances(String task, String userId) {
        return pfmAgent.invoke(new AgentToolRequest(task, userId, null, Map.of()));
    }
}
```

## Dry-Run Mode
Activate via header `X-Dry-Run: true`. No persistence, no real tool calls, no cost tracking. Response includes execution trace with routing, guardrails, and tool stubs.

Optional tool stubs: `X-Dry-Run-Tool-Stubs: {"getWeather": {"temperature": 20}}`

Disabled in production by default (`agent.dry-run.enabled=false`).

## MCP Server
Expose the agent as an MCP server for Claude Desktop, Cursor, etc:

```yaml
agent:
  mcp:
    enabled: true
    mode: gateway        # gateway | transparent
    transport:
      type: sse
      path: /mcp
```

Claude Desktop config:
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

## Token Budget Manager
Prevents exceeding context window. Non-truncatable: system prompt, user message, tool descriptions. Truncatable (in order): references, knowledge, episodic summaries.

```yaml
agent:
  memory:
    composer:
      max-context-tokens: 3000
```

## Cost Tracking
Tracked per request in MongoDB `token_usage` collection.

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
```

Admin endpoints: /api/admin/costs/summary, /by-skill, /by-user/{id}, /daily

## Chat History & Export
- History: GET /api/agent/chat/history/{userId}/{sessionId}
- Search: GET /api/agent/chat/history/{userId}/search?q=keyword
- Export: GET /api/agent/chat/export/{userId}/{sessionId}?format=md
- GDPR delete: DELETE /api/agent/chat/history/{userId}

## Structured Output
Add JSON Schema in skills/{name}/assets/schema.json and reference it in SKILL.md:
```yaml
metadata:
  output-schema: assets/schema.json
```
SchemaValidatorGuardrail validates and retries (default 2 retries) automatically.
