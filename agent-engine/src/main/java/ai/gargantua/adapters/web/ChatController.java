package ai.gargantua.adapters.web;

import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.session.DryRunContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint for synchronous chat interactions with the agent.
 * Accepts a user message and returns the full agent response including metadata.
 * User and session identity are passed via HTTP headers.
 */
@RestController
@RequestMapping("/api/agent/chat")
@Tag(name = "Chat")
public class ChatController {

    private final OrchestratorEngine orchestratorEngine;

    public ChatController(OrchestratorEngine orchestratorEngine) {
        this.orchestratorEngine = orchestratorEngine;
    }

    @PostMapping
    @Operation(
            summary = "Send a chat message",
            description = "Sends a user message to the orchestrator and returns the full agent response synchronously."
    )
    @ApiResponse(responseCode = "200", description = "Agent response")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public ResponseEntity<AgentResponse> chat(
            @RequestBody ChatRequest request,
            @Parameter(description = "User identifier")
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @Parameter(description = "Session identifier")
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Parameter(description = "Dry run mode")
            @RequestHeader(value = "X-Dry-Run", defaultValue = "false") boolean dryRun) {

        DryRunContext dryRunContext = dryRun
                ? DryRunContext.active(Map.of())
                : DryRunContext.inactive();

        AgentRequest agentRequest = AgentRequest.builder()
                .message(request.message())
                .userId(userId)
                .sessionId(sessionId)
                .dryRunContext(dryRunContext)
                .build();

        AgentResponse response = orchestratorEngine.invoke(agentRequest);
        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String message) {
    }
}
