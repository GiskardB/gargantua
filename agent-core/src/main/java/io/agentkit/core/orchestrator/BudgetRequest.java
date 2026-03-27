package io.agentkit.core.orchestrator;

import io.agentkit.core.memory.KnowledgeSegment;

import java.util.List;

public record BudgetRequest(
        String systemPrompt,
        String enrichedContext,
        List<String> references,
        List<String> episodicSummaries,
        List<KnowledgeSegment> knowledge,
        List<String> toolDescriptions,
        String userMessage,
        int maxContextTokens
) {
}
