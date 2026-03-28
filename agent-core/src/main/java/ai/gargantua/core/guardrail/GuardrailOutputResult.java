package ai.gargantua.core.guardrail;

/**
 * Outcome of a single {@link OutputGuardrail} processing step.
 *
 * @param verdict           PASS or BLOCK (WARN is not used for output guardrails)
 * @param processedResponse the (possibly transformed) response text
 * @param reason            explanation if blocked or modified
 * @param guardrailName     which guardrail produced this result
 */
public record GuardrailOutputResult(
        GuardrailVerdict verdict,
        String processedResponse,
        String reason,
        String guardrailName
) {
}
