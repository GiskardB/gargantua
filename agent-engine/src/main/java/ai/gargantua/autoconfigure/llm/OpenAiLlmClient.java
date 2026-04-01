package ai.gargantua.autoconfigure.llm;

import ai.gargantua.core.llm.LlmClient;
import ai.gargantua.core.llm.LlmRequest;
import ai.gargantua.core.llm.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Native Java HTTP client for OpenAI and Azure OpenAI chat completions API.
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final String endpoint;
    private final String apiKey;
    private final boolean isAzure;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(String endpoint, String apiKey, boolean isAzure) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.isAzure = isAzure;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            String url = endpoint.replaceAll("/+$", "") + "/chat/completions";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", request.model());
            body.put("temperature", request.temperature());
            body.put("max_tokens", request.maxTokens());

            ArrayNode messages = body.putArray("messages");
            for (var msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (isAzure) {
                reqBuilder.header("api-key", apiKey);
            } else {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("OpenAI API error (HTTP " + response.statusCode() + "): " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            String model = json.path("model").asText(request.model());
            int inputTokens = json.path("usage").path("prompt_tokens").asInt(0);
            int outputTokens = json.path("usage").path("completion_tokens").asInt(0);

            return new LlmResponse(content, model, inputTokens, outputTokens);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }
}
