package ai.gargantua.core.orchestrator;

import ai.gargantua.core.memory.KnowledgeSegment;

import java.util.List;

/**
 * Result of token budget allocation. Contains the (possibly truncated) prompt components
 * that fit within the model's context window, plus a log of what was trimmed.
 *
 * @param systemPrompt      the skill's system prompt (never truncated -- it is fixed cost)
 * @param references        reference documents, possibly truncated to fit budget
 * @param episodicSummaries past conversation summaries, trimmed oldest-first if over budget
 * @param knowledge         user knowledge segments, trimmed first when budget is tight
 * @param toolDescriptions  tool descriptions (never truncated -- they are fixed cost)
 * @param userMessage       the current user message (never truncated)
 * @param estimatedTotal    total estimated tokens after allocation
 * @param budgetRemaining   tokens still available after allocation
 * @param wasTruncated      true if any section was trimmed
 * @param truncationLog     human-readable log of what was removed
 *
 * @see BudgetRequest
 * @see TokenBudgetManager
 */
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
