package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.rag.EmbeddingPort;
import ai.gargantua.core.rag.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins {@link EmbeddingInMemoryVectorStore} (v1.2.18+ — replaces the
 * legacy keyword-based store as the default embedded RAG engine).
 *
 * <p>Uses a deterministic {@link FixtureEmbeddingPort} that returns
 * pre-canned vectors per phrase, so the cosine math is verifiable
 * without loading a real embedding model.</p>
 */
class EmbeddingInMemoryVectorStoreTest {

    private static final float[] DOG    = { 1.0f,  0.0f, 0.0f };
    private static final float[] PUPPY  = { 0.95f, 0.05f, 0.0f }; // ≈ DOG
    private static final float[] CAR    = { 0.0f,  0.0f, 1.0f };
    private static final float[] OFFICE = { 0.0f,  1.0f, 0.0f };

    @Test
    @DisplayName("Search returns chunks ordered by cosine similarity, best first")
    void rankingByCosineSimilarity() {
        var port = new FixtureEmbeddingPort(Map.of(
                "loyal canine companion", DOG,
                "small dog energy",       PUPPY,
                "fast vehicle",           CAR,
                "open-plan workspace",    OFFICE
        ));
        var store = new EmbeddingInMemoryVectorStore(port);
        store.addChunk("kb", "loyal canine companion", "doc-A");
        store.addChunk("kb", "small dog energy",       "doc-B");
        store.addChunk("kb", "fast vehicle",           "doc-C");
        store.addChunk("kb", "open-plan workspace",    "doc-D");

        port.bind("dog query", DOG);
        List<RetrievedChunk> hits = store.search("kb", "dog query", 4, 0.0);

        assertEquals(4, hits.size());
        assertEquals("doc-A", hits.get(0).source(), "exact match must rank first");
        assertEquals("doc-B", hits.get(1).source(), "near match must rank second");
        // doc-C / doc-D both have cosine 0.0 with DOG → tied at the bottom.
        assertEquals(0.0, hits.get(3).score(), 1e-9);
    }

    @Test
    @DisplayName("minScore filters out low-similarity chunks")
    void minScoreFiltering() {
        var port = new FixtureEmbeddingPort(Map.of(
                "near",     PUPPY,
                "unrelated", CAR
        ));
        var store = new EmbeddingInMemoryVectorStore(port);
        store.addChunk("kb", "near", "near-doc");
        store.addChunk("kb", "unrelated", "far-doc");

        port.bind("query", DOG);
        List<RetrievedChunk> hits = store.search("kb", "query", 10, 0.5);

        assertEquals(1, hits.size(), "only the near chunk should pass minScore=0.5");
        assertEquals("near-doc", hits.get(0).source());
    }

    @Test
    @DisplayName("maxResults clamps the output even when more chunks pass the threshold")
    void maxResultsClampsOutput() {
        var port = new FixtureEmbeddingPort(Map.of(
                "a", DOG, "b", DOG, "c", DOG
        ));
        var store = new EmbeddingInMemoryVectorStore(port);
        store.addChunk("kb", "a", "doc-a");
        store.addChunk("kb", "b", "doc-b");
        store.addChunk("kb", "c", "doc-c");

        port.bind("q", DOG);
        List<RetrievedChunk> hits = store.search("kb", "q", 2, 0.0);

        assertEquals(2, hits.size());
    }

    @Test
    @DisplayName("Missing collection returns an empty list (no exception)")
    void missingCollectionReturnsEmpty() {
        var store = new EmbeddingInMemoryVectorStore(new FixtureEmbeddingPort(Map.of()));
        assertTrue(store.search("does-not-exist", "anything", 5, 0.0).isEmpty());
    }

    @Test
    @DisplayName("Blank query returns an empty list (avoid embedding the empty string)")
    void blankQueryReturnsEmpty() {
        var port = new FixtureEmbeddingPort(Map.of("a", DOG));
        var store = new EmbeddingInMemoryVectorStore(port);
        store.addChunk("kb", "a", "doc-a");
        assertTrue(store.search("kb", "   ", 5, 0.0).isEmpty());
        assertTrue(store.search("kb", null,   5, 0.0).isEmpty());
    }

    @Test
    @DisplayName("Cosine similarity unit case: identical vectors → 1.0; orthogonal → 0.0")
    void cosineSimilarityUnitCases() {
        assertEquals(1.0, EmbeddingInMemoryVectorStore.cosineSimilarity(DOG, DOG), 1e-9);
        assertEquals(0.0, EmbeddingInMemoryVectorStore.cosineSimilarity(DOG, OFFICE), 1e-9);
        assertEquals(0.0, EmbeddingInMemoryVectorStore.cosineSimilarity(DOG, CAR), 1e-9);
    }

    /** Embedding port for tests — returns pre-canned vectors per phrase. */
    static class FixtureEmbeddingPort implements EmbeddingPort {
        private final Map<String, float[]> table;
        FixtureEmbeddingPort(Map<String, float[]> initial) {
            this.table = new HashMap<>(initial);
        }
        void bind(String phrase, float[] vec) { table.put(phrase, vec); }

        @Override public float[] embed(String text) {
            float[] v = table.get(text);
            if (v == null) {
                throw new IllegalStateException("FixtureEmbeddingPort missing binding for: " + text);
            }
            return v;
        }
        @Override public int dimension() { return 3; }
    }
}
