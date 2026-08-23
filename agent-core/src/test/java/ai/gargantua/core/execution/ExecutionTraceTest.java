package ai.gargantua.core.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Execution events")
class ExecutionTraceTest {

    private static ExecutionEvent evt(long seq, ExecutionEventType type) {
        return ExecutionEvent.of("trace-1", seq, Instant.EPOCH.plusSeconds(seq), type);
    }

    @Test
    @DisplayName("event validates required fields and copies attributes")
    void eventValidation() {
        assertThrows(IllegalArgumentException.class, () -> evtWithTrace(""));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionEvent(null, "t", -1, Instant.EPOCH, ExecutionEventType.ERROR,
                        null, null, null, null, Map.of(), null, null));

        ExecutionEvent e = new ExecutionEvent("id", "t", 0, Instant.EPOCH,
                ExecutionEventType.TOOL_CALLED, "agent", "sess", "main", "calling",
                Map.of("tool", "getPayment"), 12L, null);
        assertEquals("getPayment", e.attributes().get("tool"));
        assertThrows(UnsupportedOperationException.class, () -> e.attributes().put("x", 1));
        assertFalse(e.failed());
    }

    private static ExecutionEvent evtWithTrace(String traceId) {
        return ExecutionEvent.of(traceId, 0, Instant.EPOCH, ExecutionEventType.TURN_STARTED);
    }

    @Test
    @DisplayName("trace sorts events by sequence and exposes read helpers")
    void traceSortsAndQueries() {
        List<ExecutionEvent> unordered = new ArrayList<>(List.of(
                evt(2, ExecutionEventType.TURN_COMPLETED),
                evt(0, ExecutionEventType.TURN_STARTED),
                evt(1, ExecutionEventType.ERROR)));
        ExecutionTrace trace = new ExecutionTrace("trace-1", "agent", "sess", Instant.EPOCH, unordered);

        assertEquals(List.of(0L, 1L, 2L), trace.events().stream().map(ExecutionEvent::sequence).toList());
        assertEquals(ExecutionEventType.TURN_COMPLETED, trace.lastEvent().type());
        assertTrue(trace.hasError());
        assertEquals(1, trace.ofType(ExecutionEventType.ERROR).size());
        assertTrue(ExecutionTrace.empty("t").events().isEmpty());
    }

    @Test
    @DisplayName("noOp publisher accepts events without throwing")
    void noOpPublisher() {
        ExecutionEventPublisher publisher = ExecutionEventPublisher.noOp();
        assertDoesNotThrow(() -> publisher.publish(evt(0, ExecutionEventType.TURN_STARTED)));
    }

    @Test
    @DisplayName("a collecting publisher can rebuild a trace")
    void collectingPublisherBuildsTrace() {
        List<ExecutionEvent> collected = new ArrayList<>();
        ExecutionEventPublisher publisher = collected::add;
        publisher.publish(evt(0, ExecutionEventType.TURN_STARTED));
        publisher.publish(evt(1, ExecutionEventType.SKILL_SELECTED));
        publisher.publish(evt(2, ExecutionEventType.TURN_COMPLETED));

        ExecutionTrace trace = new ExecutionTrace("trace-1", null, null, null, collected);
        assertEquals(3, trace.events().size());
        assertEquals(ExecutionEventType.TURN_COMPLETED, trace.lastEvent().type());
    }
}
