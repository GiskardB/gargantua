package ai.gargantua.core.pact;

import ai.gargantua.core.workload.AgentSpec;
import ai.gargantua.core.workload.WorkloadManifest;
import ai.gargantua.core.workload.WorkloadMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The PACT wire shape as a plain map tree — safe to hand to any JSON/YAML writer
     * as-is. Deliberately not left to a generic object mapper: {@link Autonomy} would
     * serialize as its enum name ({@code "RECOMMENDING"}) instead of the wire-format
     * integer PACT actually specifies ({@code {"level": 2}}), and
     * {@link CognitionRequirements} is flattened for Java ergonomics but PACT nests it
     * under {@code modalities.required}/{@code capabilities.required}/
     * {@code contextWindow.minimum}. A field a caller left undeclared is omitted
     * entirely, never emitted as {@code null}.
     */
    public Map<String, Object> toWireMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", apiVersion);
        root.put("kind", kind);

        Map<String, Object> metadataNode = new LinkedHashMap<>();
        metadataNode.put("name", metadata.name());
        metadataNode.put("version", metadata.version());
        putIfPresent(metadataNode, "description", metadata.description());
        root.put("metadata", metadataNode);

        if (identity != null) {
            root.put("identity", Map.of("provider", identity.provider()));
        }
        if (purpose != null) {
            root.put("purpose", Map.of("description", purpose.description()));
        }

        if (!capabilities.isEmpty()) {
            List<Object> caps = new ArrayList<>();
            for (CapabilityRef c : capabilities) {
                Map<String, Object> cn = new LinkedHashMap<>();
                cn.put("id", c.id());
                putIfPresent(cn, "description", c.description());
                caps.add(cn);
            }
            root.put("capabilities", caps);
        }

        Map<String, Object> cognitionNode = cognitionWireMap(cognition);
        if (!cognitionNode.isEmpty()) {
            root.put("cognition", cognitionNode);
        }

        Map<String, Object> contractNode = contractWireMap(contract);
        if (!contractNode.isEmpty()) {
            root.put("contract", contractNode);
        }

        if (!interfaces.isEmpty()) {
            List<Object> endpoints = new ArrayList<>();
            for (InterfaceEndpoint e : interfaces) {
                Map<String, Object> en = new LinkedHashMap<>();
                en.put("protocol", e.protocol());
                en.put("endpoint", e.endpoint());
                putIfPresent(en, "version", e.version());
                endpoints.add(en);
            }
            root.put("interfaces", endpoints);
        }

        return root;
    }

    private static Map<String, Object> cognitionWireMap(Cognition c) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (c == null || c.isEmpty()) {
            return node;
        }
        if (!c.modalities().isEmpty()) {
            node.put("modalities", new ArrayList<>(c.modalities()));
        }
        if (!c.capabilities().isEmpty()) {
            node.put("capabilities", new ArrayList<>(c.capabilities()));
        }
        if (c.models() != null) {
            Map<String, Object> modelsNode = new LinkedHashMap<>();
            putModelDescriptor(modelsNode, "primary", c.models().primary());
            putModelDescriptor(modelsNode, "fallback", c.models().fallback());
            if (!modelsNode.isEmpty()) {
                node.put("models", modelsNode);
            }
        }
        if (c.requirements() != null) {
            Map<String, Object> reqNode = new LinkedHashMap<>();
            if (!c.requirements().modalities().isEmpty()) {
                reqNode.put("modalities", Map.of("required", new ArrayList<>(c.requirements().modalities())));
            }
            if (!c.requirements().capabilities().isEmpty()) {
                reqNode.put("capabilities", Map.of("required", new ArrayList<>(c.requirements().capabilities())));
            }
            if (c.requirements().contextWindowMinimum() != null) {
                reqNode.put("contextWindow", Map.of("minimum", c.requirements().contextWindowMinimum()));
            }
            if (!reqNode.isEmpty()) {
                node.put("requirements", reqNode);
            }
        }
        return node;
    }

    private static void putModelDescriptor(Map<String, Object> parent, String key, ModelDescriptor d) {
        if (d == null) {
            return;
        }
        Map<String, Object> node = new LinkedHashMap<>();
        putIfPresent(node, "provider", d.provider());
        putIfPresent(node, "family", d.family());
        putIfPresent(node, "name", d.name());
        if (!node.isEmpty()) {
            parent.put(key, node);
        }
    }

    private static Map<String, Object> contractWireMap(Contract c) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (c == null || c.isEmpty()) {
            return node;
        }
        if (c.autonomy() != null) {
            node.put("autonomy", Map.of("level", c.autonomy().level()));
        }
        if (!c.permissions().isEmpty()) {
            node.put("permissions", new ArrayList<>(c.permissions()));
        }
        return node;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
