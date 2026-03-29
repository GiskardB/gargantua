package ai.gargantua.autoconfigure;

import ai.gargantua.core.a2a.A2AClient;
import ai.gargantua.core.skill.SkillRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for the A2A agent card service and HTTP client.
 * Replaces the former capabilities-only configuration with the unified
 * A2A protocol support.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class CapabilitiesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentCardService.class)
    public AgentCardService agentCardService(AgentProperties properties,
                                              @Nullable SkillRegistry skillRegistry) {
        return new AgentCardService(properties, skillRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(A2AClient.class)
    public HttpA2AClient httpA2AClient() {
        return new HttpA2AClient();
    }
}
