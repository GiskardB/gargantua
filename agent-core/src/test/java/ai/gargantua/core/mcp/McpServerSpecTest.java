package ai.gargantua.core.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpServerSpec")
class McpServerSpecTest {

    @Test
    @DisplayName("stdio factory builds an enabled stdio server")
    void stdioFactory() {
        McpServerSpec spec = McpServerSpec.stdio("github", "npx", List.of("-y", "@mcp/github"));

        assertEquals("github", spec.name());
        assertEquals(McpTransport.STDIO, spec.transport());
        assertEquals("npx", spec.command());
        assertEquals(List.of("-y", "@mcp/github"), spec.args());
        assertTrue(spec.enabled());
    }

    @Test
    @DisplayName("http factory builds an enabled remote server")
    void httpFactory() {
        McpServerSpec spec = McpServerSpec.http("api", "https://mcp.internal", McpAuth.bearer("${secrets.tok}"));

        assertEquals(McpTransport.HTTP, spec.transport());
        assertEquals("https://mcp.internal", spec.url());
        assertEquals("bearer", spec.auth().type());
    }

    @Test
    @DisplayName("stdio transport requires a command")
    void stdioRequiresCommand() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new McpServerSpec("x", McpTransport.STDIO, null, null, null, null, null, null, true));
        assertTrue(ex.getMessage().contains("stdio transport requires a command"));
    }

    @Test
    @DisplayName("http transport requires a url")
    void httpRequiresUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new McpServerSpec("x", McpTransport.HTTP, null, null, null, null, null, null, true));
        assertTrue(ex.getMessage().contains("requires a url"));
    }

    @Test
    @DisplayName("sse transport requires a url")
    void sseRequiresUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                new McpServerSpec("x", McpTransport.SSE, null, null, null, null, null, null, true));
    }

    @Test
    @DisplayName("blank name is rejected")
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new McpServerSpec("  ", McpTransport.STDIO, "cmd", null, null, null, null, null, true));
    }

    @Test
    @DisplayName("null transport is rejected")
    void nullTransportRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new McpServerSpec("x", null, "cmd", null, null, null, null, null, true));
    }

    @Test
    @DisplayName("null collections default to empty")
    void nullCollectionsDefaultToEmpty() {
        McpServerSpec spec = new McpServerSpec("x", McpTransport.STDIO, "cmd",
                null, null, null, null, null, true);

        assertTrue(spec.args().isEmpty());
        assertTrue(spec.env().isEmpty());
        assertTrue(spec.allowedTools().isEmpty());
    }

    @Test
    @DisplayName("collections are defensively copied")
    void collectionsAreDefensivelyCopied() {
        McpServerSpec spec = new McpServerSpec("x", McpTransport.STDIO, "cmd",
                List.of("a"), Map.of("K", "V"), null, null, Set.of("t"), true);

        assertThrows(UnsupportedOperationException.class, () -> spec.args().add("b"));
        assertThrows(UnsupportedOperationException.class, () -> spec.env().put("K2", "V2"));
        assertThrows(UnsupportedOperationException.class, () -> spec.allowedTools().add("t2"));
    }

    @Test
    @DisplayName("an empty allow-list permits every tool")
    void emptyAllowListPermitsAll() {
        McpServerSpec spec = McpServerSpec.stdio("x", "cmd", List.of());
        assertTrue(spec.permits("anything"));
    }

    @Test
    @DisplayName("a populated allow-list permits only listed tools")
    void allowListRestrictsTools() {
        McpServerSpec spec = new McpServerSpec("x", McpTransport.STDIO, "cmd",
                null, null, null, null, Set.of("search"), true);

        assertTrue(spec.permits("search"));
        assertFalse(spec.permits("delete"));
    }
}
