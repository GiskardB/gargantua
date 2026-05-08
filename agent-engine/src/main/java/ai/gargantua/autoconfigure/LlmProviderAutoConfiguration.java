package ai.gargantua.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the LLM provider factory. {@link LlmRouter} is registered
 * via {@code @Component} scanning; only {@link LlmProviderFactory} needs an explicit
 * factory because it doesn't carry a stereotype annotation (so it can be replaced
 * cleanly with {@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class LlmProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmProviderFactory.class)
    public LlmProviderFactory llmProviderFactory(AgentProperties properties,
                                                 LlmRouter llmRouter,
                                                 ApplicationContext applicationContext,
                                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LlmProviderFactory(properties, llmRouter, applicationContext, meterRegistryProvider);
    }
}
