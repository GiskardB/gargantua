package io.agentkit.core.orchestrator;

public interface OrchestratorEngine {

    AgentResponse invoke(AgentRequest request);
}
