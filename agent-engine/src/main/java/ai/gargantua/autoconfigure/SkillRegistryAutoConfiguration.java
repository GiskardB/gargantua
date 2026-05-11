package ai.gargantua.autoconfigure;

import ai.gargantua.adapters.skill.AnnotatedSkillRegistry;
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
import org.springframework.context.ApplicationContext;
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

    /**
     * {@link AgentSkillProcessor} scans the application context for
     * {@code @AgentSkill}-annotated beans at startup. Registered here as a
     * {@code @Bean} (1.2.7+) so user applications don't have to remember
     * to expand their component scan into the framework's autoconfigure
     * package — the previous {@code @Component}-only setup silently
     * disabled the annotation when forgotten.
     */
    @Bean
    @ConditionalOnMissingBean(AgentSkillProcessor.class)
    public AgentSkillProcessor agentSkillProcessor(ApplicationContext applicationContext) {
        return new AgentSkillProcessor(applicationContext);
    }

    @Bean("annotatedSkillRegistry")
    @ConditionalOnMissingBean(name = "annotatedSkillRegistry")
    public AnnotatedSkillRegistry annotatedSkillRegistry(AgentSkillProcessor processor) {
        return new AnnotatedSkillRegistry(processor);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "agent.skill", name = "hot-reload", havingValue = "false", matchIfMissing = true)
    public SkillRegistry cachedSkillRegistry(
            FilesystemSkillRegistry filesystemSkillRegistry,
            ClasspathSkillsJarRegistry classpathSkillsJarRegistry,
            AnnotatedSkillRegistry annotatedSkillRegistry,
            AgentProperties properties) {

        // Order matters: CompositeSkillRegistry is first-match-wins, so
        // SKILL.md files (filesystem + classpath jar) take precedence over
        // @AgentSkill classes with the same name — the long-documented rule.
        var composite = new CompositeSkillRegistry(
                List.of(filesystemSkillRegistry, classpathSkillsJarRegistry, annotatedSkillRegistry));

        var ttl = resolveCacheTtl(properties);
        var maxSize = properties.getSkill().getCache().getMaxSize();
        log.info("SkillRegistry: Composite (filesystem + classpath-jar + annotated) → Cached (TTL={}, maxSize={})",
                ttl, maxSize);
        return new CachedSkillRegistry(composite, ttl, maxSize);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "agent.skill", name = "hot-reload", havingValue = "true")
    public SkillRegistry hotReloadSkillRegistry(
            FilesystemSkillRegistry filesystemSkillRegistry,
            ClasspathSkillsJarRegistry classpathSkillsJarRegistry,
            AnnotatedSkillRegistry annotatedSkillRegistry,
            AgentProperties properties,
            ApplicationEventPublisher eventPublisher) {

        var composite = new CompositeSkillRegistry(
                List.of(filesystemSkillRegistry, classpathSkillsJarRegistry, annotatedSkillRegistry));

        var ttl = resolveCacheTtl(properties);
        var maxSize = properties.getSkill().getCache().getMaxSize();
        var cached = new CachedSkillRegistry(composite, ttl, maxSize);

        var skillPath = properties.getSkill().getPath();
        var watchPath = skillPath.startsWith("classpath:")
                ? Path.of("src/main/resources/" + skillPath.replace("classpath:", ""))
                : Path.of(skillPath);

        log.info("SkillRegistry: Composite (filesystem + classpath-jar + annotated) → Cached → HotReload (watch={}, TTL={}, maxSize={})",
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
