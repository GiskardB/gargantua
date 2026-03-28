package ai.gargantua.core.memory;

import java.util.List;

public interface EpisodicMemoryPort {

    List<SessionSummary> getRecentSummaries(String userId, int limit);

    void saveSummary(SessionSummary summary);
}
