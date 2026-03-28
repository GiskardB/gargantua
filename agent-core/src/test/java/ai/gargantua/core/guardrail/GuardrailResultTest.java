package ai.gargantua.core.guardrail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardrailResultTest {

    @Test
    void passFactoryMethod() {
        GuardrailResult result = GuardrailResult.pass("test");

        assertEquals(GuardrailVerdict.PASS, result.verdict());
        assertNull(result.reason());
        assertTrue(result.metadata().isEmpty());
        assertEquals("test", result.guardrailName());
    }

    @Test
    void blockFactoryMethodPreservesReason() {
        GuardrailResult result = GuardrailResult.block("test", "reason");

        assertEquals(GuardrailVerdict.BLOCK, result.verdict());
        assertEquals("reason", result.reason());
        assertEquals("test", result.guardrailName());
    }

    @Test
    void warnFactoryMethod() {
        GuardrailResult result = GuardrailResult.warn("test", "reason");

        assertEquals(GuardrailVerdict.WARN, result.verdict());
        assertEquals("reason", result.reason());
        assertEquals("test", result.guardrailName());
    }

    @Test
    void withMetadataIsImmutable() {
        GuardrailResult original = GuardrailResult.pass("test");
        GuardrailResult updated = original.withMetadata("key", "value");

        assertNotSame(original, updated);
        assertTrue(original.metadata().isEmpty());
        assertEquals("value", updated.metadata().get("key"));
    }
}
