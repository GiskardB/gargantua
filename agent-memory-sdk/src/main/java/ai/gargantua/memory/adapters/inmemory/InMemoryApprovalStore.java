package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ApprovalStore} for embedded mode.
 * Stores pending approvals in a {@link ConcurrentHashMap} with TTL-based
 * expiry checked lazily on read.
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 *
 * @see ApprovalStore
 */
public class InMemoryApprovalStore implements ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryApprovalStore.class);

    private final ConcurrentHashMap<String, ApprovalRequest> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ApprovalDecision> resolved = new ConcurrentHashMap<>();

    public InMemoryApprovalStore() {
        log.info("[InMemoryApprovalStore] Initialized");
    }

    @Override
    public void savePending(String requestId, ApprovalRequest request, Duration ttl) {
        log.debug("[InMemoryApprovalStore] savePending requestId={} toolName={} ttl={}",
                  requestId, request.toolName(), ttl);
        pending.put(requestId, request);
    }

    @Override
    public Optional<ApprovalRequest> getPending(String requestId) {
        ApprovalRequest req = pending.get(requestId);
        if (req == null || isExpired(requestId)) {
            if (req != null) {
                // Lazily evict expired entry
                pending.remove(requestId);
                log.debug("[InMemoryApprovalStore] Evicted expired request requestId={}", requestId);
            }
            return Optional.empty();
        }
        return Optional.of(req);
    }

    @Override
    public void resolve(String requestId, ApprovalDecision decision) {
        log.info("[InMemoryApprovalStore] Resolved requestId={} decision={}", requestId, decision.decision());
        pending.remove(requestId);
        resolved.put(requestId, decision);
    }

    @Override
    public boolean isExpired(String requestId) {
        ApprovalRequest req = pending.get(requestId);
        if (req == null) {
            return true;
        }
        return Instant.now().isAfter(req.expiresAt());
    }
}
