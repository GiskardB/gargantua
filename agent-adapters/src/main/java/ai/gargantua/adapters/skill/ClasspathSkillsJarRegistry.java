package ai.gargantua.adapters.skill;

import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;
import ai.gargantua.core.skill.SkillSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Loads skills packaged inside JAR files on the classpath. Scans for
 * {@code META-INF/skills/ * /SKILL.md} resources, allowing skill libraries
 * to be distributed as Maven/Gradle dependencies.
 *
 * @see CompositeSkillRegistry
 */
@Component("classpathSkillsJarRegistry")
public class ClasspathSkillsJarRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSkillsJarRegistry.class);
    private static final String CLASSPATH_PATTERN = "classpath*:META-INF/skills/**/SKILL.md";

    private final SkillMdParser skillMdParser;
    private final ResourcePatternResolver resourcePatternResolver;
    private volatile List<SkillMeta> cachedMeta;

    public ClasspathSkillsJarRegistry(SkillMdParser skillMdParser,
                                      ResourcePatternResolver resourcePatternResolver) {
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
            var resources = resourcePatternResolver.getResources(CLASSPATH_PATTERN);
            for (var resource : resources) {
                try (var is = resource.getInputStream()) {
                    var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    var meta = skillMdParser.parseFrontmatter(content, SkillSource.CLASSPATH_JAR);
                    result.add(meta);
                } catch (IOException e) {
                    log.warn("Failed to parse classpath skill: {}", resource.getDescription(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan classpath for skills", e);
        }
        cachedMeta = new CopyOnWriteArrayList<>(result);
        return cachedMeta;
    }

    @Override
    public SkillCard load(String skillName) {
        try {
            var pattern = "classpath*:META-INF/skills/%s/SKILL.md".formatted(skillName);
            var resources = resourcePatternResolver.getResources(pattern);
            if (resources.length == 0) {
                throw new SkillNotFoundException(skillName);
            }
            try (var is = resources[0].getInputStream()) {
                var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return skillMdParser.parseFull(content, SkillSource.CLASSPATH_JAR);
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
        log.info("Classpath JAR skill registry cache cleared");
    }
}
