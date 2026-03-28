package ai.gargantua.core.orchestrator;

/**
 * Port for exposing an agent as a tool that other agents can invoke.
 * Enables multi-agent architectures where one agent delegates sub-tasks
 * to specialized agents.
 *
 * @see AgentToolRequest
 * @see AgentToolResponse
 */
public interface AgentAsToolPort {

    String agentName();

    String description();

    AgentToolResponse invoke(AgentToolRequest request);
}
