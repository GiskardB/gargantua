package ai.gargantua.core.workload;

import java.util.Map;

/**
 * Identity of a workload, independent of what the workload actually does.
 *
 * <p>Mirrors the {@code metadata} block of a Kubernetes object so that a future
 * Custom Resource Definition can reuse this shape verbatim.</p>
 *
 * @param name        unique workload name; lowercase, hyphen-separated by convention,
 *                    and stable across versions
 * @param version     semver of this workload revision
 * @param description human-readable summary shown in the Studio and the Catalog
 * @param owner       owning team or individual, used for Catalog ownership and alerting
 * @param labels      free-form key/value metadata for selection and filtering
 *                    (e.g. {@code env=prod}, {@code tier=critical})
 */
public record WorkloadMetadata(
        String name,
        String version,
        String description,
        String owner,
        Map<String, String> labels
) {

    public WorkloadMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workload metadata: name is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Workload '" + name + "': version is required");
        }
        description = description == null ? "" : description;
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    /** Minimal metadata for local development. */
    public WorkloadMetadata(String name, String version) {
        this(name, version, "", null, Map.of());
    }
}
