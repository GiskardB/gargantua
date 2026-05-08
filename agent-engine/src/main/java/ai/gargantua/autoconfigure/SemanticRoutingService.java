package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.skill.SkillMeta;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hybrid skill routing service using in-process ONNX embeddings (all-MiniLM-L6-v2)
 * for semantic matching, with LLM fallback when the similarity score is below threshold.
 *
 * <p>Embedding computation runs in-process (~2-5ms per query) with no external API calls.</p>
 *
 * @see RoutingService
 * @see ai.gargantua.core.orchestrator.RoutingResult
 */
public class SemanticRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRoutingService.class);

    private final AgentProperties properties;
    private final RoutingService routingService;
    private final EmbeddingModel embeddingModel;

    /** Cached skill description embeddings: skill name -> embedding vector. */
    private final Map<String, Embedding> skillEmbeddings = new ConcurrentHashMap<>();

    public SemanticRoutingService(AgentProperties properties, RoutingService routingService) {
        this.properties = properties;
        this.routingService = routingService;
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        log.info("Initialized ONNX embedding model: all-MiniLM-L6-v2 (quantized)");
    }

    /**
     * Index skill descriptions by computing embeddings. Call at boot or on skill reload.
     */
    public void index(List<SkillMeta> skills) {
        skillEmbeddings.clear();
        for (SkillMeta skill : skills) {
            if (skill.active() && skill.description() != null && !skill.description().isBlank()) {
                Embedding embedding = embeddingModel.embed(skill.description()).content();
                skillEmbeddings.put(skill.name(), embedding);
            }
        }
        log.info("Indexed {} skills for semantic routing (ONNX embeddings)", skillEmbeddings.size());
    }

    /**
     * Route a user message to the best matching skill, branching on
     * {@code agent.routing.strategy}: {@code semantic} (embeddings only),
     * {@code llm} (LLM-only), or {@code hybrid} (default — embeddings with
     * LLM fallback when below threshold).
     */
    public RoutingResult route(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), 0.0);
        }

        String strategy = normalizeStrategy(properties.getRouting().getStrategy());

        if ("llm".equals(strategy)) {
            log.debug("Routing strategy=llm — skipping embeddings");
            String llmResult = routingService.routeWithLlm(userMessage, skills);
            return RoutingResult.llm(llmResult);
        }

        // Ensure index is up to date for semantic and hybrid strategies
        if (skillEmbeddings.isEmpty()) {
            index(skills);
        }

        double threshold = properties.getRouting().getSemantic().getThreshold();

        // Embed the user message
        Embedding messageEmbedding = embeddingModel.embed(userMessage).content();

        String bestSkill = null;
        double bestScore = -1.0;

        for (Map.Entry<String, Embedding> entry : skillEmbeddings.entrySet()) {
            double score = cosineSimilarity(messageEmbedding.vector(), entry.getValue().vector());
            if (log.isTraceEnabled()) {
                log.trace("Semantic score for skill '{}': {}", entry.getKey(), "%.4f".formatted(score));
            }
            if (score > bestScore) {
                bestScore = score;
                bestSkill = entry.getKey();
            }
        }

        if (bestSkill != null && bestScore >= threshold) {
            if (log.isDebugEnabled()) {
                log.debug("Semantic match: skill='{}', score={}", bestSkill, "%.4f".formatted(bestScore));
            }
            return RoutingResult.semantic(bestSkill, bestScore);
        }

        if ("semantic".equals(strategy)) {
            // Strict semantic strategy — no LLM fallback. Return the configured fallback skill.
            String fallback = properties.getRouting().getFallbackSkill();
            if (log.isDebugEnabled()) {
                log.debug("Semantic strategy: score below threshold ({} < {}), returning fallback skill '{}'",
                        bestScore >= 0 ? "%.4f".formatted(bestScore) : "none", threshold, fallback);
            }
            return RoutingResult.semantic(fallback, Math.max(0.0, bestScore));
        }

        // hybrid (default) — fall back to LLM routing
        if (log.isDebugEnabled()) {
            log.debug("Hybrid strategy: score below threshold ({} < {}), falling back to LLM routing",
                    bestScore >= 0 ? "%.4f".formatted(bestScore) : "none", threshold);
        }
        String llmResult = routingService.routeWithLlm(userMessage, skills);
        return RoutingResult.llm(llmResult);
    }

    private String normalizeStrategy(String raw) {
        if (raw == null || raw.isBlank()) return "hybrid";
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "semantic", "llm", "hybrid" -> s;
            default -> {
                log.warn("Unknown routing strategy '{}' — falling back to 'hybrid'", raw);
                yield "hybrid";
            }
        };
    }

    /**
     * Cosine similarity between two embedding vectors.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
