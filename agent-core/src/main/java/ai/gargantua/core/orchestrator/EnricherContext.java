package ai.gargantua.core.orchestrator;

import java.util.Map;

/**
 * Read-only snapshot of the current request context, provided to {@link ContextEnricher}
 * implementations so they can produce relevant context sections.
 *
 * @param userId      the requesting user's identity
 * @param sessionId   current conversation session
 * @param skillName   the activated skill name
 * @param skillDomain the activated skill's domain
 * @param userMessage the raw user input
 * @param attributes  additional key-value pairs from the request
 *
 * @see ContextEnricher
 */
public record EnricherContext(
        String userId,
        String sessionId,
        String skillName,
        String skillDomain,
        String userMessage,
        Map<String, String> attributes
) {
}
