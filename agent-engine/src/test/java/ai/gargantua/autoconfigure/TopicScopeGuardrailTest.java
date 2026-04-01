package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.TopicScopeGuardrail;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TopicScopeGuardrail")
class TopicScopeGuardrailTest {

    private AgentProperties propsWithBlockedTopics(boolean enabled, List<String> topics) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setTopicScopeEnabled(enabled);
        props.getGuardrail().getInput().setBlockedTopics(topics);
        return props;
    }

    private GuardrailInputContext ctx(String message) {
        return new GuardrailInputContext(message, "user1", "session1", null, new HashMap<>());
    }

    // --- name() ---

    @Test
    @DisplayName("name() returns 'topic-scope'")
    void name_returnsTopic_scope() {
        var guardrail = new TopicScopeGuardrail(new AgentProperties());
        assertThat(guardrail.name()).isEqualTo("topic-scope");
    }

    // --- isEnabled() ---

    @Test
    @DisplayName("isEnabled() returns true when topic scope is enabled in properties")
    void isEnabled_trueWhenEnabled() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics"));
        var guardrail = new TopicScopeGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when topic scope is disabled in properties")
    void isEnabled_falseWhenDisabled() {
        AgentProperties props = propsWithBlockedTopics(false, List.of());
        var guardrail = new TopicScopeGuardrail(props);
        assertThat(guardrail.isEnabled(props)).isFalse();
    }

    @Test
    @DisplayName("isEnabled() uses injected props when argument is not AgentProperties")
    void isEnabled_fallsBackToInjectedProps() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics"));
        var guardrail = new TopicScopeGuardrail(props);
        assertThat(guardrail.isEnabled("not-agent-properties")).isTrue();
    }

    // --- check() — PASS path ---

    @Test
    @DisplayName("check() passes when blocked topics list is null")
    void check_passesWhenBlockedTopicsNull() {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setBlockedTopics(null);
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("anything goes"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("check() passes when blocked topics list is empty")
    void check_passesWhenBlockedTopicsEmpty() {
        AgentProperties props = propsWithBlockedTopics(true, List.of());
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("anything goes"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("check() passes when message does not contain any blocked topic")
    void check_passesWhenNoBlockedTopicFound() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics", "gambling"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("Tell me about fitness routines"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    @Test
    @DisplayName("check() passes when user message is null")
    void check_passesWhenMessageNull() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx(null));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.PASS);
    }

    // --- check() — BLOCK path ---

    @Test
    @DisplayName("check() blocks when message contains a blocked topic (exact)")
    void check_blocksWhenBlockedTopicFound() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics", "gambling"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("What do you think about politics?"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(result.reason()).contains("politics");
    }

    @Test
    @DisplayName("check() blocks case-insensitively")
    void check_blocksCaseInsensitive() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("Gambling"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("I love GAMBLING on weekends"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(result.reason()).contains("Gambling");
    }

    @Test
    @DisplayName("check() blocks on first matching topic when multiple match")
    void check_blocksOnFirstMatch() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("politics", "gambling"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("politics and gambling are fun"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
        assertThat(result.reason()).contains("politics");
    }

    @Test
    @DisplayName("check() blocks when topic appears as substring in message")
    void check_blocksOnSubstringMatch() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("drug"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult result = guardrail.check(ctx("Tell me about drugstores"));
        assertThat(result.verdict()).isEqualTo(GuardrailVerdict.BLOCK);
    }

    @Test
    @DisplayName("check() result guardrail name is 'topic-scope'")
    void check_resultHasCorrectGuardrailName() {
        AgentProperties props = propsWithBlockedTopics(true, List.of("violence"));
        var guardrail = new TopicScopeGuardrail(props);

        GuardrailResult passResult = guardrail.check(ctx("Hello"));
        assertThat(passResult.guardrailName()).isEqualTo("topic-scope");

        GuardrailResult blockResult = guardrail.check(ctx("violence in movies"));
        assertThat(blockResult.guardrailName()).isEqualTo("topic-scope");
    }
}
