package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.PiiOutputGuardrail;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiOutputGuardrail")
class PiiOutputGuardrailTest {

    private AgentProperties propsWithPiiOutput(boolean enabled) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getOutput().setPiiMaskingEnabled(enabled);
        return props;
    }

    private GuardrailOutputContext ctx(String response) {
        return new GuardrailOutputContext(response, "user1", "session1", null, null);
    }

    private GuardrailOutputContext ctxWithAttributes(String response, Map<String, Object> attrs) {
        return new GuardrailOutputContext(response, "user1", "session1", null, attrs);
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'pii-output-masking'")
    void name_returnsPiiOutputMasking() {
        var guardrail = new PiiOutputGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("pii-output-masking");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when PII output masking is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithPiiOutput(true);
        var guardrail = new PiiOutputGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when PII output masking is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithPiiOutput(false);
        var guardrail = new PiiOutputGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props when argument is not AgentProperties")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithPiiOutput(true);
        var guardrail = new PiiOutputGuardrail(props);
        assertThat(guardrail.isEnabled("not-props")).isTrue();
    }

    // --- process() — null/blank responses ---

    @Test
    @DisplayName("process() passes through null response unchanged")
    void process_passesNullResponse() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx(null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isNull();
    }

    @Test
    @DisplayName("process() passes through blank response unchanged")
    void process_passesBlankResponse() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("   "));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isEqualTo("   ");
    }

    // --- process() — email masking ---

    @Test
    @DisplayName("process() redacts email addresses")
    void process_redactsEmails() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Contact us at john.doe@example.com for more info."));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).contains("[EMAIL_REDACTED]");
        assertThat(result.processedResponse()).doesNotContain("john.doe@example.com");
    }

    @Test
    @DisplayName("process() redacts multiple emails")
    void process_redactsMultipleEmails() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Email a@b.com or c@d.org"));
        assertThat(result.processedResponse()).doesNotContain("a@b.com");
        assertThat(result.processedResponse()).doesNotContain("c@d.org");
    }

    // --- process() — IBAN masking ---

    @Test
    @DisplayName("process() redacts IBAN numbers")
    void process_redactsIbans() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Your IBAN is IT60X0542811101000000123456"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).contains("[IBAN_REDACTED]");
        assertThat(result.processedResponse()).doesNotContain("IT60X0542811101000000123456");
    }

    // --- process() — phone masking ---

    @Test
    @DisplayName("process() redacts phone numbers")
    void process_redactsPhoneNumbers() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Call me at +39 02 1234 5678"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).contains("[PHONE_REDACTED]");
    }

    @Test
    @DisplayName("process() redacts phone numbers without country code")
    void process_redactsPhoneWithoutCountryCode() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Call 02-1234-5678 for details"));
        assertThat(result.processedResponse()).contains("[PHONE_REDACTED]");
    }

    // --- process() — multiple PII types ---

    @Test
    @DisplayName("process() redacts all PII types in a single response")
    void process_redactsAllPiiTypes() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        String response = "Email: test@example.com, IBAN: DE89370400440532013000, Phone: +49 170 1234567";
        GuardrailOutputResult result = guardrail.process(ctx(response));

        assertThat(result.processedResponse()).contains("[EMAIL_REDACTED]");
        assertThat(result.processedResponse()).contains("[IBAN_REDACTED]");
        assertThat(result.processedResponse()).contains("[PHONE_REDACTED]");
        assertThat(result.processedResponse()).doesNotContain("test@example.com");
    }

    // --- process() — no PII ---

    @Test
    @DisplayName("process() leaves clean text unchanged")
    void process_leavesCleanTextUnchanged() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        String clean = "Here is your workout plan for today.";
        GuardrailOutputResult result = guardrail.process(ctx(clean));
        assertThat(result.processedResponse()).isEqualTo(clean);
    }

    // --- process() — with input attributes (pii_map) ---

    @Test
    @DisplayName("process() handles input attributes with pii_map without error")
    void process_handlesPiiMapInAttributes() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("pii_map", Map.of("[EMAIL_1]", "real@email.com"));

        GuardrailOutputResult result = guardrail.process(ctxWithAttributes("No PII here", attrs));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("process() handles null input attributes gracefully")
    void process_handlesNullInputAttributes() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Some text with test@example.com"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).contains("[EMAIL_REDACTED]");
    }

    @Test
    @DisplayName("process() guardrail name is 'pii-output-masking'")
    void process_resultHasCorrectGuardrailName() {
        var guardrail = new PiiOutputGuardrail(propsWithPiiOutput(true));

        GuardrailOutputResult result = guardrail.process(ctx("Hello"));
        assertThat(result.guardrailName()).isEqualTo("pii-output-masking");
    }
}
