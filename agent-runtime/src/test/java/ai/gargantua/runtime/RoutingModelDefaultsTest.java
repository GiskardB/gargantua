package ai.gargantua.runtime;

import ai.gargantua.autoconfigure.AgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the real {@code application.yml} shipped with the runtime image — not a
 * hand-copied snippet — so a typo in the nested placeholder chain
 * ({@code ${LLM_ROUTING_PROVIDER:${LLM_PRIMARY_PROVIDER:openai}}}) fails here instead
 * of surfacing as a silent Ollama connection error at runtime.
 */
@DisplayName("routing-model defaults (application.yml)")
class RoutingModelDefaultsTest {

    private static AgentProperties bindWithEnv(Map<String, String> env) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", new HashMap<>(env)));
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment).bind("agent", AgentProperties.class).orElseThrow();
    }

    @Test
    @DisplayName("rides on LLM_PRIMARY_* when LLM_ROUTING_* is unset")
    void routingFallsBackToPrimary() throws IOException {
        AgentProperties properties = bindWithEnv(Map.of(
                "LLM_PRIMARY_PROVIDER", "azure-openai",
                "LLM_PRIMARY_MODEL", "gpt-5.1",
                "LLM_PRIMARY_MAX_COMPLETION_TOKENS", "4096"
        ));

        var routing = properties.getLlm().getRoutingModel();
        assertThat(routing.getProvider()).isEqualTo("azure-openai");
        assertThat(routing.getModel()).isEqualTo("gpt-5.1");
        assertThat(routing.getMaxCompletionTokens()).isEqualTo(4096);
    }

    @Test
    @DisplayName("LLM_ROUTING_* still wins when set explicitly")
    void explicitRoutingOverrideWins() throws IOException {
        AgentProperties properties = bindWithEnv(Map.of(
                "LLM_PRIMARY_PROVIDER", "azure-openai",
                "LLM_ROUTING_PROVIDER", "ollama",
                "LLM_ROUTING_MODEL", "phi4-mini"
        ));

        var routing = properties.getLlm().getRoutingModel();
        assertThat(routing.getProvider()).isEqualTo("ollama");
        assertThat(routing.getModel()).isEqualTo("phi4-mini");
    }
}
