package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.VectorStorePort;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.memory.adapters.inmemory.InMemoryVectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the auto-configuration contract added in v1.2.9: the {@link RagEnricher}
 * bean is registered whenever a {@link SkillRegistry} is present. When a
 * {@link VectorStorePort} is also wired the enricher becomes "active"; when
 * no vector store is contributed the same bean is registered as a runtime
 * no-op. This sidesteps the {@code @ConditionalOnBean} vs. profile-gated
 * producer race that bit users in embedded mode under v1.2.8.
 */
class RagAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class));

    @Test
    @DisplayName("RagEnricher is created and ACTIVE when both VectorStorePort and SkillRegistry are present")
    void enricherActiveWhenDepsPresent() {
        contextRunner
                .withUserConfiguration(SkillRegistryConfig.class, VectorStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("RagEnricher is created but INACTIVE when no VectorStorePort is in the context")
    void enricherInactiveWhenVectorStoreMissing() {
        contextRunner
                .withUserConfiguration(SkillRegistryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isFalse();
                });
    }

    @Test
    @DisplayName("RagEnricher is NOT created without a SkillRegistry (no skills to enrich)")
    void enricherSkippedWhenSkillRegistryMissing() {
        contextRunner
                .withUserConfiguration(VectorStoreConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RagEnricher.class));
    }

    @Test
    @DisplayName("VectorStorePort contributed by a separate user config is picked up at bean-creation time "
            + "(ObjectProvider lookup, not registration-phase condition)")
    void enricherPicksUpLateRegisteredVectorStore() {
        // Simulates the embedded-mode scenario: a separate (potentially
        // profile-gated) auto-config contributes the VectorStorePort. With
        // the old @ConditionalOnBean wiring this was fragile; the
        // ObjectProvider lookup sidesteps the race entirely.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class))
                .withUserConfiguration(SkillRegistryConfig.class, LateVectorStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isTrue();
                });
    }

    @Configuration
    static class SkillRegistryConfig {
        @Bean
        SkillRegistry skillRegistry() {
            return mock(SkillRegistry.class);
        }
    }

    @Configuration
    static class VectorStoreConfig {
        @Bean
        VectorStorePort vectorStore() {
            return new InMemoryVectorStore();
        }
    }

    @Configuration
    static class LateVectorStoreConfig {
        @Bean
        VectorStorePort lateVectorStore() {
            return new InMemoryVectorStore();
        }
    }
}
