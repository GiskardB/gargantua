package ai.gargantua.adapters.web;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CapabilitiesController.class)
class CapabilitiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillRegistry skillRegistry;

    @Test
    @DisplayName("GET /api/capabilities returns capabilities with Cache-Control header")
    void getCapabilitiesReturnsCacheControlHeader() throws Exception {
        SkillMeta meta = new SkillMeta(
                "code-review", "Reviews code for quality", "1.0.0",
                true, true, "engineering", SkillSource.FILESYSTEM);

        SkillCard card = new SkillCard(
                meta, "You are a code reviewer.", List.of("read_file", "write_file"),
                null, List.of(), 4096, 0.3, "gpt-4o");

        when(skillRegistry.listMeta()).thenReturn(List.of(meta));
        when(skillRegistry.load(anyString())).thenReturn(card);

        mockMvc.perform(get("/api/capabilities"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60"))
                .andExpect(jsonPath("$.agentId").value("default-agent"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.capabilities").isArray())
                .andExpect(jsonPath("$.capabilities[0].skillId").value("code-review"))
                .andExpect(jsonPath("$.capabilities[0].active").value(true))
                .andExpect(jsonPath("$.capabilities[0].allowedTools[0]").value("read_file"));
    }
}
