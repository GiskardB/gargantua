package ai.gargantua.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for LLM provider and router beans.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class LlmProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmRouter.class)
    public LlmRouter llmRouter(AgentProperties properties,
                               ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LlmRouter(properties, meterRegistryProvider);
    }

    @Bean
    @ConditionalOnMissingBean(LlmProviderFactory.class)
    public LlmProviderFactory llmProviderFactory(AgentProperties properties,
                                                 LlmRouter llmRouter,
                                                 ApplicationContext applicationContext,
                                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LlmProviderFactory(properties, llmRouter, applicationContext, meterRegistryProvider);
    }
}
