package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.DefaultOrchestratorEngine;
import ai.gargantua.autoconfigure.GuardrailPipeline;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.PromptBuilder;
import ai.gargantua.autoconfigure.SemanticRoutingService;
import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
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
import ai.gargantua.memory.composer.MemoryComposer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Chat")
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
                                @Nullable WorkingMemoryPort workingMemoryPort) {
        this.orchestratorEngine = orchestratorEngine;
        this.llmProviderFactory = llmProviderFactory;
        this.guardrailPipeline = guardrailPipeline;
        this.semanticRoutingService = semanticRoutingService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.contextEnrichers = contextEnrichers != null ? contextEnrichers : List.of();
        this.skillRegistry = skillRegistry;
        this.memoryComposer = memoryComposer;
        this.workingMemoryPort = workingMemoryPort;
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
            long startTime = System.currentTimeMillis();
            try {
                // --- Pre-LLM pipeline (synchronous, fast) ---

                // Input guardrails
                var inputAttributes = new HashMap<String, Object>();
                if (securityContext != null) {
                    inputAttributes.put("gargantua.securityContext", securityContext);
                }
                var inputCtx = new GuardrailInputContext(
                        request.message(), userId, sessionId, null, inputAttributes);
                var inputResult = guardrailPipeline.checkInput(inputCtx);
                if (inputResult.blocked()) {
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("{\"error\":\"Blocked by guardrail: %s\"}".formatted(
                                    escapeJson(inputResult.reason())))
                            .build());
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

                // Build prompt with enrichers
                var enricherAttributes = new HashMap<String, String>();
                var enricherCtx = new EnricherContext(
                        userId, effectiveSessionId, skillCard.meta().name(),
                        skillCard.meta().domain(), request.message(), Map.of());
                contextEnrichers.stream()
                        .sorted(Comparator.comparingInt(ContextEnricher::order))
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
                streamWithToolLoop(sink, streamingModel, messages, toolSpecs, skillCard,
                        routingResult, effectiveSessionId, userId, request.message(),
                        memory, startTime, dryRun);

            } catch (Exception e) {
                log.error("Error during streaming chat setup", e);
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("{\"error\":\"%s\"}".formatted(escapeJson(
                                e.getMessage() != null ? e.getMessage() : "Unknown error")))
                        .build());
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
                                     boolean dryRun) {
        streamSingleTurn(sink, streamingModel, messages, toolSpecs, skillCard,
                routingResult, effectiveSessionId, userId, userMessage, memory,
                startTime, dryRun, 0, new ArrayList<>(), new StringBuilder());
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
                                   StringBuilder fullResponse) {
        if (iteration >= 10) {
            sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"error\":\"Max tool iterations reached\"}")
                    .build());
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

                        String result = toolRegistry.executeTool(toolName, toolRequest.arguments());

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
                            startTime, dryRun, iteration + 1, toolsCalled, fullResponse);
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

                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("done")
                            .data("{\"sessionId\":\"%s\",\"skillUsed\":\"%s\",\"totalTokens\":%d,\"durationMs\":%d,\"toolsCalled\":[%s]}"
                                    .formatted(escapeJson(effectiveSessionId),
                                            escapeJson(routingResult.skillName()),
                                            inputTokens + outputTokens, durationMs,
                                            toolsCalled.stream()
                                                    .map(t -> "\"" + escapeJson(t) + "\"")
                                                    .reduce((a, b) -> a + "," + b).orElse("")))
                            .build());

                    sink.tryEmitComplete();
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("Streaming LLM error", error);
                sink.tryEmitNext(ServerSentEvent.<String>builder()
                        .event("error")
                        .data("{\"error\":\"%s\"}".formatted(escapeJson(
                                error.getMessage() != null ? error.getMessage() : "Unknown streaming error")))
                        .build());
                sink.tryEmitComplete();
            }
        });
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record ChatRequest(String message) {
    }
}
