package ai.gargantua.memory.adapters.mongo;

import ai.gargantua.core.memory.EpisodicMemoryPort;
import ai.gargantua.core.memory.SessionSummary;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * MongoDB-backed implementation of {@link EpisodicMemoryPort}.
 * Stores session summaries in the {@code session_summaries} collection,
 * queried by {@code userId} and sorted by {@code sessionDate} descending.
 *
 * <p>When {@code ttlDays > 0}, summaries saved without an explicit
 * {@code expiresAt} get one defaulted to {@code now + ttlDays}, and a
 * Mongo TTL index on {@code expiresAt} expires them automatically.</p>
 *
 * @see ai.gargantua.core.memory.EpisodicMemoryPort
 */
public class MongoEpisodicMemoryAdapter implements EpisodicMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoEpisodicMemoryAdapter.class);
    private static final String COLLECTION = "session_summaries";

    private final MongoTemplate mongo;
    private final int ttlDays;

    public MongoEpisodicMemoryAdapter(MongoTemplate mongo) {
        this(mongo, 0);
    }

    public MongoEpisodicMemoryAdapter(MongoTemplate mongo, int ttlDays) {
        this.mongo = mongo;
        this.ttlDays = ttlDays;
    }

    @PostConstruct
    void ensureTtlIndex() {
        if (ttlDays <= 0) {
            return;
        }
        try {
            // expireAfterSeconds=0 means: expire when expiresAt <= now
            mongo.indexOps(COLLECTION).ensureIndex(
                    new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO)
            );
            log.info("[EpisodicMemory] Ensured TTL index on expiresAt (ttlDays={})", ttlDays);
        } catch (Exception e) {
            log.warn("[EpisodicMemory] Could not ensure TTL index: {}", e.getMessage());
        }
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
        SessionSummary toSave = summary;
        if (summary.expiresAt() == null && ttlDays > 0) {
            Instant expiresAt = Instant.now().plus(Duration.ofDays(ttlDays));
            toSave = new SessionSummary(
                    summary.userId(), summary.sessionId(), summary.summary(),
                    summary.keyTopics(), summary.unresolvedItems(),
                    summary.messageCount(), summary.sessionDate(), expiresAt
            );
        }
        mongo.insert(toSave, COLLECTION);
        log.info("[EpisodicMemory] Saved summary for userId={}, sessionId={}",
                toSave.userId(), toSave.sessionId());
    }
}
