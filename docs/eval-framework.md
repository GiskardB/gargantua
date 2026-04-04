# Eval Framework — LLM-as-Judge

## What is it and why you need it

Every time you change a SKILL.md (tweak the system prompt, add a tool, adjust behavior), you risk breaking something that used to work. The Eval Framework catches regressions **before they reach production** by automatically testing your agent's behavior against a set of expected outcomes.

The approach is called **LLM-as-Judge**: a second, cheaper LLM (the "judge") reads the agent's response and scores it against criteria you define — like a code review, but for AI behavior.

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Golden Dataset│────▸│  Your Agent  │────▸│  LLM Judge   │────▸│  EvalReport  │
│ (evals.json) │     │  (dry-run)   │     │  (Ollama)    │     │  (score/fail)│
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
   You write this     Runs automatically    Scores the output    Saved in MongoDB
```

**Key points:**
- The judge runs on Ollama locally (zero cost) — same model used for routing (`phi4-mini`)
- Each eval case runs in **dry-run mode** — no real tool calls, no data persisted
- Results are saved in MongoDB and compared with previous runs to detect regressions
- The CI endpoint returns HTTP 422 if the score drops below your threshold

---

## How it works, step by step

1. You write a **golden dataset** — a list of test inputs with expected and forbidden behaviors
2. The standalone `agent-eval.jar` sends each input to the agent via REST (`POST /api/agent/chat`), executing cases in **parallel** using virtual threads
3. The agent responds normally (routes to a skill, calls tools, generates a response)
4. The **judge LLM** compares the agent's response + tool calls against your expectations
5. The judge returns a verdict (PASS / FAIL / PARTIAL) with a score and reasoning
6. All results are aggregated into an `EvalReport` with an overall score
7. If a previous report exists for the same skill, the delta is calculated (regression detection)

---

## Writing a Golden Dataset

Each skill can have its own eval file at `skills/{skill-name}/evals/evals.json`. The file is a JSON array of test cases.

### File location

```
src/main/resources/skills/
└── weather-skill/
    ├── SKILL.md
    └── evals/
        └── evals.json     ← your test cases
```

### Format

```json
[
  {
    "id": "weather-current-city",
    "description": "User asks for current weather in a known city",
    "input": "What is the weather like in London right now?",
    "expectedBehaviors": [
      "Calls getWeather tool with city='London'",
      "Mentions temperature in Celsius",
      "Mentions weather condition (sunny, cloudy, etc.)",
      "Includes humidity or wind speed"
    ],
    "forbiddenBehaviors": [
      "Fabricates weather data without calling a tool",
      "Calls getForecast instead of getWeather",
      "Sends a weather alert without being asked"
    ],
    "tags": ["happy-path", "tool-calling"]
  },
  {
    "id": "weather-no-city",
    "description": "User asks about weather without specifying a city",
    "input": "What's the weather like today?",
    "expectedBehaviors": [
      "Asks the user to specify a city or location",
      "Does not call any tool without a city"
    ],
    "forbiddenBehaviors": [
      "Calls getWeather with a made-up city",
      "Provides weather data without asking for clarification"
    ],
    "tags": ["edge-case", "clarification"]
  }
]
```

### Field reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Unique identifier for the test case (used in reports) |
| `description` | Yes | Human-readable description of what this case tests |
| `input` | Yes | The user message sent to the agent |
| `expectedBehaviors` | Yes | List of behaviors the agent MUST exhibit. **All** must be satisfied for a PASS. |
| `forbiddenBehaviors` | Yes | List of behaviors the agent must NOT exhibit. **Any** present triggers automatic FAIL. |
| `tags` | No | Labels for filtering and grouping (e.g. `happy-path`, `edge-case`, `tool-calling`) |

### Writing good eval cases

**Do:**
- Be specific: `"Calls getWeather tool with city='London'"` instead of `"Uses a tool"`
- Cover edge cases: ambiguous inputs, missing parameters, out-of-scope requests
- Test scope boundaries: `"Politely declines and explains it only handles weather queries"`
- Include at least 3–5 cases per skill

**Don't:**
- Test the LLM's general knowledge (it changes between models)
- Write expectations about exact wording (the agent's phrasing varies)
- Forget to test what the agent should NOT do (forbidden behaviors catch subtle bugs)

---

## Running Evals

### Via REST API

```bash
# Run eval suite for one skill
java -jar agent-eval.jar --evals-dir ./evals --agent-url http://localhost:8080

# Run evals for ALL skills that have evals.json
java -jar agent-eval.jar --evals-dir ./evals --agent-url http://localhost:8080
```

Both endpoints return the full `EvalReport` as JSON.

```

Example CLI output:
```
Running eval suite for: weather-skill (v1.0.0)
──────────────────────────────────────────────
[1/4] weather-current-city: Current weather in a city...  ✓ PASS  (0.95)
[2/4] weather-forecast:     Multi-day forecast...         ✓ PASS  (0.90)
[3/4] weather-alert:        Alert with approval...        ~ PARTIAL (0.70)
[4/4] weather-no-city:      Missing city clarification... ✓ PASS  (1.00)

Results: 3 PASS, 0 FAIL, 1 PARTIAL | Score: 0.89 | ▲ +0.07 vs last run
Threshold: 0.70 → PASS
```

---

## Understanding the EvalReport

```json
{
  "skillName": "weather-skill",
  "skillVersion": "1.0.0",
  "runAt": "2026-03-28T18:00:00Z",
  "totalCases": 4,
  "passed": 3,
  "failed": 0,
  "partial": 1,
  "overallScore": 0.89,
  "comparison": {
    "previousScore": 0.82,
    "scoreDelta": 0.07,
    "previousRunAt": "2026-03-25T10:00:00Z"
  },
  "results": [
    {
      "caseId": "weather-current-city",
      "description": "User asks for current weather in a known city",
      "input": "What is the weather like in London right now?",
      "actualResponse": "The weather in London is currently partly cloudy at 18.5°C...",
      "toolsCalled": ["getWeather"],
      "verdict": "PASS",
      "score": 0.95,
      "judgeReasoning": "Agent correctly called getWeather with city='London'. Response includes temperature in Celsius (18.5°C), condition (partly cloudy), and humidity (72%). All expected behaviors satisfied. No forbidden behaviors observed.",
      "passedBehaviors": [
        "Calls getWeather tool with city='London'",
        "Mentions temperature in Celsius",
        "Mentions weather condition",
        "Includes humidity or wind speed"
      ],
      "failedBehaviors": [],
      "durationMs": 1240
    }
  ]
}
```

### Verdicts explained

| Verdict | Meaning | When it happens |
|---------|---------|-----------------|
| **PASS** | All expected behaviors present, zero forbidden behaviors | Score typically 0.85–1.0 |
| **PARTIAL** | Some expected behaviors present, zero forbidden behaviors | Score typically 0.40–0.84 |
| **FAIL** | Any forbidden behavior detected, OR most expected behaviors missing | Score typically 0.0–0.39 |

### The comparison field

Every time you run an eval, the framework loads the **previous report** for the same skill from MongoDB and calculates the delta:

- `scoreDelta: +0.07` → your changes **improved** the skill
- `scoreDelta: -0.15` → your changes **regressed** the skill (investigate!)
- `comparison: null` → first run, no comparison available

---

## Configuration

```yaml
agent:
  evals:
    enabled: true
    judge-model: routing-model    # Uses the same Ollama model as skill routing (zero cost)
    fail-threshold: 0.70          # Minimum score to consider a suite passed
    report-ttl-days: 90           # How long to keep reports in MongoDB
```

| Property | Description | Default |
|----------|-------------|---------|
| `enabled` | Enable/disable the eval framework | `true` |
| `judge-model` | Which model evaluates responses. `routing-model` = Ollama phi4-mini | `routing-model` |
| `fail-threshold` | Minimum `overallScore` (0.0–1.0) for a passing suite | `0.70` |
| `report-ttl-days` | Days to keep reports in MongoDB before auto-deletion | `90` |

---

## CI/CD Integration

The eval endpoints use **HTTP status codes** to signal pass/fail:

| Status | Meaning |
|--------|---------|
| `200 OK` | `overallScore >= fail-threshold` → all good |
| `422 Unprocessable Entity` | `overallScore < fail-threshold` → quality regression |

This makes it trivial to fail a CI pipeline:

### GitHub Actions example

```yaml
- name: Run eval suite
  run: |
    # Start the agent (embedded mode, no Docker needed in CI)
    LLM_PRIMARY_API_KEY=${{ secrets.LLM_API_KEY }} \
    SPRING_PROFILES_ACTIVE=embedded \
    java -jar target/my-agent.jar &

    # Wait for startup
    sleep 10

    # Run evals — exits non-zero if score < threshold
    STATUS=$(curl -s -o eval-report.json -w "%{http_code}" \
      -X POST http://localhost:8080/api/admin/evals/run)

    cat eval-report.json | jq '.overallScore, .comparison'

    if [ "$STATUS" = "422" ]; then
      echo "❌ Eval FAILED — quality regression detected"
      cat eval-report.json | jq '.results[] | select(.verdict != "PASS")'
      exit 1
    fi

    echo "✅ Eval PASSED — score: $(cat eval-report.json | jq '.overallScore')"
```

### Shell script (simple)

```bash
#!/bin/bash
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/admin/evals/run)
if [ "$STATUS" = "422" ]; then
  echo "Eval FAILED"
  exit 1
fi
echo "Eval PASSED"
```

---

## Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/admin/evals/run/{skillName}` | Run eval suite for one skill |
| `POST` | `/api/admin/evals/run` | Run evals for all skills with `evals.json` |
| `GET` | `/api/admin/evals/reports/{skillName}/latest` | Latest report for a skill |
| `GET` | `/api/admin/evals/reports/{skillName}?limit=10` | Report history (for tracking trends) |
| `GET` | `/api/admin/evals/skills` | List skills that have evals + their last score |

---

## Best Practices

1. **Run evals after every SKILL.md change** — even small prompt tweaks can cause regressions
2. **Start with 3–5 cases per skill** — cover happy path, edge cases, and scope boundaries
3. **Use the `comparison` field** — a dropping score is more important than the absolute value
4. **Add evals to CI** — catch regressions before merge, not after deploy
5. **Use tags** to organize cases (`happy-path`, `edge-case`, `scope-guard`, `tool-calling`)
6. **Review `judgeReasoning`** when a case fails — the judge explains exactly why it scored as it did
