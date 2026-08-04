package ai.gargantua.core.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelSpec")
class ModelSpecTest {

    @Test
    @DisplayName("inherit leaves every setting unset")
    void inheritLeavesEverythingUnset() {
        ModelSpec spec = ModelSpec.inherit();

        assertNull(spec.primary());
        assertNull(spec.fallback());
        assertNull(spec.routing());
        assertNull(spec.temperature());
        assertNull(spec.maxTokens());
    }

    @Test
    @DisplayName("canonical constructor stores all fields")
    void canonicalConstructor() {
        ModelSpec spec = new ModelSpec("gpt-4o", "claude-sonnet", "phi4-mini", 0.7, 2000);

        assertEquals("gpt-4o", spec.primary());
        assertEquals("claude-sonnet", spec.fallback());
        assertEquals("phi4-mini", spec.routing());
        assertEquals(0.7, spec.temperature(), 0.0001);
        assertEquals(2000, spec.maxTokens().intValue());
    }

    @Test
    @DisplayName("temperature boundaries are accepted")
    void temperatureBoundariesAccepted() {
        assertDoesNotThrow(() -> new ModelSpec(null, null, null, 0.0, null));
        assertDoesNotThrow(() -> new ModelSpec(null, null, null, 2.0, null));
    }

    @Test
    @DisplayName("temperature above the allowed range is rejected")
    void temperatureTooHighRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec(null, null, null, 2.1, null));
    }

    @Test
    @DisplayName("negative temperature is rejected")
    void negativeTemperatureRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec(null, null, null, -0.1, null));
    }

    @Test
    @DisplayName("non-positive maxTokens is rejected")
    void nonPositiveMaxTokensRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec(null, null, null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec(null, null, null, null, -1));
    }
}
