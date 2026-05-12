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
 * bean is registered <em>unconditionally</em>. When both a {@link VectorStorePort}
 * and a {@link SkillRegistry} are present the enricher is "active"; when
 * either dependency is missing the same bean is wired as a runtime no-op.
 * This sidesteps the {@code @ConditionalOnBean} vs. profile-gated / late-
 * registered producer race that bit users in embedded mode through v1.2.8.
 */
class RagAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class));

    @Test
    @DisplayName("RagEnricher is ACTIVE when both VectorStorePort and SkillRegistry are present")
    void enricherActiveWhenDepsPresent() {
        contextRunner
                .withUserConfiguration(SkillRegistryConfig.class, VectorStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("RagEnricher is registered but INACTIVE when no VectorStorePort is in the context")
    void enricherInactiveWhenVectorStoreMissing() {
        contextRunner
                .withUserConfiguration(SkillRegistryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isFalse();
                });
    }

    @Test
    @DisplayName("RagEnricher is registered but INACTIVE when no SkillRegistry is in the context")
    void enricherInactiveWhenSkillRegistryMissing() {
        contextRunner
                .withUserConfiguration(VectorStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RagEnricher.class);
                    assertThat(ctx.getBean(RagEnricher.class).isActive()).isFalse();
                });
    }

    @Test
    @DisplayName("Late-registered VectorStorePort and SkillRegistry are still picked up "
            + "(ObjectProvider lookup happens at bean-creation time, not at registration time)")
    void enricherPicksUpLateRegisteredDeps() {
        // Simulates the embedded-mode scenario where profile-gated or
        // later-loaded auto-configurations contribute the dependencies.
        // Both lookups must happen lazily for this to work regardless of
        // configuration class processing order.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RagAutoConfiguration.class))
                .withUserConfiguration(LateSkillRegistryConfig.class, LateVectorStoreConfig.class)
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
    static class LateSkillRegistryConfig {
        @Bean
        SkillRegistry lateSkillRegistry() {
            return mock(SkillRegistry.class);
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
