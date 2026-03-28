package ai.gargantua.memory.adapters.inmemory;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.WorkingMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkingMemoryPort} for embedded mode.
 * Stores chat messages in a {@link ConcurrentHashMap} keyed by session ID.
 *
 * <p>Key behaviors:</p>
 * <ul>
 *   <li>Messages are trimmed to {@code maxMessages} on each append (sliding window),
 *       mirroring Redis LTRIM behavior.</li>
 *   <li>The TTL is reset on every append, so active sessions stay alive.</li>
 *   <li>Expired sessions are lazily evicted on read.</li>
 * </ul>
 *
 * <p><strong>Warning:</strong> All data is lost when the process stops.
 * Do NOT use in production.</p>
 *
 * @see WorkingMemoryPort
 */
public class InMemoryWorkingMemoryAdapter implements WorkingMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkingMemoryAdapter.class);

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expiryTimes = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final long ttlMs;

    /**
     * Creates a new in-memory working memory adapter.
     *
     * @param maxMessages maximum number of messages to retain per session (sliding window)
     * @param ttlMs       time-to-live in milliseconds; sessions expire after this duration
     *                    of inactivity (no appends). Default recommendation: 30 * 60 * 1000 (30 min).
     */
    public InMemoryWorkingMemoryAdapter(int maxMessages, long ttlMs) {
        this.maxMessages = maxMessages;
        this.ttlMs = ttlMs;
        log.info("[InMemoryWorkingMemory] Initialized with maxMessages={}, ttlMs={}", maxMessages, ttlMs);
    }

    /**
     * Creates a new in-memory working memory adapter with defaults:
     * maxMessages=20, ttl=30 minutes.
     */
    public InMemoryWorkingMemoryAdapter() {
        this(20, 30 * 60 * 1000L);
    }

    @Override
    public List<ChatMessage> getMessages(String sessionId) {
        if (isExpired(sessionId)) {
            log.debug("[InMemoryWorkingMemory] Session expired, clearing session={}", sessionId);
            clear(sessionId);
            return List.of();
        }
        List<ChatMessage> messages = store.get(sessionId);
        if (messages == null) {
            log.debug("[InMemoryWorkingMemory] No messages found for session={}", sessionId);
            return List.of();
        }
        synchronized (messages) {
            log.debug("[InMemoryWorkingMemory] Retrieved {} messages for session={}", messages.size(), sessionId);
            return List.copyOf(messages);
        }
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        store.compute(sessionId, (key, existing) -> {
            List<ChatMessage> messages = existing != null ? existing : new ArrayList<>();
            messages.add(message);
            // Trim to keep only the most recent maxMessages entries (like Redis LTRIM)
            if (messages.size() > maxMessages) {
                int excess = messages.size() - maxMessages;
                messages.subList(0, excess).clear();
                log.debug("[InMemoryWorkingMemory] Trimmed {} old messages from session={}", excess, sessionId);
            }
            return messages;
        });
        // Reset TTL on every append
        expiryTimes.put(sessionId, System.currentTimeMillis() + ttlMs);
        log.debug("[InMemoryWorkingMemory] Appended message to session={}, role={}", sessionId, message.role());
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
        expiryTimes.remove(sessionId);
        log.info("[InMemoryWorkingMemory] Cleared session={}", sessionId);
    }

    @Override
    public boolean isExpired(String sessionId) {
        Long expiry = expiryTimes.get(sessionId);
        if (expiry == null) {
            return true;
        }
        boolean expired = System.currentTimeMillis() > expiry;
        if (expired) {
            log.debug("[InMemoryWorkingMemory] Session TTL elapsed for session={}", sessionId);
        }
        return expired;
    }
}
