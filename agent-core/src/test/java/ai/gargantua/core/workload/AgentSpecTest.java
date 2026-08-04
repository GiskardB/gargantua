package ai.gargantua.core.workload;

import ai.gargantua.core.capability.Capability;
import ai.gargantua.core.mcp.McpServerSpec;
import ai.gargantua.core.mcp.McpTransport;
import ai.gargantua.core.memory.MemoryLayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentSpec")
class AgentSpecTest {

    private static McpServerSpec disabledServer(String name) {
        return new McpServerSpec(name, McpTransport.STDIO, "cmd", null, null, null, null, null, false);
    }

    @Test
    @DisplayName("minimal spec uses platform defaults")
    void minimalUsesDefaults() {
        AgentSpec spec = AgentSpec.minimal();

        assertEquals(WorkloadKind.AGENT, spec.kind());
        assertFalse(spec.runtime().hasCustomImage());
        assertNull(spec.model().primary());
        assertTrue(spec.capabilities().isEmpty());
        assertTrue(spec.mcpServers().isEmpty());
        assertTrue(spec.usesAllMemoryLayers());
    }

    @Test
    @DisplayName("null runtime and model are replaced with inheriting defaults")
    void nullRuntimeAndModelDefaulted() {
        AgentSpec spec = new AgentSpec(null, null, null, null, null, null, null, null);

        assertNotNull(spec.runtime());
        assertNotNull(spec.model());
        assertFalse(spec.runtime().hasCustomImage());
    }

    @Test
    @DisplayName("kind is always AGENT")
    void kindIsAgent() {
        assertEquals(WorkloadKind.AGENT, AgentSpec.minimal().kind());
    }

    @Test
    @DisplayName("enabledMcpServers filters out disabled servers")
    void enabledMcpServersFiltersDisabled() {
        AgentSpec spec = new AgentSpec(null, null, null,
                List.of(McpServerSpec.stdio("on", "cmd", List.of()), disabledServer("off")),
                null, null, null, null);

        assertEquals(2, spec.mcpServers().size());
        assertEquals(1, spec.enabledMcpServers().size());
        assertEquals("on", spec.enabledMcpServers().get(0).name());
    }

    @Test
    @DisplayName("usesAllMemoryLayers is false once layers are listed")
    void memoryLayerSubsetRespected() {
        AgentSpec spec = new AgentSpec(null, null, null, null,
                Set.of(MemoryLayer.WORKING), null, null, null);

        assertFalse(spec.usesAllMemoryLayers());
        assertEquals(Set.of(MemoryLayer.WORKING), spec.memoryLayers());
    }

    @Test
    @DisplayName("duplicate MCP server names are rejected")
    void duplicateMcpServerNamesRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new AgentSpec(null, null, null,
                        List.of(McpServerSpec.stdio("dup", "a", List.of()),
                                McpServerSpec.stdio("dup", "b", List.of())),
                        null, null, null, null));
        assertTrue(ex.getMessage().contains("Duplicate MCP server names"));
    }

    @Test
    @DisplayName("duplicate capability names are rejected")
    void duplicateCapabilityNamesRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new AgentSpec(null,
                        List.of(new Capability("dup", "first", "1.0.0"),
                                new Capability("dup", "second", "2.0.0")),
                        null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("Duplicate capability names"));
    }

    @Test
    @DisplayName("distinct MCP server and capability names are accepted")
    void distinctNamesAccepted() {
        AgentSpec spec = new AgentSpec(null,
                List.of(new Capability("a", "d", "1.0.0"), new Capability("b", "d", "1.0.0")),
                null,
                List.of(McpServerSpec.stdio("one", "cmd", List.of()),
                        McpServerSpec.stdio("two", "cmd", List.of())),
                null, null, null, null);

        assertEquals(2, spec.capabilities().size());
        assertEquals(2, spec.mcpServers().size());
    }

    @Test
    @DisplayName("collections are defensively copied")
    void collectionsAreDefensivelyCopied() {
        AgentSpec spec = new AgentSpec(null, List.of(new Capability("a", "d", "1.0.0")),
                null, null, null, null, null, Set.of("role"));

        assertThrows(UnsupportedOperationException.class,
                () -> spec.capabilities().add(new Capability("b", "d", "1.0.0")));
        assertThrows(UnsupportedOperationException.class, () -> spec.allowedRoles().add("other"));
    }

    @Test
    @DisplayName("custom runtime image is preserved")
    void customRuntimeImagePreserved() {
        AgentSpec spec = new AgentSpec(new RuntimeSpec("acme/runtime:2.1", "1.0"),
                null, null, null, null, null, null, null);

        assertTrue(spec.runtime().hasCustomImage());
        assertEquals("acme/runtime:2.1", spec.runtime().image());
    }
}
