package ai.gargantua.core.workload;

/**
 * Model selection for a workload, expressed as references rather than credentials.
 *
 * <p>A bundle names <em>which</em> model to use; the runtime holds the endpoint and API
 * key for it, injected from the environment. This keeps bundles portable across
 * environments and free of secrets, and means promoting a bundle from staging to
 * production changes nothing in the artifact.</p>
 *
 * <p>Every field is nullable: {@code null} means "inherit the runtime default", so a
 * manifest overrides only what it actually cares about.</p>
 *
 * @param primary     alias of the model answering users (e.g. {@code gpt-4o})
 * @param fallback    alias used when the primary fails
 * @param routing     alias used for skill routing and session summarisation
 * @param temperature sampling temperature override, or {@code null}
 * @param maxTokens   response token ceiling override, or {@code null}
 */
public record ModelSpec(
        String primary,
        String fallback,
        String routing,
        Double temperature,
        Integer maxTokens
) {

    public ModelSpec {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("Model temperature must be between 0.0 and 2.0");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("Model maxTokens must be positive");
        }
    }

    /** Inherit every model setting from the runtime environment. */
    public static ModelSpec inherit() {
        return new ModelSpec(null, null, null, null, null);
    }
}
