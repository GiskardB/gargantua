package ai.gargantua.core.eval;

import java.util.List;

public record EvalResult(
        String caseId,
        String description,
        String input,
        String actualResponse,
        List<String> toolsCalled,
        EvalVerdict verdict,
        double score,
        String judgeReasoning,
        List<String> passedBehaviors,
        List<String> failedBehaviors,
        long durationMs
) {
}
