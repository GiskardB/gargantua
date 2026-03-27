package io.agentkit.memory.adapters.redis;

import io.agentkit.core.memory.ChatMessage;
import io.agentkit.core.memory.WorkingMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link WorkingMemoryPort}.
 * Stores chat messages as JSON strings in a Redis LIST keyed by session id.
 */
public class RedisWorkingMemoryAdapter implements WorkingMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkingMemoryAdapter.class);
    private static final String KEY_PREFIX = "working_memory:";

    private final StringRedisTemplate redis;
    private final int maxMessages;
    private final int ttlMinutes;

    public RedisWorkingMemoryAdapter(StringRedisTemplate redis, int maxMessages, int ttlMinutes) {
        this.redis = redis;
        this.maxMessages = maxMessages;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public List<ChatMessage> getMessages(String sessionId) {
        String key = key(sessionId);
        List<String> raw = redis.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            log.debug("[WorkingMemory] No messages found for session={}", sessionId);
            return List.of();
        }
        log.debug("[WorkingMemory] Retrieved {} messages for session={}", raw.size(), sessionId);
        return raw.stream().map(RedisWorkingMemoryAdapter::deserialize).toList();
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        String key = key(sessionId);
        String json = serialize(message);
        redis.opsForList().rightPush(key, json);
        // Trim to keep only the most recent maxMessages entries
        redis.opsForList().trim(key, -maxMessages, -1);
        // Reset TTL on every append
        redis.expire(key, ttlMinutes, TimeUnit.MINUTES);
        log.debug("[WorkingMemory] Appended message to session={}, role={}", sessionId, message.role());
    }

    @Override
    public void clear(String sessionId) {
        String key = key(sessionId);
        redis.delete(key);
        log.info("[WorkingMemory] Cleared session={}", sessionId);
    }

    @Override
    public boolean isExpired(String sessionId) {
        String key = key(sessionId);
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        // TTL returns -2 when key does not exist
        boolean expired = ttl != null && ttl == -2;
        log.debug("[WorkingMemory] isExpired session={} -> {}", sessionId, expired);
        return expired;
    }

    // ---- serialization helpers (no Jackson dependency) ----

    static String serialize(ChatMessage msg) {
        return "{\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(
                        escapeJson(msg.role()),
                        escapeJson(msg.content()),
                        msg.timestamp().toString()
                );
    }

    static ChatMessage deserialize(String json) {
        String role = extractJsonValue(json, "role");
        String content = extractJsonValue(json, "content");
        String timestamp = extractJsonValue(json, "timestamp");
        return new ChatMessage(role, content, Instant.parse(timestamp));
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        var sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> { sb.append('\\'); sb.append(next); }
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
