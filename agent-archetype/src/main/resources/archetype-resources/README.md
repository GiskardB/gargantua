#set( $symbol_pound = '#' )
${symbol_pound} ${agentName}

An AI agent built on the [Gargantua AI Agent Framework][gargantua].

[gargantua]: https://github.com/GiskardB/gargantua

---

${symbol_pound}${symbol_pound} What you got from the archetype

| File / folder                                | Purpose                                                                                              |
|----------------------------------------------|------------------------------------------------------------------------------------------------------|
| `src/main/java/.../${agentName}Application.java` | Spring Boot entry point. `@SpringBootApplication` + `main`. **Note:** if your `-DagentName=` contained hyphens or other non-identifier characters, the post-generate script sanitised the class name to PascalCase (Java requires this). |
| `src/main/java/.../tools/SampleTool.java`    | An `@AgentTool` example. Add your own tools next to it.                                              |
| `src/main/resources/skills/default-skill/SKILL.md` | The default skill the router falls back to.                                                          |
| `src/main/resources/skills/sample-skill/SKILL.md`  | A second skill showing the routing surface.                                                          |
| `src/main/resources/application.yml`         | Main configuration (LLM provider, skill path, guardrails, memory, audit, cost, …).                   |
| `src/main/resources/application-embedded.yml` | Overrides for `embedded` profile — no MongoDB, no Redis (see "Run modes" below).                     |
| `pom.xml`                                    | Maven build (Spring Boot starter parent, JitPack repo, the Gargantua skill-linter plugin in `verify`). |
| `Dockerfile` + `docker-compose.yml`          | Full local stack — app + MongoDB + Redis + Ollama, with model pre-pulled.                            |
| `.env.example`                               | Template for runtime configuration. Copy to `.env` and fill in your API keys.                        |

---

${symbol_pound}${symbol_pound} Quick start

${symbol_pound}${symbol_pound}${symbol_pound} 1. Pick a run mode (see below)

| Mode      | Infra you need              | Run command                                       |
|-----------|------------------------------|---------------------------------------------------|
| `embedded` | none                         | `SPRING_PROFILES_ACTIVE=embedded mvn spring-boot:run` |
| Full stack | Docker (compose pulls everything) | `cp .env.example .env && docker compose up -d` |
| Host + your own infra | local Mongo + Redis + (Ollama or cloud LLM) | `cp .env.example .env && mvn spring-boot:run` |

${symbol_pound}${symbol_pound}${symbol_pound} 2. Open the chat UI

After the app is up, point your browser at:

- Chat:    <http://localhost:8080/chat>
- Swagger: <http://localhost:8080/swagger-ui>
- Actuator health: <http://localhost:8080/actuator/health>

---

${symbol_pound}${symbol_pound} Run modes — what's active and what's not

${symbol_pound}${symbol_pound}${symbol_pound} `embedded` profile

`embedded` is for local dev and tests when you don't want to run any external
service. **The agent pipeline runs in full** — routing, skills, guardrails,
HITL, audit, cost tracking, A2A, MCP all behave exactly as in production —
but everything that is normally persisted is replaced by an in-memory
implementation:

| Component                | Production backend     | In `embedded`                     | Implication                          |
|--------------------------|-------------------------|------------------------------------|---------------------------------------|
| Working memory           | Redis                  | `InMemoryWorkingMemoryAdapter`     | Conversation context lost on restart  |
| Episodic memory          | MongoDB                | `InMemoryEpisodicMemoryAdapter`    | Past-session summaries lost on restart |
| Knowledge memory         | MongoDB                | `InMemoryKnowledgeMemoryAdapter`   | User profile / preferences lost on restart |
| Approval store (HITL)    | MongoDB                | `InMemoryApprovalStore`            | Pending approvals lost on restart      |
| Audit store              | MongoDB                | `InMemoryAuditStore`               | Audit trail lost on restart            |
| Tool-result cache        | Redis                  | In-memory `ToolResultCache`        | Cache cold on restart                 |
| Vector store (RAG)       | (your choice — pgvector/Qdrant/Pinecone) | `InMemoryVectorStore` (keyword-based, **demo only**) | RAG is a Jaccard-similarity toy — not embedding-based. See the framework docs. |

**Guardrails, routing, A2A, MCP and cost-tracking are not disabled** — they
just lose the bits that depend on persistence (e.g. cross-restart audit
history). For production, set `SPRING_PROFILES_ACTIVE=default` (or leave
it unset) and provide real Mongo + Redis URIs.

${symbol_pound}${symbol_pound}${symbol_pound} Full stack via `docker compose`

`docker-compose.yml` brings up: your app, MongoDB, Redis, and Ollama (with
the routing model auto-pulled). This is the closest mirror of production
on a developer laptop.

---

${symbol_pound}${symbol_pound} Configuring the LLM provider

The agent talks to **three** LLM roles: `primary` (main responses),
`fallback` (auto-failover on primary errors), and `routing` (small/cheap
model that picks which skill handles each request).

${symbol_pound}${symbol_pound}${symbol_pound} OpenAI

```env
LLM_PRIMARY_PROVIDER=openai
LLM_PRIMARY_MODEL=gpt-4o
LLM_PRIMARY_API_KEY=sk-...
```

${symbol_pound}${symbol_pound}${symbol_pound} Anthropic

```env
LLM_PRIMARY_PROVIDER=anthropic
LLM_PRIMARY_MODEL=claude-sonnet-4-20250514
LLM_PRIMARY_API_KEY=sk-ant-...
```

${symbol_pound}${symbol_pound}${symbol_pound} Azure OpenAI / Foundry (v1.2.15+)

Azure Foundry requires an `api-version` and (typically) a deployment name
distinct from the model id. Both are supported:

```env
LLM_PRIMARY_PROVIDER=azure-openai
LLM_PRIMARY_MODEL=gpt-4o
LLM_PRIMARY_API_KEY=<your-resource-key>
LLM_PRIMARY_ENDPOINT=https://my-resource.openai.azure.com
LLM_PRIMARY_API_VERSION=2024-08-01-preview
LLM_PRIMARY_DEPLOYMENT_NAME=gpt-4o-prod        # optional; defaults to model id
```

${symbol_pound}${symbol_pound}${symbol_pound} Routing LLM (cheap / local)

By default the routing model runs locally via **Ollama** — zero cost,
zero API key. Override only if you need a cloud provider for routing too:

```env
LLM_ROUTING_PROVIDER=ollama
LLM_ROUTING_MODEL=phi4-mini
LLM_ROUTING_ENDPOINT=http://localhost:11434
${symbol_pound} If you swap to a cloud provider, fill the key:
${symbol_pound} LLM_ROUTING_API_KEY=sk-...
```

---

${symbol_pound}${symbol_pound} Useful commands

```bash
${symbol_pound} Run tests
mvn test

${symbol_pound} Run with the embedded profile (no Docker, no infra)
SPRING_PROFILES_ACTIVE=embedded mvn spring-boot:run

${symbol_pound} Lint your SKILL.md files (runs in `verify` phase automatically)
mvn verify

${symbol_pound} Bring up the full local stack
docker compose up -d

${symbol_pound} Hit the chat REST endpoint directly
curl -X POST http://localhost:8080/api/agent/chat \\
  -H 'Content-Type: application/json' \\
  -H 'X-User-Id: alice' -H 'X-Session-Id: s1' \\
  -d '{"message": "hello"}'
```

---

${symbol_pound}${symbol_pound} Where to go next

- **Per-feature examples** (one annotation / capability at a time, each
  `mvn test`-verifiable): <https://github.com/GiskardB/gargantua-examples>
- **Framework docs**: <https://giskardb.github.io/gargantua-site>
- **Source**: <https://github.com/GiskardB/gargantua>
- **Issue tracker**: <https://github.com/GiskardB/gargantua/issues>

---

${symbol_pound}${symbol_pound} Licence

This project is generated from the Gargantua agent archetype, which is
MIT-licensed. The framework artifacts it depends on are also MIT.
