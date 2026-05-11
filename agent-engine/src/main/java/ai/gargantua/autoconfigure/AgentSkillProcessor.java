package ai.gargantua.autoconfigure;

import ai.gargantua.core.rag.RagConfig;
import ai.gargantua.core.skill.*;
import ai.gargantua.core.tool.AgentTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Scans Spring beans for {@link AgentSkill} annotations at startup and registers
 * them as skills. The system prompt is read from a {@code static final String PROMPT}
 * field on the annotated class (since Javadoc is not available at runtime).
 *
 * <p>If a SKILL.md file exists with the same name, it takes priority.</p>
 *
 * <p>Registered as a {@code @Bean} by {@code SkillRegistryAutoConfiguration}
 * (1.2.7+) — the older {@code @Component} stereotype required user apps to
 * extend their component scan, which was easy to forget. The bean is consumed
 * by {@link ai.gargantua.adapters.skill.AnnotatedSkillRegistry} as the
 * third source in the registry composite.</p>
 */
public class AgentSkillProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentSkillProcessor.class);

    private final ApplicationContext applicationContext;
    private final List<SkillCard> discoveredSkills = new ArrayList<>();

    public AgentSkillProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void processAnnotatedSkills() {
        var beans = applicationContext.getBeansWithAnnotation(AgentSkill.class);

        for (var entry : beans.entrySet()) {
            var bean = entry.getValue();
            var clazz = bean.getClass();
            var annotation = clazz.getAnnotation(AgentSkill.class);
            if (annotation == null) continue;

            // Auto-detect @AgentTool methods
            var tools = new ArrayList<String>();
            for (Method method : clazz.getMethods()) {
                if (method.isAnnotationPresent(AgentTool.class)) {
                    var toolAnn = method.getAnnotation(AgentTool.class);
                    tools.add(toolAnn.name().isEmpty() ? method.getName() : toolAnn.name());
                }
            }

            var systemPrompt = extractPrompt(clazz);
            var ragConfig = annotation.knowledgeBase().isEmpty() ? null :
                    new RagConfig(annotation.knowledgeBase(), annotation.ragMaxResults(), annotation.ragMinScore());

            var meta = new SkillMeta(
                    annotation.name(),
                    annotation.description(),
                    annotation.version(),
                    annotation.active(),
                    !annotation.outputSchema().isEmpty(),
                    annotation.domain(),
                    SkillSource.ANNOTATION,
                    annotation.allowedRoles().length > 0 ? Set.of(annotation.allowedRoles()) : Set.of()
            );

            var card = new SkillCard(
                    meta, systemPrompt, tools,
                    annotation.outputSchema().isEmpty() ? null : loadResource(annotation.outputSchema()),
                    List.of(),
                    annotation.maxTokens() > 0 ? annotation.maxTokens() : null,
                    annotation.temperature() >= 0 ? annotation.temperature() : null,
                    annotation.preferredModel().isEmpty() ? null : annotation.preferredModel(),
                    ragConfig,
                    null,
                    List.of(annotation.examples())
            );

            discoveredSkills.add(card);
            log.info("[AgentSkillProcessor] Registered @AgentSkill: name={}, tools={}, domain={}",
                    annotation.name(), tools, annotation.domain());
        }

        log.info("[AgentSkillProcessor] Discovered {} annotation-based skills", discoveredSkills.size());
    }

    /** Returns skills discovered from @AgentSkill annotations (for SkillRegistry integration). */
    public List<SkillCard> getDiscoveredSkills() {
        return List.copyOf(discoveredSkills);
    }

    private String extractPrompt(Class<?> clazz) {
        try {
            var field = clazz.getDeclaredField("PROMPT");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return "You are a helpful assistant for the %s skill.".formatted(clazz.getSimpleName());
        }
    }

    private String loadResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            return is != null ? new String(is.readAllBytes()) : null;
        } catch (Exception e) {
            log.warn("[AgentSkillProcessor] Failed to load resource: {}", path);
            return null;
        }
    }
}
