package ai.gargantua.core.flow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowDefinitionTest {

    @Test
    void shouldBuildWithSteps() {
        var flow = new FlowDefinition("test-flow", "A test flow");
        flow.step("planner").step("coder").step("reviewer");

        assertEquals("test-flow", flow.name());
        assertEquals("A test flow", flow.description());
        assertEquals(3, flow.steps().size());
        assertEquals("planner", flow.steps().get(0).skillName());
        assertEquals("coder", flow.steps().get(1).skillName());
        assertEquals("reviewer", flow.steps().get(2).skillName());
    }

    @Test
    void shouldSupportInstructions() {
        var flow = new FlowDefinition("instructed", "With instructions");
        flow.step("analyzer", "Analyze the code for bugs");

        assertEquals(1, flow.steps().size());
        assertEquals("analyzer", flow.steps().get(0).skillName());
        assertEquals("Analyze the code for bugs", flow.steps().get(0).instruction());
    }

    @Test
    void shouldReturnImmutableStepsList() {
        var flow = new FlowDefinition("immutable", "Test");
        flow.step("a").step("b");

        assertThrows(UnsupportedOperationException.class, () -> flow.steps().add(
                new FlowDefinition.FlowStep("c", null)));
    }

    @Test
    void shouldSupportFluentChaining() {
        var flow = new FlowDefinition("chain", "Chained")
                .step("a").step("b", "Do B").step("c");

        assertEquals(3, flow.steps().size());
        assertNull(flow.steps().get(0).instruction());
        assertEquals("Do B", flow.steps().get(1).instruction());
    }
}
