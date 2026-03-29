package ai.gargantua.core.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable record of a single agent interaction, written after every request.
 * Stored in MongoDB collection "audit_trail" for compliance and debugging.
 *
 * <p>Captures the full decision chain: input -> routing -> guardrails -> tools -> output.</p>
 */
public record AuditEvent(
    String eventId,              // unique ID (UUID)
    Instant timestamp,
    String userId,
    String tenantId,             // null if single-tenant
    String sessionId,
    String userMessage,          // original input (AFTER PII masking if enabled)
    String agentResponse,        // final output
    String skillSelected,        // which skill was routed to
    String routingMethod,        // SEMANTIC | LLM | FORCED
    double routingConfidence,
    List<String> toolsCalled,    // tool names invoked during this request
    List<GuardrailEvent> guardrailEvents,  // what each guardrail decided
    int inputTokens,
    int outputTokens,
    double estimatedCostUsd,
    long durationMs,
    boolean dryRun,
    Map<String, Object> metadata // extensible: custom data from enrichers, etc.
) {
    /**
     * Record of a single guardrail's decision during this request.
     */
    public record GuardrailEvent(
        String guardrailName,
        String verdict,          // PASS | BLOCK | WARN
        String reason            // null for PASS
    ) {}
}
