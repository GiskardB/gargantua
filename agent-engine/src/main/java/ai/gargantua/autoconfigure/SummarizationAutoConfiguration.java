package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.session.SessionSummarizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the production {@link SessionSummarizer} (LLM-backed via the routing
 * model) and the {@link SessionExpirySummarizationScheduler} that drives it on
 * working-memory TTL expiry. Both are conditional so applications without an
 * LLM provider (tests) or without Mongo (embedded mode) keep working.
 */
@AutoConfiguration(after = LlmProviderAutoConfiguration.class)
@EnableConfigurationProperties(AgentProperties.class)
@EnableScheduling
public class SummarizationAutoConfiguration {

    @Bean
    @ConditionalOnBean(LlmProviderFactory.class)
    @ConditionalOnMissingBean(SessionSummarizer.class)
    public SessionSummarizer sessionSummarizer(LlmProviderFactory llmProviderFactory) {
        return new RoutingModelSessionSummarizer(llmProviderFactory);
    }

    @Bean
    @ConditionalOnBean({MongoTemplate.class, SessionSummarizer.class})
    @ConditionalOnProperty(prefix = "agent.summarization", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SessionExpirySummarizationScheduler.class)
    public SessionExpirySummarizationScheduler sessionExpirySummarizationScheduler(
            MongoTemplate mongoTemplate,
            AgentProperties properties,
            SessionSummarizer summarizer,
            @Nullable EpisodicMemoryPort episodicMemoryPort) {
        return new SessionExpirySummarizationScheduler(
                mongoTemplate, properties, summarizer, episodicMemoryPort);
    }
}
