package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(
        name = "Chat",
        description = "Synchronous and streaming chat endpoints. The agent pipeline runs end-to-end "
                + "for every request: input guardrails → routing → memory composition → LLM call → "
                + "tool execution loop → output guardrails. Identity, RBAC and dry-run flags are passed "
                + "via HTTP headers (X-User-Id, X-Session-Id, X-User-Roles, X-Tenant-Id, X-Dry-Run, "
                + "X-Force-Skill)."
)
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final OrchestratorEngine orchestratorEngine;

    public ChatController(OrchestratorEngine orchestratorEngine) {
        this.orchestratorEngine = orchestratorEngine;
    }

    @PostMapping
    @Operation(
            summary = "Send a chat message (synchronous)",
            description = """
                    Runs the agent pipeline once and returns the **complete** response
                    (text + observability metadata: routing method/confidence, tools called,
                    token counts, estimated USD cost, duration, dry-run flag).

                    For token-by-token streaming, use `POST /api/agent/chat/stream` instead.

                    The pipeline order is: input guardrails → routing → memory composition →
                    LLM call → tool execution loop → output guardrails. A `BLOCK` from any
                    guardrail is surfaced as HTTP 4xx; rate-limit hits → 429; an
                    OrchestratorEngine exception → 500.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent response — see schema."),
            @ApiResponse(responseCode = "400",
                    description = "Invalid request body, blocked by an input guardrail (e.g. PromptInjection, "
                            + "MaxLength, TopicScope), or schema validation failed on the LLM output."),
            @ApiResponse(responseCode = "401", description = "Forced skill required RBAC and the caller had no matching role."),
            @ApiResponse(responseCode = "404", description = "`X-Force-Skill` (or body `forceSkill`) referenced an unknown / inactive skill."),
            @ApiResponse(responseCode = "410", description = "An approval-gated tool referenced an expired ApprovalRequest."),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded for this user / tenant."),
            @ApiResponse(responseCode = "500", description = "Unexpected orchestrator failure — see audit log for the eventId.")
    })
    public ResponseEntity<AgentResponse> chat(
            @RequestBody ChatRequest request,
            @Parameter(description = "Caller identity. Drives memory partitioning, audit attribution and rate limiting.",
                    example = "alice")
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @Parameter(description = "Conversation session id. Same value across messages keeps working memory warm; "
                    + "omit / change it to start a fresh conversation.",
                    example = "session-2026-05-12-1f3c")
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Parameter(description = "When `true`, run the pipeline without invoking real tools (stubs the side effects). "
                    + "Useful for evals and CI.")
            @RequestHeader(value = "X-Dry-Run", defaultValue = "false") boolean dryRun,
            @Parameter(description = "Bypass routing entirely and activate the named skill. Same effect as setting "
                    + "`forceSkill` in the request body — the body wins on conflict.",
                    example = "billing-skill")
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

    @Schema(description = "Single-turn chat request body.")
    public record ChatRequest(
            @Schema(description = "Natural-language message from the user. Subject to input guardrails "
                    + "(MaxLength / PromptInjection / TopicScope / PII).",
                    example = "What's the status of invoice 12345?",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String message
    ) {
    }
}
