package ai.gargantua.linter;

import ai.gargantua.linter.rules.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Core linting engine that scans skill directories for SKILL.md files
 * and applies configured lint rules.
 */
public class SkillLinter {

    private static final Map<String, LintRule> ALL_RULES = new LinkedHashMap<>();

    static {
        register(new NameMatchesFolderRule());
        register(new VersionSemverRule());
        register(new DescriptionLengthRule());
        register(new EvalsPresentRule());
        register(new ActiveMissingRule());
    }

    private static void register(LintRule rule) {
        ALL_RULES.put(rule.name(), rule);
    }

    private final List<LintRule> activeRules;

    /**
     * Creates a linter with only the specified rules enabled.
     * If {@code ruleNames} is null or empty, all rules are enabled.
     *
     * @param ruleNames list of rule names to enable, or null/empty for all
     */
    public SkillLinter(List<String> ruleNames) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            this.activeRules = new ArrayList<>(ALL_RULES.values());
        } else {
            this.activeRules = new ArrayList<>(ruleNames.stream()
                    .map(ALL_RULES::get)
                    .filter(Objects::nonNull)
                    .toList());
        }
    }

    /**
     * Lint all skills found under the given directory.
     * Each immediate subdirectory containing a SKILL.md is treated as a skill.
     *
     * @param skillsDir the root directory containing skill folders
     * @return list of lint findings
     */
    public List<SkillLintResult> lint(File skillsDir) {
        var results = new ArrayList<SkillLintResult>();

        if (skillsDir == null || !skillsDir.isDirectory()) {
            return results;
        }

        var children = skillsDir.listFiles(File::isDirectory);
        if (children == null) {
            return results;
        }

        for (var skillDir : children) {
            var skillMd = new File(skillDir, "SKILL.md");
            if (!skillMd.isFile()) {
                continue;
            }

            try {
                var content = Files.readString(skillMd.toPath());
                var input = parseSkillMd(content, skillDir);

                for (LintRule rule : activeRules) {
                    rule.check(input).ifPresent(results::add);
                }
            } catch (IOException e) {
                results.add(new SkillLintResult(
                        skillDir.getName(),
                        "parse-error",
                        LintLevel.ERROR,
                        "Failed to read SKILL.md: " + e.getMessage(),
                        "SKILL.md",
                        0
                ));
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private SkillLintInput parseSkillMd(String content, File skillDir) {
        Map<String, Object> frontmatter = new HashMap<>();
        var body = content;

        if (content.startsWith("---")) {
            var endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                var yamlBlock = content.substring(3, endIndex).trim();
                body = content.substring(endIndex + 3).trim();

                var yaml = new Yaml();
                var parsed = yaml.load(yamlBlock);
                if (parsed instanceof Map<?, ?> parsedMap) {
                    frontmatter = (Map<String, Object>) parsedMap;
                }
            }
        }

        var skillName = frontmatter.containsKey("name")
                ? frontmatter.get("name").toString()
                : null;

        return new SkillLintInput(
                skillName,
                skillDir.getName(),
                frontmatter,
                body,
                skillDir
        );
    }
}
