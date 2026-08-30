package ai.gargantua.core.workload;

import ai.gargantua.core.capability.Capability;
import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.pact.Cognition;
import ai.gargantua.core.pact.Contract;
import ai.gargantua.core.pact.InterfaceEndpoint;

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
 * @param loadout        the specific knowledge, memory, skills and resources this agent is
 *                       equipped with; {@link Loadout#empty()} when nothing is explicitly
 *                       provisioned (the agent then relies on what its skills configure)
 * @param cognition      PACT Core: what kind of reasoning/modalities this agent exposes,
 *                       vendor-neutrally; {@link Cognition#none()} when undeclared
 * @param contract       PACT Core: the basic semantic conditions under which this agent
 *                       may act (autonomy, claimed permissions); declarative only — see
 *                       {@link Contract}; {@link Contract#none()} when undeclared
 * @param interfaces     PACT Core: how another system may reach this agent, beyond the
 *                       built-in A2A endpoint every agent already exposes
 */
public record AgentSpec(
        RuntimeSpec runtime,
        List<Capability> capabilities,
        ModelSpec model,
        List<McpServerSpec> mcpServers,
        Set<MemoryLayer> memoryLayers,
        String defaultSkill,
        Map<String, Object> guardrails,
        Set<String> allowedRoles,
        Loadout loadout,
        Cognition cognition,
        Contract contract,
        List<InterfaceEndpoint> interfaces
) implements WorkloadSpec {

    public AgentSpec {
        runtime = runtime == null ? RuntimeSpec.platformDefault() : runtime;
        model = model == null ? ModelSpec.inherit() : model;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        memoryLayers = memoryLayers == null ? Set.of() : Set.copyOf(memoryLayers);
        guardrails = guardrails == null ? Map.of() : Map.copyOf(guardrails);
        allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
        loadout = loadout == null ? Loadout.empty() : loadout;
        cognition = cognition == null ? Cognition.none() : cognition;
        contract = contract == null ? Contract.none() : contract;
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);

        long distinctServers = mcpServers.stream().map(McpServerSpec::name).distinct().count();
        if (distinctServers != mcpServers.size()) {
            throw new IllegalArgumentException("Duplicate MCP server names in agent spec");
        }
        long distinctCapabilities = capabilities.stream().map(Capability::name).distinct().count();
        if (distinctCapabilities != capabilities.size()) {
            throw new IllegalArgumentException("Duplicate capability names in agent spec");
        }
    }

    /**
     * Backward-compatible constructor for callers written before PACT Core fields existed;
     * equivalent to passing {@link Cognition#none()}, {@link Contract#none()} and no
     * interfaces. Keeps the pre-1.4 9-argument shape source-compatible.
     */
    public AgentSpec(
            RuntimeSpec runtime,
            List<Capability> capabilities,
            ModelSpec model,
            List<McpServerSpec> mcpServers,
            Set<MemoryLayer> memoryLayers,
            String defaultSkill,
            Map<String, Object> guardrails,
            Set<String> allowedRoles,
            Loadout loadout) {
        this(runtime, capabilities, model, mcpServers, memoryLayers, defaultSkill, guardrails,
                allowedRoles, loadout, Cognition.none(), Contract.none(), List.of());
    }

    /**
     * Backward-compatible constructor for callers written before loadout existed; equivalent
     * to passing {@link Loadout#empty()}. Keeps the pre-1.3 8-argument shape source-compatible.
     */
    public AgentSpec(
            RuntimeSpec runtime,
            List<Capability> capabilities,
            ModelSpec model,
            List<McpServerSpec> mcpServers,
            Set<MemoryLayer> memoryLayers,
            String defaultSkill,
            Map<String, Object> guardrails,
            Set<String> allowedRoles) {
        this(runtime, capabilities, model, mcpServers, memoryLayers, defaultSkill, guardrails,
                allowedRoles, Loadout.empty());
    }

    /** Minimal spec: platform runtime, inherited models, no MCP servers, all memory layers. */
    public static AgentSpec minimal() {
        return new AgentSpec(RuntimeSpec.platformDefault(), List.of(), ModelSpec.inherit(),
                List.of(), Set.of(), null, Map.of(), Set.of(), Loadout.empty(),
                Cognition.none(), Contract.none(), List.of());
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

    /** Whether this agent is explicitly provisioned with a non-empty {@link Loadout}. */
    public boolean hasLoadout() {
        return !loadout.isEmpty();
    }
}
