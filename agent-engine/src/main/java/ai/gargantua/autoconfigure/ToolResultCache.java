package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache for tool results, driven by {@link CacheableToolResult}. The class has
 * two interchangeable backends:
 *
 * <ul>
 *   <li><b>Redis</b> — constructed via {@link #ToolResultCache(StringRedisTemplate)}.
 *       Survives restarts and is shared across replicas. Wired automatically
 *       by {@link ToolCacheAutoConfiguration} when a {@code StringRedisTemplate}
 *       bean is present.</li>
 *   <li><b>In-memory</b> — constructed via {@link #ToolResultCache()}. A
 *       process-local {@link ConcurrentHashMap} with per-entry TTL.
 *       Wired in embedded mode by {@code EmbeddedProfileAutoConfiguration}
 *       so {@code @CacheableToolResult} also works without Redis. Data is
 *       lost when the process restarts and is NOT shared across replicas.</li>
 * </ul>
 *
 * <p>Cache keys live under the {@code tool-cache:} prefix so the existing
 * {@code /api/admin/tool-cache/*} endpoints can list and clear them.</p>
 *
 * <p>Key layout: {@code tool-cache:<scope>:<tool>:[<userOrSession>:]<argsHash>}.
 * The hash is SHA-256 over the JSON-serialised, sorted subset of arguments
 * declared in {@link CacheableToolResult#keyParams()} (or all arguments when
 * the array is empty).</p>
 */
public class ToolResultCache {

    private static final Logger log = LoggerFactory.getLogger(ToolResultCache.class);
    private static final String KEY_PREFIX = "tool-cache:";

    private final @Nullable StringRedisTemplate redis;
    private final @Nullable ConcurrentMap<String, Entry> inMemory;

    /** Redis-backed constructor (production default when Redis is available). */
    public ToolResultCache(StringRedisTemplate redis) {
        this.redis = redis;
        this.inMemory = null;
    }

    /**
     * In-memory constructor — process-local cache with per-entry TTL.
     * Used in embedded mode and tests. Data does not survive restart.
     */
    public ToolResultCache() {
        this.redis = null;
        this.inMemory = new ConcurrentHashMap<>();
    }

    /** Backing entry for the in-memory backend. */
    private record Entry(String value, long expiresAtNanos) {
        boolean isExpired() { return System.nanoTime() > expiresAtNanos; }
    }

    /**
     * Build the cache key for a specific tool invocation, or return {@code null}
     * if the scope cannot be honoured (e.g. SESSION scope without a sessionId).
     */
    public String buildKey(String toolName, Method method, Map<String, String> args,
                           CacheableToolResult ann, SecurityContext securityContext, String sessionId) {
        String scopeSegment = switch (ann.scope()) {
            case GLOBAL -> "global";
            case USER -> {
                if (securityContext == null || securityContext.userId() == null) {
                    yield null;
                }
                yield "user:" + securityContext.userId();
            }
            case SESSION -> {
                if (sessionId == null || sessionId.isBlank()) {
                    yield null;
                }
                yield "session:" + sessionId;
            }
        };
        if (scopeSegment == null) {
            return null;
        }
        String hash = hashArgs(method, args, ann.keyParams());
        return KEY_PREFIX + scopeSegment + ":" + toolName + ":" + hash;
    }

    public String get(String key) {
        if (redis != null) {
            try {
                return redis.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("[ToolCache] Redis GET failed for key={}: {}", key, e.getMessage());
                return null;
            }
        }
        Entry e = inMemory.get(key);
        if (e == null) {
            return null;
        }
        if (e.isExpired()) {
            inMemory.remove(key, e);
            return null;
        }
        return e.value();
    }

    public void put(String key, String value, int ttlSeconds) {
        int ttl = ttlSeconds > 0 ? ttlSeconds : 300;
        if (redis != null) {
            try {
                redis.opsForValue().set(key, value, Duration.ofSeconds(ttl));
            } catch (Exception e) {
                log.warn("[ToolCache] Redis SET failed for key={}: {}", key, e.getMessage());
            }
            return;
        }
        long expiresAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(ttl);
        inMemory.put(key, new Entry(value, expiresAt));
    }

    private String hashArgs(Method method, Map<String, String> args, String[] keyParams) {
        Set<String> include = (keyParams == null || keyParams.length == 0)
                ? null
                : Set.of(keyParams);
        Map<String, String> sorted = new LinkedHashMap<>();
        // iterate parameters in declaration order so missing keyParams collapse to ""
        for (Parameter p : method.getParameters()) {
            if (include != null && !include.contains(p.getName())) {
                continue;
            }
            sorted.put(p.getName(), args.getOrDefault(p.getName(), ""));
        }
        if (include == null && sorted.isEmpty()) {
            // method takes no parameters — still produce a stable digest
            sorted.put("__noargs__", "");
        }
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            canonical.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return sha256(canonical.toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE; fall back to a stable string hash if it ever isn't.
            return Integer.toHexString(input.hashCode());
        }
    }

    /** Convenience for tests/debug — list of supported scopes. */
    public List<CacheScope> supportedScopes() {
        return List.of(CacheScope.GLOBAL, CacheScope.USER, CacheScope.SESSION);
    }
}
