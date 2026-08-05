package ai.gargantua.bundle;

import ai.gargantua.core.mcp.McpTransport;
import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.workload.WorkloadKind;
import ai.gargantua.core.workload.WorkloadManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManifestParser")
class ManifestParserTest {

    private static final String MINIMAL = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: customer-agent
              version: 1.2.0
            spec: {}
            """;

    @Test
    @DisplayName("parses a minimal agent manifest")
    void parsesMinimalManifest() {
        WorkloadManifest manifest = ManifestParser.parse(MINIMAL);

        assertEquals(WorkloadManifest.CURRENT_API_VERSION, manifest.apiVersion());
        assertEquals(WorkloadKind.AGENT, manifest.kind());
        assertEquals("customer-agent", manifest.metadata().name());
        assertEquals("customer-agent:1.2.0", manifest.coordinates());
        assertTrue(manifest.agentSpec().capabilities().isEmpty());
    }

    @Test
    @DisplayName("parses metadata, capabilities, model and memory layers")
    void parsesFullSpec() {
        WorkloadManifest manifest = ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: customer-agent
                  version: 1.2.0
                  description: Handles refunds
                  owner: payments-team
                  labels:
                    env: prod
                spec:
                  runtime:
                    image: ghcr.io/giskardb/gargantua-runtime:1.0
                  capabilities:
                    - name: refund-payment
                      description: Handles a refund
                      version: 1.0.0
                      implementedBy: refund-skill
                      tags: [payments, gdpr]
                  model:
                    primary: gpt-4o
                    temperature: 0.5
                    maxTokens: 2000
                  memoryLayers: [working, EPISODIC]
                  defaultSkill: default-skill
                  allowedRoles: [support-agent]
                """);

        var spec = manifest.agentSpec();
        assertEquals("payments-team", manifest.metadata().owner());
        assertEquals("prod", manifest.metadata().labels().get("env"));
        assertEquals("ghcr.io/giskardb/gargantua-runtime:1.0", spec.runtime().image());
        assertEquals(1, spec.capabilities().size());
        assertEquals("refund-skill", spec.capabilities().get(0).implementedBy());
        assertTrue(spec.capabilities().get(0).tags().contains("gdpr"));
        assertEquals("gpt-4o", spec.model().primary());
        assertEquals(0.5, spec.model().temperature(), 0.0001);
        assertEquals(2000, spec.model().maxTokens().intValue());
        assertEquals(java.util.Set.of(MemoryLayer.WORKING, MemoryLayer.EPISODIC), spec.memoryLayers());
        assertEquals("default-skill", spec.defaultSkill());
        assertTrue(spec.allowedRoles().contains("support-agent"));
    }

    @Test
    @DisplayName("parses MCP servers of both transport kinds")
    void parsesMcpServers() {
        WorkloadManifest manifest = ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  mcp:
                    servers:
                      - name: github
                        transport: stdio
                        command: npx
                        args: ["-y", "@modelcontextprotocol/server-github"]
                        env:
                          GITHUB_TOKEN: ${secrets.github-token}
                      - name: payments
                        transport: HTTP
                        url: https://mcp.internal/payments
                        auth:
                          type: bearer
                          value: ${secrets.payments-token}
                        allowedTools: [getPayment]
                        enabled: false
                """);

        var servers = manifest.agentSpec().mcpServers();
        assertEquals(2, servers.size());

        assertEquals(McpTransport.STDIO, servers.get(0).transport());
        assertEquals("npx", servers.get(0).command());
        assertEquals("${secrets.github-token}", servers.get(0).env().get("GITHUB_TOKEN"));
        assertTrue(servers.get(0).enabled());

        assertEquals(McpTransport.HTTP, servers.get(1).transport());
        assertEquals("bearer", servers.get(1).auth().type());
        assertTrue(servers.get(1).permits("getPayment"));
        assertFalse(servers.get(1).permits("refund"));
        assertFalse(servers.get(1).enabled());
        assertEquals(1, manifest.agentSpec().enabledMcpServers().size());
    }

    @Test
    @DisplayName("rejects an empty manifest")
    void rejectsEmptyManifest() {
        assertThrows(BundleException.class, () -> ManifestParser.parse("   "));
    }

    @Test
    @DisplayName("rejects a manifest that is not a mapping")
    void rejectsNonMapping() {
        assertThrows(BundleException.class, () -> ManifestParser.parse("- just\n- a list\n"));
    }

    @Test
    @DisplayName("rejects malformed YAML")
    void rejectsMalformedYaml() {
        assertThrows(BundleException.class, () -> ManifestParser.parse("key: [unclosed\n"));
    }

    @Test
    @DisplayName("rejects a missing kind")
    void rejectsMissingKind() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """));
        assertTrue(ex.getMessage().contains("kind"));
    }

    @Test
    @DisplayName("rejects an unknown kind")
    void rejectsUnknownKind() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Sandwich
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """));
        assertTrue(ex.getMessage().contains("unknown workload kind"));
    }

    @Test
    @DisplayName("rejects a workload kind this runtime cannot execute")
    void rejectsNonExecutableKind() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Workflow
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """));
        assertTrue(ex.getMessage().contains("not executable"));
    }

    @Test
    @DisplayName("rejects an unsupported apiVersion")
    void rejectsUnsupportedApiVersion() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v2
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """));
        assertTrue(ex.getMessage().contains("Unsupported manifest apiVersion"));
    }

    @Test
    @DisplayName("rejects missing metadata")
    void rejectsMissingMetadata() {
        assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                spec: {}
                """));
    }

    @Test
    @DisplayName("rejects metadata without a name")
    void rejectsMetadataWithoutName() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  version: 1.0.0
                spec: {}
                """));
        assertTrue(ex.getMessage().contains("name is required"));
    }

    @Test
    @DisplayName("rejects an unknown MCP transport, naming the offending element")
    void rejectsUnknownTransport() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  mcp:
                    servers:
                      - name: x
                        transport: carrier-pigeon
                """));
        assertTrue(ex.getMessage().contains("spec.mcp.servers[0].transport"));
        assertTrue(ex.getMessage().contains("carrier-pigeon"));
    }

    @Test
    @DisplayName("propagates domain validation, such as stdio without a command")
    void propagatesDomainValidation() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  mcp:
                    servers:
                      - name: x
                        transport: stdio
                """));
        assertTrue(ex.getMessage().contains("requires a command"));
    }

    @Test
    @DisplayName("rejects an unknown memory layer")
    void rejectsUnknownMemoryLayer() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  memoryLayers: [telepathic]
                """));
        assertTrue(ex.getMessage().contains("unknown memory layer"));
    }

    @Test
    @DisplayName("rejects a non-numeric temperature, naming the field")
    void rejectsNonNumericTemperature() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  model:
                    temperature: warm
                """));
        assertTrue(ex.getMessage().contains("spec.model.temperature"));
    }

    @Test
    @DisplayName("rejects a temperature outside the valid range")
    void rejectsOutOfRangeTemperature() {
        assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  model:
                    temperature: 5.0
                """));
    }

    @Test
    @DisplayName("rejects a capability without a name")
    void rejectsCapabilityWithoutName() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  capabilities:
                    - description: nameless
                """));
        assertTrue(ex.getMessage().contains("spec.capabilities[0].name"));
    }

    @Test
    @DisplayName("rejects duplicate capability names")
    void rejectsDuplicateCapabilities() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  capabilities:
                    - name: refund
                      version: 1.0.0
                    - name: refund
                      version: 2.0.0
                """));
        assertTrue(ex.getMessage().contains("Duplicate capability names"));
    }

    @Test
    @DisplayName("rejects a scalar where a mapping is expected")
    void rejectsScalarWhereMappingExpected() {
        BundleException ex = assertThrows(BundleException.class, () -> ManifestParser.parse("""
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec:
                  model: gpt-4o
                """));
        assertTrue(ex.getMessage().contains("spec.model"));
        assertTrue(ex.getMessage().contains("expected a mapping"));
    }

    @Test
    @DisplayName("a missing apiVersion defaults to the current one")
    void missingApiVersionDefaults() {
        WorkloadManifest manifest = ManifestParser.parse("""
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """);
        assertEquals(WorkloadManifest.CURRENT_API_VERSION, manifest.apiVersion());
    }
}
