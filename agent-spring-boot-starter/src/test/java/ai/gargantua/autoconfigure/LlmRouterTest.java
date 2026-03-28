package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmRouterTest {

    private LlmRoutingContext ctx(String skillName, String domain, int tokens, String userTier) {
        return new LlmRoutingContext(
                "user1", "session1", skillName, domain,
                "test message", 100, tokens, userTier,
                LocalTime.NOON, DayOfWeek.MONDAY, Map.of()
        );
    }

    @Test
    void returnsPrimaryWhenNoRules() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("default-model");
        LlmRouter router = new LlmRouter(props);

        String result = router.resolve(ctx("skill1", "general", 100, "free"));
        assertEquals("default-model", result);
    }

    @Test
    void matchesRuleByPriority() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("default-model");

        AgentProperties.RoutingRule lowPriority = new AgentProperties.RoutingRule();
        lowPriority.setName("low");
        lowPriority.setPriority(100);
        lowPriority.setCondition(Map.of("domain", "general"));
        lowPriority.setTargetModel("gpt-3.5");

        AgentProperties.RoutingRule highPriority = new AgentProperties.RoutingRule();
        highPriority.setName("high");
        highPriority.setPriority(10);
        highPriority.setCondition(Map.of("domain", "general"));
        highPriority.setTargetModel("gpt-4o");

        props.getLlm().setRoutingRules(List.of(lowPriority, highPriority));
        LlmRouter router = new LlmRouter(props);

        String result = router.resolve(ctx("skill1", "general", 100, "free"));
        assertEquals("gpt-4o", result);
    }

    @Test
    void returnsPrimaryWhenNoRuleMatches() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("default-model");

        AgentProperties.RoutingRule rule = new AgentProperties.RoutingRule();
        rule.setName("specific");
        rule.setPriority(10);
        rule.setCondition(Map.of("domain", "medical"));
        rule.setTargetModel("med-model");

        props.getLlm().setRoutingRules(List.of(rule));
        LlmRouter router = new LlmRouter(props);

        String result = router.resolve(ctx("skill1", "general", 100, "free"));
        assertEquals("default-model", result);
    }

    @Test
    void skipsDisabledRules() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("default-model");

        AgentProperties.RoutingRule disabled = new AgentProperties.RoutingRule();
        disabled.setName("disabled-rule");
        disabled.setPriority(1);
        disabled.setEnabled(false);
        disabled.setCondition(Map.of("domain", "general"));
        disabled.setTargetModel("disabled-model");

        props.getLlm().setRoutingRules(List.of(disabled));
        LlmRouter router = new LlmRouter(props);

        String result = router.resolve(ctx("skill1", "general", 100, "free"));
        assertEquals("default-model", result);
    }

    @Test
    void matchesByMinTokens() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("small-model");

        AgentProperties.RoutingRule rule = new AgentProperties.RoutingRule();
        rule.setName("large-context");
        rule.setPriority(10);
        rule.setCondition(Map.of("minTokens", 500));
        rule.setTargetModel("large-model");

        props.getLlm().setRoutingRules(List.of(rule));
        LlmRouter router = new LlmRouter(props);

        // Below threshold
        assertEquals("small-model", router.resolve(ctx("skill1", "general", 100, "free")));
        // Above threshold
        assertEquals("large-model", router.resolve(ctx("skill1", "general", 1000, "free")));
    }

    @Test
    void matchesByUserTier() {
        AgentProperties props = new AgentProperties();
        props.getLlm().setPrimaryAlias("basic-model");

        AgentProperties.RoutingRule rule = new AgentProperties.RoutingRule();
        rule.setName("premium");
        rule.setPriority(10);
        rule.setCondition(Map.of("userTier", "premium"));
        rule.setTargetModel("premium-model");

        props.getLlm().setRoutingRules(List.of(rule));
        LlmRouter router = new LlmRouter(props);

        assertEquals("basic-model", router.resolve(ctx("skill1", "general", 100, "free")));
        assertEquals("premium-model", router.resolve(ctx("skill1", "general", 100, "premium")));
    }
}
