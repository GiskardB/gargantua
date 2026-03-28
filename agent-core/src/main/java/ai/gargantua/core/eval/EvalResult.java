package ai.gargantua.core.eval;

import java.util.List;

/**
 * Result of evaluating a single {@link EvalCase} against the agent's actual response.
 *
 * @param caseId           the eval case identifier
 * @param description      case description
 * @param input            the input that was sent
 * @param actualResponse   the agent's raw response text
 * @param toolsCalled      tools the agent invoked during this case
 * @param verdict          PASS, FAIL, or PARTIAL
 * @param score            numeric score (0.0 to 1.0)
 * @param judgeReasoning   the LLM judge's explanation
 * @param passedBehaviors  expected behaviors that were found
 * @param failedBehaviors  expected behaviors that were missing
 * @param durationMs       wall-clock time for this case
 */
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
