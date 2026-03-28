package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.OutputGuardrail;
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
