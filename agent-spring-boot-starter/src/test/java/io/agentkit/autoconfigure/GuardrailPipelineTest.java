package io.agentkit.autoconfigure;

import io.agentkit.core.guardrail.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuardrailPipelineTest {

    private AgentProperties defaultProperties() {
        return new AgentProperties();
    }

    private InputGuardrail passingGuardrail(String name) {
        return new InputGuardrail() {
            @Override public String name() { return name; }
            @Override public boolean isEnabled(Object props) { return true; }
            @Override public GuardrailResult check(GuardrailInputContext ctx) {
                return GuardrailResult.pass(name);
            }
        };
    }

    private InputGuardrail blockingGuardrail(String name) {
        return new InputGuardrail() {
            @Override public String name() { return name; }
            @Override public boolean isEnabled(Object props) { return true; }
            @Override public GuardrailResult check(GuardrailInputContext ctx) {
                return GuardrailResult.block(name, "Blocked by " + name);
            }
        };
    }

    private InputGuardrail disabledGuardrail(String name) {
        return new InputGuardrail() {
            @Override public String name() { return name; }
            @Override public boolean isEnabled(Object props) { return false; }
            @Override public GuardrailResult check(GuardrailInputContext ctx) {
                return GuardrailResult.block(name, "Should not be called");
            }
        };
    }

    @Test
    void checkInput_allPass() {
        GuardrailPipeline pipeline = new GuardrailPipeline(
                List.of(passingGuardrail("g1"), passingGuardrail("g2")),
                List.of(),
                defaultProperties()
        );

        GuardrailInputContext ctx = new GuardrailInputContext(
                "Hello", "user1", "session1", null, new java.util.HashMap<>());
        GuardrailPipelineResult result = pipeline.checkInput(ctx);

        assertFalse(result.blocked());
        assertEquals(2, result.results().size());
    }

    @Test
    void checkInput_stopsAtFirstBlock() {
        GuardrailPipeline pipeline = new GuardrailPipeline(
                List.of(passingGuardrail("g1"), blockingGuardrail("g2"), passingGuardrail("g3")),
                List.of(),
                defaultProperties()
        );

        GuardrailInputContext ctx = new GuardrailInputContext(
                "Hello", "user1", "session1", null, new java.util.HashMap<>());
        GuardrailPipelineResult result = pipeline.checkInput(ctx);

        assertTrue(result.blocked());
        assertEquals("g2", result.blockedBy());
        assertEquals(2, result.results().size()); // g1 pass + g2 block, g3 never reached
    }

    @Test
    void checkInput_skipsDisabledGuardrails() {
        GuardrailPipeline pipeline = new GuardrailPipeline(
                List.of(disabledGuardrail("disabled"), passingGuardrail("enabled")),
                List.of(),
                defaultProperties()
        );

        GuardrailInputContext ctx = new GuardrailInputContext(
                "Hello", "user1", "session1", null, new java.util.HashMap<>());
        GuardrailPipelineResult result = pipeline.checkInput(ctx);

        assertFalse(result.blocked());
        assertEquals(1, result.results().size());
        assertEquals("enabled", result.results().get(0).guardrailName());
    }

    @Test
    void processOutput_chainsTransformations() {
        OutputGuardrail appendGuardrail = new OutputGuardrail() {
            @Override public String name() { return "appender"; }
            @Override public boolean isEnabled(Object props) { return true; }
            @Override public GuardrailOutputResult process(GuardrailOutputContext ctx) {
                return new GuardrailOutputResult(GuardrailVerdict.PASS,
                        ctx.rawResponse() + " [appended]", null, name());
            }
        };

        GuardrailPipeline pipeline = new GuardrailPipeline(
                List.of(),
                List.of(appendGuardrail),
                defaultProperties()
        );

        GuardrailOutputContext ctx = new GuardrailOutputContext(
                "Original", "user1", "session1", null, Map.of());
        String result = pipeline.processOutput(ctx);

        assertEquals("Original [appended]", result);
    }
}
