package ai.gargantua.mcp;

import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.mcp.gateway.ChatMcpTool;
import ai.gargantua.mcp.resources.CapabilitiesMcpResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;

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
 *
 * <p><b>2026-09 wiring.</b> Registering {@link ChatMcpTool}/{@link CapabilitiesMcpResource}
 * as plain beans never made them reachable — nothing constructed the actual
 * {@link McpSyncServer} or exposed its transport as a Spring route, so {@code agent.mcp.enabled=true}
 * logged a convincing startup message while every MCP client request 404'd. This now builds
 * a real {@link WebMvcSseServerTransportProvider} at {@code agent.mcp.transport.path}, wraps
 * the gateway tool and capabilities resource as real MCP feature specifications, and exposes
 * the transport's router function as a bean so Spring MVC actually serves it.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "agent.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(AgentMcpProperties.class)
public class AgentMcpServerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentMcpServerAutoConfiguration.class);

    /** JSON Schema for the single gateway tool's input: one required "message" string. */
    private static final String GATEWAY_TOOL_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}";

    private final AgentMcpProperties properties;
    private McpSyncServer mcpSyncServer;

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

    @PreDestroy
    void shutdown() {
        if (mcpSyncServer != null) {
            mcpSyncServer.closeGracefully();
        }
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

    @Bean
    @ConditionalOnMissingBean(WebMvcSseServerTransportProvider.class)
    public WebMvcSseServerTransportProvider mcpTransportProvider(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new WebMvcSseServerTransportProvider(objectMapperProvider.getIfAvailable(ObjectMapper::new),
                properties.getTransport().getPath());
    }

    /**
     * Builds the real {@link McpSyncServer} — the gateway tool wraps {@link ChatMcpTool#chat},
     * the capabilities resource serializes {@link CapabilitiesMcpResource#getCapabilities()} —
     * and returns its transport's router function so Spring MVC actually registers the routes.
     * This is the bean that was missing entirely before: without it, {@code transportProvider}
     * above exists but nothing ever calls {@link WebMvcSseServerTransportProvider#getRouterFunction()}.
     */
    @Bean
    public RouterFunction<ServerResponse> mcpServerRouterFunction(
            WebMvcSseServerTransportProvider transportProvider,
            ChatMcpTool chatMcpTool,
            CapabilitiesMcpResource capabilitiesMcpResource,
            ObjectProvider<ObjectMapper> objectMapperProvider) {

        var objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        var tool = new McpSchema.Tool(
                properties.getGateway().getToolName(),
                properties.getGateway().getToolDescription(),
                GATEWAY_TOOL_SCHEMA);
        var toolSpec = new McpServerFeatures.SyncToolSpecification(tool, (exchange, arguments) -> {
            Object message = arguments.get("message");
            String reply = chatMcpTool.chat(message != null ? message.toString() : "");
            return new McpSchema.CallToolResult(reply, false);
        });

        var resource = new McpSchema.Resource(
                "agent://capabilities",
                "capabilities",
                "Live capability discovery for this agent (skills, tools, gateway).",
                "application/json",
                null);
        var resourceSpec = new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            String json;
            try {
                json = objectMapper.writeValueAsString(capabilitiesMcpResource.getCapabilities());
            } catch (Exception e) {
                json = "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
            }
            return new McpSchema.ReadResourceResult(
                    List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
        });

        mcpSyncServer = McpServer.sync(transportProvider)
                .serverInfo(properties.getServer().getName(), properties.getServer().getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(true, false).build())
                .tools(toolSpec)
                .resources(resourceSpec)
                .build();

        log.info("MCP transport wired: tool='{}', resource='agent://capabilities', path={}",
                properties.getGateway().getToolName(), properties.getTransport().getPath());

        return transportProvider.getRouterFunction();
    }
}
