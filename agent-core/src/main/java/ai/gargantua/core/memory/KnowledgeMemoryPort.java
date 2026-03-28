package ai.gargantua.core.memory;

import java.util.List;

/**
 * Port for persistent per-user knowledge (facts, preferences, profiles).
 * Unlike episodic memory, knowledge segments are explicitly managed (CRUD)
 * and never auto-expire.
 *
 * <p>Default implementation: MongoDB-backed, stored in the {@code user_knowledge} collection.</p>
 *
 * @see ai.gargantua.memory.adapters.mongo.MongoKnowledgeMemoryAdapter
 * @see KnowledgeSegment
 */
public interface KnowledgeMemoryPort {

    /** Returns all knowledge segments for the given user. */
    List<KnowledgeSegment> getSegments(String userId);

    /** Creates or updates a knowledge segment identified by the segment key. */
    void upsertSegment(String userId, String segmentKey, String content);

    /** Deletes a specific knowledge segment. */
    void deleteSegment(String userId, String segmentKey);
}
