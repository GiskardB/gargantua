package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.RoutingMethod;
import ai.gargantua.core.orchestrator.RoutingResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    /**
     * Simple in-memory AuditStore for test assertions.
     */
    static class CapturingAuditStore implements AuditStore {
        final List<AuditEvent> recorded = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            recorded.add(event);
        }

        @Override
        public List<AuditEvent> findByUser(String userId, Instant from, Instant to, int limit) {
            return List.of();
        }

        @Override
        public List<AuditEvent> findByTenant(String tenantId, Instant from, Instant to, int limit) {
            return List.of();
        }

        @Override
        public List<AuditEvent> findBySession(String sessionId) {
            return List.of();
        }

        @Override
        public Optional<AuditEvent> findById(String eventId) {
            return Optional.empty();
        }

        @Override
        public long countByTimeRange(Instant from, Instant to) {
            return 0;
        }
    }

    private AgentRequest buildRequest() {
        return AgentRequest.builder()
                .message("What is the weather?")
                .userId("user-1")
                .sessionId("session-1")
                .build();
    }

    private AgentResponse buildResponse() {
        return new AgentResponse(
                "It is sunny.",
                "session-1",
                "weather-skill",
                List.of("weather-api"),
                RoutingMethod.SEMANTIC,
                0.92,
                50,
                30,
                80,
                0.001,
                250L,
                false
        );
    }

    private RoutingResult buildRouting() {
        return RoutingResult.semantic("weather-skill", 0.92);
    }

    private List<GuardrailResult> buildGuardrailResults() {
        return List.of(
                GuardrailResult.pass("max-length"),
                GuardrailResult.warn("topic-scope", "close to boundary")
        );
    }

    @Test
    void shouldRecordAuditEvent() {
        var store = new CapturingAuditStore();
        var props = new AgentProperties();
        props.getAudit().setEnabled(true);

        var service = new AuditService(store, props);
        service.recordRequest(buildRequest(), buildResponse(), buildRouting(), buildGuardrailResults());

        assertEquals(1, store.recorded.size());

        AuditEvent event = store.recorded.get(0);
        assertNotNull(event.eventId());
        assertNotNull(event.timestamp());
        assertEquals("user-1", event.userId());
        assertEquals("session-1", event.sessionId());
        assertEquals("What is the weather?", event.userMessage());
        assertEquals("It is sunny.", event.agentResponse());
        assertEquals("weather-skill", event.skillSelected());
        assertEquals("SEMANTIC", event.routingMethod());
        assertEquals(0.92, event.routingConfidence(), 0.001);
        assertEquals(List.of("weather-api"), event.toolsCalled());
        assertEquals(2, event.guardrailEvents().size());
        assertEquals("max-length", event.guardrailEvents().get(0).guardrailName());
        assertEquals("PASS", event.guardrailEvents().get(0).verdict());
        assertNull(event.guardrailEvents().get(0).reason());
        assertEquals("topic-scope", event.guardrailEvents().get(1).guardrailName());
        assertEquals("WARN", event.guardrailEvents().get(1).verdict());
        assertEquals("close to boundary", event.guardrailEvents().get(1).reason());
        assertEquals(50, event.inputTokens());
        assertEquals(30, event.outputTokens());
        assertFalse(event.dryRun());
    }

    @Test
    void shouldSkipWhenDisabled() {
        var store = new CapturingAuditStore();
        var props = new AgentProperties();
        props.getAudit().setEnabled(false);

        var service = new AuditService(store, props);
        service.recordRequest(buildRequest(), buildResponse(), buildRouting(), buildGuardrailResults());

        assertEquals(0, store.recorded.size());
    }
}
