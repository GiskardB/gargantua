package ai.gargantua.core.eval;

import java.util.List;

public record EvalCase(
        String id,
        String description,
        String input,
        List<String> expectedBehaviors,
        List<String> forbiddenBehaviors,
        List<String> tags
) {
}
