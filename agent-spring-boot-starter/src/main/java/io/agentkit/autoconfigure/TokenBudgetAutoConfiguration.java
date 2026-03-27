package io.agentkit.autoconfigure;

import io.agentkit.core.orchestrator.TokenBudgetManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the token budget manager.
 */
@AutoConfiguration
public class TokenBudgetAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenBudgetManager.class)
    public DefaultTokenBudgetManager defaultTokenBudgetManager() {
        return new DefaultTokenBudgetManager();
    }
}
