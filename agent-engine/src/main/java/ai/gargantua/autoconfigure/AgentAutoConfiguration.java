package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Core auto-configuration. {@link DefaultOrchestratorEngine}, {@link ToolRegistry}
 * and the guardrails are registered via {@code @Component} scanning, so this class
 * only needs to declare the beans that don't carry a stereotype annotation
 * (currently just {@link PromptBuilder}).
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "ai.gargantua.memory.autoconfigure.AgentMemoryAutoConfiguration"
})
@EnableConfigurationProperties(AgentProperties.class)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PromptBuilder.class)
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }
}
