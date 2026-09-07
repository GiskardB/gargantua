package ai.gargantua.core.pact;

import java.util.Set;

/**
 * Semantic description of an agent's reasoning system — PACT's "Cognition" pillar
 * (see the <a href="https://github.com/GiskardB/PACT">PACT specification</a> §11-13).
 *
 * <p>Distinct from {@link ai.gargantua.core.workload.ModelSpec}: {@code ModelSpec} names
 * the <em>operational</em> model alias the runtime resolves via environment (e.g.
 * {@code gpt-4o}); {@code Cognition} declares, portably and vendor-neutrally, what
 * <em>kind</em> of cognition the agent exposes — useful for discovery/matching even when
 * the concrete deployment is unknown.</p>
 *
 * <p>{@code modalities} and {@code capabilities} are open vocabularies by design, same
 * stance as {@link ai.gargantua.core.capability.Capability#tags()}: PACT does not define a
 * universal taxonomy.</p>
 *
 * @param modalities   what the agent can understand/produce (e.g. {@code text}, {@code image})
 * @param capabilities cognitive abilities offered (e.g. {@code reasoning}, {@code planning})
 * @param models       optional semantic model family declaration
 * @param requirements optional requirements this agent's cognitive substrate must meet
 */
public record Cognition(
        Set<String> modalities,
        Set<String> capabilities,
        CognitionModels models,
        CognitionRequirements requirements
) {

    private static final Cognition NONE = new Cognition(Set.of(), Set.of(), null, null);

    public Cognition {
        modalities = modalities == null ? Set.of() : Set.copyOf(modalities);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    /** No cognition declared. */
    public static Cognition none() {
        return NONE;
    }

    /** Whether nothing at all is declared. */
    public boolean isEmpty() {
        return modalities.isEmpty() && capabilities.isEmpty() && models == null && requirements == null;
    }
}
