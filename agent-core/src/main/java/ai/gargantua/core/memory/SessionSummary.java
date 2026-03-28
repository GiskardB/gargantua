package ai.gargantua.core.memory;

import java.time.Instant;
import java.util.List;

/**
 * Compressed summary of a past conversation session. Created by the
 * {@link ai.gargantua.core.session.SessionSummarizer} when working memory expires,
 * and stored in episodic memory for long-term recall.
 *
 * @param userId          the user who participated in the session
 * @param sessionId       original session identifier
 * @param summary         LLM-generated summary of the conversation
 * @param keyTopics       extracted topic keywords for search/filtering
 * @param unresolvedItems open questions or tasks from the session
 * @param messageCount    how many messages were in the original session
 * @param sessionDate     when the session started
 * @param expiresAt       optional TTL for the summary itself (null = never expires)
 *
 * @see EpisodicMemoryPort
 */
public record SessionSummary(
        String userId,
        String sessionId,
        String summary,
        List<String> keyTopics,
        List<String> unresolvedItems,
        int messageCount,
        Instant sessionDate,
        Instant expiresAt
) {

    public SessionSummary(String userId, String sessionId, String summary,
                          List<String> keyTopics, List<String> unresolvedItems,
                          int messageCount, Instant sessionDate) {
        this(userId, sessionId, summary, keyTopics, unresolvedItems, messageCount, sessionDate, null);
    }
}
