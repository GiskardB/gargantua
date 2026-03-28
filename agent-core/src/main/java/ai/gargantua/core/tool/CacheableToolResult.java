package ai.gargantua.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables caching of tool results to avoid redundant external calls.
 * The cache key is derived from the tool name plus the specified parameter values.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @AgentTool(description = "Gets stock price")
 * @CacheableToolResult(ttlSeconds = 60, keyParams = {"ticker"}, scope = CacheScope.GLOBAL)
 * public StockPrice getPrice(String ticker) { ... }
 * }</pre>
 *
 * @see CacheScope
 * @see AgentTool
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheableToolResult {

    /** How long the cached result remains valid, in seconds. */
    int ttlSeconds() default 300;

    /** Method parameter names used to build the cache key. Empty = all parameters. */
    String[] keyParams() default {};

    /** Isolation scope for the cache entry. */
    CacheScope scope() default CacheScope.GLOBAL;
}
