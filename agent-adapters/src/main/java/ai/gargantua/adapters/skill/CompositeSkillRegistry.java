package ai.gargantua.adapters.skill;

import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompositeSkillRegistry implements SkillRegistry {

    private final List<SkillRegistry> registries;

    public CompositeSkillRegistry(List<SkillRegistry> registries) {
        this.registries = List.copyOf(registries);
    }

    @Override
    public List<SkillMeta> listMeta() {
        Map<String, SkillMeta> merged = new LinkedHashMap<>();
        for (SkillRegistry registry : registries) {
            for (SkillMeta meta : registry.listMeta()) {
                merged.putIfAbsent(meta.name(), meta);
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public SkillCard load(String skillName) {
        for (SkillRegistry registry : registries) {
            Optional<SkillMeta> meta = registry.findMeta(skillName);
            if (meta.isPresent()) {
                return registry.load(skillName);
            }
        }
        throw new SkillNotFoundException(skillName);
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        for (SkillRegistry registry : registries) {
            Optional<SkillMeta> meta = registry.findMeta(skillName);
            if (meta.isPresent()) {
                return meta;
            }
        }
        return Optional.empty();
    }

    @Override
    public void reload() {
        registries.forEach(SkillRegistry::reload);
    }
}
