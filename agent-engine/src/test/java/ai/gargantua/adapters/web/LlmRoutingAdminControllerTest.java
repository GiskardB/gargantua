package ai.gargantua.adapters.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmRoutingAdminController")
class LlmRoutingAdminControllerTest {

    private LlmRoutingAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new LlmRoutingAdminController();
    }

    @Nested
    @DisplayName("listRules")
    class ListRules {

        @Test
        @DisplayName("returns 200 with default routing rules")
        void returns200WithDefaultRules() {
            var response = controller.listRules();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(3);
        }

        @Test
        @DisplayName("default rules contain expected names")
        void defaultRulesContainExpectedNames() {
            var rules = controller.listRules().getBody();

            List<String> names = rules.stream()
                    .map(r -> (String) r.get("name"))
                    .toList();

            assertThat(names).containsExactlyInAnyOrder(
                    "cost-optimization", "domain-specialization", "fallback");
        }

        @Test
        @DisplayName("all rules enabled by default")
        void allRulesEnabledByDefault() {
            var rules = controller.listRules().getBody();

            assertThat(rules).allSatisfy(rule ->
                    assertThat(rule.get("enabled")).isEqualTo(true));
        }

        @Test
        @DisplayName("rules contain name, description, priority, and enabled fields")
        void rulesContainExpectedFields() {
            var rules = controller.listRules().getBody();

            assertThat(rules).allSatisfy(rule ->
                    assertThat(rule).containsKeys("name", "description", "priority", "enabled"));
        }

        @Test
        @DisplayName("rules have correct priorities")
        void rulesHaveCorrectPriorities() {
            var rules = controller.listRules().getBody();

            Map<String, Object> costRule = rules.stream()
                    .filter(r -> "cost-optimization".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();
            assertThat(costRule.get("priority")).isEqualTo(10);

            Map<String, Object> fallbackRule = rules.stream()
                    .filter(r -> "fallback".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();
            assertThat(fallbackRule.get("priority")).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("toggleRule")
    class ToggleRule {

        @Test
        @DisplayName("disables an enabled rule")
        void disablesRule() {
            var response = controller.toggleRule("cost-optimization");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get("name")).isEqualTo("cost-optimization");
            assertThat(response.getBody().get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("re-enables a disabled rule")
        void reEnablesRule() {
            controller.toggleRule("cost-optimization");
            var response = controller.toggleRule("cost-optimization");

            assertThat(response.getBody().get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("toggled rule shows disabled in list")
        void toggledRuleReflectedInList() {
            controller.toggleRule("domain-specialization");

            var rules = controller.listRules().getBody();
            Map<String, Object> domainRule = rules.stream()
                    .filter(r -> "domain-specialization".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();

            assertThat(domainRule.get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("toggling one rule does not affect others")
        void toggleDoesNotAffectOthers() {
            controller.toggleRule("cost-optimization");

            var rules = controller.listRules().getBody();
            Map<String, Object> fallbackRule = rules.stream()
                    .filter(r -> "fallback".equals(r.get("name")))
                    .findFirst()
                    .orElseThrow();

            assertThat(fallbackRule.get("enabled")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("simulate")
    class Simulate {

        @Test
        @DisplayName("returns 200 with simulation result")
        void returns200WithSimulationResult() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "How do I lose weight?", "fitness-coach", "user-1");

            var response = controller.simulate(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKeys(
                    "selectedModel", "selectedProvider", "matchedRule",
                    "confidence", "skillName", "inputLengthChars");
        }

        @Test
        @DisplayName("simulation result includes skill name from request")
        void includesSkillName() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "test message", "my-skill", "user-1");

            var body = controller.simulate(request).getBody();

            assertThat(body.get("skillName")).isEqualTo("my-skill");
        }

        @Test
        @DisplayName("simulation calculates input length correctly")
        void calculatesInputLength() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "twelve chars", "skill", "user-1");

            var body = controller.simulate(request).getBody();

            assertThat(body.get("inputLengthChars")).isEqualTo(12);
        }

        @Test
        @DisplayName("simulation handles null message gracefully")
        void handlesNullMessage() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    null, "skill", "user-1");

            var body = controller.simulate(request).getBody();

            assertThat(body.get("inputLengthChars")).isEqualTo(0);
        }

        @Test
        @DisplayName("simulation returns confidence as double")
        void returnsConfidenceAsDouble() {
            var request = new LlmRoutingAdminController.SimulateRequest(
                    "test", "skill", "user-1");

            var body = controller.simulate(request).getBody();

            assertThat(body.get("confidence")).isInstanceOf(Double.class);
            assertThat((Double) body.get("confidence")).isBetween(0.0, 1.0);
        }
    }
}
