package ai.gargantua.core.mcp;

/**
 * Transport used to reach an MCP server.
 *
 * @see McpServerSpec
 */
public enum McpTransport {

    /** Local child process communicating over stdin/stdout. Requires a command. */
    STDIO,

    /** Remote server over streamable HTTP. Requires a URL. */
    HTTP,

    /** Remote server over HTTP Server-Sent Events. Requires a URL. */
    SSE
}
