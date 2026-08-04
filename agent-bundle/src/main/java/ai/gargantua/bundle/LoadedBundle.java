package ai.gargantua.bundle;

import ai.gargantua.core.bundle.BundleDescriptor;
import ai.gargantua.core.workload.WorkloadManifest;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * An opened bundle: its provenance, its parsed manifest, and the directory its files
 * live in.
 *
 * <p>{@link #root()} is a real directory whether the bundle was delivered as one or as an
 * archive — the loader extracts archives to a temporary location. Closing the bundle
 * removes that temporary copy, so a runtime holds its bundle open for its whole life and
 * closes it on shutdown.</p>
 *
 * @param descriptor    identity and provenance, from {@code metadata.json} or synthesised
 *                      for a development bundle
 * @param manifest      the parsed {@code manifest.yaml}
 * @param root          directory containing the bundle files
 * @param temporaryRoot directory to delete on close, or {@code null} when the bundle was
 *                      already a directory and nothing was copied
 */
public record LoadedBundle(
        BundleDescriptor descriptor,
        WorkloadManifest manifest,
        Path root,
        Path temporaryRoot
) implements Closeable {

    /** Directory holding SKILL.md folders, whether or not it exists. */
    public Path skillsPath() {
        return root.resolve(BundleLoader.SKILLS_DIR);
    }

    /** Whether this bundle ships skills. */
    public boolean hasSkills() {
        return Files.isDirectory(skillsPath());
    }

    /**
     * Resolves a bundle-relative path, refusing anything that escapes the bundle root.
     * Manifests reference files such as {@code schemas/refund-input.json}, and those
     * references must not be able to reach outside the artifact.
     *
     * @return the resolved path, or empty when it does not exist or escapes the root
     */
    public Optional<Path> resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            return Optional.empty();
        }
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root.normalize())) {
            throw new BundleException("Path '" + relative + "' escapes the bundle root");
        }
        return Files.exists(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    @Override
    public void close() throws IOException {
        if (temporaryRoot == null || !Files.exists(temporaryRoot)) {
            return;
        }
        try (var paths = Files.walk(temporaryRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: a leftover temp directory is not worth failing shutdown.
                }
            });
        }
    }
}
