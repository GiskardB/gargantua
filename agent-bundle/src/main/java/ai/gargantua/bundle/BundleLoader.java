package ai.gargantua.bundle;

import ai.gargantua.core.bundle.BundleDescriptor;
import ai.gargantua.core.workload.WorkloadManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Opens an agent bundle from a directory or an archive.
 *
 * <p>A bundle is the immutable artifact a runtime executes. It carries a manifest,
 * prompts, skills, schemas and policies — and, by {@code ADR-003}, never executable code.
 * Loading therefore reduces to reading declarative files and verifying that they are the
 * ones that were published.</p>
 *
 * <pre>
 * customer-agent.gbundle
 * ├── manifest.yaml     required
 * ├── metadata.json     optional; provenance, checksum, signature
 * ├── skills/
 * ├── prompts/
 * ├── schemas/
 * └── policies/
 * </pre>
 *
 * @see LoadedBundle
 */
public final class BundleLoader {

    private static final Logger log = LoggerFactory.getLogger(BundleLoader.class);

    public static final String MANIFEST_FILE = "manifest.yaml";
    public static final String METADATA_FILE = "metadata.json";
    public static final String SKILLS_DIR = "skills";

    /** Guards against archives that inflate to an unreasonable size. */
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;

    private BundleLoader() {}

    /**
     * Opens the bundle at {@code path}, which may be a directory or an archive.
     *
     * <p>When {@code metadata.json} declares a checksum it is verified against the actual
     * contents, so a bundle that was altered after publication fails to load rather than
     * running silently.</p>
     *
     * @throws BundleException when the bundle is missing, malformed, or fails verification
     */
    public static LoadedBundle load(Path path) {
        if (path == null) {
            throw new BundleException("Bundle path is required");
        }
        if (!Files.exists(path)) {
            throw new BundleException("Bundle not found: " + path);
        }

        Path temporaryRoot = null;
        Path root = path;
        if (!Files.isDirectory(path)) {
            temporaryRoot = extract(path);
            root = temporaryRoot;
        }

        Path manifestPath = root.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new BundleException("Bundle at " + path + " has no " + MANIFEST_FILE);
        }
        WorkloadManifest manifest = ManifestParser.parse(manifestPath);
        BundleDescriptor descriptor = readDescriptor(root, manifest);

        if (descriptor.checksum() != null && !descriptor.checksum().isBlank()) {
            String actual = checksum(root);
            if (!descriptor.checksum().equals(actual)) {
                throw new BundleException("Bundle '" + descriptor.coordinates()
                        + "' failed integrity check: declared " + descriptor.checksum()
                        + ", computed " + actual);
            }
            log.info("Bundle '{}' integrity verified", descriptor.coordinates());
        }
        if (!descriptor.isSigned()) {
            log.warn("Bundle '{}' is unsigned", descriptor.coordinates());
        }

        log.info("Loaded bundle '{}' ({} capabilities, {} MCP server(s))",
                descriptor.coordinates(),
                manifest.agentSpec().capabilities().size(),
                manifest.agentSpec().mcpServers().size());

        return new LoadedBundle(descriptor, manifest, root, temporaryRoot);
    }

    /**
     * Computes the content checksum of a bundle directory: SHA-256 over every file except
     * {@code metadata.json}, in sorted relative-path order, mixing in the path so that
     * moving a file changes the digest.
     *
     * <p>{@code metadata.json} is excluded because it is where the checksum is recorded.</p>
     */
    public static String checksum(Path root) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new BundleException("SHA-256 is unavailable in this JVM", e);
        }
        Path normalizedRoot = root.normalize();
        try (var paths = Files.walk(normalizedRoot)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(file -> !METADATA_FILE.equals(normalizedRoot.relativize(file).toString()))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String relative = normalizedRoot.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        } catch (IOException e) {
            throw new BundleException("Cannot read bundle contents: " + e.getMessage(), e);
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static BundleDescriptor readDescriptor(Path root, WorkloadManifest manifest) {
        Path metadata = root.resolve(METADATA_FILE);
        if (!Files.isRegularFile(metadata)) {
            // A development bundle straight from a working directory has no compiler output.
            return new BundleDescriptor(
                    manifest.metadata().name(),
                    manifest.metadata().version(),
                    null, null,
                    manifest.agentSpec().runtime().image(),
                    Instant.now(),
                    Map.of());
        }
        Map<String, Object> document;
        try {
            // JSON is valid YAML, so one parser covers both files.
            Object loaded = new Yaml().load(Files.readString(metadata, StandardCharsets.UTF_8));
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new BundleException(METADATA_FILE + " must contain an object");
            }
            document = new LinkedHashMap<>();
            map.forEach((key, value) -> document.put(String.valueOf(key), value));
        } catch (IOException e) {
            throw new BundleException("Cannot read " + METADATA_FILE + ": " + e.getMessage(), e);
        }

        try {
            return new BundleDescriptor(
                    text(document, "name", manifest.metadata().name()),
                    text(document, "version", manifest.metadata().version()),
                    text(document, "checksum", null),
                    text(document, "signature", null),
                    text(document, "runtimeImage", manifest.agentSpec().runtime().image()),
                    timestamp(document.get("createdAt")),
                    labels(document.get("labels")));
        } catch (IllegalArgumentException e) {
            throw new BundleException(METADATA_FILE + ": " + e.getMessage(), e);
        }
    }

    private static String text(Map<String, Object> document, String key, String fallback) {
        Object value = document.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static Instant timestamp(Object value) {
        if (value == null) {
            return Instant.now();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception e) {
            throw new BundleException(METADATA_FILE + ".createdAt: expected an ISO-8601 timestamp");
        }
    }

    private static Map<String, String> labels(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        map.forEach((key, entry) -> labels.put(String.valueOf(key), entry == null ? "" : entry.toString()));
        return labels;
    }

    /** Extracts an archive into a temporary directory, refusing entries that escape it. */
    private static Path extract(Path archive) {
        Path target;
        try {
            target = Files.createTempDirectory("gargantua-bundle-");
        } catch (IOException e) {
            throw new BundleException("Cannot create a temporary directory: " + e.getMessage(), e);
        }
        Path normalizedTarget = target.normalize();

        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    // Zip-slip: an entry naming ../ would otherwise write outside the sandbox.
                    throw new BundleException(
                            "Bundle archive contains an illegal path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                Files.createDirectories(resolved.getParent());
                long written = Files.copy(new BoundedStream(zip, MAX_ENTRY_BYTES), resolved);
                log.debug("Extracted {} ({} bytes)", entry.getName(), written);
            }
        } catch (IOException e) {
            throw new BundleException("Cannot read bundle archive " + archive + ": " + e.getMessage(), e);
        }
        return target;
    }

    /** Fails rather than filling the disk when an archive entry inflates beyond the limit. */
    private static final class BoundedStream extends InputStream {

        private final InputStream delegate;
        private final long limit;
        private long read;

        BoundedStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++read > limit) {
                throw new IOException("Bundle entry exceeds " + limit + " bytes");
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0 && (read += count) > limit) {
                throw new IOException("Bundle entry exceeds " + limit + " bytes");
            }
            return count;
        }
    }
}
