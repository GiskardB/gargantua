package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.hitl.RedisApprovalStore;
import ai.gargantua.core.hitl.ApprovalStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for Human-in-the-Loop. Wires both the
 * {@link ApprovalStore} (Redis-backed when Spring Data Redis is wired
 * upstream, otherwise left for {@code EmbeddedProfileAutoConfiguration}
 * or a user override) and the {@link HitlCoordinator} that the REST
 * controller delegates to.
 *
 * <p>The Redis {@link ApprovalStore} {@code @Bean} pairs
 * {@code @ConditionalOnBean(StringRedisTemplate.class)} with
 * {@link AutoConfigureAfter}{@code (RedisAutoConfiguration.class)} so it
 * fires only when Spring Boot's Redis auto-config has already contributed
 * a template. In an {@code embedded}-profile app Redis is excluded, no
 * template exists, and the {@code @Bean} is skipped — leaving
 * {@code EmbeddedProfileAutoConfiguration}'s {@code InMemoryApprovalStore}
 * to satisfy {@code @ConditionalOnMissingBean(ApprovalStore.class)}.</p>
 */
@AutoConfiguration
@AutoConfigureAfter(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.hitl", name = "enabled", havingValue = "true")
public class HitlAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(ApprovalStore.class)
    public ApprovalStore redisApprovalStore(StringRedisTemplate redisTemplate) {
        return new RedisApprovalStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(HitlCoordinator.class)
    public HitlCoordinator hitlCoordinator(AgentProperties properties,
                                            @Nullable ApprovalStore approvalStore) {
        return new HitlCoordinator(properties, approvalStore);
    }
}
