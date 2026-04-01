package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.DisclaimerInjectorGuardrail;
import ai.gargantua.core.guardrail.GuardrailOutputContext;
import ai.gargantua.core.guardrail.GuardrailOutputResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DisclaimerInjectorGuardrail")
class DisclaimerInjectorGuardrailTest {

    private AgentProperties propsWithDisclaimer(boolean enabled, String text, List<String> domains) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getOutput().setDisclaimerEnabled(enabled);
        props.getGuardrail().getOutput().setDisclaimerText(text);
        props.getGuardrail().getOutput().setDisclaimerDomains(domains);
        return props;
    }

    private GuardrailOutputContext ctx(String response) {
        return new GuardrailOutputContext(response, "user1", "session1", null, null);
    }

    private GuardrailOutputContext ctxWithSkill(String response, String domain) {
        SkillMeta skill = new SkillMeta("test-skill", "desc", "1.0.0", true, false, domain,
                SkillSource.FILESYSTEM, Set.of());
        return new GuardrailOutputContext(response, "user1", "session1", skill, null);
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'disclaimer-injector'")
    void name_returnsDisclaimerInjector() {
        var guardrail = new DisclaimerInjectorGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("disclaimer-injector");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when disclaimer is enabled")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithDisclaimer(true, "Disclaimer", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when disclaimer is disabled")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithDisclaimer(false, "Disclaimer", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() falls back to injected props for non-AgentProperties argument")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithDisclaimer(true, "Disclaimer", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);
        assertThat(guardrail.isEnabled("other")).isTrue();
    }

    // --- process() — no domain restriction ---

    @Test
    @DisplayName("process() appends disclaimer when no domains are configured")
    void process_appendsDisclaimerWhenNoDomainsConfigured() {
        AgentProperties props = propsWithDisclaimer(true, "AI generated content.", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx("Here is your answer."));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).contains("Here is your answer.");
        assertThat(result.processedResponse()).contains("AI generated content.");
        assertThat(result.processedResponse()).contains("---");
    }

    @Test
    @DisplayName("process() appends disclaimer with null response (treats as empty string)")
    void process_handlesNullResponse() {
        AgentProperties props = propsWithDisclaimer(true, "Disclaimer.", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx(null));
        assertThat(result.processedResponse()).contains("Disclaimer.");
    }

    // --- process() — domain-scoped disclaimer ---

    @Test
    @DisplayName("process() appends disclaimer when skill domain matches configured domains")
    void process_appendsDisclaimerForMatchingDomain() {
        AgentProperties props = propsWithDisclaimer(true, "Not medical advice.", List.of("health", "fitness"));
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctxWithSkill("Workout plan", "health"));
        assertThat(result.processedResponse()).contains("Not medical advice.");
    }

    @Test
    @DisplayName("process() skips disclaimer when skill domain does not match configured domains")
    void process_skipsDisclaimerForNonMatchingDomain() {
        AgentProperties props = propsWithDisclaimer(true, "Not medical advice.", List.of("health"));
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctxWithSkill("Financial report", "finance"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
        assertThat(result.processedResponse()).isEqualTo("Financial report");
        assertThat(result.processedResponse()).doesNotContain("Not medical advice.");
    }

    @Test
    @DisplayName("process() skips disclaimer when activated skill is null and domains are configured")
    void process_skipsDisclaimerWhenSkillIsNullAndDomainsConfigured() {
        AgentProperties props = propsWithDisclaimer(true, "Disclaimer.", List.of("health"));
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx("Some response"));
        // activatedSkill is null, domain will be "" which is not in ["health"]
        assertThat(result.processedResponse()).isEqualTo("Some response");
    }

    @Test
    @DisplayName("process() appends disclaimer when domains list is null")
    void process_appendsDisclaimerWhenDomainsListIsNull() {
        AgentProperties props = propsWithDisclaimer(true, "AI disclaimer.", null);
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx("Answer text"));
        assertThat(result.processedResponse()).contains("AI disclaimer.");
    }

    @Test
    @DisplayName("process() result guardrail name is 'disclaimer-injector'")
    void process_resultHasCorrectGuardrailName() {
        AgentProperties props = propsWithDisclaimer(true, "Disclaimer.", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx("Text"));
        assertThat(result.guardrailName()).isEqualTo("disclaimer-injector");
    }

    @Test
    @DisplayName("process() disclaimer format includes separator")
    void process_disclaimerFormatIncludesSeparator() {
        AgentProperties props = propsWithDisclaimer(true, "Legal notice here.", List.of());
        var guardrail = new DisclaimerInjectorGuardrail(props);

        GuardrailOutputResult result = guardrail.process(ctx("Response body"));
        String expected = "Response body\n\n---\nLegal notice here.";
        assertThat(result.processedResponse()).isEqualTo(expected);
    }
}
