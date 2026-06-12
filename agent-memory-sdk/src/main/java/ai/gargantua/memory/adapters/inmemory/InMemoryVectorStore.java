package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.rag.RetrievedChunk;
import ai.gargantua.core.rag.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector store using keyword matching for similarity.
 * NOT suitable for production — use a real vector database (pgvector, Qdrant, Milvus).
 * Useful for embedded mode, testing, and demos.
 *
 * <p>Similarity is computed using Jaccard index over lowercased word tokens:
 * {@code score = |intersection| / |union|}.</p>
 */
public class InMemoryVectorStore implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final ConcurrentHashMap<String, List<StoredChunk>> collections = new ConcurrentHashMap<>();

    private record StoredChunk(String content, String source, Set<String> tokens) {}

    /**
     * Add a document chunk to a collection. If the collection does not exist, it is created.
     *
     * @param collection the collection/index name
     * @param content    the text content of the chunk
     * @param source     identifier of the source document
     */
    public void addChunk(String collection, String content, String source) {
        var tokens = tokenize(content);
        var chunk = new StoredChunk(content, source, tokens);
        collections.computeIfAbsent(collection, k -> Collections.synchronizedList(new ArrayList<>()))
                   .add(chunk);
        log.debug("Added chunk to collection '{}' from source '{}' ({} tokens)", collection, source, tokens.size());
    }

    @Override
    public List<RetrievedChunk> search(String collection, String query, int maxResults, double minScore) {
        var chunks = collections.get(collection);
        if (chunks == null || chunks.isEmpty()) {
            log.debug("Collection '{}' not found or empty, returning empty results", collection);
            return List.of();
        }

        var queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<StoredChunk> snapshot;
        synchronized (chunks) {
            snapshot = new ArrayList<>(chunks);
        }

        return snapshot.stream()
                .map(chunk -> {
                    double score = jaccardSimilarity(queryTokens, chunk.tokens());
                    return new RetrievedChunk(chunk.content(), chunk.source(), score);
                })
                .filter(rc -> rc.score() >= minScore)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(maxResults)
                .toList();
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        var tokens = new HashSet<String>();
        for (var t : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!t.isBlank() && t.length() > 1) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        var intersection = new HashSet<>(a);
        intersection.retainAll(b);

        var union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }
}
