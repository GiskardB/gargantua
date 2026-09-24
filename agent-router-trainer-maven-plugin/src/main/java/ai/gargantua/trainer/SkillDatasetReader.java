package ai.gargantua.trainer;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans a skills directory (same layout as {@code FilesystemSkillRegistry} /
 * {@code SkillLinter}: one subdirectory per skill, each containing a {@code SKILL.md}
 * with YAML frontmatter) and builds a training dataset from {@code examples} and
 * {@code metadata.routing-hints}, falling back to {@code description} alone.
 */
public class SkillDatasetReader {

    /**
     * Reads all active skills under {@code skillsDir}.
     *
     * @return training phrases grouped by skill name, in the order skills were found
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> readGrouped(File skillsDir) throws IOException {
        Map<String, List<String>> bySkill = new LinkedHashMap<>();
        if (skillsDir == null || !skillsDir.isDirectory()) {
            return bySkill;
        }

        var children = skillsDir.listFiles(File::isDirectory);
        if (children == null) {
            return bySkill;
        }

        for (var skillDir : children) {
            var skillMd = new File(skillDir, "SKILL.md");
            if (!skillMd.isFile()) {
                continue;
            }

            var content = Files.readString(skillMd.toPath());
            var frontmatter = parseFrontmatter(content);

            Object metadataObj = frontmatter.get("metadata");
            Map<String, Object> metadata = metadataObj instanceof Map<?, ?> m
                    ? (Map<String, Object>) m
                    : Map.of();

            boolean active = !Boolean.FALSE.equals(metadata.get("active"));
            if (!active) {
                continue;
            }

            String name = frontmatter.containsKey("name") ? String.valueOf(frontmatter.get("name")) : skillDir.getName();

            List<String> phrases = new ArrayList<>();
            addStrings(phrases, frontmatter.get("examples"));
            addStrings(phrases, metadata.get("routing-hints"));
            Object description = frontmatter.get("description");
            if (description != null && !String.valueOf(description).isBlank()) {
                phrases.add(String.valueOf(description));
            }

            if (!phrases.isEmpty()) {
                bySkill.put(name, phrases);
            }
        }

        return bySkill;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String content) {
        if (!content.startsWith("---")) {
            return Map.of();
        }
        var endIndex = content.indexOf("---", 3);
        if (endIndex <= 0) {
            return Map.of();
        }
        var yamlBlock = content.substring(3, endIndex).trim();
        var parsed = new Yaml().load(yamlBlock);
        return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private void addStrings(List<String> out, Object rawList) {
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item));
                }
            }
        }
    }
}
