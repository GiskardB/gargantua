package ai.gargantua.core.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmRoutingContext")
class LlmRoutingContextTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        LocalTime time = LocalTime.of(14, 30);
        Map<String, String> attrs = Map.of("region", "eu-west");

        LlmRoutingContext ctx = new LlmRoutingContext(
                "user1", "sess1", "greeting", "general", "Hello world",
                11, 3, "premium", time, DayOfWeek.MONDAY, attrs
        );

        assertEquals("user1", ctx.userId());
        assertEquals("sess1", ctx.sessionId());
        assertEquals("greeting", ctx.skillName());
        assertEquals("general", ctx.skillDomain());
        assertEquals("Hello world", ctx.userMessage());
        assertEquals(11, ctx.inputLengthChars());
        assertEquals(3, ctx.estimatedInputTokens());
        assertEquals("premium", ctx.userTier());
        assertEquals(time, ctx.requestTime());
        assertEquals(DayOfWeek.MONDAY, ctx.requestDay());
        assertEquals("eu-west", ctx.attributes().get("region"));
    }

    @Test
    @DisplayName("free tier user with weekend request")
    void freeTierWeekend() {
        LlmRoutingContext ctx = new LlmRoutingContext(
                "u", "s", "sk", "d", "msg", 3, 1, "free",
                LocalTime.of(23, 59), DayOfWeek.SUNDAY, Map.of()
        );

        assertEquals("free", ctx.userTier());
        assertEquals(DayOfWeek.SUNDAY, ctx.requestDay());
        assertEquals(LocalTime.of(23, 59), ctx.requestTime());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        LocalTime time = LocalTime.NOON;
        LlmRoutingContext a = new LlmRoutingContext("u", "s", "sk", "d", "m", 1, 1, "t", time, DayOfWeek.FRIDAY, Map.of());
        LlmRoutingContext b = new LlmRoutingContext("u", "s", "sk", "d", "m", 1, 1, "t", time, DayOfWeek.FRIDAY, Map.of());
        LlmRoutingContext c = new LlmRoutingContext("u2", "s", "sk", "d", "m", 1, 1, "t", time, DayOfWeek.FRIDAY, Map.of());

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        LlmRoutingContext ctx = new LlmRoutingContext(null, null, null, null, null, 0, 0, null, null, null, null);
        assertNull(ctx.userId());
        assertNull(ctx.userTier());
        assertNull(ctx.requestTime());
        assertNull(ctx.requestDay());
        assertNull(ctx.attributes());
    }

    @Test
    @DisplayName("zero-length message")
    void zeroLengthMessage() {
        LlmRoutingContext ctx = new LlmRoutingContext("u", "s", "sk", "d", "", 0, 0, "free", LocalTime.MIDNIGHT, DayOfWeek.MONDAY, Map.of());
        assertEquals(0, ctx.inputLengthChars());
        assertEquals(0, ctx.estimatedInputTokens());
        assertEquals("", ctx.userMessage());
    }

    @Test
    @DisplayName("large input metrics")
    void largeInput() {
        LlmRoutingContext ctx = new LlmRoutingContext("u", "s", "sk", "d", "x".repeat(100000), 100000, 25000, "premium", LocalTime.NOON, DayOfWeek.WEDNESDAY, Map.of());
        assertEquals(100000, ctx.inputLengthChars());
        assertEquals(25000, ctx.estimatedInputTokens());
    }
}
