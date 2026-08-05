package ai.gargantua.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BundleLoader")
class BundleLoaderTest {

    private static final String MANIFEST = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: customer-agent
              version: 1.2.0
            spec:
              runtime:
                image: ghcr.io/giskardb/gargantua-runtime:1.0
              capabilities:
                - name: refund-payment
                  version: 1.0.0
            """;

    private static Path bundleDirectory(Path root) throws IOException {
        Path bundle = root.resolve("customer-agent.gbundle");
        Files.createDirectories(bundle.resolve("skills/default-skill"));
        Files.writeString(bundle.resolve(BundleLoader.MANIFEST_FILE), MANIFEST);
        Files.writeString(bundle.resolve("skills/default-skill/SKILL.md"), "---\nname: default-skill\n---\n");
        return bundle;
    }

    @Test
    @DisplayName("loads a bundle from a directory")
    void loadsFromDirectory(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleDirectory(root))) {
            assertEquals("customer-agent:1.2.0", bundle.descriptor().coordinates());
            assertEquals("customer-agent", bundle.manifest().metadata().name());
            assertEquals(1, bundle.manifest().agentSpec().capabilities().size());
            assertTrue(bundle.hasSkills());
            assertNull(bundle.temporaryRoot());
        }
    }

    @Test
    @DisplayName("synthesises a development descriptor when metadata.json is absent")
    void synthesisesDevelopmentDescriptor(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleDirectory(root))) {
            assertFalse(bundle.descriptor().isSigned());
            assertNull(bundle.descriptor().checksum());
            assertEquals("ghcr.io/giskardb/gargantua-runtime:1.0", bundle.descriptor().runtimeImage());
        }
    }

    @Test
    @DisplayName("reads provenance from metadata.json")
    void readsMetadata(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        Files.writeString(bundle.resolve(BundleLoader.METADATA_FILE), """
                {
                  "name": "customer-agent",
                  "version": "1.2.0",
                  "signature": "abc123",
                  "runtimeImage": "acme/runtime:2.1",
                  "createdAt": "2026-01-01T00:00:00Z",
                  "labels": {"env": "prod"}
                }
                """);

        try (LoadedBundle loaded = BundleLoader.load(bundle)) {
            assertTrue(loaded.descriptor().isSigned());
            assertEquals("acme/runtime:2.1", loaded.descriptor().runtimeImage());
            assertEquals("prod", loaded.descriptor().labels().get("env"));
            assertEquals("2026-01-01T00:00:00Z", loaded.descriptor().createdAt().toString());
        }
    }

    @Test
    @DisplayName("accepts a bundle whose declared checksum matches its contents")
    void acceptsMatchingChecksum(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        String checksum = BundleLoader.checksum(bundle);
        Files.writeString(bundle.resolve(BundleLoader.METADATA_FILE),
                "{\"checksum\": \"" + checksum + "\"}");

        try (LoadedBundle loaded = BundleLoader.load(bundle)) {
            assertEquals(checksum, loaded.descriptor().checksum());
        }
    }

    @Test
    @DisplayName("refuses a bundle altered after publication")
    void refusesTamperedBundle(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        String checksum = BundleLoader.checksum(bundle);
        Files.writeString(bundle.resolve(BundleLoader.METADATA_FILE),
                "{\"checksum\": \"" + checksum + "\"}");

        Files.writeString(bundle.resolve("skills/default-skill/SKILL.md"), "---\nname: tampered\n---\n");

        BundleException ex = assertThrows(BundleException.class, () -> BundleLoader.load(bundle));
        assertTrue(ex.getMessage().contains("failed integrity check"));
    }

    @Test
    @DisplayName("checksum is stable across repeated computation")
    void checksumIsStable(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        assertEquals(BundleLoader.checksum(bundle), BundleLoader.checksum(bundle));
    }

    @Test
    @DisplayName("checksum ignores metadata.json, which carries the checksum itself")
    void checksumIgnoresMetadata(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        String before = BundleLoader.checksum(bundle);
        Files.writeString(bundle.resolve(BundleLoader.METADATA_FILE), "{\"signature\": \"later\"}");

        assertEquals(before, BundleLoader.checksum(bundle));
    }

    @Test
    @DisplayName("checksum changes when a file moves")
    void checksumCoversPaths(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        String before = BundleLoader.checksum(bundle);
        Files.move(bundle.resolve("skills/default-skill/SKILL.md"),
                bundle.resolve("skills/SKILL.md"));

        assertNotEquals(before, BundleLoader.checksum(bundle));
    }

    @Test
    @DisplayName("loads a bundle from an archive and cleans up on close")
    void loadsFromArchive(@TempDir Path root) throws IOException {
        Path archive = root.resolve("customer-agent.gbundle");
        try (OutputStream out = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(BundleLoader.MANIFEST_FILE));
            zip.write(MANIFEST.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("skills/default-skill/SKILL.md"));
            zip.write("---\nname: default-skill\n---\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Path temporaryRoot;
        try (LoadedBundle bundle = BundleLoader.load(archive)) {
            assertEquals("customer-agent", bundle.manifest().metadata().name());
            assertTrue(bundle.hasSkills());
            temporaryRoot = bundle.temporaryRoot();
            assertNotNull(temporaryRoot);
            assertTrue(Files.exists(temporaryRoot));
        }
        assertFalse(Files.exists(temporaryRoot), "temporary extraction should be removed on close");
    }

    @Test
    @DisplayName("refuses an archive entry that escapes the extraction directory")
    void refusesZipSlip(@TempDir Path root) throws IOException {
        Path archive = root.resolve("evil.gbundle");
        try (OutputStream out = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../escaped.txt"));
            zip.write("pwned".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        BundleException ex = assertThrows(BundleException.class, () -> BundleLoader.load(archive));
        assertTrue(ex.getMessage().contains("illegal path"));
    }

    @Test
    @DisplayName("refuses a bundle without a manifest")
    void refusesBundleWithoutManifest(@TempDir Path root) throws IOException {
        Path bundle = root.resolve("empty.gbundle");
        Files.createDirectories(bundle);

        BundleException ex = assertThrows(BundleException.class, () -> BundleLoader.load(bundle));
        assertTrue(ex.getMessage().contains("has no manifest.yaml"));
    }

    @Test
    @DisplayName("refuses a path that does not exist")
    void refusesMissingPath(@TempDir Path root) {
        assertThrows(BundleException.class, () -> BundleLoader.load(root.resolve("nope")));
    }

    @Test
    @DisplayName("refuses a null path")
    void refusesNullPath() {
        assertThrows(BundleException.class, () -> BundleLoader.load(null));
    }

    @Test
    @DisplayName("resolves bundle-relative files and rejects escapes")
    void resolvesRelativePaths(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        Files.createDirectories(bundle.resolve("schemas"));
        Files.writeString(bundle.resolve("schemas/refund.json"), "{}");

        try (LoadedBundle loaded = BundleLoader.load(bundle)) {
            assertTrue(loaded.resolve("schemas/refund.json").isPresent());
            assertTrue(loaded.resolve("schemas/absent.json").isEmpty());
            assertTrue(loaded.resolve(null).isEmpty());
            assertThrows(BundleException.class, () -> loaded.resolve("../../etc/passwd"));
        }
    }

    @Test
    @DisplayName("closing a directory-backed bundle leaves it in place")
    void closingDirectoryBundleKeepsFiles(@TempDir Path root) throws IOException {
        Path bundle = bundleDirectory(root);
        BundleLoader.load(bundle).close();

        assertTrue(Files.exists(bundle.resolve(BundleLoader.MANIFEST_FILE)));
    }
}
