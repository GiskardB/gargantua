package io.agentkit.core.hitl;

import java.time.Duration;
import java.util.Optional;

public interface ApprovalStore {

    void savePending(String requestId, ApprovalRequest request, Duration ttl);

    Optional<ApprovalRequest> getPending(String requestId);

    void resolve(String requestId, ApprovalDecision decision);

    boolean isExpired(String requestId);
}
