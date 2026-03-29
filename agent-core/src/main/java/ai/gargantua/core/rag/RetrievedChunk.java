package ai.gargantua.core.rag;

/**
 * A chunk of text retrieved from a vector store, ranked by similarity to the query.
 *
 * @param content the text content of the chunk
 * @param source identifier of the source document (e.g., filename, URL, document ID)
 * @param score similarity score (0.0-1.0, higher is more relevant)
 */
public record RetrievedChunk(
    String content,
    String source,
    double score
) {}
