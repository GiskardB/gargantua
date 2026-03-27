package io.agentkit.autoconfigure.guardrails;

import io.agentkit.autoconfigure.AgentProperties;
import io.agentkit.core.guardrail.GuardrailInputContext;
import io.agentkit.core.guardrail.GuardrailResult;
import io.agentkit.core.guardrail.InputGuardrail;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
                    return GuardrailResult.block(name(), "Blocked topic detected: " + topic);
                }
            }
        }

        return GuardrailResult.pass(name());
    }
}
