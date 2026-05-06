# Cookbook AI — Gargantua Example Agent

A complete cooking-assistant agent that demonstrates **every feature** of the Gargantua framework on a single coherent domain. Use it as a reference implementation when designing your own agent.

> **How this project was created.** This module was scaffolded from `agent-archetype` exactly the way the framework documentation describes, using `mvn archetype:generate -DarchetypeArtifactId=agent-archetype …`. The archetype produced the `pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `.gitignore`, `application.yml` / `application-embedded.yml`, the Spring Boot main class, an empty test, and the `default-skill` SKILL.md. Cookbook-specific tools, skills, flows, the dietary-profile enricher, and the recipe JSON Schema were added on top.

## What it does

Cookbook AI can:
- Generate full recipes with structured (JSON-schema-validated) output
- Look up nutrition data and propose substitutes (vegan, gluten-free, allergy-aware)
- Plan multi-day meals balancing variety and macros
- Manage your pantry inventory (add / remove / list) with human approval before mutations
- Run admin operations (audit, purge) restricted by role
- Set kitchen timers

## Run it

### Prerequisites
- Docker (the only hard requirement for the all-in-one path below)
- Java 21+ and Maven 3.9+ (only if you want to run the agent on the host instead of in a container)
- ~6 GB free disk for the two local LLM models below
- 4 GB+ RAM headroom for Ollama (`llama3.2:3b` runs comfortably on a laptop CPU)

### Local models

| Role | Model | Size | Why |
|------|-------|------|-----|
| **Primary + routing** | `llama3.2:3b` | ~2 GB | Best tool-calling support under 7B parameters. Emits proper OpenAI-spec `tool_calls` on Ollama. |
| **Fallback** | `qwen2.5:3b` | ~1.9 GB | Different model family — auto-failover via Resilience4j circuit breaker if the primary times out or errors. |

Both models are pulled into the local Ollama on first `docker compose up` (see `ollama-init` service). **No cloud accounts, no API keys, no spend.**

> **Performance note.** On a laptop CPU expect 5–25 s per turn for short responses and possibly more for long generations (e.g. multi-day meal plans). For faster responses, switch to a 7B model (`qwen2.5:7b`, `llama3.1:8b`) or run Ollama on a GPU host — only the `LLM_PRIMARY_MODEL` value in `.env` and `docker-compose.yml` needs to change.

### A. Everything in containers (`docker compose up`)

```bash
cd agent-example-cookbook
docker compose up -d
```

Brings up **mongo + redis + ollama + the agent**. The `ollama-init` job auto-pulls both models on first start and the agent waits for them before booting (~2 min cold-start the first time, instant on subsequent runs). Then:

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-002" -H "X-Session-Id: s1" \
  -d '{"message": "Suggest a quick vegan pasta recipe for 2 people"}'
```

Logs / stop / nuke:

```bash
docker compose logs -f app
docker compose down            # stop, keep data volumes (mongo/redis/ollama-data)
docker compose down -v         # stop + delete all data, including pulled models
```

### B. Infrastructure in Docker, agent on the host

```bash
cd agent-example-cookbook
docker compose up -d ollama mongo redis            # ollama auto-pulls both models
mvn -pl agent-example-cookbook spring-boot:run     # from the monorepo root
```

The `.env` in this directory points `LLM_*_ENDPOINT` at the docker-compose service names by default — when running `mvn` on the host, replace those with `http://localhost:11434` (see the comment block at the top of `.env`).

### C. Embedded mode (no Docker at all — needs an external Ollama already running)

```bash
SPRING_PROFILES_ACTIVE=embedded mvn -pl agent-example-cookbook spring-boot:run
```

All storage runs in-memory (no MongoDB / Redis). Still needs Ollama on `localhost:11434` with both models pulled:

```bash
ollama pull llama3.2:3b
ollama pull qwen2.5:3b
```

## Endpoints

Once the agent is running, these are the URLs you'll typically open:

| What | URL | Notes |
|------|-----|-------|
| **Chat web UI** | <http://localhost:8080/chat> | Built-in Telegram-style chat with SSE streaming |
| **Swagger UI** | <http://localhost:8080/swagger-ui> | Interactive REST API explorer |
| **OpenAPI JSON** | <http://localhost:8080/v3/api-docs> | Raw OpenAPI 3.1 spec — feed to Postman, Bruno, etc. |
| **Agent Card (A2A)** | <http://localhost:8080/.well-known/agent.json> | Discovery doc per the A2A protocol |
| **Health** | <http://localhost:8080/actuator/health> | Liveness/readiness, exposes Mongo + Redis component health |
| **Resolved properties** | <http://localhost:8080/actuator/env/spring.mongodb.uri> | Useful when diagnosing config-binding issues; also `/actuator/configprops` |
| **Metrics** | <http://localhost:8080/actuator/metrics> | Micrometer metrics with GenAI semantic conventions |
| **Prometheus scrape** | <http://localhost:8080/actuator/prometheus> | If you wire up a Prometheus instance |

The MCP gateway (`/mcp`) is **off by default** — set `MCP_ENABLED=true` to expose the agent over the Model Context Protocol for Claude Desktop / Cursor / VS Code clients. See [`docs/extending.md`](../docs/extending.md) in the framework root.

## Framework features demonstrated

### Skills (6)

| Skill | Domain | Demonstrates |
|-------|--------|--------------|
| **default-skill** | general | `memory-layers: [working]` opt-out — skips episodic + knowledge fetches for fast greetings |
| **recipe-skill** | cooking | Structured output via `assets/recipe-schema.json`, multi-tool orchestration |
| **ingredient-skill** | cooking | RAG knowledge-base (`culinary-techniques`), low temperature for factual replies |
| **mealplan-skill** | cooking | Multi-tool composition for a multi-day plan |
| **pantry-skill** | cooking | HITL approvals on mutating actions |
| **admin-skill** | admin | RBAC restricted to `cookbook-admin` / `super-admin` roles |

### Tools (5 classes, 10 methods)

| Tool | Method | Annotations |
|------|--------|-------------|
| **RecipeTool** | `generateRecipe` | `@AgentTool` + `@CacheableToolResult(USER, 30 min)` |
| | `searchRecipes` | `@AgentTool(parallelizable)` + `@ToolRetry(3)` + `@CacheableToolResult(GLOBAL, 1 h)` |
| **IngredientTool** | `getNutrition` | `@AgentTool` + `@CacheableToolResult(GLOBAL, 24 h)` |
| | `findSubstitute` | `@AgentTool` + `@ToolRetry(2)` |
| **PantryTool** | `getPantry` | `@AgentTool` |
| | `addToPantry` | `@AgentTool` + `@RequiresApproval` (HITL) |
| | `removeFromPantry` | `@AgentTool` + `@RequiresApproval(dangerous=true)` |
| **TimerTool** | `setTimer` | `@AgentTool` (the simplest useful tool shape) |
| **AdminTool** | `viewAuditLog` | `@AgentTool` + `@RequiresRole({"cookbook-admin","super-admin"})` |
| | `purgeUserData` | `@AgentTool` + `@RequiresRole(...)` + `@RequiresApproval(dangerous=true)` |

### Context Enricher

**`DietaryProfileEnricher`** — injects each user's diet, allergies and cuisine preferences (under the `user_dietary_profile` section) into the system prompt before every LLM call. The LLM sees the constraints automatically, no need for the user to repeat them.

### Flows (`@AgentsFlow`)

| Flow | Pattern | Description |
|------|---------|-------------|
| `full-meal-plan` | sequential | ingredient research → recipe → pantry shopping list |
| `iterative-recipe` | sequential + loop | initial recipe, then up to 3 refinement passes |
| `parallel-recipe-scout` | parallel + sequential | search recipes & nutrition in parallel, then merge into a single recommendation |

Exposed at `POST /api/flows/{flowName}/start`.

### Memory layers (per-skill opt-out)

`default-skill` declares `memory-layers: [working]` in its frontmatter so the orchestrator skips episodic and knowledge layers when routing greetings — no Redis/MongoDB hits beyond the working session. All other skills use the default (all three layers).

### LLM routing rules

| Rule | Priority | Condition | Target model |
|------|----------|-----------|--------------|
| `cooking-domain-best-model` | 10 | `domain == cooking` | `primary` (e.g. gpt-4o) |
| `admin-domain-best-model` | 15 | `domain == admin` | `primary` |
| `general-domain-cheaper-model` | 20 | `domain == general` | `fallback` (e.g. gpt-4o-mini) |

### MCP server

Set `MCP_ENABLED=true` to expose the agent via the Model Context Protocol at `/mcp`. The single gateway tool is `cookbook-chat`. The capabilities resource at `agent://capabilities` reflects the live skill and tool registries.

### Other features

| Feature | Status |
|---------|--------|
| Audit trail | Enabled — every decision logged |
| Cost tracking | Enabled — per-request token + USD breakdown |
| Dry-run mode | Enabled (only in `dev` / `test` profiles) |
| Embedded mode | Supported (`SPRING_PROFILES_ACTIVE=embedded`) |
| GraalVM native | Supported via the `native` Maven profile (provided by the archetype) |

## Project structure

```
agent-example-cookbook/
├── .env.example                            from archetype — env-var reference
├── .gitignore                              from archetype
├── Dockerfile                              from archetype — multi-stage Maven + JRE
├── docker-compose.yml                      from archetype — app + mongo + redis + ollama
├── pom.xml                                 from archetype, patched to use local ai.gargantua coords
├── src/main/java/ai/gargantua/example/cookbook/
│   ├── CookbookApplication.java            from archetype — @SpringBootApplication
│   ├── enrichers/
│   │   └── DietaryProfileEnricher.java     ContextEnricher — user diet profile
│   ├── flows/
│   │   └── CookbookFlows.java              @AgentsFlow pipelines
│   └── tools/
│       ├── RecipeTool.java                 @CacheableToolResult + @ToolRetry
│       ├── IngredientTool.java             @CacheableToolResult + @ToolRetry
│       ├── PantryTool.java                 @RequiresApproval (HITL)
│       ├── TimerTool.java                  Plain @AgentTool
│       └── AdminTool.java                  @RequiresRole + @RequiresApproval(dangerous)
├── src/main/resources/
│   ├── application.yml                     from archetype + cookbook routing rules + MCP block
│   ├── application-embedded.yml            from archetype — embedded mode overrides
│   └── skills/
│       ├── default-skill/SKILL.md          memory-layers: [working] (opt-out demo)
│       ├── recipe-skill/
│       │   ├── SKILL.md                    structured output
│       │   └── assets/recipe-schema.json   JSON Schema for Recipe
│       ├── ingredient-skill/SKILL.md       RAG knowledge-base
│       ├── mealplan-skill/SKILL.md         multi-step planning
│       ├── pantry-skill/SKILL.md           HITL via @RequiresApproval
│       └── admin-skill/SKILL.md            RBAC via allowed-roles
└── src/test/java/ai/gargantua/example/cookbook/
    └── CookbookApplicationTest.java        context load + per-tool unit tests
```
