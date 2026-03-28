package ai.gargantua.core.hitl;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for persisting human-in-the-loop approval requests. Stores pending requests
 * with a TTL and resolves them when a human approves or denies.
 *
 * <p>Default implementation: {@link ai.gargantua.adapters.hitl.RedisApprovalStore},
 * which stores requests as JSON in Redis with TTL-based expiry.</p>
 *
 * @see ApprovalRequest
 * @see ApprovalDecision
 */
public interface ApprovalStore {

    /** Saves a pending approval request with the given TTL. */
    void savePending(String requestId, ApprovalRequest request, Duration ttl);

    /** Retrieves a pending request, or empty if already resolved or expired. */
    Optional<ApprovalRequest> getPending(String requestId);

    /** Records the human's decision and removes the pending request. */
    void resolve(String requestId, ApprovalDecision decision);

    /** Checks whether the request's TTL has elapsed. */
    boolean isExpired(String requestId);
}
