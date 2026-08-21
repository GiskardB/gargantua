package ai.gargantua.core.governance;

import java.time.Instant;
import java.util.List;

/**
 * The shared governance metadata that can be attached to any first-class platform
 * resource — an agent, a skill, a capability, a memory or a knowledge base. It answers
 * <em>who owns this, who may see it, and what state is it in</em>, independently of what
 * the resource actually does.
 *
 * <p>This is deliberately a <strong>trait carried by</strong> each domain type (see
 * {@link Governed}), not a common supertype the types collapse into: an agent is still an
 * agent and a capability still a capability. Sharing the envelope gives consistent
 * ownership/visibility/lifecycle across the Catalog without erasing those distinctions.</p>
 *
 * <p>Ownership of the primary identity fields is split on purpose: a resource's own name
 * and version live on the resource ({@code WorkloadMetadata}, {@code Capability}, …); the
 * envelope adds the <em>cross-cutting</em> governance concerns. {@code createdAt} and
 * {@code updatedAt} are assigned by the Control Plane when a resource is registered — a
 * signed bundle manifest does not carry them, so they are nullable here.</p>
 *
 * @param tenant     owning tenant/organisation scope, or {@code null} for the default tenant
 * @param visibility how widely the resource may be seen; never {@code null} (defaults to
 *                   {@link Visibility#PRIVATE})
 * @param status     free-form lifecycle status label (e.g. {@code draft}, {@code active},
 *                   {@code deprecated}); {@code null} when unset. A typed lifecycle state
 *                   machine is a separate, later concern
 * @param access     ACL: roles or principals granted access beyond what {@code visibility}
 *                   implies; never {@code null} (empty means "visibility alone decides")
 * @param createdAt  Control-Plane-assigned creation instant, or {@code null} (e.g. inside a
 *                   bundle manifest, which does not carry timestamps)
 * @param updatedAt  Control-Plane-assigned last-update instant, or {@code null}
 */
public record GovernanceEnvelope(
        String tenant,
        Visibility visibility,
        String status,
        List<String> access,
        Instant createdAt,
        Instant updatedAt
) {

    private static final GovernanceEnvelope NONE =
            new GovernanceEnvelope(null, Visibility.PRIVATE, null, List.of(), null, null);

    public GovernanceEnvelope {
        visibility = visibility == null ? Visibility.PRIVATE : visibility;
        access = access == null ? List.of() : List.copyOf(access);
        tenant = (tenant == null || tenant.isBlank()) ? null : tenant.trim();
        status = (status == null || status.isBlank()) ? null : status.trim();
    }

    /** The default envelope: no tenant, {@code PRIVATE}, no status, no ACL, no timestamps. */
    public static GovernanceEnvelope none() {
        return NONE;
    }

    /** An authorable envelope (tenant/visibility/status/access) with no timestamps. */
    public static GovernanceEnvelope of(String tenant, Visibility visibility, String status,
                                        List<String> access) {
        return new GovernanceEnvelope(tenant, visibility, status, access, null, null);
    }

    /** Whether this envelope carries nothing beyond the defaults. */
    public boolean isDefault() {
        return tenant == null && visibility == Visibility.PRIVATE && status == null
                && access.isEmpty() && createdAt == null && updatedAt == null;
    }

    /** A copy stamped with Control-Plane timestamps, leaving the authorable fields intact. */
    public GovernanceEnvelope withTimestamps(Instant created, Instant updated) {
        return new GovernanceEnvelope(tenant, visibility, status, access, created, updated);
    }
}
