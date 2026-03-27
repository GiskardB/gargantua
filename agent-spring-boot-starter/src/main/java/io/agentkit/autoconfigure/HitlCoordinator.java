package io.agentkit.autoconfigure;

import io.agentkit.core.exception.ApprovalExpiredException;
import io.agentkit.core.hitl.ApprovalDecision;
import io.agentkit.core.hitl.ApprovalRequest;
import io.agentkit.core.hitl.ApprovalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Coordinates human-in-the-loop approval workflows.
 */
@Component
public class HitlCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HitlCoordinator.class);

    private final AgentProperties properties;

    @Nullable
    private final ApprovalStore approvalStore;

    public HitlCoordinator(AgentProperties properties, @Nullable ApprovalStore approvalStore) {
        this.properties = properties;
        this.approvalStore = approvalStore;
    }

    /**
     * Resolve a pending approval request with the given decision.
     */
    public void resolve(String requestId, ApprovalDecision decision) {
        if (approvalStore == null) {
            log.warn("No ApprovalStore configured; cannot resolve request {}", requestId);
            return;
        }

        if (approvalStore.isExpired(requestId)) {
            if (properties.getHitl().isAutoDenyOnExpiry()) {
                log.info("Approval request {} expired, auto-denying", requestId);
                approvalStore.resolve(requestId, new ApprovalDecision(requestId, "DENIED", "Expired"));
                throw new ApprovalExpiredException(requestId);
            }
            throw new ApprovalExpiredException(requestId);
        }

        if ("DENIED".equalsIgnoreCase(decision.decision()) &&
                properties.getHitl().isRequireReasonOnDeny() &&
                (decision.reason() == null || decision.reason().isBlank())) {
            throw new IllegalArgumentException("Reason is required when denying an approval request");
        }

        approvalStore.resolve(requestId, decision);
        log.info("Resolved approval request {}: {}", requestId, decision.decision());
    }

    /**
     * Get a pending approval request.
     */
    public Optional<ApprovalRequest> getPending(String requestId) {
        if (approvalStore == null) {
            return Optional.empty();
        }
        return approvalStore.getPending(requestId);
    }
}
