package ai.gargantua.core.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentResponse")
class AgentResponseTest {

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        AgentResponse resp = new AgentResponse(
                "Hello!", "sess1", "greeting", List.of("searchTool"),
                RoutingMethod.SEMANTIC, 0.95,
                100, 50, 150, 0.003, 250L, false
        );

        assertEquals("Hello!", resp.text());
        assertEquals("sess1", resp.sessionId());
        assertEquals("greeting", resp.skillUsed());
        assertEquals(List.of("searchTool"), resp.toolsCalled());
        assertEquals(RoutingMethod.SEMANTIC, resp.routingMethod());
        assertEquals(0.95, resp.routingConfidence(), 0.001);
        assertEquals(100, resp.inputTokens());
        assertEquals(50, resp.outputTokens());
        assertEquals(150, resp.totalTokens());
        assertEquals(0.003, resp.estimatedCostUsd(), 0.0001);
        assertEquals(250L, resp.durationMs());
        assertFalse(resp.dryRun());
    }

    @Test
    @DisplayName("dry-run response")
    void dryRunResponse() {
        AgentResponse resp = new AgentResponse(
                "[DRY RUN] response", "sess1", "skill", List.of(),
                RoutingMethod.FORCED, 1.0,
                0, 0, 0, 0.0, 10L, true
        );

        assertTrue(resp.dryRun());
        assertEquals(RoutingMethod.FORCED, resp.routingMethod());
        assertTrue(resp.toolsCalled().isEmpty());
    }

    @Test
    @DisplayName("LLM routing method with confidence 1.0")
    void llmRoutingMethod() {
        AgentResponse resp = new AgentResponse(
                "text", "s1", "skill", List.of(),
                RoutingMethod.LLM, 1.0,
                200, 100, 300, 0.01, 500L, false
        );

        assertEquals(RoutingMethod.LLM, resp.routingMethod());
        assertEquals(1.0, resp.routingConfidence(), 0.001);
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        AgentResponse a = new AgentResponse("hi", "s", "sk", List.of(), RoutingMethod.SEMANTIC, 0.9, 10, 5, 15, 0.0, 100L, false);
        AgentResponse b = new AgentResponse("hi", "s", "sk", List.of(), RoutingMethod.SEMANTIC, 0.9, 10, 5, 15, 0.0, 100L, false);
        AgentResponse c = new AgentResponse("bye", "s", "sk", List.of(), RoutingMethod.SEMANTIC, 0.9, 10, 5, 15, 0.0, 100L, false);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("allows empty tools list and null text")
    void edgeCases() {
        AgentResponse resp = new AgentResponse(null, null, null, List.of(), null, 0.0, 0, 0, 0, 0.0, 0L, false);
        assertNull(resp.text());
        assertNull(resp.sessionId());
        assertTrue(resp.toolsCalled().isEmpty());
    }

    @Test
    @DisplayName("multiple tools called are preserved in order")
    void multipleTools() {
        List<String> tools = List.of("tool1", "tool2", "tool3");
        AgentResponse resp = new AgentResponse("r", "s", "sk", tools, RoutingMethod.SEMANTIC, 0.8, 0, 0, 0, 0.0, 0L, false);

        assertEquals(3, resp.toolsCalled().size());
        assertEquals("tool1", resp.toolsCalled().get(0));
        assertEquals("tool3", resp.toolsCalled().get(2));
    }
}
