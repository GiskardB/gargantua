package io.agentkit.memory;

import io.agentkit.core.memory.EpisodicMemoryPort;
import io.agentkit.core.memory.SessionSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test stub for {@link EpisodicMemoryPort}.
 */
public class InMemoryEpisodicMemoryAdapter implements EpisodicMemoryPort {

    private final ConcurrentHashMap<String, List<SessionSummary>> store = new ConcurrentHashMap<>();

    @Override
    public List<SessionSummary> getRecentSummaries(String userId, int limit) {
        List<SessionSummary> summaries = store.get(userId);
        if (summaries == null) {
            return List.of();
        }
        return summaries.stream()
                .sorted(Comparator.comparing(SessionSummary::sessionDate).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void saveSummary(SessionSummary summary) {
        store.computeIfAbsent(summary.userId(), _ -> new ArrayList<>()).add(summary);
    }
}
