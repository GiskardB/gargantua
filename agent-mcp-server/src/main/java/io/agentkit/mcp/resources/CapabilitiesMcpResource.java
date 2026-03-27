package io.agentkit.mcp.resources;

import io.agentkit.mcp.AgentMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Exposes the agent's capabilities as an MCP resource, allowing MCP clients
 * to discover what the agent can do.
 * <p>
 * This is a placeholder implementation. The actual resource registration with
 * the MCP SDK will be completed when skill discovery is available.
 */
@Component
@ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
public class CapabilitiesMcpResource {

    private static final Logger log = LoggerFactory.getLogger(CapabilitiesMcpResource.class);

    private final AgentMcpProperties properties;

    public CapabilitiesMcpResource(AgentMcpProperties properties) {
        this.properties = properties;
        log.info("Registered MCP resource: capabilities (uri=agent://capabilities)");
    }

    /**
     * Placeholder: returns a map describing the agent's capabilities.
     *
     * @return capability descriptor
     */
    public Map<String, Object> getCapabilities() {
        return Map.of(
                "name", properties.getServer().getName(),
                "version", properties.getServer().getVersion(),
                "description", properties.getServer().getDescription(),
                "mode", properties.getMode(),
                "tools", Map.of(
                        properties.getGateway().getToolName(),
                        properties.getGateway().getToolDescription()
                )
        );
    }
}
