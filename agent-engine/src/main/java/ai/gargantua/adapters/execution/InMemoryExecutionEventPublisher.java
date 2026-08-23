package ai.gargantua.adapters.execution;

import ai.gargantua.core.execution.ExecutionEvent;
import ai.gargantua.core.execution.ExecutionEventPublisher;
import ai.gargantua.core.execution.ExecutionTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The zero-infrastructure {@link ExecutionEventPublisher}: it keeps the most recent traces
 * in a bounded in-memory buffer so the Studio trace screen and {@code /api/traces} can read
 * them back. This is the default sink in the runtime — no bus, no database. An OpenTelemetry
 * or message-bus publisher can replace it later without touching the engine, which only
 * depends on the port.
 *
 * <p>Bounded twice over: at most {@code maxTraces} traces are retained (oldest evicted), and
 * at most {@code maxEventsPerTrace} events per trace (excess dropped) — so a long-running or
 * runaway agent can't grow this without limit. Thread-safe: the engine may serve turns on
 * many virtual threads at once.</p>
 */
public class InMemoryExecutionEventPublisher implements ExecutionEventPublisher {

    private final int maxTraces;
    private final int maxEventsPerTrace;

    /** traceId → its events, insertion-ordered with oldest-trace eviction. */
    private final LinkedHashMap<String, List<ExecutionEvent>> traces;

    public InMemoryExecutionEventPublisher(int maxTraces, int maxEventsPerTrace) {
        this.maxTraces = maxTraces;
        this.maxEventsPerTrace = maxEventsPerTrace;
        this.traces = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<ExecutionEvent>> eldest) {
                return size() > InMemoryExecutionEventPublisher.this.maxTraces;
            }
        };
    }

    /** Sensible defaults: 200 traces, 500 events each. */
    public InMemoryExecutionEventPublisher() {
        this(200, 500);
    }

    @Override
    public void publish(ExecutionEvent event) {
        if (event == null) {
            return;
        }
        synchronized (traces) {
            List<ExecutionEvent> events = traces.computeIfAbsent(event.traceId(), k -> new ArrayList<>());
            if (events.size() < maxEventsPerTrace) {
                events.add(event);
            }
        }
    }

    /** The trace with this id, or {@code null} if it has been evicted or never existed. */
    public ExecutionTrace trace(String traceId) {
        synchronized (traces) {
            List<ExecutionEvent> events = traces.get(traceId);
            if (events == null) {
                return null;
            }
            return toTrace(traceId, events);
        }
    }

    /** The most recent traces, newest first, at most {@code limit}. */
    public List<ExecutionTrace> recentTraces(int limit) {
        synchronized (traces) {
            List<ExecutionTrace> all = new ArrayList<>(traces.size());
            for (var entry : traces.entrySet()) {
                all.add(toTrace(entry.getKey(), entry.getValue()));
            }
            Collections.reverse(all); // LinkedHashMap is oldest-first; newest-first for callers
            return limit > 0 && all.size() > limit ? all.subList(0, limit) : all;
        }
    }

    private static ExecutionTrace toTrace(String traceId, List<ExecutionEvent> events) {
        // Snapshot the list (caller holds the lock) and derive agent/session from the events.
        List<ExecutionEvent> snapshot = List.copyOf(events);
        String agentId = snapshot.stream().map(ExecutionEvent::agentId)
                .filter(a -> a != null).findFirst().orElse(null);
        String sessionId = snapshot.stream().map(ExecutionEvent::sessionId)
                .filter(s -> s != null).findFirst().orElse(null);
        var startedAt = snapshot.isEmpty() ? null : snapshot.get(0).timestamp();
        return new ExecutionTrace(traceId, agentId, sessionId, startedAt, snapshot);
    }
}
