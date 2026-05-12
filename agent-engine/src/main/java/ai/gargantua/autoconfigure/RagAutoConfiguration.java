package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for RAG (Retrieval-Augmented Generation) support.
 *
 * <p>Registers a {@link RagEnricher} bean whenever a {@link SkillRegistry} is
 * present. The {@link VectorStorePort} dependency is resolved via an
 * {@link ObjectProvider} — looked up <em>at bean-creation time</em> rather
 * than via {@code @ConditionalOnBean} (evaluated during the registration
 * phase). When no vector store is contributed the enricher is wired with
 * {@code null} and behaves as a runtime no-op.</p>
 *
 * <p>This sidesteps the {@code @ConditionalOnBean} vs. profile-gated-producer
 * race that v1.2.8 only partially fixed: in embedded mode the in-memory
 * {@code VectorStorePort} is contributed by
 * {@link EmbeddedProfileAutoConfiguration} which is {@code @Profile("embedded")},
 * and registration-phase conditions can fire before profile-gated bean
 * definitions are visible. The {@code ObjectProvider} lookup runs after every
 * eligible bean has been registered, so embedded apps now pick up RAG with
 * zero workarounds.</p>
 */
@AutoConfiguration
public class RagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(SkillRegistry.class)
    @ConditionalOnMissingBean(RagEnricher.class)
    public RagEnricher ragEnricher(ObjectProvider<VectorStorePort> vectorStoreProvider,
                                   SkillRegistry skillRegistry) {
        VectorStorePort vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.debug("No VectorStorePort bean present — RAG enricher registered as a runtime no-op "
                    + "(skills declaring metadata.knowledge-base will behave as if RAG were disabled).");
        } else {
            log.info("RAG enricher activated — skills with knowledge-base will use vector store retrieval");
        }
        return new RagEnricher(vectorStore, skillRegistry);
    }
}
