package ai.gargantua.adapters.web;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillAdminController")
class SkillAdminControllerTest {

    @Mock
    private SkillRegistry skillRegistry;

    private SkillAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new SkillAdminController(skillRegistry);
    }

    private SkillMeta sampleMeta(String name) {
        return new SkillMeta(name, "Description of " + name, "1.0.0",
                true, false, "general", SkillSource.FILESYSTEM, Set.of());
    }

    @Nested
    @DisplayName("listSkills")
    class ListSkills {

        @Test
        @DisplayName("returns 200 with list of skill metadata")
        void returns200WithMetaList() {
            var meta1 = sampleMeta("fitness-coach");
            var meta2 = sampleMeta("nutrition-advisor");
            when(skillRegistry.listMeta()).thenReturn(List.of(meta1, meta2));

            var response = controller.listSkills();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).name()).isEqualTo("fitness-coach");
            assertThat(response.getBody().get(1).name()).isEqualTo("nutrition-advisor");
        }

        @Test
        @DisplayName("returns 200 with empty list when no skills registered")
        void returnsEmptyListWhenNoSkills() {
            when(skillRegistry.listMeta()).thenReturn(List.of());

            var response = controller.listSkills();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSkill")
    class GetSkill {

        @Test
        @DisplayName("returns 200 with full skill card")
        void returns200WithSkillCard() {
            var meta = sampleMeta("fitness-coach");
            var card = new SkillCard(meta, "You are a fitness coach", List.of("getWeather"),
                    null, List.of(), null, null, null, null);
            when(skillRegistry.load("fitness-coach")).thenReturn(card);

            var response = controller.getSkill("fitness-coach");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().systemPrompt()).isEqualTo("You are a fitness coach");
            assertThat(response.getBody().allowedTools()).containsExactly("getWeather");
        }

        @Test
        @DisplayName("delegates to registry load")
        void delegatesToRegistryLoad() {
            var card = new SkillCard(sampleMeta("test"), "prompt", List.of(),
                    null, List.of(), null, null, null, null);
            when(skillRegistry.load("test")).thenReturn(card);

            controller.getSkill("test");

            verify(skillRegistry).load("test");
        }
    }

    @Nested
    @DisplayName("reloadSkills")
    class ReloadSkills {

        @Test
        @DisplayName("returns 200 with status reloaded")
        void returns200WithReloadedStatus() {
            var response = controller.reloadSkills();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "reloaded");
        }

        @Test
        @DisplayName("invokes reload on registry")
        void invokesReloadOnRegistry() {
            controller.reloadSkills();

            verify(skillRegistry).reload();
        }
    }
}
