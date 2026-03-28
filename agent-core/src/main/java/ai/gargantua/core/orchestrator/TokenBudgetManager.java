package ai.gargantua.core.orchestrator;

public interface TokenBudgetManager {

    int estimate(String text);

    BudgetAllocation allocate(BudgetRequest request);
}
