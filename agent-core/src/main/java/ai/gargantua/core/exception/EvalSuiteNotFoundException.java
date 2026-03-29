package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when requesting an eval run for a skill that has no {@code evals/evals.json} dataset.
 */
public class EvalSuiteNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String skillName;

    public EvalSuiteNotFoundException(String skillName) {
        super("Eval suite not found for skill: %s".formatted(Objects.requireNonNull(skillName, "skillName must not be null")));
        this.skillName = skillName;
    }

    public String getSkillName() {
        return skillName;
    }
}
