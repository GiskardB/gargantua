package ai.gargantua.core.governance;

import ai.gargantua.core.workload.WorkloadMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GovernanceEnvelope")
class GovernanceEnvelopeTest {

    @Test
    @DisplayName("none() is the default and normalises to PRIVATE")
    void noneIsDefault() {
        GovernanceEnvelope none = GovernanceEnvelope.none();
        assertTrue(none.isDefault());
        assertEquals(Visibility.PRIVATE, none.visibility());
        assertNull(none.tenant());
        assertTrue(none.access().isEmpty());
    }

    @Test
    @DisplayName("null visibility becomes PRIVATE; blank tenant/status become null; access is copied")
    void normalisesAndCopies() {
        GovernanceEnvelope e = new GovernanceEnvelope("  ", null, "   ", List.of("admin"), null, null);
        assertEquals(Visibility.PRIVATE, e.visibility());
        assertNull(e.tenant());
        assertNull(e.status());
        assertThrows(UnsupportedOperationException.class, () -> e.access().add("x"));
    }

    @Test
    @DisplayName("withTimestamps keeps authorable fields and is no longer default")
    void withTimestamps() {
        GovernanceEnvelope e = GovernanceEnvelope.of("acme", Visibility.INTERNAL, "active", List.of("ops"))
                .withTimestamps(Instant.EPOCH, Instant.EPOCH);
        assertEquals("acme", e.tenant());
        assertEquals(Visibility.INTERNAL, e.visibility());
        assertEquals("active", e.status());
        assertEquals(List.of("ops"), e.access());
        assertFalse(e.isDefault());
        assertEquals(Instant.EPOCH, e.createdAt());
    }

    @Test
    @DisplayName("WorkloadMetadata is Governed and defaults to none()")
    void workloadMetadataIsGoverned() {
        WorkloadMetadata md = new WorkloadMetadata("agent", "1.0.0");
        assertNotNull(md.governance());
        assertTrue(md.governance().isDefault());
        assertInstanceOf(Governed.class, md);

        // Pre-envelope 5-arg shape still compiles and yields none().
        WorkloadMetadata legacy = new WorkloadMetadata("a", "1.0.0", "", null, java.util.Map.of());
        assertTrue(legacy.governance().isDefault());
    }
}
