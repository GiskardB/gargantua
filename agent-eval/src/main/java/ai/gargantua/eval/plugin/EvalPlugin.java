package ai.gargantua.eval.plugin;

/**
 * Extension point for custom evaluation logic. Implement this interface
 * and place the JAR on the classpath to add custom scoring.
 *
 * Plugins are discovered via ServiceLoader (META-INF/services).
 *
 * Built-in plugins: LlmJudgePlugin, KeywordMatchPlugin, RegexPlugin
 */
public interface EvalPlugin {
    /** Unique name of this plugin (e.g., "llm-judge", "toxicity", "regex"). */
    String name();

    /** Human-readable description. */
    String description();

    /** Score a single eval case. Return 0.0-1.0. */
    PluginResult evaluate(PluginContext context);
}
