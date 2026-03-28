package ai.gargantua.core.memory;

import java.time.Instant;
import java.util.List;

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
