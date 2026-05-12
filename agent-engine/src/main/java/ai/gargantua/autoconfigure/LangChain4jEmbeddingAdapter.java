package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.EmbeddingPort;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.Objects;

/**
 * Adapts a LangChain4j {@link EmbeddingModel} as a Gargantua
 * {@link EmbeddingPort}. Lets users plug any LangChain4j-compatible model
 * (OpenAI, Cohere, Vertex AI, the in-process ONNX MiniLM, …) into the RAG
 * pipeline without writing a custom port implementation.
 *
 * <p>Registered as a fallback bean by
 * {@link RagAutoConfiguration} when no other {@link EmbeddingPort} is
 * supplied: it wraps the same {@code AllMiniLmL6V2QuantizedEmbeddingModel}
 * the semantic router already uses, so embedded apps get real
 * cosine-similarity RAG out of the box (no extra dependencies, zero
 * configuration).</p>
 *
 * @since v1.2.18
 */
public class LangChain4jEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel delegate;

    public LangChain4jEmbeddingAdapter(EmbeddingModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "EmbeddingModel must not be null");
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            // The semantic router treats the empty/blank query as "no match";
            // mirror that behaviour by returning a zero vector.
            return new float[dimension()];
        }
        return delegate.embed(text).content().vector();
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }
}
