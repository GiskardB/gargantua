package ai.gargantua.core.guardrail;

/**
 * Contract for post-LLM guardrails that validate or transform the agent's response.
 * Output guardrails form a chain: each receives the (possibly modified) response from
 * the previous guardrail and can further transform it.
 *
 * <p>Built-in implementations include PII redaction, disclaimer injection,
 * JSON schema validation, and scope validation.</p>
 *
 * @see GuardrailOutputResult
 * @see GuardrailOutputContext
 */
public interface OutputGuardrail {

    /** Unique name for logging and admin endpoints. */
    String name();

    /** Whether this guardrail is active. Receives the properties object for config checks. */
    boolean isEnabled(Object props);

    /** Processes the response, potentially transforming it. May return BLOCK to suppress output. */
    GuardrailOutputResult process(GuardrailOutputContext ctx);
}
