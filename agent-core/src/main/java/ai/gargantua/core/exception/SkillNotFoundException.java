package ai.gargantua.core.exception;

/**
 * Thrown when the router selects a skill that does not exist in the registry.
 * Handled by the exception handler to return a 404 response.
 */
public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String skillName) {
        super("Skill not found: " + skillName);
    }
}
