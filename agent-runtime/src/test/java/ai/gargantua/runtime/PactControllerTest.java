package ai.gargantua.runtime;

import ai.gargantua.bundle.BundleLoader;
import ai.gargantua.bundle.LoadedBundle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live counterpart to {@code /.well-known/agent.json}: this is what proves the
 * wire-format fix in {@code PactManifest.toWireMap()} (autonomy as an integer level, not
 * an enum name) actually reaches an HTTP response, not just a unit-tested map.
 */
@DisplayName("PactController")
class PactControllerTest {

    private static final String MANIFEST_WITH_PACT_FIELDS = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: customer-agent
              version: 1.2.0
              description: Handles refunds
            spec:
              cognition:
                modalities: [text]
                capabilities: [reasoning]
              contract:
                autonomy:
                  level: 2
                permissions: [read_repository]
              interfaces:
                - protocol: a2a
                  endpoint: https://example.com/a2a
            """;

    private static Path bundleWith(Path root, String manifest) throws IOException {
        Path bundle = root.resolve("customer-agent.gbundle");
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve(BundleLoader.MANIFEST_FILE), manifest);
        return bundle;
    }

    @Test
    @DisplayName("serves the manifest's PACT fields in wire format, not enum names")
    void servesPactFieldsInWireFormat(@TempDir Path root) throws IOException {
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, MANIFEST_WITH_PACT_FIELDS))) {
            PactController controller = new PactController(bundle);

            var response = controller.wellKnownPactJson();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getCacheControl()).contains("max-age=60");

            Map<String, Object> body = response.getBody();
            assertThat(body).containsEntry("apiVersion", "pact/v1");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");
            assertThat(metadata).containsEntry("name", "customer-agent");

            @SuppressWarnings("unchecked")
            Map<String, Object> contract = (Map<String, Object>) body.get("contract");
            @SuppressWarnings("unchecked")
            Map<String, Object> autonomy = (Map<String, Object>) contract.get("autonomy");
            assertThat(autonomy).containsEntry("level", 2);

            @SuppressWarnings("unchecked")
            Map<String, Object> cognition = (Map<String, Object>) body.get("cognition");
            assertThat(cognition).containsEntry("modalities", java.util.List.of("text"));
        }
    }

    @Test
    @DisplayName("omits cognition/contract/interfaces when the manifest declares none")
    void omitsUndeclaredPactSections(@TempDir Path root) throws IOException {
        String minimal = """
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: a
                  version: 1.0.0
                spec: {}
                """;
        try (LoadedBundle bundle = BundleLoader.load(bundleWith(root, minimal))) {
            PactController controller = new PactController(bundle);

            Map<String, Object> body = controller.wellKnownPactJson().getBody();

            assertThat(body).doesNotContainKeys("cognition", "contract", "interfaces");
        }
    }
}
