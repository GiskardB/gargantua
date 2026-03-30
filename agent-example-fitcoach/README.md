# FitCoach AI — Gargantua Example Agent

A complete personal fitness and health assistant that demonstrates **every feature** of the Gargantua framework. Use this as a reference implementation when building your own agent.

## What it does

FitCoach AI can:
- Create personalized workout plans with structured JSON output
- Provide nutrition advice and meal plans (backed by RAG document retrieval)
- Calculate BMI and track health metrics (with human approval before saving)
- Fetch latest fitness and sports news
- Manage user profiles (admin-only, role-restricted)

## Run it

```bash
# Embedded mode (no Docker, everything in-memory)
LLM_PRIMARY_PROVIDER=openai \
LLM_PRIMARY_MODEL=gpt-4o \
LLM_PRIMARY_API_KEY=sk-your-key \
LLM_PRIMARY_ENDPOINT=https://api.openai.com/v1 \
SPRING_PROFILES_ACTIVE=embedded \
mvn spring-boot:run

# Full mode (with Docker infrastructure)
docker compose up -d mongo redis ollama
docker compose exec ollama ollama pull phi4-mini
mvn spring-boot:run
```

## Framework Features Demonstrated

### Skills (6)

| Skill | Domain | Special features |
|-------|--------|-----------------|
| **workout-skill** | fitness | Structured output (`assets/schema.json`), eval suite (3 cases) |
| **nutrition-skill** | medical | RAG knowledge-base (`nutrition-docs`), eval suite (2 cases) |
| **health-skill** | medical | HITL (`@RequiresApproval` on recordMetric), RBAC (`allowed-roles`) |
| **news-skill** | general | Low temperature (0.3) for factual reporting |
| **admin-skill** | general | RBAC restricted to `fitness-admin` and `super-admin` roles |
| **default-skill** | general | Fallback for greetings and general conversation |

### Tools (5 classes, 8 methods)

| Tool | Method | Annotations used |
|------|--------|-----------------|
| **WorkoutTool** | `generateWorkout` | `@AgentTool` + `@CacheableToolResult(USER, 10min)` |
| | `searchExercises` | `@AgentTool(parallelizable)` + `@ToolRetry(3)` + `@CacheableToolResult(GLOBAL, 5min)` |
| **NutritionTool** | `createMealPlan` | `@AgentTool` |
| | `lookupFood` | `@AgentTool` + `@CacheableToolResult(GLOBAL, 1h)` |
| **HealthTool** | `calculateBmi` | `@AgentTool` |
| | `recordMetric` | `@AgentTool` + `@RequiresApproval` (HITL — user confirms before saving) |
| **NewsTool** | `fetchNews` | `@AgentTool` + `@ToolRetry(2)` + `@CacheableToolResult(GLOBAL, 15min)` |
| **ProfileTool** | `getProfile` | `@AgentTool` + `@RequiresRole("fitness-admin")` |
| | `deleteProfile` | `@AgentTool` + `@RequiresRole("fitness-admin")` + `@RequiresApproval(dangerous=true)` |

### Context Enricher

**FitnessProfileEnricher** — injects the user's fitness profile (level, goal, restrictions, last workout) into the system prompt before every LLM call. The LLM sees this context automatically without the user having to repeat it.

### RAG (Retrieval-Augmented Generation)

The `nutrition-skill` declares `knowledge-base: nutrition-docs` in its SKILL.md. When activated, the framework searches the vector store for relevant nutritional information and injects it into the prompt before the LLM responds.

### RBAC (Role-Based Access Control)

- **Skill-level**: `admin-skill` and `health-skill` restrict access via `allowed-roles` in SKILL.md
- **Tool-level**: `ProfileTool` methods require `@RequiresRole("fitness-admin")`
- **Tenant isolation**: all data is automatically prefixed by `X-Tenant-Id` header

Test with roles:
```bash
# Regular user — can use workout, nutrition, health, news skills
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-User-Id: user1" -H "X-Session-Id: s1" \
  -H "X-User-Roles: user" \
  -d '{"message": "Create a workout plan for muscle gain"}'

# Admin — can also use admin-skill
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-User-Id: admin1" -H "X-Session-Id: s2" \
  -H "X-User-Roles: fitness-admin" \
  -d '{"message": "Show profile for user user1"}'

# Blocked — user without role tries admin skill
curl -X POST http://localhost:8080/api/agent/chat \
  -H "X-User-Id: user1" -H "X-Session-Id: s3" \
  -H "X-User-Roles: user" \
  -d '{"message": "Delete profile for user user2"}'
# → Blocked by RbacGuardrail
```

### Human-in-the-Loop (HITL)

When the agent calls `recordMetric` or `deleteProfile`, the SSE stream emits an `approval_required` event. The client must call `POST /api/agent/approval/{requestId}` with `APPROVED` or `DENIED` before the tool executes.

### Structured Output (JSON Schema)

The `workout-skill` declares `output-schema: assets/schema.json`. The LLM is instructed to respond in JSON matching the WorkoutPlan schema. If validation fails, the framework retries automatically (up to 2 times).

### Eval Framework

Golden datasets for automated quality testing:

```bash
# Run evals for workout skill
curl -X POST http://localhost:8080/api/admin/evals/run/workout-skill

# Run evals for nutrition skill
curl -X POST http://localhost:8080/api/admin/evals/run/nutrition-skill

# Run all
curl -X POST http://localhost:8080/api/admin/evals/run
```

### LLM Routing Rules

The `application.yml` configures rule-based model selection:

| Rule | Priority | Condition | Model |
|------|----------|-----------|-------|
| medical-domains | 10 | domain IN [medical] | primary (best model) |
| general-domains | 20 | domain EQ general | fallback (cheaper model) |

Skills with `domain: medical` (nutrition, health) automatically get the most capable model. Skills with `domain: general` (news, admin) use the cheaper fallback.

### Guardrails

Configured in `application.yml`:
- **PII masking**: email, phone, IBAN, SSN patterns
- **Prompt injection**: 7 patterns blocked
- **Medical disclaimer**: automatically appended to medical-domain responses
- **Schema validation**: enforced on workout-skill structured output

### Other Features

| Feature | Status | Notes |
|---------|--------|-------|
| Audit trail | Enabled | Every decision logged in `audit_trail` collection |
| Cost tracking | Enabled | Per-request token/cost tracking with provider pricing |
| MCP server | Enabled | Gateway mode on `/mcp`, discoverable by Claude Desktop |
| Dry-run | Enabled | `X-Dry-Run: true` header for testing without side effects |
| Embedded mode | Supported | `SPRING_PROFILES_ACTIVE=embedded` — zero infrastructure |
| GraalVM native | Supported | RuntimeHints registered for all tool records |

## Project Structure

```
agent-example-fitcoach/
├── src/main/java/ai/gargantua/example/
│   ├── ExampleAgentApplication.java          @SpringBootApplication
│   ├── AgentKitRuntimeHints.java             GraalVM reflection hints
│   ├── enrichers/
│   │   └── FitnessProfileEnricher.java       ContextEnricher — user profile
│   └── tools/
│       ├── WorkoutTool.java                  @CacheableToolResult + @ToolRetry
│       ├── NutritionTool.java                @CacheableToolResult
│       ├── HealthTool.java                   @RequiresApproval (HITL)
│       ├── NewsTool.java                     @ToolRetry + @CacheableToolResult
│       └── ProfileTool.java                  @RequiresRole + @RequiresApproval(dangerous)
├── src/main/resources/
│   ├── application.yml                       Full config (LLM, routing rules, guardrails, audit)
│   ├── application-embedded.yml              Embedded mode (no Docker)
│   ├── static/docs/index.html                Redoc API docs
│   └── skills/
│       ├── default-skill/SKILL.md
│       ├── workout-skill/
│       │   ├── SKILL.md                      Structured output
│       │   ├── assets/schema.json            JSON Schema for WorkoutPlan
│       │   └── evals/evals.json              3 eval cases
│       ├── nutrition-skill/
│       │   ├── SKILL.md                      RAG knowledge-base
│       │   └── evals/evals.json              2 eval cases
│       ├── health-skill/SKILL.md             HITL + RBAC
│       ├── news-skill/SKILL.md               Low temperature
│       └── admin-skill/SKILL.md              RBAC restricted
└── src/test/java/
    └── ExampleAgentApplicationTest.java      Context load + tool unit tests
```
