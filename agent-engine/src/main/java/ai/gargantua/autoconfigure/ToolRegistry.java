package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.core.tool.ToolParameter;
import ai.gargantua.core.tool.ToolProvider;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates every {@link ToolProvider} into the single tool surface the orchestrator
 * sees, and routes each invocation to the provider that owns the tool.
 *
 * <p>Composition is what lets one engine serve both delivery modes: an
 * {@link AnnotationToolProvider} contributes compiled {@link AgentTool} methods in
 * library mode, while an MCP-backed provider contributes tools declared in a bundle
 * manifest. Neither the prompt builder nor the tool-calling loop can tell them apart.</p>
 *
 * <p>Providers are consulted in order and the first to claim a name wins, so a compiled
 * Java tool deliberately shadows a remote tool of the same name; collisions are logged.</p>
 *
 * <p>The orchestrator uses {@link #getFilteredTools(List)} to restrict available tools
 * to those listed in the skill's {@code allowed-tools} frontmatter.</p>
 *
 * @see ToolProvider
 * @see ToolDefinition
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private static final List<String> ALL_TOOLS_KEY = List.of();

    private final List<ToolProvider> providers;
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private final Map<String, ToolProvider> routing = new LinkedHashMap<>();
    private final Map<List<String>, List<ToolSpecification>> specCache = new ConcurrentHashMap<>();

    /**
     * Library-mode registry: annotation-scanned tools only. Retained as the primary
     * constructor so existing auto-configuration keeps working unchanged.
     */
    public ToolRegistry(ApplicationContext applicationContext,
                        ObjectProvider<ToolResultCache> toolResultCacheProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(applicationContext, toolResultCacheProvider, meterRegistryProvider, List.of());
    }

    /**
     * Registry combining annotation-scanned tools with additional providers — typically
     * one per MCP server declared in configuration or in a bundle manifest.
     *
     * <p>The annotation provider is always placed first so local Java tools take
     * precedence over remote ones on a name collision.</p>
     */
    public ToolRegistry(ApplicationContext applicationContext,
                        ObjectProvider<ToolResultCache> toolResultCacheProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider,
                        List<ToolProvider> additionalProviders) {
        List<ToolProvider> all = new ArrayList<>();
        all.add(new AnnotationToolProvider(applicationContext, toolResultCacheProvider, meterRegistryProvider));
        if (additionalProviders != null) {
            all.addAll(additionalProviders);
        }
        this.providers = List.copyOf(all);
    }

    /** Registry over an explicit provider list. Used by tests and by the standalone runtime. */
    public ToolRegistry(List<ToolProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    /**
     * Builds the tool index by discovering from every provider. Runs once, after the
     * application context is fully initialised.
     */
    @PostConstruct
    public void scan() {
        tools.clear();
        routing.clear();
        specCache.clear();

        for (ToolProvider provider : providers) {
            List<ToolDefinition> definitions;
            try {
                definitions = provider.discover();
            } catch (Exception e) {
                // One unreachable source must not prevent the agent from starting.
                log.error("Tool provider '{}' failed during discovery, skipping: {}",
                        provider.name(), e.getMessage());
                continue;
            }
            if (definitions == null) {
                continue;
            }
            for (ToolDefinition definition : definitions) {
                if (definition == null || definition.name() == null) {
                    continue;
                }
                ToolProvider owner = routing.get(definition.name());
                if (owner != null) {
                    log.warn("Tool '{}' from provider '{}' ignored — already provided by '{}'",
                            definition.name(), provider.name(), owner.name());
                    continue;
                }
                tools.put(definition.name(), definition);
                routing.put(definition.name(), provider);
            }
        }

        log.info("ToolRegistry: {} tool(s) from {} provider(s)", tools.size(), providers.size());
    }

    /** Releases provider resources — child processes and HTTP connections for MCP servers. */
    @PreDestroy
    public void shutdown() {
        for (ToolProvider provider : providers) {
            try {
                provider.close();
            } catch (Exception e) {
                log.warn("Tool provider '{}' failed to close: {}", provider.name(), e.getMessage());
            }
        }
    }

    public Collection<ToolDefinition> getToolDefinitions() {
        return tools.values();
    }

    public List<String> getAllToolNames() {
        return List.copyOf(tools.keySet());
    }

    /**
     * Get tool definitions filtered by the allowed tools list.
     * If allowedTools is null or empty, return all tools.
     */
    public Collection<ToolDefinition> getFilteredTools(List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return tools.values();
        }
        return tools.entrySet().stream()
                .filter(e -> allowedTools.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Build LangChain4j {@link ToolSpecification} objects for tools matching the allowed list.
     * If allowedTools is null or empty, returns specs for all registered tools.
     *
     * <p>Specifications are cached per normalized allowed-tools list, so repeated calls
     * for the same skill incur no rebuild cost.</p>
     */
    public List<ToolSpecification> getToolSpecifications(List<String> allowedTools) {
        List<String> cacheKey = (allowedTools == null || allowedTools.isEmpty())
                ? ALL_TOOLS_KEY
                : allowedTools.stream().distinct().sorted().toList();
        return specCache.computeIfAbsent(cacheKey, this::buildToolSpecifications);
    }

    private List<ToolSpecification> buildToolSpecifications(List<String> normalizedAllowed) {
        var filtered = normalizedAllowed.isEmpty()
                ? tools.values()
                : tools.entrySet().stream()
                        .filter(e -> normalizedAllowed.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .toList();

        return filtered.stream().map(td -> {
            var builder = ToolSpecification.builder()
                    .name(td.name())
                    .description(td.description());

            if (td.hasParameters()) {
                var schemaBuilder = JsonObjectSchema.builder();
                List<String> required = new ArrayList<>();
                for (ToolParameter parameter : td.parameters()) {
                    addProperty(schemaBuilder, parameter);
                    if (parameter.required()) {
                        required.add(parameter.name());
                    }
                }
                if (!required.isEmpty()) {
                    schemaBuilder.required(required);
                }
                builder.parameters(schemaBuilder.build());
            }

            return builder.build();
        }).toList();
    }

    /**
     * Maps a provider-neutral parameter onto the schema builder. Types the model can act
     * on are passed through; anything structural (object, array) degrades to a string
     * rather than failing, since the tool can still parse it.
     */
    private static void addProperty(JsonObjectSchema.Builder builder, ToolParameter parameter) {
        String name = parameter.name();
        String description = parameter.description();
        boolean described = !description.isBlank();

        switch (parameter.type()) {
            case ToolParameter.TYPE_INTEGER -> {
                if (described) builder.addIntegerProperty(name, description);
                else builder.addIntegerProperty(name);
            }
            case ToolParameter.TYPE_NUMBER -> {
                if (described) builder.addNumberProperty(name, description);
                else builder.addNumberProperty(name);
            }
            case ToolParameter.TYPE_BOOLEAN -> {
                if (described) builder.addBooleanProperty(name, description);
                else builder.addBooleanProperty(name);
            }
            default -> {
                if (described) builder.addStringProperty(name, description);
                else builder.addStringProperty(name);
            }
        }
    }

    /**
     * Backward-compatible entrypoint with no caller context — used when no
     * security context or session is available. RBAC gating fails closed for
     * tools that declare {@link ai.gargantua.core.security.RequiresRole};
     * caching is skipped.
     */
    public String executeTool(String toolName, String jsonArguments) {
        return executeTool(toolName, jsonArguments, ToolExecutionContext.empty());
    }

    /**
     * Execute a tool by name, delegating to the provider that owns it. Errors are
     * returned as a JSON {@code error} object rather than thrown, so the model can see
     * the failure and adapt.
     */
    public String executeTool(String toolName, String jsonArguments, ToolExecutionContext context) {
        ToolProvider provider = routing.get(toolName);
        if (provider == null) {
            return "{\"error\":\"Tool not found: " + toolName.replace("\"", "\\\"") + "\"}";
        }
        return provider.execute(toolName, jsonArguments,
                context != null ? context : ToolExecutionContext.empty());
    }
}
