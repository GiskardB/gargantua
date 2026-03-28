package ai.gargantua.core.memory;

import java.time.Instant;

/**
 * A persistent piece of user knowledge (e.g. "prefers formal tone", "works in finance").
 * Stored in MongoDB and injected into the prompt when the user interacts with the agent.
 *
 * @param userId     the user this knowledge belongs to
 * @param segmentKey unique key within the user's knowledge (e.g. "preferences", "profile")
 * @param content    the knowledge text
 * @param updatedAt  last modification timestamp
 * @param source     origin of this knowledge (e.g. "user", "agent", "admin")
 *
 * @see KnowledgeMemoryPort
 */
public record KnowledgeSegment(
        String userId,
        String segmentKey,
        String content,
        Instant updatedAt,
        String source
) {
}
