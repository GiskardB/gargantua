package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.memory.ChatMessage;
import ai.gargantua.core.skill.SkillCard;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Get or build the routing model (used for skill routing and session summaries).
     */
    public ChatModel getRoutingModel() {
        return getModel("routing");
    }

    private ChatModel buildModel(String alias) {
        var config = getModelConfig(alias);
        String baseUrl = normalizeEndpoint(config.getEndpoint());

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "no-key"; // Bifrost/Ollama don't require API keys
        }

        log.info("Building ChatModel: alias={}, provider={}, model={}, endpoint={}",
                alias, config.getProvider(), config.getModel(), baseUrl);

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(config.getModel())
                .temperature(config.getTemperature())
                .logRequests(log.isDebugEnabled())
                .logResponses(log.isDebugEnabled())
                .build();
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

        var messages = new ArrayList<dev.langchain4j.data.message.ChatMessage>();
        messages.add(SystemMessage.from(systemPrompt));

        // Add conversation history (working memory)
        for (var msg : conversationHistory) {
            if ("user".equals(msg.role())) {
                messages.add(UserMessage.from(msg.content()));
            } else if ("assistant".equals(msg.role())) {
                messages.add(AiMessage.from(msg.content()));
            }
        }

        // Add the current user message
        messages.add(UserMessage.from(userMessage));

        var response = model.chat(messages);
        String text = response.aiMessage().text();

        log.debug("LLM response ({}): {}", alias, text != null && text.length() > 200
                ? text.substring(0, 200) + "..." : text);
        return text;
    }
}
