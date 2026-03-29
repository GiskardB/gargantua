package ai.gargantua.core.security;

import java.util.Set;

/**
 * Security context for the current request, extracted from HTTP headers
 * propagated by the API gateway (X-User-Id, X-Tenant-Id, X-User-Roles).
 *
 * <p>If no gateway is present (e.g., development), defaults to anonymous
 * access with no tenant and an empty role set.</p>
 */
public record SecurityContext(
    String userId,
    String tenantId,
    Set<String> roles
) {
    /** Anonymous context with no roles — used when no auth headers are present. */
    public static SecurityContext anonymous(String userId) {
        return new SecurityContext(userId, null, Set.of());
    }

    /** Check if this context has a specific role. */
    public boolean hasRole(String role) {
        return roles.contains(role) || roles.contains("super-admin");
    }

    /** Check if this context has any of the given roles. */
    public boolean hasAnyRole(String... requiredRoles) {
        for (var role : requiredRoles) {
            if (hasRole(role)) return true;
        }
        return false;
    }

    /** True if a tenantId is present. */
    public boolean isMultiTenant() {
        return tenantId != null && !tenantId.isBlank();
    }
}
