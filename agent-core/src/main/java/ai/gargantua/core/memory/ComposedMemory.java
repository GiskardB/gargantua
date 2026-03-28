package ai.gargantua.core.memory;

import java.util.List;

/**
 * The merged output of all three memory layers, ready to be injected into the prompt.
 * Produced by the {@code MemoryComposer} after parallel fetching and token-budget truncation.
 *
 * @param workingMessages   current conversation messages (highest priority, never truncated)
 * @param episodicSummaries past session summaries (truncated oldest-first if over budget)
 * @param knowledgeSegments user knowledge segments (truncated first when budget is tight)
 * @param estimatedTokens   total estimated token count across all layers
 *
 * @see ai.gargantua.memory.composer.MemoryComposer
 */
public record ComposedMemory(
        List<ChatMessage> workingMessages,
        List<SessionSummary> episodicSummaries,
        List<KnowledgeSegment> knowledgeSegments,
        int estimatedTokens
) {
}
