# Skills & Routing

## SKILL.md Format

Every skill is defined by a single `SKILL.md` file that combines YAML frontmatter with a markdown body. The frontmatter carries machine-readable metadata (name, version, allowed tools, model parameters). The markdown body is the **system prompt** injected when the skill is activated -- it tells the LLM how to behave while operating within this skill.

### Complete Example

```markdown
---
name: weather-skill
description: Answers weather-related questions using real-time data from OpenWeatherMap.
version: 1.2.0
allowed-tools:
  - getWeather
  - getWeatherForecast
metadata:
  active: true
  domain: weather
  output-schema: assets/schema.json
  max-tokens: 1024
  temperature: 0.2
  preferred-model: claude-sonnet-4-20250514
---

You are a weather assistant. Use the provided tools to answer questions about
current conditions and forecasts. Always include the temperature unit in your
response. If the user asks about a location you cannot resolve, ask for
clarification rather than guessing.

Do NOT answer questions unrelated to weather. Politely redirect the user.
```

### Frontmatter Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Unique identifier for the skill. Must match the folder name exactly. Lowercase, letters/numbers/hyphens only, max 64 characters. |
| `description` | string | Yes | Short description used by the router to decide whether this skill matches a user query. Keep it under 512 characters for best results. |
| `version` | string | Yes | Semantic version (e.g. `1.2.0`). Enforced by the linter. |
| `allowed-tools` | list of strings (or whitespace-separated string) | Yes | Tool method names this skill is permitted to call. Only these tools are exposed to the LLM when the skill is active. Both `["a", "b"]` and `"a b"` are accepted. |
| `references` | list of strings | No | File paths whose content is appended to the system prompt at activation time. **Tested** by `agent-example-skill-filesystem`. |
| `examples` | list of strings | No | Example prompts surfaced via the A2A `/.well-known/agent.json` and the MCP capabilities resource for discovery. Not used at runtime by the router. **Tested** by `agent-example-a2a`. |
| `metadata.active` | boolean | No | Whether the skill is available for routing. Defaults to `true`. Set to `false` to hide the skill from `SkillRegistry.listMeta()` and from the A2A agent card without deleting the folder. **Tested** by `agent-example-skill-filesystem` and `agent-example-a2a`. |
| `metadata.domain` | string | No | Logical grouping label (e.g. `weather`, `finance`, `devops`). Used for filtering, observability, A2A skill tags, and as the `domain` selector in LLM routing rules. |
| `metadata.output-schema` | string | No | Relative path to a JSON Schema file for structured output. When set, `SchemaValidatorGuardrail` validates the LLM response against this schema and triggers a corrective-prompt auto-retry on failure. **Tested** by `agent-example-output-schema`. |
| `metadata.max-tokens` | integer | No | Maximum token budget for the LLM response within this skill. Overrides the global `agent.llm.primary.max-tokens` for this skill only. |
| `metadata.temperature` | float | No | LLM sampling temperature override for this skill. Overrides `agent.llm.primary.temperature` for this skill only. |
| `metadata.preferred-model` | string | No | Model alias (one of `primary` / `fallback` / a custom alias from `agent.llm.models.*`) to use when this skill is active. Resolved by `LlmRouter` and wins over routing rules. |
| `metadata.knowledge-base` | string | No | Name of a RAG vector store knowledge base (e.g. `hr-docs`). When set, `RagEnricher` auto-injects retrieved documents into the prompt. **Tested** by `agent-example-rag`. **WIP:** the only shipped `VectorStorePort` is the keyword-based `InMemoryVectorStore` — see [`extending.md` → RAG / Vector Store](extending.md#rag--vector-store) for the WIP note and how to plug a real vector DB. |
| `metadata.rag-max-results` | integer | No | Maximum number of RAG documents to retrieve. Default `5`. |
| `metadata.rag-min-score` | float | No | Minimum similarity score for RAG results. Default `0.3`. |
| `metadata.allowed-roles` | list of strings | No | Roles permitted to use this skill (e.g. `[financial-advisor, super-admin]`). If set, `RbacGuardrail` blocks users without a matching role. The `super-admin` role bypasses all restrictions. **Tested** by `agent-example-tool-rbac` (tool-level RBAC, same store). |
| `metadata.memory-layers` | list of strings | No | Subset of memory layers to fetch for this skill: `working`, `episodic`, `knowledge` (case-insensitive). When set, layers not listed are skipped — their port (Redis or MongoDB) is not queried. Defaults to all three layers. Use it for stateless skills (greetings, simple Q&A) to save a Redis/MongoDB round-trip. See [Memory System](memory-system.md). |

### Folder Structure

```
skills/
└── weather-skill/
    ├── SKILL.md              # Required — skill definition
    ├── api-notes.md          # Referenced via `references:` in frontmatter
    ├── unit-guide.md
    ├── references/           # Optional — auto-appended to references list
    │   └── api-docs.md
    └── assets/
        └── schema.json       # Optional — JSON Schema for structured output
```

- **`references:` (top-level frontmatter list)** — each path listed is appended to the system prompt when the skill is activated. Use it for domain knowledge, style guides, or API documentation that the LLM should always have in context. Example:

  ```yaml
  references:
    - api-notes.md
    - unit-guide.md
  ```

  The agent assembles `system prompt = SKILL.md body + "\n\n" + concatenated references`. See the `agent-example-skill-filesystem` example for a complete end-to-end test (`support-skill` exercises the `references/` folder auto-append).

- **`references/` folder (optional)** — in addition to the frontmatter list, `FilesystemSkillRegistry` reads every readable file under `<skill>/references/` (sorted by URI for deterministic ordering) and appends each file's contents to the skill's reference list — **frontmatter entries first, folder files after**. Drop docs in there and they're picked up at the next skill load with no SKILL.md edit.

- **assets/** — static resources referenced by frontmatter fields (e.g. `output-schema`).

### Naming Rules

- The folder name **must** match the `name` field in frontmatter exactly.
- Allowed characters: lowercase letters, digits, and hyphens.
- Maximum length: 64 characters.
- Examples: `weather-skill`, `jira-integration`, `code-review-v2`.

### Default Skill

The skill named `default-skill` acts as the fallback. When the router cannot match any skill above the confidence threshold, the request is handled by `default-skill`. It should contain a general-purpose system prompt and a broad set of allowed tools suitable for open-ended conversations.

Every project should include a fallback skill (the framework default name is `default`, configurable via `agent.routing.fallback-skill`). If neither a matching skill nor the configured fallback skill exists, unmatched requests will fail.

---

## Skill Registry

The skill registry is responsible for discovering, loading, and serving skill definitions. Three implementations are provided, and they can be composed together.

### FilesystemSkillRegistry

The baseline implementation. At startup it scans `classpath:skills/` for directories containing a `SKILL.md` file and parses each one into a `SkillCard`.

### CachedSkillRegistry

A decorator that wraps any other registry with a [Caffeine](https://github.com/ben-manes/caffeine) cache. Parsed skill cards are cached in memory to avoid repeated filesystem reads and YAML parsing.

**This is the default registry implementation** — `SkillRegistryAutoConfiguration` registers a `CachedSkillRegistry` around the composite `(filesystem + classpath-jar + annotated)` chain whenever `agent.skill.hot-reload=false` (the default). Configure the cache via:

```yaml
agent:
  skill:
    hot-reload: false           # default — uses CachedSkillRegistry
    cache-ttl-minutes: 60       # legacy TTL knob (used when `cache.ttl-seconds` is unset)
    cache:
      ttl-seconds: 0            # 0 = fall back to `cache-ttl-minutes` × 60
      max-size: 200             # Caffeine `maximumSize` for the per-card cache
```

`cache.ttl-seconds` takes precedence when greater than zero, so older configurations using `cache-ttl-minutes` keep working unchanged.

### HotReloadSkillRegistry

Uses `java.nio.file.WatchService` to monitor the skills directory for changes. When a `SKILL.md` file is created, modified, or deleted, the registry updates automatically without restarting the agent.

```yaml
agent:
  skill:
    hot-reload: true          # Enable live reload (default: false)
```

This is intended for development. In production, prefer `CachedSkillRegistry` with a reasonable TTL.

### Progressive Disclosure

Skill loading is intentionally lazy to minimize startup time and memory usage.

| Phase | Trigger | What is loaded |
|-------|---------|----------------|
| **Phase 1 — Boot** | Application startup | Only YAML frontmatter is parsed into a lightweight `SkillMeta` object (name, description, version, active flag). The markdown body and references are not read. |
| **Phase 2 — Routing** | Incoming user message | Only `name` and `description` from `SkillMeta` are used to compute similarity and select a skill. |
| **Phase 3 — Activation** | Skill is matched | The full `SkillCard` is loaded: markdown body, output schema, reference files. This is the only point where disk I/O for the body occurs. |

This three-phase approach means that adding dozens of skills does not impact boot time or routing latency -- only the matched skill pays the full loading cost.

---

## Routing

### Hybrid Strategy

The default routing strategy combines fast in-process semantic similarity with an LLM fallback for ambiguous queries.

```
Input → Embedding (all-MiniLM-L6-v2-quantized, in-process, ~2-5ms)
      → Cosine similarity vs pre-computed skill embeddings
      → If score >= threshold (default 0.6): SEMANTIC routing
      → If score <  threshold: LLM routing fallback (~300ms)
      → If forceSkill provided (header OR body field): FORCED routing (skip all matching)
```

> "Forced routing" is triggered when the HTTP request carries an
> `X-Force-Skill: <skill-name>` header, **or** when the JSON request
> body includes a `"forceSkill": "<skill-name>"` field (the body field
> wins if both are present). The header is parsed by
> `ChatController` / `ChatStreamController` and turned into
> `AgentRequest.forceSkill` before the orchestrator sees the request.

- **Semantic routing** uses the [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) model running in-process via ONNX Runtime. Skill description embeddings are pre-computed at boot and cached. Typical latency is 2-5ms.
- **LLM routing** sends the user message along with all skill names and descriptions to the LLM and asks it to pick the best match. This is slower (~300ms) but handles nuanced or multi-domain queries better.
- **Forced routing** bypasses matching entirely and activates the specified skill directly.

### Configuration

```yaml
agent:
  routing:
    strategy: hybrid          # semantic | llm | hybrid
    fallback-skill: default   # Skill name to route to when no match meets threshold
    threshold: 0.6            # Minimum cosine similarity for semantic match
```

`SemanticRoutingService.route()` branches on `strategy` (default `hybrid`). Unknown values fall back to `hybrid` with a warning log.

| Strategy | Behavior |
|----------|----------|
| `semantic` | Embedding similarity only. If no skill meets the threshold, returns the configured `fallback-skill` (no LLM call). |
| `llm` | Skips embeddings entirely; every request goes through `RoutingService.routeWithLlm`. |
| `hybrid` | Default. Tries semantic first; if below threshold, falls back to LLM routing. |

### Force a Specific Skill

Two ways to bypass routing and force a particular skill:

**Via HTTP header** — passed by the API gateway / your client, e.g. `curl -H "X-Force-Skill: weather-skill"`:
```
X-Force-Skill: weather-skill
```

**Via API request body** — when calling the JSON endpoint directly:
```json
{
  "message": "What is the temperature in Berlin?",
  "forceSkill": "weather-skill"
}
```

If both are present, the JSON body field wins. Forced routing skips both semantic and LLM matching. If the specified skill does not exist or is inactive, the request fails with a `SkillNotFoundException`.

---

## SkillsJars -- Skills as Maven Dependencies

Skills do not have to live in your local `skills/` directory. The **SkillsJars** ecosystem lets you import pre-built skills as standard Maven dependencies. Inside each JAR, skills are packaged under `META-INF/skills/` using the same folder structure as local skills.

```xml
<dependency>
    <groupId>com.skillsjars</groupId>
    <artifactId>browser-use__browser-use__browser-use</artifactId>
    <version>2026_02_23-1d154e1</version>
</dependency>
```

At startup, `CompositeSkillRegistry` merges skills from all sources:

1. Local skills from `classpath:skills/`
2. JAR-packaged skills from `META-INF/skills/`

When a name conflict occurs, **local skills win**. This lets you override a JAR-provided skill by placing a skill with the same name in your local `skills/` directory.

### SKILL.md Linter

A Maven plugin is available for build-time validation of all `SKILL.md` files (both local and JAR-packaged).

```xml
<plugin>
    <groupId>ai.gargantua</groupId>
    <artifactId>agent-skill-linter-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals><goal>lint</goal></goals>
        </execution>
    </executions>
</plugin>
```

The linter enforces the following rules:

| Rule ID | Severity | Description |
|---------|----------|-------------|
| `name-matches-folder` | ERROR | The `name` field in frontmatter must match the containing folder name. |
| `version-semver` | ERROR | The `version` field must be valid semantic versioning (e.g. `1.0.0`). |
| `description-length` | WARNING | Descriptions longer than 512 characters may degrade routing accuracy. |
| `active-missing` | WARNING | The `metadata.active` field is not set. The skill will default to active, but being explicit is preferred. |

A build with any ERROR-level violation will fail. WARN-level violations are reported but do not break the build.
