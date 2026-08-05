package ai.gargantua.core.workload;

/**
 * Top-level declarative descriptor of an AI workload — the contract between the Control
 * Plane, which publishes it, and the Runtime, which executes it.
 *
 * <p>The {@code apiVersion / kind / metadata / spec} shape is borrowed from Kubernetes
 * deliberately. It gives versioned schema evolution for free, it is what platform
 * engineers already expect from a manifest, and it means the eventual Custom Resource
 * Definition is close to a direct transcription rather than a second, divergent
 * model.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * apiVersion: gargantua.ai/v1
 * kind: Agent
 * metadata:
 *   name: customer-agent
 *   version: 1.2.0
 *   owner: payments-team
 * spec:
 *   capabilities:
 *     - name: refund-payment
 *       description: Handles a payment refund request
 *       version: 1.0.0
 *   mcp:
 *     servers:
 *       - name: payments-api
 *         transport: http
 *         url: https://mcp.internal/payments
 * }</pre>
 *
 * @param apiVersion schema version; only {@link #CURRENT_API_VERSION} is accepted today
 * @param kind       workload type, which must agree with the concrete {@code spec} type
 * @param metadata   workload identity
 * @param spec       workload definition
 *
 * @see AgentSpec
 * @see ai.gargantua.core.bundle.BundleDescriptor
 */
public record WorkloadManifest(
        String apiVersion,
        WorkloadKind kind,
        WorkloadMetadata metadata,
        WorkloadSpec spec
) {

    /** The only manifest schema version this runtime understands. */
    public static final String CURRENT_API_VERSION = "gargantua.ai/v1";

    public WorkloadManifest {
        apiVersion = (apiVersion == null || apiVersion.isBlank()) ? CURRENT_API_VERSION : apiVersion;
        if (!CURRENT_API_VERSION.equals(apiVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported manifest apiVersion '" + apiVersion
                            + "'; this runtime understands " + CURRENT_API_VERSION);
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Manifest metadata is required");
        }
        if (spec == null) {
            throw new IllegalArgumentException(
                    "Manifest '" + metadata.name() + "': spec is required");
        }
        kind = kind == null ? spec.kind() : kind;
        if (kind != spec.kind()) {
            throw new IllegalArgumentException(
                    "Manifest '" + metadata.name() + "': kind is " + kind
                            + " but spec describes a " + spec.kind());
        }
    }

    /** Builds an agent manifest at the current schema version. */
    public static WorkloadManifest agent(WorkloadMetadata metadata, AgentSpec spec) {
        return new WorkloadManifest(CURRENT_API_VERSION, WorkloadKind.AGENT, metadata, spec);
    }

    /**
     * Narrows {@link #spec()} to an {@link AgentSpec}.
     *
     * @throws IllegalStateException when this manifest does not describe an agent
     */
    public AgentSpec agentSpec() {
        if (spec instanceof AgentSpec agentSpec) {
            return agentSpec;
        }
        throw new IllegalStateException(
                "Manifest '" + metadata.name() + "' is a " + kind + ", not an AGENT");
    }

    /** {@code name:version}, matching the bundle coordinate format. */
    public String coordinates() {
        return metadata.name() + ":" + metadata.version();
    }
}
