# FitCoach AI — Eval Report

> **Generated**: 2026-03-31T20:37:54.340Z
> **Target**: http://localhost:8080
> **Duration**: 172s
> **Eval cases**: 31

---

## Summary

| Metric | Value |
|--------|-------|
| **Overall Score** | **100.0%** |
| Total cases | 31 |
| PASS | 31 |
| PARTIAL | 0 |
| FAIL | 0 |
| ERROR | 0 |
| Avg tokens/response | 394 |
| Avg response time | 5552ms |
| Avg response length | 1481 chars |

---

## Results by Skill

### default-skill (3/3 PASS, score: 100.0%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
| greeting-introduction | **PASS** | 100% | fitcoach, help | - | 91 | 1372ms |
| capabilities-question | **PASS** | 100% | workout, nutrition | - | 194 | 2312ms |
| off-topic-redirect | **PASS** | 100% | fitness | - | 69 | 1049ms |

### health-skill (5/5 PASS, score: 100.0%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
| bmi-calculation | **PASS** | 100% | bmi, normal | - | 153 | 3117ms |
| bmi-overweight | **PASS** | 100% | bmi, health | - | 232 | 2974ms |
| health-metrics-tracking | **PASS** | 100% | body, track | - | 269 | 3284ms |
| resting-heart-rate | **PASS** | 100% | heart, rate | - | 153 | 2035ms |
| body-fat-estimation | **PASS** | 100% | body fat, percent | - | 283 | 4177ms |

### news-skill (3/3 PASS, score: 100.0%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
| latest-fitness-trends | **PASS** | 100% | fitness, trend | - | 342 | 3567ms |
| health-research-news | **PASS** | 100% | research, strength | - | 168 | 1780ms |
| sports-events | **PASS** | 100% | sport, fitness | - | 298 | 3395ms |

### nutrition-skill (10/10 PASS, score: 100.0%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
| meal-plan-muscle-gain | **PASS** | 100% | protein, meal, breakfast | - | 694 | 12505ms |
| food-lookup-salmon | **PASS** | 100% | protein, salmon | - | 238 | 3096ms |
| vegan-protein-sources | **PASS** | 100% | protein, plant | - | 339 | 4775ms |
| cutting-diet-1800cal | **PASS** | 100% | protein, calorie | - | 583 | 10556ms |
| pre-workout-nutrition | **PASS** | 100% | carb, before | - | 434 | 6808ms |
| post-workout-meal | **PASS** | 100% | protein, recovery | - | 391 | 6634ms |
| macro-breakdown-explanation | **PASS** | 100% | protein, carb, fat | - | 414 | 6440ms |
| food-comparison | **PASS** | 100% | protein, chicken | - | 544 | 6321ms |
| hydration-advice | **PASS** | 100% | water, hydration | - | 363 | 3305ms |
| supplement-creatine | **PASS** | 100% | creatine, gram | - | 389 | 4936ms |

### workout-skill (10/10 PASS, score: 100.0%)

| Case | Verdict | Score | Found | Missing | Tokens | Time |
|------|---------|-------|-------|---------|--------|------|
| workout-plan-muscle-gain | **PASS** | 100% | muscle, press, day | - | 1019 | 14615ms |
| search-chest-exercises | **PASS** | 100% | bench press, chest, dumbbell | - | 613 | 7963ms |
| beginner-fat-loss-workout | **PASS** | 100% | beginner, band | - | 709 | 10703ms |
| leg-day-advanced | **PASS** | 100% | squat, leg | - | 387 | 4986ms |
| home-bodyweight-routine | **PASS** | 100% | push, bodyweight | - | 413 | 5059ms |
| stretching-flexibility | **PASS** | 100% | stretch, flexibility | - | 559 | 6602ms |
| upper-lower-split | **PASS** | 100% | upper, lower | - | 710 | 14133ms |
| cardio-recommendations | **PASS** | 100% | cardio, fat | - | 378 | 4356ms |
| shoulder-exercises | **PASS** | 100% | shoulder, press | - | 428 | 5592ms |
| workout-injury-modification | **PASS** | 100% | knee, safe | - | 342 | 3669ms |

---

## Methodology

Each eval case is executed against the running FitCoach AI instance:

1. The input message is sent to `POST /api/agent/chat` with `X-Force-Skill` header to isolate skill behavior
2. The response text is checked for presence of expected terms (case-insensitive)
3. **Score** = (found terms / expected terms). If skill routing doesn't match, score = 0
4. **Verdict**: PASS (>= 66%), PARTIAL (33-65%), FAIL (< 33%), ERROR (request failed)

### Scoring criteria

- Expected terms are intentionally **broad and flexible** (e.g., "protein" not "exactly 25g of protein")
- The eval tests **domain knowledge and relevance**, not exact wording
- A PARTIAL result means the response is relevant but incomplete
- A FAIL result means the response missed the topic entirely

### Golden datasets

| Skill | Cases | Location |
|-------|-------|----------|
| workout-skill | 10 | `skills/workout-skill/evals/evals.json` |
| nutrition-skill | 10 | `skills/nutrition-skill/evals/evals.json` |
| health-skill | 5 | `skills/health-skill/evals/evals.json` |
| news-skill | 3 | `skills/news-skill/evals/evals.json` |
| default-skill | 3 | `skills/default-skill/evals/evals.json` |
