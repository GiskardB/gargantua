package ai.gargantua.core.orchestrator;

import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable request sent to the {@link OrchestratorEngine}. Use the {@link #builder()}
 * for a fluent construction API.
 *
 * @param message           the user's natural-language input
 * @param userId            caller identity, used for memory and rate limiting
 * @param sessionId         conversation session id, used for working memory
 * @param forceSkill        if non-null, bypasses routing and activates this skill directly
 * @param dryRunContext     when active, tool calls are stubbed and memory is not persisted
 * @param contextAttributes arbitrary key-value pairs forwarded to enrichers and guardrails
 * @param securityContext   RBAC and multi-tenancy context extracted from gateway headers
 *
 * @see AgentResponse
 * @see OrchestratorEngine
 */
public record AgentRequest(
        String message,
        String userId,
        String sessionId,
        String forceSkill,
        DryRunContext dryRunContext,
        Map<String, String> contextAttributes,
        SecurityContext securityContext
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String message;
        private String userId;
        private String sessionId;
        private String forceSkill;
        private DryRunContext dryRunContext;
        private Map<String, String> contextAttributes = new HashMap<>();
        private SecurityContext securityContext;

        private Builder() {
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder forceSkill(String forceSkill) {
            this.forceSkill = forceSkill;
            return this;
        }

        public Builder dryRunContext(DryRunContext dryRunContext) {
            this.dryRunContext = dryRunContext;
            return this;
        }

        public Builder contextAttributes(Map<String, String> contextAttributes) {
            this.contextAttributes = contextAttributes;
            return this;
        }

        public Builder contextAttribute(String key, String value) {
            this.contextAttributes.put(key, value);
            return this;
        }

        public Builder securityContext(SecurityContext securityContext) {
            this.securityContext = securityContext;
            return this;
        }

        public AgentRequest build() {
            return new AgentRequest(message, userId, sessionId, forceSkill,
                    dryRunContext != null ? dryRunContext : DryRunContext.inactive(),
                    contextAttributes,
                    securityContext != null ? securityContext : SecurityContext.anonymous("anonymous"));
        }
    }
}
