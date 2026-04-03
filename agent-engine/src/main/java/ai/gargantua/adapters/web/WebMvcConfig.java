package ai.gargantua.adapters.web;

import ai.gargantua.autoconfigure.AgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 * <ul>
 *   <li>{@code /docs} → Redoc API documentation</li>
 *   <li>{@code /chat} → Built-in chat UI (when {@code agent.chat-ui.enabled=true})</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AgentProperties properties;

    public WebMvcConfig(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/docs", "/docs/index.html");
        if (properties.getChatUi().isEnabled()) {
            registry.addRedirectViewController("/chat", "/chat.html");
        }
    }
}
