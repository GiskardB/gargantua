package io.agentkit.autoconfigure;

import io.agentkit.autoconfigure.guardrails.DisclaimerInjectorGuardrail;
import io.agentkit.autoconfigure.guardrails.MaxLengthGuardrail;
import io.agentkit.autoconfigure.guardrails.PiiInputGuardrail;
import io.agentkit.autoconfigure.guardrails.PiiOutputGuardrail;
import io.agentkit.autoconfigure.guardrails.PromptInjectionGuardrail;
import io.agentkit.autoconfigure.guardrails.RateLimitGuardrail;
import io.agentkit.autoconfigure.guardrails.SchemaValidatorGuardrail;
import io.agentkit.autoconfigure.guardrails.ScopeValidatorGuardrail;
import io.agentkit.autoconfigure.guardrails.TopicScopeGuardrail;
import io.agentkit.core.guardrail.InputGuardrail;
import io.agentkit.core.guardrail.OutputGuardrail;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for guardrail pipeline and all built-in guardrails.
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
