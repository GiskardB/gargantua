package ai.gargantua.adapters.web;

import ai.gargantua.core.exception.ApprovalExpiredException;
import ai.gargantua.core.exception.DryRunNotAllowedException;
import ai.gargantua.core.exception.GuardrailBlockedException;
import ai.gargantua.core.exception.RateLimitExceededException;
import ai.gargantua.core.exception.SchemaValidationException;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.exception.TokenBudgetExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentKitExceptionHandler")
class AgentKitExceptionHandlerTest {

    private AgentKitExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AgentKitExceptionHandler();
    }

    // --- SkillNotFoundException ---

    @Test
    @DisplayName("handleSkillNotFound returns 404 with skill details")
    void handleSkillNotFound() {
        var ex = new SkillNotFoundException("workout-planner");

        ProblemDetail problem = handler.handleSkillNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Skill Not Found");
        assertThat(problem.getDetail()).contains("workout-planner");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/skill-not-found");
    }

    // --- GuardrailBlockedException ---

    @Test
    @DisplayName("handleGuardrailBlocked returns 403 with guardrail name and metadata")
    void handleGuardrailBlocked() {
        var ex = new GuardrailBlockedException("prompt-injection", "Injection detected",
                Map.of("pattern", "ignore.*instructions"));

        ProblemDetail problem = handler.handleGuardrailBlocked(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Guardrail Blocked");
        assertThat(problem.getDetail()).contains("Injection detected");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/guardrail-blocked");
        assertThat(problem.getProperties()).containsEntry("guardrailName", "prompt-injection");
        assertThat(problem.getProperties()).containsKey("metadata");
    }

    @Test
    @DisplayName("handleGuardrailBlocked handles empty metadata")
    void handleGuardrailBlocked_emptyMetadata() {
        var ex = new GuardrailBlockedException("max-length", "Too long", Map.of());

        ProblemDetail problem = handler.handleGuardrailBlocked(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getProperties()).containsEntry("metadata", Map.of());
    }

    // --- RateLimitExceededException ---

    @Test
    @DisplayName("handleRateLimitExceeded returns 429 with user and limit details")
    void handleRateLimitExceeded() {
        var ex = new RateLimitExceededException("user-99", 60);

        ProblemDetail problem = handler.handleRateLimitExceeded(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getTitle()).isEqualTo("Rate Limit Exceeded");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/rate-limit-exceeded");
        assertThat(problem.getProperties()).containsEntry("userId", "user-99");
        assertThat(problem.getProperties()).containsEntry("limit", 60);
    }

    // --- TokenBudgetExceededException ---

    @Test
    @DisplayName("handleTokenBudgetExceeded returns 413 with token details")
    void handleTokenBudgetExceeded() {
        var ex = new TokenBudgetExceededException(5000, 4096);

        ProblemDetail problem = handler.handleTokenBudgetExceeded(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getTitle()).isEqualTo("Token Budget Exceeded");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/token-budget-exceeded");
        assertThat(problem.getProperties()).containsEntry("fixedTokens", 5000);
        assertThat(problem.getProperties()).containsEntry("maxTokens", 4096);
    }

    // --- ApprovalExpiredException ---

    @Test
    @DisplayName("handleApprovalExpired returns 410 with request ID")
    void handleApprovalExpired() {
        var ex = new ApprovalExpiredException("req-abc-123");

        ProblemDetail problem = handler.handleApprovalExpired(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.GONE.value());
        assertThat(problem.getTitle()).isEqualTo("Approval Expired");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/approval-expired");
        assertThat(problem.getProperties()).containsEntry("requestId", "req-abc-123");
    }

    // --- SchemaValidationException ---

    @Test
    @DisplayName("handleSchemaValidation returns 400 with skill name and validation error")
    void handleSchemaValidation() {
        var ex = new SchemaValidationException("workout-planner", "missing required field 'exercises'");

        ProblemDetail problem = handler.handleSchemaValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Schema Validation Failed");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/schema-validation");
        assertThat(problem.getProperties()).containsEntry("skillName", "workout-planner");
        assertThat(problem.getProperties()).containsEntry("validationError", "missing required field 'exercises'");
    }

    // --- DryRunNotAllowedException ---

    @Test
    @DisplayName("handleDryRunNotAllowed returns 403")
    void handleDryRunNotAllowed() {
        var ex = new DryRunNotAllowedException();

        ProblemDetail problem = handler.handleDryRunNotAllowed(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Dry Run Not Allowed");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/dry-run-not-allowed");
    }


    // --- NoResourceFoundException ---

    @Test
    @DisplayName("handleNotFound returns 404 for missing resources")
    void handleNotFound() throws NoResourceFoundException {
        var ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/nonexistent", "No static resource api/nonexistent");

        ProblemDetail problem = handler.handleNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/not-found");
    }

    // --- Generic Exception ---

    @Test
    @DisplayName("handleGeneric returns 500 for unhandled exceptions")
    void handleGeneric() {
        var ex = new RuntimeException("Something went wrong");

        ProblemDetail problem = handler.handleGeneric(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getType().toString()).isEqualTo("https://agentkit.io/errors/internal");
    }

    @Test
    @DisplayName("handleGeneric does not leak exception details")
    void handleGeneric_doesNotLeakDetails() {
        var ex = new RuntimeException("Sensitive DB connection string: jdbc:mysql://...");

        ProblemDetail problem = handler.handleGeneric(ex);

        assertThat(problem.getDetail()).doesNotContain("jdbc:mysql");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
