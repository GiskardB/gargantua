package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import ai.gargantua.autoconfigure.GuardrailPipeline;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.InputGuardrail;
import ai.gargantua.core.guardrail.OutputGuardrail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GuardrailAdminController")
class GuardrailAdminControllerTest {

    private GuardrailAdminController controller;

    private final InputGuardrail inputGuardrail1 = new InputGuardrail() {
        @Override public String name() { return "max-length"; }
        @Override public boolean isEnabled(Object props) { return true; }
        @Override public GuardrailResult check(GuardrailInputContext ctx) { return null; }
    };

    private final InputGuardrail inputGuardrail2 = new InputGuardrail() {
        @Override public String name() { return "prompt-injection"; }
        @Override public boolean isEnabled(Object props) { return true; }
        @Override public GuardrailResult check(GuardrailInputContext ctx) { return null; }
    };

    private final OutputGuardrail outputGuardrail1 = new OutputGuardrail() {
        @Override public String name() { return "pii-redaction"; }
        @Override public boolean isEnabled(Object props) { return true; }
        @Override public GuardrailOutputResult process(GuardrailOutputContext ctx) { return null; }
    };

    @BeforeEach
    void setUp() {
        var inputs = List.of(inputGuardrail1, inputGuardrail2);
        var outputs = List.<OutputGuardrail>of(outputGuardrail1);
        var pipeline = new GuardrailPipeline(inputs, outputs, new AgentProperties());
        controller = new GuardrailAdminController(inputs, outputs, pipeline);
    }

    @Nested
    @DisplayName("listGuardrails")
    class ListGuardrails {

        @Test
        @DisplayName("returns 200 with input and output guardrails")
        void returnsInputAndOutputGuardrails() {
            var response = controller.listGuardrails();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            var body = response.getBody();
            assertThat(body).containsKeys("inputGuardrails", "outputGuardrails");
        }

        @Test
        @DisplayName("lists correct number of input guardrails")
        @SuppressWarnings("unchecked")
        void listsCorrectInputCount() {
            var body = controller.listGuardrails().getBody();
            var inputList = (List<Map<String, Object>>) body.get("inputGuardrails");

            assertThat(inputList).hasSize(2);
        }

        @Test
        @DisplayName("lists correct number of output guardrails")
        @SuppressWarnings("unchecked")
        void listsCorrectOutputCount() {
            var body = controller.listGuardrails().getBody();
            var outputList = (List<Map<String, Object>>) body.get("outputGuardrails");

            assertThat(outputList).hasSize(1);
        }

        @Test
        @DisplayName("all guardrails enabled by default")
        @SuppressWarnings("unchecked")
        void allEnabledByDefault() {
            var body = controller.listGuardrails().getBody();
            var inputList = (List<Map<String, Object>>) body.get("inputGuardrails");
            var outputList = (List<Map<String, Object>>) body.get("outputGuardrails");

            assertThat(inputList).allSatisfy(entry ->
                    assertThat(entry.get("enabled")).isEqualTo(true));
            assertThat(outputList).allSatisfy(entry ->
                    assertThat(entry.get("enabled")).isEqualTo(true));
        }

        @Test
        @DisplayName("guardrail entries contain name, type, and enabled fields")
        @SuppressWarnings("unchecked")
        void entriesContainExpectedFields() {
            var body = controller.listGuardrails().getBody();
            var inputList = (List<Map<String, Object>>) body.get("inputGuardrails");

            Map<String, Object> first = inputList.get(0);
            assertThat(first).containsKeys("name", "type", "enabled");
            assertThat(first.get("type")).isEqualTo("INPUT");
        }

        @Test
        @DisplayName("output guardrails have OUTPUT type")
        @SuppressWarnings("unchecked")
        void outputGuardrailsHaveOutputType() {
            var body = controller.listGuardrails().getBody();
            var outputList = (List<Map<String, Object>>) body.get("outputGuardrails");

            assertThat(outputList.get(0).get("type")).isEqualTo("OUTPUT");
        }
    }

    @Nested
    @DisplayName("toggleGuardrail")
    class ToggleGuardrail {

        @Test
        @DisplayName("disables an enabled guardrail")
        void disablesEnabledGuardrail() {
            var response = controller.toggleGuardrail("max-length");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get("name")).isEqualTo("max-length");
            assertThat(response.getBody().get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("re-enables a disabled guardrail")
        void reEnablesDisabledGuardrail() {
            // First toggle disables
            controller.toggleGuardrail("max-length");
            // Second toggle re-enables
            var response = controller.toggleGuardrail("max-length");

            assertThat(response.getBody().get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("toggled guardrail shows disabled in list")
        @SuppressWarnings("unchecked")
        void toggledGuardrailReflectedInList() {
            controller.toggleGuardrail("max-length");

            var body = controller.listGuardrails().getBody();
            var inputList = (List<Map<String, Object>>) body.get("inputGuardrails");

            Map<String, Object> maxLength = inputList.stream()
                    .filter(e -> "max-length".equals(e.get("name")))
                    .findFirst()
                    .orElseThrow();

            assertThat(maxLength.get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("toggling unknown guardrail name succeeds")
        void togglingUnknownNameSucceeds() {
            var response = controller.toggleGuardrail("unknown-guardrail");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().get("name")).isEqualTo("unknown-guardrail");
            assertThat(response.getBody().get("enabled")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("empty guardrails")
    class EmptyGuardrails {

        @Test
        @DisplayName("handles empty input and output guardrail lists")
        void handlesEmptyLists() {
            var emptyPipeline = new GuardrailPipeline(List.of(), List.of(), new AgentProperties());
            var emptyController = new GuardrailAdminController(List.of(), List.of(), emptyPipeline);
            var response = emptyController.listGuardrails();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            var inputList = (List<?>) response.getBody().get("inputGuardrails");
            @SuppressWarnings("unchecked")
            var outputList = (List<?>) response.getBody().get("outputGuardrails");
            assertThat(inputList).isEmpty();
            assertThat(outputList).isEmpty();
        }
    }
}
