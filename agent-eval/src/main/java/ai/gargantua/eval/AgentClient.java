package ai.gargantua.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls an agent via REST API. Works with any agent that exposes POST /api/agent/chat.
 */
public class AgentClient {

    private final String agentUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public AgentClient(String agentUrl) {
        this.agentUrl = agentUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send a message to the agent and return the text response.
     */
    public String chat(String message) throws Exception {
        var body = json.writeValueAsString(java.util.Map.of("message", message));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(agentUrl + "/api/agent/chat"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", "eval-runner")
                .header("X-Session-Id", "eval-" + System.currentTimeMillis())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Agent returned HTTP %d: %s".formatted(
                    response.statusCode(), response.body()));
        }

        var node = json.readTree(response.body());
        return node.has("text") ? node.get("text").asText()
             : node.has("response") ? node.get("response").asText()
             : response.body();
    }
}
