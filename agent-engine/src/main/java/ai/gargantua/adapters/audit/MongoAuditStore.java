package ai.gargantua.adapters.audit;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB-backed implementation of {@link AuditStore}.
 * Persists audit events in the {@code audit_trail} collection as append-only documents.
 *
 * <p>Only activated when a {@link MongoTemplate} bean is available, so it does not
 * break embedded mode where no MongoDB is present.</p>
 */
@Component
@ConditionalOnBean(MongoTemplate.class)
public class MongoAuditStore implements AuditStore {

    private static final String COLLECTION = "audit_trail";

    private final MongoTemplate mongoTemplate;

    public MongoAuditStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void record(AuditEvent event) {
        mongoTemplate.insert(event, COLLECTION);
    }

    @Override
    public List<AuditEvent> findByUser(String userId, Instant from, Instant to, int limit) {
        var query = new Query(
                Criteria.where("userId").is(userId)
                        .and("timestamp").gte(from).lte(to)
        ).with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        return mongoTemplate.find(query, AuditEvent.class, COLLECTION);
    }

    @Override
    public List<AuditEvent> findByTenant(String tenantId, Instant from, Instant to, int limit) {
        var query = new Query(
                Criteria.where("tenantId").is(tenantId)
                        .and("timestamp").gte(from).lte(to)
        ).with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        return mongoTemplate.find(query, AuditEvent.class, COLLECTION);
    }

    @Override
    public List<AuditEvent> findBySession(String sessionId) {
        var query = new Query(
                Criteria.where("sessionId").is(sessionId)
        ).with(Sort.by(Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, AuditEvent.class, COLLECTION);
    }

    @Override
    public Optional<AuditEvent> findById(String eventId) {
        var query = new Query(Criteria.where("eventId").is(eventId));
        AuditEvent event = mongoTemplate.findOne(query, AuditEvent.class, COLLECTION);
        return Optional.ofNullable(event);
    }

    @Override
    public long countByTimeRange(Instant from, Instant to) {
        var query = new Query(
                Criteria.where("timestamp").gte(from).lte(to)
        );
        return mongoTemplate.count(query, COLLECTION);
    }
}
