package ai.gargantua.core.session;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.SessionSummary;

import java.util.List;

/**
 * Compresses a working memory session into a {@link SessionSummary} for long-term
 * episodic storage. Triggered when the working memory TTL expires.
 *
 * <p>Default implementation in agent-memory-sdk uses a placeholder summarizer.
 * The real implementation will call the LLM to produce a concise summary.</p>
 *
 * @see ai.gargantua.memory.summarizer.LlmSessionSummarizer
 */
public interface SessionSummarizer {

    /**
     * Summarizes the given messages into a compact session summary.
     *
     * @param sessionId the session being summarized
     * @param userId    the user who participated in the session
     * @param messages  all messages from the expired session
     * @return a summary suitable for episodic memory storage
     */
    SessionSummary summarize(String sessionId, String userId, List<ChatMessage> messages);
}
