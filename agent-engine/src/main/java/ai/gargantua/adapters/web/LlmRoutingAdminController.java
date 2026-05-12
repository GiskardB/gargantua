package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.LlmRouter;
import ai.gargantua.core.llm.LlmRoutingContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoint for inspecting, toggling and simulating LLM routing rules.
 * Backed by the live {@link AgentProperties} routing-rule list and {@link LlmRouter}
 * — no hardcoded rules — so changes via {@code /toggle} take effect immediately
 * for subsequent chat requests.
 */
@RestController
@RequestMapping("/api/admin/llm")
@Tag(
        name = "Admin — LLM Routing",
        description = "Inspect, toggle and simulate the model-pool routing rules. The rule list comes "
                + "from `agent.llm.routing-rules` (live `AgentProperties`) — every change applied via "
                + "`/toggle` takes effect on the next request. The `/simulate` endpoint runs the same "
                + "evaluator chat traffic uses, with a per-rule trace, without spending a token."
)
public class LlmRoutingAdminController {

    private final AgentProperties properties;
    private final LlmRouter llmRouter;
    private final LlmProviderFactory llmProviderFactory;

    public LlmRoutingAdminController(AgentProperties properties,
                                     LlmRouter llmRouter,
                                     LlmProviderFactory llmProviderFactory) {
        this.properties = properties;
        this.llmRouter = llmRouter;
        this.llmProviderFactory = llmProviderFactory;
    }

    @GetMapping("/rules")
    @Operation(
            summary = "List configured routing rules",
            description = "Returns every routing rule from `agent.llm.routing-rules` in declaration order, "
                    + "with `name`, `description`, `priority`, `enabled`, `targetModel` and the raw `condition` "
                    + "map. Use `POST /simulate` to see the evaluator's verdict for a specific request shape."
    )
    @ApiResponse(responseCode = "200",
            description = "Array of rule descriptors. Empty when `agent.llm.routing-rules` is unset.")
    public ResponseEntity<List<Map<String, Object>>> listRules() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentProperties.RoutingRule rule : properties.getLlm().getRoutingRules()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", rule.getName());
            entry.put("description", rule.getDescription());
            entry.put("priority", rule.getPriority());
            entry.put("enabled", rule.isEnabled());
            entry.put("targetModel", rule.getTargetModel());
            entry.put("condition", rule.getCondition());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rules/{ruleName}/toggle")
    @Operation(
            summary = "Toggle a routing rule on/off (in-process)",
            description = "Flips the `enabled` flag on the named rule. Live until restart — for permanent "
                    + "changes, edit `agent.llm.routing-rules.<name>.enabled` and redeploy."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Body returns `{name, enabled}`."),
            @ApiResponse(responseCode = "404", description = "No rule with this name in `agent.llm.routing-rules`.")
    })
    public ResponseEntity<Map<String, Object>> toggleRule(
            @Parameter(description = "Rule name as declared in `agent.llm.routing-rules.<name>`.",
                    example = "premium-and-todo")
            @PathVariable String ruleName) {
        var found = llmRouter.findRule(ruleName);
        if (found.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Routing rule not found: " + ruleName);
            return ResponseEntity.status(404).body(body);
        }
        AgentProperties.RoutingRule rule = found.get();
        rule.setEnabled(!rule.isEnabled());

        Map<String, Object> result = new HashMap<>();
        result.put("name", rule.getName());
        result.put("enabled", rule.isEnabled());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/simulate")
    @Operation(
            summary = "Dry-run the routing-rule evaluator",
            description = """
                    Builds an `LlmRoutingContext` from the request body and runs `LlmRouter.evaluateAll`
                    against the live rule set. Returns:

                    - `selectedAlias` / `selectedModel` / `selectedProvider` — what the orchestrator would
                      have used.
                    - `matchedRule` — the first rule that matched (`null` ⇒ pass-through to primary alias).
                    - `evaluatedRules` — every rule with `{enabled, matched, targetModel}` so you can see
                      the trace.

                    Uses the current wall-clock for time-window / day-of-week rules. **No LLM call is
                    made**, so this is free to call as often as you like (good for canary debugging).
                    """)
    @ApiResponse(responseCode = "200", description = "Simulation result with the per-rule trace.")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody SimulateRequest request) {
        String message = request.message() != null ? request.message() : "";
        int inputLength = request.inputLength() != null
                ? request.inputLength()
                : message.length();
        int estimatedTokens = inputLength / 4; // matches DefaultTokenBudgetManager.estimate
        Map<String, String> attributes = request.attributes() != null
                ? request.attributes() : Map.of();

        LlmRoutingContext ctx = new LlmRoutingContext(
                request.userId() != null ? request.userId() : "anonymous",
                "simulate-session",
                request.skillName(),
                request.skillDomain() != null ? request.skillDomain() : "general",
                message,
                inputLength,
                estimatedTokens,
                request.userTier() != null ? request.userTier() : "default",
                LocalTime.now(),
                DayOfWeek.from(LocalDate.now()),
                attributes
        );

        LlmRouter.RoutingDecision decision = llmRouter.evaluateAll(ctx);
        AgentProperties.LlmModelConfig config = llmProviderFactory.getModelConfig(decision.selectedAlias());

        Map<String, Object> result = new HashMap<>();
        result.put("selectedModel", config != null ? config.getModel() : decision.selectedAlias());
        result.put("selectedAlias", decision.selectedAlias());
        result.put("selectedProvider", config != null ? config.getProvider() : null);
        result.put("matchedRule", decision.matchedRule());
        result.put("skillName", request.skillName());
        result.put("skillDomain", ctx.skillDomain());
        result.put("userTier", ctx.userTier());
        result.put("inputLengthChars", inputLength);
        result.put("estimatedTokens", estimatedTokens);

        List<Map<String, Object>> evaluatedRules = new ArrayList<>();
        for (LlmRouter.RuleEvaluation eval : decision.evaluatedRules()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", eval.name());
            entry.put("priority", eval.priority());
            entry.put("enabled", eval.enabled());
            entry.put("matched", eval.matched());
            entry.put("targetModel", eval.targetModel());
            evaluatedRules.add(entry);
        }
        result.put("evaluatedRules", evaluatedRules);

        return ResponseEntity.ok(result);
    }

    /**
     * Request payload for the simulate endpoint. All fields are optional aside from
     * {@code message} (used for length-based rules); missing fields fall back to safe defaults.
     */
    @Schema(description = "Synthetic context to evaluate the routing rules against.")
    public record SimulateRequest(
            @Schema(description = "User message — feeds `input-length`, `estimated-tokens` and `input-contains` rules.",
                    example = "please fix this TODO in the parser")
            String message,
            @Schema(description = "Activated skill name. Drives `skill: …` and AND/OR conditions.",
                    example = "coding-skill")
            String skillName,
            @Schema(description = "Skill domain (defaults to `general`). Drives `domain: …` rules.",
                    example = "engineering")
            String skillDomain,
            @Schema(description = "Caller identity (defaults to `anonymous`). Currently unused by built-in rules.",
                    example = "alice")
            String userId,
            @Schema(description = "User tier (defaults to `default`). Drives `user-tier: …` rules.",
                    example = "premium")
            String userTier,
            @Schema(description = "Override `input-length` independently of `message.length()`. Useful for "
                    + "estimating what would happen with longer prompts.",
                    example = "2400")
            Integer inputLength,
            @Schema(description = "Free-form attributes — drive `attribute-match` rules and the legacy "
                    + "unknown-key attribute equality fallback.",
                    example = "{\"x-priority\": \"urgent\"}")
            Map<String, String> attributes
    ) {
        /** Backward-compatible 3-arg constructor used by older callers and tests. */
        public SimulateRequest(String message, String skillName, String userId) {
            this(message, skillName, null, userId, null, null, null);
        }
    }
}
