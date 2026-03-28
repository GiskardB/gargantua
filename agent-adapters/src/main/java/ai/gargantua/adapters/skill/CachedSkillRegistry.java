package ai.gargantua.adapters.skill;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class CachedSkillRegistry implements SkillRegistry {

    private static final String LIST_META_KEY = "ALL";

    private final SkillRegistry delegate;
    private final Cache<String, List<SkillMeta>> metaCache;
    private final Cache<String, SkillCard> cardCache;

    public CachedSkillRegistry(SkillRegistry delegate, Duration ttl) {
        this.delegate = delegate;
        this.metaCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1)
                .build();
        this.cardCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(200)
                .build();
    }

    public CachedSkillRegistry(SkillRegistry delegate) {
        this(delegate, Duration.ofMinutes(60));
    }

    @Override
    public List<SkillMeta> listMeta() {
        return metaCache.get(LIST_META_KEY, key -> delegate.listMeta());
    }

    @Override
    public SkillCard load(String skillName) {
        return cardCache.get(skillName, delegate::load);
    }

    @Override
    public Optional<SkillMeta> findMeta(String skillName) {
        return listMeta().stream()
                .filter(meta -> meta.name().equals(skillName))
                .findFirst();
    }

    @Override
    public void reload() {
        metaCache.invalidateAll();
        cardCache.invalidateAll();
        delegate.reload();
    }
}
