package ai.gargantua.mcp.client;

import ai.gargantua.core.mcp.McpAuth;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.secret.SecretPlaceholders;
import ai.gargantua.core.secret.SecretResolver;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.core.tool.ToolParameter;
import ai.gargantua.core.tool.ToolProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exposes the tools of a single MCP server as agent tools.
 *
 * <p>This is what allows a code-free bundle to have tools at all: instead of shipping
 * compiled classes, a manifest declares MCP servers, and each one becomes a
 * {@link ToolProvider} whose tools are indistinguishable, to the orchestrator, from
 * {@link ai.gargantua.core.tool.AgentTool}-annotated Java methods.</p>
 *
 * <p>Tools are discovered once during {@link #connect}, so {@link #discover()} is a
 * cheap accessor and never fails. Connection problems surface at startup as
 * {@link McpConnectionException}; problems during a call are returned to the model as a
 * JSON {@code error} object so it can adapt rather than having the request aborted.</p>
 *
 * <p><strong>Transports.</strong> {@code STDIO} launches the server as a child process.
 * {@code HTTP} and {@code SSE} both use the SDK's HTTP/SSE transport — MCP SDK 0.9.0 has
 * no separate streamable-HTTP client, so the two are equivalent today and the
 * distinction is preserved in the manifest for when it gains one.</p>
 *
 * @see McpServerSpec
 */
public final class McpToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String CLIENT_NAME = "gargantua";
    private static final String CLIENT_VERSION = "1.0";

    private final McpServerSpec spec;
    private final McpSyncClient client;
    private final List<ToolDefinition> tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private McpToolProvider(McpServerSpec spec, McpSyncClient client, List<ToolDefinition> tools) {
        this.spec = spec;
        this.client = client;
        this.tools = List.copyOf(tools);
    }

    /** Connects using the default 30-second request timeout. */
    public static McpToolProvider connect(McpServerSpec spec, SecretResolver secrets) {
        return connect(spec, secrets, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Connects to {@code spec}, performs the MCP handshake and discovers its tools.
     *
     * <p>Secret and environment placeholders in the spec are expanded here, immediately
     * before use, so resolved credentials are never stored on the spec itself.</p>
     *
     * @throws McpConnectionException when the server is unreachable or rejects the handshake
     */
    public static McpToolProvider connect(McpServerSpec spec, SecretResolver secrets,
                                          Duration requestTimeout) {
        SecretResolver resolver = secrets != null ? secrets : SecretResolver.empty();
        McpSyncClient client = null;
        try {
            client = McpClient.sync(buildTransport(spec, resolver))
                    .requestTimeout(requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT)
                    .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                    .build();
            client.initialize();

            List<ToolDefinition> discovered = discoverTools(client, spec);
            log.info("MCP server '{}' connected via {} — {} tool(s) available",
                    spec.name(), spec.transport(), discovered.size());
            return new McpToolProvider(spec, client, discovered);
        } catch (McpConnectionException e) {
            closeQuietly(client, spec.name());
            throw e;
        } catch (Exception e) {
            closeQuietly(client, spec.name());
            throw new McpConnectionException(spec.name(), "connection failed: " + describe(e), e);
        }
    }

    private static McpClientTransport buildTransport(McpServerSpec spec, SecretResolver secrets) {
        return switch (spec.transport()) {
            case STDIO -> {
                ServerParameters params = ServerParameters
                        .builder(SecretPlaceholders.expand(spec.command(), secrets))
                        .args(expandAll(spec.args(), secrets))
                        .env(SecretPlaceholders.expandAll(spec.env(), secrets))
                        .build();
                yield new StdioClientTransport(params);
            }
            // SDK 0.9.0 exposes only an SSE-based HTTP transport; HTTP and SSE share it.
            case HTTP, SSE -> {
                String url = SecretPlaceholders.expand(spec.url(), secrets);
                HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(url);
                applyAuth(builder, spec.auth(), secrets, spec.name());
                yield builder.build();
            }
        };
    }

    private static List<String> expandAll(List<String> values, SecretResolver secrets) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(value -> SecretPlaceholders.expand(value, secrets)).toList();
    }

    private static void applyAuth(HttpClientSseClientTransport.Builder builder,
                                  McpAuth auth, SecretResolver secrets, String serverName) {
        if (auth == null || auth.isNone()) {
            return;
        }
        String value = SecretPlaceholders.expand(auth.value(), secrets);
        if (value == null || value.isBlank()) {
            log.warn("MCP server '{}': auth type '{}' declared but no value resolved", serverName, auth.type());
            return;
        }
        if (SecretPlaceholders.hasPlaceholder(value)) {
            // Sending an unresolved "${secrets.x}" as a credential would fail confusingly.
            throw new McpConnectionException(serverName,
                    "unresolved secret placeholder in auth value", null);
        }
        switch (auth.type()) {
            case "bearer" -> builder.customizeRequest(rb -> rb.header("Authorization", "Bearer " + value));
            case "basic" -> builder.customizeRequest(rb -> rb.header("Authorization", "Basic " + value));
            case "header" -> {
                String header = auth.headerName();
                if (header == null || header.isBlank()) {
                    throw new McpConnectionException(serverName,
                            "auth type 'header' requires headerName", null);
                }
                builder.customizeRequest(rb -> rb.header(header, value));
            }
            default -> log.warn("MCP server '{}': unknown auth type '{}', proceeding without authentication",
                    serverName, auth.type());
        }
    }

    private static List<ToolDefinition> discoverTools(McpSyncClient client, McpServerSpec spec) {
        McpSchema.ListToolsResult result = client.listTools();
        if (result == null || result.tools() == null) {
            return List.of();
        }
        List<ToolDefinition> definitions = new ArrayList<>();
        for (McpSchema.Tool tool : result.tools()) {
            if (tool == null || tool.name() == null) {
                continue;
            }
            if (!spec.permits(tool.name())) {
                log.debug("MCP server '{}': tool '{}' excluded by allow-list", spec.name(), tool.name());
                continue;
            }
            definitions.add(new ToolDefinition(
                    tool.name(),
                    tool.description() != null ? tool.description() : "",
                    false,          // parallelizable: unknown for remote tools, assume no
                    false,          // approval is a policy concern, not advertised by MCP
                    false,          // caching is configured locally, not by the server
                    "",             // approvalMessage
                    new String[0],  // approvalShowParameters
                    false,          // dangerous
                    toParameters(tool.inputSchema())
            ));
        }
        return definitions;
    }

    /**
     * Converts an MCP JSON Schema into provider-neutral parameters. Types are passed
     * through rather than flattened to strings, so the model sees the real contract the
     * server advertises.
     */
    private static List<ToolParameter> toParameters(McpSchema.JsonSchema schema) {
        if (schema == null || schema.properties() == null || schema.properties().isEmpty()) {
            return List.of();
        }
        List<String> required = schema.required() != null ? schema.required() : List.of();
        List<ToolParameter> parameters = new ArrayList<>();
        for (Map.Entry<String, Object> entry : schema.properties().entrySet()) {
            String type = ToolParameter.TYPE_STRING;
            String description = "";
            if (entry.getValue() instanceof Map<?, ?> property) {
                if (property.get("type") instanceof String declaredType) {
                    type = declaredType;
                }
                if (property.get("description") instanceof String declaredDescription) {
                    description = declaredDescription;
                }
            }
            parameters.add(new ToolParameter(entry.getKey(), type, description,
                    required.contains(entry.getKey())));
        }
        return parameters;
    }

    @Override
    public String name() {
        return "mcp:" + spec.name();
    }

    @Override
    public List<ToolDefinition> discover() {
        return tools;
    }

    @Override
    public String execute(String toolName, String argumentsJson, ToolExecutionContext context) {
        if (!spec.permits(toolName)) {
            return errorJson("Tool '" + toolName + "' is not permitted by server '" + spec.name() + "'");
        }
        Map<String, Object> arguments;
        try {
            arguments = parseArguments(argumentsJson);
        } catch (Exception e) {
            return errorJson("Invalid tool arguments: " + describe(e));
        }
        try {
            McpSchema.CallToolResult result =
                    client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            if (result == null) {
                return errorJson("MCP server '" + spec.name() + "' returned no result for '" + toolName + "'");
            }
            String rendered = renderContent(result.content());
            if (Boolean.TRUE.equals(result.isError())) {
                return errorJson(rendered.isBlank() ? "tool reported an error" : rendered);
            }
            return rendered;
        } catch (Exception e) {
            log.warn("MCP server '{}': tool '{}' failed: {}", spec.name(), toolName, describe(e));
            return errorJson("Tool execution failed: " + describe(e));
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank() || "{}".equals(argumentsJson.trim())) {
            return Map.of();
        }
        return objectMapper.readValue(argumentsJson, new TypeReference<>() {});
    }

    /** Flattens MCP content blocks into a single string; text blocks are joined by newlines. */
    private static String renderContent(List<McpSchema.Content> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (McpSchema.Content block : content) {
            if (block == null) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(block instanceof McpSchema.TextContent text ? text.text() : block.toString());
        }
        return out.toString();
    }

    @Override
    public void close() {
        closeQuietly(client, spec.name());
    }

    private static void closeQuietly(McpSyncClient client, String serverName) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("MCP server '{}': error during shutdown: {}", serverName, describe(e));
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            return "{\"error\":\"tool invocation failed\"}";
        }
    }

    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
