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
 * Native Java HTTP client for the Ollama chat API.
 */
public class OllamaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClient.class);

    private final String endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaLlmClient(String endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        try {
            String url = endpoint.replaceAll("/+$", "") + "/api/chat";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", request.model());
            body.put("stream", false);

            ObjectNode options = body.putObject("options");
            options.put("temperature", request.temperature());
            options.put("num_predict", request.maxTokens());

            ArrayNode messages = body.putArray("messages");
            for (var msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Ollama API error (HTTP " + response.statusCode() + "): " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("message").path("content").asText("");
            String model = json.path("model").asText(request.model());
            int inputTokens = json.path("prompt_eval_count").asInt(0);
            int outputTokens = json.path("eval_count").asInt(0);

            return new LlmResponse(content, model, inputTokens, outputTokens);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Ollama API call failed: " + e.getMessage(), e);
        }
    }
}
