package io.agentkit.core.hitl;

public record ApprovalDecision(
        String requestId,
        String decision,
        String reason
) {
}
