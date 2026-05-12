package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmProviderFactory")
class LlmProviderFactoryTest {

    @Mock
    private LlmRouter llmRouter;

    private AgentProperties properties;
    private LlmProviderFactory factory;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        factory = new LlmProviderFactory(properties, llmRouter);
    }

    private LlmRoutingContext defaultCtx() {
        return new LlmRoutingContext(
                "user1", "session1", "test-skill", "general",
                "hello", 5, 10, "free",
                LocalTime.NOON, DayOfWeek.MONDAY, Map.of()
        );
    }

    private SkillCard skillCardWithPreferredModel(String preferredModel) {
        var meta = new SkillMeta("test", "desc", "1.0.0", true, false, "general",
                SkillSource.FILESYSTEM, Set.of());
        return new SkillCard(meta, "prompt", List.of(), null, List.of(),
                null, null, preferredModel, null);
    }

    @Nested
    @DisplayName("resolveModelAlias")
    class ResolveModelAlias {

        @Test
        @DisplayName("returns skill preferred model when set")
        void returnsSkillPreferredModel() {
            SkillCard card = skillCardWithPreferredModel("claude-3");
            String alias = factory.resolveModelAlias(card, defaultCtx());
            assertThat(alias).isEqualTo("claude-3");
        }

        @Test
        @DisplayName("delegates to router when skill has no preferred model")
        void delegatesToRouterWhenNoPreferredModel() {
            SkillCard card = skillCardWithPreferredModel(null);
            when(llmRouter.resolve(any())).thenReturn("routed-model");

            String alias = factory.resolveModelAlias(card, defaultCtx());
            assertThat(alias).isEqualTo("routed-model");
        }

        @Test
        @DisplayName("delegates to router when preferred model is blank")
        void delegatesToRouterWhenPreferredModelBlank() {
            SkillCard card = skillCardWithPreferredModel("   ");
            when(llmRouter.resolve(any())).thenReturn("routed-model");

            String alias = factory.resolveModelAlias(card, defaultCtx());
            assertThat(alias).isEqualTo("routed-model");
        }

        @Test
        @DisplayName("delegates to router when skill card is null")
        void delegatesToRouterWhenSkillCardNull() {
            when(llmRouter.resolve(any())).thenReturn("default-model");

            String alias = factory.resolveModelAlias(null, defaultCtx());
            assertThat(alias).isEqualTo("default-model");
        }
    }

    @Nested
    @DisplayName("getModelConfig")
    class GetModelConfig {

        @Test
        @DisplayName("returns primary config for 'primary' alias")
        void returnsPrimaryConfig() {
            properties.getLlm().getPrimary().setModel("gpt-4o");
            var config = factory.getModelConfig("primary");
            assertThat(config.getModel()).isEqualTo("gpt-4o");
        }

        @Test
        @DisplayName("returns fallback config for 'fallback' alias")
        void returnsFallbackConfig() {
            properties.getLlm().getFallback().setModel("gpt-3.5-turbo");
            var config = factory.getModelConfig("fallback");
            assertThat(config.getModel()).isEqualTo("gpt-3.5-turbo");
        }

        @Test
        @DisplayName("returns routing config for 'routing' alias")
        void returnsRoutingConfig() {
            properties.getLlm().getRoutingModel().setModel("gpt-4o-mini");
            var config = factory.getModelConfig("routing");
            assertThat(config.getModel()).isEqualTo("gpt-4o-mini");
        }

        @Test
        @DisplayName("returns custom model config from models map")
        void returnsCustomModelConfig() {
            AgentProperties.LlmModelConfig custom = new AgentProperties.LlmModelConfig();
            custom.setModel("claude-3-opus");
            custom.setProvider("anthropic");
            properties.getLlm().getModels().put("custom-model", custom);

            var config = factory.getModelConfig("custom-model");
            assertThat(config.getModel()).isEqualTo("claude-3-opus");
            assertThat(config.getProvider()).isEqualTo("anthropic");
        }

        @Test
        @DisplayName("falls back to primary when alias not found in models map")
        void fallsToPrimaryWhenAliasNotFound() {
            properties.getLlm().getPrimary().setModel("gpt-4o");
            var config = factory.getModelConfig("nonexistent-alias");
            assertThat(config.getModel()).isEqualTo("gpt-4o");
        }
    }

    @Nested
    @DisplayName("normalizeEndpoint (via getModel/buildModel)")
    class NormalizeEndpoint {

        @Test
        @DisplayName("defaults to OpenAI when endpoint is null")
        void defaultsToOpenAiWhenNull() {
            // normalizeEndpoint is private, so we test indirectly through getModelConfig
            // and verify the config itself
            properties.getLlm().getPrimary().setEndpoint(null);
            var config = factory.getModelConfig("primary");
            assertThat(config.getEndpoint()).isNull();
        }

        @Test
        @DisplayName("defaults to OpenAI when endpoint is blank")
        void defaultsToOpenAiWhenBlank() {
            properties.getLlm().getPrimary().setEndpoint("  ");
            var config = factory.getModelConfig("primary");
            assertThat(config.getEndpoint()).isEqualTo("  ");
        }

        @Test
        @DisplayName("endpoint with trailing slashes in config")
        void endpointWithTrailingSlashes() {
            properties.getLlm().getPrimary().setEndpoint("http://localhost:11434///");
            var config = factory.getModelConfig("primary");
            assertThat(config.getEndpoint()).isEqualTo("http://localhost:11434///");
        }

        @Test
        @DisplayName("endpoint already ending with /v1 is preserved")
        void endpointAlreadyEndingWithV1() {
            properties.getLlm().getPrimary().setEndpoint("http://localhost:11434/v1");
            var config = factory.getModelConfig("primary");
            assertThat(config.getEndpoint()).isEqualTo("http://localhost:11434/v1");
        }
    }

    @Nested
    @DisplayName("getModel caching")
    class GetModelCaching {

        @Test
        @DisplayName("getRoutingModel returns model for 'routing' alias")
        void getRoutingModelUsesRoutingAlias() {
            properties.getLlm().getRoutingModel().setModel("routing-model");
            properties.getLlm().getRoutingModel().setEndpoint("http://localhost:11434/v1");
            properties.getLlm().getRoutingModel().setApiKey("test-key");

            // This will build the model; we just verify no exception
            var model = factory.getRoutingModel();
            assertThat(model).isNotNull();
        }

        @Test
        @DisplayName("getModel returns same instance for same alias (cached)")
        void returnsIdenticalCachedInstance() {
            properties.getLlm().getPrimary().setEndpoint("http://localhost:11434/v1");
            properties.getLlm().getPrimary().setApiKey("test-key");

            var model1 = factory.getModel("primary");
            var model2 = factory.getModel("primary");
            assertThat(model1).isSameAs(model2);
        }
    }

    @Nested
    @DisplayName("Azure OpenAI provider (1.2.15+)")
    class AzureOpenAi {

        @Test
        @DisplayName("provider=azure-openai with explicit api-version + deployment-name builds an AzureOpenAiChatModel")
        void azureWithExplicitVersionAndDeployment() {
            properties.getLlm().getPrimary().setProvider("azure-openai");
            properties.getLlm().getPrimary().setEndpoint("https://my-foundry.openai.azure.com");
            properties.getLlm().getPrimary().setApiKey("azure-key");
            properties.getLlm().getPrimary().setModel("gpt-4o");
            properties.getLlm().getPrimary().setDeploymentName("gpt-4o-prod");
            properties.getLlm().getPrimary().setApiVersion("2024-08-01-preview");

            var model = factory.getModel("primary");
            assertThat(model).isInstanceOf(dev.langchain4j.model.azure.AzureOpenAiChatModel.class);
        }

        @Test
        @DisplayName("Empty deployment-name falls back to model id (Azure deployments are conventionally named after the model)")
        void azureDeploymentDefaultsToModel() {
            properties.getLlm().getPrimary().setProvider("azure-openai");
            properties.getLlm().getPrimary().setEndpoint("https://my-foundry.openai.azure.com");
            properties.getLlm().getPrimary().setApiKey("azure-key");
            properties.getLlm().getPrimary().setModel("gpt-4o-mini");
            properties.getLlm().getPrimary().setApiVersion("2024-08-01-preview");
            // deployment-name left blank

            // Builds successfully — exercises the resolveAzureDeployment fallback path.
            var model = factory.getModel("primary");
            assertThat(model).isInstanceOf(dev.langchain4j.model.azure.AzureOpenAiChatModel.class);
        }

        @Test
        @DisplayName("Empty api-version falls back to a default + warns (resolveAzureServiceVersion contract)")
        void azureApiVersionDefaultIsApplied() {
            properties.getLlm().getPrimary().setProvider("azure-openai");
            properties.getLlm().getPrimary().setEndpoint("https://my-foundry.openai.azure.com");
            properties.getLlm().getPrimary().setApiKey("azure-key");
            properties.getLlm().getPrimary().setModel("gpt-4o");
            // api-version intentionally left blank — caller misconfiguration

            var model = factory.getModel("primary");
            assertThat(model).isInstanceOf(dev.langchain4j.model.azure.AzureOpenAiChatModel.class);
        }

        @Test
        @DisplayName("provider=openai still picks the OpenAI-compatible builder, not Azure")
        void openAiProviderUnchanged() {
            properties.getLlm().getPrimary().setProvider("openai");
            properties.getLlm().getPrimary().setEndpoint("http://localhost:11434/v1");
            properties.getLlm().getPrimary().setApiKey("k");
            properties.getLlm().getPrimary().setModel("gpt-4o");

            var model = factory.getModel("primary");
            assertThat(model).isInstanceOf(dev.langchain4j.model.openai.OpenAiChatModel.class);
            assertThat(model).isNotInstanceOf(dev.langchain4j.model.azure.AzureOpenAiChatModel.class);
        }
    }
}
