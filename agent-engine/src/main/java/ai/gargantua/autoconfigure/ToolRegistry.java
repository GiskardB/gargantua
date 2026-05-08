package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.RequiresRole;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.core.tool.ToolRetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Auto-discovers tools at boot by scanning all Spring beans for methods annotated
 * with {@link AgentTool}. Also reads {@link RequiresApproval} metadata to build
 * complete {@link ToolDefinition} descriptors.
 *
 * <p>The orchestrator uses {@link #getFilteredTools(List)} to restrict available tools
 * to those listed in the skill's {@code allowed-tools} frontmatter.</p>
 *
 * <p>{@link #executeTool(String, String, ToolExecutionContext)} additionally honours
 * three method-level annotations: {@link RequiresRole} (RBAC gate),
 * {@link CacheableToolResult} (Redis read-through cache) and {@link ToolRetry}
 * (Resilience4j-backed exponential-backoff retry).</p>
 *
 * @see AgentTool
 * @see ToolDefinition
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private static final List<String> ALL_TOOLS_KEY = List.of();
    private static final String ERROR_PREFIX = "{\"error\":";

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ToolResultCache> toolResultCacheProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Map<String, ToolDefinition> tools = new HashMap<>();
    private final Map<String, ToolInvocation> toolInvocations = new HashMap<>();
    private final Map<List<String>, List<ToolSpecification>> specCache = new ConcurrentHashMap<>();

    /**
     * Holds the bean + method plus the three method-level annotations evaluated on
     * every invocation. Caching them at scan time turns three reflective lookups
     * per tool call ({@link RequiresRole}, {@link CacheableToolResult},
     * {@link ToolRetry}) into pure field reads on the hot path.
     */
    private record ToolInvocation(
            Object bean,
            Method method,
            @org.springframework.lang.Nullable RequiresRole requiresRole,
            @org.springframework.lang.Nullable CacheableToolResult cacheable,
            @org.springframework.lang.Nullable ToolRetry retry) {}

    public ToolRegistry(ApplicationContext applicationContext,
                        ObjectProvider<ToolResultCache> toolResultCacheProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
        this.toolResultCacheProvider = toolResultCacheProvider;
        this.meterRegistryProvider = meterRegistryProvider;
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
                        method.isAnnotationPresent(CacheableToolResult.class),
                        approvalMessage,
                        dangerous
                );

                tools.put(toolName, def);
                toolInvocations.put(toolName, new ToolInvocation(
                        bean, method,
                        method.getAnnotation(RequiresRole.class),
                        method.getAnnotation(CacheableToolResult.class),
                        method.getAnnotation(ToolRetry.class)
                ));
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
     * Backward-compatible entrypoint with no caller context — used when no
     * security context or session is available. RBAC gating fails closed for
     * tools that declare {@link RequiresRole}; caching is skipped.
     */
    public String executeTool(String toolName, String jsonArguments) {
        return executeTool(toolName, jsonArguments, ToolExecutionContext.empty());
    }

    /**
     * Execute a tool by name with JSON arguments, honouring the method-level
     * annotations {@link RequiresRole}, {@link CacheableToolResult}, and
     * {@link ToolRetry}.
     */
    public String executeTool(String toolName, String jsonArguments, ToolExecutionContext context) {
        var invocation = toolInvocations.get(toolName);
        if (invocation == null) {
            return errorJson("Tool not found: " + toolName);
        }
        ToolExecutionContext ctx = context != null ? context : ToolExecutionContext.empty();

        // 1. RBAC gate via cached @RequiresRole
        RequiresRole requiresRole = invocation.requiresRole();
        if (requiresRole != null && requiresRole.value().length > 0) {
            String denial = checkRequiresRole(toolName, requiresRole, ctx.securityContext());
            if (denial != null) return denial;
        }

        Map<String, String> args;
        try {
            args = parseArgs(jsonArguments);
        } catch (Exception e) {
            return errorJson("Invalid tool arguments: " + e.getMessage());
        }

        // 2. Cacheable read-through (cached @CacheableToolResult)
        CacheableToolResult cacheable = invocation.cacheable();
        ToolResultCache cache = toolResultCacheProvider.getIfAvailable();
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        String cacheKey = null;
        if (cacheable != null && cache != null) {
            cacheKey = cache.buildKey(toolName, invocation.method(), args, cacheable,
                    ctx.securityContext(), ctx.sessionId());
            if (cacheKey != null) {
                String hit = cache.get(cacheKey);
                if (hit != null) {
                    log.debug("[ToolRegistry] Cache hit for {} (key={})", toolName, cacheKey);
                    if (meters != null) {
                        meters.counter("agent.tool.cache.hits",
                                "tool", toolName, "scope", cacheable.scope().name()).increment();
                    }
                    return hit;
                } else if (meters != null) {
                    meters.counter("agent.tool.cache.misses",
                            "tool", toolName, "scope", cacheable.scope().name()).increment();
                }
            }
        }

        // 3. Retry-wrapped invocation (cached @ToolRetry)
        Supplier<String> call = () -> doInvoke(invocation, args);
        ToolRetry retryAnn = invocation.retry();
        String result = retryAnn != null
                ? executeWithRetry(toolName, retryAnn, call)
                : call.get();

        // 4. Cache put on success
        if (cacheKey != null && !isErrorPayload(result)) {
            cache.put(cacheKey, result, cacheable.ttlSeconds());
        }
        return result;
    }

    private String checkRequiresRole(String toolName, RequiresRole annotation, SecurityContext securityContext) {
        if (securityContext == null) {
            log.warn("[ToolRegistry] @RequiresRole on tool '{}' but no security context — denying", toolName);
            return errorJson("Access denied: no security context for role-restricted tool '" + toolName + "'");
        }
        if (securityContext.hasAnyRole(annotation.value())) {
            return null;
        }
        log.warn("[ToolRegistry] User '{}' lacks role(s) {} for tool '{}'",
                securityContext.userId(), List.of(annotation.value()), toolName);
        return errorJson("Access denied: user '" + securityContext.userId()
                + "' lacks required role(s) " + List.of(annotation.value())
                + " for tool '" + toolName + "'");
    }

    private String executeWithRetry(String toolName, ToolRetry ann, Supplier<String> call) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(Math.max(1, ann.maxAttempts()))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(Math.max(1, ann.waitDurationMs())),
                        Math.max(1.0, ann.backoffMultiplier()),
                        Duration.ofMillis(Math.max(ann.waitDurationMs(), ann.maxWaitDurationMs()))
                ))
                .retryOnException(t -> {
                    Throwable cause = unwrap(t);
                    if (matches(cause, ann.abortOn())) return false;
                    return matches(cause, ann.retryOn());
                })
                .build();

        Retry retry = Retry.of("tool-" + toolName, config);
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            retry.getEventPublisher()
                    .onRetry(e -> meters.counter("agent.tool.retry.attempts", "tool", toolName).increment())
                    .onError(e -> meters.counter("agent.tool.retry.exhausted", "tool", toolName).increment());
        }
        try {
            return Retry.decorateSupplier(retry, call).get();
        } catch (RuntimeException e) {
            log.error("[ToolRegistry] Tool '{}' failed after retries: {}", toolName, e.getMessage());
            return errorJson("Tool execution failed: " + safeMessage(e));
        }
    }

    private boolean matches(Throwable t, Class<? extends Throwable>[] types) {
        if (t == null || types == null) return false;
        for (Class<? extends Throwable> type : types) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }

    private Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current instanceof java.lang.reflect.InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private Map<String, String> parseArgs(String jsonArguments) throws Exception {
        if (jsonArguments == null || jsonArguments.isBlank() || "{}".equals(jsonArguments.trim())) {
            return Map.of();
        }
        return objectMapper.readValue(jsonArguments, new TypeReference<>() {});
    }

    /** Reflective invocation, surfacing checked exceptions as RuntimeException for retry. */
    private String doInvoke(ToolInvocation invocation, Map<String, String> args) {
        var method = invocation.method();
        var parameters = method.getParameters();
        var invokeArgs = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            var paramName = parameters[i].getName();
            var value = args.get(paramName);
            if (value == null && parameters.length == 1 && args.size() == 1) {
                value = args.values().iterator().next();
            }
            invokeArgs[i] = convertArgument(value, parameters[i].getType());
        }

        try {
            var result = method.invoke(invocation.bean(), invokeArgs);
            if (result == null) {
                return "null";
            }
            if (result instanceof String s) {
                return s;
            }
            return objectMapper.writeValueAsString(result);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isErrorPayload(String result) {
        return result != null && result.startsWith(ERROR_PREFIX);
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private Object convertArgument(String value, Class<?> type) {
        if (value == null) return null;
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            return value;
        }
    }
}
