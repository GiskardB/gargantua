package io.agentkit.core.session;

import io.agentkit.core.memory.ChatMessage;
import io.agentkit.core.memory.SessionSummary;

import java.util.List;

public interface SessionSummarizer {

    SessionSummary summarize(String sessionId, String userId, List<ChatMessage> messages);
}
