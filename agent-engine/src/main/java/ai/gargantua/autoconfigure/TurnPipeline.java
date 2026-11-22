package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailPipelineResult;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.BudgetRequest;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.memory.composer.MemoryComposer;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parts of an agent turn that do not depend on <em>how</em> the model is called:
 * everything before the LLM ({@link #prepare}) and everything after it
 * ({@link #complete}).
 *
 * <p>The synchronous engine and the SSE controller used to carry their own copy of
 * these steps, and the copies drifted: the streaming one never re-ran the input
 * guardrails once the skill was known, so a skill's {@code allowed-roles} was not
 * enforced on {@code /api/agent/chat/stream}, and it wrote neither chat history nor an
 * audit event. Both callers now go through this class, so a step added here applies to
 * both and cannot be forgotten in one of them.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public class TurnPipeline {

    private static final Logger log = LoggerFactory.getLogger(TurnPipeline.class);

    /** Attribute {@code PiiInputGuardrail} sets when it masked something. */
    private static final String MASKED_MESSAGE_ATTR = "masked_message";
    private static final String OUTPUT_SCHEMA_ATTR = "output_schema";

    private static final String CHAT_MESSAGES = "chat_messages";
    private static final String CHAT_SESSIONS = "chat_sessions";

    private static final ComposedMemory EMPTY_MEMORY =
            new ComposedMemory(List.of(), List.of(), List.of(), 0);

    /** Hooks for a caller that reports progress while a turn is being prepared. */
    public interface Listener {
        Listener NONE = new Listener() { };

        /** First guardrail pass finished — called whether it passed or blocked. */
        default void onInputChecked(GuardrailPipelineResult result) { }

        default void onRouted(RoutingResult routing) { }

        default void onSkillSelected(SkillCard skillCard) { }
    }

    /**
     * Everything a caller needs to run the model for one turn.
     *
     * @param effectiveSessionId session id, tenant-prefixed for multi-tenant callers
     * @param attributes         mutable guardrail attributes; pass them on to the output
     *                           guardrails so the input phase's state (PII map, output
     *                           schema) reaches them
     * @param inputResult        outcome of the first input-guardrail pass
     * @param llmUserMessage     the user message as the model must see it — the PII-masked
     *                           text when input masking is on, otherwise the original
     * @param messages           mutable message list: system prompt, history, user message
     */
    public record PreparedTurn(
            String effectiveSessionId,
            Map<String, Object> attributes,
            GuardrailPipelineResult inputResult,
            RoutingResult routing,
            SkillCard skillCard,
            ComposedMemory memory,
            String llmUserMessage,
            List<dev.langchain4j.data.message.ChatMessage> messages,
            List<ToolSpecification> toolSpecs,
            String modelAlias,
            ToolExecutionContext toolContext) {
    }

    private final GuardrailPipeline guardrailPipeline;
    private final SemanticRoutingService semanticRoutingService;
    private final TokenBudgetManager tokenBudgetManager;
    private final LlmProviderFactory llmProviderFactory;
    private final PromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final List<ContextEnricher> contextEnrichers;
    @Nullable private final SkillRegistry skillRegistry;
    @Nullable private final AuditService auditService;
    @Nullable private final MemoryComposer memoryComposer;
    @Nullable private final WorkingMemoryPort workingMemoryPort;
    @Nullable private final MongoTemplate mongoTemplate;
    @Nullable private final CostTracker costTracker;

    public TurnPipeline(GuardrailPipeline guardrailPipeline,
                        SemanticRoutingService semanticRoutingService,
                        TokenBudgetManager tokenBudgetManager,
                        LlmProviderFactory llmProviderFactory,
                        PromptBuilder promptBuilder,
                        ToolRegistry toolRegistry,
                        AgentProperties properties,
                        @Nullable SkillRegistry skillRegistry,
                        @Nullable List<ContextEnricher> contextEnrichers,
                        @Nullable AuditService auditService,
                        @Nullable MemoryComposer memoryComposer,
                        @Nullable WorkingMemoryPort workingMemoryPort,
                        @Nullable MongoTemplate mongoTemplate,
                        @Nullable CostTracker costTracker) {
        this.guardrailPipeline = guardrailPipeline;
        this.semanticRoutingService = semanticRoutingService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.llmProviderFactory = llmProviderFactory;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        // Sorted once: enrichers do not change per request.
        this.contextEnrichers = (contextEnrichers == null || contextEnrichers.isEmpty())
                ? List.of()
                : contextEnrichers.stream()
                        .sorted(Comparator.comparingInt(ContextEnricher::order))
                        .toList();
        this.auditService = auditService;
        this.memoryComposer = memoryComposer;
        this.workingMemoryPort = workingMemoryPort;
        this.mongoTemplate = mongoTemplate;
        this.costTracker = costTracker;
    }

    /**
     * Runs input guardrails, routing, the post-routing guardrail pass, memory
     * composition, prompt assembly and model selection.
     *
     * @throws GuardrailBlockedException when either guardrail pass blocks
     * @throws SkillNotFoundException    when the routed or forced skill cannot be loaded
     */
    public PreparedTurn prepare(AgentRequest request, Listener listener) {
        Listener hooks = listener != null ? listener : Listener.NONE;
        SecurityContext securityContext = request.securityContext();
        Map<String, String> requestAttrs = request.contextAttributes() != null
                ? request.contextAttributes() : Map.of();

        String effectiveSessionId = effectiveSessionId(request);

        // Input guardrails, skill not yet known.
        var attributes = new HashMap<String, Object>(requestAttrs);
        if (securityContext != null) {
            attributes.put(RbacGuardrail.SECURITY_CONTEXT_KEY, securityContext);
        }
        var inputResult = guardrailPipeline.checkInput(new GuardrailInputContext(
                request.message(), request.userId(), effectiveSessionId, null, attributes));
        hooks.onInputChecked(inputResult);
        if (inputResult.blocked()) {
            log.warn("[Pipeline] Input BLOCKED by guardrail '{}': {}",
                    inputResult.blockedBy(), inputResult.reason());
            throw new GuardrailBlockedException(inputResult.blockedBy(), inputResult.reason(), Map.of());
        }

        // The model must see the masked text when PII masking produced one; the
        // original stays with the caller (chat history, audit).
        String llmUserMessage = attributes.get(MASKED_MESSAGE_ATTR) instanceof String masked
                ? masked : request.message();

        // Routing.
        List<SkillMeta> skills = skillRegistry != null ? skillRegistry.listMeta() : List.of();
        RoutingResult routing;
        if (request.forceSkill() != null && !request.forceSkill().isBlank()) {
            routing = RoutingResult.forced(request.forceSkill());
        } else {
            routing = semanticRoutingService.route(llmUserMessage, skills);
        }
        log.info("[Pipeline] Routing: skill='{}', method={}, confidence={}",
                routing.skillName(), routing.method(), "%.3f".formatted(routing.confidence()));
        hooks.onRouted(routing);

        SkillCard skillCard = loadSkillCard(routing.skillName());
        hooks.onSkillSelected(skillCard);

        // Second pass, now that the skill is known: this is the one that lets
        // RbacGuardrail (and any custom skill-aware guardrail) enforce allowed-roles.
        var postRouting = guardrailPipeline.checkInput(new GuardrailInputContext(
                request.message(), request.userId(), effectiveSessionId, skillCard.meta(), attributes));
        if (postRouting.blocked()) {
            log.warn("[Pipeline] Post-routing BLOCKED: user='{}', skill='{}', reason='{}'",
                    request.userId(), routing.skillName(), postRouting.reason());
            throw new GuardrailBlockedException(postRouting.blockedBy(), postRouting.reason(), Map.of());
        }
        if (skillCard.outputSchema() != null && !skillCard.outputSchema().isBlank()) {
            attributes.putIfAbsent(OUTPUT_SCHEMA_ATTR, skillCard.outputSchema());
        }

        ComposedMemory memory = composeMemory(request.userId(), effectiveSessionId);

        String systemPrompt = buildSystemPrompt(request, effectiveSessionId, skillCard,
                memory, llmUserMessage, requestAttrs);

        var toolDescriptions = toolRegistry.getFilteredTools(skillCard.allowedTools()).stream()
                .map(ToolDefinition::description)
                .toList();
        var allocation = tokenBudgetManager.allocate(new BudgetRequest(
                systemPrompt,
                "",
                skillCard.references() != null ? skillCard.references() : List.of(),
                List.of(),
                memory.knowledgeSegments(),
                toolDescriptions,
                llmUserMessage,
                properties.getMemory().getComposer().getMaxContextTokens()));

        var llmCtx = new LlmRoutingContext(
                request.userId(),
                effectiveSessionId,
                skillCard.meta().name(),
                skillCard.meta().domain(),
                llmUserMessage,
                llmUserMessage != null ? llmUserMessage.length() : 0,
                tokenBudgetManager.estimate(llmUserMessage),
                "default",
                LocalTime.now(),
                DayOfWeek.from(LocalDate.now()),
                requestAttrs);
        String alias = llmProviderFactory.resolveModelAlias(skillCard, llmCtx);

        var messages = llmProviderFactory.buildMessages(
                allocation.systemPrompt(), llmUserMessage, memory.workingMessages());

        return new PreparedTurn(
                effectiveSessionId,
                attributes,
                inputResult,
                routing,
                skillCard,
                memory,
                llmUserMessage,
                messages,
                toolRegistry.getToolSpecifications(skillCard.allowedTools()),
                alias,
                ToolExecutionContext.of(securityContext, effectiveSessionId));
    }

    /** The session id as stored and traced: tenant-prefixed for multi-tenant callers. */
    public static String effectiveSessionId(AgentRequest request) {
        SecurityContext securityContext = request.securityContext();
        return (securityContext != null && securityContext.isMultiTenant())
                ? securityContext.tenantId() + ":" + request.sessionId()
                : request.sessionId();
    }

    /**
     * Persists the exchange, prices it, and records the audit event. Persistence and
     * audit failures are logged and swallowed: the user already has an answer.
     *
     * @param responseText the final text, after output guardrails
     * @param startTimeMs  {@link System#currentTimeMillis()} taken when the turn began
     */
    public AgentResponse complete(AgentRequest request, PreparedTurn turn, String responseText,
                                  List<String> toolsCalled, long startTimeMs) {
        boolean dryRun = request.dryRunContext() != null && request.dryRunContext().active();
        if (!dryRun) {
            persist(request, turn, responseText);
        }

        int inputTokens = tokenBudgetManager.estimate(request.message());
        int outputTokens = tokenBudgetManager.estimate(responseText);
        var modelConfig = llmProviderFactory.getModelConfig(turn.modelAlias());
        double estimatedCostUsd = costTracker != null
                ? costTracker.estimateUsd(
                        modelConfig != null ? modelConfig.getProvider() : "",
                        modelConfig != null ? modelConfig.getModel() : "",
                        inputTokens, outputTokens)
                : 0.0;

        var response = new AgentResponse(
                responseText,
                turn.effectiveSessionId(),
                turn.routing().skillName(),
                toolsCalled,
                turn.routing().method(),
                turn.routing().confidence(),
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                estimatedCostUsd,
                System.currentTimeMillis() - startTimeMs,
                dryRun);

        if (auditService != null) {
            try {
                List<GuardrailResult> results = turn.inputResult().results();
                auditService.recordRequest(auditView(request, turn), response, turn.routing(), results);
            } catch (Exception e) {
                log.warn("Failed to record audit event: {}", e.getMessage());
            }
        }
        return response;
    }

    /**
     * The request as the audit trail may store it: {@code AuditEvent.userMessage} is
     * documented as the input <em>after</em> PII masking, so a masked turn is audited
     * with the masked text rather than the raw one.
     */
    private static AgentRequest auditView(AgentRequest request, PreparedTurn turn) {
        if (java.util.Objects.equals(request.message(), turn.llmUserMessage())) {
            return request;
        }
        return new AgentRequest(turn.llmUserMessage(), request.userId(), request.sessionId(),
                request.forceSkill(), request.dryRunContext(), request.contextAttributes(),
                request.securityContext());
    }

    private SkillCard loadSkillCard(String skillName) {
        if (skillRegistry == null) {
            var meta = new SkillMeta(skillName, "", "1.0.0", true, false, "general",
                    SkillSource.FILESYSTEM, Set.of());
            return new SkillCard(meta, "", List.of(), null, List.of(), null, null, null, null);
        }
        try {
            return skillRegistry.load(skillName);
        } catch (Exception e) {
            throw new SkillNotFoundException(skillName);
        }
    }

    private ComposedMemory composeMemory(String userId, String effectiveSessionId) {
        if (memoryComposer == null) {
            return EMPTY_MEMORY;
        }
        try {
            return memoryComposer.compose(
                    userId,
                    effectiveSessionId,
                    properties.getMemory().getComposer().getMaxContextTokens(),
                    properties.getMemory().getEnabledLayers());
        } catch (Exception e) {
            log.warn("Memory composition failed, using empty memory: {}", e.getMessage());
            return EMPTY_MEMORY;
        }
    }

    private String buildSystemPrompt(AgentRequest request, String effectiveSessionId,
                                     SkillCard skillCard, ComposedMemory memory,
                                     String llmUserMessage, Map<String, String> requestAttrs) {
        String skillName = skillCard.meta().name();
        String domain = skillCard.meta().domain();
        // Seeded with the request attributes (X-Context-* headers) so enrichers can read
        // them through ctx.attributes() — see docs/extending.md.
        var sections = new HashMap<String, String>(requestAttrs);
        var enricherInput = new EnricherContext(request.userId(), effectiveSessionId,
                skillName, domain, llmUserMessage, requestAttrs);
        for (ContextEnricher enricher : contextEnrichers) {
            if (enricher.targetSkill() != null && !enricher.targetSkill().equals(skillName)) {
                continue;
            }
            try {
                String section = enricher.enrich(enricherInput);
                if (section != null && !section.isBlank()) {
                    sections.put(enricher.sectionName(), section);
                }
            } catch (Exception e) {
                log.warn("Context enricher '{}' failed: {}", enricher.sectionName(), e.getMessage());
            }
        }
        return promptBuilder.build(skillCard, memory, new EnricherContext(
                request.userId(), effectiveSessionId, skillName, domain, llmUserMessage, sections));
    }

    private void persist(AgentRequest request, PreparedTurn turn, String responseText) {
        String sessionId = turn.effectiveSessionId();
        if (workingMemoryPort != null) {
            try {
                // Working memory is replayed to the model on the next turn, so it holds
                // what the model saw (masked), not what the user typed.
                workingMemoryPort.appendMessages(sessionId, List.of(
                        ChatMessage.userMessage(turn.llmUserMessage()),
                        ChatMessage.assistantMessage(responseText)));
            } catch (Exception e) {
                log.warn("Failed to persist working memory: {}", e.getMessage());
            }
        }
        if (mongoTemplate != null) {
            try {
                var now = Instant.now();
                // One insert for both messages. The assistant's timestamp is nudged so a
                // sort by timestamp always returns the pair in conversation order.
                mongoTemplate.insert(List.of(
                        historyDocument(request.userId(), sessionId, "user", request.message(), now),
                        historyDocument(request.userId(), sessionId, "assistant", responseText,
                                now.plusMillis(1))), CHAT_MESSAGES);
                mongoTemplate.upsert(
                        Query.query(Criteria.where("userId").is(request.userId())
                                .and("sessionId").is(sessionId)),
                        new Update()
                                .set("userId", request.userId())
                                .set("sessionId", sessionId)
                                .set("lastMessageAt", now)
                                .inc("messageCount", 2),
                        CHAT_SESSIONS);
            } catch (Exception e) {
                log.warn("Failed to persist chat history: {}", e.getMessage());
            }
        }
    }

    private static Document historyDocument(String userId, String sessionId, String role,
                                            String content, Instant timestamp) {
        return new Document()
                .append("userId", userId)
                .append("sessionId", sessionId)
                .append("role", role)
                .append("content", content)
                .append("timestamp", timestamp);
    }
}
