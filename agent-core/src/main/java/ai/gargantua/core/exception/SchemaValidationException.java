package ai.gargantua.core.exception;

/**
 * Thrown when the agent's response fails JSON Schema validation against
 * the skill's configured output schema.
 */
public class SchemaValidationException extends RuntimeException {

    private final String skillName;
    private final String validationError;

    public SchemaValidationException(String skillName, String validationError) {
        super("Schema validation failed for skill '" + skillName + "': " + validationError);
        this.skillName = skillName;
        this.validationError = validationError;
    }

    public String getSkillName() {
        return skillName;
    }

    public String getValidationError() {
        return validationError;
    }
}
