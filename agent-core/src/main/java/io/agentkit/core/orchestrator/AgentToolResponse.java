package io.agentkit.core.orchestrator;

import java.util.List;

public record AgentToolResponse(
        String response,
        String skillUsed,
        List<String> toolsCalled,
        boolean success,
        String errorMessage,
        long durationMs
) {
}
