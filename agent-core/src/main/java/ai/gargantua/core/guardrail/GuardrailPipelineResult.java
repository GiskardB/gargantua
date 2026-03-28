package ai.gargantua.core.guardrail;

import java.util.List;

public record GuardrailPipelineResult(
        boolean blocked,
        String blockedBy,
        String reason,
        List<GuardrailResult> results
) {

    public static GuardrailPipelineResult passed(List<GuardrailResult> results) {
        return new GuardrailPipelineResult(false, null, null, results);
    }

    public static GuardrailPipelineResult blocked(String guardrailName, String reason, List<GuardrailResult> results) {
        return new GuardrailPipelineResult(true, guardrailName, reason, results);
    }
}
