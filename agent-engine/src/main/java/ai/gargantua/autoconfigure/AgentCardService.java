package ai.gargantua.autoconfigure;

import ai.gargantua.core.a2a.AgentCard;
import ai.gargantua.core.a2a.AgentCard.AgentAuthScheme;
import ai.gargantua.core.a2a.AgentCard.AgentCapabilities;
import ai.gargantua.core.a2a.AgentCard.AgentSkill;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the A2A {@link AgentCard} from the skill registry and agent configuration.
 * This provides the response for {@code /.well-known/agent.json}.
 */
@Component
public class AgentCardService {

    private final AgentProperties properties;

    @Nullable
    private final SkillRegistry skillRegistry;

    public AgentCardService(AgentProperties properties, @Nullable SkillRegistry skillRegistry) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
    }

    /**
     * Build the agent card describing this agent's identity and capabilities.
     *
     * @param baseUrl the base URL of this agent (derived from request or config)
     * @return the populated {@link AgentCard}
     */
    public AgentCard getAgentCard(String baseUrl) {
        List<AgentSkill> skills = buildSkills();

        AgentCapabilities capabilities = new AgentCapabilities(false, false);

        return new AgentCard(
                properties.getApi().getDisplayName(),
                properties.getApi().getDescription().isEmpty()
                        ? "AI Agent powered by Gargantua"
                        : properties.getApi().getDescription(),
                properties.getApi().getVersion(),
                baseUrl,
                "1.0",
                capabilities,
                List.of("text/plain"),
                List.of("text/plain"),
                skills,
                null,
                List.of(new AgentAuthScheme("none", "No authentication required"))
        );
    }

    private List<AgentSkill> buildSkills() {
        if (skillRegistry == null) {
            return List.of();
        }

        List<AgentSkill> skills = new ArrayList<>();
        for (SkillMeta meta : skillRegistry.listMeta()) {
            if (!meta.active()) {
                continue;
            }

            List<String> tags = new ArrayList<>();
            tags.add(meta.domain());
            // Enrich tags from allowed tools if available
            try {
                SkillCard card = skillRegistry.load(meta.name());
                tags.addAll(card.allowedTools());
            } catch (Exception ignored) {
                // Fall back — tools are optional tag enrichment
            }

            skills.add(new AgentSkill(
                    meta.name(),
                    meta.name(),
                    meta.description(),
                    meta.domain(),
                    tags
            ));
        }
        return skills;
    }
}
