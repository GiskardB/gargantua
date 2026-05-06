# Weather Agent — Gargantua minimal example

The simplest useful Gargantua agent: **one tool class, one skill, real external HTTP integration**. It talks to [Open-Meteo](https://open-meteo.com) — a free public weather API that requires no signup and no API key.

> **How this project was created.** Scaffolded straight from the framework archetype as the docs describe:
> ```bash
> mvn archetype:generate \
>   -DarchetypeGroupId=ai.gargantua \
>   -DarchetypeArtifactId=agent-archetype \
>   -DarchetypeVersion=1.0.0 \
>   -DgroupId=ai.gargantua.example \
>   -DartifactId=agent-example-weather \
>   -DagentName=Weather \
>   -Dpackage=ai.gargantua.example.weather \
>   -DinteractiveMode=false
> ```
> Then the sample tool / sample skill produced by the archetype were replaced by a single `WeatherTool` and `weather-skill/SKILL.md`. Everything else (`pom.xml`, `Dockerfile`, `docker-compose.yml`, `.env.example`, `.gitignore`, `application.yml`, `application-embedded.yml`, the Spring Boot main class, the empty `default-skill` SKILL.md, the context-load test) came verbatim from the archetype.

## What's in here

```
agent-example-weather/
├── pom.xml                                      from archetype
├── Dockerfile                                   from archetype
├── docker-compose.yml                           from archetype — mongo + redis + ollama + app
├── .env.example                                 from archetype
├── src/main/java/.../WeatherApplication.java    from archetype — @SpringBootApplication
├── src/main/java/.../tools/WeatherTool.java     ← the only custom Java code
├── src/main/resources/
│   ├── application.yml                          from archetype
│   ├── application-embedded.yml                 from archetype
│   └── skills/
│       ├── default-skill/SKILL.md               minor edits (memory-layers opt-out)
│       └── weather-skill/SKILL.md               ← the only custom skill
└── src/test/java/.../WeatherApplicationTest.java  context-load + tool bean check
```

## The tool

`WeatherTool` exposes two `@AgentTool` methods backed by [Open-Meteo](https://open-meteo.com):

| Method | What it does | Annotations |
|--------|-------------|-------------|
| `getCurrentWeather(city)` | Geocodes the city, then fetches current temperature, wind speed and a short description | `@AgentTool` + `@CacheableToolResult(GLOBAL, 5 min)` + `@ToolRetry(2, on=IOException)` |
| `getForecast(city, days)` | Daily min/max temperature for the next `days` days (1–10) | `@AgentTool` + `@CacheableToolResult(GLOBAL, 30 min)` + `@ToolRetry(2)` |

Both go through Open-Meteo's geocoding endpoint first to resolve `city` → `(lat, lon)`, then call the forecast endpoint. WMO weather codes are translated into a short human description (`clear sky`, `rain`, `thunderstorm`, …).

## Run it

### Prerequisites
- Docker (for the all-in-one path below)
- Optional: Java 21+ and Maven 3.9+ if you'd rather run the agent on the host

The `.env.example` ships with primary/fallback pointing at OpenAI by default — drop your key into a copy named `.env` and you're good. To run **fully local**, follow the same pattern the cookbook example uses (set all three LLM roles to a local Ollama model).

### A. Everything in containers (`docker compose up`)

```bash
cd agent-example-weather
cp .env.example .env       # fill in LLM_PRIMARY_API_KEY at minimum
docker compose up -d
```

This brings up **mongo + redis + ollama + the agent**. Then:

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" -H "X-Session-Id: s1" \
  -d '{"message": "What is the weather like in Rome right now?"}'
```

Or open the chat UI at <http://localhost:8080/chat>.

### B. Embedded mode (no Docker, no databases)

```bash
export LLM_PRIMARY_PROVIDER=openai
export LLM_PRIMARY_MODEL=gpt-4o-mini
export LLM_PRIMARY_API_KEY=sk-...
SPRING_PROFILES_ACTIVE=embedded mvn -pl agent-example-weather spring-boot:run
```

All storage runs in-memory.

## Endpoints

| What | URL |
|------|-----|
| Chat web UI | <http://localhost:8080/chat> |
| Swagger UI | <http://localhost:8080/swagger-ui> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |
| Agent Card (A2A) | <http://localhost:8080/.well-known/agent.json> |
| Health | <http://localhost:8080/actuator/health> |

## Try it

```bash
# current weather
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: alice" -H "X-Session-Id: s1" \
  -d '{"message": "What is the weather in Tokyo right now?"}'

# multi-day forecast
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: alice" -H "X-Session-Id: s1" \
  -d '{"message": "Give me the 5-day forecast for Reykjavik"}'

# greeting (routes to default-skill thanks to memory-layers opt-out)
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: alice" -H "X-Session-Id: s2" \
  -d '{"message": "Hi!"}'
```

## What this example deliberately does NOT show

This agent is meant to be the smallest useful demo — one tool, one skill, one real integration. For richer feature coverage see:

- **[`agent-example-fitcoach`](../agent-example-fitcoach/)** — 6 skills, RAG, RBAC, HITL, structured output, `@AgentsFlow`, multi-tier memory, Bifrost LLM gateway with Azure OpenAI.
- **[`agent-example-cookbook`](../agent-example-cookbook/)** — 6 skills + 5 tool classes covering all annotation combos, JSON-Schema structured output, fully-local Ollama llama3.2:3b primary + qwen2.5:3b fallback.
