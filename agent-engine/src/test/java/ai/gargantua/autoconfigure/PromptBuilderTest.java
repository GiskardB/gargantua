package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.KnowledgeSegment;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptBuilder")
class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    private SkillCard skillCard(String systemPrompt) {
        SkillMeta meta = new SkillMeta("test", "desc", "1.0.0", true, false, "test",
                SkillSource.FILESYSTEM, Set.of());
        return new SkillCard(meta, systemPrompt, List.of(), null, List.of(), null, null, null, null);
    }

    // --- Skill system prompt ---

    @Test
    @DisplayName("build() includes skill system prompt")
    void build_includesSkillSystemPrompt() {
        String result = promptBuilder.build(skillCard("You are a helpful assistant."), null, null);
        assertThat(result).isEqualTo("You are a helpful assistant.");
    }

    @Test
    @DisplayName("build() handles null skill card")
    void build_handlesNullSkillCard() {
        String result = promptBuilder.build(null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("build() handles skill card with null system prompt")
    void build_handlesNullSystemPrompt() {
        String result = promptBuilder.build(skillCard(null), null, null);
        assertThat(result).isEmpty();
    }

    // --- Enricher context ---

    @Test
    @DisplayName("build() appends enricher context sections")
    void build_appendsEnricherContext() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("User Profile", "Fitness enthusiast");
        attrs.put("Weather", "Sunny, 25C");
        EnricherContext enricher = new EnricherContext("u1", "s1", "skill", "domain", "msg", attrs);

        String result = promptBuilder.build(skillCard("Base prompt."), null, enricher);
        assertThat(result).contains("## User Profile");
        assertThat(result).contains("Fitness enthusiast");
        assertThat(result).contains("## Weather");
        assertThat(result).contains("Sunny, 25C");
    }

    @Test
    @DisplayName("build() skips blank enricher attribute values")
    void build_skipsBlankEnricherValues() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Present", "value");
        attrs.put("Blank", "   ");
        attrs.put("Empty", "");
        attrs.put("Null", null);
        EnricherContext enricher = new EnricherContext("u1", "s1", "skill", "domain", "msg", attrs);

        String result = promptBuilder.build(skillCard("Base."), null, enricher);
        assertThat(result).contains("## Present");
        assertThat(result).doesNotContain("## Blank");
        assertThat(result).doesNotContain("## Empty");
        assertThat(result).doesNotContain("## Null");
    }

    @Test
    @DisplayName("build() handles null enricher context")
    void build_handlesNullEnricherContext() {
        String result = promptBuilder.build(skillCard("Base."), null, null);
        assertThat(result).isEqualTo("Base.");
    }

    @Test
    @DisplayName("build() handles enricher with null attributes map")
    void build_handlesEnricherWithNullAttributes() {
        EnricherContext enricher = new EnricherContext("u1", "s1", "skill", "domain", "msg", null);
        String result = promptBuilder.build(skillCard("Base."), null, enricher);
        assertThat(result).isEqualTo("Base.");
    }

    // --- Memory: episodic summaries ---

    @Test
    @DisplayName("build() includes episodic summaries")
    void build_includesEpisodicSummaries() {
        SessionSummary s1 = new SessionSummary("u1", "sess1", "Discussed running plan",
                List.of("running"), List.of(), 5, Instant.now());
        SessionSummary s2 = new SessionSummary("u1", "sess2", "Reviewed diet goals",
                List.of("diet"), List.of(), 3, Instant.now());
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(s1, s2), List.of(), 100);

        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).contains("## Previous Conversations");
        assertThat(result).contains("Discussed running plan");
        assertThat(result).contains("Reviewed diet goals");
    }

    @Test
    @DisplayName("build() skips episodic section when summaries list is empty")
    void build_skipsEpisodicWhenEmpty() {
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).doesNotContain("Previous Conversations");
    }

    @Test
    @DisplayName("build() skips episodic section when summaries list is null")
    void build_skipsEpisodicWhenNull() {
        ComposedMemory memory = new ComposedMemory(List.of(), null, List.of(), 0);
        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).doesNotContain("Previous Conversations");
    }

    // --- Memory: knowledge segments ---

    @Test
    @DisplayName("build() includes knowledge segments")
    void build_includesKnowledgeSegments() {
        KnowledgeSegment k1 = new KnowledgeSegment("u1", "preferences", "Prefers morning workouts",
                Instant.now(), "user");
        KnowledgeSegment k2 = new KnowledgeSegment("u1", "profile", "Age 30, intermediate level",
                Instant.now(), "agent");
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(), List.of(k1, k2), 50);

        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).contains("## User Knowledge");
        assertThat(result).contains("### preferences");
        assertThat(result).contains("Prefers morning workouts");
        assertThat(result).contains("### profile");
        assertThat(result).contains("Age 30, intermediate level");
    }

    @Test
    @DisplayName("build() skips knowledge section when segments list is empty")
    void build_skipsKnowledgeWhenEmpty() {
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(), List.of(), 0);
        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).doesNotContain("User Knowledge");
    }

    @Test
    @DisplayName("build() skips knowledge section when segments list is null")
    void build_skipsKnowledgeWhenNull() {
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(), null, 0);
        String result = promptBuilder.build(skillCard("Base."), memory, null);
        assertThat(result).doesNotContain("User Knowledge");
    }

    // --- Full composition ---

    @Test
    @DisplayName("build() composes all sections together")
    void build_composesAllSections() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Context", "Morning session");
        EnricherContext enricher = new EnricherContext("u1", "s1", "skill", "domain", "msg", attrs);

        SessionSummary summary = new SessionSummary("u1", "sess1", "Previous workout plan",
                List.of(), List.of(), 3, Instant.now());
        KnowledgeSegment knowledge = new KnowledgeSegment("u1", "prefs", "Likes HIIT",
                Instant.now(), "user");
        ComposedMemory memory = new ComposedMemory(List.of(), List.of(summary), List.of(knowledge), 50);

        String result = promptBuilder.build(skillCard("You are FitCoach."), memory, enricher);

        assertThat(result).startsWith("You are FitCoach.");
        assertThat(result).contains("## Context");
        assertThat(result).contains("Morning session");
        assertThat(result).contains("## Previous Conversations");
        assertThat(result).contains("Previous workout plan");
        assertThat(result).contains("## User Knowledge");
        assertThat(result).contains("Likes HIIT");
    }

    @Test
    @DisplayName("build() returns empty string when all inputs are null")
    void build_returnsEmptyWhenAllNull() {
        String result = promptBuilder.build(null, null, null);
        assertThat(result).isEmpty();
    }
}
