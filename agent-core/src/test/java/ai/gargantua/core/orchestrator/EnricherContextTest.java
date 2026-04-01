package ai.gargantua.core.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EnricherContext")
class EnricherContextTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        Map<String, String> attrs = Map.of("tier", "premium", "lang", "en");
        EnricherContext ctx = new EnricherContext("u1", "sess1", "greeting", "general", "Hello!", attrs);

        assertEquals("u1", ctx.userId());
        assertEquals("sess1", ctx.sessionId());
        assertEquals("greeting", ctx.skillName());
        assertEquals("general", ctx.skillDomain());
        assertEquals("Hello!", ctx.userMessage());
        assertEquals(2, ctx.attributes().size());
        assertEquals("premium", ctx.attributes().get("tier"));
    }

    @Test
    @DisplayName("empty attributes map is valid")
    void emptyAttributes() {
        EnricherContext ctx = new EnricherContext("u1", "s1", "sk", "dom", "msg", Map.of());
        assertTrue(ctx.attributes().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        EnricherContext a = new EnricherContext("u1", "s1", "sk", "d", "m", Map.of());
        EnricherContext b = new EnricherContext("u1", "s1", "sk", "d", "m", Map.of());
        EnricherContext c = new EnricherContext("u2", "s1", "sk", "d", "m", Map.of());

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        EnricherContext ctx = new EnricherContext(null, null, null, null, null, null);
        assertNull(ctx.userId());
        assertNull(ctx.sessionId());
        assertNull(ctx.attributes());
    }
}
