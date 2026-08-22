package ai.gargantua.core.execution;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The ordered timeline of {@link ExecutionEvent}s for a single turn/request, keyed by
 * {@link #traceId()}. This is what the Studio's trace screen renders and what an
 * observability exporter turns into a span tree.
 *
 * <p>A pure aggregate: it holds events sorted by {@link ExecutionEvent#sequence()} and
 * offers small read helpers. Collection and transport are someone else's job (see
 * {@link ExecutionEventPublisher}).</p>
 *
 * @param traceId   correlation id shared by every event; required
 * @param agentId   the agent this trace belongs to, or {@code null}
 * @param sessionId the conversation session, or {@code null}
 * @param startedAt when the turn began, or {@code null} if unknown
 * @param events    the events, held sorted by sequence; never {@code null}
 */
public record ExecutionTrace(
        String traceId,
        String agentId,
        String sessionId,
        Instant startedAt,
        List<ExecutionEvent> events
) {

    public ExecutionTrace {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("ExecutionTrace.traceId is required");
        }
        events = events == null ? List.of()
                : events.stream().sorted(Comparator.comparingLong(ExecutionEvent::sequence)).toList();
    }

    /** An empty trace for the given id. */
    public static ExecutionTrace empty(String traceId) {
        return new ExecutionTrace(traceId, null, null, null, List.of());
    }

    /** Events of a given type, in order. */
    public List<ExecutionEvent> ofType(ExecutionEventType type) {
        return events.stream().filter(e -> e.type() == type).toList();
    }

    /** Whether any event in the trace is an error. */
    public boolean hasError() {
        return events.stream().anyMatch(ExecutionEvent::isError);
    }

    /** The last event by sequence, or {@code null} if the trace is empty. */
    public ExecutionEvent lastEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}
