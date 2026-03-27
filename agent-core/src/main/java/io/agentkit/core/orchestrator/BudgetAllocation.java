package io.agentkit.core.orchestrator;

import io.agentkit.core.memory.KnowledgeSegment;

import java.util.List;

public record BudgetAllocation(
        String systemPrompt,
        List<String> references,
        List<String> episodicSummaries,
        List<KnowledgeSegment> knowledge,
        List<String> toolDescriptions,
        String userMessage,
        int estimatedTotal,
        int budgetRemaining,
        boolean wasTruncated,
        List<String> truncationLog
) {

    public static BudgetAllocation noMemory(BudgetRequest req, int fixedTokens) {
        return new BudgetAllocation(
                req.systemPrompt(),
                req.references(),
                List.of(),
                List.of(),
                req.toolDescriptions(),
                req.userMessage(),
                fixedTokens,
                req.maxContextTokens() - fixedTokens,
                false,
                List.of()
        );
    }
}
