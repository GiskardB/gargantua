package ai.gargantua.autoconfigure;

import ai.gargantua.core.skill.SkillRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for capabilities service.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class CapabilitiesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CapabilitiesService.class)
    public CapabilitiesService capabilitiesService(AgentProperties properties,
                                                    @Nullable SkillRegistry skillRegistry) {
        return new CapabilitiesService(properties, skillRegistry);
    }
}
