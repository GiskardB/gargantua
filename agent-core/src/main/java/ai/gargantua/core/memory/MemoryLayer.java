package ai.gargantua.core.memory;

/**
 * Memory layers a skill can declare it needs at runtime. When a {@link ai.gargantua.core.skill.SkillCard}
 * lists a subset of layers, the {@code MemoryComposer} skips fetching the unlisted ones —
 * useful for stateless skills (greetings, simple Q&amp;A) that don't benefit from past
 * sessions or user knowledge.
 *
 * <p>If a skill leaves the enabled layers unset (null), all layers are fetched (default).</p>
 */
public enum MemoryLayer {
    /** Current session chat history (Redis). Always cheap; almost every skill needs it. */
    WORKING,
    /** Compressed summaries of past sessions for the same user (MongoDB). */
    EPISODIC,
    /** Stable user-level knowledge segments — preferences, profile (MongoDB). */
    KNOWLEDGE
}
