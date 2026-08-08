package ai.gargantua.core.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Loadout")
class LoadoutTest {

    @Test
    @DisplayName("empty loadout is empty and null-safe")
    void emptyIsEmpty() {
        assertTrue(Loadout.empty().isEmpty());
        assertTrue(new Loadout(null, null, null, null).isEmpty());
    }

    @Test
    @DisplayName("defensive copies and reports non-empty")
    void copiesAndReportsNonEmpty() {
        Loadout loadout = new Loadout(
                List.of(new KnowledgeRef("payments-kb")),
                List.of("history"),
                List.of("refund-skill"),
                List.of(new ResourceRef("form")));
        assertFalse(loadout.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> loadout.knowledge().add(new KnowledgeRef("x")));
    }

    @Test
    @DisplayName("rejects duplicate knowledge base names")
    void rejectsDuplicateKnowledge() {
        assertThrows(IllegalArgumentException.class, () -> new Loadout(
                List.of(new KnowledgeRef("kb"), new KnowledgeRef("kb")),
                List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("KnowledgeRef requires a name and validates retrieval overrides")
    void knowledgeRefValidation() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRef("  "));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRef("kb", null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRef("kb", null, null, 1.5));
        KnowledgeRef ok = new KnowledgeRef("kb", "desc", 8, 0.5);
        assertEquals("kb", ok.name());
        assertEquals(8, ok.maxResults());
    }

    @Test
    @DisplayName("AgentSpec defaults to an empty loadout and the 8-arg ctor stays compatible")
    void agentSpecDefaultsEmptyLoadout() {
        AgentSpec viaMinimal = AgentSpec.minimal();
        assertNotNull(viaMinimal.loadout());
        assertTrue(viaMinimal.loadout().isEmpty());
        assertFalse(viaMinimal.hasLoadout());

        // Pre-1.3 8-argument shape still compiles and yields an empty loadout.
        AgentSpec legacy = new AgentSpec(null, List.of(), null, List.of(),
                java.util.Set.of(), null, java.util.Map.of(), java.util.Set.of());
        assertTrue(legacy.loadout().isEmpty());
    }
}
