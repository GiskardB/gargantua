package ai.gargantua.core.orchestrator;

/**
 * Outcome of the skill routing phase. Captures which skill was selected,
 * how it was selected, and the confidence level of the match.
 *
 * <p>Use the static factory methods for common construction patterns.</p>
 *
 * @see RoutingMethod
 */
public record RoutingResult(
        String skillName,
        RoutingMethod method,
        double confidence,
        long durationMs
) {

    public static RoutingResult semantic(String name, double confidence) {
        return new RoutingResult(name, RoutingMethod.SEMANTIC, confidence, 0L);
    }

    public static RoutingResult llm(String name) {
        return new RoutingResult(name, RoutingMethod.LLM, 1.0, 0L);
    }

    public static RoutingResult forced(String name) {
        return new RoutingResult(name, RoutingMethod.FORCED, 1.0, 0L);
    }
}
