package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for RAG (Retrieval-Augmented Generation) support.
 *
 * <p>Registers a {@link RagEnricher} bean when both a {@link VectorStorePort}
 * and a {@link SkillRegistry} are available. If no vector store is configured
 * (e.g., non-embedded mode without an external vector DB), the enricher is
 * simply not created and RAG is inactive.</p>
 *
 * <p>Declares {@code @AutoConfigureAfter(EmbeddedProfileAutoConfiguration.class)}
 * (1.2.8+) so that, in embedded mode, the in-memory {@code VectorStorePort}
 * bean is registered <em>before</em> this configuration's
 * {@link ConditionalOnBean} predicate is evaluated. Without this, embedded
 * apps would silently get no {@link RagEnricher}, and any skill declaring
 * {@code metadata.knowledge-base} would behave as if RAG were disabled.</p>
 */
@AutoConfiguration(after = EmbeddedProfileAutoConfiguration.class)
public class RagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagAutoConfiguration.class);

    @Bean
    @ConditionalOnBean({VectorStorePort.class, SkillRegistry.class})
    public RagEnricher ragEnricher(VectorStorePort vectorStore, SkillRegistry skillRegistry) {
        log.info("RAG enricher activated — skills with knowledge-base will use vector store retrieval");
        return new RagEnricher(vectorStore, skillRegistry);
    }
}
