package ai.gargantua.core.exception;

/**
 * Thrown when the fixed-cost prompt components (system prompt + user message + tools)
 * alone exceed the configured context window. This means even after removing all
 * optional memory, the prompt cannot fit.
 */
public class TokenBudgetExceededException extends RuntimeException {

    private final int fixedTokens;
    private final int maxTokens;

    public TokenBudgetExceededException(int fixedTokens, int maxTokens) {
        super("Token budget exceeded: fixed tokens (" + fixedTokens + ") exceed max tokens (" + maxTokens + ")");
        this.fixedTokens = fixedTokens;
        this.maxTokens = maxTokens;
    }

    public int getFixedTokens() {
        return fixedTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }
}
