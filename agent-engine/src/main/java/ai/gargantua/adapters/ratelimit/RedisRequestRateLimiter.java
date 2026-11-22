package ai.gargantua.adapters.ratelimit;

import ai.gargantua.autoconfigure.RequestRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Sliding-window limiter shared by every replica of the agent: one sorted set per key,
 * scored by request time. Trim, count and record happen in a single Lua script, so two
 * replicas admitting the same caller at the same instant cannot both slip under the limit.
 *
 * <p>Fails open: if Redis cannot be reached the request is admitted and a warning logged.
 * A rate limiter that turns a Redis outage into a full outage of the agent protects less
 * than it costs.</p>
 */
public class RedisRequestRateLimiter implements RequestRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRequestRateLimiter.class);

    private static final String KEY_PREFIX = "rate_limit:";

    // KEYS[1] window set. ARGV: now (ms), window (ms), max requests, unique member.
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))
            if redis.call('ZCARD', KEYS[1]) < tonumber(ARGV[3]) then
              redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRequestRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        try {
            Long admitted = redis.execute(ACQUIRE, List.of(KEY_PREFIX + key),
                    Long.toString(System.currentTimeMillis()),
                    Long.toString(window.toMillis()),
                    Integer.toString(maxRequests),
                    UUID.randomUUID().toString());
            return admitted == null || admitted == 1L;
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, admitting request for '{}': {}", key, e.getMessage());
            return true;
        }
    }
}
