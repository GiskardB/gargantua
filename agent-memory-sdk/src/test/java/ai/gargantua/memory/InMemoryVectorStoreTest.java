package ai.gargantua.memory;

import ai.gargantua.core.rag.RetrievedChunk;
import ai.gargantua.memory.adapters.inmemory.InMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
    }

    @Test
    void shouldReturnRelevantChunks() {
        store.addChunk("hr-docs", "Employee vacation policy allows 20 days per year", "vacation-policy.pdf");
        store.addChunk("hr-docs", "Health insurance covers dental and vision", "benefits-guide.pdf");
        store.addChunk("hr-docs", "Remote work policy requires VPN connection", "remote-work.pdf");

        List<RetrievedChunk> results = store.search("hr-docs", "vacation days policy", 5, 0.0);

        assertFalse(results.isEmpty(), "Should return at least one relevant chunk");
        // The vacation policy chunk should be the most relevant
        assertEquals("vacation-policy.pdf", results.get(0).source());
        assertTrue(results.get(0).score() > 0.0, "Score should be positive");
    }

    @Test
    void shouldReturnEmptyForUnknownCollection() {
        List<RetrievedChunk> results = store.search("non-existent", "some query", 5, 0.0);

        assertTrue(results.isEmpty(), "Should return empty list for unknown collection");
    }

    @Test
    void shouldRespectMaxResults() {
        for (int i = 0; i < 10; i++) {
            store.addChunk("docs", "document about java programming topic " + i, "doc-" + i + ".txt");
        }

        List<RetrievedChunk> results = store.search("docs", "java programming document topic", 3, 0.0);

        assertTrue(results.size() <= 3, "Should not exceed maxResults limit");
    }

    @Test
    void shouldRespectMinScore() {
        store.addChunk("docs", "employee vacation policy allows twenty days", "vacation.pdf");
        store.addChunk("docs", "completely unrelated content about quantum physics", "physics.pdf");

        List<RetrievedChunk> results = store.search("docs", "vacation policy", 5, 0.5);

        for (RetrievedChunk chunk : results) {
            assertTrue(chunk.score() >= 0.5, "All results should meet minimum score threshold");
        }
    }

    @Test
    void shouldReturnResultsSortedByRelevance() {
        store.addChunk("docs", "java programming language features", "java.txt");
        store.addChunk("docs", "java programming best practices and design patterns", "java-best.txt");
        store.addChunk("docs", "python programming language basics", "python.txt");

        List<RetrievedChunk> results = store.search("docs", "java programming", 5, 0.0);

        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).score() >= results.get(i + 1).score(),
                    "Results should be sorted by score descending");
        }
    }
}
