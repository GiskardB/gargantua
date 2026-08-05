package ai.gargantua.autoconfigure;

import ai.gargantua.core.hitl.ApprovalDecision;
import ai.gargantua.core.hitl.ApprovalRequest;
import ai.gargantua.core.hitl.ApprovalStore;
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
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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

    /** Default HITL TTL when no {@link AgentProperties} bean is available. */
    private static final int DEFAULT_HITL_TTL_MINUTES = 5;

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ToolResultCache> toolResultCacheProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<ApprovalStore> approvalStoreProvider;
    private final ObjectProvider<AgentProperties> agentPropertiesProvider;
    private final Map<String, ToolDefinition> tools = new HashMap<>();
    private final Map<String, ToolInvocation> toolInvocations = new HashMap<>();
    /**
     * Tool sources other than annotation scanning — one per MCP server. Compiled
     * {@code @AgentTool} methods always win a name collision, so a local tool can
     * deliberately shadow a remote one.
     */
    private final List<ToolProvider> additionalProviders;
    private final Map<String, ToolProvider> providerRouting = new LinkedHashMap<>();
    private final Map<List<String>, List<ToolSpecification>> specCache = new ConcurrentHashMap<>();

    /**
     * Holds the bean + method plus the four method-level annotations evaluated on
     * every invocation. Caching them at scan time turns four reflective lookups
     * per tool call ({@link RequiresRole}, {@link RequiresApproval},
     * {@link CacheableToolResult}, {@link ToolRetry}) into pure field reads on
     * the hot path.
     */
    private record ToolInvocation(
            Object bean,
            Method method,
            @org.springframework.lang.Nullable RequiresRole requiresRole,
            @org.springframework.lang.Nullable RequiresApproval requiresApproval,
            @org.springframework.lang.Nullable CacheableToolResult cacheable,
            @org.springframework.lang.Nullable ToolRetry retry) {}

    public ToolRegistry(ApplicationContext applicationContext,
                        ObjectProvider<ToolResultCache> toolResultCacheProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider,
                        ObjectProvider<ApprovalStore> approvalStoreProvider,
                        ObjectProvider<AgentProperties> agentPropertiesProvider) {
        this(applicationContext, toolResultCacheProvider, meterRegistryProvider,
                approvalStoreProvider, agentPropertiesProvider, List.of());
    }

    /**
     * Registry combining annotation-scanned tools with additional providers, typically
     * one per MCP server declared in configuration or in a bundle manifest.
     */
    public ToolRegistry(ApplicationContext applicationContext,
                        ObjectProvider<ToolResultCache> toolResultCacheProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider,
                        ObjectProvider<ApprovalStore> approvalStoreProvider,
                        ObjectProvider<AgentProperties> agentPropertiesProvider,
                        List<ToolProvider> additionalProviders) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper();
        this.toolResultCacheProvider = toolResultCacheProvider;
        this.meterRegistryProvider = meterRegistryProvider;
        this.approvalStoreProvider = approvalStoreProvider;
        this.agentPropertiesProvider = agentPropertiesProvider;
        this.additionalProviders = additionalProviders == null ? List.of() : List.copyOf(additionalProviders);
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
                RequiresApproval approval = method.getAnnotation(RequiresApproval.class);
                boolean requiresApproval = approval != null;
                String approvalMessage = requiresApproval ? approval.message() : "";
                boolean dangerous = requiresApproval && approval.dangerous();
                String[] showParameters = requiresApproval ? approval.showParameters() : new String[0];

                var def = new ToolDefinition(
                        toolName,
                        annotation.description(),
                        annotation.parallelizable(),
                        requiresApproval,
                        method.isAnnotationPresent(CacheableToolResult.class),
                        approvalMessage,
                        showParameters,
                        dangerous,
                        parametersOf(method)
                );

                tools.put(toolName, def);
                toolInvocations.put(toolName, new ToolInvocation(
                        bean, method,
                        method.getAnnotation(RequiresRole.class),
                        approval,
                        method.getAnnotation(CacheableToolResult.class),
                        method.getAnnotation(ToolRetry.class)
                ));
                count++;
                log.debug("Registered tool: {}", toolName);
            }
        }

        registerProviderTools();
        log.info("ToolRegistry: {} tool(s) — {} from annotations, {} from {} provider(s)",
                tools.size(), count, providerRouting.size(), additionalProviders.size());
    }

    /**
     * Describes a method's parameters as untyped strings, matching how arguments are
     * converted at invocation time. Java parameter types are deliberately not propagated
     * to the model — changing the advertised schema changes model behaviour, so it is
     * kept as a separate step. Populating this keeps {@link ToolDefinition} consistent
     * whatever the tool's source.
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

    /**
     * Adds tools contributed by non-annotation sources. Discovery failures are logged and
     * skipped so one unreachable MCP server cannot stop the agent from starting, and a
     * name already claimed by a compiled tool is left alone.
     */
    private void registerProviderTools() {
        for (ToolProvider provider : additionalProviders) {
            List<ToolDefinition> definitions;
            try {
                definitions = provider.discover();
            } catch (Exception e) {
                log.error("Tool provider '{}' failed during discovery, skipping: {}",
                        provider.name(), e.getMessage());
                continue;
            }
            if (definitions == null) continue;
            for (ToolDefinition definition : definitions) {
                if (definition == null || definition.name() == null) continue;
                if (tools.containsKey(definition.name())) {
                    log.warn("Tool '{}' from provider '{}' ignored — already provided locally",
                            definition.name(), provider.name());
                    continue;
                }
                tools.put(definition.name(), definition);
                providerRouting.put(definition.name(), provider);
            }
        }
    }

    /** Releases provider resources — child processes and connections for MCP servers. */
    @PreDestroy
    public void shutdown() {
        for (ToolProvider provider : additionalProviders) {
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
            } else if (invocation == null && td.hasParameters()) {
                // Provider-supplied tool: it declares its own schema, so types and
                // required flags are passed through rather than flattened to strings.
                var schemaBuilder = JsonObjectSchema.builder();
                List<String> required = new ArrayList<>();
                for (ToolParameter parameter : td.parameters()) {
                    addProperty(schemaBuilder, parameter);
                    if (parameter.required()) required.add(parameter.name());
                }
                if (!required.isEmpty()) schemaBuilder.required(required);
                builder.parameters(schemaBuilder.build());
            }

            return builder.build();
        }).toList();
    }

    /**
     * Maps a provider-neutral parameter onto the schema builder. Structural types
     * (object, array) degrade to string rather than failing, since the tool can still
     * parse them.
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
            ToolProvider provider = providerRouting.get(toolName);
            if (provider != null) {
                // Remote tools carry no method annotations, so the RBAC / approval /
                // cache / retry gates below do not apply to them; the provider's own
                // allow-list and the skill's allowed-tools are what constrain them.
                return provider.execute(toolName, jsonArguments,
                        context != null ? context : ToolExecutionContext.empty());
            }
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

        // 2. HITL gate via cached @RequiresApproval. Runs BEFORE the cache so an
        //    approved tool can't be served by a stale cached result that
        //    bypassed approval. The gate returns:
        //      - Optional.empty()  → no pending decision matches: invocation
        //                            short-circuited with awaiting_approval,
        //                            or a stored deny was consumed into a refusal.
        //      - Optional.present  → response JSON to return immediately.
        //    When the gate finds a previously-stored "approve" decision for an
        //    identical (tool, args, userId) tuple it consumes it (removes from
        //    store) and returns Optional.empty() — falling through to the
        //    normal cache/retry/invoke pipeline below so the tool body runs.
        RequiresApproval approvalAnn = invocation.requiresApproval();
        if (approvalAnn != null) {
            Optional<String> gated = tryConsumeApprovalGate(toolName, args, approvalAnn, ctx);
            if (gated.isPresent()) return gated.get();
        }

        // 3. Cacheable read-through (cached @CacheableToolResult)
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

        // 3. Retry-wrapped invocation (cached @ToolRetry). Whether or not
        //    @ToolRetry is present, any RuntimeException thrown by the tool
        //    body is converted to a {"error":"..."} payload so the LLM gets
        //    a recoverable signal instead of the request blowing up.
        Supplier<String> call = () -> doInvoke(invocation, args);
        ToolRetry retryAnn = invocation.retry();
        String result;
        if (retryAnn != null) {
            result = executeWithRetry(toolName, retryAnn, call);
        } else {
            try {
                result = call.get();
            } catch (RuntimeException e) {
                log.error("[ToolRegistry] Tool '{}' threw: {}", toolName, e.getMessage());
                result = errorJson("Tool execution failed: " + safeMessage(e));
            }
        }

        // 4. Cache put on success
        if (cacheKey != null && !isErrorPayload(result)) {
            cache.put(cacheKey, result, cacheable.ttlSeconds());
        }
        return result;
    }

    /**
     * Three-way gate for {@link RequiresApproval}-annotated tools.
     *
     * <ul>
     *   <li>If the store already holds an {@code approve} decision for the
     *       (toolName, args, userId) triple, consume it (so the same
     *       approval can't be replayed) and return {@link Optional#empty()} —
     *       the caller falls through to the normal invoke pipeline.</li>
     *   <li>If the store holds a non-{@code approve} decision (deny / any
     *       other value), consume it and return a refusal JSON.</li>
     *   <li>Otherwise create a pending request keyed by the same triple and
     *       return {@code awaiting_approval} JSON.</li>
     * </ul>
     *
     * <p>The {@code requestId} is a deterministic UUIDv3 derived from
     * {@code (toolName | userId | sorted-args-json)} so the LLM's retry
     * after the human posts {@code POST /api/agent/approval/{id}} hits the
     * same key and unblocks the tool body. Applied uniformly to every chat
     * path that goes through {@link #executeTool}.</p>
     */
    private Optional<String> tryConsumeApprovalGate(String toolName, Map<String, String> args,
                                                    RequiresApproval ann, ToolExecutionContext ctx) {
        String userId = ctx.securityContext() != null ? ctx.securityContext().userId() : "anonymous";
        String requestId = deterministicRequestId(toolName, args, userId);
        ApprovalStore store = approvalStoreProvider.getIfAvailable();

        if (store != null) {
            Optional<ApprovalDecision> existing = store.getDecision(requestId);
            if (existing.isPresent()) {
                String decision = existing.get().decision();
                store.clearDecision(requestId);
                if (isApprove(decision)) {
                    log.info("[ToolRegistry] Consuming prior approval for {} (requestId={}) — proceeding",
                            toolName, requestId);
                    return Optional.empty();
                }
                log.info("[ToolRegistry] Consuming prior denial for {} (requestId={}) — refusing",
                        toolName, requestId);
                return Optional.of(errorJson("Tool '" + toolName + "' was denied by the human reviewer"
                        + (existing.get().reason() != null && !existing.get().reason().isBlank()
                            ? ": " + existing.get().reason() : ".")));
            }

            try {
                int ttl = hitlTtlMinutes();
                Map<String, Object> parameters = filterShowParameters(args, ann.showParameters());
                ApprovalRequest req = new ApprovalRequest(
                        requestId,
                        ctx.sessionId(),
                        userId,
                        toolName,
                        parameters,
                        ann.message(),
                        ann.dangerous(),
                        Instant.now().plusSeconds(ttl * 60L)
                );
                store.savePending(requestId, req, Duration.ofMinutes(ttl));
            } catch (Exception e) {
                log.warn("[ToolRegistry] Failed to persist pending approval for {}: {}",
                        toolName, e.getMessage());
            }
        } else {
            log.warn("[ToolRegistry] @RequiresApproval on tool '{}' but no ApprovalStore configured — "
                    + "request {} cannot be resolved", toolName, requestId);
        }
        return Optional.of("{\"status\":\"awaiting_approval\",\"requestId\":\"" + requestId + "\"}");
    }

    private static boolean isApprove(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        return s.equalsIgnoreCase("approve") || s.equalsIgnoreCase("approved");
    }

    /**
     * Build a stable UUIDv3 from {@code (toolName | userId | canonical-args-json)}.
     * Identical inputs across two calls produce identical ids — that is what lets
     * the gate recognise an LLM retry after the human resolved the approval.
     */
    private String deterministicRequestId(String toolName, Map<String, String> args, String userId) {
        try {
            TreeMap<String, String> sorted = new TreeMap<>(args == null ? Map.of() : args);
            String json = objectMapper.writeValueAsString(sorted);
            String seed = toolName + "|" + userId + "|" + json;
            return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        } catch (Exception e) {
            // Fallback to a random id — gate still works for the first call, just
            // forfeits the resume-after-approve path for this one.
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Apply the {@link RequiresApproval#showParameters()} filter to a raw
     * arguments map. An empty {@code showParameters} array means "show all".
     */
    private Map<String, Object> filterShowParameters(Map<String, String> args, String[] showParameters) {
        if (args == null || args.isEmpty()) return Map.of();
        if (showParameters == null || showParameters.length == 0) {
            return new LinkedHashMap<>(args);
        }
        Set<String> include = Set.of(showParameters);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            if (include.contains(e.getKey())) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }

    private int hitlTtlMinutes() {
        AgentProperties props = agentPropertiesProvider.getIfAvailable();
        if (props == null) return DEFAULT_HITL_TTL_MINUTES;
        try {
            int ttl = props.getHitl().getDefaultTtlMinutes();
            return ttl > 0 ? ttl : DEFAULT_HITL_TTL_MINUTES;
        } catch (Exception e) {
            return DEFAULT_HITL_TTL_MINUTES;
        }
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

    /**
     * Peel framework-internal wrappers so the retry predicate sees the
     * exception type the tool body actually threw. Two wrappers can appear:
     *
     * <ul>
     *   <li>{@link java.lang.reflect.InvocationTargetException} — from
     *       {@link java.lang.reflect.Method#invoke}.</li>
     *   <li>{@link CheckedToolException} — our marker for a checked exception
     *       lifted out of a reflective invocation (see {@link #doInvoke}).</li>
     * </ul>
     *
     * <p>Only these two are unwrapped; a {@link RuntimeException} that the tool
     * deliberately constructed with a cause is left alone, since its type IS
     * the type the predicate should match.</p>
     */
    private Throwable unwrap(Throwable t) {
        Throwable current = t;
        while ((current instanceof java.lang.reflect.InvocationTargetException
                || current instanceof CheckedToolException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Marker wrapper used by {@link #doInvoke} when a tool method throws a
     * checked exception. {@link #unwrap} knows to peel this back to the
     * original throwable so {@code retryOn}/{@code abortOn} predicates see
     * the type the developer declared, not a generic {@code RuntimeException}.
     */
    private static final class CheckedToolException extends RuntimeException {
        CheckedToolException(Throwable cause) { super(cause); }
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
            // Checked exception: lift through a marker wrapper so unwrap()
            // can restore the original type for the retry predicate.
            throw new CheckedToolException(cause);
        } catch (Exception e) {
            throw new CheckedToolException(e);
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
