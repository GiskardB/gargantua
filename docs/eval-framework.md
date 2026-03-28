# Eval Framework — LLM-as-Judge

## Concept
A second LLM (the "judge") evaluates agent responses against a golden dataset. Each skill can have its own evals in `skills/{name}/evals/evals.json`.

## Golden Dataset Format
```json
[
  {
    "id": "eval-001",
    "description": "Simple weather query",
    "input": "What's the weather in Rome?",
    "expectedBehaviors": [
      "Calls getWeather tool with city='Rome'",
      "Provides temperature in Celsius"
    ],
    "forbiddenBehaviors": [
      "Invents weather data without calling tool"
    ],
    "tags": ["happy-path"]
  }
]
```

## Running Evals

### Via API
```bash
# Single skill
curl -X POST http://localhost:8080/api/admin/evals/run/weather-skill

# All skills
curl -X POST http://localhost:8080/api/admin/evals/run
```
Returns HTTP 422 if overallScore < fail-threshold (useful for CI).

### Via CLI
```bash
eval run --skill weather-skill
eval run --all
```

## EvalReport
```json
{
  "skillName": "weather-skill",
  "overallScore": 0.95,
  "totalCases": 4,
  "passed": 3, "failed": 0, "partial": 1,
  "comparison": { "previousScore": 0.82, "scoreDelta": 0.13 },
  "results": [...]
}
```

## Configuration
```yaml
agent:
  evals:
    enabled: true
    judge-model: routing-model
    fail-threshold: 0.70
    report-ttl-days: 90
```

## CI Integration
The eval endpoint returns 422 when score is below threshold — fail the build:
```bash
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/admin/evals/run)
if [ "$STATUS" = "422" ]; then echo "Eval FAILED"; exit 1; fi
```
