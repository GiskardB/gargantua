package io.agentkit.core.eval;

public record EvalComparison(
        double previousScore,
        double scoreDelta,
        String previousRunAt
) {
}
