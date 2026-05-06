package ai.gargantua.mcp.gateway;

import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.mcp.AgentMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Wraps the {@link OrchestratorEngine} as an MCP tool, allowing external MCP clients
 * (Claude Desktop, Cursor, VS Code, etc.) to invoke the agent through the Model Context
 * Protocol. The single gateway tool is exposed under the name configured by
 * {@code agent.mcp.gateway.tool-name} (default {@code agent-chat}).
 *
 * <p>When the bean is constructed, the configured tool name and description are logged.
 * Each {@link #chat(String, String, String)} call produces a fresh agent invocation
 * through the full pipeline (guardrails → routing → memory → LLM → output guardrails).</p>
 */
@Component
@ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
public class ChatMcpTool {

    private static final Logger log = LoggerFactory.getLogger(ChatMcpTool.class);

    private final AgentMcpProperties properties;
    private final OrchestratorEngine orchestratorEngine;

    public ChatMcpTool(AgentMcpProperties properties, OrchestratorEngine orchestratorEngine) {
        this.properties = properties;
        this.orchestratorEngine = orchestratorEngine;
        log.info("Registered MCP tool: {} — {}",
                properties.getGateway().getToolName(),
                properties.getGateway().getToolDescription());
    }

    /**
     * Convenience overload that generates a fresh user/session id pair. Useful for
     * stateless MCP clients that don't track sessions.
     */
    public String chat(String userMessage) {
        return chat(userMessage, "mcp-client", UUID.randomUUID().toString());
    }

    /**
     * Invokes the agent with the given user message, attributing the call to the
     * provided MCP client identity. Returns the agent's text response, or an error
     * sentinel when the underlying engine throws.
     *
     * @param userMessage the message from the MCP client
     * @param userId      caller identity (typically the MCP client name)
     * @param sessionId   conversation session id; pass a stable value to enable working memory
     * @return agent response text
     */
    public String chat(String userMessage, String userId, String sessionId) {
        if (userMessage == null || userMessage.isBlank()) {
            return "{\"error\":\"empty user message\"}";
        }
        log.debug("MCP chat invoked: tool={}, userId={}, sessionId={}, msgLen={}",
                properties.getGateway().getToolName(), userId, sessionId, userMessage.length());

        AgentRequest request = AgentRequest.builder()
                .message(userMessage)
                .userId(userId != null ? userId : "mcp-client")
                .sessionId(sessionId != null ? sessionId : UUID.randomUUID().toString())
                .contextAttribute("source", "mcp")
                .contextAttribute("mcp.tool", properties.getGateway().getToolName())
                .build();

        try {
            AgentResponse response = orchestratorEngine.invoke(request);
            return response.text();
        } catch (Exception e) {
            log.warn("MCP chat invocation failed: {}", e.getMessage());
            return "{\"error\":\"agent invocation failed: " + escape(e.getMessage()) + "\"}";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
