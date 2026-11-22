package ai.gargantua.core.memory;

import java.util.List;

/**
 * Port for session-scoped working memory (current conversation messages).
 * Default implementation uses Redis with TTL-based expiry.
 *
 * <p>When the TTL expires, the {@link ai.gargantua.core.session.SessionSummarizer}
 * is triggered to compress the conversation into an episodic memory summary.</p>
 *
 * @see ai.gargantua.memory.adapters.redis.RedisWorkingMemoryAdapter
 * @see EpisodicMemoryPort
 */
public interface WorkingMemoryPort {

    /** Returns all messages in the session, ordered chronologically. */
    List<ChatMessage> getMessages(String sessionId);

    /** Appends a message and resets the session TTL. */
    void appendMessage(String sessionId, ChatMessage message);

    /**
     * Appends several messages in order. A turn always stores a user message and the
     * assistant's reply together, so adapters backed by a remote store should override
     * this to write the batch in one round trip; the default simply loops.
     *
     * @param sessionId the session to append to
     * @param messages  the messages, oldest first
     */
    default void appendMessages(String sessionId, List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            appendMessage(sessionId, message);
        }
    }

    /** Removes all messages for the session. */
    void clear(String sessionId);

    /** Checks whether the session's TTL has elapsed (key no longer exists in Redis). */
    boolean isExpired(String sessionId);
}
