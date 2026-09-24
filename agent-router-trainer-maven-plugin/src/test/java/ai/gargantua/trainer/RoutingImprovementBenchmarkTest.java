package ai.gargantua.trainer;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the actual accuracy/fallback-rate delta between the two {@code SkillClassifier}
 * engines on held-out (paraphrased, never-seen-in-training) utterances:
 *
 * <ul>
 *   <li><b>semantic</b> (today's default) — zero-shot cosine similarity vs. skill {@code description} only.</li>
 *   <li><b>tribuo</b> (opt-in via {@code mvn gargantua:train-router}) — logistic regression trained
 *       on {@code examples}+{@code routing-hints}+{@code description}, same MiniLM features.</li>
 * </ul>
 *
 * <p>Both are thresholded at the framework default (0.6): below threshold counts as "would fall
 * back to the LLM router" rather than a wrong local match. This directly tests the proposal's
 * central claim (docs/architecture/offline-skill-classifier-proposal.md §9): a trained classifier
 * resolves more requests locally, in-process, without an LLM round-trip.</p>
 */
class RoutingImprovementBenchmarkTest {

    private static final double THRESHOLD = 0.6;

    private record SkillFixture(String name, String description, List<String> examples, List<String> routingHints) {}

    private static final List<SkillFixture> SKILLS = List.of(
            new SkillFixture("spending-analysis",
                    "Analyzes personal spending by category and time period.",
                    List.of("how much did I spend this month", "show me spending by category",
                            "compare august and july", "what's my biggest expense category"),
                    List.of("spending", "budget", "expenses", "transactions")),
            new SkillFixture("weather-skill",
                    "Answers weather-related questions using real-time forecast data.",
                    List.of("what's the weather today", "will it rain tomorrow",
                            "current temperature in Berlin", "forecast for this weekend"),
                    List.of("weather", "forecast", "temperature", "rain")),
            new SkillFixture("code-review",
                    "Reviews source code for bugs, style issues and improvements.",
                    List.of("can you review this pull request", "find bugs in this function",
                            "check this code for issues", "suggest improvements to this class"),
                    List.of("code review", "bugs", "pull request", "refactor")),
            new SkillFixture("translate",
                    "Translates text between languages.",
                    List.of("translate this to spanish", "how do you say hello in french",
                            "convert this sentence to german", "translate the following paragraph"),
                    List.of("translate", "language", "spanish", "french")),
            new SkillFixture("jira-integration",
                    "Creates and manages Jira tickets and issues.",
                    List.of("create a jira ticket for this bug", "assign this issue to me",
                            "move this ticket to done", "list my open jira issues"),
                    List.of("jira", "ticket", "issue", "sprint")),
            new SkillFixture("summarize",
                    "Summarizes long text into concise bullet points.",
                    List.of("summarize this article", "give me the key points of this document",
                            "tl;dr this report", "condense this into bullet points"),
                    List.of("summarize", "summary", "tldr", "bullet points"))
    );

    // Held-out paraphrases: none of these strings appear in SKILLS' examples/routing-hints/description.
    private static final Map<String, List<String>> HELD_OUT = new LinkedHashMap<>();
    static {
        HELD_OUT.put("spending-analysis", List.of(
                "break down my expenses for last quarter",
                "where is most of my money going",
                "did I spend more on groceries than dining out",
                "show my transaction history for June"));
        HELD_OUT.put("weather-skill", List.of(
                "should I bring an umbrella tomorrow",
                "is it going to be sunny this weekend in Rome",
                "how hot is it outside right now",
                "give me the 5 day forecast"));
        HELD_OUT.put("code-review", List.of(
                "spot any bugs in this diff",
                "is this function well written",
                "review my latest commit for style problems",
                "any security issues in this snippet"));
        HELD_OUT.put("translate", List.of(
                "what does 'buongiorno' mean in english",
                "put this paragraph into italian",
                "how would a german speaker say this",
                "localize this string for the japanese market"));
        HELD_OUT.put("jira-integration", List.of(
                "open a bug ticket for the login crash",
                "who is this jira issue assigned to",
                "what sprint is this story in",
                "close out this ticket as done"));
        HELD_OUT.put("summarize", List.of(
                "give me the tldr of this email thread",
                "what are the main takeaways from this report",
                "shorten this into a few bullet points",
                "recap this meeting transcript"));
    }

    private static final String[] FEATURE_NAMES = buildFeatureNames();

    @Test
    void trainedClassifierResolvesMoreHeldOutQueriesLocallyThanZeroShotCosineSimilarity() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // --- baseline: today's default engine (zero-shot cosine similarity vs description) ---
        Map<String, Embedding> descriptionEmbeddings = new LinkedHashMap<>();
        for (SkillFixture skill : SKILLS) {
            descriptionEmbeddings.put(skill.name(), embeddingModel.embed(skill.description()).content());
        }

        // --- trained: mvn gargantua:train-router pipeline (examples + routing-hints + description) ---
        List<TrainingExample> dataset = new ArrayList<>();
        for (SkillFixture skill : SKILLS) {
            skill.examples().forEach(e -> dataset.add(new TrainingExample(skill.name(), e)));
            skill.routingHints().forEach(h -> dataset.add(new TrainingExample(skill.name(), h)));
            dataset.add(new TrainingExample(skill.name(), skill.description()));
        }
        Model<Label> trainedModel = new RouterTrainer().train(dataset);

        Report semanticReport = new Report("semantic (zero-shot, today's default)");
        Report tribuoReport = new Report("tribuo (trained, mvn gargantua:train-router)");

        for (var entry : HELD_OUT.entrySet()) {
            String expected = entry.getKey();
            for (String query : entry.getValue()) {
                Embedding queryEmbedding = embeddingModel.embed(query).content();

                String bestSkill = null;
                double bestScore = -1.0;
                for (var d : descriptionEmbeddings.entrySet()) {
                    double score = cosineSimilarity(queryEmbedding.vector(), d.getValue().vector());
                    if (score > bestScore) {
                        bestScore = score;
                        bestSkill = d.getKey();
                    }
                }
                semanticReport.record(expected, bestSkill, bestScore, query);

                float[] vector = queryEmbedding.vector();
                double[] values = new double[vector.length];
                for (int i = 0; i < vector.length; i++) values[i] = vector[i];
                Prediction<Label> prediction = trainedModel.predict(
                        new ArrayExample<>(new Label(Label.UNKNOWN), FEATURE_NAMES, values));
                tribuoReport.record(expected, prediction.getOutput().getLabel(), prediction.getOutput().getScore(), query);
            }
        }

        semanticReport.print();
        tribuoReport.print();

        System.out.printf("%n=== Delta ===%n");
        System.out.printf("Correctly routed locally (no LLM fallback needed): %s -> %s%n",
                pct(semanticReport.correctlyRoutedLocally, semanticReport.total),
                pct(tribuoReport.correctlyRoutedLocally, tribuoReport.total));
        System.out.printf("Would hit the LLM fallback: %s -> %s%n",
                pct(semanticReport.belowThreshold, semanticReport.total),
                pct(tribuoReport.belowThreshold, tribuoReport.total));
        System.out.printf("Wrong skill with high confidence (worse than a fallback): %s -> %s%n",
                pct(semanticReport.wrongAboveThreshold, semanticReport.total),
                pct(tribuoReport.wrongAboveThreshold, tribuoReport.total));

        assertTrue(tribuoReport.correctlyRoutedLocally >= semanticReport.correctlyRoutedLocally,
                "Expected the trained Tribuo classifier to resolve at least as many held-out queries "
                        + "locally as zero-shot cosine similarity — got semantic="
                        + semanticReport.correctlyRoutedLocally + " vs tribuo=" + tribuoReport.correctlyRoutedLocally
                        + " out of " + semanticReport.total);
    }

    private static class Report {
        final String label;
        int total;
        int correctlyRoutedLocally;
        int belowThreshold;
        int wrongAboveThreshold;

        Report(String label) { this.label = label; }

        void record(String expected, String predicted, double confidence, String query) {
            total++;
            boolean correct = expected.equals(predicted);
            boolean aboveThreshold = confidence >= THRESHOLD;
            if (aboveThreshold && correct) correctlyRoutedLocally++;
            else if (!aboveThreshold) belowThreshold++;
            else wrongAboveThreshold++;

            System.out.printf("  [%-9s] \"%s\" -> predicted=%s (%.3f) expected=%s%s%n",
                    aboveThreshold ? (correct ? "OK" : "WRONG") : "FALLBACK",
                    query, predicted, confidence, expected,
                    correct ? "" : "  <-- MISMATCH");
        }

        void print() {
            System.out.printf("%n=== %s ===%n", label);
            System.out.printf("  correctly routed locally: %s%n", pct(correctlyRoutedLocally, total));
            System.out.printf("  would fall back to LLM:   %s%n", pct(belowThreshold, total));
            System.out.printf("  wrong (above threshold):  %s%n", pct(wrongAboveThreshold, total));
        }
    }

    private static String pct(int n, int total) {
        return n + "/" + total + " (" + Math.round(100.0 * n / total) + "%)";
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static String[] buildFeatureNames() {
        String[] names = new String[384];
        for (int i = 0; i < names.length; i++) names[i] = "f" + i;
        return names;
    }
}
