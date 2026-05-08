package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for {@link ToolResultCache}, the Redis-backed cache that
 * powers {@link ai.gargantua.core.tool.CacheableToolResult}. Activated only
 * when a {@link StringRedisTemplate} bean is available.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class ToolCacheAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(ToolResultCache.class)
    public ToolResultCache toolResultCache(StringRedisTemplate redisTemplate) {
        return new ToolResultCache(redisTemplate);
    }
}
