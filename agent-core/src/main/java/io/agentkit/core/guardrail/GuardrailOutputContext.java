package io.agentkit.core.guardrail;

import io.agentkit.core.skill.SkillMeta;

import java.util.Map;

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
