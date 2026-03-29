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

/**
 * Merges multiple {@link SkillRegistry} sources into a single view. Skills from
 * earlier registries take precedence (first match wins). Typically wraps a
 * {@code FilesystemSkillRegistry} and a {@code ClasspathSkillsJarRegistry}.
 *
 * @see CachedSkillRegistry
 * @see HotReloadSkillRegistry
 */
public class CompositeSkillRegistry implements SkillRegistry {

    private final List<SkillRegistry> registries;

    public CompositeSkillRegistry(List<SkillRegistry> registries) {
        this.registries = List.copyOf(registries);
    }

    @Override
    public List<SkillMeta> listMeta() {
        var merged = new LinkedHashMap<String, SkillMeta>();
        for (var registry : registries) {
            for (var meta : registry.listMeta()) {
                merged.putIfAbsent(meta.name(), meta);
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public SkillCard load(String skillName) {
        for (var registry : registries) {
            var meta = registry.findMeta(skillName);
            if (meta.isPresent()) {
                return registry.load(skillName);
            }
        }
        throw new SkillNotFoundException(skillName);
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        return registries.stream()
                .map(registry -> registry.findMeta(skillName))
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    @Override
    public void reload() {
        registries.forEach(SkillRegistry::reload);
    }
}
