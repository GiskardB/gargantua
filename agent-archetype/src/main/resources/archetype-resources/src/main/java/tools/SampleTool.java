package ${package}.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

/**
 * Sample tool — replace with your own business logic.
 *
 * Each public method annotated with @AgentTool becomes available
 * to skills that list its name in their allowed-tools frontmatter.
 */
@Component
public class SampleTool {

    public record SampleResult(String query, String answer, long timestamp) {}

    @AgentTool(description = """
        Answers a factual question by looking it up in the knowledge base.
        Use when the user asks a specific question that requires a lookup.
        Do NOT use for casual conversation or greetings.
        """)
    @ToolRetry(maxAttempts = 2, waitDurationMs = 300)
    public SampleResult lookup(String query) {
        // TODO: Replace with real implementation
        return new SampleResult(
            query,
            "This is a sample response for: " + query,
            System.currentTimeMillis()
        );
    }
}
