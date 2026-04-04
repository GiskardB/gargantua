package ai.gargantua.eval;

import java.util.List;

/**
 * A single evaluation test case loaded from evals.json.
 */
public record EvalCase(
    String id,
    String description,
    String input,
    List<String> expectedBehaviors,
    List<String> forbiddenBehaviors,
    List<String> tags
) {}
