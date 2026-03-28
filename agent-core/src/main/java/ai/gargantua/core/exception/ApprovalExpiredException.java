package ai.gargantua.core.exception;

/**
 * Thrown when attempting to resolve a HITL approval request whose TTL has elapsed.
 * The coordinator may auto-deny expired requests depending on configuration.
 */
public class ApprovalExpiredException extends RuntimeException {

    private final String requestId;

    public ApprovalExpiredException(String requestId) {
        super("Approval request expired: " + requestId);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
