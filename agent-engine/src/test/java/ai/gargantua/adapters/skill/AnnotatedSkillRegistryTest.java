package ai.gargantua.adapters.skill;

import ai.gargantua.autoconfigure.AgentSkillProcessor;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the {@link AnnotatedSkillRegistry} adapter introduced
 * in 1.2.7. The processor is mocked — we only care that the registry
 * round-trips its {@link SkillCard} list correctly under the
 * {@link ai.gargantua.core.skill.SkillRegistry} contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnnotatedSkillRegistry")
class AnnotatedSkillRegistryTest {

    private AgentSkillProcessor processor;
    private AnnotatedSkillRegistry registry;

    @BeforeEach
    void setUp() {
        processor = mock(AgentSkillProcessor.class);
        registry = new AnnotatedSkillRegistry(processor);
    }

    private SkillCard card(String name, String domain) {
        SkillMeta meta = new SkillMeta(name, "desc of " + name, "1.0.0",
                true, false, domain, SkillSource.ANNOTATION, Set.of());
        return new SkillCard(meta, "## Role\nyou are " + name, List.of("tool1"),
                null, List.of(), null, null, null, null);
    }

    @Test
    @DisplayName("listMeta() returns one meta per discovered skill, in order")
    void listMetaReturnsAll() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(
                card("alpha", "math"),
                card("beta", "general")
        ));

        List<SkillMeta> metas = registry.listMeta();
        assertThat(metas).hasSize(2);
        assertThat(metas.get(0).name()).isEqualTo("alpha");
        assertThat(metas.get(0).domain()).isEqualTo("math");
        assertThat(metas.get(1).name()).isEqualTo("beta");
    }

    @Test
    @DisplayName("findMeta(known) returns the meta wrapped in Optional")
    void findMetaKnown() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(card("alpha", "math")));

        assertThat(registry.findMeta("alpha"))
                .isPresent()
                .map(SkillMeta::source)
                .contains(SkillSource.ANNOTATION);
    }

    @Test
    @DisplayName("findMeta(unknown) returns Optional.empty()")
    void findMetaUnknown() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(card("alpha", "math")));
        assertThat(registry.findMeta("nope")).isEmpty();
    }

    @Test
    @DisplayName("load(known) returns the full SkillCard")
    void loadKnown() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(card("alpha", "math")));

        SkillCard loaded = registry.load("alpha");
        assertThat(loaded.meta().name()).isEqualTo("alpha");
        assertThat(loaded.systemPrompt()).contains("you are alpha");
        assertThat(loaded.allowedTools()).containsExactly("tool1");
    }

    @Test
    @DisplayName("load(unknown) throws SkillNotFoundException")
    void loadUnknownThrows() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(card("alpha", "math")));
        assertThatThrownBy(() -> registry.load("nope"))
                .isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    @DisplayName("reload() is a no-op — annotated skills are scanned once at boot")
    void reloadIsNoop() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of(card("alpha", "math")));
        // Should not throw; should not call anything destructive on the processor.
        registry.reload();
        // And after reload, the registry still serves the same skill.
        assertThat(registry.findMeta("alpha")).isPresent();
    }

    @Test
    @DisplayName("Empty processor list produces an empty registry view")
    void emptyProcessor() {
        when(processor.getDiscoveredSkills()).thenReturn(List.of());
        assertThat(registry.listMeta()).isEmpty();
        assertThat(registry.findMeta("any")).isEmpty();
        assertThatThrownBy(() -> registry.load("any"))
                .isInstanceOf(SkillNotFoundException.class);
    }
}
