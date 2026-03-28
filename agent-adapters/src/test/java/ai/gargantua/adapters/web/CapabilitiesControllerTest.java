package ai.gargantua.adapters.web;

import ai.gargantua.core.capabilities.AgentCapabilities;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CapabilitiesController} — no Spring context needed.
 */
class CapabilitiesControllerTest {

    @Test
    @DisplayName("GET /api/capabilities returns capabilities with Cache-Control header")
    void getCapabilitiesReturnsCacheControlHeader() {
        SkillRegistry registry = mock(SkillRegistry.class);

        SkillMeta meta = new SkillMeta(
                "code-review", "Reviews code", "1.0.0",
                true, true, "engineering", SkillSource.FILESYSTEM);
        SkillCard card = new SkillCard(
                meta, "You are a code reviewer.", List.of("read_file"),
                null, List.of(), 4096, 0.3, null);

        when(registry.listMeta()).thenReturn(List.of(meta));
        when(registry.load(anyString())).thenReturn(card);

        var controller = new CapabilitiesController(registry, "test-agent", "Test Agent");
        ResponseEntity<AgentCapabilities> response = controller.getCapabilities();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("test-agent", response.getBody().agentId());
        assertTrue(response.getBody().available());
        assertEquals(1, response.getBody().capabilities().size());
        assertEquals("code-review", response.getBody().capabilities().get(0).skillId());
        assertTrue(response.getHeaders().get("Cache-Control").contains("max-age=60"));
    }
}
