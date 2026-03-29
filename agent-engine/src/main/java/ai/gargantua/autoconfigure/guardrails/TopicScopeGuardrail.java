package ai.gargantua.autoconfigure.guardrails;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Input guardrail that blocks messages containing forbidden topics.
 * Uses simple keyword matching against a configurable blocklist.
 * Disabled by default; enable via {@code agent.guardrail.input.topic-scope-enabled=true}
 * and populate {@code agent.guardrail.input.blocked-topics}.
 */
@Component
@Order(30)
public class TopicScopeGuardrail implements InputGuardrail {

    private final AgentProperties agentProperties;

    public TopicScopeGuardrail(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public String name() {
        return "topic-scope";
    }

    @Override
    public boolean isEnabled(Object props) {
        if (props instanceof AgentProperties ap) {
            return ap.getGuardrail().getInput().isTopicScopeEnabled();
        }
        return agentProperties.getGuardrail().getInput().isTopicScopeEnabled();
    }

    @Override
    public GuardrailResult check(GuardrailInputContext ctx) {
        // Placeholder: returns PASS if disabled or no blocked topics configured
        var blockedTopics = agentProperties.getGuardrail().getInput().getBlockedTopics();
        if (blockedTopics == null || blockedTopics.isEmpty()) {
            return GuardrailResult.pass(name());
        }

        // Simple keyword check placeholder
        if (ctx.userMessage() != null) {
            String lower = ctx.userMessage().toLowerCase();
            for (String topic : blockedTopics) {
                if (lower.contains(topic.toLowerCase())) {
                    return GuardrailResult.block(name(), "Blocked topic detected: %s".formatted(topic));
                }
            }
        }

        return GuardrailResult.pass(name());
    }
}
