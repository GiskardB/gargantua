package ai.gargantua.mcp.resources;

import ai.gargantua.autoconfigure.ToolRegistry;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.mcp.AgentMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes the agent's capabilities as an MCP resource at {@code agent://capabilities}.
 * MCP clients can call this resource to discover the agent's identity, the gateway
 * tool, the available skills, and the underlying agent tools.
 *
 * <p>The skill and tool listings are sourced live from {@link SkillRegistry} and
 * {@link ToolRegistry} respectively, so capability discovery always reflects the
 * current configuration (including hot-reloaded skills).</p>
 *
 * <p>Both registries are injected via {@link ObjectProvider} so that the resource
 * still functions when one of them is missing — e.g. a minimal MCP gateway with no
 * skills or no tools.</p>
 *
 * <p>Registered via {@code AgentMcpServerAutoConfiguration} when
 * {@code agent.mcp.enabled=true} (v1.2.13+).</p>
 */
public class CapabilitiesMcpResource {

    private static final Logger log = LoggerFactory.getLogger(CapabilitiesMcpResource.class);

    private final AgentMcpProperties properties;
    private final ObjectProvider<SkillRegistry> skillRegistryProvider;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;

    public CapabilitiesMcpResource(AgentMcpProperties properties,
                                   ObjectProvider<SkillRegistry> skillRegistryProvider,
                                   ObjectProvider<ToolRegistry> toolRegistryProvider) {
        this.properties = properties;
        this.skillRegistryProvider = skillRegistryProvider;
        this.toolRegistryProvider = toolRegistryProvider;
        log.info("Registered MCP resource: capabilities (uri=agent://capabilities)");
    }

    /**
     * Returns a structured map describing the agent's capabilities. Result shape:
     * <pre>
     * {
     *   "name", "version", "description", "mode",
     *   "tools":  { "&lt;gateway-tool-name&gt;": "description" },
     *   "skills": [ { name, description, version, domain, active } ],
     *   "agentTools": [ { name, description, requiresApproval, dangerous, parallelizable } ]
     * }
     * </pre>
     *
     * @return capability descriptor
     */
    public Map<String, Object> getCapabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("name", properties.getServer().getName());
        caps.put("version", properties.getServer().getVersion());
        caps.put("description", properties.getServer().getDescription());
        caps.put("mode", properties.getMode());
        caps.put("tools", Map.of(
                properties.getGateway().getToolName(),
                properties.getGateway().getToolDescription()
        ));
        caps.put("skills", listSkills());
        caps.put("agentTools", listAgentTools());
        return caps;
    }

    private List<Map<String, Object>> listSkills() {
        SkillRegistry registry = skillRegistryProvider.getIfAvailable();
        if (registry == null) {
            return List.of();
        }
        return registry.listMeta().stream()
                .sorted(Comparator.comparing(SkillMeta::name))
                .<Map<String, Object>>map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", s.name());
                    m.put("description", s.description());
                    m.put("version", s.version());
                    m.put("domain", s.domain());
                    m.put("active", s.active());
                    return m;
                })
                .toList();
    }

    private List<Map<String, Object>> listAgentTools() {
        ToolRegistry registry = toolRegistryProvider.getIfAvailable();
        if (registry == null) {
            return List.of();
        }
        return registry.getToolDefinitions().stream()
                .sorted(Comparator.comparing(ToolDefinition::name))
                .<Map<String, Object>>map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", t.name());
                    m.put("description", t.description());
                    m.put("requiresApproval", t.requiresApproval());
                    m.put("dangerous", t.dangerous());
                    m.put("parallelizable", t.parallelizable());
                    return m;
                })
                .toList();
    }
}
