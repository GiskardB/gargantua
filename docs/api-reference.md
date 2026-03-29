# API Reference

Base URL: `http://localhost:8080` (development) or your deployed host.
All endpoints accept and return JSON (`application/json`) unless stated otherwise.

---

## Headers

All chat endpoints accept the following headers. Headers marked required will produce a `400` error if missing.

| Header | Required | Description |
|--------|----------|-------------|
| `X-User-Id` | Yes | Identifies the user. Propagated from your API gateway or set by the client. |
| `X-Session-Id` | Yes (for chat) | Identifies the conversation session. Generate a UUID or call `POST /api/agent/session/new`. |
| `X-Dry-Run` | No | Set to `true` for dry-run mode (no persistence, no real tool calls). |
| `X-Context-*` | No | Custom context attributes passed to ContextEnrichers. E.g. `X-Context-Language: it`. |
| `X-Force-Skill` | No | Force a specific skill, bypassing routing. E.g. `X-Force-Skill: weather-skill`. |

---

## Chat Endpoints

### POST /api/agent/chat -- Synchronous

Sends a user message to the orchestrator and returns the full agent response in a single JSON payload. Best for server-to-server integrations where streaming is unnecessary.

**Request body**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `message` | string | Yes | The user's message. |

**Example**
```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-42" \
  -H "X-Session-Id: sess_abc123" \
  -d '{"message": "What is the weather in Rome?"}'
```

**Response (200)**
```json
{
  "text": "The current weather in Rome is 22 C and sunny.",
  "sessionId": "sess_abc123",
  "skillUsed": "weather-skill",
  "routingMethod": "SEMANTIC",
  "toolsCalled": ["getWeather"],
  "totalTokens": 87,
  "estimatedCostUsd": 0.0013,
  "durationMs": 1240
}
```

Response fields: `text` (the answer), `sessionId`, `skillUsed` (which skill handled it), `routingMethod` (`SEMANTIC` | `KEYWORD` | `FORCED`), `toolsCalled` (list of tool names), `totalTokens` (prompt + completion), `estimatedCostUsd`, `durationMs` (end-to-end latency).

---

### POST /api/agent/chat/stream -- SSE Streaming

Same request format as the synchronous endpoint, but returns a Server-Sent Events stream. Ideal for chat UIs that need real-time token delivery.

**Example**
```bash
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -H "X-User-Id: user-42" \
  -H "X-Session-Id: sess_abc123" \
  -d '{"message": "What is the weather in Rome?"}'
```

The response is a stream of SSE frames. Each frame has an `event` type and a JSON `data` payload.

#### SSE Event Types

| Event | Description |
|-------|-------------|
| `token` | Real-time token delivery. Concatenate values to build the full answer. |
| `tool_call` | Agent dispatched a tool invocation. Show a "calling tool..." indicator. |
| `tool_result` | Tool execution completed. Agent may continue generating tokens. |
| `approval_required` | HITL pause. Stream pauses until `POST /api/agent/approval/{requestId}` resolves it. |
| `guardrail_warn` | A guardrail modified the output (e.g., PII redacted) but allowed the request. |
| `done` | Final metadata. Always the last event before the stream closes. |
| `error` | Guardrail block, schema failure, or server error. Stream closes after this. |

**Example stream** (abbreviated):
```
event: token
data: {"token": "The", "index": 0}

event: tool_call
data: {"tool": "getWeather", "status": "started"}

event: tool_result
data: {"tool": "getWeather", "status": "completed", "durationMs": 142}

event: token
data: {"token": "The current weather in Rome is 22 C and sunny.", "index": 1}

event: done
data: {"sessionId": "sess_abc123", "skillUsed": "weather-skill", "routingMethod": "SEMANTIC", "totalTokens": 87, "estimatedCostUsd": 0.0013, "durationMs": 1240}
```

An `approval_required` event looks like:
```
event: approval_required
data: {"requestId": "apr_7f3a9c", "toolName": "sendEmail", "message": "Approve sending email to user@example.com?"}
```

An `error` event looks like:
```
event: error
data: {"code": "GUARDRAIL_BLOCKED", "message": "Potential prompt injection detected"}
```

---

### POST /api/agent/session/new

Creates a new session identifier. Call this before starting a conversation if you do not want to generate your own UUID.

```bash
curl -X POST http://localhost:8080/api/agent/session/new
```

**Response (200)**
```json
{
  "sessionId": "sess_e47b2a91-6c0f-4d8e-9a1b-3f5c7d2e8a04"
}
```

---

### POST /api/agent/approval/{requestId}

Resolves a pending human-in-the-loop approval request.

**Flow:**
1. Client sends a chat message via `/api/agent/chat/stream`.
2. The agent decides to call a sensitive tool (e.g., `sendEmail`).
3. The stream emits an `approval_required` event with a `requestId`.
4. A human reviewer calls this endpoint with `APPROVED` or `DENIED`.
5. If approved, the agent executes the tool and the stream resumes. If denied, the agent responds without the tool.
6. Approval requests expire after a configurable timeout (default: 5 minutes). Calling after expiry returns `410 Gone`.

**Request body**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `decision` | string | Yes | `APPROVED` or `DENIED`. |
| `reason` | string | No | Optional reason for audit logging. |

**Example**
```bash
curl -X POST http://localhost:8080/api/agent/approval/apr_7f3a9c \
  -H "Content-Type: application/json" \
  -d '{"decision": "APPROVED", "reason": "User confirmed email recipient"}'
```

**Response (200)**
```json
{
  "requestId": "apr_7f3a9c",
  "decision": "APPROVED",
  "status": "resolved"
}
```

---

## Agent Discovery

### GET /api/capabilities

Returns the agent's registered skills, their tools, domains, and metadata. The response is cached for 60 seconds. Use this for capability discovery -- for example, a UI can call it on load to show available skills.

```bash
curl http://localhost:8080/api/capabilities
```

**Response (200)**
```json
{
  "agentId": "default-agent",
  "displayName": "AI Agent",
  "description": "AI Agent powered by AgentKit",
  "available": true,
  "skills": [
    {
      "name": "weather-skill",
      "description": "Provides current weather and forecasts",
      "domain": "weather",
      "version": "1.0.0",
      "active": true,
      "hasSchema": true,
      "allowedTools": ["getWeather", "getForecast"]
    }
  ],
  "timestamp": "2026-03-29T10:15:30Z"
}
```

---

## Chat History & Export

These endpoints require MongoDB. They are conditionally registered when `MongoTemplate` is available.

### GET /api/agent/chat/sessions/{userId}

Returns a paginated list of sessions for a user, ordered by most recent activity.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Page number (zero-based). |
| `size` | `20` | Number of sessions per page. |

### GET /api/agent/chat/history/{userId}/{sessionId}

Returns paginated messages for a specific session, ordered chronologically.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Page number (zero-based). |
| `size` | `50` | Number of messages per page. |

### GET /api/agent/chat/history/{userId}/search?q={query}

Full-text search across a user's entire chat history.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `q` | (required) | Search query string. |
| `page` | `0` | Page number. |
| `size` | `20` | Results per page. |

### DELETE /api/agent/chat/history/{userId}/{sessionId}

Deletes a specific session and all its messages.
**Response:** `{"status": "deleted", "sessionId": "..."}`

### DELETE /api/agent/chat/history/{userId}

GDPR-compliant deletion of all sessions and messages for a user.
**Response:** `{"status": "deleted", "userId": "..."}`

### GET /api/agent/chat/export/{userId}/{sessionId}

Exports a single session as a downloadable file.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `format` | `json` | Export format: `json`, `txt`, or `md`. |

### GET /api/agent/chat/export/{userId}

Exports all of a user's messages within a date range.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `format` | `json` | Export format. |
| `from` | (none) | Start date (ISO-8601, e.g. `2026-01-01T00:00:00Z`). |
| `to` | (none) | End date (ISO-8601). |

---

## Admin Endpoints

Admin endpoints are grouped by subsystem. In production, protect these with authentication middleware or network-level access control.

### Skills Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/skills` | List all registered skills with their metadata. |
| GET | `/api/admin/skills/{skillName}` | Get details for a single skill. |
| POST | `/api/admin/skills/reload` | Hot-reload skill definitions from disk. No downtime required. |

### Guardrails Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/guardrails` | List all guardrails and their enabled/disabled state. |
| POST | `/api/admin/guardrails/{guardrailName}/toggle` | Toggle a guardrail on or off at runtime. |

### Evals Admin

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/evals/run/{skillName}` | Run the evaluation suite for a specific skill. |
| POST | `/api/admin/evals/run` | Run evaluations for all skills. |
| GET | `/api/admin/evals/reports/{skillName}/latest` | Get the most recent eval report for a skill. |
| GET | `/api/admin/evals/reports/{skillName}?limit=N` | List recent eval reports (default limit: 10). |
| GET | `/api/admin/evals/skills` | List skills that have eval suites configured. |

### Costs Admin

All cost endpoints default to the last 30 days when `from`/`to` are omitted. Dates are ISO-8601.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/costs/summary?from=...&to=...` | Aggregated cost summary by skill and provider. |
| GET | `/api/admin/costs/by-skill?from=...&to=...` | Cost breakdown grouped by skill. |
| GET | `/api/admin/costs/by-user/{userId}?from=...&to=...` | Token usage records for a specific user. |
| GET | `/api/admin/costs/by-provider?from=...&to=...` | Cost breakdown grouped by LLM provider. |
| GET | `/api/admin/costs/daily?from=...&to=...` | Cost breakdown grouped by day. |

### LLM Routing Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/llm/rules` | List all routing rules with name, description, priority, and enabled state. |
| POST | `/api/admin/llm/rules/{ruleName}/toggle` | Toggle a routing rule on or off. |
| POST | `/api/admin/llm/simulate` | Simulate routing for a given context without executing. |

**Simulate request:** `{"message": "What is the weather?", "skillName": "weather-skill", "userId": "user-42"}`

**Simulate response:** `{"selectedModel": "gpt-4o", "selectedProvider": "openai", "matchedRule": "domain-specialization", "confidence": 0.95}`

### Tool Cache Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/tool-cache/stats` | Cache hit/miss statistics per tool. |
| DELETE | `/api/admin/tool-cache/{toolName}` | Invalidate cached results for a specific tool. |
| DELETE | `/api/admin/tool-cache` | Flush the entire tool cache. |

---

## Error Responses (RFC 9457)

All errors use the [Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) format (`application/problem+json`). This provides a consistent, machine-readable error structure across every endpoint.

**Example:**
```json
{
  "type": "https://gargantua.ai/errors/guardrail-blocked",
  "title": "Request blocked by guardrail",
  "status": 400,
  "detail": "Potential prompt injection detected in user message",
  "instance": "/api/agent/chat",
  "guardrailName": "prompt-injection",
  "timestamp": "2026-03-29T14:22:10Z"
}
```

Standard fields: `type` (stable URI for programmatic matching), `title` (human-readable summary), `status` (HTTP code), `detail` (occurrence-specific explanation), `instance` (request path). Additional fields like `guardrailName` may appear depending on the error type.

**Error types:**

| Error Type | HTTP Status | When It Occurs |
|------------|-------------|----------------|
| `guardrail-blocked` | 400 | A guardrail blocked the request (e.g., prompt injection, toxic content). |
| `skill-not-found` | 404 | The requested skill (via `X-Force-Skill` or routing) does not exist. |
| `approval-expired` | 410 | A HITL approval request was resolved after its timeout window. |
| `schema-validation-failed` | 422 | The request body or a tool argument failed JSON Schema validation. |
| `token-budget-exceeded` | 413 | The request would exceed the configured per-request token budget. |
| `rate-limit-exceeded` | 429 | The user or client has exceeded the configured rate limit. Includes a `Retry-After` header. |

---

## Interactive Documentation

Gargantua ships with two built-in documentation UIs, both auto-generated from the OpenAPI spec.

### Swagger UI

**URL:** [http://localhost:8080/swagger-ui](http://localhost:8080/swagger-ui)

Interactive API explorer with a "Try it out" button on every endpoint. Use this during development to test requests directly from your browser without writing curl commands. Supports setting headers and request bodies in the UI.

### Redoc

**URL:** [http://localhost:8080/docs](http://localhost:8080/docs)

Read-only API documentation with a three-panel layout optimized for reading and navigation. Use this as a reference when integrating. Supports deep-linking to individual endpoints and includes request/response schemas.
