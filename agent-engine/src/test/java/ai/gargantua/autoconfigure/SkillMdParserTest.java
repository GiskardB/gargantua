package ai.gargantua.autoconfigure;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillMdParserTest {

    private SkillMdParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkillMdParser();
    }

    private static final String FULL_SKILL_MD = """
            ---
            name: summarize
            description: Summarizes text into concise bullet points
            version: 2.1.0
            allowed-tools: web-search document-reader
            metadata:
              active: true
              domain: productivity
              output-schema: '{"type":"object","properties":{"summary":{"type":"string"}}}'
              max-tokens: 2048
              temperature: 0.3
              preferred-model: gpt-4o
            references:
              - ref1.txt
              - ref2.txt
            ---
            You are a summarization assistant. Given text, produce concise bullet points.

            ## Rules
            - Be concise
            - Use bullet points
            """;

    @Test
    void parseToMeta_extractsFrontmatterCorrectly() {
        SkillMeta meta = parser.parseToMeta(FULL_SKILL_MD, SkillSource.FILESYSTEM);

        assertEquals("summarize", meta.name());
        assertEquals("Summarizes text into concise bullet points", meta.description());
        assertEquals("2.1.0", meta.version());
        assertTrue(meta.active());
        assertTrue(meta.hasSchema());
        assertEquals("productivity", meta.domain());
        assertEquals(SkillSource.FILESYSTEM, meta.source());
    }

    @Test
    void parseToCard_extractsFullSkillCard() {
        SkillCard card = parser.parseToCard(FULL_SKILL_MD, SkillSource.FILESYSTEM);

        assertEquals("summarize", card.meta().name());
        assertNotNull(card.systemPrompt());
        assertTrue(card.systemPrompt().contains("summarization assistant"));
        assertTrue(card.systemPrompt().contains("## Rules"));
        assertEquals(2, card.allowedTools().size());
        assertTrue(card.allowedTools().contains("web-search"));
        assertTrue(card.allowedTools().contains("document-reader"));
        assertNotNull(card.outputSchema());
        assertEquals(2048, card.maxTokens());
        assertEquals(0.3, card.temperature(), 0.001);
        assertEquals("gpt-4o", card.preferredModel());
        assertEquals(2, card.references().size());
    }

    @Test
    void parseToMeta_handlesEmptyContent() {
        SkillMeta meta = parser.parseToMeta("", SkillSource.CLASSPATH_JAR);
        assertEquals("unnamed", meta.name());
    }

    @Test
    void parseToMeta_handlesNoFrontmatter() {
        SkillMeta meta = parser.parseToMeta("Just some markdown content", SkillSource.FILESYSTEM);
        assertEquals("unnamed", meta.name());
    }

    @Test
    void parseToCard_handlesMinimalFrontmatter() {
        String minimal = """
                ---
                name: simple
                description: A simple skill
                ---
                Do something simple.
                """;

        SkillCard card = parser.parseToCard(minimal, SkillSource.FILESYSTEM);
        assertEquals("simple", card.meta().name());
        assertEquals("A simple skill", card.meta().description());
        assertEquals("Do something simple.", card.systemPrompt());
        assertTrue(card.allowedTools().isEmpty());
        assertNull(card.maxTokens());
        assertNull(card.temperature());
    }

    @Test
    void parseToMeta_inactiveSkill() {
        String content = """
                ---
                name: disabled
                description: Inactive skill
                metadata:
                  active: false
                ---
                Body.
                """;

        SkillMeta meta = parser.parseToMeta(content, SkillSource.FILESYSTEM);
        assertFalse(meta.active());
    }
}
