package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.cost.MongoCostTrackingRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auto-configuration for cost tracking.
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration")
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.cost-tracking", name = "enabled", havingValue = "true")
public class CostTrackingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CostTracker.class)
    public CostTracker costTracker(AgentProperties properties) {
        return new CostTracker(properties);
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(MongoCostTrackingRepository.class)
    public MongoCostTrackingRepository mongoCostTrackingRepository(MongoTemplate mongoTemplate) {
        return new MongoCostTrackingRepository(mongoTemplate);
    }
}
