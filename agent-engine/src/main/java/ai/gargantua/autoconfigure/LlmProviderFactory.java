package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.skill.SkillCard;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        this.properties = properties;
        this.llmRouter = llmRouter;
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
        return rateLimiters.computeIfAbsent(alias, a -> RateLimiter.of(a,
                RateLimiterConfig.custom()
                        .limitForPeriod(60)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ofSeconds(10))
                        .build()));
    }

    private ChatModel buildModel(String alias) {
        var config = getModelConfig(alias);

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
            default -> {
                // openai, azure-openai, ollama all speak OpenAI-compatible protocol
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
     * Ensure the endpoint URL ends with /v1 for OpenAI-compatible APIs.
     */
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String url = endpoint.replaceAll("/+$", "");
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }

    /**
     * Convenience method: generate a response from a specific model with a system prompt
     * and user message. Useful for routing, eval judging, and other non-conversation calls.
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

        try {
            return RateLimiter.decorateSupplier(getRateLimiter(alias), () ->
                    primaryCircuitBreaker.executeSupplier(() -> {
                        var resp = model.chat(messages);
                        return resp.aiMessage().text();
                    })
            ).get();
        } catch (Exception e) {
            log.warn("Primary LLM failed (alias={}), trying fallback: {}", alias, e.getMessage());
            var fallback = getFallbackModel();
            if (fallback != null) {
                var resp = fallback.chat(messages);
                return resp.aiMessage().text();
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
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
