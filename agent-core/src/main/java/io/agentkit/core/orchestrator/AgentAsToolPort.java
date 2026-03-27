package io.agentkit.core.orchestrator;

public interface AgentAsToolPort {

    String agentName();

    String description();

    AgentToolResponse invoke(AgentToolRequest request);
}
