package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.MaxLengthGuardrail;
import ai.gargantua.core.guardrail.GuardrailInputContext;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.guardrail.GuardrailVerdict;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class MaxLengthGuardrailTest {

    private AgentProperties propsWithMaxLength(int maxLength) {
        AgentProperties props = new AgentProperties();
        props.getGuardrail().getInput().setMaxLengthChars(maxLength);
        props.getGuardrail().getInput().setMaxLengthEnabled(true);
        return props;
    }

    @Test
    void passesWhenUnderLimit() {
        AgentProperties props = propsWithMaxLength(100);
        MaxLengthGuardrail guardrail = new MaxLengthGuardrail(props);

        GuardrailInputContext ctx = new GuardrailInputContext(
                "Short message", "user1", "session1", null, new HashMap<>());
        GuardrailResult result = guardrail.check(ctx);

        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    void blocksWhenOverLimit() {
        AgentProperties props = propsWithMaxLength(10);
        MaxLengthGuardrail guardrail = new MaxLengthGuardrail(props);

        GuardrailInputContext ctx = new GuardrailInputContext(
                "This message is definitely longer than 10 characters", "user1", "session1", null, new HashMap<>());
        GuardrailResult result = guardrail.check(ctx);

        assertEquals(GuardrailVerdict.BLOCK, result.verdict());
        assertTrue(result.reason().contains("10"));
    }

    @Test
    void passesExactlyAtLimit() {
        AgentProperties props = propsWithMaxLength(5);
        MaxLengthGuardrail guardrail = new MaxLengthGuardrail(props);

        GuardrailInputContext ctx = new GuardrailInputContext(
                "Hello", "user1", "session1", null, new HashMap<>());
        GuardrailResult result = guardrail.check(ctx);

        assertEquals(GuardrailVerdict.PASS, result.verdict());
    }

    @Test
    void isEnabledReflectsConfig() {
        AgentProperties enabled = propsWithMaxLength(100);
        MaxLengthGuardrail guardrail = new MaxLengthGuardrail(enabled);
        assertTrue(guardrail.isEnabled(enabled));

        AgentProperties disabled = new AgentProperties();
        disabled.getGuardrail().getInput().setMaxLengthEnabled(false);
        assertFalse(guardrail.isEnabled(disabled));
    }
}
