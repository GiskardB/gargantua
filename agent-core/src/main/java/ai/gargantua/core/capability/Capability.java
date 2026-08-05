package ai.gargantua.core.capability;

import java.util.Set;

/**
 * A externally-advertised contract that a workload is able to fulfil.
 *
 * <p>Capabilities are the unit the platform routes on. A caller asks for
 * {@code refund-payment}; the Gateway resolves that name against the Catalog and
 * dispatches to whichever workload currently advertises it. Callers therefore
 * never bind to an agent name or version — they bind to a capability.</p>
 *
 * <p>This is deliberately distinct from {@link ai.gargantua.core.skill.SkillMeta}:
 * a <em>skill</em> is internal (how the agent decides to behave), a
 * <em>capability</em> is external (what the agent promises to others). One skill
 * may back several capabilities, and a capability may be satisfied by different
 * skills across versions.</p>
 *
 * @param name           stable identifier used for routing (e.g. {@code refund-payment});
 *                       lowercase, hyphen-separated by convention
 * @param description    human-readable summary, surfaced in the Catalog and used for
 *                       intent matching by the Gateway
 * @param version        semver of the capability contract itself, independent of the
 *                       workload version that implements it
 * @param inputSchema    JSON Schema describing accepted input, or {@code null} for free-form text
 * @param outputSchema   JSON Schema describing produced output, or {@code null} for free-form text
 * @param implementedBy  name of the skill that handles invocations of this capability,
 *                       or {@code null} to let normal skill routing decide
 * @param tags           free-form labels for Catalog filtering (e.g. {@code payments}, {@code gdpr})
 *
 * @see ai.gargantua.core.workload.AgentSpec
 */
public record Capability(
        String name,
        String description,
        String version,
        String inputSchema,
        String outputSchema,
        String implementedBy,
        Set<String> tags
) {

    public Capability {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    /**
     * Creates a free-form capability with no schema contract and no explicit skill
     * binding — the common case when an agent simply advertises what it can talk about.
     */
    public Capability(String name, String description, String version) {
        this(name, description, version, null, null, null, Set.of());
    }

    /** Whether this capability declares a structured input or output contract. */
    public boolean hasSchema() {
        return (inputSchema != null && !inputSchema.isBlank())
                || (outputSchema != null && !outputSchema.isBlank());
    }
}
