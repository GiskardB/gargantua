package io.agentkit.memory.composer;

import io.agentkit.core.memory.ChatMessage;
import io.agentkit.core.memory.ComposedMemory;
import io.agentkit.core.memory.EpisodicMemoryPort;
import io.agentkit.core.memory.KnowledgeMemoryPort;
import io.agentkit.core.memory.KnowledgeSegment;
import io.agentkit.core.memory.SessionSummary;
import io.agentkit.core.memory.WorkingMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Composes all three memory layers (working, episodic, knowledge) in parallel,
 * then applies priority-based token truncation.
 *
 * <p>Priority order (highest first): working > episodic > knowledge.
 * When the total exceeds {@code maxContextTokens}, knowledge segments are
 * trimmed first, then episodic summaries (oldest first).
 */
public class MemoryComposer {

    private static final Logger log = LoggerFactory.getLogger(MemoryComposer.class);

    private final WorkingMemoryPort workingMemory;
    private final EpisodicMemoryPort episodicMemory;
    private final KnowledgeMemoryPort knowledgeMemory;
    private final int maxContextTokens;

    public MemoryComposer(WorkingMemoryPort workingMemory,
                          EpisodicMemoryPort episodicMemory,
                          KnowledgeMemoryPort knowledgeMemory,
                          int maxContextTokens) {
        this.workingMemory = workingMemory;
        this.episodicMemory = episodicMemory;
        this.knowledgeMemory = knowledgeMemory;
        this.maxContextTokens = maxContextTokens;
    }

    /**
     * Composes memory from all three layers, fetching in parallel, then
     * truncating to fit within the token budget.
     */
    public ComposedMemory compose(String userId, String sessionId, int maxTokens) {
        int budget = Math.min(maxTokens, maxContextTokens);

        log.debug("[MemoryComposer] Composing memory for userId={}, sessionId={}, budget={}",
                userId, sessionId, budget);

        // Parallel fetch
        var workingFuture = CompletableFuture.supplyAsync(() ->
                workingMemory.getMessages(sessionId));
        var episodicFuture = CompletableFuture.supplyAsync(() ->
                episodicMemory.getRecentSummaries(userId, 10));
        var knowledgeFuture = CompletableFuture.supplyAsync(() ->
                knowledgeMemory.getSegments(userId));

        CompletableFuture.allOf(workingFuture, episodicFuture, knowledgeFuture).join();

        List<ChatMessage> workingMessages = workingFuture.join();
        List<SessionSummary> episodicSummaries = new ArrayList<>(episodicFuture.join());
        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>(knowledgeFuture.join());

        // Calculate token usage per layer
        int workingTokens = estimateTokens(workingMessages.stream()
                .map(m -> m.role() + ": " + m.content())
                .toList());
        int episodicTokens = estimateTokens(episodicSummaries.stream()
                .map(SessionSummary::summary)
                .toList());
        int knowledgeTokens = estimateTokens(knowledgeSegments.stream()
                .map(KnowledgeSegment::content)
                .toList());

        int total = workingTokens + episodicTokens + knowledgeTokens;

        log.debug("[MemoryComposer] Raw tokens: working={}, episodic={}, knowledge={}, total={}",
                workingTokens, episodicTokens, knowledgeTokens, total);

        // Truncate knowledge first
        if (total > budget && !knowledgeSegments.isEmpty()) {
            while (total > budget && !knowledgeSegments.isEmpty()) {
                KnowledgeSegment removed = knowledgeSegments.removeLast();
                int removedTokens = estimateTokensSingle(removed.content());
                knowledgeTokens -= removedTokens;
                total -= removedTokens;
            }
            log.debug("[MemoryComposer] After knowledge truncation: total={}", total);
        }

        // Truncate episodic (oldest first - list is sorted newest-first, so remove from end)
        if (total > budget && !episodicSummaries.isEmpty()) {
            while (total > budget && !episodicSummaries.isEmpty()) {
                SessionSummary removed = episodicSummaries.removeLast();
                int removedTokens = estimateTokensSingle(removed.summary());
                episodicTokens -= removedTokens;
                total -= removedTokens;
            }
            log.debug("[MemoryComposer] After episodic truncation: total={}", total);
        }

        int estimatedTokens = workingTokens + episodicTokens + knowledgeTokens;
        log.info("[MemoryComposer] Composed memory: working={} msgs, episodic={} summaries, " +
                        "knowledge={} segments, estimatedTokens={}",
                workingMessages.size(), episodicSummaries.size(),
                knowledgeSegments.size(), estimatedTokens);

        return new ComposedMemory(
                workingMessages,
                List.copyOf(episodicSummaries),
                List.copyOf(knowledgeSegments),
                estimatedTokens
        );
    }

    /**
     * Rough token estimation: text length / 4.
     */
    private int estimateTokens(List<String> texts) {
        return texts.stream().mapToInt(this::estimateTokensSingle).sum();
    }

    private int estimateTokensSingle(String text) {
        return (text == null || text.isEmpty()) ? 0 : text.length() / 4;
    }
}
