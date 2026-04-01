package ai.gargantua.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionSummary")
class SessionSummaryTest {

    @Test
    @DisplayName("canonical constructor stores all fields including expiresAt")
    void canonicalConstructor() {
        Instant sessionDate = Instant.parse("2024-06-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2024-12-01T10:00:00Z");

        SessionSummary s = new SessionSummary("u1", "sess1", "A conversation about Java",
                List.of("java", "programming"), List.of("setup IDE"), 12, sessionDate, expiresAt);

        assertEquals("u1", s.userId());
        assertEquals("sess1", s.sessionId());
        assertEquals("A conversation about Java", s.summary());
        assertEquals(List.of("java", "programming"), s.keyTopics());
        assertEquals(List.of("setup IDE"), s.unresolvedItems());
        assertEquals(12, s.messageCount());
        assertEquals(sessionDate, s.sessionDate());
        assertEquals(expiresAt, s.expiresAt());
    }

    @Test
    @DisplayName("convenience constructor defaults expiresAt to null")
    void convenienceConstructorNullExpiry() {
        Instant sessionDate = Instant.now();
        SessionSummary s = new SessionSummary("u1", "sess1", "summary",
                List.of(), List.of(), 3, sessionDate);

        assertNull(s.expiresAt());
        assertEquals("u1", s.userId());
        assertEquals(3, s.messageCount());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        SessionSummary a = new SessionSummary("u1", "s1", "sum", List.of(), List.of(), 5, ts);
        SessionSummary b = new SessionSummary("u1", "s1", "sum", List.of(), List.of(), 5, ts);
        SessionSummary c = new SessionSummary("u2", "s1", "sum", List.of(), List.of(), 5, ts);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("empty lists for topics and unresolved items")
    void emptyLists() {
        SessionSummary s = new SessionSummary("u1", "s1", "sum", List.of(), List.of(), 0, Instant.now());
        assertTrue(s.keyTopics().isEmpty());
        assertTrue(s.unresolvedItems().isEmpty());
    }

    @Test
    @DisplayName("zero message count is valid")
    void zeroMessageCount() {
        SessionSummary s = new SessionSummary("u1", "s1", "empty", List.of(), List.of(), 0, Instant.now());
        assertEquals(0, s.messageCount());
    }

    @Test
    @DisplayName("allows null fields for nullable positions")
    void nullFields() {
        SessionSummary s = new SessionSummary(null, null, null, null, null, 0, null, null);
        assertNull(s.userId());
        assertNull(s.sessionId());
        assertNull(s.summary());
        assertNull(s.keyTopics());
        assertNull(s.unresolvedItems());
        assertNull(s.sessionDate());
        assertNull(s.expiresAt());
    }
}
