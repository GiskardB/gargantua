package ai.gargantua.memory;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.memory.composer.MemoryComposer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryComposerTest {

    private InMemoryWorkingMemoryAdapter workingMemory;
    private InMemoryEpisodicMemoryAdapter episodicMemory;
    private InMemoryKnowledgeMemoryAdapter knowledgeMemory;

    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";

    @BeforeEach
    void setUp() {
        workingMemory = new InMemoryWorkingMemoryAdapter();
        episodicMemory = new InMemoryEpisodicMemoryAdapter();
        knowledgeMemory = new InMemoryKnowledgeMemoryAdapter();
    }

    @Test
    void shouldComposeAllThreeLayers() {
        // Working memory
        workingMemory.appendMessage(SESSION_ID, ChatMessage.userMessage("Hello"));
        workingMemory.appendMessage(SESSION_ID, ChatMessage.assistantMessage("Hi there!"));

        // Episodic memory
        episodicMemory.saveSummary(new SessionSummary(
                USER_ID, "old-session", "Previous conversation about Java",
                List.of("Java", "Spring"), List.of(), 10, Instant.now().minusSeconds(3600)
        ));

        // Knowledge memory
        knowledgeMemory.upsertSegment(USER_ID, "prefs", "User prefers dark mode");

        var composer = new MemoryComposer(workingMemory, episodicMemory, knowledgeMemory, 5000);
        ComposedMemory result = composer.compose(USER_ID, SESSION_ID, 5000);

        assertEquals(2, result.workingMessages().size());
        assertEquals(1, result.episodicSummaries().size());
        assertEquals(1, result.knowledgeSegments().size());
        assertTrue(result.estimatedTokens() > 0);
    }

    @Test
    void shouldRespectTokenBudget() {
        // Working memory - small
        workingMemory.appendMessage(SESSION_ID, ChatMessage.userMessage("Hi"));

        // Episodic memory - add many large summaries
        for (int i = 0; i < 20; i++) {
            String largeSummary = "A".repeat(400); // 400 chars = ~100 tokens each
            episodicMemory.saveSummary(new SessionSummary(
                    USER_ID, "session-" + i, largeSummary,
                    List.of("topic"), List.of(), 5,
                    Instant.now().minusSeconds(i * 60L)
            ));
        }

        // Knowledge memory - add several segments
        for (int i = 0; i < 10; i++) {
            knowledgeMemory.upsertSegment(USER_ID, "segment-" + i, "B".repeat(200));
        }

        int budget = 500;
        var composer = new MemoryComposer(workingMemory, episodicMemory, knowledgeMemory, budget);
        ComposedMemory result = composer.compose(USER_ID, SESSION_ID, budget);

        assertTrue(result.estimatedTokens() <= budget,
                "Estimated tokens %d should be <= budget %d".formatted(result.estimatedTokens(), budget));
    }

    @Test
    void shouldReturnEmptyWhenNoData() {
        var composer = new MemoryComposer(workingMemory, episodicMemory, knowledgeMemory, 5000);
        ComposedMemory result = composer.compose(USER_ID, SESSION_ID, 5000);

        assertTrue(result.workingMessages().isEmpty());
        assertTrue(result.episodicSummaries().isEmpty());
        assertTrue(result.knowledgeSegments().isEmpty());
        assertEquals(0, result.estimatedTokens());
    }
}
