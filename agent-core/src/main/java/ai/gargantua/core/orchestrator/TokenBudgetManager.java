package ai.gargantua.core.orchestrator;

/**
 * Port for token estimation and context window budget allocation.
 * Ensures the composed prompt (system + memory + tools + user message)
 * fits within the model's context window.
 *
 * <p>Default implementation: {@link ai.gargantua.autoconfigure.DefaultTokenBudgetManager},
 * which uses a {@code text.length() / 4} heuristic and a priority-based truncation strategy.</p>
 *
 * @see BudgetRequest
 * @see BudgetAllocation
 */
public interface TokenBudgetManager {

    /** Estimates token count for the given text. */
    int estimate(String text);

    /**
     * Allocates the context window budget across all prompt components.
     * Truncates lower-priority sections (knowledge, then episodic) when the budget is exceeded.
     *
     * @throws ai.gargantua.core.exception.TokenBudgetExceededException
     *         if even the fixed-cost components (system prompt + user message) exceed the budget
     */
    BudgetAllocation allocate(BudgetRequest request);
}
