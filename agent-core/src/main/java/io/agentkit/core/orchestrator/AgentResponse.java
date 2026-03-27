package io.agentkit.core.orchestrator;

import java.util.List;

public record AgentResponse(
        String text,
        String sessionId,
        String skillUsed,
        List<String> toolsCalled,
        RoutingMethod routingMethod,
        double routingConfidence,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        double estimatedCostUsd,
        long durationMs,
        boolean dryRun
) {
}
