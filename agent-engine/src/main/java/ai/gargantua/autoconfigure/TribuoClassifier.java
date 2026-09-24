package ai.gargantua.autoconfigure;

import ai.gargantua.core.routing.ClassificationResult;
import ai.gargantua.core.routing.ScoredSkill;
import ai.gargantua.core.routing.SkillClassifier;
import ai.gargantua.core.skill.SkillMeta;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SkillClassifier} engine backed by a Tribuo model (default trainer:
 * {@code LogisticRegressionTrainer}) trained offline by {@code mvn gargantua:train-router}.
 *
 * <p>Reuses the same MiniLM embedding model as {@link SemanticSimilarityClassifier} as the
 * feature extractor, so training and inference use identical 384-dim feature vectors.
 * The Tribuo model itself is pure JVM (no native library), loaded from a protobuf-serialized
 * resource (see {@code agent.routing.classifier.tribuo.model-path}).</p>
 */
public class TribuoClassifier implements SkillClassifier {

    private static final Logger log = LoggerFactory.getLogger(TribuoClassifier.class);
    private static final String[] FEATURE_NAMES = buildFeatureNames();

    private final EmbeddingModel featureExtractor = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final Model<Label> model;

    @SuppressWarnings("unchecked")
    public TribuoClassifier(Resource modelResource) {
        try (InputStream in = modelResource.getInputStream()) {
            this.model = (Model<Label>) Model.deserializeFromStream(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Tribuo skill classifier model from "
                    + modelResource.getDescription(), e);
        }
        log.info("Loaded Tribuo skill classifier model from {}", modelResource.getDescription());
    }

    @Override
    public void index(List<SkillMeta> skills) {
        // No-op: the model is trained offline (mvn gargantua:train-router), not at boot.
    }

    @Override
    public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        float[] vector = featureExtractor.embed(userMessage).content().vector();
        double[] values = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            values[i] = vector[i];
        }

        ArrayExample<Label> example = new ArrayExample<>(new Label(Label.UNKNOWN), FEATURE_NAMES, values);
        Prediction<Label> prediction = model.predict(example);

        List<ScoredSkill> candidates = new ArrayList<>();
        for (Map.Entry<String, Label> entry : prediction.getOutputScores().entrySet()) {
            candidates.add(new ScoredSkill(entry.getKey(), entry.getValue().getScore()));
        }
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));

        Label best = prediction.getOutput();
        return new ClassificationResult(best.getLabel(), best.getScore(), candidates);
    }

    @Override
    public String engine() {
        return "tribuo";
    }

    private static String[] buildFeatureNames() {
        // all-MiniLM-L6-v2 produces 384-dim embeddings; feature names must match training.
        String[] names = new String[384];
        for (int i = 0; i < names.length; i++) {
            names[i] = "f" + i;
        }
        return names;
    }
}
