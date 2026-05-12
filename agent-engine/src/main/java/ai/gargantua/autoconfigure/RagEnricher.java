package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.ContextEnricher;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.rag.RagConfig;
import ai.gargantua.core.rag.RetrievedChunk;
import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Context enricher that performs RAG (Retrieval-Augmented Generation) for skills
 * that declare a {@code knowledge-base} in their SKILL.md frontmatter.
 *
 * <p>When the active skill has a {@link RagConfig}, this enricher:
 * <ol>
 *   <li>Searches the vector store using the user's message as the query</li>
 *   <li>Formats the top-K results as a numbered list with source attribution</li>
 *   <li>Injects the results into the system prompt under the "RELEVANT_DOCUMENTS" section</li>
 * </ol>
 *
 * <p>If the skill does NOT declare a knowledge-base, this enricher returns null
 * and adds zero overhead to the prompt.</p>
 *
 * <p>If no {@link VectorStorePort} is wired (i.e. {@link #vectorStore} is
 * {@code null}), the enricher is a runtime no-op for every skill — even those
 * that <em>do</em> declare a knowledge-base. This lets {@link RagAutoConfiguration}
 * register the bean unconditionally, sidestepping the {@code @ConditionalOnBean}
 * vs. profile-gated-producer ordering trap that v1.2.8 only partially closed.</p>
 */
public class RagEnricher implements ContextEnricher {

    private static final Logger log = LoggerFactory.getLogger(RagEnricher.class);

    private final VectorStorePort vectorStore;
    private final SkillRegistry skillRegistry;

    public RagEnricher(VectorStorePort vectorStore, SkillRegistry skillRegistry) {
        this.vectorStore = vectorStore;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String sectionName() {
        return "RELEVANT_DOCUMENTS";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public String targetSkill() {
        return null; // runs for all skills, but returns null if no RagConfig
    }

    /** True when a {@link VectorStorePort} was wired and RAG is reachable. */
    public boolean isActive() {
        return vectorStore != null;
    }

    @Override
    public String enrich(EnricherContext ctx) {
        if (vectorStore == null) {
            return null;
        }

        SkillCard skillCard;
        try {
            skillCard = skillRegistry.load(ctx.skillName());
        } catch (Exception e) {
            log.debug("Could not load skill '{}' for RAG enrichment: {}", ctx.skillName(), e.getMessage());
            return null;
        }

        RagConfig ragConfig = skillCard.ragConfig();
        if (ragConfig == null) {
            return null;
        }

        log.debug("RAG enrichment active for skill '{}', searching collection '{}'",
                ctx.skillName(), ragConfig.knowledgeBase());

        List<RetrievedChunk> chunks = vectorStore.search(
                ragConfig.knowledgeBase(),
                ctx.userMessage(),
                ragConfig.maxResults(),
                ragConfig.minScore()
        );

        if (chunks.isEmpty()) {
            log.debug("No relevant documents found for skill '{}'", ctx.skillName());
            return null;
        }

        var sb = new StringBuilder(256 + chunks.size() * 128);
        sb.append("The following documents are relevant to the user's question:\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            sb.append(i + 1).append(". [Source: ").append(chunk.source())
                    .append(" | Score: ").append(String.format("%.2f", chunk.score()))
                    .append("]\n").append(chunk.content()).append("\n\n");
        }

        log.debug("RAG enrichment for skill '{}': {} documents retrieved", ctx.skillName(), chunks.size());
        return sb.toString().strip();
    }
}
