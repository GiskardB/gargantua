package io.agentkit.core.exception;

public class RateLimitExceededException extends RuntimeException {

    private final String userId;
    private final int limit;

    public RateLimitExceededException(String userId, int limit) {
        super("Rate limit exceeded for user '" + userId + "': limit is " + limit);
        this.userId = userId;
        this.limit = limit;
    }

    public String getUserId() {
        return userId;
    }

    public int getLimit() {
        return limit;
    }
}
