package ai.gargantua.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Placeholder auto-configuration for observability (metrics, tracing, logging).
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class ObservabilityAutoConfiguration {
    // Placeholder: will register Micrometer meters and OpenTelemetry span processors
}
