package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.security.SecurityContext;
import org.springframework.core.annotation.Order;

/**
 * Input guardrail that enforces role-based access control on skills.
 * Runs at {@code @Order(5)} — before all other guardrails — so that
 * unauthorised requests are rejected as early as possible.
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>If the activated skill has an empty {@code allowedRoles} set, the guardrail passes (no restriction).</li>
 *   <li>If the activated skill has a non-empty {@code allowedRoles} set and the user holds none of those
 *       roles, the request is blocked.</li>
 *   <li>Users with the {@code super-admin} role always pass.</li>
 * </ul>
 *
 * <p>The {@link SecurityContext} is read from the {@code GuardrailInputContext} attributes
 * under the key {@value #SECURITY_CONTEXT_KEY}.</p>
 */
@Order(5)
public class RbacGuardrail implements InputGuardrail {

    public static final String SECURITY_CONTEXT_KEY = "gargantua.securityContext";

    @Override
    public String name() {
        return "rbac";
    }

    @Override
    public boolean isEnabled(Object props) {
        return true; // always enabled when registered
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        // If no skill resolved yet, pass — RBAC only applies after routing
        if (ctx.activatedSkill() == null) {
            return GuardrailResult.pass(name());
        }

        var allowedRoles = ctx.activatedSkill().allowedRoles();
        // Empty allowedRoles means no restriction
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return GuardrailResult.pass(name());
        }

        // Extract SecurityContext from attributes
        var securityContextObj = ctx.attributes().get(SECURITY_CONTEXT_KEY);
        if (!(securityContextObj instanceof SecurityContext securityContext)) {
            return GuardrailResult.block(name(),
                    "Access denied: no security context available for role-restricted skill '%s'"
                            .formatted(ctx.activatedSkill().name()));
        }

        // super-admin bypasses all restrictions
        if (securityContext.hasRole("super-admin")) {
            return GuardrailResult.pass(name());
        }

        // Check if user has any of the required roles
        if (securityContext.hasAnyRole(allowedRoles.toArray(String[]::new))) {
            return GuardrailResult.pass(name());
        }

        return GuardrailResult.block(name(),
                "Access denied: user '%s' lacks required role(s) %s for skill '%s'"
                        .formatted(securityContext.userId(), allowedRoles, ctx.activatedSkill().name()));
    }
}
