package ai.gargantua.core.pact;

import ai.gargantua.core.workload.AgentSpec;
import ai.gargantua.core.workload.WorkloadManifest;
import ai.gargantua.core.workload.WorkloadMetadata;

import java.util.List;

/**
 * Projects a {@link WorkloadManifest} onto a PACT v1 Core document — all seven pillars of
 * PACT's conceptual model (metadata, identity, purpose, capabilities, cognition, contract,
 * interfaces; PACT §3).
 *
 * <p>Gargantua's own manifest ({@code gargantua.ai/v1}) is the "Agent Manifest" layer in
 * PACT's ecosystem diagram (authority, operational boundaries, governance — see PACT
 * §16-17); {@code spec.cognition}, {@code spec.contract} and {@code spec.interfaces} are
 * PACT Core fields carried directly on {@link AgentSpec}. {@link Identity} and
 * {@link Purpose} have no dedicated manifest field — they are derived from
 * {@code metadata.owner}/{@code metadata.description}, which already answer the same
 * questions (see the javadoc on each). This record is the read-only, vendor-neutral view
 * a Gargantua agent would publish as its standalone PACT card — no separate storage, no
 * sync step, just a projection of data that already lives on the manifest.</p>
 *
 * <p>{@code capabilities} is deliberately thinner here than
 * {@link ai.gargantua.core.capability.Capability}: PACT Core capabilities are
 * {@code id + description} only (PACT §10); {@code implementedBy}/schemas/tags are
 * Gargantua-specific routing detail that stays on the full manifest, not the portable
 * card.</p>
 */
public record PactManifest(
        String apiVersion,
        String kind,
        Metadata metadata,
        Identity identity,
        Purpose purpose,
        List<CapabilityRef> capabilities,
        Cognition cognition,
        Contract contract,
        List<InterfaceEndpoint> interfaces
) {

    /** The PACT schema version this projection targets. */
    public static final String CURRENT_API_VERSION = "pact/v1";

    public record Metadata(String name, String version, String description) {
    }

    /** PACT's minimal capability shape — {@code id + description}, not the full contract. */
    public record CapabilityRef(String id, String description) {
    }

    /** Projects an agent manifest onto its PACT Core view. */
    public static PactManifest from(WorkloadManifest manifest) {
        AgentSpec spec = manifest.agentSpec();
        WorkloadMetadata meta = manifest.metadata();
        List<CapabilityRef> capabilities = spec.capabilities().stream()
                .map(c -> new CapabilityRef(c.name(), c.description()))
                .toList();
        return new PactManifest(
                CURRENT_API_VERSION,
                "Agent",
                new Metadata(meta.name(), meta.version(), meta.description()),
                identity(meta),
                purpose(meta),
                capabilities,
                spec.cognition(),
                spec.contract(),
                spec.interfaces());
    }

    private static Identity identity(WorkloadMetadata meta) {
        return blank(meta.owner()) ? null : new Identity(meta.owner());
    }

    private static Purpose purpose(WorkloadMetadata meta) {
        return blank(meta.description()) ? null : new Purpose(meta.description());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
