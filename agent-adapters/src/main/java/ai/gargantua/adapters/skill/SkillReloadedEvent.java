package ai.gargantua.adapters.skill;

import org.springframework.context.ApplicationEvent;

public class SkillReloadedEvent extends ApplicationEvent {

    private final String skillName;

    public SkillReloadedEvent(Object source, String skillName) {
        super(source);
        this.skillName = skillName;
    }

    public String getSkillName() {
        return skillName;
    }
}
