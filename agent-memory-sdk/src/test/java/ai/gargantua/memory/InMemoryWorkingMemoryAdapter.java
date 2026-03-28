package ai.gargantua.memory;

import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.memory.WorkingMemoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test stub for {@link WorkingMemoryPort}.
 * Supports configurable TTL simulation via {@code ttlMs}.
 */
public class InMemoryWorkingMemoryAdapter implements WorkingMemoryPort {

    private final ConcurrentHashMap<String, List<ChatMessage>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expiryTimes = new ConcurrentHashMap<>();
    private final long ttlMs;

    public InMemoryWorkingMemoryAdapter(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public InMemoryWorkingMemoryAdapter() {
        this(Long.MAX_VALUE);
    }

    @Override
    public List<ChatMessage> getMessages(String sessionId) {
        if (isExpired(sessionId)) {
            clear(sessionId);
            return List.of();
        }
        List<ChatMessage> messages = store.get(sessionId);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        store.computeIfAbsent(sessionId, key -> new ArrayList<>()).add(message);
        // Guard against Long overflow: if ttlMs is very large, cap expiry at Long.MAX_VALUE
        long now = System.currentTimeMillis();
        long expiry = (Long.MAX_VALUE - ttlMs < now) ? Long.MAX_VALUE : now + ttlMs;
        expiryTimes.put(sessionId, expiry);
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
        expiryTimes.remove(sessionId);
    }

    @Override
    public boolean isExpired(String sessionId) {
        Long expiry = expiryTimes.get(sessionId);
        if (expiry == null) {
            return true;
        }
        return System.currentTimeMillis() > expiry;
    }
}
