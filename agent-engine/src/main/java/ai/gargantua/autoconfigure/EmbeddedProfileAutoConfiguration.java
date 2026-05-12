package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.WorkingMemoryPort;
import ai.gargantua.core.rag.EmbeddingPort;
import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.memory.adapters.inmemory.EmbeddingInMemoryVectorStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryApprovalStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryAuditStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryEpisodicMemoryAdapter;
import ai.gargantua.memory.adapters.inmemory.InMemoryKnowledgeMemoryAdapter;
import ai.gargantua.memory.adapters.inmemory.InMemoryVectorStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryWorkingMemoryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p>The actual exclusion of Mongo/Redis auto-configuration is done in
 * {@code application-embedded.yml} via {@code spring.autoconfigure.exclude}.
 * This class only provides the in-memory bean replacements.</p>
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 */
@AutoConfiguration
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

    @Bean
    @ConditionalOnMissingBean(VectorStorePort.class)
    public VectorStorePort inMemoryVectorStore(ObjectProvider<EmbeddingPort> embeddingPortProvider) {
        // v1.2.18+: when an EmbeddingPort is wired (RagAutoConfiguration registers
        // a default in-process MiniLM one) we get real cosine-similarity RAG out
        // of the box. Without an EmbeddingPort we fall back to the legacy
        // Jaccard keyword store — useful for tests that exclude the embedding
        // model or want fully deterministic scoring.
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        if (embeddingPort != null) {
            log.info("Using in-memory vector store with cosine similarity over {}-dim embeddings "
                    + "(EmbeddingPort: {})", embeddingPort.dimension(),
                    embeddingPort.getClass().getSimpleName());
            return new EmbeddingInMemoryVectorStore(embeddingPort);
        }
        log.info("Using in-memory vector store (keyword-based Jaccard fallback — no EmbeddingPort wired)");
        return new InMemoryVectorStore();
    }

    @Bean
    @ConditionalOnMissingBean(AuditStore.class)
    public AuditStore inMemoryAuditStore() {
        log.info("Using in-memory audit store (data will be lost on restart)");
        return new InMemoryAuditStore();
    }

    @Bean
    @ConditionalOnMissingBean(ToolResultCache.class)
    public ToolResultCache inMemoryToolResultCache() {
        log.info("Using in-memory tool-result cache (data will be lost on restart)");
        return new ToolResultCache();
    }
}
