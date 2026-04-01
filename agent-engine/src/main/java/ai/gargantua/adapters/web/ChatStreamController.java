package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.core.orchestrator.AgentRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * REST endpoint for streaming chat interactions via Server-Sent Events (SSE).
 * Emits token-by-token events, tool call notifications, approval requests, and metadata.
 */
@RestController
@RequestMapping("/api/agent/chat")
@Tag(name = "Chat")
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    private final OrchestratorEngine orchestratorEngine;

    public ChatStreamController(OrchestratorEngine orchestratorEngine) {
        this.orchestratorEngine = orchestratorEngine;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream chat response via SSE",
            description = "Sends the user message to the orchestrator and streams back events including tokens, tool calls, and results."
    )
    @ApiResponse(responseCode = "200", description = "SSE stream of agent events")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public Flux<ServerSentEvent<String>> streamChat(
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

        var securityContext = (SecurityContext) httpRequest.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        Flux.defer(() -> {
            try {
                var dryRunContext = dryRun
                        ? DryRunContext.active(Map.of())
                        : DryRunContext.inactive();

                var agentRequest = AgentRequest.builder()
                        .message(request.message())
                        .userId(userId)
                        .sessionId(sessionId)
                        .forceSkill(forceSkill)
                        .dryRunContext(dryRunContext)
                        .securityContext(securityContext)
                        .build();

                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("token")
                        .data("""
                                {"status":"processing"}""")
                        .build());

                var response = orchestratorEngine.invoke(agentRequest);

                if (response.toolsCalled() != null) {
                    for (var tool : response.toolsCalled()) {
                        sink.tryEmitNext(ServerSentEvent.<String>builder()
                                .event("tool_call")
                                .data("""
                                        {"tool":"%s"}""".formatted(tool))
                                .build());
                        sink.tryEmitNext(ServerSentEvent.<String>builder()
                                .event("tool_result")
                                .data("""
                                        {"tool":"%s","status":"completed"}""".formatted(tool))
                                .build());
                    }
                }

                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("token")
                        .data(response.text())
                        .build());

                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("""
                                {"sessionId":"%s","skillUsed":"%s","totalTokens":%d,"durationMs":%d}"""
                                .formatted(response.sessionId(), response.skillUsed(),
                                        response.totalTokens(), response.durationMs()))
                        .build());

            } catch (Exception e) {
                log.error("Error during streaming chat", e);
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("""
                                {"error":"%s"}""".formatted(e.getMessage().replace("\"", "\\\"")))
                        .build());
            } finally {
                sink.tryEmitComplete();
            }
            return Flux.empty();
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        return sink.asFlux();
    }

    public record ChatRequest(String message) {
    }
}
