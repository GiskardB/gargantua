package ai.gargantua.core.memory;

import java.time.Instant;

/**
 * A single message in working memory. Immutable value object used throughout the
 * memory pipeline. Use the static factories for convenient construction.
 *
 * @param role      either "user" or "assistant"
 * @param content   the message text
 * @param timestamp when the message was created
 */
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
