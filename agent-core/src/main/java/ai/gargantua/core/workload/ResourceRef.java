package ai.gargantua.core.workload;

/**
 * A reference, in an agent's {@link Loadout}, to a named resource the agent is equipped
 * with — a file, dataset, document set or external endpoint it should have available.
 *
 * <p>Like everything in a bundle this is declarative and credential-free: {@code uri}
 * locates the resource, but secrets are referenced by name elsewhere ({@code ${secrets.*}})
 * and never inlined here.</p>
 *
 * @param name logical name the agent uses to refer to the resource; required
 * @param type optional kind hint, e.g. {@code "file"}, {@code "dataset"}, {@code "http"},
 *             {@code "s3"}; {@code null} when unspecified
 * @param uri  optional location/identifier; {@code null} when the name alone is enough
 */
public record ResourceRef(
        String name,
        String type,
        String uri
) {

    public ResourceRef {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ResourceRef.name must not be blank");
        }
        name = name.trim();
    }

    /** A reference by name only. */
    public ResourceRef(String name) {
        this(name, null, null);
    }
}
