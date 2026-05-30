package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.skill.SkillCard;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for building and caching LangChain4j {@link ChatModel} instances.
 * Uses the OpenAI-compatible API for all providers (Bifrost gateway, Ollama, etc.).
 *
 * <p>Created via {@link LlmProviderAutoConfiguration}.</p>
 */
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final AgentProperties properties;
    private final LlmRouter llmRouter;
    @Nullable
    private final ApplicationContext applicationContext;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ConcurrentHashMap<String, ChatModel> modelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final CircuitBreaker primaryCircuitBreaker = CircuitBreaker.of("primary-llm",
            CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .slidingWindowSize(10)
                    .build());

    public LlmProviderFactory(AgentProperties properties, LlmRouter llmRouter) {
        this(properties, llmRouter, null, EmptyObjectProvider.instance());
    }

    public LlmProviderFactory(AgentProperties properties, LlmRouter llmRouter,
                              @Nullable ApplicationContext applicationContext,
                              ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.llmRouter = llmRouter;
        this.applicationContext = applicationContext;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Resolve which model alias to use for the given skill and routing context.
     */
    public String resolveModelAlias(SkillCard skillCard, LlmRoutingContext ctx) {
        if (skillCard != null && skillCard.preferredModel() != null && !skillCard.preferredModel().isBlank()) {
            log.debug("Using skill preferred model: {}", skillCard.preferredModel());
            return skillCard.preferredModel();
        }
        return llmRouter.resolve(ctx);
    }

    /**
     * Get the model config for a given alias. Recognizes "primary", "fallback", "routing"
     * as well-known aliases, then checks the models map, then falls back to primary.
     */
    public AgentProperties.LlmModelConfig getModelConfig(String alias) {
        return switch (alias) {
            case "primary" -> properties.getLlm().getPrimary();
            case "fallback" -> properties.getLlm().getFallback();
            case "routing" -> properties.getLlm().getRoutingModel();
            default -> {
                AgentProperties.LlmModelConfig config = properties.getLlm().getModels().get(alias);
                yield config != null ? config : properties.getLlm().getPrimary();
            }
        };
    }

    /**
     * Get or build a cached {@link ChatModel} for the given alias.
     */
    public ChatModel getModel(String alias) {
        return modelCache.computeIfAbsent(alias, this::buildModel);
    }

    /**
     * Get or build the primary model (used for agent conversations).
     */
    public ChatModel getPrimaryModel() {
        return getModel("primary");
    }

    /**
     * Get or build the fallback model (used for failover).
     * Returns {@code null} if no fallback is configured (no model name set).
     */
    @Nullable
    public ChatModel getFallbackModel() {
        var config = getModelConfig("fallback");
        if (config.getModel() == null || config.getModel().isBlank()) {
            return null;
        }
        return getModel("fallback");
    }

    /**
     * Get or build the routing model (used for skill routing and session summaries).
     */
    public ChatModel getRoutingModel() {
        return getModel("routing");
    }

    /**
     * Get or build a cached {@link StreamingChatModel} for the given alias.
     */
    public StreamingChatModel getStreamingModel(String alias) {
        return streamingModelCache.computeIfAbsent(alias, this::buildStreamingModel);
    }

    /**
     * Get or build the primary streaming model (used for streaming chat).
     */
    public StreamingChatModel getPrimaryStreamingModel() {
        return getStreamingModel("primary");
    }

    /**
     * Get the rate limiter for a given provider alias.
     */
    private RateLimiter getRateLimiter(String alias) {
        return rateLimiters.computeIfAbsent(alias, a -> {
            var rateLimit = properties.getLlm().getRateLimit();
            int maxRequests = rateLimit.getMaxRequests() > 0 ? rateLimit.getMaxRequests() : 60;
            int windowSeconds = rateLimit.getWindowSeconds() > 0 ? rateLimit.getWindowSeconds() : 60;
            return RateLimiter.of(a,
                    RateLimiterConfig.custom()
                            .limitForPeriod(maxRequests)
                            .limitRefreshPeriod(Duration.ofSeconds(windowSeconds))
                            .timeoutDuration(Duration.ofSeconds(10))
                            .build());
        });
    }

    private ChatModel buildModel(String alias) {
        var config = getModelConfig(alias);

        // 1. Spring bean lookup — let users register a ChatModel @Bean named after the alias
        //    (or after the provider). Wins over the built-in switch so adapters for Gemini,
        //    Mistral, Cohere etc. can be plugged in without touching this factory.
        ChatModel custom = lookupChatModelBean(alias, config.getProvider());
        if (custom != null) {
            log.info("Using user-provided ChatModel bean for alias={}, provider={}",
                    alias, config.getProvider());
            return custom;
        }

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "no-key"; // Bifrost/Ollama don't require API keys
        }

        String provider = config.getProvider() != null ? config.getProvider() : "openai";

        log.info("Building ChatModel: alias={}, provider={}, model={}, endpoint={}",
                alias, provider, config.getModel(), config.getEndpoint());

        return switch (provider) {
            case "anthropic" -> AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(config.getModel())
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .logRequests(log.isDebugEnabled())
                    .logResponses(log.isDebugEnabled())
                    .build();
            case "azure-openai" -> AzureOpenAiChatModel.builder()
                    .endpoint(config.getEndpoint())
                    .apiKey(apiKey)
                    .deploymentName(resolveAzureDeployment(config))
                    .serviceVersion(resolveAzureServiceVersion(config))
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .logRequestsAndResponses(log.isDebugEnabled())
                    .build();
            default -> {
                // openai, ollama and any OpenAI-compatible gateway (Bifrost, vLLM, …)
                String baseUrl = normalizeEndpoint(config.getEndpoint());
                yield OpenAiChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName(config.getModel())
                        .temperature(config.getTemperature())
                        .logRequests(log.isDebugEnabled())
                        .logResponses(log.isDebugEnabled())
                        .build();
            }
        };
    }

    private StreamingChatModel buildStreamingModel(String alias) {
        var config = getModelConfig(alias);

        StreamingChatModel custom = lookupStreamingChatModelBean(alias, config.getProvider());
        if (custom != null) {
            log.info("Using user-provided StreamingChatModel bean for alias={}, provider={}",
                    alias, config.getProvider());
            return custom;
        }

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "no-key";
        }

        String provider = config.getProvider() != null ? config.getProvider() : "openai";

        log.info("Building StreamingChatModel: alias={}, provider={}, model={}, endpoint={}",
                alias, provider, config.getModel(), config.getEndpoint());

        return switch (provider) {
            case "anthropic" -> AnthropicStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(config.getModel())
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .logRequests(log.isDebugEnabled())
                    .logResponses(log.isDebugEnabled())
                    .build();
            case "azure-openai" -> AzureOpenAiStreamingChatModel.builder()
                    .endpoint(config.getEndpoint())
                    .apiKey(apiKey)
                    .deploymentName(resolveAzureDeployment(config))
                    .serviceVersion(resolveAzureServiceVersion(config))
                    .temperature(config.getTemperature())
                    .maxTokens(config.getMaxTokens())
                    .logRequestsAndResponses(log.isDebugEnabled())
                    .build();
            default -> {
                String baseUrl = normalizeEndpoint(config.getEndpoint());
                yield OpenAiStreamingChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName(config.getModel())
                        .temperature(config.getTemperature())
                        .logRequests(log.isDebugEnabled())
                        .logResponses(log.isDebugEnabled())
                        .build();
            }
        };
    }

    /**
     * Resolve the Azure deployment name. Defaults to {@code config.model}
     * when {@code agent.llm.*.deployment-name} is left blank — Azure
     * deployments are conventionally named after the underlying model id
     * (e.g. {@code gpt-4o}), so this keeps the simple case zero-config.
     */
    private String resolveAzureDeployment(AgentProperties.LlmModelConfig config) {
        String deployment = config.getDeploymentName();
        return (deployment != null && !deployment.isBlank()) ? deployment : config.getModel();
    }

    /**
     * Resolve the Azure {@code api-version} (LangChain4j calls it
     * {@code serviceVersion}). Required for Azure Foundry — if left blank
     * we fall back to a sensible recent default and log a hint, but the
     * caller really should configure {@code agent.llm.*.api-version}
     * explicitly to match the Azure deployment's contract.
     */
    private String resolveAzureServiceVersion(AgentProperties.LlmModelConfig config) {
        String version = config.getApiVersion();
        if (version != null && !version.isBlank()) {
            return version;
        }
        log.warn("Azure OpenAI provider configured without agent.llm.*.api-version; "
                + "falling back to '2024-08-01-preview'. Set the version explicitly to match your deployment.");
        return "2024-08-01-preview";
    }

    /**
     * Ensure the endpoint URL ends with /v1 for OpenAI-compatible APIs.
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String url = endpoint;
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        if (end != url.length()) {
            url = url.substring(0, end);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }

    /**
     * Convenience method: generate a response from a specific model with a system prompt
     * and user message. Useful for routing, summaries, and other non-conversation calls.
     * Wrapped with circuit breaker for resilience.
     */
    public String generate(ChatModel model, String systemPrompt, String userMessage) {
        try {
            return primaryCircuitBreaker.executeSupplier(() -> {
                var response = model.chat(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)
                );
                return response.aiMessage().text();
            });
        } catch (Exception e) {
            log.warn("LLM call failed with circuit breaker: {}", e.getMessage());
            var fallback = getFallbackModel();
            if (fallback != null && fallback != model) {
                var response = fallback.chat(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)
                );
                return response.aiMessage().text();
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Generate a response by calling the resolved LLM model.
     */
    public String generate(String systemPrompt, String userMessage, SkillCard skillCard, LlmRoutingContext ctx) {
        return generate(systemPrompt, userMessage, skillCard, ctx, List.of());
    }

    /**
     * Generate a response with conversation history (working memory messages).
     */
    public String generate(String systemPrompt, String userMessage, SkillCard skillCard,
                           LlmRoutingContext ctx, List<ChatMessage> conversationHistory) {
        String alias = resolveModelAlias(skillCard, ctx);
        var config = getModelConfig(alias);
        log.info("LLM call: provider={}, model={}, alias={}, endpoint={}, historySize={}",
                config.getProvider(), config.getModel(), alias, config.getEndpoint(),
                conversationHistory.size());

        var model = getModel(alias);

        var messages = buildMessages(systemPrompt, userMessage, conversationHistory);

        String skillName = ctx != null && ctx.skillName() != null ? ctx.skillName() : "unknown";
        long startNanos = System.nanoTime();
        try {
            String result = RateLimiter.decorateSupplier(getRateLimiter(alias), () ->
                    primaryCircuitBreaker.executeSupplier(() -> {
                        var resp = model.chat(messages);
                        return resp.aiMessage().text();
                    })
            ).get();
            recordLatency(alias, skillName, startNanos);
            return result;
        } catch (Exception e) {
            recordError(alias);
            log.warn("Primary LLM failed (alias={}), trying fallback: {}", alias, e.getMessage());
            var fallback = getFallbackModel();
            if (fallback != null) {
                recordFallbackUsed(alias);
                long fallbackStart = System.nanoTime();
                try {
                    var resp = fallback.chat(messages);
                    recordLatency("fallback", skillName, fallbackStart);
                    return resp.aiMessage().text();
                } catch (Exception fe) {
                    recordError("fallback");
                    throw fe instanceof RuntimeException re ? re : new RuntimeException(fe);
                }
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Returns the user-registered {@link ChatModel} bean for {@code alias}, or for
     * {@code provider} as a fallback name. {@code null} when no such bean exists or the
     * application context wasn't injected (e.g. unit-test constructor).
     */
    @Nullable
    private ChatModel lookupChatModelBean(String alias, String provider) {
        if (applicationContext == null) return null;
        // alias bean wins over a provider-named bean
        ChatModel byAlias = lookupBean(alias, ChatModel.class);
        if (byAlias != null) return byAlias;
        if (isCustomProvider(provider)) {
            return lookupBean(provider, ChatModel.class);
        }
        return null;
    }

    private void recordLatency(String alias, String skillName, long startNanos) {
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            Timer.builder("agent.llm.model.latency")
                    .tag("model", alias != null ? alias : "")
                    .tag("skill", skillName != null ? skillName : "")
                    .register(meters)
                    .record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private void recordError(String alias) {
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            meters.counter("agent.llm.model.error_rate", "model", alias != null ? alias : "").increment();
        }
    }

    private void recordFallbackUsed(String originalAlias) {
        MeterRegistry meters = meterRegistryProvider.getIfAvailable();
        if (meters != null) {
            meters.counter("agent.llm.routing.fallback.used",
                    "original_model", originalAlias != null ? originalAlias : "").increment();
        }
    }

    @Nullable
    private StreamingChatModel lookupStreamingChatModelBean(String alias, String provider) {
        if (applicationContext == null) return null;
        // streaming beans are registered under the "<name>Streaming" convention
        StreamingChatModel byAlias = lookupBean(streamingBeanName(alias), StreamingChatModel.class);
        if (byAlias != null) return byAlias;
        if (isCustomProvider(provider)) {
            return lookupBean(streamingBeanName(provider), StreamingChatModel.class);
        }
        return null;
    }

    /** {@code true} for providers that may have a user-registered bean (i.e. not the built-in ones). */
    private static boolean isCustomProvider(String provider) {
        return provider != null && !provider.isBlank()
                && !"openai".equals(provider) && !"anthropic".equals(provider);
    }

    @Nullable
    private static String streamingBeanName(String name) {
        return (name != null && !name.isBlank()) ? name + "Streaming" : null;
    }

    /**
     * Look up a bean by name and return it only if it is assignable to {@code type}.
     * {@code null} when the name is blank, no such bean exists, or it has a
     * different type (a name clash with an unrelated bean falls through silently).
     */
    @Nullable
    private <T> T lookupBean(@Nullable String name, Class<T> type) {
        if (name == null || name.isBlank() || !applicationContext.containsBean(name)) {
            return null;
        }
        try {
            Object bean = applicationContext.getBean(name);
            if (type.isInstance(bean)) return type.cast(bean);
        } catch (Exception ignored) {
            // bean exists under that name but isn't the type we want — ignore
        }
        return null;
    }

    /**
     * Build a LangChain4j message list from system prompt, user message, and conversation history.
     * Exposed for use by streaming and tool-calling paths.
     */
    public List<dev.langchain4j.data.message.ChatMessage> buildMessages(
            String systemPrompt, String userMessage, List<ChatMessage> conversationHistory) {
        var messages = new ArrayList<dev.langchain4j.data.message.ChatMessage>();
        messages.add(SystemMessage.from(systemPrompt));

        for (var msg : conversationHistory) {
            if ("user".equals(msg.role())) {
                messages.add(UserMessage.from(msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(AiMessage.from(msg.content()));
            }
        }

        messages.add(UserMessage.from(userMessage));
        return messages;
    }
}
