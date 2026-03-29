package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RbacGuardrail}.
 */
class RbacGuardrailTest {

    private final RbacGuardrail guardrail = new RbacGuardrail();

    private GuardrailInputContext ctx(SkillMeta skill, SecurityContext securityContext) {
        Map<String, Object> attributes = new HashMap<>();
        if (securityContext != null) {
            attributes.put(RbacGuardrail.SECURITY_CONTEXT_KEY, securityContext);
        }
        return new GuardrailInputContext("hello", "user-1", "sess-1", skill, attributes);
    }

    @Test
    @DisplayName("Should pass when skill has no role restriction (empty allowedRoles)")
    void shouldPassWhenSkillHasNoRoleRestriction() {
        var skill = new SkillMeta("general", "General chat", "1.0.0",
                true, false, "general", SkillSource.FILESYSTEM, Set.of());
        var security = new SecurityContext("user-1", null, Set.of("viewer"));

        var result = guardrail.check(ctx(skill, security));
        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    @DisplayName("Should pass when user has required role")
    void shouldPassWhenUserHasRequiredRole() {
        var skill = new SkillMeta("finance", "Finance skill", "1.0.0",
                true, false, "finance", SkillSource.FILESYSTEM, Set.of("financial-operator", "finance-admin"));
        var security = new SecurityContext("user-1", "tenant-a", Set.of("financial-operator"));

        var result = guardrail.check(ctx(skill, security));
        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    @DisplayName("Should block when user lacks required role")
    void shouldBlockWhenUserLacksRole() {
        var skill = new SkillMeta("finance", "Finance skill", "1.0.0",
                true, false, "finance", SkillSource.FILESYSTEM, Set.of("financial-operator"));
        var security = new SecurityContext("user-1", "tenant-a", Set.of("viewer"));

        var result = guardrail.check(ctx(skill, security));
        assertEquals(GuardrailVerdict.BLOCK, result.verdict());
        assertTrue(result.reason().contains("user-1"));
        assertTrue(result.reason().contains("financial-operator"));
    }

    @Test
    @DisplayName("Should pass for super-admin regardless of skill roles")
    void shouldPassForSuperAdmin() {
        var skill = new SkillMeta("finance", "Finance skill", "1.0.0",
                true, false, "finance", SkillSource.FILESYSTEM, Set.of("financial-operator"));
        var security = new SecurityContext("admin-1", "tenant-a", Set.of("super-admin"));

        var result = guardrail.check(ctx(skill, security));
        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    @DisplayName("Should pass when no skill is activated yet")
    void shouldPassWhenNoSkillActivated() {
        var security = new SecurityContext("user-1", null, Set.of());

        var result = guardrail.check(ctx(null, security));
        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    @DisplayName("Should block when security context is missing for restricted skill")
    void shouldBlockWhenSecurityContextMissing() {
        var skill = new SkillMeta("finance", "Finance skill", "1.0.0",
                true, false, "finance", SkillSource.FILESYSTEM, Set.of("financial-operator"));

        var result = guardrail.check(ctx(skill, null));
        assertEquals(GuardrailVerdict.BLOCK, result.verdict());
        assertTrue(result.reason().contains("no security context"));
    }
}
