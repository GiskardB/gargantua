package ai.gargantua.core.routing;

import java.util.List;

/**
 * Outcome of a {@link SkillClassifier#classify(String, List)} call.
 *
 * @param skillName  the best-matching skill name, or {@code null} if none matched
 * @param confidence score of the best match, in {@code [0.0, 1.0]}
 * @param candidates top-K scored candidates, for logging/debugging; may be empty
 */
public record ClassificationResult(
        String skillName,
        double confidence,
        List<ScoredSkill> candidates
) {

    public static ClassificationResult of(String skillName, double confidence) {
        return new ClassificationResult(skillName, confidence, List.of());
    }

    public static final ClassificationResult NONE = new ClassificationResult(null, 0.0, List.of());
}
