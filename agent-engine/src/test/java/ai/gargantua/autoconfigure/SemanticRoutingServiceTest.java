package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.RoutingMethod;
import ai.gargantua.core.orchestrator.RoutingResult;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticRoutingServiceTest {

    private SemanticRoutingService service;
    private AgentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.getRouting().getSemantic().setThreshold(0.1);
        properties.getRouting().setFallbackSkill("fallback");

        // RoutingService needs LlmProviderFactory, but for semantic-only tests
        // we use a low threshold so LLM fallback is never reached.
        // For the fallback test, the RoutingService will return the fallback skill.
        LlmRouter llmRouter = new LlmRouter(properties);
        LlmProviderFactory llmProviderFactory = new LlmProviderFactory(properties, llmRouter);
        RoutingService routingService = new RoutingService(properties, llmProviderFactory);
        service = new SemanticRoutingService(properties, routingService);
    }

    private SkillMeta skill(String name, String description) {
        return new SkillMeta(name, description, "1.0.0", true, false, "general", SkillSource.FILESYSTEM, java.util.Set.of());
    }

    @Test
    void routesToBestMatchingSkill() {
        List<SkillMeta> skills = List.of(
                skill("summarize", "Summarize text into bullet points"),
                skill("translate", "Translate text between languages"),
                skill("code-review", "Review code for bugs and improvements")
        );

        // ONNX embeddings provide real semantic similarity
        RoutingResult result = service.route("Can you summarize this article for me?", skills);

        assertEquals("summarize", result.skillName());
        assertEquals(RoutingMethod.SEMANTIC, result.method());
        assertTrue(result.confidence() > 0);
    }

    @Test
    void fallsBackToLlmWhenBelowThreshold() {
        properties.getRouting().getSemantic().setThreshold(0.99); // Very high threshold

        List<SkillMeta> skills = List.of(
                skill("skill-a", "Does something very specific"),
                skill("skill-b", "Another very specific thing")
        );

        RoutingResult result = service.route("Completely unrelated topic about cooking", skills);

        // Falls back to LLM routing (which will fail without a real LLM and return fallback)
        assertEquals(RoutingMethod.LLM, result.method());
    }

    @Test
    void returnsFallbackForEmptySkillList() {
        RoutingResult result = service.route("Hello", List.of());

        assertEquals("fallback", result.skillName());
        assertEquals(RoutingMethod.SEMANTIC, result.method());
    }

    @Test
    void indexesSkillsForRouting() {
        List<SkillMeta> skills = List.of(
                skill("active", "An active skill for doing work"),
                new SkillMeta("inactive", "An inactive skill", "1.0.0", false, false, "general", SkillSource.FILESYSTEM, java.util.Set.of())
        );

        service.index(skills);

        // Route should work after indexing — only active skills are indexed
        RoutingResult result = service.route("I need an active skill to do work", skills);
        assertNotNull(result);
    }
}
