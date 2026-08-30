package ai.gargantua.core.pact;

/**
 * PACT's "Identity" pillar: who publishes or owns the agent (PACT §8). PACT does not
 * mandate DID, certificates, blockchain identity, OAuth or a particular identity
 * provider — this is a plain, informal statement of ownership.
 *
 * <p>Gargantua does not carry a separate identity block on the manifest: this is
 * projected from {@link ai.gargantua.core.workload.WorkloadMetadata#owner()} by
 * {@link PactManifest#from}, since that field already answers the same question
 * ("who owns this, for Catalog ownership and alerting"). {@code null} when
 * {@code owner} is unset — PACT treats identity as fully optional.</p>
 *
 * @param provider owning organisation or individual
 */
public record Identity(String provider) {
}
