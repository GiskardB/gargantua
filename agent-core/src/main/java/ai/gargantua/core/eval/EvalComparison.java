package ai.gargantua.core.eval;

/**
 * Comparison between the current eval run and the previous one for regression detection.
 *
 * @param previousScore the overall score from the last run
 * @param scoreDelta    change in score (positive = improvement, negative = regression)
 * @param previousRunAt ISO timestamp of the previous run
 */
public record EvalComparison(
        double previousScore,
        double scoreDelta,
        String previousRunAt
) {
}
