package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.orchestrator.EnricherContext;
import ai.gargantua.core.skill.SkillCard;
import org.springframework.stereotype.Component;

/**
 * Composes the system prompt from skill body, enricher context, and composed memory.
 */
@Component
public class PromptBuilder {

    /**
     * Build a full system prompt from the skill's system prompt body,
     * enricher context, and composed memory.
     */
    public String build(SkillCard skillCard, ComposedMemory memory, EnricherContext enricherContext) {
        var sb = new StringBuilder();

        if (skillCard != null && skillCard.systemPrompt() != null) {
            sb.append(skillCard.systemPrompt());
        }

        if (enricherContext != null && enricherContext.attributes() != null) {
            enricherContext.attributes().forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    sb.append("\n\n## %s\n%s".formatted(key, value));
                }
            });
        }

        if (memory != null) {
            if (memory.episodicSummaries() != null && !memory.episodicSummaries().isEmpty()) {
                sb.append("\n\n## Previous Conversations\n");
                memory.episodicSummaries().forEach(s -> sb.append("- %s\n".formatted(s.summary())));
            }
            if (memory.knowledgeSegments() != null && !memory.knowledgeSegments().isEmpty()) {
                sb.append("\n\n## User Knowledge\n");
                memory.knowledgeSegments().forEach(s -> sb.append("### %s\n%s\n".formatted(s.segmentKey(), s.content())));
            }
        }

        return sb.toString();
    }
}
