package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for cost tracking.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.cost-tracking", name = "enabled", havingValue = "true")
public class CostTrackingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CostTracker.class)
    public CostTracker costTracker(AgentProperties properties) {
        return new CostTracker(properties);
    }
}
