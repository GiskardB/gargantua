package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.rag.EmbeddingPort;
import ai.gargantua.core.rag.RetrievedChunk;
import ai.gargantua.core.rag.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link VectorStorePort} that uses a real {@link EmbeddingPort}
 * to embed both stored chunks and queries, and ranks results with cosine
 * similarity. This is the embedded-mode default since v1.2.18 — replaces
 * the older keyword-based {@link InMemoryVectorStore} (still available
 * for tests and demos that want to avoid loading an embedding model).
 *
 * <p>Suitable for development, tests, demos and single-node deployments
 * with up to a few thousand chunks. For production-grade scale + persistence,
 * implement your own {@link VectorStorePort} backed by pgvector, Qdrant,
 * Milvus, etc.</p>
 *
 * @since v1.2.18
 */
public class EmbeddingInMemoryVectorStore implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingInMemoryVectorStore.class);

    private final ConcurrentHashMap<String, List<StoredChunk>> collections = new ConcurrentHashMap<>();
    private final EmbeddingPort embeddingPort;

    private record StoredChunk(String content, String source, float[] embedding) {}

    public EmbeddingInMemoryVectorStore(EmbeddingPort embeddingPort) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "EmbeddingPort must not be null");
    }

    /**
     * Add a document chunk to a collection. The chunk is embedded eagerly so
     * subsequent queries don't pay the embedding cost again.
     */
    public void addChunk(String collection, String content, String source) {
        if (content == null || content.isBlank()) {
            return;
        }
        float[] embedding = embeddingPort.embed(content);
        var chunk = new StoredChunk(content, source, embedding);
        collections.computeIfAbsent(collection, k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(chunk);
        log.debug("Indexed chunk in collection '{}' from source '{}' (dim={})",
                collection, source, embedding.length);
    }

    @Override
    public List<RetrievedChunk> search(String collection, String query, int maxResults, double minScore) {
        var chunks = collections.get(collection);
        if (chunks == null || chunks.isEmpty()) {
            log.debug("Collection '{}' not found or empty, returning empty results", collection);
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        float[] queryEmbedding = embeddingPort.embed(query);

        List<StoredChunk> snapshot;
        synchronized (chunks) {
            snapshot = new ArrayList<>(chunks);
        }

        return snapshot.stream()
                .map(chunk -> new RetrievedChunk(
                        chunk.content(),
                        chunk.source(),
                        cosineSimilarity(queryEmbedding, chunk.embedding())))
                .filter(rc -> rc.score() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(maxResults)
                .toList();
    }

    /**
     * Cosine similarity between two equal-length vectors. Returns {@code 0.0}
     * when either vector has zero magnitude (guards against the empty-string
     * embedding path).
     */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
