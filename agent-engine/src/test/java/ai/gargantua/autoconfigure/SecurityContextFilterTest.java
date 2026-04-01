package ai.gargantua.autoconfigure;

import ai.gargantua.core.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SecurityContextFilter")
class SecurityContextFilterTest {

    private SecurityContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SecurityContextFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    private SecurityContext extractSecurityContext() {
        return (SecurityContext) request.getAttribute(SecurityContextFilter.SECURITY_CONTEXT_ATTR);
    }

    // --- Header extraction ---

    @Test
    @DisplayName("extracts userId from X-User-Id header")
    void extractsUserId() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user-42");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.userId()).isEqualTo("user-42");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("extracts tenantId from X-Tenant-Id header")
    void extractsTenantId() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-Tenant-Id", "tenant-abc");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.tenantId()).isEqualTo("tenant-abc");
    }

    @Test
    @DisplayName("extracts roles from X-User-Roles header (comma-separated)")
    void extractsRoles() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-User-Roles", "admin,editor,viewer");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.roles()).containsExactlyInAnyOrder("admin", "editor", "viewer");
    }

    // --- Default values ---

    @Test
    @DisplayName("defaults userId to 'anonymous' when X-User-Id header is missing")
    void defaultsUserIdWhenMissing() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.userId()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("defaults userId to 'anonymous' when X-User-Id header is blank")
    void defaultsUserIdWhenBlank() throws ServletException, IOException {
        request.addHeader("X-User-Id", "   ");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.userId()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("defaults tenantId to null when X-Tenant-Id header is missing")
    void defaultsTenantIdToNull() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.tenantId()).isNull();
    }

    @Test
    @DisplayName("defaults roles to ['user'] when X-User-Roles header is missing")
    void defaultsRolesWhenMissing() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.roles()).containsExactly("user");
    }

    @Test
    @DisplayName("defaults roles to ['user'] when X-User-Roles header is blank")
    void defaultsRolesWhenBlank() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-User-Roles", "   ");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.roles()).containsExactly("user");
    }

    // --- Request attribute ---

    @Test
    @DisplayName("sets SecurityContext as request attribute with correct key")
    void setsRequestAttribute() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");

        filter.doFilter(request, response, chain);

        Object attr = request.getAttribute("gargantua.securityContext");
        assertThat(attr).isNotNull().isInstanceOf(SecurityContext.class);
    }

    // --- Filter chain continues ---

    @Test
    @DisplayName("always continues the filter chain")
    void continuesFilterChain() throws ServletException, IOException {
        filter.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(request, response);
    }

    // --- All headers missing (full anonymous scenario) ---

    @Test
    @DisplayName("handles fully anonymous request (no headers at all)")
    void handlesFullyAnonymousRequest() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.userId()).isEqualTo("anonymous");
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.roles()).containsExactly("user");
    }

    // --- Single role ---

    @Test
    @DisplayName("handles single role without comma")
    void handlesSingleRole() throws ServletException, IOException {
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-User-Roles", "admin");

        filter.doFilter(request, response, chain);

        SecurityContext ctx = extractSecurityContext();
        assertThat(ctx.roles()).containsExactly("admin");
    }
}
