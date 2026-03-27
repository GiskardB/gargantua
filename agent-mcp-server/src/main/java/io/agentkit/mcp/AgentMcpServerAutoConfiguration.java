package io.agentkit.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for the Agent MCP Server.
 * <p>
 * Activated when {@code agent.mcp.enabled=true} is set in application properties.
 * Registers MCP tools, resources, and prompts that expose the agent's capabilities
 * via the Model Context Protocol.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(AgentMcpProperties.class)
public class AgentMcpServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMcpServerAutoConfiguration.class);

    private final AgentMcpProperties properties;

    public AgentMcpServerAutoConfiguration(AgentMcpProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void logStatus() {
        log.info("MCP Server '{}' v{} initialized — transport={}, path={}, mode={}",
                properties.getServer().getName(),
                properties.getServer().getVersion(),
                properties.getTransport().getType(),
                properties.getTransport().getPath(),
                properties.getMode());
    }
}
