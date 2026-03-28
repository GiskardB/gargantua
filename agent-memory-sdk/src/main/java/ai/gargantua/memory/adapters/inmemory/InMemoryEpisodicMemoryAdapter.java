package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.SessionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link EpisodicMemoryPort} for embedded mode.
 * Stores session summaries in a {@link ConcurrentHashMap} keyed by user ID.
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 *
 * @see EpisodicMemoryPort
 */
public class InMemoryEpisodicMemoryAdapter implements EpisodicMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEpisodicMemoryAdapter.class);

    private final ConcurrentHashMap<String, List<SessionSummary>> store = new ConcurrentHashMap<>();

    public InMemoryEpisodicMemoryAdapter() {
        log.info("[InMemoryEpisodicMemory] Initialized");
    }

    @Override
    public List<SessionSummary> getRecentSummaries(String userId, int limit) {
        List<SessionSummary> summaries = store.get(userId);
        if (summaries == null) {
            log.debug("[InMemoryEpisodicMemory] No summaries found for userId={}", userId);
            return List.of();
        }
        synchronized (summaries) {
            List<SessionSummary> result = summaries.stream()
                    .sorted(Comparator.comparing(SessionSummary::sessionDate).reversed())
                    .limit(limit)
                    .toList();
            log.debug("[InMemoryEpisodicMemory] Retrieved {} summaries for userId={} (limit={})",
                      result.size(), userId, limit);
            return result;
        }
    }

    @Override
    public void saveSummary(SessionSummary summary) {
        store.compute(summary.userId(), (key, existing) -> {
            List<SessionSummary> summaries = existing != null ? existing : new ArrayList<>();
            summaries.add(summary);
            return summaries;
        });
        log.debug("[InMemoryEpisodicMemory] Saved summary for userId={}, sessionDate={}",
                  summary.userId(), summary.sessionDate());
    }
}
