package ai.gargantua.core.eval;

public record EvalComparison(
        double previousScore,
        double scoreDelta,
        String previousRunAt
) {
}
