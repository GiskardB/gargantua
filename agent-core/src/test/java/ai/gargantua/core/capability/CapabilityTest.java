package ai.gargantua.core.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Capability")
class CapabilityTest {

    @Test
    @DisplayName("canonical constructor stores all fields")
    void canonicalConstructor() {
        Capability capability = new Capability("refund-payment", "Handles refunds", "1.0.0",
                "{\"type\":\"object\"}", "{\"type\":\"string\"}", "refund-skill", Set.of("payments"));

        assertEquals("refund-payment", capability.name());
        assertEquals("Handles refunds", capability.description());
        assertEquals("1.0.0", capability.version());
        assertEquals("refund-skill", capability.implementedBy());
        assertTrue(capability.tags().contains("payments"));
    }

    @Test
    @DisplayName("convenience constructor produces a free-form capability")
    void convenienceConstructor() {
        Capability capability = new Capability("refund-payment", "Handles refunds", "1.0.0");

        assertNull(capability.inputSchema());
        assertNull(capability.outputSchema());
        assertNull(capability.implementedBy());
        assertTrue(capability.tags().isEmpty());
    }

    @Test
    @DisplayName("null tags default to an empty set")
    void nullTagsDefaultToEmpty() {
        Capability capability = new Capability("c", "d", "1.0.0", null, null, null, null);
        assertTrue(capability.tags().isEmpty());
    }

    @Test
    @DisplayName("tags are defensively copied and immutable")
    void tagsAreImmutable() {
        Set<String> mutable = new HashSet<>();
        mutable.add("payments");
        Capability capability = new Capability("c", "d", "1.0.0", null, null, null, mutable);

        mutable.add("added-after");

        assertEquals(1, capability.tags().size());
        assertThrows(UnsupportedOperationException.class, () -> capability.tags().add("x"));
    }

    @Test
    @DisplayName("hasSchema is false when neither schema is declared")
    void hasSchemaFalseWithoutSchemas() {
        assertFalse(new Capability("c", "d", "1.0.0").hasSchema());
    }

    @Test
    @DisplayName("hasSchema is false for blank schemas")
    void hasSchemaFalseForBlank() {
        assertFalse(new Capability("c", "d", "1.0.0", "  ", "", null, null).hasSchema());
    }

    @Test
    @DisplayName("hasSchema is true when only the input schema is declared")
    void hasSchemaTrueWithInputOnly() {
        assertTrue(new Capability("c", "d", "1.0.0", "{}", null, null, null).hasSchema());
    }

    @Test
    @DisplayName("hasSchema is true when only the output schema is declared")
    void hasSchemaTrueWithOutputOnly() {
        assertTrue(new Capability("c", "d", "1.0.0", null, "{}", null, null).hasSchema());
    }

    @Test
    @DisplayName("record equality is based on all fields")
    void equality() {
        Capability a = new Capability("c", "d", "1.0.0");
        Capability b = new Capability("c", "d", "1.0.0");
        Capability c = new Capability("c", "d", "2.0.0");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
