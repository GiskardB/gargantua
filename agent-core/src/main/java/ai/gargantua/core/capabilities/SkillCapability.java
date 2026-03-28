package ai.gargantua.core.capabilities;

import java.util.List;

public record SkillCapability(
        String skillId,
        String description,
        String domain,
        String version,
        boolean active,
        boolean hasSchema,
        List<String> allowedTools
) {
}
