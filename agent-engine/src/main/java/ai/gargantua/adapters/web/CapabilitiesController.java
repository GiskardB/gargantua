package ai.gargantua.adapters.web;

import ai.gargantua.core.a2a.A2ATask;
import ai.gargantua.core.a2a.A2ATask.A2AMessage;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unified A2A and capabilities controller. Serves the agent card at both the
 * A2A-standard {@code /.well-known/agent.json} path and the legacy
 * {@code /api/capabilities} path. Also handles A2A JSON-RPC task operations
 * at {@code /a2a}.
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

    @GetMapping(value = "/api/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get agent capabilities", description = "Returns the agent's capabilities as an A2A Agent Card (backward-compatible).")
    @ApiResponse(responseCode = "200", description = "Agent capabilities")
    public ResponseEntity<AgentCard> getCapabilities(HttpServletRequest request) {
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
    @Operation(summary = "A2A JSON-RPC endpoint", description = "Handles A2A protocol task operations: tasks/send, tasks/get, tasks/cancel.")
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
            case "tasks/send" -> handleTaskSend(id, params);
            case "tasks/get" -> handleTaskGet(id, params);
            case "tasks/cancel" -> handleTaskCancel(id, params);
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> handleTaskSend(Object id, Map<String, Object> params) {
        if (orchestratorEngine == null) {
            return jsonRpcError(id, -32603, "Orchestrator engine not available");
        }

        String message = null;
        String skillHint = (String) params.get("skillHint");

        // Extract message from params — support nested input object or flat message
        Object inputObj = params.get("input");
        if (inputObj instanceof Map) {
            message = (String) ((Map<String, Object>) inputObj).get("content");
        }
        if (message == null) {
            message = (String) params.get("message");
        }
        if (message == null) {
            return jsonRpcError(id, -32602, "Missing 'message' or 'input.content' in params");
        }

        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        A2AMessage inputMessage = new A2AMessage("user", message, "text/plain");

        try {
            AgentRequest.Builder requestBuilder = AgentRequest.builder()
                    .message(message)
                    .userId("a2a-agent")
                    .sessionId(taskId);

            if (skillHint != null && !skillHint.isBlank()) {
                requestBuilder.forceSkill(skillHint);
            }

            AgentResponse response = orchestratorEngine.invoke(requestBuilder.build());

            A2AMessage outputMessage = new A2AMessage("agent", response.text(), "text/plain");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("skillUsed", response.skillUsed());
            metadata.put("durationMs", response.durationMs());
            metadata.put("totalTokens", response.totalTokens());

            A2ATask task = new A2ATask(
                    taskId, "completed", inputMessage, outputMessage, now, Instant.now(), metadata
            );

            return jsonRpcResult(id, taskToMap(task));

        } catch (Exception e) {
            log.error("A2A tasks/send failed: {}", e.getMessage(), e);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("error", e.getMessage());

            A2ATask task = new A2ATask(
                    taskId, "failed", inputMessage, null, now, Instant.now(), metadata
            );

            return jsonRpcResult(id, taskToMap(task));
        }
    }

    private ResponseEntity<Map<String, Object>> handleTaskGet(Object id, Map<String, Object> params) {
        // Synchronous implementation — tasks are not stored, so we cannot retrieve them after completion.
        return jsonRpcError(id, -32602, "Task storage not implemented. Tasks are executed synchronously via tasks/send.");
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
        map.put("status", task.status());
        map.put("input", messageToMap(task.input()));
        map.put("output", task.output() != null ? messageToMap(task.output()) : null);
        map.put("createdAt", task.createdAt().toString());
        map.put("updatedAt", task.updatedAt().toString());
        map.put("metadata", task.metadata());
        return map;
    }

    private Map<String, Object> messageToMap(A2AMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", msg.role());
        map.put("content", msg.content());
        map.put("contentType", msg.contentType());
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
