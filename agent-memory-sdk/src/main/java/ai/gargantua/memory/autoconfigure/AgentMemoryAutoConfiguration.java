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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties(AgentMemoryProperties.class)
public class AgentMemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryAutoConfiguration.class);

    @Bean
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
    @ConditionalOnMissingBean(EpisodicMemoryPort.class)
    public EpisodicMemoryPort episodicMemoryPort(MongoTemplate mongoTemplate) {
        log.info("[AgentMemory] Registering MongoEpisodicMemoryAdapter");
        return new MongoEpisodicMemoryAdapter(mongoTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeMemoryPort.class)
    public KnowledgeMemoryPort knowledgeMemoryPort(MongoTemplate mongoTemplate) {
        log.info("[AgentMemory] Registering MongoKnowledgeMemoryAdapter");
        return new MongoKnowledgeMemoryAdapter(mongoTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryComposer.class)
    public MemoryComposer memoryComposer(WorkingMemoryPort workingMemoryPort,
                                         EpisodicMemoryPort episodicMemoryPort,
                                         KnowledgeMemoryPort knowledgeMemoryPort,
                                         AgentMemoryProperties properties) {
        log.info("[AgentMemory] Registering MemoryComposer (maxContextTokens={})",
                properties.getComposer().getMaxContextTokens());
        return new MemoryComposer(
                workingMemoryPort,
                episodicMemoryPort,
                knowledgeMemoryPort,
                properties.getComposer().getMaxContextTokens()
        );
    }
}
