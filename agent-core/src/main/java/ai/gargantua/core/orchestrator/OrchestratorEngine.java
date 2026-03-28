package ai.gargantua.core.orchestrator;

/**
 * The central orchestration engine that processes agent requests through the full pipeline:
 * guardrails -> routing -> skill activation -> memory composition -> LLM call -> output guardrails.
 *
 * <p>Default implementation: {@link ai.gargantua.autoconfigure.DefaultOrchestratorEngine}</p>
 *
 * @see AgentRequest
 * @see AgentResponse
 */
public interface OrchestratorEngine {

    /**
     * Processes a single agent request end-to-end and returns the response.
     *
     * @param request the incoming user message with routing hints and context
     * @return the agent's response including metadata (tokens, cost, timing)
     * @throws ai.gargantua.core.exception.GuardrailBlockedException if an input guardrail blocks the request
     * @throws ai.gargantua.core.exception.SkillNotFoundException if the routed skill does not exist
     */
    AgentResponse invoke(AgentRequest request);
}
