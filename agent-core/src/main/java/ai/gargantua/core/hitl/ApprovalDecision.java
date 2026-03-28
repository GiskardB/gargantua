package ai.gargantua.core.hitl;

/**
 * Human's response to a pending {@link ApprovalRequest}.
 *
 * @param requestId the approval request being resolved
 * @param decision  "APPROVED" or "DENIED"
 * @param reason    optional explanation (required on deny when configured)
 *
 * @see ApprovalStore#resolve
 */
public record ApprovalDecision(
        String requestId,
        String decision,
        String reason
) {
}
