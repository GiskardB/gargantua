package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.ComposedMemory;
import ai.gargantua.core.memory.KnowledgeSegment;
import ai.gargantua.core.memory.SessionSummary;
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
        StringBuilder sb = new StringBuilder();

        // Skill system prompt
        if (skillCard != null && skillCard.systemPrompt() != null) {
            sb.append(skillCard.systemPrompt());
        }

        // Enricher context (additional context sections)
        if (enricherContext != null && enricherContext.attributes() != null) {
            for (var entry : enricherContext.attributes().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    sb.append("\n\n## ").append(entry.getKey()).append("\n");
                    sb.append(entry.getValue());
                }
            }
        }

        // Memory sections
        if (memory != null) {
            // Episodic summaries
            if (memory.episodicSummaries() != null && !memory.episodicSummaries().isEmpty()) {
                sb.append("\n\n## Previous Conversations\n");
                for (SessionSummary summary : memory.episodicSummaries()) {
                    sb.append("- ").append(summary.summary()).append("\n");
                }
            }

            // Knowledge segments
            if (memory.knowledgeSegments() != null && !memory.knowledgeSegments().isEmpty()) {
                sb.append("\n\n## User Knowledge\n");
                for (KnowledgeSegment segment : memory.knowledgeSegments()) {
                    sb.append("### ").append(segment.segmentKey()).append("\n");
                    sb.append(segment.content()).append("\n");
                }
            }
        }

        return sb.toString();
    }
}
