package ai.gargantua.core.tool;

import java.util.List;

/**
 * Source of executable tools for a workload.
 *
 * <p>This is the seam that lets the same execution engine serve both delivery modes.
 * In library mode tools come from compiled {@link AgentTool} methods discovered in the
 * Spring context; in runtime mode they come from MCP servers declared in the bundle
 * manifest. Both appear to the orchestrator as plain {@link ToolDefinition}s, so
 * neither the prompt builder nor the tool-calling loop needs to know the difference.</p>
 *
 * <p>The tool registry owns name-to-provider routing: it calls {@link #discover()}
 * once at startup and builds the index. Implementations therefore do not need to
 * answer membership questions themselves.</p>
 *
 * <p>Implementations must be thread-safe — {@link #execute} is called concurrently
 * from virtual threads serving different requests.</p>
 *
 * @see ToolDefinition
 * @see ToolExecutionContext
 */
public interface ToolProvider extends AutoCloseable {

    /**
     * Stable identifier for this provider, used in logs, metrics and to disambiguate
     * tools with colliding names. Examples: {@code annotation}, {@code mcp:github}.
     */
    String name();

    /**
     * Returns the tools this provider offers. Called once during startup; the result
     * is expected to be stable for the provider's lifetime.
     *
     * <p>Implementations should return an empty list rather than throwing when the
     * underlying source is unreachable, so that one broken MCP server does not
     * prevent the workload from starting.</p>
     */
    List<ToolDefinition> discover();

    /**
     * Invokes {@code toolName} with JSON-encoded arguments and returns a JSON-encoded
     * result. Errors are reported as a JSON object with an {@code error} field rather
     * than by throwing, so that the model can see and react to the failure.
     *
     * @param toolName      name as advertised by {@link #discover()}
     * @param argumentsJson arguments produced by the model
     * @param context       caller identity and session, for RBAC and cache scoping
     */
    String execute(String toolName, String argumentsJson, ToolExecutionContext context);

    /**
     * Releases resources held by this provider — child processes for stdio MCP servers,
     * HTTP connections for remote ones. Overridden to drop the checked exception, since
     * shutdown failures are logged rather than propagated.
     */
    @Override
    default void close() {
        // no-op by default; stateless providers have nothing to release
    }
}
