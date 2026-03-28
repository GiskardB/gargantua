package ai.gargantua.core.guardrail;

import ai.gargantua.core.skill.SkillMeta;

import java.util.Map;

public record GuardrailInputContext(
        String userMessage,
        String userId,
        String sessionId,
        SkillMeta activatedSkill,
        Map<String, Object> attributes
) {
}
