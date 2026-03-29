package ai.gargantua.autoconfigure;

import ai.gargantua.core.audit.AuditStore;
import ai.gargantua.adapters.audit.MongoAuditStore;
import ai.gargantua.adapters.web.AuditAdminController;
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
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "agent.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    @ConditionalOnMissingBean(AuditStore.class)
    public MongoAuditStore mongoAuditStore(MongoTemplate mongoTemplate) {
        return new MongoAuditStore(mongoTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AuditService.class)
    @ConditionalOnBean(AuditStore.class)
    public AuditService auditService(AuditStore auditStore, AgentProperties properties) {
        return new AuditService(auditStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AuditAdminController.class)
    @ConditionalOnBean(AuditStore.class)
    public AuditAdminController auditAdminController(AuditStore auditStore) {
        return new AuditAdminController(auditStore);
    }
}
