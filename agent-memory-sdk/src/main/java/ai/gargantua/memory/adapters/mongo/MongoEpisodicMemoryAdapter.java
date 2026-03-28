package ai.gargantua.memory.adapters.mongo;

import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.SessionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * MongoDB-backed implementation of {@link EpisodicMemoryPort}.
 * Stores session summaries in the "session_summaries" collection.
 */
public class MongoEpisodicMemoryAdapter implements EpisodicMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoEpisodicMemoryAdapter.class);
    private static final String COLLECTION = "session_summaries";

    private final MongoTemplate mongo;

    public MongoEpisodicMemoryAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<SessionSummary> getRecentSummaries(String userId, int limit) {
        var query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "sessionDate"))
                .limit(limit);
        List<SessionSummary> summaries = mongo.find(query, SessionSummary.class, COLLECTION);
        log.debug("[EpisodicMemory] Retrieved {} summaries for userId={}", summaries.size(), userId);
        return summaries;
    }

    @Override
    public void saveSummary(SessionSummary summary) {
        mongo.insert(summary, COLLECTION);
        log.info("[EpisodicMemory] Saved summary for userId={}, sessionId={}",
                summary.userId(), summary.sessionId());
    }
}
