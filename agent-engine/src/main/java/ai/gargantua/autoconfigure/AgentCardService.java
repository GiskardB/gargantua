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

    private final CapabilityRegistry capabilityRegistry;

    public AgentCardService(AgentProperties properties, @Nullable SkillRegistry skillRegistry) {
        this(properties, skillRegistry, CapabilityRegistry.empty());
    }

    public AgentCardService(AgentProperties properties,
                            @Nullable SkillRegistry skillRegistry,
                            @Nullable CapabilityRegistry capabilityRegistry) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        this.capabilityRegistry = capabilityRegistry != null ? capabilityRegistry : CapabilityRegistry.empty();
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

    /**
     * Builds the card's advertised entries.
     *
     * <p>When the workload declares capabilities they are what gets advertised: the card
     * is the discovery surface the Catalog and Gateway consume, and capabilities are the
     * contract callers bind to. Skills are internal and only surface when no capability
     * has been declared, which keeps library-mode agents behaving as before.</p>
     */
    private List<AgentSkill> buildSkills() {
        if (!capabilityRegistry.isEmpty()) {
            return buildFromCapabilities();
        }
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
            List<String> examples = List.of();
            try {
                SkillCard card = skillRegistry.load(meta.name());
                tags.addAll(card.allowedTools());
                examples = card.examples();
            } catch (Exception ignored) {
                // Fall back — tools and examples are optional enrichment
            }

            skills.add(new AgentSkill(
                    meta.name(),
                    meta.name(),
                    meta.description(),
                    meta.domain(),
                    tags,
                    examples
            ));
        }
        return skills;
    }

    private List<AgentSkill> buildFromCapabilities() {
        List<AgentSkill> advertised = new ArrayList<>();
        for (var capability : capabilityRegistry.all()) {
            List<String> tags = new ArrayList<>(capability.tags());
            if (capability.version() != null && !capability.version().isBlank()) {
                // Version travels as a tag so consumers can pin without a schema change.
                tags.add("v" + capability.version());
            }
            advertised.add(new AgentSkill(
                    capability.name(),
                    capability.name(),
                    capability.description() != null ? capability.description() : "",
                    capability.implementedBy(),
                    tags,
                    List.of()
            ));
        }
        return advertised;
    }
}
