package ai.gargantua.core.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecurityContext}.
 */
class SecurityContextTest {

    @Test
    void hasRoleReturnsTrueForMatchingRole() {
        var ctx = new SecurityContext("user-1", "tenant-a", Set.of("editor", "viewer"));
        assertTrue(ctx.hasRole("editor"));
        assertTrue(ctx.hasRole("viewer"));
        assertFalse(ctx.hasRole("admin"));
    }

    @Test
    void hasRoleReturnsTrueForSuperAdmin() {
        var ctx = new SecurityContext("admin-1", "tenant-a", Set.of("super-admin"));
        assertTrue(ctx.hasRole("editor"));
        assertTrue(ctx.hasRole("anything"));
        assertTrue(ctx.hasRole("super-admin"));
    }

    @Test
    void hasAnyRoleWorksCorrectly() {
        var ctx = new SecurityContext("user-1", "tenant-a", Set.of("viewer"));
        assertTrue(ctx.hasAnyRole("editor", "viewer"));
        assertFalse(ctx.hasAnyRole("admin", "financial-operator"));
    }

    @Test
    void hasAnyRolePassesForSuperAdmin() {
        var ctx = new SecurityContext("admin-1", null, Set.of("super-admin"));
        assertTrue(ctx.hasAnyRole("editor", "viewer"));
    }

    @Test
    void anonymousFactoryMethod() {
        var ctx = SecurityContext.anonymous("anon-user");
        assertEquals("anon-user", ctx.userId());
        assertNull(ctx.tenantId());
        assertTrue(ctx.roles().isEmpty());
        assertFalse(ctx.isMultiTenant());
    }

    @Test
    void isMultiTenantReturnsTrueWhenTenantIdPresent() {
        var ctx = new SecurityContext("user-1", "tenant-x", Set.of());
        assertTrue(ctx.isMultiTenant());
    }

    @Test
    void isMultiTenantReturnsFalseWhenTenantIdNull() {
        var ctx = new SecurityContext("user-1", null, Set.of());
        assertFalse(ctx.isMultiTenant());
    }

    @Test
    void isMultiTenantReturnsFalseWhenTenantIdBlank() {
        var ctx = new SecurityContext("user-1", "  ", Set.of());
        assertFalse(ctx.isMultiTenant());
    }
}
