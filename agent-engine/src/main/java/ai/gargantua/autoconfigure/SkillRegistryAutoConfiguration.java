package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.skill.CachedSkillRegistry;
import ai.gargantua.adapters.skill.ClasspathSkillsJarRegistry;
import ai.gargantua.adapters.skill.CompositeSkillRegistry;
import ai.gargantua.adapters.skill.FilesystemSkillRegistry;
import ai.gargantua.adapters.skill.HotReloadSkillRegistry;
import ai.gargantua.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Auto-configuration for the skill registry chain.
 *
 * <p>Assembles: FilesystemSkillRegistry + ClasspathSkillsJarRegistry
 * → CompositeSkillRegistry → CachedSkillRegistry
 * → (optionally) HotReloadSkillRegistry.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentProperties.class)
public class SkillRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "skillMdParser")
    public SkillMdParser skillMdParser() {
        return new SkillMdParser();
    }

    @Bean("filesystemSkillRegistry")
    @ConditionalOnMissingBean(name = "filesystemSkillRegistry")
    public FilesystemSkillRegistry filesystemSkillRegistry(
            AgentProperties properties,
            SkillMdParser skillMdParser,
            ResourcePatternResolver resourcePatternResolver) {
        return new FilesystemSkillRegistry(
                properties.getSkill().getPath(),
                skillMdParser,
                resourcePatternResolver);
    }

    @Bean("classpathSkillsJarRegistry")
    @ConditionalOnMissingBean(name = "classpathSkillsJarRegistry")
    public ClasspathSkillsJarRegistry classpathSkillsJarRegistry(
            SkillMdParser skillMdParser,
            ResourcePatternResolver resourcePatternResolver) {
        return new ClasspathSkillsJarRegistry(skillMdParser, resourcePatternResolver);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "agent.skill", name = "hot-reload", havingValue = "false", matchIfMissing = true)
    public SkillRegistry cachedSkillRegistry(
            FilesystemSkillRegistry filesystemSkillRegistry,
            ClasspathSkillsJarRegistry classpathSkillsJarRegistry,
            AgentProperties properties) {

        var composite = new CompositeSkillRegistry(
                List.of(filesystemSkillRegistry, classpathSkillsJarRegistry));

        var ttl = resolveCacheTtl(properties);
        var maxSize = properties.getSkill().getCache().getMaxSize();
        log.info("SkillRegistry: Composite → Cached (TTL={}, maxSize={})", ttl, maxSize);
        return new CachedSkillRegistry(composite, ttl, maxSize);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "agent.skill", name = "hot-reload", havingValue = "true")
    public SkillRegistry hotReloadSkillRegistry(
            FilesystemSkillRegistry filesystemSkillRegistry,
            ClasspathSkillsJarRegistry classpathSkillsJarRegistry,
            AgentProperties properties,
            ApplicationEventPublisher eventPublisher) {

        var composite = new CompositeSkillRegistry(
                List.of(filesystemSkillRegistry, classpathSkillsJarRegistry));

        var ttl = resolveCacheTtl(properties);
        var maxSize = properties.getSkill().getCache().getMaxSize();
        var cached = new CachedSkillRegistry(composite, ttl, maxSize);

        var skillPath = properties.getSkill().getPath();
        var watchPath = skillPath.startsWith("classpath:")
                ? Path.of("src/main/resources/" + skillPath.replace("classpath:", ""))
                : Path.of(skillPath);

        log.info("SkillRegistry: Composite → Cached → HotReload (watch={}, TTL={}, maxSize={})",
                watchPath, ttl, maxSize);
        return new HotReloadSkillRegistry(cached, watchPath, eventPublisher);
    }

    private Duration resolveCacheTtl(AgentProperties properties) {
        int ttlSeconds = properties.getSkill().getCache().getTtlSeconds();
        return ttlSeconds > 0
                ? Duration.ofSeconds(ttlSeconds)
                : Duration.ofMinutes(properties.getSkill().getCacheTtlMinutes());
    }
}
