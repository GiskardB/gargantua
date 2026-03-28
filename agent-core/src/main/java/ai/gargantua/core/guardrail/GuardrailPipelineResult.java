package ai.gargantua.core.guardrail;

import java.util.List;

/**
 * Aggregate result of running the full input guardrail pipeline. If any guardrail
 * returns BLOCK, the pipeline short-circuits and this result captures which guardrail
 * blocked and why.
 *
 * @param blocked   true if the pipeline was halted by a BLOCK verdict
 * @param blockedBy name of the guardrail that blocked (null if not blocked)
 * @param reason    explanation from the blocking guardrail (null if not blocked)
 * @param results   individual results from all guardrails that ran
 */
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
