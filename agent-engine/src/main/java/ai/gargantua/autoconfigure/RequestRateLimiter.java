package ai.gargantua.autoconfigure;

import java.time.Duration;

/**
 * Counts requests per key over a sliding window. The store behind it decides the scope
 * of the limit: in one JVM for {@code InMemoryRequestRateLimiter}, across every replica
 * of the agent for {@code RedisRequestRateLimiter}.
 */
public interface RequestRateLimiter {

    /**
     * Records one request for {@code key} if fewer than {@code maxRequests} were recorded
     * in the last {@code window}.
     *
     * @return {@code true} when the request was admitted and counted, {@code false} when
     *         the key is over its limit (in which case nothing is recorded)
     */
    boolean tryAcquire(String key, int maxRequests, Duration window);
}
