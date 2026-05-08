package ai.gargantua.memory.adapters.mongo;

import ai.gargantua.core.memory.KnowledgeMemoryPort;
import ai.gargantua.core.memory.KnowledgeSegment;
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
 * Stores knowledge segments in the {@code user_knowledge} collection,
 * keyed by {@code (userId, segmentKey)} with upsert semantics.
 *
 * @see ai.gargantua.core.memory.KnowledgeMemoryPort
 */
public class MongoKnowledgeMemoryAdapter implements KnowledgeMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoKnowledgeMemoryAdapter.class);
    private static final String COLLECTION = "user_knowledge";

    private final MongoTemplate mongo;
    private final int maxSegments;
    private final int maxTokensPerSegment;

    public MongoKnowledgeMemoryAdapter(MongoTemplate mongo) {
        this(mongo, 0, 0);
    }

    public MongoKnowledgeMemoryAdapter(MongoTemplate mongo, int maxSegments, int maxTokensPerSegment) {
        this.mongo = mongo;
        this.maxSegments = maxSegments;
        this.maxTokensPerSegment = maxTokensPerSegment;
    }

    @Override
    public List<KnowledgeSegment> getSegments(String userId) {
        var query = new Query(Criteria.where("userId").is(userId));
        if (maxSegments > 0) {
            query.limit(maxSegments);
        }
        List<KnowledgeSegment> segments = mongo.find(query, KnowledgeSegment.class, COLLECTION);
        if (maxTokensPerSegment > 0) {
            // ~4 chars per token rule of thumb, matching MemoryComposer.estimateTokens
            int maxChars = maxTokensPerSegment * 4;
            segments = segments.stream()
                    .map(seg -> truncate(seg, maxChars))
                    .toList();
        }
        log.debug("[KnowledgeMemory] Retrieved {} segments for userId={}", segments.size(), userId);
        return segments;
    }

    private KnowledgeSegment truncate(KnowledgeSegment seg, int maxChars) {
        String content = seg.content();
        if (content == null || content.length() <= maxChars) {
            return seg;
        }
        return new KnowledgeSegment(
                seg.userId(), seg.segmentKey(), content.substring(0, maxChars),
                seg.updatedAt(), seg.source()
        );
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
