package ai.gargantua.autoconfigure;

import ai.gargantua.core.hitl.ApprovalStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for Human-in-the-Loop coordinator.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.hitl", name = "enabled", havingValue = "true")
public class HitlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HitlCoordinator.class)
    public HitlCoordinator hitlCoordinator(AgentProperties properties,
                                            @Nullable ApprovalStore approvalStore) {
        return new HitlCoordinator(properties, approvalStore);
    }
}
