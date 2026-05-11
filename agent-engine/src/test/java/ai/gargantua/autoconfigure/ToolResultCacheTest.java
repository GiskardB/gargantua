package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the in-memory {@link ToolResultCache} backend introduced
 * in 1.2.5. The Redis backend is covered by integration tests; here we pin:
 *
 * <ul>
 *   <li>{@code get} on a missing key returns {@code null}.</li>
 *   <li>{@code put} → {@code get} on the same key returns the same value.</li>
 *   <li>An entry past its TTL is treated as missing (no stale reads).</li>
 *   <li>{@code buildKey} respects the {@link CacheScope} and the
 *       {@code keyParams} subset.</li>
 * </ul>
 */
@DisplayName("ToolResultCache (in-memory backend)")
class ToolResultCacheTest {

    @Nested
    @DisplayName("get / put / TTL")
    class GetPutTtl {

        @Test
        @DisplayName("missing key returns null")
        void missingKeyReturnsNull() {
            ToolResultCache cache = new ToolResultCache();
            assertThat(cache.get("nope")).isNull();
        }

        @Test
        @DisplayName("put then get returns the stored value")
        void putThenGet() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("k", "v", 60);
            assertThat(cache.get("k")).isEqualTo("v");
        }

        @Test
        @DisplayName("non-positive ttl falls back to the default 300s")
        void nonPositiveTtlFallsBack() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("k", "v", 0);
            assertThat(cache.get("k")).isEqualTo("v");
            cache.put("k2", "v2", -10);
            assertThat(cache.get("k2")).isEqualTo("v2");
        }

        @Test
        @DisplayName("entry past its TTL is treated as missing")
        void expiredEntryIsMissing() throws InterruptedException {
            ToolResultCache cache = new ToolResultCache();
            cache.put("k", "v", 1); // 1 second TTL
            assertThat(cache.get("k")).isEqualTo("v");
            Thread.sleep(1_100);
            assertThat(cache.get("k"))
                    .as("entry should have expired after waiting 1.1s with 1s TTL")
                    .isNull();
        }

        @Test
        @DisplayName("a second put on the same key replaces the value")
        void putReplaces() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("k", "v1", 60);
            cache.put("k", "v2", 60);
            assertThat(cache.get("k")).isEqualTo("v2");
        }
    }

    @Nested
    @DisplayName("admin / housekeeping API (1.2.6)")
    class AdminApi {

        @Test
        @DisplayName("size() reflects the number of held entries")
        void sizeIsAccurate() {
            ToolResultCache cache = new ToolResultCache();
            assertThat(cache.size()).isEqualTo(0);
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            cache.put("tool-cache:global:add:hash-b", "2", 60);
            assertThat(cache.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("keys() returns every cached key")
        void keysReturnsEverything() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            cache.put("tool-cache:global:mul:hash-b", "4", 60);
            assertThat(cache.keys())
                    .containsExactlyInAnyOrder(
                            "tool-cache:global:add:hash-a",
                            "tool-cache:global:mul:hash-b");
        }

        @Test
        @DisplayName("keys(toolName) filters by the tool segment in the key layout")
        void keysFiltersByTool() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            cache.put("tool-cache:global:add:hash-b", "2", 60);
            cache.put("tool-cache:user:alice:add:hash-c", "3", 60);
            cache.put("tool-cache:global:mul:hash-d", "4", 60);

            assertThat(cache.keys("add"))
                    .containsExactlyInAnyOrder(
                            "tool-cache:global:add:hash-a",
                            "tool-cache:global:add:hash-b",
                            "tool-cache:user:alice:add:hash-c");
            assertThat(cache.keys("mul"))
                    .containsExactly("tool-cache:global:mul:hash-d");
            assertThat(cache.keys("missing")).isEmpty();
        }

        @Test
        @DisplayName("clear() empties the cache and returns the prior size")
        void clearAllReturnsCount() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            cache.put("tool-cache:global:add:hash-b", "2", 60);

            assertThat(cache.clear()).isEqualTo(2);
            assertThat(cache.size()).isEqualTo(0);
            assertThat(cache.get("tool-cache:global:add:hash-a")).isNull();
        }

        @Test
        @DisplayName("clear(toolName) removes only that tool's entries and returns the count")
        void clearByToolRemovesOnlyMatching() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            cache.put("tool-cache:global:mul:hash-b", "4", 60);
            cache.put("tool-cache:user:alice:add:hash-c", "9", 60);

            assertThat(cache.clear("add")).isEqualTo(2);
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.get("tool-cache:global:mul:hash-b")).isEqualTo("4");
        }

        @Test
        @DisplayName("clear(null) and clear(\"\") are no-ops")
        void clearWithBlankToolNameIsNoop() {
            ToolResultCache cache = new ToolResultCache();
            cache.put("tool-cache:global:add:hash-a", "1", 60);
            assertThat(cache.clear(null)).isEqualTo(0);
            assertThat(cache.clear("")).isEqualTo(0);
            assertThat(cache.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("buildKey")
    class BuildKey {

        // Sample method whose parameter names drive hash computation.
        static class Sample {
            @SuppressWarnings("unused")
            public String price(String ticker, String currency, int days) { return ""; }
        }

        private final Method method = Arrays.stream(Sample.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("price"))
                .findFirst().orElseThrow();
        private final ToolResultCache cache = new ToolResultCache();

        @Test
        @DisplayName("GLOBAL scope key includes 'global:' segment")
        void globalScope() {
            String key = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL", "currency", "USD", "days", "5"),
                    ann(CacheScope.GLOBAL, new String[]{}),
                    null, null);
            assertThat(key).startsWith("tool-cache:global:price:");
        }

        @Test
        @DisplayName("USER scope without security context yields null (cannot honour)")
        void userScopeWithoutSecurityContextYieldsNull() {
            String key = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL"),
                    ann(CacheScope.USER, new String[]{}),
                    null, null);
            assertThat(key).isNull();
        }

        @Test
        @DisplayName("USER scope embeds user id when security context is present")
        void userScopeIncludesUserId() {
            SecurityContext ctx = new SecurityContext("alice", null, Set.of("user"));
            String key = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL"),
                    ann(CacheScope.USER, new String[]{}),
                    ctx, null);
            assertThat(key).startsWith("tool-cache:user:alice:price:");
        }

        @Test
        @DisplayName("SESSION scope without session id yields null")
        void sessionScopeWithoutSessionIdYieldsNull() {
            String key = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL"),
                    ann(CacheScope.SESSION, new String[]{}),
                    null, null);
            assertThat(key).isNull();
        }

        @Test
        @DisplayName("keyParams subset: only listed parameters affect the hash")
        void keyParamsSubsetControlsHash() {
            // Two calls that differ only in 'currency' but keyParams=['ticker'] should hash equal.
            String key1 = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL", "currency", "USD", "days", "5"),
                    ann(CacheScope.GLOBAL, new String[]{"ticker"}),
                    null, null);
            String key2 = cache.buildKey("price", method,
                    Map.of("ticker", "AAPL", "currency", "EUR", "days", "5"),
                    ann(CacheScope.GLOBAL, new String[]{"ticker"}),
                    null, null);
            assertThat(key1).isEqualTo(key2);

            // Different ticker → different hash.
            String key3 = cache.buildKey("price", method,
                    Map.of("ticker", "MSFT", "currency", "USD", "days", "5"),
                    ann(CacheScope.GLOBAL, new String[]{"ticker"}),
                    null, null);
            assertThat(key3).isNotEqualTo(key1);
        }
    }

    // ── tiny annotation literal so we can construct CacheableToolResult instances in tests ──
    private static CacheableToolResult ann(CacheScope scope, String[] keyParams) {
        return new CacheableToolResult() {
            @Override public int ttlSeconds() { return 60; }
            @Override public String[] keyParams() { return keyParams; }
            @Override public CacheScope scope() { return scope; }
            @Override public Class<? extends Annotation> annotationType() { return CacheableToolResult.class; }
        };
    }
}
