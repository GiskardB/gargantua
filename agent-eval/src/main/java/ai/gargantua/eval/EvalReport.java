package ai.gargantua.eval;


import java.util.List;

public record EvalReport(
    String agentUrl,
    String runAt,
    int totalCases,
    int passed,
    int failed,
    int partial,
    double overallScore,
    List<EvalResult> results
) {}
