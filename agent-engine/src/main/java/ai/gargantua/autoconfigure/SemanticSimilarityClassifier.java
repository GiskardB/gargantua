package ai.gargantua.autoconfigure;

import ai.gargantua.core.routing.ClassificationResult;
import ai.gargantua.core.routing.ScoredSkill;
import ai.gargantua.core.routing.SkillClassifier;
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
 * Default {@link SkillClassifier} engine: in-process ONNX embeddings (all-MiniLM-L6-v2)
 * with cosine similarity against each skill's description. Zero-shot — no training required.
 *
 * <p>This is a straight extraction of the routing logic that previously lived inline in
 * {@code SemanticRoutingService}; behavior is unchanged.</p>
 */
public class SemanticSimilarityClassifier implements SkillClassifier {

    private static final Logger log = LoggerFactory.getLogger(SemanticSimilarityClassifier.class);

    private final EmbeddingModel embeddingModel;

    /** Cached skill description embeddings: skill name -> embedding vector. */
    private final Map<String, Embedding> skillEmbeddings = new ConcurrentHashMap<>();

    public SemanticSimilarityClassifier() {
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        log.info("Initialized ONNX embedding model: all-MiniLM-L6-v2 (quantized)");
    }

    @Override
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

    @Override
    public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        if (skillEmbeddings.isEmpty()) {
            index(skills);
        }

        Embedding messageEmbedding = embeddingModel.embed(userMessage).content();

        String bestSkill = null;
        double bestScore = -1.0;
        List<ScoredSkill> candidates = new java.util.ArrayList<>(skillEmbeddings.size());

        for (Map.Entry<String, Embedding> entry : skillEmbeddings.entrySet()) {
            double score = cosineSimilarity(messageEmbedding.vector(), entry.getValue().vector());
            candidates.add(new ScoredSkill(entry.getKey(), score));
            if (score > bestScore) {
                bestScore = score;
                bestSkill = entry.getKey();
            }
        }

        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (bestSkill == null) {
            return ClassificationResult.NONE;
        }
        return new ClassificationResult(bestSkill, bestScore, candidates);
    }

    @Override
    public String engine() {
        return "semantic";
    }

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
