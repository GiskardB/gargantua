package ai.gargantua.autoconfigure;

import ai.gargantua.core.memory.MemoryLayer;
import ai.gargantua.core.rag.RagConfig;
import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses SKILL.md files consisting of YAML frontmatter delimited by "---" lines
 * followed by a markdown body that serves as the system prompt.
 */
public class SkillMdParser {

    private static final Logger log = LoggerFactory.getLogger(SkillMdParser.class);
    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * Parse a SKILL.md file into a {@link SkillMeta} (frontmatter only).
     */
    public SkillMeta parseToMeta(String content, SkillSource source) {
        var frontmatter = extractFrontmatter(content);

        var name = getString(frontmatter, "name", "unnamed");
        var description = getString(frontmatter, "description", "");
        var version = getString(frontmatter, "version", "1.0.0");

        var metadata = getMap(frontmatter, "metadata");
        var active = getBoolean(metadata, "active", true);
        var domain = getString(metadata, "domain", "general");
        var outputSchema = getString(metadata, "output-schema", null);
        var hasSchema = outputSchema != null && !outputSchema.isBlank();

        var allowedRolesStr = getStringList(metadata, "allowed-roles");
        var allowedRoles = allowedRolesStr.isEmpty()
                ? Set.<String>of()
                : Collections.unmodifiableSet(new HashSet<>(allowedRolesStr));

        return new SkillMeta(name, description, version, active, hasSchema, domain, source, allowedRoles);
    }

    /**
     * Parse a SKILL.md file into a full {@link SkillCard} (frontmatter + body).
     */
    public SkillCard parseToCard(String content, SkillSource source) {
        var frontmatter = extractFrontmatter(content);
        var body = extractBody(content);

        var name = getString(frontmatter, "name", "unnamed");
        var description = getString(frontmatter, "description", "");
        var version = getString(frontmatter, "version", "1.0.0");

        var allowedTools = parseAllowedTools(frontmatter.get("allowed-tools"));

        var metadata = getMap(frontmatter, "metadata");
        var active = getBoolean(metadata, "active", true);
        var domain = getString(metadata, "domain", "general");
        var outputSchema = getString(metadata, "output-schema", null);
        var hasSchema = outputSchema != null && !outputSchema.isBlank();
        var maxTokens = getInteger(metadata, "max-tokens");
        var temperature = getDouble(metadata, "temperature");
        var preferredModel = getString(metadata, "preferred-model", null);

        var references = getStringList(frontmatter, "references");

        // RAG configuration from metadata
        var knowledgeBase = getString(metadata, "knowledge-base", null);
        RagConfig ragConfig = null;
        if (knowledgeBase != null && !knowledgeBase.isBlank()) {
            var ragMaxResults = getInteger(metadata, "rag-max-results");
            var ragMinScore = getDouble(metadata, "rag-min-score");
            ragConfig = new RagConfig(
                    knowledgeBase,
                    ragMaxResults != null ? ragMaxResults : 5,
                    ragMinScore != null ? ragMinScore : 0.3
            );
        }

        var allowedRolesCard = getStringList(metadata, "allowed-roles");
        var allowedRolesSet = allowedRolesCard.isEmpty()
                ? Set.<String>of()
                : Collections.unmodifiableSet(new HashSet<>(allowedRolesCard));

        var memoryLayers = parseMemoryLayers(getStringList(metadata, "memory-layers"));

        var meta = new SkillMeta(name, description, version, active, hasSchema, domain, source, allowedRolesSet);

        return new SkillCard(
                meta,
                body,
                allowedTools,
                outputSchema,
                references,
                maxTokens,
                temperature,
                preferredModel,
                ragConfig,
                memoryLayers
        );
    }

    /**
     * Parses an optional {@code memory-layers} list from frontmatter into a {@link MemoryLayer}
     * set. Returns {@code null} (meaning "fetch all layers") when the list is absent or empty,
     * preserving the historical default behaviour. Unknown values are logged and ignored.
     */
    private Set<MemoryLayer> parseMemoryLayers(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        var result = EnumSet.noneOf(MemoryLayer.class);
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                result.add(MemoryLayer.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown memory-layers value '{}' — ignoring", s);
            }
        }
        return result.isEmpty() ? null : Collections.unmodifiableSet(result);
    }

    // ---- internal helpers ----

    Map<String, Object> extractFrontmatter(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }

        String trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return Collections.emptyMap();
        }

        int firstDelimEnd = trimmed.indexOf('\n');
        if (firstDelimEnd == -1) {
            return Collections.emptyMap();
        }

        int secondDelimStart = trimmed.indexOf(FRONTMATTER_DELIMITER, firstDelimEnd + 1);
        if (secondDelimStart == -1) {
            return Collections.emptyMap();
        }

        String yamlContent = trimmed.substring(firstDelimEnd + 1, secondDelimStart).strip();
        if (yamlContent.isEmpty()) {
            return Collections.emptyMap();
        }

        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(yamlContent);
        return parsed != null ? parsed : Collections.emptyMap();
    }

    String extractBody(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return trimmed;
        }

        int firstDelimEnd = trimmed.indexOf('\n');
        if (firstDelimEnd == -1) {
            return "";
        }

        int secondDelimStart = trimmed.indexOf(FRONTMATTER_DELIMITER, firstDelimEnd + 1);
        if (secondDelimStart == -1) {
            return "";
        }

        int bodyStart = trimmed.indexOf('\n', secondDelimStart);
        if (bodyStart == -1) {
            return "";
        }

        return trimmed.substring(bodyStart + 1).strip();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) return Collections.emptyMap();
        return map.get(key) instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        var val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    // Accepts a YAML list `[a, b]` or a whitespace-separated string `"a b"` for backward compatibility.
    private List<String> parseAllowedTools(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Arrays.asList(s.split("\\s+"));
        }
        return Collections.emptyList();
    }

    private List<String> getStringList(Map<String, Object> map, String key) {
        if (map == null) return Collections.emptyList();
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
