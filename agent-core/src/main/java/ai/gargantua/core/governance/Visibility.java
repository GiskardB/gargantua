package ai.gargantua.core.governance;

/**
 * How widely a governed resource may be seen and used across tenants.
 *
 * <p>Ordered from most to least restrictive. This is a declaration of intent carried by
 * the domain model; the Control Plane's Policy Manager is what actually enforces it.</p>
 */
public enum Visibility {

    /** Visible only to its owner (and principals named in the ACL). The safe default. */
    PRIVATE,

    /** Visible to the owning tenant/organisation. */
    INTERNAL,

    /** Visible to everyone the platform serves. */
    PUBLIC
}
