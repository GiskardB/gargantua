package ai.gargantua.runtime;

import ai.gargantua.bundle.BundleException;
import ai.gargantua.bundle.BundleLoader;
import ai.gargantua.bundle.LoadedBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entry point of the standalone runtime: loads an agent bundle and executes it.
 *
 * <p>The runtime image is generic and the bundle is its payload. Keeping the two separate
 * is what allows a bundle to be rolled forward without rebuilding the image, and a
 * patched image to be rolled out without republishing bundles.</p>
 *
 * <pre>
 * gargantua run      [bundle]   execute a bundle (default)
 * gargantua validate  bundle    parse and verify a bundle, then exit
 * </pre>
 *
 * <p>The bundle location is taken from the first positional argument, then
 * {@code --bundle=}, then {@code GARGANTUA_BUNDLE}, then {@code /bundle} — the last being
 * the conventional mount point inside a container.</p>
 */
@SpringBootApplication
public class GargantuaRuntime {

    private static final Logger log = LoggerFactory.getLogger(GargantuaRuntime.class);

    static final String BUNDLE_ENV = "GARGANTUA_BUNDLE";
    static final String DEFAULT_BUNDLE_PATH = "/bundle";
    private static final String BUNDLE_FLAG = "--bundle=";

    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;

    public static void main(String[] args) {
        List<String> arguments = List.of(args);
        String command = command(arguments);

        switch (command) {
            case "run" -> run(arguments);
            case "validate" -> System.exit(validate(arguments));
            case "help", "--help", "-h" -> {
                printUsage(System.out);
                System.exit(0);
            }
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage(System.err);
                System.exit(EXIT_USAGE);
            }
        }
    }

    private static void run(List<String> arguments) {
        LoadedBundle bundle;
        try {
            bundle = BundleLoader.load(bundlePath(arguments));
        } catch (BundleException e) {
            log.error("Cannot start: {}", e.getMessage());
            System.exit(EXIT_FAILURE);
            return;
        }

        for (String warning : ManifestProperties.unappliedFields(bundle)) {
            log.warn("Manifest: {}", warning);
        }
        Map<String, Object> properties = ManifestProperties.from(bundle);
        log.info("Executing bundle '{}' with {} derived setting(s)",
                bundle.descriptor().coordinates(), properties.size());

        // Closing removes the temporary extraction directory for archive-delivered bundles.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeQuietly(bundle), "bundle-cleanup"));

        SpringApplication application = new SpringApplication(GargantuaRuntime.class);
        application.addInitializers(context -> installBundle(context, bundle, properties));
        application.run(arguments.toArray(String[]::new));
    }

    /**
     * Installs the bundle-derived settings just below the process environment, so the
     * bundle overrides the image defaults while an operator can still override the bundle
     * without republishing it.
     */
    private static void installBundle(ConfigurableApplicationContext context,
                                      LoadedBundle bundle, Map<String, Object> properties) {
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        MapPropertySource source = new MapPropertySource("gargantuaBundle", properties);
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            sources.addFirst(source);
        }
        context.getBeanFactory().registerSingleton("loadedBundle", bundle);
    }

    private static int validate(List<String> arguments) {
        try (LoadedBundle bundle = BundleLoader.load(bundlePath(arguments))) {
            var manifest = bundle.manifest();
            var spec = manifest.agentSpec();

            System.out.println("Bundle:       " + bundle.descriptor().coordinates());
            System.out.println("Kind:         " + manifest.kind());
            System.out.println("Runtime:      " + (spec.runtime().hasCustomImage()
                    ? spec.runtime().image() : "platform default"));
            System.out.println("Signed:       " + (bundle.descriptor().isSigned() ? "yes" : "no"));
            System.out.println("Checksum:     " + (bundle.descriptor().checksum() != null
                    ? "declared and verified" : "not declared"));
            System.out.println("Skills:       " + (bundle.hasSkills() ? "present" : "none"));
            System.out.println("Capabilities: " + (spec.capabilities().isEmpty() ? "none"
                    : spec.capabilities().stream().map(c -> c.name()).toList()));
            System.out.println("MCP servers:  " + (spec.mcpServers().isEmpty() ? "none"
                    : spec.mcpServers().stream().map(s -> s.name()).toList()));

            List<String> warnings = ManifestProperties.unappliedFields(bundle);
            for (String warning : warnings) {
                System.out.println("WARNING:      " + warning);
            }
            System.out.println(warnings.isEmpty() ? "OK" : "OK (with warnings)");
            return 0;
        } catch (BundleException e) {
            System.err.println("INVALID: " + e.getMessage());
            return EXIT_FAILURE;
        } catch (IOException e) {
            System.err.println("INVALID: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    /** First non-flag argument, defaulting to {@code run} so a bare invocation starts the agent. */
    static String command(List<String> arguments) {
        for (String argument : arguments) {
            if (!argument.startsWith("-")) {
                return argument;
            }
        }
        return "run";
    }

    /**
     * Resolves the bundle location, in order: second positional argument,
     * {@code --bundle=}, {@code GARGANTUA_BUNDLE}, then the container mount point.
     */
    static Path bundlePath(List<String> arguments) {
        return bundlePath(arguments, System.getenv(BUNDLE_ENV));
    }

    static Path bundlePath(List<String> arguments, String fromEnvironment) {
        List<String> positional = new ArrayList<>();
        for (String argument : arguments) {
            if (argument.startsWith(BUNDLE_FLAG)) {
                String value = argument.substring(BUNDLE_FLAG.length());
                if (!value.isBlank()) {
                    return Path.of(value);
                }
            } else if (!argument.startsWith("-")) {
                positional.add(argument);
            }
        }
        if (positional.size() > 1) {
            return Path.of(positional.get(1));
        }
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Path.of(fromEnvironment);
        }
        return Path.of(DEFAULT_BUNDLE_PATH);
    }

    private static void closeQuietly(LoadedBundle bundle) {
        try {
            bundle.close();
        } catch (IOException e) {
            log.warn("Could not clean up bundle files: {}", e.getMessage());
        }
    }

    private static void printUsage(java.io.PrintStream out) {
        // Plain ASCII: this is read from container logs under arbitrary console encodings.
        out.println("""
                Gargantua Runtime - executes an agent bundle

                Usage:
                  gargantua run      [bundle]   execute a bundle (default command)
                  gargantua validate  bundle    parse and verify a bundle, then exit
                  gargantua help                show this message

                Bundle location, in order of precedence:
                  1. positional argument
                  2. --bundle=<path>
                  3. GARGANTUA_BUNDLE environment variable
                  4. /bundle
                """);
    }
}
