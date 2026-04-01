package ai.gargantua.core.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EvalResult")
class EvalResultTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        EvalResult r = new EvalResult(
                "c1", "greeting test", "Hello",
                "Hi there! How can I help?",
                List.of("searchTool"),
                EvalVerdict.PASS, 1.0,
                "Response contains greeting",
                List.of("greets user"),
                List.of(),
                150L
        );

        assertEquals("c1", r.caseId());
        assertEquals("greeting test", r.description());
        assertEquals("Hello", r.input());
        assertEquals("Hi there! How can I help?", r.actualResponse());
        assertEquals(List.of("searchTool"), r.toolsCalled());
        assertEquals(EvalVerdict.PASS, r.verdict());
        assertEquals(1.0, r.score(), 0.001);
        assertEquals("Response contains greeting", r.judgeReasoning());
        assertEquals(List.of("greets user"), r.passedBehaviors());
        assertTrue(r.failedBehaviors().isEmpty());
        assertEquals(150L, r.durationMs());
    }

    @Test
    @DisplayName("FAIL verdict with failed behaviors")
    void failVerdict() {
        EvalResult r = new EvalResult(
                "c2", "fail case", "input", "bad response", List.of(),
                EvalVerdict.FAIL, 0.0, "Missing expected behavior",
                List.of(), List.of("should greet user"), 200L
        );

        assertEquals(EvalVerdict.FAIL, r.verdict());
        assertEquals(0.0, r.score(), 0.001);
        assertEquals(1, r.failedBehaviors().size());
        assertTrue(r.passedBehaviors().isEmpty());
    }

    @Test
    @DisplayName("PARTIAL verdict with mixed behaviors")
    void partialVerdict() {
        EvalResult r = new EvalResult(
                "c3", "partial", "input", "response", List.of(),
                EvalVerdict.PARTIAL, 0.5, "Some behaviors found",
                List.of("found A"), List.of("missing B"), 300L
        );

        assertEquals(EvalVerdict.PARTIAL, r.verdict());
        assertEquals(0.5, r.score(), 0.001);
        assertEquals(1, r.passedBehaviors().size());
        assertEquals(1, r.failedBehaviors().size());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        EvalResult a = new EvalResult("c", "d", "i", "r", List.of(), EvalVerdict.PASS, 1.0, "j", List.of(), List.of(), 10L);
        EvalResult b = new EvalResult("c", "d", "i", "r", List.of(), EvalVerdict.PASS, 1.0, "j", List.of(), List.of(), 10L);
        EvalResult c = new EvalResult("c2", "d", "i", "r", List.of(), EvalVerdict.PASS, 1.0, "j", List.of(), List.of(), 10L);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("multiple tools called preserved in order")
    void multipleTools() {
        EvalResult r = new EvalResult("c", "d", "i", "r", List.of("t1", "t2", "t3"),
                EvalVerdict.PASS, 1.0, "j", List.of(), List.of(), 10L);
        assertEquals(3, r.toolsCalled().size());
        assertEquals("t1", r.toolsCalled().get(0));
        assertEquals("t3", r.toolsCalled().get(2));
    }

    @Test
    @DisplayName("allows null fields")
    void nullFields() {
        EvalResult r = new EvalResult(null, null, null, null, null, null, 0.0, null, null, null, 0L);
        assertNull(r.caseId());
        assertNull(r.verdict());
        assertNull(r.toolsCalled());
    }
}
