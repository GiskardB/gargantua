package ai.gargantua.core.capabilities;

import java.time.Instant;
import java.util.List;

public record AgentCapabilities(
        String agentId,
        String displayName,
        String description,
        boolean available,
        List<SkillCapability> capabilities,
        Instant updatedAt
) {
}
