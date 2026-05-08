package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.LlmRouter;
import ai.gargantua.core.llm.LlmRoutingContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Admin — LLM Routing")
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
    @Operation(summary = "List routing rules",
            description = "Returns all configured LLM routing rules with their state.")
    @ApiResponse(responseCode = "200", description = "Routing rules")
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
    @Operation(summary = "Toggle routing rule",
            description = "Flips the enabled flag on a configured rule. Returns 404 when the rule name is unknown.")
    @ApiResponse(responseCode = "200", description = "Rule toggled")
    @ApiResponse(responseCode = "404", description = "Rule not found")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable String ruleName) {
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
    @Operation(summary = "Simulate routing",
            description = "Runs the live routing-rule evaluator against the supplied context "
                    + "and returns the selected model plus a per-rule trace. No LLM call is made.")
    @ApiResponse(responseCode = "200", description = "Simulation result")
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
    public record SimulateRequest(
            String message,
            String skillName,
            String skillDomain,
            String userId,
            String userTier,
            Integer inputLength,
            Map<String, String> attributes
    ) {
        /** Backward-compatible 3-arg constructor used by older callers and tests. */
        public SimulateRequest(String message, String skillName, String userId) {
            this(message, skillName, null, userId, null, null, null);
        }
    }
}
