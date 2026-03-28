package ai.gargantua.core.eval;

import java.util.List;

/**
 * A single test case in a skill's evaluation dataset. Loaded from
 * {@code evals/evals.json} inside the skill directory.
 *
 * @param id                 unique case identifier within the suite
 * @param description        human-readable description of what is being tested
 * @param input              the simulated user message
 * @param expectedBehaviors  strings that should appear in the agent's response
 * @param forbiddenBehaviors strings that must NOT appear in the response
 * @param tags               labels for filtering/grouping eval cases
 *
 * @see EvalResult
 * @see EvalReport
 */
public record EvalCase(
        String id,
        String description,
        String input,
        List<String> expectedBehaviors,
        List<String> forbiddenBehaviors,
        List<String> tags
) {
}
