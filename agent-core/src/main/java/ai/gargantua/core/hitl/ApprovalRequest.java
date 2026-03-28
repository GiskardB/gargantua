package ai.gargantua.core.hitl;

import java.time.Instant;
import java.util.Map;

/**
 * A pending human-in-the-loop approval request, created when a tool annotated
 * with {@link ai.gargantua.core.tool.RequiresApproval} is about to execute.
 *
 * @param requestId  unique identifier for this approval request
 * @param sessionId  the conversation session where the tool was invoked
 * @param userId     the user who triggered the tool
 * @param toolName   the tool awaiting approval
 * @param parameters tool parameters shown to the approver for review
 * @param message    human-readable description of the pending action
 * @param dangerous  whether the tool is flagged as high-risk
 * @param expiresAt  when the approval window closes
 *
 * @see ApprovalStore
 * @see ApprovalDecision
 */
public record ApprovalRequest(
        String requestId,
        String sessionId,
        String userId,
        String toolName,
        Map<String, Object> parameters,
        String message,
        boolean dangerous,
        Instant expiresAt
) {
}
