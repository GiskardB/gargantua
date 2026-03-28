package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.KnowledgeSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link KnowledgeMemoryPort} for embedded mode.
 * Stores knowledge segments in a nested {@link ConcurrentHashMap} keyed by
 * user ID and segment key.
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 *
 * @see KnowledgeMemoryPort
 */
public class InMemoryKnowledgeMemoryAdapter implements KnowledgeMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKnowledgeMemoryAdapter.class);

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, KnowledgeSegment>> store =
            new ConcurrentHashMap<>();

    public InMemoryKnowledgeMemoryAdapter() {
        log.info("[InMemoryKnowledgeMemory] Initialized");
    }

    @Override
    public List<KnowledgeSegment> getSegments(String userId) {
        var segments = store.get(userId);
        if (segments == null) {
            log.debug("[InMemoryKnowledgeMemory] No segments found for userId={}", userId);
            return List.of();
        }
        log.debug("[InMemoryKnowledgeMemory] Retrieved {} segments for userId={}", segments.size(), userId);
        return List.copyOf(segments.values());
    }

    @Override
    public void upsertSegment(String userId, String segmentKey, String content) {
        store.computeIfAbsent(userId, _ -> new ConcurrentHashMap<>())
                .put(segmentKey, new KnowledgeSegment(userId, segmentKey, content, Instant.now(), "embedded"));
        log.debug("[InMemoryKnowledgeMemory] Upserted segment userId={}, segmentKey={}", userId, segmentKey);
    }

    @Override
    public void deleteSegment(String userId, String segmentKey) {
        var segments = store.get(userId);
        if (segments != null) {
            segments.remove(segmentKey);
            log.debug("[InMemoryKnowledgeMemory] Deleted segment userId={}, segmentKey={}", userId, segmentKey);
        }
    }
}
