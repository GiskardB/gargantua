package ai.gargantua.autoconfigure;

import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for the guardrail pipeline. The individual built-in guardrails
 * (max-length, prompt-injection, topic-scope, PII input/output, rate-limit, schema-validator,
 * scope-validator, disclaimer-injector) are registered via {@code @Component} scanning;
 * users override any of them by declaring their own {@code @Bean} of the same type
 * (Spring's {@code @ConditionalOnMissingBean} mechanism).
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class GuardrailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GuardrailPipeline.class)
    public GuardrailPipeline guardrailPipeline(List<InputGuardrail> inputGuardrails,
                                                List<OutputGuardrail> outputGuardrails,
                                                AgentProperties properties) {
        return new GuardrailPipeline(inputGuardrails, outputGuardrails, properties);
    }
}
