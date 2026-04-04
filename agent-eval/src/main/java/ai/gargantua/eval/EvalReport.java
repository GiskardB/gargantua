package ai.gargantua.eval;

import java.time.Instant;
import java.util.List;

public record EvalReport(
    String agentUrl,
    Instant runAt,
    int totalCases,
    int passed,
    int failed,
    int partial,
    double overallScore,
    List<EvalResult> results
) {}
