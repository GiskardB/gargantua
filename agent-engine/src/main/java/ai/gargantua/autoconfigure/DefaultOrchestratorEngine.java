package ai.gargantua.autoconfigure;

import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.memory.composer.MemoryComposer;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.orchestrator.BudgetRequest;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.tool.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final SemanticRoutingService semanticRoutingService;
    private final TokenBudgetManager tokenBudgetManager;
    private final LlmProviderFactory llmProviderFactory;
    private final PromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final List<ContextEnricher> contextEnrichers;

    @Nullable
    private final SkillRegistry skillRegistry;

    @Nullable
    private final AuditService auditService;

    @Nullable
    private final MemoryComposer memoryComposer;

    @Nullable
    private final WorkingMemoryPort workingMemoryPort;

    @Nullable
    private final MongoTemplate mongoTemplate;

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
                                     @Nullable MongoTemplate mongoTemplate) {
        this.guardrailPipeline = guardrailPipeline;
        this.semanticRoutingService = semanticRoutingService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.llmProviderFactory = llmProviderFactory;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        this.contextEnrichers = contextEnrichers != null ? contextEnrichers : List.of();
        this.auditService = auditService;
        this.memoryComposer = memoryComposer;
        this.workingMemoryPort = workingMemoryPort;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[Pipeline] START userId={}, sessionId={}, messageLength={}, forceSkill={}",
                request.userId(), request.sessionId(),
                request.message() != null ? request.message().length() : 0,
                request.forceSkill());

        // 1. Extract dry-run context
        var isDryRun = request.dryRunContext() != null && request.dryRunContext().active();
        if (isDryRun) {
            log.info("[Pipeline] Dry-run mode ACTIVE — no persistence, no side effects");
        }

        // Tenant-aware session id: prefix with tenantId when available
        var securityContext = request.securityContext();
        String effectiveSessionId = (securityContext != null && securityContext.isMultiTenant())
                ? securityContext.tenantId() + ":" + request.sessionId()
                : request.sessionId();
        log.debug("[Pipeline] effectiveSessionId={}, tenantId={}, roles={}",
                effectiveSessionId,
                securityContext != null ? securityContext.tenantId() : "none",
                securityContext != null ? securityContext.roles() : "none");

        // 2. Input guardrails
        var inputAttributes = new HashMap<String, Object>();
        if (request.contextAttributes() != null) {
            inputAttributes.putAll(request.contextAttributes());
        }
        // Propagate SecurityContext into guardrail attributes for RBAC guardrail
        if (securityContext != null) {
            inputAttributes.put("gargantua.securityContext", securityContext);
        }

        var inputCtx = new GuardrailInputContext(
                request.message(),
                request.userId(),
                effectiveSessionId,
                null, // skill not yet resolved
                inputAttributes
        );
        var inputResult = guardrailPipeline.checkInput(inputCtx);
        if (inputResult.blocked()) {
            log.warn("[Pipeline] Input BLOCKED by guardrail '{}': {}", inputResult.blockedBy(), inputResult.reason());
            throw new GuardrailBlockedException(
                    inputResult.blockedBy(),
                    inputResult.reason(),
                    Map.of()
            );
        }
        log.debug("[Pipeline] Step 2 — Input guardrails passed ({} checks)", inputResult.results().size());

        // 3. List available skills
        var skills = skillRegistry != null ? skillRegistry.listMeta() : List.<SkillMeta>of();
        log.debug("[Pipeline] Step 3 — {} active skills available", skills.size());

        // 4. Route to skill
        RoutingResult routingResult;
        if (request.forceSkill() != null && !request.forceSkill().isBlank()) {
            routingResult = RoutingResult.forced(request.forceSkill());
            log.info("[Pipeline] Step 4 — Routing FORCED to skill '{}'", request.forceSkill());
        } else {
            routingResult = semanticRoutingService.route(request.message(), skills);
            log.info("[Pipeline] Step 4 — Routing: skill='{}', method={}, confidence={}",
                    routingResult.skillName(), routingResult.method(),
                    "%.3f".formatted(routingResult.confidence()));
        }

        // 5. Load skill card
        SkillCard skillCard;
        if (skillRegistry != null) {
            try {
                skillCard = skillRegistry.load(routingResult.skillName());
            } catch (Exception e) {
                throw new SkillNotFoundException(routingResult.skillName());
            }
        } else {
            // No skill registry available, use a minimal skill card
            var meta = new SkillMeta(routingResult.skillName(), "", "1.0.0",
                    true, false, "general", ai.gargantua.core.skill.SkillSource.FILESYSTEM, java.util.Set.of());
            skillCard = new SkillCard(meta, "", List.of(), null, List.of(), null, null, null, null);
        }

        // 5b. Post-routing RBAC check — now that the skill is resolved, re-run input guardrails
        //     with the activated skill so that RbacGuardrail can enforce role-based access control.
        var postRoutingCtx = new GuardrailInputContext(
                request.message(),
                request.userId(),
                effectiveSessionId,
                skillCard.meta(),
                inputAttributes
        );
        var rbacResult = guardrailPipeline.checkInput(postRoutingCtx);
        if (rbacResult.blocked()) {
            log.warn("[Pipeline] Post-routing RBAC BLOCKED: user='{}', skill='{}', reason='{}'",
                    request.userId(), routingResult.skillName(), rbacResult.reason());
            throw new GuardrailBlockedException(
                    rbacResult.blockedBy(),
                    rbacResult.reason(),
                    Map.of()
            );
        }
        log.debug("[Pipeline] Step 5b — Post-routing RBAC passed for skill '{}'", routingResult.skillName());

        // 6. Compose memory from layers the skill needs
        ComposedMemory memory;
        if (memoryComposer != null) {
            try {
                memory = memoryComposer.compose(
                        request.userId(),
                        effectiveSessionId,
                        properties.getMemory().getComposer().getMaxContextTokens(),
                        skillCard.enabledMemoryLayers()
                );
            } catch (Exception e) {
                log.warn("Memory composition failed, using empty memory: {}", e.getMessage());
                memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
            }
        } else {
            memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
        }
        log.info("[Pipeline] Step 6 — Memory composed: working={} msgs, episodic={} summaries, knowledge={} segments, tokens={}",
                memory.workingMessages().size(), memory.episodicSummaries().size(),
                memory.knowledgeSegments().size(), memory.estimatedTokens());

        // 7. Build prompt — run context enrichers first
        var enricherAttributes = new HashMap<String, String>();
        var enricherCtxForEnrichers = new EnricherContext(
                request.userId(),
                effectiveSessionId,
                skillCard.meta().name(),
                skillCard.meta().domain(),
                request.message(),
                Map.of()
        );
        contextEnrichers.stream()
                .sorted(Comparator.comparingInt(ContextEnricher::order))
                .filter(e -> e.targetSkill() == null || e.targetSkill().equals(skillCard.meta().name()))
                .forEach(e -> {
                    try {
                        String section = e.enrich(enricherCtxForEnrichers);
                        if (section != null && !section.isBlank()) {
                            enricherAttributes.put(e.sectionName(), section);
                        }
                    } catch (Exception ex) {
                        log.warn("Context enricher '{}' failed: {}", e.sectionName(), ex.getMessage());
                    }
                });

        var enricherContext = new EnricherContext(
                request.userId(),
                effectiveSessionId,
                skillCard.meta().name(),
                skillCard.meta().domain(),
                request.message(),
                enricherAttributes
        );
        var systemPrompt = promptBuilder.build(skillCard, memory, enricherContext);
        log.debug("[Pipeline] Step 7 — System prompt built ({} chars, {} enricher sections)",
                systemPrompt.length(), enricherAttributes.size());

        // 8. Token budget allocation
        var toolDescriptions = toolRegistry.getFilteredTools(skillCard.allowedTools())
                .stream()
                .map(ToolDefinition::description)
                .toList();

        var budgetRequest = new BudgetRequest(
                systemPrompt,
                "",
                skillCard.references() != null ? skillCard.references() : List.of(),
                List.of(),
                memory.knowledgeSegments(),
                toolDescriptions,
                request.message(),
                properties.getMemory().getComposer().getMaxContextTokens()
        );
        var allocation = tokenBudgetManager.allocate(budgetRequest);

        // 9. LLM call via LangChain4j with tool calling loop
        var llmCtx = new LlmRoutingContext(
                request.userId(),
                effectiveSessionId,
                skillCard.meta().name(),
                skillCard.meta().domain(),
                request.message(),
                request.message() != null ? request.message().length() : 0,
                tokenBudgetManager.estimate(request.message()),
                "default",
                LocalTime.now(),
                DayOfWeek.from(java.time.LocalDate.now()),
                request.contextAttributes() != null ? request.contextAttributes() : Map.of()
        );

        // Build tool specifications for allowed tools
        var toolSpecs = toolRegistry.getToolSpecifications(skillCard.allowedTools());
        var toolsCalled = new ArrayList<String>();

        String alias = llmProviderFactory.resolveModelAlias(skillCard, llmCtx);
        ChatModel model = llmProviderFactory.getModel(alias);

        var messages = llmProviderFactory.buildMessages(
                allocation.systemPrompt(), request.message(), memory.workingMessages());

        var rawResponse = executeLlmWithTools(model, messages, toolSpecs, toolsCalled);
        log.info("[Pipeline] Step 9 — LLM call complete, tools called: {}", toolsCalled);

        // 10. Output guardrails
        var outputCtx = new GuardrailOutputContext(
                rawResponse,
                request.userId(),
                request.sessionId(),
                skillCard.meta(),
                inputAttributes
        );
        var processedResponse = guardrailPipeline.processOutput(outputCtx);
        log.debug("[Pipeline] Step 10 — Output guardrails applied (response {} chars)", processedResponse.length());

        // 11. Persist memory (skip in dry-run)
        if (!isDryRun) {
            if (workingMemoryPort != null) {
                try {
                    workingMemoryPort.appendMessage(effectiveSessionId,
                            ChatMessage.userMessage(request.message()));
                    workingMemoryPort.appendMessage(effectiveSessionId,
                            ChatMessage.assistantMessage(processedResponse));
                } catch (Exception e) {
                    log.warn("Failed to persist working memory: {}", e.getMessage());
                }
            }
            // Persist chat history to MongoDB
            if (mongoTemplate != null) {
                try {
                    var now = Instant.now();
                    persistChatMessage(request.userId(), effectiveSessionId, "user", request.message(), now);
                    persistChatMessage(request.userId(), effectiveSessionId, "assistant", processedResponse, now);
                    mongoTemplate.upsert(
                            Query.query(Criteria.where("userId").is(request.userId())
                                    .and("sessionId").is(effectiveSessionId)),
                            new Update()
                                    .set("userId", request.userId())
                                    .set("sessionId", effectiveSessionId)
                                    .set("lastMessageAt", now)
                                    .inc("messageCount", 2),
                            "chat_sessions"
                    );
                } catch (Exception e) {
                    log.warn("Failed to persist chat history: {}", e.getMessage());
                }
            }
        }

        // 12. Build and return response
        long durationMs = System.currentTimeMillis() - startTime;
        int inputTokens = tokenBudgetManager.estimate(request.message());
        int outputTokens = tokenBudgetManager.estimate(processedResponse);

        var response = new AgentResponse(
                processedResponse,
                effectiveSessionId,
                routingResult.skillName(),
                toolsCalled,
                routingResult.method(),
                routingResult.confidence(),
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                0.0,
                durationMs,
                isDryRun
        );

        // 13. Record audit trail
        if (auditService != null) {
            try {
                auditService.recordRequest(request, response, routingResult, inputResult.results());
                log.debug("[Pipeline] Step 13 — Audit event recorded");
            } catch (Exception e) {
                log.warn("Failed to record audit event: {}", e.getMessage());
            }
        }

        log.info("[Pipeline] END userId={}, skill={}, method={}, tokens={}, durationMs={}, dryRun={}",
                request.userId(), routingResult.skillName(), routingResult.method(),
                response.totalTokens(), durationMs, isDryRun);

        return response;
    }

    /**
     * Execute the LLM with a tool calling loop. The LLM may request tool executions;
     * each tool result is fed back until the LLM produces a final text response.
     */
    private String executeLlmWithTools(ChatModel model,
                                        List<dev.langchain4j.data.message.ChatMessage> messages,
                                        List<ToolSpecification> toolSpecs,
                                        List<String> toolsCalled) {
        int maxIterations = 10;

        for (int i = 0; i < maxIterations; i++) {
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(messages);
            if (toolSpecs != null && !toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }

            ChatResponse chatResponse = model.chat(requestBuilder.build());
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

                String result = toolRegistry.executeTool(toolName, toolRequest.arguments());
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

    private void persistChatMessage(String userId, String sessionId, String role, String content, Instant timestamp) {
        var doc = new HashMap<String, Object>();
        doc.put("userId", userId);
        doc.put("sessionId", sessionId);
        doc.put("role", role);
        doc.put("content", content);
        doc.put("timestamp", timestamp);
        mongoTemplate.insert(new org.bson.Document(doc), "chat_messages");
    }
}
