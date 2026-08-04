package ai.gargantua.core.workload;

import ai.gargantua.core.capability.Capability;

import java.util.List;

/**
 * The {@code spec} block of a {@link WorkloadManifest} — what the workload does, as
 * opposed to {@link WorkloadMetadata} which says what it is called.
 *
 * <p>Sealed because each {@link WorkloadKind} has a structurally different spec, and
 * the runtime must be able to exhaustively decide whether it can execute a given
 * manifest. Adding a workload type is therefore a deliberate, compiler-checked change
 * rather than an untyped map lookup.</p>
 *
 * @see AgentSpec
 */
public sealed interface WorkloadSpec permits AgentSpec {

    /** The kind this spec is valid for; used to validate manifest consistency. */
    WorkloadKind kind();

    /** Capabilities this workload advertises to the Catalog. Never {@code null}. */
    List<Capability> capabilities();
}
