package ai.gargantua.core.pact;

import java.util.Set;

/**
 * What this agent's hosting cognitive substrate must provide — distinct from
 * {@link Cognition#modalities()}/{@link Cognition#capabilities()}, which declare what the
 * agent itself offers. This is a <em>requirement</em> on the runtime, not a
 * self-description; it enables semantic agent selection without naming a vendor.
 *
 * @param modalities           modalities the substrate must support
 * @param capabilities         cognitive capabilities the substrate must support
 * @param contextWindowMinimum minimum context window in tokens, or {@code null}
 */
public record CognitionRequirements(Set<String> modalities, Set<String> capabilities,
                                     Integer contextWindowMinimum) {

    public CognitionRequirements {
        modalities = modalities == null ? Set.of() : Set.copyOf(modalities);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        if (contextWindowMinimum != null && contextWindowMinimum <= 0) {
            throw new IllegalArgumentException("Cognition requirements contextWindow.minimum must be positive");
        }
    }
}
