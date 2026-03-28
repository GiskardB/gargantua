package ai.gargantua.core.orchestrator;

import java.util.List;

/**
 * Immutable result returned by the {@link OrchestratorEngine} after processing a request.
 * Carries the LLM-generated text plus observability metadata (tokens, cost, timing, routing info).
 *
 * @param text              the final post-guardrail response text
 * @param sessionId         the conversation session this response belongs to
 * @param skillUsed         which skill was activated to handle the request
 * @param toolsCalled       names of tools invoked during the request (empty if none)
 * @param routingMethod     how the skill was selected (semantic, LLM, or forced)
 * @param routingConfidence confidence score from the router (1.0 for forced/LLM)
 * @param inputTokens       estimated input token count
 * @param outputTokens      estimated output token count
 * @param totalTokens       sum of input + output tokens
 * @param estimatedCostUsd  estimated USD cost based on pricing config
 * @param durationMs        wall-clock time for the entire pipeline
 * @param dryRun            whether this was a dry-run invocation (no side effects)
 *
 * @see AgentRequest
 */
public record AgentResponse(
        String text,
        String sessionId,
        String skillUsed,
        List<String> toolsCalled,
        RoutingMethod routingMethod,
        double routingConfidence,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        double estimatedCostUsd,
        long durationMs,
        boolean dryRun
) {
}
