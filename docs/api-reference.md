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
| `X-Context-*` | No | Arbitrary per-request context. Headers prefixed `X-Context-` are extracted by both `ChatController` and `ChatStreamController`, the prefix is stripped, the remainder is lowercased and the pair is forwarded into `EnricherContext.attributes()` and the input-guardrail attribute map. Example: `X-Context-Language: it` → `attributes.get("language") == "it"`. |
| `X-Tenant-Id` | No | Tenant identifier for multi-tenancy. Enables automatic data isolation by tenantId prefix on all storage keys. |
| `X-User-Roles` | No | Comma-separated list of user roles (e.g. `financial-advisor,viewer`). Used by RbacGuardrail to enforce `allowed-roles` on skills and `@RequiresRole` on tools. |
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

Response fields: `text` (the answer), `sessionId`, `skillUsed` (which skill handled it), `routingMethod` (`SEMANTIC` | `LLM` | `FORCED`), `toolsCalled` (list of tool names), `totalTokens` (prompt + completion), `estimatedCostUsd`, `durationMs` (end-to-end latency).

---

### POST /api/agent/chat/stream -- SSE Streaming

Same request format as the synchronous endpoint, but returns a Server-Sent Events stream with real token-by-token streaming from the LLM. Ideal for chat UIs that need real-time token delivery. The stream also includes `tool_call` and `tool_result` events when the agent invokes tools, enabling a multi-turn tool calling loop where the LLM can call multiple tools in sequence before producing a final answer.

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
| `done` | Final metadata. Always the last event before the stream closes. |
| `error` | Guardrail block, schema failure, or server error. Stream closes after this. |
| `approval_required` | Emitted before a tool annotated with `@RequiresApproval` would run. Carries `requestId`, `tool`, `arguments`, `message`, `dangerous`, `ttlMinutes`. The pending request is also persisted via `ApprovalStore` (when configured) so a reviewer can resolve it via `POST /api/agent/approval/{requestId}`. The tool result returned to the LLM is `{"status":"awaiting_approval","requestId":"..."}`. |
| `guardrail_warn` | Emitted for every guardrail that returned `WARN` (or `PASS` with a non-blank reason). Carries `phase` (`input` for now), `guardrail`, `reason`. |

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
data: {"sessionId": "sess_abc123", "skillUsed": "weather-skill", "routingMethod": "SEMANTIC", "totalTokens": 87, "estimatedCostUsd": 0.0013, "durationMs": 1240, "toolsCalled": ["getWeather"]}
```

The `done` payload includes: `sessionId`, `skillUsed`, `routingMethod` (`SEMANTIC` | `LLM` | `FORCED`), `totalTokens`, `estimatedCostUsd` (computed by `CostTracker.estimateUsd` from `agent.cost-tracking.pricing`; `0.0` when cost-tracking is disabled), `durationMs`, and `toolsCalled`.

An `error` event carries a structured `code` field whose vocabulary mirrors the `type` URIs used by `AgentKitExceptionHandler` on the synchronous endpoint:

```
event: error
data: {"error": "Potential prompt injection detected", "code": "GUARDRAIL_BLOCKED"}
```

| Code | Cause |
|------|-------|
| `GUARDRAIL_BLOCKED` | Input or output guardrail returned `BLOCK`. |
| `RATE_LIMIT_EXCEEDED` | Per-user rate-limit guardrail tripped. |
| `SKILL_NOT_FOUND` | Routed skill name does not exist in any registry. |
| `TOKEN_BUDGET_EXCEEDED` | Fixed prompt sections already consume more than `agent.memory.composer.max-context-tokens`. |
| `SCHEMA_VALIDATION` | Output failed JSON-schema validation after all `agent.output.validation-retries` retries. |
| `APPROVAL_EXPIRED` | A HITL approval window elapsed without a decision. |
| `DRY_RUN_NOT_ALLOWED` | Dry-run requested but `agent.dry-run.enabled` is `false` for the active profile. |
| `MAX_TOOL_ITERATIONS` | The streaming tool-calling loop hit its 10-iteration cap. |
| `INTERNAL_ERROR` | Anything else (logs carry the original stack). |

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

**Flow:** The streaming endpoint emits an `approval_required` event with a `requestId` when a sensitive tool needs human sign-off. The stream pauses until this endpoint is called. If approved, the tool executes and the stream resumes. If denied, the agent responds without the tool. Requests expire after a configurable timeout (default 5 min); calling after expiry returns `410 Gone`.

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

## A2A Protocol (Agent-to-Agent)

### GET /.well-known/agent.json -- Agent Card

Returns the A2A-standard Agent Card for discovery, served at the well-known URI per the A2A specification.

```bash
curl http://localhost:8080/.well-known/agent.json
```

**Response (200)**
```json
{
  "name": "AI Agent",
  "description": "AI Agent powered by Gargantua",
  "version": "1.0.0",
  "url": "http://localhost:8080",
  "protocolVersion": "1.0",
  "capabilities": { "streaming": false, "pushNotifications": false },
  "defaultInputModes": ["text/plain"],
  "defaultOutputModes": ["text/plain"],
  "skills": [{ "id": "weather-skill", "name": "weather-skill", "description": "..." }],
  "authSchemes": [{ "scheme": "none", "description": "No authentication required" }]
}
```

### POST /a2a -- JSON-RPC 2.0

A2A-standard JSON-RPC 2.0 endpoint. Supports the following methods: `message/send`, `tasks/get`, `tasks/cancel`.

```bash
curl -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "message/send", "params": {"message": {"messageId": "msg1", "role": "user", "parts": [{"kind": "text", "text": "What is the weather in Rome?"}]}}}'
```

**Response (200)**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "id": "task_abc123",
    "kind": "task",
    "status": { "state": "completed", "message": { "messageId": "...", "role": "agent", "parts": [{"kind": "text", "text": "The current weather in Rome is 22 C and sunny."}] } },
    "artifacts": [{ "name": "response", "parts": [{"kind": "text", "text": "The current weather in Rome is 22 C and sunny."}] }]
  }
}
```

Use `HttpA2AClient` to call remote A2A-compatible agents programmatically.

---

## Chat History & Export

These endpoints require MongoDB. They are conditionally registered when `MongoTemplate` is available. All list endpoints support `page` (default `0`) and `size` query parameters for pagination.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/agent/chat/sessions/{userId}` | List sessions for a user, ordered by most recent. Default size: 20. |
| GET | `/api/agent/chat/history/{userId}/{sessionId}` | Get messages for a session, ordered chronologically. Default size: 50. |
| GET | `/api/agent/chat/history/{userId}/search?q={query}` | Full-text search across a user's chat history. Default size: 20. |
| DELETE | `/api/agent/chat/history/{userId}/{sessionId}` | Delete a session and all its messages. |
| DELETE | `/api/agent/chat/history/{userId}` | GDPR-compliant deletion of all sessions and messages for a user. |
| GET | `/api/agent/chat/export/{userId}/{sessionId}?format=json` | Export a session. Formats: `json`, `txt`, `md`. |
| GET | `/api/agent/chat/export/{userId}?format=json&from=...&to=...` | Export all user messages in a date range (ISO-8601). |

---

## Agent Flows

Multi-step skill pipelines. See [Agent DSL](agent-dsl.md) for how to define flows.

### GET /api/flows — List all registered flows

```bash
curl http://localhost:8080/api/flows
```

Response:
```json
[
  {
    "name": "full-fitness-plan",
    "description": "Health assessment → Workout → Nutrition",
    "steps": ["health-skill", "workout-skill", "nutrition-skill"]
  }
]
```

### POST /api/flows/{flowName}/start — Execute a flow

```bash
curl -X POST http://localhost:8080/api/flows/full-fitness-plan/start \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -d '{"input": "I want to get fit, I weigh 80kg and am 175cm tall"}'
```

Response:
```json
{
  "flowName": "full-fitness-plan",
  "finalOutput": "Here is your complete nutrition plan...",
  "stepResults": [
    { "skillName": "health-skill", "input": "...", "output": "BMI is 26.1...", "durationMs": 1200 },
    { "skillName": "workout-skill", "input": "...", "output": "4-week plan...", "durationMs": 2100 },
    { "skillName": "nutrition-skill", "input": "...", "output": "2200 cal/day...", "durationMs": 1800 }
  ],
  "totalDurationMs": 5100
}
```

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

### Audit Admin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/audit?userId=...&limit=50` | Query audit events for a user. `userId` is **required**; `limit` defaults to 50. |
| GET | `/api/admin/audit/tenant?tenantId=...&limit=50` | Query audit events for a tenant. |
| GET | `/api/admin/audit/session/{sessionId}` | Query audit events for a session (path variable). |
| GET | `/api/admin/audit/{eventId}` | Fetch a single audit event by ID (path variable). |
| GET | `/api/admin/audit/count` | Count audit events in a time range. |

Each `AuditEvent` is an immutable record capturing: input, routing decision, guardrails applied, tools called, output, token usage, estimated cost, and duration.

**Example:**
```bash
curl "http://localhost:8080/api/admin/audit?userId=user-42&limit=10"
```

**Config keys:**
- `agent.audit.enabled` -- boolean, default `true`
- `agent.audit.retention-days` -- integer, default `365`

Storage: `MongoAuditStore` writes to an append-only `audit_trail` MongoDB collection. `InMemoryAuditStore` is used in embedded mode.

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
  "type": "https://agentkit.io/errors/guardrail-blocked",
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
| `guardrail-blocked` | 403 | A guardrail blocked the request (e.g., prompt injection, toxic content). |
| `skill-not-found` | 404 | The requested skill (via `X-Force-Skill` or routing) does not exist. |
| `approval-expired` | 410 | A HITL approval request was resolved after its timeout window. |
| `schema-validation` | 400 | The request body or a tool argument failed JSON Schema validation. |
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
