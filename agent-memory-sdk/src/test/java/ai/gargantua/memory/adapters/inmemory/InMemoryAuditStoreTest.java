package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.audit.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAuditStoreTest {

    private InMemoryAuditStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryAuditStore();
    }

    private AuditEvent createEvent(String eventId, String userId, String tenantId,
                                    String sessionId, Instant timestamp) {
        return new AuditEvent(
                eventId, timestamp, userId, tenantId, sessionId,
                "message", "response", "skill", "SEMANTIC", 0.9,
                List.of(), List.of(), 10, 20, 0.001, 100L, false, Map.of()
        );
    }

    @Test
    void recordAndFindByUser() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);

        store.record(createEvent("e1", "user-1", "t1", "s1", hourAgo));
        store.record(createEvent("e2", "user-1", "t1", "s2", now));
        store.record(createEvent("e3", "user-2", "t1", "s3", now));

        var results = store.findByUser("user-1", twoHoursAgo, now, 10);

        assertEquals(2, results.size());
        // Should be sorted desc by timestamp
        assertEquals("e2", results.get(0).eventId());
        assertEquals("e1", results.get(1).eventId());
    }

    @Test
    void findByUserRespectsLimit() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);

        store.record(createEvent("e1", "user-1", "t1", "s1", hourAgo));
        store.record(createEvent("e2", "user-1", "t1", "s2", now));

        var results = store.findByUser("user-1", hourAgo.minus(1, ChronoUnit.HOURS), now, 1);

        assertEquals(1, results.size());
        assertEquals("e2", results.get(0).eventId());
    }

    @Test
    void findByTenant() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);

        store.record(createEvent("e1", "user-1", "tenant-a", "s1", now));
        store.record(createEvent("e2", "user-2", "tenant-a", "s2", now));
        store.record(createEvent("e3", "user-3", "tenant-b", "s3", now));

        var results = store.findByTenant("tenant-a", hourAgo, now, 10);

        assertEquals(2, results.size());
    }

    @Test
    void findBySession() {
        Instant now = Instant.now();

        store.record(createEvent("e1", "user-1", "t1", "session-abc", now));
        store.record(createEvent("e2", "user-1", "t1", "session-abc", now.minus(1, ChronoUnit.MINUTES)));
        store.record(createEvent("e3", "user-1", "t1", "session-xyz", now));

        var results = store.findBySession("session-abc");

        assertEquals(2, results.size());
        // Sorted desc by timestamp
        assertEquals("e1", results.get(0).eventId());
        assertEquals("e2", results.get(1).eventId());
    }

    @Test
    void findById() {
        Instant now = Instant.now();
        store.record(createEvent("e1", "user-1", "t1", "s1", now));

        var found = store.findById("e1");
        assertTrue(found.isPresent());
        assertEquals("e1", found.get().eventId());

        var notFound = store.findById("nonexistent");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void countByTimeRange() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
        Instant threeHoursAgo = now.minus(3, ChronoUnit.HOURS);

        store.record(createEvent("e1", "user-1", "t1", "s1", threeHoursAgo));
        store.record(createEvent("e2", "user-1", "t1", "s2", hourAgo));
        store.record(createEvent("e3", "user-1", "t1", "s3", now));

        // Count events in last 2 hours
        long count = store.countByTimeRange(twoHoursAgo, now);
        assertEquals(2, count);

        // Count all events
        long allCount = store.countByTimeRange(threeHoursAgo, now);
        assertEquals(3, allCount);
    }

    @Test
    void findByUserExcludesOutOfRange() {
        Instant now = Instant.now();
        Instant hourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant threeHoursAgo = now.minus(3, ChronoUnit.HOURS);

        store.record(createEvent("e1", "user-1", "t1", "s1", threeHoursAgo));
        store.record(createEvent("e2", "user-1", "t1", "s2", now));

        // Only the recent event should be in range
        var results = store.findByUser("user-1", hourAgo, now, 10);
        assertEquals(1, results.size());
        assertEquals("e2", results.get(0).eventId());
    }
}
