package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input guardrail that detects common prompt injection patterns (e.g. "ignore previous instructions",
 * "you are now a...", "DAN mode"). Blocks the request if any pattern matches.
 * Enabled by default; configure via {@code agent.guardrail.input.prompt-injection-enabled}.
 */
@Component
@Order(20)
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?prior\\s+(instructions|prompts)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+(?:a|an)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(all\\s+)?(your|previous)\\s+(instructions|rules)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDAN\\s+mode\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+(restrictions|rules|guidelines)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+(your|the)\\s+(system|safety)\\s+(prompt|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+you\\s+are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+your\\s+instructions", Pattern.CASE_INSENSITIVE)
    );

    private final AgentProperties agentProperties;

    public PromptInjectionGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "prompt-injection";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getInput().isPromptInjectionEnabled();
        }
        return agentProperties.getGuardrail().getInput().isPromptInjectionEnabled();
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        if (ctx.userMessage() == null || ctx.userMessage().isBlank()) {
            return GuardrailResult.pass(name());
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            var matcher = pattern.matcher(ctx.userMessage());
            if (matcher.find()) {
                return GuardrailResult.block(name(),
                        "Potential prompt injection detected")
                        .withMetadata("matched_pattern", pattern.pattern());
            }
        }

        return GuardrailResult.pass(name());
    }
}
