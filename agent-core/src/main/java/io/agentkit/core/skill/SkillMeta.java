package io.agentkit.core.skill;

public record SkillMeta(
        String name,
        String description,
        String version,
        boolean active,
        boolean hasSchema,
        String domain,
        SkillSource source
) {
}
