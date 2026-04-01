package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Extracts security context from trusted gateway headers:
 * <ul>
 *   <li>{@code X-User-Id} &rarr; userId</li>
 *   <li>{@code X-Tenant-Id} &rarr; tenantId</li>
 *   <li>{@code X-User-Roles} &rarr; comma-separated roles</li>
 * </ul>
 *
 * <p>Stores the resulting {@link SecurityContext} as request attribute
 * {@value #SECURITY_CONTEXT_ATTR} so controllers can retrieve it without
 * depending on a thread-local or Spring Security filter chain.</p>
 */
public class SecurityContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityContextFilter.class);

    public static final String SECURITY_CONTEXT_ATTR = "gargantua.securityContext";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) userId = "anonymous";

        var tenantId = request.getHeader("X-Tenant-Id");

        var rolesHeader = request.getHeader("X-User-Roles");
        Set<String> roles = (rolesHeader != null && !rolesHeader.isBlank())
            ? Set.of(rolesHeader.split(","))
            : Set.of("user");

        var ctx = new SecurityContext(userId, tenantId, roles);
        request.setAttribute(SECURITY_CONTEXT_ATTR, ctx);
        log.debug("[Security] {} {} — userId={}, tenantId={}, roles={}",
                request.getMethod(), request.getRequestURI(), userId, tenantId, roles);
        chain.doFilter(request, response);
    }
}
