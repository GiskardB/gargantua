package io.agentkit.autoconfigure.guardrails;

import io.agentkit.autoconfigure.AgentProperties;
import io.agentkit.core.guardrail.GuardrailOutputContext;
import io.agentkit.core.guardrail.GuardrailOutputResult;
import io.agentkit.core.guardrail.GuardrailVerdict;
import io.agentkit.core.guardrail.OutputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ScopeValidatorGuardrail implements OutputGuardrail {

    private final AgentProperties agentProperties;

    public ScopeValidatorGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "scope-validator";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getOutput().isScopeValidationEnabled();
        }
        return agentProperties.getGuardrail().getOutput().isScopeValidationEnabled();
    }

    @Override
    public GuardrailOutputResult process(GuardrailOutputContext ctx) {
        // Placeholder: passes through if disabled
        return new GuardrailOutputResult(GuardrailVerdict.PASS, ctx.rawResponse(), null, name());
    }
}
