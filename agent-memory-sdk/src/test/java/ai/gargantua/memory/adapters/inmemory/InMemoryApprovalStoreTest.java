package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryApprovalStoreTest {

    private InMemoryApprovalStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryApprovalStore();
    }

    // ── savePending / getPending ─────────────────────────────

    @Test
    @DisplayName("savePending stores request and getPending retrieves it")
    void savePending_andGetPending() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        Optional<ApprovalRequest> result = store.getPending("req-1");

        assertThat(result).isPresent();
        assertThat(result.get().requestId()).isEqualTo("req-1");
        assertThat(result.get().toolName()).isEqualTo("dangerousTool");
        assertThat(result.get().sessionId()).isEqualTo("session-1");
        assertThat(result.get().userId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("getPending returns empty for unknown request ID")
    void getPending_unknownId_returnsEmpty() {
        assertThat(store.getPending("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("savePending overwrites existing request with same ID")
    void savePending_overwritesExisting() {
        ApprovalRequest req1 = createRequest("req-1", Instant.now().plusSeconds(300));
        ApprovalRequest req2 = new ApprovalRequest(
                "req-1", "session-2", "user-2", "otherTool",
                Map.of(), "Updated request", false, Instant.now().plusSeconds(300)
        );

        store.savePending("req-1", req1, Duration.ofMinutes(5));
        store.savePending("req-1", req2, Duration.ofMinutes(5));

        Optional<ApprovalRequest> result = store.getPending("req-1");
        assertThat(result).isPresent();
        assertThat(result.get().toolName()).isEqualTo("otherTool");
    }

    // ── resolve ─────────────────────────────────────────────

    @Test
    @DisplayName("resolve removes pending request and records decision")
    void resolve_removesPendingAndRecords() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        store.resolve("req-1", new ApprovalDecision("req-1", "APPROVED", null));

        // Pending should be gone
        assertThat(store.getPending("req-1")).isEmpty();
    }

    @Test
    @DisplayName("resolve with DENIED decision removes pending request")
    void resolve_denied_removesPending() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        store.resolve("req-1", new ApprovalDecision("req-1", "DENIED", "Too risky"));

        assertThat(store.getPending("req-1")).isEmpty();
    }

    @Test
    @DisplayName("resolve on nonexistent request does not throw")
    void resolve_nonexistent_noException() {
        store.resolve("no-such-id", new ApprovalDecision("no-such-id", "APPROVED", null));
        // should not throw
    }

    // ── isExpired ───────────────────────────────────────────

    @Test
    @DisplayName("isExpired returns true for unknown request ID")
    void isExpired_unknownId_returnsTrue() {
        assertThat(store.isExpired("nonexistent")).isTrue();
    }

    @Test
    @DisplayName("isExpired returns false for request with future expiresAt")
    void isExpired_futureExpiry_returnsFalse() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        assertThat(store.isExpired("req-1")).isFalse();
    }

    @Test
    @DisplayName("isExpired returns true for request with past expiresAt")
    void isExpired_pastExpiry_returnsTrue() {
        ApprovalRequest request = createRequest("req-1", Instant.now().minusSeconds(10));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        assertThat(store.isExpired("req-1")).isTrue();
    }

    @Test
    @DisplayName("isExpired returns true after resolve removes request")
    void isExpired_afterResolve_returnsTrue() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));
        store.resolve("req-1", new ApprovalDecision("req-1", "APPROVED", null));

        assertThat(store.isExpired("req-1")).isTrue();
    }

    // ── Expiry handling (lazy eviction) ─────────────────────

    @Test
    @DisplayName("getPending lazily evicts expired request and returns empty")
    void getPending_expiredRequest_lazilyEvicts() {
        ApprovalRequest expiredReq = createRequest("req-1", Instant.now().minusSeconds(10));
        store.savePending("req-1", expiredReq, Duration.ofMinutes(5));

        // First call should evict and return empty
        Optional<ApprovalRequest> result = store.getPending("req-1");
        assertThat(result).isEmpty();

        // Subsequent call should also return empty (evicted)
        assertThat(store.getPending("req-1")).isEmpty();
    }

    @Test
    @DisplayName("getPending does not evict non-expired request")
    void getPending_nonExpired_doesNotEvict() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));
        store.savePending("req-1", request, Duration.ofMinutes(5));

        // Multiple reads should all succeed
        assertThat(store.getPending("req-1")).isPresent();
        assertThat(store.getPending("req-1")).isPresent();
    }

    // ── Full lifecycle ──────────────────────────────────────

    @Test
    @DisplayName("full submit-resolve lifecycle: submit, retrieve, resolve, verify gone")
    void fullLifecycle_submitResolve() {
        ApprovalRequest request = createRequest("req-1", Instant.now().plusSeconds(300));

        // Step 1: submit
        store.savePending("req-1", request, Duration.ofMinutes(5));
        assertThat(store.getPending("req-1")).isPresent();
        assertThat(store.isExpired("req-1")).isFalse();

        // Step 2: resolve
        store.resolve("req-1", new ApprovalDecision("req-1", "APPROVED", "Looks good"));

        // Step 3: verify resolved
        assertThat(store.getPending("req-1")).isEmpty();
        assertThat(store.isExpired("req-1")).isTrue();
    }

    @Test
    @DisplayName("multiple independent requests do not interfere")
    void multipleRequests_independent() {
        store.savePending("req-1", createRequest("req-1", Instant.now().plusSeconds(300)), Duration.ofMinutes(5));
        store.savePending("req-2", createRequest("req-2", Instant.now().plusSeconds(300)), Duration.ofMinutes(5));

        store.resolve("req-1", new ApprovalDecision("req-1", "APPROVED", null));

        assertThat(store.getPending("req-1")).isEmpty();
        assertThat(store.getPending("req-2")).isPresent();
    }

    // ── Helpers ──────────────────────────────────────────────

    private ApprovalRequest createRequest(String requestId, Instant expiresAt) {
        return new ApprovalRequest(
                requestId, "session-1", "user-1", "dangerousTool",
                Map.of("param1", "value1"), "Please approve this action",
                true, expiresAt
        );
    }
}
