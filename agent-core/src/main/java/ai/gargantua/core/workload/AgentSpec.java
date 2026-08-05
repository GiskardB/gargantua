package ai.gargantua.core.workload;

import ai.gargantua.core.capability.Capability;
import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.mcp.McpServerSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative definition of a conversational agent — the executable content of a bundle.
 *
 * <p>Everything here is data. Skills and prompts live as files inside the bundle and are
 * referenced by the skill registry; tools arrive from the {@link #mcpServers()} declared
 * below, or from compiled {@link ai.gargantua.core.tool.AgentTool} methods when the agent
 * runs on a custom runtime image built in library mode. No field carries code or
 * credentials, which is what allows a bundle to be signed and promoted across
 * environments unchanged.</p>
 *
 * @param runtime        runtime image requirement; use {@link RuntimeSpec#platformDefault()}
 *                       for the stock image
 * @param capabilities   contracts this agent advertises to the Catalog; may be empty for
 *                       an agent that is only reachable directly rather than through
 *                       capability routing
 * @param model          model selection, or {@link ModelSpec#inherit()} to take the
 *                       runtime defaults
 * @param mcpServers     MCP servers to connect to at startup for tool discovery
 * @param memoryLayers   memory layers to enable; empty means all layers
 * @param defaultSkill   skill handling requests that routing cannot match, or {@code null}
 *                       to use the runtime default
 * @param guardrails     raw guardrail overrides applied on top of runtime configuration,
 *                       keyed by guardrail name; deliberately untyped because guardrail
 *                       settings vary per implementation and the runtime binds them onto
 *                       its own configuration objects
 * @param allowedRoles   roles permitted to invoke this agent at all; empty means no
 *                       restriction beyond per-skill checks
 */
public record AgentSpec(
        RuntimeSpec runtime,
        List<Capability> capabilities,
        ModelSpec model,
        List<McpServerSpec> mcpServers,
        Set<MemoryLayer> memoryLayers,
        String defaultSkill,
        Map<String, Object> guardrails,
        Set<String> allowedRoles
) implements WorkloadSpec {

    public AgentSpec {
        runtime = runtime == null ? RuntimeSpec.platformDefault() : runtime;
        model = model == null ? ModelSpec.inherit() : model;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        memoryLayers = memoryLayers == null ? Set.of() : Set.copyOf(memoryLayers);
        guardrails = guardrails == null ? Map.of() : Map.copyOf(guardrails);
        allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);

        long distinctServers = mcpServers.stream().map(McpServerSpec::name).distinct().count();
        if (distinctServers != mcpServers.size()) {
            throw new IllegalArgumentException("Duplicate MCP server names in agent spec");
        }
        long distinctCapabilities = capabilities.stream().map(Capability::name).distinct().count();
        if (distinctCapabilities != capabilities.size()) {
            throw new IllegalArgumentException("Duplicate capability names in agent spec");
        }
    }

    /** Minimal spec: platform runtime, inherited models, no MCP servers, all memory layers. */
    public static AgentSpec minimal() {
        return new AgentSpec(RuntimeSpec.platformDefault(), List.of(), ModelSpec.inherit(),
                List.of(), Set.of(), null, Map.of(), Set.of());
    }

    @Override
    public WorkloadKind kind() {
        return WorkloadKind.AGENT;
    }

    /** MCP servers with {@code enabled=true}, i.e. those the runtime should connect to. */
    public List<McpServerSpec> enabledMcpServers() {
        return mcpServers.stream().filter(McpServerSpec::enabled).toList();
    }

    /** Whether every memory layer is in play — the default when none are listed. */
    public boolean usesAllMemoryLayers() {
        return memoryLayers.isEmpty();
    }
}
