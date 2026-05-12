package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the v1.2.11 binding-shape tolerance in {@link RoutingRuleEvaluator}:
 * every list-valued condition field must accept both the canonical
 * {@code List<?>} shape and the indexed-{@code Map<String,?>} shape that
 * Spring Boot 4's {@code @ConfigurationProperties} binder produces for
 * YAML lists nested inside a {@code Map<String,Object>}.
 */
class RoutingRuleEvaluatorTest {

    private final RoutingRuleEvaluator evaluator = new RoutingRuleEvaluator();

    private LlmRoutingContext ctx(String skill, String domain) {
        return new LlmRoutingContext(
                "u", "s", skill, domain, "hello TODO right here",
                20, 5, "premium",
                LocalTime.of(12, 0), DayOfWeek.SATURDAY,
                Map.of("x-priority", "urgent"));
    }

    private static LinkedHashMap<String, Object> indexed(Object... elements) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < elements.length; i++) {
            m.put(Integer.toString(i), elements[i]);
        }
        return m;
    }

    // ─── coerceList unit checks ─────────────────────────────────────────

    @Test
    @DisplayName("coerceList accepts a real List as-is")
    void coerceListAcceptsList() {
        List<Object> out = RoutingRuleEvaluator.coerceList(List.of("a", "b"));
        assertEquals(List.of("a", "b"), out);
    }

    @Test
    @DisplayName("coerceList reads an indexed Map in numeric-key order")
    void coerceListAcceptsIndexedMap() {
        var binderShape = new LinkedHashMap<String, Object>();
        binderShape.put("1", "b");
        binderShape.put("0", "a");
        binderShape.put("2", "c");
        List<Object> out = RoutingRuleEvaluator.coerceList(binderShape);
        assertEquals(List.of("a", "b", "c"), out, "Output must be ordered by integer key");
    }

    @Test
    @DisplayName("coerceList returns empty for null")
    void coerceListNullIsEmpty() {
        assertTrue(RoutingRuleEvaluator.coerceList(null).isEmpty());
    }

    @Test
    @DisplayName("coerceList wraps a scalar into a singleton list (QoL fallback)")
    void coerceListWrapsScalar() {
        assertEquals(List.of("TODO"), RoutingRuleEvaluator.coerceList("TODO"));
    }

    @Test
    @DisplayName("coerceList returns empty for a non-indexed Map (treated as 'not a list')")
    void coerceListNonIndexedMapIsEmpty() {
        var m = Map.<String, Object>of("from", "09:00", "to", "18:00");
        assertTrue(RoutingRuleEvaluator.coerceList(m).isEmpty());
    }

    // ─── End-to-end: every list-valued field with both shapes ──────────

    @Test
    @DisplayName("string IN works with both List values and indexed-Map values")
    void stringInOperatorBindingShapes() {
        var domainListShape = Map.<String, Object>of(
                "domain", Map.of("operator", "IN", "values", List.of("medical", "legal")));
        assertTrue(evaluator.matches(domainListShape, ctx("default-skill", "medical")));

        var domainMapShape = Map.<String, Object>of(
                "domain", Map.of("operator", "IN", "values", indexed("medical", "legal")));
        assertTrue(evaluator.matches(domainMapShape, ctx("default-skill", "medical")));
        assertFalse(evaluator.matches(domainMapShape, ctx("default-skill", "general")));
    }

    @Test
    @DisplayName("string NOT_IN works with indexed-Map values")
    void stringNotInOperatorIndexedMap() {
        var spec = Map.<String, Object>of(
                "domain", Map.of("operator", "NOT_IN", "values", indexed("general", "billing")));
        assertTrue(evaluator.matches(spec, ctx("default-skill", "robotics")));
        assertFalse(evaluator.matches(spec, ctx("default-skill", "general")));
    }

    @Test
    @DisplayName("day-of-week works with indexed-Map days")
    void dayOfWeekIndexedMap() {
        var spec = Map.<String, Object>of(
                "day-of-week", Map.of("days", indexed("SATURDAY", "SUNDAY")));
        assertTrue(evaluator.matches(spec, ctx("default-skill", "general")));
    }

    @Test
    @DisplayName("input-contains works with indexed-Map patterns")
    void inputContainsIndexedMap() {
        var spec = Map.<String, Object>of(
                "input-contains", Map.of("patterns", indexed("TODO", "FIXME")));
        assertTrue(evaluator.matches(spec, ctx("default-skill", "general")));
    }

    @Test
    @DisplayName("AND combinator works with both List arms and indexed-Map arms")
    void andCombinatorBindingShapes() {
        var conditionArms = List.of(
                Map.of("user-tier", Map.of("operator", "EQ", "value", "premium")),
                Map.of("input-contains", Map.of("patterns", List.of("TODO")))
        );
        var listShape = Map.<String, Object>of("AND", conditionArms);
        var indexedArms = indexed(conditionArms.get(0), conditionArms.get(1));
        var mapShape = Map.<String, Object>of("AND", indexedArms);

        assertTrue(evaluator.matches(listShape, ctx("default-skill", "general")));
        assertTrue(evaluator.matches(mapShape, ctx("default-skill", "general")));
    }

    @Test
    @DisplayName("OR combinator works with indexed-Map arms")
    void orCombinatorIndexedMap() {
        var arms = indexed(
                Map.of("skill", Map.of("operator", "EQ", "value", "metrics-skill")),
                Map.of("domain", Map.of("operator", "EQ", "value", "metrics"))
        );
        var spec = Map.<String, Object>of("OR", arms);
        assertTrue(evaluator.matches(spec, ctx("metrics-skill", "general")));
        assertTrue(evaluator.matches(spec, ctx("default-skill", "metrics")));
        assertFalse(evaluator.matches(spec, ctx("default-skill", "general")));
    }
}
