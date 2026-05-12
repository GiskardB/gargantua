# Agent DSL — Skills and Flows in Java

Gargantua offers two ways to define skills: **SKILL.md files** (declarative, hot-reloadable) and **Java annotations** (type-safe, co-located with tools). Both produce identical skills at runtime — the framework treats them the same way.

---

## @AgentSkill — Define a Skill in Java

Instead of writing a separate SKILL.md file, annotate a Java class:

```java
@AgentSkill(
    name = "coder",
    description = "Writes and reviews code. Use for programming tasks.",
    domain = "engineering",
    version = "1.0.0"
)
@Component
public class CoderAgent {

    /** The system prompt — what the LLM sees when this skill is activated. */
    public static final String PROMPT = """
        ## Role
        You are a senior software engineer.

        ## Behavior
        - Write clean, tested, maintainable code
        - Always explain your reasoning
        - If the spec is ambiguous, ask for clarification

        ## Scope
        Code only. Redirect infrastructure or DevOps questions.
        """;

    @AgentTool(description = "Writes code from a specification")
    public String writeCode(String spec) {
        return codeService.generate(spec);
    }

    @AgentTool(description = "Reviews code for bugs and improvements")
    public String reviewCode(String code) {
        return codeService.review(code);
    }
}
```

### How it works

1. At startup, `AgentSkillProcessor` scans all Spring beans annotated with `@AgentSkill`
2. It auto-detects all **public** `@AgentTool` methods in the class → these become the skill's `allowed-tools` (private/protected methods are ignored)
3. The system prompt is read from a `public static final String PROMPT` field (since Javadoc isn't available at runtime)
4. A `SkillCard` is generated and registered in the `SkillRegistry`
5. The skill participates in routing, guardrails, memory, and everything else — identical to file-based skills

### Priority: SKILL.md wins

If both `skills/coder/SKILL.md` and `@AgentSkill(name = "coder")` exist, the **file takes priority**. The annotation is a convenience for simple skills — use files when you need hot-reload or complex prompt editing.

### All attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `name` | String | **(required)** | Unique skill name |
| `description` | String | **(required)** | Used by routing to decide when to activate this skill |
| `version` | String | `"1.0.0"` | Semantic version |
| `domain` | String | `"general"` | Domain for disclaimers and LLM routing rules |
| `active` | boolean | `true` | If false, skill is invisible to routing |
| `allowedRoles` | String[] | `{}` | RBAC: roles that can use this skill. Empty = no restriction. |
| `knowledgeBase` | String | `""` | RAG: vector store collection name. Empty = no RAG. The embedded default (v1.2.18+) is `EmbeddingInMemoryVectorStore` with real cosine similarity over MiniLM embeddings — see [`extending.md` → RAG / Vector Store](extending.md#rag--vector-store) for swapping the embedding model or the storage backend. |
| `ragMaxResults` | int | `5` | Max RAG chunks to retrieve |
| `ragMinScore` | double | `0.3` | Min similarity score for RAG results |
| `temperature` | double | `-1` | LLM temperature override. -1 = use default. |
| `maxTokens` | int | `-1` | LLM max tokens override. -1 = use default. |
| `outputSchema` | String | `""` | Classpath path to JSON Schema for structured output |
| `preferredModel` | String | `""` | Force a specific LLM model alias |
| `examples` | String[] | `{}` | Example prompts surfaced via the A2A Agent Card for discovery. Propagated by `AgentSkillProcessor` into `SkillCard.examples()` and exposed by `AgentCardService` on `/.well-known/agent.json`. |

### Advanced example — all features

```java
@AgentSkill(
    name = "nutrition",
    description = "Provides nutrition advice, meal plans, and food information",
    domain = "medical",                              // → medical disclaimer auto-injected
    allowedRoles = {"user", "nutritionist"},          // → RBAC restriction
    knowledgeBase = "nutrition-docs",                 // → RAG from vector store
    ragMaxResults = 3,
    temperature = 0.5,
    outputSchema = "schemas/nutrition-response.json", // → structured JSON output
    examples = {"Create a meal plan for 2000 calories", "Is avocado healthy?"}
)
@Component
public class NutritionAgent {

    static final String PROMPT = """
        ## Role
        You are a certified nutritionist.

        ## Behavior
        - Consider dietary restrictions from the user profile
        - Always mention this is general advice, not medical prescription
        - Use the retrieved nutrition documents to support your answers

        ## Scope
        Nutrition and diet only. Redirect exercise questions.
        """;

    @AgentTool(description = "Creates a daily meal plan for a caloric target")
    public MealPlan createMealPlan(String goal, int targetCalories) { ... }

    @AgentTool(description = "Looks up nutritional info for a food item")
    @CacheableToolResult(ttlSeconds = 3600, scope = CacheScope.GLOBAL)
    public NutrientInfo lookupFood(String food) { ... }
}
```

---

## Example prompts

Use the `examples` attribute on `@AgentSkill` to declare prompt suggestions. They are propagated into `SkillCard.examples()` and surfaced in the A2A Agent Card response (`/.well-known/agent.json`) so client UIs (Claude Desktop, custom UIs, quick-action buttons) can render them as discovery hints.

```java
@AgentSkill(
    name = "fitness",
    description = "...",
    examples = {
        "Create a workout plan for muscle gain",
        "What should I eat for breakfast?",
        "Calculate my BMI"
    }
)
@Component
public class FitnessAgent { ... }
```

---

## @AgentsFlow — Multi-Step Pipelines

A flow chains multiple skills in sequence. Each step's output becomes context for the next step. Every step passes through the full Gargantua pipeline (guardrails, routing, memory, LLM).

### Define a flow

```java
@Component
public class MyFlows {

    @AgentsFlow(name = "code-review", description = "Plan, code, then review")
    public void codeReviewFlow(FlowDefinition flow) {
        flow.step("planner", "Break down the task into implementation steps")
            .step("coder", "Implement the code based on the plan above")
            .step("reviewer", "Review the code for bugs, security, and style");
    }
}
```

### How it works

```
User message: "Build a REST API for payments"
    │
    ▼  Step 1: planner skill
    "Break down the task..." + user message
    → "1. Create PaymentController  2. Add validation  3. ..."
    │
    ▼  Step 2: coder skill
    "Implement based on the plan above..." + planner output
    → "```java\n@RestController\npublic class PaymentController { ... }"
    │
    ▼  Step 3: reviewer skill
    "Review the code..." + coder output
    → "The code looks good. Suggestions: 1. Add input validation..."
    │
    ▼  Final output returned to client
```

Each step:
1. Gets the previous step's output as context
2. Is forced to a specific skill (bypasses routing)
3. Goes through guardrails, memory, and the full LLM pipeline
4. Has its own isolated session ID (`{sessionId}:flow:{flowName}:step:{i}`)

### Execute a flow

**Via REST API:**

```bash
curl -X POST http://localhost:8080/api/flows/code-review/start \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -d '{"input": "Build a REST API for payments"}'
```

**Response:**

```json
{
  "flowName": "code-review",
  "finalOutput": "The code looks good. Suggestions: ...",
  "stepResults": [
    {
      "skillName": "planner",
      "input": "Break down the task...\nBuild a REST API for payments",
      "output": "1. Create PaymentController...",
      "durationMs": 2340
    },
    {
      "skillName": "coder",
      "input": "Implement based on the plan...\n1. Create PaymentController...",
      "output": "```java\n@RestController...",
      "durationMs": 4120
    },
    {
      "skillName": "reviewer",
      "input": "Review the code...\n```java\n@RestController...",
      "output": "The code looks good. Suggestions: ...",
      "durationMs": 1890
    }
  ],
  "totalDurationMs": 8350
}
```

**List all registered flows:**

```bash
curl http://localhost:8080/api/flows
```

### Step instructions

Each step can have an optional instruction prepended to the input:

```java
flow.step("analyzer")                          // no instruction — just passes input
    .step("writer", "Write a blog post about the analysis above")  // instruction added
    .step("editor", "Edit for grammar and clarity");               // instruction added
```

### Loop steps

A loop step repeats a skill up to a maximum number of iterations. The loop exits early if the LLM includes `[DONE]` or `[SATISFIED]` in its output, signaling that further iteration is unnecessary.

```java
@AgentsFlow(name = "iterative-review", description = "Draft and refine iteratively")
public void iterativeReview(FlowDefinition flow) {
    flow.step("writer", "Write an initial draft")
        .loop("reviewer", 3);  // Review and improve up to 3 times
}
```

Each iteration receives the previous iteration's output as context. This is useful for refinement workflows where quality improves with each pass.

### Parallel steps

Parallel steps execute simultaneously using virtual threads. All parallel steps receive the same input (the output of the previous step). Their outputs are combined and passed as context to the next step.

```java
@AgentsFlow(name = "multi-analysis", description = "Analyze from multiple angles in parallel")
public void multiAnalysis(FlowDefinition flow) {
    flow.parallel("security-reviewer", "performance-reviewer", "style-reviewer")
        .step("summarizer", "Combine the reviews above into a single report");
}
```

Consecutive parallel steps are grouped and executed together. The combined output is formatted as `[skill-name]: output` sections.

### FitCoach example flows

The [FitCoach AI example](https://github.com/GiskardB/gargantua-examples/tree/main/agent-example-fitcoach) includes flows demonstrating all three step types:

```java
// Sequential: health assessment → workout → nutrition
@AgentsFlow(name = "full-fitness-plan")
public void fullFitnessPlan(FlowDefinition flow) {
    flow.step("health-skill", "Assess the user's current health and fitness level")
        .step("workout-skill", "Create a personalized workout based on the assessment")
        .step("nutrition-skill", "Create a nutrition plan matching the workout");
}

// Loop: create then iteratively refine
@AgentsFlow(name = "iterative-workout")
public void iterativeWorkout(FlowDefinition flow) {
    flow.step("workout-skill", "Create an initial workout plan")
        .loop("reviewer-skill", 3);  // Review and improve up to 3 times
}

// Parallel: assess health and nutrition simultaneously, then combine
@AgentsFlow(name = "parallel-assessment")
public void parallelAssessment(FlowDefinition flow) {
    flow.parallel("health-skill", "nutrition-skill")
        .step("workout-skill", "Create a workout plan based on the health assessment and nutrition info above");
}
```

### Advanced orchestration with langchain4j-agentic

The `langchain4j-agentic` dependency is available on the classpath for advanced custom orchestration patterns (e.g., building untyped agent graphs, custom routing logic). The built-in `FlowExecutor` uses our own orchestrator so that guardrails, memory, and audit are applied to every step, but you can use `langchain4j-agentic` directly for specialized use cases that need lower-level control.

---

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/flows` | List all registered flows with their steps |
| `POST` | `/api/flows/{flowName}/start` | Execute a flow — returns FlowResult with all step outputs |

---

## When to use what

| Approach | Best for | Hot-reload? | Type-safe? |
|----------|---------|-------------|------------|
| **SKILL.md file** | Complex prompts, frequent prompt iteration, non-developers editing behavior | Yes | No |
| **@AgentSkill** | Simple skills, co-locating tools + skill in one class, compile-time validation | No (rebuild needed) | Yes |
| **@AgentsFlow** | Multi-step pipelines (sequential, loop, parallel), orchestrating multiple skills, complex workflows | No (rebuild needed) | Yes |
| **Both** | Use @AgentSkill for simple skills, SKILL.md for complex ones — they coexist | Mixed | Mixed |
