package ai.gargantua.core.pact;

/**
 * Semantic model declarations — {@link Cognition#models()}.
 *
 * @param primary  the agent's primary model family
 * @param fallback the agent's fallback model family, or {@code null}
 */
public record CognitionModels(ModelDescriptor primary, ModelDescriptor fallback) {
}
