package io.agentkit.autoconfigure;

import io.agentkit.core.orchestrator.OrchestratorEngine;
import io.agentkit.core.orchestrator.TokenBudgetManager;
import io.agentkit.core.skill.SkillRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Core auto-configuration that registers the main agent beans.
 */
@AutoConfiguration
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
            @Nullable SkillRegistry skillRegistry) {
        return new DefaultOrchestratorEngine(
                guardrailPipeline, semanticRoutingService, tokenBudgetManager,
                llmProviderFactory, promptBuilder, toolRegistry, properties, skillRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(PromptBuilder.class)
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    @Bean
    @ConditionalOnMissingBean(ToolRegistry.class)
    public ToolRegistry toolRegistry(ApplicationContext applicationContext) {
        return new ToolRegistry(applicationContext);
    }
}
