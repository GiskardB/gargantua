package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.ScopeValidatorGuardrail;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScopeValidatorGuardrail")
class ScopeValidatorGuardrailTest {

    private AgentProperties propsWithScopeValidation(boolean enabled) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getOutput().setScopeValidationEnabled(enabled);
        return props;
    }

    private GuardrailOutputContext ctx(String response) {
        return new GuardrailOutputContext(response, "user1", "session1", null, null);
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'scope-validator'")
    void name_returnsScopeValidator() {
        var guardrail = new ScopeValidatorGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("scope-validator");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when scope validation is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithScopeValidation(true);
        var guardrail = new ScopeValidatorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when scope validation is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithScopeValidation(false);
        var guardrail = new ScopeValidatorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() returns false by default")
    void isEnabled_falseByDefault() {
        var guardrail = new ScopeValidatorGuardrail(new AgentProperties());
        assertThat(guardrail.isEnabled(new AgentProperties())).isFalse();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props for non-AgentProperties argument")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithScopeValidation(true);
        var guardrail = new ScopeValidatorGuardrail(props);
        assertThat(guardrail.isEnabled(42)).isTrue();
    }

    // --- process() (placeholder — always PASS) ---

    @Test
    @DisplayName("process() always returns PASS with original response (placeholder)")
    void process_alwaysPassesThroughResponse() {
        var guardrail = new ScopeValidatorGuardrail(propsWithScopeValidation(true));

        GuardrailOutputResult result = guardrail.process(ctx("Some response"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isEqualTo("Some response");
        assertThat(result.guardrailName()).isEqualTo("scope-validator");
    }

    @Test
    @DisplayName("process() passes through null response")
    void process_passesNullResponse() {
        var guardrail = new ScopeValidatorGuardrail(propsWithScopeValidation(true));

        GuardrailOutputResult result = guardrail.process(ctx(null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isNull();
    }

    @Test
    @DisplayName("process() passes through empty response")
    void process_passesEmptyResponse() {
        var guardrail = new ScopeValidatorGuardrail(propsWithScopeValidation(true));

        GuardrailOutputResult result = guardrail.process(ctx(""));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isEmpty();
    }
}
