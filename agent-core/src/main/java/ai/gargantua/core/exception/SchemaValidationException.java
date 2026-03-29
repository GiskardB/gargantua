package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when the agent's response fails JSON Schema validation against
 * the skill's configured output schema.
 */
public class SchemaValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String skillName;
    private final String validationError;

    public SchemaValidationException(String skillName, String validationError) {
        super("Schema validation failed for skill '%s': %s".formatted(skillName, validationError));
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.validationError = Objects.requireNonNull(validationError, "validationError must not be null");
    }

    public String getSkillName() {
        return skillName;
    }

    public String getValidationError() {
        return validationError;
    }
}
