package io.agentkit.autoconfigure;

import io.agentkit.core.orchestrator.OrchestratorEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for evaluation runner and dataset loader.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class EvalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EvalDatasetLoader.class)
    public EvalDatasetLoader evalDatasetLoader(AgentProperties properties) {
        return new EvalDatasetLoader(properties);
    }

    @Bean
    @ConditionalOnMissingBean(EvalRunner.class)
    public EvalRunner evalRunner(EvalDatasetLoader datasetLoader,
                                  OrchestratorEngine orchestratorEngine,
                                  AgentProperties properties) {
        return new EvalRunner(datasetLoader, orchestratorEngine, properties);
    }
}
