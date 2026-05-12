package ai.gargantua.core.rag;

/**
 * Port for embedding text into a dense vector representation. Implementations
 * wrap a specific embedding model (OpenAI {@code text-embedding-3-*}, Cohere,
 * Vertex AI, an in-process ONNX model, …) so the rest of the framework stays
 * model-agnostic.
 *
 * <p>The framework auto-wires {@link EmbeddingPort} into the in-memory vector
 * store when a {@code VectorStorePort} bean is not explicitly provided. For
 * production, your own {@code VectorStorePort} adapter (pgvector, Qdrant,
 * Milvus, …) is expected to take an {@link EmbeddingPort} too — see the
 * {@code agent-example-rag} per-feature example.</p>
 *
 * @since v1.2.18
 */
public interface EmbeddingPort {

    /**
     * Generate the embedding for a single piece of text.
     *
     * @param text the input string (must not be {@code null} or blank)
     * @return a dense embedding vector. The dimension is implementation-defined
     *         (e.g. 384 for {@code all-MiniLM-L6-v2}, 1536 for
     *         {@code text-embedding-3-small}). Implementations must return
     *         vectors of consistent dimension across calls.
     */
    float[] embed(String text);

    /**
     * Convenience: dimension of the embedding vectors returned by
     * {@link #embed(String)}. Used by storage adapters to size database
     * columns / index parameters at startup.
     */
    int dimension();
}
