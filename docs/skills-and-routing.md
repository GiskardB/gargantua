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
| `allowed-tools` | list of strings | Yes | Tool method names this skill is permitted to call. Only these tools are exposed to the LLM when the skill is active. |
| `metadata.active` | boolean | No | Whether the skill is available for routing. Defaults to `true`. Set to `false` to disable without deleting. |
| `metadata.domain` | string | No | Logical grouping label (e.g. `weather`, `finance`, `devops`). Used for filtering and observability. |
| `metadata.output-schema` | string | No | Relative path to a JSON Schema file for structured output. When set, the LLM response is validated against this schema. |
| `metadata.max-tokens` | integer | No | Maximum token budget for the LLM response within this skill. |
| `metadata.temperature` | float | No | LLM sampling temperature override for this skill. |
| `metadata.preferred-model` | string | No | Model identifier to use when this skill is active, overriding the global default. |

### Folder Structure

```
skills/
└── weather-skill/
    ├── SKILL.md              # Required — skill definition
    ├── references/           # Optional — files injected into the system prompt
    │   ├── api-notes.md
    │   └── unit-guide.md
    ├── assets/
    │   └── schema.json       # Optional — JSON Schema for structured output
    └── evals/
        └── evals.json        # Optional — evaluation golden dataset
```

- **references/**: Every file in this directory is appended to the system prompt when the skill is activated. Use it for domain knowledge, style guides, or API documentation that the LLM should always have in context.
- **assets/**: Static resources referenced by frontmatter fields (e.g. `output-schema`).
- **evals/**: Golden input/output pairs for automated evaluation. The linter warns if this directory is missing.

### Naming Rules

- The folder name **must** match the `name` field in frontmatter exactly.
- Allowed characters: lowercase letters, digits, and hyphens.
- Maximum length: 64 characters.
- Examples: `weather-skill`, `jira-integration`, `code-review-v2`.

### Default Skill

The skill named `default-skill` acts as the fallback. When the router cannot match any skill above the confidence threshold, the request is handled by `default-skill`. It should contain a general-purpose system prompt and a broad set of allowed tools suitable for open-ended conversations.

Every project should include a `default-skill`. If none is present, unmatched requests will fail with a `NoSkillMatchedException`.

---

## Skill Registry

The skill registry is responsible for discovering, loading, and serving skill definitions. Three implementations are provided, and they can be composed together.

### FilesystemSkillRegistry

The baseline implementation. At startup it scans `classpath:skills/` for directories containing a `SKILL.md` file and parses each one into a `SkillCard`.

### CachedSkillRegistry

A decorator that wraps any other registry with a [Caffeine](https://github.com/ben-manes/caffeine) cache. Parsed skill cards are cached in memory to avoid repeated filesystem reads and YAML parsing.

```yaml
agent:
  skill:
    cache:
      ttl-seconds: 600       # Time-to-live for cached entries
      max-size: 100           # Maximum number of cached skills
```

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
Input → Embedding (all-MiniLM-L6-v2, in-process, ~2-5ms)
      → Cosine similarity vs pre-computed skill embeddings
      → If score >= threshold (default 0.82): SEMANTIC routing
      → If score <  threshold: LLM routing fallback (~300ms)
      → If forceSkill header present: FORCED routing (skip all matching)
```

- **Semantic routing** uses the [all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) model running in-process via ONNX Runtime. Skill description embeddings are pre-computed at boot and cached. Typical latency is 2-5ms.
- **LLM routing** sends the user message along with all skill names and descriptions to the LLM and asks it to pick the best match. This is slower (~300ms) but handles nuanced or multi-domain queries better.
- **Forced routing** bypasses matching entirely and activates the specified skill directly.

### Configuration

```yaml
agent:
  routing:
    strategy: hybrid          # semantic | llm | hybrid
    fallback-skill: default-skill
    semantic:
      threshold: 0.82         # Minimum cosine similarity for semantic match
```

| Strategy | Behavior |
|----------|----------|
| `semantic` | Embedding similarity only. Falls back to `fallback-skill` if no skill meets the threshold. |
| `llm` | LLM-based routing only. Every request incurs the LLM call. |
| `hybrid` | Tries semantic first; if below threshold, falls back to LLM routing. Recommended default. |

### Force a Specific Skill

There are three ways to bypass routing and force a particular skill.

**Via HTTP header:**
```
X-Force-Skill: weather-skill
```

**Via CLI:**
```
\skill weather-skill
```

**Via API request body:**
```json
{
  "message": "What is the temperature in Berlin?",
  "forceSkill": "weather-skill"
}
```

Forced routing skips both semantic and LLM matching. If the specified skill does not exist or is inactive, the request fails with a `SkillNotFoundException`.

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
    <groupId>io.agentkit</groupId>
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

| Rule | Severity | Description |
|------|----------|-------------|
| `NAME_MATCHES_FOLDER` | ERROR | The `name` field in frontmatter must match the containing folder name. |
| `VERSION_SEMVER` | ERROR | The `version` field must be valid semantic versioning (e.g. `1.0.0`). |
| `DESCRIPTION_LENGTH` | WARN | Descriptions longer than 512 characters may degrade routing accuracy. |
| `EVALS_PRESENT` | WARN | The `evals/` directory is missing or `evals.json` is empty. |
| `ACTIVE_MISSING` | WARN | The `metadata.active` field is not set. The skill will default to active, but being explicit is preferred. |

A build with any ERROR-level violation will fail. WARN-level violations are reported but do not break the build.
