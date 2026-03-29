package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when attempting to resolve a HITL approval request whose TTL has elapsed.
 * The coordinator may auto-deny expired requests depending on configuration.
 */
public class ApprovalExpiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String requestId;

    public ApprovalExpiredException(String requestId) {
        super("Approval request expired: %s".formatted(Objects.requireNonNull(requestId, "requestId must not be null")));
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
