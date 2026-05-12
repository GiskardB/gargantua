package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.web.ApprovalController;
import ai.gargantua.adapters.web.CapabilitiesController;
import ai.gargantua.adapters.web.ToolCacheAdminController;
import ai.gargantua.core.hitl.ApprovalStore;
import ai.gargantua.memory.adapters.inmemory.InMemoryApprovalStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the v1.2.14 cleanup of the cross-config {@code @ConditionalOnBean}
 * usage in {@link WebAutoConfiguration}. The three controllers
 * ({@link CapabilitiesController}, {@link ApprovalController},
 * {@link ToolCacheAdminController}) now resolve their dependency via
 * {@link ObjectProvider} at bean-creation time and {@code return null}
 * when the dependency is missing — Spring MVC's controller scan filters
 * out NullBean entries so unmapped endpoints are not exposed.
 *
 * <p>Tested at the factory-method level (not via {@code ApplicationContextRunner})
 * because the rest of {@code WebAutoConfiguration} pulls in heavy dependencies
 * — {@code LlmProviderFactory}, {@code GuardrailPipeline}, etc. — that would
 * dominate the test setup. The three factory methods under test are
 * independent of those dependencies, so direct invocation captures the
 * contract precisely.</p>
 *
 * <p>The remaining {@code @ConditionalOnBean(MongoTemplate.class)} usages
 * for {@link ai.gargantua.adapters.web.ChatHistoryController},
 * {@link ai.gargantua.adapters.web.ChatExportController}, and
 * {@link ai.gargantua.adapters.web.CostAdminController} are intentionally
 * preserved — they are gated against Spring Data Mongo's {@code MongoTemplate},
 * which {@code WebAutoConfiguration.afterName} already orders before us.</p>
 */
class WebAutoConfigurationTest {

    private final WebAutoConfiguration config = new WebAutoConfiguration();

    /** Empty ObjectProvider that always reports no candidates. */
    private static <T> ObjectProvider<T> emptyProvider() {
        return new StubObjectProvider<>(Stream.empty());
    }

    /** ObjectProvider that resolves to exactly one candidate (mirrors the late-registered scenario). */
    private static <T> ObjectProvider<T> providerOf(T candidate) {
        return new StubObjectProvider<>(Stream.of(candidate));
    }

    // ─── CapabilitiesController ───────────────────────────────────────

    @Test
    @DisplayName("capabilitiesController returns an instance when AgentCardService is available")
    void capabilitiesControllerActiveWhenServicePresent() {
        AgentCardService svc = new AgentCardService(new AgentProperties(), null);
        CapabilitiesController ctrl = config.capabilitiesController(providerOf(svc), null);
        assertNotNull(ctrl, "Controller must be built when the service is wired");
    }

    @Test
    @DisplayName("capabilitiesController returns null when AgentCardService is missing (controller scan filters it)")
    void capabilitiesControllerSkippedWhenServiceMissing() {
        assertNull(config.capabilitiesController(emptyProvider(), null));
    }

    // ─── ApprovalController ───────────────────────────────────────────

    @Test
    @DisplayName("approvalController returns an instance when ApprovalStore is available")
    void approvalControllerActiveWhenStorePresent() {
        ApprovalController ctrl = config.approvalController(providerOf(new InMemoryApprovalStore()));
        assertNotNull(ctrl);
    }

    @Test
    @DisplayName("approvalController returns null when ApprovalStore is missing")
    void approvalControllerSkippedWhenStoreMissing() {
        ApprovalController ctrl = config.approvalController(emptyProvider());
        assertNull(ctrl, "Controller must be skipped so Spring MVC doesn't map dead endpoints");
    }

    @Test
    @DisplayName("approvalController picks up an ApprovalStore from a late-registered config "
            + "(ObjectProvider lookup defers until bean-creation time)")
    void approvalControllerFindsLateRegisteredStore() {
        ApprovalStore late = new InMemoryApprovalStore();
        ApprovalController ctrl = config.approvalController(providerOf(late));
        assertNotNull(ctrl);
    }

    // ─── ToolCacheAdminController ─────────────────────────────────────

    @Test
    @DisplayName("toolCacheAdminController returns an instance when ToolResultCache is available")
    void toolCacheAdminControllerActiveWhenCachePresent() {
        ToolCacheAdminController ctrl = config.toolCacheAdminController(providerOf(new ToolResultCache()));
        assertNotNull(ctrl);
    }

    @Test
    @DisplayName("toolCacheAdminController returns null when ToolResultCache is missing")
    void toolCacheAdminControllerSkippedWhenCacheMissing() {
        ToolCacheAdminController ctrl = config.toolCacheAdminController(emptyProvider());
        assertNull(ctrl);
    }

    // ─── Minimal ObjectProvider stub ──────────────────────────────────

    /**
     * Hand-rolled {@link ObjectProvider} that supports the {@code getIfAvailable()}
     * + iteration paths used by the {@link WebAutoConfiguration} factories. Avoids
     * pulling in Mockito for what is fundamentally a two-method contract.
     */
    private record StubObjectProvider<T>(java.util.List<T> candidates) implements ObjectProvider<T> {

        StubObjectProvider(Stream<T> candidates) {
            this(candidates.toList());
        }

        @Override public T getObject() {
            if (candidates.isEmpty()) {
                throw new org.springframework.beans.factory.NoSuchBeanDefinitionException("none");
            }
            if (candidates.size() > 1) {
                throw new org.springframework.beans.factory.NoUniqueBeanDefinitionException(Object.class, candidates.size(), "multiple");
            }
            return candidates.get(0);
        }

        @Override public T getObject(Object... args) { return getObject(); }
        @Override public T getIfAvailable() { return candidates.isEmpty() ? null : candidates.get(0); }
        @Override public T getIfUnique() { return candidates.size() == 1 ? candidates.get(0) : null; }
        @Override public java.util.Iterator<T> iterator() { return candidates.iterator(); }
        @Override public Stream<T> stream() { return candidates.stream(); }
        @Override public Stream<T> orderedStream() { return candidates.stream(); }
    }
}
