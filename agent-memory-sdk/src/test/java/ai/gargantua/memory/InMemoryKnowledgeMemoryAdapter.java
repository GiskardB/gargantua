package ai.gargantua.memory;

import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.KnowledgeSegment;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test stub for {@link KnowledgeMemoryPort}.
 */
public class InMemoryKnowledgeMemoryAdapter implements KnowledgeMemoryPort {

    // Key: userId -> (segmentKey -> KnowledgeSegment)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, KnowledgeSegment>> store =
            new ConcurrentHashMap<>();

    @Override
    public List<KnowledgeSegment> getSegments(String userId) {
        var segments = store.get(userId);
        if (segments == null) {
            return List.of();
        }
        return List.copyOf(segments.values());
    }

    @Override
    public void upsertSegment(String userId, String segmentKey, String content) {
        store.computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
                .put(segmentKey, new KnowledgeSegment(userId, segmentKey, content, Instant.now(), "test"));
    }

    @Override
    public void deleteSegment(String userId, String segmentKey) {
        var segments = store.get(userId);
        if (segments != null) {
            segments.remove(segmentKey);
        }
    }
}
