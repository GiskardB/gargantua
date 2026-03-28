package ai.gargantua.autoconfigure;

import ai.gargantua.core.skill.SkillMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder routing service that uses LLM to route messages.
 * Will use ChatLanguageModel when wired.
 */
@Component
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final AgentProperties properties;

    public RoutingService(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Route using LLM. Placeholder implementation returns "default-skill".
     */
    public String routeWithLlm(String userMessage, List<SkillMeta> skills) {
        log.debug("LLM routing placeholder called for message: '{}'", truncate(userMessage, 80));

        // Placeholder: return fallback skill or first active skill
        if (skills != null) {
            for (SkillMeta skill : skills) {
                if (skill.active()) {
                    return skill.name();
                }
            }
        }

        return properties.getRouting().getFallbackSkill();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
