package ai.gargantua.core.orchestrator;

import java.util.Map;

/**
 * Request sent when an agent is invoked as a tool by a parent agent.
 *
 * @param input            the sub-task description from the parent agent
 * @param userId           the original user identity (propagated for access control)
 * @param parentSessionId  the parent agent's session id (for tracing)
 * @param context          arbitrary context passed from the parent agent
 *
 * @see AgentAsToolPort
 * @see AgentToolResponse
 */
public record AgentToolRequest(
        String input,
        String userId,
        String parentSessionId,
        Map<String, Object> context
) {
}
