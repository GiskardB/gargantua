package ai.gargantua.core.guardrail;

import ai.gargantua.core.skill.SkillMeta;

import java.util.Map;

/**
 * Context passed to {@link OutputGuardrail#process} with the LLM's raw response
 * and request metadata. Output guardrails chain by replacing {@code rawResponse}
 * via {@link #withRawResponse(String)}.
 *
 * @param rawResponse     the current response text (may already be modified by a prior guardrail)
 * @param userId          caller identity
 * @param sessionId       current conversation session
 * @param activatedSkill  the skill that produced this response
 * @param inputAttributes attributes from the input phase (e.g. PII map for de-anonymization)
 */
public record GuardrailOutputContext(
        String rawResponse,
        String userId,
        String sessionId,
        SkillMeta activatedSkill,
        Map<String, Object> inputAttributes
) {

    public GuardrailOutputContext withRawResponse(String r) {
        return new GuardrailOutputContext(r, this.userId, this.sessionId, this.activatedSkill, this.inputAttributes);
    }
}
