package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for LLM provider and router beans.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class LlmProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmRouter.class)
    public LlmRouter llmRouter(AgentProperties properties) {
        return new LlmRouter(properties);
    }

    @Bean
    @ConditionalOnMissingBean(LlmProviderFactory.class)
    public LlmProviderFactory llmProviderFactory(AgentProperties properties, LlmRouter llmRouter) {
        return new LlmProviderFactory(properties, llmRouter);
    }
}
