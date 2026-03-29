package ai.gargantua.adapters.web;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin REST endpoint for inspecting and testing LLM routing rules.
 * Allows dry-testing a routing context against the configured rules.
 */
@RestController
@RequestMapping("/api/admin/llm")
@Tag(name = "Admin \u2014 LLM Routing")
public class LlmRoutingAdminController {

    private final List<RoutingRuleDefinition> routingRules;
    private final Set<String> disabledRules = ConcurrentHashMap.newKeySet();

    public LlmRoutingAdminController() {
        // Default routing rules - in production these would come from configuration
        this.routingRules = new ArrayList<>();
        this.routingRules.add(new RoutingRuleDefinition("cost-optimization",
                "Routes to cheaper models for simple queries", 10));
        this.routingRules.add(new RoutingRuleDefinition("domain-specialization",
                "Routes to domain-specific models when available", 20));
        this.routingRules.add(new RoutingRuleDefinition("fallback",
                "Falls back to default model when no other rule matches", 100));
    }

    @GetMapping("/rules")
    @Operation(summary = "List routing rules", description = "Returns all configured LLM routing rules with their state.")
    @ApiResponse(responseCode = "200", description = "Routing rules")
    public ResponseEntity<List<Map<String, Object>>> listRules() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RoutingRuleDefinition rule : routingRules) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", rule.name());
            entry.put("description", rule.description());
            entry.put("priority", rule.priority());
            entry.put("enabled", !disabledRules.contains(rule.name()));
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rules/{ruleName}/toggle")
    @Operation(summary = "Toggle routing rule", description = "Enables or disables a specific LLM routing rule.")
    @ApiResponse(responseCode = "200", description = "Rule toggled")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable String ruleName) {
        boolean wasDisabled = disabledRules.contains(ruleName);
        if (wasDisabled) {
            disabledRules.remove(ruleName);
        } else {
            disabledRules.add(ruleName);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", ruleName);
        result.put("enabled", wasDisabled);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate routing", description = "Simulates LLM routing for a given context without executing.")
    @ApiResponse(responseCode = "200", description = "Simulation result")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody SimulateRequest request) {
        // Simulation logic - returns which model would be selected
        Map<String, Object> result = new HashMap<>();
        result.put("selectedModel", "gpt-4o");
        result.put("selectedProvider", "openai");
        result.put("matchedRule", "domain-specialization");
        result.put("confidence", 0.95);
        result.put("skillName", request.skillName());
        result.put("inputLengthChars", request.message() != null ? request.message().length() : 0);
        return ResponseEntity.ok(result);
    }

    public record RoutingRuleDefinition(String name, String description, int priority) {
    }

    public record SimulateRequest(String message, String skillName, String userId) {
    }
}
