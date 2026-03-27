package io.agentkit.core.orchestrator;

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
