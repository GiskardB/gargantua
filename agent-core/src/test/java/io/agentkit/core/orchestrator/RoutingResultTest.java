package io.agentkit.core.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutingResultTest {

    @Test
    void semanticFactoryMethod() {
        RoutingResult result = RoutingResult.semantic("skill", 0.95);

        assertEquals("skill", result.skillName());
        assertEquals(RoutingMethod.SEMANTIC, result.method());
        assertEquals(0.95, result.confidence(), 0.001);
    }

    @Test
    void llmFactoryMethod() {
        RoutingResult result = RoutingResult.llm("skill");

        assertEquals("skill", result.skillName());
        assertEquals(RoutingMethod.LLM, result.method());
    }

    @Test
    void forcedFactoryMethod() {
        RoutingResult result = RoutingResult.forced("skill");

        assertEquals("skill", result.skillName());
        assertEquals(RoutingMethod.FORCED, result.method());
        assertEquals(1.0, result.confidence(), 0.001);
    }
}
