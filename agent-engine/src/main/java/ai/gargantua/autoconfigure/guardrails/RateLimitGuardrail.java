package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.adapters.ratelimit.InMemoryRequestRateLimiter;
import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.RequestRateLimiter;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.security.SecurityContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Input guardrail (order 50) that limits how many requests one caller may make in a
 * sliding window: {@code agent.guardrail.input.rate-limit-max-requests} per
 * {@code rate-limit-window-seconds}. A caller is the user id, scoped by tenant when the
 * request carries one.
 *
 * <p>The input pipeline runs twice per request — before routing and again once the skill
 * is known. A request is counted on the first pass only; the second always passes here,
 * otherwise every request would cost two.</p>
 */
@Component
@Order(50)
public class RateLimitGuardrail implements InputGuardrail {

    public static final String NAME = "rate-limit";

    private final AgentProperties agentProperties;
    private final RequestRateLimiter limiter;

    /** Uses a limiter local to this JVM. */
    public RateLimitGuardrail(AgentProperties agentProperties) {
        this(agentProperties, new InMemoryRequestRateLimiter());
    }

    public RateLimitGuardrail(AgentProperties agentProperties, RequestRateLimiter limiter) {
        this.agentProperties = agentProperties;
        this.limiter = limiter;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getInput().isRateLimitEnabled();
        }
        return agentProperties.getGuardrail().getInput().isRateLimitEnabled();
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (ctx.activatedSkill() != null) {
            // Post-routing pass: this request was already counted.
            return GuardrailResult.pass(name());
        }
        var input = agentProperties.getGuardrail().getInput();
        int maxRequests = input.getRateLimitMaxRequests();
        int windowSeconds = input.getRateLimitWindowSeconds();
        if (maxRequests <= 0 || windowSeconds <= 0) {
            return GuardrailResult.pass(name());
        }
        if (limiter.tryAcquire(callerKey(ctx), maxRequests, Duration.ofSeconds(windowSeconds))) {
            return GuardrailResult.pass(name());
        }
        return GuardrailResult.block(name(),
                "Rate limit exceeded: at most %d requests per %d seconds".formatted(maxRequests, windowSeconds));
    }

    private static String callerKey(GuardrailInputContext ctx) {
        String userId = ctx.userId() != null ? ctx.userId() : "anonymous";
        Object security = ctx.attributes() != null
                ? ctx.attributes().get(RbacGuardrail.SECURITY_CONTEXT_KEY) : null;
        if (security instanceof SecurityContext sc && sc.isMultiTenant()) {
            return sc.tenantId() + ":" + userId;
        }
        return userId;
    }
}
