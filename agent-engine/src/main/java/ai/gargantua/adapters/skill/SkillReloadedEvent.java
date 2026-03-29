package ai.gargantua.adapters.skill;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published when a skill is reloaded from the filesystem.
 * Listeners can use this to re-index semantic routing embeddings or invalidate caches.
 */
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
