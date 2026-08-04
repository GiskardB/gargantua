package ai.gargantua.mcp.client;

import java.io.Serial;

/**
 * Raised when an MCP server cannot be reached or fails protocol initialisation.
 *
 * <p>Thrown only from {@link McpToolProvider#connect}, never from tool discovery or
 * execution. That split is deliberate: connection failure is a startup concern the
 * caller must decide about — skip the server and continue, or refuse to start — whereas
 * a failure during execution is reported to the model as an error result so it can
 * react.</p>
 */
public class McpConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serverName;

    public McpConnectionException(String serverName, String message, Throwable cause) {
        super("MCP server '" + serverName + "': " + message, cause);
        this.serverName = serverName;
    }

    /** Name of the MCP server that could not be connected, as declared in the manifest. */
    public String getServerName() {
        return serverName;
    }
}
