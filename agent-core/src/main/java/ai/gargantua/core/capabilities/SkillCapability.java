package ai.gargantua.core.capabilities;

import java.util.List;

/**
 * Public-facing capability description for a single skill. Derived from
 * {@link ai.gargantua.core.skill.SkillMeta} but safe for external consumers.
 *
 * @param skillId      the skill name
 * @param description  what the skill does
 * @param domain       logical grouping
 * @param version      semver string
 * @param active       whether the skill is currently enabled
 * @param hasSchema    whether JSON output schema validation is configured
 * @param allowedTools tools this skill can invoke
 */
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
