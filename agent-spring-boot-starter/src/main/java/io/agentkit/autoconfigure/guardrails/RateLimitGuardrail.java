package io.agentkit.autoconfigure.guardrails;

import io.agentkit.autoconfigure.AgentProperties;
import io.agentkit.core.guardrail.GuardrailInputContext;
import io.agentkit.core.guardrail.GuardrailResult;
import io.agentkit.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
