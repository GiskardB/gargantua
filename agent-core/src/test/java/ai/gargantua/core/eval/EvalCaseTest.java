package ai.gargantua.core.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EvalCase")
class EvalCaseTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        EvalCase ec = new EvalCase(
                "tc-001", "Tests greeting behavior", "Hello",
                List.of("should greet", "should be polite"),
                List.of("should not be rude"),
                List.of("greeting", "basic")
        );

        assertEquals("tc-001", ec.id());
        assertEquals("Tests greeting behavior", ec.description());
        assertEquals("Hello", ec.input());
        assertEquals(2, ec.expectedBehaviors().size());
        assertEquals("should greet", ec.expectedBehaviors().get(0));
        assertEquals(1, ec.forbiddenBehaviors().size());
        assertEquals("should not be rude", ec.forbiddenBehaviors().get(0));
        assertEquals(2, ec.tags().size());
    }

    @Test
    @DisplayName("empty behavior and tag lists")
    void emptyLists() {
        EvalCase ec = new EvalCase("tc-002", "desc", "input", List.of(), List.of(), List.of());
        assertTrue(ec.expectedBehaviors().isEmpty());
        assertTrue(ec.forbiddenBehaviors().isEmpty());
        assertTrue(ec.tags().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        EvalCase a = new EvalCase("1", "d", "i", List.of(), List.of(), List.of());
        EvalCase b = new EvalCase("1", "d", "i", List.of(), List.of(), List.of());
        EvalCase c = new EvalCase("2", "d", "i", List.of(), List.of(), List.of());

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        EvalCase ec = new EvalCase(null, null, null, null, null, null);
        assertNull(ec.id());
        assertNull(ec.input());
        assertNull(ec.expectedBehaviors());
    }
}
