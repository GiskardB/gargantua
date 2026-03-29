package ai.gargantua.core.a2a;

import java.util.List;

/**
 * A2A Agent Card — describes the agent's identity and capabilities.
 * Served at {@code /.well-known/agent.json} and {@code /api/capabilities}.
 * Conforms to the A2A Protocol specification.
 *
 * @param name                 human-readable agent name
 * @param description          what this agent does
 * @param version              semver version string
 * @param url                  base URL of this agent
 * @param skills               capabilities exposed to other agents
 * @param supportedProtocols   protocol identifiers (e.g. "a2a/1.0", "mcp/1.0")
 * @param authentication       authentication requirements
 *
 * @see <a href="https://a2a-protocol.org">A2A Protocol</a>
 */
public record AgentCard(
    String name,
    String description,
    String version,
    String url,
    List<AgentSkill> skills,
    List<String> supportedProtocols,
    AgentAuthentication authentication
) {

    /**
     * Describes a single skill the agent can perform.
     *
     * @param id          unique skill identifier
     * @param name        human-readable skill name
     * @param description what the skill does
     * @param domain      logical grouping (e.g. "engineering", "finance")
     * @param tags        searchable tags for discovery
     */
    public record AgentSkill(
        String id,
        String name,
        String description,
        String domain,
        List<String> tags
    ) {}

    /**
     * Authentication requirements for invoking this agent.
     *
     * @param type       authentication type: "none", "bearer", or "api-key"
     * @param headerName HTTP header name (e.g. "Authorization")
     */
    public record AgentAuthentication(
        String type,
        String headerName
    ) {}
}
