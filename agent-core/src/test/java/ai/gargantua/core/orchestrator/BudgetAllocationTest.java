package ai.gargantua.core.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgetAllocationTest {

    @Test
    void noMemoryFactoryMethod() {
        BudgetAllocation allocation = BudgetAllocation.noMemory();

        assertNotNull(allocation);
        assertTrue(allocation.episodicMemory().isEmpty());
        assertTrue(allocation.knowledgeMemory().isEmpty());
        assertTrue(allocation.workingMemory().isEmpty());
    }
}
