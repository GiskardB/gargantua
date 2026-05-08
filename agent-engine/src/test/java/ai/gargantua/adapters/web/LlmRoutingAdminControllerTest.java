package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.LlmProviderFactory;
import ai.gargantua.autoconfigure.LlmRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmRoutingAdminController")
class LlmRoutingAdminControllerTest {

    private AgentProperties properties;
    private LlmRoutingAdminController controller;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.getLlm().setPrimaryAlias("primary");
        properties.getLlm().getPrimary().setProvider("openai");
        properties.getLlm().getPrimary().setModel("gpt-4o");

        properties.getLlm().setRoutingRules(List.of(
                buildRule("domain-specialization", 10, true, "claude-sonnet",
                        Map.of("domain", "medical")),
                buildRule("cost-optimization", 20, true, "gpt-4o-mini",
                        Map.of("min-tokens", 0)),
                buildRule("premium-tier", 30, true, "gpt-4o",
                        Map.of("user-tier", "premium"))
        ));

        LlmRouter router = new LlmRouter(properties);
        LlmProviderFactory factory = new LlmProviderFactory(properties, router);
        controller = new LlmRoutingAdminController(properties, router, factory);
    }

    private AgentProperties.RoutingRule buildRule(String name, int priority, boolean enabled,
                                                  String targetModel, Map<String, Object> condition) {
        var rule = new AgentProperties.RoutingRule();
        rule.setName(name);
        rule.setPriority(priority);
        rule.setEnabled(enabled);
        rule.setTargetModel(targetModel);
        rule.setCondition(condition);
        return rule;
    }

    @Nested
    @DisplayName("listRules")
    class ListRules {

        @Test
        @DisplayName("returns 200 with the configured routing rules")
        void returns200WithConfiguredRules() {
            var response = controller.listRules();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(3);
        }

        @Test
        @DisplayName("rules contain expected names")
        void containExpectedNames() {
            var rules = controller.listRules().getBody();

            List<String> names = rules.stream().map(r -> (String) r.get("name")).toList();
            assertThat(names).containsExactlyInAnyOrder(
                    "cost-optimization", "domain-specialization", "premium-tier");
        }

        @Test
        @DisplayName("rules carry name, priority, enabled, targetModel and condition")
        void containExpectedFields() {
            var rules = controller.listRules().getBody();
            assertThat(rules).allSatisfy(rule ->
                    assertThat(rule).containsKeys("name", "priority", "enabled",
                            "targetModel", "condition", "description"));
        }
    }

    @Nested
    @DisplayName("toggleRule")
    class ToggleRule {

        @Test
        @DisplayName("flips enabled flag for an existing rule")
        void flipsEnabledFlag() {
            var response = controller.toggleRule("cost-optimization");
            var costRule = properties.getLlm().getRoutingRules().stream()
                    .filter(r -> "cost-optimization".equals(r.getName()))
                    .findFirst().orElseThrow();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get("enabled")).isEqualTo(false);
            assertThat(costRule.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("re-toggling restores the original state")
        void reToggling() {
            controller.toggleRule("cost-optimization");
            controller.toggleRule("cost-optimization");
            var costRule = properties.getLlm().getRoutingRules().stream()
                    .filter(r -> "cost-optimization".equals(r.getName()))
                    .findFirst().orElseThrow();
            assertThat(costRule.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("returns 404 for an unknown rule name")
        void unknownRule() {
            var response = controller.toggleRule("does-not-exist");
            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody().get("error")).asString()
                    .contains("does-not-exist");
        }
    }

    @Nested
    @DisplayName("simulate")
    class Simulate {

        @Test
        @DisplayName("returns the alias selected by the live evaluator")
        void selectsAliasFromLiveEvaluator() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "What treatment is recommended?", "med-skill", "medical",
                    "user-1", "free", null, null);

            var body = controller.simulate(request).getBody();

            assertThat(body.get("selectedAlias")).isEqualTo("claude-sonnet");
            assertThat(body.get("matchedRule")).isEqualTo("domain-specialization");
        }

        @Test
        @DisplayName("falls back to primary alias when no rule matches")
        void fallsBackToPrimary() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "Hi", "chitchat-skill", "general", "user-1", "free", null, null);

            var body = controller.simulate(request).getBody();

            assertThat(body.get("selectedAlias")).isEqualTo("gpt-4o-mini"); // cost-optimization wins on min-tokens
            assertThat(body.get("matchedRule")).isEqualTo("cost-optimization");
        }

        @Test
        @DisplayName("includes evaluatedRules trace ordered by priority with per-rule matched flags")
        void evaluatedRulesTrace() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "premium request", "any-skill", "general", "user-1",
                    "premium", 50, null);

            var body = controller.simulate(request).getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trace = (List<Map<String, Object>>) body.get("evaluatedRules");

            assertThat(trace).hasSize(3);
            assertThat(trace).extracting(m -> m.get("name")).containsExactly(
                    "domain-specialization", "cost-optimization", "premium-tier");
            assertThat(trace.get(0).get("matched")).isEqualTo(false); // general != medical
            assertThat(trace.get(1).get("matched")).isEqualTo(true);  // first match — cost-optimization wins
            assertThat(trace.get(2).get("matched")).isEqualTo(true);  // premium-tier also matches but does not win
            assertThat(body.get("matchedRule")).isEqualTo("cost-optimization");
            assertThat(body.get("selectedAlias")).isEqualTo("gpt-4o-mini");
        }

        @Test
        @DisplayName("includes selectedProvider and selectedModel from LlmProviderFactory")
        void includesProviderAndModel() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "test", "skill", "general", "u1", "free", 10, null);

            var body = controller.simulate(request).getBody();

            assertThat(body).containsKey("selectedProvider");
            assertThat(body).containsKey("selectedModel");
        }

        @Test
        @DisplayName("3-arg compatibility constructor still works")
        void compatibilityCtor() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "twelve chars", "skill", "user-1");

            var body = controller.simulate(request).getBody();

            assertThat(body.get("inputLengthChars")).isEqualTo(12);
            assertThat(body.get("skillName")).isEqualTo("skill");
        }
    }
}
