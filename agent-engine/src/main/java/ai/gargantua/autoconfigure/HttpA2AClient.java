package ai.gargantua.autoconfigure;

import ai.gargantua.core.a2a.A2AClient;
import ai.gargantua.core.a2a.A2ATask;
import ai.gargantua.core.a2a.A2ATask.A2AMessage;
import ai.gargantua.core.a2a.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP-based implementation of {@link A2AClient} using Spring's {@link RestClient}.
 * Communicates with remote A2A-compatible agents via their standard endpoints:
 * <ul>
 *   <li>{@code GET /.well-known/agent.json} for discovery</li>
 *   <li>{@code POST /a2a} for JSON-RPC task operations</li>
 * </ul>
 */
public class HttpA2AClient implements A2AClient {

    private static final Logger log = LoggerFactory.getLogger(HttpA2AClient.class);

    private final RestClient restClient;

    public HttpA2AClient() {
        this.restClient = RestClient.create();
    }

    public HttpA2AClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AgentCard discover(String agentUrl) {
        log.debug("Discovering agent at {}", agentUrl);
        return restClient.get()
                .uri(agentUrl + "/.well-known/agent.json")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AgentCard.class);
    }

    @Override
    public A2ATask sendTask(String agentUrl, String message, String skillHint) {
        log.debug("Sending task to {} (skillHint={})", agentUrl, skillHint);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("role", "user");
        input.put("content", message);
        input.put("contentType", "text/plain");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("input", input);
        if (skillHint != null && !skillHint.isBlank()) {
            params.put("skillHint", skillHint);
        }

        Map<String, Object> response = jsonRpcCall(agentUrl, "tasks/send", params, 1);
        return parseTaskFromResult(response);
    }

    @Override
    public Optional<A2ATask> getTask(String agentUrl, String taskId) {
        log.debug("Getting task {} from {}", taskId, agentUrl);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskId", taskId);

        try {
            Map<String, Object> response = jsonRpcCall(agentUrl, "tasks/get", params, 2);
            if (response.containsKey("error")) {
                return Optional.empty();
            }
            return Optional.of(parseTaskFromResult(response));
        } catch (Exception e) {
            log.warn("Failed to get task {} from {}: {}", taskId, agentUrl, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void cancelTask(String agentUrl, String taskId) {
        log.debug("Cancelling task {} at {}", taskId, agentUrl);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("taskId", taskId);

        jsonRpcCall(agentUrl, "tasks/cancel", params, 3);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonRpcCall(String agentUrl, String method,
                                             Map<String, Object> params, Object id) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("params", params);
        request.put("id", id);

        return restClient.post()
                .uri(agentUrl + "/a2a")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private A2ATask parseTaskFromResult(Map<String, Object> response) {
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            String errorMsg = error != null ? (String) error.get("message") : "Unknown error";
            throw new RuntimeException("A2A JSON-RPC error: " + errorMsg);
        }

        String id = (String) result.get("id");
        String status = (String) result.get("status");

        A2AMessage inputMsg = parseMessage((Map<String, Object>) result.get("input"));
        A2AMessage outputMsg = result.get("output") != null
                ? parseMessage((Map<String, Object>) result.get("output"))
                : null;

        Instant createdAt = Instant.parse((String) result.get("createdAt"));
        Instant updatedAt = Instant.parse((String) result.get("updatedAt"));
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

        return new A2ATask(id, status, inputMsg, outputMsg, createdAt, updatedAt, metadata);
    }

    private A2AMessage parseMessage(Map<String, Object> map) {
        return new A2AMessage(
                (String) map.get("role"),
                (String) map.get("content"),
                (String) map.get("contentType")
        );
    }
}
