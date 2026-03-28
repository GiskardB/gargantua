package ai.gargantua.core.memory;

import java.time.Instant;

public record ChatMessage(
        String role,
        String content,
        Instant timestamp
) {

    public static ChatMessage userMessage(String content) {
        return new ChatMessage("user", content, Instant.now());
    }

    public static ChatMessage assistantMessage(String content) {
        return new ChatMessage("assistant", content, Instant.now());
    }
}
