package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.web.AuditAdminController;
import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryAuditStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the v1.2.12 wiring contract added to {@link AuditAutoConfiguration}:
 * {@link AuditService} (and {@link AuditAdminController}) are now registered
 * unconditionally whenever {@code agent.audit.enabled=true}; the
 * {@link AuditStore} dependency is resolved via {@code ObjectProvider} at
 * bean-creation time, mirroring the v1.2.10 RAG fix. This sidesteps the
 * {@code @ConditionalOnBean(AuditStore.class)} race that previously hid
 * the audit service in embedded mode.
 */
class AuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    @Test
    @DisplayName("AuditService is ACTIVE when an AuditStore is in the context")
    void serviceActiveWhenStorePresent() {
        contextRunner
                .withUserConfiguration(InMemoryStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AuditService.class);
                    assertThat(ctx.getBean(AuditService.class).isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("AuditService is registered but INACTIVE when no AuditStore is present")
    void serviceInactiveWhenStoreMissing() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AuditService.class);
            assertThat(ctx.getBean(AuditService.class).isActive()).isFalse();
        });
    }

    @Test
    @DisplayName("AuditService is NOT registered when agent.audit.enabled=false")
    void serviceSkippedWhenAuditDisabled() {
        contextRunner
                .withPropertyValues("agent.audit.enabled=false")
                .withUserConfiguration(InMemoryStoreConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditService.class));
    }

    @Test
    @DisplayName("Late-registered AuditStore (e.g. embedded-profile bean) is still picked up by the ObjectProvider")
    void serviceFindsLateRegisteredStore() {
        // Simulates the embedded-mode scenario where the AuditStore comes
        // from a profile-gated config processed after AuditAutoConfiguration.
        // Registration-phase @ConditionalOnBean would miss it; ObjectProvider
        // lookup at bean-creation time picks it up.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
                .withUserConfiguration(LateInMemoryStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AuditService.class);
                    assertThat(ctx.getBean(AuditService.class).isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("AuditAdminController is always registered when audit is enabled")
    void adminControllerAlwaysRegisteredWhenEnabled() {
        contextRunner
                .withUserConfiguration(InMemoryStoreConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(AuditAdminController.class));
        contextRunner
                .run(ctx -> assertThat(ctx).hasSingleBean(AuditAdminController.class));
    }

    @Configuration
    static class InMemoryStoreConfig {
        @Bean
        AuditStore auditStore() {
            return new InMemoryAuditStore();
        }
    }

    @Configuration
    static class LateInMemoryStoreConfig {
        @Bean
        AuditStore lateAuditStore() {
            return new InMemoryAuditStore();
        }
    }
}
