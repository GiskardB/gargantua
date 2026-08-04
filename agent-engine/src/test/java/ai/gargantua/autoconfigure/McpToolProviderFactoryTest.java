package ai.gargantua.autoconfigure;

import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.mcp.McpTransport;
import ai.gargantua.core.secret.SecretResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpToolProviderFactory")
class McpToolProviderFactoryTest {

    private static AgentProperties.McpClient.Server stdioServer() {
        var server = new AgentProperties.McpClient.Server();
        server.setName("github");
        server.setTransport("stdio");
        server.setCommand("npx");
        server.setArgs(List.of("-y", "@modelcontextprotocol/server-github"));
        server.setEnv(Map.of("GITHUB_TOKEN", "${secrets.github-token}"));
        return server;
    }

    @Test
    @DisplayName("maps a stdio declaration onto a spec")
    void mapsStdioDeclaration() {
        McpServerSpec spec = McpToolProviderFactory.toSpec(stdioServer());

        assertThat(spec.name()).isEqualTo("github");
        assertThat(spec.transport()).isEqualTo(McpTransport.STDIO);
        assertThat(spec.command()).isEqualTo("npx");
        assertThat(spec.args()).containsExactly("-y", "@modelcontextprotocol/server-github");
        assertThat(spec.env()).containsEntry("GITHUB_TOKEN", "${secrets.github-token}");
        assertThat(spec.enabled()).isTrue();
    }

    @Test
    @DisplayName("transport name is case-insensitive")
    void transportIsCaseInsensitive() {
        var server = stdioServer();
        server.setTransport("StDiO");

        assertThat(McpToolProviderFactory.toSpec(server).transport()).isEqualTo(McpTransport.STDIO);
    }

    @Test
    @DisplayName("maps an http declaration with bearer auth")
    void mapsHttpDeclarationWithAuth() {
        var server = new AgentProperties.McpClient.Server();
        server.setName("payments");
        server.setTransport("http");
        server.setUrl("https://mcp.internal/payments");
        server.getAuth().setType("bearer");
        server.getAuth().setValue("${secrets.payments-token}");
        server.setAllowedTools(List.of("getPayment", "refundPayment"));

        McpServerSpec spec = McpToolProviderFactory.toSpec(server);

        assertThat(spec.transport()).isEqualTo(McpTransport.HTTP);
        assertThat(spec.url()).isEqualTo("https://mcp.internal/payments");
        assertThat(spec.auth().type()).isEqualTo("bearer");
        assertThat(spec.auth().value()).isEqualTo("${secrets.payments-token}");
        assertThat(spec.allowedTools()).containsExactlyInAnyOrder("getPayment", "refundPayment");
    }

    @Test
    @DisplayName("absent auth becomes none")
    void absentAuthBecomesNone() {
        assertThat(McpToolProviderFactory.toSpec(stdioServer()).auth().isNone()).isTrue();
    }

    @Test
    @DisplayName("an unknown transport is rejected with a readable message")
    void unknownTransportRejected() {
        var server = stdioServer();
        server.setTransport("carrier-pigeon");

        assertThatThrownBy(() -> McpToolProviderFactory.toSpec(server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown transport");
    }

    @Test
    @DisplayName("a stdio declaration without a command is rejected")
    void stdioWithoutCommandRejected() {
        var server = stdioServer();
        server.setCommand("");

        assertThatThrownBy(() -> McpToolProviderFactory.toSpec(server))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a command");
    }

    @Test
    @DisplayName("no providers are built when the client is disabled")
    void disabledClientBuildsNothing() {
        var properties = new AgentProperties();
        properties.getMcpClient().setEnabled(false);
        properties.getMcpClient().setServers(List.of(stdioServer()));

        assertThat(McpToolProviderFactory.fromProperties(properties, SecretResolver.empty())).isEmpty();
    }

    @Test
    @DisplayName("no providers are built when no servers are declared")
    void noServersBuildsNothing() {
        assertThat(McpToolProviderFactory.fromProperties(new AgentProperties(), SecretResolver.empty()))
                .isEmpty();
    }

    @Test
    @DisplayName("an invalid declaration is skipped by default")
    void invalidDeclarationSkippedByDefault() {
        var invalid = stdioServer();
        invalid.setTransport("carrier-pigeon");
        var properties = new AgentProperties();
        properties.getMcpClient().setServers(List.of(invalid));

        assertThat(McpToolProviderFactory.fromProperties(properties, SecretResolver.empty())).isEmpty();
    }

    @Test
    @DisplayName("an invalid declaration aborts startup when fail-fast is set")
    void invalidDeclarationAbortsWhenFailFast() {
        var invalid = stdioServer();
        invalid.setTransport("carrier-pigeon");
        var properties = new AgentProperties();
        properties.getMcpClient().setFailFast(true);
        properties.getMcpClient().setServers(List.of(invalid));

        assertThatThrownBy(() -> McpToolProviderFactory.fromProperties(properties, SecretResolver.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a disabled server is not connected")
    void disabledServerNotConnected() {
        var disabled = stdioServer();
        disabled.setEnabled(false);
        var properties = new AgentProperties();
        properties.getMcpClient().setServers(List.of(disabled));

        assertThat(McpToolProviderFactory.fromProperties(properties, SecretResolver.empty())).isEmpty();
    }

    @Test
    @DisplayName("client defaults are sensible")
    void clientDefaults() {
        var config = new AgentProperties().getMcpClient();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isFailFast()).isFalse();
        assertThat(config.getRequestTimeoutSeconds()).isEqualTo(30);
        assertThat(config.getServers()).isEmpty();
    }
}
