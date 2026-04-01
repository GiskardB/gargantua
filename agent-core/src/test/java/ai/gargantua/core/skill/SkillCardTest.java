package ai.gargantua.core.skill;

import ai.gargantua.core.rag.RagConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillCard")
class SkillCardTest {

    private SkillMeta sampleMeta() {
        return new SkillMeta("greeting", "Greets users", "1.0.0", true, false, "general", SkillSource.FILESYSTEM, Set.of());
    }

    @Test
    @DisplayName("all fields are accessible via record accessors")
    void allFieldsAccessible() {
        SkillMeta meta = sampleMeta();
        RagConfig rag = new RagConfig("kb-greeting", 10, 0.5);

        SkillCard card = new SkillCard(
                meta, "You are a greeter", List.of("searchTool"),
                "{\"type\":\"object\"}", List.of("ref1.md"), 256, 0.7, "gpt-4o", rag
        );

        assertEquals(meta, card.meta());
        assertEquals("You are a greeter", card.systemPrompt());
        assertEquals(List.of("searchTool"), card.allowedTools());
        assertEquals("{\"type\":\"object\"}", card.outputSchema());
        assertEquals(List.of("ref1.md"), card.references());
        assertEquals(256, card.maxTokens());
        assertEquals(0.7, card.temperature(), 0.001);
        assertEquals("gpt-4o", card.preferredModel());
        assertEquals(rag, card.ragConfig());
    }

    @Test
    @DisplayName("nullable fields can be null (no overrides)")
    void nullableFields() {
        SkillCard card = new SkillCard(
                sampleMeta(), "prompt", List.of(), null, List.of(), null, null, null, null
        );

        assertNull(card.outputSchema());
        assertNull(card.maxTokens());
        assertNull(card.temperature());
        assertNull(card.preferredModel());
        assertNull(card.ragConfig());
    }

    @Test
    @DisplayName("empty allowed tools list means no tools")
    void emptyTools() {
        SkillCard card = new SkillCard(sampleMeta(), "prompt", List.of(), null, List.of(), null, null, null, null);
        assertTrue(card.allowedTools().isEmpty());
        assertTrue(card.references().isEmpty());
    }

    @Test
    @DisplayName("record equality based on all fields")
    void equality() {
        SkillMeta meta = sampleMeta();
        SkillCard a = new SkillCard(meta, "p", List.of(), null, List.of(), null, null, null, null);
        SkillCard b = new SkillCard(meta, "p", List.of(), null, List.of(), null, null, null, null);
        SkillCard c = new SkillCard(meta, "different", List.of(), null, List.of(), null, null, null, null);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("multiple allowed tools preserved in order")
    void multipleTools() {
        List<String> tools = List.of("toolA", "toolB", "toolC");
        SkillCard card = new SkillCard(sampleMeta(), "p", tools, null, List.of(), null, null, null, null);
        assertEquals(3, card.allowedTools().size());
        assertEquals("toolA", card.allowedTools().get(0));
        assertEquals("toolC", card.allowedTools().get(2));
    }
}
