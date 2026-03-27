package io.agentkit.autoconfigure;

import io.agentkit.core.exception.TokenBudgetExceededException;
import io.agentkit.core.memory.KnowledgeSegment;
import io.agentkit.core.orchestrator.BudgetAllocation;
import io.agentkit.core.orchestrator.BudgetRequest;
import io.agentkit.core.orchestrator.TokenBudgetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Default token budget manager that estimates tokens as text.length()/4
 * and implements a truncation strategy when budget is exceeded.
 */
@Component
public class DefaultTokenBudgetManager implements TokenBudgetManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenBudgetManager.class);

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.length() / 4;
    }

    @Override
    public BudgetAllocation allocate(BudgetRequest request) {
        int maxTokens = request.maxContextTokens();
        List<String> truncationLog = new ArrayList<>();

        // Fixed costs: system prompt + user message + tool descriptions
        int systemTokens = estimate(request.systemPrompt());
        int userTokens = estimate(request.userMessage());
        int enricherTokens = estimate(request.enrichedContext());
        int toolTokens = request.toolDescriptions().stream().mapToInt(this::estimate).sum();

        int fixedTokens = systemTokens + userTokens + enricherTokens + toolTokens;

        if (fixedTokens > maxTokens) {
            throw new TokenBudgetExceededException(fixedTokens, maxTokens);
        }

        int remaining = maxTokens - fixedTokens;

        // Allocate references
        List<String> references = new ArrayList<>(request.references());
        int refTokens = references.stream().mapToInt(this::estimate).sum();
        if (refTokens > remaining / 3) {
            references = truncateList(references, remaining / 3, truncationLog, "references");
            refTokens = references.stream().mapToInt(this::estimate).sum();
        }
        remaining -= refTokens;

        // Allocate episodic summaries
        List<String> episodic = new ArrayList<>(request.episodicSummaries());
        int episodicTokens = episodic.stream().mapToInt(this::estimate).sum();
        if (episodicTokens > remaining / 2) {
            episodic = truncateList(episodic, remaining / 2, truncationLog, "episodic-summaries");
            episodicTokens = episodic.stream().mapToInt(this::estimate).sum();
        }
        remaining -= episodicTokens;

        // Allocate knowledge segments
        List<KnowledgeSegment> knowledge = new ArrayList<>(request.knowledge());
        int knowledgeTokens = knowledge.stream().mapToInt(k -> estimate(k.content())).sum();
        if (knowledgeTokens > remaining) {
            knowledge = truncateKnowledge(knowledge, remaining, truncationLog);
            knowledgeTokens = knowledge.stream().mapToInt(k -> estimate(k.content())).sum();
        }
        remaining -= knowledgeTokens;

        int estimatedTotal = maxTokens - remaining;
        boolean wasTruncated = !truncationLog.isEmpty();

        if (wasTruncated) {
            log.info("Token budget truncation applied: {}", truncationLog);
        }

        return new BudgetAllocation(
                request.systemPrompt(),
                references,
                episodic,
                knowledge,
                request.toolDescriptions(),
                request.userMessage(),
                estimatedTotal,
                remaining,
                wasTruncated,
                truncationLog
        );
    }

    private List<String> truncateList(List<String> items, int maxTokens, List<String> log, String label) {
        List<String> result = new ArrayList<>();
        int used = 0;
        for (String item : items) {
            int cost = estimate(item);
            if (used + cost > maxTokens) {
                log.add("Truncated " + label + ": kept " + result.size() + "/" + items.size());
                break;
            }
            result.add(item);
            used += cost;
        }
        return result;
    }

    private List<KnowledgeSegment> truncateKnowledge(List<KnowledgeSegment> items, int maxTokens,
                                                      List<String> log) {
        List<KnowledgeSegment> result = new ArrayList<>();
        int used = 0;
        for (KnowledgeSegment item : items) {
            int cost = estimate(item.content());
            if (used + cost > maxTokens) {
                log.add("Truncated knowledge: kept " + result.size() + "/" + items.size());
                break;
            }
            result.add(item);
            used += cost;
        }
        return result;
    }
}
