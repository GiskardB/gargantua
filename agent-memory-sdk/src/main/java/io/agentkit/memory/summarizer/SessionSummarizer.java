package io.agentkit.memory.summarizer;

import io.agentkit.core.memory.ChatMessage;
import io.agentkit.core.memory.SessionSummary;

import java.util.List;

/**
 * Produces a {@link SessionSummary} from a list of chat messages.
 */
public interface SessionSummarizer {

    SessionSummary summarize(String userId, String sessionId, List<ChatMessage> messages);
}
