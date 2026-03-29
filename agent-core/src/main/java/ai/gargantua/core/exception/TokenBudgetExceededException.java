package ai.gargantua.core.exception;

import java.io.Serial;

/**
 * Thrown when the fixed-cost prompt components (system prompt + user message + tools)
 * alone exceed the configured context window. This means even after removing all
 * optional memory, the prompt cannot fit.
 */
public class TokenBudgetExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int fixedTokens;
    private final int maxTokens;

    public TokenBudgetExceededException(int fixedTokens, int maxTokens) {
        super("Token budget exceeded: fixed tokens (%d) exceed max tokens (%d)".formatted(fixedTokens, maxTokens));
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
