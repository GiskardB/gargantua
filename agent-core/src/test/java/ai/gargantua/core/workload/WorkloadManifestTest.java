package ai.gargantua.core.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkloadManifest")
class WorkloadManifestTest {

    private static final WorkloadMetadata META = new WorkloadMetadata("customer-agent", "1.2.0");

    @Test
    @DisplayName("agent factory builds a manifest at the current api version")
    void agentFactory() {
        WorkloadManifest manifest = WorkloadManifest.agent(META, AgentSpec.minimal());

        assertEquals(WorkloadManifest.CURRENT_API_VERSION, manifest.apiVersion());
        assertEquals(WorkloadKind.AGENT, manifest.kind());
        assertEquals("customer-agent", manifest.metadata().name());
    }

    @Test
    @DisplayName("null apiVersion defaults to the current version")
    void nullApiVersionDefaults() {
        WorkloadManifest manifest =
                new WorkloadManifest(null, WorkloadKind.AGENT, META, AgentSpec.minimal());

        assertEquals(WorkloadManifest.CURRENT_API_VERSION, manifest.apiVersion());
    }

    @Test
    @DisplayName("an unsupported apiVersion is rejected")
    void unsupportedApiVersionRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new WorkloadManifest("gargantua.ai/v2", WorkloadKind.AGENT, META, AgentSpec.minimal()));
        assertTrue(ex.getMessage().contains("Unsupported manifest apiVersion"));
    }

    @Test
    @DisplayName("null kind is inferred from the spec")
    void nullKindInferredFromSpec() {
        WorkloadManifest manifest = new WorkloadManifest(null, null, META, AgentSpec.minimal());
        assertEquals(WorkloadKind.AGENT, manifest.kind());
    }

    @Test
    @DisplayName("a kind that disagrees with the spec is rejected")
    void kindSpecMismatchRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new WorkloadManifest(null, WorkloadKind.WORKFLOW, META, AgentSpec.minimal()));
        assertTrue(ex.getMessage().contains("but spec describes a"));
    }

    @Test
    @DisplayName("missing metadata is rejected")
    void missingMetadataRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkloadManifest(null, WorkloadKind.AGENT, null, AgentSpec.minimal()));
    }

    @Test
    @DisplayName("missing spec is rejected")
    void missingSpecRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkloadManifest(null, WorkloadKind.AGENT, META, null));
    }

    @Test
    @DisplayName("agentSpec narrows the spec for an agent manifest")
    void agentSpecNarrows() {
        AgentSpec spec = AgentSpec.minimal();
        WorkloadManifest manifest = WorkloadManifest.agent(META, spec);

        assertSame(spec, manifest.agentSpec());
    }

    @Test
    @DisplayName("coordinates combine name and version")
    void coordinates() {
        assertEquals("customer-agent:1.2.0",
                WorkloadManifest.agent(META, AgentSpec.minimal()).coordinates());
    }

    @Test
    @DisplayName("metadata requires a name")
    void metadataRequiresName() {
        assertThrows(IllegalArgumentException.class, () -> new WorkloadMetadata("", "1.0.0"));
    }

    @Test
    @DisplayName("metadata requires a version")
    void metadataRequiresVersion() {
        assertThrows(IllegalArgumentException.class, () -> new WorkloadMetadata("agent", ""));
    }

    @Test
    @DisplayName("metadata description defaults to empty and labels are copied")
    void metadataDefaults() {
        WorkloadMetadata metadata = new WorkloadMetadata("agent", "1.0.0", null, null, null);

        assertEquals("", metadata.description());
        assertTrue(metadata.labels().isEmpty());
    }

    @Test
    @DisplayName("metadata labels are immutable")
    void metadataLabelsImmutable() {
        WorkloadMetadata metadata =
                new WorkloadMetadata("agent", "1.0.0", "d", "team", Map.of("env", "prod"));

        assertThrows(UnsupportedOperationException.class, () -> metadata.labels().put("k", "v"));
    }
}
