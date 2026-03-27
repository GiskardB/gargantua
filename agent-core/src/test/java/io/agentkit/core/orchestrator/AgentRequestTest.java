package io.agentkit.core.orchestrator;

import io.agentkit.core.session.DryRunContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentRequestTest {

    @Test
    void shouldBuildWithRequiredFields() {
        AgentRequest request = AgentRequest.builder()
                .message("hello")
                .userId("user-1")
                .sessionId("session-1")
                .build();

        assertEquals("hello", request.message());
        assertEquals("user-1", request.userId());
        assertEquals("session-1", request.sessionId());
    }

    @Test
    void shouldHaveDefaultDryRunInactive() {
        AgentRequest request = AgentRequest.builder()
                .message("hello")
                .userId("user-1")
                .sessionId("session-1")
                .build();

        DryRunContext dryRun = request.dryRunContext();
        assertNotNull(dryRun);
        assertFalse(dryRun.active());
        assertTrue(dryRun.toolStubs().isEmpty());
    }
}
