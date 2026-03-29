package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.SecurityContextFilter;
import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.RoutingMethod;
import ai.gargantua.core.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ChatController} — no Spring context needed.
 */
class ChatControllerTest {

    private final OrchestratorEngine orchestratorEngine = mock(OrchestratorEngine.class);
    private final ChatController controller = new ChatController(orchestratorEngine);

    private HttpServletRequest mockHttpRequest() {
        var httpRequest = mock(HttpServletRequest.class);
        var ctx = new SecurityContext("user-1", null, Set.of());
        when(httpRequest.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR)).thenReturn(ctx);
        return httpRequest;
    }

    @Test
    @DisplayName("POST /api/agent/chat returns successful response")
    void chatReturnsSuccessfulResponse() {
        AgentResponse agentResponse = new AgentResponse(
                "Hello! How can I help you?",
                "sess_123",
                "general-chat",
                List.of(),
                RoutingMethod.SEMANTIC,
                0.95,
                50, 30, 80,
                0.002,
                150L,
                false
        );

        when(orchestratorEngine.invoke(any(AgentRequest.class))).thenReturn(agentResponse);

        var request = new ChatController.ChatRequest("Hello");
        var response = controller.chat(request, "user-1", "sess_123", false, mockHttpRequest());

        assertNotNull(response.getBody());
        assertEquals("Hello! How can I help you?", response.getBody().text());
        assertEquals("sess_123", response.getBody().sessionId());
        assertEquals("general-chat", response.getBody().skillUsed());
        assertEquals(80, response.getBody().totalTokens());
    }

    @Test
    @DisplayName("Chat throws GuardrailBlockedException when guardrail blocks")
    void chatThrowsWhenGuardrailBlocks() {
        when(orchestratorEngine.invoke(any(AgentRequest.class)))
                .thenThrow(new GuardrailBlockedException(
                        "content-filter",
                        "Message contains prohibited content",
                        Map.of("category", "violence")));

        var request = new ChatController.ChatRequest("bad content");
        assertThrows(GuardrailBlockedException.class, () ->
                controller.chat(request, "user-1", "sess_123", false, mockHttpRequest()));
    }
}
