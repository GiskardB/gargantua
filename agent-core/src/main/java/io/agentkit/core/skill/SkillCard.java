package io.agentkit.core.skill;

import java.util.List;

public record SkillCard(
        SkillMeta meta,
        String systemPrompt,
        List<String> allowedTools,
        String outputSchema,
        List<String> references,
        Integer maxTokens,
        Double temperature,
        String preferredModel
) {
}
