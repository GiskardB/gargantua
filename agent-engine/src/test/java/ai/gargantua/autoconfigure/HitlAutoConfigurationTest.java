package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.hitl.RedisApprovalStore;
import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryApprovalStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the v1.2.20 wiring contract for the HITL subsystem.
 *
 * <p>Until v1.2.19 the {@link RedisApprovalStore} was declared
 * {@code @Component} inside {@code ai.gargantua.adapters.hitl} — outside
 * any user app's component scan — so every {@code @RequiresApproval}-gated
 * tool in production logged {@code "no ApprovalStore configured"} and
 * silently lost the pending request. v1.2.20 moves the registration to
 * {@link HitlAutoConfiguration} as an explicit {@code @Bean} factory.</p>
 *
 * <p>Same registration-bug family as v1.2.10 (RAG) and v1.2.13 (MCP).</p>
 */
class HitlAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HitlAutoConfiguration.class))
            .withPropertyValues("agent.hitl.enabled=true");

    @Test
    @DisplayName("RedisApprovalStore is wired when a StringRedisTemplate is available")
    void redisApprovalStoreWiredWhenRedisPresent() {
        contextRunner
                .withUserConfiguration(MockRedisTemplateConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ApprovalStore.class);
                    assertThat(ctx.getBean(ApprovalStore.class)).isInstanceOf(RedisApprovalStore.class);
                });
    }

    @Test
    @DisplayName("Without a StringRedisTemplate, no Redis-backed ApprovalStore is contributed (leaves room for the embedded in-memory bean)")
    void noRedisApprovalStoreWithoutTemplate() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(RedisApprovalStore.class);
            // ApprovalStore may exist via a user override; not contributed by HitlAutoConfiguration here.
            assertThat(ctx).doesNotHaveBean(ApprovalStore.class);
        });
    }

    @Test
    @DisplayName("User-supplied @Bean ApprovalStore always wins over the default Redis-backed one")
    void userBeanWinsOverDefault() {
        contextRunner
                .withUserConfiguration(MockRedisTemplateConfig.class, UserInMemoryStoreConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ApprovalStore.class);
                    assertThat(ctx.getBean(ApprovalStore.class))
                            .isInstanceOf(InMemoryApprovalStore.class)
                            .as("user-declared ApprovalStore takes precedence");
                });
    }

    @Test
    @DisplayName("HitlCoordinator is registered when the auto-config fires")
    void coordinatorRegistered() {
        contextRunner
                .withUserConfiguration(MockRedisTemplateConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(HitlCoordinator.class));
    }

    @Test
    @DisplayName("Whole auto-config is skipped when agent.hitl.enabled=false")
    void skippedWhenDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HitlAutoConfiguration.class))
                .withPropertyValues("agent.hitl.enabled=false")
                .withUserConfiguration(MockRedisTemplateConfig.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ApprovalStore.class);
                    assertThat(ctx).doesNotHaveBean(HitlCoordinator.class);
                });
    }

    @Configuration
    static class MockRedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            // A pure mock is enough — none of the wiring tests dial Redis.
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration
    static class UserInMemoryStoreConfig {
        @Bean
        ApprovalStore approvalStore() {
            return new InMemoryApprovalStore();
        }
    }
}
