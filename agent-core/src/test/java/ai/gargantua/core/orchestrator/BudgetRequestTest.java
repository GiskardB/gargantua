package ai.gargantua.core.orchestrator;

import ai.gargantua.core.memory.KnowledgeSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BudgetRequest")
class BudgetRequestTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        KnowledgeSegment seg = new KnowledgeSegment("u1", "prefs", "likes java", Instant.now(), "user");

        BudgetRequest req = new BudgetRequest(
                "You are a helpful assistant",
                "User is premium",
                List.of("ref1.md"),
                List.of("Previous session: discussed Java"),
                List.of(seg),
                List.of("searchTool: searches the web"),
                "What is Java?",
                8000
        );

        assertEquals("You are a helpful assistant", req.systemPrompt());
        assertEquals("User is premium", req.enrichedContext());
        assertEquals(1, req.references().size());
        assertEquals("ref1.md", req.references().get(0));
        assertEquals(1, req.episodicSummaries().size());
        assertEquals(1, req.knowledge().size());
        assertEquals(seg, req.knowledge().get(0));
        assertEquals(1, req.toolDescriptions().size());
        assertEquals("What is Java?", req.userMessage());
        assertEquals(8000, req.maxContextTokens());
    }

    @Test
    @DisplayName("empty lists for all collection fields")
    void emptyCollections() {
        BudgetRequest req = new BudgetRequest(
                "prompt", "", List.of(), List.of(), List.of(), List.of(), "msg", 4096
        );

        assertTrue(req.references().isEmpty());
        assertTrue(req.episodicSummaries().isEmpty());
        assertTrue(req.knowledge().isEmpty());
        assertTrue(req.toolDescriptions().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        BudgetRequest a = new BudgetRequest("p", "c", List.of(), List.of(), List.of(), List.of(), "m", 100);
        BudgetRequest b = new BudgetRequest("p", "c", List.of(), List.of(), List.of(), List.of(), "m", 100);
        BudgetRequest c = new BudgetRequest("p", "c", List.of(), List.of(), List.of(), List.of(), "m", 200);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields via canonical constructor")
    void nullFields() {
        BudgetRequest req = new BudgetRequest(null, null, null, null, null, null, null, 0);
        assertNull(req.systemPrompt());
        assertNull(req.enrichedContext());
        assertNull(req.references());
        assertNull(req.userMessage());
    }

    @Test
    @DisplayName("zero max context tokens is valid")
    void zeroMaxTokens() {
        BudgetRequest req = new BudgetRequest("p", "", List.of(), List.of(), List.of(), List.of(), "m", 0);
        assertEquals(0, req.maxContextTokens());
    }
}
