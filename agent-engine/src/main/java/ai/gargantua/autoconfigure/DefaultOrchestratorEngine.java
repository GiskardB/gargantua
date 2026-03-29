package ai.gargantua.autoconfigure;

import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailPipelineResult;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.orchestrator.BudgetAllocation;
import ai.gargantua.core.orchestrator.BudgetRequest;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.RoutingMethod;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.session.DryRunContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
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

    public DefaultOrchestratorEngine(GuardrailPipeline guardrailPipeline,
                                     SemanticRoutingService semanticRoutingService,
                                     TokenBudgetManager tokenBudgetManager,
                                     LlmProviderFactory llmProviderFactory,
                                     PromptBuilder promptBuilder,
                                     ToolRegistry toolRegistry,
                                     AgentProperties properties,
                                     @Nullable SkillRegistry skillRegistry,
                                     List<ContextEnricher> contextEnrichers,
                                     @Nullable AuditService auditService) {
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
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Extract dry-run context
        var dryRunContext = request.dryRunContext() != null
                ? request.dryRunContext()
                : DryRunContext.inactive();
        var isDryRun = dryRunContext.active();

        // Tenant-aware session id: prefix with tenantId when available
        var securityContext = request.securityContext();
        String effectiveSessionId = (securityContext != null && securityContext.isMultiTenant())
                ? securityContext.tenantId() + ":" + request.sessionId()
                : request.sessionId();

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
            throw new GuardrailBlockedException(
                    inputResult.blockedBy(),
                    inputResult.reason(),
                    Map.of()
            );
        }

        // 3. List available skills
        var skills = skillRegistry != null ? skillRegistry.listMeta() : List.<SkillMeta>of();

        // 4. Route to skill
        RoutingResult routingResult;
        if (request.forceSkill() != null && !request.forceSkill().isBlank()) {
            routingResult = RoutingResult.forced(request.forceSkill());
        } else {
            routingResult = semanticRoutingService.route(request.message(), skills);
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

        // 6. Compose memory (placeholder - empty memory)
        var memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);

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

        // 9. LLM call (placeholder)
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

        var rawResponse = llmProviderFactory.generate(
                allocation.systemPrompt(),
                request.message(),
                skillCard,
                llmCtx
        );

        // 10. Output guardrails
        var outputCtx = new GuardrailOutputContext(
                rawResponse,
                request.userId(),
                request.sessionId(),
                skillCard.meta(),
                inputAttributes
        );
        var processedResponse = guardrailPipeline.processOutput(outputCtx);

        // 11. Persist memory (skip in dry-run)
        if (!isDryRun) {
            log.debug("Memory persistence would happen here (not in dry-run)");
        }

        // 12. Build and return response
        long durationMs = System.currentTimeMillis() - startTime;
        int inputTokens = tokenBudgetManager.estimate(request.message());
        int outputTokens = tokenBudgetManager.estimate(processedResponse);

        var response = new AgentResponse(
                processedResponse,
                effectiveSessionId,
                routingResult.skillName(),
                List.of(),
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
            } catch (Exception e) {
                log.warn("Failed to record audit event: {}", e.getMessage());
            }
        }

        return response;
    }
}
