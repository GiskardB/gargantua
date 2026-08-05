package ai.gargantua.core.mcp;

/**
 * Authentication applied when connecting to a remote MCP server.
 *
 * <p>{@code value} is never a literal credential in a distributed bundle — it carries
 * a placeholder such as {@code ${secrets.github-token}} that the runtime resolves at
 * startup. Bundles are immutable and signed, so they must stay free of secrets.</p>
 *
 * @param type       {@code none}, {@code bearer}, {@code basic}, or {@code header}
 * @param value      credential or placeholder reference; ignored when type is {@code none}
 * @param headerName header to place the credential in when type is {@code header}
 *
 * @see ai.gargantua.core.secret.SecretPlaceholders
 */
public record McpAuth(String type, String value, String headerName) {

    private static final String NONE_TYPE = "none";

    public McpAuth {
        type = (type == null || type.isBlank()) ? NONE_TYPE : type.toLowerCase(java.util.Locale.ROOT);
    }

    /** Auth-less access, the default for local stdio servers. */
    public static McpAuth none() {
        return new McpAuth(NONE_TYPE, null, null);
    }

    /** Bearer-token auth; {@code value} is typically a {@code ${secrets.*}} placeholder. */
    public static McpAuth bearer(String value) {
        return new McpAuth("bearer", value, null);
    }

    public boolean isNone() {
        return NONE_TYPE.equals(type);
    }
}
