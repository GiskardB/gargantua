package ai.gargantua.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an agent tool -- a physical action the agent can perform.
 * The LLM uses the {@code description} to decide when to invoke this tool.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @AgentTool(description = "Fetches current weather for a city")
 * public WeatherResult getWeather(String city) { ... }
 * }</pre>
 *
 * <p>Tools are auto-discovered at boot by scanning all Spring beans.
 * Only tools listed in the skill's {@code allowed-tools} frontmatter are
 * available during a given request.</p>
 *
 * @see RequiresApproval
 * @see ToolRetry
 * @see CacheableToolResult
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /** Override the tool name. Defaults to the method name. */
    String name() default "";

    /** Description shown to the LLM so it knows when to call this tool. */
    String description();

    /** Whether this tool can run in parallel with other tool calls. */
    boolean parallelizable() default true;
}
