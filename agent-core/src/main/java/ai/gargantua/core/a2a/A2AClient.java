package ai.gargantua.core.a2a;

import java.util.Optional;

/**
 * Client for invoking remote A2A-compatible agents.
 * Use this to delegate tasks to other agents in a multi-agent system.
 */
public interface A2AClient {

    /**
     * Discover a remote agent's capabilities by fetching its Agent Card.
     *
     * @param agentUrl base URL of the remote agent
     * @return the agent's {@link AgentCard}
     */
    AgentCard discover(String agentUrl);

    /**
     * Send a task to a remote agent and wait for the result.
     *
     * @param agentUrl   base URL of the remote agent
     * @param message    the task message
     * @param skillHint  optional hint to route to a specific skill (may be null)
     * @return the completed (or failed) task
     */
    A2ATask sendTask(String agentUrl, String message, String skillHint);

    /**
     * Get the status of a previously submitted task.
     *
     * @param agentUrl base URL of the remote agent
     * @param taskId   the task identifier
     * @return the task if found, or empty
     */
    Optional<A2ATask> getTask(String agentUrl, String taskId);

    /**
     * Cancel a running task.
     *
     * @param agentUrl base URL of the remote agent
     * @param taskId   the task identifier
     */
    void cancelTask(String agentUrl, String taskId);
}
