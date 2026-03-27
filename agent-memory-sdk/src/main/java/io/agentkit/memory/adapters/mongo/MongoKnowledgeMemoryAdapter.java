package io.agentkit.memory.adapters.mongo;

import io.agentkit.core.memory.KnowledgeMemoryPort;
import io.agentkit.core.memory.KnowledgeSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB-backed implementation of {@link KnowledgeMemoryPort}.
 * Stores knowledge segments in the "user_knowledge" collection.
 */
public class MongoKnowledgeMemoryAdapter implements KnowledgeMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoKnowledgeMemoryAdapter.class);
    private static final String COLLECTION = "user_knowledge";

    private final MongoTemplate mongo;

    public MongoKnowledgeMemoryAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<KnowledgeSegment> getSegments(String userId) {
        var query = new Query(Criteria.where("userId").is(userId));
        List<KnowledgeSegment> segments = mongo.find(query, KnowledgeSegment.class, COLLECTION);
        log.debug("[KnowledgeMemory] Retrieved {} segments for userId={}", segments.size(), userId);
        return segments;
    }

    @Override
    public void upsertSegment(String userId, String segmentKey, String content) {
        var query = new Query(Criteria.where("userId").is(userId)
                .and("segmentKey").is(segmentKey));
        var update = new Update()
                .set("userId", userId)
                .set("segmentKey", segmentKey)
                .set("content", content)
                .set("updatedAt", Instant.now())
                .set("source", "user");
        mongo.upsert(query, update, COLLECTION);
        log.info("[KnowledgeMemory] Upserted segment userId={}, segmentKey={}", userId, segmentKey);
    }

    @Override
    public void deleteSegment(String userId, String segmentKey) {
        var query = new Query(Criteria.where("userId").is(userId)
                .and("segmentKey").is(segmentKey));
        mongo.remove(query, COLLECTION);
        log.info("[KnowledgeMemory] Deleted segment userId={}, segmentKey={}", userId, segmentKey);
    }
}
