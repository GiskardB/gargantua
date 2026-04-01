package ai.gargantua.core.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetrievedChunk")
class RetrievedChunkTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        RetrievedChunk chunk = new RetrievedChunk("Java is a programming language.", "java-tutorial.md", 0.92);

        assertEquals("Java is a programming language.", chunk.content());
        assertEquals("java-tutorial.md", chunk.source());
        assertEquals(0.92, chunk.score(), 0.001);
    }

    @Test
    @DisplayName("perfect score of 1.0")
    void perfectScore() {
        RetrievedChunk chunk = new RetrievedChunk("exact match", "doc.txt", 1.0);
        assertEquals(1.0, chunk.score(), 0.001);
    }

    @Test
    @DisplayName("zero score")
    void zeroScore() {
        RetrievedChunk chunk = new RetrievedChunk("irrelevant", "doc.txt", 0.0);
        assertEquals(0.0, chunk.score(), 0.001);
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        RetrievedChunk a = new RetrievedChunk("text", "src", 0.5);
        RetrievedChunk b = new RetrievedChunk("text", "src", 0.5);
        RetrievedChunk c = new RetrievedChunk("text", "src", 0.6);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null content and source")
    void nullFields() {
        RetrievedChunk chunk = new RetrievedChunk(null, null, 0.0);
        assertNull(chunk.content());
        assertNull(chunk.source());
    }

    @Test
    @DisplayName("empty content string")
    void emptyContent() {
        RetrievedChunk chunk = new RetrievedChunk("", "src", 0.5);
        assertEquals("", chunk.content());
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        RetrievedChunk chunk = new RetrievedChunk("content", "source.md", 0.75);
        String str = chunk.toString();
        assertTrue(str.contains("content"));
        assertTrue(str.contains("source.md"));
        assertTrue(str.contains("0.75"));
    }
}
