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
 * Native Java HTTP client for the Anthropic Messages API.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicLlmClient(String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            String url = endpoint.replaceAll("/+$", "") + "/v1/messages";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", request.model());
            body.put("max_tokens", request.maxTokens());
            body.put("temperature", request.temperature());

            // Extract system message and non-system messages
            ArrayNode messages = body.putArray("messages");
            for (var msg : request.messages()) {
                if ("system".equals(msg.role())) {
                    body.put("system", msg.content());
                } else {
                    ObjectNode msgNode = messages.addObject();
                    msgNode.put("role", msg.role());
                    msgNode.put("content", msg.content());
                }
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Anthropic API error (HTTP " + response.statusCode() + "): " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("content").path(0).path("text").asText("");
            String model = json.path("model").asText(request.model());
            int inputTokens = json.path("usage").path("input_tokens").asInt(0);
            int outputTokens = json.path("usage").path("output_tokens").asInt(0);

            return new LlmResponse(content, model, inputTokens, outputTokens);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }
}
