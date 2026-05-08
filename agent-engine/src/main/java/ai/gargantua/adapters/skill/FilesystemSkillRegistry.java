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
            String content;
            try (var is = resources[0].getInputStream()) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            SkillCard card = skillMdParser.parseToCard(content, SkillSource.FILESYSTEM);
            return appendFolderReferences(card, skillName);
        } catch (IOException e) {
            throw new SkillNotFoundException(skillName);
        }
    }

    /**
     * Augments {@link SkillCard#references()} with the contents of every file under
     * {@code <skillPath>/<skillName>/references/}. Frontmatter-declared references
     * stay first, then folder files in lexicographic order. The folder is optional —
     * if it doesn't exist the original card is returned unchanged.
     */
    private SkillCard appendFolderReferences(SkillCard card, String skillName) {
        var pattern = skillPath.endsWith("/")
                ? "%s%s/references/*".formatted(skillPath, skillName)
                : "%s/%s/references/*".formatted(skillPath, skillName);
        List<String> folderRefs = new ArrayList<>();
        try {
            var refResources = resourcePatternResolver.getResources(pattern);
            if (refResources == null || refResources.length == 0) {
                return card;
            }
            // Sort by URI for deterministic ordering across platforms
            java.util.Arrays.sort(refResources, java.util.Comparator.comparing(r -> {
                try { return r.getURI().toString(); } catch (IOException e) { return r.getFilename() != null ? r.getFilename() : ""; }
            }));
            for (var resource : refResources) {
                if (!resource.isReadable()) continue;
                try (var is = resource.getInputStream()) {
                    folderRefs.add(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.warn("Failed to read reference file: {}", resource.getDescription(), e);
                }
            }
        } catch (IOException e) {
            // No references/ folder is fine — log only at debug
            log.debug("No references/ folder for skill '{}' (or scan failed: {})", skillName, e.getMessage());
        }
        if (folderRefs.isEmpty()) {
            return card;
        }
        List<String> merged = new ArrayList<>(card.references() != null ? card.references() : List.of());
        merged.addAll(folderRefs);
        return new SkillCard(
                card.meta(), card.systemPrompt(), card.allowedTools(), card.outputSchema(),
                List.copyOf(merged), card.maxTokens(), card.temperature(), card.preferredModel(),
                card.ragConfig(), card.enabledMemoryLayers(), card.examples()
        );
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
