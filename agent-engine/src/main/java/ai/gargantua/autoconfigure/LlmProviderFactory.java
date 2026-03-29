package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.skill.SkillCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for building LLM model instances. Currently a placeholder that returns
 * a stub. The real implementation will build LangChain4j ChatLanguageModel instances
 * based on provider configuration.
 */
@Component
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final AgentProperties properties;
    private final LlmRouter llmRouter;

    public LlmProviderFactory(AgentProperties properties, LlmRouter llmRouter) {
        this.properties = properties;
        this.llmRouter = llmRouter;
    }

    /**
     * Get the model configuration for the given skill and routing context.
     * Returns the resolved model alias after applying routing rules.
     */
    public String resolveModelAlias(SkillCard skillCard, LlmRoutingContext ctx) {
        // If the skill has a preferred model, use it
        if (skillCard != null && skillCard.preferredModel() != null && !skillCard.preferredModel().isBlank()) {
            log.debug("Using skill preferred model: {}", skillCard.preferredModel());
            return skillCard.preferredModel();
        }

        // Otherwise, use the LLM router
        return llmRouter.resolve(ctx);
    }

    /**
     * Get the model config for a given alias.
     */
    public AgentProperties.LlmModelConfig getModelConfig(String alias) {
        // Check models map first
        AgentProperties.LlmModelConfig config = properties.getLlm().getModels().get(alias);
        if (config != null) {
            return config;
        }

        // Fall back to primary config
        return properties.getLlm().getPrimary();
    }

    /**
     * Placeholder: generate a response using the resolved model.
     * The real implementation will create LangChain4j ChatLanguageModel instances.
     */
    public String generate(String systemPrompt, String userMessage, SkillCard skillCard, LlmRoutingContext ctx) {
        String alias = resolveModelAlias(skillCard, ctx);
        AgentProperties.LlmModelConfig config = getModelConfig(alias);
        log.info("LLM call placeholder: provider={}, model={}, alias={}",
                config.getProvider(), config.getModel(), alias);

        return "[Placeholder LLM response for model '" + config.getModel() + "']";
    }
}
