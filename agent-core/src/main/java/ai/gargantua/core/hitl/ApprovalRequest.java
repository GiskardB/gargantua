package ai.gargantua.core.hitl;

import java.time.Instant;
import java.util.Map;

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
