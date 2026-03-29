package ai.gargantua.core.rag;

import java.util.List;

/**
 * Port for searching a vector store. Each implementation connects to a specific
 * vector database (pgvector, Qdrant, Milvus, in-memory, etc.).
 *
 * <p>The framework calls this port automatically when a skill declares
 * {@code knowledge-base} in its SKILL.md frontmatter.</p>
 */
public interface VectorStorePort {

    /**
     * Search for document chunks similar to the query.
     *
     * @param collection the knowledge base name (from SKILL.md metadata.knowledge-base)
     * @param query the user's message (or an embedding of it)
     * @param maxResults maximum number of chunks to return
     * @param minScore minimum similarity score (0.0-1.0) to include
     * @return list of matching chunks, sorted by relevance (best first)
     */
    List<RetrievedChunk> search(String collection, String query, int maxResults, double minScore);
}
