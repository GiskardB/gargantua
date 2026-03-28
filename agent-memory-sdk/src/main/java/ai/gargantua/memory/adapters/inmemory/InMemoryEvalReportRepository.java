package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.eval.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory replacement for {@code MongoEvalReportRepository} used in embedded mode.
 * Stores eval reports in a {@link CopyOnWriteArrayList} with query methods that
 * mirror the Mongo-backed repository.
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 */
public class InMemoryEvalReportRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEvalReportRepository.class);

    private final CopyOnWriteArrayList<EvalReport> store = new CopyOnWriteArrayList<>();

    public InMemoryEvalReportRepository() {
        log.info("[InMemoryEvalReportRepository] Initialized");
    }

    public void save(EvalReport report) {
        store.add(report);
        log.debug("[InMemoryEvalReportRepository] Saved eval report: skill={}, score={}, passed={}/{}",
                  report.skillName(), report.overallScore(), report.passed(), report.totalCases());
    }

    public Optional<EvalReport> findLatest(String skillName) {
        return store.stream()
                .filter(r -> skillName.equals(r.skillName()))
                .max(Comparator.comparing(EvalReport::runAt));
    }

    public List<EvalReport> findHistory(String skillName, int limit) {
        return store.stream()
                .filter(r -> skillName.equals(r.skillName()))
                .sorted(Comparator.comparing(EvalReport::runAt).reversed())
                .limit(limit)
                .toList();
    }
}
