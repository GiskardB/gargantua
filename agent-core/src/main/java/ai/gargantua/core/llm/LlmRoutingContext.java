package ai.gargantua.core.llm;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

/**
 * Snapshot of request metadata used by the {@code LlmRouter} to select which
 * LLM model handles a given request. Routing rules evaluate conditions against
 * these fields (e.g. route long inputs to a cheaper model, premium users to GPT-4).
 *
 * @param userId              caller identity
 * @param sessionId           conversation session
 * @param skillName           the activated skill name
 * @param skillDomain         the activated skill's domain
 * @param userMessage         the raw user input
 * @param inputLengthChars    character count of the user message
 * @param estimatedInputTokens estimated token count of the user message
 * @param userTier            user tier label (e.g. "free", "premium") for tiered routing
 * @param requestTime         local time of the request (for time-based routing rules)
 * @param requestDay          day of week (for time-based routing rules)
 * @param attributes          additional key-value pairs for custom routing conditions
 *
 * @see ai.gargantua.autoconfigure.LlmRouter
 */
public record LlmRoutingContext(
        String userId,
        String sessionId,
        String skillName,
        String skillDomain,
        String userMessage,
        int inputLengthChars,
        int estimatedInputTokens,
        String userTier,
        LocalTime requestTime,
        DayOfWeek requestDay,
        Map<String, String> attributes
) {
}
