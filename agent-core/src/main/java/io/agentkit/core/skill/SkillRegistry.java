package io.agentkit.core.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRegistry {

    List<SkillMeta> listMeta();

    SkillCard load(String skillName);

    Optional<SkillMeta> findMeta(String skillName);

    void reload();
}
