package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmClient;
import ai.gargantua.core.llm.LlmRequest;
import ai.gargantua.core.llm.LlmResponse;
import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.autoconfigure.llm.AnthropicLlmClient;
import ai.gargantua.autoconfigure.llm.OllamaLlmClient;
import ai.gargantua.autoconfigure.llm.OpenAiLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for building and caching {@link LlmClient} instances based on
 * provider configuration. Creates the appropriate native HTTP client
 * (OpenAI, Anthropic, Azure OpenAI, or Ollama) from {@link AgentProperties}.
 *
 * <p>Created via {@link LlmProviderAutoConfiguration}.</p>
 */
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final AgentProperties properties;
    private final LlmRouter llmRouter;
    private final Map<String, LlmClient> clientCache = new ConcurrentHashMap<>();

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
     * Get or create an {@link LlmClient} for the given provider configuration.
     * Clients are cached by a key derived from provider + endpoint + apiKey.
     */
    public LlmClient getLlmClient(AgentProperties.LlmModelConfig config) {
        String cacheKey = config.getProvider() + "|" + config.getEndpoint() + "|" + config.getApiKey();
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(config));
    }

    /**
     * Get or create the routing {@link LlmClient} (used for skill routing and session summaries).
     */
    public LlmClient getRoutingClient() {
        return getLlmClient(properties.getLlm().getRoutingModel());
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

        LlmClient client = getLlmClient(config);

        var messages = new ArrayList<LlmRequest.LlmMessage>();
        messages.add(new LlmRequest.LlmMessage("system", systemPrompt));

        // Add conversation history (working memory)
        for (var msg : conversationHistory) {
            if ("user".equals(msg.role())) {
                messages.add(new LlmRequest.LlmMessage("user", msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(new LlmRequest.LlmMessage("assistant", msg.content()));
            }
        }

        // Add the current user message
        messages.add(new LlmRequest.LlmMessage("user", userMessage));

        var request = new LlmRequest(
                config.getModel(),
                messages,
                config.getTemperature(),
                config.getMaxTokens()
        );

        LlmResponse response = client.chat(request);
        String text = response.content();

        log.debug("LLM response ({}): {}", alias, text != null && text.length() > 200
                ? text.substring(0, 200) + "..." : text);
        return text;
    }

    private LlmClient createClient(AgentProperties.LlmModelConfig config) {
        String provider = config.getProvider().toLowerCase();
        return switch (provider) {
            case "openai" -> new OpenAiLlmClient(
                    defaultIfBlank(config.getEndpoint(), "https://api.openai.com/v1"),
                    config.getApiKey(), false);
            case "azure-openai", "azure_openai" -> new OpenAiLlmClient(
                    config.getEndpoint(), config.getApiKey(), true);
            case "anthropic" -> new AnthropicLlmClient(
                    defaultIfBlank(config.getEndpoint(), "https://api.anthropic.com"),
                    config.getApiKey());
            case "ollama" -> new OllamaLlmClient(
                    defaultIfBlank(config.getEndpoint(), "http://localhost:11434"));
            default -> throw new IllegalArgumentException(
                    "Unsupported LLM provider: '%s'. Built-in providers: openai, anthropic, azure-openai, ollama. "
                    + "For other providers, register a custom LlmClient bean.".formatted(provider));
        };
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
