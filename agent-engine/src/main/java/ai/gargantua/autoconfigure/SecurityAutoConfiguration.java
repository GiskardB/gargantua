package ai.gargantua.autoconfigure;

import ai.gargantua.autoconfigure.guardrails.RbacGuardrail;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the RBAC and multi-tenancy security layer.
 * Registers:
 * <ul>
 *   <li>{@link SecurityContextFilter} — servlet filter that extracts gateway headers into a
 *       {@link ai.gargantua.core.security.SecurityContext} request attribute.</li>
 *   <li>{@link RbacGuardrail} — input guardrail (order 5) that enforces skill-level role checks.</li>
 * </ul>
 */
@AutoConfiguration
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityContextFilter.class)
    public FilterRegistrationBean<SecurityContextFilter> securityContextFilter() {
        var registration = new FilterRegistrationBean<>(new SecurityContextFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(RbacGuardrail.class)
    public RbacGuardrail rbacGuardrail(AgentProperties agentProperties) {
        return new RbacGuardrail(agentProperties);
    }
}
