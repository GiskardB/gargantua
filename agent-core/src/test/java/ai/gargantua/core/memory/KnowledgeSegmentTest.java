package ai.gargantua.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KnowledgeSegment")
class KnowledgeSegmentTest {

    @Test
    @DisplayName("all fields are accessible via accessors")
    void allFieldsAccessible() {
        Instant now = Instant.now();
        KnowledgeSegment seg = new KnowledgeSegment("user1", "preferences", "likes dark mode", now, "user");

        assertEquals("user1", seg.userId());
        assertEquals("preferences", seg.segmentKey());
        assertEquals("likes dark mode", seg.content());
        assertEquals(now, seg.updatedAt());
        assertEquals("user", seg.source());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        KnowledgeSegment a = new KnowledgeSegment("u1", "key", "content", ts, "agent");
        KnowledgeSegment b = new KnowledgeSegment("u1", "key", "content", ts, "agent");
        KnowledgeSegment c = new KnowledgeSegment("u2", "key", "content", ts, "agent");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields via canonical constructor")
    void nullFields() {
        KnowledgeSegment seg = new KnowledgeSegment(null, null, null, null, null);
        assertNull(seg.userId());
        assertNull(seg.segmentKey());
        assertNull(seg.content());
        assertNull(seg.updatedAt());
        assertNull(seg.source());
    }

    @Test
    @DisplayName("empty string content is valid")
    void emptyContent() {
        KnowledgeSegment seg = new KnowledgeSegment("u1", "key", "", Instant.now(), "user");
        assertEquals("", seg.content());
    }

    @Test
    @DisplayName("toString includes all field values")
    void toStringContainsFields() {
        KnowledgeSegment seg = new KnowledgeSegment("u1", "prefs", "data", Instant.parse("2024-01-01T00:00:00Z"), "admin");
        String str = seg.toString();
        assertTrue(str.contains("u1"));
        assertTrue(str.contains("prefs"));
        assertTrue(str.contains("data"));
        assertTrue(str.contains("admin"));
    }
}
