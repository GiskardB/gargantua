package ai.gargantua.adapters.skill;

import ai.gargantua.autoconfigure.SkillMdParser;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads skills from SKILL.md files on the local filesystem. Scans the configured
 * skill directory ({@code agent.skill.path}) for subdirectories containing SKILL.md files.
 *
 * @see CompositeSkillRegistry
 */
public class FilesystemSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(FilesystemSkillRegistry.class);

    private final String skillPath;
    private final SkillMdParser skillMdParser;
    private final ResourcePatternResolver resourcePatternResolver;
    private volatile List<SkillMeta> cachedMeta;

    public FilesystemSkillRegistry(
            String skillPath,
            SkillMdParser skillMdParser,
            ResourcePatternResolver resourcePatternResolver) {
        this.skillPath = skillPath;
        this.skillMdParser = skillMdParser;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Override
    public List<SkillMeta> listMeta() {
        if (cachedMeta != null) {
            return cachedMeta;
        }
        var result = new ArrayList<SkillMeta>();
        try {
            var pattern = skillPath.endsWith("/")
                    ? skillPath + "**/SKILL.md"
                    : skillPath + "/**/SKILL.md";
            var resources = resourcePatternResolver.getResources(pattern);
            for (var resource : resources) {
                try (var is = resource.getInputStream()) {
                    var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    var meta = skillMdParser.parseToMeta(content, SkillSource.FILESYSTEM);
                    result.add(meta);
                } catch (IOException e) {
                    log.warn("Failed to parse skill file: {}", resource.getDescription(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan skill directory: {}", skillPath, e);
        }
        cachedMeta = new CopyOnWriteArrayList<>(result);
        return cachedMeta;
    }

    @Override
    public SkillCard load(String skillName) {
        try {
            var pattern = skillPath.endsWith("/")
                    ? "%s%s/SKILL.md".formatted(skillPath, skillName)
                    : "%s/%s/SKILL.md".formatted(skillPath, skillName);
            var resources = resourcePatternResolver.getResources(pattern);
            if (resources.length == 0) {
                throw new SkillNotFoundException(skillName);
            }
            try (var is = resources[0].getInputStream()) {
                var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return skillMdParser.parseToCard(content, SkillSource.FILESYSTEM);
            }
        } catch (IOException e) {
            throw new SkillNotFoundException(skillName);
        }
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        return listMeta().stream()
                .filter(meta -> meta.name().equals(skillName))
                .findFirst();
    }

    @Override
    public void reload() {
        cachedMeta = null;
        log.info("Filesystem skill registry cache cleared");
    }
}
