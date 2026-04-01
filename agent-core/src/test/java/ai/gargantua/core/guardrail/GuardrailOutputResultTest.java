package ai.gargantua.core.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuardrailOutputResult")
class GuardrailOutputResultTest {

    @Test
    @DisplayName("PASS verdict with unmodified response")
    void passVerdict() {
        GuardrailOutputResult result = new GuardrailOutputResult(
                GuardrailVerdict.PASS, "Hello world", null, "output-filter"
        );

        assertEquals(GuardrailVerdict.PASS, result.verdict());
        assertEquals("Hello world", result.processedResponse());
        assertNull(result.reason());
        assertEquals("output-filter", result.guardrailName());
    }

    @Test
    @DisplayName("BLOCK verdict with reason")
    void blockVerdict() {
        GuardrailOutputResult result = new GuardrailOutputResult(
                GuardrailVerdict.BLOCK, null, "Contains PII", "pii-scrubber"
        );

        assertEquals(GuardrailVerdict.BLOCK, result.verdict());
        assertNull(result.processedResponse());
        assertEquals("Contains PII", result.reason());
        assertEquals("pii-scrubber", result.guardrailName());
    }

    @Test
    @DisplayName("PASS verdict with transformed response and reason")
    void transformedResponse() {
        GuardrailOutputResult result = new GuardrailOutputResult(
                GuardrailVerdict.PASS, "Hello [REDACTED]", "Phone number redacted", "pii-scrubber"
        );

        assertEquals(GuardrailVerdict.PASS, result.verdict());
        assertEquals("Hello [REDACTED]", result.processedResponse());
        assertEquals("Phone number redacted", result.reason());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        GuardrailOutputResult a = new GuardrailOutputResult(GuardrailVerdict.PASS, "text", null, "g");
        GuardrailOutputResult b = new GuardrailOutputResult(GuardrailVerdict.PASS, "text", null, "g");
        GuardrailOutputResult c = new GuardrailOutputResult(GuardrailVerdict.BLOCK, "text", null, "g");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        GuardrailOutputResult result = new GuardrailOutputResult(null, null, null, null);
        assertNull(result.verdict());
        assertNull(result.processedResponse());
        assertNull(result.reason());
        assertNull(result.guardrailName());
    }

    @Test
    @DisplayName("empty processed response")
    void emptyResponse() {
        GuardrailOutputResult result = new GuardrailOutputResult(GuardrailVerdict.PASS, "", null, "g");
        assertEquals("", result.processedResponse());
    }
}
