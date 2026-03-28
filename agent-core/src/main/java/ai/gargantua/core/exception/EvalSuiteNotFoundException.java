package ai.gargantua.core.exception;

/**
 * Thrown when requesting an eval run for a skill that has no {@code evals/evals.json} dataset.
 */
public class EvalSuiteNotFoundException extends RuntimeException {

    private final String skillName;

    public EvalSuiteNotFoundException(String skillName) {
        super("Eval suite not found for skill: " + skillName);
        this.skillName = skillName;
    }

    public String getSkillName() {
        return skillName;
    }
}
