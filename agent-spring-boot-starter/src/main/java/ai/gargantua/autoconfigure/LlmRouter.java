package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Evaluates routing rules from config to resolve which LLM model alias to use.
 */
@Component
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final AgentProperties properties;

    public LlmRouter(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve the model alias for the given routing context.
     * Evaluates rules sorted by priority (lowest first). Returns primary alias if no rule matches.
     */
    public String resolve(LlmRoutingContext ctx) {
        List<AgentProperties.RoutingRule> rules = properties.getLlm().getRoutingRules();
        if (rules == null || rules.isEmpty()) {
            return properties.getLlm().getPrimaryAlias();
        }

        List<AgentProperties.RoutingRule> sorted = rules.stream()
                .filter(AgentProperties.RoutingRule::isEnabled)
                .sorted(Comparator.comparingInt(AgentProperties.RoutingRule::getPriority))
                .toList();

        for (AgentProperties.RoutingRule rule : sorted) {
            if (matchesRule(rule, ctx)) {
                log.debug("LLM routing rule matched: '{}' -> model '{}'", rule.getName(), rule.getTargetModel());
                return rule.getTargetModel();
            }
        }

        log.debug("No LLM routing rule matched, using primary: '{}'", properties.getLlm().getPrimaryAlias());
        return properties.getLlm().getPrimaryAlias();
    }

    /**
     * Simple condition matching. Supports:
     * - "skill" -> matches skill name
     * - "domain" -> matches skill domain
     * - "minTokens" -> matches when estimated input tokens exceed the value
     * - "userTier" -> matches user tier
     */
    boolean matchesRule(AgentProperties.RoutingRule rule, LlmRoutingContext ctx) {
        Map<String, Object> condition = rule.getCondition();
        if (condition == null || condition.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();

            boolean match = switch (key) {
                case "skill" -> expected.toString().equals(ctx.skillName());
                case "domain" -> expected.toString().equals(ctx.skillDomain());
                case "minTokens" -> {
                    int threshold = expected instanceof Number n ? n.intValue() : Integer.parseInt(expected.toString());
                    yield ctx.estimatedInputTokens() >= threshold;
                }
                case "userTier" -> expected.toString().equals(ctx.userTier());
                default -> {
                    // Check against context attributes
                    yield ctx.attributes() != null &&
                          expected.toString().equals(ctx.attributes().get(key));
                }
            };

            if (!match) {
                return false;
            }
        }

        return true;
    }
}
