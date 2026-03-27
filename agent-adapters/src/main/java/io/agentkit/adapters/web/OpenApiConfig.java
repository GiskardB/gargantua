package io.agentkit.adapters.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${agent.display-name:AI Agent}")
    private String agentDisplayName;

    @Bean
    public OpenAPI agentKitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(agentDisplayName + " API")
                        .description("REST API for the AgentKit AI Agent framework")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AgentKit Team")
                                .url("https://github.com/agentkit"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    @Bean
    public GroupedOpenApi chatApi() {
        return GroupedOpenApi.builder()
                .group("chat")
                .displayName("Chat API")
                .pathsToMatch("/api/agent/**", "/api/capabilities")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin API")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
