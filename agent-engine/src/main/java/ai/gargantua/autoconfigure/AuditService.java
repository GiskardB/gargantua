package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.RoutingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for recording audit trail events after each agent invocation.
 * Called by {@link DefaultOrchestratorEngine} after every {@code invoke()}.
 * Skipped if auditing is disabled in configuration.
 *
 * @see AuditEvent
 * @see AuditStore
 */
@Component
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditStore auditStore;
    private final AgentProperties props;

    public AuditService(AuditStore auditStore, AgentProperties props) {
        this.auditStore = auditStore;
        this.props = props;
    }

    /**
     * Records an audit event from the completed request.
     * Called by DefaultOrchestratorEngine after every invoke().
     * Skipped if auditing is disabled in config.
     */
    public void recordRequest(AgentRequest request, AgentResponse response,
                               RoutingResult routing, List<GuardrailResult> guardrailResults) {
        if (!props.getAudit().isEnabled()) {
            return;
        }

        var event = new AuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                request.userId(),
                request.securityContext() != null ? request.securityContext().tenantId() : null,
                request.sessionId(),
                request.message(),
                response.text(),
                response.skillUsed(),
                routing.method().name(),
                routing.confidence(),
                response.toolsCalled(),
                guardrailResults.stream()
                        .map(g -> new AuditEvent.GuardrailEvent(
                                g.guardrailName(), g.verdict().name(), g.reason()))
                        .toList(),
                response.inputTokens(),
                response.outputTokens(),
                response.estimatedCostUsd(),
                response.durationMs(),
                response.dryRun(),
                Map.of()
        );

        try {
            auditStore.record(event);
        } catch (Exception e) {
            log.error("Failed to record audit event {}: {}", event.eventId(), e.getMessage(), e);
        }
    }
}
