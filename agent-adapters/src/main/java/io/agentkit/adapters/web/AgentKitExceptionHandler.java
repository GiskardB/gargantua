package io.agentkit.adapters.web;

import io.agentkit.core.exception.ApprovalExpiredException;
import io.agentkit.core.exception.DryRunNotAllowedException;
import io.agentkit.core.exception.EvalSuiteNotFoundException;
import io.agentkit.core.exception.GuardrailBlockedException;
import io.agentkit.core.exception.RateLimitExceededException;
import io.agentkit.core.exception.SchemaValidationException;
import io.agentkit.core.exception.SkillNotFoundException;
import io.agentkit.core.exception.TokenBudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;

@ControllerAdvice
public class AgentKitExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentKitExceptionHandler.class);

    @ExceptionHandler(SkillNotFoundException.class)
    public ProblemDetail handleSkillNotFound(SkillNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Skill Not Found");
        problem.setType(URI.create("https://agentkit.io/errors/skill-not-found"));
        return problem;
    }

    @ExceptionHandler(GuardrailBlockedException.class)
    public ProblemDetail handleGuardrailBlocked(GuardrailBlockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Guardrail Blocked");
        problem.setType(URI.create("https://agentkit.io/errors/guardrail-blocked"));
        problem.setProperty("guardrailName", ex.getGuardrailName());
        problem.setProperty("metadata", ex.getMetadata());
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Rate Limit Exceeded");
        problem.setType(URI.create("https://agentkit.io/errors/rate-limit-exceeded"));
        problem.setProperty("userId", ex.getUserId());
        problem.setProperty("limit", ex.getLimit());
        return problem;
    }

    @ExceptionHandler(TokenBudgetExceededException.class)
    public ProblemDetail handleTokenBudgetExceeded(TokenBudgetExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setTitle("Token Budget Exceeded");
        problem.setType(URI.create("https://agentkit.io/errors/token-budget-exceeded"));
        problem.setProperty("fixedTokens", ex.getFixedTokens());
        problem.setProperty("maxTokens", ex.getMaxTokens());
        return problem;
    }

    @ExceptionHandler(ApprovalExpiredException.class)
    public ProblemDetail handleApprovalExpired(ApprovalExpiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        problem.setTitle("Approval Expired");
        problem.setType(URI.create("https://agentkit.io/errors/approval-expired"));
        problem.setProperty("requestId", ex.getRequestId());
        return problem;
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ProblemDetail handleSchemaValidation(SchemaValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Schema Validation Failed");
        problem.setType(URI.create("https://agentkit.io/errors/schema-validation"));
        problem.setProperty("skillName", ex.getSkillName());
        problem.setProperty("validationError", ex.getValidationError());
        return problem;
    }

    @ExceptionHandler(DryRunNotAllowedException.class)
    public ProblemDetail handleDryRunNotAllowed(DryRunNotAllowedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Dry Run Not Allowed");
        problem.setType(URI.create("https://agentkit.io/errors/dry-run-not-allowed"));
        return problem;
    }

    @ExceptionHandler(EvalSuiteNotFoundException.class)
    public ProblemDetail handleEvalSuiteNotFound(EvalSuiteNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Eval Suite Not Found");
        problem.setType(URI.create("https://agentkit.io/errors/eval-suite-not-found"));
        problem.setProperty("skillName", ex.getSkillName());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://agentkit.io/errors/internal"));
        return problem;
    }
}
