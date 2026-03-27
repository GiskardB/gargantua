package io.agentkit.mcp.gateway;

import io.agentkit.mcp.AgentMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Wraps the OrchestratorEngine as an MCP tool, allowing external MCP clients
 * to invoke the agent through the Model Context Protocol.
 * <p>
 * This is a placeholder implementation. The actual tool registration with the
 * MCP SDK will be wired when the orchestrator module is available.
 */
@Component
@ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
public class ChatMcpTool {

    private static final Logger log = LoggerFactory.getLogger(ChatMcpTool.class);

    private final AgentMcpProperties properties;

    public ChatMcpTool(AgentMcpProperties properties) {
        this.properties = properties;
        log.info("Registered MCP tool: {} — {}",
                properties.getGateway().getToolName(),
                properties.getGateway().getToolDescription());
    }

    /**
     * Placeholder: invoke the agent with the given user message.
     *
     * @param userMessage the message from the MCP client
     * @return agent response text
     */
    public String chat(String userMessage) {
        log.debug("MCP chat invoked with message: {}", userMessage);
        // TODO: delegate to OrchestratorEngine once available
        return "MCP tool '%s' received message but orchestrator is not yet wired."
                .formatted(properties.getGateway().getToolName());
    }
}
