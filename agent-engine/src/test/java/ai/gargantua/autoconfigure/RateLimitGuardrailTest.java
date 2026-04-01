package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.RateLimitGuardrail;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitGuardrail")
class RateLimitGuardrailTest {

    private AgentProperties propsWithRateLimit(boolean enabled) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setRateLimitEnabled(enabled);
        return props;
    }

    private GuardrailInputContext ctx(String message) {
        return new GuardrailInputContext(message, "user1", "session1", null, new HashMap<>());
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'rate-limit'")
    void name_returnsRateLimit() {
        var guardrail = new RateLimitGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("rate-limit");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when rate limit is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithRateLimit(true);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when rate limit is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithRateLimit(false);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props when argument is not AgentProperties")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithRateLimit(true);
        var guardrail = new RateLimitGuardrail(props);
        assertThat(guardrail.isEnabled("something-else")).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false by default")
    void isEnabled_falseByDefault() {
        var guardrail = new RateLimitGuardrail(new AgentProperties());
        assertThat(guardrail.isEnabled(new AgentProperties())).isFalse();
    }

    // --- check() ---

    @Test
    @DisplayName("check() always returns PASS (placeholder implementation)")
    void check_alwaysPasses() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx("Hello"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.guardrailName()).isEqualTo("rate-limit");
    }

    @Test
    @DisplayName("check() passes with null message")
    void check_passesWithNullMessage() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx(null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("check() passes with empty message")
    void check_passesWithEmptyMessage() {
        var guardrail = new RateLimitGuardrail(propsWithRateLimit(true));

        GuardrailResult result = guardrail.check(ctx(""));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }
}
