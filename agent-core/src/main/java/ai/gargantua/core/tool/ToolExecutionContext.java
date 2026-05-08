package ai.gargantua.core.tool;

import ai.gargantua.core.security.SecurityContext;

/**
 * Lightweight per-call context handed to {@code ToolRegistry.executeTool} so that
 * cross-cutting concerns (RBAC, scoped caching) can be evaluated without sharing
 * a thread-local. All fields are nullable; the registry falls back to safe defaults
 * (anonymous user, no caching) when omitted.
 *
 * @param securityContext caller identity used to gate {@link ai.gargantua.core.security.RequiresRole}
 * @param sessionId       conversation identifier used by {@link CacheScope#SESSION} keys
 */
public record ToolExecutionContext(
        SecurityContext securityContext,
        String sessionId
) {
    public static ToolExecutionContext empty() {
        return new ToolExecutionContext(null, null);
    }

    public static ToolExecutionContext of(SecurityContext securityContext, String sessionId) {
        return new ToolExecutionContext(securityContext, sessionId);
    }
}
