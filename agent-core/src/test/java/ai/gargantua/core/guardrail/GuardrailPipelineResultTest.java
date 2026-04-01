package ai.gargantua.core.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuardrailPipelineResult")
class GuardrailPipelineResultTest {

    @Test
    @DisplayName("passed factory creates non-blocked result with null blockedBy and reason")
    void passedFactory() {
        GuardrailResult gr = GuardrailResult.pass("pii-check");
        GuardrailPipelineResult result = GuardrailPipelineResult.passed(List.of(gr));

        assertFalse(result.blocked());
        assertNull(result.blockedBy());
        assertNull(result.reason());
        assertEquals(1, result.results().size());
        assertEquals(gr, result.results().get(0));
    }

    @Test
    @DisplayName("blocked factory creates blocked result with guardrail name and reason")
    void blockedFactory() {
        GuardrailResult pass = GuardrailResult.pass("pii-check");
        GuardrailResult block = GuardrailResult.block("toxicity", "Harmful content detected");

        GuardrailPipelineResult result = GuardrailPipelineResult.blocked(
                "toxicity", "Harmful content detected", List.of(pass, block)
        );

        assertTrue(result.blocked());
        assertEquals("toxicity", result.blockedBy());
        assertEquals("Harmful content detected", result.reason());
        assertEquals(2, result.results().size());
    }

    @Test
    @DisplayName("passed with empty results list")
    void passedEmptyResults() {
        GuardrailPipelineResult result = GuardrailPipelineResult.passed(List.of());
        assertFalse(result.blocked());
        assertTrue(result.results().isEmpty());
    }

    @Test
    @DisplayName("blocked with empty results list (short-circuit before any ran)")
    void blockedEmptyResults() {
        GuardrailPipelineResult result = GuardrailPipelineResult.blocked("guard", "reason", List.of());
        assertTrue(result.blocked());
        assertTrue(result.results().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        GuardrailPipelineResult a = GuardrailPipelineResult.passed(List.of());
        GuardrailPipelineResult b = GuardrailPipelineResult.passed(List.of());
        GuardrailPipelineResult c = GuardrailPipelineResult.blocked("g", "r", List.of());

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("canonical constructor allows arbitrary values")
    void canonicalConstructor() {
        GuardrailPipelineResult result = new GuardrailPipelineResult(true, "custom", "custom reason", null);
        assertTrue(result.blocked());
        assertEquals("custom", result.blockedBy());
        assertNull(result.results());
    }

    @Test
    @DisplayName("multiple guardrail results preserved in order")
    void multipleResults() {
        GuardrailResult r1 = GuardrailResult.pass("g1");
        GuardrailResult r2 = GuardrailResult.warn("g2", "suspicious");
        GuardrailResult r3 = GuardrailResult.pass("g3");

        GuardrailPipelineResult result = GuardrailPipelineResult.passed(List.of(r1, r2, r3));
        assertEquals(3, result.results().size());
        assertEquals("g1", result.results().get(0).guardrailName());
        assertEquals("g3", result.results().get(2).guardrailName());
    }
}
