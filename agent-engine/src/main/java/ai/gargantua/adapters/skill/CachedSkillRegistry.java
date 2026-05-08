package ai.gargantua.adapters.skill;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Decorator that adds Caffeine-based TTL caching to any {@link SkillRegistry}.
 * Caches the metadata list (single entry) and individual skill cards (up to 200).
 * Calling {@link #reload()} invalidates all caches and delegates to the underlying registry.
 *
 * @see CompositeSkillRegistry
 */
public class CachedSkillRegistry implements SkillRegistry {

    private static final String LIST_META_KEY = "ALL";

    private static final int DEFAULT_MAX_SIZE = 200;

    private final SkillRegistry delegate;
    private final Cache<String, List<SkillMeta>> metaCache;
    private final Cache<String, SkillCard> cardCache;

    public CachedSkillRegistry(SkillRegistry delegate, Duration ttl) {
        this(delegate, ttl, DEFAULT_MAX_SIZE);
    }

    public CachedSkillRegistry(SkillRegistry delegate, Duration ttl, int maxSize) {
        this.delegate = delegate;
        int cardMaxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.metaCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(1)
                .build();
        this.cardCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(cardMaxSize)
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
