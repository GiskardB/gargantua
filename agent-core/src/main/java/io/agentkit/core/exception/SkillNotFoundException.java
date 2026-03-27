package io.agentkit.core.exception;

public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String skillName) {
        super("Skill not found: " + skillName);
    }
}
