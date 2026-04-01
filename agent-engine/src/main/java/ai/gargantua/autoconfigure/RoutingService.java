package ai.gargantua.autoconfigure;

import ai.gargantua.core.skill.SkillMeta;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM-based skill routing service. Uses the routing model (typically Ollama phi4-mini)
 * to classify user messages into the most appropriate skill. This is used as a fallback
 * when semantic routing confidence is below threshold.
 */
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final AgentProperties properties;
    private final LlmProviderFactory llmProviderFactory;

    public RoutingService(AgentProperties properties, LlmProviderFactory llmProviderFactory) {
        this.properties = properties;
        this.llmProviderFactory = llmProviderFactory;
    }

    /**
     * Route using the routing LLM model. Sends a classification prompt with all
     * available skills and returns the best matching skill name.
     */
    public String routeWithLlm(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return properties.getRouting().getFallbackSkill();
        }

        // Build the skill catalog for the classification prompt
        String skillCatalog = skills.stream()
                .filter(SkillMeta::active)
                .map(s -> "- " + s.name() + ": " + (s.description() != null ? s.description() : "No description"))
                .collect(Collectors.joining("\n"));

        List<String> skillNames = skills.stream()
                .filter(SkillMeta::active)
                .map(SkillMeta::name)
                .toList();

        String systemPrompt = """
                You are a skill router. Given a user message and a list of available skills, \
                respond with ONLY the name of the single best-matching skill. \
                Do not explain. Do not add punctuation. Just output the skill name exactly as listed.

                Available skills:
                %s
                """.formatted(skillCatalog);

        try {
            ChatModel routingModel = llmProviderFactory.getRoutingModel();
            var response = routingModel.chat(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );
            String result = response.aiMessage().text().trim();

            // Validate the result is a known skill name
            String matched = skillNames.stream()
                    .filter(name -> name.equalsIgnoreCase(result) || result.toLowerCase().contains(name.toLowerCase()))
                    .findFirst()
                    .orElse(null);

            if (matched != null) {
                log.debug("LLM routing result: '{}' -> skill '{}'", result, matched);
                return matched;
            }

            log.warn("LLM routing returned unknown skill '{}', falling back to '{}'",
                    result, properties.getRouting().getFallbackSkill());
            return properties.getRouting().getFallbackSkill();

        } catch (Exception e) {
            log.error("LLM routing failed: {}, falling back to '{}'",
                    e.getMessage(), properties.getRouting().getFallbackSkill());
            return properties.getRouting().getFallbackSkill();
        }
    }
}
