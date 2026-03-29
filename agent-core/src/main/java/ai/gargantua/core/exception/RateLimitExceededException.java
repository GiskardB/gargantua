package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when a user exceeds the configured request rate limit.
 * Handled by the exception handler to return a 429 response.
 */
public class RateLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final int limit;

    public RateLimitExceededException(String userId, int limit) {
        super("Rate limit exceeded for user '%s': limit is %d".formatted(userId, limit));
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.limit = limit;
    }

    public String getUserId() {
        return userId;
    }

    public int getLimit() {
        return limit;
    }
}
