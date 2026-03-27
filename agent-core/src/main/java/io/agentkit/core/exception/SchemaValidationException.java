package io.agentkit.core.exception;

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
