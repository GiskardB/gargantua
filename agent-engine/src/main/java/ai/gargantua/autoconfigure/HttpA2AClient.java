package ai.gargantua.autoconfigure;

import ai.gargantua.core.a2a.A2AClient;
import ai.gargantua.core.a2a.A2ATask;
import ai.gargantua.core.a2a.A2ATask.Artifact;
import ai.gargantua.core.a2a.A2ATask.Message;
import ai.gargantua.core.a2a.A2ATask.Part;
import ai.gargantua.core.a2a.A2ATask.TaskStatus;
import ai.gargantua.core.a2a.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

        // Build A2A Message with Parts
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("kind", "text");
        textPart.put("text", message);

        Map<String, Object> messageMap = new LinkedHashMap<>();
        messageMap.put("messageId", UUID.randomUUID().toString());
        messageMap.put("role", "user");
        messageMap.put("parts", List.of(textPart));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", messageMap);
        if (skillHint != null && !skillHint.isBlank()) {
            params.put("skillHint", skillHint);
        }

        Map<String, Object> response = jsonRpcCall(agentUrl, "message/send", params, 1);
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
        String kind = (String) result.getOrDefault("kind", "task");
        String contextId = (String) result.get("contextId");

        // Parse status
        TaskStatus status;
        Object statusObj = result.get("status");
        if (statusObj instanceof Map) {
            Map<String, Object> statusMap = (Map<String, Object>) statusObj;
            String state = (String) statusMap.get("state");
            Message statusMessage = statusMap.get("message") != null
                    ? parseMessage((Map<String, Object>) statusMap.get("message"))
                    : null;
            status = new TaskStatus(state, statusMessage);
        } else {
            // Fallback for legacy string status
            status = new TaskStatus((String) statusObj, null);
        }

        // Parse artifacts
        List<Artifact> artifacts = null;
        Object artifactsObj = result.get("artifacts");
        if (artifactsObj instanceof List) {
            artifacts = new ArrayList<>();
            for (Object artObj : (List<Object>) artifactsObj) {
                Map<String, Object> artMap = (Map<String, Object>) artObj;
                String artName = (String) artMap.get("name");
                List<Part> artParts = parseParts((List<Map<String, Object>>) artMap.get("parts"));
                artifacts.add(new Artifact(artName, artParts));
            }
        }

        String createdAtStr = (String) result.get("createdAt");
        Instant createdAt = createdAtStr != null ? Instant.parse(createdAtStr) : Instant.now();
        String updatedAtStr = (String) result.get("updatedAt");
        Instant updatedAt = updatedAtStr != null ? Instant.parse(updatedAtStr) : createdAt;
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");

        return new A2ATask(id, kind, contextId, status, artifacts, createdAt, updatedAt, metadata);
    }

    @SuppressWarnings("unchecked")
    private Message parseMessage(Map<String, Object> map) {
        String messageId = (String) map.get("messageId");
        String role = (String) map.get("role");
        List<Part> parts = parseParts((List<Map<String, Object>>) map.get("parts"));
        return new Message(messageId, role, parts);
    }

    private List<Part> parseParts(List<Map<String, Object>> partsList) {
        if (partsList == null) {
            return List.of();
        }
        List<Part> parts = new ArrayList<>();
        for (Map<String, Object> partMap : partsList) {
            String kind = (String) partMap.get("kind");
            String text = (String) partMap.get("text");
            parts.add(new Part(kind, text));
        }
        return parts;
    }
}
