package io.agentkit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Placeholder auto-configuration for dry-run mode.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class DryRunAutoConfiguration {
    // Placeholder: dry-run behavior is handled within DefaultOrchestratorEngine
}
