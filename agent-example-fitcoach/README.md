# FitCoach AI — Gargantua Example Agent

A complete personal fitness and health assistant that demonstrates **every feature** of the Gargantua framework. Use this as a reference implementation when building your own agent.

## What it does

FitCoach AI can:
- Create personalized workout plans with structured JSON output
- Provide nutrition advice and meal plans (backed by RAG document retrieval)
- Calculate BMI and track health metrics (with human approval before saving)
- Fetch latest fitness and sports news
- Manage user profiles (admin-only, role-restricted)

## Architecture

```
                  ┌──────────────┐
  User ──────────>│  FitCoach AI │──── skill routing ────> Ollama (phi4-mini)
                  │  (Spring Boot)│                         localhost:11434
                  └──────┬───────┘
                         │
                   LLM calls (primary/fallback)
                         │
                         v
                  ┌──────────────┐
                  │   Bifrost    │──── Azure OpenAI / OpenAI / ...
                  │ (LLM Gateway)│    (real API keys configured here)
                  └──────────────┘
                    localhost:8090
```

**Bifrost** is the LLM gateway — all primary and fallback LLM calls go through it. You configure the real provider API keys (Azure OpenAI, OpenAI, Anthropic, etc.) inside Bifrost via `.env`. The application itself never holds provider API keys directly.

**Ollama** runs locally for lightweight skill routing (phi4-mini model, auto-pulled on first start).

## Run it

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker

### Option 1: Embedded mode (no database)

Best for quick testing. Uses in-memory stores (no MongoDB/Redis).

```bash
# Start infrastructure (Ollama + Bifrost)
docker compose up -d

# Run the app
start-embedded.bat
```

Or manually:
```bash
docker compose up -d
mvn spring-boot:run -pl agent-example-fitcoach -Dspring-boot.run.profiles=embedded
```

### Option 2: Full mode (with persistence)

Uses MongoDB for data persistence and Redis for caching.

```bash
# Start all infrastructure (Ollama + Bifrost + MongoDB + Redis)
start-infra.bat

# Run the app
start-app.bat
```

Or manually:
```bash
docker compose --profile full up -d
mvn spring-boot:run -pl agent-example-fitcoach
```

### Configuration (.env)

Copy `.env.example` to `.env` and configure:

```env
# Bifrost LLM Gateway — provider keys
AZURE_OPENAI_API_KEY=your-azure-key
AZURE_OPENAI_BASE_URL=https://your-resource.openai.azure.com
AZURE_OPENAI_API_VERSION=2024-02-01
LLM_MODEL=gpt-4o
LLM_ROUTING_MODEL=gpt-4o-mini

# App settings
SERVER_PORT=8080
BIFROST_PORT=8090
LLM_ROUTING_MODEL=phi4-mini
```

### Option 3: Interactive Shell — Embedded mode

Runs the **agent-shell** CLI with the agent engine running in-process (no separate server). Best for quick testing and debugging.

```bash
# Start infrastructure + launch shell
start-shell-embedded.bat
```

Or manually:
```bash
docker compose up -d
mvn spring-boot:run -pl agent-shell -Dspring-boot.run.profiles=embedded -Dspring-boot.run.jvmArguments="-Dagent.shell.mode=embedded"
```

### Option 4: Interactive Shell — Remote mode (Full)

Connects the **agent-shell** CLI to a running FitCoach server via HTTP. The agent runs as a separate process; the shell is a thin client.

```bash
# First, start infra + app in another terminal
start-infra.bat
start-app.bat

# Then start the shell
start-shell-full.bat
```

Or manually:
```bash
docker compose --profile full up -d
mvn spring-boot:run -pl agent-example-fitcoach   # terminal 1 — server
mvn spring-boot:run -pl agent-shell -Dspring-boot.run.jvmArguments="-Dagent.shell.mode=remote -Dagent.shell.remote.url=http://localhost:8080"  # terminal 2 — shell
```

### Shell Commands

Once the shell is running, type `chat` to start an interactive conversation:

```
shell:> chat

  ╔═══════════════════════════════════╗
  ║  FitCoach AI — Interactive Chat   ║
  ╚═══════════════════════════════════╝
  Session: abc-123  User: dev-user  Dry: OFF

agent> Create a workout plan for muscle gain

  [tool: generateWorkout] ...
  Here's your 4-week muscle gain plan:
  ...

  --- Response Meta ---
    Skill:      workout-skill
    Routing:    llm (confidence: 0.95)
    Tokens:     245 in / 512 out
    Duration:   2341ms

agent> \help
  Available commands:
    \exit     - Exit the chat session
    \new      - Start a new session
    \dry      - Toggle dry run mode
    \skill <name> - Force a specific skill (empty to clear)
    \history  - Show message history
    \info     - Show session info
    \clear    - Clear screen
    \help     - Show this help

agent> \skill nutrition-skill
  Skill forced: nutrition-skill

agent> Give me a meal plan for 2000 calories
  [Forced to nutrition-skill, bypassing routing]
  ...

agent> \exit
  Session ended.
```

Other shell commands (outside chat mode):

| Command | Description |
|---------|-------------|
| `skill list` | Show all available skills in table format |
| `skill show <name>` | Show detailed info for a specific skill |
| `skill reload` | Hot-reload all SKILL.md files |
| `session list` | List recent sessions |
| `eval run <skill>` | Run eval suite for a skill |
| `eval run` | Run all eval suites |
| `cost report` | Show token/cost usage report |

### Shell Configuration

In `agent-shell/src/main/resources/application.yml`:

```yaml
agent:
  shell:
    mode: embedded              # "embedded" or "remote"
    user-id: dev-user           # Default user ID for requests
    show-meta: true             # Show skill/routing/tokens after each response
    show-timing: true           # Show request duration
    ansi: true                  # ANSI color support (true/false)
    remote:
      url: http://localhost:8080  # Agent server URL (remote mode only)
      api-key: ""                 # API key if required
      timeout-ms: 30000           # Request timeout
```

### Embedded vs Remote Shell — When to use

| | Embedded Shell | Remote Shell |
|---|---|---|
| **Use when** | Quick testing, debugging tools, skill dev | Testing against running server, team demo |
| **Agent runs** | In-process (same JVM) | Separate server process |
| **Database** | In-memory (no MongoDB/Redis) | Full persistence |
| **Startup** | `start-shell-embedded.bat` | `start-infra.bat` + `start-app.bat` + `start-shell-full.bat` |
| **HITL support** | Yes (inline approval prompts) | Yes (inline approval prompts via SSE) |
| **Hot reload** | `\skill reload` in shell | `\skill reload` calls server API |

### Stop everything

```bash
stop.bat
# or
docker compose --profile full down
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
# -> Blocked by RbacGuardrail
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
| fitness-domains | 15 | domain EQ fitness | primary (best model) |
| general-domains | 20 | domain EQ general | fallback (cheaper model) |

All LLM calls are routed through **Bifrost**, which handles the actual provider keys and load balancing.

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
| Embedded mode | Supported | `SPRING_PROFILES_ACTIVE=embedded` — no MongoDB/Redis |
| GraalVM native | Supported | RuntimeHints registered for all tool records |

## Project Structure

```
agent-example-fitcoach/
├── docker-compose.yml                     Ollama + Bifrost (+ MongoDB/Redis with --profile full)
├── infra/bifrost/
│   ├── config.template.json               Bifrost provider config template
│   └── entrypoint.sh                      Generates runtime config from .env
├── src/main/java/ai/gargantua/example/
│   ├── ExampleAgentApplication.java       @SpringBootApplication
│   ├── AgentKitRuntimeHints.java          GraalVM reflection hints
│   ├── enrichers/
│   │   └── FitnessProfileEnricher.java    ContextEnricher — user profile
│   └── tools/
│       ├── WorkoutTool.java               @CacheableToolResult + @ToolRetry
│       ├── NutritionTool.java             @CacheableToolResult
│       ├── HealthTool.java                @RequiresApproval (HITL)
│       ├── NewsTool.java                  @ToolRetry + @CacheableToolResult
│       └── ProfileTool.java              @RequiresRole + @RequiresApproval(dangerous)
├── src/main/resources/
│   ├── application.yml                    Full config (LLM via Bifrost, routing, guardrails)
│   ├── application-embedded.yml           Embedded mode overrides
│   ├── static/docs/index.html             Redoc API docs
│   └── skills/
│       ├── default-skill/SKILL.md
│       ├── workout-skill/
│       │   ├── SKILL.md                   Structured output
│       │   ├── assets/schema.json         JSON Schema for WorkoutPlan
│       │   └── evals/evals.json           3 eval cases
│       ├── nutrition-skill/
│       │   ├── SKILL.md                   RAG knowledge-base
│       │   └── evals/evals.json           2 eval cases
│       ├── health-skill/SKILL.md          HITL + RBAC
│       ├── news-skill/SKILL.md            Low temperature
│       └── admin-skill/SKILL.md           RBAC restricted
├── start-embedded.bat                     Embedded mode (Ollama + Bifrost only)
├── start-infra.bat                        Start full infra (+ MongoDB + Redis)
├── start-app.bat                          Run app against full infra
├── start-shell-embedded.bat               Interactive shell — embedded agent engine
├── start-shell-full.bat                   Interactive shell — connects to running server
├── stop.bat                               Stop all Docker services
└── src/test/java/
    └── ExampleAgentApplicationTest.java   Context load + tool unit tests
```
