package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates routing rules from configuration to select which LLM model alias
 * handles a request. Rules are sorted by priority (lowest first) and evaluated
 * against the {@link LlmRoutingContext}; the first matching rule wins. If no
 * rule matches, the primary model alias is used.
 *
 * <p>Condition syntax is documented in {@code docs/llm-configuration.md} and
 * implemented by {@link RoutingRuleEvaluator}, which supports operators
 * (EQ/IN/NOT_IN/GT/LT/GTE/LTE/CONTAINS/REGEX), special structures
 * (time-window, day-of-week, random-sampling, input-contains, attribute-match)
 * and {@code AND}/{@code OR} combinators while remaining backward-compatible
 * with the simple historical syntax.</p>
 */
@Component
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final AgentProperties properties;
    private final RoutingRuleEvaluator evaluator;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public LlmRouter(AgentProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.evaluator = new RoutingRuleEvaluator();
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /** Test-only constructor that disables metrics emission. */
    public LlmRouter(AgentProperties properties) {
        this(properties, EmptyObjectProvider.instance());
    }

    /** Trace entry returned by {@link #evaluateAll(LlmRoutingContext)}. */
    public record RuleEvaluation(String name, int priority, boolean enabled, boolean matched, String targetModel) {}

    /** Outcome of a routing decision, including which rules were considered. */
    public record RoutingDecision(String selectedAlias, String matchedRule, List<RuleEvaluation> evaluatedRules) {}

    /**
     * Resolve the model alias for the given routing context.
     * Evaluates rules sorted by priority (lowest first). Returns primary alias if no rule matches.
     */
    public String resolve(LlmRoutingContext ctx) {
        return evaluateAll(ctx).selectedAlias();
    }

    /**
     * Same as {@link #resolve(LlmRoutingContext)} but also reports per-rule evaluation
     * outcomes (used by the simulate admin endpoint).
     */
    public RoutingDecision evaluateAll(LlmRoutingContext ctx) {
        var rules = properties.getLlm().getRoutingRules();
        List<RuleEvaluation> trace = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            String primary = properties.getLlm().getPrimaryAlias();
            return new RoutingDecision(primary, null, trace);
        }

        var sorted = rules.stream()
                .sorted(Comparator.comparingInt(AgentProperties.RoutingRule::getPriority))
                .toList();

        String matchedRule = null;
        String selected = null;
        for (AgentProperties.RoutingRule rule : sorted) {
            boolean enabled = rule.isEnabled();
            boolean matched = enabled && evaluator.matches(rule.getCondition(), ctx);
            trace.add(new RuleEvaluation(rule.getName(), rule.getPriority(), enabled, matched, rule.getTargetModel()));
            if (selected == null && matched) {
                matchedRule = rule.getName();
                selected = rule.getTargetModel();
                recordRuleMatch(rule.getName(), rule.getTargetModel());
                log.debug("LLM routing rule matched: '{}' -> model '{}'", rule.getName(), rule.getTargetModel());
                // Don't break — finish trace so simulate can report later rules too
            }
        }

        if (selected == null) {
            selected = properties.getLlm().getPrimaryAlias();
            log.debug("No LLM routing rule matched, using primary: '{}'", selected);
        }
        recordModelSelection(selected);
        return new RoutingDecision(selected, matchedRule, trace);
    }

    /**
     * Look up a single routing rule by name (used by admin endpoints).
     */
    public Optional<AgentProperties.RoutingRule> findRule(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return properties.getLlm().getRoutingRules().stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst();
    }

    /**
     * Backward-compatible wrapper kept for existing tests. Delegates to the
     * evaluator with the rule's condition map.
     */
    boolean matchesRule(AgentProperties.RoutingRule rule, LlmRoutingContext ctx) {
        return evaluator.matches(rule.getCondition(), ctx);
    }

    private void recordRuleMatch(String ruleName, String model) {
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            meters.counter("agent.llm.routing.rule.matched",
                    "rule_name", safe(ruleName), "model", safe(model)).increment();
        }
    }

    private void recordModelSelection(String model) {
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            meters.counter("agent.llm.routing.model.selected", "model", safe(model)).increment();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
