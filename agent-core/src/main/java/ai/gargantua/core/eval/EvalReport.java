package ai.gargantua.core.eval;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate report for a complete eval suite run. Contains per-case results,
 * summary statistics, and an optional comparison with the previous run
 * to detect regressions.
 *
 * @param skillName    the evaluated skill
 * @param skillVersion the skill version at evaluation time
 * @param runAt        when the eval suite was executed
 * @param totalCases   total number of cases in the suite
 * @param passed       cases with PASS verdict
 * @param failed       cases with FAIL verdict
 * @param partial      cases with PARTIAL verdict
 * @param overallScore average score across all cases (0.0 to 1.0)
 * @param results      individual case results
 * @param comparison   delta vs. previous run (null if no prior run exists)
 *
 * @see EvalResult
 */
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
