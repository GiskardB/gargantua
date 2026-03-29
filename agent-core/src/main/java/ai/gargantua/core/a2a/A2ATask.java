package ai.gargantua.core.a2a;

import java.time.Instant;
import java.util.Map;

/**
 * An A2A task — a unit of work sent from one agent to another.
 *
 * @param id        unique task identifier
 * @param status    task status: "pending", "running", "completed", "failed", or "cancelled"
 * @param input     the input message that initiated the task
 * @param output    the output message (null until completed)
 * @param createdAt when the task was created
 * @param updatedAt when the task was last updated
 * @param metadata  arbitrary key-value metadata
 */
public record A2ATask(
    String id,
    String status,
    A2AMessage input,
    A2AMessage output,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {

    /**
     * A message within an A2A task.
     *
     * @param role        message role: "user" or "agent"
     * @param content     the message content
     * @param contentType MIME type: "text/plain" or "application/json"
     */
    public record A2AMessage(
        String role,
        String content,
        String contentType
    ) {}
}
