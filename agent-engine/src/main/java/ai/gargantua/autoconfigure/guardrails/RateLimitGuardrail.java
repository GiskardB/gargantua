package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Input guardrail for per-user rate limiting. Currently a placeholder that always
 * passes. The real implementation will use a Redis sliding window counter.
 * Disabled by default; enable via {@code agent.guardrail.input.rate-limit-enabled=true}.
 */
@Component
@Order(50)
public class RateLimitGuardrail implements InputGuardrail {

    private final AgentProperties agentProperties;

    public RateLimitGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "rate-limit";
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
        // Placeholder: returns PASS if disabled
        return GuardrailResult.pass(name());
    }
}
