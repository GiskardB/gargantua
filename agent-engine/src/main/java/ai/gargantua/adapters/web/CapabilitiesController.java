package ai.gargantua.adapters.web;

import ai.gargantua.core.a2a.A2ATask;
import ai.gargantua.core.a2a.A2ATask.Message;
import ai.gargantua.core.a2a.A2ATask.Part;
import ai.gargantua.core.a2a.A2ATask.TaskStatus;
import ai.gargantua.core.a2a.A2ATask.Artifact;
import ai.gargantua.core.a2a.AgentCard;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.autoconfigure.AgentCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A2A protocol controller. Serves the agent card at the A2A-standard
 * {@code /.well-known/agent.json} path. Also handles A2A JSON-RPC task
 * operations at {@code /a2a}.
 */
@RestController
@Tag(name = "A2A", description = "Agent-to-Agent protocol endpoints")
public class CapabilitiesController {

    private static final Logger log = LoggerFactory.getLogger(CapabilitiesController.class);

    private final AgentCardService agentCardService;

    @Nullable
    private final OrchestratorEngine orchestratorEngine;

    public CapabilitiesController(AgentCardService agentCardService,
                                  @Nullable OrchestratorEngine orchestratorEngine) {
        this.agentCardService = agentCardService;
        this.orchestratorEngine = orchestratorEngine;
    }

    // ==================== Agent Card (Discovery) ====================

    @GetMapping(value = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "A2A Agent Card", description = "Returns the agent's A2A Agent Card for discovery by other agents.")
    @ApiResponse(responseCode = "200", description = "Agent Card")
    public ResponseEntity<AgentCard> wellKnownAgentJson(HttpServletRequest request) {
        return agentCardResponse(request);
    }

    private ResponseEntity<AgentCard> agentCardResponse(HttpServletRequest request) {
        String baseUrl = deriveBaseUrl(request);
        AgentCard card = agentCardService.getAgentCard(baseUrl);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(card);
    }

    // ==================== A2A JSON-RPC ====================

    @SuppressWarnings("unchecked")
    @PostMapping(value = "/a2a", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "A2A JSON-RPC endpoint", description = "Handles A2A protocol operations: message/send, tasks/get, tasks/cancel.")
    @ApiResponse(responseCode = "200", description = "JSON-RPC response")
    public ResponseEntity<Map<String, Object>> handleA2A(@RequestBody Map<String, Object> jsonRpc) {
        var method = (String) jsonRpc.get("method");
        var id = jsonRpc.get("id");
        var params = (Map<String, Object>) jsonRpc.get("params");

        if (method == null) {
            return jsonRpcError(id, -32600, "Invalid Request: missing 'method'");
        }

        if (params == null) {
            params = Map.of();
        }

        log.debug("A2A JSON-RPC: method={}, id={}", method, id);

        return switch (method) {
            case "message/send" -> handleMessageSend(id, params);
            case "tasks/get" -> handleTaskGet(id, params);
            case "tasks/cancel" -> handleTaskCancel(id, params);
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> handleMessageSend(Object id, Map<String, Object> params) {
        if (orchestratorEngine == null) {
            return jsonRpcError(id, -32603, "Orchestrator engine not available");
        }

        String message = null;
        String skillHint = (String) params.get("skillHint");

        // Extract message from params — support nested message.parts or flat message
        Object messageObj = params.get("message");
        if (messageObj instanceof Map) {
            Map<String, Object> msgMap = (Map<String, Object>) messageObj;
            Object partsObj = msgMap.get("parts");
            if (partsObj instanceof List) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) partsObj;
                for (Map<String, Object> part : parts) {
                    if ("text".equals(part.get("kind"))) {
                        message = (String) part.get("text");
                        break;
                    }
                }
            }
        }
        // Fallback: support legacy flat params
        if (message == null) {
            Object inputObj = params.get("input");
            if (inputObj instanceof Map) {
                message = (String) ((Map<String, Object>) inputObj).get("content");
            }
        }
        if (message == null) {
            message = (String) params.get("message");
            if (message instanceof String) {
                // plain string message, use as-is
            }
        }
        if (message == null) {
            return jsonRpcError(id, -32602, "Missing 'message.parts' in params");
        }

        String taskId = UUID.randomUUID().toString();
        String contextId = (String) params.get("contextId");
        Instant now = Instant.now();

        Message inputMessage = new Message(
                UUID.randomUUID().toString(), "user",
                List.of(new Part("text", message))
        );

        try {
            AgentRequest.Builder requestBuilder = AgentRequest.builder()
                    .message(message)
                    .userId("a2a-agent")
                    .sessionId(taskId);

            if (skillHint != null && !skillHint.isBlank()) {
                requestBuilder.forceSkill(skillHint);
            }

            AgentResponse response = orchestratorEngine.invoke(requestBuilder.build());

            Message outputMessage = new Message(
                    UUID.randomUUID().toString(), "agent",
                    List.of(new Part("text", response.text()))
            );

            List<Artifact> artifacts = List.of(
                    new Artifact("response", List.of(new Part("text", response.text())))
            );

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("skillUsed", response.skillUsed());
            metadata.put("durationMs", response.durationMs());
            metadata.put("totalTokens", response.totalTokens());

            TaskStatus status = new TaskStatus("completed", outputMessage);

            A2ATask task = new A2ATask(
                    taskId, "task", contextId, status, artifacts, now, Instant.now(), metadata
            );

            return jsonRpcResult(id, taskToMap(task));

        } catch (Exception e) {
            log.error("A2A message/send failed: {}", e.getMessage(), e);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("error", e.getMessage());

            Message errorMessage = new Message(
                    UUID.randomUUID().toString(), "agent",
                    List.of(new Part("text", e.getMessage() != null ? e.getMessage() : "Unknown error"))
            );

            TaskStatus status = new TaskStatus("failed", errorMessage);

            A2ATask task = new A2ATask(
                    taskId, "task", contextId, status, null, now, Instant.now(), metadata
            );

            return jsonRpcResult(id, taskToMap(task));
        }
    }

    private ResponseEntity<Map<String, Object>> handleTaskGet(Object id, Map<String, Object> params) {
        // Synchronous implementation — tasks are not stored, so we cannot retrieve them after completion.
        return jsonRpcError(id, -32602, "Task storage not implemented. Tasks are executed synchronously via message/send.");
    }

    private ResponseEntity<Map<String, Object>> handleTaskCancel(Object id, Map<String, Object> params) {
        // Synchronous implementation — tasks complete immediately, cancellation is not applicable.
        return jsonRpcError(id, -32602, "Task cancellation not supported. Tasks are executed synchronously.");
    }

    // ==================== JSON-RPC helpers ====================

    private ResponseEntity<Map<String, Object>> jsonRpcResult(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", error);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> taskToMap(A2ATask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("kind", task.kind());
        map.put("contextId", task.contextId());
        map.put("status", statusToMap(task.status()));
        map.put("artifacts", task.artifacts() != null ? task.artifacts().stream().map(this::artifactToMap).toList() : null);
        map.put("createdAt", task.createdAt().toString());
        map.put("updatedAt", task.updatedAt().toString());
        map.put("metadata", task.metadata());
        return map;
    }

    private Map<String, Object> statusToMap(TaskStatus status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("state", status.state());
        map.put("message", status.message() != null ? messageToMap(status.message()) : null);
        return map;
    }

    private Map<String, Object> messageToMap(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("messageId", msg.messageId());
        map.put("role", msg.role());
        map.put("parts", msg.parts().stream().map(this::partToMap).toList());
        return map;
    }

    private Map<String, Object> partToMap(Part part) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", part.kind());
        map.put("text", part.text());
        return map;
    }

    private Map<String, Object> artifactToMap(Artifact artifact) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", artifact.name());
        map.put("parts", artifact.parts().stream().map(this::partToMap).toList());
        return map;
    }

    private String deriveBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();

        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + host + contextPath;
        }
        return scheme + "://" + host + ":" + port + contextPath;
    }
}
