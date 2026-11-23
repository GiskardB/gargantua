package ai.gargantua.runtime;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Runtime CLI argument handling")
class GargantuaRuntimeCliTest {

    @Test
    @DisplayName("defaults to run when no command is given")
    void defaultsToRun() {
        assertThat(GargantuaRuntime.command(List.of())).isEqualTo("run");
        assertThat(GargantuaRuntime.command(List.of("--bundle=/x"))).isEqualTo("run");
    }

    @Test
    @DisplayName("reads the first positional argument as the command")
    void readsCommand() {
        assertThat(GargantuaRuntime.command(List.of("validate", "/bundles/a"))).isEqualTo("validate");
        assertThat(GargantuaRuntime.command(List.of("--debug", "run"))).isEqualTo("run");
    }

    @Test
    @DisplayName("takes the bundle from the second positional argument")
    void bundleFromPositionalArgument() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run", "/bundles/a"), null))
                .isEqualTo(Path.of("/bundles/a"));
    }

    @Test
    @DisplayName("takes the bundle from the --bundle flag")
    void bundleFromFlag() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run", "--bundle=/bundles/b"), null))
                .isEqualTo(Path.of("/bundles/b"));
    }

    @Test
    @DisplayName("the flag wins over a positional argument")
    void flagWinsOverPositional() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run", "/positional", "--bundle=/flag"), null))
                .isEqualTo(Path.of("/flag"));
    }

    @Test
    @DisplayName("falls back to the environment variable")
    void bundleFromEnvironment() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run"), "/env/bundle"))
                .isEqualTo(Path.of("/env/bundle"));
    }

    @Test
    @DisplayName("a positional argument wins over the environment variable")
    void positionalWinsOverEnvironment() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run", "/positional"), "/env/bundle"))
                .isEqualTo(Path.of("/positional"));
    }

    @Test
    @DisplayName("falls back to the container mount point")
    void bundleDefaultsToMountPoint() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run"), null))
                .isEqualTo(Path.of(GargantuaRuntime.DEFAULT_BUNDLE_PATH));
        assertThat(GargantuaRuntime.bundlePath(List.of("run"), "  "))
                .isEqualTo(Path.of(GargantuaRuntime.DEFAULT_BUNDLE_PATH));
    }

    @Test
    @DisplayName("an empty --bundle flag is ignored")
    void emptyFlagIgnored() {
        assertThat(GargantuaRuntime.bundlePath(List.of("run", "--bundle="), "/env/bundle"))
                .isEqualTo(Path.of("/env/bundle"));
    }

    @Test
    @DisplayName("a blank bundle URL falls through to local path resolution")
    void blankUrlFallsThrough() {
        assertThat(GargantuaRuntime.resolveBundlePath(List.of("run", "/bundles/a"), null))
                .isEqualTo(Path.of("/bundles/a"));
        assertThat(GargantuaRuntime.resolveBundlePath(List.of("run", "/bundles/a"), "  "))
                .isEqualTo(Path.of("/bundles/a"));
    }

    @Test
    @DisplayName("a bundle URL is downloaded to a temp file")
    void bundleDownloadedFromUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = "PK-fake-bundle-bytes".getBytes(StandardCharsets.UTF_8);
        server.createContext("/bundle", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bundle";
            Path downloaded = GargantuaRuntime.resolveBundlePath(List.of("run"), url);
            assertThat(downloaded).exists();
            assertThat(Files.readAllBytes(downloaded)).isEqualTo(body);
            Files.deleteIfExists(downloaded);
        } finally {
            server.stop(0);
        }
    }

    /** A stand-in Control Plane that serves the bundle only to the bearer token "s3cret". */
    private static HttpServer protectedBundleServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bundle", exchange -> {
            boolean ok = "Bearer s3cret".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = (ok ? "bundle" : "denied").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ok ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    @DisplayName("the bundle token is sent as a bearer token")
    void bundleTokenIsSentAsBearer() throws Exception {
        HttpServer server = protectedBundleServer();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bundle";
            Path downloaded = GargantuaRuntime.resolveBundlePath(List.of("run"), url, " s3cret ");
            assertThat(Files.readString(downloaded)).isEqualTo("bundle");
            Files.deleteIfExists(downloaded);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("a refused download names the variable to set")
    void refusedDownloadPointsAtTheTokenVariable() throws Exception {
        HttpServer server = protectedBundleServer();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bundle";
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> GargantuaRuntime.resolveBundlePath(List.of("run"), url, null))
                    .hasMessageContaining("HTTP 401")
                    .hasMessageContaining("GARGANTUA_BUNDLE_TOKEN");
        } finally {
            server.stop(0);
        }
    }
}
