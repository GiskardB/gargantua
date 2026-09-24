package ai.gargantua.autoconfigure;

import ai.gargantua.core.routing.ClassificationResult;
import ai.gargantua.core.routing.ScoredSkill;
import ai.gargantua.core.routing.SkillClassifier;
import ai.gargantua.core.skill.SkillMeta;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SkillClassifier} escape hatch for a custom classification head exported to ONNX
 * (e.g. trained externally with sklearn/PyTorch on the same embedding features).
 *
 * <p>Input is the 384-dim MiniLM embedding vector produced by the same model used by
 * {@link SemanticSimilarityClassifier}/{@link TribuoClassifier} — no tokenizer required, the
 * ONNX graph only needs to implement embedding -&gt; logits.</p>
 */
public class OnnxClassifier implements SkillClassifier {

    private static final Logger log = LoggerFactory.getLogger(OnnxClassifier.class);
    private static final String INPUT_NAME = "embedding";

    private final EmbeddingModel featureExtractor = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final List<String> labels;

    public OnnxClassifier(Resource modelResource, List<String> labels) {
        this.labels = labels;
        try {
            byte[] modelBytes = modelResource.getInputStream().readAllBytes();
            this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
        } catch (IOException | OrtException e) {
            throw new IllegalStateException("Failed to load ONNX skill classifier model from "
                    + modelResource.getDescription(), e);
        }
        log.info("Loaded ONNX skill classifier model from {} ({} labels)", modelResource.getDescription(), labels.size());
    }

    @Override
    public void index(List<SkillMeta> skills) {
        // No-op: the model is trained/exported offline; labels are fixed at load time.
    }

    @Override
    public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        float[] vector = featureExtractor.embed(userMessage).content().vector();
        try (OnnxTensor input = OnnxTensor.createTensor(env, new float[][]{vector});
             OrtSession.Result result = session.run(Map.of(INPUT_NAME, input))) {
            float[] logits = ((float[][]) result.get(0).getValue())[0];
            return softmaxToResult(logits);
        } catch (OrtException e) {
            log.warn("ONNX classifier inference failed: {}", e.getMessage());
            return ClassificationResult.NONE;
        }
    }

    @Override
    public String engine() {
        return "onnx";
    }

    private ClassificationResult softmaxToResult(float[] logits) {
        double max = Float.NEGATIVE_INFINITY;
        for (float logit : logits) max = Math.max(max, logit);

        double sum = 0;
        double[] probs = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = Math.exp(logits[i] - max);
            sum += probs[i];
        }

        List<ScoredSkill> candidates = new ArrayList<>(logits.length);
        int bestIdx = -1;
        double bestScore = -1.0;
        for (int i = 0; i < logits.length && i < labels.size(); i++) {
            double score = probs[i] / sum;
            candidates.add(new ScoredSkill(labels.get(i), score));
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));

        if (bestIdx < 0) {
            return ClassificationResult.NONE;
        }
        return new ClassificationResult(labels.get(bestIdx), bestScore, candidates);
    }
}
