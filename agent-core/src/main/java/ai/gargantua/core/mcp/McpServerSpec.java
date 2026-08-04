package ai.gargantua.core.mcp;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative definition of an MCP server that a workload consumes for its tools.
 *
 * <p>This is the mechanism by which a code-free bundle acquires tools: instead of
 * shipping Java classes, the manifest names the MCP servers to connect to, and the
 * runtime discovers their tools at startup and merges them into the tool registry
 * alongside any compiled {@link ai.gargantua.core.tool.AgentTool} methods.</p>
 *
 * <p>Validation is enforced in the compact constructor: {@link McpTransport#STDIO}
 * requires a {@code command}, while {@link McpTransport#HTTP} and
 * {@link McpTransport#SSE} require a {@code url}. Failing fast here means an invalid
 * manifest is rejected at load time rather than on first tool call.</p>
 *
 * @param name         unique identifier within the workload; also used to namespace
 *                     discovered tools and to tag telemetry
 * @param transport    how to reach the server
 * @param command      executable to launch for {@code STDIO} (e.g. {@code npx}); ignored otherwise
 * @param args         arguments passed to {@code command}; ignored for remote transports
 * @param env          environment variables for the child process; values may contain
 *                     {@code ${secrets.*}} or {@code ${env.*}} placeholders
 * @param url          endpoint for {@code HTTP} / {@code SSE}; ignored for {@code STDIO}
 * @param auth         authentication for remote transports; {@code null} means none
 * @param allowedTools when non-empty, only these tool names are exposed to the LLM —
 *                     an allow-list applied on top of whatever the server advertises
 * @param enabled      whether to connect at startup; lets an operator disable a server
 *                     without editing the rest of the manifest
 *
 * @see ai.gargantua.core.workload.AgentSpec
 */
public record McpServerSpec(
        String name,
        McpTransport transport,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        McpAuth auth,
        Set<String> allowedTools,
        boolean enabled
) {

    public McpServerSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MCP server name is required");
        }
        if (transport == null) {
            throw new IllegalArgumentException("MCP server '" + name + "': transport is required");
        }
        if (transport == McpTransport.STDIO && (command == null || command.isBlank())) {
            throw new IllegalArgumentException(
                    "MCP server '" + name + "': stdio transport requires a command");
        }
        if (transport != McpTransport.STDIO && (url == null || url.isBlank())) {
            throw new IllegalArgumentException(
                    "MCP server '" + name + "': " + transport + " transport requires a url");
        }
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
    }

    /** Local stdio server launched as a child process — the most common form. */
    public static McpServerSpec stdio(String name, String command, List<String> args) {
        return new McpServerSpec(name, McpTransport.STDIO, command, args,
                Map.of(), null, null, Set.of(), true);
    }

    /** Remote streamable-HTTP server. */
    public static McpServerSpec http(String name, String url, McpAuth auth) {
        return new McpServerSpec(name, McpTransport.HTTP, null, List.of(),
                Map.of(), url, auth, Set.of(), true);
    }

    /** Whether {@code toolName} passes this server's allow-list. */
    public boolean permits(String toolName) {
        return allowedTools.isEmpty() || allowedTools.contains(toolName);
    }
}
