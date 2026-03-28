package ai.gargantua.memory.summarizer;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.SessionSummary;

import java.util.List;

/**
 * Produces a {@link SessionSummary} from a list of chat messages.
 */
public interface SessionSummarizer {

    SessionSummary summarize(String userId, String sessionId, List<ChatMessage> messages);
}
