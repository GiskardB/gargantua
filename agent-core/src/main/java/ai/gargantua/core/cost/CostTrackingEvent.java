package ai.gargantua.core.cost;

public record CostTrackingEvent(
        String userId,
        String sessionId,
        String skillName,
        String provider,
        String model,
        String phase,
        int inputTokens,
        int outputTokens,
        long durationMs,
        boolean dryRun
) {
}
