package ai.gargantua.mcp;

import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.mcp.gateway.ChatMcpTool;
import ai.gargantua.mcp.resources.CapabilitiesMcpResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the v1.2.13 auto-config wiring of the MCP server beans:
 *
 * <ul>
 *   <li>Conditional activation gate (agent.mcp.enabled=true).</li>
 *   <li>{@code ChatMcpTool} + {@code CapabilitiesMcpResource} registered via
 *       explicit {@code @Bean} factories — no longer relying on package
 *       component-scanning that did not reach user-app base packages.</li>
 *   <li>{@code AgentMcpProperties} bound from {@code agent.mcp.*} properties.</li>
 * </ul>
 */
class AgentMcpServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentMcpServerAutoConfiguration.class))
            .withUserConfiguration(StubOrchestratorEngineConfig.class);

    @Test
    @DisplayName("Beans are NOT registered when agent.mcp.enabled is unset (default false)")
    void disabledByDefault() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(ChatMcpTool.class);
            assertThat(ctx).doesNotHaveBean(CapabilitiesMcpResource.class);
            assertThat(ctx).doesNotHaveBean(AgentMcpServerAutoConfiguration.class);
        });
    }

    @Test
    @DisplayName("Beans are NOT registered when agent.mcp.enabled=false")
    void disabledExplicitly() {
        contextRunner
                .withPropertyValues("agent.mcp.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ChatMcpTool.class);
                    assertThat(ctx).doesNotHaveBean(CapabilitiesMcpResource.class);
                });
    }

    @Test
    @DisplayName("All MCP beans are registered when agent.mcp.enabled=true")
    void enabledRegistersAllBeans() {
        contextRunner
                .withPropertyValues("agent.mcp.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AgentMcpProperties.class);
                    assertThat(ctx).hasSingleBean(ChatMcpTool.class);
                    assertThat(ctx).hasSingleBean(CapabilitiesMcpResource.class);
                    assertThat(ctx).hasSingleBean(AgentMcpServerAutoConfiguration.class);
                });
    }

    @Test
    @DisplayName("AgentMcpProperties binds the full agent.mcp.* property tree")
    void propertiesBindFromConfig() {
        contextRunner
                .withPropertyValues(
                        "agent.mcp.enabled=true",
                        "agent.mcp.mode=embedded",
                        "agent.mcp.server.name=my-agent",
                        "agent.mcp.server.version=2.3.4",
                        "agent.mcp.server.description=Demo gateway",
                        "agent.mcp.transport.type=stdio",
                        "agent.mcp.transport.path=/custom",
                        "agent.mcp.gateway.tool-name=ask-the-agent",
                        "agent.mcp.gateway.tool-description=Use this when…",
                        "agent.mcp.security.auth-required=true",
                        "agent.mcp.security.token-header=X-Auth"
                )
                .run(ctx -> {
                    AgentMcpProperties p = ctx.getBean(AgentMcpProperties.class);
                    assertThat(p.isEnabled()).isTrue();
                    assertThat(p.getMode()).isEqualTo("embedded");
                    assertThat(p.getServer().getName()).isEqualTo("my-agent");
                    assertThat(p.getServer().getVersion()).isEqualTo("2.3.4");
                    assertThat(p.getServer().getDescription()).isEqualTo("Demo gateway");
                    assertThat(p.getTransport().getType()).isEqualTo("stdio");
                    assertThat(p.getTransport().getPath()).isEqualTo("/custom");
                    assertThat(p.getGateway().getToolName()).isEqualTo("ask-the-agent");
                    assertThat(p.getGateway().getToolDescription()).isEqualTo("Use this when…");
                    assertThat(p.getSecurity().isAuthRequired()).isTrue();
                    assertThat(p.getSecurity().getTokenHeader()).isEqualTo("X-Auth");
                });
    }

    @Test
    @DisplayName("User-provided ChatMcpTool overrides the auto-config one (@ConditionalOnMissingBean)")
    void userOverrideWins() {
        ChatMcpTool override = new ChatMcpTool(new AgentMcpProperties(), mock(OrchestratorEngine.class));
        contextRunner
                .withPropertyValues("agent.mcp.enabled=true")
                .withBean(ChatMcpTool.class, () -> override)
                .run(ctx -> assertThat(ctx.getBean(ChatMcpTool.class)).isSameAs(override));
    }

    @Configuration
    static class StubOrchestratorEngineConfig {
        @Bean
        OrchestratorEngine orchestratorEngine() {
            return mock(OrchestratorEngine.class);
        }
    }
}
