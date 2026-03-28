package ai.gargantua.adapters.skill;

import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
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
@Component("filesystemSkillRegistry")
public class FilesystemSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(FilesystemSkillRegistry.class);

    private final String skillPath;
    private final SkillMdParser skillMdParser;
    private final ResourcePatternResolver resourcePatternResolver;
    private volatile List<SkillMeta> cachedMeta;

    public FilesystemSkillRegistry(
            @Value("${agent.skill.path:classpath:skills/}") String skillPath,
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
        List<SkillMeta> result = new ArrayList<>();
        try {
            String pattern = skillPath.endsWith("/")
                    ? skillPath + "**/SKILL.md"
                    : skillPath + "/**/SKILL.md";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    SkillMeta meta = skillMdParser.parseFrontmatter(content, SkillSource.FILESYSTEM);
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
            String pattern = skillPath.endsWith("/")
                    ? skillPath + skillName + "/SKILL.md"
                    : skillPath + "/" + skillName + "/SKILL.md";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            if (resources.length == 0) {
                throw new SkillNotFoundException(skillName);
            }
            try (InputStream is = resources[0].getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return skillMdParser.parseFull(content, SkillSource.FILESYSTEM);
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
