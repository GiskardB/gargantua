package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.EmbeddingPort;
import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillRegistry;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for RAG (Retrieval-Augmented Generation) support.
 *
 * <p>Registers a {@link RagEnricher} bean <em>unconditionally</em>. Both
 * dependencies ({@link VectorStorePort} and {@link SkillRegistry}) are
 * resolved via {@link ObjectProvider} — looked up at bean-creation time
 * rather than via {@code @ConditionalOnBean} (evaluated during the
 * registration phase). When either dependency is absent the enricher is
 * wired as a runtime no-op.</p>
 *
 * <p>This closes the long-standing ordering trap that bit users in embedded
 * mode through v1.2.8: {@code @ConditionalOnBean} on a {@code @Bean} method
 * only sees bean definitions registered <em>so far</em>, so the condition's
 * verdict depended on the order in which Spring Boot processed
 * {@link RagAutoConfiguration}, {@code SkillRegistryAutoConfiguration} and
 * {@link EmbeddedProfileAutoConfiguration} (the latter being
 * {@code @Profile("embedded")} adds another layer of fragility). The
 * {@link ObjectProvider} lookup runs after every eligible bean has been
 * registered, so the order genuinely does not matter.</p>
 */
@AutoConfiguration
public class RagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagAutoConfiguration.class);

    /**
     * Default {@link EmbeddingPort} that wraps the in-process ONNX MiniLM
     * model already on the classpath (the same one the semantic router uses).
     * Override by registering your own {@code EmbeddingPort} bean — typical
     * production setup is a {@link LangChain4jEmbeddingAdapter} around an
     * OpenAI / Cohere / Vertex AI embedding model. Added in v1.2.18 to
     * power the new cosine-similarity in-memory vector store.
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingPort.class)
    public EmbeddingPort defaultEmbeddingPort() {
        log.info("Default EmbeddingPort: in-process all-MiniLM-L6-v2-quantized (384 dims). "
                + "Override with your own EmbeddingPort @Bean for production-grade models.");
        return new LangChain4jEmbeddingAdapter(new AllMiniLmL6V2QuantizedEmbeddingModel());
    }

    @Bean
    @ConditionalOnMissingBean(RagEnricher.class)
    public RagEnricher ragEnricher(ObjectProvider<VectorStorePort> vectorStoreProvider,
                                   ObjectProvider<SkillRegistry> skillRegistryProvider) {
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        SkillRegistry skillRegistry = skillRegistryProvider.getIfAvailable();
        if (vectorStore != null && skillRegistry != null) {
            log.info("RAG enricher activated — skills with knowledge-base will use vector store retrieval");
        } else {
            log.debug("RAG enricher registered as a runtime no-op (vectorStore={}, skillRegistry={})",
                    vectorStore != null, skillRegistry != null);
        }
        return new RagEnricher(vectorStore, skillRegistry);
    }
}
