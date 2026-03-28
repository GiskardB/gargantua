package ai.gargantua.autoconfigure;

import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.memory.adapters.inmemory.InMemoryApprovalStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryEpisodicMemoryAdapter;
import ai.gargantua.memory.adapters.inmemory.InMemoryKnowledgeMemoryAdapter;
import ai.gargantua.memory.adapters.inmemory.InMemoryWorkingMemoryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Auto-configuration for embedded mode — replaces MongoDB and Redis with
 * in-memory ConcurrentHashMap-based implementations.
 *
 * <p>Activated by: {@code SPRING_PROFILES_ACTIVE=embedded}</p>
 *
 * <p>This allows running an agent with ZERO external infrastructure —
 * no Docker, no MongoDB, no Redis. Ideal for:</p>
 * <ul>
 *   <li>Local development and prototyping</li>
 *   <li>CI/CD pipelines</li>
 *   <li>Quick demos and evaluation</li>
 *   <li>Learning the framework</li>
 * </ul>
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 */
@AutoConfiguration(before = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        RedisAutoConfiguration.class
})
@Profile("embedded")
public class EmbeddedProfileAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedProfileAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(WorkingMemoryPort.class)
    public WorkingMemoryPort inMemoryWorkingMemory() {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║  EMBEDDED MODE — in-memory storage active       ║");
        log.info("║  No MongoDB or Redis required                   ║");
        log.info("║  Data will be lost on restart                   ║");
        log.info("╚══════════════════════════════════════════════════╝");
        return new InMemoryWorkingMemoryAdapter(20, 30 * 60 * 1000L);
    }

    @Bean
    @ConditionalOnMissingBean(EpisodicMemoryPort.class)
    public EpisodicMemoryPort inMemoryEpisodicMemory() {
        return new InMemoryEpisodicMemoryAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeMemoryPort.class)
    public KnowledgeMemoryPort inMemoryKnowledgeMemory() {
        return new InMemoryKnowledgeMemoryAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(ApprovalStore.class)
    public ApprovalStore inMemoryApprovalStore() {
        return new InMemoryApprovalStore();
    }
}
