package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.TokenBudgetManager;
import ai.gargantua.core.secret.SecretResolver;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.memory.composer.MemoryComposer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Core auto-configuration that registers the main agent beans:
 * orchestrator engine, prompt builder, tool registry, token budget manager,
 * and flow DSL components.
 *
 * <p>Note: although the {@code @Component} stereotypes on
 * {@link DefaultOrchestratorEngine}, {@link ToolRegistry} etc. read like they
 * could replace these factories, downstream Spring Boot apps don't scan the
 * framework's package. The {@code @Bean} methods below are the actual
 * registration mechanism — keep them.</p>
 */

@AutoConfiguration(afterName = {
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "ai.gargantua.memory.autoconfigure.AgentMemoryAutoConfiguration"
})
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OrchestratorEngine.class)
    public DefaultOrchestratorEngine defaultOrchestratorEngine(
            GuardrailPipeline guardrailPipeline,
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
        return new DefaultOrchestratorEngine(
                guardrailPipeline, semanticRoutingService, tokenBudgetManager,
                llmProviderFactory, promptBuilder, toolRegistry, properties,
                skillRegistry, contextEnrichers, auditService,
                memoryComposer, workingMemoryPort, mongoTemplate, costTracker);
    }

    @Bean
    @ConditionalOnMissingBean(PromptBuilder.class)
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    /**
     * Resolves {@code ${secrets.*}} references from the process environment. Replace this
     * bean to source them from a vault instead; nothing else changes, because manifests
     * and configuration only ever carry the reference.
     */
    @Bean
    @ConditionalOnMissingBean(SecretResolver.class)
    public SecretResolver secretResolver() {
        return new EnvironmentSecretResolver();
    }

    /**
     * Composes the tool surface: compiled {@code @AgentTool} methods plus one provider per
     * reachable MCP server declared under {@code agent.mcp-client.servers}.
     */
    @Bean
    @ConditionalOnMissingBean(ToolRegistry.class)
    public ToolRegistry toolRegistry(ApplicationContext applicationContext,
                                     ObjectProvider<ToolResultCache> toolResultCacheProvider,
                                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                                     ObjectProvider<ai.gargantua.core.hitl.ApprovalStore> approvalStoreProvider,
                                     ObjectProvider<AgentProperties> agentPropertiesProvider,
                                     AgentProperties properties,
                                     SecretResolver secretResolver) {
        return new ToolRegistry(applicationContext, toolResultCacheProvider, meterRegistryProvider,
                approvalStoreProvider, agentPropertiesProvider,
                McpToolProviderFactory.fromProperties(properties, secretResolver));
    }

    // ── Token Budget (merged from TokenBudgetAutoConfiguration) ────

    @Bean
    @ConditionalOnMissingBean(TokenBudgetManager.class)
    public DefaultTokenBudgetManager defaultTokenBudgetManager() {
        return new DefaultTokenBudgetManager();
    }

    // ── Flow DSL (merged from FlowAutoConfiguration) ───────────────

    @Bean
    @ConditionalOnMissingBean(FlowRegistry.class)
    public FlowRegistry flowRegistry(ApplicationContext applicationContext) {
        return new FlowRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(FlowExecutor.class)
    public FlowExecutor flowExecutor(OrchestratorEngine orchestrator) {
        return new FlowExecutor(orchestrator);
    }
}
