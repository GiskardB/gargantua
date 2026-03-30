package ai.gargantua.adapters.web;

import ai.gargantua.core.a2a.AgentCard;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import ai.gargantua.autoconfigure.AgentCardService;
import ai.gargantua.autoconfigure.AgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CapabilitiesController} — no Spring context needed.
 */
class CapabilitiesControllerTest {

    @Test
    @DisplayName("GET /.well-known/agent.json returns A2A Agent Card with Cache-Control header")
    void wellKnownAgentJsonReturnsAgentCard() {
        AgentCardService agentCardService = buildAgentCardService();

        var controller = new CapabilitiesController(agentCardService, null);

        var request = mockRequest();
        ResponseEntity<AgentCard> response = controller.wellKnownAgentJson(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Test Agent", response.getBody().name());
        assertEquals("1.0", response.getBody().protocolVersion());
        assertEquals(1, response.getBody().skills().size());
        assertEquals("code-review", response.getBody().skills().get(0).id());
        assertTrue(response.getHeaders().get("Cache-Control").contains("max-age=60"));
    }

    @Test
    @DisplayName("POST /a2a with unknown method returns JSON-RPC error")
    void unknownMethodReturnsError() {
        AgentCardService agentCardService = buildAgentCardService();

        var controller = new CapabilitiesController(agentCardService, null);

        Map<String, Object> jsonRpc = Map.of(
                "jsonrpc", "2.0",
                "method", "unknown/method",
                "id", 1
        );

        ResponseEntity<Map<String, Object>> response = controller.handleA2A(jsonRpc);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    @DisplayName("POST /a2a message/send without orchestrator returns error")
    void messageSendWithoutOrchestratorReturnsError() {
        AgentCardService agentCardService = buildAgentCardService();

        var controller = new CapabilitiesController(agentCardService, null);

        Map<String, Object> jsonRpc = Map.of(
                "jsonrpc", "2.0",
                "method", "message/send",
                "id", 1,
                "params", Map.of("message", "Hello")
        );

        ResponseEntity<Map<String, Object>> response = controller.handleA2A(jsonRpc);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    private AgentCardService buildAgentCardService() {
        SkillRegistry registry = mock(SkillRegistry.class);

        SkillMeta meta = new SkillMeta(
                "code-review", "Reviews code", "1.0.0",
                true, true, "engineering", SkillSource.FILESYSTEM, java.util.Set.of());
        SkillCard card = new SkillCard(
                meta, "You are a code reviewer.", List.of("read_file"),
                null, List.of(), 4096, 0.3, null, null);

        when(registry.listMeta()).thenReturn(List.of(meta));
        when(registry.load(anyString())).thenReturn(card);

        AgentProperties properties = new AgentProperties();
        properties.getApi().setDisplayName("Test Agent");
        properties.getApi().setAgentId("test-agent");
        properties.getApi().setDescription("A test agent");
        properties.getApi().setVersion("1.0.0");

        return new AgentCardService(properties, registry);
    }

    private jakarta.servlet.http.HttpServletRequest mockRequest() {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getContextPath()).thenReturn("");
        return request;
    }
}
