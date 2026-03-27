package io.agentkit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for semantic routing and LLM routing services.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class SemanticRoutingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RoutingService.class)
    public RoutingService routingService(AgentProperties properties) {
        return new RoutingService(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SemanticRoutingService.class)
    public SemanticRoutingService semanticRoutingService(AgentProperties properties,
                                                         RoutingService routingService) {
        return new SemanticRoutingService(properties, routingService);
    }
}
