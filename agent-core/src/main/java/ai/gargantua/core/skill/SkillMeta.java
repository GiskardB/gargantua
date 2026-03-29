package ai.gargantua.core.skill;

import java.util.Set;

/**
 * Lightweight metadata for a skill, loaded at boot time from SKILL.md frontmatter.
 * Used by the routing engine to decide which skill handles a request without
 * loading the full skill content (system prompt, references, etc.).
 *
 * <p>The full skill content ({@link SkillCard}) is loaded lazily only when the
 * skill is actually activated for a request.</p>
 *
 * @param name         unique identifier for the skill (matches the SKILL.md folder name)
 * @param description  human-readable summary used by the semantic router for matching
 * @param version      semver string, validated by the skill linter
 * @param active       whether the skill is available for routing (disabled skills are invisible)
 * @param hasSchema    whether the skill defines a JSON output schema for validation
 * @param domain       logical grouping (e.g. "finance", "hr") used by guardrails and routing rules
 * @param source       where this skill was loaded from (filesystem, classpath JAR, database)
 * @param allowedRoles roles allowed to use this skill; empty set means no restriction
 *
 * @see SkillCard
 * @see SkillRegistry
 */
public record SkillMeta(
        String name,
        String description,
        String version,
        boolean active,
        boolean hasSchema,
        String domain,
        SkillSource source,
        Set<String> allowedRoles
) {
}
