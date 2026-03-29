package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementation of {@link AuditStore} backed by a {@link CopyOnWriteArrayList}.
 * Suitable for embedded mode and testing where no MongoDB is available.
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 */
public class InMemoryAuditStore implements AuditStore {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> findByUser(String userId, Instant from, Instant to, int limit) {
        return events.stream()
                .filter(e -> userId.equals(e.userId()))
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<AuditEvent> findByTenant(String tenantId, Instant from, Instant to, int limit) {
        return events.stream()
                .filter(e -> tenantId.equals(e.tenantId()))
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<AuditEvent> findBySession(String sessionId) {
        return events.stream()
                .filter(e -> sessionId.equals(e.sessionId()))
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .toList();
    }

    @Override
    public Optional<AuditEvent> findById(String eventId) {
        return events.stream()
                .filter(e -> eventId.equals(e.eventId()))
                .findFirst();
    }

    @Override
    public long countByTimeRange(Instant from, Instant to) {
        return events.stream()
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .count();
    }
}
