package ai.gargantua.adapters.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures OpenAPI / Swagger documentation. Splits the published spec into
 * three groups so the Swagger UI sidebar stays navigable:
 *
 * <ul>
 *   <li><b>chat</b>   — the primary chat surface plus protocol endpoints
 *       (`/api/agent/chat`, `/api/agent/chat/stream`, `/api/agent/approval/*`,
 *       `/.well-known/agent.json`, `/a2a`).</li>
 *   <li><b>admin</b>  — operator endpoints under `/api/admin/**`
 *       (audit, costs, skills, guardrails, LLM routing, tool cache,
 *       chat history / export).</li>
 *   <li><b>protocols</b> — A2A discovery / JSON-RPC.</li>
 * </ul>
 *
 * <p>The displayed agent name comes from {@code agent.display-name} (or
 * {@code agent.api.display-name} when that is set in your application
 * properties).</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${agent.api.display-name:${agent.display-name:Gargantua AI Agent}}")
    private String agentDisplayName;

    @Value("${agent.api.version:1.0.0}")
    private String agentVersion;

    @Value("${agent.api.description:}")
    private String agentDescription;

    @Bean
    public OpenAPI agentOpenApi() {
        String description = agentDescription != null && !agentDescription.isBlank()
                ? agentDescription
                : """
                  REST API for an AI agent built on the **Gargantua AI Agent Framework**.

                  Groups:
                  - **chat** — synchronous and streaming chat (`/api/agent/chat`, `/api/agent/chat/stream`),
                    human-in-the-loop approvals (`/api/agent/approval/*`), and the agent-to-agent
                    discovery / RPC surface (`/.well-known/agent.json`, `/a2a`).
                  - **admin** — operator endpoints under `/api/admin/**` for audit, costs,
                    skills, guardrails, LLM routing rules and the tool-result cache.

                  Headers honoured by every chat endpoint:
                  - `X-User-Id`, `X-Session-Id` — identity & session for memory.
                  - `X-User-Roles` — comma-separated list, drives RBAC (`@RequiresRole` + skill `allowed-roles`).
                  - `X-Tenant-Id` — multi-tenancy partition for audit + episodic memory.
                  - `X-Dry-Run: true` — execute the pipeline without side-effecting tools.
                  - `X-Force-Skill: <name>` — bypass routing and activate a skill directly.
                  """;

        return new OpenAPI()
                .info(new Info()
                        .title("%s API".formatted(agentDisplayName))
                        .description(description)
                        .version(agentVersion)
                        .contact(new Contact()
                                .name("Gargantua")
                                .url("https://github.com/GiskardB/gargantua")
                                .email("noreply@gargantua.ai"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Gargantua docs")
                        .url("https://giskardb.github.io/gargantua-site"));
    }

    @Bean
    public GroupedOpenApi chatApi() {
        return GroupedOpenApi.builder()
                .group("chat")
                .displayName("Chat API")
                .pathsToMatch("/api/agent/**", "/.well-known/agent.json", "/a2a")
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
