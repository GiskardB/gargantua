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
        var frontmatter = extractFrontmatter(content);
        return buildMeta(frontmatter, source);
    }

    public SkillCard parseFull(String content, SkillSource source) {
        var frontmatter = extractFrontmatter(content);
        var body = extractBody(content);
        var meta = buildMeta(frontmatter, source);

        var allowedToolsRaw = getStringOrDefault(frontmatter, "allowed-tools", "");
        var allowedTools = allowedToolsRaw.isBlank()
                ? List.<String>of()
                : Arrays.stream(allowedToolsRaw.split("\\s+")).filter(s -> !s.isBlank()).toList();

        @SuppressWarnings("unchecked")
        var metadata = frontmatter.containsKey("metadata")
                ? (Map<String, Object>) frontmatter.get("metadata")
                : Map.<String, Object>of();

        var outputSchema = metadata.get("output-schema") instanceof Object v
                ? String.valueOf(v) : null;

        var maxTokens = metadata.get("max-tokens") instanceof Number n
                ? n.intValue() : null;

        var temperature = metadata.get("temperature") instanceof Number n
                ? n.doubleValue() : null;

        var preferredModel = metadata.get("preferred-model") instanceof Object v
                ? String.valueOf(v) : null;

        return new SkillCard(meta, body, allowedTools, outputSchema, List.of(),
                maxTokens, temperature, preferredModel);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFrontmatter(String content) {
        var trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return Map.of();
        }
        var endIndex = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (endIndex < 0) {
            return Map.of();
        }
        var yamlBlock = trimmed.substring(FRONTMATTER_DELIMITER.length(), endIndex).strip();
        var yaml = new Yaml();
        Map<String, Object> result = yaml.load(yamlBlock);
        return result != null ? result : Map.of();
    }

    private String extractBody(String content) {
        var trimmed = content.strip();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return trimmed;
        }
        var endIndex = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (endIndex < 0) {
            return "";
        }
        return trimmed.substring(endIndex + FRONTMATTER_DELIMITER.length()).strip();
    }

    @SuppressWarnings("unchecked")
    private SkillMeta buildMeta(Map<String, Object> fm, SkillSource source) {
        var name = getStringOrDefault(fm, "name", "unknown");
        var description = getStringOrDefault(fm, "description", "");
        var version = getStringOrDefault(fm, "version", "1.0.0");

        var metadata = fm.containsKey("metadata")
                ? (Map<String, Object>) fm.get("metadata")
                : Map.<String, Object>of();

        var active = metadata.get("active") instanceof Object v
                ? Boolean.parseBoolean(String.valueOf(v))
                : true;
        var hasSchema = metadata.containsKey("output-schema");
        var domain = metadata.get("domain") instanceof Object v
                ? String.valueOf(v)
                : "general";

        return new SkillMeta(name, description, version, active, hasSchema, domain, source);
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        var value = map.get(key);
        return value != null ? String.valueOf(value).strip() : defaultValue;
    }
}
