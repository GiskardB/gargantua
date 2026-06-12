package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.cost.MongoCostTrackingRepository;
import ai.gargantua.adapters.web.AgentKitExceptionHandler;
import ai.gargantua.adapters.web.ApprovalController;
import ai.gargantua.adapters.web.CapabilitiesController;
import ai.gargantua.adapters.web.ChatController;
import ai.gargantua.adapters.web.ChatExportController;
import ai.gargantua.adapters.web.ChatHistoryController;
import ai.gargantua.adapters.web.ChatStreamController;
import ai.gargantua.adapters.web.CostAdminController;
import ai.gargantua.adapters.web.GuardrailAdminController;
import ai.gargantua.adapters.web.LlmRoutingAdminController;
import ai.gargantua.adapters.web.OpenApiConfig;
import ai.gargantua.adapters.web.SessionController;
import ai.gargantua.adapters.web.SkillAdminController;
import ai.gargantua.adapters.web.ToolCacheAdminController;
import ai.gargantua.adapters.web.WebMvcConfig;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.memory.composer.MemoryComposer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Auto-configuration that registers all web-layer controllers so they are
 * discovered regardless of the consuming application's base package.
 *
 * <p>Runs after the core auto-configurations so all dependencies
 * (OrchestratorEngine, SkillRegistry, etc.) are already available.</p>
 */
@AutoConfiguration(
        after = {
                AgentAutoConfiguration.class,
                SkillRegistryAutoConfiguration.class,
                HitlAutoConfiguration.class,
                CapabilitiesAutoConfiguration.class,
                GuardrailAutoConfiguration.class,
                CostTrackingAutoConfiguration.class,
                ToolCacheAutoConfiguration.class,
                AuditAutoConfiguration.class,
                EmbeddedProfileAutoConfiguration.class
        },
        afterName = {
                "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
                "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
        }
)
@Import({
        WebMvcConfig.class,
        OpenApiConfig.class,
        AgentKitExceptionHandler.class
})
public class WebAutoConfiguration {

    // ── Core controllers (always available) ─────────────────────

    @Bean
    @ConditionalOnMissingBean(ChatController.class)
    public ChatController chatController(OrchestratorEngine orchestratorEngine) {
        return new ChatController(orchestratorEngine);
    }

    @Bean
    @ConditionalOnMissingBean(ChatStreamController.class)
    public ChatStreamController chatStreamController(
            OrchestratorEngine orchestratorEngine,
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
        return new ChatStreamController(orchestratorEngine, llmProviderFactory,
                guardrailPipeline, semanticRoutingService, tokenBudgetManager,
                promptBuilder, toolRegistry, properties, contextEnrichers,
                skillRegistry, memoryComposer, workingMemoryPort, costTracker, approvalStore);
    }

    @Bean
    @ConditionalOnMissingBean(SessionController.class)
    public SessionController sessionController() {
        return new SessionController();
    }

    @Bean
    @ConditionalOnMissingBean(SkillAdminController.class)
    public SkillAdminController skillAdminController(SkillRegistry skillRegistry) {
        return new SkillAdminController(skillRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailAdminController.class)
    public GuardrailAdminController guardrailAdminController(
            List<InputGuardrail> inputGuardrails,
            List<OutputGuardrail> outputGuardrails) {
        return new GuardrailAdminController(inputGuardrails, outputGuardrails);
    }

    @Bean
    @ConditionalOnMissingBean(LlmRoutingAdminController.class)
    public LlmRoutingAdminController llmRoutingAdminController(AgentProperties properties,
                                                               LlmRouter llmRouter,
                                                               LlmProviderFactory llmProviderFactory) {
        return new LlmRoutingAdminController(properties, llmRouter, llmProviderFactory);
    }

    // ── A2A ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(CapabilitiesController.class)
    @ConditionalOnBean(AgentCardService.class)
    public CapabilitiesController capabilitiesController(
            AgentCardService agentCardService,
            @Nullable OrchestratorEngine orchestratorEngine) {
        return new CapabilitiesController(agentCardService, orchestratorEngine);
    }

    // ── HITL ────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ApprovalController.class)
    @ConditionalOnBean(ApprovalStore.class)
    public ApprovalController approvalController(ApprovalStore approvalStore) {
        return new ApprovalController(approvalStore);
    }

    // ── MongoDB-dependent ───────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ChatHistoryController.class)
    @ConditionalOnBean(MongoTemplate.class)
    public ChatHistoryController chatHistoryController(MongoTemplate mongoTemplate) {
        return new ChatHistoryController(mongoTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ChatExportController.class)
    @ConditionalOnBean(MongoTemplate.class)
    public ChatExportController chatExportController(MongoTemplate mongoTemplate) {
        return new ChatExportController(mongoTemplate);
    }


    @Bean
    @ConditionalOnMissingBean(CostAdminController.class)
    @ConditionalOnBean(MongoCostTrackingRepository.class)
    public CostAdminController costAdminController(MongoCostTrackingRepository costRepository) {
        return new CostAdminController(costRepository);
    }

    // ── Redis-dependent ─────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(ToolCacheAdminController.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public ToolCacheAdminController toolCacheAdminController(StringRedisTemplate redisTemplate) {
        return new ToolCacheAdminController(redisTemplate);
    }
}
