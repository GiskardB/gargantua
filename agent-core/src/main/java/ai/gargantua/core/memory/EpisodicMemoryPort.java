package ai.gargantua.core.memory;

import java.util.List;

/**
 * Port for long-term episodic memory (compressed conversation summaries).
 * Summaries are created when a working memory session expires and provide
 * the agent with context about past interactions.
 *
 * <p>Default implementation: MongoDB-backed, stored in the {@code session_summaries} collection.</p>
 *
 * @see ai.gargantua.memory.adapters.mongo.MongoEpisodicMemoryAdapter
 * @see SessionSummary
 */
public interface EpisodicMemoryPort {

    /** Returns the most recent summaries for a user, sorted newest-first. */
    List<SessionSummary> getRecentSummaries(String userId, int limit);

    /** Persists a new session summary (typically after working memory TTL expires). */
    void saveSummary(SessionSummary summary);
}
