package ai.gargantua.core.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CostTrackingEvent")
class CostTrackingEventTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        CostTrackingEvent event = new CostTrackingEvent(
                "user1", "sess1", "greeting", "openai", "gpt-4o",
                "main", 500, 200, 1200L, false
        );

        assertEquals("user1", event.userId());
        assertEquals("sess1", event.sessionId());
        assertEquals("greeting", event.skillName());
        assertEquals("openai", event.provider());
        assertEquals("gpt-4o", event.model());
        assertEquals("main", event.phase());
        assertEquals(500, event.inputTokens());
        assertEquals(200, event.outputTokens());
        assertEquals(1200L, event.durationMs());
        assertFalse(event.dryRun());
    }

    @Test
    @DisplayName("dry-run event")
    void dryRunEvent() {
        CostTrackingEvent event = new CostTrackingEvent(
                "u", "s", "sk", "anthropic", "claude-3", "routing", 10, 5, 50L, true
        );
        assertTrue(event.dryRun());
        assertEquals("routing", event.phase());
    }

    @Test
    @DisplayName("eval phase event")
    void evalPhase() {
        CostTrackingEvent event = new CostTrackingEvent(
                "u", "s", "sk", "openai", "gpt-4o-mini", "eval", 100, 50, 300L, false
        );
        assertEquals("eval", event.phase());
    }

    @Test
    @DisplayName("zero tokens and duration")
    void zeroValues() {
        CostTrackingEvent event = new CostTrackingEvent("u", "s", "sk", "p", "m", "main", 0, 0, 0L, false);
        assertEquals(0, event.inputTokens());
        assertEquals(0, event.outputTokens());
        assertEquals(0L, event.durationMs());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        CostTrackingEvent a = new CostTrackingEvent("u", "s", "sk", "p", "m", "main", 10, 5, 100L, false);
        CostTrackingEvent b = new CostTrackingEvent("u", "s", "sk", "p", "m", "main", 10, 5, 100L, false);
        CostTrackingEvent c = new CostTrackingEvent("u2", "s", "sk", "p", "m", "main", 10, 5, 100L, false);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null string fields")
    void nullFields() {
        CostTrackingEvent event = new CostTrackingEvent(null, null, null, null, null, null, 0, 0, 0L, false);
        assertNull(event.userId());
        assertNull(event.provider());
        assertNull(event.model());
    }
}
