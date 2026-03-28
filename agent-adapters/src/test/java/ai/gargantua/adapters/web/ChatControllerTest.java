package ai.gargantua.adapters.web;

import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.orchestrator.RoutingMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    /** Minimal Spring Boot configuration so @WebMvcTest can bootstrap without a full app. */
    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrchestratorEngine orchestratorEngine;

    @Test
    @DisplayName("POST /api/agent/chat returns successful response")
    void chatReturnsSuccessfulResponse() throws Exception {
        AgentResponse response = new AgentResponse(
                "Hello! How can I help you?",
                "sess_123",
                "general-chat",
                List.of(),
                RoutingMethod.SEMANTIC,
                0.95,
                50,
                30,
                80,
                0.002,
                150L,
                false
        );

        when(orchestratorEngine.invoke(any(AgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Hello"}
                                """)
                        .header("X-User-Id", "user-1")
                        .header("X-Session-Id", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello! How can I help you?"))
                .andExpect(jsonPath("$.sessionId").value("sess_123"))
                .andExpect(jsonPath("$.skillUsed").value("general-chat"))
                .andExpect(jsonPath("$.totalTokens").value(80))
                .andExpect(jsonPath("$.dryRun").value(false));
    }

    @Test
    @DisplayName("POST /api/agent/chat returns 403 when guardrail blocks")
    void chatReturnsBlockedWhenGuardrailFires() throws Exception {
        when(orchestratorEngine.invoke(any(AgentRequest.class)))
                .thenThrow(new GuardrailBlockedException(
                        "content-filter",
                        "Message contains prohibited content",
                        Map.of("category", "violence")));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "bad content"}
                                """)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Guardrail Blocked"))
                .andExpect(jsonPath("$.detail").value("Message contains prohibited content"));
    }
}
