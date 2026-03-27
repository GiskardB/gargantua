package io.agentkit.autoconfigure;

import io.agentkit.core.capabilities.AgentCapabilities;
import io.agentkit.core.capabilities.SkillCapability;
import io.agentkit.core.skill.SkillMeta;
import io.agentkit.core.skill.SkillRegistry;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Provides agent capabilities information derived from the skill registry.
 */
@Component
public class CapabilitiesService {

    private final AgentProperties properties;

    @Nullable
    private final SkillRegistry skillRegistry;

    public CapabilitiesService(AgentProperties properties, @Nullable SkillRegistry skillRegistry) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
    }

    /**
     * Get the current capabilities of the agent.
     */
    public AgentCapabilities getCapabilities() {
        List<SkillCapability> capabilities;

        if (skillRegistry != null) {
            capabilities = skillRegistry.listMeta().stream()
                    .map(this::toCapability)
                    .toList();
        } else {
            capabilities = List.of();
        }

        return new AgentCapabilities(
                properties.getApi().getAgentId(),
                properties.getApi().getDisplayName(),
                properties.getApi().getDescription(),
                true,
                capabilities,
                Instant.now()
        );
    }

    private SkillCapability toCapability(SkillMeta meta) {
        return new SkillCapability(
                meta.name(),
                meta.description(),
                meta.domain(),
                meta.version(),
                meta.active(),
                meta.hasSchema(),
                List.of() // allowed tools are resolved from SkillCard, not SkillMeta
        );
    }
}
