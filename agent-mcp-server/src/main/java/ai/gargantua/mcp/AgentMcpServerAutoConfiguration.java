package ai.gargantua.mcp;

import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.mcp.gateway.ChatMcpTool;
import ai.gargantua.mcp.resources.CapabilitiesMcpResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for the Agent MCP Server.
 * <p>
 * Activated when {@code agent.mcp.enabled=true} is set in application properties.
 * Registers MCP tools, resources, and prompts that expose the agent's capabilities
 * via the Model Context Protocol.
 *
 * <p><b>v1.2.13+ wiring.</b> Previously the {@link ChatMcpTool} and
 * {@link CapabilitiesMcpResource} beans relied on classpath component
 * scanning ({@code @Component}) to be discovered. That works only when
 * the consuming application happens to scan the {@code ai.gargantua.mcp}
 * package — which user apps (rooted in their own package) do not. The
 * auto-configuration now registers both beans explicitly via {@code @Bean}
 * factories so they show up in every {@code @SpringBootApplication} that
 * sets {@code agent.mcp.enabled=true}, regardless of base package.</p>
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

    @Bean
    @ConditionalOnMissingBean(ChatMcpTool.class)
    public ChatMcpTool chatMcpTool(AgentMcpProperties props,
                                   OrchestratorEngine orchestratorEngine) {
        return new ChatMcpTool(props, orchestratorEngine);
    }

    @Bean
    @ConditionalOnMissingBean(CapabilitiesMcpResource.class)
    public CapabilitiesMcpResource capabilitiesMcpResource(AgentMcpProperties props,
                                                           ObjectProvider<SkillRegistry> skillRegistryProvider,
                                                           ObjectProvider<ToolRegistry> toolRegistryProvider) {
        return new CapabilitiesMcpResource(props, skillRegistryProvider, toolRegistryProvider);
    }
}
