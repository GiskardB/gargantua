package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for skill registry components.
 * The actual SkillRegistry implementations are provided by the agent-adapters module.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class SkillRegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "skillMdParser")
    public SkillMdParser skillMdParser() {
        return new SkillMdParser();
    }
}
