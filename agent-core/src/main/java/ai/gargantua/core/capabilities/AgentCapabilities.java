package ai.gargantua.core.capabilities;

import java.time.Instant;
import java.util.List;

/**
 * Describes the agent's current capabilities, including all registered skills.
 * Exposed via the {@code /capabilities} REST endpoint and the MCP resource.
 *
 * @param agentId      unique agent identifier from config
 * @param displayName  human-friendly name
 * @param description  what this agent does
 * @param available    whether the agent is currently accepting requests
 * @param capabilities list of skill-level capabilities
 * @param updatedAt    when the capabilities were last refreshed
 *
 * @see SkillCapability
 */
public record AgentCapabilities(
        String agentId,
        String displayName,
        String description,
        boolean available,
        List<SkillCapability> capabilities,
        Instant updatedAt
) {
}
