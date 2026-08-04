package ai.gargantua.runtime;

import ai.gargantua.autoconfigure.CapabilityRegistry;
import ai.gargantua.bundle.LoadedBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires bundle-derived beans that cannot be expressed as plain configuration properties.
 *
 * <p>Most of the manifest reaches the engine through {@link ManifestProperties}, which
 * projects it onto {@code agent.*}. Capabilities are the exception: they are a typed
 * domain concept rather than a setting, so they are bound as a bean.</p>
 */
@Configuration(proxyBeanMethods = false)
public class RuntimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfiguration.class);

    /**
     * Publishes the capabilities declared in the manifest. This is what the A2A Agent Card
     * advertises and, in turn, what the Catalog indexes and the Gateway routes on.
     */
    @Bean
    public CapabilityRegistry capabilityRegistry(LoadedBundle bundle) {
        var capabilities = bundle.manifest().agentSpec().capabilities();
        if (capabilities.isEmpty()) {
            log.warn("Bundle '{}' declares no capabilities — it will not be discoverable "
                    + "by capability routing", bundle.descriptor().coordinates());
        } else {
            log.info("Advertising {} capability/capabilities: {}",
                    capabilities.size(), capabilities.stream().map(c -> c.name()).toList());
        }
        return new CapabilityRegistry(capabilities);
    }
}
