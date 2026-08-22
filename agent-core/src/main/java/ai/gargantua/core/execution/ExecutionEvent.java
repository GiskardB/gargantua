package ai.gargantua.core.execution;

import java.time.Instant;
import java.util.Map;

/**
 * A single, fine-grained event in an agent's execution timeline — one step of one turn.
 * Many of these, sharing a {@link #traceId()}, form an {@link ExecutionTrace}.
 *
 * <p>The shape is intentionally OpenTelemetry-friendly: a typed {@link #type()} plus a
 * free-form {@link #attributes()} map, so the same event can be logged, streamed, rendered
 * in the Studio trace screen, or exported as an OTel span without reshaping. It is a pure
 * value: no transport, no timing side effects — the caller supplies the timestamp so the
 * domain stays deterministic and the module free of clock/UUID dependencies.</p>
 *
 * @param eventId    unique id for this event, or {@code null} to let the sink assign one
 * @param traceId    correlates every event of one turn/request; required
 * @param sequence   monotonic 0-based order of this event within its trace
 * @param timestamp  when the event occurred; required
 * @param type       what happened; required
 * @param agentId    the agent producing the event, or {@code null}
 * @param sessionId  the conversation session, or {@code null}
 * @param phase      pipeline phase ("main", "routing", "summarizer"), or {@code null}
 *                   — aligned with {@link ai.gargantua.core.cost.CostTrackingEvent}
 * @param message    short human-readable label, or {@code null}
 * @param attributes structured detail (tool name, skill, tokens, verdict…); never {@code null}
 * @param durationMs wall-clock duration for span-like events, or {@code null} for instants
 * @param error      error message when {@code type == ERROR}, else {@code null}
 */
public record ExecutionEvent(
        String eventId,
        String traceId,
        long sequence,
        Instant timestamp,
        ExecutionEventType type,
        String agentId,
        String sessionId,
        String phase,
        String message,
        Map<String, Object> attributes,
        Long durationMs,
        String error
) {

    public ExecutionEvent {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("ExecutionEvent.traceId is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("ExecutionEvent.timestamp is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("ExecutionEvent.type is required");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("ExecutionEvent.sequence must be >= 0");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Minimal event: a trace, an order, a time and a type. */
    public static ExecutionEvent of(String traceId, long sequence, Instant timestamp,
                                    ExecutionEventType type) {
        return new ExecutionEvent(null, traceId, sequence, timestamp, type,
                null, null, null, null, Map.of(), null, null);
    }

    /** Whether this event represents a failure. */
    public boolean isError() {
        return type == ExecutionEventType.ERROR;
    }
}
