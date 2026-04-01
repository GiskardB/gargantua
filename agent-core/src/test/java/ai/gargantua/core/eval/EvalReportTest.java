package ai.gargantua.core.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EvalReport")
class EvalReportTest {

    private EvalResult sampleResult(EvalVerdict verdict, double score) {
        return new EvalResult("c1", "desc", "input", "response", List.of(),
                verdict, score, "reasoning", List.of(), List.of(), 100L);
    }

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        Instant runAt = Instant.parse("2024-06-15T12:00:00Z");
        EvalResult result = sampleResult(EvalVerdict.PASS, 1.0);
        EvalComparison comp = new EvalComparison(0.8, 0.1, "2024-06-14T12:00:00Z");

        EvalReport report = new EvalReport(
                "greeting", "1.0.0", runAt, 3, 2, 1, 0, 0.85,
                List.of(result), comp
        );

        assertEquals("greeting", report.skillName());
        assertEquals("1.0.0", report.skillVersion());
        assertEquals(runAt, report.runAt());
        assertEquals(3, report.totalCases());
        assertEquals(2, report.passed());
        assertEquals(1, report.failed());
        assertEquals(0, report.partial());
        assertEquals(0.85, report.overallScore(), 0.001);
        assertEquals(1, report.results().size());
        assertNotNull(report.comparison());
        assertEquals(0.1, report.comparison().scoreDelta(), 0.001);
    }

    @Test
    @DisplayName("null comparison when no previous run exists")
    void nullComparison() {
        EvalReport report = new EvalReport("sk", "1.0", Instant.now(), 1, 1, 0, 0, 1.0, List.of(), null);
        assertNull(report.comparison());
    }

    @Test
    @DisplayName("all-fail scenario")
    void allFail() {
        EvalResult fail = sampleResult(EvalVerdict.FAIL, 0.0);
        EvalReport report = new EvalReport("sk", "1.0", Instant.now(), 2, 0, 2, 0, 0.0,
                List.of(fail, fail), null);

        assertEquals(0, report.passed());
        assertEquals(2, report.failed());
        assertEquals(0.0, report.overallScore(), 0.001);
    }

    @Test
    @DisplayName("partial results")
    void partialResults() {
        EvalResult partial = sampleResult(EvalVerdict.PARTIAL, 0.5);
        EvalReport report = new EvalReport("sk", "1.0", Instant.now(), 1, 0, 0, 1, 0.5,
                List.of(partial), null);

        assertEquals(1, report.partial());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        EvalReport a = new EvalReport("sk", "1.0", ts, 0, 0, 0, 0, 0.0, List.of(), null);
        EvalReport b = new EvalReport("sk", "1.0", ts, 0, 0, 0, 0, 0.0, List.of(), null);
        EvalReport c = new EvalReport("sk2", "1.0", ts, 0, 0, 0, 0, 0.0, List.of(), null);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
