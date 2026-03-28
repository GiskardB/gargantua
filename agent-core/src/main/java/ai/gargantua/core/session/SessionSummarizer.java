package ai.gargantua.core.session;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.SessionSummary;

import java.util.List;

public interface SessionSummarizer {

    SessionSummary summarize(String sessionId, String userId, List<ChatMessage> messages);
}
