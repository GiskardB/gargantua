package ai.gargantua.memory.composer;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.KnowledgeSegment;
import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.memory.SessionSummary;
import ai.gargantua.core.memory.WorkingMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Composes all three memory layers (working, episodic, knowledge) in parallel,
 * then applies priority-based token truncation.
 *
 * <p>Priority order (highest first): working &gt; episodic &gt; knowledge.
 * When the total exceeds {@code maxContextTokens}, knowledge segments are
 * trimmed first, then episodic summaries (oldest first).</p>
 *
 * <p>A skill can opt out of layers it doesn't need by passing a restricted
 * {@link MemoryLayer} set to {@link #compose(String, String, int, Set)} —
 * useful for stateless skills (greetings, simple Q&amp;A) that don't benefit
 * from past sessions or stored user knowledge. Skipped layers avoid the
 * Redis/MongoDB round-trip entirely.</p>
 */
public class MemoryComposer {

    private static final Logger log = LoggerFactory.getLogger(MemoryComposer.class);

    private static final Set<MemoryLayer> ALL_LAYERS = EnumSet.allOf(MemoryLayer.class);

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
        return compose(userId, sessionId, maxTokens, ALL_LAYERS);
    }

    /**
     * Composes memory from the {@code enabledLayers} only, fetching in parallel.
     * Disabled layers are returned as empty lists; their backing port is never called.
     *
     * @param enabledLayers layers to fetch; {@code null} or empty is treated as
     *                      "all layers" for backward-compatibility.
     */
    public ComposedMemory compose(String userId, String sessionId, int maxTokens, Set<MemoryLayer> enabledLayers) {
        Set<MemoryLayer> layers = (enabledLayers == null || enabledLayers.isEmpty())
                ? ALL_LAYERS
                : enabledLayers;

        int budget = Math.min(maxTokens, maxContextTokens);

        log.debug("[MemoryComposer] Composing memory for userId={}, sessionId={}, budget={}, layers={}",
                userId, sessionId, budget, layers);

        // Parallel fetch — only for enabled layers
        CompletableFuture<List<ChatMessage>> workingFuture = layers.contains(MemoryLayer.WORKING)
                ? CompletableFuture.supplyAsync(() -> workingMemory.getMessages(sessionId))
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<SessionSummary>> episodicFuture = layers.contains(MemoryLayer.EPISODIC)
                ? CompletableFuture.supplyAsync(() -> episodicMemory.getRecentSummaries(userId, 10))
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<KnowledgeSegment>> knowledgeFuture = layers.contains(MemoryLayer.KNOWLEDGE)
                ? CompletableFuture.supplyAsync(() -> knowledgeMemory.getSegments(userId))
                : CompletableFuture.completedFuture(List.of());

        CompletableFuture.allOf(workingFuture, episodicFuture, knowledgeFuture).join();

        List<ChatMessage> workingMessages = workingFuture.join();
        List<SessionSummary> episodicSummaries = new ArrayList<>(episodicFuture.join());
        List<KnowledgeSegment> knowledgeSegments = new ArrayList<>(knowledgeFuture.join());

        // Calculate token usage per layer
        int workingTokens = estimateTokens(workingMessages.stream()
                .map(m -> "%s: %s".formatted(m.role(), m.content()))
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
