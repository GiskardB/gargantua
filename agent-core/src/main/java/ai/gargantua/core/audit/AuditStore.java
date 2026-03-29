package ai.gargantua.core.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port for persisting and querying audit events.
 * Default implementation uses MongoDB. Override with @Bean for custom storage.
 */
public interface AuditStore {
    /** Persist an audit event (append-only, never updated or deleted). */
    void record(AuditEvent event);

    /** Query audit events by user within a time range. */
    List<AuditEvent> findByUser(String userId, Instant from, Instant to, int limit);

    /** Query audit events by tenant within a time range. */
    List<AuditEvent> findByTenant(String tenantId, Instant from, Instant to, int limit);

    /** Query audit events by session. */
    List<AuditEvent> findBySession(String sessionId);

    /** Get a specific audit event by ID. */
    Optional<AuditEvent> findById(String eventId);

    /** Count events in a time range (for dashboard metrics). */
    long countByTimeRange(Instant from, Instant to);
}
