package ai.gargantua.adapters.skill;

import ai.gargantua.core.skill.SkillCard;
import ai.gargantua.core.skill.SkillMeta;
import ai.gargantua.core.skill.SkillSource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Parses SKILL.md files in the adapters module. Delegates to the same logic as
 * the starter module's parser but lives here to avoid a circular dependency.
 *
 * @see ai.gargantua.autoconfigure.SkillMdParser
 */
@Component
public class SkillMdParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    public SkillMeta parseFrontmatter(String content, SkillSource source) {
        Map<String, Object> frontmatter = extractFrontmatter(content);
        return buildMeta(frontmatter, source);
    }

    public SkillCard parseFull(String content, SkillSource source) {
        Map<String, Object> frontmatter = extractFrontmatter(content);
        String body = extractBody(content);
        SkillMeta meta = buildMeta(frontmatter, source);

        String allowedToolsRaw = getStringOrDefault(frontmatter, "allowed-tools", "");
        List<String> allowedTools = allowedToolsRaw.isBlank()
                ? List.of()
                : Arrays.stream(allowedToolsRaw.split("\\s+")).filter(s -> !s.isBlank()).toList();

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = frontmatter.containsKey("metadata")
                ? (Map<String, Object>) frontmatter.get("metadata")
                : Map.of();

        String outputSchema = metadata.containsKey("output-schema")
                ? String.valueOf(metadata.get("output-schema"))
                : null;

        Integer maxTokens = metadata.containsKey("max-tokens")
                ? ((Number) metadata.get("max-tokens")).intValue()
                : null;

        Double temperature = metadata.containsKey("temperature")
                ? ((Number) metadata.get("temperature")).doubleValue()
                : null;

        String preferredModel = metadata.containsKey("preferred-model")
                ? String.valueOf(metadata.get("preferred-model"))
                : null;

        return new SkillCard(meta, body, allowedTools, outputSchema, List.of(),
                maxTokens, temperature, preferredModel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFrontmatter(String content) {
        String trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return Map.of();
        }
        int endIndex = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (endIndex < 0) {
            return Map.of();
        }
        String yamlBlock = trimmed.substring(FRONTMATTER_DELIMITER.length(), endIndex).strip();
        Yaml yaml = new Yaml();
        Map<String, Object> result = yaml.load(yamlBlock);
        return result != null ? result : Map.of();
    }

    private String extractBody(String content) {
        String trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return trimmed;
        }
        int endIndex = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (endIndex < 0) {
            return "";
        }
        return trimmed.substring(endIndex + FRONTMATTER_DELIMITER.length()).strip();
    }

    @SuppressWarnings("unchecked")
    private SkillMeta buildMeta(Map<String, Object> fm, SkillSource source) {
        String name = getStringOrDefault(fm, "name", "unknown");
        String description = getStringOrDefault(fm, "description", "");
        String version = getStringOrDefault(fm, "version", "1.0.0");

        Map<String, Object> metadata = fm.containsKey("metadata")
                ? (Map<String, Object>) fm.get("metadata")
                : Map.of();

        boolean active = metadata.containsKey("active")
                ? Boolean.parseBoolean(String.valueOf(metadata.get("active")))
                : true;
        boolean hasSchema = metadata.containsKey("output-schema");
        String domain = metadata.containsKey("domain")
                ? String.valueOf(metadata.get("domain"))
                : "general";

        return new SkillMeta(name, description, version, active, hasSchema, domain, source);
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value).strip() : defaultValue;
    }
}
