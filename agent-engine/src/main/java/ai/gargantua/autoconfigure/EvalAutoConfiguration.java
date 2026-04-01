package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.eval.MongoEvalReportRepository;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auto-configuration for evaluation runner and dataset loader.
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration")
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

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(MongoEvalReportRepository.class)
    public MongoEvalReportRepository mongoEvalReportRepository(MongoTemplate mongoTemplate) {
        return new MongoEvalReportRepository(mongoTemplate);
    }
}
