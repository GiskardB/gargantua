package ai.gargantua.core.guardrail;

import ai.gargantua.core.skill.SkillMeta;

import java.util.Map;

/**
 * Context passed to {@link InputGuardrail#check} with all information needed
 * to evaluate the incoming user message.
 *
 * @param userMessage    the raw user input to validate
 * @param userId         caller identity (for rate limiting, PII tracking)
 * @param sessionId      current conversation session
 * @param activatedSkill the routed skill (may be null if guardrails run before routing)
 * @param attributes     mutable map for inter-guardrail communication (e.g. PII map)
 */
public record GuardrailInputContext(
        String userMessage,
        String userId,
        String sessionId,
        SkillMeta activatedSkill,
        Map<String, Object> attributes
) {
}
