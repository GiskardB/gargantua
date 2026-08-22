package ai.gargantua.core.execution;

/**
 * Port for emitting {@link ExecutionEvent}s as an agent runs. Implementations decide the
 * transport — an in-memory buffer for the Studio trace screen, an OpenTelemetry exporter,
 * or a message bus — while the engine depends only on this interface.
 *
 * <p>This is deliberately the single seam for execution events: the choice of bus
 * (NATS vs. Kafka vs. none) stays open behind it, so nothing downstream is committed to a
 * transport. The default is {@link #noOp()} — events cost nothing until a sink is wired.</p>
 */
@FunctionalInterface
public interface ExecutionEventPublisher {

    /** Emit a single event. Implementations must not throw on the agent's hot path. */
    void publish(ExecutionEvent event);

    /** A publisher that discards everything — the zero-config default. */
    static ExecutionEventPublisher noOp() {
        return event -> { };
    }
}
