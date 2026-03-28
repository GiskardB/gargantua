package ai.gargantua.core.memory;

import java.util.List;

public interface KnowledgeMemoryPort {

    List<KnowledgeSegment> getSegments(String userId);

    void upsertSegment(String userId, String segmentKey, String content);

    void deleteSegment(String userId, String segmentKey);
}
