package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Output guardrail that appends a configurable disclaimer to the agent's response.
 * Can be scoped to specific skill domains (e.g. only add "not medical advice" to health skills).
 * Disabled by default; enable via {@code agent.guardrail.output.disclaimer-enabled=true}.
 */
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
        var response = ctx.rawResponse() != null ? ctx.rawResponse() : "";

        var disclaimerText = agentProperties.getGuardrail().getOutput().getDisclaimerText();
        var disclaimerDomains = agentProperties.getGuardrail().getOutput().getDisclaimerDomains();

        // If specific domains are configured, only add disclaimer for matching skills
        if (disclaimerDomains != null && !disclaimerDomains.isEmpty()) {
            var skillDomain = ctx.activatedSkill() != null ? ctx.activatedSkill().domain() : "";
            if (!disclaimerDomains.contains(skillDomain)) {
                return new GuardrailOutputResult(GuardrailVerdict.PASS, response, null, name());
            }
        }

        var withDisclaimer = "%s\n\n---\n%s".formatted(response, disclaimerText);
        return new GuardrailOutputResult(GuardrailVerdict.PASS, withDisclaimer, null, name());
    }
}
