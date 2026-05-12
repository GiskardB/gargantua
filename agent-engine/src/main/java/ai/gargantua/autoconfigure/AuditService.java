package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditEvent;
import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.core.guardrail.GuardrailResult;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.RoutingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for recording audit trail events after each agent invocation.
 * Called by {@link DefaultOrchestratorEngine} after every {@code invoke()}.
 * Skipped if auditing is disabled in configuration.
 *
 * <p><b>v1.2.12+ — null-safe store.</b> When no {@link AuditStore} is wired
 * the service is registered as a runtime no-op; {@link #isActive()} reflects
 * the wired state. This lets {@link AuditAutoConfiguration} sidestep the
 * registration-phase {@code @ConditionalOnBean} race that previously hid
 * the bean in embedded mode.</p>
 *
 * @see AuditEvent
 * @see AuditStore
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditStore auditStore;
    private final AgentProperties props;

    public AuditService(AuditStore auditStore, AgentProperties props) {
        this.auditStore = auditStore;
        this.props = props;
    }

    /**
     * True when an {@link AuditStore} was wired and audit is enabled in
     * configuration. When false, {@link #recordRequest} is a no-op.
     */
    public boolean isActive() {
        return auditStore != null && props.getAudit().isEnabled();
    }

    /**
     * Records an audit event from the completed request.
     * Called by DefaultOrchestratorEngine after every invoke().
     * Skipped if auditing is disabled in config OR if no store is wired.
     */
    public void recordRequest(AgentRequest request, AgentResponse response,
                               RoutingResult routing, List<GuardrailResult> guardrailResults) {
        if (!isActive()) {
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
