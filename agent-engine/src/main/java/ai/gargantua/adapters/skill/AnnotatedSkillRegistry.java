package ai.gargantua.adapters.skill;

import ai.gargantua.autoconfigure.AgentSkillProcessor;
import ai.gargantua.core.exception.SkillNotFoundException;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link SkillRegistry} that surfaces the skills discovered by
 * {@link AgentSkillProcessor} (i.e. classes annotated with
 * {@code @AgentSkill}). Added in 1.2.7 — before that, the processor
 * discovered annotated skills but the registry chain ignored them.
 *
 * <p>Indexes the processor's output by name on every call. The cost is
 * negligible (one {@code LinkedHashMap} build over a handful of
 * {@link SkillCard}s) and it keeps the contract trivially correct when
 * the underlying list changes (e.g. via {@link #reload()} downstream).</p>
 *
 * <p>Composed <em>after</em> the filesystem / classpath-jar registries in
 * {@code SkillRegistryAutoConfiguration} so SKILL.md files of the same
 * name continue to win — the long-documented precedence rule.</p>
 */
public class AnnotatedSkillRegistry implements SkillRegistry {

    private final AgentSkillProcessor processor;

    public AnnotatedSkillRegistry(AgentSkillProcessor processor) {
        this.processor = processor;
    }

    private Map<String, SkillCard> index() {
        Map<String, SkillCard> byName = new LinkedHashMap<>();
        for (SkillCard card : processor.getDiscoveredSkills()) {
            byName.put(card.meta().name(), card);
        }
        return byName;
    }

    @Override
    public List<SkillMeta> listMeta() {
        return processor.getDiscoveredSkills().stream()
                .map(SkillCard::meta)
                .toList();
    }

    @Override
    public SkillCard load(String skillName) {
        SkillCard card = index().get(skillName);
        if (card == null) {
            throw new SkillNotFoundException(skillName);
        }
        return card;
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        SkillCard card = index().get(skillName);
        return card == null ? Optional.empty() : Optional.of(card.meta());
    }

    @Override
    public void reload() {
        // No-op — annotated skills are scanned once at boot. Re-running the
        // processor would require an application-context refresh anyway, so
        // there is nothing useful for this method to do at runtime.
    }
}
