package ai.gargantua.core.orchestrator;

public interface OrchestratorEngine {

    AgentResponse invoke(AgentRequest request);
}
