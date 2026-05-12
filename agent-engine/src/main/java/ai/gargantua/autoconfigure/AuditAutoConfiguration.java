package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.adapters.audit.MongoAuditStore;
import ai.gargantua.adapters.web.AuditAdminController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auto-configuration for the audit trail subsystem.
 * Registers the {@link MongoAuditStore} when MongoDB is available,
 * the {@link AuditService} for recording events, and the
 * {@link AuditAdminController} for querying the trail via REST.
 *
 * <p>Activated when {@code agent.audit.enabled=true} (the default).</p>
 *
 * <p>In embedded mode, {@link EmbeddedProfileAutoConfiguration} provides an
 * {@link ai.gargantua.memory.adapters.inmemory.InMemoryAuditStore} instead.</p>
 *
 * <p><b>v1.2.12+ wiring.</b> {@link AuditService} and
 * {@link AuditAdminController} now resolve their {@link AuditStore} via
 * {@link ObjectProvider}, mirroring the v1.2.10 RAG fix: their previous
 * {@code @ConditionalOnBean(AuditStore.class)} guards were evaluated during
 * the registration phase and missed embedded-mode {@code AuditStore} beans
 * contributed by the profile-gated {@link EmbeddedProfileAutoConfiguration}.
 * The {@link AuditService} is therefore always registered when
 * {@code agent.audit.enabled=true}; it silently no-ops at runtime when no
 * store is wired ({@link AuditService#isActive()} reflects this).</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration")
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(AuditStore.class)
    public MongoAuditStore mongoAuditStore(MongoTemplate mongoTemplate) {
        return new MongoAuditStore(mongoTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AuditService.class)
    public AuditService auditService(ObjectProvider<AuditStore> auditStoreProvider,
                                     AgentProperties properties) {
        AuditStore store = auditStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("AuditService registered as a runtime no-op — no AuditStore bean is wired.");
        } else {
            log.info("AuditService activated — recording to {}", store.getClass().getSimpleName());
        }
        return new AuditService(store, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuditAdminController.class)
    public AuditAdminController auditAdminController(ObjectProvider<AuditStore> auditStoreProvider) {
        AuditStore store = auditStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("AuditAdminController registered with a null store — REST queries will return empty results.");
        }
        return new AuditAdminController(store);
    }
}
