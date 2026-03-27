package io.agentkit.adapters.web;

import io.agentkit.core.guardrail.InputGuardrail;
import io.agentkit.core.guardrail.OutputGuardrail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/admin/guardrails")
@Tag(name = "Admin \u2014 Guardrails")
public class GuardrailAdminController {

    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final Set<String> disabledGuardrails = ConcurrentHashMap.newKeySet();

    public GuardrailAdminController(
            List<InputGuardrail> inputGuardrails,
            List<OutputGuardrail> outputGuardrails) {
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
    }

    @GetMapping
    @Operation(summary = "List guardrail pipeline", description = "Returns all configured guardrails with their enabled state.")
    @ApiResponse(responseCode = "200", description = "Guardrail pipeline")
    public ResponseEntity<Map<String, Object>> listGuardrails() {
        List<Map<String, Object>> input = new ArrayList<>();
        for (InputGuardrail g : inputGuardrails) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", g.name());
            entry.put("type", "INPUT");
            entry.put("enabled", !disabledGuardrails.contains(g.name()));
            input.add(entry);
        }

        List<Map<String, Object>> output = new ArrayList<>();
        for (OutputGuardrail g : outputGuardrails) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", g.name());
            entry.put("type", "OUTPUT");
            entry.put("enabled", !disabledGuardrails.contains(g.name()));
            output.add(entry);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("inputGuardrails", input);
        result.put("outputGuardrails", output);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{guardrailName}/toggle")
    @Operation(summary = "Toggle guardrail", description = "Enables or disables a specific guardrail.")
    @ApiResponse(responseCode = "200", description = "Guardrail toggled")
    public ResponseEntity<Map<String, Object>> toggleGuardrail(@PathVariable String guardrailName) {
        boolean wasDisabled = disabledGuardrails.contains(guardrailName);
        if (wasDisabled) {
            disabledGuardrails.remove(guardrailName);
        } else {
            disabledGuardrails.add(guardrailName);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", guardrailName);
        result.put("enabled", wasDisabled);
        return ResponseEntity.ok(result);
    }
}
