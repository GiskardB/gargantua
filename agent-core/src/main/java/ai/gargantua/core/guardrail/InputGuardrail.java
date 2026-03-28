package ai.gargantua.core.guardrail;

/**
 * Contract for pre-LLM guardrails that validate or transform user input.
 * Implementations are executed in order by the {@code GuardrailPipeline} before
 * routing. A BLOCK verdict aborts the request immediately.
 *
 * <p>Built-in implementations include prompt injection detection, max-length check,
 * PII masking, topic scope enforcement, and rate limiting.</p>
 *
 * @see GuardrailResult
 * @see GuardrailInputContext
 */
public interface InputGuardrail {

    /** Unique name for logging and admin endpoints. */
    String name();

    /** Whether this guardrail is active. Receives the properties object for config checks. */
    boolean isEnabled(Object props);

    /** Inspects the input and returns PASS, WARN, or BLOCK. */
    GuardrailResult check(GuardrailInputContext ctx);
}
