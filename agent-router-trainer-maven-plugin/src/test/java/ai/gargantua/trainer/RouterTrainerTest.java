package ai.gargantua.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterTrainerTest {

    @Test
    void trainsAndReloadsAModelThatSeparatesTwoSkills(@TempDir Path tempDir) throws IOException {
        List<TrainingExample> dataset = List.of(
                new TrainingExample("weather-skill", "what is the weather today"),
                new TrainingExample("weather-skill", "will it rain tomorrow"),
                new TrainingExample("weather-skill", "current temperature in Berlin"),
                new TrainingExample("weather-skill", "forecast for this weekend"),
                new TrainingExample("spending-analysis", "how much did I spend this month"),
                new TrainingExample("spending-analysis", "show me my spending by category"),
                new TrainingExample("spending-analysis", "compare my budget across months"),
                new TrainingExample("spending-analysis", "list my recent transactions")
        );

        RouterTrainer trainer = new RouterTrainer();
        Model<Label> model = trainer.train(dataset);

        Path outputPath = tempDir.resolve("skill-classifier.tribuo");
        trainer.exportModel(model, outputPath);

        @SuppressWarnings("unchecked")
        Model<Label> reloaded = (Model<Label>) Model.deserializeFromFile(outputPath);

        String[] featureNames = new String[384];
        for (int i = 0; i < featureNames.length; i++) featureNames[i] = "f" + i;

        var embeddingModel = new dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel();
        float[] vector = embeddingModel.embed("what's the weather like right now").content().vector();
        double[] values = new double[vector.length];
        for (int i = 0; i < vector.length; i++) values[i] = vector[i];

        Prediction<Label> prediction = reloaded.predict(new ArrayExample<>(new Label(Label.UNKNOWN), featureNames, values));

        assertEquals("weather-skill", prediction.getOutput().getLabel());
    }
}
