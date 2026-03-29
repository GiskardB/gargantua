package ai.gargantua.core.rag;

/**
 * RAG configuration extracted from SKILL.md frontmatter.
 * If a skill declares {@code metadata.knowledge-base}, an instance is created.
 * If not, RAG is not activated for that skill.
 *
 * @param knowledgeBase the collection/index name in the vector store
 * @param maxResults maximum chunks to retrieve (default: 5)
 * @param minScore minimum similarity threshold (default: 0.3)
 */
public record RagConfig(
    String knowledgeBase,
    int maxResults,
    double minScore
) {
    public RagConfig(String knowledgeBase) {
        this(knowledgeBase, 5, 0.3);
    }
}
