package ai.gargantua.eval;

import java.util.List;

public record EvalResult(
    String caseId,
    String input,
    String agentResponse,
    String verdict,         // PASS | FAIL | PARTIAL
    double score,
    String reason,
    List<String> passedBehaviors,
    List<String> failedBehaviors,
    long durationMs
) {}
