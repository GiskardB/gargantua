package ai.gargantua.core.memory;

import java.time.Instant;

public record KnowledgeSegment(
        String userId,
        String segmentKey,
        String content,
        Instant updatedAt,
        String source
) {
}
