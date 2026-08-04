package ai.gargantua.autoconfigure;

import ai.gargantua.core.mcp.McpAuth;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.mcp.McpTransport;
import ai.gargantua.core.secret.SecretResolver;
import ai.gargantua.core.tool.ToolProvider;
import ai.gargantua.mcp.client.McpConnectionException;
import ai.gargantua.mcp.client.McpToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds {@link ToolProvider}s from declared MCP servers.
 *
 * <p>Both delivery modes converge here. In library mode the declarations come from
 * {@code agent.mcp-client.servers} in application configuration; in runtime mode they
 * come from the {@code spec.mcp.servers} block of a bundle manifest. Either way they
 * become {@link McpServerSpec}s and then connected providers.</p>
 *
 * <p>By default a server that cannot be reached is logged and skipped, so one broken
 * dependency does not stop the agent from starting. Set {@code fail-fast} when a server
 * is essential and starting without it would be worse than not starting at all.</p>
 */
public final class McpToolProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(McpToolProviderFactory.class);

    private McpToolProviderFactory() {}

    /** Builds providers from application configuration — the library-mode entry point. */
    public static List<ToolProvider> fromProperties(AgentProperties properties, SecretResolver secrets) {
        AgentProperties.McpClient config = properties.getMcpClient();
        if (config == null || !config.isEnabled() || config.getServers().isEmpty()) {
            return List.of();
        }
        List<McpServerSpec> specs = new ArrayList<>();
        for (AgentProperties.McpClient.Server server : config.getServers()) {
            try {
                specs.add(toSpec(server));
            } catch (IllegalArgumentException e) {
                // An invalid declaration is a configuration error: report it and move on,
                // unless the operator asked for strictness.
                if (config.isFailFast()) {
                    throw e;
                }
                log.error("Skipping invalid MCP server declaration: {}", e.getMessage());
            }
        }
        return connectAll(specs, secrets,
                Duration.ofSeconds(Math.max(1, config.getRequestTimeoutSeconds())),
                config.isFailFast());
    }

    /**
     * Connects every enabled spec, returning one provider per reachable server.
     *
     * @param failFast when true, the first connection failure propagates
     */
    public static List<ToolProvider> connectAll(List<McpServerSpec> specs, SecretResolver secrets,
                                                Duration requestTimeout, boolean failFast) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<ToolProvider> providers = new ArrayList<>();
        for (McpServerSpec spec : specs) {
            if (!spec.enabled()) {
                log.info("MCP server '{}' is disabled, skipping", spec.name());
                continue;
            }
            try {
                providers.add(McpToolProvider.connect(spec, secrets, requestTimeout));
            } catch (McpConnectionException e) {
                if (failFast) {
                    closeAll(providers);
                    throw e;
                }
                log.error("MCP server '{}' unavailable, continuing without it: {}",
                        spec.name(), e.getMessage());
            }
        }
        return List.copyOf(providers);
    }

    /** Translates a configuration entry into the neutral spec shared with the manifest. */
    static McpServerSpec toSpec(AgentProperties.McpClient.Server server) {
        McpTransport transport;
        try {
            transport = McpTransport.valueOf(
                    server.getTransport().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP server '" + server.getName()
                    + "': unknown transport '" + server.getTransport() + "'");
        }

        Set<String> allowedTools = server.getAllowedTools() == null
                ? Set.of() : new LinkedHashSet<>(server.getAllowedTools());

        return new McpServerSpec(
                server.getName(),
                transport,
                blankToNull(server.getCommand()),
                server.getArgs(),
                server.getEnv(),
                blankToNull(server.getUrl()),
                toAuth(server.getAuth()),
                allowedTools,
                server.isEnabled());
    }

    private static McpAuth toAuth(AgentProperties.McpClient.Auth auth) {
        if (auth == null || auth.getType() == null || auth.getType().isBlank()
                || "none".equalsIgnoreCase(auth.getType())) {
            return McpAuth.none();
        }
        return new McpAuth(auth.getType(), blankToNull(auth.getValue()), blankToNull(auth.getHeaderName()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void closeAll(List<ToolProvider> providers) {
        for (ToolProvider provider : providers) {
            try {
                provider.close();
            } catch (Exception e) {
                log.warn("Failed to close provider '{}': {}", provider.name(), e.getMessage());
            }
        }
    }
}
