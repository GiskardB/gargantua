package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *       process-local Caffeine cache with per-entry TTL, active expiry and a
 *       bounded {@link #MAX_IN_MEMORY_ENTRIES maximum size} (so a high churn of
 *       distinct keys can never grow the heap without bound). Wired in embedded
 *       mode by {@code EmbeddedProfileAutoConfiguration} so
 *       {@code @CacheableToolResult} also works without Redis. Data is lost when
 *       the process restarts and is NOT shared across replicas.</li>
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

    /** Upper bound on entries held by the in-memory backend (LRU-evicted by Caffeine). */
    static final long MAX_IN_MEMORY_ENTRIES = 10_000;

    private final @Nullable StringRedisTemplate redis;
    private final @Nullable Cache<String, Entry> inMemory;

    /** Redis-backed constructor (production default when Redis is available). */
    public ToolResultCache(StringRedisTemplate redis) {
        this.redis = redis;
        this.inMemory = null;
    }

    /**
     * In-memory constructor — process-local Caffeine cache with per-entry TTL,
     * active expiry and a bounded size. Used in embedded mode and tests.
     * Data does not survive restart.
     */
    public ToolResultCache() {
        this.redis = null;
        this.inMemory = Caffeine.newBuilder()
                .maximumSize(MAX_IN_MEMORY_ENTRIES)
                .expireAfter(new Expiry<String, Entry>() {
                    @Override public long expireAfterCreate(String key, Entry e, long now) {
                        return e.ttlNanos();
                    }
                    @Override public long expireAfterUpdate(String key, Entry e, long now, long currentDuration) {
                        return e.ttlNanos();
                    }
                    @Override public long expireAfterRead(String key, Entry e, long now, long currentDuration) {
                        return currentDuration; // reads do not extend the TTL
                    }
                })
                .build();
    }

    /** Backing entry for the in-memory backend (carries its own TTL for variable expiry). */
    private record Entry(String value, long ttlNanos) {}

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
        Entry e = inMemory.getIfPresent(key);
        return e == null ? null : e.value();
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
        inMemory.put(key, new Entry(value, TimeUnit.SECONDS.toNanos(ttl)));
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

    // ── Admin / housekeeping API (1.2.6+) ──────────────────────────
    //
    // Both backends MUST honour these so the /api/admin/tool-cache/*
    // controllers work uniformly whether Redis is wired or not.

    /** Total number of entries currently held (in-memory) or visible (Redis). */
    public int size() {
        if (redis != null) {
            return scanKeys().size();
        }
        return inMemory.asMap().size();
    }

    /** All cache keys held by this instance (always under the {@code tool-cache:} prefix). */
    public Collection<String> keys() {
        if (redis != null) {
            return Collections.unmodifiableSet(scanKeys());
        }
        return List.copyOf(inMemory.asMap().keySet());
    }

    /**
     * Enumerate every {@code tool-cache:*} key using a non-blocking {@code SCAN}
     * cursor. Unlike {@code KEYS}, {@code SCAN} does not stall the Redis event
     * loop on large keyspaces, so the admin endpoints stay safe in production.
     * Returns an empty set on any Redis error.
     */
    private Set<String> scanKeys() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("[ToolCache] Redis SCAN failed: {}", e.getMessage());
        }
        return keys;
    }

    /** All cache keys for a specific tool name. */
    public Collection<String> keys(String toolName) {
        Collection<String> all = keys();
        List<String> match = new ArrayList<>();
        for (String key : all) {
            if (isKeyForTool(key, toolName)) {
                match.add(key);
            }
        }
        return Collections.unmodifiableList(match);
    }

    /** Clear every entry. Returns the number of entries removed. */
    public int clear() {
        if (redis != null) {
            try {
                Set<String> keys = scanKeys();
                if (keys.isEmpty()) return 0;
                Long deleted = redis.delete(keys);
                return deleted == null ? 0 : deleted.intValue();
            } catch (Exception e) {
                log.warn("[ToolCache] Redis DEL failed: {}", e.getMessage());
                return 0;
            }
        }
        var map = inMemory.asMap();
        int removed = map.size();
        inMemory.invalidateAll();
        return removed;
    }

    /** Clear every entry for a specific tool name. Returns the number removed. */
    public int clear(String toolName) {
        if (toolName == null || toolName.isBlank()) return 0;
        if (redis != null) {
            try {
                Collection<String> match = keys(toolName);
                if (match.isEmpty()) return 0;
                Long deleted = redis.delete(match);
                return deleted == null ? 0 : deleted.intValue();
            } catch (Exception e) {
                log.warn("[ToolCache] Redis DEL by tool failed: {}", e.getMessage());
                return 0;
            }
        }
        var map = inMemory.asMap();
        int before = map.size();
        map.keySet().removeIf(k -> isKeyForTool(k, toolName));
        return before - map.size();
    }

    /**
     * Match the key layout {@code tool-cache:<scope>:<tool>:[<userOrSession>:]<argsHash>}
     * (and also {@code tool-cache:user:<id>:<tool>:…} / {@code tool-cache:session:<id>:<tool>:…}).
     * We strip the prefix and then look for {@code :<toolName>:} or the bare
     * {@code <toolName>:} at any segment boundary.
     */
    private static boolean isKeyForTool(String key, String toolName) {
        if (!key.startsWith(KEY_PREFIX)) return false;
        String rest = key.substring(KEY_PREFIX.length());
        for (String segment : rest.split(":")) {
            if (segment.equals(toolName)) return true;
        }
        return false;
    }
}
