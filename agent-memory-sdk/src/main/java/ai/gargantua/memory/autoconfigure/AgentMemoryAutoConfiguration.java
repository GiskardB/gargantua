package ai.gargantua.memory.autoconfigure;

import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.memory.adapters.mongo.MongoEpisodicMemoryAdapter;
import ai.gargantua.memory.adapters.mongo.MongoKnowledgeMemoryAdapter;
import ai.gargantua.memory.adapters.redis.RedisWorkingMemoryAdapter;
import ai.gargantua.memory.composer.MemoryComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for the three-layer memory subsystem.
 * Registers Redis-backed working memory, MongoDB-backed episodic and knowledge
 * memory, and the {@link ai.gargantua.memory.composer.MemoryComposer} that
 * merges all three layers into a single prompt-ready structure.
 *
 * <p>All beans are {@code @ConditionalOnMissingBean} so applications can override
 * any adapter with a custom implementation.</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
@EnableConfigurationProperties(AgentMemoryProperties.class)
public class AgentMemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(WorkingMemoryPort.class)
    public WorkingMemoryPort workingMemoryPort(StringRedisTemplate redisTemplate,
                                               AgentMemoryProperties properties) {
        log.info("[AgentMemory] Registering RedisWorkingMemoryAdapter (maxMessages={}, ttlMinutes={})",
                properties.getWorking().getMaxMessages(),
                properties.getWorking().getTtlMinutes());
        return new RedisWorkingMemoryAdapter(
                redisTemplate,
                properties.getWorking().getMaxMessages(),
                properties.getWorking().getTtlMinutes()
        );
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(EpisodicMemoryPort.class)
    public EpisodicMemoryPort episodicMemoryPort(MongoTemplate mongoTemplate,
                                                 AgentMemoryProperties properties) {
        int ttlDays = properties.getEpisodic().getTtlDays();
        log.info("[AgentMemory] Registering MongoEpisodicMemoryAdapter (ttlDays={})", ttlDays);
        return new MongoEpisodicMemoryAdapter(mongoTemplate, ttlDays);
    }

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(KnowledgeMemoryPort.class)
    public KnowledgeMemoryPort knowledgeMemoryPort(MongoTemplate mongoTemplate,
                                                   AgentMemoryProperties properties) {
        int maxSegments = properties.getKnowledge().getMaxSegments();
        int maxTokensPerSegment = properties.getKnowledge().getMaxTokensPerSegment();
        log.info("[AgentMemory] Registering MongoKnowledgeMemoryAdapter (maxSegments={}, maxTokensPerSegment={})",
                maxSegments, maxTokensPerSegment);
        return new MongoKnowledgeMemoryAdapter(mongoTemplate, maxSegments, maxTokensPerSegment);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryComposer.class)
    public MemoryComposer memoryComposer(WorkingMemoryPort workingMemoryPort,
                                         EpisodicMemoryPort episodicMemoryPort,
                                         KnowledgeMemoryPort knowledgeMemoryPort,
                                         AgentMemoryProperties properties) {
        int maxSummaries = properties.getEpisodic().getMaxSummaries();
        log.info("[AgentMemory] Registering MemoryComposer (maxContextTokens={}, maxEpisodicSummaries={})",
                properties.getComposer().getMaxContextTokens(), maxSummaries);
        return new MemoryComposer(
                workingMemoryPort,
                episodicMemoryPort,
                knowledgeMemoryPort,
                properties.getComposer().getMaxContextTokens(),
                maxSummaries
        );
    }
}
