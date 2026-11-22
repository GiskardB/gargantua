package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.ratelimit.InMemoryRequestRateLimiter;
import ai.gargantua.adapters.ratelimit.RedisRequestRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * Each guardrail carries {@code @Component} for clarity, but consumer
 * Spring Boot apps don't scan the framework's package — the explicit
 * {@code @Bean} factories below are the real registration mechanism. Each
 * is {@code @ConditionalOnMissingBean} so users can override any single one.
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

    /**
     * Shared across replicas when a Redis template is present, local to this JVM
     * otherwise (the {@code embedded} profile, or an app with no Redis).
     */
    @Bean
    @ConditionalOnMissingBean(RequestRateLimiter.class)
    public RequestRateLimiter requestRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        return redis != null ? new RedisRequestRateLimiter(redis) : new InMemoryRequestRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitGuardrail.class)
    public RateLimitGuardrail rateLimitGuardrail(AgentProperties properties, RequestRateLimiter limiter) {
        return new RateLimitGuardrail(properties, limiter);
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
