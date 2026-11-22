package ai.gargantua.autoconfigure;

import ai.gargantua.core.execution.ExecutionEvent;
import ai.gargantua.core.execution.ExecutionEventPublisher;
import ai.gargantua.core.execution.ExecutionEventType;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.memory.composer.MemoryComposer;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.tool.ToolExecutionContext;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link OrchestratorEngine} that executes the full
 * 13-step agent invocation pipeline:
 *
 * <ol>
 *   <li>Extract dry-run context</li>
 *   <li>Run input guardrails (max-length, prompt injection, PII masking, topic scope, rate limit)</li>
 *   <li>List available skills from the registry</li>
 *   <li>Route to the best skill (semantic match, LLM fallback, or forced)</li>
 *   <li>Load the full skill card (system prompt, allowed tools, schema)</li>
 *   <li>Compose memory from all three layers (working, episodic, knowledge)</li>
 *   <li>Build the system prompt with enricher context and memory sections</li>
 *   <li>Allocate the token budget and truncate lower-priority sections if needed</li>
 *   <li>Call the LLM via the provider factory (with model routing)</li>
 *   <li>Run output guardrails (PII redaction, disclaimer injection, schema validation)</li>
 *   <li>Persist memory (skip in dry-run mode)</li>
 *   <li>Build and return the response with metadata</li>
 *   <li>Record audit trail event (if auditing is enabled)</li>
 * </ol>
 *
 * @see OrchestratorEngine
 * @see AgentRequest
 * @see AgentResponse
 */
@Component
public class DefaultOrchestratorEngine implements OrchestratorEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrchestratorEngine.class);

    private final GuardrailPipeline guardrailPipeline;
    private final LlmProviderFactory llmProviderFactory;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final TurnPipeline turnPipeline;
    private final ExecutionEventPublisher eventPublisher;

    public DefaultOrchestratorEngine(TurnPipeline turnPipeline,
                                     GuardrailPipeline guardrailPipeline,
                                     LlmProviderFactory llmProviderFactory,
                                     ToolRegistry toolRegistry,
                                     AgentProperties properties,
                                     @Nullable ExecutionEventPublisher eventPublisher) {
        this.turnPipeline = turnPipeline;
        this.guardrailPipeline = guardrailPipeline;
        this.llmProviderFactory = llmProviderFactory;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.eventPublisher = eventPublisher == null ? ExecutionEventPublisher.noOp() : eventPublisher;
    }

    /**
     * Constructor kept for callers that assemble the engine by hand; it builds the
     * {@link TurnPipeline} the engine now delegates to.
     */
    public DefaultOrchestratorEngine(GuardrailPipeline guardrailPipeline,
                                     SemanticRoutingService semanticRoutingService,
                                     TokenBudgetManager tokenBudgetManager,
                                     LlmProviderFactory llmProviderFactory,
                                     PromptBuilder promptBuilder,
                                     ToolRegistry toolRegistry,
                                     AgentProperties properties,
                                     @Nullable SkillRegistry skillRegistry,
                                     List<ContextEnricher> contextEnrichers,
                                     @Nullable AuditService auditService,
                                     @Nullable MemoryComposer memoryComposer,
                                     @Nullable WorkingMemoryPort workingMemoryPort,
                                     @Nullable MongoTemplate mongoTemplate,
                                     @Nullable CostTracker costTracker,
                                     @Nullable ExecutionEventPublisher eventPublisher) {
        this(new TurnPipeline(guardrailPipeline, semanticRoutingService, tokenBudgetManager,
                        llmProviderFactory, promptBuilder, toolRegistry, properties, skillRegistry,
                        contextEnrichers, auditService, memoryComposer, workingMemoryPort,
                        mongoTemplate, costTracker),
                guardrailPipeline, llmProviderFactory, toolRegistry, properties, eventPublisher);
    }

    /**
     * Backward-compatible constructor for callers written before execution-event emission;
     * equivalent to passing {@link ExecutionEventPublisher#noOp()}.
     */
    public DefaultOrchestratorEngine(GuardrailPipeline guardrailPipeline,
                                     SemanticRoutingService semanticRoutingService,
                                     TokenBudgetManager tokenBudgetManager,
                                     LlmProviderFactory llmProviderFactory,
                                     PromptBuilder promptBuilder,
                                     ToolRegistry toolRegistry,
                                     AgentProperties properties,
                                     @Nullable SkillRegistry skillRegistry,
                                     List<ContextEnricher> contextEnrichers,
                                     @Nullable AuditService auditService,
                                     @Nullable MemoryComposer memoryComposer,
                                     @Nullable WorkingMemoryPort workingMemoryPort,
                                     @Nullable MongoTemplate mongoTemplate,
                                     @Nullable CostTracker costTracker) {
        this(guardrailPipeline, semanticRoutingService, tokenBudgetManager, llmProviderFactory,
                promptBuilder, toolRegistry, properties, skillRegistry, contextEnrichers,
                auditService, memoryComposer, workingMemoryPort, mongoTemplate, costTracker, null);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        String traceId = UUID.randomUUID().toString();
        AtomicLong seq = new AtomicLong();
        String agentId = properties.getApi() != null ? properties.getApi().getAgentId() : null;
        emit(traceId, seq, ExecutionEventType.TURN_STARTED, "main", "turn started",
                Map.of(), agentId, request.sessionId(), null);
        try {
            return invokeInternal(request, traceId, seq, agentId);
        } catch (RuntimeException e) {
            emit(traceId, seq, ExecutionEventType.ERROR, "main", e.getClass().getSimpleName(),
                    Map.of("message", String.valueOf(e.getMessage())),
                    agentId, request.sessionId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Emits one execution event; never throws on the agent's hot path (a broken sink must
     * not break a turn). Errors from the publisher are swallowed by design.
     */
    private void emit(String traceId, AtomicLong seq, ExecutionEventType type, String phase,
                      String message, Map<String, Object> attributes, String agentId,
                      String sessionId, String error) {
        try {
            eventPublisher.publish(new ExecutionEvent(
                    null, traceId, seq.getAndIncrement(), Instant.now(), type,
                    agentId, sessionId, phase, message,
                    attributes == null ? Map.of() : attributes, null, error));
        } catch (Exception ignored) {
            // A trace sink is best-effort; never let it affect the response.
        }
    }

    private AgentResponse invokeInternal(AgentRequest request, String traceId, AtomicLong seq,
                                         String agentId) {
        long startTime = System.currentTimeMillis();
        log.info("[Pipeline] START userId={}, sessionId={}, messageLength={}, forceSkill={}",
                request.userId(), request.sessionId(),
                request.message() != null ? request.message().length() : 0,
                request.forceSkill());

        // Steps 1-8: guardrails, routing, skill, memory, prompt, budget, model selection.
        // Shared with the streaming endpoint — see TurnPipeline.
        String tracedSessionId = TurnPipeline.effectiveSessionId(request);
        var turn = turnPipeline.prepare(request, new TurnPipeline.Listener() {
            @Override
            public void onRouted(RoutingResult routing) {
                emit(traceId, seq, ExecutionEventType.ROUTING_DECIDED, "routing",
                        "routed to " + routing.skillName(),
                        Map.of("skill", routing.skillName(),
                                "method", String.valueOf(routing.method()),
                                "confidence", routing.confidence()),
                        agentId, tracedSessionId, null);
            }

            @Override
            public void onSkillSelected(SkillCard skillCard) {
                emit(traceId, seq, ExecutionEventType.SKILL_SELECTED, "main",
                        skillCard.meta().name(),
                        Map.of("skill", skillCard.meta().name(), "domain", skillCard.meta().domain()),
                        agentId, tracedSessionId, null);
            }
        });
        String effectiveSessionId = turn.effectiveSessionId();
        SkillCard skillCard = turn.skillCard();

        // 9. LLM call via LangChain4j with tool calling loop
        var toolsCalled = new ArrayList<String>();
        ChatModel model = llmProviderFactory.getModel(turn.modelAlias());
        emit(traceId, seq, ExecutionEventType.LLM_CALL, "main", "model " + turn.modelAlias(),
                Map.of("model", turn.modelAlias(), "toolsAvailable", turn.toolSpecs().size()),
                agentId, effectiveSessionId, null);
        var usage = new TurnPipeline.TokenTally();
        var rawResponse = executeLlmWithTools(model, turn.messages(), turn.toolSpecs(),
                toolsCalled, turn.toolContext(), skillCard, usage);
        log.info("[Pipeline] Step 9 — LLM call complete, tools called: {}", toolsCalled);
        for (String tool : toolsCalled) {
            emit(traceId, seq, ExecutionEventType.TOOL_CALLED, "main", tool,
                    Map.of("tool", tool), agentId, effectiveSessionId, null);
        }

        // 10. Output guardrails — with schema-validation auto-retry.
        var processedResponse = runOutputGuardrailsWithSchemaRetry(
                rawResponse, request, effectiveSessionId, skillCard, turn.attributes(),
                model, turn.messages(), turn.toolSpecs(), toolsCalled, turn.toolContext(), usage);

        // 11-13. Persist, price, audit.
        var response = turnPipeline.complete(request, turn, processedResponse, toolsCalled, startTime, usage);

        log.info("[Pipeline] END userId={}, skill={}, method={}, tokens={}, durationMs={}, dryRun={}",
                request.userId(), response.skillUsed(), response.routingMethod(),
                response.totalTokens(), response.durationMs(), response.dryRun());

        emit(traceId, seq, ExecutionEventType.TURN_COMPLETED, "main", "turn completed",
                Map.of("skill", response.skillUsed(),
                        "totalTokens", response.totalTokens(),
                        "durationMs", response.durationMs(),
                        "toolsCalled", toolsCalled.size()),
                agentId, effectiveSessionId, null);

        return response;
    }

    /**
     * Run the output-guardrail chain. When the {@code schema-validator} guardrail
     * raises a {@code BLOCK}, append a corrective-prompt user message and re-invoke
     * the LLM up to {@code agent.output.validation-retries} times.
     */
    private String runOutputGuardrailsWithSchemaRetry(
            String initialResponse,
            AgentRequest request,
            String effectiveSessionId,
            SkillCard skillCard,
            Map<String, Object> inputAttributes,
            ChatModel model,
            List<dev.langchain4j.data.message.ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            List<String> toolsCalled,
            ToolExecutionContext toolContext,
            TurnPipeline.TokenTally usage) {

        int maxRetries = Math.max(0, properties.getOutput().getValidationRetries());
        String currentResponse = initialResponse;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            var outputCtx = new GuardrailOutputContext(
                    currentResponse,
                    request.userId(),
                    effectiveSessionId,
                    skillCard.meta(),
                    inputAttributes
            );
            var detailed = guardrailPipeline.processOutputDetailed(outputCtx);
            if (!detailed.blocked() || !"schema-validator".equals(detailed.blockedBy())) {
                return detailed.processedText();
            }
            if (attempt == maxRetries) {
                log.warn("[Pipeline] Schema validation failed after {} retries, returning blocked response",
                        maxRetries);
                return detailed.processedText();
            }
            log.info("[Pipeline] Schema validation failed (attempt {}/{}), asking LLM to correct: {}",
                    attempt + 1, maxRetries + 1, detailed.blockedReason());
            messages.add(dev.langchain4j.data.message.AiMessage.from(currentResponse));
            messages.add(dev.langchain4j.data.message.UserMessage.from(
                    "Your previous response failed JSON-schema validation: "
                            + detailed.blockedReason()
                            + ". Re-emit a valid response that conforms to the schema; "
                            + "do not include any prose outside the JSON."));
            currentResponse = executeLlmWithTools(model, messages, toolSpecs, toolsCalled, toolContext, skillCard, usage);
        }
        return currentResponse;
    }

    /**
     * Execute the LLM with a tool calling loop. The LLM may request tool executions;
     * each tool result is fed back until the LLM produces a final text response.
     *
     * <p>When {@code skillCard} carries non-null {@code temperature} / {@code maxTokens}
     * values, both are applied to every {@link ChatRequest} in the loop — those skill-level
     * overrides took effect from v1.2.17 (previously parsed but never wired through).</p>
     */
    private String executeLlmWithTools(ChatModel model,
                                        List<dev.langchain4j.data.message.ChatMessage> messages,
                                        List<ToolSpecification> toolSpecs,
                                        List<String> toolsCalled,
                                        ToolExecutionContext toolContext,
                                        @Nullable SkillCard skillCard,
                                        TurnPipeline.TokenTally usage) {
        int maxIterations = 10;

        Double skillTemperature = skillCard != null ? skillCard.temperature() : null;
        Integer skillMaxTokens   = skillCard != null ? skillCard.maxTokens() : null;

        for (int i = 0; i < maxIterations; i++) {
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(messages);
            if (toolSpecs != null && !toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }
            if (skillTemperature != null) {
                requestBuilder.temperature(skillTemperature);
            }
            if (skillMaxTokens != null && skillMaxTokens > 0) {
                requestBuilder.maxOutputTokens(skillMaxTokens);
            }

            ChatResponse chatResponse = model.chat(requestBuilder.build());
            usage.add(chatResponse.tokenUsage());
            AiMessage aiMessage = chatResponse.aiMessage();
            messages.add(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                // No more tool calls — return the final text
                return aiMessage.text() != null ? aiMessage.text() : "";
            }

            // Execute each tool call and feed results back
            for (var toolRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolRequest.name();
                toolsCalled.add(toolName);
                log.info("[Pipeline] Tool call: {} with args: {}", toolName, toolRequest.arguments());

                String result = toolRegistry.executeTool(toolName, toolRequest.arguments(), toolContext);
                log.debug("[Pipeline] Tool result for {}: {}", toolName,
                        result != null && result.length() > 200 ? result.substring(0, 200) + "..." : result);

                messages.add(ToolExecutionResultMessage.from(toolRequest, result));
            }
        }

        // Max iterations reached — return whatever text is available
        log.warn("[Pipeline] Tool calling loop reached max iterations ({})", maxIterations);
        var lastMsg = messages.getLast();
        if (lastMsg instanceof AiMessage ai) {
            return ai.text() != null ? ai.text() : "Max tool iterations reached.";
        }
        return "Max tool iterations reached.";
    }
}
