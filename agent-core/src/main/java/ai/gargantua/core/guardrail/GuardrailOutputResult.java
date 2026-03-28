package ai.gargantua.core.guardrail;

public record GuardrailOutputResult(
        GuardrailVerdict verdict,
        String processedResponse,
        String reason,
        String guardrailName
) {
}
