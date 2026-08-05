package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.FlowExecutor;
import ai.gargantua.autoconfigure.FlowRegistry;
import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.core.flow.FlowDefinition;
import ai.gargantua.core.flow.FlowResult;
import ai.gargantua.core.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for executing and listing agent flows.
 * A flow is a multi-step pipeline where multiple skills are executed in sequence.
 */
@RestController
@RequestMapping("/api/flows")
@Tag(
        name = "Flows",
        description = "Discover and execute `@AgentsFlow` declarations. A flow chains multiple skills "
                + "(`SEQUENTIAL` / `LOOP` / `PARALLEL` step types) into a single composite invocation; "
                + "this endpoint returns the final result plus a step-by-step trace so callers can see "
                + "exactly which skill ran with which output. See the `agent-example-agents-flow` "
                + "per-feature example for the registration contract."
)
public class FlowController {

    private final FlowRegistry flowRegistry;
    private final FlowExecutor flowExecutor;

    public FlowController(FlowRegistry flowRegistry, FlowExecutor flowExecutor) {
        this.flowRegistry = flowRegistry;
        this.flowExecutor = flowExecutor;
    }

    @GetMapping
    @Operation(summary = "List all registered flows")
    public ResponseEntity<List<Map<String, Object>>> listFlows() {
        var flows = flowRegistry.getAll().stream()
                .map(f -> Map.<String, Object>of(
                        "name", f.name(),
                        "description", f.description(),
                        "steps", f.steps().stream().map(FlowDefinition.FlowStep::skillName).toList()
                ))
                .toList();
        return ResponseEntity.ok(flows);
    }

    @PostMapping("/{flowName}/start")
    @Operation(summary = "Execute a flow", description = "Runs all steps sequentially, returns the final result with step-by-step trace")
    public ResponseEntity<FlowResult> startFlow(
            @PathVariable String flowName,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            HttpServletRequest request) {

        var flow = flowRegistry.get(flowName);
        if (flow.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var input = body.getOrDefault("input", body.getOrDefault("message", ""));
        var effectiveSessionId = sessionId != null ? sessionId : "flow-" + UUID.randomUUID();

        var securityContext = (SecurityContext) request.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR);
        if (securityContext == null) {
            securityContext = SecurityContext.anonymous(userId);
        }

        var result = flowExecutor.execute(flow.get(), input, userId, effectiveSessionId, securityContext);
        return ResponseEntity.ok(result);
    }
}
