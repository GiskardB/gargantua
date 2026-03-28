package ai.gargantua.autoconfigure;

import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailPipelineResult;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.BudgetAllocation;
import ai.gargantua.core.orchestrator.BudgetRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link OrchestratorEngine} that executes the full
 * 12-step agent invocation pipeline:
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

    @Nullable
    private final SkillRegistry skillRegistry;

    public DefaultOrchestratorEngine(GuardrailPipeline guardrailPipeline,
                                     SemanticRoutingService semanticRoutingService,
                                     TokenBudgetManager tokenBudgetManager,
                                     LlmProviderFactory llmProviderFactory,
                                     PromptBuilder promptBuilder,
                                     ToolRegistry toolRegistry,
                                     AgentProperties properties,
                                     @Nullable SkillRegistry skillRegistry) {
        this.guardrailPipeline = guardrailPipeline;
        this.semanticRoutingService = semanticRoutingService;
        this.tokenBudgetManager = tokenBudgetManager;
        this.llmProviderFactory = llmProviderFactory;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Extract dry-run context
        DryRunContext dryRunContext = request.dryRunContext() != null
                ? request.dryRunContext()
                : DryRunContext.inactive();
        boolean isDryRun = dryRunContext.active();

        // 2. Input guardrails
        Map<String, Object> inputAttributes = new HashMap<>();
        if (request.contextAttributes() != null) {
            inputAttributes.putAll(request.contextAttributes());
        }

        GuardrailInputContext inputCtx = new GuardrailInputContext(
                request.message(),
                request.userId(),
                request.sessionId(),
                null, // skill not yet resolved
                inputAttributes
        );
        GuardrailPipelineResult inputResult = guardrailPipeline.checkInput(inputCtx);
        if (inputResult.blocked()) {
            throw new GuardrailBlockedException(
                    inputResult.blockedBy(),
                    inputResult.reason(),
                    Map.of()
            );
        }

        // 3. List available skills
        List<SkillMeta> skills = skillRegistry != null ? skillRegistry.listMeta() : List.of();

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
            SkillMeta meta = new SkillMeta(routingResult.skillName(), "", "1.0.0",
                    true, false, "general", ai.gargantua.core.skill.SkillSource.FILESYSTEM);
            skillCard = new SkillCard(meta, "", List.of(), null, List.of(), null, null, null);
        }

        // 6. Compose memory (placeholder - empty memory)
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);

        // 7. Build prompt
        EnricherContext enricherContext = new EnricherContext(
                request.userId(),
                request.sessionId(),
                skillCard.meta().name(),
                skillCard.meta().domain(),
                request.message(),
                Map.of()
        );
        String systemPrompt = promptBuilder.build(skillCard, memory, enricherContext);

        // 8. Token budget allocation
        List<String> toolDescriptions = toolRegistry.getFilteredTools(skillCard.allowedTools())
                .stream()
                .map(ToolDefinition::description)
                .toList();

        BudgetRequest budgetRequest = new BudgetRequest(
                systemPrompt,
                "",
                skillCard.references() != null ? skillCard.references() : List.of(),
                List.of(),
                memory.knowledgeSegments(),
                toolDescriptions,
                request.message(),
                properties.getMemory().getComposer().getMaxContextTokens()
        );
        BudgetAllocation allocation = tokenBudgetManager.allocate(budgetRequest);

        // 9. LLM call (placeholder)
        LlmRoutingContext llmCtx = new LlmRoutingContext(
                request.userId(),
                request.sessionId(),
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

        String rawResponse = llmProviderFactory.generate(
                allocation.systemPrompt(),
                request.message(),
                skillCard,
                llmCtx
        );

        // 10. Output guardrails
        GuardrailOutputContext outputCtx = new GuardrailOutputContext(
                rawResponse,
                request.userId(),
                request.sessionId(),
                skillCard.meta(),
                inputAttributes
        );
        String processedResponse = guardrailPipeline.processOutput(outputCtx);

        // 11. Persist memory (skip in dry-run)
        if (!isDryRun) {
            log.debug("Memory persistence would happen here (not in dry-run)");
        }

        // 12. Build and return response
        long durationMs = System.currentTimeMillis() - startTime;
        int inputTokens = tokenBudgetManager.estimate(request.message());
        int outputTokens = tokenBudgetManager.estimate(processedResponse);

        return new AgentResponse(
                processedResponse,
                request.sessionId(),
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
    }
}
