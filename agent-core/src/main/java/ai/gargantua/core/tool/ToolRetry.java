package ai.gargantua.core.tool;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures automatic retry with exponential backoff for a tool method.
 * Useful for tools that call flaky external APIs (HTTP, gRPC, etc.).
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @AgentTool(description = "Fetches exchange rates")
 * @ToolRetry(maxAttempts = 5, retryOn = {SocketTimeoutException.class, IOException.class})
 * public ExchangeRate getRate(String currency) { ... }
 * }</pre>
 *
 * @see AgentTool
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolRetry {

    /** Maximum number of attempts (including the first call). */
    int maxAttempts() default 3;

    /** Initial wait between retries, in milliseconds. */
    long waitDurationMs() default 500;

    /** Multiplier applied to wait duration after each failed attempt. */
    double backoffMultiplier() default 2.0;

    /** Upper bound on wait duration to prevent excessive delays. */
    long maxWaitDurationMs() default 5000;

    /** Exception types that trigger a retry. */
    Class<? extends Throwable>[] retryOn() default {IOException.class};

    /** Exception types that immediately abort without retrying. */
    Class<? extends Throwable>[] abortOn() default {IllegalArgumentException.class};
}
