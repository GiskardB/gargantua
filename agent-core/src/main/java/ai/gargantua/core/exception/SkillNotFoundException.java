package ai.gargantua.core.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when the router selects a skill that does not exist in the registry.
 * Handled by the exception handler to return a 404 response.
 */
public class SkillNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SkillNotFoundException(String skillName) {
        super("Skill not found: %s".formatted(Objects.requireNonNull(skillName, "skillName must not be null")));
    }
}
