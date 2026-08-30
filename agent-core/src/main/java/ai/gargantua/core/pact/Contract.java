package ai.gargantua.core.pact;

import java.util.Set;

/**
 * PACT's "Contract" pillar: the basic semantic conditions under which the agent may act
 * (see {@code PACT_v0.3_Agent_Contract_Specification.md} §14-15).
 *
 * <p>Deliberately small and <strong>declarative</strong> — not a security control and not
 * a substitute for {@link ai.gargantua.core.workload.AgentSpec#allowedRoles()} /
 * {@link ai.gargantua.core.workload.AgentSpec#guardrails()}, which remain the mechanisms
 * the runtime actually enforces. A capability does not imply permission, and neither does
 * a contract: this is what the agent <em>claims</em>, not what it is authorized to do.</p>
 *
 * @param autonomy    self-declared autonomy, or {@code null} if undeclared — see
 *                    {@link Autonomy}
 * @param permissions free-form permission strings the agent claims to require (e.g.
 *                    {@code read_repository}); no controlled vocabulary, same
 *                    open-taxonomy stance as {@link ai.gargantua.core.capability.Capability#tags()}
 */
public record Contract(Autonomy autonomy, Set<String> permissions) {

    private static final Contract NONE = new Contract(null, Set.of());

    public Contract {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** No contract declared. */
    public static Contract none() {
        return NONE;
    }

    /** Whether nothing at all is declared. */
    public boolean isEmpty() {
        return autonomy == null && permissions.isEmpty();
    }
}
