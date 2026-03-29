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
        properties.getRouting().getSemantic().setThreshold(0.1); // Low threshold for term-overlap routing
        properties.getRouting().setFallbackSkill("fallback");

        RoutingService routingService = new RoutingService(properties);
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

        // Use message with strong word overlap to ensure term-frequency cosine similarity passes
        RoutingResult result = service.route("Summarize text into bullet points please", skills);

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
                skill("active", "An active skill"),
                new SkillMeta("inactive", "An inactive skill", "1.0.0", false, false, "general", SkillSource.FILESYSTEM, java.util.Set.of())
        );

        service.index(skills);

        // Route should work after indexing
        RoutingResult result = service.route("active skill", skills);
        assertNotNull(result);
    }

    @Test
    void cosineSimilarityCalculation() {
        String[] a = {"hello", "world", "test"};
        String[] b = {"hello", "world", "foo"};
        String[] c = {"completely", "different", "words"};

        double simAB = service.cosineSimilarity(a, b);
        double simAC = service.cosineSimilarity(a, c);

        assertTrue(simAB > simAC, "Similar texts should have higher similarity");
        assertTrue(simAB > 0, "Overlapping texts should have positive similarity");
        assertEquals(0.0, simAC, "Non-overlapping texts should have zero similarity");
    }
}
