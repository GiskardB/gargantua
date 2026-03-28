package ai.gargantua.core.orchestrator;

/**
 * Extension point for injecting dynamic context into the system prompt at runtime.
 * Enrichers run after skill activation but before the LLM call, appending named
 * sections (e.g. "Current Date", "User Preferences") to the prompt.
 *
 * <p>Implement this interface as a Spring bean. Enrichers are sorted by {@link #order()}
 * and can optionally target a specific skill via {@link #targetSkill()}.</p>
 *
 * @see EnricherContext
 */
public interface ContextEnricher {

    /** The section heading appended to the system prompt (e.g. "Current Date"). */
    String sectionName();

    /** Execution order. Lower values run first. */
    int order();

    /**
     * If non-null, this enricher only runs for the named skill.
     * Return null to run for all skills.
     */
    default String targetSkill() {
        return null;
    }

    /**
     * Produces the context section content. Return empty string to skip.
     */
    String enrich(EnricherContext ctx);
}
