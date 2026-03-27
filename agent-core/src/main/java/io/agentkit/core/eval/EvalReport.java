package io.agentkit.core.eval;

import java.time.Instant;
import java.util.List;

public record EvalReport(
        String skillName,
        String skillVersion,
        Instant runAt,
        int totalCases,
        int passed,
        int failed,
        int partial,
        double overallScore,
        List<EvalResult> results,
        EvalComparison comparison
) {
}
