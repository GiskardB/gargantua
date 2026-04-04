package ai.gargantua.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls an LLM (via OpenAI-compatible API) to judge agent responses.
 * Works with OpenAI, Ollama, LiteLLM, or any OpenAI-compatible endpoint.
 */
public class LlmJudge {

    private static final String SYSTEM_PROMPT = """
            You are an evaluation judge. Score the AI response on a scale from 0.0 to 1.0.
            
            Respond in EXACTLY this format (no other text):
            SCORE: <number>
            PASSED: <comma-separated list of passed behaviors, or NONE>
            FAILED: <comma-separated list of failed behaviors, or NONE>
            REASON: <one-line explanation>
            """;

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public LlmJudge(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public JudgeResult judge(EvalCase evalCase, String agentResponse) {
        try {
            var userPrompt = buildPrompt(evalCase, agentResponse);
            var response = callLlm(userPrompt);
            return parseResponse(response, evalCase.expectedBehaviors());
        } catch (Exception e) {
            return keywordFallback(evalCase, agentResponse);
        }
    }

    private String callLlm(String userMessage) throws Exception {
        var messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userMessage)
        );
        var body = json.writeValueAsString(Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.0,
                "max_tokens", 200
        ));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var node = json.readTree(response.body());
        return node.at("/choices/0/message/content").asText("");
    }

    private String buildPrompt(EvalCase c, String response) {
        var sb = new StringBuilder();
        sb.append("User input: ").append(c.input()).append("\n\n");
        sb.append("Agent response: ").append(response).append("\n\n");
        sb.append("Expected behaviors:\n");
        c.expectedBehaviors().forEach(b -> sb.append("- ").append(b).append("\n"));
        if (c.forbiddenBehaviors() != null && !c.forbiddenBehaviors().isEmpty()) {
            sb.append("\nForbidden behaviors:\n");
            c.forbiddenBehaviors().forEach(b -> sb.append("- ").append(b).append("\n"));
        }
        return sb.toString();
    }

    private JudgeResult parseResponse(String output, List<String> allExpected) {
        double score = 0.5;
        var passed = new ArrayList<String>();
        var failed = new ArrayList<>(allExpected);
        var reason = "Could not parse judge output";

        for (var line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("SCORE:")) {
                try { score = Double.parseDouble(line.substring(6).trim()); }
                catch (NumberFormatException ignored) {}
            } else if (line.startsWith("PASSED:") && !line.contains("NONE")) {
                passed = new ArrayList<>(List.of(line.substring(7).trim().split("\\s*,\\s*")));
                failed.removeAll(passed);
            } else if (line.startsWith("REASON:")) {
                reason = line.substring(7).trim();
            }
        }
        return new JudgeResult(Math.max(0, Math.min(1, score)), passed, failed, reason);
    }

    private JudgeResult keywordFallback(EvalCase c, String response) {
        var lower = response != null ? response.toLowerCase() : "";
        var passed = new ArrayList<String>();
        var failed = new ArrayList<String>();
        for (var exp : c.expectedBehaviors()) {
            boolean found = false;
            for (var word : exp.toLowerCase().split("\\s+")) {
                if (word.length() > 4 && lower.contains(word)) { found = true; break; }
            }
            if (found) passed.add(exp); else failed.add(exp);
        }
        double score = c.expectedBehaviors().isEmpty() ? 1.0
                : (double) passed.size() / c.expectedBehaviors().size();
        return new JudgeResult(score, passed, failed, "Keyword fallback");
    }

    public record JudgeResult(double score, List<String> passed, List<String> failed, String reason) {}
}
