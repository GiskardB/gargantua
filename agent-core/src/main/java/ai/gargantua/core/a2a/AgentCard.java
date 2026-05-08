package ai.gargantua.core.a2a;

import java.util.List;

/**
 * A2A-compliant Agent Card — describes the agent's identity and capabilities.
 * Served at {@code /.well-known/agent.json}.
 * Conforms to the A2A Protocol specification v1.0.
 *
 * @see <a href="https://a2a-protocol.org">A2A Protocol</a>
 */
public record AgentCard(
    String name,
    String description,
    String version,
    String url,
    String protocolVersion,                    // e.g. "1.0"
    AgentCapabilities capabilities,
    List<String> defaultInputModes,            // e.g. ["text/plain"]
    List<String> defaultOutputModes,           // e.g. ["text/plain"]
    List<AgentSkill> skills,
    AgentProvider provider,                    // nullable
    List<AgentAuthScheme> authSchemes          // nullable
) {
    public record AgentCapabilities(
        boolean streaming,
        boolean pushNotifications
    ) {}

    public record AgentSkill(
        String id,
        String name,
        String description,
        String domain,                         // Gargantua extension (not in A2A spec)
        List<String> tags,
        List<String> examples                  // example prompts for discovery
    ) {
        public AgentSkill(String id, String name, String description, String domain, List<String> tags) {
            this(id, name, description, domain, tags, List.of());
        }
    }

    public record AgentProvider(
        String organization,
        String url
    ) {}

    public record AgentAuthScheme(
        String scheme,                         // "none" | "apiKey" | "bearer" | "oauth2"
        String description
    ) {}
}
