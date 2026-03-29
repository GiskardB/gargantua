package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Placeholder auto-configuration for tool result caching.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class ToolCacheAutoConfiguration {
    // Placeholder: tool caching will use Caffeine cache with @CacheableToolResult
}
