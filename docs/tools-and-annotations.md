# Tools & Annotations

## @AgentTool

The `@AgentTool` annotation marks a method as a tool that can be called by the LLM. Annotated methods are discovered at boot time and registered in the `ToolRegistry`.

```java
@AgentTool(
    description = "Returns current weather for a city",  // REQUIRED
    name = "getWeather",        // optional, default: method name
    parallelizable = true       // optional, default: true
)
public WeatherResult getWeather(String city) { ... }
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `description` | String | -- (required) | What the tool does. This is the text the LLM reads when deciding whether to call the tool. |
| `name` | String | method name | Override the tool name exposed to the LLM. |
| `parallelizable` | boolean | `true` | Whether the LLM may call this tool in parallel with other tools in the same turn. Set to `false` for tools with side effects that must run sequentially. |

### Writing Good Descriptions

The `description` is the single most important factor in whether the LLM calls the right tool. Follow these guidelines:

- **Be specific.** Say what the tool returns, not just what it does. "Returns the current temperature, humidity, and wind speed for a given city" is better than "Gets weather."
- **State limitations.** If the tool only covers certain regions or data sources, say so. "Covers US cities only" prevents hallucinated calls for unsupported locations.
- **Say what it does NOT do.** "Does not return forecast data -- use getWeatherForecast for that" helps the LLM disambiguate between similar tools.
- **Keep it under 200 characters.** The description is injected into every LLM call for the skill, so brevity reduces token usage.

---

## @ToolRetry -- Automatic Retry with Exponential Backoff

Wraps the tool invocation in a retry policy backed by [Resilience4j Retry](https://resilience4j.readme.io/docs/retry). Honored by `ToolRegistry.executeTool` whenever the tool method carries `@ToolRetry`. Use it for tools that call external services prone to transient failures.

```java
@ToolRetry(
    maxAttempts = 3,
    waitDurationMs = 500,
    backoffMultiplier = 2.0,
    maxWaitDurationMs = 5000,
    retryOn = { IOException.class, HttpTimeoutException.class },
    abortOn = { IllegalArgumentException.class }
)
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `maxAttempts` | int | 3 | Total number of attempts including the initial call. |
| `waitDurationMs` | long | 500 | Wait time before the first retry, in milliseconds. |
| `backoffMultiplier` | double | 2.0 | Multiplier applied to the wait duration after each failed attempt. |
| `maxWaitDurationMs` | long | 5000 | Upper bound on the wait duration regardless of backoff. |
| `retryOn` | Class[] | `{ IOException.class }` | Exception types that trigger a retry. |
| `abortOn` | Class[] | `{ IllegalArgumentException.class }` | Exception types that abort immediately without retrying. Takes precedence over `retryOn`. |

### Retry Sequence Example

With the defaults above, the retry timing looks like this:

```
Attempt 1 → fails → wait 500ms
Attempt 2 → fails → wait 1000ms
Attempt 3 → fails → exception propagated to agent
```

### Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `agent.tool.retry.attempts` | Counter | Total retry attempts across all tools. Tagged by `tool` name. |
| `agent.tool.retry.exhausted` | Counter | Number of times all retries were exhausted and the tool failed. Tagged by `tool` name. |

---

## @RequiresApproval -- Human-in-the-Loop

Pauses agent execution and requests human approval before the tool runs. This is essential for tools that perform destructive, costly, or irreversible actions.

```java
@RequiresApproval(
    message = "The agent wants to send an alert",
    showParameters = {"alertType", "message"},
    dangerous = false
)
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `message` | String | `""` | Human-readable description shown in the approval prompt. |
| `showParameters` | String[] | `{}` | Parameter names whose values are included in the approval prompt so the reviewer can see what the tool will do. |
| `dangerous` | boolean | `false` | If `true`, the approval UI renders a warning indicator. Does not change behavior, only presentation. |

### Approval Flow

When the agent attempts to call a tool annotated with `@RequiresApproval`, the following sequence occurs:

1. The agent runtime suspends and persists an approval request keyed by a unique `requestId` (via `ApprovalStore` — Redis in standard mode, in-memory in embedded mode).
   > On the streaming endpoint (`/api/agent/chat/stream`), `ChatStreamController` emits an `approval_required` SSE event before the tool would run, with `requestId`, `tool`, `arguments`, `message`, `dangerous`, `ttlMinutes`. The pending request is also persisted via `ApprovalStore` (when configured) so a reviewer can resolve it via `POST /api/agent/approval/{requestId}`. The synthetic tool result fed back to the LLM is `{"status":"awaiting_approval","requestId":"..."}`.
2. The agent **pauses** execution. No further tool calls or LLM requests are made.
3. The client (web UI, CLI, or external system) presents the approval prompt to a human reviewer.
4. The reviewer calls the approval endpoint:
   ```
   POST /api/agent/approval/{requestId}
   Content-Type: application/json

   {
     "decision": "APPROVED"   // or "DENIED"
   }
   ```
5. If **approved**, the tool executes normally and the agent resumes.
6. If **denied**, the agent receives a denial notification and can inform the user or choose an alternative action.

### Configuration

```yaml
agent:
  hitl:
    enabled: false                 # Master switch for human-in-the-loop (default: false — requires Redis)
    default-ttl-minutes: 5         # How long an approval request stays open
    auto-deny-on-expiry: true      # Automatically deny if TTL expires without a decision
    require-reason-on-deny: false  # If true, denials must include a non-blank `reason`
```

When `auto-deny-on-expiry` is `true` and the TTL elapses, the agent receives the same denial notification as an explicit deny. When `false`, the agent remains paused indefinitely until a decision arrives or the session times out.

---

## @RequiresRole — Role-Based Access Control on tools

Restricts a tool method to callers that have at least one of the listed roles in their `SecurityContext`. `ToolRegistry.executeTool` checks the annotation before any cache lookup or invocation; on denial it returns a `{"error":"Access denied: ..."}` payload that the LLM sees as the tool result, so it can either pick a different tool or apologise. The skill-level `allowed-roles` check in `RbacGuardrail` runs in addition, earlier in the pipeline — `@RequiresRole` complements it for finer per-tool gating.

```java
import ai.gargantua.core.security.RequiresRole;

@AgentTool(description = "Permanently deletes a user profile")
@RequiresRole({"fitness-admin", "super-admin"})
@RequiresApproval(message = "Delete this user?", dangerous = true)
public void deleteProfile(String userId) { ... }
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `value` | `String[]` | — (required) | One or more role names. Caller needs **at least one** match. The reserved role `super-admin` bypasses every per-tool check. |

### How roles arrive at the agent

Roles are read from the `X-User-Roles` HTTP header on the chat endpoints (comma-separated) and propagated into the request `SecurityContext` by the `SecurityContextFilter`. The same context drives the skill-level `allowed-roles` check in `RbacGuardrail`.

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-User-Id: alice" -H "X-Session-Id: s1" \
  -H "X-User-Roles: fitness-admin" \
  -d '{"message": "delete the test profile"}'
```

### Combining with @RequiresApproval

`@RequiresRole` is checked **before** `@RequiresApproval` — so dangerous admin tools typically carry both annotations: the role gate filters out unauthorised callers entirely, and the HITL gate makes authorised admins confirm explicitly before the destructive action runs.

---

## @CacheableToolResult -- Tool Output Caching

Caches tool return values in Redis (prefix `tool-cache:`) to avoid redundant external calls. Activated when `ToolResultCache` is registered (auto-configured if a `StringRedisTemplate` bean is present). Particularly useful for tools that query slow or rate-limited APIs with predictable outputs.

```java
@CacheableToolResult(
    ttlSeconds = 300,
    keyParams = {"city"},           // cache key based on these params only
    scope = CacheScope.GLOBAL       // GLOBAL | USER | SESSION
)
```

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ttlSeconds` | int | 300 | Time-to-live for the cached result, in seconds. |
| `keyParams` | String[] | `{}` (means: all params) | Parameter names to include in the cache key. If omitted, all parameters are used. Specify a subset when some parameters do not affect the result (e.g. a `verbose` flag). |
| `scope` | CacheScope | `GLOBAL` | Determines cache isolation. |

### Cache Scopes

| Scope | Key Includes | Use Case |
|-------|-------------|----------|
| `GLOBAL` | Tool name + key params | Results are the same for every user (e.g. public weather data). |
| `USER` | Tool name + user ID + key params | Results vary by user (e.g. user-specific account data). |
| `SESSION` | Tool name + session ID + key params | Results should not leak between conversations. |

### Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `agent.tool.cache.hits` | Counter | Cache hits. Tagged by `tool` name and `scope`. |
| `agent.tool.cache.misses` | Counter | Cache misses. Tagged by `tool` name and `scope`. |

### Admin Endpoints

These endpoints are available under the admin API for cache management.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/tool-cache/stats` | Returns hit/miss counts and entry counts per tool. |
| `DELETE` | `/api/admin/tool-cache/{toolName}` | Evicts all cached entries for a specific tool. |
| `DELETE` | `/api/admin/tool-cache` | Evicts the entire tool cache. |

---

## Tool Discovery

At application startup, the `ToolRegistry` scans all Spring-managed beans for methods annotated with `@AgentTool`. Each discovered method is registered as an available tool with its name, description, parameter types, and return type.

However, a tool being registered does not mean it is available to every skill. Each skill's `allowed-tools` list in its SKILL.md frontmatter controls which tools the LLM can see when that skill is active. If a tool is not in the list, it is invisible to the LLM for that skill -- it will not appear in the tool definitions sent with the prompt.

This means:
- A single tool class can serve multiple skills.
- Each skill only exposes the tools it needs, reducing confusion and misuse.
- Adding a new `@AgentTool` method does not automatically make it available anywhere. You must add its name to the relevant skill's `allowed-tools`.

---

## Complete Example

The following class demonstrates all four annotations working together on a single tool. `@AgentTool` and `@RequiresApproval` are fully wired today; `@ToolRetry` and `@CacheableToolResult` are still in development (see the planned banners on each section above) — the example showcases the intended API surface.

```java
import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.ToolRetry;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.CacheScope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

@Component
public class WeatherTool {

    @AgentTool(
        name = "getWeather",
        description = "Returns the current temperature, humidity, and wind speed for a given "
                    + "city. Supports worldwide locations. Does not return forecast data — "
                    + "use getWeatherForecast for multi-day forecasts.",
        parallelizable = true
    )
    @ToolRetry(
        maxAttempts = 3,
        waitDurationMs = 500,
        backoffMultiplier = 2.0,
        maxWaitDurationMs = 5000,
        retryOn = { IOException.class, HttpTimeoutException.class },
        abortOn = { IllegalArgumentException.class }
    )
    @CacheableToolResult(
        ttlSeconds = 300,
        keyParams = {"city"},
        scope = CacheScope.GLOBAL
    )
    public WeatherResult getWeather(String city) {
        // Call external weather API
        var response = weatherApiClient.getCurrentWeather(city);

        return new WeatherResult(
            response.city(),
            response.temperatureCelsius(),
            response.humidity(),
            response.windSpeedKmh()
        );
    }

    @AgentTool(
        name = "sendWeatherAlert",
        description = "Sends a weather alert notification to the specified channel. "
                    + "Use only when the user explicitly requests an alert.",
        parallelizable = false
    )
    @RequiresApproval(
        message = "The agent wants to send a weather alert",
        showParameters = {"channel", "alertMessage"},
        dangerous = false
    )
    public AlertResult sendWeatherAlert(String channel, String alertMessage) {
        return alertService.send(channel, alertMessage);
    }
}
```

In this example:

- `getWeather` is parallelizable, retries on transient HTTP errors, and caches results globally for 5 minutes keyed by city.
- `sendWeatherAlert` is not parallelizable (side effect), requires human approval before sending, and is not cached (each alert is unique).
- Both tools must appear in a skill's `allowed-tools` list to be available to the LLM during that skill's activation.
