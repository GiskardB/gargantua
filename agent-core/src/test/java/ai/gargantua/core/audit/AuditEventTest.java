package ai.gargantua.core.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditEventTest {

    @Test
    void shouldConstructWithAllFields() {
        Instant now = Instant.now();
        var guardrailEvent = new AuditEvent.GuardrailEvent("max-length", "PASS", null);
        var event = new AuditEvent(
                "evt-001",
                now,
                "user-1",
                "tenant-1",
                "session-1",
                "Hello agent",
                "Hello user",
                "greeting-skill",
                "SEMANTIC",
                0.95,
                List.of("web-search"),
                List.of(guardrailEvent),
                100,
                50,
                0.002,
                350L,
                false,
                Map.of("source", "test")
        );

        assertEquals("evt-001", event.eventId());
        assertEquals(now, event.timestamp());
        assertEquals("user-1", event.userId());
        assertEquals("tenant-1", event.tenantId());
        assertEquals("session-1", event.sessionId());
        assertEquals("Hello agent", event.userMessage());
        assertEquals("Hello user", event.agentResponse());
        assertEquals("greeting-skill", event.skillSelected());
        assertEquals("SEMANTIC", event.routingMethod());
        assertEquals(0.95, event.routingConfidence(), 0.001);
        assertEquals(List.of("web-search"), event.toolsCalled());
        assertEquals(1, event.guardrailEvents().size());
        assertEquals(100, event.inputTokens());
        assertEquals(50, event.outputTokens());
        assertEquals(0.002, event.estimatedCostUsd(), 0.0001);
        assertEquals(350L, event.durationMs());
        assertFalse(event.dryRun());
        assertEquals("test", event.metadata().get("source"));
    }

    @Test
    void shouldAllowNullTenantId() {
        var event = new AuditEvent(
                "evt-002",
                Instant.now(),
                "user-1",
                null,
                "session-1",
                "msg",
                "resp",
                "skill",
                "FORCED",
                1.0,
                List.of(),
                List.of(),
                10,
                20,
                0.0,
                100L,
                true,
                Map.of()
        );

        assertNull(event.tenantId());
        assertTrue(event.dryRun());
    }

    @Test
    void guardrailEventShouldCaptureAllFields() {
        var ge = new AuditEvent.GuardrailEvent("pii-masking", "BLOCK", "PII detected");

        assertEquals("pii-masking", ge.guardrailName());
        assertEquals("BLOCK", ge.verdict());
        assertEquals("PII detected", ge.reason());
    }

    @Test
    void guardrailEventPassShouldHaveNullReason() {
        var ge = new AuditEvent.GuardrailEvent("max-length", "PASS", null);

        assertEquals("PASS", ge.verdict());
        assertNull(ge.reason());
    }
}
