package ai.gargantua.core.hitl;

public record ApprovalDecision(
        String requestId,
        String decision,
        String reason
) {
}
