package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.RequiresRole;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolDefinition;
import ai.gargantua.core.tool.ToolExecutionContext;
import ai.gargantua.core.tool.ToolParameter;
import ai.gargantua.core.tool.ToolProvider;
import ai.gargantua.core.tool.ToolRetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Supplies tools from compiled {@link AgentTool}-annotated methods found in the Spring
 * context — the library-mode tool source.
 *
 * <p>The annotation-driven cross-cutting concerns live here rather than in
 * {@link ToolRegistry} because they are inherently properties of a Java method:
 * {@link RequiresRole} gating, {@link CacheableToolResult} read-through caching and
 * {@link ToolRetry} backoff all read configuration from annotations that no external
 * tool source has. The registry is left to do routing.</p>
 *
 * @see ToolProvider
 * @see ToolRegistry
 */
public class AnnotationToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(AnnotationToolProvider.class);

    private static final String ERROR_PREFIX = "{\"error\":";

    private final ApplicationContext applicationContext;
    private final ObjectProvider<ToolResultCache> toolResultCacheProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ToolInvocation> toolInvocations = new HashMap<>();

    private List<ToolDefinition> discovered;

    /**
     * Holds the bean + method plus the three method-level annotations evaluated on
     * every invocation. Caching them at scan time turns three reflective lookups
     * per tool call ({@link RequiresRole}, {@link CacheableToolResult},
     * {@link ToolRetry}) into pure field reads on the hot path.
     */
    private record ToolInvocation(
            Object bean,
            Method method,
            @Nullable RequiresRole requiresRole,
            @Nullable CacheableToolResult cacheable,
            @Nullable ToolRetry retry) {}

    public AnnotationToolProvider(ApplicationContext applicationContext,
                                  ObjectProvider<ToolResultCache> toolResultCacheProvider,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.applicationContext = applicationContext;
        this.toolResultCacheProvider = toolResultCacheProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public String name() {
        return "annotation";
    }

    /**
     * Scans every Spring bean for {@link AgentTool} methods on first call and caches the
     * result. Scanning is deferred rather than done in a constructor so that the registry
     * controls when it happens, once the context is fully initialised.
     */
    @Override
    public synchronized List<ToolDefinition> discover() {
        if (discovered != null) {
            return discovered;
        }
        List<ToolDefinition> definitions = new ArrayList<>();

        for (var beanName : applicationContext.getBeanDefinitionNames()) {
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

                definitions.add(new ToolDefinition(
                        toolName,
                        annotation.description(),
                        annotation.parallelizable(),
                        requiresApproval,
                        method.isAnnotationPresent(CacheableToolResult.class),
                        approvalMessage,
                        dangerous,
                        parametersOf(method)
                ));

                toolInvocations.put(toolName, new ToolInvocation(
                        bean, method,
                        method.getAnnotation(RequiresRole.class),
                        method.getAnnotation(CacheableToolResult.class),
                        method.getAnnotation(ToolRetry.class)
                ));
                log.debug("Registered tool: {}", toolName);
            }
        }

        discovered = List.copyOf(definitions);
        log.info("AnnotationToolProvider: scanned {} tool(s)", discovered.size());
        return discovered;
    }

    /**
     * Describes method parameters as untyped strings, matching how arguments are
     * converted at invocation time by {@link #convertArgument}. Java parameter types are
     * not propagated to the model; that would be an improvement, but changing the
     * advertised schema changes model behaviour and is kept as a separate step.
     */
    private static List<ToolParameter> parametersOf(Method method) {
        if (method.getParameterCount() == 0) {
            return List.of();
        }
        List<ToolParameter> parameters = new ArrayList<>(method.getParameterCount());
        for (Parameter parameter : method.getParameters()) {
            parameters.add(ToolParameter.string(parameter.getName()));
        }
        return parameters;
    }

    @Override
    public String execute(String toolName, String jsonArguments, ToolExecutionContext context) {
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
                    log.debug("[AnnotationToolProvider] Cache hit for {} (key={})", toolName, cacheKey);
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
            log.warn("[AnnotationToolProvider] @RequiresRole on tool '{}' but no security context — denying", toolName);
            return errorJson("Access denied: no security context for role-restricted tool '" + toolName + "'");
        }
        if (securityContext.hasAnyRole(annotation.value())) {
            return null;
        }
        log.warn("[AnnotationToolProvider] User '{}' lacks role(s) {} for tool '{}'",
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
            log.error("[AnnotationToolProvider] Tool '{}' failed after retries: {}", toolName, e.getMessage());
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
