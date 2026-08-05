package ai.gargantua.mcp.client;

import ai.gargantua.core.mcp.McpAuth;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.mcp.McpTransport;
import ai.gargantua.core.secret.SecretResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the paths that do not require a live MCP server: transport construction,
 * credential resolution and failure reporting. End-to-end behaviour against a real
 * server belongs in an integration test.
 */
@DisplayName("McpToolProvider")
class McpToolProviderTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(2);

    @Test
    @DisplayName("an unresolved secret placeholder in auth fails fast instead of sending it")
    void unresolvedSecretPlaceholderRejected() {
        McpServerSpec spec = McpServerSpec.http("api", "http://localhost:1",
                McpAuth.bearer("${secrets.missing}"));

        McpConnectionException ex = assertThrows(McpConnectionException.class,
                () -> McpToolProvider.connect(spec, SecretResolver.empty(), SHORT_TIMEOUT));

        assertEquals("api", ex.getServerName());
        assertTrue(ex.getMessage().contains("unresolved secret placeholder"));
    }

    @Test
    @DisplayName("header auth without a header name is rejected")
    void headerAuthWithoutHeaderNameRejected() {
        McpServerSpec spec = new McpServerSpec("api", McpTransport.HTTP, null, null, null,
                "http://localhost:1", new McpAuth("header", "value", null), null, true);

        McpConnectionException ex = assertThrows(McpConnectionException.class,
                () -> McpToolProvider.connect(spec, SecretResolver.empty(), SHORT_TIMEOUT));

        assertTrue(ex.getMessage().contains("requires headerName"));
    }

    @Test
    @DisplayName("a stdio server whose command does not exist reports a connection failure")
    void missingStdioCommandReportsConnectionFailure() {
        McpServerSpec spec = McpServerSpec.stdio(
                "ghost", "gargantua-nonexistent-mcp-binary", List.of());

        McpConnectionException ex = assertThrows(McpConnectionException.class,
                () -> McpToolProvider.connect(spec, SecretResolver.empty(), SHORT_TIMEOUT));

        assertEquals("ghost", ex.getServerName());
        assertTrue(ex.getMessage().startsWith("MCP server 'ghost'"));
    }

    @Test
    @DisplayName("secrets are resolved rather than passed through literally")
    void secretsAreResolvedBeforeUse() {
        // A resolvable placeholder must not trip the unresolved-placeholder guard; the
        // attempt then fails on connection instead, which is the expected next step.
        McpServerSpec spec = McpServerSpec.http("api", "http://localhost:1",
                McpAuth.bearer("${secrets.token}"));
        SecretResolver resolver = name -> "token".equals(name) ? Optional.of("resolved") : Optional.empty();

        McpConnectionException ex = assertThrows(McpConnectionException.class,
                () -> McpToolProvider.connect(spec, resolver, SHORT_TIMEOUT));

        assertFalse(ex.getMessage().contains("unresolved secret placeholder"));
    }

    @Test
    @DisplayName("connection exception carries the declared server name")
    void connectionExceptionCarriesServerName() {
        McpConnectionException ex = new McpConnectionException("payments", "boom", null);

        assertEquals("payments", ex.getServerName());
        assertEquals("MCP server 'payments': boom", ex.getMessage());
    }

    @Test
    @DisplayName("an allow-list on the spec is honoured independently of the server")
    void allowListIsEnforcedBySpec() {
        McpServerSpec spec = new McpServerSpec("api", McpTransport.STDIO, "cmd",
                List.of(), Map.of(), null, null, Set.of("search"), true);

        assertTrue(spec.permits("search"));
        assertFalse(spec.permits("delete"));
    }
}
