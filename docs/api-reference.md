# API Reference

## Chat Endpoints

### POST /api/agent/chat (Sync)
Request:
```
Content-Type: application/json
X-User-Id: {userId}        (required)
X-Session-Id: {sessionId}  (required)

{"message": "What's the weather in Rome?", "forceSkill": "weather-skill"}
```
Response: AgentResponse JSON with: response, sessionId, skillUsed, toolsCalled, totalTokens, durationMs

### POST /api/agent/chat/stream (SSE)
Same request, Accept: text/event-stream. Optional: X-Dry-Run: true

SSE event types:
- `token` — {"token": "The", "index": 0}
- `tool_call` — {"toolName": "getWeather", "status": "started"}
- `tool_result` — {"toolName": "getWeather", "status": "completed", "durationMs": 142}
- `approval_required` — {"requestId": "...", "toolName": "...", "message": "..."}
- `guardrail_warn` — {"guardrailName": "...", "message": "..."}
- `done` — {"skillUsed": "...", "routingMethod": "SEMANTIC", "totalTokens": 87, "durationMs": 1240}
- `error` — {"code": "GUARDRAIL_BLOCKED", "message": "..."}

### POST /api/agent/session/new
Response: {"sessionId": "sess_<uuid>"}

### POST /api/agent/approval/{requestId}
Body: {"decision": "APPROVED", "reason": "optional"}

## Capabilities
### GET /api/capabilities
Returns AgentCapabilities with list of active skills, their tools, domains.

## Chat History
- GET /api/agent/chat/sessions/{userId} — list sessions (paginated)
- GET /api/agent/chat/history/{userId}/{sessionId} — messages (paginated)
- GET /api/agent/chat/history/{userId}/search?q=... — full-text search
- DELETE /api/agent/chat/history/{userId}/{sessionId} — delete session
- DELETE /api/agent/chat/history/{userId} — GDPR delete all

## Export
- GET /api/agent/chat/export/{userId}/{sessionId}?format=json|txt|md

## Admin — Skills
- GET /api/admin/skills
- GET /api/admin/skills/{skillName}
- POST /api/admin/skills/reload

## Admin — Guardrails
- GET /api/admin/guardrails
- POST /api/admin/guardrails/{name}/toggle

## Admin — Evals
- POST /api/admin/evals/run/{skillName}
- POST /api/admin/evals/run (all skills)
- GET /api/admin/evals/reports/{skillName}/latest
- GET /api/admin/evals/reports/{skillName}?limit=10
- GET /api/admin/evals/skills

## Admin — Costs
- GET /api/admin/costs/summary?from=...&to=...
- GET /api/admin/costs/by-skill?from=...&to=...
- GET /api/admin/costs/by-user/{userId}?from=...&to=...
- GET /api/admin/costs/daily?from=...&to=...

## Admin — LLM Routing
- GET /api/admin/llm/rules
- POST /api/admin/llm/rules/{ruleName}/toggle
- POST /api/admin/llm/simulate (body: userId, skillName, userTier, inputLength)

## Admin — Tool Cache
- GET /api/admin/tool-cache/stats
- DELETE /api/admin/tool-cache/{toolName}
- DELETE /api/admin/tool-cache

## Error Responses (RFC 9457)
All errors use Problem Details format:
```json
{
  "type": "https://gargantua.ai/errors/guardrail-blocked",
  "title": "Request blocked by guardrail",
  "status": 400,
  "detail": "Potential prompt injection detected",
  "guardrailName": "prompt-injection"
}
```

| Error Type | Status |
|---|---|
| GuardrailBlocked | 400 |
| SkillNotFound | 404 |
| ApprovalExpired | 410 |
| SchemaValidationFailed | 422 |
| TokenBudgetExceeded | 413 |
| RateLimitExceeded | 429 |

## Interactive Docs
- Swagger UI: http://localhost:8080/swagger-ui
- Redoc: http://localhost:8080/docs
