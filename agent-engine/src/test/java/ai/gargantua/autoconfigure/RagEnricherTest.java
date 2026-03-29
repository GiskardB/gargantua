package ai.gargantua.autoconfigure;

import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.rag.RagConfig;
import ai.gargantua.core.rag.RetrievedChunk;
import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagEnricherTest {

    private VectorStorePort vectorStore;
    private SkillRegistry skillRegistry;
    private RagEnricher enricher;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStorePort.class);
        skillRegistry = mock(SkillRegistry.class);
        enricher = new RagEnricher(vectorStore, skillRegistry);
    }

    @Test
    void shouldReturnNullWhenSkillHasNoRagConfig() {
        var meta = new SkillMeta("general", "General skill", "1.0.0",
                true, false, "general", SkillSource.FILESYSTEM, java.util.Set.of());
        var card = new SkillCard(meta, "You are helpful.", List.of(),
                null, List.of(), null, null, null, null);
        when(skillRegistry.load("general")).thenReturn(card);

        var ctx = new EnricherContext("user1", "session1", "general", "general",
                "What is the vacation policy?", Map.of());

        String result = enricher.enrich(ctx);

        assertNull(result, "Should return null when skill has no RagConfig");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldReturnFormattedDocumentsWhenRagConfigPresent() {
        var ragConfig = new RagConfig("hr-docs", 5, 0.3);
        var meta = new SkillMeta("hr-assistant", "HR assistant", "1.0.0",
                true, false, "hr", SkillSource.FILESYSTEM, java.util.Set.of());
        var card = new SkillCard(meta, "You are an HR assistant.", List.of(),
                null, List.of(), null, null, null, ragConfig);
        when(skillRegistry.load("hr-assistant")).thenReturn(card);

        var chunks = List.of(
                new RetrievedChunk("Employees get 20 vacation days per year.", "vacation-policy.pdf", 0.85),
                new RetrievedChunk("Unused days can be carried over.", "vacation-policy.pdf", 0.72)
        );
        when(vectorStore.search(eq("hr-docs"), eq("What is the vacation policy?"), eq(5), eq(0.3)))
                .thenReturn(chunks);

        var ctx = new EnricherContext("user1", "session1", "hr-assistant", "hr",
                "What is the vacation policy?", Map.of());

        String result = enricher.enrich(ctx);

        assertNotNull(result, "Should return formatted documents");
        assertTrue(result.contains("vacation-policy.pdf"), "Should include source attribution");
        assertTrue(result.contains("Employees get 20 vacation days"), "Should include chunk content");
        assertTrue(result.contains("Unused days can be carried over"), "Should include second chunk");
        assertTrue(result.contains("1."), "Should number the results");
        assertTrue(result.contains("2."), "Should have second numbered result");
    }

    @Test
    void shouldReturnNullWhenNoChunksFound() {
        var ragConfig = new RagConfig("empty-collection");
        var meta = new SkillMeta("test-skill", "Test", "1.0.0",
                true, false, "general", SkillSource.FILESYSTEM, java.util.Set.of());
        var card = new SkillCard(meta, "Test prompt.", List.of(),
                null, List.of(), null, null, null, ragConfig);
        when(skillRegistry.load("test-skill")).thenReturn(card);
        when(vectorStore.search(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        var ctx = new EnricherContext("user1", "session1", "test-skill", "general",
                "some query", Map.of());

        String result = enricher.enrich(ctx);

        assertNull(result, "Should return null when no chunks are found");
    }

    @Test
    void shouldHaveCorrectSectionNameAndOrder() {
        assertEquals("RELEVANT_DOCUMENTS", enricher.sectionName());
        assertEquals(5, enricher.order());
        assertNull(enricher.targetSkill(), "Should run for all skills");
    }
}
