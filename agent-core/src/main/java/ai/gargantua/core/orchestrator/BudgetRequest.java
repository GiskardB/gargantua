package ai.gargantua.core.orchestrator;

import ai.gargantua.core.memory.KnowledgeSegment;

import java.util.List;

/**
 * Input to the {@link TokenBudgetManager#allocate(BudgetRequest)} method.
 * Bundles all prompt components that compete for the context window budget.
 *
 * @param systemPrompt     the skill system prompt
 * @param enrichedContext   additional context from {@link ContextEnricher} plugins
 * @param references        reference documents from the skill card
 * @param episodicSummaries past conversation summaries from episodic memory
 * @param knowledge         user knowledge segments from knowledge memory
 * @param toolDescriptions  descriptions of available tools
 * @param userMessage       the current user message
 * @param maxContextTokens  hard ceiling for the total token count
 *
 * @see BudgetAllocation
 */
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
