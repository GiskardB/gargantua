package ai.gargantua.autoconfigure;

import ai.gargantua.core.capability.Capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The capabilities this workload advertises to the platform.
 *
 * <p>Capabilities are what the Gateway routes on: a caller asks for
 * {@code refund-payment} and the Catalog resolves it to whichever agent currently
 * provides it. They are therefore the agent's external contract, deliberately separate
 * from the skills that decide its internal behaviour.</p>
 *
 * <p>In runtime mode the registry is populated from the bundle manifest. In library mode
 * it is normally empty, and the agent is reached directly rather than through capability
 * routing — which is why an empty registry is a valid, silent state rather than a
 * misconfiguration.</p>
 *
 * @see ai.gargantua.core.workload.AgentSpec#capabilities()
 */
public class CapabilityRegistry {

    private final Map<String, Capability> byName;

    public CapabilityRegistry(List<Capability> capabilities) {
        Map<String, Capability> index = new LinkedHashMap<>();
        if (capabilities != null) {
            for (Capability capability : capabilities) {
                if (capability != null && capability.name() != null) {
                    index.put(capability.name(), capability);
                }
            }
        }
        this.byName = Map.copyOf(index);
    }

    /** Registry for an agent that advertises nothing — the library-mode default. */
    public static CapabilityRegistry empty() {
        return new CapabilityRegistry(List.of());
    }

    public List<Capability> all() {
        return List.copyOf(byName.values());
    }

    public Optional<Capability> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Name of the skill implementing {@code capabilityName}, when one is bound. */
    public Optional<String> skillFor(String capabilityName) {
        return find(capabilityName)
                .map(Capability::implementedBy)
                .filter(skill -> skill != null && !skill.isBlank());
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    public int size() {
        return byName.size();
    }
}
