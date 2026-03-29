package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.DisclaimerInjectorGuardrail;
import ai.gargantua.autoconfigure.guardrails.MaxLengthGuardrail;
import ai.gargantua.autoconfigure.guardrails.PiiInputGuardrail;
import ai.gargantua.autoconfigure.guardrails.PiiOutputGuardrail;
import ai.gargantua.autoconfigure.guardrails.PromptInjectionGuardrail;
import ai.gargantua.autoconfigure.guardrails.RateLimitGuardrail;
import ai.gargantua.autoconfigure.guardrails.SchemaValidatorGuardrail;
import ai.gargantua.autoconfigure.guardrails.ScopeValidatorGuardrail;
import ai.gargantua.autoconfigure.guardrails.TopicScopeGuardrail;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for the guardrail pipeline and all built-in guardrails.
 * Registers input guardrails (max-length, prompt injection, topic scope, PII masking,
 * rate limit) and output guardrails (PII redaction, disclaimer injection, scope validation,
 * schema validation). Each guardrail is {@code @ConditionalOnMissingBean} for easy override.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class GuardrailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MaxLengthGuardrail.class)
    public MaxLengthGuardrail maxLengthGuardrail(AgentProperties properties) {
        return new MaxLengthGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PromptInjectionGuardrail.class)
    public PromptInjectionGuardrail promptInjectionGuardrail(AgentProperties properties) {
        return new PromptInjectionGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(TopicScopeGuardrail.class)
    public TopicScopeGuardrail topicScopeGuardrail(AgentProperties properties) {
        return new TopicScopeGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PiiInputGuardrail.class)
    public PiiInputGuardrail piiInputGuardrail(AgentProperties properties) {
        return new PiiInputGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitGuardrail.class)
    public RateLimitGuardrail rateLimitGuardrail(AgentProperties properties) {
        return new RateLimitGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PiiOutputGuardrail.class)
    public PiiOutputGuardrail piiOutputGuardrail(AgentProperties properties) {
        return new PiiOutputGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(DisclaimerInjectorGuardrail.class)
    public DisclaimerInjectorGuardrail disclaimerInjectorGuardrail(AgentProperties properties) {
        return new DisclaimerInjectorGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(ScopeValidatorGuardrail.class)
    public ScopeValidatorGuardrail scopeValidatorGuardrail(AgentProperties properties) {
        return new ScopeValidatorGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SchemaValidatorGuardrail.class)
    public SchemaValidatorGuardrail schemaValidatorGuardrail(AgentProperties properties) {
        return new SchemaValidatorGuardrail(properties);
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailPipeline.class)
    public GuardrailPipeline guardrailPipeline(List<InputGuardrail> inputGuardrails,
                                                List<OutputGuardrail> outputGuardrails,
                                                AgentProperties properties) {
        return new GuardrailPipeline(inputGuardrails, outputGuardrails, properties);
    }
}
