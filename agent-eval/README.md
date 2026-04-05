# Gargantua Agent Eval

Standalone LLM-as-Judge evaluation tool for AI agents. Calls your agent via REST, scores responses with a pluggable judge, and produces JSON + HTML reports.

Zero Spring dependencies. Pure Java 21 + HttpClient + Jackson.

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Run against your agent (uses LLM judge by default)
java -jar target/agent-eval-1.0.0.jar \
  --agent-url http://localhost:8080 \
  --evals-dir ./examples/weather-skill \
  --judge-endpoint http://localhost:11434/v1 \
  --judge-model phi4-mini

# Or use keyword matching (no LLM required)
java -jar target/agent-eval-1.0.0.jar \
  --agent-url http://localhost:8080 \
  --evals-dir ./examples/weather-skill \
  --plugin keyword-match
```

Exit code 0 = passed (score >= threshold), exit code 1 = failed.

## Configuration

Configuration loads in order of priority: CLI args > `eval-config.yml` > defaults.

Copy the example config to get started:

```bash
cp eval-config.example.yml eval-config.yml
```

### eval-config.yml

```yaml
agent-url: http://localhost:8080
evals-dir: ./evals
threshold: 0.70
parallelism: 4

# Scoring plugin: llm-judge | keyword-match | regex
plugin: llm-judge

# Plugin-specific configuration
plugin-config:
  judge.endpoint: http://localhost:11434/v1
  judge.model: phi4-mini
  judge.key: ""

# Report output
report:
  json: true
  html: true
  output-dir: ./eval-reports
```

### CLI Arguments

| Argument | Default | Description |
|---|---|---|
| `--config <path>` | `./eval-config.yml` | Path to YAML config file |
| `--agent-url <url>` | `http://localhost:8080` | Agent REST API base URL |
| `--evals-dir <path>` | `./evals` | Directory containing `evals.json` files |
| `--plugin <name>` | `llm-judge` | Scoring plugin to use |
| `--judge-endpoint <url>` | `http://localhost:11434/v1` | LLM judge endpoint |
| `--judge-model <name>` | `phi4-mini` | Judge model name |
| `--judge-key <key>` | (empty) | Judge API key |
| `--threshold <0.0-1.0>` | `0.70` | Minimum passing score |
| `--parallelism <n>` | `4` | Max concurrent eval cases |
| `--output-dir <path>` | `./eval-reports` | Report output directory |
| `--no-html` | | Disable HTML report |
| `--no-json` | | Disable JSON report |

## Eval Cases Format

Create `evals.json` files in your evals directory. Each file contains an array of test cases:

```json
[
  {
    "id": "weather-basic",
    "description": "Ask for current weather in a specific city",
    "input": "What's the weather like in San Francisco?",
    "expectedBehaviors": [
      "Mentions San Francisco",
      "Includes temperature or weather condition",
      "Provides a helpful response"
    ],
    "forbiddenBehaviors": [
      "Makes up specific numbers without a data source",
      "Says it cannot help"
    ],
    "tags": ["weather", "basic"]
  }
]
```

Fields:
- **id** -- unique identifier for the case
- **description** -- human-readable description
- **input** -- the message sent to the agent
- **expectedBehaviors** -- list of behaviors the response should exhibit
- **forbiddenBehaviors** -- list of behaviors the response must not exhibit (optional)
- **tags** -- labels for filtering and grouping (optional)

Nested directories are supported. The tool walks the evals directory recursively looking for `evals.json` files.

## Plugin System

Scoring is handled by plugins discovered via `java.util.ServiceLoader`. Three built-in plugins ship with agent-eval:

### Built-in Plugins

**llm-judge** -- Sends the agent response + expected behaviors to an LLM (via OpenAI-compatible API) for scoring. Most accurate but requires a running LLM endpoint.

**keyword-match** -- Pure string matching. Checks if significant words from expected behaviors appear in the response. Fast, no external calls. Good fallback when no LLM is available.

**regex** -- Treats each expected behavior as a regex pattern and checks if it matches the response. Useful for structured output validation (JSON fields, specific formats).

### Writing a Custom Plugin

1. Implement the `EvalPlugin` interface:

```java
package com.example.eval;

import ai.gargantua.eval.plugin.*;

public class ToxicityPlugin implements EvalPlugin {

    @Override
    public String name() { return "toxicity"; }

    @Override
    public String description() { return "Checks response for toxic content"; }

    @Override
    public PluginResult evaluate(PluginContext context) {
        var response = context.agentResponse();
        var config = context.config(); // access plugin-config from YAML

        // Your scoring logic here
        double score = 1.0;
        String verdict = "PASS";

        return new PluginResult(score, verdict, "No toxic content detected",
                List.of("clean"), List.of());
    }
}
```

2. Register it in `META-INF/services/ai.gargantua.eval.plugin.EvalPlugin`:

```
com.example.eval.ToxicityPlugin
```

3. Place the JAR on the classpath:

```bash
java -cp agent-eval.jar:my-plugin.jar ai.gargantua.eval.EvalCli --plugin toxicity
```

Plugin-specific configuration is passed via the `plugin-config` map in YAML or via CLI args like `--judge-endpoint`.

## Docker

Build and run with Docker:

```bash
# Build the image
mvn clean package -DskipTests
docker build -t gargantua/agent-eval .

# Run (mount your evals directory)
docker run -v ./evals:/eval/evals gargantua/agent-eval \
  --agent-url http://host.docker.internal:8080 \
  --plugin keyword-match

# With LLM judge
docker run \
  -v ./evals:/eval/evals \
  -v ./eval-reports:/eval/eval-reports \
  gargantua/agent-eval \
  --agent-url http://host.docker.internal:8080 \
  --judge-endpoint http://host.docker.internal:11434/v1

# With a config file
docker run \
  -v ./evals:/eval/evals \
  -v ./eval-config.yml:/eval/eval-config.yml \
  -v ./eval-reports:/eval/eval-reports \
  gargantua/agent-eval
```

Use `host.docker.internal` to reach services running on the host machine.

## Report Formats

### JSON Report

Produced at `./eval-reports/eval-report-YYYY-MM-DD.json`. Contains full structured data: overall score, per-case results, passed/failed behaviors, timing, and verdicts.

### HTML Report

Produced at `./eval-reports/eval-report-YYYY-MM-DD.html`. A single self-contained HTML file (inline CSS, no external dependencies) with:

- Summary dashboard: overall score, pass/fail/partial counts
- Per-case expandable cards showing input, response, score, verdict, and behavior breakdowns
- Color coding: green (pass), red (fail), yellow (partial)

## CI/CD Integration

### GitHub Actions

```yaml
name: Agent Eval
on:
  push:
    branches: [main]
  schedule:
    - cron: '0 6 * * *'  # daily at 6am

jobs:
  eval:
    runs-on: ubuntu-latest
    services:
      agent:
        image: your-org/your-agent:latest
        ports:
          - 8080:8080
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Build agent-eval
        working-directory: agent-eval
        run: mvn clean package -DskipTests

      - name: Run evaluations
        working-directory: agent-eval
        run: |
          java -jar target/agent-eval-1.0.0.jar \
            --agent-url http://localhost:8080 \
            --plugin keyword-match \
            --threshold 0.70

      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: eval-report
          path: agent-eval/eval-reports/
```

## Architecture

```
evals.json ──> EvalCli ──> AgentClient ──> Your Agent (REST)
                  │                              │
                  │         agent response <──────┘
                  │
                  ├──> EvalPlugin (ServiceLoader)
                  │      ├── LlmJudgePlugin ──> LLM endpoint
                  │      ├── KeywordMatchPlugin (local)
                  │      └── RegexPlugin (local)
                  │
                  ├──> EvalReport (JSON)
                  └──> HtmlReportGenerator (HTML)
```

The agent is called via `POST /api/agent/chat` with `{"message": "..."}`. The response is expected to have a `text` or `response` field, or the raw body is used.
