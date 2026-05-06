package ai.gargantua.autoconfigure;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-discovers tools at boot by scanning all Spring beans for methods annotated
 * with {@link AgentTool}. Also reads {@link RequiresApproval} metadata to build
 * complete {@link ToolDefinition} descriptors.
 *
 * <p>The orchestrator uses {@link #getFilteredTools(List)} to restrict available tools
 * to those listed in the skill's {@code allowed-tools} frontmatter.</p>
 *
 * @see AgentTool
 * @see ToolDefinition
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private static final List<String> ALL_TOOLS_KEY = List.of();

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolDefinition> tools = new HashMap<>();
    private final Map<String, ToolInvocation> toolInvocations = new HashMap<>();
    private final Map<List<String>, List<ToolSpecification>> specCache = new ConcurrentHashMap<>();

    /** Holds the bean instance and method needed to invoke a tool at runtime. */
    private record ToolInvocation(Object bean, Method method) {}

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void scan() {
        var beanNames = applicationContext.getBeanDefinitionNames();
        var count = 0;

        for (var beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            for (var method : bean.getClass().getMethods()) {
                var annotation = method.getAnnotation(AgentTool.class);
                if (annotation == null) continue;

                var toolName = annotation.name().isBlank() ? method.getName() : annotation.name();
                var requiresApproval = method.isAnnotationPresent(RequiresApproval.class);
                var approvalMessage = "";
                var dangerous = false;

                if (requiresApproval) {
                    var approval = method.getAnnotation(RequiresApproval.class);
                    approvalMessage = approval.message();
                    dangerous = approval.dangerous();
                }

                var def = new ToolDefinition(
                        toolName,
                        annotation.description(),
                        annotation.parallelizable(),
                        requiresApproval,
                        false, // cacheable is determined by @CacheableToolResult
                        approvalMessage,
                        dangerous
                );

                tools.put(toolName, def);
                toolInvocations.put(toolName, new ToolInvocation(bean, method));
                count++;
                log.debug("Registered tool: {}", toolName);
            }
        }

        log.info("ToolRegistry: scanned {} tool(s)", count);
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
     * for the same skill incur no reflection cost.</p>
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
            var invocation = toolInvocations.get(td.name());
            var builder = ToolSpecification.builder()
                    .name(td.name())
                    .description(td.description());

            if (invocation != null && invocation.method().getParameterCount() > 0) {
                var schemaBuilder = JsonObjectSchema.builder();
                for (Parameter param : invocation.method().getParameters()) {
                    schemaBuilder.addStringProperty(param.getName());
                }
                builder.parameters(schemaBuilder.build());
            }

            return builder.build();
        }).toList();
    }

    /**
     * Execute a tool by name with JSON arguments.
     * Parses the JSON arguments, matches them to method parameters, invokes the method,
     * and returns the result serialized as a JSON string.
     *
     * @param toolName      the registered tool name
     * @param jsonArguments JSON object string with argument key-value pairs
     * @return the tool result as a JSON string
     */
    public String executeTool(String toolName, String jsonArguments) {
        var invocation = toolInvocations.get(toolName);
        if (invocation == null) {
            return "{\"error\":\"Tool not found: " + toolName + "\"}";
        }

        try {
            Map<String, String> args;
            if (jsonArguments == null || jsonArguments.isBlank() || "{}".equals(jsonArguments.trim())) {
                args = Map.of();
            } else {
                args = objectMapper.readValue(jsonArguments, new TypeReference<>() {});
            }

            var method = invocation.method();
            var parameters = method.getParameters();
            var invokeArgs = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                var paramName = parameters[i].getName();
                var value = args.get(paramName);
                if (value == null) {
                    // Try to match by position if only one arg
                    if (parameters.length == 1 && args.size() == 1) {
                        value = args.values().iterator().next();
                    }
                }
                invokeArgs[i] = convertArgument(value, parameters[i].getType());
            }

            var result = method.invoke(invocation.bean(), invokeArgs);
            if (result == null) {
                return "null";
            }
            if (result instanceof String s) {
                return s;
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to execute tool '{}': {}", toolName, e.getMessage(), e);
            return "{\"error\":\"Tool execution failed: " + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }

    private Object convertArgument(String value, Class<?> type) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        // Fallback: try Jackson deserialization
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            return value;
        }
    }
}
