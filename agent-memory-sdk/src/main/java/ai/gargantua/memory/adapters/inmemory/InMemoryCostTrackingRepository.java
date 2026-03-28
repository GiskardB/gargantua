package ai.gargantua.memory.adapters.inmemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory replacement for {@code MongoCostTrackingRepository} used in embedded mode.
 * Stores token usage documents in a {@link CopyOnWriteArrayList} and performs
 * aggregation queries in-process via the Stream API.
 *
 * <p>This provides the same query methods as the Mongo-backed repository so it
 * can be swapped in transparently when no database is available.</p>
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 */
public class InMemoryCostTrackingRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCostTrackingRepository.class);

    private final CopyOnWriteArrayList<TokenUsageDocument> store = new CopyOnWriteArrayList<>();

    public InMemoryCostTrackingRepository() {
        log.info("[InMemoryCostTrackingRepository] Initialized");
    }

    public void save(TokenUsageDocument document) {
        store.add(document);
        log.debug("[InMemoryCostTrackingRepository] Saved token usage: skill={}, provider={}, cost={}",
                  document.skillName(), document.provider(), document.estimatedCostUsd());
    }

    public List<Map> findSummary(Instant from, Instant to) {
        return store.stream()
                .filter(d -> !d.timestamp().isBefore(from) && !d.timestamp().isAfter(to))
                .collect(Collectors.groupingBy(d -> d.skillName() + "|" + d.provider()))
                .entrySet().stream()
                .map(entry -> {
                    List<TokenUsageDocument> docs = entry.getValue();
                    TokenUsageDocument first = docs.getFirst();
                    return Map.of(
                            "_id", Map.of("skillName", first.skillName(), "provider", first.provider()),
                            "totalInputTokens", docs.stream().mapToInt(TokenUsageDocument::inputTokens).sum(),
                            "totalOutputTokens", docs.stream().mapToInt(TokenUsageDocument::outputTokens).sum(),
                            "totalCostUsd", docs.stream().mapToDouble(TokenUsageDocument::estimatedCostUsd).sum(),
                            "requestCount", docs.size()
                    );
                })
                .map(m -> (Map) m)
                .toList();
    }

    public List<TokenUsageDocument> findByUser(String userId, Instant from, Instant to) {
        return store.stream()
                .filter(d -> userId.equals(d.userId()))
                .filter(d -> !d.timestamp().isBefore(from) && !d.timestamp().isAfter(to))
                .toList();
    }

    public List<Map> findBySkill(Instant from, Instant to) {
        return store.stream()
                .filter(d -> !d.timestamp().isBefore(from) && !d.timestamp().isAfter(to))
                .collect(Collectors.groupingBy(TokenUsageDocument::skillName))
                .entrySet().stream()
                .map(entry -> {
                    List<TokenUsageDocument> docs = entry.getValue();
                    return Map.of(
                            "_id", entry.getKey(),
                            "totalInputTokens", docs.stream().mapToInt(TokenUsageDocument::inputTokens).sum(),
                            "totalOutputTokens", docs.stream().mapToInt(TokenUsageDocument::outputTokens).sum(),
                            "totalCostUsd", docs.stream().mapToDouble(TokenUsageDocument::estimatedCostUsd).sum(),
                            "requestCount", docs.size(),
                            "avgDurationMs", docs.stream().mapToLong(TokenUsageDocument::durationMs).average().orElse(0.0)
                    );
                })
                .map(m -> (Map) m)
                .toList();
    }

    public List<Map> findDaily(Instant from, Instant to) {
        return store.stream()
                .filter(d -> !d.timestamp().isBefore(from) && !d.timestamp().isAfter(to))
                .collect(Collectors.groupingBy(d -> d.timestamp().toString().substring(0, 10)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<TokenUsageDocument> docs = entry.getValue();
                    return Map.of(
                            "_id", entry.getKey(),
                            "totalInputTokens", docs.stream().mapToInt(TokenUsageDocument::inputTokens).sum(),
                            "totalOutputTokens", docs.stream().mapToInt(TokenUsageDocument::outputTokens).sum(),
                            "totalCostUsd", docs.stream().mapToDouble(TokenUsageDocument::estimatedCostUsd).sum(),
                            "requestCount", docs.size()
                    );
                })
                .map(m -> (Map) m)
                .toList();
    }

    /**
     * Token usage document matching the structure used by the Mongo-backed repository.
     */
    public record TokenUsageDocument(
            String id,
            String userId,
            String sessionId,
            String skillName,
            String provider,
            String model,
            String phase,
            int inputTokens,
            int outputTokens,
            double estimatedCostUsd,
            long durationMs,
            boolean dryRun,
            Instant timestamp
    ) {
    }
}
