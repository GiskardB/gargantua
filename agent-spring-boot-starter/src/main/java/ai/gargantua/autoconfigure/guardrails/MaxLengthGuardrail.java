package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class MaxLengthGuardrail implements InputGuardrail {

    private final AgentProperties agentProperties;

    public MaxLengthGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "max-length";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getInput().isMaxLengthEnabled();
        }
        return agentProperties.getGuardrail().getInput().isMaxLengthEnabled();
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        int maxChars = agentProperties.getGuardrail().getInput().getMaxLengthChars();
        if (ctx.userMessage() != null && ctx.userMessage().length() > maxChars) {
            return GuardrailResult.block(name(),
                    "Message exceeds maximum length of " + maxChars + " characters (was " + ctx.userMessage().length() + ")");
        }
        return GuardrailResult.pass(name());
    }
}
