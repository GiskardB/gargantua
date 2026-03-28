package ai.gargantua.core.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BudgetAllocationTest {

    @Test
    void noMemoryFactoryMethod() {
        BudgetRequest request = new BudgetRequest(
                "You are an assistant.",
                "",
                List.of("ref1"),
                List.of("summary1"),
                List.of(),
                List.of("tool: getWeather"),
                "What is the weather?",
                3000
        );

        BudgetAllocation allocation = BudgetAllocation.noMemory(request, 100);

        assertNotNull(allocation);
        assertEquals("You are an assistant.", allocation.systemPrompt());
        assertEquals("What is the weather?", allocation.userMessage());
        // noMemory should return empty episodic and knowledge
        assertTrue(allocation.episodicSummaries().isEmpty());
        assertTrue(allocation.knowledge().isEmpty());
        // references and tool descriptions are preserved from request
        assertEquals(List.of("ref1"), allocation.references());
        assertEquals(List.of("tool: getWeather"), allocation.toolDescriptions());
        // budget math: 3000 - 100 = 2900 remaining
        assertEquals(100, allocation.estimatedTotal());
        assertEquals(2900, allocation.budgetRemaining());
        assertFalse(allocation.wasTruncated());
    }
}
