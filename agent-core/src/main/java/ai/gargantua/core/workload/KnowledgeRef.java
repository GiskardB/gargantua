package ai.gargantua.core.workload;

/**
 * A reference, in an agent's {@link Loadout}, to a specific knowledge base the agent is
 * equipped with — a named collection/index in the vector store, the same identifier a
 * skill's {@code metadata.knowledge-base} points at (see
 * {@link ai.gargantua.core.rag.RagConfig}).
 *
 * <p>This is what makes an agent's knowledge <em>targeted</em>: instead of implicitly
 * reaching whatever a skill happens to configure, the agent declares up front the
 * knowledge bases it is allowed to draw on. It carries only a reference and optional
 * retrieval hints — never data or credentials — so a bundle stays signable.</p>
 *
 * @param name        the knowledge base (vector collection/index) name; required
 * @param description optional human-readable note on what this base holds
 * @param maxResults  optional override for the number of chunks to retrieve; {@code null}
 *                    means inherit the skill/runtime default (see {@code RagConfig})
 * @param minScore    optional override for the similarity threshold; {@code null} means
 *                    inherit the default
 */
public record KnowledgeRef(
        String name,
        String description,
        Integer maxResults,
        Double minScore
) {

    public KnowledgeRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("KnowledgeRef.name must not be blank");
        }
        name = name.trim();
        if (maxResults != null && maxResults < 1) {
            throw new IllegalArgumentException("KnowledgeRef.maxResults must be >= 1");
        }
        if (minScore != null && (minScore < 0.0 || minScore > 1.0)) {
            throw new IllegalArgumentException("KnowledgeRef.minScore must be in [0.0, 1.0]");
        }
    }

    /** A reference by name only, inheriting all retrieval defaults. */
    public KnowledgeRef(String name) {
        this(name, null, null, null);
    }
}
