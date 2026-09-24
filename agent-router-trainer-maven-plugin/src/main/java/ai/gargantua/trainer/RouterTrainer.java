package ai.gargantua.trainer;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Trains a Tribuo logistic-regression skill classifier over MiniLM embedding
 * features — the same feature representation {@code TribuoClassifier} uses at
 * inference time in {@code agent-engine}.
 */
public class RouterTrainer {

    private static final int EMBEDDING_DIM = 384; // all-MiniLM-L6-v2
    private static final String[] FEATURE_NAMES = buildFeatureNames();

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    public Model<Label> train(List<TrainingExample> dataset) {
        LabelFactory labelFactory = new LabelFactory();
        List<Example<Label>> examples = new ArrayList<>(dataset.size());
        for (TrainingExample te : dataset) {
            float[] vector = embeddingModel.embed(te.text()).content().vector();
            double[] values = new double[vector.length];
            for (int i = 0; i < vector.length; i++) {
                values[i] = vector[i];
            }
            examples.add(new ArrayExample<>(new Label(te.skillName()), FEATURE_NAMES, values));
        }

        var provenance = new SimpleDataSourceProvenance("gargantua-skill-router-training", labelFactory);
        var dataSource = new ListDataSource<>(examples, labelFactory, provenance);
        var mutableDataset = new MutableDataset<>(dataSource);

        return new LogisticRegressionTrainer().train(mutableDataset);
    }

    public void exportModel(Model<Label> model, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        model.serializeToFile(outputPath);
    }

    private static String[] buildFeatureNames() {
        String[] names = new String[EMBEDDING_DIM];
        for (int i = 0; i < names.length; i++) {
            names[i] = "f" + i;
        }
        return names;
    }
}
