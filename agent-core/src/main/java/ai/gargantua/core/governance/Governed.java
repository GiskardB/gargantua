package ai.gargantua.core.governance;

/**
 * Implemented by any domain type that carries a {@link GovernanceEnvelope} — agents,
 * skills, capabilities, memories, knowledge bases. It lets Catalog and Policy code treat
 * ownership/visibility/lifecycle uniformly ("who owns this, who may see it, what state is
 * it in?") without the types sharing a common supertype: each stays its own record and
 * merely exposes its envelope.
 */
public interface Governed {

    /** The governance metadata for this resource; never {@code null} (use {@link GovernanceEnvelope#none()}). */
    GovernanceEnvelope governance();
}
