package io.agentkit.core.exception;

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
