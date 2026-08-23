package ai.gargantua.adapters.web;

import ai.gargantua.adapters.execution.InMemoryExecutionEventPublisher;
import ai.gargantua.core.execution.ExecutionTrace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to recent {@link ExecutionTrace}s — the fine-grained, per-turn event
 * timelines the engine emits. Backed by the in-memory sink
 * ({@link InMemoryExecutionEventPublisher}); this is what the Studio Trace Explorer reads
 * to replace its sample data with real runs.
 */
@RestController
@RequestMapping("/api/traces")
@Tag(name = "Traces", description = "Recent per-turn execution traces (in-memory, most recent first).")
public class TraceController {

    private final InMemoryExecutionEventPublisher store;

    public TraceController(InMemoryExecutionEventPublisher store) {
        this.store = store;
    }

    @GetMapping
    @Operation(summary = "List recent execution traces (newest first)")
    public List<ExecutionTrace> recent(@RequestParam(defaultValue = "20") int limit) {
        return store.recentTraces(limit);
    }

    @GetMapping("/{traceId}")
    @Operation(summary = "Fetch one execution trace by id")
    public ResponseEntity<ExecutionTrace> byId(@PathVariable String traceId) {
        ExecutionTrace trace = store.trace(traceId);
        return trace == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(trace);
    }
}
