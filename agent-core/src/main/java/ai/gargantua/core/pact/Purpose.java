package ai.gargantua.core.pact;

/**
 * PACT's "Purpose" pillar: the intended role of the agent (PACT §9). Descriptive only —
 * it does not grant permission, and answers "what is it for", distinct from
 * {@code metadata.description}'s "what is it" (PACT §24, Human Questions).
 *
 * <p>Gargantua does not carry a separate purpose statement on the manifest: this is
 * projected from {@link ai.gargantua.core.workload.WorkloadMetadata#description()} by
 * {@link PactManifest#from} as a pragmatic default, since most manifests only author one
 * free-text field today. This is an approximation, not a perfect semantic match — a
 * manifest wanting to answer "what is it" and "what is it for" differently has nowhere to
 * put the second one yet. {@code null} when {@code description} is blank.</p>
 *
 * @param description human-readable statement of intent
 */
public record Purpose(String description) {
}
