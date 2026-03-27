package io.agentkit.shell.client;

import java.util.function.Consumer;

public interface AgentClient {

    void stream(ShellChatRequest request, Consumer<SseEvent> eventConsumer);

    void resolveApproval(String requestId, String decision);

    String newSession();

    record ShellChatRequest(
            String message,
            String sessionId,
            String userId,
            boolean dryRun,
            String forceSkill
    ) {}

    record SseEvent(
            String type,
            String token,
            String toolName,
            String approvalMessage,
            boolean dangerous,
            String requestId,
            String skillUsed,
            String routingMethod,
            double routingConfidence,
            int inputTokens,
            int outputTokens,
            String errorMessage
    ) {
        public static SseEvent token(String token) {
            return new SseEvent("token", token, null, null, false, null, null, null, 0, 0, 0, null);
        }

        public static SseEvent toolCall(String toolName) {
            return new SseEvent("tool_call", null, toolName, null, false, null, null, null, 0, 0, 0, null);
        }

        public static SseEvent approval(String message, boolean dangerous, String requestId) {
            return new SseEvent("approval_required", null, null, message, dangerous, requestId, null, null, 0, 0, 0, null);
        }

        public static SseEvent meta(String skillUsed, String routingMethod, double routingConfidence,
                                     int inputTokens, int outputTokens) {
            return new SseEvent("meta", null, null, null, false, null,
                    skillUsed, routingMethod, routingConfidence, inputTokens, outputTokens, null);
        }

        public static SseEvent done() {
            return new SseEvent("done", null, null, null, false, null, null, null, 0, 0, 0, null);
        }

        public static SseEvent error(String errorMessage) {
            return new SseEvent("error", null, null, null, false, null, null, null, 0, 0, 0, errorMessage);
        }
    }
}
