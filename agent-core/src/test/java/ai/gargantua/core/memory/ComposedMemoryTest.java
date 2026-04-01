package ai.gargantua.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ComposedMemory")
class ComposedMemoryTest {

    @Test
    @DisplayName("stores all three memory layers and token estimate")
    void allFieldsAccessible() {
        ChatMessage msg = ChatMessage.userMessage("hi");
        SessionSummary summary = new SessionSummary("u1", "s1", "summary",
                List.of("topic"), List.of(), 5, Instant.now());
        KnowledgeSegment seg = new KnowledgeSegment("u1", "prefs", "likes java", Instant.now(), "user");

        ComposedMemory mem = new ComposedMemory(
                List.of(msg),
                List.of(summary),
                List.of(seg),
                1500
        );

        assertEquals(1, mem.workingMessages().size());
        assertEquals(msg, mem.workingMessages().get(0));
        assertEquals(1, mem.episodicSummaries().size());
        assertEquals(summary, mem.episodicSummaries().get(0));
        assertEquals(1, mem.knowledgeSegments().size());
        assertEquals(seg, mem.knowledgeSegments().get(0));
        assertEquals(1500, mem.estimatedTokens());
    }

    @Test
    @DisplayName("accepts empty lists for all layers")
    void emptyLayers() {
        ComposedMemory mem = new ComposedMemory(List.of(), List.of(), List.of(), 0);

        assertTrue(mem.workingMessages().isEmpty());
        assertTrue(mem.episodicSummaries().isEmpty());
        assertTrue(mem.knowledgeSegments().isEmpty());
        assertEquals(0, mem.estimatedTokens());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        ComposedMemory a = new ComposedMemory(List.of(), List.of(), List.of(), 100);
        ComposedMemory b = new ComposedMemory(List.of(), List.of(), List.of(), 100);
        ComposedMemory c = new ComposedMemory(List.of(), List.of(), List.of(), 200);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null lists via canonical constructor")
    void nullLists() {
        ComposedMemory mem = new ComposedMemory(null, null, null, 0);
        assertNull(mem.workingMessages());
        assertNull(mem.episodicSummaries());
        assertNull(mem.knowledgeSegments());
    }

    @Test
    @DisplayName("negative token estimate is permitted by the record")
    void negativeTokens() {
        ComposedMemory mem = new ComposedMemory(List.of(), List.of(), List.of(), -1);
        assertEquals(-1, mem.estimatedTokens());
    }
}
