package ai.gargantua.core.guardrail;

/**
 * Three-state outcome for guardrail evaluations.
 */
public enum GuardrailVerdict {
    /** Input/output is acceptable. Processing continues. */
    PASS,
    /** Input/output is rejected. The pipeline halts and an error is returned. */
    BLOCK,
    /** Input/output is suspicious but allowed. A warning is logged. */
    WARN
}
