package ai.gargantua.runtime;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.bundle.BundleLoader;
import ai.gargantua.bundle.LoadedBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection from manifest to {@code agent.*} settings is only useful if the keys
 * actually bind, so these tests bind the generated map onto the real
 * {@link AgentProperties} rather than asserting on key names.
 */
@DisplayName("ManifestProperties")
class ManifestPropertiesTest {

    private static final String FULL_MANIFEST = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: customer-agent
              version: 1.2.0
              description: Handles refunds
            spec:
              capabilities:
                - name: refund-payment
                  version: 1.0.0
              model:
                primary: gpt-4o-mini
                fallback: claude-sonnet-4-20250514
                routing: phi4-mini
                temperature: 0.3
                maxTokens: 1500
              defaultSkill: default-skill
              mcp:
                servers:
                  - name: github
                    transport: stdio
                    command: npx
                    args: ["-y", "@modelcontextprotocol/server-github"]
                    env:
                      GITHUB_TOKEN: ${secrets.github-token}
                  - name: payments
                    transport: http
                    url: https://mcp.internal/payments
                    auth:
                      type: bearer
                      value: ${secrets.payments-token}
                    allowedTools: [getPayment]
                    enabled: false
            """;

    private static Path bundleWith(Path root, String manifest, boolean withSkills) throws IOException {
        Path bundle = root.resolve("customer-agent.gbundle");
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve(BundleLoader.MANIFEST_FILE), manifest);
        if (withSkills) {
            Files.createDirectories(bundle.resolve("skills/default-skill"));
            Files.writeString(bundle.resolve("skills/default-skill/SKILL.md"), "---\nname: default-skill\n---\n");
        }
        return bundle;
    }

    private static AgentProperties bind(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("manifest", properties));
        return Binder.get(environment)
                .bind("agent", AgentProperties.class)
                .orElseGet(AgentProperties::new);
    }

    @Test
    @DisplayName("manifest identity binds onto the API properties")
    void identityBinds(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));

            assertThat(properties.getApi().getAgentId()).isEqualTo("customer-agent");
            assertThat(properties.getApi().getDisplayName()).isEqualTo("customer-agent");
            assertThat(properties.getApi().getVersion()).isEqualTo("1.2.0");
            assertThat(properties.getApi().getDescription()).isEqualTo("Handles refunds");
        }
    }

    @Test
    @DisplayName("model selection binds onto the LLM properties")
    void modelBinds(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));

            assertThat(properties.getLlm().getPrimary().getModel()).isEqualTo("gpt-4o-mini");
            assertThat(properties.getLlm().getPrimary().getTemperature()).isEqualTo(0.3);
            assertThat(properties.getLlm().getPrimary().getMaxTokens()).isEqualTo(1500);
            assertThat(properties.getLlm().getFallback().getModel()).isEqualTo("claude-sonnet-4-20250514");
            assertThat(properties.getLlm().getRoutingModel().getModel()).isEqualTo("phi4-mini");
        }
    }

    @Test
    @DisplayName("MCP servers bind onto the client configuration, index by index")
    void mcpServersBind(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));
            var servers = properties.getMcpClient().getServers();

            assertThat(properties.getMcpClient().isEnabled()).isTrue();
            assertThat(servers).hasSize(2);

            assertThat(servers.get(0).getName()).isEqualTo("github");
            assertThat(servers.get(0).getTransport()).isEqualTo("stdio");
            assertThat(servers.get(0).getCommand()).isEqualTo("npx");
            assertThat(servers.get(0).getArgs())
                    .containsExactly("-y", "@modelcontextprotocol/server-github");
            assertThat(servers.get(0).getEnv())
                    .containsEntry("GITHUB_TOKEN", "${secrets.github-token}");
            assertThat(servers.get(0).isEnabled()).isTrue();

            assertThat(servers.get(1).getName()).isEqualTo("payments");
            assertThat(servers.get(1).getTransport()).isEqualTo("http");
            assertThat(servers.get(1).getUrl()).isEqualTo("https://mcp.internal/payments");
            assertThat(servers.get(1).getAuth().getType()).isEqualTo("bearer");
            assertThat(servers.get(1).getAuth().getValue()).isEqualTo("${secrets.payments-token}");
            assertThat(servers.get(1).getAllowedTools()).containsExactly("getPayment");
            assertThat(servers.get(1).isEnabled()).isFalse();
        }
    }

    @Test
    @DisplayName("the round trip from manifest to spec survives the property projection")
    void mcpRoundTripsBackToSpec(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));

            var original = bundle.manifest().agentSpec().mcpServers().get(0);
            var projected = ai.gargantua.autoconfigure.McpToolProviderFactory
                    .toSpec(properties.getMcpClient().getServers().get(0));

            assertThat(projected.name()).isEqualTo(original.name());
            assertThat(projected.transport()).isEqualTo(original.transport());
            assertThat(projected.command()).isEqualTo(original.command());
            assertThat(projected.args()).isEqualTo(original.args());
            assertThat(projected.env()).isEqualTo(original.env());
        }
    }

    @Test
    @DisplayName("the default skill binds onto routing")
    void defaultSkillBinds(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));

            assertThat(properties.getRouting().getFallbackSkill()).isEqualTo("default-skill");
        }
    }

    @Test
    @DisplayName("bundled skills point the registry at the bundle directory")
    void skillPathPointsIntoBundle(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, true))) {
            AgentProperties properties = bind(ManifestProperties.from(bundle));

            assertThat(properties.getSkill().getPath())
                    .startsWith("file:")
                    .endsWith("skills");
        }
    }

    @Test
    @DisplayName("a bundle without skills leaves the skill path at its default")
    void noSkillsLeavesPathUntouched(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            assertThat(ManifestProperties.from(bundle)).doesNotContainKey("agent.skill.path");
        }
    }

    @Test
    @DisplayName("a minimal manifest produces no MCP configuration")
    void minimalManifestHasNoMcp(@TempDir Path root) throws IOException {
        String minimal = """
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """;
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, minimal, false))) {
            assertThat(ManifestProperties.from(bundle))
                    .doesNotContainKey("agent.mcp-client.enabled");
        }
    }

    @Test
    @DisplayName("guardrail overrides are flattened and kebab-cased")
    void guardrailOverridesFlattened(@TempDir Path root) throws IOException {
        String manifest = """
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  guardrails:
                    input:
                      maxLengthChars: 8000
                      piiMasking: true
                """;
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, manifest, false))) {
            assertThat(ManifestProperties.from(bundle))
                    .containsEntry("agent.guardrail.input.max-length-chars", 8000)
                    .containsEntry("agent.guardrail.input.pii-masking", true);
        }
    }

    @Test
    @DisplayName("declared but unimplemented fields are reported rather than dropped")
    void unappliedFieldsAreReported(@TempDir Path root) throws IOException {
        String manifest = """
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  memoryLayers: [working]
                  allowedRoles: [support]
                  runtime:
                    minVersion: "9.9"
                """;
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, manifest, false))) {
            assertThat(ManifestProperties.unappliedFields(bundle))
                    .hasSize(3)
                    .anySatisfy(w -> assertThat(w).contains("memoryLayers"))
                    .anySatisfy(w -> assertThat(w).contains("allowedRoles"))
                    .anySatisfy(w -> assertThat(w).contains("minVersion"));
        }
    }

    @Test
    @DisplayName("a fully applied manifest reports nothing")
    void fullyAppliedManifestReportsNothing(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, FULL_MANIFEST, false))) {
            assertThat(ManifestProperties.unappliedFields(bundle)).isEmpty();
        }
    }
}
