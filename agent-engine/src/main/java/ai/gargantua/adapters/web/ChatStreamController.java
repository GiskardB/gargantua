package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.CostTracker;
import ai.gargantua.autoconfigure.DefaultOrchestratorEngine;
import ai.gargantua.autoconfigure.GuardrailPipeline;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.PromptBuilder;
import ai.gargantua.autoconfigure.SemanticRoutingService;
import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.RateLimitExceededException;
import ai.gargantua.core.exception.SchemaValidationException;
import ai.gargantua.core.exception.TokenBudgetExceededException;
import ai.gargantua.core.exception.ApprovalExpiredException;
import ai.gargantua.core.exception.DryRunNotAllowedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.BudgetRequest;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.memory.composer.MemoryComposer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for streaming chat interactions via Server-Sent Events (SSE).
 * Emits token-by-token events, tool call notifications, approval requests, and metadata.
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
public class ChatStreamController {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamController.class);

    private final OrchestratorEngine orchestratorEngine;
    private final LlmProviderFactory llmProviderFactory;
    private final GuardrailPipeline guardrailPipeline;
    private final SemanticRoutingService semanticRoutingService;
    private final TokenBudgetManager tokenBudgetManager;
    private final PromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final List<ContextEnricher> contextEnrichers;

    @Nullable
    private final SkillRegistry skillRegistry;

    @Nullable
    private final MemoryComposer memoryComposer;

    @Nullable
    private final WorkingMemoryPort workingMemoryPort;

    @Nullable
    private final CostTracker costTracker;

    @Nullable
    private final ApprovalStore approvalStore;

    public ChatStreamController(OrchestratorEngine orchestratorEngine,
                                LlmProviderFactory llmProviderFactory,
                                GuardrailPipeline guardrailPipeline,
                                SemanticRoutingService semanticRoutingService,
                                TokenBudgetManager tokenBudgetManager,
                                PromptBuilder promptBuilder,
                                ToolRegistry toolRegistry,
                                AgentProperties properties,
                                List<ContextEnricher> contextEnrichers,
                                @Nullable SkillRegistry skillRegistry,
                                @Nullable MemoryComposer memoryComposer,
                                @Nullable WorkingMemoryPort workingMemoryPort,
                                @Nullable CostTracker costTracker,
                                @Nullable ApprovalStore approvalStore) {
        this.orchestratorEngine = orchestratorEngine;
        this.llmProviderFactory = llmProviderFactory;
        this.guardrailPipeline = guardrailPipeline;
        this.semanticRoutingService = semanticRoutingService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        // Pre-sort enrichers once — see DefaultOrchestratorEngine for the same rationale.
        this.contextEnrichers = (contextEnrichers == null || contextEnrichers.isEmpty())
                ? List.of()
                : contextEnrichers.stream()
                        .sorted(Comparator.comparingInt(ContextEnricher::order))
                        .toList();
        this.skillRegistry = skillRegistry;
        this.memoryComposer = memoryComposer;
        this.workingMemoryPort = workingMemoryPort;
        this.costTracker = costTracker;
        this.approvalStore = approvalStore;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream chat response via Server-Sent Events",
            description = """
                    Same pipeline as `POST /api/agent/chat`, but emits each event as soon as
                    it is produced. SSE event types:

                    - `token`              — incremental LLM output token.
                    - `tool_call`          — the LLM requested a tool. Payload includes name + arguments JSON.
                    - `tool_result`        — tool execution finished. Payload includes the JSON return value.
                    - `approval_required`  — execution paused on a `@RequiresApproval` tool.
                                              Payload carries the `approvalRequestId` to resolve via
                                              `POST /api/agent/approval/{id}`.
                    - `guardrail_block`    — a guardrail blocked the message. Stream ends.
                    - `metadata`           — final event with token counts / cost / duration / skill used.
                    - `done`               — clean stream termination.
                    - `error`              — pipeline error. The connection closes immediately afterwards.

                    Clients should treat `done` and `error` as terminal; `metadata` always
                    precedes `done` on success.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream of agent events. See description for the event taxonomy."),
            @ApiResponse(responseCode = "400", description = "Invalid request body or blocked by an input guardrail."),
            @ApiResponse(responseCode = "401", description = "Forced skill required RBAC and the caller had no matching role."),
            @ApiResponse(responseCode = "404", description = "Forced skill name does not exist."),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded for this user / tenant.")
    })
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestBody ChatRequest request,
            @Parameter(description = "Caller identity. Drives memory partitioning, audit attribution and rate limiting.",
                    example = "alice")
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @Parameter(description = "Conversation session id. Same value across messages keeps working memory warm.",
                    example = "session-2026-05-12-1f3c")
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @Parameter(description = "When `true`, run the pipeline without invoking real tools.")
            @RequestHeader(value = "X-Dry-Run", defaultValue = "false") boolean dryRun,
            @Parameter(description = "Bypass routing entirely and activate the named skill.",
                    example = "billing-skill")
            @RequestHeader(value = "X-Force-Skill", required = false) String forceSkill,
            HttpServletRequest httpRequest) {

        var securityContext = (SecurityContext) httpRequest.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR);
        Map<String, String> headerAttrs = RequestContextHeaders.extract(httpRequest);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        Flux.defer(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // --- Pre-LLM pipeline (synchronous, fast) ---

                // Input guardrails
                var inputAttributes = new HashMap<String, Object>();
                if (securityContext != null) {
                    inputAttributes.put("gargantua.securityContext", securityContext);
                }
                inputAttributes.putAll(headerAttrs);
                var inputCtx = new GuardrailInputContext(
                        request.message(), userId, sessionId, null, inputAttributes);
                var inputResult = guardrailPipeline.checkInput(inputCtx);
                emitGuardrailWarnings(sink, inputResult.results(), "input");
                if (inputResult.blocked()) {
                    emitError(sink, "GUARDRAIL_BLOCKED",
                            "Blocked by guardrail: " + inputResult.reason());
                    sink.tryEmitComplete();
                    return Flux.empty();
                }

                // Route to skill
                var skills = skillRegistry != null ? skillRegistry.listMeta() : List.<SkillMeta>of();
                var routingResult = (forceSkill != null && !forceSkill.isBlank())
                        ? ai.gargantua.core.orchestrator.RoutingResult.forced(forceSkill)
                        : semanticRoutingService.route(request.message(), skills);

                // Load skill card
                SkillCard skillCard;
                if (skillRegistry != null) {
                    try {
                        skillCard = skillRegistry.load(routingResult.skillName());
                    } catch (Exception e) {
                        throw new SkillNotFoundException(routingResult.skillName());
                    }
                } else {
                    var meta = new SkillMeta(routingResult.skillName(), "", "1.0.0",
                            true, false, "general",
                            ai.gargantua.core.skill.SkillSource.FILESYSTEM, java.util.Set.of());
                    skillCard = new SkillCard(meta, "", List.of(), null, List.of(), null, null, null, null);
                }

                // Compose memory
                String effectiveSessionId = (securityContext != null && securityContext.isMultiTenant())
                        ? securityContext.tenantId() + ":" + sessionId
                        : sessionId;

                ComposedMemory memory;
                if (memoryComposer != null) {
                    try {
                        memory = memoryComposer.compose(userId, effectiveSessionId,
                                properties.getMemory().getComposer().getMaxContextTokens(),
                                skillCard.enabledMemoryLayers());
                    } catch (Exception e) {
                        memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
                    }
                } else {
                    memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
                }

                // Build prompt with enrichers — seed with X-Context-* attrs so enrichers can read them
                var enricherAttributes = new HashMap<String, String>(headerAttrs);
                var enricherCtx = new EnricherContext(
                        userId, effectiveSessionId, skillCard.meta().name(),
                        skillCard.meta().domain(), request.message(), headerAttrs);
                contextEnrichers.stream()
                        .filter(e -> e.targetSkill() == null || e.targetSkill().equals(skillCard.meta().name()))
                        .forEach(e -> {
                            try {
                                String section = e.enrich(enricherCtx);
                                if (section != null && !section.isBlank()) {
                                    enricherAttributes.put(e.sectionName(), section);
                                }
                            } catch (Exception ex) {
                                log.warn("Context enricher failed: {}", ex.getMessage());
                            }
                        });

                var enricherContext = new EnricherContext(
                        userId, effectiveSessionId, skillCard.meta().name(),
                        skillCard.meta().domain(), request.message(), enricherAttributes);
                var systemPrompt = promptBuilder.build(skillCard, memory, enricherContext);

                // Token budget
                var toolDescriptions = toolRegistry.getFilteredTools(skillCard.allowedTools())
                        .stream().map(ToolDefinition::description).toList();
                var budgetRequest = new BudgetRequest(
                        systemPrompt, "",
                        skillCard.references() != null ? skillCard.references() : List.of(),
                        List.of(), memory.knowledgeSegments(), toolDescriptions,
                        request.message(),
                        properties.getMemory().getComposer().getMaxContextTokens());
                var allocation = tokenBudgetManager.allocate(budgetRequest);

                // Build messages and tool specs
                var messages = llmProviderFactory.buildMessages(
                        allocation.systemPrompt(), request.message(), memory.workingMessages());
                var toolSpecs = toolRegistry.getToolSpecifications(skillCard.allowedTools());

                // Resolve streaming model
                var llmCtx = new LlmRoutingContext(
                        userId, effectiveSessionId, skillCard.meta().name(),
                        skillCard.meta().domain(), request.message(),
                        request.message() != null ? request.message().length() : 0,
                        tokenBudgetManager.estimate(request.message()),
                        "default", LocalTime.now(),
                        DayOfWeek.from(java.time.LocalDate.now()), Map.of());
                String alias = llmProviderFactory.resolveModelAlias(skillCard, llmCtx);
                StreamingChatModel streamingModel = llmProviderFactory.getStreamingModel(alias);

                // --- Real streaming with tool calling loop ---
                var toolContext = ToolExecutionContext.of(securityContext, effectiveSessionId);
                streamWithToolLoop(sink, streamingModel, messages, toolSpecs, skillCard,
                        routingResult, effectiveSessionId, userId, request.message(),
                        memory, startTime, dryRun, toolContext, alias);

            } catch (Exception e) {
                log.error("Error during streaming chat setup", e);
                emitError(sink, errorCodeFor(e),
                        e.getMessage() != null ? e.getMessage() : "Unknown error");
                sink.tryEmitComplete();
            }
            return Flux.empty();
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();

        return sink.asFlux();
    }

    /**
     * Perform real LLM streaming with a tool calling loop. When the LLM requests tool calls,
     * tools are executed and results fed back; then streaming resumes for the next LLM turn.
     */
    private void streamWithToolLoop(Sinks.Many<ServerSentEvent<String>> sink,
                                     StreamingChatModel streamingModel,
                                     List<dev.langchain4j.data.message.ChatMessage> messages,
                                     List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs,
                                     SkillCard skillCard,
                                     ai.gargantua.core.orchestrator.RoutingResult routingResult,
                                     String effectiveSessionId,
                                     String userId,
                                     String userMessage,
                                     ComposedMemory memory,
                                     long startTime,
                                     boolean dryRun,
                                     ToolExecutionContext toolContext,
                                     String modelAlias) {
        streamSingleTurn(sink, streamingModel, messages, toolSpecs, skillCard,
                routingResult, effectiveSessionId, userId, userMessage, memory,
                startTime, dryRun, 0, new ArrayList<>(), new StringBuilder(), toolContext, modelAlias);
    }

    private void streamSingleTurn(Sinks.Many<ServerSentEvent<String>> sink,
                                   StreamingChatModel streamingModel,
                                   List<dev.langchain4j.data.message.ChatMessage> messages,
                                   List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs,
                                   SkillCard skillCard,
                                   ai.gargantua.core.orchestrator.RoutingResult routingResult,
                                   String effectiveSessionId,
                                   String userId,
                                   String userMessage,
                                   ComposedMemory memory,
                                   long startTime,
                                   boolean dryRun,
                                   int iteration,
                                   List<String> toolsCalled,
                                   StringBuilder fullResponse,
                                   ToolExecutionContext toolContext,
                                   String modelAlias) {
        if (iteration >= 10) {
            emitError(sink, "MAX_TOOL_ITERATIONS", "Max tool iterations reached");
            sink.tryEmitComplete();
            return;
        }

        dev.langchain4j.model.chat.request.ChatRequest.Builder requestBuilder =
                dev.langchain4j.model.chat.request.ChatRequest.builder().messages(messages);
        if (toolSpecs != null && !toolSpecs.isEmpty()) {
            requestBuilder.toolSpecifications(toolSpecs);
        }

        streamingModel.chat(requestBuilder.build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullResponse.append(partialResponse);
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("token")
                        .data("{\"token\":\"%s\"}".formatted(escapeJson(partialResponse)))
                        .build());
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                AiMessage aiMessage = response.aiMessage();
                messages.add(aiMessage);

                if (aiMessage.hasToolExecutionRequests()) {
                    // Reset response buffer for next turn (tool results, not final text)
                    fullResponse.setLength(0);

                    for (var toolRequest : aiMessage.toolExecutionRequests()) {
                        String toolName = toolRequest.name();
                        toolsCalled.add(toolName);

                        sink.tryEmitNext(ServerSentEvent.<String>builder()
                                .event("tool_call")
                                .data("{\"tool\":\"%s\",\"arguments\":%s}".formatted(
                                        escapeJson(toolName), toolRequest.arguments()))
                                .build());

                        ToolDefinition def = toolRegistry.getFilteredTools(null).stream()
                                .filter(t -> toolName.equals(t.name()))
                                .findFirst().orElse(null);

                        // Since 1.2.6 the @RequiresApproval gate lives in
                        // ToolRegistry.executeTool — the registry persists the
                        // pending request and returns the awaiting_approval JSON.
                        // The controller's job is only to surface the SSE event
                        // when that JSON comes back.
                        String result = toolRegistry.executeTool(toolName, toolRequest.arguments(), toolContext);
                        if (def != null && def.requiresApproval() && isAwaitingApproval(result)) {
                            emitApprovalRequiredEvent(sink, def, toolName, extractRequestId(result));
                        }

                        sink.tryEmitNext(ServerSentEvent.<String>builder()
                                .event("tool_result")
                                .data("{\"tool\":\"%s\",\"result\":\"%s\"}".formatted(
                                        escapeJson(toolName), escapeJson(
                                                result.length() > 500 ? result.substring(0, 500) + "..." : result)))
                                .build());

                        messages.add(ToolExecutionResultMessage.from(toolRequest, result));
                    }

                    // Continue the loop: call LLM again with tool results
                    streamSingleTurn(sink, streamingModel, messages, toolSpecs, skillCard,
                            routingResult, effectiveSessionId, userId, userMessage, memory,
                            startTime, dryRun, iteration + 1, toolsCalled, fullResponse,
                            toolContext, modelAlias);
                } else {
                    // Final response — complete the stream
                    String finalText = fullResponse.toString();

                    // Output guardrails
                    try {
                        var outputCtx = new GuardrailOutputContext(
                                finalText, userId, effectiveSessionId, skillCard.meta(), Map.of());
                        finalText = guardrailPipeline.processOutput(outputCtx);
                    } catch (Exception e) {
                        log.warn("Output guardrail failed: {}", e.getMessage());
                    }

                    // Persist memory
                    if (!dryRun && workingMemoryPort != null) {
                        try {
                            workingMemoryPort.appendMessage(effectiveSessionId,
                                    ChatMessage.userMessage(userMessage));
                            workingMemoryPort.appendMessage(effectiveSessionId,
                                    ChatMessage.assistantMessage(finalText));
                        } catch (Exception e) {
                            log.warn("Failed to persist working memory: {}", e.getMessage());
                        }
                    }

                    long durationMs = System.currentTimeMillis() - startTime;
                    int inputTokens = tokenBudgetManager.estimate(userMessage);
                    int outputTokens = tokenBudgetManager.estimate(finalText);

                    var modelConfig = llmProviderFactory.getModelConfig(modelAlias);
                    String provider = modelConfig != null ? modelConfig.getProvider() : "";
                    String modelName = modelConfig != null ? modelConfig.getModel() : "";
                    double estimatedCostUsd = costTracker != null
                            ? costTracker.estimateUsd(provider, modelName, inputTokens, outputTokens)
                            : 0.0;

                    String toolsJson = String.join(",", toolsCalled.stream()
                            .map(t -> "\"" + escapeJson(t) + "\"")
                            .toList());
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(("{\"sessionId\":\"%s\",\"skillUsed\":\"%s\","
                                    + "\"routingMethod\":\"%s\",\"totalTokens\":%d,"
                                    + "\"estimatedCostUsd\":%s,\"durationMs\":%d,"
                                    + "\"toolsCalled\":[%s]}")
                                    .formatted(escapeJson(effectiveSessionId),
                                            escapeJson(routingResult.skillName()),
                                            routingResult.method() != null ? routingResult.method().name() : "",
                                            inputTokens + outputTokens,
                                            formatCost(estimatedCostUsd),
                                            durationMs,
                                            toolsJson))
                            .build());

                    sink.tryEmitComplete();
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("Streaming LLM error", error);
                emitError(sink, errorCodeFor(error),
                        error.getMessage() != null ? error.getMessage() : "Unknown streaming error");
                sink.tryEmitComplete();
            }
        });
    }

    private static final java.util.regex.Pattern AWAITING_REQUEST_ID =
            java.util.regex.Pattern.compile("\"requestId\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");

    /** Recognise the JSON the registry returns when a tool is gated by {@code @RequiresApproval}. */
    private static boolean isAwaitingApproval(String result) {
        return result != null && result.startsWith("{\"status\":\"awaiting_approval\"");
    }

    /** Extract the registry-generated requestId from the awaiting_approval JSON, or {@code null}. */
    private static String extractRequestId(String result) {
        if (result == null) return null;
        var m = AWAITING_REQUEST_ID.matcher(result);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Emit an {@code approval_required} SSE event for a tool that the registry
     * has already persisted as a pending {@link ApprovalRequest}. The event
     * surfaces the filtered parameter subset (per
     * {@link ai.gargantua.core.tool.RequiresApproval#showParameters}), the
     * human-readable message, the {@code dangerous} flag and the TTL window.
     */
    private void emitApprovalRequiredEvent(Sinks.Many<ServerSentEvent<String>> sink,
                                           ToolDefinition def, String toolName,
                                           @org.springframework.lang.Nullable String requestId) {
        if (requestId == null) {
            log.warn("[ChatStream] approval_required event skipped: registry returned awaiting JSON "
                    + "without a parseable requestId for tool {}", toolName);
            return;
        }
        int ttl = properties.getHitl().getDefaultTtlMinutes();
        String parametersJson = renderApprovalParameters(requestId);
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("approval_required")
                .data(("{\"requestId\":\"%s\",\"tool\":\"%s\",\"parameters\":%s,"
                        + "\"message\":\"%s\",\"dangerous\":%s,\"ttlMinutes\":%d}")
                        .formatted(escapeJson(requestId),
                                escapeJson(toolName),
                                parametersJson,
                                escapeJson(def.approvalMessage() != null ? def.approvalMessage() : ""),
                                def.dangerous(),
                                ttl))
                .build());
    }

    /**
     * Look up the persisted {@link ApprovalRequest} by id and render its
     * {@code parameters} map as JSON. Falls back to {@code {}} when the store
     * is missing or the entry can't be found (e.g. immediate readback race or
     * an in-memory store that just rotated).
     */
    private String renderApprovalParameters(String requestId) {
        if (approvalStore == null) return "{}";
        try {
            return approvalStore.getPending(requestId)
                    .map(req -> mapToJson(req.parameters()))
                    .orElse("{}");
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(escapeJson(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
            first = false;
        }
        return sb.append('}').toString();
    }

    /**
     * Emits a {@code guardrail_warn} SSE event for every guardrail result whose
     * verdict is {@link GuardrailVerdict#WARN} (or has a non-blank reason while
     * passing — covers guardrails that mention soft caveats).
     */
    private void emitGuardrailWarnings(Sinks.Many<ServerSentEvent<String>> sink,
                                       List<GuardrailResult> results, String phase) {
        if (results == null) return;
        for (GuardrailResult r : results) {
            boolean isWarn = r.verdict() == GuardrailVerdict.WARN
                    || (r.verdict() == GuardrailVerdict.PASS && r.reason() != null && !r.reason().isBlank());
            if (!isWarn) continue;
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("guardrail_warn")
                    .data("{\"phase\":\"%s\",\"guardrail\":\"%s\",\"reason\":\"%s\"}".formatted(
                            escapeJson(phase),
                            escapeJson(r.guardrailName()),
                            escapeJson(r.reason() != null ? r.reason() : "")))
                    .build());
        }
    }

    private void emitError(Sinks.Many<ServerSentEvent<String>> sink, String code, String message) {
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"error\":\"%s\",\"code\":\"%s\"}".formatted(
                        escapeJson(message != null ? message : ""),
                        escapeJson(code != null ? code : "INTERNAL_ERROR")))
                .build());
    }

    /**
     * Maps a thrown exception to the structured {@code code} field surfaced on
     * SSE error events. The values mirror the {@code type} URIs used by
     * {@link AgentKitExceptionHandler}, so REST and SSE clients see the same
     * vocabulary for the same failure modes.
     */
    static String errorCodeFor(Throwable error) {
        if (error == null) return "INTERNAL_ERROR";
        if (error instanceof GuardrailBlockedException) return "GUARDRAIL_BLOCKED";
        if (error instanceof RateLimitExceededException) return "RATE_LIMIT_EXCEEDED";
        if (error instanceof SkillNotFoundException) return "SKILL_NOT_FOUND";
        if (error instanceof TokenBudgetExceededException) return "TOKEN_BUDGET_EXCEEDED";
        if (error instanceof SchemaValidationException) return "SCHEMA_VALIDATION";
        if (error instanceof ApprovalExpiredException) return "APPROVAL_EXPIRED";
        if (error instanceof DryRunNotAllowedException) return "DRY_RUN_NOT_ALLOWED";
        return "INTERNAL_ERROR";
    }

    private static String formatCost(double cost) {
        // Always emit a JSON-safe number — handles NaN/Infinity by collapsing to 0.
        if (Double.isNaN(cost) || Double.isInfinite(cost)) return "0.0";
        return Double.toString(cost);
    }

    private static String escapeJson(String value) {
        return JsonUtils.escapeJson(value);
    }

    @Schema(description = "Single-turn chat request body (same shape as the synchronous endpoint).")
    public record ChatRequest(
            @Schema(description = "Natural-language message from the user.",
                    example = "What's the status of invoice 12345?",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String message
    ) {
    }
}
