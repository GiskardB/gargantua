package ai.gargantua.core.orchestrator;

import java.util.List;

/**
 * Response returned when an agent completes a sub-task invoked via {@link AgentAsToolPort}.
 *
 * @param response     the agent's textual output
 * @param skillUsed    which skill the child agent activated
 * @param toolsCalled  tools the child agent invoked during execution
 * @param success      whether the invocation completed without error
 * @param errorMessage error details if {@code success} is false
 * @param durationMs   wall-clock time of the child agent invocation
 *
 * @see AgentAsToolPort
 */
public record AgentToolResponse(
        String response,
        String skillUsed,
        List<String> toolsCalled,
        boolean success,
        String errorMessage,
        long durationMs
) {
}
