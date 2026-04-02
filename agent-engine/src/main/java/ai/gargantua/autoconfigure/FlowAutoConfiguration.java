package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.OrchestratorEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Agent Flow DSL — multi-step skill pipelines.
 */
@AutoConfiguration
public class FlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlowRegistry.class)
    public FlowRegistry flowRegistry(ApplicationContext ctx) {
        return new FlowRegistry(ctx);
    }

    @Bean
    @ConditionalOnMissingBean(FlowExecutor.class)
    public FlowExecutor flowExecutor(OrchestratorEngine orchestrator) {
        return new FlowExecutor(orchestrator);
    }
}
