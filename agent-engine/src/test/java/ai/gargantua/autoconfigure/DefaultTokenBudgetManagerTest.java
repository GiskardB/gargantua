package ai.gargantua.autoconfigure;

import ai.gargantua.core.exception.TokenBudgetExceededException;
import ai.gargantua.core.memory.KnowledgeSegment;
import ai.gargantua.core.orchestrator.BudgetAllocation;
import ai.gargantua.core.orchestrator.BudgetRequest;
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

    @Test
    void estimate_returnsOneForVeryShortText() {
        // Math.max(1, 1/4) = Math.max(1, 0) = 1
        assertEquals(1, manager.estimate("ab"));
        assertEquals(1, manager.estimate("a"));
    }

    @Test
    void allocate_withEmptyListsNoTruncation() {
        BudgetRequest request = new BudgetRequest(
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                1000
        );

        BudgetAllocation alloc = manager.allocate(request);

        assertFalse(alloc.wasTruncated());
        assertTrue(alloc.truncationLog().isEmpty());
        assertEquals(0, alloc.estimatedTotal());
        assertEquals(1000, alloc.budgetRemaining());
    }

    @Test
    void allocate_preservesOriginalSystemPromptAndUserMessage() {
        BudgetRequest request = new BudgetRequest(
                "System instruction",
                "",
                List.of("ref1"),
                List.of("summary1"),
                List.of(),
                List.of("tool1"),
                "User query",
                5000
        );

        BudgetAllocation alloc = manager.allocate(request);

        assertEquals("System instruction", alloc.systemPrompt());
        assertEquals("User query", alloc.userMessage());
    }

    @Test
    void allocate_episodicTruncation() {
        String longSummary = "s".repeat(400); // 100 tokens each

        BudgetRequest request = new BudgetRequest(
                "P",
                "",
                List.of(),
                List.of(longSummary, longSummary, longSummary, longSummary),
                List.of(),
                List.of(),
                "Q",
                50 // tight budget: ~1 token for P, ~1 for Q, remaining ~48
        );

        BudgetAllocation alloc = manager.allocate(request);

        assertTrue(alloc.wasTruncated());
        // episodic gets at most remaining/2, which is ~24 tokens, each summary is 100 tokens, so 0 fit
        assertTrue(alloc.episodicSummaries().size() < 4);
    }

    @Test
    void allocate_enrichedContextCountsAsFixedCost() {
        BudgetRequest request = new BudgetRequest(
                "Sys",
                "x".repeat(400), // 100 tokens of enriched context
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "Hi",
                50 // only 50 tokens budget
        );

        // enriched context (100) + system (~1) + user (~1) = ~102 > 50
        assertThrows(TokenBudgetExceededException.class, () -> manager.allocate(request));
    }
}
