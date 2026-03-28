package ai.gargantua.core.orchestrator;

import java.util.Map;

public record AgentToolRequest(
        String input,
        String userId,
        String parentSessionId,
        Map<String, Object> context
) {
}
