package ai.gargantua.core.pact;

/**
 * A semantic (not operational) model family reference — PACT's vendor-neutral "what kind
 * of model" declaration, as opposed to {@link ai.gargantua.core.workload.ModelSpec}'s
 * operational alias resolved by the runtime environment.
 *
 * @param provider vendor/provider, e.g. {@code anthropic}
 * @param family   model family, e.g. {@code claude}
 * @param name     concrete model name, or {@code null} to leave the family unspecified
 */
public record ModelDescriptor(String provider, String family, String name) {
}
