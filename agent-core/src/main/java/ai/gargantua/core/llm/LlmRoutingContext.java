package ai.gargantua.core.llm;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

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
