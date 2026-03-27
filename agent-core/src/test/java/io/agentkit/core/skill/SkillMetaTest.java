package io.agentkit.core.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillMetaTest {

    @Test
    void shouldBuildWithAllFields() {
        SkillMeta meta = new SkillMeta(
                "summarize",
                "Summarizes text",
                "1.2.0",
                true,
                true,
                "nlp",
                SkillSource.CLASSPATH_JAR
        );

        assertEquals("summarize", meta.name());
        assertEquals("Summarizes text", meta.description());
        assertEquals("1.2.0", meta.version());
        assertTrue(meta.active());
        assertTrue(meta.hasSchema());
        assertEquals("nlp", meta.domain());
        assertEquals(SkillSource.CLASSPATH_JAR, meta.source());
    }

    @Test
    void shouldHandleNullDomain() {
        SkillMeta meta = new SkillMeta(
                "translate",
                "Translates text",
                "0.1.0",
                false,
                false,
                null,
                SkillSource.FILESYSTEM
        );

        assertNull(meta.domain());
        assertEquals("translate", meta.name());
    }
}
