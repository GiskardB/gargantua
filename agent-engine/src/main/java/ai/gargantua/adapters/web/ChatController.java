package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

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
            @RequestHeader(value = "X-Dry-Run", defaultValue = "false") boolean dryRun,
            @Parameter(description = "Force a specific skill (bypass routing)")
            @RequestHeader(value = "X-Force-Skill", required = false) String forceSkill,
            HttpServletRequest httpRequest) {

        DryRunContext dryRunContext = dryRun
                ? DryRunContext.active(Map.of())
                : DryRunContext.inactive();

        var securityContext = (SecurityContext) httpRequest.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR);

        Map<String, String> headerAttrs = RequestContextHeaders.extract(httpRequest);

        AgentRequest agentRequest = AgentRequest.builder()
                .message(request.message())
                .userId(userId)
                .sessionId(sessionId)
                .forceSkill(forceSkill)
                .dryRunContext(dryRunContext)
                .securityContext(securityContext)
                .contextAttributes(new java.util.HashMap<>(headerAttrs))
                .build();

        log.info("[Chat] POST /api/agent/chat — userId={}, sessionId={}, forceSkill={}, dryRun={}",
                userId, sessionId, forceSkill, dryRun);

        AgentResponse response = orchestratorEngine.invoke(agentRequest);

        log.info("[Chat] Response — skill={}, routing={}, tokens={}, durationMs={}",
                response.skillUsed(), response.routingMethod(), response.totalTokens(), response.durationMs());

        return ResponseEntity.ok(response);
    }

    public record ChatRequest(String message) {
    }
}
