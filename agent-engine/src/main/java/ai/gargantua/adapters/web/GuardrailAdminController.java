package ai.gargantua.adapters.web;

import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Admin REST endpoint for inspecting and toggling guardrails at runtime.
 * Lists all registered input/output guardrails and their enabled status.
 */
@RestController
@RequestMapping("/api/admin/guardrails")
@Tag(
        name = "Admin \u2014 Guardrails",
        description = "Inspect and toggle the input/output guardrail pipeline at runtime. The toggle "
                + "is in-process only (it flips an in-memory set on this node); persist via the "
                + "`agent.guardrail.*.<name>-enabled` properties for cluster-wide changes."
)
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
    @Operation(
            summary = "List the guardrail pipeline",
            description = "Returns every input and output guardrail discovered by Spring, in the order "
                    + "the pipeline runs them, with their per-node `enabled` state. Disabled guardrails "
                    + "still show up in the list — the runtime simply skips them."
    )
    @ApiResponse(responseCode = "200",
            description = "Object with `inputGuardrails` and `outputGuardrails` arrays. Each entry: "
                    + "`{name, type: \"INPUT\"|\"OUTPUT\", enabled}`.")
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
    @Operation(
            summary = "Toggle a guardrail on/off (in-process)",
            description = "Flips the enabled state for the named guardrail on **this** node only. "
                    + "Useful for incident response (e.g. quickly silencing a false-positive PromptInjection "
                    + "rule). For permanent / cluster-wide changes, set the corresponding "
                    + "`agent.guardrail.*.<name>-enabled` property and restart."
    )
    @ApiResponse(responseCode = "200",
            description = "Body returns `{name, enabled}` reflecting the new state.")
    public ResponseEntity<Map<String, Object>> toggleGuardrail(
            @Parameter(description = "Guardrail name as reported by `GET /api/admin/guardrails`.",
                    example = "prompt-injection")
            @PathVariable String guardrailName) {
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
