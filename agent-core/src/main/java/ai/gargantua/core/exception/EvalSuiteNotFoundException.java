package ai.gargantua.core.exception;

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
