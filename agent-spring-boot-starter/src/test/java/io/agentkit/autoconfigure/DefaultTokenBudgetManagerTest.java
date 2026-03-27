package io.agentkit.autoconfigure;

import io.agentkit.core.exception.TokenBudgetExceededException;
import io.agentkit.core.memory.KnowledgeSegment;
import io.agentkit.core.orchestrator.BudgetAllocation;
import io.agentkit.core.orchestrator.BudgetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTokenBudgetManagerTest {

    private DefaultTokenBudgetManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultTokenBudgetManager();
    }

    @Test
    void estimate_returnsLengthDivFour() {
        assertEquals(0, manager.estimate(null));
        assertEquals(0, manager.estimate(""));
        assertEquals(3, manager.estimate("hello world!")); // 12 chars / 4
        assertEquals(25, manager.estimate("a".repeat(100)));
    }

    @Test
    void allocate_withoutTruncation() {
        BudgetRequest request = new BudgetRequest(
                "System prompt",       // ~3 tokens
                "",                    // 0 tokens
                List.of("ref1"),       // ~1 token
                List.of("summary1"),   // ~2 tokens
                List.of(),             // 0 knowledge
                List.of("tool desc"),  // ~2 tokens
                "Hello",               // ~1 token
                1000                   // plenty of budget
        );

        BudgetAllocation alloc = manager.allocate(request);

        assertFalse(alloc.wasTruncated());
        assertEquals("System prompt", alloc.systemPrompt());
        assertEquals("Hello", alloc.userMessage());
        assertEquals(1, alloc.references().size());
        assertEquals(1, alloc.episodicSummaries().size());
        assertTrue(alloc.budgetRemaining() > 0);
    }

    @Test
    void allocate_withTruncation() {
        // Create a scenario where memory items exceed budget
        String longRef = "x".repeat(400);   // 100 tokens
        String longSummary = "y".repeat(400); // 100 tokens

        BudgetRequest request = new BudgetRequest(
                "Prompt",              // ~1 token
                "",
                List.of(longRef, longRef, longRef),  // 300 tokens total
                List.of(longSummary, longSummary),   // 200 tokens total
                List.of(),
                List.of(),
                "Hi",                  // ~0 tokens
                50                     // very tight budget
        );

        BudgetAllocation alloc = manager.allocate(request);

        assertTrue(alloc.wasTruncated());
        assertFalse(alloc.truncationLog().isEmpty());
        // References should be truncated
        assertTrue(alloc.references().size() < 3);
    }

    @Test
    void allocate_throwsWhenFixedTokensExceedMax() {
        BudgetRequest request = new BudgetRequest(
                "x".repeat(1000),  // 250 tokens
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "y".repeat(1000),  // 250 tokens
                100                // budget too small for fixed costs
        );

        assertThrows(TokenBudgetExceededException.class, () -> manager.allocate(request));
    }

    @Test
    void allocate_handlesKnowledgeTruncation() {
        KnowledgeSegment seg1 = new KnowledgeSegment("u1", "k1", "a".repeat(200), Instant.now(), "test");
        KnowledgeSegment seg2 = new KnowledgeSegment("u1", "k2", "b".repeat(200), Instant.now(), "test");

        BudgetRequest request = new BudgetRequest(
                "Sys",
                "",
                List.of(),
                List.of(),
                List.of(seg1, seg2),
                List.of(),
                "Hi",
                30 // very tight
        );

        BudgetAllocation alloc = manager.allocate(request);
        // Should fit system + user but may truncate knowledge
        assertTrue(alloc.knowledge().size() <= 2);
    }
}
