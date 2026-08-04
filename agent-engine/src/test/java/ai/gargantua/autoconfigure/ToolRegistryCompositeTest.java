package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.core.tool.ToolParameter;
import ai.gargantua.core.tool.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolRegistry composition")
class ToolRegistryCompositeTest {

    /** Minimal provider returning fixed definitions and echoing invocations. */
    private static final class FakeProvider implements ToolProvider {

        private final String name;
        private final List<ToolDefinition> definitions;
        private final RuntimeException discoveryFailure;
        private final List<String> executed = new ArrayList<>();
        private boolean closed;

        FakeProvider(String name, List<ToolDefinition> definitions) {
            this(name, definitions, null);
        }

        FakeProvider(String name, List<ToolDefinition> definitions, RuntimeException discoveryFailure) {
            this.name = name;
            this.definitions = definitions;
            this.discoveryFailure = discoveryFailure;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<ToolDefinition> discover() {
            if (discoveryFailure != null) {
                throw discoveryFailure;
            }
            return definitions;
        }

        @Override
        public String execute(String toolName, String argumentsJson, ToolExecutionContext context) {
            executed.add(toolName);
            return "{\"provider\":\"" + name + "\",\"tool\":\"" + toolName + "\"}";
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static ToolDefinition tool(String name) {
        return new ToolDefinition(name, name + " description", false, false, false, "", false);
    }

    private static ToolDefinition toolWithParameters(String name, ToolParameter... parameters) {
        return new ToolDefinition(name, name + " description", false, false, false, "", false,
                List.of(parameters));
    }

    @Test
    @DisplayName("merges tools from every provider")
    void mergesToolsFromEveryProvider() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("localTool"))),
                new FakeProvider("mcp:github", List.of(tool("remoteTool")))));
        registry.scan();

        assertThat(registry.getAllToolNames()).containsExactlyInAnyOrder("localTool", "remoteTool");
    }

    @Test
    @DisplayName("routes execution to the provider owning the tool")
    void routesExecutionToOwningProvider() {
        var local = new FakeProvider("annotation", List.of(tool("localTool")));
        var remote = new FakeProvider("mcp:github", List.of(tool("remoteTool")));
        var registry = new ToolRegistry(List.of(local, remote));
        registry.scan();

        String result = registry.executeTool("remoteTool", "{}", ToolExecutionContext.empty());

        assertThat(result).contains("mcp:github");
        assertThat(remote.executed).containsExactly("remoteTool");
        assertThat(local.executed).isEmpty();
    }

    @Test
    @DisplayName("first provider wins when tool names collide")
    void firstProviderWinsOnCollision() {
        var local = new FakeProvider("annotation", List.of(tool("shared")));
        var remote = new FakeProvider("mcp:github", List.of(tool("shared")));
        var registry = new ToolRegistry(List.of(local, remote));
        registry.scan();

        registry.executeTool("shared", "{}", ToolExecutionContext.empty());

        assertThat(registry.getAllToolNames()).containsExactly("shared");
        assertThat(local.executed).containsExactly("shared");
        assertThat(remote.executed).isEmpty();
    }

    @Test
    @DisplayName("a provider failing discovery does not prevent the others from registering")
    void discoveryFailureIsIsolated() {
        var broken = new FakeProvider("mcp:down", List.of(), new IllegalStateException("unreachable"));
        var healthy = new FakeProvider("annotation", List.of(tool("localTool")));
        var registry = new ToolRegistry(List.of(broken, healthy));

        registry.scan();

        assertThat(registry.getAllToolNames()).containsExactly("localTool");
    }

    @Test
    @DisplayName("unknown tool returns a structured error instead of throwing")
    void unknownToolReturnsError() {
        var registry = new ToolRegistry(List.of(new FakeProvider("annotation", List.of())));
        registry.scan();

        String result = registry.executeTool("missing", "{}", ToolExecutionContext.empty());

        assertThat(result).isEqualTo("{\"error\":\"Tool not found: missing\"}");
    }

    @Test
    @DisplayName("null execution context is tolerated")
    void nullContextTolerated() {
        var provider = new FakeProvider("annotation", List.of(tool("localTool")));
        var registry = new ToolRegistry(List.of(provider));
        registry.scan();

        assertThat(registry.executeTool("localTool", "{}", null)).contains("localTool");
    }

    @Test
    @DisplayName("filters tools by the skill allow-list")
    void filtersByAllowList() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("a"), tool("b"), tool("c")))));
        registry.scan();

        assertThat(registry.getFilteredTools(List.of("a", "c")))
                .extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder("a", "c");
        assertThat(registry.getFilteredTools(List.of())).hasSize(3);
        assertThat(registry.getFilteredTools(null)).hasSize(3);
    }

    @Test
    @DisplayName("builds specifications only for allowed tools")
    void buildsSpecificationsForAllowedTools() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("a"), tool("b")))));
        registry.scan();

        assertThat(registry.getToolSpecifications(List.of("a")))
                .singleElement()
                .satisfies(spec -> assertThat(spec.name()).isEqualTo("a"));
    }

    @Test
    @DisplayName("a tool without parameters produces no schema")
    void noParametersProducesNoSchema() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("noArgs")))));
        registry.scan();

        assertThat(registry.getToolSpecifications(null).get(0).parameters()).isNull();
    }

    @Test
    @DisplayName("declared parameter types and required flags reach the specification")
    void parameterTypesReachSpecification() {
        var registry = new ToolRegistry(List.of(new FakeProvider("mcp:api", List.of(
                toolWithParameters("search",
                        new ToolParameter("query", ToolParameter.TYPE_STRING, "what to look for", true),
                        new ToolParameter("limit", ToolParameter.TYPE_INTEGER, "", false))))));
        registry.scan();

        var parameters = registry.getToolSpecifications(null).get(0).parameters();

        assertThat(parameters).isNotNull();
        assertThat(parameters.properties()).containsKeys("query", "limit");
        assertThat(parameters.required()).containsExactly("query");
    }

    @Test
    @DisplayName("specifications are cached per allow-list")
    void specificationsAreCached() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("a")))));
        registry.scan();

        assertThat(registry.getToolSpecifications(List.of("a")))
                .isSameAs(registry.getToolSpecifications(List.of("a")));
    }

    @Test
    @DisplayName("allow-list order and duplicates do not create separate cache entries")
    void allowListIsNormalisedForCaching() {
        var registry = new ToolRegistry(List.of(
                new FakeProvider("annotation", List.of(tool("a"), tool("b")))));
        registry.scan();

        assertThat(registry.getToolSpecifications(List.of("b", "a", "a")))
                .isSameAs(registry.getToolSpecifications(List.of("a", "b")));
    }

    @Test
    @DisplayName("shutdown closes every provider")
    void shutdownClosesProviders() {
        var first = new FakeProvider("annotation", List.of());
        var second = new FakeProvider("mcp:github", List.of());
        var registry = new ToolRegistry(List.of(first, second));
        registry.scan();

        registry.shutdown();

        assertThat(first.closed).isTrue();
        assertThat(second.closed).isTrue();
    }
}
