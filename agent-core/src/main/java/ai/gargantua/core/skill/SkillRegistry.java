package ai.gargantua.core.skill;

import java.util.List;
import java.util.Optional;

/**
 * Port for discovering and loading skills. Skills are the fundamental unit of
 * agent behavior -- each skill defines a system prompt, allowed tools, and
 * optional output schema.
 *
 * <p>The framework ships with a decorator chain of implementations:
 * {@code CompositeSkillRegistry} (merges filesystem + classpath JAR sources)
 * -> {@code CachedSkillRegistry} (TTL-based cache)
 * -> {@code HotReloadSkillRegistry} (watches filesystem for changes).</p>
 *
 * @see SkillMeta
 * @see SkillCard
 */
public interface SkillRegistry {

    /** Returns lightweight metadata for all registered skills (active and inactive). */
    List<SkillMeta> listMeta();

    /**
     * Loads the full skill card for execution. This is the expensive operation
     * that parses the SKILL.md body and resolves references.
     *
     * @throws ai.gargantua.core.exception.SkillNotFoundException if the skill does not exist
     */
    SkillCard load(String skillName);

    /** Finds metadata for a single skill by name, or empty if not found. */
    Optional<SkillMeta> findMeta(String skillName);

    /** Forces a reload of all skills from their sources. */
    void reload();
}
