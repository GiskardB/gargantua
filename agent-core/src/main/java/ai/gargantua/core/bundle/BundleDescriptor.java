package ai.gargantua.core.bundle;

import java.time.Instant;
import java.util.Map;

/**
 * Identity and provenance of an agent bundle — the immutable, versioned artifact that
 * the Control Plane publishes and a runtime executes.
 *
 * <p>The bundle is deliberately <strong>code-free</strong>: manifest, prompts, skills,
 * MCP server declarations and policies, but no compiled classes. That constraint is
 * what makes {@link #signature} meaningful — verifying a signature over declarative
 * data establishes a real trust boundary, whereas signing a bundle containing arbitrary
 * JARs would merely certify the origin of code that still runs with full privileges.</p>
 *
 * <p>{@link #runtimeImage} records which runtime the bundle expects. Image and bundle
 * version independently: a bundle can be rolled forward without rebuilding the image,
 * and a patched runtime image can be rolled out without republishing bundles. Teams
 * that need custom Java tooling build their own image in library mode and name it
 * here.</p>
 *
 * @param name         bundle name, matching the workload it carries
 * @param version      semver of this bundle revision
 * @param checksum     SHA-256 over the bundle contents, used for integrity checks on load
 * @param signature    detached signature over {@link #checksum}, or {@code null} for
 *                     unsigned local development bundles
 * @param runtimeImage container image required to execute this bundle
 *                     (e.g. {@code ghcr.io/giskardb/gargantua-runtime:1.0}), or
 *                     {@code null} to accept the platform default
 * @param createdAt    build timestamp recorded by the compiler
 * @param labels       free-form metadata propagated to the Catalog
 */
public record BundleDescriptor(
        String name,
        String version,
        String checksum,
        String signature,
        String runtimeImage,
        Instant createdAt,
        Map<String, String> labels
) {

    public BundleDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bundle name is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Bundle '" + name + "': version is required");
        }
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    /** Unsigned descriptor for local development, where no compiler has run. */
    public static BundleDescriptor development(String name, String version) {
        return new BundleDescriptor(name, version, null, null, null, Instant.now(), Map.of());
    }

    /** Whether this bundle carries a signature that a runtime can verify before loading. */
    public boolean isSigned() {
        return signature != null && !signature.isBlank();
    }

    /** {@code name:version}, the canonical way to refer to a bundle revision. */
    public String coordinates() {
        return name + ":" + version;
    }
}
