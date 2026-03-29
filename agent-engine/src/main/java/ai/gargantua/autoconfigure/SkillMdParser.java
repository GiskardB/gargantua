package ai.gargantua.autoconfigure;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses SKILL.md files consisting of YAML frontmatter delimited by "---" lines
 * followed by a markdown body that serves as the system prompt.
 */
public class SkillMdParser {

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

        return new SkillMeta(name, description, version, active, hasSchema, domain, source);
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

        var allowedToolsStr = getString(frontmatter, "allowed-tools", "");
        var allowedTools = allowedToolsStr.isBlank()
                ? Collections.<String>emptyList()
                : Arrays.asList(allowedToolsStr.split("\\s+"));

        var metadata = getMap(frontmatter, "metadata");
        var active = getBoolean(metadata, "active", true);
        var domain = getString(metadata, "domain", "general");
        var outputSchema = getString(metadata, "output-schema", null);
        var hasSchema = outputSchema != null && !outputSchema.isBlank();
        var maxTokens = getInteger(metadata, "max-tokens");
        var temperature = getDouble(metadata, "temperature");
        var preferredModel = getString(metadata, "preferred-model", null);

        var references = getStringList(frontmatter, "references");

        var meta = new SkillMeta(name, description, version, active, hasSchema, domain, source);

        return new SkillCard(
                meta,
                body,
                allowedTools,
                outputSchema,
                references,
                maxTokens,
                temperature,
                preferredModel
        );
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
        Object val = map.get(key);
        if (val instanceof Map<?, ?>) {
            return (Map<String, Object>) val;
        }
        return Collections.emptyMap();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object val = map.get(key);
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
    private List<String> getStringList(Map<String, Object> map, String key) {
        if (map == null) return Collections.emptyList();
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
