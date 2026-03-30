package ai.gargantua.core.a2a;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A2A-compliant Task — a unit of work sent between agents.
 */
public record A2ATask(
    String id,
    String kind,                              // always "task"
    String contextId,                         // conversation context (nullable)
    TaskStatus status,
    List<Artifact> artifacts,                 // output artifacts (nullable until completed)
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {
    /**
     * Task status with state enum.
     * Valid states: submitted, working, completed, failed, input-required, canceled
     */
    public record TaskStatus(
        String state,
        Message message                       // nullable — details about current state
    ) {}

    /**
     * A2A Message — a communication turn between user and agent.
     */
    public record Message(
        String messageId,
        String role,                          // "user" | "agent"
        List<Part> parts
    ) {}

    /**
     * A2A Part — smallest content unit in a message.
     */
    public record Part(
        String kind,                          // "text" | "file" | "data"
        String text                           // content (for kind="text")
    ) {}

    /**
     * A2A Artifact — output composed of parts.
     */
    public record Artifact(
        String name,
        List<Part> parts
    ) {}
}
