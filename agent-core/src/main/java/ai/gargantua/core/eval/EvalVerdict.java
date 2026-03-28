package ai.gargantua.core.eval;

/**
 * Three-state outcome for a single eval case judgment.
 */
public enum EvalVerdict {
    /** All expected behaviors present, no forbidden behaviors found. */
    PASS,
    /** No expected behaviors found, or forbidden behavior detected. */
    FAIL,
    /** Some expected behaviors found but not all. */
    PARTIAL
}
