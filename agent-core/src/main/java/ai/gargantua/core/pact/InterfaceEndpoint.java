package ai.gargantua.core.pact;

/**
 * PACT's "Interfaces" pillar: how another system may reach this agent (see
 * {@code PACT_v0.2_Agent_Contract_Specification.md} §22). PACT does not define the
 * protocols themselves.
 *
 * <p>Every Gargantua agent already exposes an A2A endpoint at
 * {@code /.well-known/agent.json} ({@link ai.gargantua.core.a2a.AgentCard}); listing it
 * here too makes the interface discoverable from the manifest declaration alone, without
 * a live agent to ask.</p>
 *
 * @param protocol e.g. {@code a2a}, {@code mcp}, {@code http}; open vocabulary
 * @param endpoint URL the protocol is reachable at
 * @param version  protocol version, or {@code null}
 */
public record InterfaceEndpoint(String protocol, String endpoint, String version) {

    public InterfaceEndpoint {
        if (protocol == null || protocol.isBlank()) {
            throw new IllegalArgumentException("Interface protocol is required");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Interface endpoint is required");
        }
    }
}
