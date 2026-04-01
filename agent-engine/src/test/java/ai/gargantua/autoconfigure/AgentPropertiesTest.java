package ai.gargantua.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentProperties")
class AgentPropertiesTest {

    @Nested
    @DisplayName("Api defaults")
    class ApiDefaults {

        @Test
        @DisplayName("has default title")
        void hasDefaultTitle() {
            var props = new AgentProperties();
            assertThat(props.getApi().getTitle()).isEqualTo("AgentKit");
        }

        @Test
        @DisplayName("has default version")
        void hasDefaultVersion() {
            var props = new AgentProperties();
            assertThat(props.getApi().getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("has default agentId")
        void hasDefaultAgentId() {
            var props = new AgentProperties();
            assertThat(props.getApi().getAgentId()).isEqualTo("default-agent");
        }

        @Test
        @DisplayName("has default displayName")
        void hasDefaultDisplayName() {
            var props = new AgentProperties();
            assertThat(props.getApi().getDisplayName()).isEqualTo("AgentKit Agent");
        }
    }

    @Nested
    @DisplayName("Skill defaults")
    class SkillDefaults {

        @Test
        @DisplayName("has default path")
        void hasDefaultPath() {
            assertThat(new AgentProperties().getSkill().getPath()).isEqualTo("skills");
        }

        @Test
        @DisplayName("hot reload is disabled by default")
        void hotReloadDisabledByDefault() {
            assertThat(new AgentProperties().getSkill().isHotReload()).isFalse();
        }

        @Test
        @DisplayName("has default cache TTL")
        void hasDefaultCacheTtl() {
            assertThat(new AgentProperties().getSkill().getCacheTtlMinutes()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("LLM defaults")
    class LlmDefaults {

        @Test
        @DisplayName("primary model defaults to gpt-4o")
        void primaryModelDefaultsToGpt4o() {
            assertThat(new AgentProperties().getLlm().getPrimary().getModel()).isEqualTo("gpt-4o");
        }

        @Test
        @DisplayName("default temperature is 0.7")
        void defaultTemperature() {
            assertThat(new AgentProperties().getLlm().getPrimary().getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("default max tokens is 4096")
        void defaultMaxTokens() {
            assertThat(new AgentProperties().getLlm().getPrimary().getMaxTokens()).isEqualTo(4096);
        }

        @Test
        @DisplayName("primary alias defaults to 'default'")
        void primaryAliasDefault() {
            assertThat(new AgentProperties().getLlm().getPrimaryAlias()).isEqualTo("default");
        }

        @Test
        @DisplayName("models map is empty by default")
        void modelsMapEmpty() {
            assertThat(new AgentProperties().getLlm().getModels()).isEmpty();
        }

        @Test
        @DisplayName("routing rules list is empty by default")
        void routingRulesEmpty() {
            assertThat(new AgentProperties().getLlm().getRoutingRules()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Routing defaults")
    class RoutingDefaults {

        @Test
        @DisplayName("default strategy is semantic")
        void defaultStrategy() {
            assertThat(new AgentProperties().getRouting().getStrategy()).isEqualTo("semantic");
        }

        @Test
        @DisplayName("default fallback skill is 'default'")
        void defaultFallbackSkill() {
            assertThat(new AgentProperties().getRouting().getFallbackSkill()).isEqualTo("default");
        }

        @Test
        @DisplayName("semantic threshold defaults to 0.6")
        void semanticThreshold() {
            assertThat(new AgentProperties().getRouting().getSemantic().getThreshold()).isEqualTo(0.6);
        }
    }

    @Nested
    @DisplayName("Memory defaults")
    class MemoryDefaults {

        @Test
        @DisplayName("working memory defaults to 20 messages")
        void workingMemoryMaxMessages() {
            assertThat(new AgentProperties().getMemory().getWorking().getMaxMessages()).isEqualTo(20);
        }

        @Test
        @DisplayName("working memory TTL defaults to 30 minutes")
        void workingMemoryTtl() {
            assertThat(new AgentProperties().getMemory().getWorking().getTtlMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("episodic memory defaults to 5 summaries")
        void episodicMaxSummaries() {
            assertThat(new AgentProperties().getMemory().getEpisodic().getMaxSummaries()).isEqualTo(5);
        }

        @Test
        @DisplayName("knowledge memory defaults to 10 segments")
        void knowledgeMaxSegments() {
            assertThat(new AgentProperties().getMemory().getKnowledge().getMaxSegments()).isEqualTo(10);
        }

        @Test
        @DisplayName("composer defaults to 3000 max context tokens")
        void composerMaxContextTokens() {
            assertThat(new AgentProperties().getMemory().getComposer().getMaxContextTokens()).isEqualTo(3000);
        }
    }

    @Nested
    @DisplayName("Guardrail defaults")
    class GuardrailDefaults {

        @Test
        @DisplayName("max length enabled by default")
        void maxLengthEnabled() {
            assertThat(new AgentProperties().getGuardrail().getInput().isMaxLengthEnabled()).isTrue();
        }

        @Test
        @DisplayName("max length chars defaults to 10000")
        void maxLengthChars() {
            assertThat(new AgentProperties().getGuardrail().getInput().getMaxLengthChars()).isEqualTo(10000);
        }

        @Test
        @DisplayName("prompt injection enabled by default")
        void promptInjectionEnabled() {
            assertThat(new AgentProperties().getGuardrail().getInput().isPromptInjectionEnabled()).isTrue();
        }

        @Test
        @DisplayName("PII masking disabled by default on input")
        void piiMaskingDisabledInput() {
            assertThat(new AgentProperties().getGuardrail().getInput().isPiiMaskingEnabled()).isFalse();
        }

        @Test
        @DisplayName("schema validation enabled by default on output")
        void schemaValidationEnabled() {
            assertThat(new AgentProperties().getGuardrail().getOutput().isSchemaValidationEnabled()).isTrue();
        }

        @Test
        @DisplayName("disclaimer disabled by default")
        void disclaimerDisabled() {
            assertThat(new AgentProperties().getGuardrail().getOutput().isDisclaimerEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("HITL defaults")
    class HitlDefaults {

        @Test
        @DisplayName("HITL disabled by default")
        void hitlDisabled() {
            assertThat(new AgentProperties().getHitl().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("default TTL is 5 minutes")
        void defaultTtl() {
            assertThat(new AgentProperties().getHitl().getDefaultTtlMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("auto deny on expiry is true by default")
        void autoDenyDefault() {
            assertThat(new AgentProperties().getHitl().isAutoDenyOnExpiry()).isTrue();
        }
    }

    @Nested
    @DisplayName("CostTracking defaults")
    class CostTrackingDefaults {

        @Test
        @DisplayName("cost tracking disabled by default")
        void disabledByDefault() {
            assertThat(new AgentProperties().getCostTracking().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("retention defaults to 90 days")
        void retentionDays() {
            assertThat(new AgentProperties().getCostTracking().getRetentionDays()).isEqualTo(90);
        }

        @Test
        @DisplayName("pricing map is empty by default")
        void pricingEmpty() {
            assertThat(new AgentProperties().getCostTracking().getPricing()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Evals defaults")
    class EvalsDefaults {

        @Test
        @DisplayName("evals disabled by default")
        void disabledByDefault() {
            assertThat(new AgentProperties().getEvals().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("dataset path defaults to 'evals'")
        void datasetPath() {
            assertThat(new AgentProperties().getEvals().getDatasetPath()).isEqualTo("evals");
        }

        @Test
        @DisplayName("fail threshold defaults to 0.7")
        void failThreshold() {
            assertThat(new AgentProperties().getEvals().getFailThreshold()).isEqualTo(0.7);
        }
    }

    @Nested
    @DisplayName("Setters work correctly")
    class Setters {

        @Test
        @DisplayName("can set and get custom LLM model config")
        void customLlmModelConfig() {
            var props = new AgentProperties();
            var config = new AgentProperties.LlmModelConfig();
            config.setProvider("anthropic");
            config.setModel("claude-3-opus");
            config.setTemperature(0.3);
            config.setMaxTokens(8192);
            config.setApiKey("sk-test");
            config.setEndpoint("https://api.anthropic.com");

            props.getLlm().getModels().put("claude", config);

            assertThat(props.getLlm().getModels().get("claude").getProvider()).isEqualTo("anthropic");
            assertThat(props.getLlm().getModels().get("claude").getModel()).isEqualTo("claude-3-opus");
            assertThat(props.getLlm().getModels().get("claude").getTemperature()).isEqualTo(0.3);
            assertThat(props.getLlm().getModels().get("claude").getMaxTokens()).isEqualTo(8192);
        }

        @Test
        @DisplayName("can set routing rules")
        void routingRules() {
            var props = new AgentProperties();
            var rule = new AgentProperties.RoutingRule();
            rule.setName("premium-routing");
            rule.setPriority(5);
            rule.setEnabled(true);
            rule.setTargetModel("gpt-4o");
            rule.setDescription("Route premium users");
            rule.setCondition(Map.of("userTier", "premium"));

            props.getLlm().setRoutingRules(List.of(rule));

            assertThat(props.getLlm().getRoutingRules()).hasSize(1);
            assertThat(props.getLlm().getRoutingRules().get(0).getName()).isEqualTo("premium-routing");
            assertThat(props.getLlm().getRoutingRules().get(0).getCondition()).containsEntry("userTier", "premium");
        }

        @Test
        @DisplayName("can set nested shell remote properties")
        void shellRemoteProperties() {
            var props = new AgentProperties();
            props.getShell().getRemote().setUrl("http://localhost:8080");
            props.getShell().getRemote().setApiKey("my-key");
            props.getShell().getRemote().setTimeoutMs(60000);

            assertThat(props.getShell().getRemote().getUrl()).isEqualTo("http://localhost:8080");
            assertThat(props.getShell().getRemote().getApiKey()).isEqualTo("my-key");
            assertThat(props.getShell().getRemote().getTimeoutMs()).isEqualTo(60000);
        }

        @Test
        @DisplayName("can replace entire nested objects via setters")
        void replaceNestedObjects() {
            var props = new AgentProperties();
            var newApi = new AgentProperties.Api();
            newApi.setTitle("My Custom Agent");
            newApi.setVersion("2.0.0");
            props.setApi(newApi);

            assertThat(props.getApi().getTitle()).isEqualTo("My Custom Agent");
            assertThat(props.getApi().getVersion()).isEqualTo("2.0.0");
        }
    }

    @Nested
    @DisplayName("Observability defaults")
    class ObservabilityDefaults {

        @Test
        @DisplayName("tracing enabled by default")
        void tracingEnabled() {
            assertThat(new AgentProperties().getObservability().isTracingEnabled()).isTrue();
        }

        @Test
        @DisplayName("metrics enabled by default")
        void metricsEnabled() {
            assertThat(new AgentProperties().getObservability().isMetricsEnabled()).isTrue();
        }

        @Test
        @DisplayName("log token usage enabled by default")
        void logTokenUsageEnabled() {
            assertThat(new AgentProperties().getObservability().isLogTokenUsage()).isTrue();
        }
    }

    @Nested
    @DisplayName("DryRun defaults")
    class DryRunDefaults {

        @Test
        @DisplayName("dry run disabled by default")
        void disabledByDefault() {
            assertThat(new AgentProperties().getDryRun().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("allowed profiles is empty by default")
        void allowedProfilesEmpty() {
            assertThat(new AgentProperties().getDryRun().getAllowedProfiles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Audit defaults")
    class AuditDefaults {

        @Test
        @DisplayName("audit enabled by default")
        void enabledByDefault() {
            assertThat(new AgentProperties().getAudit().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("retention defaults to 365 days")
        void retentionDays() {
            assertThat(new AgentProperties().getAudit().getRetentionDays()).isEqualTo(365);
        }
    }

    @Nested
    @DisplayName("MCP defaults")
    class McpDefaults {

        @Test
        @DisplayName("MCP disabled by default")
        void disabledByDefault() {
            assertThat(new AgentProperties().getMcp().isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Skillsjars defaults")
    class SkillsjarsDefaults {

        @Test
        @DisplayName("skillsjars disabled by default")
        void disabledByDefault() {
            assertThat(new AgentProperties().getSkillsjars().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("unresolved tools behavior defaults to 'warn'")
        void unresolvedToolsBehavior() {
            assertThat(new AgentProperties().getSkillsjars().getUnresolvedToolsBehavior()).isEqualTo("warn");
        }

        @Test
        @DisplayName("tool mappings empty by default")
        void toolMappingsEmpty() {
            assertThat(new AgentProperties().getSkillsjars().getToolMappings()).isEmpty();
        }
    }
}
