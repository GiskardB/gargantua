package ai.gargantua.core.orchestrator;

import java.util.Map;

public record EnricherContext(
        String userId,
        String sessionId,
        String skillName,
        String skillDomain,
        String userMessage,
        Map<String, String> attributes
) {
}
