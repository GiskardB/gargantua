package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(20)
public class DisclaimerInjectorGuardrail implements OutputGuardrail {

    private final AgentProperties agentProperties;

    public DisclaimerInjectorGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "disclaimer-injector";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getOutput().isDisclaimerEnabled();
        }
        return agentProperties.getGuardrail().getOutput().isDisclaimerEnabled();
    }

    @Override
    public GuardrailOutputResult process(GuardrailOutputContext ctx) {
        String response = ctx.rawResponse();
        if (response == null) {
            response = "";
        }

        String disclaimerText = agentProperties.getGuardrail().getOutput().getDisclaimerText();
        List<String> disclaimerDomains = agentProperties.getGuardrail().getOutput().getDisclaimerDomains();

        // If specific domains are configured, only add disclaimer for matching skills
        if (disclaimerDomains != null && !disclaimerDomains.isEmpty()) {
            String skillDomain = ctx.activatedSkill() != null ? ctx.activatedSkill().domain() : "";
            if (!disclaimerDomains.contains(skillDomain)) {
                return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
            }
        }

        String withDisclaimer = response + "\n\n---\n" + disclaimerText;
        return new GuardrailOutputResult(GuardrailVerdict.PASS, withDisclaimer, null, name());
    }
}
